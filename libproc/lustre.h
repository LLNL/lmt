/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>
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

int proc_lustre_ostlist (pctx_t ctx, List *lp);
int proc_lustre_mdtlist (pctx_t ctx, List *lp);

int proc_lustre_files (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp);
int proc_lustre_kbytes (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp);

int proc_lustre_uuid (pctx_t ctx, char *name, char **uuidp);

typedef struct {
    char *key;
    char *val;
} shash_t;

int proc_lustre_parsestat (hash_t stats, const char *key, uint64_t *countp,
                           uint64_t *minp, uint64_t *maxp,
                           uint64_t *sump, uint64_t *sumsqp);

int proc_lustre_hashstats (pctx_t ctx, char *name, hash_t *hp);

int proc_lustre_rwbytes (pctx_t ctx, char *name, uint64_t *rbp, uint64_t *wbp);

int proc_lustre_lnet_newbytes (pctx_t ctx, uint64_t *valp);

int proc_lustre_lnet_routing_enabled (pctx_t ctx, int *valp);



/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

