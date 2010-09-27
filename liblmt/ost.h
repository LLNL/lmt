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


int lmt_ost_string_v2 (pctx_t ctx, char *s, int len);

int lmt_ost_decode_v2 (const char *s, char **ossnamep,
                        float *pct_cpup, float *pct_memp, List *ostinfop);
int lmt_ost_decode_v2_ostinfo (const char *s, char **ostnamep,
                        uint64_t *read_bytesp, uint64_t *write_bytesp,
                        uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                        uint64_t *inodes_freep, uint64_t *inodes_totalp,
                        uint64_t *iopsp, uint64_t *num_exportsp,
                        uint64_t *lock_countp, uint64_t *grant_ratep,
                        uint64_t *cancel_ratep, uint64_t *connectp,
                        uint64_t *reconnectp, char **recov_statusp);

/* legacy */

int lmt_oss_decode_v1 (const char *s, char **ossnamep, float *pct_cpup,
                       float *pct_memp);
int lmt_ost_decode_v1 (const char *s, char **ossnamep, char **ostnamep,
                       uint64_t *read_bytesp, uint64_t *write_bytesp,
                       uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                       uint64_t *inodes_freep, uint64_t *inodes_totalp);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
