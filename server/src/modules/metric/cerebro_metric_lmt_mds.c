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
#include <limits.h>
#include <dirent.h>

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_metric_module.h>
#include <cerebro/cerebro_constants.h>
#include <cerebro/cerebro_error.h>
#include "cerebro_metric_lmt_common.h"

#define LMT_METRIC_MDS_MODULE_NAME "lmt_mds"
#define LMT_METRIC_MDS_NAME        "lmt_mds"

static char *lmt_mds_dirname = NULL;

static char *
lmt_metric_mds_get_name(void)
{
	return LMT_METRIC_MDS_NAME;
}

static int
lmt_metric_mds_get_file_usage(char *usagestr, int buflen)
{
        DIR *dirp;
        struct dirent *dp;
        char dirname[PATH_MAX+1];
        int bufleft = PATH_MAX+1;
        char *tmp = NULL;
        int rv = 0;

        if ((dirp = opendir(LMT_MDS_DIRNAME)) == NULL) {
                cerebro_err_debug("couldn't open '%s'", LMT_MDS_DIRNAME);
                return CEREBRO_ERR_INTERNAL;
        }

        do {
                errno = 0;
                if ((dp = readdir(dirp)) != NULL) {
                        if (strncmp(dp->d_name, ".", 2) == 0  ||
                            strncmp(dp->d_name, "..", 3) == 0 ||
                            dp->d_type != DT_DIR) {
                                continue;
                        }

                        if (dp->d_type != DT_DIR) {
                                continue;
                        }

                        tmp = dirname;
                        rv = common_metric_lmt_strncpy(&tmp, LMT_MDS_DIRNAME, &bufleft);
                        if (rv) {
                                cerebro_err_debug("problem copying '%s' to buffer", LMT_MDS_DIRNAME);
                                rv = CEREBRO_ERR_INTERNAL;
                                goto err;
                        }
                        rv = common_metric_lmt_strncpy(&tmp, LMT_PATH_SEPARATOR, &bufleft);
                        if (rv) {
                                cerebro_err_debug("problem copying '%s' to buffer", LMT_PATH_SEPARATOR);
                                rv = CEREBRO_ERR_INTERNAL;
                                goto err;
                        }
                        rv = common_metric_lmt_strncpy(&tmp, dp->d_name, &bufleft);
                        if (rv) {
                                cerebro_err_debug("problem copying '%s' to buffer", dp->d_name);
                                rv = CEREBRO_ERR_INTERNAL;
                                goto err;
                        }
                        rv = common_metric_lmt_get_file_usage(dirname, usagestr, buflen);
                        break;
                }
        } while (dp != NULL);

err:
        (void) closedir(dirp);
        return rv;
}

int
lmt_metric_mds_setup(void)
{
        DIR *dirp = NULL;
        struct dirent *dp = NULL;
        int rc = 0;
        int lmt_mds_dirnamesize = 0;
        char *tmp = NULL;

        if (lmt_mds_dirname != NULL) {
                cerebro_err_debug("dirname should not be set");
                return 0;
        }

        /* Open the mds directory */
        if ((dirp = opendir(LMT_MDS_DIRNAME)) == NULL) {
                cerebro_err_debug("couldn't open '%s'", LMT_MDS_DIRNAME);
                return 0;
        }

nextentry:
        if ((dp = readdir(dirp)) != NULL) {
                if (strncmp(dp->d_name, ".", 2) == 0  ||
                    strncmp(dp->d_name, "..", 3) == 0 ||
                    dp->d_type != DT_DIR) {
                        goto nextentry;
                }
        }

        /* This should not happen */
        if (dp == NULL) {
                cerebro_err_debug("could not read mds directory.");
                rc = 0;
                goto err;
        }

        lmt_mds_dirnamesize = strlen(LMT_MDS_DIRNAME) +
                              strlen(dp->d_name) +
                              strlen(LMT_PATH_SEPARATOR) +
                              1;
        lmt_mds_dirname = malloc(sizeof(char) * lmt_mds_dirnamesize);
        if (lmt_mds_dirname == NULL) {
                rc = 0;
                goto err;
        }
        memset(lmt_mds_dirname, 0, sizeof(char) * lmt_mds_dirnamesize);

        tmp = lmt_mds_dirname;
        strcpy(tmp, LMT_MDS_DIRNAME);
        tmp += strlen(LMT_MDS_DIRNAME);
        strcpy(tmp, LMT_PATH_SEPARATOR);
        tmp += strlen(LMT_PATH_SEPARATOR);
        strcpy(tmp, dp->d_name);

err:
        /* Currently we want this to always succeed, since a failure
         * in the setup will cause cerebro to fail the module load.*/
        (void) closedir(dirp);
        return rc;
}

int
lmt_metric_mds_cleanup(void)
{
        if (lmt_mds_dirname != NULL) {
                free(lmt_mds_dirname);
                lmt_mds_dirname = NULL;
        }

        return 0;
}

static int
lmt_metric_mds_get_value(unsigned int *metric_value_type,
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

        if (!common_metric_lmt_ismds()) {
                /* cerebro_err_debug("not an mds node"); */
		return CEREBRO_ERR_INTERNAL;
        }

        /* If we have not setup the mds path
         * then set it up now. */
        if (lmt_mds_dirname == NULL) {
                lmt_metric_mds_setup();
                if (lmt_mds_dirname == NULL) {
                        cerebro_err_debug("could not setup mds");
                        return CEREBRO_ERR_INTERNAL;
                }
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
        rv = common_metric_lmt_strncpy(&tmp, LMT_MDS_PROTOCOL_VERSION, &bufleft);
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

        /* Get MDS hostname */
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

        /* Get MDS UUID */
        rv = common_metric_lmt_get_uuid(lmt_mds_dirname, lmtbuf, LMT_BUFLEN);
        if (rv != 0) {
                cerebro_err_debug("problem retrieving UUID");
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying UUID to buffer");
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

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                goto cleanup;
        }

        /* Get File Usage */
        rv = lmt_metric_mds_get_file_usage(lmtbuf, LMT_BUFLEN);
        if (rv != 0) {
                cerebro_err_debug("problem retrieving File usage");
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying File usage to buffer");
                goto cleanup;
        }

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                goto cleanup;
        }

        /* Get Filesystem Operations */
        rv = common_metric_lmt_get_filesystem_operations(LMT_PROC_MDS_STAT_PATH, lmtbuf, LMT_BUFLEN);
        if (rv != 0) {
                cerebro_err_debug("problem retrieving Filesystem Operations");
                goto cleanup;
        }
        rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
        if (rv != 0) {
                cerebro_err_debug("problem copying Filesystem Operations to buffer");
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
struct cerebro_metric_module_info lmt_metric_mds_module_info =
#else  /* !WITH_STATIC_MODULES */
struct cerebro_metric_module_info metric_module_info =
#endif /* !WITH_STATIC_MODULES */
{
	LMT_METRIC_MDS_MODULE_NAME,
	&common_metric_lmt_interface_version,
	&lmt_metric_mds_setup,
	&lmt_metric_mds_cleanup,
	&lmt_metric_mds_get_name,
	&common_metric_lmt_get_period,
	&common_metric_lmt_get_flags,
	&lmt_metric_mds_get_value,
	&common_metric_lmt_destroy_metric_value_free_value,
	&common_metric_lmt_get_metric_thread_null,
	&common_metric_lmt_send_message_function_pointer_unused,
};

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
