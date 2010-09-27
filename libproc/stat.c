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
#include "stat.h"

#define PROC_STAT   "stat"

/* Read the first line out of /proc/stat (aggregate cpu stats):
 *    cpu <usr> <nice> <sys> <idle> <iowait> <irq> <softirq> 0
 * See proc(5) for more info.
 */
int
proc_stat (pctx_t ctx, uint64_t *usrp, uint64_t *nicep, uint64_t *sysp,
           uint64_t *idlep, uint64_t *iowaitp, uint64_t *irqp,
           uint64_t *softirqp)
{
    uint64_t usr, nice, sys, idle, iowait, irq, softirq;
    int n;

    n = proc_scanf (ctx, PROC_STAT, " cpu%*[ ] %"PRIu64" %"PRIu64" %"PRIu64
                    " %"PRIu64" %"PRIu64" %"PRIu64" %"PRIu64"",
                    &usr, &nice, &sys, &idle, &iowait, &irq, &softirq);
    if (n < 0) {
        /* errno already set */
        return -1;
    }
    if (n != 7) {
        errno = EIO; 
        return -1;
    }
    if (usrp)
        *usrp = usr;
    if (nicep)
        *nicep = nice;
    if (sysp)
        *sysp = sys;
    if (idlep)
        *idlep = idle;
    if (iowaitp)
        *iowaitp = iowait;
    if (irqp)
        *irqp = irq;
    if (softirqp)
        *softirqp = softirq;
    return 0;
}

/* Compute percent utilization.
 * Initialize usage and total to zero, and first call will return percent
 * cpu utilization since boot.  Subsequent calls will return percent cpu
 * utilization since last call.
 */
int
proc_stat2 (pctx_t ctx, uint64_t *usagep, uint64_t *totalp, double *pctp)
{
    uint64_t usr, nice, sys, idle, iowait, irq, softirq;
    uint64_t usage, ousage = usagep ? *usagep : 0;
    uint64_t total, ototal = totalp ? *totalp : 0;

    if (proc_stat (ctx, &usr, &nice, &sys, &idle, &iowait, &irq, &softirq) < 0)
        return -1;
    usage = usr + nice + sys + irq + softirq;
    total = usr + nice + sys + idle + iowait + irq + softirq;

    if (pctp) {
        if (total - ototal > 0)
            *pctp = (double)(usage - ousage) / (total - ototal) * 100.0; 
        else
            *pctp = 0;
    }
    if (usagep)
        *usagep = usage;
    if (totalp)
        *totalp = total;
    return 0;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

