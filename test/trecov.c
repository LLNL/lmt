/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module was written by Jim Garlick <garlick@llnl.gov>
 *  UCRL-CODE-232438 All Rights Reserved.
 *
 *  This file is part of the Lustre Monitoring Tool.
 *  For details, see http://github.com/chaos/lmt.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the license, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the terms and conditions of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see
 *  http://www.gnu.org/licenses.
 *****************************************************************************/

/* trecov.c - test parsing of lustre recovery_status file */

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

int
print_keyval (shash_t *s, char *key, char *name)
{
    msg ("%s: %s='%s'", name, key, s->val);
    return 0;
}

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    List srvlist;
    ListIterator itr;
    char *name;
    hash_t stats;

    err_init (argv[0]);
    if (argc != 2)
        msg_exit ("missing proc argument");

    ctx = proc_create (argv[1]);

    if (proc_lustre_ostlist (ctx, &srvlist) < 0)
        err_exit ("error looking for ost's");
    itr = list_iterator_create (srvlist);
    while ((name = list_next (itr))) {
        msg ("ost: %s", name);
        if (proc_lustre_hashrecov (ctx, name, &stats) < 0)
            err_exit ("proc_lustre_hashrecov: failed");
        hash_for_each (stats, (hash_arg_f)print_keyval, name);
        hash_destroy (stats);
    }
    list_iterator_destroy (itr);
    list_destroy (srvlist);

    if (proc_lustre_mdtlist (ctx, &srvlist) < 0)
        err_exit ("error looking for mdt's");
    itr = list_iterator_create (srvlist);
    while ((name = list_next (itr))) {
        msg ("mdt: %s", name);
        if (proc_lustre_hashrecov (ctx, name, &stats) < 0)
            err_exit ("proc_lustre_hashrecov: failed");
        hash_for_each (stats, (hash_arg_f)print_keyval, name);
        hash_destroy (stats);
    }
    list_iterator_destroy (itr);
    list_destroy (srvlist);

    proc_destroy (ctx);

    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
