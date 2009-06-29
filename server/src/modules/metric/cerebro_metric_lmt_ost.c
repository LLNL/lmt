/*********************************************************************************
 * Copyright (C) 2007, Lawrence Livermore National Security, LLC.
 * Copyright (c) 2007, The Regents of the University of California.
 * Produced at the Lawrence Livermore National Laboratory.
 * Written by C. Morrone, H. Wartens, P. Spencer, N. O'Neill, J. Long
 * UCRL-CODE-232438.
 * All rights reserved.
 *
 * This file is part of Lustre Monitoring Tools, version 2. 
 * For details, see http://sourceforge.net/projects/lmt/.
 *
 * Please also read Our Notice and GNU General Public License, available in the
 * COPYING file in the source distribution.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License (as published by the Free Software
 * Foundation) version 2, dated June 1991.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the terms and conditions of the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 ********************************************************************************/

#if HAVE_CONFIG_H
#include "config.h"
#endif /* HAVE_CONFIG_H */

#include <stdio.h>
#include <stdlib.h>
#if STDC_HEADERS
#include <string.h>
#endif /* STDC_HEADERS */
#include <errno.h>
#include <sys/utsname.h>
#include <unistd.h>
#if HAVE_PTHREAD_H
#include <pthread.h>
#endif /* HAVE_PTHREAD_H */
#include <dirent.h>

#include <cerebro.h>
#include <cerebro/cerebro_config.h>
#include <cerebro/cerebro_metric_module.h>
#include <cerebro/cerebro_constants.h>
#include <cerebro/cerebro_error.h>
#include "cerebro_metric_lmt_common.h"
#include "hash.h"

#define LMT_METRIC_OST_MODULE_NAME "lmt_ost"
#define LMT_METRIC_OST_NAME        "lmt_ost"

/*
 * lmt_metric_ost_send_message_function
 *
 * Stores pointer to function to send a message
 */
Cerebro_metric_send_message lmt_metric_ost_send_message_function = NULL;

int
lmt_metric_ost_hash_strcmp(const void *key1, const void *key2)
{
        return strcmp((char *) key1, (char *) key2);
}

void
lmt_metric_ost_hash_fs_freeitem(void *data)
{
        struct lmt_ost_thread *thread;

        if (data == NULL) {
                return;
        }
        thread = (struct lmt_ost_thread *) data;

        if (thread->tid != NULL) {
                free(thread->tid);
                thread->tid = NULL;
        }

        if (thread->arg != NULL) {
                if (thread->arg->hostname != NULL) {
                        free(thread->arg->hostname);
                        thread->arg->hostname = NULL;
                }
                if (thread->arg->ostpath != NULL) {
                        free(thread->arg->ostpath);
                        thread->arg->ostpath = NULL;
                }

                free(thread->arg);
                thread->arg = NULL;
        }

        free(data);
}

void
lmt_metric_ost_thread_destroy(struct lmt_ost_thread *thread)
{
        if (thread->tid != NULL) {
                free(thread->tid);
                thread->tid = NULL;
        }

        if (thread->arg != NULL) {
                if (thread->arg->hostname != NULL) {
                        free(thread->arg->hostname);
                        thread->arg->hostname = NULL;
                }
                if (thread->arg->ostpath != NULL) {
                        free(thread->arg->ostpath);
                        thread->arg->ostpath = NULL;
                }

                free(thread->arg);
                thread->arg = NULL;
        }
}

static int
lmt_metric_ost_thread_init(struct lmt_ost_thread *thread,
                           int hostnamesize,
                           int ostpathsize)
{
        int rc = 0;

        if (thread == NULL) {
                return CEREBRO_ERR_PARAMETERS;
        }
        memset(thread, 0, sizeof(struct lmt_ost_thread));

        thread->tid = malloc(sizeof(pthread_t));
        if (thread->tid == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(thread->tid, 0, sizeof(pthread_t));

        thread->arg = malloc(sizeof(struct lmt_ost_thread_arg));
        if (thread->arg == NULL) {
                rc = ENOMEM;
                goto err;
        }
        memset(thread->arg, 0, sizeof(struct lmt_ost_thread_arg));

        if (hostnamesize > 0) {
                thread->arg->hostnamesize = hostnamesize;
                thread->arg->hostname = malloc(sizeof(char) * hostnamesize);
                if (thread->arg->hostname == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
        }

        if (ostpathsize > 0) {
                thread->arg->ostpathsize = ostpathsize;
                thread->arg->ostpath = malloc(sizeof(char) * ostpathsize);
                if (thread->arg->ostpath == NULL) {
                        rc = ENOMEM;
                        goto err;
                }
        }

        /* Initialize the mutex */
        pthread_mutex_init(&thread->arg->mutex, NULL);
        thread->arg->running = 1;
        thread->arg->started = 0;

        return 0;

err:
        if (thread->tid != NULL) {
                free(thread->tid);
                thread->tid = NULL;
        }

        if (thread->arg != NULL) {
                if (thread->arg->hostname != NULL) {
                        free(thread->arg->hostname);
                        thread->arg->hostname = NULL;
                }
                if (thread->arg->ostpath != NULL) {
                        free(thread->arg->ostpath);
                        thread->arg->ostpath = NULL;
                }

                free(thread->arg);
                thread->arg = NULL;
        }

        return rc;
}

static char *
lmt_metric_ost_get_name(void)
{
	return LMT_METRIC_OST_NAME;
}

static int
lmt_metric_ost_get_value(unsigned int *metric_value_type,
			 unsigned int *metric_value_len,
			 void **metric_value)
{
        cerebro_err_debug("do nothing");
        return CEREBRO_ERR_INTERNAL;
}

int
_lmt_ost_metric_send_message(char* hostname, char* buf, int length)
{
        struct cerebrod_message *hb  = NULL;
        struct cerebrod_message_metric *hd = NULL;
        int rv = 0;

        hb = malloc(sizeof(struct cerebrod_message));
        if (hb == NULL) {
                rv = ENOMEM;
                goto cleanup;
        }
        memset(hb, 0, sizeof(struct cerebrod_message));
        hb->version = CEREBROD_MESSAGE_PROTOCOL_VERSION;
        strncpy(hb->nodename, hostname, CEREBRO_MAX_NODENAME_LEN);
        hb->metrics_len = 1;

        hb->metrics = malloc(sizeof(struct cerebrod_message_metric *) * hb->metrics_len);
        if (hb->metrics == NULL) {
                rv = ENOMEM;
                goto cleanup;
        }
        memset(hb->metrics, 0, sizeof(struct cerebrod_message_metric *) * hb->metrics_len);

        hd = malloc(sizeof(struct cerebrod_message_metric));
        if (hd == NULL) {
                rv = ENOMEM;
                goto cleanup;
        }
        memset(hd, 0, sizeof(struct cerebrod_message_metric));
        strncpy(hd->metric_name, LMT_METRIC_OST_NAME, CEREBRO_MAX_METRIC_NAME_LEN);

        hd->metric_value_type = CEREBRO_DATA_VALUE_TYPE_STRING;
        hd->metric_value_len = sizeof(char) * length;
        hd->metric_value = buf;
        hb->metrics[0] = hd;

        rv = (*lmt_metric_ost_send_message_function)(hb);
        if (rv < 0) {
                cerebro_err_debug("problem sending message.");
                goto cleanup;
        }

cleanup:

        if (hb != NULL) {
                if (hb->metrics != NULL) {
                        free(hb->metrics);
                        hb->metrics = NULL;
                }
                free(hb);
                hb = NULL;
        }

        if (hd != NULL) {
                free(hd);
                hd = NULL;
        }

        return rv;
}

void*
_lmt_ost_metric_collect(void* arg)
{
        struct lmt_ost_thread_arg *lmt_arg = NULL;
        char *buf = NULL, *tmp = NULL;
        char lmtbuf[LMT_BUFLEN];
        char proc_ost_stat_path[PATH_MAX];
        struct utsname uts;
        int buflen = 0, bufleft = 0;
        int rv = 0, tmplen = 0;

        if (arg == NULL) {
                return NULL;
        }
        lmt_arg = (struct lmt_ost_thread_arg *) arg;

        pthread_mutex_lock(&lmt_arg->mutex);
        lmt_arg->started = 1;
        pthread_mutex_unlock(&lmt_arg->mutex);

        buflen = CEREBRO_MAX_DATA_STRING_LEN / sizeof(char);
        buf = malloc(sizeof(char) * buflen);
        if (buf == NULL) {
                goto err;
        }
        
        /* Get OST hostname */
        errno = 0;
        if (uname(&uts) < 0) {
                cerebro_err_debug("problem retrieving hostname");
                goto err;
        }

        /* Get stats procfile name */
        tmplen = strlen(lmt_arg->ostpath) + strlen(LMT_PATH_SEPARATOR) + strlen(LMT_STATS_NAME);
        if (tmplen > PATH_MAX) {
                cerebro_err_debug("stats path too long");
                goto err;
        }
        tmp = proc_ost_stat_path;
        strcpy(tmp, lmt_arg->ostpath);
        tmp += strlen(lmt_arg->ostpath);
        strcpy(tmp, LMT_PATH_SEPARATOR);
        tmp += strlen(LMT_PATH_SEPARATOR);
        strcpy(tmp, LMT_STATS_NAME);

        while (1) {
                memset(buf, 0, buflen);
                memset(lmtbuf, 0, sizeof(char) * LMT_BUFLEN);
                tmp = buf;
                bufleft = buflen;

                /* Insert Protocol Version */
                rv = common_metric_lmt_strncpy(&tmp, LMT_OST_PROTOCOL_VERSION, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying protocol version to buffer");
                        goto cleanup;
                }
                
                /* Insert Separator */
                rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        goto cleanup;
                }
                
                /* Insert Hostname */
                rv = common_metric_lmt_strncpy(&tmp, uts.nodename, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying hostname to buffer");
                        goto cleanup;
                }
                
                /* Insert Separator */
                rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        goto cleanup;
                }

                /* Insert the UUID */
                rv = common_metric_lmt_get_uuid(lmt_arg->ostpath, lmtbuf, LMT_BUFLEN);
                if (rv != 0) {
                        cerebro_err_output("problem retrieving UUID");
                        goto err;
                }
                rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying UUID to buffer");
                        goto cleanup;
                }

                /* Insert Separator */
                rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        goto cleanup;
                }

                /* Get File Usage */
                rv = common_metric_lmt_get_file_usage(lmt_arg->ostpath, lmtbuf, LMT_BUFLEN);
                if (rv != 0) {
                        cerebro_err_output("problem retrieving File usage");
                        goto err;
                }
                rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying File usage to buffer");
                        goto cleanup;
                }

                /* Insert Separator */
                rv = common_metric_lmt_strncpy(&tmp, LMT_SEPARATOR, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying separator to buffer");
                        goto cleanup;
                }

                /* Get Bandwidth Usage */
                rv = common_metric_lmt_get_bandwidth_usage(proc_ost_stat_path, lmtbuf, LMT_BUFLEN);
                if (rv != 0) {
                        cerebro_err_output("problem retrieving Bandwidth usage");
                        goto err;
                }
                rv = common_metric_lmt_strncpy(&tmp, lmtbuf, &bufleft);
                if (rv != 0) {
                        cerebro_err_debug("problem copying Bandwidth usage to buffer");
                        goto cleanup;
                }
                                                                                                
                /* Send the metric message */
                rv = _lmt_ost_metric_send_message(uts.nodename, buf, buflen - bufleft + 1);
                if (rv != 0) {
                        cerebro_err_debug("problem sending ost message");
                        goto cleanup;
                }
cleanup:
                sleep(5);
        }

err:
        if (buf != NULL) {
                free(buf);
                buf = NULL;
        }

        /* Ensure that the parent thread will
         * know that this thread is not running
         * anymore */
        pthread_mutex_lock(&lmt_arg->mutex);
        lmt_arg->running = 0;
        pthread_mutex_unlock(&lmt_arg->mutex);
        return NULL;
}

/* 
 * lmt_ost_metric_thread
 *
 * Thread that will continually monitor the osts
 */
static void *
lmt_ost_metric_thread(void *arg)
{
        DIR *dirp;
        struct dirent *dp;
        hash_t thread_hash;
        struct lmt_ost_thread *thread = NULL, *tmpthread = NULL;
        int hostnamesize = 0, ostpathsize = 0;
        int numosts = 0, status = 0, rc = 0;
        char *ostpath = NULL;
        char *tmp = NULL;

        /* Allocate space for ostpath buffer.  This will be used
         * as a key to the hash */
        ostpath = malloc(sizeof(char) * (PATH_MAX+1));

        /* Create ost name to thread hash */
        thread_hash = hash_create(0,
                                 (hash_key_f) hash_key_string,
                                 lmt_metric_ost_hash_strcmp,
                                 lmt_metric_ost_hash_fs_freeitem);

        while (1) {
                numosts = common_metric_lmt_isost();
                if (numosts <= 0) {
                        /* cerebro_err_debug("no osts found."); */
                        sleep(5);
                        continue;
                }

                /* Open the ost directory */
                if ((dirp = opendir(LMT_OST_DIRNAME)) == NULL) {
                        cerebro_err_debug("couldn't open '%s'", LMT_OST_DIRNAME);
                        sleep(5);
                        continue;
                }

                /* Get the next ost directory */
                errno = 0;
                while ((dp = readdir(dirp)) != NULL) {
                        if (errno != 0) {
                                break;
                        }

                        if (strncmp(dp->d_name, ".", 2) == 0  ||
                            strncmp(dp->d_name, "..", 3) == 0 ||
                            dp->d_type != DT_DIR) {
                                errno = 0;
                                continue;
                        }

                        /* Initialize the ostpath which will be used as
                         * the key for the hash */
                        memset(ostpath, 0, sizeof(char) * (PATH_MAX+1));
                        tmp = ostpath;
                        strcpy(tmp, LMT_OST_DIRNAME);
                        tmp += strlen(LMT_OST_DIRNAME);
                        strcpy(tmp, LMT_PATH_SEPARATOR);
                        tmp += strlen(LMT_PATH_SEPARATOR);
                        strcpy(tmp, dp->d_name);

                        thread = hash_find(thread_hash, ostpath);
                        if (thread == NULL) {
                                /* Allocate memory for the thread */
                                thread = malloc(sizeof(struct lmt_ost_thread));
                                if (thread == NULL) {
                                        cerebro_err_debug("could not allocate space for ost thread.");
                                        break;
                                }
                                memset(thread, 0, sizeof(struct lmt_ost_thread));
                                hostnamesize = 0;
                                ostpathsize = strlen(LMT_OST_DIRNAME) + strlen(dp->d_name) + strlen(LMT_PATH_SEPARATOR) + 1;
                                rc = lmt_metric_ost_thread_init(thread, hostnamesize, ostpathsize);
                                if (rc != 0) {
                                        if (thread != NULL) {
                                                lmt_metric_ost_thread_destroy(thread);
                                                free(thread);
                                                thread = NULL;
                                        }
                                        break;
                                }
                                strncpy(thread->arg->ostpath, ostpath, ostpathsize);

                                /* Insert the thread into the hash table */
                                errno = 0;
                                hash_insert(thread_hash, ostpath, thread);
                                if (errno) {
                                        if (thread != NULL) {
                                                lmt_metric_ost_thread_destroy(thread);
                                                free(thread);
                                                thread = NULL;
                                        }
                                        cerebro_err_debug("could not insert ost into hash.");
                                        break;
                                }
                                
                                /* Start thread */
                                if (pthread_create(thread->tid,
                                                   NULL,
                                                   _lmt_ost_metric_collect,
                                                   thread->arg)) {
                                        cerebro_err_debug("problem creating thread.");
                                        break;
                                }
                        }
                        else {
                                /* Thread has been found.  Check to see if it has
                                 * completed its work. */
                                pthread_mutex_lock(&thread->arg->mutex);
                                if (thread->arg->started && !thread->arg->running) {
                                        pthread_join(*thread->tid, (void*) &status);

                                        /* Remove this ost from the hash table */
                                        errno = 0;
                                        tmpthread = hash_remove(thread_hash, ostpath);
                                }
                                pthread_mutex_unlock(&thread->arg->mutex);

                                /* Actually deallocate the memory setup for this thread */
                                if (tmpthread != NULL) {
                                        /* Under normal conditions tmpthread should
                                         * be the same address as thread.  */
                                        if (tmpthread == thread) {
                                                lmt_metric_ost_thread_destroy(tmpthread);
                                                free(tmpthread);
                                                thread = tmpthread = NULL;
                                        }
                                        /* Handle the unlikely case that the address for thread and
                                         * tmpthread differ */
                                        else {
                                                cerebro_err_debug("thread in hash not same as expected.");
                                                lmt_metric_ost_thread_destroy(tmpthread);
                                                free(tmpthread);
                                                tmpthread = NULL;

                                                /* Since there was no match we should probably also
                                                 * deallocate unknown ost thread */
                                                if (thread != NULL) {
                                                        lmt_metric_ost_thread_destroy(thread);
                                                        free(thread);
                                                        thread = NULL;
                                                }
                                        }
                                }
                        }

                        errno = 0;
                }
                closedir(dirp);
                sleep(5);
        }

        return NULL;
}

/*
 * lmt_metric_ost_get_thread
 *
 * lmt_metric_ost_get_thread function
 */
static Cerebro_metric_thread_pointer
lmt_metric_ost_get_thread(void)
{
        return &lmt_ost_metric_thread;
}

/*
 * lmt_metric_ost_send_message_function_pointer
 *
 * lmt metric ost module send_message_function_pointer function
 */
static int
lmt_metric_ost_send_message_function_pointer(Cerebro_metric_send_message function_pointer)
{
        if (function_pointer == NULL) {
                cerebro_err_debug("invalid function_pointer");
                return CEREBRO_ERR_PARAMETERS;
        }
        
        lmt_metric_ost_send_message_function = function_pointer;
        return 0;
}

#if WITH_STATIC_MODULES
struct cerebro_metric_module_info lmt_metric_ost_module_info =
#else  /* !WITH_STATIC_MODULES */
struct cerebro_metric_module_info metric_module_info =
#endif /* !WITH_STATIC_MODULES */
{
	LMT_METRIC_OST_MODULE_NAME,
	&common_metric_lmt_interface_version,
	&common_metric_lmt_setup_do_nothing,
	&common_metric_lmt_cleanup_do_nothing,
	&lmt_metric_ost_get_name,
	&common_metric_lmt_get_period_none,
	&common_metric_lmt_get_flags,
	&lmt_metric_ost_get_value,
	&common_metric_lmt_destroy_metric_value_do_nothing,
	&lmt_metric_ost_get_thread,
	&lmt_metric_ost_send_message_function_pointer,
};

/*
 * vi:tabstop=8 shiftwidth=8 expandtab
 */
