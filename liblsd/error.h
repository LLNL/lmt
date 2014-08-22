void err_init (char *p);
void err_fini (void);
void err_set_dest (char *dest);
char *err_get_dest (void);

void err_exit (const char *fmt, ...)
        __attribute__ ((format (printf, 1, 2)));
void err (const char *fmt, ...)
        __attribute__ ((format (printf, 1, 2)));
void errn_exit (int errnum, const char *fmt, ...)
        __attribute__ ((format (printf, 2, 3)));
void errn (int errnum, const char *fmt, ...)
        __attribute__ ((format (printf, 2, 3)));
void msg_exit (const char *fmt, ...)
        __attribute__ ((format (printf, 1, 2)));
void msg (const char *fmt, ...)
        __attribute__ ((format (printf, 1, 2)));

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
