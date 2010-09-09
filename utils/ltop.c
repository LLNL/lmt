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

/* ltop.c - curses based interface to lmt cerebro data */

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
#if HAVE_GETOPT_H
#include <getopt.h>
#endif
#include <libgen.h>
#include <time.h>
#include <ctype.h>
#include <assert.h>

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

#include "sample.h"

#ifndef MAXHOSTNAMELEN
#define MAXHOSTNAMELEN 64
#endif

typedef struct {
    char name[5];
    char oscstate[2];
    sample_t rbytes;
    sample_t wbytes;
    sample_t iops;
    sample_t num_exports;
    sample_t lock_count;
    sample_t grant_rate;
    sample_t cancel_rate;
    sample_t connect;
    sample_t kbytes_free;
    sample_t kbytes_total;
    char recov_status[32];
    time_t ost_metric_timestamp;
    char ossname[MAXHOSTNAMELEN];
    int tag;
} oststat_t;

typedef struct {
    char name[5];
    sample_t inodes_free;
    sample_t inodes_total;
    sample_t open;
    sample_t close;
    sample_t getattr;
    sample_t setattr;
    sample_t link;
    sample_t unlink;
    sample_t mkdir;
    sample_t rmdir;
    sample_t statfs;
    sample_t rename;
    sample_t getxattr;
    time_t mdt_metric_timestamp;
    char mdsname[MAXHOSTNAMELEN];
} mdtstat_t;

typedef enum {
    SORT_OST, SORT_OSS, SORT_RBW, SORT_WBW, SORT_IOPS, SORT_EXP, SORT_LOCKS,
    SORT_LGR, SORT_LCR, SORT_CONN,
} sort_t;

static void _poll_osc (char *fs, List ost_data, int stale_secs);
static void _poll_ost (char *fs, List ost_data, int stale_secs);
static void _poll_mdt (char *fs, List mdt_data, int stale_secs);
static void _update_display_top (WINDOW *win, char *fs, List mdt_data,
                                 List ost_data, int stale_secs);
static void _update_display_ost (WINDOW *win, List ost_data, int minost,
                                 int selost, int stale_secs);
static void _destroy_oststat (oststat_t *o);
static void _destroy_mdtstat (mdtstat_t *m);
static void _summarize_ost (List ost_data, List oss_data, int stale_secs);
static void _clear_tags (List ost_data);
static void _tag_nth_ost (List ost_data, int selost, List ost_data2);
static void _sort_ostlist (List ost_data, sort_t s);

/* Hardwired display geometry.
 */
#define TOPWIN_LINES    7       /* lines in topwin */
#define OSTWIN_H_LINES  1       /* header lines in ostwin */
#define HDRLINES    (TOPWIN_LINES + OSTWIN_H_LINES)

#define OPTIONS "f:c:t:s:"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"filesystem",      required_argument,  0, 'f'},
    {"config-file",     required_argument,  0, 'c'},
    {"sample-period",   required_argument,  0, 't'},
    {"stale-secs",      required_argument,  0, 's'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

static void
usage (void)
{
    fprintf (stderr, "Usage: ltop -f FS [-c config] [-t SECS] [-s SECS]\n");
    exit (1);
}

int
main (int argc, char *argv[])
{
    int c;
    char *conffile = NULL;
    WINDOW *topwin, *ostwin;
    int ostcount, selost = -1, minost = 0;
    int ostview = 1, resort = 0;
    sort_t ost_sort = SORT_OST;
    char *fs = NULL;
    int sample_period = 2; /* seconds */
    int stale_secs = 12; /* seconds */
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    List mdt_data = list_create ((ListDelF)_destroy_mdtstat);
    List oss_data = list_create ((ListDelF)_destroy_oststat);
    time_t last_sample = 0;

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
            case 't':   /* --sample-period SECS */
                sample_period = strtoul (optarg, NULL, 10);
                break;
            case 's':   /* --stale-secs SECS */
                stale_secs = strtoul (optarg, NULL, 10);
                break;
            default:
                usage ();
        }
    }
    if (optind < argc || !fs)
        usage();
    if (lmt_conf_init (1, conffile) < 0) /* FIXME: needed? */
        exit (1);

    /* Poll cerebro for data, then sort the ost data for display.
     * If either the mds or any ost's are up, then ostcount > 0.
     */
    _poll_osc (fs, ost_data, stale_secs);
    _poll_ost (fs, ost_data, stale_secs);
    _poll_mdt (fs, mdt_data, stale_secs);
    _sort_ostlist (ost_data, ost_sort);
    assert (ostview);
    if ((ostcount = list_count (ost_data)) == 0)
        msg_exit ("no data found for file system `%s'", fs);

    /* Curses-fu:  keys will not be echoed, tty control sequences aren't
     * handled by tty driver, getch () times out and returns ERR after
     * sample_period seconds, multi-char keypad/arrow keys are handled.
     * Make cursor invisible.
     */
    if (!(topwin = initscr ()))
        err_exit ("error initializing parent window");
    if (!(ostwin = newwin (ostcount, 80, TOPWIN_LINES, 0)))
        err_exit ("error initializing subwindow");
    raw ();
    noecho ();
    timeout (sample_period * 1000);
    keypad (topwin, TRUE);
    curs_set (0);

    /* Main processing loop:
     * Update display, read kbd (or timeout), update ost_data & mdt_data,
     *   create oss_data (summary of ost_data), [repeat]
     */
    while (!isendwin ()) {
        _update_display_top (topwin, fs, ost_data, mdt_data, stale_secs);
        _update_display_ost (ostwin, ostview ? ost_data : oss_data,
                             minost, selost, stale_secs);
        switch (getch ()) {
            case KEY_DC:            /* Delete - turn off highlighting */
                selost = -1;
                _clear_tags (ost_data);
                _clear_tags (oss_data);
                break;
            case 'q':               /* q|Ctrl-C - quit */
            case 0x03:
                delwin (ostwin);
                endwin ();
                break;
            case KEY_UP:            /* UpArrow|k - move highlight up */
            case 'k':   /* vi */
                if (selost > 0)
                    selost--;
                if (selost >= minost)
                    break;
                /* fall thru */
            case KEY_PPAGE:         /* PageUp|Ctrl-U - previous page */
            case 0x15:
                minost -= (LINES - HDRLINES);
                if (minost < 0)
                    minost = 0;
                break;
            case KEY_DOWN:          /* DnArrow|j - move highlight down */
            case 'j':   /* vi */
                if (selost < ostcount - 1)
                    selost++;
                if (selost - minost < LINES - HDRLINES)
                    break;
                 /* fall thru */
            case KEY_NPAGE:         /* PageDn|Ctrl-D - next page */
            case 0x04:
                if (minost + LINES - HDRLINES <= ostcount)
                    minost += (LINES - HDRLINES);
                break;
            case 'c':               /* c - toggle compressed oss view */
                ostview = !ostview;
                if (!ostview)
                    _summarize_ost (ost_data, oss_data, stale_secs);
                resort = 1;
                ostcount = list_count (ostview ? ost_data : oss_data);
                minost = 0;
                selost = -1;
                break;
            case ' ':               /* SPACE - tag selected OST */
                if (ostview)
                    _tag_nth_ost (ost_data, selost, NULL);
                else
                    _tag_nth_ost (oss_data, selost, ost_data);
                break;
            case 't':               /* t - sort by ost */
                ost_sort = SORT_OST;
                resort = 1;
                break;
            case 's':               /* O - sort by oss */
                ost_sort = SORT_OSS;
                resort = 1;
                break;
            case 'r':               /* r - sort by read MB/s */
                ost_sort = SORT_RBW;
                resort = 1;
                break;
            case 'w':               /* w - sort by write MB/s */
                ost_sort = SORT_WBW;
                resort = 1;
                break;
            case 'i':               /* i - sort by IOPS */
                ost_sort = SORT_IOPS;
                resort = 1;
                break;
            case 'x':               /* x - sort by export count */
                ost_sort = SORT_EXP;
                resort = 1;
                break;
            case 'l':               /* l - sort by lock count */
                ost_sort = SORT_LOCKS;
                resort = 1;
                break;
            case 'g':               /* l - sort by lock grant rate */
                ost_sort = SORT_LGR;
                resort = 1;
                break;
            case 'L':               /* l - sort by lock cancellation rate */
                ost_sort = SORT_LCR;
                resort = 1;
                break;
            case 'C':               /* n - sort by (re-)connection rate */
                ost_sort = SORT_CONN;
                resort = 1;
                break;
            case ERR:               /* timeout */
                break;
        }
        if (time (NULL) - last_sample >= sample_period) {
            _poll_osc (fs, ost_data, stale_secs);
            _poll_ost (fs, ost_data, stale_secs);
            _poll_mdt (fs, mdt_data, stale_secs);
            if (!ostview)
                _summarize_ost (ost_data, oss_data, stale_secs);
            ostcount = list_count (ostview ? ost_data : oss_data);
            last_sample = time (NULL);
            timeout (sample_period * 1000);
            resort = 1;
        } else
            timeout ((sample_period - (time (NULL) - last_sample)) * 1000);

        if (resort) {
            _sort_ostlist (ost_data, ost_sort); 
            _sort_ostlist (oss_data, ost_sort); 
            resort = 0;
        }
    }

    list_destroy (ost_data);
    list_destroy (mdt_data);
    list_destroy (oss_data);

    msg ("Goodbye");
    exit (0);
}

/* Update the top (summary) window of the display.
 * Sum data rate and free space over all OST's.
 * Sum op rates and free inodes over all MDT's (>1 if CMD).
 */
static void
_update_display_top (WINDOW *win, char *fs, List ost_data, List mdt_data,
                     int stale_secs)
{
    time_t t = 0, now = time (NULL);
    int x = 0;
    ListIterator itr;
    double rmbps = 0, wmbps = 0, iops = 0;
    double tbytes_free = 0, tbytes_total = 0;
    double minodes_free = 0, minodes_total = 0;
    double open = 0, close = 0, getattr = 0, setattr = 0;
    double link = 0, unlink = 0, rmdir = 0, mkdir = 0;
    double statfs = 0, rename = 0, getxattr = 0;
    oststat_t *o;
    mdtstat_t *m;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        rmbps         += sample_rate (o->rbytes) / (1024*1024);
        wmbps         += sample_rate (o->wbytes) / (1024*1024);    
        iops          += sample_rate (o->iops);
        tbytes_free   += sample_val (o->kbytes_free) / (1024*1024*1024);
        tbytes_total  += sample_val (o->kbytes_total) / (1024*1024*1024);
    }
    list_iterator_destroy (itr);
    itr = list_iterator_create (mdt_data);
    while ((m = list_next (itr))) {
        open          += sample_rate (m->open);
        close         += sample_rate (m->close);
        getattr       += sample_rate (m->getattr);
        setattr       += sample_rate (m->setattr);
        link          += sample_rate (m->link);
        unlink        += sample_rate (m->unlink);
        rmdir         += sample_rate (m->rmdir);
        mkdir         += sample_rate (m->mkdir);
        statfs        += sample_rate (m->statfs);
        rename        += sample_rate (m->rename);
        getxattr      += sample_rate (m->getxattr);
        minodes_free  += sample_val (m->inodes_free) / (1024*1024);
        minodes_total += sample_val (m->inodes_total) / (1024*1024);
        if (m->mdt_metric_timestamp > t)
            t = m->mdt_metric_timestamp;
    }
    list_iterator_destroy (itr);

    wclear (win);

    mvwprintw (win, x++, 0, "Filesystem: %s", fs);
    if (now - t > stale_secs)
        return;
    mvwprintw (win, x++, 0,
      "    Inodes: %10.3fm total, %10.3fm used (%3.0f%%), %10.3fm free",
               minodes_total, minodes_total - minodes_free,
               ((minodes_total - minodes_free) / minodes_total) * 100,
               minodes_free);
    mvwprintw (win, x++, 0,
      "     Space: %10.3ft total, %10.3ft used (%3.0f%%), %10.3ft free",
               tbytes_total, tbytes_total - tbytes_free,
               ((tbytes_total - tbytes_free) / tbytes_total) * 100,
               tbytes_free);
    mvwprintw (win, x++, 0,
      "   Bytes/s: %10.3fg read,  %10.3fg write,            %6.0f IOPS",
               rmbps / 1024, wmbps / 1024, iops);
    mvwprintw (win, x++, 0,
      "   MDops/s: %6.0f open,   %6.0f close,  %6.0f getattr,  %6.0f setattr",
               open, close, getattr, setattr);
    mvwprintw (win, x++, 0,
      "            %6.0f link,   %6.0f unlink, %6.0f mkdir,    %6.0f rmdir",
               link, unlink, mkdir, rmdir);
    mvwprintw (win, x++, 0,
      "            %6.0f statfs, %6.0f rename, %6.0f getxattr",
               statfs, rename, getxattr);

    wrefresh (win);

    assert (x == TOPWIN_LINES);
}

/* Update the ost window of the display.
 * Minost is the first ost to display (zero origin).
 * Selost is the selected ost, or -1 if none are selected (zero origin).
 * Stale_secs is the number of seconds after which data is expried.
 */
static void
_update_display_ost (WINDOW *win, List ost_data, int minost, int selost,
                     int stale_secs)
{
    ListIterator itr;
    oststat_t *o;
    int x = 0;
    time_t now = time (NULL);
    int skipost = minost;

    wclear (win);

    wattron (win, A_REVERSE);
    mvwprintw (win, x++, 0,
               "%-80s", " OST S        OSS   Exp rMB/s wMB/s  IOPS   LOCKS LGR LCR CON");
    wattroff(win, A_REVERSE);
    assert (x == OSTWIN_H_LINES);

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (skipost-- > 0)
            continue;
        if (x - 1 + minost == selost)
            wattron (win, A_REVERSE);
        if (o->tag)
            wattron (win, A_UNDERLINE);
        /* available info is expired */
        if ((now - o->ost_metric_timestamp) > stale_secs) {
            mvwprintw (win, x, 0, "%4.4s %1.1s", o->name, o->oscstate);
        /* ost is in recovery - display recovery stats */
        } else if (strncmp (o->recov_status, "COMPLETE", 8) != 0) {
            mvwprintw (win, x, 0, "%4.4s %1.1s   %s", o->name, o->oscstate,
                       o->recov_status);
        /* ost is in normal state */
        } else {
            mvwprintw (win, x, 0,
              "%4.4s %1.1s %10.10s %5.0f %5.0f %5.0f %5.0f %7.0f %3.0f %3.0f %3.0f",
                       o->name, o->oscstate, o->ossname,
                       sample_val (o->num_exports),
                       sample_rate (o->rbytes) / (1024*1024),
                       sample_rate (o->wbytes) / (1024*1024),
                       sample_rate (o->iops),
                       sample_val (o->lock_count),
                       sample_val (o->grant_rate),
                       sample_val (o->cancel_rate),
                       sample_rate (o->connect));
        }
        if (x - 1 + minost == selost)
            wattroff(win, A_REVERSE);
        if (o->tag)
            wattroff(win, A_UNDERLINE);
        x++;
    }
    list_iterator_destroy (itr);

    wrefresh (win);
}

/*  Used for list_find_first () of MDT by target name, e.g. fs-MDTxxxx.
 */
static int
_match_mdtstat (mdtstat_t *m, char *name)
{
    char *p = strstr (name, "-MDT");

    return (strcmp (m->name, p ? p + 4 : name) == 0);
}

/* Create an mdtstat record.
 */
static mdtstat_t *
_create_mdtstat (char *name, int stale_secs)
{
    mdtstat_t *m = xmalloc (sizeof (*m));
    char *mdtx = strstr (name, "-MDT");

    memset (m, 0, sizeof (*m));
    strncpy (m->name, mdtx ? mdtx + 4 : name, sizeof(m->name) - 1);
    m->inodes_free =  sample_create (stale_secs);
    m->inodes_total = sample_create (stale_secs);
    m->open =         sample_create (stale_secs);
    m->close =        sample_create (stale_secs);
    m->getattr =      sample_create (stale_secs);
    m->setattr =      sample_create (stale_secs);
    m->link =         sample_create (stale_secs);
    m->unlink =       sample_create (stale_secs);
    m->mkdir =        sample_create (stale_secs);
    m->rmdir =        sample_create (stale_secs);
    m->statfs =       sample_create (stale_secs);
    m->rename =       sample_create (stale_secs);
    m->getxattr =     sample_create (stale_secs);
    return m;
}

/* Destroy an mdtstat record.
 */
static void
_destroy_mdtstat (mdtstat_t *m)
{
    sample_destroy (m->inodes_free);
    sample_destroy (m->inodes_total);
    sample_destroy (m->open);
    sample_destroy (m->close);
    sample_destroy (m->getattr);
    sample_destroy (m->setattr);
    sample_destroy (m->link);
    sample_destroy (m->unlink);
    sample_destroy (m->mkdir);
    sample_destroy (m->rmdir);
    sample_destroy (m->statfs);
    sample_destroy (m->rename);
    sample_destroy (m->getxattr);
    free (m);
}

/*  Used for list_find_first () of OST by target name, e.g. fs-OSTxxxx.
 */
static int
_match_oststat (oststat_t *o, char *name)
{
    char *p = strstr (name, "-OST");

    return (strcmp (o->name, p ? p + 4 : name) == 0);
}

/*  Used for list_find_first () of OST by oss name.
 */
static int
_match_oststat2 (oststat_t *o, char *name)
{
    return (strcmp (o->ossname, name) == 0);
}

/* Helper for _cmp_ostatst2 ()
 */
static char *
_numerical_suffix (char *s, unsigned long *np)
{
    char *p = s + strlen (s);

    while (p > s && isdigit (*(p - 1)))
        p--;
    if (*p)
        *np = strtoul (p, NULL, 10);
    return p;
}

/* Used for list_sort () of OST list by ossname.
 * Like strcmp, but handle variable-width (unpadded) numerical suffixes, if any.
 */
static int
_cmp_oststat_byoss (oststat_t *o1, oststat_t *o2)
{
    unsigned long n1, n2;
    char *p1 = _numerical_suffix (o1->ossname, &n1);
    char *p2 = _numerical_suffix (o2->ossname, &n2);

    if (*p1 && *p2
            && (p1 - o1->ossname) == (p2 - o2->ossname)
            && !strncmp (o1->ossname, o2->ossname, p1 - o1->ossname)) {
        return (n1 < n2 ? -1 
              : n1 > n2 ? 1 : 0);
    }
    return strcmp (o1->ossname, o2->ossname);
}

/* Used for list_sort () of OST list by ostname.
 * Fixed width hex sorts alphanumerically.
 */
static int
_cmp_oststat_byost (oststat_t *o1, oststat_t *o2)
{
    return strcmp (o1->name, o2->name);
}

/* Used for list_sort () of OST list by export count (ascending order).
 */
static int
_cmp_oststat_byexp (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_val (o1->num_exports);
    double v2 = sample_val (o2->num_exports);

    return (v1 > v2 ? 1
          : v1 < v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by lock count (descending order).
 */
static int
_cmp_oststat_bylocks (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_val (o1->lock_count);
    double v2 = sample_val (o2->lock_count);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by lock grant rate (descending order).
 */
static int
_cmp_oststat_bylgr (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_val (o1->grant_rate);
    double v2 = sample_val (o2->grant_rate);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by lock cancel rate (descending order).
 */
static int
_cmp_oststat_bylcr (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_val (o1->cancel_rate);
    double v2 = sample_val (o2->cancel_rate);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by (re-)connect rate (descending order).
 */
static int
_cmp_oststat_byconn (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_val (o1->connect);
    double v2 = sample_val (o2->connect);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by iops (descending order).
 */
static int
_cmp_oststat_byiops (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_rate (o1->iops);
    double v2 = sample_rate (o2->iops);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by read b/w (descending order).
 */
static int
_cmp_oststat_byrbw (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_rate (o1->rbytes);
    double v2 = sample_rate (o2->rbytes);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Used for list_sort () of OST list by write b/w (descending order).
 */
static int
_cmp_oststat_bywbw (oststat_t *o1, oststat_t *o2)
{
    double v1 = sample_rate (o1->wbytes);
    double v2 = sample_rate (o2->wbytes);

    return (v1 < v2 ? 1
          : v1 > v2 ? -1 : 0);
}

/* Create an oststat record.
 */
static oststat_t *
_create_oststat (char *name, int stale_secs)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->name, ostx ? ostx + 4 : name, sizeof(o->name) - 1);
    *o->oscstate = '\0';
    o->rbytes =       sample_create (stale_secs);
    o->wbytes =       sample_create (stale_secs);
    o->iops =         sample_create (stale_secs);
    o->num_exports =  sample_create (stale_secs);
    o->lock_count =   sample_create (stale_secs);
    o->grant_rate =   sample_create (stale_secs);
    o->cancel_rate =  sample_create (stale_secs);
    o->connect =      sample_create (stale_secs);
    o->kbytes_free =  sample_create (stale_secs);
    o->kbytes_total = sample_create (stale_secs);
    return o;
}

/* Destroy an oststat record.
 */
static void
_destroy_oststat (oststat_t *o)
{
    sample_destroy (o->rbytes);
    sample_destroy (o->wbytes);
    sample_destroy (o->iops);
    sample_destroy (o->num_exports);
    sample_destroy (o->lock_count);
    sample_destroy (o->grant_rate);
    sample_destroy (o->cancel_rate);
    sample_destroy (o->connect);
    sample_destroy (o->kbytes_free);
    sample_destroy (o->kbytes_total);
    free (o);
}

/* Copy an oststat record.
 */
static oststat_t *
_copy_oststat (oststat_t *o1)
{
    oststat_t *o = xmalloc (sizeof (*o));

    memcpy (o, o1, sizeof (*o));
    o->rbytes =       sample_copy (o1->rbytes);
    o->wbytes =       sample_copy (o1->wbytes);
    o->iops =         sample_copy (o1->iops);
    o->num_exports =  sample_copy (o1->num_exports);
    o->lock_count =   sample_copy (o1->lock_count);
    o->grant_rate =   sample_copy (o1->grant_rate);
    o->cancel_rate =  sample_copy (o1->cancel_rate);
    o->connect =      sample_copy (o1->connect);
    o->kbytes_free =  sample_copy (o1->kbytes_free);
    o->kbytes_total = sample_copy (o1->kbytes_total);
    return o;
}

/* Update oststat_t record (oscstate field) in ost_data list for
 * specified ostname.  Create an entry if one doesn't exist.
 * FIXME(design): we only keep one OSC state per OST, but possibly multiple
 * MDT's are reporting it under CMD and last in wins.
 */
static void
_update_osc (char *name, char *state, List ost_data, int stale_secs)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, name))) {
        o = _create_oststat (name, stale_secs);
        list_append (ost_data, o);
    }
    strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
}

/* Update oststat_t record in ost_data list for specified ostname.
 * Create an entry if one doesn't exist.
 */
static void
_update_ost (char *ostname, char *ossname, time_t t,
             uint64_t read_bytes, uint64_t write_bytes, uint64_t iops,
             uint64_t num_exports, uint64_t lock_count, uint64_t grant_rate,
             uint64_t cancel_rate, uint64_t connect,
             char *recov_status, uint64_t kbytes_free, uint64_t kbytes_total,
             int stale_secs, List ost_data)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, ostname))) {
        o = _create_oststat (ostname, stale_secs);
        list_append (ost_data, o);
    }
    if (o->ost_metric_timestamp < t) {
        if (strcmp (ossname, o->ossname) != 0) { /* failover/failback */
            sample_invalidate (o->rbytes);
            sample_invalidate (o->wbytes);
            sample_invalidate (o->iops);
            sample_invalidate (o->num_exports);
            sample_invalidate (o->lock_count);
            sample_invalidate (o->kbytes_free);
            sample_invalidate (o->kbytes_total);
            snprintf (o->ossname, sizeof (o->ossname), "%s", ossname);
        }
        o->ost_metric_timestamp = t;
        sample_update (o->rbytes, (double)read_bytes, t);
        sample_update (o->wbytes, (double)write_bytes, t);
        sample_update (o->iops, (double)iops, t);
        sample_update (o->num_exports, (double)num_exports, t);
        sample_update (o->lock_count, (double)lock_count, t);
        sample_update (o->kbytes_free, (double)kbytes_free, t);
        sample_update (o->kbytes_total, (double)kbytes_total, t);
        snprintf (o->recov_status, sizeof(o->recov_status), "%s", recov_status);
    }
}

/* Update mdtstat_t record in mdt_data list for specified mdtname.
 * Create an entry if one doesn't exist.
 */
static void
_update_mdt (char *mdtname, char *mdsname, time_t t,
             uint64_t inodes_free, uint64_t inodes_total,
             uint64_t kbytes_free, uint64_t kbytes_total, List mdops,
             int stale_secs, List mdt_data)
{
    char *opname, *s;
    ListIterator itr;
    mdtstat_t *m;
    uint64_t samples, sum, sumsquares;

    if (!(m = list_find_first (mdt_data, (ListFindF)_match_mdtstat, mdtname))) {
        m = _create_mdtstat (mdtname, stale_secs);
        list_append (mdt_data, m);
    }
    if (m->mdt_metric_timestamp < t) {
        if (strcmp (mdsname, m->mdsname) != 0) { /* failover/failback */
            sample_invalidate (m->inodes_free);
            sample_invalidate (m->inodes_total);
            sample_invalidate (m->open);
            sample_invalidate (m->close);
            sample_invalidate (m->getattr);
            sample_invalidate (m->setattr);
            sample_invalidate (m->link);
            sample_invalidate (m->unlink);
            sample_invalidate (m->mkdir);
            sample_invalidate (m->rmdir);
            sample_invalidate (m->statfs);
            sample_invalidate (m->rename);
            sample_invalidate (m->getxattr);
            snprintf (m->mdsname, sizeof (m->mdsname), "%s", mdsname);
        }
        m->mdt_metric_timestamp = t;
        sample_update (m->inodes_free, (double)inodes_free, t);
        sample_update (m->inodes_total, (double)inodes_total, t);
        itr = list_iterator_create (mdops);
        while ((s = list_next (itr))) {
            if (lmt_mdt_decode_v1_mdops (s, &opname,
                                    &samples, &sum, &sumsquares) == 0) {
                if (!strcmp (opname, "open"))
                    sample_update (m->open, (double)samples, t);
                else if (!strcmp (opname, "close"))
                    sample_update (m->close, (double)samples, t);
                else if (!strcmp (opname, "getattr"))
                    sample_update (m->getattr, (double)samples, t);
                else if (!strcmp (opname, "setattr"))
                    sample_update (m->setattr, (double)samples, t);
                else if (!strcmp (opname, "link"))
                    sample_update (m->link, (double)samples, t);
                else if (!strcmp (opname, "unlink"))
                    sample_update (m->unlink, (double)samples, t);
                else if (!strcmp (opname, "mkdir"))
                    sample_update (m->mkdir, (double)samples, t);
                else if (!strcmp (opname, "rmdir"))
                    sample_update (m->rmdir, (double)samples, t);
                else if (!strcmp (opname, "statfs"))
                    sample_update (m->statfs, (double)samples, t);
                else if (!strcmp (opname, "rename"))
                    sample_update (m->rename, (double)samples, t);
                else if (!strcmp (opname, "getxattr"))
                    sample_update (m->getxattr, (double)samples, t);
                free (opname);
            }
        }
        list_iterator_destroy (itr);
    }
}

/* Match an OST or MDT target against a file system name.
 * Target names are assumed to be of the form fs-OSTxxxx or fs-MDTxxxx.
 */
static int
_fsmatch (char *name, char *fs)
{
    char *p = strchr (name, '-');
    int len = p ? p - name : strlen (name);

    if (strlen (fs) == len && strncmp (name, fs, len) == 0)
        return 1;
    return 0;
}

/* Obtain lmt_osc records from cerebro and update ost_data.
 * Note that this is a way to get a list of all the OST's
 * when starting up, even if they have never reported in to cerebro.
 */
static void
_poll_osc (char *fs, List ost_data, int stale_secs)
{
    cmetric_t c;
    List oscinfo, l = NULL;
    char *s, *val, *mdsname, *oscname, *oscstate;
    ListIterator itr, itr2;
    float vers;
    time_t t, now = time (NULL);

    if (lmt_cbr_get_metrics ("lmt_osc", &l) < 0)
        return;
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        t = lmt_cbr_get_time (c);
        if (!(val = lmt_cbr_get_val (c)))
            continue;
        if (sscanf (val, "%f;", &vers) != 1 || vers != 1)
            continue; 
        if (lmt_osc_decode_v1 (val, &mdsname, &oscinfo) < 0)
            continue;
        free (mdsname);
        itr2 = list_iterator_create (oscinfo);
        while ((s = list_next (itr2))) {
            if (lmt_osc_decode_v1_oscinfo (s, &oscname, &oscstate) == 0) {
                if (_fsmatch (oscname, fs)) {
                    if (now - t > stale_secs)
                        _update_osc (oscname, "", ost_data, stale_secs);
                    else
                        _update_osc (oscname, oscstate, ost_data, stale_secs);
                }
                free (oscname);
                free (oscstate);
            }
        }
        list_iterator_destroy (itr2);
        list_destroy (oscinfo);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
}

/* Obtain lmt_ost records from cerebro and update ost_data.
 */
static void
_poll_ost (char *fs, List ost_data, int stale_secs)
{
    cmetric_t c;
    List ostinfo, l = NULL;
    char *s, *val, *ossname, *ostname, *recov_status;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    ListIterator itr, itr2;
    float vers;
    time_t t;
    
    if (lmt_cbr_get_metrics ("lmt_ost", &l) < 0)
        return;
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        if (!(val = lmt_cbr_get_val (c)))
            continue;
        if (sscanf (val, "%f;", &vers) != 1 || vers != 2)
            continue; 
        t = lmt_cbr_get_time (c);
        if (lmt_ost_decode_v2 (val, &ossname, &pct_cpu, &pct_mem, &ostinfo) < 0)
            continue;
        itr2 = list_iterator_create (ostinfo);
        while ((s = list_next (itr2))) {
            if (lmt_ost_decode_v2_ostinfo (s, &ostname,
                                           &read_bytes, &write_bytes,
                                           &kbytes_free, &kbytes_total,
                                           &inodes_free, &inodes_total, &iops,
                                           &num_exports, &lock_count,
                                           &grant_rate, &cancel_rate,
                                           &connect, &reconnect,
                                           &recov_status) == 0) {
                if (_fsmatch (ostname, fs)) {
                    _update_ost (ostname, ossname, t, read_bytes, write_bytes,
                                 iops, num_exports, lock_count, grant_rate,
                                 cancel_rate, connect + reconnect,
                                 recov_status, kbytes_free, kbytes_total,
                                 stale_secs, ost_data);
                }
                free (ostname);
                free (recov_status);
            }
        }
        list_iterator_destroy (itr2);
        list_destroy (ostinfo);
        free (ossname);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
}

/* Obtain lmt_mdt records from cerebro and update mdt_data.
 */
static void
_poll_mdt (char *fs, List mdt_data, int stale_secs)
{
    cmetric_t c;
    List mdops, mdtinfo, l = NULL;
    char *s, *val, *mdsname, *mdtname;
    float pct_cpu, pct_mem;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    ListIterator itr, itr2;
    float vers;
    time_t t;

    if (lmt_cbr_get_metrics ("lmt_mdt", &l) < 0)
        return;
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        if (!(val = lmt_cbr_get_val (c)))
            continue;
        if (sscanf (val, "%f;", &vers) != 1 || vers != 1)
            continue; 
        t = lmt_cbr_get_time (c);
        if (lmt_mdt_decode_v1 (val, &mdsname, &pct_cpu, &pct_mem, &mdtinfo) < 0)
            continue;
        free (mdsname);
        itr2 = list_iterator_create (mdtinfo);
        while ((s = list_next (itr2))) {
            if (lmt_mdt_decode_v1_mdtinfo (s, &mdtname, &inodes_free,
                                           &inodes_total, &kbytes_free,
                                           &kbytes_total, &mdops) == 0) {
                if (_fsmatch (mdtname, fs)) {
                    _update_mdt (mdtname, mdsname, t, inodes_free, inodes_total,
                                 kbytes_free, kbytes_total, mdops, stale_secs,
                                 mdt_data);
                }
                free (mdtname);
                list_destroy (mdops);
            }
        }
        list_iterator_destroy (itr2);
        list_destroy (mdtinfo);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
}

/* Re-create oss_data, one record per oss, with data aggregated from
 * the OST's on that OSS.
 */
static void
_summarize_ost (List ost_data, List oss_data, int stale_secs)
{
    oststat_t *o, *o2;
    ListIterator itr;

    while ((o = list_dequeue (oss_data)))
        _destroy_oststat (o);

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        o2 = list_find_first (oss_data, (ListFindF)_match_oststat2, o->ossname);
        if (o2) {
            sample_add (o2->rbytes, o->rbytes);
            sample_add (o2->wbytes, o->wbytes);
            sample_add (o2->iops, o->iops);
            sample_add (o2->kbytes_free, o->kbytes_free);
            sample_add (o2->kbytes_total, o->kbytes_total);
            sample_add (o2->lock_count, o->lock_count);
            sample_add (o2->grant_rate, o->grant_rate);
            sample_add (o2->cancel_rate, o->cancel_rate);
            sample_add (o2->connect, o->connect);
            if (o->ost_metric_timestamp > o2->ost_metric_timestamp)
                o2->ost_metric_timestamp = o->ost_metric_timestamp;
            /* Ensure recov_status and oscstate reflect any unrecovered or
             * non-full state of individual OSTs.  Last in wins.
             */
            if (strcmp (o->oscstate, "F") != 0)
                memcpy (o2->oscstate, o->oscstate, sizeof (o->oscstate));
            if (strncmp (o->recov_status, "COMPLETE", 8) != 0)
                memcpy (o2->recov_status, o->recov_status,
                        sizeof (o->recov_status));
            /* Similarly, any "missing clients" on OST's should be reflected.
             * in the OSS exports count.
             */
            sample_min (o2->num_exports, o->num_exports);
            /* Maintain OST count in name field.
             */
            snprintf (o2->name, sizeof (o2->name), "(%d)",
                      (int)strtoul (o2->name + 1, NULL, 10) + 1);
            if (o->tag)
                o2->tag = o->tag;
        } else {
            o2 = _copy_oststat (o);
            snprintf (o2->name, sizeof (o2->name), "(%d)", 1);
            list_append (oss_data, o2);
        }
    }
    list_iterator_destroy (itr);
    list_sort (oss_data, (ListCmpF)_cmp_oststat_byoss);
}

/* Clear all tags.
 */
static void
_clear_tags (List ost_data)
{
    oststat_t *o;
    ListIterator itr;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr)))
        o->tag = 0;
    list_iterator_destroy (itr);
}

/* Set tag value on ost's with specified oss.
 */
static void
_tag_ost_byoss (List ost_data, char *ossname, int tagval)
{
    oststat_t *o;
    ListIterator itr;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr)))
        if (!strcmp (o->ossname, ossname))
            o->tag = tagval;
    list_iterator_destroy (itr);
}

/* Toggle tag value on nth ost.
 * If tagging ost_data (first param), set the last paramter NULL.
 * If tagging oss_data (first param), set the last parmater to ost_data,
 * and all ost's on this oss will get tagged too.
 */
static void
_tag_nth_ost (List ost_data, int selost, List ost_data2)
{
    oststat_t *o;
    ListIterator itr;
    int n = 0;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (selost == n++) {
            o->tag = !o->tag;
            break;
        }
    }
    list_iterator_destroy (itr);
    if (ost_data2 && o != NULL)
        _tag_ost_byoss (ost_data2, o->ossname, o->tag);
}

static void
_sort_ostlist (List ost_data, sort_t s)
{
    ListCmpF c = NULL;

    switch (s) {
        case SORT_OST:
            c = (ListCmpF)_cmp_oststat_byost;
            break;
        case SORT_OSS:
            c = (ListCmpF)_cmp_oststat_byoss;
            break;
        case SORT_RBW:
            c = (ListCmpF)_cmp_oststat_byrbw;
            break;
        case SORT_WBW:
            c = (ListCmpF)_cmp_oststat_bywbw;
            break;
        case SORT_IOPS:
            c = (ListCmpF)_cmp_oststat_byiops;
            break;
        case SORT_EXP:
            c = (ListCmpF)_cmp_oststat_byexp;
            break;
        case SORT_LOCKS:
            c = (ListCmpF)_cmp_oststat_bylocks;
            break;
        case SORT_LGR:
            c = (ListCmpF)_cmp_oststat_bylgr;
            break;
        case SORT_LCR:
            c = (ListCmpF)_cmp_oststat_bylcr;
            break;
        case SORT_CONN:
            c = (ListCmpF)_cmp_oststat_byconn;
            break;
    }
    list_sort (ost_data, c);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
