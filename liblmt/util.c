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

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "list.h"
#include "error.h"

void *
xmalloc (size_t size)
{
    void *obj = malloc (size);

    if (!obj)
        msg_exit ("out of memory");
    return obj;
}

void *
xrealloc (void *ptr, size_t size)
{
    void *obj = realloc (ptr, size);

    if (!obj)
        msg_exit ("out of memory");
    return obj;
}

char *
xstrdup (const char *s)
{
    char *cpy = strdup (s);

    if (!cpy)
        msg_exit ("out of memory");
    return cpy;
}

char *
xstrndup (const char *s, size_t n)
{
    char *cpy = malloc (n + 1);

    if (!cpy)
        msg_exit ("out of memory");
    strncpy (cpy, s, n);
    cpy[n] = '\0';
    return cpy;
}

/* Return a pointer just past n sep-delimited fields.
 * If there are fewer than that, return NULL.
 * (Push past trailing delimiter, if any)
 */
const char *
strskip (const char *s, int n, char sep)
{
    const char *p;

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
strskipcpy (const char **sp, int n, char sep)
{
    char *res = NULL;
    const char *s = *sp;
    const char *p = strskip (s, n, sep);
    int len = p ? (p - s) : 0;

    if (len > 0)
        res = xstrndup (s, s[len - 1] == sep ? len - 1 : len);
    if (res)
        *sp += len;
    return res;
}

char *
strappendfield (char **s1p, const char *s2, char sep)
{
    char *s1 = *s1p;
    int len = strlen(s1) + strlen(s2) + 2;
    char *s = xmalloc (len);

    snprintf (s, len, "%s%c%s", s1, sep, s2);
    free (s1);
    *s1p = s;
    return s;
}

/* Separate string s into a list of sep-delimited fields.
 * Caller must destroy the returned list with list_destroy ().
 */
List
list_tok (const char *s, char *sep)
{
    List l = list_create ((ListDelF)free);
    char *cpy = xstrdup (s);
    char *tok;

    tok = strtok (cpy, sep);
    while (tok) {
        list_append (l, xstrdup (tok));
        tok = strtok (NULL, sep);
    }
    free (cpy);
    return l;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
