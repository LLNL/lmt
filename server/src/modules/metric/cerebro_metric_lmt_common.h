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

#ifndef _CEREBRO_METRIC_LMT_COMMON
#define _CEREBRO_METRIC_LMT_COMMON

#include "lmt.h"

#define LMT_PROC_MEMINFO_PATH       "/proc/meminfo"
#define LMT_PROC_STAT_PATH          "/proc/stat"
#define LMT_PROC_MDS_STAT_PATH      "/proc/fs/lustre/mdt/MDS/mds/stats"
#define LMT_PROC_ROUTES             "/proc/sys/lnet/routes"
#define LMT_PROC_LNET_STATS_PATH    "/proc/sys/lnet/stats"

#define LMT_MDS_DIRNAME             "/proc/fs/lustre/mds"
#define LMT_OST_DIRNAME             "/proc/fs/lustre/obdfilter"

#define LMT_FILESFREE_NAME          "filesfree"
#define LMT_FILESTOTAL_NAME         "filestotal"
#define LMT_KBYTESFREE_NAME         "kbytesfree"
#define LMT_KBYTESTOTAL_NAME        "kbytestotal"
#define LMT_NUMREFS_NAME            "num_refs"
#define LMT_STATS_NAME              "stats"
#define LMT_UUID_NAME               "uuid"

#define LMT_PATH_SEPARATOR          "/"
#define LMT_BUFLEN                  4096
#define LMT_PERIOD                  5
#define LMT_PERIOD_NONE             3600
#define LMT_ROUTING_ENABLED_STR     "Routing enabled"

struct lmt_ost_thread_arg {
        int                        hostnamesize;
        char*                      hostname;
        int                        ostpathsize;
        char*                      ostpath;

        pthread_mutex_t            mutex;
        short                      started;
        short                      running;
};

struct lmt_ost_thread {
        pthread_t*                 tid;
        struct lmt_ost_thread_arg* arg;
};

int                           common_metric_lmt_interface_version(void);
int                           common_metric_lmt_setup_do_nothing(void);
int                           common_metric_lmt_cleanup_do_nothing(void);
int                           common_metric_lmt_get_period(int *period);
int                           common_metric_lmt_get_period_none(int *period);
int                           common_metric_lmt_get_flags(u_int32_t *flags);
int                           common_metric_lmt_destroy_metric_value_do_nothing(void *metric_value);
int                           common_metric_lmt_destroy_metric_value_free_value(void *metric_value);
Cerebro_metric_thread_pointer common_metric_lmt_get_metric_thread_null(void);
int                           common_metric_lmt_send_message_function_pointer_unused(Cerebro_metric_send_message function_pointer);

int common_metric_lmt_get_cpu_usage(char *usagestr, int buflen);
int common_metric_lmt_get_lnet_network_usage(char *usagestr, int buflen);
int common_metric_lmt_get_memory_usage(char *usagestr, int buflen);
int common_metric_lmt_get_file_usage(char *dirname, char *usagestr, int buflen);
int common_metric_lmt_get_filesystem_operations(char *procfilename, char *usagestr, int buflen);
int common_metric_lmt_get_bandwidth_usage(char *procfilename, char *usagestr, int buflen);
int common_metric_lmt_get_uuid(char *procfiledir, char *usagestr, int buflen);

int common_metric_lmt_ismds(void);
int common_metric_lmt_isoss(void);
int common_metric_lmt_isost(void);
int common_metric_lmt_isrouter(void);

int common_metric_lmt_read_file(char *filename, char *buf, int bufleft);
int common_metric_lmt_strncpy(char **s1, char *s2, int *bufleft);

#endif

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
