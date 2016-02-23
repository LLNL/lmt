int lmt_mdt_string_v2 (pctx_t ctx, char *s, int len);

int lmt_mdt_decode_v1_v2 (const char *s, char **mdsnamep,
                        float *pct_cpup, float *pct_memp, List *mdtinfo,
                        int version);
int lmt_mdt_decode_v2_mdtinfo (const char *s, char **mdtnamep,
                        uint64_t *inodes_freep, uint64_t *inodes_totalp,
                        uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                        char **recovery_info, List *mdopsp);
int lmt_mdt_decode_v1_mdops (const char *s, char **opnamep, uint64_t *samplesp,
                        uint64_t *sump, uint64_t *sumsquaresp);

/* legacy */

int lmt_mdt_decode_v1_mdtinfo (const char *s, char **mdtnamep,
                        uint64_t *inodes_freep, uint64_t *inodes_totalp,
                        uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                        List *mdopsp);
int lmt_mds_decode_v2 (const char *s, char **mdsnamep, char **mdtnamep,
                        float *pct_cpup, float *pct_memp,
                        uint64_t *inodes_freep, uint64_t *inodes_totalp,
                        uint64_t *kbytes_freep, uint64_t *kbytes_totalp,
                        List *mdopsp);
int lmt_mds_decode_v2_mdops (const char *s, char **opnamep, uint64_t *samplesp,
                        uint64_t *sump, uint64_t *sumsquaresp);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
