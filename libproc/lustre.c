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

#include "proc.h"
#include "lustre.h"

#define PROC_FS_LUSTRE_MDT_DIR          "fs/lustre/mds"
#define PROC_FS_LUSTRE_OST_DIR          "fs/lustre/obdfilter"

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

#define PROC_FS_LUSTRE_MDT_STATS        "fs/lustre/mds/%s/stats"
#define PROC_FS_LUSTRE_OST_STATS        "fs/lustre/obdfilter/%s/stats"

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
    int error;

    errno = 0;
    error = proc_openf (ctx, tmpl, a1);
    if (error < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%"PRIu64, valp) != 1)
        error = -1;
    proc_close (ctx);
done:
    if (error < 0 && errno == 0)    /* EOF or 0 items scanned */
        errno = EIO;
    return error;
}

static int
_readstr1 (pctx_t ctx, char *tmpl, char *a1, char **valp)
{
    int error;
    char s[256];

    errno = 0;
    error = proc_openf (ctx, tmpl, a1);
    if (error < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%255s", s) != 1)
        error = -1;
    proc_close (ctx);
    if (error >= 0 && !(*valp = strdup (s))) {
        errno = ENOMEM;
        error = -1;
    }
done:
    if (error < 0 && errno == 0)    /* EOF or 0 items scanned */
        errno = EIO;
    return error;
}

int
proc_lustre_files (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp)
{
    uint64_t f, t;
    int error = -1;

    if (strstr (name, "-OST")) {
        error = _readint1 (ctx, PROC_FS_LUSTRE_OST_FILESFREE, name, &f);
        if (error < 0)
            goto done;
        error = _readint1 (ctx, PROC_FS_LUSTRE_OST_FILESTOTAL, name, &t);
    } else if (strstr (name, "-MDT")) {
        error = _readint1 (ctx, PROC_FS_LUSTRE_MDT_FILESFREE, name, &f);
        if (error < 0)
            goto done;
        error = _readint1 (ctx, PROC_FS_LUSTRE_MDT_FILESTOTAL, name, &t);
    } else 
        errno = EINVAL;
done:
    if (error >= 0) {
        *fp = f;
        *tp = t;
    }
    return error;
}

int
proc_lustre_kbytes (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp)
{
    uint64_t f, t;
    int error = -1;

    if (strstr (name, "-OST")) {
        error = _readint1 (ctx, PROC_FS_LUSTRE_OST_KBYTESFREE, name, &f);
        if (error < 0)
            goto done;
        error = _readint1 (ctx, PROC_FS_LUSTRE_OST_KBYTESTOTAL, name, &t);
    } else if (strstr (name, "-MDT")) {
        error = _readint1 (ctx, PROC_FS_LUSTRE_MDT_KBYTESFREE, name, &f);
        if (error < 0)
            goto done;
        error = _readint1 (ctx, PROC_FS_LUSTRE_MDT_KBYTESTOTAL, name, &t);
    } else 
        errno = EINVAL;
done:
    if (error >= 0) {
        *fp = f;
        *tp = t;
    }
    return error;
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
    int error = -1;

    if (strstr (name, "-OST")) {
        error = _readstr1 (ctx, PROC_FS_LUSTRE_OST_UUID, name, &uuid);
    } else if (strstr (name, "-MDT")) {
        error = _readstr1 (ctx, PROC_FS_LUSTRE_MDT_UUID, name, &uuid);
    } else 
        errno = EINVAL;
    if (error >= 0) {
        _trim_uuid (uuid);
        *uuidp = uuid;
    }
    return error;
}

static int
_subdirlist (pctx_t ctx, const char *path, List *lp)
{
    List l;
    int error;
    char *name;

    errno = 0;
    if (!(l = list_create ((ListDelF)free))) {
        error = -1;
        goto done;
    }
    if ((error = proc_open (ctx, path)) < 0)
        goto done;
    while ((error = proc_readdir (ctx, PROC_READDIR_NOFILE, &name)) >= 0) {
        if (!list_append (l, name)) {
            error = -1;
            break;
        }
    }
    if (error < 0 && errno == 0) /* treat EOF as success */
        error = 0;
    proc_close (ctx);
done:
    if (error < 0)
         list_destroy (l);
    else
        *lp = l;
    return error;
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

static int
_create_shash (char *key, char *val, shash_t **sp)
{
    shash_t *s = malloc (sizeof (shash_t));

    if (!s)
        goto nomem;
    memset (s, 0, sizeof (shash_t));
    if (!(s->key = strdup (key)))
        goto nomem;
    if (!(s->val = strdup (val)))
        goto nomem;
    *sp = s;
    return 0;
nomem:
    _destroy_shash (s);
    errno = ENOMEM;
    return -1;
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
    if (_create_shash (key, s, itemp) < 0)
        return -1;
    return 0;
}

static int
_hash_stats (pctx_t ctx, hash_t h)
{
    char line[256];
    shash_t *s;
    int error;

    errno = 0;
    while ((error = proc_gets (ctx, NULL, line, sizeof (line))) >= 0) {
        error = _parse_stat (line, &s);
        if (error < 0)
            break;
        if (!hash_insert (h, s->key, s)) {
            _destroy_shash (s);
            error = -1;
            break;
        }
    }
    if (error < 0 && errno == 0) /* treat EOF as success */
        error = 0;
    return error;
}

int
proc_lustre_hashstats (pctx_t ctx, char *name, hash_t *hp)
{
    hash_t h = NULL;
    int error = -1;

    if (strstr (name, "-OST"))
        error = proc_openf (ctx, PROC_FS_LUSTRE_OST_STATS, name);
    else if (strstr (name, "-MDT"))
        error = proc_openf (ctx, PROC_FS_LUSTRE_MDT_STATS, name);
    else 
        errno = EINVAL;
    if (error < 0)
        goto done;
    if (!(h = hash_create (STATS_HASH_SIZE, (hash_key_f)hash_key_string,
                           (hash_cmp_f)strcmp, (hash_del_f)_destroy_shash))) {
        errno = ENOMEM;
        error = -1;
    } else
        error = _hash_stats (ctx, h);
    proc_close (ctx);
done:
    if (error < 0) {
        if (h)
            hash_destroy (h);
    } else
        *hp = h;                           
    return error;
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
    int retval = -1;

    if (!(s = hash_find (stats, key)))
        goto done;
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
    retval = 0;
done:
    return retval;
}

int
proc_lustre_rwbytes (pctx_t ctx, char *name, uint64_t *rbp, uint64_t *wbp)
{
    int error = -1;
    hash_t stats = NULL;

    if (proc_lustre_hashstats (ctx, name, &stats) < 0)
        goto done;
    *rbp = *wbp = 0;
    (void)proc_lustre_parsestat (stats, "read_bytes", NULL, NULL, NULL,
                                 rbp, NULL);
    (void)proc_lustre_parsestat (stats, "write_bytes", NULL, NULL, NULL,
                                 wbp, NULL);
    error = 0;
done:
    if (stats)
        hash_destroy (stats);
    return error;
}

int
proc_lustre_lnet_newbytes (pctx_t ctx, uint64_t *valp)
{
    if (proc_scanf (ctx, PROC_SYS_LNET_STATS, "%*u %*u %*u %*u %*u "
                    "%*u %*u %*u %*u %"PRIu64" %*u", valp) != 1) {
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

