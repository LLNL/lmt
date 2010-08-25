/*****************************************************************************
 *  Copyright (C) 2007-2010 Lawrence Livermore National Security, LLC.
 *  This module written by Jim Garlick <garlick@llnl.gov>.
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
#include <assert.h>
#include <cerebro.h>

#include "list.h"
#include "util.h"
#include "error.h"

#include "lmtcerebro.h"
#include "lmtconf.h"

struct cmetric_struct {
    char *nodename;
    char *name;
    unsigned int time;
    unsigned int type;
    unsigned int size;
    void *value;
};

static void
_destroy_cmetric (cmetric_t c)
{
    if (c->name)
        free (c->name);
    if (c->value)
        free (c->value);
    if (c->nodename)
        free (c->nodename);
    free (c); 
}

static cmetric_t 
_create_cmetric (char *name, char *nodename, unsigned int time,
                 unsigned int type, unsigned int size, void *value)
{
    cmetric_t c = xmalloc (sizeof (*c));
    memset (c, 0, sizeof (*c));
    c->name = xstrdup (name);
    c->nodename = xstrdup (nodename);
    c->time = time;
    c->type = type;
    c->size = size;
    c->value = xmalloc (size);
    memcpy (c->value, value, size);

    return c;
}

char *
lmt_cbr_get_val (cmetric_t c)
{
    if (c->type != CEREBRO_DATA_VALUE_TYPE_STRING || !c->value)
        return NULL;
    return c->value;
}

char *
lmt_cbr_get_name (cmetric_t c)
{
    return c->name;
}

time_t
lmt_cbr_get_time (cmetric_t c)
{
    return (time_t)c->time;
}

static int
_get_metric_data (cerebro_t ch, char *name, List rl)
{
    int retval; 
    cerebro_nodelist_t n = NULL;
    cerebro_nodelist_iterator_t nitr;
    cmetric_t c;
    unsigned int time, type, size;
    void *value;
    char *nodename;

    if (!(n = cerebro_get_metric_data (ch, name))) {
        if (lmt_conf_get_cbr_debug ())
            msg ("error getting metric data for %s: %s",
                 name, cerebro_strerror (cerebro_errnum (ch)));
        goto done;
    }
    if (!(nitr = cerebro_nodelist_iterator_create (n))) {
        if (lmt_conf_get_cbr_debug ())
            msg ("error creating nodelist iterator for %s metric data: %s",
                 name, cerebro_strerror (cerebro_errnum (ch)));
        goto done;
    }
    while (!cerebro_nodelist_iterator_at_end (nitr)) {
        if (cerebro_nodelist_iterator_nodename (nitr, &nodename) < 0 ||
            cerebro_nodelist_iterator_metric_value (nitr,
                                       &time, &type, &size, &value) < 0) {
            if (lmt_conf_get_cbr_debug ())
                msg ("error retrieving metric value for %s: %s", name,
                   cerebro_strerror (cerebro_nodelist_iterator_errnum (nitr)));
            goto done;
        }
        c = _create_cmetric (name, nodename, time, type, size, value);
        if (!(list_append (rl, c))) {
            _destroy_cmetric (c);
            goto done;
        }
        if (cerebro_nodelist_iterator_next (nitr) < 0) {
            if (lmt_conf_get_cbr_debug ())
                msg ("error iterating on metric %s: %s", name,
                   cerebro_strerror (cerebro_nodelist_iterator_errnum (nitr)));
            goto done;
        }
    }
    retval = 0;
done:
    if (n)
        cerebro_nodelist_destroy (n); /* side effect: destroys nitr */
    return retval;
}

int
lmt_cbr_get_metrics (char *names, List *rlp)
{
    int retval = -1;
    List rl = NULL;
    List nl = NULL;
    ListIterator itr = NULL;
    cerebro_t ch = NULL;
    char *name;

    if (!(ch = cerebro_handle_create()))
        goto done;
    nl = list_tok (names, ",");
    rl = list_create ((ListDelF)_destroy_cmetric);
    itr = list_iterator_create (nl);
    while ((name = list_next (itr))) {
        if (_get_metric_data (ch, name, rl) < 0)
            goto done;
    }
    *rlp = rl;
    retval = 0;
done:    
    if (itr)
        list_iterator_destroy (itr);
    if (retval < 0 && rl)
        list_destroy (rl);
    if (nl)
        list_destroy (nl);
    if (ch)
        cerebro_handle_destroy (ch);
    return retval;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
