int proc_stat (pctx_t ctx, uint64_t *usrp, uint64_t *nicep, uint64_t *sysp,
               uint64_t *idlep, uint64_t *iowaitp, uint64_t *irqp,
               uint64_t *softirqp);
int proc_stat2 (pctx_t ctx, uint64_t *usagep, uint64_t *totalp, double *pctp);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */

