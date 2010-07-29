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

#include <cerebro.h>
#include <cerebro/cerebro_metric_module.h>

#include "proc.h"
#include "lmt.h"
#include "lmtconf.h"

#define METRIC_NAME         "lmt_router"
#define METRIC_FLAGS        (CEREBRO_METRIC_MODULE_FLAGS_SEND_ON_PERIOD)

static int
_get_metric_value (unsigned int *metric_value_type,
                   unsigned int *metric_value_len,
                   void **metric_value)
{
    pctx_t ctx = proc_create ("/proc");
    char *buf = xmalloc (CEREBRO_MAX_DATA_STRING_LEN);
    int retval = -1;

    if (lmt_router_string_v3 (ctx, buf, CEREBRO_MAX_DATA_STRING_LEN) < 0)
        goto done; 
    *metric_value_type = CEREBRO_DATA_VALUE_TYPE_STRING;
    *metric_value_len = strlen (buf) + 1;
    *metric_value = buf;
    retval = 0;
done:
    proc_destroy (ctx);
    if (retval < 0)
        free (buf);
    return retval;
}

static int
_send_message_function_pointer (Cerebro_metric_send_message fp)
{
    return 0;
}

static Cerebro_metric_thread_pointer
_get_metric_thread (void)
{
    return NULL;
}

static int
_destroy_metric_value (void *val)
{
    free (val);
    return 0;
}

static int
_get_metric_flags (u_int32_t *flags)
{
    *flags = METRIC_FLAGS;
    return 0;
}

static int
_get_metric_period (int *period)
{
    *period = LMT_UPDATE_INTERVAL;
    return 0;
}

static char *
_get_metric_name (void)
{
    return METRIC_NAME;
}

static int
_cleanup (void)
{
    return 0;
}

static int
_setup (void)
{
    lmt_conf_init (0, NULL);
    return 0;
}

static int
_interface_version(void)
{
    return CEREBRO_METRIC_INTERFACE_VERSION;
}

struct cerebro_metric_module_info metric_module_info =
{
    .metric_module_name             = METRIC_NAME,
    .interface_version              = _interface_version,
    .setup                          = _setup,
    .cleanup                        = _cleanup,
    .get_metric_name                = _get_metric_name,
    .get_metric_period              = _get_metric_period,
    .get_metric_flags               = _get_metric_flags,
    .get_metric_value               = _get_metric_value,
    .destroy_metric_value           = _destroy_metric_value,
    .get_metric_thread              = _get_metric_thread,
    .send_message_function_pointer  = _send_message_function_pointer,
};

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
