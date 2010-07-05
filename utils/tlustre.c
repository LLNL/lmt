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

    if (!(itr = list_iterator_create (srvlist))) {
        perror ("list_iterator_create");
        exit (1);
    }
    while ((srv = list_next (itr))) {
        if (proc_lustre_files (ctx, srv, &f, &t) < 0) {
            perror (srv);
            exit (1);
        }
        printf ("%s: files free %"PRIu64" total %"PRIu64"\n", srv, f, t);

        if (proc_lustre_kbytes (ctx, srv, &f, &t) < 0) {
            perror (srv);
            exit (1);
        }
        printf ("%s: kbytes free %"PRIu64" total %"PRIu64"\n", srv, f, t);

        if (proc_lustre_uuid (ctx, srv, &uuid) < 0) {
            perror (srv);
            exit (1);
        }
        printf ("%s: uuid %s\n", srv, uuid);
        free (uuid);

        if (proc_lustre_hashstats (ctx, srv, &stats) < 0) {
            perror (srv);
            exit (1);
        }
        hash_for_each (stats, (hash_arg_f)print_keyval, srv);
        hash_destroy (stats);
    }
    list_iterator_destroy (itr);
}

void
print_lnet (pctx_t ctx)
{
    uint64_t newbytes;

    if (proc_lustre_lnet_newbytes (ctx, &newbytes) < 0) {
        perror ("proc_lustre_lnet_newbytes");
        exit (1);
    }
    printf ("lnet: newbytes = %"PRIu64"\n", newbytes);
}

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    List srvlist;

    if (!(ctx = proc_create ("/proc"))) {
        perror ("proc_create");
        exit (1);
    }

    printf( "OST data:\n");
    if (proc_lustre_ostlist (ctx, &srvlist) < 0)
        fprintf (stderr, "there are no active OST's\n");
    else {
        print_stats (ctx, srvlist);
        list_destroy (srvlist);
    }

    printf( "\nMDT data\n");
    if (proc_lustre_mdtlist (ctx, &srvlist) < 0)
        fprintf (stderr, "there are no active MDT's\n");
    else {
        print_stats (ctx, srvlist);
        list_destroy (srvlist);
    }

    printf( "\nLNET data:\n");
    print_lnet (ctx);

    proc_destroy (ctx);
    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

