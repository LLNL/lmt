/*****************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>.
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
#include <inttypes.h>
#include <math.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"

#include "lmt.h"
#include "ost.h"
#include "util.h"
#include "lmtconf.h"

static int
_get_mem_usage (pctx_t ctx, double *fp)
{
    uint64_t kfree, ktot;

    if (proc_meminfo (ctx, &ktot, &kfree) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading memory usage from proc");
        return -1;
    }
    *fp = ((double)(ktot - kfree) / (double)(ktot)) * 100.0;
    return 0;
}

/*
 * Read the "pages per bulk r/w"  histogram from the
 * brw_stats file.  Then sum rpc counts from all of the
 * histogram bins, including both reads and writes,
 * into a single number representing all bulk RPCs
 * that this target has handled.  ltop calls these
 * "IOPs".
 */
static int
_get_iops (pctx_t ctx, char *name, uint64_t *iopsp)
{
    histogram_t *h;
    int i;
    uint64_t iops = 0;

    if (proc_lustre_brwstats (ctx, name, BRW_RPC, &h) < 0)
        return -1;
    for (i = 0; i < h->bincount; i++)
        iops += h->bin[i].yr + h->bin[i].yw;
    histogram_destroy (h);
    *iopsp = iops;
    return 0;
}

static int
_get_recovstr (pctx_t ctx, char *name, char *s, int len)
{
    hash_t rh = NULL;
    shash_t *status, *completed_clients, *time_remaining;
    int res = -1;

    if (proc_lustre_hashrecov (ctx, name, &rh) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s recovery_status from proc", name);
        goto done;
    }
    if (!(status = hash_find (rh, "status:"))) {
        if (lmt_conf_get_proto_debug ())
            err ("error parsing lustre %s recovery_status from proc", name);
        goto done;
    }
    completed_clients = hash_find (rh, "completed_clients:");
    time_remaining = hash_find (rh, "time_remaining:");
    /* N.B. ltop depends on placement of status in the first field */
    snprintf (s, len, "%s %s %ss remaining", status->val,
              completed_clients ? completed_clients->val : "",
              time_remaining ? time_remaining->val : "0");
    res = 0;
done:
    if (rh)
        hash_destroy (rh);
    return res;
}

static int
_get_oststring_v2 (pctx_t ctx, char *name, char *s, int len)
{
    char *uuid = NULL;
    uint64_t filesfree, filestotal;
    uint64_t kbytesfree, kbytestotal;
    uint64_t read_bytes, write_bytes;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    hash_t stats_hash = NULL;
    int n, retval = -1;
    char recov_str[64];

    if (proc_lustre_uuid (ctx, name, &uuid) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s uuid from proc", name);
        goto done;
    }
    if (proc_lustre_hashstats (ctx, name, &stats_hash) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s stats from proc", name);
        goto done;
    }
    proc_lustre_parsestat (stats_hash, "read_bytes", NULL, NULL, NULL,
                           &read_bytes, NULL);
    proc_lustre_parsestat (stats_hash, "write_bytes", NULL, NULL, NULL,
                           &write_bytes, NULL);
    proc_lustre_parsestat (stats_hash, "connect", &connect, NULL, NULL,
                           NULL, NULL);
    proc_lustre_parsestat (stats_hash, "reconnect", &reconnect, NULL, NULL,
                           NULL, NULL);
    //proc_lustre_parsestat (stats_hash, "commitrw", &iops, NULL, NULL,
    //                       NULL, NULL);
    if (_get_iops (ctx, name, &iops) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s brw_stats", name);
        goto done;
    }
    if (proc_lustre_files (ctx, name, &filesfree, &filestotal) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s file stats from proc", name);
        goto done;
    }
    if (proc_lustre_kbytes (ctx, name, &kbytesfree, &kbytestotal) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s kbytes stats from proc", name);
        goto done;
    }
    if (proc_lustre_num_exports (ctx, name, &num_exports) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s num_exports stats from proc", name);
        goto done;
    }
    if (proc_lustre_ldlm_lock_count (ctx, name, &lock_count) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s ldlm lock_count from proc", name);
        goto done;
    }
    if (proc_lustre_ldlm_grant_rate (ctx, name, &grant_rate) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s ldlm grant_rate from proc", name);
        goto done;
    }
    if (proc_lustre_ldlm_cancel_rate (ctx, name, &cancel_rate) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s ldlm cancel_rate from proc", name);
        goto done;
    }
    if (_get_recovstr (ctx, name, recov_str, sizeof (recov_str)) < 0)
        goto done;
    n = snprintf (s, len, "%s;%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64
                  ";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64
                  ";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";%s;",
                  uuid, filesfree, filestotal, kbytesfree, kbytestotal,
                  read_bytes, write_bytes, iops, num_exports,
                  lock_count, grant_rate, cancel_rate,
                  connect, reconnect, recov_str);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        return -1;
    }
    retval = 0;
done:
    if (uuid)
        free (uuid);
    if (stats_hash)
        hash_destroy (stats_hash);
    return retval;
}

int
lmt_ost_string_v2 (pctx_t ctx, char *s, int len)
{
    static uint64_t cpuused = 0, cputot = 0;
    ListIterator itr = NULL;
    List ostlist = NULL;
    struct utsname uts;
    double cpupct, mempct;
    int used, n, retval = -1;
    char *name;

    if (proc_lustre_ostlist (ctx, &ostlist) < 0)
        goto done;
    if (list_count (ostlist) == 0) {
        errno = 0;
        goto done;
    }
    if (uname (&uts) < 0) {
        err ("uname");
        goto done;
    }
    if (proc_stat2 (ctx, &cpuused, &cputot, &cpupct) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading cpu usage from proc");
        goto done;
    }
    if (_get_mem_usage (ctx, &mempct) < 0)
        goto done;
    n = snprintf (s, len, "2;%s;%f;%f;",
                  uts.nodename,
                  cpupct,
                  mempct);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    itr = list_iterator_create (ostlist);
    while ((name = list_next (itr))) {
        used = strlen (s);
        if (_get_oststring_v2 (ctx, name, s + used, len - used) < 0)
            goto done;
    }
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (ostlist)
        list_destroy (ostlist);
    return retval;
}

int
lmt_ost_decode_v2 (const char *s, char **ossnamep, float *pct_cpup,
                   float *pct_memp, List *ostinfop)
{
    int retval = -1;
    char *ossname =  xmalloc (strlen(s) + 1);
    char *cpy = NULL;
    float pct_mem, pct_cpu;
    List ostinfo = list_create ((ListDelF)free);

    if (sscanf (s, "%*f;%[^;];%f;%f;", ossname, &pct_cpu, &pct_mem) != 3) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_ost_v2: parse error: oss component");
        goto done;
    }
    if (!(s = strskip (s, 4, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_ost_v2: parse error: skipping oss component");
        goto done;
    }
    while ((cpy = strskipcpy (&s, 15, ';')))
        list_append (ostinfo, cpy);
    if (strlen (s) > 0) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_ost_v2: parse error: string not exhausted");
        goto done;
    }
    *ossnamep = ossname;
    *pct_cpup = pct_cpu;
    *pct_memp = pct_mem;
    *ostinfop = ostinfo;
    retval = 0;
done:
    if (retval < 0) {
        free (ossname);
        list_destroy (ostinfo);
    }
    return retval;
}

int
lmt_ost_decode_v2_ostinfo (const char *s, char **ostnamep,
                           uint64_t *read_bytesp, uint64_t *write_bytesp,
                           uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                           uint64_t *inodes_freep, uint64_t *inodes_totalp,
                           uint64_t *iopsp, uint64_t *num_exportsp,
                           uint64_t *lock_countp, uint64_t *grant_ratep,
                           uint64_t *cancel_ratep,
                           uint64_t *connectp, uint64_t *reconnectp,
                           char **recov_statusp)
{
    int retval = -1;
    char *ostname = xmalloc (strlen (s) + 1);;
    char *recov_status = xmalloc (strlen (s) + 1);;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;

    if (sscanf (s,   "%[^;];%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64
                ";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64
                ";%"PRIu64";%"PRIu64";%[^;];",
                ostname, &inodes_free, &inodes_total, &kbytes_free,
                &kbytes_total, &read_bytes, &write_bytes, &iops, &num_exports,
                &lock_count, &grant_rate, &cancel_rate,
                &connect, &reconnect, recov_status) != 15) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_ost_v2: parse error: ostinfo");
        goto done;
    }
    *ostnamep = ostname;
    *read_bytesp = read_bytes;
    *write_bytesp = write_bytes;
    *kbytes_freep = kbytes_free;
    *kbytes_totalp = kbytes_total;
    *inodes_freep = inodes_free;
    *inodes_totalp = inodes_total;
    *iopsp = iops;
    *num_exportsp = num_exports;
    *lock_countp = lock_count;
    *grant_ratep = grant_rate;
    *cancel_ratep = cancel_rate;
    *connectp = connect;
    *reconnectp = reconnect;
    *recov_statusp = recov_status;
    retval = 0;
done:
    if (retval < 0) {
        free (ostname);
        free (recov_status);
    }
    return retval;
}

/**
 ** Legacy
 **/

int
lmt_oss_decode_v1 (const char *s, char **ossnamep, float *pct_cpup,
                   float *pct_memp)
{
    int retval = -1;
    char *ossname = xmalloc (strlen(s) + 1);
    float pct_mem, pct_cpu;

    if (sscanf (s, "%*f;%[^;];%f;%f", ossname, &pct_cpu, &pct_mem) != 3) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_oss_v1: parse error");
        goto done;
    }
    *ossnamep = ossname;
    *pct_cpup = pct_cpu;
    *pct_memp = pct_mem;
    retval = 0;
done:
    if (retval < 0)
        free (ossname);
    return retval;
}

/* N.B. Having multiple lmt_ost metric values per host works with the
 * cerebro monitor module, since it gets a callback every time a metric
 * arrives, but it doesn't work when iterating over the cerebro server's
 * stored metric values since the values are stored by hostname and therefore
 * overwrite each other.  That was the major reason in lmt-3.0 for introducing
 * lmt_ost_v2, a single oss+ost metric that embeds multiple ost values.
 *
 * Therefore, a caveat of continuing to support ost_v1 is that new tools
 * that iterate over the cerebro server metric values, as opposed to mysql
 * stored values, don't see all the OST data.
 */

int
lmt_ost_decode_v1 (const char *s, char **ossnamep, char **ostnamep,
                   uint64_t *read_bytesp, uint64_t *write_bytesp,
                   uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                   uint64_t *inodes_freep, uint64_t *inodes_totalp)
{
    int retval = -1;
    char *ostname = xmalloc (strlen (s) + 1);
    char *ossname = xmalloc (strlen (s) + 1);
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;

    if (sscanf (s, "%*f;%[^;];%[^;];%"PRIu64";%"PRIu64";%"PRIu64
                ";%"PRIu64";%"PRIu64";%"PRIu64,
                ossname, ostname, &inodes_free, &inodes_total,
                &kbytes_free, &kbytes_total, &read_bytes, &write_bytes) != 8) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_ost_v1: parse error");
        goto done;
    }
    *ossnamep = ossname;
    *ostnamep = ostname;
    *read_bytesp = read_bytes;
    *write_bytesp = write_bytes;
    *kbytes_freep = kbytes_free;
    *kbytes_totalp = kbytes_total;
    *inodes_freep = inodes_free;
    *inodes_totalp = inodes_total;

    retval = 0;
done:
    if (retval < 0) {
        free (ostname);
        free (ossname);
    }
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
