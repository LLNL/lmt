typedef struct lmt_db_struct *lmt_db_t;

int lmt_db_create (int readonly, const char *dbname, lmt_db_t *dbp);

int lmt_db_create_all (int readonly, List *dblp);

int lmt_db_list (char *user, char *pass, List *lp);

int lmt_db_drop (char *user, char *pass, char *fs);

int lmt_db_add (char *user, char *pass, char *fs, char *schema_vers,
                char *sql_schema);

void lmt_db_destroy (lmt_db_t db);

int lmt_db_insert_mds_data (lmt_db_t db, char *mdsname, char *mdtname,
                        float pct_cpu, uint64_t kbytes_free,
                        uint64_t kbytes_used, uint64_t inodes_free,
                        uint64_t inodes_used);
int lmt_db_insert_mds_ops_data (lmt_db_t db, char *mdtname, char *opname,
                        uint64_t samples, uint64_t sum, uint64_t sumsquares);
int lmt_db_insert_oss_data (lmt_db_t db, int quiet_noexist, char *name,
                        float pctcpu, float pctmem);
int lmt_db_insert_ost_data (lmt_db_t db, char *ossname, char *ostname,
                        uint64_t read_bytes, uint64_t write_bytes,
                        uint64_t kbytes_free, uint64_t kbytes_used,
                        uint64_t inodes_free, uint64_t inodes_used);
int lmt_db_insert_router_data (lmt_db_t db, char *name,
                        uint64_t bytes, float pct_cpu);

int lmt_db_update_ops(char *user, char *pass, char *fs);

/* accessors */

char *lmt_db_fsname (lmt_db_t db);

int lmt_db_lookup (lmt_db_t db, char *svctype, char *name);

typedef int (*lmt_db_map_f) (const char *key, void *arg);

int lmt_db_server_map (lmt_db_t db, char *svctype, lmt_db_map_f mf, void *arg);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
