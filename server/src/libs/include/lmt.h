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

#ifndef _LMT_COMMON
#define _LMT_COMMON

#define LMT_MDS_PROTOCOL_VERSION    "1.0"
#define LMT_OSS_PROTOCOL_VERSION    "1.0"
#define LMT_OST_PROTOCOL_VERSION    "1.0"
#define LMT_ROUTER_PROTOCOL_VERSION "1.0"

#define LMT_SEPARATOR               ";"

/* Operations */
#define LMT_OPERATION_TABLE {                   \
        {0,   "req_waittime"},                  \
        {1,   "req_qdepth"},                    \
        {2,   "req_active"},                    \
        {3,   "reqbuf_avail"},                  \
        {4,   "ost_reply"},                     \
        {5,   "ost_getattr"},                   \
        {6,   "ost_setattr"},                   \
        {7,   "ost_read"},                      \
        {8,   "ost_write"},                     \
        {9,   "ost_create"},                    \
        {10,  "ost_destroy"},                   \
        {11,  "ost_get_info"},                  \
        {12,  "ost_connect"},                   \
        {13,  "ost_disconnect"},                \
        {14,  "ost_punch"},                     \
        {15,  "ost_open"},                      \
        {16,  "ost_close"},                     \
        {17,  "ost_statfs"},                    \
        {18,  "ost_san_read"},                  \
        {19,  "ost_san_write"},                 \
        {20,  "ost_sync"},                      \
        {21,  "ost_set_info"},                  \
        {22,  "ost_quotacheck"},                \
        {23,  "ost_quotactl"},                  \
        {24,  "mds_getattr"},                   \
        {25,  "mds_getattr_lock"},              \
        {26,  "mds_close"},                     \
        {27,  "mds_reint"},                     \
        {28,  "mds_readpage"},                  \
        {29,  "mds_connect"},                   \
        {30,  "mds_disconnect"},                \
        {31,  "mds_getstatus"},                 \
        {32,  "mds_statfs"},                    \
        {33,  "mds_pin"},                       \
        {34,  "mds_unpin"},                     \
        {35,  "mds_sync"},                      \
        {36,  "mds_done_writing"},              \
        {37,  "mds_set_info"},                  \
        {38,  "mds_quotacheck"},                \
        {39,  "mds_quotactl"},                  \
        {40,  "mds_getxattr"},                  \
        {41,  "mds_setxattr"},                  \
        {42,  "ldlm_enqueue"},                  \
        {43,  "ldlm_convert"},                  \
        {44,  "ldlm_cancel"},                   \
        {45,  "ldlm_bl_callback"},              \
        {46,  "ldlm_cp_callback"},              \
        {47,  "ldlm_gl_callback"},              \
        {48,  "obd_ping"},                      \
        {49,  "llog_origin_handle_cancel"},     \
        {50,  "read_bytes"},                    \
        {51,  "write_bytes"},                   \
        {-1,  NULL}                             \
}

/* Do not include "read_bytes" or "write_bytes"
 * in LMT_NUM_OPERATIONS count */
#define LMT_NUM_OPERATIONS           52
#define LMT_MDS_OPERATIONS_IDX_START 0
#define LMT_MDS_OPERATIONS_IDX_END   49
#define LMT_OST_BANDWIDTH_IDX_START  50
#define LMT_OST_BANDWIDTH_IDX_END    51
#define LMT_OPERATION_NAME_LENGTH    128

struct lmt_fsop_jt_item
{
        int    idx;
        char * op;
};

struct lmt_filesystem_operation {
        unsigned long long mysql_id;
	char               name[LMT_OPERATION_NAME_LENGTH+1];
	unsigned long long numsamples;
	unsigned long long value;
	unsigned long long sumsquares;
};

/* The following preprocessor magic is necessary
 * for expanding arguments to the LMT_TO_STR
 * macro */
#define LMT_TO_STR_INTERNAL(x) #x
#define LMT_TO_STR(x) LMT_TO_STR_INTERNAL(x)

#endif

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
