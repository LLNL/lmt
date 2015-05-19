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

typedef struct {
    char fsname[17];            /* file system name */
    char name[17];              /* target index (4 hex digits) */
    char oscstate[2];           /* single char state (blank if unknown) */
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
    sample_t pct_cpu;
    sample_t pct_mem;
    char recov_status[RECOVERY_STR_SIZE];      /* free form string representing recov status */
    time_t ost_metric_timestamp;/* cerebro timestamp for ost metric (not osc) */
    char ossname[MAXHOSTNAMELEN];/* oss hostname */
    int tag;                    /* display this ost line underlined */
} oststat_t;

typedef struct {
    char fsname[17];            /* file system name */
    char name[17];              /* target index (4 hex digitis) */
    char recov_status[RECOVERY_STR_SIZE];      /* free form string representing recov status */
    char tgtstate[2];           /* single char state (blank if unknown) */
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
    time_t mdt_metric_timestamp;/* cerebro timestamp for mdt metric */
    char mdsname[MAXHOSTNAMELEN];/* mds hostname */
} mdtstat_t;

typedef struct {
    long p;                     /* file offset */
    uint64_t t;                 /* time stamp */
} ts_t;

typedef struct {
    char     fsname[17];       /* file system name */
    uint64_t num_ost;          /* number of OSTs */
} fsstat_t;

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
static void _update_display_ost (WINDOW *win, List ost_data, int minost,
                                 int selost, int stale_secs, time_t tnow,
                                 int i);
static void _destroy_oststat (oststat_t *o);
static int _fsmatch (char *name, char *fs);
static void _destroy_mdtstat (mdtstat_t *m);
static void _summarize_ost (List ost_data, List oss_data, time_t tnow,
                            int stale_secs);
static void _clear_tags (List ost_data);
static void _tag_nth_ost (List ost_data, int selost, List ost_data2);
static void _sort_ostlist (List ost_data, time_t tnow, char k, int *ip);
static char *_find_first_fs (FILE *playf, int stale_secs);
static List _find_all_fs (FILE *playf, int stale_secs);
static void _record_file (FILE *f, time_t tnow, time_t trcv, char *node,
                          char *name, char *s);
static int _rewind_file (FILE *f, List time_series, int count);
static void _rewind_file_to (FILE *f, List time_series, time_t target);
static void _list_empty_out (List l);

/* Hardwired display geometry.  We also assume 80 chars wide.
 */
#define TOPWIN_LINES    7       /* lines in topwin */
#define OSTWIN_H_LINES  1       /* header lines in ostwin */
#define HDRLINES    (TOPWIN_LINES + OSTWIN_H_LINES)

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
 * _sort_ostlist () to pass the current time to its various sorting
 * functions.
 */
static time_t sort_tnow = 0;

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

int
main (int argc, char *argv[])
{
    int c;
    WINDOW *topwin, *ostwin;
    int ostcount, selost = -1, minost = 0;
    int ostview = 1, resort = 0, recompute = 0, repoll = 0;
    char *fs = NULL, *newfs;
    int sopt = 0;
    int sample_period = 2; /* seconds */
    int stale_secs = 12; /* seconds */
    List ost_data = list_create ((ListDelF)_destroy_oststat);
    List mdt_data = list_create ((ListDelF)_destroy_mdtstat);
    List oss_data = list_create ((ListDelF)_destroy_oststat);
    List time_series = list_create ((ListDelF)free);
    time_t tcycle, last_sample = 0;
    char *recpath = "ltop.log";
    FILE *recf = NULL;
    FILE *playf = NULL;
    int pause = 0;
    int showhelp = 0;
    int ost_fp = 0;

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
    _sort_ostlist (ost_data, tcycle, 0, &ost_fp);
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
    if (!(ostwin = newwin (ostcount + 1, 80, TOPWIN_LINES, 0)))
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
        if (showhelp) {
            _update_display_help (topwin);
        } else {
            _update_display_top (topwin, fs, ost_data, mdt_data, stale_secs,
                                 recf, playf, tcycle, pause);
            _update_display_ost (ostwin, ostview ? ost_data : oss_data,
                                 minost, selost, stale_secs, tcycle, ost_fp);
        }
        switch ((c = getch ())) {
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
                recompute = 1;
                minost = 0;
                selost = -1;
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
                    }
                    free (newfs);
                }
                break;
            case '>':               /* change sorting column (ost/oss) */
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
                _sort_ostlist (ost_data, tcycle, c, &ost_fp); 
                _sort_ostlist (oss_data, tcycle, 0, &ost_fp); 
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
            ostcount = list_count (ostview ? ost_data : oss_data);
            delwin (ostwin);
            if (!(ostwin = newwin (ostcount + 1, 80, TOPWIN_LINES, 0)))
                err_exit ("error initializing subwindow");
            if (!ostview)
                _summarize_ost (ost_data, oss_data, tcycle, stale_secs);
            resort = 1;
            recompute = 0;
        }
        if (resort) {
            _sort_ostlist (ost_data, tcycle, 0, &ost_fp); 
            _sort_ostlist (oss_data, tcycle, 0, &ost_fp); 
            resort = 0;
        }
    }

    list_destroy (ost_data);
    list_destroy (mdt_data);
    list_destroy (oss_data);
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
 */
static void _update_display_help (WINDOW *win)
{
    int y = 0;
    wclear (win);
    wattron (win, A_REVERSE);
    mvwprintw (win, y++, 0,
              "Help for Interactive Commands - ltop version %s-%s",
               META_VERSION, META_RELEASE);
    wattroff (win, A_REVERSE);
    y++;
    mvwprintw (win, y++, 2, "PageUp     Page up through OST information");
    mvwprintw (win, y++, 2, "PageDown   Page down through OST information");
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
    mvwprintw (win, y++, 2, "c          Toggle OST/OSS view");
    mvwprintw (win, y++, 2, "f          Select filesystem to monitor");
    mvwprintw (win, y++, 2, ">          Sort on next right column");
    mvwprintw (win, y++, 2, "<          Sort on next left column");
    mvwprintw (win, y++, 2, "s          Sort on OSS name (ascending)");
    mvwprintw (win, y++, 2, "x          Sort on export count (ascending)");
    mvwprintw (win, y++, 2, "C          Sort on connect rate (descending)");
    mvwprintw (win, y++, 2, "r          Sort on read b/w (descending)");
    mvwprintw (win, y++, 2, "w          Sort on write b/w (descending)");
    mvwprintw (win, y++, 2, "i          Sort on IOPS (descending)");
    mvwprintw (win, y++, 2, "l          Sort on lock count (descending)");
    mvwprintw (win, y++, 2, "g          Sort on lock grant rate (descending)");
    mvwprintw (win, y++, 2, "L          Sort on lock cancellation rate (descending)");
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
     * Recovery status fits between filesystem name and indicators like "RECORDING"
     * The former takes up 12+strlen(fs) columns
     * The indicators are displayed starting at column 68
     * Some blank spaces on either side are desirable.
     */
    recov_status_len = 68 - (12 + strlen(fs) + 4);

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
        if (m->recov_status && strstr(m->recov_status,"RECOV")) {
            /*
             * Multiple MDTs may be in recovery, but display room is
             * limited.  We print the full status of the first MDT in recovery
             * we find.  This allows the user to tell whether the count of
             * reconnected clients is increasing.
             */
            if (recovery_status[0] == '\0')
                snprintf(recovery_status, recov_status_len, "MDT%s %s",
                         m->name, m->recov_status);
       }

        if (m->mdt_metric_timestamp > trcv)
            trcv = m->mdt_metric_timestamp;
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
_create_fsstat (char *fsname, uint64_t osts)
{
    fsstat_t *f = xmalloc (sizeof (*f));
    memset (f, 0, sizeof (*f));
    strncpy (f->fsname, fsname, sizeof (f->fsname) - 1);
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
    mvwprintw (win, y++, 0, "NAME              OSTs");
    wattroff (win, A_REVERSE);

    for (; (f = list_next (fsitr)); y++) {
        if (y - hdr_rows == selfs)
            wattron (win, A_UNDERLINE);
        mvwprintw (win, y, 0, "%-17s %4d", f->fsname, f->num_ost);
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
        if (!(f = list_find_first (fsl, (ListFindF)_match_fsstat, m->fsname)))
            list_append (fsl, _create_fsstat (m->fsname, 0));
    }
    list_iterator_destroy (itr);
    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (!(f = list_find_first (fsl, (ListFindF)_match_fsstat, o->fsname)))
            list_append (fsl, _create_fsstat (o->fsname, 1));
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

/* Update the ost window of the display.
 * Minost is the first ost to display (zero origin).
 * Selost is the selected ost, or -1 if none are selected (zero origin).
 * Stale_secs is the number of seconds after which data is expried.
 * i is the column currently used for sorting (zero origin).
 */
static void
_update_display_ost (WINDOW *win, List ost_data, int minost, int selost,
                     int stale_secs, time_t tnow, int i)
{
    ListIterator itr;
    oststat_t *o;
    int y = 0;
    int skipost = minost;

    wclear (win);
    wmove (win, y++, 0);

    wattron (win, A_REVERSE);
    wprintw (win, "%sOST",        i == 0  ? ">" : " ");
    wprintw (win, "%sS",          i == 1  ? ">" : " ");
    wprintw (win, "       %sOSS", i == 2  ? ">" : " ");
    wprintw (win, "  %sExp",      i == 3  ? ">" : " ");
    wprintw (win, "  %sCR",       i == 4  ? ">" : " ");
    wprintw (win, "%srMB/s",      i == 5  ? ">" : " ");
    wprintw (win, "%swMB/s",      i == 6  ? ">" : " ");
    wprintw (win, " %sIOPS",      i == 7  ? ">" : " ");
    wprintw (win, "  %sLOCKS",    i == 8  ? ">" : " ");
    wprintw (win, " %sLGR",       i == 9  ? ">" : " ");
    wprintw (win, " %sLCR",       i == 10 ? ">" : " ");
    wprintw (win, "%s%%cpu",      i == 11 ? ">" : " ");
    wprintw (win, "%s%%mem",      i == 12 ? ">" : " ");
    wprintw (win, "%s%%spc",      i == 13 ? ">" : " ");
    wattroff(win, A_REVERSE);

    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (skipost-- > 0)
            continue;
        if (y - 1 + minost == selost)
            wattron (win, A_REVERSE);
        if (o->tag)
            wattron (win, A_UNDERLINE);
        /* available info is expired */
        if ((tnow - o->ost_metric_timestamp) > stale_secs) {
            mvwprintw (win, y, 0, "%4.4s %1.1s", o->name, o->oscstate);
        /* ost is in recovery - display recovery stats */
        } else if (strncmp (o->recov_status, "COMPLETE", 8) != 0
                && strncmp (o->recov_status, "INACTIVE", 8) != 0) {
            mvwprintw (win, y, 0, "%4.4s %1.1s %10.10s   %s",
                       o->name, o->oscstate, _ltrunc (o->ossname, 10),
                       o->recov_status);
        /* ost is not in recover (state == INACTIVE|COMPLETE) */
        } else {
            double ktot = sample_val (o->kbytes_total, tnow);
            double kfree = sample_val (o->kbytes_free, tnow);
            double pct_used = ktot > 0 ? ((ktot - kfree) / ktot)*100.0 : 0;

            mvwprintw (win, y, 0, "%4.4s %1.1s %10.10s"
                       " %5.0f %4.0f %5.0f %5.0f %5.0f %7.0f %4.0f %4.0f"
                       " %4.0f %4.0f %4.0f",
                       o->name, o->oscstate, _ltrunc (o->ossname, 10),
                       sample_val (o->num_exports, tnow),
                       sample_rate (o->connect, tnow),
                       sample_rate (o->rbytes, tnow) / (1024*1024),
                       sample_rate (o->wbytes, tnow) / (1024*1024),
                       sample_rate (o->iops, tnow),
                       sample_val (o->lock_count, tnow),
                       sample_val (o->grant_rate, tnow),
                       sample_val (o->cancel_rate, tnow),
                       sample_val (o->pct_cpu, tnow),
                       sample_val (o->pct_mem, tnow),
                       pct_used);
        }
        if (y - 1 + minost == selost)
            wattroff(win, A_REVERSE);
        if (o->tag)
            wattroff(win, A_UNDERLINE);
        y++;
    }
    list_iterator_destroy (itr);

    wrefresh (win);
}

/*  Used for list_find_first () of MDT by filesystem and target name, e.g. fs-MDTxxxx.
 */
static int
_match_mdtstat (mdtstat_t *m, char *name)
{
    int targetmatch;
    int fsmatch;
    char *p = strstr (name, "-MDT");

    fsmatch = _fsmatch(name, m->fsname);
    targetmatch = (strcmp (m->name, p ? p + 4 : name) == 0);

    return (targetmatch && fsmatch);
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
    strncpy (m->fsname, name, mdtx ? mdtx - name : sizeof (m->fsname) - 1);
    *m->tgtstate = '\0';
    *m->recov_status='\0';
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
    int targetmatch;
    int fsmatch;
    char *p = strstr (name, "-OST");

    fsmatch = _fsmatch(name, o->fsname);
    targetmatch = (strcmp (o->name, p ? p + 4 : name) == 0);

    return (targetmatch && fsmatch);
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

/* Used for list_sort () of OST list by pct_mem (descending order).
 */
static int
_cmp_oststat_bymem (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_val_cmp (o1->pct_mem, o2->pct_mem, sort_tnow);
}

/* Used for list_sort () of OST list by pct_cpu (descending order).
 */
static int
_cmp_oststat_bycpu (oststat_t *o1, oststat_t *o2)
{
    return -1 * sample_val_cmp (o1->pct_cpu, o2->pct_cpu, sort_tnow);
}

/* Used for no-op list_sort () of OST list.
 */
static int
_cmp_oststat_noop (oststat_t *o1, oststat_t *o2)
{
    return 0;
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

/* Create an oststat record.
 */
static oststat_t *
_create_oststat (char *name, int stale_secs)
{
    oststat_t *o = xmalloc (sizeof (*o));
    char *ostx = strstr (name, "-OST");

    memset (o, 0, sizeof (*o));
    strncpy (o->name, ostx ? ostx + 4 : name, sizeof(o->name) - 1);
    strncpy (o->fsname, name, ostx ? ostx - name : sizeof (o->fsname) - 1);
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
    o->pct_cpu      = sample_create (stale_secs);
    o->pct_mem      = sample_create (stale_secs);
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
    sample_destroy (o->pct_cpu);
    sample_destroy (o->pct_mem);
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
    o->pct_cpu      = sample_copy (o1->pct_cpu);
    o->pct_mem      = sample_copy (o1->pct_mem);
    return o;
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

/* Update oststat_t record (oscstate field) in ost_data list for
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
        strncpy (o->oscstate, "", sizeof (o->oscstate) - 1);
    else    
        strncpy (o->oscstate, state, sizeof (o->oscstate) - 1);
}

static void
_decode_osc_v1 (char *val, char *fs, List ost_data,
             time_t tnow, time_t trcv, int stale_secs)
{
    char *s, *mdsname, *oscname, *oscstate;
    List oscinfo;
    ListIterator itr;

    if (lmt_osc_decode_v1 (val, &mdsname, &oscinfo) < 0)
        return;
    itr = list_iterator_create (oscinfo);
    while ((s = list_next (itr))) {
        if (lmt_osc_decode_v1_oscinfo (s, &oscname, &oscstate) >= 0) {
            if (!fs || _fsmatch (oscname, fs))
                _update_osc (oscname, oscstate, ost_data,
                             tnow, trcv, stale_secs);
            free (oscname);
            free (oscstate);
        }
    }
    list_iterator_destroy (itr);
    list_destroy (oscinfo);
    free (mdsname);
}

/* Update oststat_t record in ost_data list for specified ostname.
 * Create an entry if one doesn't exist.
 */
static void
_update_ost (char *ostname, char *ossname, uint64_t read_bytes,
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
    if (o->ost_metric_timestamp < trcv) {
        if (strcmp (ossname, o->ossname) != 0) { /* failover/failback */
            sample_invalidate (o->rbytes);
            sample_invalidate (o->wbytes);
            sample_invalidate (o->iops);
            sample_invalidate (o->num_exports);
            sample_invalidate (o->lock_count);
            sample_invalidate (o->kbytes_free);
            sample_invalidate (o->kbytes_total);
            sample_invalidate (o->pct_cpu);
            sample_invalidate (o->pct_mem);
            snprintf (o->ossname, sizeof (o->ossname), "%s", ossname);
        }
        o->ost_metric_timestamp = trcv;
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
        sample_update (o->pct_cpu, (double)pct_cpu, trcv);
        sample_update (o->pct_mem, (double)pct_mem, trcv);
        snprintf (o->recov_status, sizeof(o->recov_status), "%s", recov_status);
    }
}

static void
_decode_ost_v2 (char *val, char *fs, List ost_data,
                time_t tnow, time_t trcv, int stale_secs)
{
    List ostinfo;
    char *s, *p, *ossname, *ostname, *recov_status;
    float pct_cpu, pct_mem;
    uint64_t read_bytes, write_bytes;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    uint64_t iops, num_exports;
    uint64_t lock_count, grant_rate, cancel_rate;
    uint64_t connect, reconnect;
    ListIterator itr;
    
    if (lmt_ost_decode_v2 (val, &ossname, &pct_cpu, &pct_mem, &ostinfo) < 0)
        return;
    /* Issue 53: drop domain name, if any */
    if ((p = strchr (ossname, '.')))
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
                _update_ost (ostname, ossname, read_bytes, write_bytes,
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
    free (ossname);
}

/* Update mdtstat_t record in mdt_data list for specified mdtname.
 * Create an entry if one doesn't exist.
 */
static void
_update_mdt (char *mdtname, char *mdsname, uint64_t inodes_free,
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
    if (m->mdt_metric_timestamp < trcv) {
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
            m->recov_status[0]='\0';
        }
        m->mdt_metric_timestamp = trcv;
        sample_update (m->inodes_free, (double)inodes_free, trcv);
        sample_update (m->inodes_total, (double)inodes_total, trcv);
        if (version==2)
            snprintf (m->recov_status, sizeof (m->recov_status), "%s", recov_status);
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
    char *s, *mdsname, *mdtname;
    float pct_cpu, pct_mem;
    uint64_t kbytes_free, kbytes_total;
    uint64_t inodes_free, inodes_total;
    ListIterator itr;

    if (lmt_mdt_decode_v1_v2 (val, &mdsname, &pct_cpu, &pct_mem, &mdtinfo, 1) < 0)
        return;
    itr = list_iterator_create (mdtinfo);
    while ((s = list_next (itr))) {
        if (lmt_mdt_decode_v1_mdtinfo (s, &mdtname, &inodes_free,
                                       &inodes_total, &kbytes_free,
                                       &kbytes_total, &mdops) == 0) {
            if (!fs || _fsmatch (mdtname, fs)) {
                _update_mdt (mdtname, mdsname, inodes_free, inodes_total,
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
    free (mdsname);
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
                             recov_info, mdops, mdt_data, tnow, trcv, stale_secs,
                             2);
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
 * Ignore file systems with no OSTs.
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
        if (f->num_ost > 0)
            ret = xstrdup (f->fsname);
    }
    list_iterator_destroy (itr);
    list_destroy (fsl);

    return ret;
}

/* Re-create oss_data, one record per oss, with data aggregated from
 * the OST's on that OSS.
 */
static void
_summarize_ost (List ost_data, List oss_data, time_t tnow, int stale_secs)
{
    oststat_t *o, *o2;
    ListIterator itr;

    _list_empty_out (oss_data);
    itr = list_iterator_create (ost_data);
    while ((o = list_next (itr))) {
        if (tnow - o->ost_metric_timestamp > stale_secs)
            continue;
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
            if (o->ost_metric_timestamp < o2->ost_metric_timestamp)
                o2->ost_metric_timestamp = o->ost_metric_timestamp;
            /* Ensure recov_status and oscstate reflect any unrecovered or
             * non-full state of individual OSTs.  Last in wins.
             */
            if (strcmp (o->oscstate, "F") != 0)
                memcpy (o2->oscstate, o->oscstate, sizeof (o->oscstate));
            if (strncmp (o->recov_status, "COMPLETE", 8) != 0)
                memcpy (o2->recov_status, o->recov_status,
                        sizeof (o->recov_status));
            /* Any "missing clients" on OST's should be reflected in OSS exp.
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

typedef struct {
    ListCmpF fun;
    char k;
} sort_t;

/* Sort the list of OST's according to the specified key (k).
 */
static void
_sort_ostlist (List ost_data, time_t tnow, char k, int *ip)
{
    sort_t c[] = { /* order affects - see _update_display_ost () */
        { .fun = (ListCmpF)_cmp_oststat_byost,   .k = 't' },
        { .fun = (ListCmpF)_cmp_oststat_noop,    .k =  0  }, /* no-op */
        { .fun = (ListCmpF)_cmp_oststat_byoss,   .k = 's' },
        { .fun = (ListCmpF)_cmp_oststat_byexp,   .k = 'x' },
        { .fun = (ListCmpF)_cmp_oststat_byconn,  .k = 'C' },
        { .fun = (ListCmpF)_cmp_oststat_byrbw,   .k = 'r' },
        { .fun = (ListCmpF)_cmp_oststat_bywbw,   .k = 'w' },
        { .fun = (ListCmpF)_cmp_oststat_byiops,  .k = 'i' },
        { .fun = (ListCmpF)_cmp_oststat_bylocks, .k = 'l' },
        { .fun = (ListCmpF)_cmp_oststat_bylgr,   .k = 'g' },
        { .fun = (ListCmpF)_cmp_oststat_bylcr,   .k = 'L' },
        { .fun = (ListCmpF)_cmp_oststat_bycpu,   .k = 'u' },
        { .fun = (ListCmpF)_cmp_oststat_bymem,   .k = 'm' },
        { .fun = (ListCmpF)_cmp_oststat_byspc,   .k = 'S' },
    };
    int i = *ip, j, nc = sizeof (c) / sizeof (c[0]);

    if (k == '<') {
        if (--i < 0)
            i = nc - 1;
    } else if (k == '>') {
        if (++i == nc)
            i = 0;
    } else if (k != 0) {
        for (j = 0; j < nc; j++) {
            if (c[j].k == k) {
                i = j;
                break;
            }
        }
    }
    assert (i >= 0 && i < nc);
 
    sort_tnow = tnow;
    list_sort (ost_data, c[i].fun);
    *ip = i;
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

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
