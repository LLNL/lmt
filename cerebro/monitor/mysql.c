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

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_monitor_module.h>
#include <cerebro/cerebro_constants.h>
#include <cerebro/cerebro_error.h>

#include "proc.h"
#include "lmt.h"

#define MONITOR_NAME            "lmt_mysql"
#define METRIC_NAMES            "lmt_mdt,lmt_ost,lmt_router"
#define LEGACY_METRIC_NAMES     "lmt_oss"

static lmt_dbhandle_t db;

static int
_setup (void)
{
    const char *sqlerr = NULL; 

    if (lmt_initdb (&db, &sqlerr) < 0) {
        cerebro_err_output ("lmt_initdb: %s",
                            sqlerr ? sqlerr : strerror(errno));
        return CEREBRO_ERR_INTERNAL;
    }
    return CEREBRO_ERR_SUCCESS;
}

static int
_cleanup (void)
{
    lmt_finidb (db);
    return CEREBRO_ERR_SUCCESS;
}

static char *
_metric_names (void)
{
    return METRIC_NAMES "," LEGACY_METRIC_NAMES;
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
    const char *sqlerr = NULL; 
    int retval = CEREBRO_ERR_SUCCESS;
    char *s = metric_value;
    char vers[8];

    if (metric_value_len == 0) {
        cerebro_err_output ("metric update called with zero length metric");
        retval = CEREBRO_ERR_INTERNAL;
        goto done;
    }
        
    s[metric_value_len - 1] = '\0';
    if (sscanf (s, "%8s;", vers) != 1) {
        cerebro_err_output ("error parsing version from metric string");
        retval = CEREBRO_ERR_INTERNAL;
        goto done;
    }

    if (!strcmp (metric_name, "lmt_ost") && !strcmp (vers, "3")) {
        if (lmt_ost_updatedb_v3 (db, s, &sqlerr) < 0) {
            cerebro_err_debug ("lmt_ost_updatedb_v3: %s",
                               sqlerr ? sqlerr : strerror (errno));
            retval = CEREBRO_ERR_INTERNAL;
        }
        goto done;
    }

    if (!strcmp (metric_name, "lmt_mdt") && !strcmp (vers, "3")) {
        if (lmt_mdt_updatedb_v3 (db, s, &sqlerr) < 0) {
            cerebro_err_debug ("lmt_mdt_updatedb_v3: %s",
                               sqlerr ? sqlerr : strerror (errno));
            retval = CEREBRO_ERR_INTERNAL;
        }
        goto done;
    }

    if (!strcmp (metric_name, "lmt_router") && !strcmp (vers, "3")) {
        if (lmt_router_updatedb_v3 (db, s, &sqlerr) < 0) {
            cerebro_err_debug ("lmt_router_updatedb_v3: %s",
                               sqlerr ? sqlerr : strerror (errno));
            retval = CEREBRO_ERR_INTERNAL;
        }
        goto done;
    }

    cerebro_err_output ("unknown metric: %s_v%s", metric_name, vers);
    retval = CEREBRO_ERR_INTERNAL;
done:
    return retval; 
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
