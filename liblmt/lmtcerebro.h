typedef struct cmetric_struct *cmetric_t;

int lmt_cbr_get_metrics (char *names, List *rlp);

char *lmt_cbr_get_val (cmetric_t c);

char *lmt_cbr_get_name (cmetric_t c);

char *lmt_cbr_get_nodename (cmetric_t c);

time_t lmt_cbr_get_time (cmetric_t c);


/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
