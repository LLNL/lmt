AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = \
	-I$(top_builddir)/liblmt \
	-I$(top_builddir)/libproc \
	-I$(top_builddir)/liblsd

common_libadd = \
        $(top_builddir)/liblmt/liblmt.la \
        $(top_builddir)/libproc/libproc.la \
        $(top_builddir)/liblsd/liblsd.la

module_ldflags = -module -avoid-version -Wl,--version-script=metric.map
modulelibdir = $(libdir)/cerebro

modulelib_LTLIBRARIES = \
	cerebro_metric_lmt_ost.la \
	cerebro_metric_lmt_mdt.la \
	cerebro_metric_lmt_osc.la \
	cerebro_metric_lmt_router.la

cerebro_metric_lmt_ost_la_SOURCES = ost.c
cerebro_metric_lmt_ost_la_LDFLAGS = $(module_ldflags)
cerebro_metric_lmt_ost_la_LIBADD = $(common_libadd)

cerebro_metric_lmt_mdt_la_SOURCES = mdt.c
cerebro_metric_lmt_mdt_la_LDFLAGS = $(module_ldflags)
cerebro_metric_lmt_mdt_la_LIBADD = $(common_libadd)

cerebro_metric_lmt_osc_la_SOURCES = osc.c
cerebro_metric_lmt_osc_la_LDFLAGS = $(module_ldflags)
cerebro_metric_lmt_osc_la_LIBADD = $(common_libadd)

cerebro_metric_lmt_router_la_SOURCES = router.c
cerebro_metric_lmt_router_la_LDFLAGS = $(module_ldflags)
cerebro_metric_lmt_router_la_LIBADD = $(common_libadd)
