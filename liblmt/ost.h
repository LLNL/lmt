int lmt_ost_string_v2 (pctx_t ctx, char *s, int len);

int lmt_ost_decode_v2 (const char *s, char **ossnamep,
                        float *pct_cpup, float *pct_memp, List *ostinfop);
int lmt_ost_decode_v2_ostinfo (const char *s, char **ostnamep,
                        uint64_t *read_bytesp, uint64_t *write_bytesp,
                        uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                        uint64_t *inodes_freep, uint64_t *inodes_totalp,
                        uint64_t *iopsp, uint64_t *num_exportsp,
                        uint64_t *lock_countp, uint64_t *grant_ratep,
                        uint64_t *cancel_ratep, uint64_t *connectp,
                        uint64_t *reconnectp, char **recov_statusp);

/* legacy */

int lmt_oss_decode_v1 (const char *s, char **ossnamep, float *pct_cpup,
                       float *pct_memp);
int lmt_ost_decode_v1 (const char *s, char **ossnamep, char **ostnamep,
                       uint64_t *read_bytesp, uint64_t *write_bytesp,
                       uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                       uint64_t *inodes_freep, uint64_t *inodes_totalp);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
