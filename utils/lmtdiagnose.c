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

#include "proc.h"
#include "lmt.h"

#include "ost.h"
#include "mdt.h"
#include "router.h"
#include "lmtmysql.h"
#include "lmtcerebro.h"

#define OPTIONS "f:"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"filesystem",      required_argument,  0, 'f'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

#define CURRENT_METRIC_NAMES    "lmt_mdt,lmt_ost,lmt_router"
#define LEGACY_METRIC_NAMES     "lmt_oss,lmt_mds"
#define METRIC_NAMES            CURRENT_METRIC_NAMES","LEGACY_METRIC_NAMES

static char *prog;

/* default to localhost:3306 root:"" */
static const char *db_host = "localhost";
static const unsigned int db_port = 0;
static const char *db_user = "lwatchclient";
static const char *db_passwd = NULL;

static void
usage()
{
    fprintf (stderr,
"Usage: lmtdiagnose [OPTIONS]\n"
"   -f,--filesystem NAME        select file system [default all]\n"
    );
    exit (1);
}

void
check_cerebro_metric (char *name, char *val)
{
    float vers;
    char *desc;

    if (sscanf (val, "%f;", &vers) != 1) {
        fprintf (stderr, "%s: error parsing metric version\n", prog);
        return;
    }
    if (!strcmp (name, "lmt_ost") && vers == 2)
        desc = "current";
    else if (!strcmp (name, "lmt_mdt") && vers == 1)
        desc = "current";
    else if (!strcmp (name, "lmt_router") && vers == 1)
        desc = "current";
    else if (!strcmp (name, "lmt_mds") && vers == 2)
        desc = "legacy";
    else if (!strcmp (name, "lmt_oss") && vers == 1)
        desc = "legacy";
    else if (!strcmp (name, "lmt_ost") && vers == 1)
        desc = "legacy";
    else
        desc = "unknown";
    fprintf (stderr, "%s: %s metric %s_v%d\n", prog, desc, name, (int)vers);
}

void
check_cerebro (void)
{
    List l = NULL;;
    ListIterator itr;
    char *name, *val, *errstr = NULL;
    cmetric_t c;

    if (lmt_cbr_get_metrics (METRIC_NAMES, &l, &errstr) < 0) {
        fprintf (stderr, "%s: lmt_ost: %s\n", prog,
                 errstr ? errstr : strerror (errno));
        exit (1);
    }
    if (!(itr = list_iterator_create (l))) {
        fprintf (stderr, "%s: out of memory\n", prog);
        exit (1);
    }
    while ((c = list_next (itr))) {
        name = lmt_cbr_get_name (c);
        val = lmt_cbr_get_val (c);
        if (val)
            check_cerebro_metric (name, val);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
}

void
check_mysql (void)
{
    List dbs = NULL;
    const char *errstr = NULL;
    ListIterator itr;
    lmt_db_t db;

    if (lmt_db_create_all (db_host, db_port, db_user, db_passwd,
                                                    &dbs, &errstr) < 0) {
        fprintf (stderr, "%s: %s\n", prog, errstr ? errstr : strerror (errno));
        exit (1);
    }
    if (list_is_empty (dbs)) {
        fprintf (stderr, "%s: mysql has no file systems configured\n", prog);
        exit (1);
    }
    if (!(itr = list_iterator_create (dbs))) {
        fprintf (stderr, "%s: out of memory\n", prog);
        exit (1);
    }
    while ((db = list_next (itr))) {
        fprintf (stderr, "%s: mysql: %s\n", prog, lmt_db_name (db));
    }
    list_iterator_destroy (itr);

    list_destroy (dbs);
}

int
main (int argc, char *argv[])
{
    char *fs = NULL;
    int c;

    prog = basename (argv[0]);
    optind = 0;
    opterr = 0;
    while ((c = GETOPT (argc, argv, OPTIONS, longopts)) != -1) {
        switch (c) {
            case 'f':   /* --filesystem NAME */
                fs = optarg;
                break;
            default:
                usage ();
        }
    }
    if (optind < argc)
        usage();

    check_cerebro ();
    check_mysql ();

    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

