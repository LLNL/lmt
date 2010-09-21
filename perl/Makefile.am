AM_CFLAGS = @GCCWARN@

scriptlibdir = @X_DATADIR@/@PACKAGE@
cronlibdir = @X_DATADIR@/@PACKAGE@/cron

scriptlib_SCRIPTS = \
	lmt_update_fs_agg \
	lmt_update_mds_agg \
	lmt_update_ost_agg \
	lmt_update_other_agg \
	lmt_update_router_agg \
	lmtcf \
	lmtsh

scriptlib_DATA = \
	LMT.pm

cronlib_SCRIPTS = \
	lmt_agg.cron

CLEANFILES = \
	lmtsh \
	lmt_agg.cron \
        lmt_update_fs_agg \
        lmt_update_mds_agg \
        lmt_update_ost_agg \
        lmt_update_other_agg \
        lmt_update_router_agg \
	LMT.pm

