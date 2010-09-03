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
    char name[5];       /* last 4 chars of OST name, e.g. lc1-OST0001 */
    char oscstate[2];   /* 1 char OSC state (translated by lmt_osc metric) */
    sample_t rbytes;
    sample_t wbytes;
    sample_t iops;
    sample_t num_exports;
    sample_t kbytes_free;
    sample_t kbytes_total;
    char recov_status[32];
    time_t ost_metric_timestamp;
    char ossname[MAXHOSTNAMELEN];
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

static void _poll_cerebro (char *fs, List mdt_data, List ost_data,
                           int stale_secs);
static void _update_display (WINDOW *win, char *fs, List mdt_data,
                             List ost_data, int stale_secs);
static void _update_display_ost (WINDOW *win, List ost_data, int minost,
                                 int selost, int stale_secs);
static void _destroy_oststat (oststat_t *o);
static void _destroy_mdtstat (mdtstat_t *m);
static int _cmp_oststat (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat2 (oststat_t *o1, oststat_t *o2);

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
    WINDOW *win, *ostwin;
    int ostcount, selost = -1, minost = 0;
    int mdtcount;
    int sort_ost = 1;
    char *fs = NULL;
    int sample_period = 2; /* seconds */
    int stale_secs = 12; /* seconds */
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    List mdt_data = list_create ((ListDelF)_destroy_mdtstat);
    time_t now;

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
    if (optind < argc)
        usage();
    if (!fs)
        usage();

    if (lmt_conf_init (1, conffile) < 0)
        exit (1);

    /* Populate the list of OST's for this file system using the OSC data,
     * which indirectly reflects the MGS configuration.  Caveat: if the MDS
     * has not reported in to cerebro since cerebrod was rebooted, we won't
     * see the file system.  Just abort in that case.
     */

    _poll_cerebro (fs, mdt_data, ost_data, stale_secs);

    if ((ostcount = list_count (ost_data)) == 0
                || (mdtcount = list_count (mdt_data)) == 0) {
        msg_exit ("no data found for file system `%s'", fs);
    }

    list_sort (ost_data, (ListCmpF)_cmp_oststat);

    if (!(win = initscr ()))
        err_exit ("error initializing parent window");
    if (!(ostwin = newwin (ostcount, 80, 8, 0)))
        err_exit ("error initializing subwindow");

    /* Keys will not be echoed, tty control sequences aren't handled by tty
     * driver, getch () times out and returns ERR after sample_period seconds,
     * multi-char keypad/arrow keys are handled.  Make cursor invisible.
     */
    raw ();
    noecho ();
    timeout (sample_period * 1000);
    keypad (win, TRUE);
    curs_set (0);

    while (!isendwin ()) {
        _update_display (win, fs, ost_data, mdt_data, stale_secs);
        _update_display_ost (ostwin, ost_data, minost, selost, stale_secs);
        now = time (NULL);
        switch (getch ()) {
            case KEY_DC:            /* Delete - turn off highlighting */
                selost = -1;
                break;
            case 'q':               /* q|Ctrl-C - quit */
            case 0x03:
                delwin (win);
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
                minost -= (LINES - 8);
                if (minost < 0)
                    minost = 0;
                break;
            case KEY_DOWN:          /* DnArrow|j - move highlight down */
            case 'j':   /* vi */
                if (selost < ostcount - 1)
                    selost++;
                if (selost - minost < LINES - 8)
                    break;
                 /* fall thru */
            case KEY_NPAGE:         /* PageDn|Ctrl-D - next page */
            case 0x04:
                if (minost + LINES - 8 <= ostcount)
                    minost += (LINES - 8);
                break;
            case 'O':
                sort_ost = 0;
                break;
            case 'o':
                sort_ost = 1;
                break;
            case ERR:   /* timeout */
                break;
        }
        if (time (NULL) - now >= sample_period)
            _poll_cerebro (fs, mdt_data, ost_data, stale_secs);
    
        if (sort_ost)
            list_sort (ost_data, (ListCmpF)_cmp_oststat);
        else /* sort by oss */
            list_sort (ost_data, (ListCmpF)_cmp_oststat2);
    }

    list_destroy (ost_data);
    list_destroy (mdt_data);

    msg ("Goodbye");
    exit (0);
}

static void
_update_display (WINDOW *win, char *fs, List ost_data, List mdt_stat,
                 int stale_secs)
{
    time_t t = 0, now = time (NULL);
    int x = 0;
    ListIterator itr;
    double rmbps = 0, wmbps = 0;
    double tbytes_free = 0, tbytes_total = 0;
    double minodes_free = 0, minodes_total = 0;
    double open = 0, close = 0, getattr = 0, setattr = 0;
    double link = 0, unlink = 0, rmdir = 0, mkdir = 0;
    double statfs = 0, rename = 0, getxattr = 0;
    oststat_t *o;
    mdtstat_t *m;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        rmbps += sample_to_rate (o->rbytes) / (1024*1024);
        wmbps += sample_to_rate (o->wbytes) / (1024*1024);    
        tbytes_free += sample_to_val (o->kbytes_free) / (1024*1024*1024);
        tbytes_total += sample_to_val (o->kbytes_total) / (1024*1024*1024);
    }
    list_iterator_destroy (itr);
    itr = list_iterator_create (mdt_stat);
    while ((m = list_next (itr))) {
        open += sample_to_rate (m->open);
        close += sample_to_rate (m->close);
        getattr += sample_to_rate (m->getattr);
        setattr += sample_to_rate (m->setattr);
        link += sample_to_rate (m->link);
        unlink += sample_to_rate (m->unlink);
        rmdir += sample_to_rate (m->rmdir);
        mkdir += sample_to_rate (m->mkdir);
        statfs += sample_to_rate (m->statfs);
        rename += sample_to_rate (m->rename);
        getxattr += sample_to_rate (m->getxattr);
        minodes_free += sample_to_val (m->inodes_free) / (1024*1024);
        minodes_total += sample_to_val (m->inodes_total) / (1024*1024);
        if (m->mdt_metric_timestamp > t)
            t = m->mdt_metric_timestamp;
    }
    list_iterator_destroy (itr);

    wclear (win);

    mvwprintw (win, x++, 0, "Filesystem: %s", fs);
    if (now - t > stale_secs) {
        x += 6;
        goto skipmdt;
    }
    mvwprintw (win, x++, 0,
      "    Inodes: %12.3fm total, %12.3fm used, %12.3fm free",
               minodes_total, minodes_total - minodes_free, minodes_free);
    mvwprintw (win, x++, 0,
      "     Space: %12.3ft total, %12.3ft used, %12.3ft free",
               tbytes_total, tbytes_total - tbytes_free, tbytes_free);
    mvwprintw (win, x++, 0,
      "   Bytes/s: %12.3fg read,  %12.3fg write",
               rmbps / 1024, wmbps / 1024);
    mvwprintw (win, x++, 0,
      "   MDops/s: %6.0f open,   %6.0f close,  %6.0f getattr,  %6.0f setattr",
               open, close, getattr, setattr);
    mvwprintw (win, x++, 0,
      "            %6.0f link,   %6.0f unlink, %6.0f mkdir,    %6.0f rmdir",
               link, unlink, mkdir, rmdir);
    mvwprintw (win, x++, 0,
      "            %6.0f statfs, %6.0f rename, %6.0f getxattr",
               statfs, rename, getxattr);
skipmdt:
    wattron (win, A_REVERSE);
    mvwprintw (win, x++, 0,
               "%-80s", "OST  S        OSS   Exp rMB/s wMB/s  IOPS");
    wattroff(win, A_REVERSE);

    wrefresh (win);
}

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

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (skipost-- > 0)
            continue;
        if (selost == x + minost)
            wattron (win, A_REVERSE);
        /* available info is expired */
        if ((now - o->ost_metric_timestamp) > stale_secs) {
            mvwprintw (win, x, 0, "%s %s", o->name, o->oscstate);
        /* ost is in recovery - display recovery stats */
        } else if (strncmp (o->recov_status, "COMPLETE", 8) != 0) {
            mvwprintw (win, x, 0, "%s %s   %s", o->name, o->oscstate,
                       o->recov_status);
        /* ost is in normal state */
        } else {
            mvwprintw (win, x, 0, "%s %s %10.10s %5.0f %5.0f %5.0f %5.0f",
                  o->name,
                  o->oscstate,
                  o->ossname,
                  sample_to_val (o->num_exports),
                  sample_to_rate (o->rbytes) / (1024*1024),
                  sample_to_rate (o->wbytes) / (1024*1024),
                  sample_to_rate (o->iops));
        }
        if (selost == x + minost)
            wattroff(win, A_REVERSE);
        x++;
    }
    list_iterator_destroy (itr);

    wrefresh (win);
}

/**
 ** Cerebro Routines
 **/

static int
_match_oststat (oststat_t *o, char *name)
{
    char *p = strstr (name, "-OST");

    return (strcmp (o->name, p ? p + 4 : name) == 0);
}

static int
_match_mdtstat (mdtstat_t *m, char *name)
{
    char *p = strstr (name, "-MDT");

    return (strcmp (m->name, p ? p + 4 : name) == 0);
}

static int
_cmp_oststat (oststat_t *o1, oststat_t *o2)
{
    return strcmp (o1->name, o2->name);
}

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

static mdtstat_t *
_create_mdtstat (char *name, int stale_secs)
{
    mdtstat_t *m = xmalloc (sizeof (*m));
    char *mdtx = strstr (name, "-MDT");

    memset (m, 0, sizeof (*m));
    strncpy (m->name, mdtx ? mdtx + 4 : name, sizeof(m->name) - 1);
    m->inodes_free = sample_create (stale_secs);
    m->inodes_total = sample_create (stale_secs);
    m->open = sample_create (stale_secs);
    m->close = sample_create (stale_secs);
    m->getattr = sample_create (stale_secs);
    m->setattr = sample_create (stale_secs);
    m->link = sample_create (stale_secs);
    m->unlink = sample_create (stale_secs);
    m->mkdir = sample_create (stale_secs);
    m->rmdir = sample_create (stale_secs);
    m->statfs = sample_create (stale_secs);
    m->rename = sample_create (stale_secs);
    m->getxattr = sample_create (stale_secs);
    return m;
}

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

static int
_cmp_oststat2 (oststat_t *o1, oststat_t *o2)
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

static oststat_t *
_create_oststat (char *name, int stale_secs)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->name, ostx ? ostx + 4 : name, sizeof(o->name) - 1);
    strncpy (o->oscstate, " ", sizeof (o->oscstate) - 1);
    o->rbytes = sample_create (stale_secs);
    o->wbytes = sample_create (stale_secs);
    o->iops = sample_create (stale_secs);
    o->num_exports = sample_create (stale_secs);
    o->kbytes_free = sample_create (stale_secs);
    o->kbytes_total = sample_create (stale_secs);
    return o;
}

static void
_destroy_oststat (oststat_t *o)
{
    sample_destroy (o->rbytes);
    sample_destroy (o->wbytes);
    sample_destroy (o->iops);
    sample_destroy (o->num_exports);
    sample_destroy (o->kbytes_free);
    sample_destroy (o->kbytes_total);
    free (o);
}

static int
_fsmatch (char *name, char *fs)
{
    char *p = strchr (name, '-');
    int len = p ? p - name : strlen (name);

    if (strncmp (name, fs, len) == 0)
        return 1;
    return 0;
}

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

static void
_update_ost (char *ostname, char *ossname, time_t t,
             uint64_t read_bytes, uint64_t write_bytes, uint64_t iops,
             uint64_t num_exports, char *recov_status, uint64_t kbytes_free,
             uint64_t kbytes_total, int stale_secs, List ost_data)
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
            sample_invalidate (o->kbytes_free);
            sample_invalidate (o->kbytes_total);
            snprintf (o->ossname, sizeof (o->ossname), "%s", ossname);
        }
        o->ost_metric_timestamp = t;
        sample_update (o->rbytes, (double)read_bytes, t);
        sample_update (o->wbytes, (double)write_bytes, t);
        sample_update (o->iops, (double)iops, t);
        sample_update (o->num_exports, (double)num_exports, t);
        sample_update (o->kbytes_free, (double)kbytes_free, t);
        sample_update (o->kbytes_total, (double)kbytes_total, t);
        snprintf (o->recov_status, sizeof(o->recov_status), "%s", recov_status);
    }
}

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

/* We get the definitive list of OST's from the MDS's osc view, which should
 * be constructed from the MDS logs, which in turn comes from the MGS.
 * The lmt_osc metric also provides the OST state from the MDS point of view,
 * a useful thing to know.
 */
static int
_poll_osc (char *fs, List ost_data, int stale_secs)
{
    int retval = -1;
    cmetric_t c;
    List oscinfo, l = NULL;
    char *s, *val, *mdsname, *oscname, *oscstate;
    ListIterator itr, itr2;
    float vers;
    time_t t, now = time (NULL);

    if (lmt_cbr_get_metrics ("lmt_osc", &l) < 0)
        goto done;
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
                        _update_osc (oscname, " ", ost_data, stale_secs);
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
    retval = 0;
done: 
    return retval;
}

static int
_poll_ost (char *fs, List ost_data, int stale_secs)
{
    int retval = -1;
    cmetric_t c;
    List ostinfo, l = NULL;
    char *s, *val, *ossname, *ostname, *recov_status;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    ListIterator itr, itr2;
    float vers;
    time_t t;
    
    if (lmt_cbr_get_metrics ("lmt_ost", &l) < 0)
        goto done;
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
                                           &num_exports, &recov_status) == 0) {
                if (_fsmatch (ostname, fs)) {
                    _update_ost (ostname, ossname, t, read_bytes, write_bytes,
                                 iops, num_exports, recov_status, kbytes_free,
                                 kbytes_total, stale_secs, ost_data);
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
    retval = 0;
done: 
    return retval;
}

static int
_poll_mdt (char *fs, List mdt_data, int stale_secs)
{
    int retval = -1;
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
        goto done;
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
    retval = 0;
done: 
    return retval;
}

static void
_poll_cerebro (char *fs, List mdt_data, List ost_data, int stale_secs)
{
    if (_poll_osc (fs, ost_data, stale_secs) < 0)
        err_exit ("could not get cerebro lmt_osc metric");
    if (_poll_ost (fs, ost_data, stale_secs) < 0)
        err_exit ("could not get cerebro lmt_ost metric");
    if (_poll_mdt (fs, mdt_data, stale_secs) < 0)
        err_exit ("could not get cerebro lmt_mdt metric");
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
