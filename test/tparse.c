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
#include "util.h"

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

const char *oss_v1_str =
    "1.0;tycho1;0.100000;98.810898";
const char *ost_v1_str =
    "1.0;tycho1;lc1-OST0000;121615156;122494976;"
    "1819998804;1929120176;19644389;289174933853";
const char *mds_v2_str =
    "2.0;tycho-mds2;lc2-MDT0000;0.000000;1.561927;31242342;31242480;124969368;"
    "125441296;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;1;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;3132344;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;48;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;48;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;217;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";
const char *router_v1_str =
    "1.0;alc42;0.100000;98.810898;1845066588";

void parse_utils (void);
void parse_current (void);
void parse_legacy (void);

static char *prog;

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

    parse_utils ();
    parse_legacy ();
    exit (0);
}

int
_parse_ost_v2 (char *s)
{
    int retval = -1;
    return retval;
}

int
_parse_mdt_v1 (const char *s)
{
    int retval = -1;
    return retval;
}

int
_parse_router_v1 (const char *s)
{
    int retval = -1;
    char *name = NULL;
    float pct_cpu, pct_mem;
    uint64_t bytes;

    if (lmt_router_decode_v1 (s, &name, &pct_cpu, &pct_mem, &bytes) < 0)
        goto done;
    retval = 0;
done:
    if (name)
        free (name);
    return retval;
}

int
_parse_mds_v2 (const char *s)
{
    int retval = -1;
    char *mdsname = NULL;
    char *name = NULL;
    float pct_cpu, pct_mem;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    List mdops = NULL;
    ListIterator itr = NULL;
    char *opname, *op;
    uint64_t samples, sum, sumsq;

    if (lmt_mds_decode_v2 (s, &mdsname, &name, &pct_cpu, &pct_mem,
          &inodes_free, &inodes_total, &kbytes_free, &kbytes_total, &mdops) < 0)
        goto done;
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
    if (name)
        free (name); 
    if (mdsname)
        free (mdsname); 
    if (mdops)
        list_destroy (mdops);
    return retval;
}

int
_parse_oss_v1 (const char *s)
{
    int retval = -1;
    char *name = NULL;
    float pct_cpu, pct_mem;

    if (lmt_oss_decode_v1 (s, &name, &pct_cpu, &pct_mem) < 0)
        goto done;
    retval = 0;
done:
    if (name)
        free (name);
    return retval;
}

int
_parse_ost_v1 (const char *s)
{
    int retval = -1;
    char *ossname = NULL;
    char *name = NULL;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;

    if (lmt_ost_decode_v1 (s, &ossname, &name, &read_bytes, &write_bytes,
              &kbytes_free, &kbytes_total, &inodes_free, &inodes_total) < 0)
        goto done;
    retval = 0;
done:
    if (name)
        free (name);
    if (ossname)
        free (ossname);
    return retval;
}

void
parse_utils (void)
{
    int i;
    const char *s[] = {
        "foo;bar;baz",
        "foo;bar;baz;",
    };

    for (i = 0; i < sizeof (s) / sizeof (s[0]); i++) {
        const char *p;

        p = strskip(s[i], 0, ';');
        printf ("strskip 0: %s\n", p == s[i] ? "OK" : "FAIL");
        p = strskip(s[i], 1, ';');
        printf ("strskip 1: %s\n", p == s[i]+4 ? "OK" : "FAIL");
        p = strskip(s[i], 2, ';');
        printf ("strskip 2: %s\n", p == s[i]+8 ? "OK" : "FAIL");
        p = strskip(s[i], 3, ';');
        printf ("strskip 3: %s\n", p == s[i]+strlen (s[i]) ? "OK" : "FAIL");
        p = strskip(s[i], 4, ';');
        printf ("strskip 4: %s\n", p ==  NULL ? "OK" : "FAIL");
    }
    for (i = 0; i < sizeof (s) / sizeof (s[0]); i++) {
        const char *q = s[i];
        char *p;

        /* FIXME: distinguish out of memory from end of string */
        while ((p = strskipcpy (&q, 1, ';'))) {
            printf ("strskipcpy 1: '%s'\n", p);
            free (p);
        }
        if (strlen (q) > 0)
            printf ("strskipcpy failed to consume entire string\n");
    }
    {
        char *p = strdup ("fubar");

        if (!p) {
            fprintf (stderr, "out of memory\n");
            exit (1);
        }
        if (!strappendfield (&p, "smurf", ';')) {
            fprintf (stderr, "out of memory\n");
            exit (1);
        }
        printf ("strappendfield: %s\n", p);
        free (p);
    }
    
}

void
parse_current (void)
{
}

void
parse_legacy (void)
{
    int n;

    n = _parse_oss_v1 (oss_v1_str);
    printf ("oss_v1: %s\n", n < 0 ? strerror (errno) : "OK");

    n = _parse_router_v1 (router_v1_str);
    printf ("router_v1: %s\n", n < 0 ? strerror (errno) : "OK");

    n = _parse_ost_v1 (ost_v1_str);
    printf ("ost_v1: %s\n", n < 0 ? strerror (errno) : "OK");

    n = _parse_mds_v2 (mds_v2_str);
    printf ("mds_v2: %s\n", n < 0 ? strerror (errno) : "OK");
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

