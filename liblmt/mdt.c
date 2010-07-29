/*****************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>.
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
#include <inttypes.h>
#include <math.h>

#include "list.h"
#include "hash.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"
#include "error.h"

#include "lmt.h"
#include "mdt.h"
#include "util.h"
#include "lmtconf.h"

typedef struct {
    int num;
    char *name;
} optab_t;

/* This is the hardwired order of ops in both mdt_v1 and mds_v2 (count=81)
 * FIXME: needs audit for validity of all fields in current lustre code,
 * and relevance to monitoring goals.  Preserve this list for legacy support.
 */
static const char *optab[] = {
    "open",
    "close",
    "mknod",
    "link",
    "unlink",
    "mkdir",
    "rmdir",
    "rename",
    "getxattr",
    "setxattr",
    "iocontrol",
    "get_info",
    "set_info_async",
    "attach",
    "detach",
    "setup",
    "precleanup",
    "cleanup",
    "process_config",
    "postrecov",
    "add_conn",
    "del_conn",
    "connect",
    "reconnect",
    "disconnect",
    "statfs",
    "statfs_async",
    "packmd",
    "unpackmd",
    "checkmd",
    "preallocate",
    "precreate",
    "create",
    "destroy",
    "setattr",
    "setattr_async",
    "getattr",
	"getattr_async",
	"brw",
	"brw_async",
	"prep_async_page",
	"reget_short_lock",
	"release_short_lock",
	"queue_async_io",
	"queue_group_io",
	"trigger_group_io",
	"set_async_flags",
	"teardown_async_page",
	"merge_lvb",
	"adjust_kms",
	"punch",
	"sync",
	"migrate",
	"copy",
	"iterate",
	"preprw",
	"commitrw",
    "enqueue",
	"match",
	"change_cbdata",
	"cancel",
	"cancel_unused",
	"join_lru",
	"init_export",
	"destroy_export",
	"extent_calc",
	"llog_init",
	"llog_finish",
	"pin",
	"unpin",
	"import_event",
	"notify",
	"health_check",
	"quotacheck",
	"quotactl",
	"quota_adjust_quint",
	"ping",
	"register_page_removal_cb",
	"unregister_page_removal_cb",
	"register_lock_cancel_cb",
	"unregister_lock_cancel_cb",
};
const int optablen = sizeof (optab) / sizeof(optab[0]);

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
        if (lmt_conf_get_proto_debug ())
            err ("error reading cpu usage from proc");
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

    if (proc_meminfo (ctx, &ktot, &kfree) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading memory usage from proc");
        return -1;
    }
    *fp = ((double)(ktot - kfree) / (double)(ktot)) * 100.0;
    return 0;
}

static int
_get_mdtop (hash_t stats, const char *key, char *s, int len)
{
    uint64_t count = 0, sum = 0, sumsq = 0;
    int retval = -1;
    int n;

    (void)proc_lustre_parsestat (stats, key, &count, NULL, NULL, &sum, &sumsq);
    n = snprintf (s, len, "%"PRIu64";%"PRIu64";%"PRIu64";", count, sum, sumsq);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    retval = 0;
done:
    return retval;
}

static int
_get_mdtstring (pctx_t ctx, char *name, char *s, int len)
{
    uint64_t filesfree, filestotal;
    uint64_t kbytesfree, kbytestotal;
    char *uuid = NULL;
    hash_t stats = NULL;
    int i, used, n, retval = -1;

    if (proc_lustre_uuid (ctx, name, &uuid) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s uuid from proc", name);
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
    if (proc_lustre_hashstats (ctx, name, &stats) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s stats from proc", name);
        goto done;
    }
    n = snprintf (s, len, "%s;%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";",
                  uuid, filesfree, filestotal, kbytesfree, kbytestotal);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    /* Add count, sum, and sumsquare values to the mdtstring for each op,
     * as required by schema 1.1.  Substitute zeroes if not found.
     * N.B. lustre-1.8.2: sum and sumsquare appear to be missing from proc.
     */
    for (i = 0; i < optablen; i++) {
        used = strlen (s);
        if (_get_mdtop (stats, optab[i], s + used, len - used) < 0)
            goto done;
    }
    retval = 0;
done: 
    if (uuid)
        free (uuid);
    if (stats)
        hash_destroy (stats);
    return retval;
}

int
lmt_mdt_string_v1 (pctx_t ctx, char *s, int len)
{
    struct utsname uts;
    int n, used, retval = -1;
    double mempct, cpupct;
    List mdtlist = NULL;
    ListIterator itr = NULL;
    char *name;

    if (proc_lustre_mdtlist (ctx, &mdtlist) < 0)
        goto done;
    if (list_count (mdtlist) == 0) {
        errno = 0;
        goto done;
    }
    if (uname (&uts) < 0) {
        err ("uname");
        goto done;
    }
    if (_get_cpu_usage (ctx, &cpupct) < 0) {
        goto done;
    }
    if (_get_mem_usage (ctx, &mempct) < 0) {
        goto done;
    }
    n = snprintf (s, len, "1;%s;%f;%f;", uts.nodename, cpupct, mempct);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    itr = list_iterator_create (mdtlist);
    while ((name = list_next (itr))) {
        used = strlen (s);
        if (_get_mdtstring (ctx, name, s + used, len - used) < 0)
            goto done;
    }
    if (s[strlen (s) - 1] == ';') /* chomp traling semicolon */
        s[strlen (s) - 1] = '\0';
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (mdtlist)
        list_destroy (mdtlist);
    return retval;
}

int
lmt_mdt_decode_v1 (const char *s, char **mdsnamep, float *pct_cpup,
                   float *pct_memp, List *mdtinfop)
{
    const int mdtfields = 5 + 3 * optablen;
    int retval = -1;
    char *mdsname = xmalloc (strlen(s) + 1);
    char *cpy = NULL;
    float pct_mem, pct_cpu;
    List mdtinfo = list_create ((ListDelF)free);

    if (sscanf (s, "%*f;%[^;];%f;%f;", mdsname, &pct_cpu, &pct_mem) != 3) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: mdsinfo");
        goto done;
    }
    if (!(s = strskip (s, 4, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: skipping mdsinfo");
        goto done;
    }
    mdtinfo = list_create ((ListDelF)free);
    while ((cpy = strskipcpy (&s, mdtfields, ';')))
        list_append (mdtinfo, cpy);
    if (strlen (s) > 0) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: string not exhausted");
        goto done;
    }
    *mdsnamep = mdsname;
    *pct_cpup = pct_cpu;
    *pct_memp = pct_mem;
    *mdtinfop = mdtinfo;
    retval = 0;
done:
    if (retval < 0) {
        free (mdsname);
        list_destroy (mdtinfo);
    }
    return retval;
}

int
lmt_mdt_decode_v1_mdtinfo (const char *s, char **mdtnamep,
                           uint64_t *inodes_freep, uint64_t *inodes_totalp,
                           uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                           List *mdopsp)
{
    int retval = -1;
    char *mdtname = xmalloc (strlen(s) + 1);
    char *cpy = NULL;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    List mdops = list_create ((ListDelF)free);
    int i = 0;

    if (sscanf (s, "%[^;];%"PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";",
                mdtname, &inodes_free, &inodes_total,
                &kbytes_free, &kbytes_total) != 5) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: mdtinfo");
        goto done;
    }
    if (!(s = strskip (s, 5, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: skipping mdtinfo");
        goto done;
    }
    while ((cpy = strskipcpy (&s, 3, ';'))) {
        if (i >= optablen) {
            if (lmt_conf_get_proto_debug ())
                msg ("lmt_mdt_v1: parse error: operation out of range");
            free (cpy);
            goto done;
        }
        strappendfield (&cpy, optab[i++], ';');
        list_append (mdops, cpy);
    }
    if (strlen (s) > 0) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: mdtinfo: string not exhausted");
        goto done;
    }
    *mdtnamep = mdtname;
    *inodes_freep = inodes_free;
    *inodes_totalp = inodes_total;
    *kbytes_freep = kbytes_free;
    *kbytes_totalp = kbytes_total;
    *mdopsp = mdops;
    retval = 0;
done:
    if (retval < 0) {
        free (mdtname);
        list_destroy (mdops);
    }
    return retval;
}

/* N.B. This function is doing double duty as lmt_mds_decode_v2_mdops.
 */
int
lmt_mdt_decode_v1_mdops (const char *s, char **opnamep, uint64_t *samplesp,
                         uint64_t *sump, uint64_t *sumsquaresp)
{
    int retval = -1;
    char *opname = xmalloc (strlen(s) + 1);
    uint64_t samples, sum, sumsquares;

    if (sscanf(s, "%"PRIu64";%"PRIu64";%"PRIu64";%[^;]",
               &samples, &sum, &sumsquares, opname) != 4) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mdt_v1: parse error: mdops");
        goto done;
    }
    *opnamep = opname;
    *samplesp = samples;
    *sump = sum;
    *sumsquaresp = sumsquares;
    retval = 0;
done:
    if (retval < 0)
        free (opname);
    return retval;
}

/**
 ** Legacy
 **/

int lmt_mds_decode_v2 (const char *s, char **mdsnamep, char **namep,
                       float *pct_cpup, float *pct_memp,
                       uint64_t *inodes_freep, uint64_t *inodes_totalp,
                       uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                       List *mdopsp)
{
    int i = 0, retval = -1;
    char *name = xmalloc (strlen(s) + 1);
    char *cpy = NULL;
    char *mdsname = xmalloc (strlen(s) + 1);
    float pct_mem, pct_cpu;
    List mdops = list_create ((ListDelF)free);
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;

    if (sscanf (s, "%*f;%[^;];%[^;];%f;%f;%"
                PRIu64";%"PRIu64";%"PRIu64";%"PRIu64";", mdsname, name,
                &pct_cpu, &pct_mem, &inodes_free, &inodes_total,
                &kbytes_free, &kbytes_total) != 8) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mds_v2: parse error: mds component");
        goto done;
    }
    if (!(s = strskip (s, 9, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_mds_v2: parse error: skipping mds component");
        goto done;
    }
    while ((cpy = strskipcpy (&s, 3, ';'))) {
        if (i >= optablen) {
            if (lmt_conf_get_proto_debug ())
                msg ("lmt_mds_v2: parse error: operation out of range");
            free (cpy);
            goto done;
        }
        strappendfield (&cpy, optab[i++], ';');
        if (!list_append (mdops, cpy)) {
            free (cpy);
            goto done;
        }
    }
    if (strlen (s) > 0) {
        msg ("lmt_mds_v2: parse error: string not exhausted");
        goto done;
    }
    *mdsnamep = mdsname;
    *namep = name;
    *pct_cpup = pct_cpu;
    *pct_memp = pct_mem;
    *inodes_freep = inodes_free;
    *inodes_totalp = inodes_total;
    *kbytes_freep = kbytes_free;
    *kbytes_totalp = kbytes_total;
    *mdopsp = mdops;
    retval = 0;
done:
    if (retval < 0) {
        free (mdsname);
        free (name);
        list_destroy (mdops);
    }
    return retval;
}
    
int lmt_mds_decode_v2_mdops (const char *s, char **opnamep, uint64_t *samplesp,
                             uint64_t *sump, uint64_t *sumsquaresp)
{
    return lmt_mdt_decode_v1_mdops (s, opnamep, samplesp, sump, sumsquaresp);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
