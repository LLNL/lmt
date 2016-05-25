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

/* ltop.c - curses based interface to lmt cerebro data */

#if HAVE_CONFIG_H
#include "config.h"
#endif
#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <stdint.h>
#include <inttypes.h>
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

#include "common.h"
#include "lmtcerebro.h"
#include "lmtconf.h"

#include "sample.h"

#ifndef MAXHOSTNAMELEN
#define MAXHOSTNAMELEN 64
#endif

/* (Mostly) fields common to all targets. For use in generic
 * sort/summarize/display code.  Exception is tag, which is set only
 * for OSTs, but needs to be visible to generic loop code.
 */
typedef struct {
    char fsname[17];            /* file system name */
    char name[17];              /* target index (4 hex digits) */
    char servername[MAXHOSTNAMELEN];/* oss or mds hostname */
    time_t tgt_metric_timestamp; /* cerebro timestamp for metric (not osc) */
    char recov_status[RECOVERY_STR_SIZE];   /* free form string representing */
                                            /* recovery status */
    char tgtstate[2];           /* single char state (blank if unknown), */
                                /* from osc */
    sample_t pct_cpu;
    sample_t pct_mem;
    int tag;                    /* display this target line underlined */
} generic_target_t;

typedef struct {
    generic_target_t common;    /* information common to all targets */
    sample_t rbytes;            /* read bytes/sec */
    sample_t wbytes;            /* write bytes/sec */
    sample_t iops;              /* io operations (r/w) per second */
    sample_t num_exports;       /* export count */
    sample_t lock_count;        /* lock count */
    sample_t grant_rate;        /* lock grant rate (LGR) */
    sample_t cancel_rate;       /* lock cancel rate (LCR) */
    sample_t connect;           /* connect+reconnect per second */
    sample_t kbytes_free;       /* free space (kbytes) */
    sample_t kbytes_total;      /* total space (kbytes) */
    time_t ost_metric_timestamp;/* cerebro timestamp for ost metric (not osc) */
    char ossname[MAXHOSTNAMELEN];/* oss hostname */
} oststat_t;

typedef struct {
    generic_target_t common;    /* information common to all targets */
    sample_t inodes_free;       /* free inode count */
    sample_t inodes_total;      /* total inode count */
    sample_t open;              /* open ops/sec */
    sample_t close;             /* close ops/sec */
    sample_t getattr;           /* getattr ops/sec */
    sample_t setattr;           /* setattr ops/sec */
    sample_t link;              /* link ops/sec */
    sample_t unlink;            /* unlink ops/sec */
    sample_t mkdir;             /* mkdir ops/sec */
    sample_t rmdir;             /* rmdir ops/sec */
    sample_t statfs;            /* statfs ops/sec */
    sample_t rename;            /* rename ops/sec */
    sample_t getxattr;          /* getxattr ops/sec */
} mdtstat_t;

typedef struct {
    ListCmpF fun;
    char k;
    char h[20];
} sort_t;

typedef struct {
    long p;                     /* file offset */
    uint64_t t;                 /* time stamp */
} ts_t;

typedef struct {
    char     fsname[17];       /* file system name */
    uint64_t num_mdt;          /* number of MDTs */
    uint64_t num_ost;          /* number of OSTs */
} fsstat_t;

/* used by _update_display_target */
typedef void (* _display_line_fn) (WINDOW *win, int line, void *o,
                                  int stale_secs, time_t tnow);

/* used by _summarize_target */
typedef void (* _tgt_update_summary) (void *tgt_v, void *summary_v);
typedef void * (* _copy_tgtstat) (void *src);

static void _poll_cerebro (char *fs, List mdt_data, List ost_data,
                           int stale_secs, FILE *recf, time_t *tp);
static void _play_file (char *fs, List mdt_data, List ost_data,
                        List time_series, int stale_secs, FILE *playf,
                        time_t *tp, int *tdiffp);
static void _update_display_help (WINDOW *win);
static char *_choose_fs (WINDOW *win, FILE *playf, int stale_secs);
static void _update_display_top (WINDOW *win, char *fs, List mdt_data,
                                 List ost_data, int stale_secs, FILE *recf,
                                 FILE *playf, time_t tnow, int pause);
static void _update_display_hdr (WINDOW *win, int cols, sort_t colhdr[],
                                 int selcol);
static void _update_display_target (WINDOW *win, List target_data,
                                    int mintarget, int seltarget,
                                    int stale_secs, time_t tnow,
                                 _display_line_fn fn);
static void _update_display_ost (WINDOW *win, int line, void *o,
                                 int stale_secs, time_t tnow);
static void _update_display_mdt (WINDOW *win, int line, void *target,
                                 int stale_secs, time_t tnow);
static void _destroy_oststat (oststat_t *o);
static int _fsmatch (char *name, char *fs);
static void _destroy_mdtstat (mdtstat_t *m);
static void _summarize_ost (List ost_data, List oss_data, time_t tnow,
                            int stale_secs);
static void _summarize_mdt (List mdt_data, List mds_data, time_t tnow,
                            int stale_secs);
static void _clear_tags (List ost_data);
static void _tag_nth_ost (List ost_data, int selost, List ost_data2);
static void _sort_tgtlist (List tgt_data, time_t tnow,
                           ListCmpF comparison_function);
static int  _get_sort_index (char k, int sort_index, sort_t c[], int nc);
static char *_find_first_fs (FILE *playf, int stale_secs);
static List _find_all_fs (FILE *playf, int stale_secs);
static void _record_file (FILE *f, time_t tnow, time_t trcv, char *node,
                          char *name, char *s);
static int _rewind_file (FILE *f, List time_series, int count);
static void _rewind_file_to (FILE *f, List time_series, time_t target);
static void _list_empty_out (List l);


/* comparison functions needed to sort by different fields */
/* generic */
static int _cmp_tgtstat_byserver (void *p1, void *p2);
static int _cmp_tgtstat_bytarget (void *p1, void *p2);
static int _cmp_tgtstat_bycpu (void *p1, void *p2);
static int _cmp_tgtstat_bymem (void *p1, void *p2);
static int _cmp_tgtstat_noop (void *p1, void *p2);

/* OST/OSS */
static int _cmp_oststat_byexp (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_bylocks (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_bylgr (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_bylcr (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_byconn (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_byiops (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_byrbw (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_bywbw (oststat_t *o1, oststat_t *o2);
static int _cmp_oststat_byspc (oststat_t *o1, oststat_t *o2);

/* MDT/MDS */
static int _cmp_mdtstat_byopen (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_byclose (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_bygetattr (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_bysetattr (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_byunlink (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_byrmdir (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_bymkdir (mdtstat_t *m1, mdtstat_t *m2);
static int _cmp_mdtstat_byrename (mdtstat_t *m1, mdtstat_t *m2);

/* Top of display fixed.  We also assume 80 chars wide.
 */
#define TOPWIN_LINES    7       /* lines in topwin */

#define OPTIONS "f:t:s:r:p:"
#if HAVE_GETOPT_LONG
#define GETOPT(ac,av,opt,lopt) getopt_long (ac,av,opt,lopt,NULL)
static const struct option longopts[] = {
    {"filesystem",      required_argument,  0, 'f'},
    {"sample-period",   required_argument,  0, 't'},
    {"stale-secs",      required_argument,  0, 's'},
    {"record",          required_argument,  0, 'r'},
    {"play",            required_argument,  0, 'p'},
    {0, 0, 0, 0},
};
#else
#define GETOPT(ac,av,opt,lopt) getopt (ac,av,opt)
#endif

/* N.B. This global is used ONLY for the purpose of allowing
 * _sort_tgtlist () to pass the current time to its various sorting
 * functions that operate on samples and must validate them.
 */
static time_t sort_tnow = 0;

/* The order of records in ost_col and mdt_col should match the order
 * the corresponding columns are displayed, so that when the user
 * enters > or < the new sort column is adjacent to the last one in
 * the display.  See _get_sort_index().
 */
sort_t ost_col[] = {
    { .fun = (ListCmpF)_cmp_tgtstat_bytarget,.k = 't',  .h = "%sOST"        },
                         /* search no-op */
    { .fun = (ListCmpF)_cmp_tgtstat_noop,    .k =  0,   .h = "%sS"          },
    { .fun = (ListCmpF)_cmp_tgtstat_byserver,.k = 's',  .h = "       %sOSS" },
    { .fun = (ListCmpF)_cmp_oststat_byexp,   .k = 'x',  .h = "  %sExp"      },
    { .fun = (ListCmpF)_cmp_oststat_byconn,  .k = 'C',  .h = "  %sCR"       },
    { .fun = (ListCmpF)_cmp_oststat_byrbw,   .k = 'r',  .h = "%srMB/s"      },
    { .fun = (ListCmpF)_cmp_oststat_bywbw,   .k = 'w',  .h = "%swMB/s"      },
    { .fun = (ListCmpF)_cmp_oststat_byiops,  .k = 'i',  .h = " %sIOPS"      },
    { .fun = (ListCmpF)_cmp_oststat_bylocks, .k = 'l',  .h = "  %sLOCKS"    },
    { .fun = (ListCmpF)_cmp_oststat_bylgr,   .k = 'g',  .h = " %sLGR"       },
    { .fun = (ListCmpF)_cmp_oststat_bylcr,   .k = 'L',  .h = " %sLCR"       },
    { .fun = (ListCmpF)_cmp_tgtstat_bycpu,   .k = 'u',  .h = "%s%%cpu"      },
    { .fun = (ListCmpF)_cmp_tgtstat_bymem,   .k = 'm',  .h = "%s%%mem"      },
    { .fun = (ListCmpF)_cmp_oststat_byspc,   .k = 'S',  .h = "%s%%spc"      },
};

sort_t mdt_col[] = {
    { .fun = (ListCmpF)_cmp_tgtstat_bytarget, .k =  't', .h = "%sMDT "      },
    { .fun = (ListCmpF)_cmp_tgtstat_byserver, .k =  's', .h = "        %sMDS"},
    { .fun = (ListCmpF)_cmp_mdtstat_byopen,   .k =  'o', .h = " %sOpen"      },
    { .fun = (ListCmpF)_cmp_mdtstat_byclose,  .k =  'C', .h = "%sClose"      },
    { .fun = (ListCmpF)_cmp_mdtstat_bygetattr,.k =  'g', .h = "%sGetAt"      },
    { .fun = (ListCmpF)_cmp_mdtstat_bysetattr,.k =  'S', .h = "%sSetAt"      },
    { .fun = (ListCmpF)_cmp_mdtstat_byunlink, .k =  'U', .h = "%sUnlnk"      },
    { .fun = (ListCmpF)_cmp_mdtstat_bymkdir,  .k =  'M', .h = "%sMkdir"      },
    { .fun = (ListCmpF)_cmp_mdtstat_byrmdir,  .k =  'r', .h = "%sRmdir"      },
    { .fun = (ListCmpF)_cmp_mdtstat_byrename, .k =  'R', .h = "%sRenam"      },
    { .fun = (ListCmpF)_cmp_tgtstat_bycpu,    .k =  'u', .h = " %s%%cpu"     },
    { .fun = (ListCmpF)_cmp_tgtstat_bymem,    .k =  'm', .h = " %s%%mem"     },
};

static void
usage (void)
{
    fprintf (stderr,
"Usage: ltop [OPTIONS]\n"
"   -f,--filesystem NAME      monitor file system NAME [default: first found]\n"
"   -t,--sample-period SECS   change display refresh [default: 1]\n"
"   -r,--record FILE          record session to FILE\n"
"   -p,--play FILE            play session from FILE\n"
"   -s,--stale-secs SECS      ignore data older than SECS [default: 12]\n"
    );
    exit (1);
}

/* Decide how many lines to allocate to each portion of the window.
 * 3 display geometries supported:
 * A) topwin (or a subset) only
 * B) topwin + OST header + some number of OST lines
 * C) topwin + MDT and OST sets of (header, lines)
 *
 * mdtlines = 1 line for the header + rest for list of targets
 * ostlines = 1 line for the header + rest for list of targets
 */
void
size_windows(int total_size, int mdts, int osts,
             int *mdtlines, int *ostlines)
{
    int headers = 2;    /* max lines occupied by MDT and OST headers */
    int extra = total_size - (TOPWIN_LINES + mdts + osts + headers);

    if (total_size <= TOPWIN_LINES) {
        *mdtlines = 0;
        *ostlines = 0;
    } else {
        if (mdts < 2 || (total_size < (TOPWIN_LINES + headers + 2))) {
            // no need for per-mdt lines OR
            // only big enough for topwin + small OST section
            *mdtlines = 0;
            *ostlines = total_size - TOPWIN_LINES;
        } else {
            /* big enough for both MDT and OST sections, and >1 MDT */
            if (extra >= 0) { 
                /* enough room to display everything */
                *mdtlines = mdts+1;
                *ostlines = osts+1;
            } else {
                /* OST and MDT section sizes proportional to target counts */
                *mdtlines = mdts + 1 + ((float)extra*mdts) / ((float)mdts+osts);
                *ostlines = total_size - (TOPWIN_LINES + *mdtlines);
            }
        }
    }
}

void
create_target_window(WINDOW **targetwin, int targetlines, int start_y)
{
    if (targetlines>=2) {
        if (!(*targetwin = newwin (targetlines, 80, start_y, 0)))
                err_exit ("error initializing subwindow");
    } else {
        *targetwin = NULL;
    }
}

int
main (int argc, char *argv[])
{
    int c;
    WINDOW *topwin, *ostwin, *mdtwin;
    int in_ostwin = 1;
    int ostcount, selost = -1, minost = 0;
    int mdtcount, selmdt = -1, minmdt = 0;
    int mdtlines = 0, ostlines = 0;
    int *mintgt = &minost, *seltgt = &selost, *tgtlines = &ostlines;
    int *tgtcount = &ostcount;
    int mdtview = 1, ostview = 1, resort = 0, recompute = 0, repoll = 0;
    int *target_view = &ostview;
    char *fs = NULL, *newfs;
    int sopt = 0;
    int sample_period = 2; /* seconds */
    int stale_secs = 12; /* seconds */
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    List oss_data = list_create ((ListDelF)_destroy_oststat);
    List mdt_data = list_create ((ListDelF)_destroy_mdtstat);
    List mds_data = list_create ((ListDelF)_destroy_oststat);
    List time_series = list_create ((ListDelF)free);
    time_t tcycle, last_sample = 0;
    char *recpath = "ltop.log";
    FILE *recf = NULL;
    FILE *playf = NULL;
    int pause = 0;
    int showhelp = 0;
    int mdt_fp = 0, ost_fp = 0;

    err_init (argv[0]);
    optind = 0;
    opterr = 0;

    lmt_conf_set_proto_debug(1);

    while ((c = GETOPT (argc, argv, OPTIONS, longopts)) != -1) {
        switch (c) {
            case 'f':   /* --filesystem FS */
                fs = xstrdup (optarg);
                break;
            case 't':   /* --sample-period SECS */
                sample_period = strtoul (optarg, NULL, 10);
                sopt = 1;
                break;
            case 's':   /* --stale-secs SECS */
                stale_secs = strtoul (optarg, NULL, 10);
                break;
            case 'r':   /* --record FILE */
                recpath = optarg;
                if (!(recf = fopen (recpath, "w+")))
                    err_exit ("error opening %s for writing", recpath);
                break;
            case 'p':   /* --play FILE */
                if (!(playf = fopen (optarg, "r")))
                    err_exit ("error opening %s for reading", optarg);
                break;
            default:
                usage ();
        }
    }
    if (optind < argc)
        usage();
    if (playf && sopt)
        msg_exit ("--sample-period and --play cannot be used together");
    if (playf && recf)
        msg_exit ("--record and --play cannot be used together");
#if ! HAVE_CEREBRO_H
    if (!playf)
        msg_exit ("ltop was not built with cerebro support, use -p option");
#endif
    if (!fs)
        fs = _find_first_fs(playf, stale_secs);
    if (!fs)
        msg_exit ("No live file system data found.");

    /* Poll cerebro for data, then sort the ost data for display.
     * If either the mds or any ost's are up, then ostcount > 0.
     */
    if (playf) {
        _play_file (fs, mdt_data, ost_data, time_series,
                    stale_secs, playf, &tcycle, &sample_period);
        if (feof (playf))
            msg_exit ("premature end of file on playback file");
    } else
        _poll_cerebro (fs, mdt_data, ost_data, stale_secs, recf, &tcycle);
    _sort_tgtlist (ost_data, tcycle, ost_col[ost_fp].fun);
    _sort_tgtlist (mdt_data, tcycle, mdt_col[mdt_fp].fun);
    assert (ostview);
    assert (mdtview);
    if ((mdtcount = list_count (mdt_data)) == 0)
        msg_exit ("no MDT data found for file system `%s'", fs);
    if ((ostcount = list_count (ost_data)) == 0)
        msg_exit ("no OST data found for file system `%s'", fs);

    /* Initialize curses and create the windows.  more curses below. */
    if (!(topwin = initscr ()))
        err_exit ("error initializing parent window");

    size_windows(LINES, mdtcount, ostcount, &mdtlines, &ostlines);

    create_target_window(&mdtwin, mdtlines, TOPWIN_LINES);

    create_target_window(&ostwin, ostlines, TOPWIN_LINES + mdtlines);

    /* Curses-fu:  keys will not be echoed, tty control sequences aren't
     * handled by tty driver, getch () times out and returns ERR after
     * sample_period seconds, multi-char keypad/arrow keys are handled.
     * Make cursor invisible.
     */
    raw ();
    noecho ();
    timeout (sample_period * 1000);
    keypad (topwin, TRUE);
    curs_set (0);

    /* Main processing loop:
     * Update display, read kbd (or timeout), update ost/mdt_data,
     *   create oss/mds_data (summary of ost/mdt_data), [repeat]
     */
    while (!isendwin ()) {
        if (showhelp) {
            _update_display_help (topwin);
        } else {
            _update_display_top (topwin, fs, ost_data, mdt_data, stale_secs,
                                 recf, playf, tcycle, pause);
            if (mdtwin) {
                    _update_display_hdr (mdtwin,
                                         sizeof(mdt_col)/sizeof(mdt_col[0]),
                                         mdt_col, mdt_fp);
                    _update_display_target (mdtwin,
                                            mdtview ? mdt_data : mds_data,
                                            minmdt, selmdt, stale_secs, tcycle,
                                            _update_display_mdt);
            }
            if (ostwin) {
                _update_display_hdr (ostwin, sizeof(ost_col)/sizeof(ost_col[0]),
                                     ost_col, ost_fp);
                _update_display_target (ostwin, ostview ? ost_data : oss_data,
                                        minost, selost, stale_secs, tcycle,
                                        _update_display_ost);
            }
        }
        switch ((c = getch ())) {
            case 'z':               /* z - toggle between OST and MDT window */
            case 'Z':
                if (mdtwin) {
                    in_ostwin = !in_ostwin;
                    recompute = 1;
                }
                break;
            case KEY_DC:            /* Delete - turn off highlighting */
                selost = selmdt = -1;
                _clear_tags (ost_data);
                _clear_tags (oss_data);
                break;
            case 'q':               /* q|Ctrl-C - quit */
            case 0x03:
                if (ostwin)
                    delwin (ostwin);
                if (mdtwin)
                    delwin (mdtwin);
                endwin ();
                break;
            case KEY_UP:            /* UpArrow|k - move highlight up */
            case 'k':   /* vi */
                if (*seltgt > 0)
                    (*seltgt)--;
                if (*seltgt >= *mintgt)
                    break;
                /* fall thru */
            case KEY_PPAGE:         /* PageUp|Ctrl-U - previous page */
            case 0x15:
                *mintgt -= (*tgtlines-1);
                if (*mintgt < 0)
                    *mintgt = 0;
                break;
            case KEY_DOWN:          /* DnArrow|j - move highlight down */
            case 'j':   /* vi */
                if (*seltgt < *tgtcount - 1)
                    (*seltgt)++;
                if (*seltgt - *mintgt < (*tgtlines-1))
                    break;
                 /* fall thru */
            case KEY_NPAGE:         /* PageDn|Ctrl-D - next page */
            case 0x04:
                if (*mintgt + *tgtlines <= *tgtcount)
                    *mintgt += (*tgtlines-1);
                break;
            case 'c':               /* c - toggle compressed server view */
                *target_view = !(*target_view);
                recompute = 1;
                *mintgt = 0;
                *seltgt = -1;
                break;
            case ' ':               /* SPACE - tag selected OST */
                if (ostview)
                    _tag_nth_ost (ost_data, selost, NULL);
                else
                    _tag_nth_ost (oss_data, selost, ost_data);
                break;
            case 'f':
                newfs = _choose_fs (topwin, playf, stale_secs);
                if (newfs) {
                    if (strcmp (newfs, fs) != 0) {
                        free (fs);
                        fs = xstrdup (newfs);
                        repoll = 1;
                        selost = selmdt = -1;
                        minost = minmdt = 0;
                        in_ostwin = 1;
                    }
                    free (newfs);
                }
                break;
            case '>':               /* change sorting column */
            case '<': 
            case 't': 
            case 's': 
            case 'r': 
            case 'w': 
            case 'i': 
            case 'x': 
            case 'l': 
            case 'g': 
            case 'L': 
            case 'C': 
            case 'u': 
            case 'm': 
            case 'S': 
                if (in_ostwin) {
                    ost_fp = _get_sort_index (c, ost_fp, ost_col,
                                              sizeof(ost_col)/sizeof(ost_col[0]));
                    _sort_tgtlist (ost_data, tcycle, ost_col[ost_fp].fun);
                    _sort_tgtlist (oss_data, tcycle, ost_col[ost_fp].fun);
                } else {
                    mdt_fp = _get_sort_index (c, mdt_fp, mdt_col,
                                              sizeof(mdt_col)/sizeof(mdt_col[0]));
                    _sort_tgtlist (mdt_data, tcycle, mdt_col[mdt_fp].fun);
                    _sort_tgtlist (mds_data, tcycle, mdt_col[mdt_fp].fun);
                }
                break;
            case 'R':               /* R - toggle record mode */
                if (!playf) {
                    if (recf) {
                        (void)fclose (recf);
                        recf = NULL;
                    } else
                        recf = fopen (recpath, "w+");
                }
                break;
            case 'p':               /* p - pause playback */
                if (playf)
                    pause = !pause;
                break;
            case KEY_LEFT:          /* LeftArrow - rewind 1 sample_period */
                if (playf) {
                    int count = _rewind_file (playf, time_series, 3);

                    if (count > 0) {
                        _list_empty_out (mdt_data);
                        _list_empty_out (ost_data);
                    }
                    while (count-- > 1)
                        _play_file (fs, mdt_data, ost_data, time_series,
                                    stale_secs, playf, &tcycle, &sample_period);
                    last_sample = time (NULL);
                    recompute = 1;
                }
                break;
            case KEY_BACKSPACE:     /* BACKSPACE - rewind 1 minute */
                if (playf) {
                    _list_empty_out (mdt_data);
                    _list_empty_out (ost_data);
                    _rewind_file_to (playf, time_series, tcycle - 60);
                    (void)_rewind_file (playf, time_series, 1);
                    _play_file (fs, mdt_data, ost_data, time_series,
                                stale_secs, playf, &tcycle, &sample_period);
                    _play_file (fs, mdt_data, ost_data, time_series,
                                stale_secs, playf, &tcycle, &sample_period);
                    last_sample = time (NULL);
                    recompute = 1;
                }
                break;
            case KEY_RIGHT:         /* RightArrow - ffwd 1 sample_period */
                if (playf) {
                    _list_empty_out (mdt_data);
                    _list_empty_out (ost_data);
                    _play_file (fs, mdt_data, ost_data, time_series,
                                stale_secs, playf, &tcycle, &sample_period);
                    last_sample = time (NULL);
                    recompute = 1;
                } 
                break;
            case '\t':              /* tab - fast-fwd 1 minute */
                if (playf) {
                    time_t t1 = tcycle + 60;

                    do {
                        _play_file (fs, mdt_data, ost_data, time_series,
                                    stale_secs, playf, &tcycle, &sample_period);
                    } while (tcycle < t1 && !feof (playf));
                    last_sample = time (NULL);
                    recompute = 1;
                }
                break;
            case '?':               /* ? - display help screen */
                showhelp = 1;
                break;
            case ERR:               /* timeout */
                break;
        }
        if (c != ERR && c != '?')
            showhelp = 0;

        if (in_ostwin) {
            mintgt = &minost;
            seltgt = &selost;
            tgtlines = &ostlines;
            tgtcount = &ostcount;
            target_view = &ostview;
        } else {
            mintgt = &minmdt;
            seltgt = &selmdt;
            tgtlines = &mdtlines;
            tgtcount = &mdtcount;
            target_view = &mdtview;
        }

        if (repoll) {
            _list_empty_out (mdt_data);
            _list_empty_out (ost_data);
            repoll = 0;
            last_sample = 0; /* force resample */
        }

        if (time (NULL) - last_sample >= sample_period) {
            if (!pause) {
                if (playf)
                    _play_file (fs, mdt_data, ost_data, time_series,
                                stale_secs, playf, &tcycle, &sample_period);
                else
                    _poll_cerebro (fs, mdt_data, ost_data, stale_secs, recf,
                                   &tcycle);
                last_sample = time (NULL);
                recompute = 1;
            }
            timeout (sample_period * 1000);
        } else
            timeout ((sample_period - (time (NULL) - last_sample)) * 1000);

        if (recompute) {
            mdtcount = list_count (mdtview ? mdt_data : mds_data);
            ostcount = list_count (ostview ? ost_data : oss_data);
        }

        size_windows(LINES, mdtcount, ostcount, &mdtlines, &ostlines);

        if (recompute) {
            if (mdtwin)
                delwin (mdtwin);
            if (ostwin)
                delwin (ostwin);

            create_target_window(&mdtwin, mdtlines, TOPWIN_LINES);

            create_target_window(&ostwin, ostlines, TOPWIN_LINES + mdtlines);

            if (!ostview)
                _summarize_ost (ost_data, oss_data, tcycle, stale_secs);
            if (!mdtview)
                _summarize_mdt (mdt_data, mds_data, tcycle, stale_secs);
            resort = 1;
            recompute = 0;
        }
        if (resort) {
            _sort_tgtlist (ost_data, tcycle, ost_col[ost_fp].fun); 
            _sort_tgtlist (oss_data, tcycle, ost_col[ost_fp].fun); 
            _sort_tgtlist (mdt_data, tcycle, mdt_col[mdt_fp].fun);
            _sort_tgtlist (mds_data, tcycle, mdt_col[mdt_fp].fun);
            resort = 0;
        }
    }

    list_destroy (ost_data);
    list_destroy (mdt_data);
    list_destroy (oss_data);
    list_destroy (mds_data);
    list_destroy (time_series);
    free (fs);

    if (recf) {
        if (fclose (recf) == EOF)
            err ("Error closing %s", recpath);
        else
            msg ("Log recorded in %s", recpath);
    }
    msg ("Goodbye");
    exit (0);
}

/* Show help window.
 * Uppercase keys are used where lowercase is already taken.
 * For some keys, meaning depends on whether OST or MDT is the
 * currently selected window.
 * Keep in sync wiht mdt_col[] and ost_col[].
 */
static void _update_display_help (WINDOW *win)
{
    int y = 0;
    wclear (win);
    wattron (win, A_REVERSE);
    mvwprintw (win, y++, 0,
              "Help for Interactive Commands - ltop version %s",
              PACKAGE_VERSION);
    wattroff (win, A_REVERSE);
    y++;
    mvwprintw (win, y++, 2, "z/Z        switch between OST/MDT window");
    mvwprintw (win, y++, 2, "PageUp     Page up through targets");
    mvwprintw (win, y++, 2, "PageDown   Page down through targets");
    mvwprintw (win, y++, 2, "UpArrow    Move cursor up");
    mvwprintw (win, y++, 2, "DownArrow  Move cursor down");
    mvwprintw (win, y++, 2, "Space      Tag/untag OST under cursor");
    mvwprintw (win, y++, 2, "Delete     Park cursor and clear tags");
    mvwprintw (win, y++, 2, "R          Toggle record mode");
    mvwprintw (win, y++, 2, "p          Pause data collection/playback");
    mvwprintw (win, y++, 2, "RightArrow Fast-fwd playback one sample period");
    mvwprintw (win, y++, 2, "LeftArrow  Rewind playback one sample period");
    mvwprintw (win, y++, 2, "Tab        Fast-fwd playback one minute");
    mvwprintw (win, y++, 2, "Backspace  Rewind playback one minute");
    mvwprintw (win, y++, 2, "c          Toggle target/server view");
    mvwprintw (win, y++, 2, "f          Select filesystem to monitor");
    mvwprintw (win, y++, 2, ">          Sort on next right column");
    mvwprintw (win, y++, 2, "<          Sort on next left column");
    mvwprintw (win, y++, 2, "t          Sort on target name (ascending)");
    mvwprintw (win, y++, 2, "s          Sort on server name (ascending)");

    mvwprintw (win, y++, 2, "x          Sort on export count (ascending/OST)");
    mvwprintw (win, y++, 2, "C          Sort on connect rate (descending/OST)");
    mvwprintw (win, y++, 2, "r          Sort on read b/w (descending/OST)");
    mvwprintw (win, y++, 2, "w          Sort on write b/w (descending/OST)");
    mvwprintw (win, y++, 2, "i          Sort on IOPS (descending/OST)");
    mvwprintw (win, y++, 2, "l          Sort on lock count (descending/OST)");
    mvwprintw (win, y++, 2, "g          Sort on lock grant rate (descending/OST)");
    mvwprintw (win, y++, 2, "L          Sort on lock cancel  rate (descending/OST)");

    mvwprintw (win, y++, 2, "o          Sort on open rate (descending/MDT)");
    mvwprintw (win, y++, 2, "C          Sort on close rate (descending/MDT)");
    mvwprintw (win, y++, 2, "g          Sort on getattr rate (descending/MDT)");
    mvwprintw (win, y++, 2, "S          Sort on setattr rate (descending/MDT)");
    mvwprintw (win, y++, 2, "U          Sort on unlink rate (descending/MDT)");
    mvwprintw (win, y++, 2, "M          Sort on mkdir rate (descending/MDT)");
    mvwprintw (win, y++, 2, "r          Sort on rmdir rate (descending/MDT)");
    mvwprintw (win, y++, 2, "R          Sort on rename rate (descending/MDT)");

    mvwprintw (win, y++, 2, "u          Sort on %%cpu utilization (descending)");
    mvwprintw (win, y++, 2, "m          Sort on %%memory utilization (descending)");
    mvwprintw (win, y++, 2, "S          Sort on %%disk space utilization (descending)");
    mvwprintw (win, y++, 2, "q          Quit");

    wrefresh (win);
}

/* Update the top (summary) window of the display.
 * Sum data rate and free space over all OST's.
 * Sum op rates and free inodes over all MDT's (>1 if CMD).
 */
static void
_update_display_top (WINDOW *win, char *fs, List ost_data, List mdt_data,
                     int stale_secs, FILE *recf, FILE *playf, time_t tnow,
                     int pause)
{
    time_t trcv = 0;
    int y = 0;
    ListIterator itr;
    double rmbps = 0, wmbps = 0, iops = 0;
    double tbytes_free = 0, tbytes_total = 0;
    double minodes_free = 0, minodes_total = 0;
    double open = 0, close = 0, getattr = 0, setattr = 0;
    double link = 0, unlink = 0, rmdir = 0, mkdir = 0;
    double statfs = 0, rename = 0, getxattr = 0;
    char recovery_status[RECOVERY_STR_SIZE]="";
    int recov_status_len;
    oststat_t *o;
    mdtstat_t *m;

    /*
     * Recovery status fits between filesystem name and indicators
     * like "RECORDING". The former takes up 12+strlen(fs) columns.
     * The indicators are displayed starting at column 68. Some blank
     * spaces on either side are desirable.
     *
     * We also need to make sure we don't overflow the recovery_status.
     */
    recov_status_len = 68 - (12 + strlen(fs) + 4);
    recov_status_len = (RECOVERY_STR_SIZE < recov_status_len ?
                        RECOVERY_STR_SIZE : recov_status_len);

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        rmbps         += sample_rate (o->rbytes, tnow) / (1024*1024);
        wmbps         += sample_rate (o->wbytes, tnow) / (1024*1024);
        iops          += sample_rate (o->iops, tnow);
        tbytes_free   += sample_val (o->kbytes_free, tnow) / (1024*1024*1024);
        tbytes_total  += sample_val (o->kbytes_total, tnow) / (1024*1024*1024);
    }
    list_iterator_destroy (itr);
    itr = list_iterator_create (mdt_data);
    while ((m = list_next (itr))) {
        open          += sample_rate (m->open, tnow);
        close         += sample_rate (m->close, tnow);
        getattr       += sample_rate (m->getattr, tnow);
        setattr       += sample_rate (m->setattr, tnow);
        link          += sample_rate (m->link, tnow);
        unlink        += sample_rate (m->unlink, tnow);
        rmdir         += sample_rate (m->rmdir, tnow);
        mkdir         += sample_rate (m->mkdir, tnow);
        statfs        += sample_rate (m->statfs, tnow);
        rename        += sample_rate (m->rename, tnow);
        getxattr      += sample_rate (m->getxattr, tnow);
        minodes_free  += sample_val (m->inodes_free, tnow) / (1024*1024);
        minodes_total += sample_val (m->inodes_total, tnow) / (1024*1024);

        /*
         * recovery_status is just a string, and has no timestamp.
         */
        if ((tnow - m->common.tgt_metric_timestamp) < stale_secs) {
            if (m->common.recov_status && strstr(m->common.recov_status,"RECOV")) {
                /*
                 * Multiple MDTs may be in recovery, but display room
                 * is limited.  We print the full status of the first
                 * MDT in recovery we find.  This allows the user to
                 * tell whether the count of reconnected clients is
                 * increasing.
                 */
                if (recovery_status[0] == '\0')
                    snprintf(recovery_status, recov_status_len, "MDT%s %s",
                             m->common.name, m->common.recov_status);
           }
        }

        if (m->common.tgt_metric_timestamp > trcv)
            trcv = m->common.tgt_metric_timestamp;
    }
    list_iterator_destroy (itr);

    wclear (win);

    mvwprintw (win, y, 0, "Filesystem: %s  %s", fs, recovery_status);
    if (pause) {
        wattron (win, A_REVERSE);
        mvwprintw (win, y, 73, "PAUSED");
        wattroff (win, A_REVERSE);
    } else if (recf) {
        wattron (win, A_REVERSE);
        if (ferror (recf))
            mvwprintw (win, y, 68, "WRITE ERROR");
        else
            mvwprintw (win, y, 70, "RECORDING");
        wattroff (win, A_REVERSE);
    } else if (playf) {
        char *ts = ctime (&tnow);

        wattron (win, A_REVERSE);
        if (ferror (playf))
            mvwprintw (win, y, 69, "READ ERROR");
        else if (feof (playf))
            mvwprintw (win, y, 68, "END OF FILE");
        else
            mvwprintw (win, y, 55, "%*s", strlen (ts) - 1, ts);
        wattroff (win, A_REVERSE);
    }
    y++;
    if (tnow - trcv <= stale_secs) { /* mdt data is live */
        mvwprintw (win, y++, 0,
          "    Inodes: %10.3fm total, %10.3fm used (%3.0f%%), %10.3fm free",
                   minodes_total, minodes_total - minodes_free,
                   ((minodes_total - minodes_free) / minodes_total) * 100,
                   minodes_free);
    } else  {
        mvwprintw (win, y++, 0,
          "    Inodes: %11s total, %11s used (%3s%%), %11s free",
                    "", "", "", "" );
    } 
    mvwprintw (win, y++, 0,
      "     Space: %10.3ft total, %10.3ft used (%3.0f%%), %10.3ft free",
               tbytes_total, tbytes_total - tbytes_free,
               ((tbytes_total - tbytes_free) / tbytes_total) * 100,
               tbytes_free);
    mvwprintw (win, y++, 0,
      "   Bytes/s: %10.3fg read,  %10.3fg write,            %6.0f IOPS",
               rmbps / 1024, wmbps / 1024, iops);
    if (tnow - trcv <= stale_secs) { /* mdt data is live */
        mvwprintw (win, y++, 0,
          "   MDops/s: %6.0f open,   %6.0f close,  %6.0f getattr,  %6.0f setattr",
                   open, close, getattr, setattr);
        mvwprintw (win, y++, 0,
          "            %6.0f link,   %6.0f unlink, %6.0f mkdir,    %6.0f rmdir",
                   link, unlink, mkdir, rmdir);
        mvwprintw (win, y++, 0,
          "            %6.0f statfs, %6.0f rename, %6.0f getxattr",
                   statfs, rename, getxattr);
    } else {
        mvwprintw (win, y++, 0,
          "   MDops/s: %6s open,   %6s close,  %6s getattr,  %6s setattr",
                   "", "", "", "");
        mvwprintw (win, y++, 0,
          "            %6s link,   %6s unlink, %6s mkdir,    %6s rmdir",
                   "", "", "", "");
        mvwprintw (win, y++, 0,
          "            %6s statfs, %6s rename, %6s getxattr",
                   "", "", "");
    }
    wrefresh (win);
}

/*  Used for list_find_first () of fsstat_t by filesystem name.
 */
static int
_match_fsstat(fsstat_t *fsstat, char *fsname)
{
    return (strcmp (fsstat->fsname, fsname) == 0);
}

/* Trivial destructor for fstat record.
 */
static void
_destroy_fsstat (fsstat_t *f)
{
    free (f);
}

/* Create an fsstat record.
 */
static fsstat_t *
_create_fsstat (char *fsname, uint64_t mdts, uint64_t osts)
{
    fsstat_t *f = xmalloc (sizeof (*f));
    memset (f, 0, sizeof (*f));
    strncpy (f->fsname, fsname, sizeof (f->fsname) - 1);
    f->num_mdt = mdts;
    f->num_ost = osts;
    return f;
}

/* Display a menu allowing selection from a list of file systems available
 * for monitoring.
 */
static void
_update_display_choose_fs (int selfs, WINDOW *win, List fsl)
{
    int y = 0;
    fsstat_t *f;
    ListIterator fsitr;
    fsitr = list_iterator_create (fsl);
    int hdr_rows = 3; /* rows in header */

    wclear (win);
    mvwprintw (win, y++, 0, "Select a filesystem to monitor.");
    mvwprintw (win, y++, 0, "");
    wattron (win, A_REVERSE);
    mvwprintw (win, y++, 0, "NAME              MDTs    OSTs");
    wattroff (win, A_REVERSE);

    for (; (f = list_next (fsitr)); y++) {
        if (y - hdr_rows == selfs)
            wattron (win, A_UNDERLINE);
        mvwprintw (win, y, 0, "%-17s %4d    %4d", f->fsname, f->num_mdt, f->num_ost);
        if (y - hdr_rows == selfs)
            wattroff (win, A_UNDERLINE);
    }

    list_iterator_destroy (fsitr);
    wrefresh (win);
}

/* Return a list of fsstat_t records containing file system names
 * and OST counts for all available file systems.
 */
static List
_find_all_fs (FILE *playf, int stale_secs)
{
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    List mdt_data = list_create ((ListDelF)_destroy_mdtstat);
    List fsl      = list_create ((ListDelF)_destroy_fsstat);
    ListIterator itr;
    mdtstat_t *m;
    oststat_t *o;
    fsstat_t *f;

    if (playf)
        _play_file (NULL, mdt_data, ost_data, NULL, stale_secs,
                    playf, NULL, NULL);
    else
        _poll_cerebro (NULL, mdt_data, ost_data, stale_secs, NULL, NULL);

    itr = list_iterator_create (mdt_data);
    while ((m = list_next (itr))) {
        if (!(f = list_find_first (fsl, (ListFindF)_match_fsstat,
                                   m->common.fsname)))
            list_append (fsl, _create_fsstat (m->common.fsname, 1, 0));
        else
            f->num_mdt++;
    }
    list_iterator_destroy (itr);
    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (!(f = list_find_first (fsl, (ListFindF)_match_fsstat,
                                   o->common.fsname)))
            list_append (fsl, _create_fsstat (o->common.fsname, 0, 1));
        else
            f->num_ost++;
    }

    list_iterator_destroy (itr);
    list_destroy (ost_data);
    list_destroy (mdt_data);
    return fsl;
}

/* Return a heap-allocated string containing name of selected
 * file system, or NULL if no selection was made.
 */
static char *
_choose_fs (WINDOW *win, FILE *playf, int stale_secs)
{
    int selfs = 0, fscount = 0, i, loop, c;
    fsstat_t *f;
    char *ret = NULL;
    List fsl = _find_all_fs (playf, stale_secs);
    ListIterator fsitr = list_iterator_create (fsl);

    _update_display_choose_fs (selfs, win, fsl);
    fscount = list_count (fsl);
    loop = fscount;
    while (loop && !ret) {
        switch ((c = getch ())) {
            case KEY_UP:    /* UpArrow|k - move highlight up */
            case 'k':       /* vi */
                if (selfs > 0)
                    _update_display_choose_fs (--selfs, win, fsl);
                break;
            case KEY_DOWN:  /* DnArrow|j - move highlight down */
            case 'j':       /* vi */
                if (selfs < fscount - 1)
                    _update_display_choose_fs (++selfs, win, fsl);
                break;
            case '\n':      /* Enter|Space selects filesystem */
            case KEY_ENTER:
            case ' ':
                for (i=0; (f = list_next (fsitr)); i++)
                    if (i == selfs)
                        ret = xstrdup (f->fsname);
                break;
            case ERR:       /* timeout */
                break;
            default:        /* Any other key returns to main screen */
                loop = 0;
                break;
        }
    }

    list_iterator_destroy (fsitr);
    list_destroy (fsl);
    return ret;
}

/* Left "truncate" s by returning an offset into it such that the new
 * string has at most max characters.
 */
static char *
_ltrunc (char *s, int max)
{
    int len = strlen (s);

    return s + (len > max ? len - max : 0);
}

static void
_update_display_hdr (WINDOW *win, int cols, sort_t colhdr[], int sel)
{
    int i;

    wclear (win);
    wmove (win, 0, 0);
    wattron (win, A_REVERSE);
    for(i=0;i<cols;i++) {
        wprintw (win, colhdr[i].h,  sel == i  ? ">" : " ");
    }
    wattroff(win, A_REVERSE);
    wrefresh (win);
}

static void
_update_display_mdt (WINDOW *win, int line, void *target, int stale_secs,
                     time_t tnow)
{
    mdtstat_t *m = (mdtstat_t *) target;

    /* Future enhancement: if all "osc status" reported by an
     * MDT are the same, print the corresponding single
     * character.  If not, print a character indicating
     * "mixed" (update man page).
     * Same for OST.  Then you get clear indicators for
     * common issues (e.g. OSS died).
     * For now, omit that field for the MDTs.
     * See: osc.c:_get_oscstring()
     *      proc_lustre_oscinfo()
     *      fs/lustre/osc/%s/ost_server_uuid
     *      fs/lustre/osc/%s/state
     *      man ltop "OSC status"
     *      comment at _update_osc()
     */

    if ((tnow - m->common.tgt_metric_timestamp) > stale_secs) {
        // available info is expired 
        mvwprintw (win, line, 0, "%4.4s data is stale", m->common.name);
    } else if (m->common.recov_status &&
               strstr(m->common.recov_status,"RECOV")) {
        /* mdt is in recovery - display recovery stats */
        mvwprintw (win, line, 0, "%4.4s   %10.10s %s",
                   m->common.name, _ltrunc (m->common.servername, 10),
                   m->common.recov_status);
    } else {
        /* mdt is not in recovery */
        mvwprintw (win, line, 0, "%4.4s %12.12s"
                   " %5.0f %5.0f %5.0f %5.0f %5.0f %5.0f %5.0f %5.0f"
                   " %5.0f %5.0f",
                   m->common.name, _ltrunc (m->common.servername, 10),
                   sample_rate (m->open, tnow),
                   sample_rate (m->close, tnow),
                   sample_rate (m->getattr, tnow),
                   sample_rate (m->setattr, tnow),
                   sample_rate (m->unlink, tnow),
                   sample_rate (m->mkdir, tnow),
                   sample_rate (m->rmdir, tnow),
                   sample_rate (m->rename, tnow),
                   sample_val (m->common.pct_cpu, tnow),
                   sample_val (m->common.pct_mem, tnow)
                   );
    }
}

static void
_update_display_ost (WINDOW *win, int line, void *target, int stale_secs,
                     time_t tnow)
{
    oststat_t *o = (oststat_t *) target;

    double ktot = sample_val (o->kbytes_total, tnow);
    double kfree = sample_val (o->kbytes_free, tnow);
    double pct_used = ktot > 0 ? ((ktot - kfree) / ktot)*100.0 : 0;

    /* available info is expired */
    if ((tnow - o->common.tgt_metric_timestamp) > stale_secs) {
        mvwprintw (win, line, 0, "%4.4s %1.1s data is stale",
                   o->common.name, o->common.tgtstate);
    /* ost is in recovery - display recovery stats */
    } else if (strncmp (o->common.recov_status, "COMPLETE", 8) != 0
            && strncmp (o->common.recov_status, "INACTIVE", 8) != 0) {
        mvwprintw (win, line, 0, "%4.4s %1.1s %10.10s   %s",
                   o->common.name, o->common.tgtstate,
                   _ltrunc (o->common.servername, 10),
                   o->common.recov_status);
    /* ost is not in recover (state == INACTIVE|COMPLETE) */
    } else {
        mvwprintw (win, line, 0, "%4.4s %1.1s %10.10s"
                   " %5.0f %4.0f %5.0f %5.0f %5.0f %7.0f %4.0f %4.0f"
                   " %4.0f %4.0f %4.0f",
                   o->common.name, o->common.tgtstate,
                   _ltrunc (o->common.servername, 10),
                   sample_val (o->num_exports, tnow),
                   sample_rate (o->connect, tnow),
                   sample_rate (o->rbytes, tnow) / (1024*1024),
                   sample_rate (o->wbytes, tnow) / (1024*1024),
                   sample_rate (o->iops, tnow),
                   sample_val (o->lock_count, tnow),
                   sample_val (o->grant_rate, tnow),
                   sample_val (o->cancel_rate, tnow),
                   sample_val (o->common.pct_cpu, tnow),
                   sample_val (o->common.pct_mem, tnow),
                   pct_used);
    }
}

/* Update the ost/oss or mdt/mds window of the display.
 * tgt is either an mdt, mds, ost, or oss
 * Mintgt is the first tgt to display (zero origin).
 * Seltgt is the selected tgt, or -1 if none are selected (zero origin).
 * Stale_secs is the number of seconds after which data is expried.
 * _display_line_fn is the function to call to display one target.
 */
static void
_update_display_target (WINDOW *win, List tgt_data, int mintgt, int seltgt,
                     int stale_secs, time_t tnow, _display_line_fn fn)
{
    ListIterator        itr;
    generic_target_t    *o;
    int                 y = 1;
    int                 skiptgt = mintgt;

    itr = list_iterator_create (tgt_data);
    while ((o = (generic_target_t *) list_next (itr))) {
        if (skiptgt-- > 0)
            continue;
        if (y - 1 + mintgt == seltgt)
            wattron (win, A_REVERSE);
        if (o->tag)
            wattron (win, A_UNDERLINE);
        fn (win, y, o, stale_secs, tnow);
        if (y - 1 + mintgt == seltgt)
            wattroff(win, A_REVERSE);
        if (o->tag)
            wattroff(win, A_UNDERLINE);
        y++;
    }
    list_iterator_destroy (itr);

    wrefresh (win);
}

/*  Used for list_find_first () of MDT by mds name.
 */
static int
_match_mdtstat2 (mdtstat_t *m, char *name)
{
    return (strcmp (m->common.servername, name) == 0);
}

/*  Used for list_find_first () of MDT by filesystem and target name,
 *  e.g. fs-MDTxxxx.
 */
static int
_match_mdtstat (mdtstat_t *m, char *name)
{
    int targetmatch;
    int fsmatch;
    char *p = strstr (name, "-MDT");

    fsmatch = _fsmatch(name, m->common.fsname);
    targetmatch = (strcmp (m->common.name, p ? p + 4 : name) == 0);

    return (targetmatch && fsmatch);
}

/* Copy an mdtstat record.
 */
static void *
_copy_mdtstat (void *src_v)
{
    mdtstat_t *src = (mdtstat_t *) src_v;
    mdtstat_t *m = xmalloc (sizeof (*m));

    memcpy (m, src, sizeof (*m));
    m->inodes_free =  sample_copy (src->inodes_free);
    m->inodes_total = sample_copy (src->inodes_total);
    m->open =         sample_copy (src->open);
    m->close =        sample_copy (src->close);
    m->getattr =      sample_copy (src->getattr);
    m->setattr =      sample_copy (src->setattr);
    m->link =         sample_copy (src->link);
    m->unlink =       sample_copy (src->unlink);
    m->mkdir =        sample_copy (src->mkdir);
    m->rmdir =        sample_copy (src->rmdir);
    m->statfs =       sample_copy (src->statfs);
    m->rename =       sample_copy (src->rename);
    m->common.pct_cpu =      sample_copy (src->common.pct_cpu);
    m->common.pct_mem =      sample_copy (src->common.pct_mem);
    m->getxattr =     sample_copy (src->getxattr);
    return (void *) m;
}

/* Create an mdtstat record.
 */
static mdtstat_t *
_create_mdtstat (char *name, int stale_secs)
{
    mdtstat_t *m = xmalloc (sizeof (*m));
    char *mdtx = strstr (name, "-MDT");

    memset (m, 0, sizeof (*m));
    strncpy (m->common.name, mdtx ? mdtx + 4 : name, sizeof(m->common.name)-1);
    strncpy (m->common.fsname, name,
             mdtx ? mdtx - name : sizeof (m->common.fsname)-1);
    *m->common.tgtstate = '\0';
    *m->common.recov_status='\0';
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
    m->common.pct_cpu =      sample_create (stale_secs);
    m->common.pct_mem =      sample_create (stale_secs);
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
    sample_destroy (m->common.pct_cpu);
    sample_destroy (m->common.pct_mem);
    sample_destroy (m->getxattr);
    free (m);
}

/*  Used for list_find_first () of OST by target name, e.g. fs-OSTxxxx.
 */
static int
_match_oststat (oststat_t *o, char *name)
{
    int targetmatch;
    int fsmatch;
    char *p = strstr (name, "-OST");

    fsmatch = _fsmatch(name, o->common.fsname);
    targetmatch = (strcmp (o->common.name, p ? p + 4 : name) == 0);

    return (targetmatch && fsmatch);
}

/*  Used for list_find_first () of OST by oss name.
 */
static int
_match_oststat2 (oststat_t *o, char *name)
{
    return (strcmp (o->common.servername, name) == 0);
}

/* Helper for _cmp_server_names ()
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

/* Helper for _cmp_oststat_byoss () and _cmp_mdtstat_bymds () Like
 * strcmp, but handle variable-width (unpadded) numerical suffixes, if
 * any.
 */
static int
_cmp_server_names (char *servername1, char *servername2)
{
    unsigned long n1, n2;
    char *p1 = _numerical_suffix (servername1, &n1);
    char *p2 = _numerical_suffix (servername2, &n2);

    if (*p1 && *p2
            && (p1 - servername1) == (p2 - servername2)
            && !strncmp (servername1, servername2, p1 - servername1)) {
        return (n1 < n2 ? -1 
              : n1 > n2 ? 1 : 0);
    }
    return strcmp (servername1, servername2);
}

/* Used for list_sort () of OST or MDT list by servername.
 * See _cmp_server_names for details.
 */
static int
_cmp_tgtstat_byserver (void *p1, void *p2)
{
    return _cmp_server_names (((generic_target_t *) p1)->servername,
                              ((generic_target_t *) p2)->servername);
}

/* Used for list_sort () of OST/MDT list by target name.
 * Fixed width hex sorts alphanumerically.
 */
static int
_cmp_tgtstat_bytarget (void *p1, void *p2)
{
    return strcmp (((generic_target_t *) p1)->name,
                   ((generic_target_t *) p2)->name);
}

/* Used for list_sort () of OST/MDT list by pct_mem (descending order).
 */
static int
_cmp_tgtstat_bymem (void *p1, void *p2)
{
    return -1 * sample_val_cmp (((generic_target_t *) p1)->pct_mem,
                                ((generic_target_t *) p2)->pct_mem, sort_tnow);
}

/* Used for list_sort () of OST/MDT list by pct_cpu (descending order).
 */
static int
_cmp_tgtstat_bycpu (void *p1, void *p2)
{
    return -1 * sample_val_cmp (((generic_target_t *) p1)->pct_cpu,
                                ((generic_target_t *) p2)->pct_cpu, sort_tnow);
}

/* Used for no-op list_sort () of OST/MDT list.
 */
static int
_cmp_tgtstat_noop (void *p1, void *p2)
{
    return 0;
}

/* Used for list_sort () of OST list by export count (ascending order).
 */
static int
_cmp_oststat_byexp (oststat_t *o1, oststat_t *o2)
{
    return sample_val_cmp (o1->num_exports, o2->num_exports, sort_tnow);
}

/* Used for list_sort () of OST list by lock count (descending order).
 */
static int
_cmp_oststat_bylocks (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_val_cmp (o1->lock_count, o2->lock_count, sort_tnow);
}

/* Used for list_sort () of OST list by lock grant rate (descending order).
 */
static int
_cmp_oststat_bylgr (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_val_cmp (o1->grant_rate, o2->grant_rate, sort_tnow);
}

/* Used for list_sort () of OST list by lock cancel rate (descending order).
 */
static int
_cmp_oststat_bylcr (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_val_cmp (o1->cancel_rate, o2->cancel_rate, sort_tnow);
}

/* Used for list_sort () of OST list by (re-)connect rate (descending order).
 */
static int
_cmp_oststat_byconn (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_rate_cmp (o1->connect, o2->connect, sort_tnow);
}

/* Used for list_sort () of OST list by iops (descending order).
 */
static int
_cmp_oststat_byiops (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_rate_cmp (o1->iops, o2->iops, sort_tnow);
}

/* Used for list_sort () of OST list by read b/w (descending order).
 */
static int
_cmp_oststat_byrbw (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_rate_cmp (o1->rbytes, o2->rbytes, sort_tnow);
}

/* Used for list_sort () of OST list by write b/w (descending order).
 */
static int
_cmp_oststat_bywbw (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_rate_cmp (o1->wbytes, o2->wbytes, sort_tnow);
}


/* Used for list_sort () of OST list by pct space used (descending order).
 */
static int
_cmp_oststat_byspc (oststat_t *o1, oststat_t *o2)
{
    double t1 = sample_val (o1->kbytes_total, sort_tnow);
    double f1 = sample_val (o1->kbytes_free, sort_tnow);
    double p1 = t1 > 0 ? ((t1 - f1) / t1)*100.0 : 0;
    double t2 = sample_val (o2->kbytes_total, sort_tnow);
    double f2 = sample_val (o2->kbytes_free, sort_tnow);
    double p2 = t2 > 0 ? ((t2 - f2) / t2)*100.0 : 0;
    int ret = p1 == p2 ? 0 : p1 < p2 ? -1 : 1; /* ascending */

    return -1 * ret;
}

/* Used for list_sort () of MDT list by opens (descending order).
 */
static int
_cmp_mdtstat_byopen (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->open, m2->open, sort_tnow);
}

/* Used for list_sort () of MDT list by closes (descending order).
 */
static int
_cmp_mdtstat_byclose (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->close, m2->close, sort_tnow);
}

/* Used for list_sort () of MDT list by getattrs (descending order).
 */
static int
_cmp_mdtstat_bygetattr (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->getattr, m2->getattr, sort_tnow);
}

/* Used for list_sort () of MDT list by setattrs (descending order).
 */
static int
_cmp_mdtstat_bysetattr (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->setattr, m2->setattr, sort_tnow);
}

/* Used for list_sort () of MDT list by unlinks (descending order).
 */
static int
_cmp_mdtstat_byunlink (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->unlink, m2->unlink, sort_tnow);
}

/* Used for list_sort () of MDT list by rmdirs (descending order).
 */
static int
_cmp_mdtstat_byrmdir (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->rmdir, m2->rmdir, sort_tnow);
}

/* Used for list_sort () of MDT list by mkdirs (descending order).
 */
static int
_cmp_mdtstat_bymkdir (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->mkdir, m2->mkdir, sort_tnow);
}

/* Used for list_sort () of MDT list by renames (descending order).
 */
static int
_cmp_mdtstat_byrename (mdtstat_t *m1, mdtstat_t *m2)
{
    return -1 * sample_rate_cmp (m1->rename, m2->rename, sort_tnow);
}

/* Create an oststat record.
 */
static oststat_t *
_create_oststat (char *name, int stale_secs)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->common.name, ostx ? ostx + 4 : name, sizeof(o->common.name) - 1);
    strncpy (o->common.fsname, name,
             ostx ? ostx - name : sizeof (o->common.fsname) - 1);
    *o->common.tgtstate = '\0';
    *o->common.recov_status='\0';
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
    o->common.pct_cpu      = sample_create (stale_secs);
    o->common.pct_mem      = sample_create (stale_secs);
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
    sample_destroy (o->common.pct_cpu);
    sample_destroy (o->common.pct_mem);
    free (o);
}

/* Copy an oststat record.
 */
static void *
_copy_oststat (void *src)
{
    oststat_t *o1 = (oststat_t *) src;
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
    o->common.pct_cpu      = sample_copy (o1->common.pct_cpu);
    o->common.pct_mem      = sample_copy (o1->common.pct_mem);
    return (void *) o;
}

/* Match an OST or MDT target against a file system name.
 * Target names are assumed to be of the form fs-OSTxxxx or fs-MDTxxxx.
 * Careful of hyphen in file system name (see issue #50).
 */
static int
_fsmatch (char *name, char *fs)
{
    char *p = strrchr (name, '-');
    int len = p ? p - name : strlen (name);

    if (strlen (fs) == len && strncmp (name, fs, len) == 0)
        return 1;
    return 0;
}

/* Update oststat_t record (tgtstate field) in ost_data list for
 * specified ostname.  Create an entry if one doesn't exist.
 * FIXME(design): we only keep one OSC state per OST, but possibly multiple
 * MDT's are reporting it under CMD and last in wins.
 */
static void
_update_osc (char *name, char *state, List ost_data,
             time_t tnow, time_t trcv, int stale_secs)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, name))) {
        o = _create_oststat (name, stale_secs);
        list_append (ost_data, o);
    }
    if (tnow - trcv > stale_secs)
        strncpy (o->common.tgtstate, "", sizeof (o->common.tgtstate) - 1);
    else
        strncpy (o->common.tgtstate, state, sizeof (o->common.tgtstate) - 1);
}

static void
_decode_osc_v1 (char *val, char *fs, List ost_data,
             time_t tnow, time_t trcv, int stale_secs)
{
    char *s, *servername, *oscname, *tgtstate;
    List oscinfo;
    ListIterator itr;

    if (lmt_osc_decode_v1 (val, &servername, &oscinfo) < 0)
        return;
    itr = list_iterator_create (oscinfo);
    while ((s = list_next (itr))) {
        if (lmt_osc_decode_v1_oscinfo (s, &oscname, &tgtstate) >= 0) {
            if (!fs || _fsmatch (oscname, fs))
                _update_osc (oscname, tgtstate, ost_data,
                             tnow, trcv, stale_secs);
            free (oscname);
            free (tgtstate);
        }
    }
    list_iterator_destroy (itr);
    list_destroy (oscinfo);
    free (servername);
}

/* Update oststat_t record in ost_data list for specified ostname.
 * Create an entry if one doesn't exist.
 */
static void
_update_ost (char *ostname, char *servername, uint64_t read_bytes,
             uint64_t write_bytes, uint64_t iops, uint64_t num_exports,
             uint64_t lock_count, uint64_t grant_rate, uint64_t cancel_rate,
             uint64_t connect, char *recov_status, uint64_t kbytes_free,
             uint64_t kbytes_total, float pct_cpu, float pct_mem,
             List ost_data, time_t tnow, time_t trcv, int stale_secs)
{
    oststat_t *o;

    if (!(o = list_find_first (ost_data, (ListFindF)_match_oststat, ostname))) {
        o = _create_oststat (ostname, stale_secs);
        list_append (ost_data, o);
    }
    if (o->common.tgt_metric_timestamp < trcv) {
        if (strcmp (servername, o->common.servername) != 0) { /*failover/back*/
            sample_invalidate (o->rbytes);
            sample_invalidate (o->wbytes);
            sample_invalidate (o->iops);
            sample_invalidate (o->num_exports);
            sample_invalidate (o->lock_count);
            sample_invalidate (o->kbytes_free);
            sample_invalidate (o->kbytes_total);
            sample_invalidate (o->common.pct_cpu);
            sample_invalidate (o->common.pct_mem);
            snprintf (o->common.servername, sizeof (o->common.servername),
                      "%s", servername);
        }
        o->common.tgt_metric_timestamp = trcv;
        sample_update (o->rbytes, (double)read_bytes, trcv);
        sample_update (o->wbytes, (double)write_bytes, trcv);
        sample_update (o->iops, (double)iops, trcv);
        sample_update (o->num_exports, (double)num_exports, trcv);
        sample_update (o->lock_count, (double)lock_count, trcv);
        sample_update (o->grant_rate, (double)grant_rate, trcv);
        sample_update (o->cancel_rate, (double)cancel_rate, trcv);
        sample_update (o->connect, (double)connect, trcv);
        sample_update (o->kbytes_free, (double)kbytes_free, trcv);
        sample_update (o->kbytes_total, (double)kbytes_total, trcv);
        sample_update (o->common.pct_cpu, (double)pct_cpu, trcv);
        sample_update (o->common.pct_mem, (double)pct_mem, trcv);
        snprintf (o->common.recov_status, sizeof(o->common.recov_status),
                  "%s", recov_status);
    }
}

static void
_decode_ost_v2 (char *val, char *fs, List ost_data,
                time_t tnow, time_t trcv, int stale_secs)
{
    List ostinfo;
    char *s, *p, *servername, *ostname, *recov_status;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    ListIterator itr;

    if (lmt_ost_decode_v2 (val, &servername, &pct_cpu, &pct_mem, &ostinfo) < 0)
        return;
    /* Issue 53: drop domain name, if any */
    if ((p = strchr (servername, '.')))
        *p = '\0';
    itr = list_iterator_create (ostinfo);
    while ((s = list_next (itr))) {
        if (lmt_ost_decode_v2_ostinfo (s, &ostname,
                                       &read_bytes, &write_bytes,
                                       &kbytes_free, &kbytes_total,
                                       &inodes_free, &inodes_total, &iops,
                                       &num_exports, &lock_count,
                                       &grant_rate, &cancel_rate,
                                       &connect, &reconnect,
                                       &recov_status) == 0) {
            if (!fs || _fsmatch (ostname, fs)) {
                _update_ost (ostname, servername, read_bytes, write_bytes,
                             iops, num_exports, lock_count, grant_rate,
                             cancel_rate, connect + reconnect, recov_status,
                             kbytes_free, kbytes_total, pct_cpu, pct_mem,
                             ost_data, tnow, trcv, stale_secs);
            }
            free (ostname);
            free (recov_status);
        }
    }
    list_iterator_destroy (itr);
    list_destroy (ostinfo);
    free (servername);
}

/* Update mdtstat_t record in mdt_data list for specified mdtname.
 * Create an entry if one doesn't exist.
 */
static void
_update_mdt (char *mdtname, char *servername, uint64_t inodes_free,
             uint64_t inodes_total, uint64_t kbytes_free,
             uint64_t kbytes_total, float pct_cpu, float pct_mem,
             char *recov_status, List mdops, List mdt_data, time_t tnow,
             time_t trcv, int stale_secs, int version)
{
    char *opname, *s;
    ListIterator itr;
    mdtstat_t *m;
    uint64_t samples, sum, sumsquares;

    assert (version==1 || version==2);
    assert (version==1 ? recov_status==NULL : recov_status!=NULL );

    if (!(m = list_find_first (mdt_data, (ListFindF)_match_mdtstat, mdtname))) {
        m = _create_mdtstat (mdtname, stale_secs);
        list_append (mdt_data, m);
    }
    if (m->common.tgt_metric_timestamp < trcv) {
        if (strcmp (servername, m->common.servername) != 0) { /*failover/back*/
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
            sample_invalidate (m->common.pct_cpu);
            sample_invalidate (m->common.pct_mem);
            snprintf (m->common.servername, sizeof (m->common.servername),
                      "%s", servername);
            m->common.recov_status[0]='\0';
        }
        m->common.tgt_metric_timestamp = trcv;
        sample_update (m->inodes_free, (double)inodes_free, trcv);
        sample_update (m->inodes_total, (double)inodes_total, trcv);
        sample_update (m->common.pct_cpu, (double)pct_cpu, trcv);
        sample_update (m->common.pct_mem, (double)pct_mem, trcv);
        if (version==2)
            snprintf (m->common.recov_status, sizeof (m->common.recov_status),
                      "%s", recov_status);
        itr = list_iterator_create (mdops);
        while ((s = list_next (itr))) {
            if (lmt_mdt_decode_v1_mdops (s, &opname,
                                    &samples, &sum, &sumsquares) == 0) {
                if (!strcmp (opname, "open"))
                    sample_update (m->open, (double)samples, trcv);
                else if (!strcmp (opname, "close"))
                    sample_update (m->close, (double)samples, trcv);
                else if (!strcmp (opname, "getattr"))
                    sample_update (m->getattr, (double)samples, trcv);
                else if (!strcmp (opname, "setattr"))
                    sample_update (m->setattr, (double)samples, trcv);
                else if (!strcmp (opname, "link"))
                    sample_update (m->link, (double)samples, trcv);
                else if (!strcmp (opname, "unlink"))
                    sample_update (m->unlink, (double)samples, trcv);
                else if (!strcmp (opname, "mkdir"))
                    sample_update (m->mkdir, (double)samples, trcv);
                else if (!strcmp (opname, "rmdir"))
                    sample_update (m->rmdir, (double)samples, trcv);
                else if (!strcmp (opname, "statfs"))
                    sample_update (m->statfs, (double)samples, trcv);
                else if (!strcmp (opname, "rename"))
                    sample_update (m->rename, (double)samples, trcv);
                else if (!strcmp (opname, "getxattr"))
                    sample_update (m->getxattr, (double)samples, trcv);
                free (opname);
            }
        }
        list_iterator_destroy (itr);
    }
}

static void
_decode_mdt_v1 (char *val, char *fs, List mdt_data,
                time_t tnow, time_t trcv, int stale_secs)
{
    List mdops, mdtinfo;
    char *s, *servername, *mdtname;
    float pct_cpu, pct_mem;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    ListIterator itr;

    if (lmt_mdt_decode_v1_v2 (val, &servername, &pct_cpu, &pct_mem, &mdtinfo, 1) < 0)
        return;
    itr = list_iterator_create (mdtinfo);
    while ((s = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdtinfo (s, &mdtname, &inodes_free,
                                       &inodes_total, &kbytes_free,
                                       &kbytes_total, &mdops) == 0) {
            if (!fs || _fsmatch (mdtname, fs)) {
                _update_mdt (mdtname, servername, inodes_free, inodes_total,
                             kbytes_free, kbytes_total, pct_cpu, pct_mem,
                             NULL, mdops, mdt_data, tnow, trcv, stale_secs,
                             1);
            }
            free (mdtname);
            list_destroy (mdops);
        }
    }
    list_iterator_destroy (itr);
    list_destroy (mdtinfo);
    free (servername);
}

static void
_decode_mdt_v2 (char *val, char *fs, List mdt_data,
                time_t tnow, time_t trcv, int stale_secs)
{
    List mdops, mdtinfo;
    char *s, *mdsname, *mdtname;
    float pct_cpu, pct_mem;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    ListIterator itr;

    char *recov_info;

    if (lmt_mdt_decode_v1_v2 (val, &mdsname, &pct_cpu, &pct_mem, &mdtinfo, 2) < 0)
        return;
    itr = list_iterator_create (mdtinfo);
    while ((s = list_next (itr))) {
        if (lmt_mdt_decode_v2_mdtinfo (s, &mdtname, &inodes_free,
                                       &inodes_total, &kbytes_free,
                                       &kbytes_total, &recov_info,
                                       &mdops) == 0) {
            if (!fs || _fsmatch (mdtname, fs))
                _update_mdt (mdtname, mdsname, inodes_free, inodes_total,
                             kbytes_free, kbytes_total, pct_cpu, pct_mem,
                             recov_info, mdops, mdt_data, tnow, trcv,
                             stale_secs, 2);
            free (mdtname);
            list_destroy (mdops);
        }
    }
    list_iterator_destroy (itr);
    list_destroy (mdtinfo);
    free (mdsname);
}

static void
_poll_cerebro (char *fs, List mdt_data, List ost_data, int stale_secs,
               FILE *recf, time_t *tp)
{
    time_t trcv, tnow = time (NULL);
    cmetric_t c;
    List l = NULL;
    char *s, *name, *node;
    ListIterator itr;
    float vers;

#if ! HAVE_CEREBRO_H
    return;
#endif
    if (lmt_cbr_get_metrics ("lmt_mdt,lmt_ost,lmt_osc", &l) < 0)
        return;
    itr = list_iterator_create (l);
    while ((c = list_next (itr))) {
        if (!(name = lmt_cbr_get_name (c)))
            continue;
        if (!(node = lmt_cbr_get_nodename (c)))
            continue;
        if (!(s = lmt_cbr_get_val (c)))
            continue;
        if (sscanf (s, "%f;", &vers) != 1)
            continue;
        trcv = lmt_cbr_get_time (c);
        if (recf)
            _record_file (recf, tnow, trcv, node, name, s);
        if (!strcmp (name, "lmt_mdt") && vers == 1)
            _decode_mdt_v1 (s, fs, mdt_data, tnow, trcv, stale_secs);
        else if (!strcmp (name, "lmt_mdt") && vers == 2)
            _decode_mdt_v2 (s, fs, mdt_data, tnow, trcv, stale_secs);
        else if (!strcmp (name, "lmt_ost") && vers == 2)
            _decode_ost_v2 (s, fs, ost_data, tnow, trcv, stale_secs);
        else if (!strcmp (name, "lmt_osc") && vers == 1)
            _decode_osc_v1 (s, fs, ost_data, tnow, trcv, stale_secs);
    }
    list_iterator_destroy (itr);
    list_destroy (l);
    if (tp)
        *tp = tnow;
}

/* Write a cerebro metric record and some other info to a line in a file.
 * Ignore any errors, check with ferror () elsewhere.
 */
static void
_record_file (FILE *f, time_t tnow, time_t trcv, char *node,
              char *name, char *s)
{
    (void)fprintf (f, "%"PRIu64" %"PRIu64" %s %s %s\n",
                   (uint64_t)tnow, (uint64_t)trcv, node, name, s);
}

/* Create a (time, offset) tuple to be added to time_series List.
 */
static ts_t *
_ts_create (long p, uint64_t t)
{
    ts_t *ts = xmalloc (sizeof (ts_t));

    ts->p = p;
    ts->t = t;
    return ts;
}

/* Analagous to _poll_cerebro (), except input is taken from a file
 * written by _record_file ().   The wall clock time recorded in the first
 * field groups records into batches.  This function reads only one batch,
 * and places its wall clock time in *tp.
 */
static void
_play_file (char *fs, List mdt_data, List ost_data, List time_series,
            int stale_secs, FILE *f, time_t *tp, int *tdiffp)
{
    static char s[65536];
    float vers;
    uint64_t tnow, trcv, tmark = 0;
    char node[64], name[16];
    long pos = 0;
    int tdiff = 0;
    ts_t *ts = NULL;

    if (feof (f) || ferror (f))
        return;
    while (fscanf (f, "%"PRIu64" %"PRIu64" %64s %16s %65536[^\n]\n",
                   &tnow, &trcv, node, name, s) == 5) {
        if (tmark != 0 && tmark != tnow) {
            if (fseek (f, pos, SEEK_SET) < 0)
                err_exit ("fseek failed on playback file");
            tdiff = tnow - tmark;
            break;
        }
        if (sscanf (s, "%f;", &vers) != 1)
            msg_exit ("Parse error reading metric version in playback file");
        if (!strcmp (name, "lmt_mdt") && vers == 1)
            _decode_mdt_v1 (s, fs, mdt_data, tnow, trcv, stale_secs);
        if (!strcmp (name, "lmt_mdt") && vers == 2)
            _decode_mdt_v2 (s, fs, mdt_data, tnow, trcv, stale_secs);
        else if (!strcmp (name, "lmt_ost") && vers == 2)
            _decode_ost_v2 (s, fs, ost_data, tnow, trcv, stale_secs);
        else if (!strcmp (name, "lmt_osc") && vers == 1)
            _decode_osc_v1 (s, fs, ost_data, tnow, trcv, stale_secs);
        if ((pos = ftell (f)) < 0)
            err_exit ("ftell failed on playback file");
        if (!ts && time_series)
            ts = _ts_create (pos, tnow);
        tmark = tnow;
    }
    if (ferror (f))
        err_exit ("Error reading playback file");
    if (tmark == 0)
        msg_exit ("Error parsing playback file");
    if (tp)
        *tp = tmark;
    if (tdiffp && !feof (f))
        *tdiffp = tdiff;
    if (ts && time_series)
        list_prepend (time_series, ts); 
        
}

/* Seek to [count] batches of cerebro data ago.
 * (position at beginning of batch).
 */
static int
_rewind_file (FILE *f, List time_series, int count)
{
    ts_t *ts;
    int res = 0;

    while (count-- && (ts = list_dequeue (time_series))) {
        if (fseek (f, ts->p, SEEK_SET) < 0)
            err_exit ("fseek failed on playback file");
        res++;
        free (ts);
    }
    return res;
}

/* Seek to previous batch repeatedly until target time is reached.
 */
static void
_rewind_file_to (FILE *f, List time_series, time_t target)
{
    ts_t *ts;
    int found = 0;

    while (!found && (ts = list_dequeue (time_series))) {
        if (fseek (f, ts->p, SEEK_SET) < 0)
            err_exit ("fseek failed on playback file");
        if (ts->t <= target)
            found = 1;
        free (ts);
    }
}

/* Peek at the data to find a default file system to monitor.
 * Ignore file systems with no OSTs or no MDTs.
 */
static char *
_find_first_fs (FILE *playf, int stale_secs)
{
    List fsl = _find_all_fs (playf, stale_secs);
    ListIterator itr;
    fsstat_t *f;
    char *ret = NULL;

    itr = list_iterator_create (fsl);
    while (!ret && (f = list_next (itr))) {
        if (f->num_ost > 0 && f->num_mdt > 0)
            ret = xstrdup (f->fsname);
    }
    list_iterator_destroy (itr);
    list_destroy (fsl);

    return ret;
}

static void
_single_ost_update_summary (void *ost_v, void *summary_v)
{
    oststat_t *ost        = (oststat_t *) ost_v;
    oststat_t *summary    = (oststat_t *) summary_v;

    sample_add (summary->rbytes, ost->rbytes);
    sample_add (summary->wbytes, ost->wbytes);
    sample_add (summary->iops, ost->iops);
    sample_add (summary->kbytes_free, ost->kbytes_free);
    sample_add (summary->kbytes_total, ost->kbytes_total);
    sample_add (summary->lock_count, ost->lock_count);
    sample_add (summary->grant_rate, ost->grant_rate);
    sample_add (summary->cancel_rate, ost->cancel_rate);
    sample_add (summary->connect, ost->connect);

    /* Any "missing clients" on OST's should be reflected in OSS exp.
     */
    sample_min (summary->num_exports, ost->num_exports);
    
    if (ost->common.tag)
        summary->common.tag = ost->common.tag;
}

static void
_summarize_target (List target_data, List server_data, time_t tnow,
                   int stale_secs, _tgt_update_summary update_fn,
                   _copy_tgtstat copy_fn, ListFindF cmp_fn)
{
    generic_target_t *target, *server;
    ListIterator itr;

    _list_empty_out (server_data);
    itr = list_iterator_create (target_data);
    while ((target = list_next (itr))) {
        if (tnow - target->tgt_metric_timestamp > stale_secs)
            continue;
        server = list_find_first (server_data, (ListFindF) cmp_fn,
                                  target->servername);
        if (server) {
            update_fn( (void *) target, (void *) server );
            if (target->tgt_metric_timestamp < server->tgt_metric_timestamp)
                server->tgt_metric_timestamp = target->tgt_metric_timestamp;
            /* Ensure recov_status and tgtstate reflect any unrecovered or
             * non-full state of individual targets.  Last in wins.
             */
            if (strcmp (target->tgtstate, "F") != 0)
                memcpy (server->tgtstate, target->tgtstate,
                        sizeof (target->tgtstate));
            if (strncmp (target->recov_status, "COMPLETE", 8) != 0)
                memcpy (server->recov_status, target->recov_status,
                        sizeof (target->recov_status));
            /* Maintain target count in name field.
             */
            snprintf (server->name, sizeof (server->name), "(%d)",
                      (int)strtoul (server->name + 1, NULL, 10) + 1);
        } else {
            server = (generic_target_t *) copy_fn ((void *) target);
            snprintf (server->name, sizeof (server->name), "(%d)", 1);
            list_append (server_data, server);
        }
    }
    list_iterator_destroy (itr);
}

static void
_single_mdt_update_summary (void *mdt_v, void *summary_v)
{
    mdtstat_t *mdt        = (mdtstat_t *) mdt_v;
    mdtstat_t *summary    = (mdtstat_t *) summary_v;

    sample_add (summary->open, mdt->open);
    sample_add (summary->close, mdt->close);
    sample_add (summary->getattr, mdt->getattr);
    sample_add (summary->setattr, mdt->setattr);
    sample_add (summary->link, mdt->link);
    sample_add (summary->unlink, mdt->unlink);
    sample_add (summary->mkdir, mdt->mkdir);
    sample_add (summary->rmdir, mdt->rmdir);
    sample_add (summary->statfs, mdt->statfs);
    sample_add (summary->rename, mdt->rename);
    sample_add (summary->getxattr, mdt->getxattr);

    /* %cpu and %mem are per-server, and so the initial copy
     * made by _copy_mdtstat() is correct for the summarized
     * view with no further processing
     */
}

/* Re-create mds_data, one record per mds, with data aggregated from
 * the MDT's on that MDS.
 */
static void
_summarize_mdt (List mdt_data, List mds_data, time_t tnow, int stale_secs)
{
    _summarize_target (mdt_data, mds_data, tnow, stale_secs,
                       _single_mdt_update_summary, _copy_mdtstat,
                       (ListFindF)  _match_mdtstat2);
    list_sort (mds_data, (ListCmpF)_cmp_tgtstat_byserver);
}

/* Re-create oss_data, one record per oss, with data aggregated from
 * the OST's on that OSS.
 */
static void
_summarize_ost (List ost_data, List oss_data, time_t tnow, int stale_secs)
{
    _summarize_target (ost_data, oss_data, tnow, stale_secs,
                       _single_ost_update_summary, _copy_oststat,
                       (ListFindF)  _match_oststat2);
    list_sort (oss_data, (ListCmpF)_cmp_tgtstat_byserver);
}

/* Identify the record in c[] which should be used for
 * sorting the list of targets.  k is the character
 * entered by the user, sort_index identifies the
 * record used previously, and c[] includes the key
 * for each column along with a pointer to the appropriate
 * comparison function.
 * Logic for '<' and '>' assumes c[] is ordered appropriately.
 */
static int
_get_sort_index (char k, int sort_index, sort_t c[], int nc)
{
    int j;

    if (k == '<') {
        if (--sort_index < 0)
            sort_index = nc - 1;
    } else if (k == '>') {
        if (++sort_index == nc)
            sort_index = 0;
    } else if (k != 0) {
        for (j = 0; j < nc; j++) {
            if (c[j].k == k) {
                sort_index = j;
                break;
            }
        }
    }
    assert (sort_index >= 0 && sort_index < nc);
    return sort_index;
}

/* Sort the list of MDT's using the specified comparison function.
 * tnow required for comparisons that operate on samples.
 */
static void
_sort_tgtlist (List tgt_data, time_t tnow, ListCmpF comparison_function)
{
    sort_tnow = tnow;
    list_sort (tgt_data, comparison_function);
}

/* Helper for _list_empty_out ().
 */
static int
_list_find_all (void *x, void *key)
{
    return 1;
}

/* Utility function to destroy all elements of a List without
 * destroying the list itself.
 */
static void
_list_empty_out (List l)
{
    list_delete_all (l, (ListFindF)_list_find_all, NULL);
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
        o->common.tag = 0;
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
            o->common.tag = tagval;
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
            o->common.tag = !o->common.tag;
            break;
        }
    }
    list_iterator_destroy (itr);
    if (ost_data2 && o != NULL)
        _tag_ost_byoss (ost_data2, o->ossname, o->common.tag);
}


/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
