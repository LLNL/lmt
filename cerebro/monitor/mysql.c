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
#include <stdarg.h>
#endif /* STDC_HEADERS */
#include <errno.h>
#include <stdint.h>

#include <cerebro.h>
#include <cerebro/cerebro_monitor_module.h>

#include "error.h"

#include "proc.h"

#include "lmt.h"
#include "lmtconf.h"

#define MONITOR_NAME            "lmt_mysql"
#define METRIC_NAMES            "lmt_mdt,lmt_ost,lmt_router"
#define LEGACY_METRIC_NAMES     "lmt_oss,lmt_mds"

static int
_setup (void)
{
    lmt_log_init (MONITOR_NAME);
    lmt_log_set_dest ("cerebro");
    lmt_conf_init (0, NULL);
    return 0;
}

static int
_cleanup (void)
{
    return 0;
}

static char *
_metric_names (void)
{
    return METRIC_NAMES","LEGACY_METRIC_NAMES;
}

static int
_interface_version (void)
{
    return CEREBRO_MONITOR_INTERFACE_VERSION;
}

static int 
_metric_update (const char *nodename,
              const char *metric_name,
              unsigned int metric_value_type,
              unsigned int metric_value_len,
              void *metric_value)
{
    char *s = metric_value;
    float vers;
    int result = 0;

    if (metric_value_type != CEREBRO_DATA_VALUE_TYPE_STRING) {
        msg ("%s: %s: incorrect metric_type: %d", nodename, metric_name,
                                                  metric_value_type);
        goto done;
    }
    if (sscanf (s, "%f;", &vers) != 1) {
        msg ("%s: %s: error parsing metric version", nodename, metric_name);
        goto done;
    }
    /* current metrics */
    if (!strcmp (metric_name, "lmt_ost") && vers == 2) {
        result = lmt_db_insert_ost_v2 (s);
    } else if (!strcmp (metric_name, "lmt_mdt") && vers == 1) {
        result = lmt_db_insert_mdt_v1 (s);
    } else if (!strcmp (metric_name, "lmt_router") && vers == 1) {
        result = lmt_db_insert_router_v1 (s);

    /* legacy metrics */
    } else if (!strcmp (metric_name, "lmt_mds") && vers == 2) {
        result = lmt_db_insert_mds_v2 (s);
    } else if (!strcmp (metric_name, "lmt_oss") && vers == 1) {
        result = lmt_db_insert_oss_v1 (s);
    } else if (!strcmp (metric_name, "lmt_ost") && vers == 1) {
        result = lmt_db_insert_ost_v1 (s);
    } else
        msg ("%s: %s_v%d: unknown metric", nodename, metric_name, (int)vers);
done:
    return 0; 
}

struct cerebro_monitor_module_info monitor_module_info = {
    .monitor_module_name        = MONITOR_NAME,
    .interface_version          = _interface_version,
    .setup                      = _setup,
    .cleanup                    = _cleanup,
    .metric_names               = _metric_names,
    .metric_update              = _metric_update,
};

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
