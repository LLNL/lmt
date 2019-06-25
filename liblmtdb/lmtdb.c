/*****************************************************************************
 *  Copyright (C) 2007 Lawrence Livermore National Security, LLC.
 *  This module was written by Jim Garlick <garlick@llnl.gov>
 *  UCRL-CODE-232438 All Rights Reserved.
 *
 *  This file is part of the Lustre Monitoring Tool.
 *  For details, see http://github.com/chaos/lmt.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the license, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the terms and conditions of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see
 *  http://www.gnu.org/licenses.
 *****************************************************************************/

/* lmtdb.c - wire up mysql.c and ost|mdt|router.c for use by cerebro monitor */

#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <errno.h>
#include <sys/utsname.h>
#include <stdint.h>
#include <sys/time.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"

#include "ost.h"
#include "mdt.h"
#include "router.h"
#include "lmtmysql.h"
#include "lmtconf.h"
#include "lmt.h"

/* FIXME [schema 1.1]:
 * 
 * . There is a fixed mapping of OST->OSS in the OST_INFO table, when there
 *   should be a dynamic mapping in OST_DATA to support failover.  As a
 *   result, during failover, b/w will be attributed to the wrong OSS.
 *
 * . Although OSS_DATA and OST_DATA are separate tables, MDS and MDT
 *   data are combined in MDS_DATA forcing MDS data to be replicated across
 *   databases when there are multiple targets per MDS, and makes failover
 *   hard to represent.
 *
 * . pct_mem was (unintentionally?) omitted from MDS_DATA and ROUTER_DATA.
 *
 * . pct_cpu was (unintentionally?) included in OST_DATA (not used).
 */

/**
 ** Manage a list of db handles.
 **/

static List dbs = NULL;
static struct timeval last_connect = { .tv_usec = 0, .tv_sec = 0 };
static int reconnect_needed = 1;

#define MIN_RECONNECT_SECS  15

static int
_init_db_ifneeded (void)
{
    int retval = -1;
    struct timeval now;

    /* FIXME: check if config should be reloaded, do it if so.
     */

    if (reconnect_needed) {
        if (dbs) {
            msg ("disconnecting from database");
            list_destroy (dbs);
            dbs = NULL;
        }
        if (gettimeofday (&now, NULL) < 0)
            goto done;
        if (now.tv_sec - last_connect.tv_sec < MIN_RECONNECT_SECS)
            goto done;
        last_connect = now;
        if (lmt_db_create_all (0, &dbs) < 0) {
            msg ("failed to connect to database");
            goto done;
        }
        msg ("connected to database");
        reconnect_needed = 0;
    }
    retval = 0;
done:
    return retval;
}

static void
_trigger_db_reconnect (void)
{
    reconnect_needed = 1;
}

/* Locate db for ost or mdt using assumption about naming:
 * e.g. lc1-OST0000 corresponds to filesystem_lc1.
 * or   lc2-MDT0000 corresponds to filesystem_lc2.
 */
static lmt_db_t
_svc_to_db (char *name)
{
    lmt_db_t db = NULL;
    ListIterator itr;
    char *p = strchr (name, '-');
    int len = p ? p - name : strlen (name);

    if (dbs) {
        itr = list_iterator_create (dbs);
        while ((db = list_next (itr)))
            if (!strncmp (lmt_db_fsname (db), name, len))
                break;
        list_iterator_destroy (itr);
        if (!db) {
            if (lmt_conf_get_db_debug ())
                msg ("%s: no database", name);
        }
    }
    return db;
}

/**
 ** Handlers for incoming strings.
 **/

/* Helper for lmt_db_insert_ost_v2 () */
static void
_insert_ostinfo (char *ossname, float pct_cpu, float pct_mem, char *s)
{
    lmt_db_t db;
    char *ostname = NULL;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    char *recov_status = NULL;

    if (lmt_ost_decode_v2_ostinfo (s, &ostname, &read_bytes, &write_bytes,
                                   &kbytes_free, &kbytes_total,
                                   &inodes_free, &inodes_total, &iops,
                                   &num_exports, &lock_count, &grant_rate,
                                   &cancel_rate, &connect, &reconnect,
                                   &recov_status) < 0) {
        goto done;
    }
    if (!(db = _svc_to_db (ostname)))
        goto done;
    if (lmt_db_insert_ost_data (db, ossname, ostname, read_bytes, write_bytes,
                                kbytes_free, kbytes_total - kbytes_free,
                                inodes_free, inodes_total - inodes_free) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
    if (lmt_db_insert_oss_data (db, 0, ossname, pct_cpu, pct_mem) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
done:
    if (ostname)
        free (ostname);
    if (recov_status)
        free (recov_status);
}

/* lmt_ost_v2: oss + multiple ost's */
void
lmt_db_insert_ost_v2 (char *s)
{
    ListIterator itr = NULL;
    char *ostr, *ossname = NULL;
    float pct_cpu, pct_mem;
    List ostinfo = NULL;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_ost_decode_v2 (s, &ossname, &pct_cpu, &pct_mem, &ostinfo) < 0)
        goto done;
    itr = list_iterator_create (ostinfo);
    while ((ostr = list_next (itr)))
        _insert_ostinfo (ossname, pct_cpu, pct_mem, ostr);
    list_iterator_destroy (itr);
done:
    if (ossname)
        free (ossname);
    if (ostinfo)
        list_destroy (ostinfo);
}

/* helper for _insert_mds () */
static void
_insert_mds_ops (lmt_db_t db, char *mdtname, char *s)
{
    char *opname = NULL;
    uint64_t samples, sum, sumsquares;

    if (lmt_mdt_decode_v1_mdops (s, &opname, &samples, &sum, &sumsquares) < 0)
        goto done;
    if (lmt_db_insert_mds_ops_data (db, mdtname, opname,
                                    samples, sum, sumsquares) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
done:
    if (opname)
        free (opname);
}

/* helper for lmt_db_insert_mdt_v1 () */
static void
_insert_mds (char *mdsname, float pct_cpu, float pct_mem, char *s, int ver)
{
    ListIterator itr;
    lmt_db_t db;
    char *op, *mdtname = NULL;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdops = NULL;
    char *recov_status = NULL;
    int rc;

    if (ver==1)
        rc = lmt_mdt_decode_v1_mdtinfo (s, &mdtname, &inodes_free,
                    &inodes_total, &kbytes_free, &kbytes_total, &mdops);
    else if (ver==2)
        rc = lmt_mdt_decode_v2_mdtinfo (s, &mdtname, &inodes_free,
                    &inodes_total, &kbytes_free, &kbytes_total, &recov_status,
                    &mdops);
    else
        goto done;

    if (rc<0)
        goto done;

    if (!(db = _svc_to_db (mdtname)))
        goto done;
    if (lmt_db_insert_mds_data (db, mdsname, mdtname, pct_cpu,
                                kbytes_free, kbytes_total - kbytes_free,
                                inodes_free, inodes_total - inodes_free) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
    itr = list_iterator_create (mdops);
    while ((op = list_next (itr)))
        _insert_mds_ops (db, mdtname, op);
    list_iterator_destroy (itr);
done:
    if (recov_status)
        free (recov_status);
    if (mdtname)
        free (mdtname);
    if (mdops)
        list_destroy (mdops);
}

/* lmt_mdt_v1 and lmt_mdt_v2 helper */
void
lmt_db_insert_mdt_v1_v2 (char *s, int ver)
{
    ListIterator itr;
    char *mdt, *mdsname = NULL;
    float pct_cpu, pct_mem;
    List mdtinfo = NULL;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_mdt_decode_v1_v2 (s, &mdsname, &pct_cpu, &pct_mem, &mdtinfo, ver) < 0)
        goto done;
    itr = list_iterator_create (mdtinfo);
    while ((mdt = list_next (itr)))
        _insert_mds (mdsname, pct_cpu, pct_mem, mdt, ver);
    list_iterator_destroy (itr);        
done:
    if (mdsname)
        free (mdsname);    
    if (mdtinfo)
        list_destroy (mdtinfo);
}

/* lmt_mdt_v1: mds + multipe mdt's */
void
lmt_db_insert_mdt_v1 (char *s)
{
    lmt_db_insert_mdt_v1_v2 (s, 1);
}

/* lmt_mdt_v2: mds + multipe mdt's w/ recovery info */
void
lmt_db_insert_mdt_v2 (char *s)
{
    lmt_db_insert_mdt_v1_v2 (s, 2);
}

/* lmt_router_v1: router */
void
lmt_db_insert_router_v1 (char *s)
{
    ListIterator itr;
    lmt_db_t db;
    char *rtrname = NULL;
    float pct_cpu, pct_mem;
    uint64_t bytes;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_router_decode_v1 (s, &rtrname, &pct_cpu, &pct_mem, &bytes) < 0)
        goto done;
    itr = list_iterator_create (dbs);
    while ((db = list_next (itr))) {
        if (lmt_db_insert_router_data (db, rtrname, bytes, pct_cpu) < 0) {
            _trigger_db_reconnect ();
            break;
        }
    }
    list_iterator_destroy (itr);        
done:
    if (rtrname)
        free (rtrname);
}

/**
 ** Legacy
 **/

/* lmt_mds_v2: single mds + single mdt */
void
lmt_db_insert_mds_v2 (char *s)
{
    ListIterator itr;
    lmt_db_t db;
    char *op, *mdtname = NULL, *mdsname = NULL;
    float pct_cpu, pct_mem;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdops = NULL;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_mds_decode_v2 (s, &mdsname, &mdtname, &pct_cpu, &pct_mem,
                           &inodes_free, &inodes_total,
                           &kbytes_free, &kbytes_total, &mdops) < 0)
        goto done;
    if (!(db = _svc_to_db (mdtname)))
        goto done;
    if (lmt_db_insert_mds_data (db, mdsname, mdtname, pct_cpu,
                                kbytes_free, kbytes_total - kbytes_free,
                                inodes_free, inodes_total - inodes_free) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
    itr = list_iterator_create (mdops);
    while ((op = list_next (itr)))
        _insert_mds_ops (db, mdtname, op);
    list_iterator_destroy (itr);        
done:
    if (mdtname)
        free (mdtname);    
    if (mdsname)
        free (mdsname);    
    if (mdops)
        list_destroy (mdops);
}

/* lmt_oss_v1: single oss (no ost info) */
void
lmt_db_insert_oss_v1 (char *s)
{
    ListIterator itr = NULL;
    lmt_db_t db;
    char *ossname = NULL;
    float pct_cpu, pct_mem;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_oss_decode_v1 (s, &ossname, &pct_cpu, &pct_mem) < 0)
        goto done;
    itr = list_iterator_create (dbs);
    while ((db = list_next (itr))) {
        /* N.B. defeat automatic insertion of new OSS_INFO for legacy,
         * as there is no way to tie OSS to a particular file system.
         */
        if (lmt_db_lookup (db, "oss", ossname) < 0)
            continue; 
        if (lmt_db_insert_oss_data (db, 1, ossname, pct_cpu, pct_mem) < 0) {
            _trigger_db_reconnect ();
            goto done;
        }
    }
done:
    if (ossname)
        free (ossname);    
    if (itr)
        list_iterator_destroy (itr);        
}

/* lmt_ost_v1: single ost (no oss info) */
void
lmt_db_insert_ost_v1 (char *s)
{
    lmt_db_t db;
    char *ossname = NULL, *ostname = NULL;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_ost_decode_v1 (s, &ossname, &ostname, &read_bytes, &write_bytes,
                           &kbytes_free, &kbytes_total,
                           &inodes_free, &inodes_total) < 0) {
        goto done;
    }
    if (!(db = _svc_to_db (ostname)))
        goto done;
    if (lmt_db_insert_ost_data (db, ossname, ostname, read_bytes, write_bytes,
                                kbytes_free, kbytes_total - kbytes_free,
                                inodes_free, inodes_total - inodes_free) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
done:
    if (ostname)
        free (ostname);    
    if (ossname)
        free (ossname);    
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
