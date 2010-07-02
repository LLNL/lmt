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

#include "list.h"
#include "hash.h"

#include "proc.h"
#include "lmt.h"

#include "ost.h"
#include "mdt.h"
#include "router.h"
#include "lmtsql.h"

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

/* default to localhost:3306 root:"" */
static const char *db_host = NULL;
static const unsigned int db_port = 0;
static const char *db_user = NULL;
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

int
main (int argc, char *argv[])
{
    char *fs = NULL;
    int c;
    List dbs = NULL;
    const char *errstr = NULL;


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

    if (lmt_db_create_all (db_host, db_port, db_user, db_passwd,
                                                    &dbs, &errstr) < 0) {
        fprintf (stderr, "%s\n", errstr ? errstr : strerror (errno));
        exit (1);
    }

    /* FIXME: do stuff
     */

    list_destroy (dbs);

    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

