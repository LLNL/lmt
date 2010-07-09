AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd $(MYSQL_CFLAGS)

noinst_LTLIBRARIES = liblmt.la

liblmt_la_SOURCES = \
	ost.c \
	mdt.c \
	router.c \
	util.c \
	lmtconf.c 
if MYSQL
liblmt_la_SOURCES += \
	lmtmysql.c \
	lmtdb.c
endif
if CEREBRO
liblmt_la_SOURCES += \
	lmtcerebro.c
endif

liblmt_la_LIBADD = $(MYSQL_LIBS) $(CEREBRO_LIBS) $(LIBLUA)

