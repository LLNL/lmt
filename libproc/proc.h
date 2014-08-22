typedef struct proc_ctx_struct *pctx_t;

pctx_t proc_create (const char *root);
void proc_destroy (pctx_t ctx);

int proc_open (pctx_t ctx, const char *path);

int proc_openf (pctx_t ctx, const char *fmt, ...)
                __attribute__ ((format (printf, 2, 3)));

void proc_close (pctx_t ctx);

int proc_scanf (pctx_t ctx, const char *path, const char *fmt, ...)
                __attribute__ ((format (scanf, 3, 4)));

int proc_gets (pctx_t ctx, const char *path, char *buf, int len);

int proc_eof (pctx_t ctx);

typedef enum {
    PROC_READDIR_NODIR = 1,
    PROC_READDIR_NOFILE = 2,
} proc_readdir_flag_t;
int proc_readdir (pctx_t ctx, proc_readdir_flag_t flag, char **namep);



/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

