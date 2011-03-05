/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>
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

#include <sys/types.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <errno.h>
#include <stdarg.h>
#include <limits.h> /* PATH_MAX */
#include <string.h>
#include <inttypes.h>
#include <assert.h>
#ifndef __USE_ISOC99
#define __USE_ISOC99 /* enable vfscanf prototype */
#endif
#include <stdio.h>

#include "error.h"

#include "proc.h"

#define PCTX_MAGIC  0xf00f5542

struct proc_ctx_struct {
	int	    pctx_magic;
    char    pctx_root[PATH_MAX];
    char    *pctx_path;
    int     pctx_pathlen;
    FILE    *pctx_fp;
    DIR     *pctx_dp;    
    void    *pctx_stat_pvt;    
};

pctx_t
proc_create (const char *root)
{
    pctx_t  ctx;

    if (!(ctx = malloc (sizeof (*ctx))))
        msg_exit ("out of memory");
    snprintf (ctx->pctx_root, sizeof (ctx->pctx_root), "%s/", root);
    ctx->pctx_path = ctx->pctx_root + strlen (ctx->pctx_root);
    ctx->pctx_pathlen = sizeof (ctx->pctx_root) - strlen (ctx->pctx_root);
    ctx->pctx_fp = NULL;
    ctx->pctx_dp = NULL;
    ctx->pctx_stat_pvt = NULL;
    ctx->pctx_magic = PCTX_MAGIC;

    return ctx;
}

void
proc_destroy (pctx_t ctx)
{
    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp && !ctx->pctx_dp);
    ctx->pctx_magic = 0;
    free (ctx);
}

static int
_open (pctx_t ctx)
{
    if ((ctx->pctx_dp = opendir (ctx->pctx_root)))
        return 0;
    if (errno != ENOTDIR)
        return -1;
    errno = 0;
    if (!(ctx->pctx_fp = fopen (ctx->pctx_root, "r"))) {
        /* fopen sets errno on failure */
        return -1;
    }
    return 0;
}

int
proc_open (pctx_t ctx, const char *path)
{
    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp && !ctx->pctx_dp);

    snprintf (ctx->pctx_path, ctx->pctx_pathlen, "%s", path);
    return _open (ctx);
}

static int
proc_vopenf (pctx_t ctx, const char *fmt, va_list ap)
{
    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp && !ctx->pctx_dp);

    (void)vsnprintf (ctx->pctx_path, ctx->pctx_pathlen, fmt, ap);
    return _open (ctx);
}

int
proc_openf (pctx_t ctx, const char *fmt, ...)
{
    va_list ap;
    int ret;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp && !ctx->pctx_dp);

    va_start (ap, fmt);
    ret = proc_vopenf (ctx, fmt, ap);
    va_end (ap);

    return ret;
}

void
proc_close (pctx_t ctx)
{
    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (ctx->pctx_fp || ctx->pctx_dp);

    if (ctx->pctx_fp)
        (void)fclose (ctx->pctx_fp);
    else
        (void)closedir (ctx->pctx_dp);
    ctx->pctx_fp = NULL;
    ctx->pctx_dp = NULL;
}

static int
proc_vscanf (pctx_t ctx, const char *path, const char *fmt, va_list ap)
{
    int n = -1;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    if (path) {
        if (proc_open (ctx, path) < 0)
            return -1;
    }
    n = vfscanf (ctx->pctx_fp, fmt, ap);
    if (path)
        proc_close (ctx);
    return n;
}

int
proc_scanf (pctx_t ctx, const char *path, const char *fmt, ...)
{
    va_list ap;
    int n;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    va_start (ap, fmt);
    n = proc_vscanf (ctx, path, fmt, ap);
    va_end (ap);

    return n;
}

/* Returns -1 with errno == 0 for EOF
 * Trailing newlines are trimmed in the result.
 */
int
proc_gets (pctx_t ctx, const char *path, char *buf, int len)
{
    int i, ret = 0;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    if (path) {
        if (proc_open (ctx, path) < 0)
            return -1;
    }
    errno = 0;
    if (!fgets (buf, len, ctx->pctx_fp))
        ret = -1;
    if (path)
        proc_close (ctx);
    if (ret == 0 && (i = strlen (buf)) > 0 && buf[i - 1] == '\n')
        buf[i - 1] = '\0'; 
    return ret;
}

int
proc_readdir (pctx_t ctx, proc_readdir_flag_t flag, char **namep)
{
    struct dirent *d;
    char *name = NULL;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp);
    assert (ctx->pctx_dp);

    while ((d = readdir (ctx->pctx_dp))) { 
        if (d->d_name[0] == '.') /* ignore ".", "..", ".svn", etc */
            continue;
        if ((flag & PROC_READDIR_NODIR) && d->d_type == DT_DIR)
            continue;
        if ((flag & PROC_READDIR_NOFILE) && d->d_type != DT_DIR)
            continue;
        if (!(name = strdup (d->d_name)))
            msg_exit ("out of memory");
        break;                    
    }
    if (!d || !name)
        return -1;
    *namep = name;
    return 0;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

