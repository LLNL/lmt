int lmt_conf_init (int vopt, char *path);

char *lmt_conf_get_db_rouser (void);
int   lmt_conf_set_db_rouser (char *s);

char *lmt_conf_get_db_ropasswd (void);
int   lmt_conf_set_db_ropasswd (char *s);

char *lmt_conf_get_db_rwuser (void);
int   lmt_conf_set_db_rwuser (char *s);

char *lmt_conf_get_db_rwpasswd (void);
int   lmt_conf_set_db_rwpasswd (char *s);

char *lmt_conf_get_db_host (void);
int   lmt_conf_set_db_host (char *s);

int   lmt_conf_get_db_port (void);
void  lmt_conf_set_db_port (int i);

int   lmt_conf_get_db_debug (void);
void  lmt_conf_set_db_debug (int i);

int   lmt_conf_get_db_autoconf (void);
void  lmt_conf_set_db_autoconf (int i);

int   lmt_conf_get_cbr_debug (void);
void  lmt_conf_set_cbr_debug (int i);

int   lmt_conf_get_proto_debug (void);
void  lmt_conf_set_proto_debug (int i);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
