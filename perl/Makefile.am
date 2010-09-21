AM_CFLAGS = @GCCWARN@

noinst_SCRIPTS = \
	lmt_agg.sh \
	lmt_agg.cron

sbin_SCRIPTS = \
	lmtsh

scriptlib_SCRIPTS = \
	lmt_update_fs_agg \
	lmt_update_mds_agg \
	lmt_update_ost_agg \
	lmt_update_other_agg \
	lmt_update_router_agg \
	lmtcf

scriptlibdir = @X_DATADIR@/@PACKAGE@

scriptlib_DATA = \
	LMT.pm

CLEANFILES = \
	lmtsh \
        lmt_update_fs_agg \
        lmt_update_mds_agg \
        lmt_update_ost_agg \
        lmt_update_other_agg \
        lmt_update_router_agg \
	LMT.pm
