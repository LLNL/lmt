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
#include <dirent.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_metric_module.h>
#include <cerebro/cerebro_error.h>
#include "cerebro_metric_lmt_common.h"

struct lmt_fsop_jt_item lmt_fsop_jt[] = LMT_OPERATION_TABLE;

int
common_metric_lmt_interface_version(void)
{
        return CEREBRO_METRIC_INTERFACE_VERSION;
}

int
common_metric_lmt_setup_do_nothing(void)
{
        /* nothing to do */
        return 0;
}

int
common_metric_lmt_cleanup_do_nothing(void)
{
        /* nothing to do */
        return 0;
}

int
common_metric_lmt_get_period(int *period)
{
        if (!period) {
                cerebro_err_debug("invalid parameters");
                return -1;
        }
        
        *period = LMT_PERIOD;
        return 0;
}

int
common_metric_lmt_get_period_none(int *period)
{
        if (!period) {
                cerebro_err_debug("invalid parameters");
                return -1;
        }
        
        *period = LMT_PERIOD_NONE;
        return 0;
}

int
common_metric_lmt_get_flags(u_int32_t *flags)
{
        if (!flags) {
                cerebro_err_debug("invalid parameters");
                return -1;
        }
        
        *flags = CEREBRO_METRIC_MODULE_FLAGS_SEND_ON_PERIOD;
        return 0;
}

int
common_metric_lmt_destroy_metric_value_do_nothing(void *metric_value)
{
        return 0;
}

int
common_metric_lmt_destroy_metric_value_free_value(void *metric_value)
{
        if (!metric_value) {
                cerebro_err_debug("invalid parameters");
                return -1;
        }
        
        free(metric_value);
        return 0;
}

Cerebro_metric_thread_pointer
common_metric_lmt_get_metric_thread_null(void)
{
        return NULL;
}

int
common_metric_lmt_send_message_function_pointer_unused(Cerebro_metric_send_message function_pointer)
{
        return 0;
}

int
common_metric_lmt_get_cpu_usage(char *usagestr, int buflen)
{
        static unsigned long long oldusage = 0;
        static unsigned long long oldtotal = 0;
        unsigned long long newusage = 0;
        unsigned long long newtotal = 0;
        int rv = 0, srv = 0;
        char buf[LMT_BUFLEN];
        unsigned long long val[8];
        float usage;

        memset(usagestr, 0, buflen);

        rv = common_metric_lmt_read_file(LMT_PROC_STAT_PATH, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }
        srv = sscanf(buf, " cpu%*[ ] %llu %llu %llu %llu %llu %llu %llu %llu",
                          &val[0],
                          &val[1],
                          &val[2],
                          &val[3],
                          &val[4],
                          &val[5],
                          &val[6],
                          &val[7]);
        if (srv < 7) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* add     usr    + nice   + sys    + irq    + softirq */
        newusage = val[0] + val[1] + val[2] + val[5] + val[6];
        newtotal = newusage + val[3] + val[4];

        if ((newtotal - oldtotal) == 0) {
                oldusage = newusage;
                oldtotal = newtotal;
                return CEREBRO_ERR_ERRNUMRANGE;
        }

        usage = fabs(((float) (newusage - oldusage)) / ((float) (newtotal - oldtotal)));
        usage = usage * 100.0;
        snprintf(usagestr, buflen, "%f", usage);
        oldusage = newusage;
        oldtotal = newtotal;
        return 0;
}

int
common_metric_lmt_get_memory_usage(char *usagestr, int buflen)
{
        int newusage = 0;
        int newtotal = 0;
        int rv = 0, srv = 0;
        char buf[LMT_BUFLEN];
        int val[2];
        float usage;

        memset(usagestr, 0, buflen);

        rv = common_metric_lmt_read_file(LMT_PROC_MEMINFO_PATH, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }
        srv = sscanf(buf, " MemTotal: %d kB MemFree: %d kB",
                          &val[0],
                          &val[1]);
        if (srv != 2) {
                return CEREBRO_ERR_INTERNAL;
        }

        newusage = val[0] - val[1];
        newtotal = val[0];

        usage = ((float) newusage) / ((float) newtotal);
        usage = usage * 100.0;
        snprintf(usagestr, buflen, "%f", usage);
        return 0;
}

int
common_metric_lmt_get_file_usage(char *dirname, char *usagestr, int buflen)
{
        char filename[PATH_MAX+1];
        int bufleft = PATH_MAX+1;
        char buf[LMT_BUFLEN];
        char *tmp;
        int rv = 0, srv = 0, len = 0;
        unsigned long long bytes = 0;

        memset(usagestr, 0, buflen);

        /* Build filesfree name */
        memset(filename, 0, bufleft);
        tmp = filename;
        rv = common_metric_lmt_strncpy(&tmp, dirname, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", dirname);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_PATH_SEPARATOR, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_PATH_SEPARATOR);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_FILESFREE_NAME, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_FILESFREE_NAME);
                return rv;
        }

        /* Read metric from file */
        rv = common_metric_lmt_read_file(filename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        srv = sscanf(buf, "%llu", &bytes);
        if (srv != 1) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* Add filesfree to usagestr */
        len = snprintf(usagestr, buflen, "%llu", bytes);
        if (len > buflen) {
                return CEREBRO_ERR_INTERNAL;
        }
        usagestr += len;
        buflen -= len;

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                return CEREBRO_ERR_INTERNAL;
        }

        /* Build filestotal name */
        memset(filename, 0, bufleft);
        tmp = filename;
        rv = common_metric_lmt_strncpy(&tmp, dirname, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", dirname);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_PATH_SEPARATOR, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_PATH_SEPARATOR);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_FILESTOTAL_NAME, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_FILESTOTAL_NAME);
                return rv;
        }

        /* Read metric from file */
        rv = common_metric_lmt_read_file(filename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        srv = sscanf(buf, "%llu", &bytes);
        if (srv != 1) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* Add filestotal to usagestr */
        len = snprintf(usagestr, buflen, "%llu", bytes);
        if (len > buflen) {
                return CEREBRO_ERR_INTERNAL;
        }
        usagestr += len;
        buflen -= len;

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                return CEREBRO_ERR_INTERNAL;
        }

        /* Build kbytesfree name */
        memset(filename, 0, bufleft);
        tmp = filename;
        rv = common_metric_lmt_strncpy(&tmp, dirname, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", dirname);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_PATH_SEPARATOR, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_PATH_SEPARATOR);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_KBYTESFREE_NAME, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_KBYTESFREE_NAME);
                return rv;
        }

        /* Read metric from file */
        rv = common_metric_lmt_read_file(filename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        srv = sscanf(buf, "%llu", &bytes);
        if (srv != 1) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* Add kbytesfree to usagestr */
        len = snprintf(usagestr, buflen, "%llu", bytes);
        if (len > buflen) {
                return CEREBRO_ERR_INTERNAL;
        }
        usagestr += len;
        buflen -= len;

        /* Insert Separator */
        rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
        if (rv != 0) {
                cerebro_err_debug("problem copying separator to buffer");
                return CEREBRO_ERR_INTERNAL;
        }

        /* Build kbytestotal name */
        memset(filename, 0, bufleft);
        tmp = filename;
        rv = common_metric_lmt_strncpy(&tmp, dirname, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", dirname);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_PATH_SEPARATOR, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_PATH_SEPARATOR);
                return rv;
        }
        rv = common_metric_lmt_strncpy(&tmp, LMT_KBYTESTOTAL_NAME, &bufleft);
        if (rv) {
                cerebro_err_debug("problem copying '%s' to buffer", LMT_KBYTESTOTAL_NAME);
                return rv;
        }

        /* Read metric from file */
        rv = common_metric_lmt_read_file(filename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        srv = sscanf(buf, "%llu", &bytes);
        if (srv != 1) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* Add kbytestotal to usagestr */
        len = snprintf(usagestr, buflen, "%llu", bytes);
        if (len > buflen) {
                return CEREBRO_ERR_INTERNAL;
        }
        usagestr += len;
        buflen -= len;

        return rv;
}

int
common_metric_lmt_get_lnet_network_usage(char *usagestr, int buflen)
{
        unsigned long long newbytes = 0;
        int rv = 0, srv = 0;
        char buf[LMT_BUFLEN];
        unsigned long long val[11];

        memset(usagestr, 0, buflen);

        rv = common_metric_lmt_read_file(LMT_PROC_LNET_STATS_PATH, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }
        srv = sscanf(buf, "%llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu",
                          &val[0],
                          &val[1],
                          &val[2],
                          &val[3],
                          &val[4],
                          &val[5],
                          &val[6],
                          &val[7],
                          &val[8],
                          &val[9],
                          &val[10]);
        if (srv != 11) {
                return CEREBRO_ERR_INTERNAL;
        }

        /* Note that newbytes is a long so it does
           wrap.  The client application should
           handle this case */
        newbytes = val[9];
        snprintf(usagestr, buflen, "%llu", newbytes);
        return rv;
}

int
common_metric_lmt_get_filesystem_operations(char *procfilename, char *usagestr, int buflen)
{
        static double cached_time = 0.0;
        double new_time = 0.0;
        int rv = 0, srv = 0;
        char metric[LMT_OPERATION_NAME_LENGTH+1];
        char buf[LMT_BUFLEN];
        char *saveptr = NULL, *tok = NULL;
        unsigned long long numsamples;
        unsigned long long val[4];
        int idx = 0, i = 0;
        struct lmt_filesystem_operation fs_op[LMT_NUM_OPERATIONS];

        memset(usagestr, 0, sizeof(char) * buflen);
        memset(buf, 0, sizeof(char) * LMT_BUFLEN);
        memset(fs_op, 0, sizeof(struct lmt_filesystem_operation) * LMT_NUM_OPERATIONS);

        rv = common_metric_lmt_read_file(procfilename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        /* The first line is expected to be the snapshot_time */
        srv = sscanf(buf, "snapshot_time %lf %*s", &new_time);
        if (srv != 1) {
                cerebro_err_debug("problem reading snapshot_time");
                return CEREBRO_ERR_INTERNAL;
        }

        /* If we have reread a value just report it */
        if (new_time == cached_time) {
                cerebro_err_debug("duplicate time");
        }
        cached_time = new_time;

        /* Get the first token from the buffer */
        tok = strtok_r(buf, "\n", &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem tokenizing filesystem operations");
                return CEREBRO_ERR_INTERNAL;
        }

        do {
                memset(metric, 0, sizeof(char) * LMT_OPERATION_NAME_LENGTH+1);
                memset(val, 0, sizeof(unsigned long long) * 4);
                numsamples = 0;
                srv = sscanf(tok, "%" LMT_TO_STR(LMT_OPERATION_NAME_LENGTH) "s "
                                  "%llu samples %*s %llu %llu %llu %llu",
                                  metric,
                                  &numsamples,
                                  &val[0],
                                  &val[1],
                                  &val[2],
                                  &val[3]);
                if (srv < 2) {
                        cerebro_err_output("problem reading metric: %s", tok);
                        return CEREBRO_ERR_INTERNAL;
                }

                /* Set idx to an invalid value */
                idx = -1;

                /* Make sure to place an ordering on all the the items in
                 * the csv output in order according to protocol version */
                for (i = 0; i < LMT_NUM_OPERATIONS; i++) {
                        if (strncmp(metric, lmt_fsop_jt[i].op, LMT_OPERATION_NAME_LENGTH+1) == 0) {
                                idx = i;
                                break;
                        }
                }

                /* We did not find any relevant metric
                 * just assume that another metric was
                 * added without our knowledge.  Ignore it
                 * and continue on */
                if (idx >= 0 && idx < LMT_NUM_OPERATIONS) {
                        /* Copy the relelvant information into the structure */
                        strncpy(fs_op[idx].name, metric, LMT_OPERATION_NAME_LENGTH+1);
                        fs_op[idx].numsamples = numsamples;
                        fs_op[idx].value = val[2];
                        fs_op[idx].sumsquares = val[3];
                }
        } while ((tok = strtok_r(NULL, "\n", &saveptr)) != NULL);

        for (idx = LMT_MDS_OPERATIONS_IDX_START; idx <= LMT_MDS_OPERATIONS_IDX_END; idx++) {

                snprintf(buf, LMT_BUFLEN, "%llu", fs_op[idx].numsamples);
                rv = common_metric_lmt_strncpy(&usagestr, buf, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying numsamples to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
                rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
                snprintf(buf, LMT_BUFLEN, "%llu", fs_op[idx].value);
                rv = common_metric_lmt_strncpy(&usagestr, buf, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying value to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
                rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
                snprintf(buf, LMT_BUFLEN, "%llu", fs_op[idx].sumsquares);
                rv = common_metric_lmt_strncpy(&usagestr, buf, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying sumsquares to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }

                /* Don't print out the last separator */
                if (idx == LMT_MDS_OPERATIONS_IDX_END) {
                        break;
                }

                rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
        }

        return rv;
}

int
common_metric_lmt_get_bandwidth_usage(char *procfilename, char *usagestr, int buflen)
{
        static double cached_time = 0.0;
        double new_time = 0.0;
        int rv = 0, srv = 0;
        char metric[LMT_OPERATION_NAME_LENGTH+1];
        char buf[LMT_BUFLEN];
        char *saveptr = NULL, *tok = NULL;
        unsigned long long numsamples;
        unsigned long long val[4];
        int idx = 0, i = 0;
        struct lmt_filesystem_operation fs_op[LMT_NUM_OPERATIONS];

        memset(usagestr, 0, sizeof(char) * buflen);
        memset(buf, 0, sizeof(char) * LMT_BUFLEN);
        memset(fs_op, 0, sizeof(struct lmt_filesystem_operation) * LMT_NUM_OPERATIONS);

        rv = common_metric_lmt_read_file(procfilename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }

        /* The first line is expected to be the snapshot_time */
        srv = sscanf(buf, "snapshot_time %lf %*s", &new_time);
        if (srv != 1) {
                cerebro_err_debug("problem reading snapshot_time");
                return CEREBRO_ERR_INTERNAL;
        }

        /* If we have reread a value just report it */
        if (new_time == cached_time) {
                cerebro_err_debug("duplicate time");
        }
        cached_time = new_time;

        /* Cheat and use index 0 and index 1 as read and write bytes respectively */
        tok = strtok_r(buf, "\n", &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem tokenizing bandwidth usage");
                return CEREBRO_ERR_INTERNAL;
        }

        do {
                memset(metric, 0, sizeof(char) * LMT_OPERATION_NAME_LENGTH+1);
                memset(val, 0, sizeof(unsigned long long) * 4);
                numsamples = 0;
                srv = sscanf(tok, "%" LMT_TO_STR(LMT_OPERATION_NAME_LENGTH) "s "
                                  "%llu samples %*s %llu %llu %llu %llu",
                                  metric,
                                  &numsamples,
                                  &val[0],
                                  &val[1],
                                  &val[2],
                                  &val[3]);
                if (srv < 2) {
                        cerebro_err_output("problem reading metric: %s", tok);
                        return CEREBRO_ERR_INTERNAL;
                }

                /* Set idx to an invalid value */
                idx = -1;

                /* Make sure to place an ordering on all the the items in
                 * the csv output in order according to protocol version */
                for (i = LMT_OST_BANDWIDTH_IDX_START; i <= LMT_OST_BANDWIDTH_IDX_END; i++) {
                        if (strncmp(metric, lmt_fsop_jt[i].op, LMT_OPERATION_NAME_LENGTH+1) == 0) {
                                idx = i;
                        }
                }

                /* We did not find any relevant metric
                 * just assume that another metric was
                 * added without our knowledge.  Ignore it
                 * and continue on */
                if (idx >= LMT_OST_BANDWIDTH_IDX_START && idx <= LMT_OST_BANDWIDTH_IDX_END) {
                        /* Copy the relelvant information into the structure */
                        strncpy(fs_op[idx].name, metric, LMT_OPERATION_NAME_LENGTH+1);
                        fs_op[idx].numsamples = numsamples;
                        if (numsamples != 0) {
                                fs_op[idx].value = val[2];
                        }
                        fs_op[idx].sumsquares = 0;
                }
        } while ((tok = strtok_r(NULL, "\n", &saveptr)) != NULL);

        for (idx = LMT_OST_BANDWIDTH_IDX_START; idx <= LMT_OST_BANDWIDTH_IDX_END; idx++) {

                snprintf(buf, LMT_BUFLEN, "%llu", fs_op[idx].value);
                rv = common_metric_lmt_strncpy(&usagestr, buf, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying value to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }

                /* Don't print out the last separator */
                if (idx == LMT_OST_BANDWIDTH_IDX_END) {
                        break;
                }

                rv = common_metric_lmt_strncpy(&usagestr, LMT_SEPARATOR, &buflen);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        return CEREBRO_ERR_INTERNAL;
                }
        }

        return rv;
}

int
common_metric_lmt_get_uuid(char *procfiledir, char *usagestr, int buflen)
{
        char buf[LMT_BUFLEN];
        char uuid[LMT_BUFLEN+1];
        char procfilename[PATH_MAX+1];
        char *tmp;
        int rv = 0;
        int templen = strlen(procfiledir) +
                      strlen(LMT_PATH_SEPARATOR) +
                      strlen(LMT_UUID_NAME) + 2;

        if (templen > PATH_MAX) {
                cerebro_err_debug("path is too long.");
                return CEREBRO_ERR_INTERNAL;
        }
        memset(usagestr, 0, buflen);

        tmp = procfilename;
        memset(tmp, 0, templen);
        strcpy(tmp, procfiledir);
        tmp += strlen(procfiledir);
        strcpy(tmp, LMT_PATH_SEPARATOR);
        tmp += strlen(LMT_PATH_SEPARATOR);
        strcpy(tmp, LMT_UUID_NAME);

        rv = common_metric_lmt_read_file(procfilename, buf, LMT_BUFLEN);
        if (rv) {
                return rv;
        }
        memset(uuid, 0, LMT_BUFLEN+1);
        sscanf(buf, "%" LMT_TO_STR(LMT_BUFLEN) "s", uuid);

        /* Remove the _UUID from the UUID */
        tmp = strrchr(uuid, '_');
        if (tmp != NULL) {
                if (strcmp(tmp, "_UUID") == 0) {
                        while (*tmp != '\0') {
                                (*tmp) = '\0';
                        }
                }
        }
        
        rv = common_metric_lmt_strncpy(&usagestr, uuid, &buflen);
        if (rv != 0) {
                cerebro_err_debug("problem copying UUID to buffer");
                return CEREBRO_ERR_INTERNAL;
        }

        return 0;
}

int
common_metric_lmt_ismds(void)
{
        struct stat st;
        int rv = 0;

        errno = 0;
        rv = stat(LMT_PROC_MDS_STAT_PATH, &st);
        if (rv == 0) {
                return 1;
        }

	return 0;
}

int
common_metric_lmt_isoss(void)
{
        int numosts = common_metric_lmt_isost();

        if (numosts > 0)
                return 1;

        return 0;
}

int
common_metric_lmt_isost(void)
{
        DIR *dirp;
        struct dirent *dp;
        int numosts = 0;

        if ((dirp = opendir(LMT_OST_DIRNAME)) == NULL) {
                //cerebro_err_debug("couldn't open '%s'", LMT_OST_DIRNAME);
                return 0;
        }
        
        do {
                errno = 0;
                if ((dp = readdir(dirp)) != NULL) {
                        if (strncmp(dp->d_name, ".", 2) == 0  ||
                            strncmp(dp->d_name, "..", 3) == 0 ||
                            dp->d_type != DT_DIR) {
                                continue;
                        }

                        numosts++;
                }
        } while (dp != NULL);

        (void) closedir(dirp);

        return numosts;
}

int
common_metric_lmt_isrouter(void)
{
        char buf[LMT_BUFLEN];
        char *tmp = NULL;
        int rv = 0;

        rv = common_metric_lmt_read_file(LMT_PROC_ROUTES, buf, LMT_BUFLEN);
        if (rv) {
                return 0;
        }

        tmp = strchr(buf, '\n');
        if(tmp != NULL) {
                *tmp = '\0';
        }

        if (strncmp(buf, LMT_ROUTING_ENABLED_STR, LMT_BUFLEN) == 0) {
                return 1;
        }

	return 0;
}

int
common_metric_lmt_read_file(char *filename, char *buf, int buflen)
{
        int fd;
        int rv = 0;
        char *tmp = NULL;
        int bufleft = 0;
        int len = 0;

        if (!filename || !buf) {
                return CEREBRO_ERR_INTERNAL;
        }
        tmp = buf;
        bufleft = buflen;

        errno = 0;
        if ((fd = open(filename, O_RDONLY, 0)) == -1) {
                rv = errno;
                return rv;
        }

        while (1) {
                errno = 0;
                len = read(fd, tmp, bufleft);

                if (len == -1) {
                        rv = errno;
                        break;
                }

                if (len == 0) {
                        break;
                }

                tmp += len;
                bufleft -= len;

                if (bufleft <= 0) {
                        break;
                }
        }

        errno = 0;
        if (close(fd) != 0) {
                rv = errno;
        }

        return rv;
}

int
common_metric_lmt_strncpy(char **s1, char *s2, int *bufleft)
{
        int tmplen = 0;
        if (!s1 || !(*s1) ||
            !s2 ||
            !bufleft) {
                return CEREBRO_ERR_INTERNAL;
        }
        tmplen = strlen(s2);

        if (tmplen > *bufleft) {
                cerebro_err_debug("problem copying string to buffer");
                return CEREBRO_ERR_INTERNAL;
        }
        
        strncpy((*s1), s2, (*bufleft));
        *s1 += tmplen;
        *bufleft -= tmplen;
        return 0;
}

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
