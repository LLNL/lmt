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

/* Return a pointer just past n semicolon-delimited fields.
 * The last field's trailing semicolon (if any) is not included.
 * If there are fewer than that, return NULL.
 */
char *
strskip (char *s, int n)
{
    char *p;

    while (n > 0 && *s) {
        if ((p = strchr (s, ';')))
            s = p + 1;
        else
            s += strlen (s);
        n--;
    }
    return n > 0 ? NULL : s;
}

/* Copy a group of n semicolon delimited fields
 */
char *
strskipcpy (char **sp, int n)
{
    char *res = NULL;
    char *s = *sp;
    char *p = strskip (s, n);
    int len = p ? (p - s) : 0;

    if (len > 0)
        res = strndup (s, s[len - 1] == ';' ? len - 1 : len);
    if (res)
        *sp += len;
    return res;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
