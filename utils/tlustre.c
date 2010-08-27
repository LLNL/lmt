/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module by Jim Garlick <garlick@llnl.gov>
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
#include <inttypes.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"


int
print_keyval (shash_t *s, char *key, char *name)
{
    printf ("%s: %s='%s'\n", name, key, s->val);
    return 0;
}

void
print_stats (pctx_t ctx, List srvlist)
{
    ListIterator itr;
    char *srv, *uuid;
    hash_t stats;
    uint64_t f, t;

    itr = list_iterator_create (srvlist);
    while ((srv = list_next (itr))) {
        if (proc_lustre_files (ctx, srv, &f, &t) < 0)
            err_exit ("%s: proc_lustre_files", srv);
        msg ("%s: files free %"PRIu64" total %"PRIu64, srv, f, t);

        if (proc_lustre_kbytes (ctx, srv, &f, &t) < 0)
            err_exit ("%s: proc_lustre_kbytes", srv);
        msg ("%s: kbytes free %"PRIu64" total %"PRIu64, srv, f, t);

        if (proc_lustre_uuid (ctx, srv, &uuid) < 0)
            err_exit ("%s: proc_lustre_uuid", srv);
        msg ("%s: uuid %s", srv, uuid);
        free (uuid);

        if (proc_lustre_hashstats (ctx, srv, &stats) < 0)
            err_exit ("%s: proc_lustre_hashstats", srv);
        hash_for_each (stats, (hash_arg_f)print_keyval, srv);
        hash_destroy (stats);

        if (proc_lustre_hashrecov (ctx, srv, &stats) < 0)
            err_exit ("%s: proc_lustre_hashrecov", srv);
        hash_for_each (stats, (hash_arg_f)print_keyval, srv);
        hash_destroy (stats);
    }
    list_iterator_destroy (itr);
}

void
print_osc (pctx_t ctx, List srvlist)
{
    ListIterator itr;
    char *srv, *uuid, *state;

    itr = list_iterator_create (srvlist);
    while ((srv = list_next (itr))) {
        /* will fail on clients */
        if (proc_lustre_oscinfo (ctx, srv, &uuid, &state) < 0)
            continue;
        msg ("%s: %s: %s", srv, uuid, state);
        free (uuid);
        free (state);
    }
    list_iterator_destroy (itr);
}

void
print_lnet (pctx_t ctx)
{
    uint64_t newbytes;

    if (proc_lustre_lnet_newbytes (ctx, &newbytes) < 0)
        err_exit ("proc_lustre_lnet_newbytes");
    msg ("lnet: newbytes = %"PRIu64"", newbytes);
}

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    List srvlist;

    err_init (argv[0]);

    ctx = proc_create ("/proc");

    printf ("OST data:\n");
    if (proc_lustre_ostlist (ctx, &srvlist) < 0)
        msg ("there are no active OST's");
    else {
        print_stats (ctx, srvlist);
        list_destroy (srvlist);
    }

    printf ("\nMDT data\n");
    if (proc_lustre_mdtlist (ctx, &srvlist) < 0)
        msg ("there are no active MDT's");
    else {
        print_stats (ctx, srvlist);
        list_destroy (srvlist);
    }

    printf ("\nLNET data:\n");
    print_lnet (ctx);

    printf ("\nOSC data\n");
    if (proc_lustre_osclist (ctx, &srvlist) < 0)
        msg ("there are no active (mds style) OSC's");
    else {
        print_osc (ctx, srvlist);
        list_destroy (srvlist);
    }

    proc_destroy (ctx);
    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

