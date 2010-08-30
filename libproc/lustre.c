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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "lustre.h"

#define PROC_FS_LUSTRE_MDT_DIR          "fs/lustre/mds"
#define PROC_FS_LUSTRE_OST_DIR          "fs/lustre/obdfilter"
#define PROC_FS_LUSTRE_OSC_DIR          "fs/lustre/osc"

#define PROC_FS_LUSTRE_MDT_FILESFREE    "fs/lustre/mds/%s/filesfree"
#define PROC_FS_LUSTRE_MDT_FILESTOTAL   "fs/lustre/mds/%s/filestotal"
#define PROC_FS_LUSTRE_OST_FILESFREE    "fs/lustre/obdfilter/%s/filesfree"
#define PROC_FS_LUSTRE_OST_FILESTOTAL   "fs/lustre/obdfilter/%s/filestotal"

#define PROC_FS_LUSTRE_MDT_KBYTESFREE   "fs/lustre/mds/%s/kbytesfree"
#define PROC_FS_LUSTRE_MDT_KBYTESTOTAL  "fs/lustre/mds/%s/kbytestotal"
#define PROC_FS_LUSTRE_OST_KBYTESFREE   "fs/lustre/obdfilter/%s/kbytesfree"
#define PROC_FS_LUSTRE_OST_KBYTESTOTAL  "fs/lustre/obdfilter/%s/kbytestotal"

#define PROC_FS_LUSTRE_MDT_UUID         "fs/lustre/mds/%s/uuid"
#define PROC_FS_LUSTRE_OST_UUID         "fs/lustre/obdfilter/%s/uuid"

#define PROC_FS_LUSTRE_OSC_OST_SERVER_UUID \
                                        "fs/lustre/osc/%s/ost_server_uuid"

#define PROC_FS_LUSTRE_MDT_STATS        "fs/lustre/mds/%s/stats"
#define PROC_FS_LUSTRE_OST_STATS        "fs/lustre/obdfilter/%s/stats"

#define PROC_FS_LUSTRE_OSC_STATE        "fs/lustre/osc/%s/state"

#define PROC_FS_LUSTRE_OST_RECOVERY_STATUS \
                                        "fs/lustre/obdfilter/%s/recovery_status"
#define PROC_FS_LUSTRE_MDT_RECOVERY_STATUS \
                                        "fs/lustre/mds/%s/recovery_status"

#define PROC_FS_LUSTRE_OST_NUM_EXPORTS  "fs/lustre/obdfilter/%s/num_exports"
#define PROC_FS_LUSTRE_MDT_NUM_EXPORTS  "fs/lustre/mds/%s/num_exports"

#define PROC_SYS_LNET_ROUTES            "sys/lnet/routes"
#define PROC_SYS_LNET_STATS             "sys/lnet/stats"

#define STATS_HASH_SIZE                 64

/* Note: unless otherwise noted, functions return -1 on error (errno set),
 * 0 or greater on success.  Scanf functions return number of matches.
 * On EOF, functions return -1 with errno clear.
 */

static int
_readint1 (pctx_t ctx, char *tmpl, char *a1, uint64_t *valp)
{
    uint64_t val;
    int ret;

    if ((ret = proc_openf (ctx, tmpl, a1)) < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%"PRIu64, &val) != 1) {
        errno = EIO;
        ret = -1;
    }
    proc_close (ctx);
done:
    if (ret == 0)
        *valp = val;
    return ret;
}

static int
_readstr1 (pctx_t ctx, char *tmpl, char *a1, char **valp)
{
    int ret;
    char s[256];

    if ((ret = proc_openf (ctx, tmpl, a1)) < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%255s", s) != 1) {
        errno = EIO;
        ret = -1;
    }
    proc_close (ctx);
done:
    if (ret == 0) {
        if (!(*valp = strdup (s)))
            msg_exit ("out of memory");
    }
    return ret;
}

int
proc_lustre_files (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp)
{
    int ret = -1;
    uint64_t f, t;
    char *tmplf, *tmplt;

    if (strstr (name, "-OST")) {
        tmplf = PROC_FS_LUSTRE_OST_FILESFREE;
        tmplt = PROC_FS_LUSTRE_OST_FILESTOTAL;
    } else if (strstr (name, "-MDT")) {
        tmplf = PROC_FS_LUSTRE_MDT_FILESFREE;
        tmplt = PROC_FS_LUSTRE_MDT_FILESTOTAL;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmplf, name, &f)) < 0)
        goto done;
    if ((ret = _readint1 (ctx, tmplt, name, &t)) < 0)
        goto done;
done:
    if (ret == 0) {
        *fp = f;
        *tp = t;
    }
    return ret;
}

int
proc_lustre_kbytes (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp)
{
    int ret = -1;
    uint64_t f, t;
    char *tmplf, *tmplt;

    if (strstr (name, "-OST")) {
        tmplf = PROC_FS_LUSTRE_OST_KBYTESFREE;
        tmplt = PROC_FS_LUSTRE_OST_KBYTESTOTAL;
    } else if (strstr (name, "-MDT")) {
        tmplf = PROC_FS_LUSTRE_MDT_KBYTESFREE;
        tmplt = PROC_FS_LUSTRE_MDT_KBYTESTOTAL;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmplf, name, &f)) < 0)
        goto done;
    if ((ret = _readint1 (ctx, tmplt, name, &t)) < 0)
        goto done;
done:
    if (ret == 0) {
        *fp = f;
        *tp = t;
    }
    return ret;
}

int
proc_lustre_num_exports (pctx_t ctx, char *name, uint64_t *np)
{
    int ret = -1;
    uint64_t n;
    char *tmpl;

    if (strstr (name, "-OST")) {
        tmpl = PROC_FS_LUSTRE_OST_NUM_EXPORTS;
    } else if (strstr (name, "-MDT")) {
        tmpl = PROC_FS_LUSTRE_MDT_NUM_EXPORTS;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmpl, name, &n)) < 0)
        goto done;
done:
    if (ret == 0)
        *np = n;
    return ret;
}


static void
_trim_uuid (char *s)
{
    char *p = s + strlen (s) - 5;

    if (p >= s && !strcmp (p, "_UUID"))
        *p = '\0';
}

/* FIXME: is it always <servername>_UUID?  If so this could be simpler */
int
proc_lustre_uuid (pctx_t ctx, char *name, char **uuidp)
{
    char *uuid;
    int ret = -1;

    if (strstr (name, "-OST")) {
        ret = _readstr1 (ctx, PROC_FS_LUSTRE_OST_UUID, name, &uuid);
    } else if (strstr (name, "-MDT")) {
        ret = _readstr1 (ctx, PROC_FS_LUSTRE_MDT_UUID, name, &uuid);
    } else 
        errno = EINVAL;
    if (ret == 0) {
        _trim_uuid (uuid);
        *uuidp = uuid;
    }
    return ret;
}

int
proc_lustre_oscinfo (pctx_t ctx, char *name, char **uuidp, char **statep)
{
    int ret;
    char s1[32], s2[32];

    if ((ret = proc_openf (ctx, PROC_FS_LUSTRE_OSC_OST_SERVER_UUID, name)) < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%31s %31s", s1, s2) != 2) {
        errno = EIO;
        ret = -1;
    }
    proc_close (ctx);
done:
    if (ret == 0) {
        _trim_uuid (s1);
        if (!(*uuidp = strdup (s1)))
            msg_exit ("out of memory");
        if (!(*statep = strdup (s2)))
            msg_exit ("out of memory");
    }
    return ret;
}

static int
_subdirlist (pctx_t ctx, const char *path, List *lp)
{
    List l = list_create((ListDelF)free);
    int ret;
    char *name;

    errno = 0;
    if ((ret = proc_open (ctx, path)) < 0)
        goto done;
    while ((ret = proc_readdir (ctx, PROC_READDIR_NOFILE, &name)) >= 0) {
        if (strstr (name, "-osc-")) /* ignore client-instantiated osc's */
            continue;               /*  e.g. lc1-OST0005-osc-ffff81007f018c00 */
        list_append (l, name);
    }
    if (ret < 0 && errno == 0) /* treat EOF as success */
        ret = 0;
    proc_close (ctx);
done:
    if (ret == 0)
        *lp = l;
    else
         list_destroy (l);
    return ret;
}

int
proc_lustre_ostlist (pctx_t ctx, List *lp)
{
    return _subdirlist (ctx, PROC_FS_LUSTRE_OST_DIR, lp);
}

int
proc_lustre_mdtlist (pctx_t ctx, List *lp)
{ 
    return _subdirlist (ctx, PROC_FS_LUSTRE_MDT_DIR, lp);
}

int
proc_lustre_osclist (pctx_t ctx, List *lp)
{
    return _subdirlist (ctx, PROC_FS_LUSTRE_OSC_DIR, lp);
}

static void
_destroy_shash (shash_t *s)
{
    if (s) {
        if (s->key)
            free (s->key);
        if (s->val)
            free (s->val);
        free (s);
    }
}

static shash_t *
_create_shash (char *key, char *val)
{
    shash_t *s;

    if (!(s = malloc (sizeof (shash_t))))
        msg_exit ("out of memory");
    memset (s, 0, sizeof (shash_t));
    if (!(s->key = strdup (key)))
        msg_exit ("out of memory");
    if (!(s->val = strdup (val)))
        msg_exit ("out of memory");
    return s;
}

static int
_parse_stat (char *s, shash_t **itemp)
{
    char *key = s;

    while (*s && !isspace (*s))
        s++;
    if (!*s) {
        errno = EIO;
        return -1;
    }
    *s++ = '\0';
    while (*s && isspace (*s))
        s++;
    if (!*s) {
        errno = EIO;
        return -1;
    }
    *itemp = _create_shash (key, s);
    return 0;
}

static int
_hash_stats (pctx_t ctx, hash_t h)
{
    char line[256];
    shash_t *s;
    int ret;

    errno = 0;
    while ((ret = proc_gets (ctx, NULL, line, sizeof (line))) >= 0) {
        if ((ret = _parse_stat (line, &s)) < 0)
            break;
        if (!hash_insert (h, s->key, s)) {
            _destroy_shash (s);
            ret = -1;
            break;
        }
    }
    if (ret == -1 && errno == 0) /* treat EOF as success */
        ret = 0;
    return ret;
}

int
proc_lustre_hashstats (pctx_t ctx, char *name, hash_t *hp)
{
    hash_t h = NULL;
    int ret = -1;

    if (strstr (name, "-OST"))
        ret = proc_openf (ctx, PROC_FS_LUSTRE_OST_STATS, name);
    else if (strstr (name, "-MDT"))
        ret = proc_openf (ctx, PROC_FS_LUSTRE_MDT_STATS, name);
    else 
        errno = EINVAL;
    if (ret < 0)
        goto done;
    h = hash_create (STATS_HASH_SIZE, (hash_key_f)hash_key_string,
                    (hash_cmp_f)strcmp, (hash_del_f)_destroy_shash);
    ret = _hash_stats (ctx, h);
    proc_close (ctx);
done:
    if (ret == 0)
        *hp = h;                           
    else if (h)
        hash_destroy (h);
    return ret;
}

/* The recovery_status file is in "key <space> value" form like stats
 * so we borrow _hash_stats ().
 */
int
proc_lustre_hashrecov (pctx_t ctx, char *name, hash_t *hp)
{
    hash_t h = NULL;
    int ret = -1;

    if (strstr (name, "-OST"))
        ret = proc_openf (ctx, PROC_FS_LUSTRE_OST_RECOVERY_STATUS, name);
    else if (strstr (name, "-MDT"))
        ret = proc_openf (ctx, PROC_FS_LUSTRE_MDT_RECOVERY_STATUS, name);
    else 
        errno = EINVAL;
    if (ret < 0)
        goto done;
    h = hash_create (STATS_HASH_SIZE, (hash_key_f)hash_key_string,
                    (hash_cmp_f)strcmp, (hash_del_f)_destroy_shash);
    ret = _hash_stats (ctx, h);
    proc_close (ctx);
done:
    if (ret == 0)
        *hp = h;                           
    else if (h)
        hash_destroy (h);
    return ret;
}

/* stat format is:  <key>   <count> samples [<unit>] <min> <max> <sum> <sumsq> 
 * minimum is:      <key>   <count> samples [<unit>] 
 */
int
proc_lustre_parsestat (hash_t stats, const char *key, uint64_t *countp,
                       uint64_t *minp, uint64_t *maxp,
                       uint64_t *sump, uint64_t *sumsqp)
{
    shash_t *s;
    uint64_t count = 0;
    uint64_t min = 0;
    uint64_t max = 0;
    uint64_t sum = 0;
    uint64_t sumsq = 0;
    int ret = -1;

    if (!(s = hash_find (stats, key))) {
        errno = EINVAL;
        goto done;
    }
    if (sscanf (s->val,
                "%"PRIu64" samples %*s %"PRIu64" %"PRIu64" %"PRIu64" %"PRIu64,
                &count, &min, &max, &sum, &sumsq) < 1) {
        errno = EIO;
        goto done;
    }
    if (countp)
        *countp = count;
    if (minp)
        *minp = min;
    if (maxp)
        *maxp = max;
    if (sump)
        *sump = sum;
    if (sumsqp)
        *sumsqp = sumsq;
    ret = 0;
done:
    return ret;
}

int
proc_lustre_rwbytes (pctx_t ctx, char *name, uint64_t *rbp, uint64_t *wbp,
                     uint64_t *iop)
{
    int ret = -1;
    hash_t stats = NULL;

    if (proc_lustre_hashstats (ctx, name, &stats) < 0)
        goto done;
    /* If values are zero, token will not be present in proc file, so
     * ignore errors parsing these tokens from proc.
     */
    *iop = *rbp = *wbp = 0;
    proc_lustre_parsestat (stats, "read_bytes", NULL, NULL, NULL, rbp, NULL);
    proc_lustre_parsestat (stats, "write_bytes", NULL, NULL, NULL, wbp, NULL);
    proc_lustre_parsestat (stats, "commitrw", iop, NULL, NULL, NULL, NULL);
    ret = 0;
done:
    if (stats)
        hash_destroy (stats);
    return ret;
}

int
proc_lustre_lnet_newbytes (pctx_t ctx, uint64_t *valp)
{
    int n;

    n = proc_scanf (ctx, PROC_SYS_LNET_STATS, "%*u %*u %*u %*u %*u "
                    "%*u %*u %*u %*u %"PRIu64" %*u", valp);
    if (n < 0)
        return -1;
    if (n != 1) {
        errno = EIO;
        return -1;
    }
    return 0;
}

int
proc_lustre_lnet_routing_enabled (pctx_t ctx, int *valp)
{
    char buf[32];
    int retval = 0;

    if (!proc_gets (ctx, PROC_SYS_LNET_ROUTES, buf, sizeof (buf))) {
        if (errno == 0)
            errno = EIO;
        retval = -1;
    } else if (!strcmp (buf, "Routing enabled")) {
        *valp = 1;
    } else if (!strcmp (buf, "Routing disabled")) {
        *valp = 0;
    } else {
        retval = -1;
        errno = EIO;
    }
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

