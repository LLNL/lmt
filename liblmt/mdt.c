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

#define LMT_MDT_PROTOCOL_VERSION    "3"

typedef struct {
    int num;
    char *name;
} optab_t;

/* AUDIT:  this is the original list of ops produced for lmt 2.0 in lustre-1.6
 * era.  How many are invalid now?  On a real system, we see many zeroes.
 */
static optab_t mds_ops[] = {
    {52,  "open"},                          \
    {53,  "close"},                         \
    {54,  "mknod"},                         \
    {55,  "link"},                          \
    {56,  "unlink"},                        \
    {57,  "mkdir"},                         \
    {58,  "rmdir"},                         \
    {59,  "rename"},                        \
    {60,  "getxattr"},                      \
    {61,  "setxattr"},                      \
    {62,  "iocontrol"},                     \
    {63,  "get_info"},                      \
    {64,  "set_info_async"},                \
    {65,  "attach"},                        \
    {66,  "detach"},                        \
    {67,  "setup"},                         \
    {68,  "precleanup"},                    \
    {69,  "cleanup"},                       \
    {70,  "process_config"},                \
    {71,  "postrecov"},                     \
    {72,  "add_conn"},                      \
    {73,  "del_conn"},                      \
    {74,  "connect"},                       \
    {75,  "reconnect"},                     \
    {76,  "disconnect"},                    \
    {77,  "statfs"},                        \
    {78,  "statfs_async"},                  \
    {79,  "packmd"},                        \
    {80,  "unpackmd"},                      \
    {81,  "checkmd"},                       \
    {82,  "preallocate"},                   \
    {83,  "precreate"},                     \
    {84,  "create"},                        \
    {85,  "destroy"},                       \
    {86,  "setattr"},                       \
    {87,  "setattr_async"},                 \
    {88,  "getattr"},                       \
    {89,  "getattr_async"},                 \
    {90,  "brw"},                           \
    {91,  "brw_async"},                     \
    {92,  "prep_async_page"},               \
    {93,  "reget_short_lock"},              \
    {94,  "release_short_lock"},            \
    {95,  "queue_async_io"},                \
    {96,  "queue_group_io"},                \
    {97,  "trigger_group_io"},              \
    {98,  "set_async_flags"},               \
    {99,  "teardown_async_page"},           \
    {100, "merge_lvb"},                     \
    {101,  "adjust_kms"},                   \
    {102,  "punch"},                        \
    {103,  "sync"},                         \
    {104,  "migrate"},                      \
    {105,  "copy"},                         \
    {106,  "iterate"},                      \
    {107,  "preprw"},                       \
    {108,  "commitrw"},                     \
    {110,  "match"},                        \
    {111,  "change_cbdata"},                \
    {112,  "cancel"},                       \
    {113,  "cancel_unused"},                \
    {114,  "join_lru"},                     \
    {115,  "init_export"},                  \
    {116,  "destroy_export"},               \
    {117,  "extent_calc"},                  \
    {118,  "llog_init"},                    \
    {119,  "llog_finish"},                  \
    {120,  "pin"},                          \
    {121,  "unpin"},                        \
    {122,  "import_event"},                 \
    {123,  "notify"},                       \
    {124,  "health_check"},                 \
    {125,  "quotacheck"},                   \
    {126,  "quotactl"},                     \
    {127,  "quota_adjust_quint"},           \
    {128,  "ping"},                         \
    {129,  "register_page_removal_cb"},     \
    {130,  "unregister_page_removal_cb"},   \
    {131,  "register_lock_cancel_cb"},      \
    {132,  "unregister_lock_cancel_cb"},    \
};

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

static int
_get_mdtop (hash_t stats, char *key, char *s, int len)
{
    uint64_t count = 0, sum = 0, sumsq = 0;
    int retval = -1;

    (void)proc_lustre_parsestat (stats, key, &count, NULL, NULL, &sum, &sumsq);
    if (snprintf (s, len, "%lu;%lu;%lu;", count, sum, sumsq) >= len) {
        errno = E2BIG;
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

    if (proc_lustre_uuid (ctx, name, &uuid) < 0)
        goto done;
    if (proc_lustre_files (ctx, name, &filesfree, &filestotal) < 0)
        goto done;
    if (proc_lustre_kbytes (ctx, name, &kbytesfree, &kbytestotal) < 0)
        goto done;
    if (proc_lustre_hashstats (ctx, name, &stats) < 0)
        goto done;
    n = snprintf (s, len, "%s;%lu;%lu;%lu;%lu;",
                  uuid,
                  filesfree,
                  filestotal,
                  kbytesfree,
                  kbytestotal
                  );
    if (n >= len) {
        errno = E2BIG;
        goto done;
    }
    /* Add count, sum, and sumsquare values to the mdtstring for each op,
     * as required by schema 1.1.  Substitute zeroes if not found.
     * N.B. lustre-1.8.2: sum and sumsquare appear to be missing from proc.
     */
    for (i = 0; i < sizeof (mds_ops) / sizeof (mds_ops[0]); i++) {
        used = strlen (s);
        if (_get_mdtop (stats, mds_ops[i].name, s + used, len - used) < 0)
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
lmt_mdt_string_v3 (pctx_t ctx, char *s, int len)
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
    if (uname (&uts) < 0)
        goto done;
    if (_get_cpu_usage (ctx, &cpupct) < 0)
        goto done;
    if (_get_mem_usage (ctx, &mempct) < 0)
        goto done;
    n = snprintf (s, len, "%s;%s;%f;%f;",
                  LMT_MDT_PROTOCOL_VERSION,
                  uts.nodename,
                  cpupct,
                  mempct);
    if (n >= len) {
        errno = E2BIG;
        goto done;
    }
    if (!(itr = list_iterator_create (mdtlist)))
        goto done;
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
lmt_mdt_updatedb_v3 (lmt_db_t hp, char *s)
{
    return 0;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
