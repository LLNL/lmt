###############
if WITH_GNU_LD
MONITOR_VERSION_SCRIPT = $(srcdir)/monitor.map
MONITOR_OTHER_FLAGS = -Wl,--version-script=$(MONITOR_VERSION_SCRIPT)
endif
###############

###############
LMT_MONITOR_MODULE = cerebro_monitor_lmt.la
###############

###############
modulelibdir = $(CEREBRO_MODULE_DIR)
modulelib_LTLIBRARIES = $(LMT_MONITOR_MODULE)
###############

###############
MODULE_FLAGS = -module -avoid-version
###############

###############
cerebro_monitor_lmt_la_SOURCES = cerebro_monitor_lmt.c
cerebro_monitor_lmt_la_LDFLAGS = $(MODULE_FLAGS) \
                                 $(LMT_MON_LDFLAGS) \
                                 $(LMT_MON_LIBS) \
                                 $(MONITOR_OTHER_FLAGS)
cerebro_monitor_lmt_la_CFLAGS  = -I/usr/include \
                                 -I/usr/include/cerebro \
                                 -I$(srcdir)/../../libs/include \
                                 -I$(srcdir)/../../libs/common
cerebro_monitor_lmt_la_LIBADD  = ../../libs/common/libhash.la

force-dependency-check:

EXTRA_DIST = monitor.map
###############
