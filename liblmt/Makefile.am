AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd

noinst_LTLIBRARIES = liblmt.la

liblmt_la_SOURCES = \
	ost.c \
	ost.h \
	mdt.c \
	mdt.h \
	osc.c \
	osc.h \
	router.c \
	router.h \
	util.c \
	util.h \
	lmtconf.c \
	lmtconf.h  \
	lmt.h

if CEREBRO
liblmt_la_SOURCES += \
	lmtcerebro.c \
	lmtcerebro.h
endif

liblmt_la_LIBADD = $(CEREBRO_LIBS) $(LIBLUA)

