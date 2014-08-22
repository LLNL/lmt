int lmt_osc_string_v1 (pctx_t ctx, char *s, int len);

int lmt_osc_decode_v1 (const char *s, char **mdsnamep, List *oscinfop);
int lmt_osc_decode_v1_oscinfo (const char *s, char **oscnamep,
                               char **oscstatep);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
