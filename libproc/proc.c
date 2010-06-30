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

#include <sys/types.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <errno.h>
#include <stdarg.h>
#include <limits.h> /* PATH_MAX */
#include <string.h>
#include <stdint.h>
#include <assert.h>
#define __USE_ISOC99 /* enable vfscanf prototype */
#include <stdio.h>

#include "proc.h"

/* Note: unless otherwise noted, functions return -1 on error (errno set),
 * 0 or greater on success.  Scanf functions return number of matches.
 * On EOF, functions return -1 with errno clear.
 */

#define PCTX_MAGIC  0xf00f5542

struct proc_ctx_struct {
	int	    pctx_magic;
    char    pctx_root[PATH_MAX];
    char    *pctx_path;
    int     pctx_pathlen;
    FILE    *pctx_fp;
    DIR     *pctx_dp;    
};

pctx_t
proc_create (const char *root)
{
    pctx_t  ctx = malloc (sizeof (*ctx));

    if (!ctx) {
        errno = ENOMEM;
        return NULL;
    }
    snprintf (ctx->pctx_root, sizeof (ctx->pctx_root), "%s/", root);
    ctx->pctx_path = ctx->pctx_root + strlen (ctx->pctx_root);
    ctx->pctx_pathlen = sizeof (ctx->pctx_root) - strlen (ctx->pctx_root);
    ctx->pctx_fp = NULL;
    ctx->pctx_dp = NULL;
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
    int error = -1;

    if (!(ctx->pctx_dp = opendir (ctx->pctx_root)))
        if (errno == ENOTDIR) {
            errno = 0;
            ctx->pctx_fp = fopen (ctx->pctx_root, "r");
        }
    if (ctx->pctx_dp || ctx->pctx_fp)
        error = 0; 

    return error;
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
    int error;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (!ctx->pctx_fp && !ctx->pctx_dp);

    va_start (ap, fmt);
    error = proc_vopenf (ctx, fmt, ap);
    va_end (ap);

    return error;
}

void
proc_close (pctx_t ctx)
{
    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (ctx->pctx_fp || ctx->pctx_dp);

    if (ctx->pctx_fp)
        fclose (ctx->pctx_fp);
    else
        closedir (ctx->pctx_dp);
    ctx->pctx_fp = NULL;
    ctx->pctx_dp = NULL;
}

static int
proc_vscanf (pctx_t ctx, const char *path, const char *fmt, va_list ap)
{
    int error = -1;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    if (!path || (error = proc_open (ctx, path)) == 0) {
        error = vfscanf (ctx->pctx_fp, fmt, ap);
        if (path)
            proc_close (ctx);
    }
    return error;
}

int
proc_scanf (pctx_t ctx, const char *path, const char *fmt, ...)
{
    va_list ap;
    int error;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    va_start (ap, fmt);
    error = proc_vscanf (ctx, path, fmt, ap);
    va_end (ap);

    return error;
}

int
proc_gets (pctx_t ctx, const char *path, char *buf, int len)
{
    int i, error = 0;

    assert (ctx->pctx_magic == PCTX_MAGIC);
    assert (path || ctx->pctx_fp);
    assert (!ctx->pctx_dp);

    if (!path || (error = proc_open (ctx, path)) == 0) {
        if (!fgets (buf, len, ctx->pctx_fp))
            error = -1;
        if (path)
            proc_close (ctx);
        if (error >= 0 && (i = strlen (buf)) > 0 && buf[i - 1] == '\n')
            buf[i - 1] = '\0'; 
    }
    return error;
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
        if (!strcmp(d->d_name, "..") || !strcmp(d->d_name, "."))
            continue;
        if ((flag & PROC_READDIR_NODIR) && d->d_type == DT_DIR)
            continue;
        if ((flag & PROC_READDIR_NOFILE) && d->d_type != DT_DIR)
            continue;
        if (!(name = strdup (d->d_name)))
            errno = ENOMEM;
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

