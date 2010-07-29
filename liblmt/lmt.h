/*****************************************************************************
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

#define LMT_UPDATE_INTERVAL     5   /* in seconds */

/* lmtdb.c */
int lmt_db_insert_ost_v2 (char *s);
int lmt_db_insert_mdt_v1 (char *s);
int lmt_db_insert_router_v1 (char *s);
int lmt_db_insert_mds_v2 (char *s); // legacy
int lmt_db_insert_oss_v1 (char *s); // legacy
int lmt_db_insert_ost_v1 (char *s); // legacy

/* ost.c */
int lmt_ost_string_v2 (pctx_t ctx, char *s, int len);

/* mdt.c */
int lmt_mdt_string_v1 (pctx_t ctx, char *s, int len);

/* router.c */
int lmt_router_string_v1 (pctx_t ctx, char *s, int len);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
