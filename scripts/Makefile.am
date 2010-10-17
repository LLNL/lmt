AM_CFLAGS = @GCCWARN@

scriptlibdir = @X_DATADIR@/@PACKAGE@
cronlibdir = @X_DATADIR@/@PACKAGE@/cron

sbin_SCRIPTS = \
	lmtsh

scriptlib_SCRIPTS = \
	lmt_update_fs_agg \
	lmt_update_mds_agg \
	lmt_update_ost_agg \
	lmt_update_other_agg \
	lmt_update_router_agg

dist_scriptlib_DATA = \
	create_schema-1.1.sql \
	mkusers.sql

scriptlib_DATA = \
	LMT.pm

cronlib_SCRIPTS = \
	lmt_agg.cron

man8_MANS = \
	lmt_agg.cron.8

CLEANFILES = \
	lmtsh \
	lmt_agg.cron \
        lmt_update_fs_agg \
        lmt_update_mds_agg \
        lmt_update_ost_agg \
        lmt_update_other_agg \
        lmt_update_router_agg \
	LMT.pm \
	lmt_agg.cron.8

