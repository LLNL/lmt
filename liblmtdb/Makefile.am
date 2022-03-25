AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd -I../liblmt $(MYSQL_CFLAGS)

noinst_LTLIBRARIES = liblmtdb.la

liblmtdb_la_SOURCES = \
	lmtdb.c \
	lmtdb.h \
	lmtmysql.c \
	lmtmysql.h

liblmtdb_la_LIBADD = $(MYSQL_LIBS)
