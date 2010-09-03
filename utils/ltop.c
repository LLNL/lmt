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

#ifndef MAXHOSTNAMELEN
#define MAXHOSTNAMELEN 64
#endif

typedef struct {
    double val[2];
    time_t time[2];
    int valid; /* count of valid samples [0,1,2] */
    int stale_secs;
} sample_t;

typedef struct {
    char name[5];       /* last 4 chars of OST name, e.g. lc1-OST0001 */
    char oscstate[2];   /* 1 char OSC state (translated by lmt_osc metric) */
    sample_t rbytes;
    sample_t wbytes;
    sample_t iops;
    sample_t num_exports;
    char recov_status[32];
    time_t ost_metric_timestamp;
    char ossname[MAXHOSTNAMELEN];
} oststat_t;

typedef struct {
    double tbytes_free;
    double tbytes_total;
    double rmbps;
    double wmbps;
} ostsum_t;

typedef struct {
    double minodes_free;   /* mds */
    double minodes_total;
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
} mdtsum_t;

static void _poll_cerebro (char *fs, List ost_data, mdtsum_t *mdtsum,
                           ostsum_t *ostsum, int stale_secs);
static void _update_display (WINDOW *win, char *fs, mdtsum_t *mdtsum,
                             ostsum_t *ostsum, int stale_secs);
static void _update_display_ost (WINDOW *win, List ost_data, int minost,
                                 int selost, int stale_secs);
static void _destroy_oststat (oststat_t *o);
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
    int sort_ost = 1;
    char *fs = NULL;
    int sample_period = 2; /* seconds */
    int stale_secs = 12; /* seconds */
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    ostsum_t ostsum;
    mdtsum_t mdtsum;
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


    memset (&mdtsum, 0, sizeof (mdtsum));
    memset (&ostsum, 0, sizeof (ostsum));

    _poll_cerebro (fs, ost_data, &mdtsum, &ostsum, stale_secs);

    if ((ostcount = list_count (ost_data)) == 0)
        msg_exit ("no data found for file system `%s'", fs);

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
        _update_display (win, fs, &mdtsum, &ostsum, stale_secs);
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
            _poll_cerebro (fs, ost_data, &mdtsum, &ostsum, stale_secs);
    
        if (sort_ost)
            list_sort (ost_data, (ListCmpF)_cmp_oststat);
        else /* sort by oss */
            list_sort (ost_data, (ListCmpF)_cmp_oststat2);
    }

    list_destroy (ost_data);

    msg ("Goodbye");
    exit (0);
}

static void
_sample_init (sample_t *sp, int stale_secs)
{
    sp->valid = 0;
    sp->stale_secs = stale_secs;
}

static void
_sample_invalid (sample_t *sp)
{
    sp->valid = 0;
}

static void
_sample_update (sample_t *sp, double val, time_t t)
{
    if (sp->valid == 0) {
        sp->time[1] = t;
        sp->val[1] = val;
        sp->valid++;
    } else if (sp->time[1] < t) {
        sp->time[0] = sp->time[1];
        sp->val[0] = sp->val[1];
        sp->time[1] = t;
        sp->val[1] = val;
        if (sp->valid < 2)
            sp->valid++;
    }
}

static double
_sample_to_rate (sample_t *sp)
{
    time_t now = time (NULL);
    double val = 0;

    if (sp->valid == 2 && (now - sp->time[1]) <= sp->stale_secs)
        val = (sp->val[1] - sp->val[0]) / (sp->time[1] - sp->time[0]);
    if (val < 0)
        val = 0;
    return val;
}


static double
_sample_to_val (sample_t *sp)
{
    time_t now = time (NULL);
    double val = 0;

    if (sp->valid > 0 && (now - sp->time[1]) <= sp->stale_secs)
        val = sp->val[1];
    return val;
}

static void
_update_display (WINDOW *win, char *fs, mdtsum_t *mdtsum, ostsum_t *ostsum,
                 int stale_secs)
{
    time_t now = time (NULL);
    int x = 0;

    wclear (win);

    mvwprintw (win, x++, 0, "Filesystem: %s", fs);
    if ((now - mdtsum->mdt_metric_timestamp) > stale_secs) {
        x += 6;
        goto skipmdt;
    }
    mvwprintw (win, x++, 0,
               "    Inodes: %12.3fm total, %12.3fm used, %12.3fm free",
               mdtsum->minodes_total,
               mdtsum->minodes_total - mdtsum->minodes_free,
               mdtsum->minodes_free);
    mvwprintw (win, x++, 0,
               "     Space: %12.3ft total, %12.3ft used, %12.3ft free",
               ostsum->tbytes_total,
               ostsum->tbytes_total - ostsum->tbytes_free,
               ostsum->tbytes_free);
    mvwprintw (win, x++, 0,
               "   Bytes/s: %12.3fg read,  %12.3fg write",
               ostsum->rmbps / 1024,
               ostsum->wmbps / 1024);
    mvwprintw (win, x++, 0,
               "   MDops/s: %6.0f open,   %6.0f close,  %6.0f getattr,  %6.0f setattr",
               _sample_to_rate (&mdtsum->open),
               _sample_to_rate (&mdtsum->close),
               _sample_to_rate (&mdtsum->getattr),
               _sample_to_rate (&mdtsum->setattr));
    mvwprintw (win, x++, 0,
               "            %6.0f link,   %6.0f unlink, %6.0f mkdir,    %6.0f rmdir",
               _sample_to_rate (&mdtsum->link),
               _sample_to_rate (&mdtsum->unlink),
               _sample_to_rate (&mdtsum->mkdir),
               _sample_to_rate (&mdtsum->rmdir));
    mvwprintw (win, x++, 0, "            %6.0f statfs, %6.0f rename, %6.0f getxattr",
               _sample_to_rate (&mdtsum->statfs),
               _sample_to_rate (&mdtsum->rename),
               _sample_to_rate (&mdtsum->getxattr));
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
                  _sample_to_val (&o->num_exports),
                  _sample_to_rate (&o->rbytes) / (1024*1024),
                  _sample_to_rate (&o->wbytes) / (1024*1024),
                  _sample_to_rate (&o->iops));
        }
        if (selost == x + minost)
            wattroff(win, A_REVERSE);
        x++;
    }
    list_iterator_destroy (itr);

    wrefresh (win);
}

static void
_update_ostsum (List ost_data, ostsum_t *ostsum)
{
    ListIterator itr;
    double totr = 0, totw = 0;
    oststat_t *o;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        totr += _sample_to_rate (&o->rbytes) / (1024*1024);
        totw += _sample_to_rate (&o->wbytes) / (1024*1024);    
    }
    list_iterator_destroy (itr);        
    ostsum->rmbps = totr;
    ostsum->wmbps = totw;
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
_create_oststat (char *name, char *state, int stale_secs)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->name, ostx ? ostx + 4 : name, sizeof(o->name) - 1);
    strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
    _sample_init (&o->rbytes, stale_secs);
    _sample_init (&o->wbytes, stale_secs);
    _sample_init (&o->iops, stale_secs);
    _sample_init (&o->num_exports, stale_secs);
    return o;
}

static void
_destroy_oststat (oststat_t *o)
{
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
        o = _create_oststat (name, state, stale_secs);
        list_append (ost_data, o);
    } else {
        strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
    }
}

static void
_update_ost (char *ostname, char *ossname, time_t t,
             uint64_t read_bytes, uint64_t write_bytes, uint64_t iops,
             uint64_t num_exports, char *recov_status, List ost_data)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, ostname)))
        return;
    if (o->ost_metric_timestamp < t) {
        if (strcmp (ossname, o->ossname) != 0) { /* failover/failback */
            _sample_invalid (&o->rbytes);
            _sample_invalid (&o->wbytes);
            _sample_invalid (&o->iops);
            _sample_invalid (&o->num_exports);
            snprintf (o->ossname, sizeof (o->ossname), "%s", ossname);
        }
        o->ost_metric_timestamp = t;
        _sample_update (&o->rbytes, (double)read_bytes, t);
        _sample_update (&o->wbytes, (double)write_bytes, t);
        _sample_update (&o->iops, (double)iops, t);
        _sample_update (&o->num_exports, (double)num_exports, t);
        snprintf (o->recov_status, sizeof(o->recov_status), "%s", recov_status);
    }
}

static void
_update_mdt (char *name, time_t t, uint64_t inodes_free, uint64_t inodes_total,
             uint64_t kbytes_free, uint64_t kbytes_total, List mdops,
             mdtsum_t *mdtsum)
{
    char *opname, *s;
    ListIterator itr;
    uint64_t samples, sum, sumsquares;

    itr = list_iterator_create (mdops);
    while ((s = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdops (s, &opname,
                                &samples, &sum, &sumsquares) == 0) {
            if (!strcmp (opname, "open"))
                _sample_update (&mdtsum->open, (double)samples, t);
            else if (!strcmp (opname, "close"))
                _sample_update (&mdtsum->close, (double)samples, t);
            else if (!strcmp (opname, "getattr"))
                _sample_update (&mdtsum->getattr, (double)samples, t);
            else if (!strcmp (opname, "setattr"))
                _sample_update (&mdtsum->setattr, (double)samples, t);
            else if (!strcmp (opname, "link"))
                _sample_update (&mdtsum->link, (double)samples, t);
            else if (!strcmp (opname, "unlink"))
                _sample_update (&mdtsum->unlink, (double)samples, t);
            else if (!strcmp (opname, "mkdir"))
                _sample_update (&mdtsum->mkdir, (double)samples, t);
            else if (!strcmp (opname, "rmdir"))
                _sample_update (&mdtsum->rmdir, (double)samples, t);
            else if (!strcmp (opname, "statfs"))
                _sample_update (&mdtsum->statfs, (double)samples, t);
            else if (!strcmp (opname, "rename"))
                _sample_update (&mdtsum->rename, (double)samples, t);
            else if (!strcmp (opname, "getxattr"))
                _sample_update (&mdtsum->getxattr, (double)samples, t);
            free (opname);
        }
    }
    list_iterator_destroy (itr);
    mdtsum->mdt_metric_timestamp = t;
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
_poll_ost (char *fs, List ost_data, ostsum_t *ostsum, int stale_secs)
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
    uint64_t sum_kbytes_free = 0, sum_kbytes_total = 0;
    ListIterator itr, itr2;
    float vers;
    time_t t, now = time (NULL);
    
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
                                 iops, num_exports, recov_status, ost_data);
                    if (now - t <= stale_secs) {
                        sum_kbytes_free += kbytes_free;
                        sum_kbytes_total += kbytes_total;
                    }
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
    ostsum->tbytes_free = (double)sum_kbytes_free / (1024.0*1024*1024);
    ostsum->tbytes_total = (double)sum_kbytes_total / (1024.0*1024*1024);
    retval = 0;
done: 
    return retval;
}

static int
_poll_mdt (char *fs, mdtsum_t *mdtsum)
{
    int retval = -1;
    cmetric_t c;
    List mdops, mdtinfo, l = NULL;
    char *s, *val, *mdsname, *mdtname;
    float pct_cpu, pct_mem;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t sum_inodes_free = 0, sum_inodes_total = 0;
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
                    _update_mdt (mdtname, t, inodes_free, inodes_total,
                                 kbytes_free, kbytes_total, mdops, mdtsum);
                    sum_inodes_free += inodes_free;
                    sum_inodes_total += inodes_total;
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
    mdtsum->minodes_free = (double)sum_inodes_free / (1024.0*1024);
    mdtsum->minodes_total = (double)sum_inodes_total / (1024.0*1024);
    retval = 0;
done: 
    return retval;
}

static void
_poll_cerebro (char *fs, List ost_data, mdtsum_t *mdtsum, ostsum_t *ostsum,
               int stale_secs)
{
    if (_poll_osc (fs, ost_data, stale_secs) < 0)
        err_exit ("could not get cerebro lmt_osc metric");
    if (_poll_ost (fs, ost_data, ostsum, stale_secs) < 0)
        err_exit ("could not get cerebro lmt_ost metric");
    _update_ostsum (ost_data, ostsum);
    if (_poll_mdt (fs, mdtsum) < 0)
        err_exit ("could not get cerebro lmt_mdt metric");
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
