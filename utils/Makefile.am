AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../liblsd -I../libproc -I../liblmt -I../liblmtdb $(MYSQL_CFLAGS)

bin_PROGRAMS = ltop
sbin_PROGRAMS = lmtmetric
if MYSQL
sbin_PROGRAMS += lmtinit
endif

common_ldadd = \
	$(top_builddir)/liblmt/liblmt.la \
	$(top_builddir)/libproc/libproc.la \
	$(top_builddir)/liblsd/liblsd.la

ltop_SOURCES = ltop.c sample.c sample.h
ltop_LDADD = $(common_ldadd) $(LIBCURSES)
man1_MANS = ltop.1
CLEANFILES = ltop.1

lmtmetric_SOURCES = lmtmetric.c
lmtmetric_LDADD = \
	$(top_builddir)/liblmtdb/liblmtdb.la \
	$(common_ldadd) \
	$(MYSQL_LIBS)

if MYSQL
lmtinit_SOURCES = lmtinit.c
lmtinit_LDADD = $(top_builddir)/liblmtdb/liblmtdb.la $(common_ldadd)
man8_MANS = lmtinit.8
CLEANFILES += lmtinit.8
endif

