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

#include "proc.h"
#include "lmt.h"

#include "ost.h"
#include "mdt.h"
#include "router.h"
#include "mysql.h"

/**
 ** Manage a list of db handles.
 **/

static List dbs = NULL;
static struct timeval last_connect = { .tv_usec = 0, .tv_sec = 0 };

#define MIN_RECONNECT_SECS  15

static int
_init_db_ifneeded (const char **errp, int *retp)
{
    int retval = -1;
    struct timeval now;

    if (!dbs) {
        if (gettimeofday (&now, NULL) < 0)
            goto done;
        if (now.tv_sec - last_connect.tv_sec < MIN_RECONNECT_SECS) {
            *retp = 0; /* silently succeed: too early to reconnect */
            goto done;
        }
        last_connect = now;
        if (lmt_db_create_all (&dbs, errp) < 0)
            goto done;
    }
    retval = 0;
done:
    return retval;
}

static void
_trigger_db_reconnect (void)
{
    if (dbs) {
        list_destroy (dbs);
        dbs = NULL;
    }
}

/**
 ** Handlers for incoming strings.
 **/

/* Helper for lmt_db_insert_ost_v2 () */
static int
_insert_ostinfo (char *s, const char **errp)
{
    int retval = -1;
    ListIterator itr = NULL;
    lmt_db_t db;
    char *name = NULL;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_used;
    uint64_t inodes_free, inodes_used;
    int inserts = 0;

    if (lmt_ost_decode_v2_ostinfo (s, &name, &read_bytes, &write_bytes,
                                    &kbytes_free, &kbytes_used,
                                    &inodes_free, &inodes_used) < 0) {
        if (errno == EIO)
            *errp = "error parsing ost_v2 string";
        goto done;
    }
    if (!(itr = list_iterator_create (dbs)))
        goto done;
    while ((db = list_next (itr))) {
        if (lmt_db_lookup (db, "ost", name) < 0)
            continue; 
        if (lmt_db_insert_ost_data (db, name, read_bytes, write_bytes,
                                    kbytes_free, kbytes_used,
                                    inodes_free, inodes_used, errp) < 0) {
            _trigger_db_reconnect ();
            goto done;
        }
        inserts++;
    }
    if (inserts == 0) /* ost is present in exactly one db */
        goto done;
    if (inserts > 1) {
        *errp = "ost is present in more than one db";
        goto done;
    }
done:
    if (itr)
        list_iterator_destroy (itr);
    if (name)
        free (name);
    return retval;
}

int
lmt_db_insert_ost_v2 (char *s, const char **errp)
{
    int retval = -1;
    ListIterator itr = NULL;
    lmt_db_t db;
    char *ostr, *name = NULL;
    float pct_cpu, pct_mem;
    List ostinfo = NULL;
    int inserts = 0;

    if (_init_db_ifneeded (errp, &retval) < 0)
        goto done;
    if (lmt_ost_decode_v2 (s, &name, &pct_cpu, &pct_mem, &ostinfo) < 0) {
        if (errno == EIO)
            *errp = "error parsing ost_v2 string";
        goto done;
    }

    /* Insert the OSS info.
     */
    if (!(itr = list_iterator_create (dbs)))
        goto done;
    while ((db = list_next (itr))) {
        if (lmt_db_lookup (db, "oss", name) < 0)
            continue; 
        if (lmt_db_insert_oss_data (db, name, pct_cpu, pct_mem, errp) < 0) {
            _trigger_db_reconnect ();
            goto done;
        }
        inserts++;
    }
    if (inserts == 0) /* oss is present in one more more db's */
        goto done;
    list_iterator_destroy (itr);

    /* Insert the OST info (for each OST on the OSS).
     */
    if (!(itr = list_iterator_create (ostinfo)))
        goto done;
    while ((ostr = list_next (itr))) {
        if (_insert_ostinfo (ostr, errp) < 0)
            goto done;
    }
    retval = 0;
done:
    if (name)
        free (name);    
    if (itr)
        list_iterator_destroy (itr);        
    if (ostinfo)
        list_destroy (ostinfo);
    return retval;
}

int
lmt_db_insert_mdt_v1 (char *s, const char **errp)
{
    return -1;
}

int
lmt_db_insert_router_v1 (char *s, const char **errp)
{
    int retval = -1;
    ListIterator itr = NULL;
    lmt_db_t db;
    char *name = NULL;
    float pct_cpu;
    uint64_t bytes;

    if (_init_db_ifneeded (errp, &retval) < 0)
        goto done;
    if (lmt_router_decode_v1 (s, &name, &bytes, &pct_cpu) < 0) {
        if (errno == EIO)
            *errp = "error parsing router_v1 string";
        goto done;
    }
    if (!(itr = list_iterator_create (dbs)))
        goto done;
    while ((db = list_next (itr))) {
        if (lmt_db_lookup (db, "router", name) < 0)
            goto done; /* router should be present in all db's */
        if (lmt_db_insert_router_data (db, name, bytes, pct_cpu, errp) < 0) {
            _trigger_db_reconnect ();
            goto done;
        }
    }
    retval = 0;
done:
    if (name)
        free (name);
    if (itr)
        list_iterator_destroy (itr);        
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
