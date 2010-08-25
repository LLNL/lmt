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

/* otop.c - simple top-like OST bandwidth display */

#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>
#include <string.h>
#include <curses.h>
#include <signal.h>
#if HAVE_GETOPT_H
#include <getopt.h>
#endif
#include <libgen.h>

#include "list.h"
#include "hash.h"
#include "error.h"

#include "proc.h"
#include "lmt.h"

#include "util.h"
#include "ost.h"
#include "mdt.h"
#include "osc.h"
#include "router.h"

#include "lmtcerebro.h"
#include "lmtconf.h"

typedef struct {
    char *name;
    char *oscstate;
} oststat_t;

static void _sigint_handler (int arg);
static void _poll_cerebro (void);
static void _update_display (void);

#define OPTIONS "f:c:"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"filesystem",      required_argument,  0, 'f'},
    {"config-file",     required_argument,  0, 'c'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

static int exit_flag = 0;
static int sample_period = 2; /* seconds */
static List ost_data = NULL;
static char *fs = NULL;

static void
usage (void)
{
    fprintf (stderr, "Usage: ltop -f FS [-c config]\n");
    exit (1);
}

int
main (int argc, char *argv[])
{
    int c;
    char *conffile = NULL;

    err_init (argv[0]);
    optind = 0;
    opterr = 0;

    while ((c = GETOPT (argc, argv, OPTIONS, longopts)) != -1) {
        switch (c) {
            case 'f':   /* --filesystem FS */
                fs = optarg;
                break;
            case 'c':   /* --config-file FILE */
                conffile = optarg;
                break;
            default:
                usage ();
        }
    }
    if (optind < argc)
        usage();
    if (!fs)
        usage();

    if (lmt_conf_init (1, conffile) < 0)
        exit (1);

    _poll_cerebro ();
#if 1
    signal (SIGINT, _sigint_handler);
    /* FIXME: handle SIGTSTP, SIGCONT */
    /* FIXME: handle SIGWINCH */
    /* FIXME: set raw, nonblocking input mode and handle single-char cmds */
    initscr ();
    curs_set (0);
    while (!exit_flag) {
        _update_display ();
        sleep (sample_period);
        _poll_cerebro ();
    }
    endwin ();
#endif
    msg ("Goodbye");
    exit (0);
}

static void
_sigint_handler (int arg)
{
    exit_flag = 1;    
}

static void
_update_display (void)
{
    ListIterator itr;
    oststat_t *o;
    int x = 0;

    clear ();

    mvprintw (x++, 0, "Filesystem: %s", fs);
    x++;

    /* Display the header */
    attron (A_REVERSE);
    mvprintw (x++, 0, "%-80s", "OST  State");
    attroff(A_REVERSE);

    /* Display the list of ost's */
    if (ost_data) {
        itr = list_iterator_create (ost_data);
        while ((o = list_next (itr))) {
            mvprintw (x++, 0, "%8s %s", o->name, o->oscstate);
        }
    }

    refresh ();
}

/**
 ** Cerebro Routines
 **/

static int
_match_oststat (oststat_t *o, char *name)
{
    return (strcmp (o->name, name) == 0);
}

static oststat_t *
_create_oststat (char *name, char *state)
{
    oststat_t *o = xmalloc (sizeof (*o));

    o->name = xstrdup (name);
    o->oscstate = xstrdup (state);

    return o;
}

static void
_destroy_oststat (oststat_t *o)
{
    free (o->oscstate);
    free (o->name);
    free (o);
}

static int
_fsmatch (char *name)
{
    char *p = strchr (name, '-');
    int len = p ? p - name : strlen (name);

    if (strncmp (name, fs, len) == 0)
        return 1;
    return 0;
}

static void
_update_osc (char *name, char *oscstate)
{
    oststat_t *o;

    if (!ost_data)
        ost_data = list_create ((ListDelF)_destroy_oststat);
    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, name))) {
        o = _create_oststat (name, oscstate);
        list_append (ost_data, o);
    } else {
        free (o->oscstate);
        o->oscstate = strdup (oscstate);
    }
}

static int
_poll_osc (void)
{
    int retval = -1;
    cmetric_t c;
    List oscinfo, l = NULL;
    char *s, *val, *mdsname, *oscname, *oscstate;
    ListIterator itr, oitr;
    float vers;

    if (lmt_cbr_get_metrics ("lmt_osc", &l) < 0)
        goto done;
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        if (!(val = lmt_cbr_get_val (c)))
            continue;
        if (sscanf (val, "%f;", &vers) != 1 || vers != 1)
            continue; 
        if (lmt_osc_decode_v1 (val, &mdsname, &oscinfo) < 0)
            continue;
        free (mdsname);
        oitr = list_iterator_create (oscinfo);
        while ((s = list_next (oitr))) {
            if (lmt_osc_decode_v1_oscinfo (s, &oscname, &oscstate) == 0) {
                if (_fsmatch (oscname))
                    _update_osc (oscname, oscstate);
                free (oscname);
                free (oscstate);
            }
        }
        list_iterator_destroy (oitr);
        list_destroy (oscinfo);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
    retval = 0;
done: 
    return retval;
                                      
}

static void
_poll_cerebro (void)
{
    _poll_osc ();
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
