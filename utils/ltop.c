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
#include <time.h>

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
    double val[2];
    time_t time[2];
    int valid; /* count of valid samples [0,1,2] */
} sample_t;

typedef struct {
    char name[5];       /* last 4 chars of OST name, e.g. lc1-OST0001 */
    char oscstate[2];   /* 1 char OSC state (translated by lmt_osc metric) */
    sample_t rbytes;
    sample_t wbytes;
    sample_t riops;
    sample_t wiops;
    uint64_t num_exports;
    char recov_status[32];
    time_t ost_metric_timestamp;
} oststat_t;

#define STALE_THRESH_SEC    12

typedef struct {
    double tbytes_free;
    double tbytes_total;
    double rmbps;
    double wmbps;
} ostsum_t;

typedef struct {
    time_t ost_metric_timestamp;
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
} mdtsum_t;

static void _sigint_handler (int arg);
static void _poll_cerebro (void);
static void _update_display (void);
static void _destroy_oststat (oststat_t *o);

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
static ostsum_t ostsum;
static mdtsum_t mdtsum;
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

    ost_data = list_create ((ListDelF)_destroy_oststat);

    memset (&mdtsum, 0, sizeof (mdtsum));
    memset (&ostsum, 0, sizeof (ostsum));

    _poll_cerebro ();

    if (list_count (ost_data) == 0)
        msg_exit ("no data found for file system `%s'", fs);

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

    list_destroy (ost_data);

    msg ("Goodbye");
    exit (0);
}

static void
_sigint_handler (int arg)
{
    exit_flag = 1;    
}

static void
_sample_init (sample_t *sp)
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

static char *
_sample_to_mbps (sample_t *sp, char *s, int len, double *valp)
{
    time_t now = time (NULL);
    double val;

    if (sp->valid == 2 && (now - sp->time[1]) > STALE_THRESH_SEC)
        _sample_init (sp);
    if (sp->valid == 2) {
        val = (sp->val[1] - sp->val[0]) / ((sp->time[1] - sp->time[0]) * 1E6);
        if (s)
            snprintf (s, len, "%*.2f", len - 1, val);
        if (valp)
            *valp = val;
    } else {
        if (s)
            snprintf (s, len, "%*s", len - 1, "***");
        if (valp)
            *valp = 0.0;
    }
    return s;
}

static char *
_sample_to_oprate (sample_t *sp, char *s, int len)
{
    time_t now = time (NULL);
    double val;

    if (sp->valid == 2 && (now - sp->time[1]) > STALE_THRESH_SEC)
        _sample_init (sp);
    if (sp->valid == 2) {
        val = (sp->val[1] - sp->val[0]) / (sp->time[1] - sp->time[0]);
        snprintf (s, len, "%*lu", len - 1, (unsigned long)val);
    } else {
        snprintf (s, len, "%*s", len - 1, "***");
    }
    return s;
}

static char *
_nexp_to_val (oststat_t *o, char *s, int len)
{
    time_t now = time (NULL);

    if ((now - o->ost_metric_timestamp) > STALE_THRESH_SEC)
        snprintf (s, len, "%*s", len - 1, "***");
    else
        snprintf (s, len, "%*lu", len - 1, o->num_exports);
    return s;
}

static void
_update_display (void)
{
    ListIterator itr;
    oststat_t *o;
    int x = 0;
    char rmbps[8], wmbps[8];
    char riops[8], wiops[8];
    char op[7], cl[7], gattr[7], sattr[7];
    char li[7], ul[7], mkd[7], rmd[7];
    char sfs[7], ren[7], gxattr[7], nexp[6];
    time_t now = time (NULL);

    clear ();

    mvprintw (x++, 0, "Filesystem: %s", fs);
    mvprintw (x++, 0, "    Inodes: %12.3fm total, %12.3fm used, %12.3fm free",
              mdtsum.minodes_total,
              mdtsum.minodes_total - mdtsum.minodes_free,
              mdtsum.minodes_free);
    mvprintw (x++, 0, "     Space: %12.3ft total, %12.3ft used, %12.3ft free",
              ostsum.tbytes_total,
              ostsum.tbytes_total - ostsum.tbytes_free,
              ostsum.tbytes_free);
    mvprintw (x++, 0, "   Bytes/s: %12.3fg read,  %12.3fg write",
              ostsum.rmbps / 1024,
              ostsum.wmbps / 1024);
    mvprintw (x++, 0, "   MDops/s: %6s open,   %6s close,  %6s getattr,  %6s setattr",
              _sample_to_oprate (&mdtsum.open,    op,    sizeof (op)),
              _sample_to_oprate (&mdtsum.close,   cl,    sizeof (cl)),
              _sample_to_oprate (&mdtsum.getattr, gattr, sizeof (gattr)),
              _sample_to_oprate (&mdtsum.setattr, sattr, sizeof (sattr)));
    mvprintw (x++, 0, "            %6s link,   %6s unlink, %6s mkdir,    %6s rmdir",
              _sample_to_oprate (&mdtsum.link,    li,    sizeof (li)),
              _sample_to_oprate (&mdtsum.unlink,  ul,    sizeof (ul)),
              _sample_to_oprate (&mdtsum.mkdir,   mkd,   sizeof (mkd)),
              _sample_to_oprate (&mdtsum.rmdir,   rmd,   sizeof (rmd)));
    mvprintw (x++, 0, "            %6s statfs, %6s rename, %6s getxattr",
              _sample_to_oprate (&mdtsum.statfs,  sfs,    sizeof (sfs)),
              _sample_to_oprate (&mdtsum.rename,  ren,    sizeof (ren)),
              _sample_to_oprate (&mdtsum.getxattr,gxattr,sizeof (gxattr)));

    /* Display the header */
    attron (A_REVERSE);
    mvprintw (x++, 0, "%-80s", "OST  S   Exp   rMB/s   wMB/s   rIOPS   wIOPS");
    attroff(A_REVERSE);

    /* Display the list of ost's */
    if (ost_data) {
        itr = list_iterator_create (ost_data);
        while ((o = list_next (itr))) {
            /* available info is expired */
            if ((now - o->ost_metric_timestamp) > STALE_THRESH_SEC) {
                mvprintw (x++, 0, "%s %s", o->name, o->oscstate);

            /* ost is in recovery - display recovery stats */
            } else if (strncmp (o->recov_status, "COMPLETE", 8) != 0) {
                mvprintw (x++, 0, "%s %s   %s", o->name, o->oscstate,
                          o->recov_status);

            /* ost is in normal state */
            } else {
                mvprintw (x++, 0, "%s %s %s %s %s %s", o->name, o->oscstate,
                      _nexp_to_val (o, nexp, sizeof (nexp)),
                      _sample_to_mbps (&o->rbytes, rmbps, sizeof (rmbps), NULL),
                      _sample_to_mbps (&o->wbytes, wmbps, sizeof (wmbps), NULL),
                      _sample_to_oprate (&o->riops, riops, sizeof (riops)),
                      _sample_to_oprate (&o->wiops, wiops, sizeof (wiops)));
            }
        }
    }

    refresh ();
}

static void
_update_ostsum (void)
{
    ListIterator itr;
    double totr = 0, totw = 0;
    double r, w;
    oststat_t *o;

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        _sample_to_mbps (&o->rbytes, NULL, 0, &r);
        totr += r;
        _sample_to_mbps (&o->wbytes, NULL, 0, &w);    
        totw += w;
    }
    list_iterator_destroy (itr);        
    ostsum.rmbps = totr;
    ostsum.wmbps = totw;
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

static oststat_t *
_create_oststat (char *name, char *state)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->name, ostx ? ostx + 4 : name, sizeof(o->name) - 1);
    strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
    _sample_init (&o->rbytes);
    _sample_init (&o->wbytes);
    _sample_init (&o->riops);
    _sample_init (&o->wiops);

    return o;
}

static void
_destroy_oststat (oststat_t *o)
{
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
_update_osc (char *name, char *state)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, name))) {
        o = _create_oststat (name, state);
        list_append (ost_data, o);
    } else {
        strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
    }
}

static void
_update_ost (char *name, time_t t, uint64_t read_bytes, uint64_t write_bytes,
             uint64_t num_exports, char *recov_status)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, name)))
        return;
    if (o->ost_metric_timestamp < t) {
        o->ost_metric_timestamp = t;
        _sample_update (&o->rbytes, (double)read_bytes, t);
        _sample_update (&o->wbytes, (double)write_bytes, t);
        o->num_exports = num_exports;
        memset (o->recov_status, 0, sizeof (o->recov_status));
        strncpy (o->recov_status, recov_status, sizeof (o->recov_status) - 1);
    }
}

static void
_update_mdt (char *name, time_t t, uint64_t inodes_free, uint64_t inodes_total,
             uint64_t kbytes_free, uint64_t kbytes_total, List mdops)
{
    char *opname, *s;
    ListIterator itr;
    uint64_t samples, sum, sumsquares;

    itr = list_iterator_create (mdops);
    while ((s = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdops (s, &opname,
                                &samples, &sum, &sumsquares) == 0) {
            if (!strcmp (opname, "open"))
                _sample_update (&mdtsum.open, (double)samples, t);
            else if (!strcmp (opname, "close"))
                _sample_update (&mdtsum.close, (double)samples, t);
            else if (!strcmp (opname, "getattr"))
                _sample_update (&mdtsum.getattr, (double)samples, t);
            else if (!strcmp (opname, "setattr"))
                _sample_update (&mdtsum.setattr, (double)samples, t);
            else if (!strcmp (opname, "link"))
                _sample_update (&mdtsum.link, (double)samples, t);
            else if (!strcmp (opname, "unlink"))
                _sample_update (&mdtsum.unlink, (double)samples, t);
            else if (!strcmp (opname, "mkdir"))
                _sample_update (&mdtsum.mkdir, (double)samples, t);
            else if (!strcmp (opname, "rmdir"))
                _sample_update (&mdtsum.rmdir, (double)samples, t);
            else if (!strcmp (opname, "statfs"))
                _sample_update (&mdtsum.statfs, (double)samples, t);
            else if (!strcmp (opname, "rename"))
                _sample_update (&mdtsum.rename, (double)samples, t);
            else if (!strcmp (opname, "getxattr"))
                _sample_update (&mdtsum.getxattr, (double)samples, t);
            free (opname);
        }
    }
    list_iterator_destroy (itr);
}

/* We get the definitive list of OST's from the MDS's osc view, which should
 * be constructed from the MDS logs, which in turn comes from the MGS.
 * The lmt_osc metric also provides the OST state from the MDS point of view,
 * a useful thing to know.
 */
static int
_poll_osc (void)
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
                if (_fsmatch (oscname)) {
                    if (now - t > STALE_THRESH_SEC)
                        _update_osc (oscname, " ");
                    else
                        _update_osc (oscname, oscstate);
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
_poll_ost (void)
{
    int retval = -1;
    cmetric_t c;
    List ostinfo, l = NULL;
    char *s, *val, *ossname, *ostname, *recov_status;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t num_exports;
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
        free (ossname);
        itr2 = list_iterator_create (ostinfo);
        while ((s = list_next (itr2))) {
            if (lmt_ost_decode_v2_ostinfo (s, &ostname,
                                           &read_bytes, &write_bytes,
                                           &kbytes_free, &kbytes_total,
                                           &inodes_free, &inodes_total,
                                           &num_exports, &recov_status) == 0) {
                if (_fsmatch (ostname)) {
                    _update_ost (ostname, t, read_bytes, write_bytes,
                                 num_exports, recov_status);
                    if (now - t <= STALE_THRESH_SEC) {
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
    }
    list_iterator_destroy (itr);
    list_destroy (l);
    ostsum.tbytes_free = (double)sum_kbytes_free / (1024.0*1024*1024);
    ostsum.tbytes_total = (double)sum_kbytes_total / (1024.0*1024*1024);
    retval = 0;
done: 
    return retval;
}

static int
_poll_mdt (void)
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
                if (_fsmatch (mdtname)) {
                    _update_mdt (mdtname, t, inodes_free, inodes_total,
                                 kbytes_free, kbytes_total, mdops);
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
    mdtsum.minodes_free = (double)sum_inodes_free / (1024.0*1024);
    mdtsum.minodes_total = (double)sum_inodes_total / (1024.0*1024);
    retval = 0;
done: 
    return retval;
}

static void
_poll_cerebro (void)
{
    if (_poll_osc () < 0)
        err_exit ("could not get cerebro lmt_osc metric");
    if (_poll_ost () < 0)
        err_exit ("could not get cerebro lmt_ost metric");
    _update_ostsum ();
    if (_poll_mdt () < 0)
        err_exit ("could not get cerebro lmt_mdt metric");

    list_sort (ost_data, (ListCmpF)_cmp_oststat);
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
