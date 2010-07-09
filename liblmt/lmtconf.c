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

/* config.c - config registry for lmt */

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
#include <unistd.h>
#include <math.h>
#if HAVE_LUA_H
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
#endif

#include "lmtconf.h"

typedef struct {
    char *rw_dbuser;
    char *rw_dbpasswd;
    char *ro_dbuser;
    char *ro_dbpasswd;
    char *dbhost;
    int dbport;
    int debug;
} config_t;

static config_t config = {
    .rw_dbuser = NULL,
    .rw_dbpasswd = NULL,
    .ro_dbuser = NULL,
    .ro_dbpasswd = NULL,
    .dbhost = NULL,
    .dbport = 0,
    .debug = 0,
};

static int
_set_conf_str (char **cfg, char *s)
{
    char *cpy = strdup (s);

    if (!cpy) {
        errno = ENOMEM;
        return -1;
    }
    if (*cfg)
        free (*cfg);
    *cfg = cpy;
    return 0;
}

char *
lmt_conf_get_ro_dbuser (void)
{
    return config.ro_dbuser;
}
int
lmt_conf_set_ro_dbuser (char *s)
{
    return _set_conf_str (&config.ro_dbuser, s);
}

char *
lmt_conf_get_ro_dbpasswd (void)
{
    return config.ro_dbpasswd;
}
int
lmt_conf_set_ro_dbpasswd (char *s)
{
    return _set_conf_str (&config.ro_dbpasswd , s);
}

char *
lmt_conf_get_rw_dbuser (void)
{
    return config.rw_dbuser;
}
int
lmt_conf_set_rw_dbuser (char *s)
{
    return _set_conf_str (&config.rw_dbuser, s);
}

char *
lmt_conf_get_rw_dbpasswd (void)
{
    return config.rw_dbpasswd;
}
int
lmt_conf_set_rw_dbpasswd (char *s)
{
    return _set_conf_str (&config.rw_dbpasswd, s);
}

char *
lmt_conf_get_dbhost (void)
{
    return config.dbhost;
}
int
lmt_conf_set_dbhost (char *s)
{
    return _set_conf_str (&config.dbhost, s);
}

int
lmt_conf_get_dbport (void)
{
    return config.dbport;
}
void
lmt_conf_set_dbport (int i)
{
    config.dbport = i;
}

int
lmt_conf_get_debug (void)
{
    return config.debug;
}
void
lmt_conf_set_debug (int i)
{
    config.debug = i;
}

#ifdef HAVE_LUA_H
static int
_lua_getglobal_int (int vopt, char *path, lua_State *L, char *key, int *ip)
{
    int res = 0;

    lua_getglobal (L, key);
    if (!lua_isnil (L, -1)) {
        if (!lua_isnumber (L, -1)) {
            if (vopt)
                fprintf (stderr, "%s: `%s' should be number", path, key);
            errno = EIO;
            res = -1;
            goto done;
        }
        if (ip)
            *ip = (int)lua_tonumber (L, -1);
    }
done:
    lua_pop (L, 1);
    return res;
}

static int
_lua_getglobal_string (int vopt, char *path, lua_State *L, char *key, char **sp)
{
    int res = 0;
    char *cpy;

    lua_getglobal (L, key);
    if (!lua_isnil (L, -1)) {
        if (!lua_isstring (L, -1)) {
            if (vopt)
                fprintf (stderr, "%s: `%s' should be string", path, key);
            errno = EIO;
            res = -1;
            goto done;
        }
        if (sp) {
            cpy = strdup ((char *)lua_tostring (L, -1));
            if (!cpy) {
                if (vopt)
                    fprintf (stderr, "%s: out of memory\n", path);
                errno = ENOMEM;
                goto done;
            }
            if (*sp != NULL)
                free (*sp);
            *sp = cpy;
        }
    }
done:
    lua_pop (L, 1);
    return res;
}
#endif /* HAVE_LUA_H */

int
lmt_conf_init (int vopt, char *path)
{
#ifdef HAVE_LUA_H
    lua_State *L;
    static char buf[PATH_MAX];
    int res = -1;

    if (!path) {
        snprintf (buf, sizeof (buf), "%s/lmt/lmt.conf", X_SYSCONFDIR);
        if (access (buf, R_OK) == 0)
            path = buf;  /* missing default config file is not fatal */
    }
    if (path) {
        L = lua_open ();
        luaL_openlibs(L);

        if (luaL_loadfile (L, path) || lua_pcall (L, 0, 0, 0)) {
            if (vopt)
                fprintf (stderr, "%s\n", lua_tostring (L, -1));
            goto done;
        }
        if (_lua_getglobal_string (vopt, path, L, "lmt_rw_dbuser",
                               &config.rw_dbuser) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_rw_dbpasswd",
                               &config.rw_dbpasswd) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_ro_dbuser",
                               &config.ro_dbuser) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_ro_dbpasswd",
                               &config.ro_dbpasswd) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_dbhost",
                               &config.dbhost) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_dbport",
                               &config.dbport) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_debug",
                            &config.debug) < 0)
            goto done;
        res = 0;
done:
        lua_close(L);
    } else
        res = 0;
    return res;
#else
    if (path) {
        if (vopt)
            fprintf (stderr, "%s: LMT was built without LUA support\n", path);
        return -1;
    }
    return 0;
#endif /* HAVE_LUA_H */
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
