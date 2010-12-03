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
#include <unistd.h>
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

#define PROC_FS_LUSTRE_OST_DIR          "fs/lustre/obdfilter"
#define PROC_FS_LUSTRE_OSC_DIR          "fs/lustre/osc"

#define PROC_FS_LUSTRE_1_8_MDT_DIR      "fs/lustre/mds"
#define PROC_FS_LUSTRE_2_0_MDT_DIR      "fs/lustre/mdt"

#define LUSTRE_1_8_LDISKFS_OSD_DIR      PROC_FS_LUSTRE_1_8_MDT_DIR
#define LUSTRE_2_0_LDISKFS_OSD_DIR      "fs/lustre/osd-ldiskfs"
#define LUSTRE_2_0_ZFS_OSD_DIR          "fs/lustre/osd-zfs"

#define PROC_FS_LUSTRE_MDT_FILESFREE    "%s/filesfree"
#define PROC_FS_LUSTRE_MDT_FILESTOTAL   "%s/filestotal"
#define PROC_FS_LUSTRE_OST_FILESFREE    "fs/lustre/obdfilter/%s/filesfree"
#define PROC_FS_LUSTRE_OST_FILESTOTAL   "fs/lustre/obdfilter/%s/filestotal"

#define PROC_FS_LUSTRE_MDT_KBYTESFREE   "%s/kbytesfree"
#define PROC_FS_LUSTRE_MDT_KBYTESTOTAL  "%s/kbytestotal"
#define PROC_FS_LUSTRE_OST_KBYTESFREE   "fs/lustre/obdfilter/%s/kbytesfree"
#define PROC_FS_LUSTRE_OST_KBYTESTOTAL  "fs/lustre/obdfilter/%s/kbytestotal"

#define PROC_FS_LUSTRE_MDT_UUID         "%s/uuid"
#define PROC_FS_LUSTRE_OST_UUID         "fs/lustre/obdfilter/%s/uuid"

#define PROC_FS_LUSTRE_OSC_OST_SERVER_UUID \
                                        "fs/lustre/osc/%s/ost_server_uuid"

#define PROC_FS_LUSTRE_1_8_MDT_STATS    "%s/stats"
#define PROC_FS_LUSTRE_2_0_MDT_STATS    "%s/md_stats"
#define PROC_FS_LUSTRE_OST_STATS        "fs/lustre/obdfilter/%s/stats"

#define PROC_FS_LUSTRE_MDT_EXPORTS      "%s/%s/exports"
#define PROC_FS_LUSTRE_MDT_EXPORT_STATS "%s/%s/exports/%s/stats"

#define PROC_FS_LUSTRE_OST_BRW_STATS    "fs/lustre/obdfilter/%s/brw_stats"

#define PROC_FS_LUSTRE_OST_RECOVERY_STATUS \
                                        "fs/lustre/obdfilter/%s/recovery_status"
#define PROC_FS_LUSTRE_MDT_RECOVERY_STATUS \
                                        "%s/recovery_status"

#define PROC_FS_LUSTRE_OST_NUM_EXPORTS  "fs/lustre/obdfilter/%s/num_exports"
#define PROC_FS_LUSTRE_MDT_NUM_EXPORTS  "%s/num_exports"

#define PROC_FS_LUSTRE_OST_LDLM_LOCK_COUNT \
                    "fs/lustre/ldlm/namespaces/filter-%s_UUID/lock_count"
#define PROC_FS_LUSTRE_OST_LDLM_GRANT_RATE \
                    "fs/lustre/ldlm/namespaces/filter-%s_UUID/pool/grant_rate"
#define PROC_FS_LUSTRE_OST_LDLM_CANCEL_RATE \
                    "fs/lustre/ldlm/namespaces/filter-%s_UUID/pool/cancel_rate"

#define PROC_FS_LUSTRE_MDT_LDLM_LOCK_COUNT \
                    "fs/lustre/ldlm/namespaces/mds-%s_UUID/lock_count"
#define PROC_FS_LUSTRE_MDT_LDLM_GRANT_RATE \
                    "fs/lustre/ldlm/namespaces/mds-%s_UUID/pool/grant_rate"
#define PROC_FS_LUSTRE_MDT_LDLM_CANCEL_RATE \
                    "fs/lustre/ldlm/namespaces/mds-%s_UUID/pool/cancel_rate"

#define PROC_FS_LUSTRE_VERSION          "fs/lustre/version"

#define PROC_SYS_LNET_ROUTES            "sys/lnet/routes"
#define PROC_SYS_LNET_STATS             "sys/lnet/stats"

#define STATS_HASH_SIZE                 64

/* Quirks:
 * Make some unexplained/buggy behavior in lustre non-fatal in LMT.
 * FIXME: these result in values reported as zero without any warning that
 * the values are not correct.
 */

/* per issue 28 - at least one guys 1.8.3 is missing ldlm pool directory */
/* 2.0.50.zfs is missing all ldlm stats */
#define NONFATAL_MISSING_LDLM_STATS 1

/* 2.0.50.zfs is missing MDT export stats */
#define NONFATAL_MISSING_MDT_EXPORT_STATS 1

/* per issue 46 - statfs (once) appeared twice in mds stats in llnl 1.8.3 */
#define NONFATAL_DUPLICATE_STATS_HASHKEY 1

/* forward declaration */
static int _read_lustre_version_string (pctx_t ctx, char **version_string);

typedef enum {
    BACKFS_LDISKFS,
    BACKFS_ZFS
} backfs_t;

/* Note: unless otherwise noted, functions return -1 on error (errno set),
 * 0 or greater on success.  Scanf functions return number of matches.
 * On EOF, functions return -1 with errno clear.
 */

/*
 * Fill-in the supplied version components with the Lustre version
 * found in /proc.
 *
 * Returns -1 or less on error, 0 or greater on success.
 */
int
proc_fs_lustre_version (pctx_t ctx, int *major, int *minor, int *patch,
                        int *fix)
{
    int ret = -1;
    char *version_string = NULL;

    if ((ret = _read_lustre_version_string (ctx, &version_string)) < 0)
        goto done;

    /* first, get the numbers which must be present in a version string */
    if ((ret = sscanf (version_string, "%d.%d.%d", major, minor, patch)) != 3) {
        errno = EIO;
        ret = -3 + ret;
        goto done;
    }

    /* now, get the optional fix value or set it to 0 */
    if (sscanf (version_string, "%*d.%*d.%*d.%d", fix) != 1)
        *fix = 0;

done:
    if (version_string)
        free (version_string);
    return ret;
}

/*
 * Read the Lustre version from /proc and pack it into an int.
 * Returns the packed version or -1 on error.
 */
static int
_packed_lustre_version (pctx_t ctx)
{
    int ret = -1;
    int major = 0, minor = 0, patch = 0, fix = 0;

    if ((ret = proc_fs_lustre_version (ctx, &major, &minor, &patch, &fix)) < 0)
        goto done;

    ret = PACKED_VERSION(major, minor, patch, fix);
done:
    return ret;
}

/*
 * Add a slight level of abstraction to centralize the logic of figuring
 * out where a given Lustre version's MDT directory lives.
 *
 * Returns a static string.
 */
static char *
_find_mdt_dir (pctx_t ctx)
{
    int lustre_version = _packed_lustre_version (ctx);

    /* Keep adding to the top of this as changes accrue */
    if (lustre_version >= LUSTRE_2_0)
       return PROC_FS_LUSTRE_2_0_MDT_DIR;
    else
       return PROC_FS_LUSTRE_1_8_MDT_DIR;
}

/*
 * Another level of abstraction for filling in the #define-d path templates
 * with the correct MDT path for this Lustre version.
 */
static int
_build_mdt_path (pctx_t ctx, char *in_tmpl, char **out_tmpl)
{
    int len = 0;
    char *mdt_dir = _find_mdt_dir (ctx);

    len = strlen (in_tmpl) + strlen (mdt_dir) + 2;
    if (!(*out_tmpl = malloc (len)))
        msg_exit ("out of memory");

    return snprintf(*out_tmpl, len, "%s/%s", mdt_dir, in_tmpl);
}

/*
 * Abstract the logic of finding which backfs type is in-use for the
 * OSDs (ldiskfs or zfs).
 *
 * Returns an enum value.
 */
static int
_find_lustre_backfs_type (pctx_t ctx)
{
    if (proc_open (ctx, LUSTRE_2_0_ZFS_OSD_DIR) == 0) {
        proc_close (ctx);
        return BACKFS_ZFS;
    } else {
        return BACKFS_LDISKFS;
    }
}

/*
 * Abstract the logic of finding the stats entry for a given OSD name
 * for this Lustre version.
 */
static int
_build_osd_stats_path (pctx_t ctx, char *name, char **stats)
{
    int ret = -1;
    int lustre_version = _packed_lustre_version (ctx);

    if (strstr (name, "-MDT")) {
        if (lustre_version >= LUSTRE_2_0) {
            if (_find_lustre_backfs_type (ctx) == BACKFS_ZFS) {
                if ((ret = _build_mdt_path (ctx,
                                            PROC_FS_LUSTRE_1_8_MDT_STATS,
                                            stats)) < 0)
                    goto done;
            } else {
                if ((ret = _build_mdt_path (ctx,
                                            PROC_FS_LUSTRE_2_0_MDT_STATS,
                                            stats)) < 0)
                    goto done;
            }
        } else {
            if ((ret = _build_mdt_path (ctx,
                                        PROC_FS_LUSTRE_1_8_MDT_STATS,
                                        stats)) < 0)
                goto done;
        }
    } else if (strstr (name, "-OST")) {
        /* Ugly, but avoids a problem with free-ing a constant later. */
        if (!(*stats = strdup (PROC_FS_LUSTRE_OST_STATS)))
            msg_exit ("out of memory");
        ret = 0;
    } else {
        errno = EINVAL;
    }
done:
    return ret;
}

/*
 * Add a slight level of abstraction to centralize the logic of figuring
 * out the correct OSD path for a given version/backfs combination.
 *
 * Returns a static string.
 */
static char *
_find_osd_dir (pctx_t ctx)
{
    int lustre_version = _packed_lustre_version (ctx);

    /* Keep adding to the top of this as changes accrue */
    if (lustre_version >= LUSTRE_2_0) {
        switch (_find_lustre_backfs_type (ctx)) {
            case BACKFS_LDISKFS:
                return LUSTRE_2_0_LDISKFS_OSD_DIR;
            case BACKFS_ZFS:
                return LUSTRE_2_0_ZFS_OSD_DIR;
        }
    }

    /* Default path. */
    return LUSTRE_1_8_LDISKFS_OSD_DIR;
}

/*
 * Another level of abstraction for filling in the #define-d path templates
 * with the correct OSD path for this Lustre version/backfs combination.
 */
static int
_build_osd_path (pctx_t ctx, char *in_tmpl, char **out_tmpl)
{
    char *osd_dir = _find_osd_dir (ctx);
    int len = 0;

    len = strlen (in_tmpl) + strlen (osd_dir) + 2;
    if (!(*out_tmpl = malloc (len)))
        msg_exit ("out of memory");

    return snprintf(*out_tmpl, len, "%s/%s", osd_dir, in_tmpl);
}

static int
_readint1 (pctx_t ctx, char *tmpl, char *a1, uint64_t *valp)
{
    uint64_t val;
    int ret = -1;
    char *new_tmpl;

    if (strstr (a1, "-MDT")) {
        /* Shouldn't be too fragile, as long as these names are stable. */
        if (strstr (tmpl, "/files") || strstr (tmpl, "/kbytes")) {
            if ((ret = _build_osd_path (ctx, tmpl, &new_tmpl)) < 0)
                goto done;
        } else {
            if ((ret = _build_mdt_path (ctx, tmpl, &new_tmpl)) < 0)
                goto done;
        }
    } else {
        if (!(new_tmpl = strdup (tmpl)))
            msg_exit ("out of memory");
    }

    if ((ret = proc_openf (ctx, new_tmpl, a1)) < 0)
        goto done;
    if (proc_scanf (ctx, NULL, "%"PRIu64, &val) != 1) {
        errno = EIO;
        ret = -1;
    }
    proc_close (ctx);
done:
    if (ret == 0)
        *valp = val;

    if (new_tmpl)
        free (new_tmpl);
    return ret;
}

static int
_readstr1 (pctx_t ctx, char *tmpl, char *a1, char **valp)
{
    int ret = -1;
    char s[256];
    char *new_tmpl;

    if (strstr (a1, "-MDT")) {
        if ((ret = _build_mdt_path (ctx, tmpl, &new_tmpl)) < 0)
            goto done;
    } else {
        if (!(new_tmpl = strdup (tmpl)))
            msg_exit ("out of memory");
    }

    if ((ret = proc_openf (ctx, new_tmpl, a1)) < 0)
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

    if (new_tmpl)
        free (new_tmpl);
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

int
proc_lustre_ldlm_lock_count (pctx_t ctx, char *name, uint64_t *np)
{
    int ret = -1;
    uint64_t n = 0;
    char *tmpl;

    if (strstr (name, "-OST")) {
        tmpl = PROC_FS_LUSTRE_OST_LDLM_LOCK_COUNT;
    } else if (strstr (name, "-MDT")) {
        tmpl = PROC_FS_LUSTRE_MDT_LDLM_LOCK_COUNT;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmpl, name, &n)) < 0) {
#if NONFATAL_MISSING_LDLM_STATS
        if (errno == ENOENT)
            ret = 0;
#endif
        goto done;
    }
done:
    if (ret == 0)
        *np = n;
    return ret;
}

int
proc_lustre_ldlm_grant_rate (pctx_t ctx, char *name, uint64_t *np)
{
    int ret = -1;
    uint64_t n = 0;
    char *tmpl;

    if (strstr (name, "-OST")) {
        tmpl = PROC_FS_LUSTRE_OST_LDLM_GRANT_RATE;
    } else if (strstr (name, "-MDT")) {
        tmpl = PROC_FS_LUSTRE_MDT_LDLM_GRANT_RATE;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmpl, name, &n)) < 0) {
#if NONFATAL_MISSING_LDLM_STATS
        if (errno == ENOENT)
            ret = 0;
#endif
        goto done;
    }
done:
    if (ret == 0)
        *np = n;
    return ret;
}

int
proc_lustre_ldlm_cancel_rate (pctx_t ctx, char *name, uint64_t *np)
{
    int ret = -1;
    uint64_t n = 0;
    char *tmpl;

    if (strstr (name, "-OST")) {
        tmpl = PROC_FS_LUSTRE_OST_LDLM_CANCEL_RATE;
    } else if (strstr (name, "-MDT")) {
        tmpl = PROC_FS_LUSTRE_MDT_LDLM_CANCEL_RATE;
    } else {
        errno = EINVAL;
        goto done;
    }
    if ((ret = _readint1 (ctx, tmpl, name, &n)) < 0) {
#if NONFATAL_MISSING_LDLM_STATS
        if (errno == ENOENT)
            ret = 0;
#endif
        goto done;
    }
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
_cmp_subdir_entries (char *e1, char *e2)
{
    return strcmp (e1, e2);
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
        if (strstr (name, "-osc-") && !strstr (name, "MDT"))
            free (name);            /* ignore client-instantiated osc's */
        else                        /*  e.g. lc1-OST0005-osc-ffff81007f018c00 */
            list_append (l, name);
    }
    if (ret < 0 && errno == 0) /* treat EOF as success */
        ret = 0;
    proc_close (ctx);
done:
    if (ret == 0) {
        list_sort (l, (ListCmpF)_cmp_subdir_entries);
        *lp = l;
    } else {
         list_destroy (l);
    }
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
    char *mdt_dir = _find_mdt_dir (ctx);

    return _subdirlist (ctx, mdt_dir, lp);
}

int
proc_lustre_osclist (pctx_t ctx, List *lp)
{
    return _subdirlist (ctx, PROC_FS_LUSTRE_OSC_DIR, lp);
}

int
proc_lustre_mdt_exportlist (pctx_t ctx, char *name, List *lp)
{
    int ret = -1;
    char *export_path, *mdt_dir = _find_mdt_dir (ctx);
    int len = strlen (PROC_FS_LUSTRE_MDT_EXPORTS) + \
              strlen (mdt_dir) + strlen (name) + 1;

    if (!(export_path = malloc (len)))
        msg_exit ("out of memory");

    if ((ret = snprintf (export_path, len, PROC_FS_LUSTRE_MDT_EXPORTS,
                         mdt_dir, name)) < 0)
        goto done;

    ret = _subdirlist (ctx, export_path, lp);
done:
    if (export_path)
        free (export_path);
    return ret;
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

#if NONFATAL_DUPLICATE_STATS_HASHKEY
static void *
_hash_insert_lastinwins (hash_t h, const void *key, void *data, hash_del_f del)
{
    void *res = NULL, *old = NULL;

    if (!(res = hash_insert (h, key, data))) {
        if (errno != EEXIST)
            goto done;
        old = hash_remove (h, key);
        if (old)
            del (old);
        res = hash_insert (h, key, data);
    }
done: 
    return res;
}
#endif

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
#if NONFATAL_DUPLICATE_STATS_HASHKEY
        if (!_hash_insert_lastinwins (h, s->key, s,
                                     (hash_del_f)_destroy_shash)) {
#else
        if (!hash_insert (h, s->key, s)) {
#endif
            _destroy_shash (s);
            ret = -1;
            break;
        }
    }
    if (ret == -1 && errno == 0) /* treat EOF as success */
        ret = 0;
    return ret;
}

static int
_parse_stat_node (shash_t *node, uint64_t *countp, uint64_t *minp,
                  uint64_t *maxp, uint64_t *sump, uint64_t *sumsqp)
{
    uint64_t count = 0, min = 0, max = 0, sum = 0, sumsq = 0;
    int ret = -1;

    assert (node->val);
    if (sscanf (node->val,
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

/*
 * Aggregate statistics across multiple stats entries.  When a duplicate
 * key is encountered, store the existing keypair's values, add them to the
 * new keypair's values, and replace the existing keypair with the
 * aggregate keypair.
 */
static int
_hash_aggregate_stats (pctx_t ctx, hash_t h)
{
    char line[256];
    char newval[256];
    shash_t *cur, *new, *old;
    uint64_t oldcount, oldmin, oldmax, oldsum, oldsumsq;
    uint64_t newcount, newmin, newmax, newsum, newsumsq;
    int ret = -1;

    errno = 0;
    while ((ret = proc_gets (ctx, NULL, line, sizeof (line))) >= 0) {
        oldcount = oldmin = oldmax = oldsum = oldsumsq = 0;
        newcount = newmin = newmax = newsum = newsumsq = 0;

        /* Get the current key name */
        if ((ret = _parse_stat (line, &cur)) < 0)
            break;

        /* Don't try to aggregate snapshot_time -- just skip it. */
        if (!strcmp(cur->key, "snapshot_time")) {
            _destroy_shash (cur);
            continue;
        }

        /* Read the new stat values */
        if ((_parse_stat_node (cur, &newcount, &newmin, &newmax, &newsum,
                               &newsumsq)) < 0)
            goto done;
        /* Get the old stat values, if they exist */
        if ((old = hash_find (h, cur->key))) {
            if ((_parse_stat_node (old, &oldcount, &oldmin, &oldmax,
                                   &oldsum, &oldsumsq)) < 0)
                goto done;

            hash_remove (h, old->key);
            /* This seems to be necesssary to prevent leaks -- shouldn't
               hash_node_free() handle it, though? */
            _destroy_shash (old);
        }

        /* Bit of a hack, but we'll create an approximation of the original
           hash value in a format suitable for consumption by existing code. */
        snprintf (newval, sizeof (newval),
                  "%"PRIu64" samples [reqs] %"PRIu64" %"PRIu64" %"PRIu64" %"PRIu64,
                  oldcount + newcount, oldmin + newmin, oldmax + newmax,
                  oldsum + newsum, oldsumsq + newsumsq);

        new = _create_shash (cur->key, newval);
        _destroy_shash (cur);

        if (!hash_insert (h, new->key, new)) {
            _destroy_shash (new);
            ret = -1;
            break;
        }
    }
    if (ret == -1 && errno == 0) /* treat EOF as success */
        ret = 0;

done:
    return ret;
}

/*
 * The purpose of this function is to force the mdt stats keys to
 * conform to the key names in the optab_mdt_v1 table found in
 * liblmt/mdt.c.  It is supplied as the callback to hash_for_each()
 * and called for each key/val pair in the hash.
 *
 * This seems less intrusive than adding another table
 * for Lustre 2.x MD ops which are identical except for the name.
 *
 * Always returns 0 as there are no useful return values to check.
 */
static int
_rekey_mdt_stats (shash_t *s, char *key, void *empty)
{
    char *p, *strip = "mds_";

    /* Bail unless the substring to strip exists at the beginning of the key. */
    if (!(p = strstr (key, strip)) || key != p)
        return 0;

    memmove(p, p + strlen (strip), strlen (p + strlen (strip)));
    p[strlen (p + strlen (strip))] = '\0';

    return 0;
}

/*
 * Lustre 2.x seems to have lost many aggregate MDop stats (e.g. open, close,
 * mkdir, mknod, etc.), so we'll try to recreate that functionality by
 * aggregating the per-client-export MDT stats ourselves.
 */
static int
_aggregate_mdt_export_stats (pctx_t ctx, char *mdt_name, hash_t h)
{
    int ret = -1;
    List l = list_create ((ListDelF)free);
    ListIterator itr = NULL;
    char *name, *mdt_dir = _find_mdt_dir (ctx);

    ret = proc_lustre_mdt_exportlist (ctx, mdt_name, &l);

    /* Don't fail if there are no exports -- just skip collection. */
    if ((ret < 0 && errno == ENOENT) || list_count (l) == 0) {
        ret = 0;
        goto done;
    } else if (ret < 0) {
        goto done;
    }

    itr = list_iterator_create (l);
    while ((name = list_next (itr))) {
        if ((ret = proc_openf (ctx, PROC_FS_LUSTRE_MDT_EXPORT_STATS,
                               mdt_dir, mdt_name, name)) < 0) {
#if NONFATAL_MISSING_MDT_EXPORT_STATS
            if (errno == ENOENT)
                ret = 0;
#endif
            goto done;
        }
        if ((ret = _hash_aggregate_stats (ctx, h)) < 0)
            goto done;
        proc_close (ctx);
    }
done:
    if (itr)
        list_iterator_destroy (itr);
    list_destroy (l);
    return ret;
}

int
proc_lustre_hashstats (pctx_t ctx, char *name, hash_t *hp)
{
    hash_t h = NULL;
    int ret = -1;
    int lustre_version = _packed_lustre_version (ctx);
    char *stats;

    if ((ret = _build_osd_stats_path (ctx, name, &stats)) < 0)
        goto done;

    h = hash_create (STATS_HASH_SIZE, (hash_key_f)hash_key_string,
                    (hash_cmp_f)strcmp, (hash_del_f)_destroy_shash);

    ret = proc_openf (ctx, stats, name);
    ret = _hash_stats (ctx, h);
    proc_close (ctx);
    free (stats);

    if (ret < 0)
        goto done;

    if (strstr (name, "-MDT")) {
        if (lustre_version >= LUSTRE_2_0) {
            /* 2.x prior to 2.0.56 was missing aggregate MDT stats. */
            if (lustre_version < PACKED_VERSION (2,0,56,0))
                if ((ret = _aggregate_mdt_export_stats (ctx, name, h)) < 0)
                    goto done;

            /* Fix MDT stats names */
            hash_for_each (h, (hash_arg_f)_rekey_mdt_stats, NULL);
        }
    }
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
    char *tmplr = NULL;
    int ret = -1;

    if (strstr (name, "-OST")) {
        ret = proc_openf (ctx, PROC_FS_LUSTRE_OST_RECOVERY_STATUS, name);
    } else if (strstr (name, "-MDT")) {
        if ((ret = _build_mdt_path (ctx, PROC_FS_LUSTRE_MDT_RECOVERY_STATUS,
                                    &tmplr)) < 0)
            goto done;
        ret = proc_openf (ctx, tmplr, name);
    } else {
        errno = EINVAL;
    }
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

    if (tmplr)
        free (tmplr);
    return ret;
}

/* borrow our friend _hash_stats() to get the version string */
static int
_read_lustre_version_string(pctx_t ctx, char **version_string)
{
    int ret = -1;
    hash_t rh = NULL;
    shash_t *version;

    if ((ret = proc_openf (ctx, PROC_FS_LUSTRE_VERSION)) < 0)
        goto done;

    rh = hash_create (STATS_HASH_SIZE, (hash_key_f)hash_key_string,
                      (hash_cmp_f)strcmp, (hash_del_f)_destroy_shash);

    ret = _hash_stats (ctx, rh);

    proc_close (ctx);

    if (!(version = hash_find (rh, "lustre:"))) {
        ret = -1;
        goto done;
    }

    if (!(*version_string = strdup(version->val)))
        msg_exit ("out of memeory");

    ret = 0;
done:
    if (rh)
        hash_destroy(rh);
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
    int ret = -1;

    /* Zero the counters here to avoid returning uninitialized values
       if the requested key doesn't exist in Lustre stats. */
    if (countp)
        *countp = 0;
    if (minp)
        *minp = 0;
    if (maxp)
        *maxp = 0;
    if (sump)
        *sump = 0;
    if (sumsqp)
        *sumsqp = 0;

    if (!(s = hash_find (stats, key))) {
        errno = EINVAL;
        goto done;
    }
    ret = _parse_stat_node (s, countp, minp, maxp, sump, sumsqp);
done:
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

    if (proc_gets (ctx, PROC_SYS_LNET_ROUTES, buf, sizeof (buf)) < 0) {
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

static histogram_t *
histogram_create (void)
{
    histogram_t *h;

    if (!(h = malloc (sizeof (histogram_t))))
        msg_exit ("out of memory");
    memset (h, 0, sizeof (histogram_t));

    return h;
}

void
histogram_destroy (histogram_t *h)
{
    free (h->bin);
    free (h);
}

static void
histogram_add (histogram_t *h, uint64_t x, uint64_t yr, uint64_t yw)
{
    int i;

    for (i = 0; i < h->bincount; i++) {
        if (h->bin[i].x == x)
            break;
    }
    if (i == h->bincount) {
        int newsize = (i + 1) * sizeof (h->bin[0]);

        if (i == 0)
            h->bin = malloc (newsize);
        else
            h->bin = realloc (h->bin, newsize);
        if (!h->bin)
            msg_exit ("out of memory");
        memset (&h->bin[i], 0, sizeof(h->bin[i]));
        h->bincount++;
    }
    h->bin[i].x = x;
    h->bin[i].yr += yr;
    h->bin[i].yw += yw;
}

static int
_cmp_bin (const void *a1, const void *a2)
{
    const histent_t *h1 = a1;
    const histent_t *h2 = a2;

    return (h1->x < h2->x ? -1 :
            h1->x > h2->x ? 1 : 0);
}

static void
histogram_sort (histogram_t *h)
{
    qsort (h->bin, h->bincount, sizeof (h->bin[0]), _cmp_bin);
}

/* Seek to desired section of brw_stats file.
 */
static int
_brw_seek (pctx_t ctx, brw_t t)
{
    char line[256];
    const char *s = "";
    int ret;

    switch (t) {
        case BRW_RPC:
            s = "pages per bulk r/w";
            break;
        case BRW_DISPAGES:
            s = "discontiguous pages";
            break;
        case BRW_DISBLOCKS:
            s = "discontiguous blocks";
            break;
        case BRW_FRAG:
            s = "disk fragmented I/Os";
            break;
        case BRW_FLIGHT:
            s = "disk I/Os in flight";
            break;
        case BRW_IOTIME:
            s = "I/O time (1/1000s)";
            break;
        case BRW_IOSIZE:
            s = "disk I/O size";
            break;
    }
    while ((ret = proc_gets (ctx, NULL, line, sizeof (line))) >= 0) {
        if (!strncmp (line, s, strlen (s)))
            break;
    }
    return ret;
}

static int
_brw_parse (pctx_t ctx, histogram_t **hp)
{
    const char *fmt = "%15[^:]: %"PRIu64" %*d %*d | %"PRIu64" %*d %*d";
    char s[16];
    uint64_t r, w, x;
    histogram_t *h = histogram_create ();
    char *endptr;
    int ret = -1;

    while ((ret = proc_scanf (ctx, NULL, fmt, s, &r, &w) == 3)) {
        x = strtoul (s, &endptr, 10);
        switch (*endptr) {
            case 'K' :
                x *= 1024;
                break;
            case 'M' :
                x *= (1024*1024);
                break;
            default:
                break;
        }
        histogram_add (h, x, r, w);
    }
    if (ret != 3 && errno == 0) /* treat EOF/stanza end as success */
        ret = 0;
    if (ret == 0) {
        histogram_sort (h);
        *hp = h;
    } else
        histogram_destroy (h);
    return ret;
}

/* Parse section [t] of brw_stats file into histogram stored in [histp].
 * N.B. lustre populates section headings in proc file, but bins are not
 * shown if empty.
 */
int
proc_lustre_brwstats (pctx_t ctx, char *name, brw_t t, histogram_t **hp)
{
    int ret = -1;
    histogram_t *h = NULL;

    if (strstr (name, "-OST"))
        ret = proc_openf (ctx, PROC_FS_LUSTRE_OST_BRW_STATS, name);
    else 
        errno = EINVAL;
    if (ret < 0)
        goto done;
    ret = _brw_seek (ctx, t);
    if (ret == 0)
        ret = _brw_parse (ctx, &h);
    proc_close (ctx);
done:
    if (ret == 0)
        *hp = h;
    return ret;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

