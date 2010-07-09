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

#include <cerebro/cerebro_monitor_module.h>

#include "proc.h"
#include "lmt.h"
#include "lmtconf.h"

#define MONITOR_NAME            "lmt_mysql"
#define METRIC_NAMES            "lmt_mdt,lmt_ost,lmt_router"
#define LEGACY_METRIC_NAMES     "lmt_oss,lmt_mds"

static int
_setup (void)
{
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
    const char *errstr = NULL; 
    char *s;
    float vers;
    int result = 0;

    if (!(s = malloc (metric_value_len + 1))) {
        cerebro_err_output ("out of memory");
        goto done;
    }
    memcpy (s, metric_value, metric_value_len);
    s[metric_value_len] = '\0';

    if (sscanf (s, "%f;", &vers) != 1) {
        cerebro_err_output ("error parsing metric version");
        goto done;
    }
    /* current metrics */
    if (!strcmp (metric_name, "lmt_ost") && vers == 2) {
        result = lmt_db_insert_ost_v2 (s, &errstr);
    } else if (!strcmp (metric_name, "lmt_mdt") && vers == 1) {
        result = lmt_db_insert_mdt_v1 (s, &errstr);
    } else if (!strcmp (metric_name, "lmt_router") && vers == 1) {
        result = lmt_db_insert_router_v1 (s, &errstr);

    /* legacy metrics */
    } else if (!strcmp (metric_name, "lmt_mds") && vers == 2) {
        result = lmt_db_insert_mds_v2 (s, &errstr);
    } else if (!strcmp (metric_name, "lmt_oss") && vers == 1) {
        result = lmt_db_insert_oss_v1 (s, &errstr);
    } else if (!strcmp (metric_name, "lmt_ost") && vers == 1) {
        result = lmt_db_insert_ost_v1 (s, &errstr);
    } else
        cerebro_err_output ("%s_v%d: from %s: unknown metric",
                            metric_name, (int)vers, nodename);
    if (result < 0 && errno != ESRCH)
        cerebro_err_debug ("%s_v%d: from %s: %s",
                           metric_name, (int)vers, nodename,
                           errstr ? errstr : strerror (errno));
done:
    if (s)
        free (s);
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
