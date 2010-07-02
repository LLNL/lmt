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

#define _GNU_SOURCE
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

/* Return a pointer just past n sep-delimited fields.
 * If there are fewer than that, return NULL.
 * (Push past trailing delimiter, if any)
 */
char *
strskip (char *s, int n, char sep)
{
    char *p;

    while (n > 0 && *s) {
        if ((p = strchr (s, sep)))
            s = p + 1;
        else
            s += strlen (s);
        n--;
    }
    return n > 0 ? NULL : s;
}

/* Copy a group of n sep-delimited fields
 * Don't return trailing delimiter, if any, in copy.
 */
char *
strskipcpy (char **sp, int n, char sep)
{
    char *res = NULL;
    char *s = *sp;
    char *p = strskip (s, n, sep);
    int len = p ? (p - s) : 0;

    if (len > 0)
        res = strndup (s, s[len - 1] == sep ? len - 1 : len);
    if (res)
        *sp += len;
    return res;
}

char *
strappendfield (char **s1p, const char *s2, char sep)
{
    char *s1 = *s1p;
    int len = strlen(s1) + strlen(s2) + 2;
    char *s = malloc (len);

    if (s) {
        snprintf (s, len, "%s%c%s", s1, sep, s2);
        free (s1);
        *s1p = s;
    }
    return s;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
