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

#define _GNU_SOURCE

#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#include <stdarg.h>
#endif /* STDC_HEADERS */
#if TIME_WITH_SYS_TIME
# include <sys/time.h>
# include <time.h>
#else  /* !TIME_WITH_SYS_TIME */
# if HAVE_SYS_TIME_H
#  include <sys/time.h>
# else /* !HAVE_SYS_TIME_H */
#  include <time.h>
# endif /* !HAVE_SYS_TIME_H */
#endif /* !TIME_WITH_SYS_TIME */
#include <limits.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <mysql/mysql.h>
#include <mysql/errmsg.h>

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_monitor_module.h>
#include <cerebro/cerebro_constants.h>
#include <cerebro/cerebro_error.h>
#include "cerebro_monitor_lmt.h"

#define LMT_MONITOR_MODULE_NAME   "mon_lmt"
#define LMT_MONITOR_METRIC_NAMES  "lmt_mds,lmt_oss,lmt_ost,lmt_router"
#define LMT_MDS_METRIC_NAME       "lmt_mds"
#define LMT_OSS_METRIC_NAME       "lmt_oss"
#define LMT_OST_METRIC_NAME       "lmt_ost"
#define LMT_ROUTER_METRIC_NAME    "lmt_router"

extern char    *optarg;
extern int     optind, opterr, optopt;
struct lmtopt  *g_lmt = NULL;
struct lmt_fsop_jt_item lmt_fsop_jt[] = LMT_OPERATION_TABLE;

void
print_lmterr(MYSQL *conn, char *msg, ... )
{
        va_list va_arg_ptr;
        char *line = NULL;
        int linesize = 4096 * sizeof(char);
        int i = 0;

        if (msg != NULL) {
                line = malloc(linesize);
                if (line == NULL) {
                        return;
                }
                memset(line, 0, linesize);

                va_start(va_arg_ptr, msg);
                while(*msg && i < linesize) {
                        if (*msg == '%' && *(msg++)) {
                                switch(*msg) {
                                        case 0:
                                                line[i] = *msg++;
                                                i += sizeof(line);
                                                break;
                                        case '%':
                                                line[i++] = *msg++;
                                                break;
                                        case 's':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%s",
                                                              va_arg(va_arg_ptr,
                                                                     char*));
                                                msg++;
                                                break;
                                        case 'c':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%c",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        case 'd':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%d",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        case 'x':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%x",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        default:
                                                line[i++] = *msg++;
                                                break;
                                }
                        }
                        else if (*msg) {
                                line[i++] = *msg++;
                        }
                }
                va_end(va_arg_ptr);

                /* If we went too far null terminate the line
                 * and assume that the line was supposed to
                 * end with a newline
                 */
                if ((i+1) >= linesize) {
                        line[i] = 0;
                        line[i-1] = '\0';
                }
                else {
                        line[i+1] = 0;
                        line[i] = '\0';
                }
                cerebro_err_output("%s", line);
                free(line);
        }

        if (conn != NULL) {
                cerebro_err_output("Error %u (%s): %s",
                                   mysql_errno(conn),
                                   mysql_sqlstate(conn),
                                   mysql_error(conn));
        }
}

void
print_lmtstmterr(MYSQL_STMT *stmt, char *msg, ... )
{
        va_list va_arg_ptr;
        char *line = NULL;
        int linesize = 4096 * sizeof(char);
        int i = 0;

        if (msg != NULL) {
                line = malloc(linesize);
                if (line == NULL) {
                        return;
                }
                memset(line, 0, linesize);

                va_start(va_arg_ptr, msg);
                while(*msg && i < linesize) {
                        if (*msg == '%' && *(msg++)) {
                                switch(*msg) {
                                        case 0:
                                                line[i] = *msg++;
                                                i += sizeof(line);
                                                break;
                                        case '%':
                                                line[i++] = *msg++;
                                                break;
                                        case 's':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%s",
                                                              va_arg(va_arg_ptr,
                                                                     char*));
                                                msg++;
                                                break;
                                        case 'c':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%c",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        case 'd':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%d",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        case 'x':
                                                i += snprintf(&line[i],
                                                              linesize - i,
                                                              "%x",
                                                              va_arg(va_arg_ptr,
                                                                     int));
                                                msg++;
                                                break;
                                        default:
                                                line[i++] = *msg++;
                                                break;
                                }
                        }
                        else if (*msg) {
                                line[i++] = *msg++;
                        }
                }
                va_end(va_arg_ptr);

                /* If we went too far null terminate the line
                 * and assume that the line was supposed to
                 * end with a newline
                 */
                if ((i+1) >= linesize) {
                        line[i] = 0;
                        line[i-1] = '\0';
                }
                else {
                        line[i+1] = 0;
                        line[i] = '\0';
                }

                cerebro_err_output("%s", line);
                free(line);
        }

        if (stmt != NULL) {
                cerebro_err_output("Error %u (%s): %s",
                                   mysql_stmt_errno(stmt),
                                   mysql_stmt_sqlstate(stmt),
                                   mysql_stmt_error(stmt));
        }
}

int
lmt_monitor_hash_strcmp(const void *key1, const void *key2)
{
        return strcmp((char *) key1, (char *) key2);
}

void
lmt_monitor_hash_fs_freeitem(void *data)
{
        struct lmt_db_conn *ptr = NULL;

        if (data == NULL) {
                return;
        }
        ptr = (struct lmt_db_conn *) data;
        mysql_close(ptr->mysql_conn);
        ptr = NULL;
}

void
lmt_monitor_hash_freeitem(void *data)
{
        struct lmt_info *lmtinfo = NULL;

        if (data == NULL) {
                return;
        }
        lmtinfo = (struct lmt_info *) data;

        /* Free the memory allocated for
         * the array of pointers, but *NOT*
         * the pointers themselves */
        if (lmtinfo->lmt_conn != NULL) {
                free(lmtinfo->lmt_conn);
                lmtinfo->lmt_conn = NULL;
        }
}

void
strip_comments(char *buf, int bufsize)
{
        int i = 0;

        if (buf == NULL || bufsize <= 0) {
                return;
        }

        /* Could use index(), but want to respect bufsize */
        for (i = 0; i < bufsize; i++) {
                switch (buf[i]) {
                        case '\0':
                                goto out;
                                break;
                        case '#':
                                buf[i] = '\0';
                                goto out;
                                break;
                        default:
                                break;
                }
        }

out:
        return;
}

void
ltrim(char *buf, int bufsize)
{
        int i, j, startidx;
        
        if (buf == NULL || bufsize <= 0) {
                return;
        }
        
        /* i will be the position of the first non space character; */
        for (i = 0; i < bufsize; i++) {
                if(!isspace(buf[i])) {
                        break;
                }
        }
        startidx = i;
        
        if (startidx == 0) {
                return;
        }
        
        /* Note that if the string was properly null terminated
         * before it still will be.  We could also add in an if
         * statement to break out of the loop once we have found
         * the terminating NULL character, but want to remove
         * possible branch instruction check each time through
         * the loop.
         */
        for (i = startidx, j = 0; i < bufsize; i++) {
                buf[j++] = buf[i];
        }
        
        return;
}

void
rtrim(char *buf, int bufsize)
{
        int i, endidx, nullidx;
        
        if (buf == NULL || bufsize <= 0) {
                return;
        }
        
        /* i will be the position of the last non space character; */
        for (i = strnlen( buf, bufsize ) - 1; i >= 0; i--) {
                if (!isspace(buf[i])) {
                        break;
                }
        }
        
        endidx = i < 0 ? 0 : i;
        nullidx = endidx + 1;
        
        if (nullidx < bufsize) {
                buf[nullidx] = '\0';
        }
        else {
                buf[endidx] = '\0';
        }
}

void
lrtrim(char *buf, int bufsize)
{
        if (buf == NULL || bufsize <= 0) {
                return;
        }
        
        ltrim(buf, bufsize);
        rtrim(buf, bufsize);
}

int
lmtrc_exists(char *filename)
{
        struct stat st;

        errno = 0;
        if (filename == NULL || stat(filename, &st) < 0) {
                return 0;
        }

        return 1;
}

int
lmtrc_parse(FILE *lmtrc, struct lmt_db_conn **conn)
{
        static short idx = 1;
        char *line = NULL;
        size_t linesize = 4096;
        int sizeleft = 4096, tmpsize = 0;
        int rc = 0;
        long filepos = 0, tmpidx = 0;
        ssize_t read = 0;
        char *ptr, *tmpptr, *arg, *value;
        int argsize = 0, valsize = 0;
        struct lmt_db_conn *tmpconn = NULL;

        /* Make sure that file is initialized and that
         * the database connection is not set up yet */
        if (lmtrc == NULL || conn == NULL || (*conn) != NULL) {
                return EINVAL;
        }
        if (feof(lmtrc)) {
                return 0;
        }

        line =  malloc(sizeof(char) * 4096);
        if (line == NULL) {
                return ENOMEM;
        }
        
        rc = lmt_db_conn_init(&tmpconn);
        if (rc != 0) {
                print_lmterr(NULL,
                             "lmtrc_parse(): "
                             "problem allocating space for "
                             "conn");
                rc = ENOMEM;
                goto err;
        }

        filepos = ftell(lmtrc);
        errno = 0;
        while ((read = getline(&line, &linesize, lmtrc)) != -1) {
                strip_comments(line, (int) linesize);
                lrtrim(line, linesize);

                if (strnlen(line, (int) linesize) == 0) {
                        continue;
                }

                ptr = line;
                sizeleft = linesize;

                if (ptr == NULL || strncmp(ptr, LMTRC_FILESYS_STR, strlen(LMTRC_FILESYS_STR)) != 0) {
                        rc = EINVAL;
                        goto err;
                }

                tmpsize = strlen(LMTRC_FILESYS_STR);
                sizeleft -= tmpsize;
                ptr += tmpsize;

                if (ptr == NULL || *ptr != '.') {
                        rc = EINVAL;
                        goto err;
                }
                ptr++;
                sizeleft -= 1;

                errno = 0;
                tmpidx = strtol(ptr, &tmpptr, 0);
                if (errno != 0) {
                        rc = errno;
                        goto err;
                }
                if (tmpidx < 0 || tmpidx > INT_MAX) {
                        rc = ERANGE;
                        goto err;
                }
                tmpsize = (tmpptr - ptr) < 0 ? 0 : (tmpptr - ptr);
                ptr = tmpptr;
                sizeleft -= tmpsize;
                if (tmpidx != idx) {
                        idx++;
                        errno = 0;
                        rc = fseek(lmtrc, filepos, SEEK_SET);
                        if (idx < 0) {
                                rc = ERANGE;
                        }
                        goto err;
                }

                if (ptr == NULL || *ptr != '.') {
                        rc = EINVAL;
                        goto err;
                }
                ptr++;
                sizeleft -= 1;

                arg = value = NULL;
                arg = ptr;
                value = strchr(ptr, '=');
                if (value == NULL) {
                        rc = EINVAL;
                        goto err;
                }
                *value = '\0';
                value++;
                argsize = strnlen(arg, sizeleft);
                tmpsize = (value - ptr) < 0 ? 0 : (value - ptr);
                sizeleft -= tmpsize;
                valsize = sizeleft;

                if (strncmp(arg, LMTRC_NAME_STR, argsize) == 0) {
                        /* Do Nothing */
                }
                else if (strncmp(arg, LMTRC_MOUNTNAME_STR, argsize) == 0) {
                        /* Do Nothing */
                }
                else if (strncmp(arg, LMTRC_DBHOST_STR, argsize) == 0) {
                        if (tmpconn->host != NULL) {
                                free(tmpconn->host);
                                tmpconn->host = NULL;
                        }
                        tmpconn->hostsize = strnlen(value, valsize) + 1;
                        tmpconn->host = malloc(sizeof(char) * tmpconn->hostsize);
                        if (tmpconn->host == NULL) {
                                rc = ENOMEM;
                                goto err;
                        }
                        strncpy(tmpconn->host, value, tmpconn->hostsize);
                }
                else if (strncmp(arg, LMTRC_DBPORT_STR, argsize) == 0) {
                        long tmpport = 0;

                        errno = 0;
                        tmpport = strtol(value, NULL, 0);
                        if (errno) {
                                rc = errno;
                                goto err;
                        }
                        if (tmpport < 0 || tmpport > INT_MAX) {
                                rc = ERANGE;
                                goto err;
                        }
                        tmpconn->port = tmpport;
                }
                else if (strncmp(arg, LMTRC_DBUSER_STR, argsize) == 0) {
                        if (tmpconn->user != NULL) {
                                free(tmpconn->user);
                                tmpconn->user = NULL;
                        }
                        tmpconn->usersize = strnlen(value, valsize) + 1;
                        tmpconn->user = malloc(sizeof(char) * tmpconn->usersize);
                        if (tmpconn->user == NULL) {
                                rc = ENOMEM;
                                goto err;
                        }
                        strncpy(tmpconn->user, value, tmpconn->usersize);
                }
                else if (strncmp(arg, LMTRC_DBAUTH_STR, argsize) == 0) {
                        if (tmpconn->password != NULL) {
                                free(tmpconn->password);
                                tmpconn->password = NULL;
                        }
                        tmpconn->passwordsize = strnlen(value, valsize) + 1;
                        tmpconn->password = malloc(sizeof(char) * tmpconn->passwordsize);
                        if (tmpconn->password == NULL) {
                                rc = ENOMEM;
                                goto err;
                        }
                        strncpy(tmpconn->password, value, tmpconn->passwordsize);
                }
                else if (strncmp(arg, LMTRC_DBNAME_STR, argsize) == 0) {
                        strncpy(tmpconn->dbname, value, MAX_LMT_DBNAME_LEN);
                }

                filepos = ftell(lmtrc);
                errno = 0;
        }
        if (errno != 0) {
                rc = errno;
        }

err:
        /* Free the connection if there was an error or if
           the database name was never set */
        if ((rc && tmpconn != NULL) || (tmpconn && tmpconn->dbname == NULL)) {
                lmt_db_conn_destroy(&tmpconn);
                tmpconn = NULL;
        }
        free(line);

        *conn = tmpconn;
        return rc;
}

int
lmt_setup_time_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into TIMESTAMP_INFO ("
                        "TIMESTAMP) "
                            "values ("
                        "FROM_UNIXTIME(?)) ";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->time_param;
        num_param = conn->time_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_time_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_time_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != TIME_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* TIMESTAMP */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_time_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->time_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_setup_mds_ops_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into MDS_OPS_DATA ("
                        "MDS_ID, "
                        "OPERATION_ID, "
                        "TS_ID, "
                        "SAMPLES, "
                        "SUM, "
                        "SUMSQUARES) "
                            "values ("
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?)";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->mds_ops_param;
        num_param = conn->mds_ops_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_mds_ops_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_mds_ops_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != MDS_OPS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* MDS_ID */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        /* OPERATION_ID */
        param[1].buffer_type = MYSQL_TYPE_LONG;
        param[1].is_unsigned = 1;
        param[1].is_null = 0;

        /* TS_ID */
        param[2].buffer_type = MYSQL_TYPE_LONG;
        param[2].is_unsigned = 1;
        param[2].is_null = 0;

        /* SAMPLES */
        param[3].buffer_type = MYSQL_TYPE_LONG;
        param[3].is_unsigned = 1;
        param[3].is_null = 0;

        /* SUM */
        param[4].buffer_type = MYSQL_TYPE_LONG;
        param[4].is_unsigned = 1;
        param[4].is_null = 0;

        /* SUMSQUARES */
        param[5].buffer_type = MYSQL_TYPE_LONG;
        param[5].is_unsigned = 1;
        param[5].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_mds_ops_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->mds_ops_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_setup_mds_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into MDS_DATA ("
                        "MDS_ID, "
                        "TS_ID, "
                        "PCT_CPU, "
                        "KBYTES_FREE, "
                        "KBYTES_USED, "
                        "INODES_FREE, "
                        "INODES_USED) "
                            "values ("
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?)";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->mds_param;
        num_param = conn->mds_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_mds_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_mds_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != MDS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* MDS_ID */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        /* TS_ID */
        param[1].buffer_type = MYSQL_TYPE_LONG;
        param[1].is_unsigned = 1;
        param[1].is_null = 0;

        /* PCT_CPU */
        param[2].buffer_type = MYSQL_TYPE_FLOAT;
        param[2].is_unsigned = 1;
        param[2].is_null = 0;

        /* KBYTES_FREE */
        param[3].buffer_type = MYSQL_TYPE_LONG;
        param[3].is_unsigned = 1;
        param[3].is_null = 0;

        /* KBYTES_USED */
        param[4].buffer_type = MYSQL_TYPE_LONG;
        param[4].is_unsigned = 1;
        param[4].is_null = 0;

        /* INODES_FREE */
        param[5].buffer_type = MYSQL_TYPE_LONG;
        param[5].is_unsigned = 1;
        param[5].is_null = 0;

        /* INODES_USED */
        param[6].buffer_type = MYSQL_TYPE_LONG;
        param[6].is_unsigned = 1;
        param[6].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_mds_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->mds_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_setup_oss_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into OSS_DATA ("
                        "OSS_ID, "
                        "TS_ID, "
                        "PCT_CPU, "
                        "PCT_MEMORY) "
                            "values ("
                        "?, "
                        "?, "
                        "?, "
                        "?)";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->oss_param;
        num_param = conn->oss_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_oss_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_oss_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != OSS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* OSS_ID */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        /* TS_ID */
        param[1].buffer_type = MYSQL_TYPE_LONG;
        param[1].is_unsigned = 1;
        param[1].is_null = 0;

        /* PCT_CPU */
        param[2].buffer_type = MYSQL_TYPE_FLOAT;
        param[2].is_unsigned = 1;
        param[2].is_null = 0;

        /* PCT_MEMORY */
        param[3].buffer_type = MYSQL_TYPE_FLOAT;
        param[3].is_unsigned = 1;
        param[3].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_oss_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->oss_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_setup_ost_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into OST_DATA ("
                        "OST_ID, "
                        "TS_ID, "
                        "READ_BYTES, "
                        "WRITE_BYTES, "
                        "KBYTES_FREE, "
                        "KBYTES_USED, "
                        "INODES_FREE, "
                        "INODES_USED) "
                            "values ("
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?, "
                        "?)";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->ost_param;
        num_param = conn->ost_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_ost_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_ost_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != OST_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* OST_ID */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        /* TS_ID */
        param[1].buffer_type = MYSQL_TYPE_LONG;
        param[1].is_unsigned = 1;
        param[1].is_null = 0;

        /* READ_BYTES */
        param[2].buffer_type = MYSQL_TYPE_LONG;
        param[2].is_unsigned = 1;
        param[2].is_null = 0;

        /* WRITE_BYTES */
        param[3].buffer_type = MYSQL_TYPE_LONG;
        param[3].is_unsigned = 1;
        param[3].is_null = 0;

        /* KBYTES_FREE */
        param[4].buffer_type = MYSQL_TYPE_LONG;
        param[4].is_unsigned = 1;
        param[4].is_null = 0;

        /* KBYTES_USED */
        param[5].buffer_type = MYSQL_TYPE_LONG;
        param[5].is_unsigned = 1;
        param[5].is_null = 0;

        /* INODES_FREE */
        param[6].buffer_type = MYSQL_TYPE_LONG;
        param[6].is_unsigned = 1;
        param[6].is_null = 0;

        /* INODES_USED */
        param[7].buffer_type = MYSQL_TYPE_LONG;
        param[7].is_unsigned = 1;
        param[7].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_ost_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->ost_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_setup_rtr_stmt(struct lmt_db_conn *conn)
{
        MYSQL_STMT *stmt = NULL;
        MYSQL_BIND *param = NULL;
        int rc = 0, num_param = 0;
        char *str = "insert into ROUTER_DATA ("
                        "ROUTER_ID, "
                        "TS_ID, "
                        "BYTES, "
                        "PCT_CPU) "
                            "values ("
                        "?, "
                        "?, "
                        "?, "
                        "?)";

        if (conn == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        param = conn->rtr_param;
        num_param = conn->rtr_param_num;

        stmt = mysql_stmt_init(conn->mysql_conn);
        if (stmt == NULL) {
                print_lmtstmterr(NULL, "lmt_setup_rtr_stmt(): "
                                       "problem initializing stmt");
                goto err;
        }

        rc = mysql_stmt_prepare(stmt, str, strlen(str));
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_rtr_stmt(): "
                                       "problem preparing stmt");
                goto err;
        }

        if (stmt == NULL ||
            param == NULL || num_param != RTR_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* ROUTER_ID */
        param[0].buffer_type = MYSQL_TYPE_LONG;
        param[0].is_unsigned = 1;
        param[0].is_null = 0;

        /* TS_ID */
        param[1].buffer_type = MYSQL_TYPE_LONG;
        param[1].is_unsigned = 1;
        param[1].is_null = 0;

        /* BYTES */
        param[2].buffer_type = MYSQL_TYPE_LONG;
        param[2].is_unsigned = 1;
        param[2].is_null = 0;

        /* PCT_CPU */
        param[3].buffer_type = MYSQL_TYPE_FLOAT;
        param[3].is_unsigned = 1;
        param[3].is_null = 0;

        rc = mysql_stmt_bind_param(stmt, param);
        if (rc) {
                print_lmtstmterr(stmt, "lmt_setup_rtr_stmt(): "
                                       "problem binding param");
                goto err;
        }
        conn->rtr_stmt = stmt;

        return 0;
err:
        return rc;
}

int
lmt_param_time_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != TIME_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                param[i].buffer = malloc(sizeof(unsigned long long));

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_time_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_param_mds_ops_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != MDS_OPS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                param[i].buffer = malloc(sizeof(unsigned long long));

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_mds_ops_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_param_mds_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != MDS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                if (i == 2) {
                        param[i].buffer = malloc(sizeof(float));
                }
                else {
                        param[i].buffer = malloc(sizeof(unsigned long long));
                }

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_mds_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_param_oss_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != OSS_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                if (i == 2 || i == 3) {
                        param[i].buffer = malloc(sizeof(float));
                }
                else {
                        param[i].buffer = malloc(sizeof(unsigned long long));
                }

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_oss_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_param_ost_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != OST_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                param[i].buffer = malloc(sizeof(unsigned long long));

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_ost_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_param_rtr_init(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        if (param_num != RTR_NUM_BIND_PARAMETERS) {
                return CEREBRO_ERR_PARAMETERS;
        }

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        return CEREBRO_ERR_PARAMETERS;
                }
                
                if (i == 3) {
                        param[i].buffer = malloc(sizeof(float));
                }
                else {
                        param[i].buffer = malloc(sizeof(unsigned long long));
                }

                if (param[i].buffer == NULL) {
                        return ENOMEM;
                }
        }

        return 0;
}

void
lmt_param_rtr_destroy(MYSQL_BIND *param, int param_num)
{
        int i = 0;

        for (i = 0; i < param_num; i++) {
                if (param[i].buffer != NULL) {
                        free(param[i].buffer);
                        param[i].buffer = NULL;
                }
        }
}

int
lmt_db_conn_init(struct lmt_db_conn **connptr)
{
        struct lmt_db_conn *conn = NULL;
        int rc = 0;

        if (connptr == NULL || *connptr != NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }

        (*connptr) = malloc(sizeof(struct lmt_db_conn));
        if ((*connptr) == NULL) {
                return ENOMEM;
        }
        conn = (*connptr);

        conn->hostsize = LOCALHOST_STRLEN + 1;
        conn->host = malloc(sizeof(char) * conn->hostsize);
        if (conn->host == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strncpy(conn->host, LOCALHOST_STR, conn->hostsize);
        
        conn->port = 0;
        
        conn->usersize = LWATCHADMIN_STRLEN + 1;
        conn->user = malloc(sizeof(char) * conn->usersize);
        if (conn->user == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strncpy(conn->user, LWATCHADMIN_STR, conn->usersize);
        
        conn->passwordsize = 0;
        conn->password = NULL;

        conn->timestampid = 0;
        conn->timestamp = 0;

        conn->dbname = malloc(MAX_LMT_DBNAME_LEN * sizeof(char));
        if (conn->dbname == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->dbname, 0, MAX_LMT_DBNAME_LEN * sizeof(char));
        
        conn->mysql_conn = malloc(sizeof(MYSQL));
        if (conn->mysql_conn == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->mysql_conn, 0, sizeof(MYSQL));

        conn->time_param_num = TIME_NUM_BIND_PARAMETERS;
        conn->mds_ops_param_num = MDS_OPS_NUM_BIND_PARAMETERS;
        conn->mds_param_num = MDS_NUM_BIND_PARAMETERS;
        conn->oss_param_num = OSS_NUM_BIND_PARAMETERS;
        conn->ost_param_num = OST_NUM_BIND_PARAMETERS;
        conn->rtr_param_num = RTR_NUM_BIND_PARAMETERS;

        conn->time_param = malloc(sizeof(MYSQL_BIND) *
                                     conn->time_param_num);
        if (conn->time_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->time_param, 0, sizeof(MYSQL_BIND) *
               conn->time_param_num);
        rc = lmt_param_time_init(conn->time_param,
                                 conn->time_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing time_param");
                goto err;
        }

        conn->mds_ops_param = malloc(sizeof(MYSQL_BIND) *
                                     conn->mds_ops_param_num);
        if (conn->mds_ops_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->mds_ops_param, 0, sizeof(MYSQL_BIND) *
               conn->mds_ops_param_num);
        rc = lmt_param_mds_ops_init(conn->mds_ops_param,
                                    conn->mds_ops_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing mds_ops_param");
                goto err;
        }

        conn->mds_param = malloc(sizeof(MYSQL_BIND) * conn->mds_param_num);
        if (conn->mds_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->mds_param, 0, sizeof(MYSQL_BIND) * conn->mds_param_num);
        rc = lmt_param_mds_init(conn->mds_param,
                                conn->mds_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing mds_param");
                goto err;
        }

        conn->oss_param = malloc(sizeof(MYSQL_BIND) * conn->oss_param_num);
        if (conn->oss_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->oss_param, 0, sizeof(MYSQL_BIND) * conn->oss_param_num);
        rc = lmt_param_oss_init(conn->oss_param,
                                conn->oss_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing oss_param");
                goto err;
        }

        conn->ost_param = malloc(sizeof(MYSQL_BIND) * conn->ost_param_num);
        if (conn->ost_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->ost_param, 0, sizeof(MYSQL_BIND) * conn->ost_param_num);
        rc = lmt_param_ost_init(conn->ost_param,
                                conn->ost_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing ost_param");
                goto err;
        }

        conn->rtr_param = malloc(sizeof(MYSQL_BIND) * conn->rtr_param_num);
        if (conn->rtr_param == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(conn->rtr_param, 0, sizeof(MYSQL_BIND) * conn->rtr_param_num);
        rc = lmt_param_rtr_init(conn->rtr_param,
                                conn->rtr_param_num);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_db_conn_init(): "
                                   "problem initializing rtr_param");
                goto err;
        }

        return 0;

err:
        lmt_db_conn_destroy(connptr);
        return rc;
}

void
lmt_db_conn_destroy(struct lmt_db_conn **connptr)
{
        struct lmt_db_conn *conn = NULL;
        
        if (connptr == NULL || *connptr == NULL) {
                return;
        }
        conn = (*connptr);

        conn->hostsize = 0;
        if (conn->host != NULL) {
                free(conn->host);
                conn->host = NULL;
        }

        conn->port = 0;

        conn->usersize = 0;
        if (conn->user != NULL) {
                free(conn->user);
                conn->user = NULL;
        }

        conn->passwordsize = 0;
        if (conn->password == NULL) {
                free(conn->password);
                conn->password = NULL;
        }

        conn->timestampid = 0;
        conn->timestamp = 0;

        if (conn->dbname != NULL) {
                free(conn->dbname);
                conn->dbname = NULL;
        }

        if (conn->mds_stmt != NULL) {
                mysql_stmt_close(conn->mds_stmt);
                conn->mds_stmt = NULL;
        }

        if (conn->time_stmt != NULL) {
                mysql_stmt_close(conn->time_stmt);
                conn->time_stmt = NULL;
        }

        if (conn->mds_ops_stmt != NULL) {
                mysql_stmt_close(conn->mds_ops_stmt);
                conn->mds_ops_stmt = NULL;
        }

        if (conn->oss_stmt != NULL) {
                mysql_stmt_close(conn->oss_stmt);
                conn->oss_stmt = NULL;
        }

        if (conn->ost_stmt != NULL) {
                mysql_stmt_close(conn->ost_stmt);
                conn->ost_stmt = NULL;
        }

        if (conn->rtr_stmt != NULL) {
                mysql_stmt_close(conn->rtr_stmt);
                conn->rtr_stmt = NULL;
        }

        if (conn->time_param != NULL) {
                lmt_param_time_destroy(conn->time_param,
                                       conn->time_param_num);
                free(conn->time_param);
                conn->time_param = NULL;
        }

        if (conn->mds_param != NULL) {
                lmt_param_mds_destroy(conn->mds_param,
                                      conn->mds_param_num);
                free(conn->mds_param);
                conn->mds_param = NULL;
        }

        if (conn->mds_ops_param != NULL) {
                lmt_param_mds_ops_destroy(conn->mds_ops_param,
                                          conn->mds_ops_param_num);
                free(conn->mds_ops_param);
                conn->mds_ops_param = NULL;
        }

        if (conn->oss_param != NULL) {
                lmt_param_oss_destroy(conn->oss_param,
                                      conn->oss_param_num);
                free(conn->oss_param);
                conn->oss_param = NULL;
        }

        if (conn->ost_param != NULL) {
                lmt_param_ost_destroy(conn->ost_param,
                                      conn->ost_param_num);
                free(conn->ost_param);
                conn->ost_param = NULL;
        }

        if (conn->rtr_param != NULL) {
                lmt_param_rtr_destroy(conn->rtr_param,
                                      conn->rtr_param_num);
                free(conn->rtr_param);
                conn->rtr_param = NULL;
        }

        if (conn->mysql_conn != NULL) {
                mysql_close(conn->mysql_conn);
                free(conn->mysql_conn);
                conn->mysql_conn = NULL;
        }

        free(conn);
        conn = NULL;
}

int
lmt_db_item_init(struct lmt_db_item **itmptr)
{
        struct lmt_db_item *itm = NULL;

        if (itmptr == NULL || *itmptr != NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }

        (*itmptr) = malloc(sizeof(struct lmt_db_item));
        if ((*itmptr) == NULL) {
                return ENOMEM;
        }
        itm = (*itmptr);
        memset(itm, 0, sizeof(struct lmt_db_item));

        itm->fs_op = malloc(sizeof(struct lmt_filesystem_operation) * LMT_NUM_OPERATIONS);
        if (itm->fs_op == NULL) {
                return ENOMEM;
        }

        return 0;
}

void
lmt_db_item_destroy(struct lmt_db_item **itmptr)
{
        struct lmt_db_item *itm = NULL;

        if (itmptr == NULL || *itmptr == NULL) {
                return;
        }
        itm = (*itmptr);

        if (itm->version != NULL) {
                free(itm->version);
        }

        if (itm->hostname != NULL) {
                free(itm->hostname);
        }

        if (itm->uuid != NULL) {
                free(itm->uuid);
        }

        if (itm ->fs_op != NULL) {
                free(itm->fs_op);
        }

        memset(itm, 0, sizeof(struct lmt_db_item));
        free(itm);
        itm = NULL;
}

/* Insert the particular item into the appropriate hash
 * table based on the type (MDS, OSS, OST, ROUTER).  If the
 * item does not exist yet, then create it and then insert
 * it.  This allows a particular item to have a list of
 * connections that it should update (eg. ROUTERS or OSSes
 * that are part of multiple filesystems). */
int
lmt_monitor_update_mysql_conn_info(lmt_service_t lmt_st,
                                   char *lmt_key,
                                   void *lmt_data,
                                   int conn_idx,
                                   struct lmtopt *mylmt)
{
        struct lmt_info *lmtinfo = NULL;
        hash_t tmphash;
        int rc = 0;

        if (lmt_st == lmt_unknown_t ||
            lmt_key == NULL ||
            lmt_data == NULL ||
            conn_idx < 0 ||
            conn_idx >= MAX_LMT_MYSQL_CONN ||
            mylmt == NULL ) {
                rc = EINVAL;
                goto err;
        }

        switch (lmt_st) {
                case lmt_mds_t:
                        tmphash = mylmt->mds;
                        break;
                case lmt_oss_t:
                        tmphash = mylmt->oss;
                        break;
                case lmt_ost_t:
                        tmphash = mylmt->ost;
                        break;
                case lmt_router_t:
                        tmphash = mylmt->router;
                        break;
                case lmt_operation_t:
                        tmphash = mylmt->op;
                        break;
                default:
                        rc = EINVAL;
                        goto err;
                        break;
        }

        errno = 0;
        lmtinfo = hash_find(tmphash, lmt_key);
        if (lmtinfo == NULL && errno != 0) {
                rc = errno;
                goto err;
        }
        else if (lmtinfo == NULL) {
                lmtinfo = malloc(sizeof(struct lmt_info));
                if (lmtinfo == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(lmtinfo, 0, sizeof(struct lmt_info));

                lmtinfo->lmt_conn = malloc(mylmt->num_conn * sizeof(struct lmt_db_conn *));
                if (lmtinfo->lmt_conn == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(lmtinfo->lmt_conn, 0, mylmt->num_conn * sizeof(struct lmt_db_conn *));

                lmtinfo->lmt_data = malloc(mylmt->num_conn * sizeof(void *));
                if (lmtinfo->lmt_data == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(lmtinfo->lmt_data, 0, mylmt->num_conn * sizeof(void *));

                lmtinfo->lmt_st = lmt_st;
                lmtinfo->lmt_num_conn = 0;
                lmtinfo->lmt_conn[lmtinfo->lmt_num_conn] = mylmt->conn[conn_idx];
                lmtinfo->lmt_data[lmtinfo->lmt_num_conn] = lmt_data;
                lmtinfo->lmt_num_conn++;

                errno = 0;
                hash_insert(tmphash, lmt_key, lmtinfo);
                if (errno) {
                        cerebro_err_debug("lmt_monitor_update_mysql_conn_info: "
                                          "problem inserting item into hashtable");
                        rc = errno;
                        goto err;
                }
        }
        else {
                lmtinfo->lmt_conn[lmtinfo->lmt_num_conn] = mylmt->conn[conn_idx];
                lmtinfo->lmt_data[lmtinfo->lmt_num_conn] = lmt_data;
                lmtinfo->lmt_num_conn++;
        }

        return 0;

err:
        if (lmtinfo != NULL) {
                if (lmtinfo->lmt_conn != NULL) {
                        free(lmtinfo->lmt_conn);
                        lmtinfo->lmt_conn = NULL;
                }

                if (lmtinfo->lmt_data != NULL) {
                        free(lmtinfo->lmt_data);
                        lmtinfo->lmt_conn = NULL;
                }

                free(lmtinfo);
                lmtinfo = NULL;
        }

        return rc;
}

int
lmt_monitor_get_operation_info(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0, i = 0;
        MYSQL *mysql = mylmt->conn[conn_idx]->mysql_conn;
        MYSQL_RES *res_set = NULL;
        MYSQL_ROW row;
        MYSQL_FIELD *field;
        struct operation_info *op = NULL;

        rc = mysql_query(mysql, "SELECT * from OPERATION_INFO" );
        if (rc != 0) {
                print_lmterr(mysql, "lmt_monitor_get_operation_info(): "
                                    "problem getting operation_info.");
                return rc;
        }

        res_set = mysql_store_result(mysql);
        if (res_set == NULL) {
                print_lmterr(mysql, "lmt_monitor_get_operation_info(): "
                                    "problem storing result set.");
                return EINVAL;
        }

        while((row = mysql_fetch_row(res_set)) != NULL) {
                mysql_field_seek(res_set, 0);

                /* Allocate space for a new operation structure */
                op = malloc(sizeof(struct operation_info));
                if (op == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(op, 0, sizeof(struct operation_info));

                for(i = 0; i < mysql_num_fields(res_set); i++) {
                        if (row[i] == NULL) {
                                continue;
                        }

                        field = mysql_fetch_field(res_set);
                        if (strcmp(field->name, "OPERATION_ID") == 0) {
                                errno = 0;
                                op->operation_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "OPERATION_NAME") == 0) {
                                op->operationnamesize = strlen(row[i]) + 1;
                                op->operationname =
                                        malloc(op->operationnamesize *
                                               sizeof(char));
                                if (op->operationname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(op->operationname,
                                        row[i],
                                        op->operationnamesize);
                        }
                }

                /* Insert this operation into the hash table
                 * if it doesn't already exist */
                rc = lmt_monitor_update_mysql_conn_info(lmt_operation_t,
                                                        op->operationname,
                                                        op,
                                                        conn_idx,
                                                        mylmt);
                if (rc != 0) {
                        goto err;
                }
        }

        mysql_free_result(res_set);
        return 0;

err:
        mysql_free_result(res_set);
        if (op != NULL) {
                free(op);
                op = NULL;
        }
        return rc;
}

/* Get information about the MDS for a particular filesystem referenced
 * by conn_idx */
int lmt_monitor_get_mds_info(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0, i = 0;
        MYSQL *mysql = mylmt->conn[conn_idx]->mysql_conn;
        MYSQL_RES *res_set = NULL;
        MYSQL_ROW row;
        MYSQL_FIELD *field;
        struct mds_info *mds = NULL;

        rc = mysql_query(mysql, "SELECT * from MDS_INFO" );
        if (rc != 0) {
                print_lmterr(mysql, "lmt_monitor_get_mds_info(): "
                                    "problem getting mds_info.");
                return rc;
        }

        res_set = mysql_store_result(mysql);
        if (res_set == NULL) {
                print_lmterr(mysql, "lmt_monitor_get_mds_info(): "
                                    "problem storing result set.");
                return EINVAL;
        }

        while((row = mysql_fetch_row(res_set)) != NULL) {
                mysql_field_seek(res_set, 0);

                /* Allocate space for a new mds structure */
                mds = malloc(sizeof(struct mds_info));
                if (mds == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(mds, 0, sizeof(struct mds_info));

                for(i = 0; i < mysql_num_fields(res_set); i++) {
                        if (row[i] == NULL) {
                                continue;
                        }

                        field = mysql_fetch_field(res_set);
                        if (strcmp(field->name, "MDS_ID") == 0) {
                                errno = 0;
                                mds->mds_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "FILESYSTEM_ID") == 0) {
                                errno = 0;
                                mds->filesystem_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "MDS_NAME") == 0) {
                                mds->mdsnamesize = strlen(row[i]) + 1;
                                mds->mdsname =
                                        malloc(mds->mdsnamesize *
                                               sizeof(char));
                                if (mds->mdsname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(mds->mdsname,
                                        row[i],
                                        mds->mdsnamesize);
                        }
                        else if (strcmp(field->name, "HOSTNAME") == 0) {
                                mds->hostnamesize = strlen(row[i]) + 1;
                                mds->hostname =
                                        malloc(mds->hostnamesize *
                                               sizeof(char));
                                if (mds->hostname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(mds->hostname,
                                        row[i],
                                        mds->hostnamesize);
                        }
                        else if (strcmp(field->name, "DEVICE_NAME") == 0) {
                                mds->devicenamesize = strlen(row[i]) + 1;
                                mds->devicename =
                                        malloc(mds->devicenamesize *
                                               sizeof(char));
                                if (mds->devicename == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(mds->devicename,
                                        row[i],
                                        mds->devicenamesize);
                        }
                }

                /* Insert this mds into the hash table
                 * if it doesn't already exist */
                rc = lmt_monitor_update_mysql_conn_info(lmt_mds_t,
                                                        mds->mdsname,
                                                        mds,
                                                        conn_idx,
                                                        mylmt);
                if (rc != 0) {
                        goto err;
                }
        }

        mysql_free_result(res_set);
        return 0;

err:
        mysql_free_result(res_set);
        if (mds != NULL) {
                free(mds);
                mds = NULL;
        }
        return rc;
}

int lmt_monitor_get_oss_info(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0, i = 0;
        MYSQL *mysql = mylmt->conn[conn_idx]->mysql_conn;
        MYSQL_RES *res_set = NULL;
        MYSQL_ROW row;
        MYSQL_FIELD *field;
        struct oss_info *oss = NULL;

        rc = mysql_query(mysql, "SELECT * from OSS_INFO" );
        if (rc != 0) {
                print_lmterr(mysql, "lmt_monitor_get_oss_info(): "
                                    "problem getting oss_info.");
                return rc;
        }

        res_set = mysql_store_result(mysql);
        if (res_set == NULL) {
                print_lmterr(mysql, "lmt_monitor_get_oss_info(): "
                                    "problem storing result set.");
                return EINVAL;
        }

        while((row = mysql_fetch_row(res_set)) != NULL) {
                mysql_field_seek(res_set, 0);

                /* Allocate space for a new oss structure */
                oss = malloc(sizeof(struct oss_info));
                if (oss == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(oss, 0, sizeof(struct oss_info));

                for(i = 0; i < mysql_num_fields(res_set); i++) {
                        if (row[i] == NULL) {
                                continue;
                        }

                        field = mysql_fetch_field(res_set);
                        if (strcmp(field->name, "OSS_ID") == 0) {
                                errno = 0;
                                oss->oss_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "FILESYSTEM_ID") == 0) {
                                errno = 0;
                                oss->filesystem_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "HOSTNAME") == 0) {
                                oss->hostnamesize = strlen(row[i]) + 1;
                                oss->hostname =
                                        malloc(oss->hostnamesize *
                                               sizeof(char));
                                if (oss->hostname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(oss->hostname,
                                        row[i],
                                        oss->hostnamesize);
                        }
                        else if (strcmp(field->name, "FAILOVERHOST") == 0) {
                        }
                }

                /* Insert this oss into the hash table
                 * if it doesn't already exist */
                rc = lmt_monitor_update_mysql_conn_info(lmt_oss_t,
                                                        oss->hostname,
                                                        oss,
                                                        conn_idx,
                                                        mylmt);
                if (rc != 0) {
                        goto err;
                }
        }

        mysql_free_result(res_set);
        return 0;

err:
        mysql_free_result(res_set);
        if (oss != NULL) {
                free(oss);
                oss = NULL;
        }
        return rc;
}

int lmt_monitor_get_ost_info(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0, i = 0;
        MYSQL *mysql = mylmt->conn[conn_idx]->mysql_conn;
        MYSQL_RES *res_set = NULL;
        MYSQL_ROW row;
        MYSQL_FIELD *field;
        struct ost_info *ost = NULL;

        rc = mysql_query(mysql, "SELECT * from OST_INFO" );
        if (rc != 0) {
                print_lmterr(mysql, "lmt_monitor_get_ost_info(): "
                                    "problem getting ost_info.");
                return rc;
        }

        res_set = mysql_store_result(mysql);
        if (res_set == NULL) {
                print_lmterr(mysql, "lmt_monitor_get_ost_info(): "
                                    "problem storing result set.");
                return EINVAL;
        }

        while((row = mysql_fetch_row(res_set)) != NULL) {
                mysql_field_seek(res_set, 0);

                /* Allocate space for a new ost structure */
                ost = malloc(sizeof(struct ost_info));
                if (ost == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(ost, 0, sizeof(struct ost_info));

                for(i = 0; i < mysql_num_fields(res_set); i++) {
                        if (row[i] == NULL) {
                                continue;
                        }

                        field = mysql_fetch_field(res_set);
                        if (strcmp(field->name, "OST_ID") == 0) {
                                errno = 0;
                                ost->ost_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "OSS_ID") == 0) {
                                errno = 0;
                                ost->oss_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "OST_NAME") == 0) {
                                ost->ostnamesize = strlen(row[i]) + 1;
                                ost->ostname =
                                        malloc(ost->ostnamesize *
                                               sizeof(char));
                                if (ost->ostname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(ost->ostname,
                                        row[i],
                                        ost->ostnamesize);
                        }
                        else if (strcmp(field->name, "HOSTNAME") == 0) {
                                ost->hostnamesize = strlen(row[i]) + 1;
                                ost->hostname =
                                        malloc(ost->hostnamesize *
                                               sizeof(char));
                                if (ost->hostname == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(ost->hostname,
                                        row[i],
                                        ost->hostnamesize);
                        }
                        else if (strcmp(field->name, "OFFLINE") == 0) {
                                errno = 0;
                                ost->offline = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "DEVICE_NAME") == 0) {
                                ost->devicenamesize = strlen(row[i]) + 1;
                                ost->devicename =
                                        malloc(ost->devicenamesize *
                                               sizeof(char));
                                if (ost->devicename == NULL) {
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(ost->devicename,
                                        row[i],
                                        ost->devicenamesize);
                        }
                }

                /* Insert this ost into the hash table
                 * if it doesn't already exist */
                rc = lmt_monitor_update_mysql_conn_info(lmt_ost_t, 
                                                        ost->ostname,
                                                        ost,
                                                        conn_idx,
                                                        mylmt);
                if (rc != 0) {
                        goto err;
                }
        }

        mysql_free_result(res_set);
        return 0;

err:
        mysql_free_result(res_set);
        if (ost != NULL) {
                free(ost);
                ost = NULL;
        }
        return rc;
}

int lmt_monitor_get_router_info(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0, i = 0;
        MYSQL *mysql = mylmt->conn[conn_idx]->mysql_conn;
        MYSQL_RES *res_set = NULL;
        MYSQL_ROW row;
        MYSQL_FIELD *field;
        struct router_info *rtr = NULL;

        rc = mysql_query(mysql, "SELECT * from ROUTER_INFO" );
        if (rc != 0) {
                print_lmterr(mysql, "lmt_monitor_get_router_info(): "
                                    "problem getting router_info.");
                return rc;
        }

        res_set = mysql_store_result(mysql);
        if (res_set == NULL) {
                print_lmterr(mysql, "lmt_monitor_get_router_info(): "
                                    "problem storing result set.");
                return EINVAL;
        }

        while((row = mysql_fetch_row(res_set)) != NULL) {
                mysql_field_seek(res_set, 0);

                /* Allocate space for a new router structure */
                rtr = malloc(sizeof(struct router_info));
                if (rtr == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
                memset(rtr, 0, sizeof(struct router_info));

                for(i = 0; i < mysql_num_fields(res_set); i++) {
                        if (row[i] == NULL) {
                                continue;
                        }

                        field = mysql_fetch_field(res_set);
                        if (strcmp(field->name, "ROUTER_ID") == 0) {
                                errno = 0;
                                rtr->router_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        print_lmterr(mysql,
                                            "lmt_monitor_get_router_info(): "
                                             "error converting router_id");
                                        rc = errno;
                                        goto err;
                                }
                        }
                        else if (strcmp(field->name, "ROUTER_NAME") == 0) {
                                rtr->routernamesize = strlen(row[i]) + 1;
                                rtr->routername =
                                        malloc(rtr->routernamesize *
                                               sizeof(char));
                                if (rtr->routername == NULL) {
                                        print_lmterr(mysql,
                                            "lmt_monitor_get_router_info(): "
                                            "problem allocating router name");
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(rtr->routername,
                                        row[i],
                                        rtr->routernamesize);
                        }
                        else if (strcmp(field->name, "HOSTNAME") == 0) {
                                rtr->hostnamesize = strlen(row[i]) + 1;
                                rtr->hostname =
                                        malloc(rtr->hostnamesize *
                                               sizeof(char));
                                if (rtr->hostname == NULL) {
                                        print_lmterr(mysql,
                                            "lmt_monitor_get_router_info(): "
                                            "problem allocating router "
                                            "hostname");
                                        rc = ENOMEM;
                                        goto err;
                                }
                                strncpy(rtr->hostname,
                                        row[i],
                                        rtr->hostnamesize);
                        }
                        else if (strcmp(field->name, "ROUTER_GROUP_ID") == 0) {
                                errno = 0;
                                rtr->routergroup_id = strtoul(row[i], NULL, 0);
                                if (errno == ERANGE || errno == EINVAL) {
                                        rc = errno;
                                        goto err;
                                }
                        }
                }

                /* Insert this router into the hash table
                 * if it doesn't already exist */
                rc = lmt_monitor_update_mysql_conn_info(lmt_router_t,
                                                        rtr->routername,
                                                        rtr,
                                                        conn_idx,
                                                        mylmt);
                if (rc != 0) {
                        goto err;
                }
        }

        mysql_free_result(res_set);
        return 0;

err:
        mysql_free_result(res_set);
        if (rtr != NULL) {
                free(rtr);
                rtr = NULL;
        }
        return rc;
}

int lmt_monitor_get_filesystem_info(struct lmtopt *mylmt)
{
        FILE *lmtrc = NULL;
        struct lmt_db_conn *conn = NULL;
        MYSQL *tmpconn = NULL;
        int rc = 0, complete = 0;

        errno = 0;
        lmtrc = fopen(LMTRC, "r");
        if (lmtrc == NULL) {
                print_lmterr(NULL,
                             "lmt_monitor_get_filesystem_info(): "
                             "problem accessing file " LMTRC);
                rc = errno;
                return rc;
        }

        while(!complete) {
                /* Make sure to get the proper settings for this
                 * database connection from the lmtrc file */
                conn = NULL;
                rc = lmtrc_parse(lmtrc, &conn);
                if (rc != 0) {
                        tmpconn = conn ?
                                  conn->mysql_conn :
                                  NULL;
                        print_lmterr(tmpconn,
                                     "lmt_monitor_get_filesystem_info(): "
                                     "problem setting up connection");
                        goto err;
                }
                if (conn == NULL) {
                        complete = 1;
                        continue;
                }
                
                errno = 0;
                hash_insert(mylmt->fs, conn->dbname, conn);
                if (errno) {
                        tmpconn = conn ?
                                  conn->mysql_conn :
                                  NULL;
                        print_lmterr(tmpconn,
                                     "lmt_monitor_get_filesystem_info(): "
                                     "problem inserting conn into "
                                     "fs hash");
                        rc = errno;
                        goto err;
                }
                
                /* Actually form the connection */
                mysql_init(conn->mysql_conn);
                tmpconn = mysql_real_connect(conn->mysql_conn,
                                             conn->host,
                                             conn->user,
                                             conn->password,
                                             conn->dbname,
                                             conn->port,
                                             NULL,
                                             0);
                if (tmpconn == NULL) {
                        print_lmterr(NULL,
                                     "lmt_monitor_get_filesystem_info(): "
                                     "mysql_real_connect() failed.");
                        rc = ENOTCONN;
                        goto err;
                }
                mylmt->conn[mylmt->num_conn] = conn;
                
                /* Get the configuration for a 
                 * particular filesystem and place
                 * it in the appropriate hash table */
                rc = lmt_monitor_get_lmtconfig(mylmt->num_conn, mylmt);
                if (rc) {
                        print_lmterr(NULL,
                                     "lmt_monitor_get_filesystem_info(): "
                                     "problem getting filesystem "
                                     "configuration");
                        goto err;
                }
                mylmt->num_conn++;
                
                rc = lmt_setup_time_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
                
                rc = lmt_setup_mds_ops_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
                
                rc = lmt_setup_mds_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
                
                rc = lmt_setup_oss_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
                
                rc = lmt_setup_ost_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
                
                rc = lmt_setup_rtr_stmt(conn);
                if (rc != 0) {
                        goto err;
                }
        }

        if (hash_count(mylmt->fs) <= 0) {
                rc = EINVAL;
                goto err;
        }

err:
        return rc;
}

int lmt_monitor_get_lmtconfig(int conn_idx, struct lmtopt *mylmt)
{
        int rc = 0;

        rc = lmt_monitor_get_operation_info(conn_idx, mylmt);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_monitor_get_lmtconfig(): "
                                   "problem getting operation info");
                goto err;
        }

        rc = lmt_monitor_get_mds_info(conn_idx, mylmt);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_monitor_get_lmtconfig(): "
                                   "problem getting mds info");
                goto err;
        }

        rc = lmt_monitor_get_oss_info(conn_idx, mylmt);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_monitor_get_lmtconfig(): "
                                   "problem getting oss info");
                goto err;
        }

        rc = lmt_monitor_get_ost_info(conn_idx, mylmt);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_monitor_get_lmtconfig(): "
                                   "problem getting ost info");
                goto err;
        }

        rc = lmt_monitor_get_router_info(conn_idx, mylmt);
        if (rc != 0) {
                print_lmterr(NULL, "lmt_monitor_get_lmtconfig(): "
                                   "problem getting router info");
                goto err;
        }

err:
        return rc;
}

void lmt_monitor_destroy_lmtopt(struct lmtopt **mylmt)
{
        struct lmtopt *tmplmt = NULL;
        int i;

        if (mylmt == NULL || (*mylmt) == NULL) {
                return;
        }
        tmplmt = (*mylmt);

        /* Free up hashes */
        hash_destroy(tmplmt->fs);
        hash_destroy(tmplmt->op);
        hash_destroy(tmplmt->mds);
        hash_destroy(tmplmt->oss);
        hash_destroy(tmplmt->ost);
        hash_destroy(tmplmt->router);

        /* Try to close all the connections to the
         * database.  Also free up each db string */
        if (tmplmt->num_conn > 0) {
                for (i = 0; i < tmplmt->num_conn; i++) {
                        if (tmplmt->conn[i] != NULL) {
                                lmt_db_conn_destroy(&tmplmt->conn[i]);
                                tmplmt->conn[i] = NULL;
                        }
                }
        }
        tmplmt->num_conn = 0;

        if (tmplmt->conn != NULL) {
                free(tmplmt->conn);
                tmplmt->conn = NULL;
        }

        memset(tmplmt, 0, sizeof(struct lmtopt));
        free(tmplmt);
        (*mylmt) = NULL;
}

int lmt_monitor_init_lmtopt(struct lmtopt **mylmt)
{
        int rc = 0;

        if (mylmt == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }

        /* Only allocate a new struct lmtopt if
         * (*mylmt) == NULL.  If (*mylmt) != NULL
         * assume that all pointers are valid pointers
         * and free them as necessary to get structure
         * back to good initial state */
        if ((*mylmt) != NULL) {
                lmt_monitor_destroy_lmtopt(mylmt);
        }

        (*mylmt) = malloc(sizeof(struct lmtopt));
        if ((*mylmt) == NULL) {
                return ENOMEM;
        }
        memset((*mylmt), 0, sizeof(struct lmtopt));

        (*mylmt)->mds = NULL;
        (*mylmt)->oss = NULL;
        (*mylmt)->ost = NULL;
        (*mylmt)->router = NULL;

        /* Create Hashes */
        (*mylmt)->fs = hash_create(0,
                                  (hash_key_f) hash_key_string,
                                  lmt_monitor_hash_strcmp,
                                  lmt_monitor_hash_fs_freeitem);
        (*mylmt)->op = hash_create(0,
                                  (hash_key_f) hash_key_string,
                                  lmt_monitor_hash_strcmp,
                                  lmt_monitor_hash_freeitem);
        (*mylmt)->mds = hash_create(0,
                                   (hash_key_f) hash_key_string,
                                   lmt_monitor_hash_strcmp,
                                   lmt_monitor_hash_freeitem);
        (*mylmt)->oss = hash_create(0,
                                   (hash_key_f) hash_key_string,
                                   lmt_monitor_hash_strcmp,
                                   lmt_monitor_hash_freeitem);
        (*mylmt)->ost = hash_create(0,
                                   (hash_key_f) hash_key_string,
                                   lmt_monitor_hash_strcmp,
                                   lmt_monitor_hash_freeitem);
        (*mylmt)->router = hash_create(0,
                                      (hash_key_f) hash_key_string,
                                      lmt_monitor_hash_strcmp,
                                      lmt_monitor_hash_freeitem);

        (*mylmt)->conn = malloc(MAX_LMT_MYSQL_CONN * sizeof(struct lmt_db_conn *));
        if ((*mylmt)->conn == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset((*mylmt)->conn, 0, MAX_LMT_MYSQL_CONN * sizeof(struct lmt_db_conn *));

        return 0;
err:
        lmt_monitor_destroy_lmtopt(mylmt);
        return rc;
}


/*
 * lmt_monitor_interface_version
 *
 * lmt monitor module interface_version function
 */
static int
lmt_monitor_interface_version(void)
{
  return CEREBRO_MONITOR_INTERFACE_VERSION;
}

/*
 * lmt_monitor_setup
 *
 * lmt monitor module setup function.
 */
static int
lmt_monitor_setup(void)
{
        int rc = 0;
        
        g_lmt = malloc(sizeof(struct lmtopt));
        if (g_lmt == NULL) {
                return ENOMEM;
        }
        memset(g_lmt, 0, sizeof(struct lmtopt));

        /* Initialize the global lmtopt structure */
        rc = lmt_monitor_init_lmtopt(&g_lmt);
        if (rc != 0) {
                goto err;
        }

         /* Before connecting to a particular filesystem_* database
          * see which filesystems exist first */
        rc = lmt_monitor_get_filesystem_info(g_lmt);
        if (rc) {
                print_lmterr(NULL, "lmt_monitor_setup(): "
                                   "problem getting filesystem info");
                goto err;
        }

        return 0;

err:
        lmt_monitor_destroy_lmtopt(&g_lmt);
        return -1;
}

/*
 * lmt_monitor_cleanup
 *
 * lmt monitor module cleanup function
 */
static int
lmt_monitor_cleanup(void)
{
        return 0;
}

/*
 * lmt_monitor_metric_names
 *
 * lmt monitor module metric_names function
 */
static char *
lmt_monitor_metric_names(void)
{
        return LMT_MONITOR_METRIC_NAMES;
}

int __lmt_monitor_update_timestamp(struct lmt_info *lmtinfo)
{
        unsigned long long secs;
        struct timeval tm;
        int i = 0, rem = 0;
        int rc = 0;

        gettimeofday(&tm, NULL);
        secs = (unsigned long long) tm.tv_sec;

        /* Make secs divisible by 5 so we will not insert
         * too many timestamps */
        rem = secs % 10;
        if (rem < 5) {
                secs -= rem;
        }
        else if (rem > 5) {
                rem -= 5;
                secs -= rem;
        }

        /* Here we want to update the timestamps in each
         * relevant database */
        for (i = 0; i < lmtinfo->lmt_num_conn; i++) {
                char query[MAX_LMT_QUERY_LEN];
                
                if (secs <= lmtinfo->lmt_conn[i]->timestamp) {
                        continue;
                }
                lmtinfo->lmt_conn[i]->timestamp = secs;

                snprintf(query, MAX_LMT_QUERY_LEN, "insert into TIMESTAMP_INFO "
                                                       "(TIMESTAMP) values "
                                                       "(FROM_UNIXTIME('%llu')) ",
                                                       secs);
                rc = mysql_query(lmtinfo->lmt_conn[i]->mysql_conn, query);
                if (rc != 0) {
                        print_lmterr(lmtinfo->lmt_conn[i]->mysql_conn,
                                     "update_lmt_db(): "
                                     "problem inserting timestamp");
                        rc = mysql_errno(lmtinfo->lmt_conn[i]->mysql_conn);
                        return rc;
                }
                else {
                        /* Update the timestamp id for this database connection */
                        lmtinfo->lmt_conn[i]->timestampid = (unsigned long long) mysql_insert_id(lmtinfo->lmt_conn[i]->mysql_conn);
                }
        }

        return rc;
}

int __lmt_mds_monitor_update_db(struct lmtopt *mylmt,
                                unsigned int buflen,
                                char *buf)
{
        int i, j;
        int rc = 0;
        char *tok = NULL;
        char *saveptr = NULL;
        struct lmt_info *lmtinfo = NULL;
        struct lmt_info *lmtopinfo = NULL;
        struct mds_info *mds = NULL;
        struct lmt_db_item *itm = NULL;
        char *lmt_key;
        MYSQL_BIND *param = NULL;

        rc = lmt_db_item_init(&itm);
        if (rc != 0) {
                cerebro_err_output("problem initializing db item");
                goto err;
        }

        /* Parse the data coming in */
        tok = strtok_r(buf, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing mds version");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the protocol version */
        itm->versionsize = strlen(tok) + 1;
        itm->version = malloc(sizeof(char) * itm->versionsize);
        if (itm->version == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->version, tok);

        /* Ensure that the protocol version is something we expect */
        if (strcmp(itm->version, LMT_MDS_PROTOCOL_VERSION) != 0) {
                cerebro_err_output("mds protocol version mismatch");
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing mds hostname");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS hostname */
        itm->hostnamesize = strlen(tok) + 1;
        itm->hostname = malloc(sizeof(char) * itm->hostnamesize);
        if (itm->hostname == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->hostname, tok);

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing UUID");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS UUID */
        itm->uuidsize = strlen(tok) + 1;
        itm->uuid = malloc(sizeof(char) * itm->uuidsize);
        if (itm->uuid == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->uuid, tok);
        lmt_key = itm->uuid;

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing cpu usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS CPU Usage */
        errno = 0;
        itm->cpu_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing memory usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS Memory Usage */
        errno = 0;
        itm->memory_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing filesfree");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS Files Free */
        errno = 0;
        itm->filesfree = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing filestotal");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS Files Total */
        errno = 0;
        itm->filestotal = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing kbytesfree");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS Kbytes Free */
        errno = 0;
        itm->kbytesfree = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing kbytestotal");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the MDS Kbytes Total */
        errno = 0;
        itm->kbytestotal = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        for (i = LMT_MDS_OPERATIONS_IDX_START; i <= LMT_MDS_OPERATIONS_IDX_END; i++) {

                strcpy(itm->fs_op[i].name, lmt_fsop_jt[i].op);

                /* Get the next token */
                tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
                if (tok == NULL) {
                        cerebro_err_debug("problem parsing fs op");
                        rc = CEREBRO_ERR_INTERNAL;
                        goto err;
                }

                errno = 0;
                itm->fs_op[i].numsamples = strtoull(tok, NULL, 0);
                if (errno != 0) {
                        rc = errno;
                        goto err;
                }

                /* Get the next token */
                tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
                if (tok == NULL) {
                        cerebro_err_debug("problem parsing fs op");
                        rc = CEREBRO_ERR_INTERNAL;
                        goto err;
                }

                errno = 0;
                itm->fs_op[i].value = strtoull(tok, NULL, 0);
                if (errno != 0) {
                        rc = errno;
                        goto err;
                }

                /* Get the next token */
                tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
                if (tok == NULL) {
                        cerebro_err_debug("problem parsing fs op");
                        rc = CEREBRO_ERR_INTERNAL;
                        goto err;
                }

                errno = 0;
                itm->fs_op[i].sumsquares = strtoull(tok, NULL, 0);
                if (errno != 0) {
                        rc = errno;
                        goto err;
                }
        }

        errno = 0;
        lmtinfo = hash_find(mylmt->mds, lmt_key);
        if (lmtinfo != NULL) {
                /* Try to update the timestamp */
                rc = __lmt_monitor_update_timestamp(lmtinfo);
                if (rc != 0) {
                        goto err;
                }

                for (i = 0; i < lmtinfo->lmt_num_conn; i++) {

                        /* Insert into MDS_DATA table */
                        mds = (struct mds_info *) lmtinfo->lmt_data[i];
                        param = lmtinfo->lmt_conn[i]->mds_param;
                        if (mds == NULL || param == NULL) {
                                rc = EINVAL;
                                goto err;
                        }
                        *((unsigned long long *) param[0].buffer) = mds->mds_id;
                        *((unsigned long long *) param[1].buffer) = lmtinfo->lmt_conn[i]->timestampid;
                        *((float *)              param[2].buffer) = itm->cpu_usage;
                        *((unsigned long long *) param[3].buffer) = itm->kbytesfree;
                        *((unsigned long long *) param[4].buffer) = itm->kbytestotal - itm->kbytesfree;
                        *((unsigned long long *) param[5].buffer) = itm->filesfree;
                        *((unsigned long long *) param[6].buffer) = itm->filestotal - itm->filesfree;
                        rc = mysql_stmt_execute(lmtinfo->lmt_conn[i]->mds_stmt);
                        if (rc != 0) {
                                print_lmtstmterr(lmtinfo->lmt_conn[i]->mds_stmt,
                                                 "update_lmt_db(): "
                                                 "problem inserting mds row");
                                rc = mysql_stmt_errno(lmtinfo->lmt_conn[i]->mds_stmt);
                                goto err;
                        }

                        /* Start inserting items into the MDS_OPS_DATA table */
                        param = lmtinfo->lmt_conn[i]->mds_ops_param;

                        *((unsigned long long *) param[0].buffer) = mds->mds_id;
                        *((unsigned long long *) param[2].buffer) = lmtinfo->lmt_conn[i]->timestampid;
                        for (j = LMT_MDS_OPERATIONS_IDX_START; j <= LMT_MDS_OPERATIONS_IDX_END; j++) {
                                struct operation_info *op = NULL;
                                errno = 0;
                                lmtopinfo = hash_find(mylmt->op, itm->fs_op[j].name);
                                if (lmtopinfo == NULL) {
                                        continue;
                                }
                                op = (struct operation_info *) lmtopinfo->lmt_data[i];

                                *((unsigned long long *) param[1].buffer) = op->operation_id;
                                *((unsigned long long *) param[3].buffer) = itm->fs_op[j].numsamples;
                                *((unsigned long long *) param[4].buffer) = itm->fs_op[j].value;
                                *((unsigned long long *) param[5].buffer) = itm->fs_op[j].sumsquares;

                                rc = mysql_stmt_execute(lmtinfo->lmt_conn[i]->mds_ops_stmt);
                                if (rc != 0) {
                                        print_lmtstmterr(lmtinfo->lmt_conn[i]->mds_ops_stmt,
                                                         "update_lmt_db(): "
                                                         "problem inserting mds ops row");
                                        rc = mysql_stmt_errno(lmtinfo->lmt_conn[i]->mds_ops_stmt);
                                        goto err;
                                }
                        }
                }
        }

err:
        lmt_db_item_destroy(&itm);
        return rc;
}

int __lmt_oss_monitor_update_db(struct lmtopt *mylmt,
                                unsigned int buflen,
                                char *buf)
{
        int i;
        int rc = 0;
        char *tok = NULL;
        char *saveptr = NULL;
        struct lmt_info *lmtinfo = NULL;
        struct oss_info *oss = NULL;
        struct lmt_db_item *itm = NULL;
        char *lmt_key;
        MYSQL_BIND *param = NULL;

        rc = lmt_db_item_init(&itm);
        if (rc != 0) {
                cerebro_err_output("problem initializing db item");
                goto err;
        }

        /* Parse the data coming in */
        tok = strtok_r(buf, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing oss version");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the protocol version */
        itm->versionsize = strlen(tok) + 1;
        itm->version = malloc(sizeof(char) * itm->versionsize);
        if (itm->version == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->version, tok);

        /* Ensure that the protocol version is something we expect */
        if (strcmp(itm->version, LMT_OSS_PROTOCOL_VERSION) != 0) {
                cerebro_err_output("oss protocol version mismatch");
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing oss hostname");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OSS hostname */
        itm->hostnamesize = strlen(tok) + 1;
        itm->hostname = malloc(sizeof(char) * itm->hostnamesize);
        if (itm->hostname == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->hostname, tok);
        lmt_key = itm->hostname;

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing CPU usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OSS CPU Usage */
        errno = 0;
        itm->cpu_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing memory usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OSS Memory Usage */
        errno = 0;
        itm->memory_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        errno = 0;
        lmtinfo = hash_find(mylmt->oss, lmt_key);
        if (lmtinfo != NULL) {
                /* Try to update the timestamp */
                rc = __lmt_monitor_update_timestamp(lmtinfo);
                if (rc != 0) {
                        goto err;
                }

                for (i = 0; i < lmtinfo->lmt_num_conn; i++) {

                        /* Start inserting items into the OSS_DATA table */
                        oss = (struct oss_info *) lmtinfo->lmt_data[i];
                        param = lmtinfo->lmt_conn[i]->oss_param;
                        if (oss == NULL || param == NULL) {
                                rc = EINVAL;
                                goto err;
                        }

                        *((unsigned long long *) param[0].buffer) = oss->oss_id;
                        *((unsigned long long *) param[1].buffer) = lmtinfo->lmt_conn[i]->timestampid;
                        *((float *)              param[2].buffer) = itm->cpu_usage;
                        *((float *)              param[3].buffer) = itm->memory_usage;

                        rc = mysql_stmt_execute(lmtinfo->lmt_conn[i]->oss_stmt);
                        if (rc != 0) {
                                print_lmtstmterr(lmtinfo->lmt_conn[i]->oss_stmt,
                                             "update_lmt_db(): "
                                             "problem inserting oss row");
                                rc = mysql_stmt_errno(lmtinfo->lmt_conn[i]->oss_stmt);
                                goto err;
                        }
                }
        }

err:
        lmt_db_item_destroy(&itm);
        return rc;
}

int __lmt_ost_monitor_update_db(struct lmtopt *mylmt,
                                unsigned int buflen,
                                char *buf)
{
        int i;
        int rc = 0;
        char *tok = NULL;
        char *saveptr = NULL;
        struct lmt_info *lmtinfo = NULL;
        struct ost_info *ost = NULL;
        struct lmt_db_item *itm = NULL;
        char *lmt_key;
        MYSQL_BIND *param = NULL;

        rc = lmt_db_item_init(&itm);
        if (rc != 0) {
                cerebro_err_output("problem initializing db item");
                goto err;
        }

        /* Parse the data coming in */
        tok = strtok_r(buf, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing ost version");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the protocol version */
        itm->versionsize = strlen(tok) + 1;
        itm->version = malloc(sizeof(char) * itm->versionsize);
        if (itm->version == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->version, tok);

        /* Ensure that the protocol version is something we expect */
        if (strcmp(itm->version, LMT_OST_PROTOCOL_VERSION) != 0) {
                cerebro_err_output("ost protocol version mismatch");
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing ost hostname");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST hostname */
        itm->hostnamesize = strlen(tok) + 1;
        itm->hostname = malloc(sizeof(char) * itm->hostnamesize);
        if (itm->hostname == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->hostname, tok);

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing ost uuid");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST UUID */
        itm->uuidsize = strlen(tok) + 1;
        itm->uuid = malloc(sizeof(char) * itm->uuidsize);
        if (itm->uuid == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->uuid, tok);
        lmt_key = itm->uuid;

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing files free");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Files Free */
        errno = 0;
        itm->filesfree = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing filestotal");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Files Total */
        errno = 0;
        itm->filestotal = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing kbytesfree");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Kbytes Free */
        errno = 0;
        itm->kbytesfree = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing kbytestotal");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Kbytes Total */
        errno = 0;
        itm->kbytestotal = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing ost read bw");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Read BW */
        errno = 0;
        itm->rbw = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing ost read bw");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the OST Write BW */
        errno = 0;
        itm->wbw = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        errno = 0;
        lmtinfo = hash_find(mylmt->ost, lmt_key);
        if (lmtinfo != NULL) {
                /* Try to update the timestamp */
                rc = __lmt_monitor_update_timestamp(lmtinfo);
                if (rc != 0) {
                        goto err;
                }

                for (i = 0; i < lmtinfo->lmt_num_conn; i++) {

                        /* Start inserting items into the OST_DATA table */
                        ost = (struct ost_info *) lmtinfo->lmt_data[i];
                        param = lmtinfo->lmt_conn[i]->ost_param;
                        if (ost == NULL || param == NULL) {
                                rc = EINVAL;
                                goto err;
                        }

                        *((unsigned long long *) param[0].buffer) = ost->ost_id;
                        *((unsigned long long *) param[1].buffer) = lmtinfo->lmt_conn[i]->timestampid;
                        *((unsigned long long *) param[2].buffer) = itm->rbw;
                        *((unsigned long long *) param[3].buffer) = itm->wbw;
                        *((unsigned long long *) param[4].buffer) = itm->kbytesfree;
                        *((unsigned long long *) param[5].buffer) = itm->kbytestotal - itm->kbytesfree;
                        *((unsigned long long *) param[6].buffer) = itm->filesfree;
                        *((unsigned long long *) param[7].buffer) = itm->filestotal - itm->filesfree;

                        rc = mysql_stmt_execute(lmtinfo->lmt_conn[i]->ost_stmt);
                        if (rc != 0) {
                                print_lmtstmterr(lmtinfo->lmt_conn[i]->ost_stmt,
                                             "update_lmt_db(): "
                                             "problem inserting ost row");
                                rc = mysql_stmt_errno(lmtinfo->lmt_conn[i]->ost_stmt);
                                goto err;
                        }
                }
        }

err:
        lmt_db_item_destroy(&itm);
        return rc;
}

int __lmt_router_monitor_update_db(struct lmtopt *mylmt,
                                   unsigned int buflen,
                                   char *buf)
{
        int i;
        int rc = 0;
        char *tok = NULL;
        char *saveptr = NULL;
        struct lmt_info *lmtinfo = NULL;
        struct router_info *rtr = NULL;
        struct lmt_db_item *itm = NULL;
        char *lmt_key;
        MYSQL_BIND *param = NULL;

        rc = lmt_db_item_init(&itm);
        if (rc != 0) {
                cerebro_err_output("problem initializing db item");
                goto err;
        }

        /* Parse the data coming in */
        tok = strtok_r(buf, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing router version");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the protocol version */
        itm->versionsize = strlen(tok) + 1;
        itm->version = malloc(sizeof(char) * itm->versionsize);
        if (itm->version == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->version, tok);

        /* Ensure that the protocol version is something we expect */
        if (strcmp(itm->version, LMT_ROUTER_PROTOCOL_VERSION) != 0) {
                cerebro_err_output("router protocol version mismatch");
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing router hostname");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the Router hostname */
        itm->hostnamesize = strlen(tok) + 1;
        itm->hostname = malloc(sizeof(char) * itm->hostnamesize);
        if (itm->hostname == NULL) {
                rc = ENOMEM;
                goto err;
        }
        strcpy(itm->hostname, tok);
        lmt_key = itm->hostname;

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing CPU usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the Router CPU Usage */
        errno = 0;
        itm->cpu_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing memory usage");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the Router Memory Usage */
        errno = 0;
        itm->memory_usage = strtod(tok, NULL);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        /* Get the next token */
        tok = strtok_r(NULL, LMT_SEPARATOR, &saveptr);
        if (tok == NULL) {
                cerebro_err_debug("problem parsing bandwidth");
                rc = CEREBRO_ERR_INTERNAL;
                goto err;
        }

        /* Get the LNET bandwidth */
        errno = 0;
        itm->rbw = strtoull(tok, NULL, 0);
        if (errno != 0) {
                rc = errno;
                goto err;
        }

        errno = 0;
        lmtinfo = hash_find(mylmt->router, lmt_key);
        if (lmtinfo != NULL) {
                /* Try to update the timestamp */
                rc = __lmt_monitor_update_timestamp(lmtinfo);
                if (rc != 0) {
                        goto err;
                }

                for (i = 0; i < lmtinfo->lmt_num_conn; i++) {
                        
                        /* Start inserting items into ROUTER_DATA table */
                        rtr = (struct router_info *) lmtinfo->lmt_data[i];
                        param = lmtinfo->lmt_conn[i]->rtr_param;
                        if (rtr == NULL || param == NULL) {
                                rc = EINVAL;
                                goto err;
                        }

                        *((unsigned long long *) param[0].buffer) = rtr->router_id;
                        *((unsigned long long *) param[1].buffer) = lmtinfo->lmt_conn[i]->timestampid;
                        *((unsigned long long *) param[2].buffer) = itm->rbw;
                        *((float *)              param[3].buffer) = itm->cpu_usage;

                        rc = mysql_stmt_execute(lmtinfo->lmt_conn[i]->rtr_stmt);
                        if (rc != 0) {
                                print_lmtstmterr(lmtinfo->lmt_conn[i]->rtr_stmt,
                                             "update_lmt_db(): "
                                             "problem inserting router row");
                                rc = mysql_stmt_errno(lmtinfo->lmt_conn[i]->rtr_stmt);
                                goto err;
                        }
                }
        }

err:
        lmt_db_item_destroy(&itm);
        return rc;
}

/*
 * lmt_monitor_metric_update
 *
 * lmt monitor module metric_update function.
 */
static int 
lmt_monitor_metric_update(const char *nodename,
                          const char *metric_name,
                          unsigned int metric_value_type,
                          unsigned int metric_value_len,
                          void *metric_value)
{
        int rv = 0;

        if (!nodename ||
            !metric_name ||
            !metric_value) {
                cerebro_err_debug("invalid parameters");
                return CEREBRO_ERR_PARAMETERS;
        }

        if (strcmp(metric_name, LMT_MDS_METRIC_NAME) == 0) {
                rv = __lmt_mds_monitor_update_db(g_lmt,
                                                 metric_value_len,
                                                 (char *) metric_value);
        }
        else if (strcmp(metric_name, LMT_OSS_METRIC_NAME) == 0) {
                rv = __lmt_oss_monitor_update_db(g_lmt,
                                                 metric_value_len,
                                                 (char *) metric_value);
        }
        else if (strcmp(metric_name, LMT_OST_METRIC_NAME) == 0) {
                rv = __lmt_ost_monitor_update_db(g_lmt,
                                                 metric_value_len,
                                                 (char *) metric_value);
        }
        else if (strcmp(metric_name, LMT_ROUTER_METRIC_NAME) == 0) {
                rv = __lmt_router_monitor_update_db(g_lmt,
                                                    metric_value_len,
                                                    (char *) metric_value);
        }
        else {
                cerebro_err_debug("invalid metric %s", metric_name);
                return CEREBRO_ERR_PARAMETERS;
        }
        
        if (rv == CR_SERVER_GONE_ERROR ||
            rv == CR_SERVER_LOST) {
                cerebro_err_output("lmt_monitor_metric_update(): Connection Lost "
                                    "rv: %d.  Attempting reconnect.", rv);
                
                while (rv != 0) {
                        /* A bit heavy handed but should do */
                        lmt_monitor_destroy_lmtopt(&g_lmt);
                        rv = lmt_monitor_setup();
                        if (rv) {
                                print_lmterr(NULL, "lmt_monitor_metric_update(): "
                                                   "problem getting filesystem info");
                        }
                        sleep(15);
                }
        }
        
        return rv;
}

#if WITH_STATIC_MODULES
struct cerebro_monitor_module_info lmt_monitor_module_info = {
#else  /* !WITH_STATIC_MODULES */
struct cerebro_monitor_module_info monitor_module_info = {
#endif /* !WITH_STATIC_MODULES */
        LMT_MONITOR_MODULE_NAME,
        &lmt_monitor_interface_version,
        &lmt_monitor_setup,
        &lmt_monitor_cleanup,
        &lmt_monitor_metric_names,
        &lmt_monitor_metric_update,
};

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
