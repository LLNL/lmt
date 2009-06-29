/*********************************************************************************
 * Copyright (C) 2007, Lawrence Livermore National Security, LLC.
 * Copyright (c) 2007, The Regents of the University of California.
 * Produced at the Lawrence Livermore National Laboratory.
 * Written by C. Morrone, H. Wartens, P. Spencer, N. O'Neill, J. Long
 * UCRL-CODE-232438.
 * All rights reserved.
 *
 * This file is part of Lustre Monitoring Tools, version 2. 
 * For details, see http://sourceforge.net/projects/lmt/.
 *
 * Please also read Our Notice and GNU General Public License, available in the
 * COPYING file in the source distribution.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License (as published by the Free Software
 * Foundation) version 2, dated June 1991.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the terms and conditions of the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 ********************************************************************************/

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

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_metric_module.h>
#include <cerebro/cerebro_constants.h>
#include <cerebro/cerebro_error.h>
#include "cerebro_metric_lmt_common.h"

#define LMT_METRIC_OSS_MODULE_NAME "lmt_oss"
#define LMT_METRIC_OSS_NAME        "lmt_oss"

static char *
lmt_metric_oss_get_name(void)
{
	return LMT_METRIC_OSS_NAME;
}

static int
lmt_metric_oss_get_value(unsigned int *metric_value_type,
			 unsigned int *metric_value_len,
			 void **metric_value)
{
	char *buf = NULL, *tmp = NULL;
        char lmtbuf[LMT_BUFLEN];
        int buflen = 0, bufleft = 0;
        struct utsname uts;
	int rv = -1;
	
	if (!metric_value_type || !metric_value_len || !metric_value) {
		cerebro_err_debug("invalid parameters");
		return CEREBRO_ERR_PARAMETERS;
	}

        if (!common_metric_lmt_isoss()) {
                /* cerebro_err_debug("not an oss node"); */
                return CEREBRO_ERR_INTERNAL;
        }

        buflen = CEREBRO_MAX_DATA_STRING_LEN / sizeof(char);
        bufleft = buflen;
        buf = malloc(sizeof(char) * buflen);
        if (!buf) {
                cerebro_err_debug("insufficient memory");
                return CEREBRO_ERR_OUTMEM;
        }
        memset(buf, 0, sizeof(char) * buflen);
        tmp = buf;

        /* Insert Protocol Version */
        rv = common_metric_lmt_strncpy(&tmp, LMT_OSS_PROTOCOL_VERSION, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying protocol version to buffer");
                goto cleanup;
        }

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                goto cleanup;
        }

        /* Get OSS hostname */
        errno = 0;
        if (uname(&uts) < 0) {
                cerebro_err_debug("problem retrieving hostname");
                rv = errno;
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, uts.nodename, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying hostname to buffer");
                goto cleanup;
        }

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                goto cleanup;
        }

        /* Get CPU Usage */
        rv = common_metric_lmt_get_cpu_usage(lmtbuf, LMT_BUFLEN);
        if (rv != 0) {
                cerebro_err_debug("problem retrieving CPU usage");
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying CPU usage to buffer");
                goto cleanup;
        }

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                goto cleanup;
        }

        /* Get Memory Usage */
        rv = common_metric_lmt_get_memory_usage(lmtbuf, LMT_BUFLEN);
        if (rv != 0) {
                cerebro_err_debug("problem retrieving Memory usage");
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying Memory usage to buffer");
                goto cleanup;
        }

	*metric_value_type = CEREBRO_DATA_VALUE_TYPE_STRING;
	*metric_value_len = buflen - bufleft + 1;
	*metric_value = malloc(sizeof(char) * (*metric_value_len));
	if (*metric_value == NULL) {
		cerebro_err_debug("insufficient memory");
		goto cleanup;
	}
	strncpy(*metric_value, buf, (*metric_value_len));
	rv = 0;

cleanup:
	if (buf != NULL) {
		free(buf);
		buf = NULL;
	}

	if (rv != 0 && (*metric_value) != NULL) {
		free(*metric_value);
		*metric_value = NULL;
                *metric_value_type = CEREBRO_DATA_VALUE_TYPE_NONE;
                *metric_value_len = 0;
	}

	return rv;
}

#if WITH_STATIC_MODULES
struct cerebro_metric_module_info lmt_metric_oss_module_info =
#else  /* !WITH_STATIC_MODULES */
struct cerebro_metric_module_info metric_module_info =
#endif /* !WITH_STATIC_MODULES */
{
	LMT_METRIC_OSS_MODULE_NAME,
	&common_metric_lmt_interface_version,
	&common_metric_lmt_setup_do_nothing,
	&common_metric_lmt_cleanup_do_nothing,
	&lmt_metric_oss_get_name,
	&common_metric_lmt_get_period,
	&common_metric_lmt_get_flags,
	&lmt_metric_oss_get_value,
	&common_metric_lmt_destroy_metric_value_free_value,
	&common_metric_lmt_get_metric_thread_null,
	&common_metric_lmt_send_message_function_pointer_unused,
};

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
