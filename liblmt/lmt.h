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

#define LMT_UPDATE_INTERVAL     5   /* in secnods */

/* mysql.c */
typedef struct lmt_db_struct *lmt_db_t;

int lmt_db_dbnames (char **dbnames, const char **sqlerrp);
int lmt_db_create (lmt_db_t *dbp, const char *dbname, const char **sqlerrp);
void lmt_db_destroy (lmt_db_t db);
int lmt_db_insert_mds_data (lmt_db_t db, char *name, float pct_cpu,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used);
int lmt_db_insert_mds_ops_data (lmt_db_t db, char *name, uint64_t op_id,
                        uint64_t samples, uint64_t sum, uint64_t sumsquares);
int lmt_db_insert_oss_data (lmt_db_t db, char *name,
                        float pctcpu, float pctmem);
int lmt_db_insert_ost_data (lmt_db_t db, char *name,
                        uint64_t read_bytes, uint64_t write_bytes,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used);
int lmt_db_insert_router_data (lmt_db_t db, char *name,
                        uint64_t bytes, float pct_cpu);

/* ost.c */
int lmt_ost_string_v3 (pctx_t ctx, char *s, int len);
int lmt_ost_updatedb_v3 (lmt_db_t db, char *s);

/* mdt.c */
int lmt_mdt_string_v3 (pctx_t ctx, char *s, int len);
int lmt_mdt_updatedb_v3 (lmt_db_t db, char *s);

/* router.c */
int lmt_router_string_v3 (pctx_t ctx, char *s, int len);
int lmt_router_updatedb_v3 (lmt_db_t db, char *s);


/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
