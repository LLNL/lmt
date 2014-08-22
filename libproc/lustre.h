int proc_lustre_ostlist (pctx_t ctx, List *lp);
int proc_lustre_mdtlist (pctx_t ctx, List *lp);
int proc_lustre_osclist (pctx_t ctx, List *lp);
int proc_lustre_mdt_exportlist (pctx_t ctx, char *name, List *lp);


int proc_lustre_files (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp);
int proc_lustre_kbytes (pctx_t ctx, char *name, uint64_t *fp, uint64_t *tp);

int proc_lustre_uuid (pctx_t ctx, char *name, char **uuidp);

int proc_lustre_oscinfo (pctx_t ctx, char *name, char **uuidp, char **statep);

int proc_lustre_num_exports (pctx_t ctx, char *name, uint64_t *np);

int proc_lustre_ldlm_lock_count (pctx_t ctx, char *name, uint64_t *np);

int proc_lustre_ldlm_grant_rate (pctx_t ctx, char *name, uint64_t *np);

int proc_lustre_ldlm_cancel_rate (pctx_t ctx, char *name, uint64_t *np);

typedef struct {
    char *key;
    char *val;
} shash_t;

typedef struct {
    uint64_t x;
    uint64_t yr;
    uint64_t yw;
} histent_t;

typedef struct {
    int bincount;
    histent_t *bin;
} histogram_t;

void histogram_destroy (histogram_t *h);

int proc_lustre_parsestat (hash_t stats, const char *key, uint64_t *countp,
                           uint64_t *minp, uint64_t *maxp,
                           uint64_t *sump, uint64_t *sumsqp);

int proc_lustre_hashstats (pctx_t ctx, char *name, hash_t *hp);

int proc_lustre_hashrecov (pctx_t ctx, char *name, hash_t *hp);

typedef enum {
    BRW_RPC, BRW_DISPAGES, BRW_DISBLOCKS, BRW_FRAG, BRW_FLIGHT, BRW_IOTIME,
    BRW_IOSIZE,
} brw_t;

int proc_lustre_brwstats (pctx_t ctx, char *name, brw_t t, histogram_t **histp);

int proc_lustre_lnet_newbytes (pctx_t ctx, uint64_t *valp);

int proc_lustre_lnet_routing_enabled (pctx_t ctx, int *valp);

int proc_fs_lustre_version (pctx_t ctx, int *major, int *minor, int *patch,
                            int *fix);

/* lifted from lustre/include/lustre/lustre_idl.h */
#define PACKED_VERSION(major,minor,patch,fix) (((major)<<24) + ((minor)<<16) +\
                                               ((patch)<<8) + (fix))
#define PACKED_VERSION_MAJOR(version) ((int)((version)>>24)&255)
#define PACKED_VERSION_MINOR(version) ((int)((version)>>16)&255)
#define PACKED_VERSION_PATCH(version) ((int)((version)>>8)&255)
#define PACKED_VERSION_FIX(version) ((int)(version)&255)

#define LUSTRE_1_8 PACKED_VERSION(1,8,0,0)
#define LUSTRE_2_0 PACKED_VERSION(2,0,0,0)

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

