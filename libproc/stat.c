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
    if (n < 0)
        goto error;
    if (n < 7) {
        errno = EIO; 
        n = -1;
        goto error;
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
error:
    return  n;
}

/* Summarize proc_stat () values as two sums.  We can use these values in
 * a manner similar to top(1) to compute a percent utilization:
 * pct_util = abs (delta usage / delta total) * 100.0.
 */
int
proc_stat2 (pctx_t ctx, uint64_t *usagep, uint64_t *totalp)
{
    uint64_t usr, nice, sys, idle, iowait, irq, softirq;
    int n;

    n = proc_stat (ctx, &usr, &nice, &sys, &idle, &iowait, &irq, &softirq);
    if (n < 0)
        goto error;
    if (usagep)
        *usagep = usr + nice + sys + irq + softirq;
    if (totalp)
        *totalp = usr + nice + sys + idle + iowait + irq + softirq;
error:
    return  n;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

