AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../libproc -I../liblsd @MYSQL_CFLAGS@

noinst_LTLIBRARIES = liblmt.la

liblmt_la_SOURCES = \
	ost.c \
	mdt.c \
	router.c \
	util.c

if MYSQL
liblmt_la_SOURCES += \
	lmtsql.c \
	lmtdb.c
endif

liblmt_la_LIBADD = @MYSQL_LIBS@

