/*****************************************************************************
 *  Copyright (C) 2007 Lawrence Livermore National Security, LLC.
 *  This module was written by Jim Garlick <garlick@llnl.gov>
 *  UCRL-CODE-232438 All Rights Reserved.
 *
 *  This file is part of the Lustre Monitoring Tool.
 *  For details, see http://github.com/chaos/lmt.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the license, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the terms and conditions of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see
 *  http://www.gnu.org/licenses.
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
    char *db_rwuser;
    char *db_rwpasswd;
    char *db_rouser;
    char *db_ropasswd;
    char *db_host;
    int db_port;
    int db_debug;
    int db_autoconf;
    int cbr_debug;
    int proto_debug;
} config_t;

static config_t config = {
    .db_rwuser = NULL,
    .db_rwpasswd = NULL,
    .db_rouser = NULL,
    .db_ropasswd = NULL,
    .db_host = NULL,
    .db_port = 0,
    .db_debug = 0,
    .db_autoconf = 1,
    .cbr_debug = 0,
    .proto_debug = 0,
};

#define PATH_LMTCONF        X_SYSCONFDIR "/" PACKAGE "/lmt.conf"

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

char *lmt_conf_get_db_rouser (void) { return config.db_rouser; }
int lmt_conf_set_db_rouser (char *s) {
    return _set_conf_str (&config.db_rouser, s);
}

char *lmt_conf_get_db_ropasswd (void) { return config.db_ropasswd; }
int lmt_conf_set_db_ropasswd (char *s) {
    return _set_conf_str (&config.db_ropasswd , s);
}

char *lmt_conf_get_db_rwuser (void) { return config.db_rwuser; }
int lmt_conf_set_db_rwuser (char *s) {
    return _set_conf_str (&config.db_rwuser, s);
}

char *lmt_conf_get_db_rwpasswd (void) { return config.db_rwpasswd; }
int lmt_conf_set_db_rwpasswd (char *s) {
    return _set_conf_str (&config.db_rwpasswd, s);
}

char *lmt_conf_get_db_host (void) { return config.db_host; }
int lmt_conf_set_db_host (char *s) {
    return _set_conf_str (&config.db_host, s);
}

int lmt_conf_get_db_port (void) { return config.db_port; }
void lmt_conf_set_db_port (int i) { config.db_port = i; }

int lmt_conf_get_db_debug (void) { return config.db_debug; }
void lmt_conf_set_db_debug (int i) { config.db_debug = i; }

int lmt_conf_get_db_autoconf (void) { return config.db_autoconf; }
void lmt_conf_set_db_autoconf (int i) { config.db_autoconf = i; }

int lmt_conf_get_cbr_debug (void) { return config.cbr_debug; }
void lmt_conf_set_cbr_debug (int i) { config.cbr_debug = i; }

int lmt_conf_get_proto_debug (void) { return config.proto_debug; }
void lmt_conf_set_proto_debug (int i) { config.proto_debug = i; }

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
    int res = -1;

    if (!path) {
        if (access (PATH_LMTCONF, R_OK) == 0)
            path = PATH_LMTCONF; /* missing default config file is not fatal */
    }
    if (path) {
        L = luaL_newstate();
        luaL_openlibs(L);

        if (luaL_loadfile (L, path) || lua_pcall (L, 0, 0, 0)) {
            if (vopt)
                fprintf (stderr, "%s\n", lua_tostring (L, -1));
            goto done;
        }
        if (_lua_getglobal_string (vopt, path, L, "lmt_db_rwuser",
                                                &config.db_rwuser) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_db_rwpasswd",
                                                &config.db_rwpasswd) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_db_rouser",
                                                &config.db_rouser) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_db_ropasswd",
                                                &config.db_ropasswd) < 0)
            goto done;
        if (_lua_getglobal_string (vopt, path, L, "lmt_db_host",
                                                &config.db_host) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_db_port",
                                                &config.db_port) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_db_debug",
                                                &config.db_debug) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_db_autoconf",
                                                &config.db_autoconf) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_cbr_debug",
                                                &config.cbr_debug) < 0)
            goto done;
        if (_lua_getglobal_int (vopt, path, L, "lmt_proto_debug",
                                                &config.proto_debug) < 0)
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
