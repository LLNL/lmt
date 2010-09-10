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

#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <stdint.h>
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
main (int argc, char *argv[])
{
    pctx_t ctx;
    uint64_t usage, total;
    uint64_t ousage, ototal;
    uint64_t ktot, kfree;

    if (!(ctx = proc_create ("/proc"))) {
        perror ("proc_create");
        exit (1);
    }

    if (proc_stat2 (ctx, &usage, &total) < 0) {
        perror ("proc_stat2");
        exit (1);
    }
    while (1) {
        sleep (1);
        ousage = usage;
        ototal = total;
        if (proc_stat2 (ctx, &usage, &total) < 0) {
            perror ("proc_stat2");
            exit (1);
        }
        if (proc_meminfo (ctx, &ktot, &kfree) < 0) {
            perror ("proc_meminfo");
            exit (1);
        }
        printf ("cpu=%.2f%% memory=%.2f%%\n",
            fabs ((float)(usage - ousage) / (float)(total - ototal)) * 100.0,
            (float)(ktot - kfree)/ktot * 100.0);
    }

    proc_destroy (ctx);
    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

