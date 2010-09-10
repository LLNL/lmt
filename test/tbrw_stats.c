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
#include "lustre.h"

void
dump_brw_stats (pctx_t ctx, char *name, brw_t t, char *desc)
{
    histogram_t *h;
    int i;

    if (proc_lustre_brwstats (ctx, name, t, &h) < 0)
        err_exit ("error reading %s", desc);
    msg ("%s", desc);
    for (i = 0; i < h->bincount; i++)
        msg ("%lu: %lu, %lu", h->bin[i].x, h->bin[i].yr, h->bin[i].yw);
    histogram_destroy (h); 
}

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    List srvlist;
    ListIterator itr;
    char *name;

    err_init (argv[0]);
    if (argc != 2)
        msg_exit ("missing proc argument");

    ctx = proc_create (argv[1]);

    if (proc_lustre_ostlist (ctx, &srvlist) < 0)
        err_exit ("error looking for ost's");
    if (list_count (srvlist) == 0)
        msg_exit ("no ost information found");
    itr = list_iterator_create (srvlist);
    while ((name = list_next (itr))) {
        dump_brw_stats (ctx, name, BRW_RPC, "pages per bulk r/w");
        dump_brw_stats (ctx, name, BRW_DISPAGES, "discontiguous pages");
        dump_brw_stats (ctx, name, BRW_DISBLOCKS, "discontiguous blocks");
        dump_brw_stats (ctx, name, BRW_FRAG, "disk fragmented I/Os");
        dump_brw_stats (ctx, name, BRW_FLIGHT, "disk I/Os in flight");
        dump_brw_stats (ctx, name, BRW_IOTIME, "I/O time (1/1000s)");
        dump_brw_stats (ctx, name, BRW_IOSIZE, "disk I/O size");
    }
    list_iterator_destroy (itr);

    list_destroy (srvlist);
    proc_destroy (ctx);

    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

