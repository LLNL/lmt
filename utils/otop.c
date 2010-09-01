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
#include <curses.h>
#include <signal.h>

#include "list.h"
#include "hash.h"

#include "proc.h"
#include "stat.h"
#include "meminfo.h"
#include "lustre.h"

#define SAMPLE_PERIOD_SEC   2

typedef struct {
    char *name;
    uint64_t rb[2];
    uint64_t wb[2];
    uint64_t iops[2];
    uint64_t rtot;
    uint64_t wtot;
    uint64_t itot;
    int     valid; /* count of valid samples [0,1,2] */
} oststat_t;

static int exit_flag = 0;


static void
_oststat_destroy (oststat_t *o)
{
    free (o->name);
    free (o);
}

static oststat_t *
_oststat_create (char *name)
{
    oststat_t *o;

    if (!(o = malloc (sizeof (oststat_t))))
        return NULL;
    memset (o, 0, sizeof (oststat_t));
    if (!(o->name = strdup (name))) {
        free (o);
        return NULL;
    }
    return o;
}

#define NUM_TMPL "%-15s %-12.2f %-12.2f %-12.2f %-12.2f"
#define STR_TMPL "%-15s %-12s %-12s %-12s %-12s"
static int
update_display (List oststats)
{
    ListIterator itr;
    oststat_t *o;
    int x = 0;

    clear ();
    attron (A_BOLD);
    mvprintw (x++, 0, STR_TMPL, "Name", "Read (MB/s)", "Write (MB/s)",
              "Read (TB)", "Write (TB)");
    attroff(A_BOLD);

    if (!(itr = list_iterator_create (oststats)))
        return -1;
    while ((o = list_next (itr))) {
        if (o->valid == 2)
            mvprintw (x++, 0, NUM_TMPL, o->name,
                     (double)(o->rb[1] - o->rb[0])/(SAMPLE_PERIOD_SEC * 1E6),
                     (double)(o->wb[1] - o->wb[0])/(SAMPLE_PERIOD_SEC * 1E6),
                     (double)(o->rtot) / 1E12,
                     (double)(o->wtot) / 1E12);
        else
            mvprintw (x++, 0, STR_TMPL, o->name,
                     "****", "****", "****", "****");
    }
    list_iterator_destroy (itr);

    refresh ();
    return 0;
}

static int
update_oststats (pctx_t ctx, List oststats)
{
    ListIterator itr;
    oststat_t *o;

    if (!(itr = list_iterator_create (oststats)))
        return -1;
    while ((o = list_next (itr))) {
        o->rb[0] = o->rb[1];
        o->wb[0] = o->wb[1];
        o->iops[0] = o->iops[1];
        if (proc_lustre_rwbytes (ctx, o->name, &o->rb[1], &o->wb[1],
                                 &o->iops[1]) < 0) {
            if (o->valid > 0)
                o->valid--;
        } else  {
            if (o->valid < 2)
                o->valid++;
        }
        if (o->valid == 2) {
            o->rtot += (o->rb[1] - o->rb[0]);
            o->wtot += (o->wb[1] - o->wb[0]);
            o->itot += (o->iops[1] - o->iops[0]);
        }
    }
    list_iterator_destroy (itr);
    return 0;
}

/* helper for update_ostlist () */
static int
_oststat_matchname (oststat_t *o, char *name)
{
    return !strcmp (o->name, name);
}

static int
update_ostlist (pctx_t ctx, List oststats)
{
    List ostnames;
    ListIterator itr;
    char *name;

    if (proc_lustre_ostlist (ctx, &ostnames) < 0)
        return 0; /* ignore transient error */
    if (!(itr = list_iterator_create (ostnames)))
        return -1;
    while ((name = list_next (itr))) {
        if (!list_find_first (oststats, (ListFindF)_oststat_matchname, name))
            if (!list_append (oststats, _oststat_create (name)))
                return -1;
    }
    list_iterator_destroy (itr);
    list_destroy (ostnames);
    return 0;
}

static void
sigint_handler (int arg)
{
    exit_flag = 1;    
}

int
main (int argc, char *argv[])
{
    pctx_t ctx;
    char *msg = "Goodbye";
    static List oststats;

    if (!(ctx = proc_create ("/proc"))) {
        perror ("proc_create");
        exit (1);
    }
    if (!(oststats = list_create ((ListDelF)_oststat_destroy))) {
        perror ("list_create");
        exit (1);
    }
    signal (SIGINT, sigint_handler);
    /* FIXME: handle SIGTSTP, SIGCONT */
    /* FIXME: handle SIGWINCH */
    /* FIXME: set raw, nonblocking input mode and handle single-char cmds */

    initscr ();
    curs_set (0);
    while (!exit_flag) {
        if (update_ostlist (ctx, oststats) < 0) {
            msg = "Out of memory, aborting";
            break;
        }
        if (update_oststats (ctx, oststats) < 0) {
            msg = "Out of memory, aborting";
            break;
        }
        if (update_display (oststats) < 0) {
            msg = "out of memory, aborting";
            break;
        }
        sleep (SAMPLE_PERIOD_SEC);
    }
    endwin ();

    fprintf (stderr, "otop: %s\n", msg);
    list_destroy (oststats);
    proc_destroy (ctx);
    exit (0);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

