AM_CPPFLAGS = \
	-I$(top_builddir)/liblmt \
	-I$(top_builddir)/libproc \
	-I$(top_builddir)/liblsd

common_libadd = \
        $(top_builddir)/liblmt/liblmt.la \
        $(top_builddir)/libproc/libproc.la \
        $(top_builddir)/liblsd/liblsd.la

module_ldflags =  -module -avoid-version -Wl,--version-script=monitor.map
modulelibdir = $(libdir)/cerebro

modulelib_LTLIBRARIES = \
	cerebro_monitor_lmt_mysql.la

cerebro_monitor_lmt_mysql_la_SOURCES = mysql.c
cerebro_monitor_lmt_mysql_la_LDFLAGS = $(module_ldflags)
cerebro_monitor_lmt_mysql_la_LIBADD  = $(common_libadd)

EXTRA_DIST = monitor.map
