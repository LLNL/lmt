/*****************************************************************************
 *  Copyright (C) 2007 Lawrence Livermore National Security, LLC.
 *  This module was written by Jim Garlick <garlick@llnl.gov>
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

#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <inttypes.h>

#include "list.h"
#include "hash.h"
#include "error.h"
#include "proc.h"
#include "lustre.h"

#include "lmt.h"
#include "util.h"
#include "lmtconf.h"

int
get_recovstr (pctx_t ctx, char *name, char *s, int len)
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

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
