/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module by Michael MacDonald <mjmac@whamcloud.com>
 *  UCRL-CODE-232438 All Rights Reserved.
 *
 *  This file is part of the Lustre Monitoring Tool.
 *  For details, see http://github.com/chaos/lmt.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the license, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the terms and conditions of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see
 *  http://www.gnu.org/licenses.
 *****************************************************************************/

/* tversion.c - test parsing of /proc/fs/lustre/version */

#if HAVE_CONFIG_H
#include "config.h"
#endif
#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <inttypes.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "lustre.h"

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    int major, minor, patch, fix;

    err_init (argv[0]);
    if (argc != 2)
        msg_exit ("missing proc argument");

    ctx = proc_create (argv[1]);

    if (proc_fs_lustre_version (ctx, &major, &minor, &patch, &fix) < 0)
        err_exit ("proc_fs_lustre_version");
    msg ("proc_fs_lustre_version:  major: %d, minor: %d, patch: %d, fix: %d",
         major, minor, patch, fix);

    proc_destroy (ctx);

    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
