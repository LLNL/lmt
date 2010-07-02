/***************************************************************************** *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>.
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

/* A note on mysql authentication:
 *
 * In lmt 2.0: lmt was configured via two config files:
 *  . for java clients (read-only): /usr/share/lmt/etc/lmtrc or ~/.lmtrc
 *  . for cerebro monitor and aggregation scripts: /usr/share/lmt/cron/lmtrc
 * This allowed cerebrod to run remotely from mysqld, and be configured
 * differently on a per-file system basis.  This was complicated and as
 * far as I know was not fulfilling any particular need.
 * 
 * In lmt 3.0, /usr/share/lmt/cron/lmtrc goes away and cerebro monitor,
 * aggregation scripts, and new utilities are hardwired to use UNIX auth
 * on localhost:3306.  Thus when upgrading it is necessary to grant
 * root@localhost read-write access to the database, and then ensure that
 * cerebrod and aggregation scripts run as root (or pick another account,
 * as long as everybody uses the same one).  Any old "lwatchadmin" account
 * or similar read-write account can be removed.
 *
 * The clients still authenticate as before, with mysql usernames/passwords
 * stored in /usr/share/lmt/etc/lmtrc or ~/.lmtrc.
 */
 
#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <unistd.h>
#include <errno.h>
#include <assert.h>
#include <stdint.h>
#include <sys/time.h>

#include <mysql/mysql.h>
#include <mysql/errmsg.h>

#include "list.h"
#include "hash.h"
#include "proc.h"
#include "mysql.h"
#include "lmt.h"

#define IDHASH_SIZE     256

typedef struct {
    char *key;
    uint64_t id;
} svcid_t;

#define LMT_DBHANDLE_MAGIC 0x5454aabf
struct lmt_db_struct {
    int magic;
    MYSQL *conn;

    /* cached prepared statements for high-frequency inserts */
    MYSQL_STMT *ins_timestamp_info;
    MYSQL_STMT *ins_mds_data;
    MYSQL_STMT *ins_mds_ops_data;
    MYSQL_STMT *ins_oss_data;
    MYSQL_STMT *ins_ost_data;
    MYSQL_STMT *ins_router_data;

    /* cached most recent TIMESTAMP_INFO insertion */
    uint64_t timestamp;
    uint64_t timestamp_id;

    /* hash to map names to database id's */
    hash_t idhash;
};

const char *sql_ins_timestamp_info = 
    "insert into TIMESTAMP_INFO "
    "(TIMESTAMP) " 
    "values ( FROM_UNIXTIME(?) )";
const char *sql_ins_mds_data =
    "insert into MDS_DATA "
    "(MDS_ID, TS_ID, PCT_CPU, KBYTES_FREE, KBYTES_USED, INODES_FREE, "
    "INODES_USED) "
    "values ( ?, ?, ?, ?, ?, ?, ?)";
const char *sql_ins_mds_ops_data = 
    "insert into MDS_OPS_DATA "
    "(MDS_ID, OPERATION_ID, TS_ID, SAMPLES, SUM, SUMSQUARES) "
    "values (?, ?, ?, ?, ?, ?)";
const char *sql_ins_oss_data =
    "insert into OSS_DATA "
    "(OSS_ID, TS_ID, PCT_CPU, PCT_MEMORY) " 
    "values (?, ?, ?, ?)";
const char *sql_ins_ost_data =
    "insert into OST_DATA "
    "(OST_ID, TS_ID, READ_BYTES, WRITE_BYTES, KBYTES_FREE, KBYTES_USED, "
    "INODES_FREE, INODES_USED) "
    "values ( ?, ?, ?, ?, ?, ?, ?, ?)";
const char *sql_ins_router_data =
    "insert into ROUTER_DATA "
    "(ROUTER_ID, TS_ID, BYTES, PCT_CPU) "
    "values (?, ?, ?, ?)";

const char *sql_sel_mds_info =
    "select MDS_NAME, MDS_ID from MDS_INFO";
const char *sql_sel_oss_info =
    "select HOSTNAME, OSS_ID from OSS_INFO";
const char *sql_sel_ost_info =
    "select OST_NAME, OST_ID from OST_INFO";
const char *sql_sel_router_info =
    "select HOSTNAME, ROUTER_ID from ROUTER_INFO";
const char *sql_sel_operation_info =
    "select OPERATION_NAME, OPERATION_ID from OPERATION_INFO";

/**
 ** Idhash functions (internal)
 **/

static void
_destroy_svcid (svcid_t *s)
{
    if (s) {
        if (s->key)
            free (s->key);
        free (s);
   }
}

static int
_create_svcid (const char *key_prefix, const char *key,
               uint64_t id, svcid_t **sp)
{
    svcid_t *s = malloc (sizeof (svcid_t));
    int keylen = strlen (key) + strlen (key_prefix) + 1;

    if (!s)
        goto nomem;
    memset (s, 0, sizeof (svcid_t));
    if (!(s->key = malloc (keylen)))
        goto nomem;
    snprintf (s->key, keylen, "%s%s", key_prefix, key);
    s->id = id;
    *sp = s;
    return 0;
nomem:
    _destroy_svcid (s);
    errno = ENOMEM;
    return -1;
}

static int
_verify_type (MYSQL_RES *res, int i, enum enum_field_types t)
{
    int retval = -1;
    MYSQL_FIELD *field;

    mysql_field_seek(res, i);
    if (!(field = mysql_fetch_field(res)))
        goto done;
    if (field->type != t)
        goto done;
    retval = 0;
done:
    return retval;
}

static int
_populate_idhash_qry (lmt_db_t db, const char *sql, const char *pfx)
{
    int retval = -1;
    MYSQL_RES *res = NULL;
    MYSQL_ROW row;
    uint64_t id;
    svcid_t *s; 

    if (mysql_query (db->conn, sql))
        goto done;
    if (!(res = mysql_store_result (db->conn)))
        goto done;
    while ((row = mysql_fetch_row (res))) {
        if (_verify_type (res, 0, MYSQL_TYPE_VARCHAR) < 0)
            goto done;
        if (_verify_type (res, 1, MYSQL_TYPE_LONG) < 0)
            goto done;
        id = strtoul (row[1], NULL, 10);
        if (_create_svcid (pfx, row[0], id, &s) < 0)
            goto done;
        if (!hash_insert (db->idhash, s->key, s))
            goto done;
    }
    retval = 0;
done:
    if (res)
        mysql_free_result (res);
    return retval;
}

static int
_populate_idhash (lmt_db_t db)
{
    int retval = -1;

    /* MDS_INFO:    MDS_NAME -> MDS_ID */
    if (_populate_idhash_qry (db, sql_sel_mds_info, "mds") < 0)
        goto done;
    /* OSS_INFO:    HOSTNAME -> OSS_ID */
    if (_populate_idhash_qry (db, sql_sel_oss_info, "oss") < 0)
        goto done;
    /* OST_INFO:    OST_NAME -> OST_ID */
    if (_populate_idhash_qry (db, sql_sel_ost_info, "ost") < 0)
        goto done;
    /* ROUTER_INFO: HOSTNAME -> ROUTER_ID */
    if (_populate_idhash_qry (db, sql_sel_router_info, "router") < 0)
        goto done;
    /* OPERATION_INFO: OPERATION_NAME -> OPERATION_ID */
    if (_populate_idhash_qry (db, sql_sel_operation_info, "op") < 0)
        goto done;
    retval = 0;
done: 
    return retval;
}

static int
_lookup_idhash (lmt_db_t db, char *svctype, char *name, uint64_t *idp)
{
    char key[128];
    int n, retval = -1;
    svcid_t *s;

    n = snprintf (key, sizeof(key), "%s_%s", svctype, name);
    if (n >= sizeof (key)) {
        errno = E2BIG;
        goto done;
    }
    if (!(s = hash_find (db->idhash, key))) {
        errno = ESRCH;
        goto done;
    }
    retval = 0;
    if (idp)
        *idp = s->id;
done:
    return retval;
}

int
lmt_db_lookup (lmt_db_t db, char *svctype, char *name)
{
    return _lookup_idhash (db, svctype, name, NULL);
}

/**
 ** Database insert functions
 **/

static void
_param_init_int (MYSQL_BIND *p, enum enum_field_types t, void *vp)
{
    p->buffer_type = t;
    p->is_unsigned = 1;
    p->buffer = vp;
}

static int
_update_timestamp (lmt_db_t db)
{
    MYSQL_BIND param[1];
    uint64_t timestamp;
    struct timeval tv;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_timestamp_info) == 1);

    /* N.B. Round timestamp down to nearest multiple of LMT_UPDATE_INTERVAL,
     * seconds and don't insert a new entry if <= the last timestamp inserted.
     * This keeps the number of rows in TIMESTAMP_INFO in check.
     */
    if (gettimeofday (&tv, NULL) < 0)
        goto done;
    timestamp = tv.tv_sec;
    timestamp -= (timestamp % LMT_UPDATE_INTERVAL);
    if (timestamp <= db->timestamp) {
        retval = 0;
        goto done;
    }
    memset (param, 0, sizeof (param));
    _param_init_int (&param[0], MYSQL_TYPE_LONGLONG, &timestamp);

    if (mysql_stmt_bind_param (db->ins_timestamp_info, param))
        goto done;
    if (mysql_stmt_execute (db->ins_timestamp_info))
        goto done;
    db->timestamp = timestamp;
    db->timestamp_id = (uint64_t)mysql_insert_id (db->conn);
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_mds_data (lmt_db_t db, char *name, float pct_cpu,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used,
                        const char **sqlerrp)
{
    MYSQL_BIND param[7];
    uint64_t mds_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_mds_data) == 7);

    if (_lookup_idhash(db, "mds", name, &mds_id) < 0)
        goto done;
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    /* FIXME: we have type LONG and LONGLONG both pointing to uint64_t */
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &mds_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_FLOAT, &pct_cpu);
    /* FIXME: [schema] we gather pct_memory but we don't insert it */
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &kbytes_free);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &kbytes_used);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &inodes_free);
    _param_init_int (&param[6], MYSQL_TYPE_LONGLONG, &inodes_used);
   
    if (mysql_stmt_bind_param (db->ins_mds_data, param))
        goto done;
    if (mysql_stmt_execute (db->ins_mds_data))
        goto done;
    //if (mysql_stmt_affected_rows (db->mds_data) != 1)
    //    goto done;
    retval = 0;
done:
    if (retval < 0)
        *sqlerrp = mysql_error (db->conn);
    return retval;
}

int
lmt_db_insert_mds_ops_data (lmt_db_t db, char *mdsname, char *opname,
                        uint64_t samples, uint64_t sum, uint64_t sumsquares,
                        const char **sqlerrp)
{
    MYSQL_BIND param[6];
    uint64_t mds_id, op_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_mds_ops_data) == 6);

    if (_lookup_idhash (db, "mds", mdsname, &mds_id) < 0)
        goto done;
    if (_lookup_idhash (db, "op", opname, &op_id) < 0)
        goto done;
    //if (_update_timestamp (db) < 0)
    //    goto done;

    memset (param, 0, sizeof (param));
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &mds_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &op_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &samples);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &sum);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &sumsquares);
   
    if (mysql_stmt_bind_param (db->ins_mds_ops_data, param))
        goto done;
    if (mysql_stmt_execute (db->ins_mds_ops_data))
        goto done;
    retval = 0;
done:
    if (retval < 0)
        *sqlerrp = mysql_error (db->conn);
    return retval;
}

int
lmt_db_insert_oss_data (lmt_db_t db, char *name, float pct_cpu,
                        float pct_memory, const char **sqlerrp)
{
    MYSQL_BIND param[4];
    uint64_t oss_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_oss_data) == 4);

    if (_lookup_idhash (db, "oss", name, &oss_id) < 0)
        goto done;
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &oss_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_FLOAT, &pct_cpu);
    _param_init_int (&param[3], MYSQL_TYPE_FLOAT, &pct_memory);
   
    if (mysql_stmt_bind_param (db->ins_oss_data, param))
        goto done;
    if (mysql_stmt_execute (db->ins_oss_data))
        goto done;
    retval = 0;
done:
    if (retval < 0)
        *sqlerrp = mysql_error (db->conn);
    return retval;
}

int
lmt_db_insert_ost_data (lmt_db_t db, char *name,
                        uint64_t read_bytes, uint64_t write_bytes,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used,
                        const char **sqlerrp)
{
    MYSQL_BIND param[8];
    uint64_t ost_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_ost_data) == 8);

    if (_lookup_idhash (db, "ost", name, &ost_id) < 0)
        goto done;
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &ost_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONGLONG, &read_bytes);
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &write_bytes);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &kbytes_free);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &kbytes_used);
    _param_init_int (&param[6], MYSQL_TYPE_LONGLONG, &inodes_free);
    _param_init_int (&param[7], MYSQL_TYPE_LONGLONG, &inodes_used);
   
    if (mysql_stmt_bind_param (db->ins_ost_data, param))
        goto done;
    if (mysql_stmt_execute (db->ins_ost_data))
        goto done;
    retval = 0;
done:
    if (retval < 0)
        *sqlerrp = mysql_error (db->conn);
    return retval;
}

int
lmt_db_insert_router_data (lmt_db_t db, char *name, uint64_t bytes,
                           float pct_cpu, const char **sqlerrp)
{
    MYSQL_BIND param[4];
    uint64_t router_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    assert (mysql_stmt_param_count (db->ins_router_data) == 4);

    if (_lookup_idhash (db, "router", name, &router_id) < 0)
        goto done;
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &router_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONGLONG, &bytes);
    _param_init_int (&param[3], MYSQL_TYPE_FLOAT, &pct_cpu);
   
    if (mysql_stmt_bind_param (db->ins_router_data, param))
        goto done;
    if (mysql_stmt_execute (db->ins_router_data))
        goto done;
    retval = 0;
done:
    if (retval < 0)
        *sqlerrp = mysql_error (db->conn);
    return retval;
}

/**
 ** Database handle functions
 **/

static int
_prepare_stmt (lmt_db_t db, MYSQL_STMT **sp, const char *sql)
{
    int retval = -1;
    MYSQL_STMT *s;

    if (!(s = mysql_stmt_init (db->conn))) {
        errno = ENOMEM;
        goto done;
    }
    errno = 0;
    if (mysql_stmt_prepare (s, sql, strlen (sql))) {
        mysql_stmt_close (s);
        goto done;
    }
    *sp = s;
    retval = 0;
done:
    return retval;
}

void
lmt_db_destroy (lmt_db_t db)
{
    assert (db->magic == LMT_DBHANDLE_MAGIC);

    if (db->ins_timestamp_info)
        mysql_stmt_close (db->ins_timestamp_info);
    if (db->ins_mds_data)
        mysql_stmt_close (db->ins_mds_data);
    if (db->ins_mds_ops_data)
        mysql_stmt_close (db->ins_mds_ops_data);
    if (db->ins_oss_data)
        mysql_stmt_close (db->ins_oss_data);
    if (db->ins_ost_data)
        mysql_stmt_close (db->ins_ost_data);
    if (db->ins_router_data)
        mysql_stmt_close (db->ins_router_data);
    if (db->idhash)
        hash_destroy (db->idhash);
    if (db->conn)
        mysql_close (db->conn);
    db->magic = 0;
    free (db);
}

int
lmt_db_create (lmt_db_t *dbp, const char *dbname, const char **sqlerrp)
{
    lmt_db_t db;
    int retval = -1;

    if (!(db = malloc (sizeof (*db)))) {
        errno = ENOMEM;
        goto done;
    }
    db->magic = LMT_DBHANDLE_MAGIC;
    if (!(db->conn = mysql_init (NULL))) {
        errno = ENOMEM;
        goto done;
    }
    if (!mysql_real_connect (db->conn, NULL, NULL, NULL, dbname, 0, NULL, 0))
        goto done;
    if (_prepare_stmt (db, &db->ins_timestamp_info, sql_ins_timestamp_info) < 0)
        goto done;
    if (_prepare_stmt (db, &db->ins_mds_data, sql_ins_mds_data) < 0)
        goto done;
    if (_prepare_stmt (db, &db->ins_mds_ops_data, sql_ins_mds_ops_data) < 0)
        goto done;
    if (_prepare_stmt (db, &db->ins_oss_data, sql_ins_oss_data) < 0)
        goto done;
    if (_prepare_stmt (db, &db->ins_ost_data, sql_ins_ost_data) < 0)
        goto done;
    if (_prepare_stmt (db, &db->ins_router_data, sql_ins_router_data) < 0)
        goto done;
    db->timestamp = 0;
    db->timestamp_id = 0;
    if (!(db->idhash = hash_create (IDHASH_SIZE, (hash_key_f)hash_key_string,
                                    (hash_cmp_f)strcmp,
                                    (hash_del_f)_destroy_svcid)))
        goto done;
    if (_populate_idhash (db) < 0)
        goto done;
    retval = 0;
    *dbp = db;
done:
    if (retval < 0 && db) {
        if (db->conn)
            *sqlerrp = mysql_error (db->conn);
        lmt_db_destroy (db);
    }
    return retval;
}

/* FIXME: perhaps verify SCHEMA_VERSION in FILESYSTEM_INFO is 1.1 before
 * returning the database name?
 */
int
lmt_db_create_all (List *dblp, const char **sqlerrp)
{
    MYSQL *conn = NULL;
    MYSQL_RES *res = NULL;
    MYSQL_ROW row;
    List dbl = NULL;
    lmt_db_t db;
    int i, retval = -1;

    if (!(conn = mysql_init (NULL))) {
        errno = ENOMEM;    
        goto done;
    }
    if (!mysql_real_connect (conn, NULL, NULL, NULL, NULL, 0, NULL, 0)) {
        *sqlerrp = mysql_error (conn);
        goto done;
    }
    /* FIXME: need to escape underbar so it isn't interpreted as wildcard */
    if (!(res = mysql_list_dbs (conn, "filesystem_%")))
        goto done;
    if (!(dbl = list_create ((ListDelF)lmt_db_destroy)))
        goto done;
    for (i = 0; i < mysql_num_rows (res); i++) {
        row = mysql_fetch_row (res);
        if (lmt_db_create (&db, row[0], sqlerrp) < 0)
            goto done;
        if (!list_append (dbl, db)) {
            lmt_db_destroy (db);
            goto done;
        }
    }
    *dblp = dbl;
    retval = 0;
done:
    if (res)
        mysql_free_result (res);
    if (conn)
        mysql_close (conn);
    if (retval < 0) {
        if (dbl)
            list_destroy (dbl);
    }
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
