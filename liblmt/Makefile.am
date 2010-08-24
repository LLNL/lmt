AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd

noinst_LTLIBRARIES = liblmt.la

liblmt_la_SOURCES = \
	ost.c \
	mdt.c \
	osc.c \
	router.c \
	util.c \
	lmtconf.c 

if CEREBRO
liblmt_la_SOURCES += \
	lmtcerebro.c
endif

liblmt_la_LIBADD = $(CEREBRO_LIBS) $(LIBLUA)

