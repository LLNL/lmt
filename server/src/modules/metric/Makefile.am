##########
noinst_HEADERS = cerebro_metric_lmt_common.h
##########

##########
if WITH_GNU_LD
METRIC_VERSION_SCRIPT = $(srcdir)/metric.map
METRIC_OTHER_FLAGS = -Wl,--version-script=$(METRIC_VERSION_SCRIPT)
endif
##########

##########
LMT_MDS_METRIC_MODULE = cerebro_metric_lmt_mds.la
LMT_OSS_METRIC_MODULE = cerebro_metric_lmt_oss.la
LMT_OST_METRIC_MODULE = cerebro_metric_lmt_ost.la
LMT_ROUTER_METRIC_MODULE = cerebro_metric_lmt_router.la
##########

##########
modulelibdir = $(CEREBRO_MODULE_DIR)
modulelib_LTLIBRARIES = $(LMT_MDS_METRIC_MODULE) \
                        $(LMT_OSS_METRIC_MODULE) \
                        $(LMT_OST_METRIC_MODULE) \
                        $(LMT_ROUTER_METRIC_MODULE)
##########

##########
MODULE_FLAGS = -module -avoid-version
##########

##########
cerebro_metric_lmt_mds_la_SOURCES = cerebro_metric_lmt_mds.c \
                                    cerebro_metric_lmt_common.c
cerebro_metric_lmt_mds_la_LDFLAGS = $(MODULE_FLAGS) \
                                    $(METRIC_OTHER_FLAGS)
cerebro_metric_lmt_mds_la_CFLAGS  = -I/usr/include \
                                    -I/usr/include/cerebro \
                                    -I$(srcdir)/../../libs/include
cerebro_metric_lmt_mds_la_LIBADD  =

cerebro_metric_lmt_oss_la_SOURCES = cerebro_metric_lmt_oss.c \
                                    cerebro_metric_lmt_common.c
cerebro_metric_lmt_oss_la_LDFLAGS = $(MODULE_FLAGS) \
                                    $(METRIC_OTHER_FLAGS)
cerebro_metric_lmt_oss_la_CFLAGS  = -I/usr/include \
                                    -I/usr/include/cerebro \
                                    -I$(srcdir)/../../libs/include
cerebro_metric_lmt_oss_la_LIBADD  =

cerebro_metric_lmt_ost_la_SOURCES = cerebro_metric_lmt_ost.c \
                                    cerebro_metric_lmt_common.c
cerebro_metric_lmt_ost_la_LDFLAGS = $(MODULE_FLAGS) \
                                    $(METRIC_OTHER_FLAGS)
cerebro_metric_lmt_ost_la_CFLAGS  = -I/usr/include \
                                    -I/usr/include/cerebro \
                                    -I$(srcdir)/../../libs/include \
                                    -I$(srcdir)/../../libs/common
cerebro_metric_lmt_ost_la_LIBADD  = ../../libs/common/libhash.la

cerebro_metric_lmt_router_la_SOURCES = cerebro_metric_lmt_router.c \
                                       cerebro_metric_lmt_common.c
cerebro_metric_lmt_router_la_LDFLAGS = $(MODULE_FLAGS) \
                                       $(METRIC_OTHER_FLAGS)
cerebro_metric_lmt_router_la_CFLAGS  = -I/usr/include \
                                       -I/usr/include/cerebro \
                                       -I$(srcdir)/../../libs/include
cerebro_metric_lmt_router_la_LIBADD  =
##########
