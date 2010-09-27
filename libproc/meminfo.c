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

#include <errno.h>
#include <inttypes.h>

#include "proc.h"
#include "meminfo.h"

#define PROC_MEMINFO   "meminfo"

/* Return MemTotal and MemFree values (kilobytes) which can be used to
 * compute a percentage of memory in use.
 */
int
proc_meminfo (pctx_t ctx, uint64_t *ktotp, uint64_t *kfreep)
{
    uint64_t ktot, kfree;
    int n, ret = -1;

    n = proc_scanf (ctx, PROC_MEMINFO,
                    " MemTotal: %"PRIu64" kB MemFree: %"PRIu64" kB",
                    &ktot, &kfree);
    if (n < 0)
        goto error;
    if (n != 2) {
        errno = EIO; 
        goto error;
    }
    ret = 0;
    if (ktotp)
        *ktotp = ktot;
    if (kfreep)
        *kfreep = kfree;
error:
    return ret;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

