/*****************************************************************************
 *  Copyright (C) 2010 Lawrence Livermore National Security, LLC.
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

#if HAVE_CONFIG_H
#include "config.h"
#endif
#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>
#include <string.h>
#if HAVE_GETOPT_H
#include <getopt.h>
#endif
#include <libgen.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "util.h"

#include "lmtconf.h"

#include "lmtmysql.h"

#define OPTIONS "a:d:lc:s:u:p:Px"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"add",             required_argument,  0, 'a'},
    {"delete",          required_argument,  0, 'd'},
    {"list",            no_argument,        0, 'l'},
    {"config-file",     required_argument,  0, 'c'},
    {"schema-file",     required_argument,  0, 's'},
    {"user",            required_argument,  0, 'u'},
    {"password",        required_argument,  0, 'p'},
    {"prompt-password", no_argument,        0, 'P'},
    {"dump-config",     no_argument,        0, 'x'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

#define LMT_SCHEMA_VERSION "1.1"
#define LMT_SCHEMA_PATH \
    X_DATADIR "/" PACKAGE "/create_schema-" LMT_SCHEMA_VERSION ".sql"


static void _list (char *user, char *pass);
static void _del (char *user, char *pass, char *fsname);
static void _add (char *user, char *pass, char *fsname, char *schemafile);
static void _xconf (char *user, char *pass);


static void
usage(void)
{
    fprintf (stderr, "Usage: lmtinit [OPTIONS]\n"
        "  -a,--add FS            create database for file system\n"
        "  -d,--delete FS         remove database for file system\n"
        "  -l,--list              list configured file systems\n"
        "  -c,--config-file FILE  use an alternate config file\n"
        "  -s,--schema-file FILE  use an alternate schema file\n"
        "  -u,--user=USER         connect to the db with USER\n"
        "  -p,--password=PASS     connect to the db with PASS\n"
        "  -P,--prompt-password   prompt for password\n"
        "  -x,--dump-config       dump config in machine readable form\n"
    );
    exit (1);
}

int
main (int argc, char *argv[])
{
    int c;
    int aopt = 0;
    int dopt = 0;
    int lopt = 0;
    int Popt = 0;
    int xopt = 0;
    char *fsname = NULL;
    char *conffile = NULL;
    char *schemafile = NULL;
    char *user = NULL;
    char *pass = NULL;

    err_init (argv[0]);
    optind = 0;
    opterr = 0;
    while ((c = GETOPT (argc, argv, OPTIONS, longopts)) != -1) {
        switch (c) {
            case 'a':   /* --add FS */
                aopt = 1;
                fsname = optarg;
                break;
            case 'd':   /* --delete FS */
                dopt = 1;
                fsname = optarg;
                break;
            case 'l':   /* --list */
                lopt = 1;
                break;
            case 'c':   /* --config-file FILE */
                conffile = optarg;
                break;
            case 's':   /* --schema-file FILE */
                schemafile = optarg;
                break;
            case 'u':   /* --user USER */
                user = optarg;
                break;
            case 'p':   /* --password PASS */
                pass = optarg;
                break;
            case 'P':   /* --prompt-password */
                Popt = 1;
                break;
            case 'x':   /* --dump-config */
                xopt = 1;
                break;
            default:
                usage ();
        }
    }
    if (lmt_conf_init (1, conffile) < 0)
        exit (1);
    lmt_conf_set_db_debug (1);
    if (optind < argc)
        usage ();
    if (!aopt && !dopt && !lopt && !xopt)
        usage ();
    if (aopt + dopt + lopt + xopt > 1)
        msg_exit ("Use only one of -a, -d, -l, and -x options.");
    if (pass && Popt)
        msg_exit ("Use only one of -p and -P options.");
    if (xopt && (Popt || user || pass))
        msg_exit ("-x cannot be used with -u, -p, or -P options.");

    if (lopt) {
        if (!user)
            user = lmt_conf_get_db_rouser ();
        if (Popt)
            pass = getpass ("Password: ");
        else if (!pass)
            pass = lmt_conf_get_db_ropasswd ();
    } else {
        if (!user)
            user = lmt_conf_get_db_rwuser ();
        if (Popt)
            pass = getpass ("Password: ");
        else if (!pass)
            pass = lmt_conf_get_db_rwpasswd ();
    }

    if (lopt)
        _list (user, pass);
    else if (dopt)
        _del (user, pass, fsname);
    else if (aopt)
        _add (user, pass, fsname, schemafile);
    else if (xopt)
        _xconf (user, pass);

    exit (0);
}

static void
_list (char *user, char *pass)
{
    List l;
    ListIterator itr;
    char *s, *p;

    if (lmt_db_list (user, pass, &l) < 0)
        exit (1);
    itr = list_iterator_create (l);
    while ((s = list_next (itr))) {
        p = strchr (s, '_');
        printf ("%s\n", p ? p + 1 : s);
    }
    list_iterator_destroy (itr);
}

static void
_del (char *user, char *pass, char *fsname)
{
    if (lmt_db_drop (user, pass, fsname) < 0)
        exit (1);
}

static int
_read_schema (char *filename, char **cp)
{
    int fd = -1;
    struct stat sb;
    char *buf = NULL;
    char *path = filename ? filename : LMT_SCHEMA_PATH;
    int n, res = -1;

    if ((fd = open (path, O_RDONLY)) < 0) {
        err ("could not open %s for reading", path);
        goto done;
    }
    if (fstat (fd, &sb) < 0) {
        err ("could not fstat %s", path);
        goto done;
    }
    buf = xmalloc (sb.st_size);
    n = read (fd, buf, sb.st_size);
    if (n < 0) {
        err ("error reading %s", path);
        goto done;
    } else if (n == 0) {
        msg ("error reading %s: premature EOF", path);
        goto done;
    } if (n != sb.st_size) {
        msg ("error reading %s: short read", path);
        goto done;
    }
    res = 0;
    *cp = buf;
done:
    if (fd != -1)
        close (fd);
    if (res < 0)
        free (buf);
    return res;
}

static void
_add (char *user, char *pass, char *fsname, char *schemafile)
{
    char *buf = NULL;
    
    if (_read_schema (schemafile, &buf) < 0)
        exit (1);
    if (lmt_db_add (user, pass, fsname, LMT_SCHEMA_VERSION, buf) < 0)
        exit (1);
    if (buf)
        free (buf);
}

static void
_xconf (char *user, char *pass)
{
    char *host = lmt_conf_get_db_host ();
    int port = lmt_conf_get_db_port ();

    printf ("dbhost:%s\n", host ? host : "");
    printf ("dbport:%d\n", port);
    printf ("dbuser:%s\n", user ? user : "");
    printf ("dbauth:%s\n", pass ? pass : "");
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

