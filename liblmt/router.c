/*****************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module (re)written by Jim Garlick <garlick@llnl.gov>.
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
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <errno.h>
#include <sys/utsname.h>
#include <stdint.h>
#include <math.h>

#include "list.h"
#include "hash.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"

#include "lmt.h"

#define LMT_ROUTER_PROTOCOL_VERSION    "3"

typedef struct {
    uint64_t    usage[2];
    uint64_t    total[2];
    int         valid;      /* number of valid samples [0,1,2] */
} usage_t;

static int
_get_cpu_usage (pctx_t ctx, double *fp)
{
    static usage_t u = { .valid = 0 };

    u.usage[0] = u.usage[1];
    u.total[0] = u.total[1];

    if (proc_stat2 (ctx, &u.usage[1], &u.total[1]) < 0) {
        if (u.valid > 0)
            u.valid--;
    } else {
        if (u.valid < 2)
            u.valid++;
    }
    if (u.valid == 2) {
        *fp = fabs ((double)(u.usage[1] - u.usage[0]) 
                  / (double)(u.total[1] - u.total[0])) * 100.0;
        return 0;
    }
    return -1;
}

static int
_get_mem_usage (pctx_t ctx, double *fp)
{
    uint64_t kfree, ktot;

    if (proc_meminfo (ctx, &ktot, &kfree) < 0)
        return -1;
    *fp = ((double)(ktot - kfree) / (double)(ktot)) * 100.0;
    return 0;
}

int
lmt_router_string_v3 (pctx_t ctx, char *s, int len)
{
    int retval = -1;
    struct utsname uts;
    double mempct, cpupct;
    uint64_t newbytes;
    int n, ena;

    if (proc_lustre_lnet_routing_enabled (ctx, &ena) < 0)
        goto done;
    if (!ena) {
        errno = 0;
        goto done;
    }
    if (uname (&uts) < 0)
        goto done;
    if (_get_cpu_usage (ctx, &cpupct) < 0)
        goto done;
    if (_get_mem_usage (ctx, &mempct) < 0)
        goto done;
    if (proc_lustre_lnet_newbytes (ctx, &newbytes) < 0)
        goto done;
    n = snprintf (s, len, "%s;%s;%f;%f;%lu",
                  LMT_ROUTER_PROTOCOL_VERSION,
                  uts.nodename,
                  cpupct,
                  mempct,
                  newbytes);
    if (n >= len) {
        errno = E2BIG;
        goto done;
    }

    retval = 0;
done:
    return retval;
}

int
lmt_router_updatedb_v3 (lmt_db_t hp, char *s)
{
    return 0;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
