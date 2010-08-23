/*****************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>.
 *  UCRL-CODE-232438
 *  All Rights Reserved.
 *
 *  This file is part of Lustre Monitoring Tool, version 2.
 *  Authors: H. Wartens, P. Spencer, N. O'Neill, J. Long, J. Garlick
 *  For details, see http://code.google.com/p/lmt/.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License (as published by the
 *  Free Software Foundation) version 2, dated June 1991.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the terms and conditions of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see
 *  <http://www.gnu.org/licenses/>.
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
 * . Although there OSS_DATA and OST_DATA are separate tables, MDS and MDT
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

#define MIN_RECONNECT_SECS  15

static int
_init_db_ifneeded (void)
{
    int retval = -1;
    struct timeval now;

    /* FIXME: check if config should be reloaded, do it if so.
     */

    if (!dbs) {
        if (gettimeofday (&now, NULL) < 0)
            goto done;
        if (now.tv_sec - last_connect.tv_sec < MIN_RECONNECT_SECS)
            goto done;
        last_connect = now;
        msg ("connecting to database");
        if (lmt_db_create_all (0, &dbs) < 0)
            goto done;
    }
    retval = 0;
done:
    return retval;
}

static void
_trigger_db_reconnect (void)
{
    msg ("disconnecting from database");
    if (dbs) {
        list_destroy (dbs);
        dbs = NULL;
    }
}

/* Helper for _ost_to_db () and _mdt_to_db () */
static lmt_db_t 
_finddb (char *name, int len)
{
    lmt_db_t db = NULL;
    ListIterator itr;

    itr = list_iterator_create (dbs);
    while ((db = list_next (itr)))
        if (!strncmp (lmt_db_fsname (db), name, len))
            break;
    list_iterator_destroy (itr);

    return db;
}

/* Locate db for ostname using assumption about naming:
 * e.g. lc1-OST0000 corresponds to filesystem_lc1.
 * Verify against OST_INFO hash for that database.
 */
static lmt_db_t
_ost_to_db (char *ostname)
{
    char *p = strchr (ostname, '-');
    int fslen = p ? p - ostname : strlen (ostname);
    lmt_db_t db = _finddb (ostname, fslen);

    if (!db) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no database", ostname);
        return NULL;
    }
    if (lmt_db_lookup (db, "ost", ostname) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in %s OST_INFO", ostname, lmt_db_fsname (db));
        return NULL;
    }
    return db;
}

/* Locate db for ostname using assumption about naming:
 * e.g. lc1-MDT0000 corresponds to filesystem_lc1.
 * Verify against MDS_INFO hash for that database.
 */
static lmt_db_t
_mdt_to_db (char *mdtname)
{
    char *p = strchr (mdtname, '-');
    int fslen = p ? p - mdtname : strlen (mdtname);
    lmt_db_t db = _finddb (mdtname, fslen);

    if (!db) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no database", mdtname);
        return NULL;
    }
    if (lmt_db_lookup (db, "mdt", mdtname) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in %s MDS_INFO", mdtname, lmt_db_fsname (db));
        return NULL;
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

    if (lmt_ost_decode_v2_ostinfo (s, &ostname, &read_bytes, &write_bytes,
                                   &kbytes_free, &kbytes_total,
                                   &inodes_free, &inodes_total) < 0) {
        goto done;
    }
    if (!(db = _ost_to_db (ostname)))
        goto done;
    if (lmt_db_insert_ost_data (db, ostname, read_bytes, write_bytes,
                                kbytes_free, kbytes_total - kbytes_free,
                                inodes_free, inodes_total - inodes_free) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
    if (lmt_db_lookup (db, "oss", ossname) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in %s OSS_INFO", ossname, lmt_db_fsname (db));
        goto done;
    }
    if (lmt_db_insert_oss_data (db, ossname, pct_cpu, pct_mem) < 0) {
        _trigger_db_reconnect ();
        goto done;
    }
done:
    if (ostname)
        free (ostname);
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
    if (lmt_db_lookup (db, "op", opname) < 0)
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
_insert_mds (char *mdsname, float pct_cpu, float pct_mem, char *s)
{
    ListIterator itr;
    lmt_db_t db;
    char *op, *mdtname = NULL;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdops = NULL;

    if (lmt_mdt_decode_v1_mdtinfo (s, &mdtname, &inodes_free, &inodes_total,
                                   &kbytes_free, &kbytes_total, &mdops) < 0)
        goto done;
    if (!(db = _mdt_to_db (mdtname)))
        goto done;
    if (lmt_db_insert_mds_data (db, mdtname, pct_cpu,
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
    if (mdops)
        list_destroy (mdops);
}

/* lmt_mdt_v1: mds + multipe mdt's */
void
lmt_db_insert_mdt_v1 (char *s)
{
    ListIterator itr;
    char *mdt, *mdsname = NULL;
    float pct_cpu, pct_mem;
    List mdtinfo = NULL;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_mdt_decode_v1 (s, &mdsname, &pct_cpu, &pct_mem, &mdtinfo) < 0)
        goto done;
    itr = list_iterator_create (mdtinfo);
    while ((mdt = list_next (itr)))
        _insert_mds (mdsname, pct_cpu, pct_mem, mdt);
    list_iterator_destroy (itr);        
done:
    if (mdsname)
        free (mdsname);    
    if (mdtinfo)
        list_destroy (mdtinfo);
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
        if (lmt_db_lookup (db, "router", rtrname) < 0) {
            if (lmt_conf_get_db_debug ())
                msg ("router %s is not found in ROUTER_INFO of %s db",
                     rtrname, lmt_db_fsname (db));
            continue;
        }
        if (lmt_db_insert_router_data (db, rtrname, bytes, pct_cpu) < 0) {
            _trigger_db_reconnect ();
            continue;
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
    if (!(db = _mdt_to_db (mdtname)))
        goto done;
    if (lmt_db_insert_mds_data (db, mdtname, pct_cpu,
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
    int inserts = 0;

    if (_init_db_ifneeded () < 0)
        goto done;
    if (lmt_oss_decode_v1 (s, &ossname, &pct_cpu, &pct_mem) < 0)
        goto done;
    itr = list_iterator_create (dbs);
    while ((db = list_next (itr))) {
        if (lmt_db_lookup (db, "oss", ossname) < 0)
            continue; 
        if (lmt_db_insert_oss_data (db, ossname, pct_cpu, pct_mem) < 0) {
            _trigger_db_reconnect ();
            goto done;
        }
        inserts++;
    }
    if (inserts == 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in any OSS_INFO", ossname);
        goto done;
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
    if (!(db = _ost_to_db (ostname)))
        goto done;
    if (lmt_db_insert_ost_data (db, ostname, read_bytes, write_bytes,
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
