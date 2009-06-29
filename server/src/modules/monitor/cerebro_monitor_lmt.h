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

#ifndef _CEREBRO_MONITOR_LMT
#define _CEREBRO_MONITOR_LMT

#include "lmt.h"
#include "hash.h"

#define PROGNAME_STR         "lmt"
#define PROGNAME_STRLEN      3
#define FILESYSTEM_STR       "filesystem_"
#define FILESYSTEM_STRLEN    11
#define LOCALHOST_STR        "127.0.0.1"
#define LOCALHOST_STRLEN     9
#define LWATCHADMIN_STR      "lwatchadmin"
#define LWATCHADMIN_STRLEN   11
#define MAX_LINE             4096
#define MAX_LMT_QUERY_LEN    4096
#define MAX_LMT_MYSQL_CONN   256
#define MAX_LMT_DBNAME_LEN   256

#define LMTRC               "/usr/share/lmt/cron/lmtrc"
#define LMTRC_FILESYS_STR   "filesys"
#define LMTRC_NAME_STR      "name"
#define LMTRC_MOUNTNAME_STR "mountname"
#define LMTRC_DBHOST_STR    "dbhost"
#define LMTRC_DBPORT_STR    "dbport"
#define LMTRC_DBUSER_STR    "dbuser"
#define LMTRC_DBAUTH_STR    "dbauth"
#define LMTRC_DBNAME_STR    "dbname"

#define TIME_NUM_BIND_PARAMETERS 1
#define MDS_OPS_NUM_BIND_PARAMETERS 6
#define MDS_NUM_BIND_PARAMETERS 7
#define OSS_NUM_BIND_PARAMETERS 4
#define OST_NUM_BIND_PARAMETERS 8
#define RTR_NUM_BIND_PARAMETERS 4

/* This is used in the lmt_info stucture to
 * denote the type of node that the item
 * represents. */
typedef enum lmt_service_type {
        lmt_unknown_t = 0,
        lmt_mds_t = 1,
        lmt_oss_t = 2,
        lmt_ost_t = 3,
        lmt_router_t = 4,
        lmt_operation_t = 5
} lmt_service_t;

/* This structure is used to store the mysql connection
 * as well as the prepared statements that utilize that
 * particular connection. */
struct lmt_db_conn {
        unsigned int                      hostsize;
        char*                             host;
        unsigned int                      port;
        unsigned int                      usersize;
        char*                             user;
        unsigned int                      passwordsize;
        char*                             password;

        unsigned long long                timestamp;
        unsigned long long                timestampid;
        char  *                           dbname;
        MYSQL *                           mysql_conn;

        MYSQL_STMT *                      time_stmt;
        MYSQL_BIND *                      time_param;
        unsigned int                      time_param_num;

        MYSQL_STMT *                      mds_ops_stmt;
        MYSQL_BIND *                      mds_ops_param;
        unsigned int                      mds_ops_param_num;

        MYSQL_STMT *                      mds_stmt;
        MYSQL_BIND *                      mds_param;
        unsigned int                      mds_param_num;

        MYSQL_STMT *                      oss_stmt;
        MYSQL_BIND *                      oss_param;
        unsigned int                      oss_param_num;

        MYSQL_STMT *                      ost_stmt;
        MYSQL_BIND *                      ost_param;
        unsigned int                      ost_param_num;

        MYSQL_STMT *                      rtr_stmt;
        MYSQL_BIND *                      rtr_param;
        unsigned int                      rtr_param_num;

        struct mds_info *                 mds_data;
        struct oss_info *                 oss_data;
        struct ost_info *                 ost_data;
        struct router_info *              rtr_data;
};

/* This structure is used to store the information that
 * was passed from the particular lmt metic module */
struct lmt_db_item {
        unsigned int                      versionsize;
        char *                            version;
        unsigned int                      hostnamesize;
        char *                            hostname;
        unsigned int                      uuidsize;
        char *                            uuid;
        double                            cpu_usage;
        double                            memory_usage;
        unsigned long long                rbw;
        unsigned long long                wbw;
        unsigned long long                filesfree;
        unsigned long long                filestotal;
        unsigned long long                kbytesfree;
        unsigned long long                kbytestotal;
        struct lmt_filesystem_operation * fs_op;
};

struct operation_info {
        unsigned long long operation_id;
        unsigned int       operationnamesize;
        char *             operationname;
};

struct mds_info {
        unsigned long long mds_id;
        unsigned long long filesystem_id;
        unsigned long long timestamp_id;
        unsigned int       mdsnamesize;
        char *             mdsname;
        unsigned int       hostnamesize;
        char *             hostname;
        unsigned int       devicenamesize;
        char *             devicename;
};

struct oss_info {
        unsigned long long oss_id;
        unsigned long long filesystem_id;
        unsigned long long timestamp_id;
        unsigned int       hostnamesize;
        char *             hostname;
        unsigned int       fohostnamesize;
        char *             fohostname;
};

struct ost_info {
        unsigned long long  ost_id;
        unsigned long long  oss_id;
        unsigned long long  timestamp_id;
        unsigned int        ostnamesize;
        char *              ostname;
        unsigned int        hostnamesize;
        char *              hostname;
        unsigned short      offline;
        unsigned int        devicenamesize;
        char *              devicename;
};

struct router_info {
        unsigned long long router_id;
        unsigned long long timestamp_id;
        unsigned int       routernamesize;
        char *             routername;
        unsigned int       hostnamesize;
        char *             hostname;
        unsigned long long routergroup_id;
};

struct lmt_info {
        lmt_service_t         lmt_st;
        int                   lmt_num_conn;
        struct lmt_db_conn ** lmt_conn;          /* This is an array of
                                                  * lmt_db_conn pointers that
                                                  * references the conn field
                                                  * in struct lmtopt */

        void **               lmt_data;          /* This is an array of lmt
                                                  * specific data */
};

struct lmtopt {
        int                  num_conn;
        struct lmt_db_conn** conn;

        unsigned short       verbose;

        hash_t               fs;          /* Maps fs_name to conn index */
        hash_t               op;          /* Maps operation_name to some set of
                                           * conn indices */
        hash_t               mds;         /* Maps mds_uuid to some set of
                                           * conn indices */
        hash_t               oss;         /* Maps oss hostname to some set of
                                           * conn indices */
        hash_t               ost;         /* Maps ost_uuid to some set of
                                           * conn indices */
        hash_t               router;      /* Maps router hostname to some set of
                                           * conn indices */
};

void print_lmterr(MYSQL* conn, char* msg, ... );

int  lmt_monitor_hash_strcmp(const void *key1, const void *key2);
void lmt_monitor_hash_fs_freeitem(void *data);
void lmt_monitor_hash_freeitem(void *data);

void lmt_param_mds_ops_destroy(MYSQL_BIND *param, int param_num);
int  lmt_param_mds_ops_init(MYSQL_BIND *param, int param_num);
void lmt_param_mds_destroy(MYSQL_BIND *param, int param_num);
int  lmt_param_mds_init(MYSQL_BIND *param, int param_num);
void lmt_param_oss_destroy(MYSQL_BIND *param, int param_num);
int  lmt_param_oss_init(MYSQL_BIND *param, int param_num);
void lmt_param_ost_destroy(MYSQL_BIND *param, int param_num);
int  lmt_param_ost_init(MYSQL_BIND *param, int param_num);
void lmt_param_rtr_destroy(MYSQL_BIND *param, int param_num);
int  lmt_param_rtr_init(MYSQL_BIND *param, int param_num);
void lmt_db_conn_destroy(struct lmt_db_conn **connptr);
int  lmt_db_conn_init(struct lmt_db_conn **connptr);
void lmt_db_item_destroy(struct lmt_db_item **itmptr);
int  lmt_db_item_init(struct lmt_db_item **itmptr);
void lmt_monitor_destroy_lmtopt(struct lmtopt** mylmt);
int  lmt_monitor_init_lmtopt(struct lmtopt** mylmt);
int  lmt_monitor_get_mds_info(int conn_idx, struct lmtopt* mylmt);
int  lmt_monitor_get_oss_info(int conn_idx, struct lmtopt* mylmt);
int  lmt_monitor_get_ost_info(int conn_idx, struct lmtopt* mylmt);
int  lmt_monitor_get_router_info(int conn_idx, struct lmtopt* mylmt);
int  lmt_monitor_get_filesystem_info(struct lmtopt* mylmt);
int  lmt_monitor_get_lmtconfig(int conn_idx, struct lmtopt* mylmt);
int  lmt_monitor_update_db(char* lmt_key, struct lmtopt* mylmt);
int  lmt_monitor_update_mysql_conn_info(lmt_service_t lmt_st,
                                        char* lmt_key,
                                        void* lmt_data,
                                        int conn_idx,
                                        struct lmtopt* mylmt);


#endif

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
