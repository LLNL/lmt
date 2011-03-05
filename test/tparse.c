/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>
 *  UCRL-CODE-232438
 *  All Rights Reserved.
 *
 *  This file is part of Lustre Monitoring Tool, version 2.
 *  Authors: H. Wartens, P. Spencer, N. O'Neill, J. Long, J. Garlick
 *  For details, see http://github.com/chaos/lmt.
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

/* tparse.c - test parsing of lmt metric strings */

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
#include <libgen.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"

#include "ost.h"
#include "mdt.h"
#include "osc.h"
#include "router.h"
#include "util.h"
#include "lmtconf.h"

const char *oss_v1_str =
    "1.0;tycho1;0.100000;98.810898";
const char *ost_v1_str =
    "1.0;tycho1;lc1-OST0000;121615156;122494976;"
    "1819998804;1929120176;19644389;289174933853";
const char *mds_v2_str =
    "2.0;tycho-mds2;lc2-MDT0000;0.000000;1.561927;"
    "31242342;31242480;124969368;125441296;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;1;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;3132344;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;48;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;48;0;0;0;0;0;0;0;0;0;"
    "0;0;0;0;0;217;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";

const char *router_v1_str =
    "1.0;alc42;0.100000;98.810898;1845066588";
const char *ost_v2_str = 
    "2;tycho1;0.100000;98.810898;"
    "lc1-OST0000;15156;976;99880;116;18;28;42;128;2;1;1;1;1;COMPLETED 100/100;"
    "lc1-OST0008;15156;976;99880;116;18;28;42;128;1;1;1;1;1;COMPLETED 1/1;"
    "lc1-OST0010;15156;976;99880;116;18;28;42;128;0;1;1;1;1;RECOVERING 1/1009";
const char *mdt_v1_str =
    "1;tycho-mds2;0.000000;1.561927;"
    "lc1-MDT0000;413253193;467523892;1653012772;1688473892;"
    "68;0;0;8;0;0;0;0;0;0;0;0;0;0;0;5;0;0;0;0;0;0;0;0;0;0;0;2;0;0;141790;0;0;"
    "8205730;0;0;143656;0;0;882908;0;0;4;0;0;98;0;0;4;0;0;5022;0;0;96;0;0;499;"
    "0;0;0;0;0;"
    "lc2-MDT0000;413253193;467523892;1653012772;1688473892;"
    "68;0;0;8;0;0;0;0;0;0;0;0;0;0;0;5;0;0;0;0;0;0;0;0;0;0;0;2;0;0;141790;0;0;"
    "8205730;0;0;143656;0;0;882908;0;0;4;0;0;98;0;0;4;0;0;5022;0;0;96;0;0;499;"
    "0;0;0;0;0";
const char *osc_v1_str =
    "1;tycho-mds1;"
    "lc1-OST0042_UUID;FULL;lc1-OST005b_UUID;FULL;lc1-OST0015_UUID;FULL;"
    "lc1-OST0043_UUID;FULL;lc1-OST0044_UUID;FULL;lc1-OST005a_UUID;FULL;"
    "lc1-OST0046_UUID;FULL;lc1-OST0014_UUID;FULL;lc1-OST0047_UUID;FULL;"
    "lc1-OST005f_UUID;FULL;lc1-OST0016_UUID;FULL;lc1-OST002e_UUID;FULL;"
    "lc1-OST0059_UUID;FULL;lc1-OST0045_UUID;FULL;lc1-OST005e_UUID;FULL;"
    "lc1-OST0011_UUID;FULL;lc1-OST005c_UUID;FULL;lc1-OST002a_UUID;FULL;"
    "lc1-OST005d_UUID;FULL;lc1-OST0058_UUID;FULL;lc1-OST0010_UUID;FULL;"
    "lc1-OST0012_UUID;FULL;lc1-OST002f_UUID;FULL;lc1-OST002b_UUID;FULL;"
    "lc1-OST0040_UUID;FULL;lc1-OST0041_UUID;FULL;lc1-OST002c_UUID;FULL;"
    "lc1-OST002d_UUID;FULL;lc1-OST0029_UUID;FULL;lc1-OST0028_UUID;FULL;"
    "lc1-OST0013_UUID;FULL;lc1-OST0017_UUID;FULL;lc1-OST003c_UUID;FULL;"
    "lc1-OST003b_UUID;FULL;lc1-OST003a_UUID;FULL;lc1-OST0053_UUID;FULL;"
    "lc1-OST000d_UUID;FULL;lc1-OST003e_UUID;FULL;lc1-OST0052_UUID;FULL;"
    "lc1-OST000e_UUID;FULL;lc1-OST003f_UUID;FULL;lc1-OST0057_UUID;FULL;"
    "lc1-OST000c_UUID;FULL;lc1-OST0026_UUID;FULL;lc1-OST0051_UUID;FULL;"
    "lc1-OST0009_UUID;FULL;lc1-OST0022_UUID;FULL;lc1-OST003d_UUID;FULL;"
    "lc1-OST0055_UUID;FULL;lc1-OST0056_UUID;FULL;lc1-OST0054_UUID;FULL;"
    "lc1-OST000a_UUID;FULL;lc1-OST0023_UUID;FULL;lc1-OST0050_UUID;FULL;"
    "lc1-OST0027_UUID;FULL;lc1-OST0008_UUID;FULL;lc1-OST0038_UUID;FULL;"
    "lc1-OST0024_UUID;FULL;lc1-OST0025_UUID;FULL;lc1-OST0039_UUID;FULL;"
    "lc1-OST0020_UUID;FULL;lc1-OST0021_UUID;FULL;lc1-OST000b_UUID;FULL;"
    "lc1-OST000f_UUID;FULL;lc1-OST0032_UUID;FULL;lc1-OST004a_UUID;FULL;"
    "lc1-OST0033_UUID;FULL;lc1-OST0036_UUID;FULL;lc1-OST0006_UUID;FULL;"
    "lc1-OST004b_UUID;FULL;lc1-OST004f_UUID;FULL;lc1-OST0034_UUID;FULL;"
    "lc1-OST0037_UUID;FULL;lc1-OST0035_UUID;FULL;lc1-OST0001_UUID;FULL;"
    "lc1-OST0049_UUID;FULL;lc1-OST001e_UUID;FULL;lc1-OST001a_UUID;FULL;"
    "lc1-OST0005_UUID;FULL;lc1-OST004e_UUID;FULL;lc1-OST004c_UUID;FULL;"
    "lc1-OST004d_UUID;FULL;lc1-OST0002_UUID;FULL;lc1-OST001b_UUID;FULL;"
    "lc1-OST0004_UUID;FULL;lc1-OST001f_UUID;FULL;lc1-OST0031_UUID;FULL;"
    "lc1-OST0048_UUID;FULL;lc1-OST0030_UUID;FULL;lc1-OST001c_UUID;FULL;"
    "lc1-OST0019_UUID;FULL;lc1-OST001d_UUID;FULL;lc1-OST0000_UUID;FULL;"
    "lc1-OST0003_UUID;FULL;lc1-OST0018_UUID;FULL;lc1-OST0007_UUID;FULL";

void parse_utils (void);
void parse_current (void);
void parse_current_short (void);
void parse_current_long (void);
void parse_legacy (void);

int
main (int argc, char *argv[])
{
    err_init (argv[0]);
    lmt_conf_init (1, NULL);

    lmt_conf_set_proto_debug (1);

    parse_utils ();
    parse_legacy ();
    parse_current ();
    parse_current_short ();
    parse_current_long ();
    exit (0);
}

int
_parse_osc_v1 (const char *s)
{
    int retval = -1;
    char *mdsname = NULL;
    char *ostname, *state;
    List oscinfo = NULL;
    ListIterator itr = NULL;
    char *s2;

    if (lmt_osc_decode_v1 (s, &mdsname, &oscinfo) < 0)
        goto done;
    itr = list_iterator_create (oscinfo);
    while ((s2 = list_next (itr))) {
        if (lmt_osc_decode_v1_oscinfo (s2, &ostname, &state) < 0)
            goto done;
        free (ostname);
        free (state);
    }
    retval = 0;
done:
    if (mdsname)
        free (mdsname);
    if (itr)
        list_iterator_destroy (itr);
    if (oscinfo)
        list_destroy (oscinfo);
    return retval;
}


int
_parse_ost_v2 (const char *s)
{
    int retval = -1;
    char *ossname = NULL;
    char *ostname = NULL;
    char *recov_status = NULL;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t inodes_free, inodes_total;
    uint64_t kbytes_free, kbytes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    List ostinfo = NULL;
    ListIterator itr = NULL;
    char *osi;

    if (lmt_ost_decode_v2 (s, &ossname, &pct_cpu, &pct_mem, &ostinfo) < 0)
        goto done;
    if (!(itr = list_iterator_create (ostinfo)))
        goto done;
    while ((osi = list_next (itr))) {
        if (lmt_ost_decode_v2_ostinfo (osi, &ostname, &read_bytes, &write_bytes,
                                       &kbytes_free, &kbytes_total,
                                       &inodes_free, &inodes_total, &iops,
                                       &num_exports, &lock_count, &grant_rate,
                                       &cancel_rate, &connect, &reconnect,
                                       &recov_status) < 0)
            goto done;
        free (ostname);
        free (recov_status);
    }
    retval = 0;
done:
    if (ossname)
        free (ossname);
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
_parse_mdt_v1 (const char *s)
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
    if (!(itr = list_iterator_create (mdtinfo)))
        goto done;
    while ((mdi = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdtinfo (mdi, &mdtname, &inodes_free,
                     &inodes_total, &kbytes_free, &kbytes_total, &mdops) < 0)
            goto done;
        free (mdtname);
        if (_parse_mdt_v1_mdops (mdops) < 0) {
            list_destroy (mdops); 
            goto done;
        }
        list_destroy (mdops); 
    }
    retval = 0;
done:
    if (mdsname)
        free (mdsname);
    if (itr)
        list_iterator_destroy (itr);
    if (mdtinfo)
        list_destroy (mdtinfo);
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
        msg ("strskip 0: %s", p == s[i] ? "OK" : "FAIL");
        p = strskip(s[i], 1, ';');
        msg ("strskip 1: %s", p == s[i]+4 ? "OK" : "FAIL");
        p = strskip(s[i], 2, ';');
        msg ("strskip 2: %s", p == s[i]+8 ? "OK" : "FAIL");
        p = strskip(s[i], 3, ';');
        msg ("strskip 3: %s", p == s[i]+strlen (s[i]) ? "OK" : "FAIL");
        p = strskip(s[i], 4, ';');
        msg ("strskip 4: %s", p ==  NULL ? "OK" : "FAIL");
    }
    for (i = 0; i < sizeof (s) / sizeof (s[0]); i++) {
        const char *q = s[i];
        char *p;

        while ((p = strskipcpy (&q, 1, ';'))) {
            msg ("strskipcpy 1: '%s'", p);
            free (p);
        }
        if (strlen (q) > 0)
            msg ("strskipcpy failed to consume entire string");
    }
    {
        char *p = xstrdup ("fubar");

        strappendfield (&p, "smurf", ';');
        msg ("strappendfield: %s", p);
        free (p);
    }
    
}

void
parse_current_short (void)
{
    int n;
    char *mdt_v1_str_short = xstrdup (mdt_v1_str);
    char *ost_v2_str_short = xstrdup (ost_v2_str);

    mdt_v1_str_short[strlen (mdt_v1_str_short) - 35] = '\0';
    n = _parse_mdt_v1 (mdt_v1_str_short);
    msg ("mdt_v1(truncated): %s", n < 0 ? "FAIL" : "OK");

    ost_v2_str_short[strlen (ost_v2_str_short) - 35] = '\0';
    n = _parse_ost_v2 (ost_v2_str_short);
    msg ("ost_v2(truncated): %s", n < 0 ? "FAIL" : "OK");

    free (mdt_v1_str_short);
    free (ost_v2_str_short);
}

void
parse_current_long (void)
{
    int n;
    int mdtlen = strlen (mdt_v1_str) + 36;
    int ostlen = strlen (ost_v2_str) + 36;
    char *mdt_v1_str_long = xmalloc (mdtlen);
    char *ost_v2_str_long = xmalloc (ostlen);

    snprintf (mdt_v1_str_long, mdtlen, "%ssdfdfsdlafwererefsdf", mdt_v1_str);
    n = _parse_mdt_v1 (mdt_v1_str_long);
    msg ("mdt_v1(elongated): %s", n < 0 ? "FAIL" : "OK");

    /* we're too dumb to detect this case, oh well */
    snprintf (ost_v2_str_long, ostlen, "%ssdfdfsdlafwererefsdf", ost_v2_str);
    n = _parse_ost_v2 (ost_v2_str_long);
    msg ("ost_v2(elongated): %s", n < 0 ? "FAIL" : "OK");

    free (mdt_v1_str_long);
    free (ost_v2_str_long);
}

void
parse_current (void)
{
    int n;

    n = _parse_mdt_v1 (mdt_v1_str);
    msg ("mdt_v1: %s", n < 0 ? "FAIL" : "OK");
    n = _parse_ost_v2 (ost_v2_str);
    msg ("ost_v2: %s", n < 0 ? "FAIL" : "OK");
    n = _parse_osc_v1 (osc_v1_str);
    msg ("osc_v1: %s", n < 0 ? "FAIL" : "OK");
}

void
parse_legacy (void)
{
    int n;

    n = _parse_oss_v1 (oss_v1_str);
    msg ("oss_v1: %s", n < 0 ? "FAIL" : "OK");

    n = _parse_router_v1 (router_v1_str);
    msg ("router_v1: %s", n < 0 ? "FAIL" : "OK");

    n = _parse_ost_v1 (ost_v1_str);
    msg ("ost_v1: %s", n < 0 ? "FAIL" : "OK");

    n = _parse_mds_v2 (mds_v2_str);
    msg ("mds_v2: %s", n < 0 ? "FAIL" : "OK");
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

