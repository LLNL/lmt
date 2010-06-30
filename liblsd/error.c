#if HAVE_CONFIG_H
#include "config.h"
#endif
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>

void
lsd_fatal_error (char *file, int line, char *mesg)
{
    fprintf (stderr, "fatal error: %s: %s::%d", mesg, file, line);
    exit (1);
}

void *
lsd_nomem_error (char *file, int line, char *mesg)
{
    fprintf (stderr, "out of memory: %s: %s::%d", mesg, file, line);
    errno = ENOMEM;
    return NULL;
}

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

