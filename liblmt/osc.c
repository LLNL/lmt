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

#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <errno.h>
#include <sys/utsname.h>
#include <inttypes.h>
#include <math.h>

#include "list.h"
#include "hash.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"
#include "error.h"

#include "lmt.h"
#include "mdt.h"
#include "util.h"
#include "lmtconf.h"

static int
_get_oscstring (pctx_t ctx, char *name, char *s, int len)
{
    char *uuid = NULL;
    char *state = NULL;
    int n, retval = -1;

    if (proc_lustre_oscinfo (ctx, name, &uuid, &state) < 0) {
        if (lmt_conf_get_proto_debug ())
            err ("error reading lustre %s uuid from proc", name);
        goto done;
    }

    /* translate state to 1 char representation documented in ltop(1) */
    if (!strcmp (state, "CLOSED"))
        strcpy (state, "C");
    else if (!strcmp (state, "NEW"))
        strcpy (state, "N");
    else if (!strcmp (state, "DISCONN"))
        strcpy (state, "D");
    else if (!strcmp (state, "CONNECTING"))
        strcpy (state, "c");
    else if (!strcmp (state, "REPLAY"))
        strcpy (state, "r");
    else if (!strcmp (state, "REPLAY_LOCKS"))
        strcpy (state, "l");
    else if (!strcmp (state, "REPLAY_WAIT"))
        strcpy (state, "w");
    else if (!strcmp (state, "RECOVER"))
        strcpy (state, "R");
    else if (!strcmp (state, "FULL"))
        strcpy (state, "F");
    else if (!strcmp (state, "EVICTED")) 
        strcpy (state, "E");
    else
        strcpy (state, "?");    /* <UNKNOWN> or ?? */

    n = snprintf (s, len, "%s;%s;", uuid, state);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    retval = 0;
done: 
    if (uuid)
        free (uuid);
    if (state)
        free (state);
    return retval;
}

int
lmt_osc_string_v1 (pctx_t ctx, char *s, int len)
{
    struct utsname uts;
    int n, used, retval = -1;
    List osclist = NULL;
    ListIterator itr = NULL;
    char *name;

    /* N.B. this should only succeed on an mds - see libproc/lustre.c.
     */
    if (proc_lustre_osclist (ctx, &osclist) < 0)
        goto done;
    if (list_count (osclist) == 0) {
        errno = ENOENT;
        goto done;
    }
    if (uname (&uts) < 0) {
        err ("uname");
        goto done;
    }
    n = snprintf (s, len, "1;%s;", uts.nodename);
    if (n >= len) {
        if (lmt_conf_get_proto_debug ())
            msg ("string overflow");
        goto done;
    }
    itr = list_iterator_create (osclist);
    while ((name = list_next (itr))) {
        used = strlen (s);
        if (_get_oscstring (ctx, name, s + used, len - used) < 0)
            goto done;
    }
    if (s[strlen (s) - 1] == ';') /* chomp traling semicolon */
        s[strlen (s) - 1] = '\0';
    retval = 0;
done:
    if (itr)
        list_iterator_destroy (itr);
    if (osclist)
        list_destroy (osclist);
    return retval;
}

int
lmt_osc_decode_v1 (const char *s, char **mdsnamep, List *oscinfop)
{
    int retval = -1;
    char *mdsname = xmalloc (strlen(s) + 1);
    char *cpy = NULL;
    List oscinfo = list_create ((ListDelF)free);

    if (sscanf (s, "%*f;%[^;]", mdsname) != 1) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: mdsinfo");
        goto done;
    }
    if (!(s = strskip (s, 2, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: skipping mdsinfo");
        goto done;
    }
    oscinfo = list_create ((ListDelF)free);
    while ((cpy = strskipcpy (&s, 2, ';')))
        list_append (oscinfo, cpy);
    if (strlen (s) > 0) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: string not exhausted");
        goto done;
    }
    *mdsnamep = mdsname;
    *oscinfop = oscinfo;
    retval = 0;
done:
    if (retval < 0) {
        free (mdsname);
        list_destroy (oscinfo);
    }
    return retval;
}

int
lmt_osc_decode_v1_oscinfo (const char *s, char **oscnamep, char **oscstatep)
{
    int retval = -1;
    char *oscname = xmalloc (strlen(s) + 1);
    char *oscstate = xmalloc (strlen(s) + 1);

    if (sscanf (s, "%[^;];%[^;];", oscname, oscstate) != 2) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: oscinfo");
        goto done;
    }
    if (!(s = strskip (s, 2, ';'))) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: skipping oscinfo");
        goto done;
    }
    if (strlen (s) > 0) {
        if (lmt_conf_get_proto_debug ())
            msg ("lmt_osc_v1: parse error: oscinfo: string not exhausted");
        goto done;
    }
    *oscnamep = oscname;
    *oscstatep = oscstate;
    retval = 0;
done:
    if (retval < 0) {
        free (oscname);
        free (oscstate);
    }
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
