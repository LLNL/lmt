/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>
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

#if HAVE_CONFIG_H
#include "config.h"
#endif
#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>
#include <string.h>
#if HAVE_GETOPT_H
#include <getopt.h>
#endif
#include <libgen.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "lmt.h"

#include "util.h"
#include "ost.h"
#include "mdt.h"
#include "router.h"

#include "lmtcerebro.h"
#include "lmtconf.h"

#include "lmtmysql.h"
#include "lmtdb.h"

#define OPTIONS "vc:"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"verbose",         no_argument,        0, 'v'},
    {"config-file",     required_argument,  0, 'c'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

#define CURRENT_METRIC_NAMES    "lmt_mdt,lmt_ost,lmt_router"
#define LEGACY_METRIC_NAMES     "lmt_oss,lmt_mds"
#define METRIC_NAMES            CURRENT_METRIC_NAMES","LEGACY_METRIC_NAMES

static int legacy_ost = 0;
static int verbose = 0;

struct lmt_fs_struct {
    List oss;
    List ost;
    List mds;
    List mdt;
    List router;
};
typedef struct lmt_fs_struct *lmt_fs_t;

lmt_fs_t read_cerebro_data (void);
lmt_fs_t read_mysql_data ();
void _lmtfs_destroy (lmt_fs_t f);
lmt_fs_t _lmtfs_create (void);
void analyze (lmt_fs_t fdb, lmt_fs_t fcrb);

static void
usage()
{
    fprintf (stderr, "Usage: lmtdiagnose [-v] [-c lmt.conf]\n");
    exit (1);
}

int
main (int argc, char *argv[])
{
    lmt_fs_t fdb, fcrb;
    int c;
    char *conffile = NULL;

    err_init (argv[0]);
    optind = 0;
    opterr = 0;
    while ((c = GETOPT (argc, argv, OPTIONS, longopts)) != -1) {
        switch (c) {
            case 'v':   /* --verbose */
                verbose = 1;
                break;
            case 'c':   /* --config-file FILE */
                conffile = optarg;
                break;
            default:
                usage ();
        }
    }
    if (optind < argc)
        usage();

    if (lmt_conf_init (1, conffile) < 0)
        exit (1);
    lmt_conf_set_db_debug (1);
    lmt_conf_set_cbr_debug (1);
    lmt_conf_set_proto_debug (1);

    fdb = read_mysql_data ();
    fcrb = read_cerebro_data ();

    analyze (fdb, fcrb);

    /* run two of these 5s(+1s?) apart:
     *   select MAX(TS_ID) from TIMESTAMP_INFO
     * the result should increment or the database is not being updated.
     */

    _lmtfs_destroy (fdb);
    _lmtfs_destroy (fcrb);

    exit (0);
}

int
_findstr (char *s1, char *s2)
{
    return !strcmp (s1, s2);
}

void
append_uniq (List l, char *s)
{
    if (list_find_first (l, (ListFindF)_findstr, s))
        free (s);
    else
        list_append (l, s);
}

lmt_fs_t 
_lmtfs_create (void)
{
    lmt_fs_t f = xmalloc (sizeof (*f));

    f->oss = list_create ((ListDelF)free);
    f->ost = list_create ((ListDelF)free);
    f->mds = list_create ((ListDelF)free);
    f->mdt = list_create ((ListDelF)free);
    f->router = list_create ((ListDelF)free);
    return f; 
}

void
_lmtfs_destroy (lmt_fs_t f)
{
    list_destroy (f->router);
    list_destroy (f->mdt);
    list_destroy (f->mds);
    list_destroy (f->ost);
    list_destroy (f->oss);
    free (f);
}

int
_parse_ost_v2 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *ossname = NULL;
    char *ostname = NULL;
    char *recov_status = NULL;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    uint64_t num_exports;
    List ostinfo = NULL;
    ListIterator itr = NULL;
    char *osi;

    if (lmt_ost_decode_v2 (s, &ossname, &pct_cpu, &pct_mem, &ostinfo) < 0)
        goto done;
    append_uniq (f->oss, ossname);
    if (!(itr = list_iterator_create (ostinfo)))
        goto done;
    while ((osi = list_next (itr))) {
        if (lmt_ost_decode_v2_ostinfo (osi, &ostname, &read_bytes, &write_bytes,
                                       &kbytes_free, &kbytes_total,
                                       &inodes_free, &inodes_total,
                                       &num_exports, &recov_status) < 0)
            goto done;
        free (recov_status);
        append_uniq (f->ost, ostname);
    }
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (ostinfo)
        list_destroy (ostinfo);
    return retval;
}

int
_parse_mdt_v1_mdops (List mdops)
{
    int retval = -1;
    char *opname, *op;
    uint64_t samples, sum, sumsq;
    ListIterator itr;

    if (!(itr = list_iterator_create (mdops)))
        goto done;
    while ((op = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdops (op, &opname, &samples, &sum, &sumsq) < 0)
            goto done;
        free (opname);
    }
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    return retval;    
}

int
_parse_mdt_v1 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *mdsname = NULL;
    char *mdtname = NULL;
    float pct_cpu, pct_mem;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdtinfo = NULL;
    List mdops = NULL;
    ListIterator itr = NULL;
    char *mdi;

    if (lmt_mdt_decode_v1 (s, &mdsname, &pct_cpu, &pct_mem, &mdtinfo) < 0)
        goto done;
    append_uniq (f->mds, mdsname);
    if (!(itr = list_iterator_create (mdtinfo)))
        goto done;
    while ((mdi = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdtinfo (mdi, &mdtname, &inodes_free,
                     &inodes_total, &kbytes_free, &kbytes_total, &mdops) < 0)
            goto done;
        append_uniq (f->mdt, mdtname);
        if (_parse_mdt_v1_mdops (mdops) < 0) {
            list_destroy (mdops); 
            goto done;
        }
        list_destroy (mdops); 
    }
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (mdtinfo)
        list_destroy (mdtinfo);
    return retval;
}

int
_parse_router_v1 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *rtrname = NULL;
    float pct_cpu, pct_mem;
    uint64_t bytes;

    if (lmt_router_decode_v1 (s, &rtrname, &pct_cpu, &pct_mem, &bytes) < 0)
        goto done;
    append_uniq (f->router, rtrname);
    retval = 0;
done:
    return retval;
}

int
_parse_mds_v2 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *mdsname = NULL;
    char *mdtname = NULL;
    float pct_cpu, pct_mem;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdops = NULL;
    ListIterator itr = NULL;
    char *opname, *op;
    uint64_t samples, sum, sumsq;

    if (lmt_mds_decode_v2 (s, &mdsname, &mdtname, &pct_cpu, &pct_mem,
          &inodes_free, &inodes_total, &kbytes_free, &kbytes_total, &mdops) < 0)
        goto done;
    append_uniq (f->mds, mdsname);
    append_uniq (f->mdt, mdtname);
    if (!(itr = list_iterator_create (mdops)))
        goto done;
    while ((op = list_next (itr))) {
        if (lmt_mds_decode_v2_mdops (op, &opname, &samples, &sum, &sumsq) < 0)
            goto done;
        free (opname);
    }
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (mdops)
        list_destroy (mdops);
    return retval;
}

int
_parse_oss_v1 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *ossname = NULL;
    float pct_cpu, pct_mem;

    if (lmt_oss_decode_v1 (s, &ossname, &pct_cpu, &pct_mem) < 0)
        goto done;
    append_uniq (f->oss, ossname);
    retval = 0;
done:
    return retval;
}


int
_parse_ost_v1 (const char *s, lmt_fs_t f)
{
    int retval = -1;
    char *ossname = NULL;
    char *ostname = NULL;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;

    if (lmt_ost_decode_v1 (s, &ossname, &ostname, &read_bytes, &write_bytes,
              &kbytes_free, &kbytes_total, &inodes_free, &inodes_total) < 0)
        goto done;
    legacy_ost++;
    append_uniq (f->oss, ossname);
    append_uniq (f->ost, ostname);
    retval = 0;
done:
    return retval;
}

lmt_fs_t
read_cerebro_data (void)
{
    lmt_fs_t f = _lmtfs_create ();
#ifdef HAVE_CEREBRO
    List l = NULL;;
    ListIterator itr;
    char *name, *val;
    cmetric_t c;
    float vers;
    int err;

    if (verbose)
        msg ("cerebro: reading metric data");
    if (lmt_cbr_get_metrics (METRIC_NAMES, &l) < 0)
        exit (1);
    if (verbose)
        msg ("cerebro: %d metric data elements", list_count (l));
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        name = lmt_cbr_get_name (c);
        val = lmt_cbr_get_val (c);
        if (!val)
            continue;
        if (sscanf (val, "%f;", &vers) != 1) {
            msg ("error parsing metric version");
            continue; 
        }
        if (!strcmp (name, "lmt_ost") && vers == 2)
            err = _parse_ost_v2 (val, f);
        else if (!strcmp (name, "lmt_mdt") && vers == 1)
            err = _parse_mdt_v1 (val, f);
        else if (!strcmp (name, "lmt_router") && vers == 1)
            err = _parse_router_v1 (val, f);
        else if (!strcmp (name, "lmt_mds") && vers == 2)
            err = _parse_mds_v2 (val, f);
        else if (!strcmp (name, "lmt_oss") && vers == 1)
            err = _parse_oss_v1 (val, f);
        else if (!strcmp (name, "lmt_ost") && vers == 1)
            err = _parse_ost_v1 (val, f);
        else {
            msg ("%s_v%d: unknown metric", name, (int)vers);
            continue;
        }
    }
    list_iterator_destroy (itr);
    list_destroy (l);
#else
    if (verbose)
        msg ("cerebro is disabled in this build of LMT");
#endif
    return f;
}

int _map (const char *key, void *arg)
{
    char *cpy = xstrdup ((char *)key);

    append_uniq ((List)arg, cpy);
    return 0;
}

lmt_fs_t 
read_mysql_data (void)
{
    lmt_fs_t f = _lmtfs_create ();
#ifdef HAVE_MYSQL
    lmt_db_t db;
    List dbs = NULL;
    ListIterator itr;

    if (verbose)
        msg ("mysql: connecting");
    if (lmt_db_create_all (1, &dbs) < 0)
        exit (1);
    if (verbose)
        msg ("mysql: %d LMT databases found", list_count (dbs));
    itr = list_iterator_create (dbs);
    while ((db = list_next (itr))) {
        lmt_db_server_map (db, "oss", (lmt_db_map_f)_map, f->oss);
        lmt_db_server_map (db, "ost", (lmt_db_map_f)_map, f->ost);
        lmt_db_server_map (db, "mds", (lmt_db_map_f)_map, f->mds);
        lmt_db_server_map (db, "mdt", (lmt_db_map_f)_map, f->mdt);
        lmt_db_server_map (db, "router", (lmt_db_map_f)_map, f->router);
    }
    list_iterator_destroy (itr);
    list_destroy (dbs);
#else
    if (verbose)
        msg ("mysql is disabled in this build of LMT");
#endif

    return f;
}

void
listcmp (char *svctype, List l1, char *s1, List l2, char *s2)
{
    ListIterator itr;
    char *name;

    itr = list_iterator_create (l1);
    while ((name = list_next (itr))) {
        if (!list_find_first (l2, (ListFindF)_findstr, name))
            printf ("%s '%s' in %s but not %s\n", svctype, name, s1, s2);
    } 
    list_iterator_destroy (itr);
}

void analyze (lmt_fs_t fdb, lmt_fs_t fcrb)
{
#ifdef HAVE_MYSQL
    if (verbose) {
        msg ("mysql: %d oss nodes",    list_count (fdb->oss));
        msg ("mysql: %d osts",         list_count (fdb->ost));
        msg ("mysql: %d mds nodes",    list_count (fdb->mds));
        msg ("mysql: %d mdts",         list_count (fdb->mdt));
        msg ("mysql: %d router nodes", list_count (fdb->router));
    }
#endif
#ifdef HAVE_CEREBRO
    if (verbose) {
        msg ("cerebro: %d oss nodes",  list_count (fcrb->oss));
        msg ("cerebro: %d osts",       list_count (fcrb->ost));
        msg ("cerebro: %d mds nodes",  list_count (fcrb->mds));
        msg ("cerbero: %d mdts",       list_count (fcrb->mdt));
        msg ("cerebro: %d router nodes", list_count (fcrb->router));
    }
#endif
#if defined (HAVE_CEREBRO) && defined (HAVE_MYSQL)
    if (verbose)
        msg ("cross-checking oss data");
    listcmp ("oss",    fdb->oss,    "mysql", fcrb->oss,    "cerebro");
    listcmp ("oss",    fcrb->oss,   "cerebro", fdb->oss,   "mysql");

    if (verbose)
        msg ("cross-checking ost data%s",
                legacy_ost ? " (relaxed due to legacy ost_v1 use)" : "");
    if (!legacy_ost)
        listcmp ("ost",    fdb->ost,    "mysql", fcrb->ost,    "cerebro");
    listcmp ("ost",    fcrb->ost,    "cerebro", fdb->ost,    "mysql");

    if (verbose)
        msg ("cross-checking mds data");
    listcmp ("mds",    fdb->mds,    "mysql", fcrb->mds,    "cerebro");
    listcmp ("mds",    fcrb->mds,    "cerebro", fdb->mds,    "mysql");

    if (verbose)
        msg ("cross-checking mdt data");
    listcmp ("mdt",    fdb->mdt,    "mysql", fcrb->mdt,    "cerebro");
    listcmp ("mdt",    fcrb->mdt,    "cerebro", fdb->mdt,    "mysql");

    if (verbose)
        msg ("cross-checking router data");
    listcmp ("router", fcrb->router, "cerebro", fdb->router, "mysql");
    listcmp ("router", fdb->router, "mysql", fcrb->router, "cerebro");
#endif
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

