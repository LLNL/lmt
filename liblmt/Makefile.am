AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd $(MYSQL_CFLAGS)

noinst_LTLIBRARIES = liblmt.la

liblmt_la_SOURCES = \
	ost.c \
	mdt.c \
	router.c \
	lmtsql.c \
	lmtdb.c \
	util.c

liblmt_la_LIBADD = $(MYSQL_LIBS)
