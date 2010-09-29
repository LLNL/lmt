/******************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
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
#include <inttypes.h>
#include <sys/time.h>
#include <mysql.h>
#include <mysqld_error.h>

#include "list.h"
#include "hash.h"
#include "proc.h"
#include "lmtmysql.h"
#include "lmt.h"
#include "lmtconf.h"
#include "util.h"
#include "error.h"

#define IDHASH_SIZE     256

typedef struct {
    char *key;
    uint64_t id;
} svcid_t;

#define LMT_DBHANDLE_MAGIC 0x5454aabf
struct lmt_db_struct {
    int magic;
    MYSQL *conn;
    char *name;

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
    "select HOSTNAME, MDS_ID from MDS_INFO";
const char *sql_sel_mdt_info =
    "select MDS_NAME, MDS_ID from MDS_INFO";
const char *sql_sel_oss_info =
    "select HOSTNAME, OSS_ID from OSS_INFO";
const char *sql_sel_ost_info =
    "select OST_NAME, OST_ID from OST_INFO";
const char *sql_sel_router_info =
    "select HOSTNAME, ROUTER_ID from ROUTER_INFO";
const char *sql_sel_operation_info =
    "select OPERATION_NAME, OPERATION_ID from OPERATION_INFO";

const char *sql_ins_mds_info_tmpl =
    "insert into MDS_INFO "
    "(FILESYSTEM_ID, MDS_NAME, HOSTNAME, DEVICE_NAME) "
    "values ('1', '%s', '%s', '')";
const char *sql_ins_oss_info_tmpl =
    "insert into OSS_INFO "
    "(FILESYSTEM_ID, HOSTNAME) "
    "values ('1', '%s')";
const char *sql_ins_ost_info_tmpl =
    "insert into OST_INFO "
    "(OSS_ID, OST_NAME, HOSTNAME, DEVICE_NAME, OFFLINE) "
    "values (%"PRIu64",'%s', '%s', '', '0')";
const char *sql_ins_router_info_tmpl =
    "insert into ROUTER_INFO "
    "(ROUTER_NAME, HOSTNAME, ROUTER_GROUP_ID) "
    "values ('%s', '%s', 0)";

const char *sql_sel_mds_info_tmpl =
    "select HOSTNAME, MDS_ID from MDS_INFO where HOSTNAME = '%s'";
const char *sql_sel_mdt_info_tmpl =
    "select MDS_NAME, MDS_ID from MDS_INFO where MDS_NAME = '%s'";
const char *sql_sel_oss_info_tmpl =
    "select HOSTNAME, OSS_ID from OSS_INFO where HOSTNAME = '%s'";
const char *sql_sel_ost_info_tmpl =
    "select OST_NAME, OST_ID from OST_INFO where OST_NAME = '%s'";
const char *sql_sel_router_info_tmpl =
    "select HOSTNAME, ROUTER_ID from ROUTER_INFO where HOSTNAME = '%s'";

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

static svcid_t *
_create_svcid (const char *key_prefix, const char *key, uint64_t id)
{
    svcid_t *s = xmalloc (sizeof (svcid_t));
    int keylen = strlen (key) + strlen (key_prefix) + 2;

    memset (s, 0, sizeof (svcid_t));
    s->key = xmalloc (keylen);
    snprintf (s->key, keylen, "%s_%s", key_prefix, key);
    s->id = id;
    return s;
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
        if (_verify_type (res, 0, MYSQL_TYPE_VAR_STRING) < 0)
            goto done;
        if (_verify_type (res, 1, MYSQL_TYPE_LONG) < 0)
            goto done;
        id = strtoul (row[1], NULL, 10);
        s = _create_svcid (pfx, row[0], id);
        if (!hash_insert (db->idhash, s->key, s)) {
	    _destroy_svcid (s);
            goto done;
        }
    }
    retval = 0;
done:
    if (res)
        mysql_free_result (res);
    return retval;
}

static int
_populate_idhash_one (lmt_db_t db, const char *tmpl, const char *pfx,
                      char *a1, uint64_t *idp)
{
    int len = strlen (a1) + strlen (tmpl) + 1;
    char *qry = xmalloc (len);
    MYSQL_RES *res = NULL;
    MYSQL_ROW row;
    uint64_t id;
    svcid_t *s; 
    int result = -1;

    snprintf (qry, len, tmpl, a1);
    if (mysql_query (db->conn, qry))
        goto done;
    if (!(row = mysql_fetch_row (res)))
        goto done;
    if (_verify_type (res, 0, MYSQL_TYPE_VAR_STRING) < 0)
        goto done;
    if (_verify_type (res, 1, MYSQL_TYPE_LONG) < 0)
        goto done;
    id = strtoul (row[1], NULL, 10);
    s = _create_svcid (pfx, row[0], id);
    if (!hash_insert (db->idhash, s->key, s)) {
	_destroy_svcid (s);
        goto done;
    }
    result = 0;
    *idp = id;
done:
    if (res)
        mysql_free_result (res);
    free (qry);
    return result;
}

static int
_populate_idhash (lmt_db_t db)
{
    int retval = -1;

    /* MDS_INFO:    HOSTNAME -> MDS_ID */
    if (_populate_idhash_qry (db, sql_sel_mds_info, "mds") < 0)
        goto done;
    /* MDS_INFO:    MDS_NAME -> MDS_ID */
    if (_populate_idhash_qry (db, sql_sel_mdt_info, "mdt") < 0)
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
    int keysize = strlen (svctype) + strlen (name) + 3;
    char *key = xmalloc (keysize);
    int retval = -1;
    svcid_t *s;

    snprintf (key, keysize, "%s_%s", svctype, name);
    if ((s = hash_find (db->idhash, key))) {
        retval = 0;
        if (idp)
            *idp = s->id;
        free (key);
    }
    return retval;
}

int
lmt_db_lookup (lmt_db_t db, char *svctype, char *name)
{
    assert (db->magic == LMT_DBHANDLE_MAGIC);

    return _lookup_idhash (db, svctype, name, NULL);
}

/* private arg structure for _mapfun () */
struct map_struct {
    char *svctype;
    lmt_db_map_f mf;
    void *arg;
    int error;
};

int
_mapfun (void *data, const void *key, void *arg)
{
    struct map_struct *mp = (struct map_struct *)arg;
    char *s = (char *)key;
    char *p = strchr (s, '_');

    if (p && !strncmp (s, mp->svctype, p - s)) {
        if (mp->mf (p + 1, mp->arg) < 0)
            mp->error++;
    }
    return 0;
}

int
lmt_db_server_map (lmt_db_t db, char *svctype, lmt_db_map_f mf, void *arg)
{
    struct map_struct m;
    
    assert (db->magic == LMT_DBHANDLE_MAGIC);

    m.svctype = svctype;
    m.mf = mf;
    m.arg = arg;
    m.error = 0;

    hash_for_each (db->idhash, (hash_arg_f)_mapfun, &m);
    return (m.error ? -1 : 0);
}

/**
 ** Database *_INFO insert functions
 ** Used to implement semi-automatic MySQL configuration, new for lmt3.
 **/

static int
_insert_mds_info (lmt_db_t db, char *mdsname, char *mdtname, uint64_t *idp)
{
    int retval = -1;
    uint64_t id;
    int len = strlen (sql_ins_mds_info_tmpl)
            + strlen (mdsname) + strlen (mdtname) + 1;
    char *qry = xmalloc (len);

    assert (db->magic == LMT_DBHANDLE_MAGIC);

    snprintf (qry, len, sql_ins_mds_info_tmpl, mdtname, mdsname);
    if (mysql_query (db->conn, qry) == 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error inserting %s MDS_INFO %s, %s: %s",
                 lmt_db_fsname (db), mdtname, mdsname, mysql_error (db->conn));
        goto done;
    }
    if (_populate_idhash_one (db, sql_sel_mds_info_tmpl, "mds", mdsname,
                              &id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error querying %s of %s from MDS_INFO after insert: %s",
                 lmt_db_fsname (db), mdsname, mysql_error (db->conn));
        goto done;
    }
    if (_populate_idhash_one (db, sql_sel_mdt_info_tmpl, "mdt", mdtname,
                              &id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error querying %s of %s from MDS_INFO after insert: %s",
                 lmt_db_fsname (db), mdtname, mysql_error (db->conn));
        goto done;
    }
    *idp = id;
    retval = 0;
done:
    free (qry);
    return retval;
}

static int
_insert_oss_info (lmt_db_t db, char *ossname, uint64_t *idp)
{
    int retval = -1;
    uint64_t id;
    int len = strlen (sql_ins_oss_info_tmpl) + strlen (ossname) + 1;
    char *qry = xmalloc (len);

    assert (db->magic == LMT_DBHANDLE_MAGIC);

    snprintf (qry, len, sql_ins_oss_info_tmpl, ossname);
    if (mysql_query (db->conn, qry) == 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error inserting %s OSS_INFO %s: %s",
                 lmt_db_fsname (db), ossname, mysql_error (db->conn));
        goto done;
    }
    if (_populate_idhash_one (db, sql_sel_oss_info_tmpl, "oss",
                              ossname, &id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error querying %s of %s from OSS_INFO after insert: %s",
                 lmt_db_fsname (db), ossname, mysql_error (db->conn));
        goto done;
    }
    *idp = id;
    retval = 0;
done:
    free (qry);
    return retval;
}

static int
_insert_ost_info (lmt_db_t db, char *ossname, char *ostname, uint64_t *idp)
{
    int retval = -1;
    uint64_t id, oss_id;
    int len = strlen (sql_ins_ost_info_tmpl)
            + strlen (ossname)*2 + strlen (ostname) + 1;
    char *qry = xmalloc (len);

    if (_lookup_idhash (db, "oss", ossname, &oss_id) < 0) {
	if (_insert_oss_info (db, ossname, &oss_id) < 0)
            goto done;
    }
    snprintf (qry, len, sql_ins_ost_info_tmpl, oss_id, ostname, ossname);
    if (mysql_query (db->conn, qry) == 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error inserting %s OSS_INFO %s: %s",
                 lmt_db_fsname (db), ostname, mysql_error (db->conn));
        goto done;
    }
    if (_populate_idhash_one (db, sql_sel_ost_info_tmpl, "ost",
                              ostname, &id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error querying %s of %s from OST_INFO after insert: %s",
                 lmt_db_fsname (db), ostname, mysql_error (db->conn));
        goto done;
    }
    *idp = id;
    retval = 0;
done:
    free (qry);
    return retval;
}

static int
_insert_router_info (lmt_db_t db, char *rtrname, uint64_t *idp)
{
    int retval = -1;
    uint64_t id;
    int len = strlen (sql_ins_router_info_tmpl) + strlen (rtrname) + 1;
    char *qry = xmalloc (len);

    snprintf (qry, len, sql_ins_router_info_tmpl, rtrname);
    if (mysql_query (db->conn, qry) == 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error inserting %s ROUTER_INFO %s: %s",
                 lmt_db_fsname (db), rtrname, mysql_error (db->conn));
        goto done;
    }
    if (_populate_idhash_one (db, sql_sel_router_info_tmpl, "router",
                              rtrname, &id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("error querying %s of %s from ROUTER_INFO after insert: %s",
                 lmt_db_fsname (db), rtrname, mysql_error (db->conn));
        goto done;
    }
    *idp = id;
    retval = 0;
done:
    free (qry);
    return retval;
}

/**
 ** Database *_DATA and TIMESTAMP_INFO insert functions
 ** -1 return will cause disconnect/reconnect in lmtdb.c.
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
    if (!db->ins_timestamp_info) {
        errno = EPERM;
        goto done;
    }
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
    assert (mysql_stmt_param_count (db->ins_timestamp_info) == 1);
    _param_init_int (&param[0], MYSQL_TYPE_LONGLONG, &timestamp);

    if (mysql_stmt_bind_param (db->ins_timestamp_info, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding params for insert into %s TIMESTAMP_INFO: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_timestamp_info)) {
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s TIMESTAMP_INFO: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    db->timestamp = timestamp;
    db->timestamp_id = (uint64_t)mysql_insert_id (db->conn);
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_mds_data (lmt_db_t db, char *mdsname, char *mdtname,
                        float pct_cpu,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used)
{
    MYSQL_BIND param[7];
    uint64_t mds_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);

    /* db permissions are checked when stmt is prepared, not now  */
    if (!db->ins_mds_data) {
        if (lmt_conf_get_db_debug ())
            msg ("no permission to insert into %s MDS_DATA",
                 lmt_db_fsname (db));
        goto done;
    }
    if (_lookup_idhash(db, "mdt", mdtname, &mds_id) < 0) {
	if (_insert_mds_info (db, mdsname, mdtname, &mds_id) < 0)
            goto done;
    }
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    assert (mysql_stmt_param_count (db->ins_mds_data) == 7);
    /* FIXME: we have type LONG and LONGLONG both pointing to uint64_t */
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &mds_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_FLOAT, &pct_cpu);
    /* FIXME: [schema] we gather pct_memory but we don't insert it */
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &kbytes_free);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &kbytes_used);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &inodes_free);
    _param_init_int (&param[6], MYSQL_TYPE_LONGLONG, &inodes_used);
   
    if (mysql_stmt_bind_param (db->ins_mds_data, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding parameters for insert into %s MDS_DATA: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_mds_data)) {
        if (mysql_errno (db->conn) == ER_DUP_ENTRY) {
            retval = 0; /* expected failure if previous insert was delayed */
            goto done;
        }
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s MDS_DATA: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_mds_ops_data (lmt_db_t db, char *mdtname, char *opname,
                        uint64_t samples, uint64_t sum, uint64_t sumsquares)
{
    MYSQL_BIND param[6];
    uint64_t mds_id, op_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    if (!db->ins_mds_ops_data) {
        if (lmt_conf_get_db_debug ())
            msg ("no permission to insert into %s MDS_OPS_DATA",
                 lmt_db_fsname (db));
        goto done;
    }
    /* N.B. lookup would have failed earlier and been fixed so don't
     * expect an error here.
     */
    if (_lookup_idhash (db, "mdt", mdtname, &mds_id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in %s MDT_INFO", mdtname, lmt_db_fsname (db));
        retval = 0; /* avoid a reconnect */
        goto done;
    }
    if (_lookup_idhash (db, "op", opname, &op_id) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("%s: no entry in %s OPERATION_INFO", opname,
                 lmt_db_fsname (db));
        retval = 0; /* avoid a reconnect */
        goto done;
    }
    //if (_update_timestamp (db) < 0)
    //    goto done;

    memset (param, 0, sizeof (param));
    assert (mysql_stmt_param_count (db->ins_mds_ops_data) == 6);
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &mds_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &op_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &samples);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &sum);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &sumsquares);
   
    if (mysql_stmt_bind_param (db->ins_mds_ops_data, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding parameters for insert into %s MDS_OPS_DATA: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_mds_ops_data)) {
        if (mysql_errno (db->conn) == ER_DUP_ENTRY) {
            retval = 0; /* expected failure if previous insert was delayed */
            goto done;
        }
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s MDS_OPS_DATA: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_oss_data (lmt_db_t db, int quiet_noexist, char *ossname,
                        float pct_cpu, float pct_memory)
{
    MYSQL_BIND param[4];
    uint64_t oss_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    if (!db->ins_oss_data) {
        if (lmt_conf_get_db_debug ())
            msg ("no permission to insert into %s OSS_DATA",
                 lmt_db_fsname (db));
        goto done;
    }
    if (_lookup_idhash (db, "oss", ossname, &oss_id) < 0) {
	if (_insert_oss_info (db, ossname, &oss_id) < 0)
            goto done;
    }
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    assert (mysql_stmt_param_count (db->ins_oss_data) == 4);
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &oss_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_FLOAT, &pct_cpu);
    _param_init_int (&param[3], MYSQL_TYPE_FLOAT, &pct_memory);
   
    if (mysql_stmt_bind_param (db->ins_oss_data, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding parameters for insert into %s OSS_DATA: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_oss_data)) {
        if (mysql_errno (db->conn) == ER_DUP_ENTRY) {
            retval = 0; /* expected failure if previous insert was delayed */
            goto done;
        }
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s OSS_DATA: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_ost_data (lmt_db_t db, char *ossname, char *ostname,
                        uint64_t read_bytes, uint64_t write_bytes,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used)
{
    MYSQL_BIND param[8];
    uint64_t ost_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    if (!db->ins_ost_data) {
        if (lmt_conf_get_db_debug ())
            msg ("no permission to insert into %s OST_DATA",
                 lmt_db_fsname (db));
        goto done;
    }
    if (_lookup_idhash (db, "ost", ostname, &ost_id) < 0) {
        if (_insert_ost_info (db, ossname, ostname, &ost_id) < 0)
            goto done;
    }
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    assert (mysql_stmt_param_count (db->ins_ost_data) == 8);
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &ost_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONGLONG, &read_bytes);
    _param_init_int (&param[3], MYSQL_TYPE_LONGLONG, &write_bytes);
    _param_init_int (&param[4], MYSQL_TYPE_LONGLONG, &kbytes_free);
    _param_init_int (&param[5], MYSQL_TYPE_LONGLONG, &kbytes_used);
    _param_init_int (&param[6], MYSQL_TYPE_LONGLONG, &inodes_free);
    _param_init_int (&param[7], MYSQL_TYPE_LONGLONG, &inodes_used);
   
    if (mysql_stmt_bind_param (db->ins_ost_data, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding parameters for insert into %s OST_DATA: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_ost_data)) {
        if (mysql_errno (db->conn) == ER_DUP_ENTRY) {
            retval = 0; /* expected failure if previous insert was delayed */
            goto done;
        }
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s OST_DATA: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    retval = 0;
done:
    return retval;
}

int
lmt_db_insert_router_data (lmt_db_t db, char *rtrname, uint64_t bytes,
                           float pct_cpu)
{
    MYSQL_BIND param[4];
    uint64_t router_id;
    int retval = -1;

    assert (db->magic == LMT_DBHANDLE_MAGIC);
    if (!db->ins_router_data) {
        if (lmt_conf_get_db_debug ())
            msg ("no permission to insert into %s ROUTER_DATA",
                 lmt_db_fsname (db));
        goto done;
    }
    if (_lookup_idhash (db, "router", rtrname, &router_id) < 0) {
        if (_insert_router_info (db, rtrname, &router_id) < 0)
            goto done;
    }
    if (_update_timestamp (db) < 0)
        goto done;

    memset (param, 0, sizeof (param));
    assert (mysql_stmt_param_count (db->ins_router_data) == 4);
    _param_init_int (&param[0], MYSQL_TYPE_LONG, &router_id);
    _param_init_int (&param[1], MYSQL_TYPE_LONG, &db->timestamp_id);
    _param_init_int (&param[2], MYSQL_TYPE_LONGLONG, &bytes);
    _param_init_int (&param[3], MYSQL_TYPE_FLOAT, &pct_cpu);
   
    if (mysql_stmt_bind_param (db->ins_router_data, param)) {
        if (lmt_conf_get_db_debug ())
            msg ("error binding parameters for insert into %s ROUTER_DATA: %s",
                lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    if (mysql_stmt_execute (db->ins_router_data)) {
        if (mysql_errno (db->conn) == ER_DUP_ENTRY) {
            retval = 0; /* expected failure if previous insert was delayed */
            goto done;
        }
        if (lmt_conf_get_db_debug ())
            msg ("error executing insert into %s ROUTER_DATA: %s",
                 lmt_db_fsname (db), mysql_error (db->conn));
        goto done;
    }
    retval = 0;
done:
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

    if (!(s = mysql_stmt_init (db->conn)))
        msg_exit ("out of memory");
    errno = 0;
    if (mysql_stmt_prepare (s, sql, strlen (sql))) {
        mysql_stmt_close (s);
        goto done; /* prepare fails if GRANT would not permit operation */
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

    if (db->name)
        free (db->name);
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
lmt_db_create (int readonly, const char *dbname, lmt_db_t *dbp)
{
    lmt_db_t db = xmalloc (sizeof (*db));
    int retval = -1;
    char *dbhost = lmt_conf_get_db_host ();
    int dbport = lmt_conf_get_db_port ();
    char *dbuser = readonly ? lmt_conf_get_db_rouser ()
                            : lmt_conf_get_db_rwuser ();
    char *dbpass = readonly ? lmt_conf_get_db_ropasswd ()
                            : lmt_conf_get_db_rwpasswd ();
    int prepfail = 0;

    memset (db, 0, sizeof (*db));
    db->magic = LMT_DBHANDLE_MAGIC;
    db->name = xstrdup (dbname);
    if (!(db->conn = mysql_init (NULL)))
        msg_exit ("out of memory");
    if (!mysql_real_connect (db->conn, dbhost, dbuser, dbpass, dbname, dbport,
                             NULL, 0)) {
        if (lmt_conf_get_db_debug ())
            msg ("lmt_db_create: connect %s: %s", dbname,
                 mysql_error (db->conn));
        goto done;
    }
    if (!readonly) {
        if (_prepare_stmt (db, &db->ins_timestamp_info,
                                sql_ins_timestamp_info) < 0)
            prepfail++;
        if (_prepare_stmt (db, &db->ins_mds_data, sql_ins_mds_data) < 0)
            prepfail++;
        if (_prepare_stmt (db, &db->ins_mds_ops_data, sql_ins_mds_ops_data) < 0)
            prepfail++;
        if (_prepare_stmt (db, &db->ins_oss_data, sql_ins_oss_data) < 0)
            prepfail++;
        if (_prepare_stmt (db, &db->ins_ost_data, sql_ins_ost_data) < 0)
            prepfail++;
        if (_prepare_stmt (db, &db->ins_router_data, sql_ins_router_data) < 0)
            prepfail++;
    }
    if (prepfail) {
        if (lmt_conf_get_db_debug ())
            msg ("lmt_db_create: %s: failed to prepare %d/6 inserts",
                 dbname, prepfail);
        goto done;
    }
    db->timestamp = 0;
    db->timestamp_id = 0;
    db->idhash = hash_create (IDHASH_SIZE, (hash_key_f)hash_key_string,
                              (hash_cmp_f)strcmp, (hash_del_f)_destroy_svcid);
    if (_populate_idhash (db) < 0) {
        if (lmt_conf_get_db_debug ())
            msg ("lmt_db_create: %s: failed to populate idhash: %s",
                 dbname, mysql_error (db->conn));
        goto done;
    }
    retval = 0;
    *dbp = db;
done:
    if (retval < 0)
        lmt_db_destroy (db);
    return retval;
}

int
lmt_db_create_all (int readonly, List *dblp)
{
    MYSQL *conn = NULL;
    MYSQL_RES *res = NULL;
    MYSQL_ROW row;
    List dbl = list_create ((ListDelF)lmt_db_destroy);
    lmt_db_t db;
    int retval = -1;
    char *dbhost = lmt_conf_get_db_host ();
    int dbport = lmt_conf_get_db_port ();
    char *dbuser = readonly ? lmt_conf_get_db_rouser ()
                            : lmt_conf_get_db_rwuser ();
    char *dbpass = readonly ? lmt_conf_get_db_ropasswd ()
                            : lmt_conf_get_db_rwpasswd ();

    if (!(conn = mysql_init (NULL)))
        msg_exit ("out of memory");
    if (!mysql_real_connect (conn, dbhost, dbuser, dbpass, NULL, dbport,
                             NULL, 0)) {
        if (lmt_conf_get_db_debug ())
            msg ("lmt_db_create_all: %s",  mysql_error (conn));
        goto done;
    }
    if (!(res = mysql_list_dbs (conn, "filesystem_%"))) {
        if (lmt_conf_get_db_debug ())
            msg ("lmt_db_create_all: unable to list lmt databases");
        goto done;
    }
    while ((row = mysql_fetch_row (res))) {
        if (lmt_db_create (readonly, row[0], &db) < 0)
            goto done;
        list_append (dbl, db);
    }
    *dblp = dbl;
    retval = 0;
done:
    if (res)
        mysql_free_result (res);
    if (conn)
        mysql_close (conn);
    if (retval < 0)
        list_destroy (dbl);
    return retval;
}

char *
lmt_db_fsname (lmt_db_t db)
{
    char *p = strchr (db->name, '_');

    return (p ? p + 1 : db->name);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
