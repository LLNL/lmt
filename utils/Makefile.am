AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../liblsd -I../libproc -I../liblmt -I../liblmtdb

noinst_PROGRAMS = lmtdiagnose
bin_PROGRAMS = ltop
sbin_PROGRAMS = lmtmetric
if MYSQL
sbin_PROGRAMS += lmtinit
DBLIB = $(top_builddir)/liblmtdb/liblmtdb.la
endif

common_ldadd = \
	$(DBLIB) \
	$(top_builddir)/liblmt/liblmt.la \
	$(top_builddir)/libproc/libproc.la \
	$(top_builddir)/liblsd/liblsd.la

ltop_SOURCES = ltop.c sample.c
ltop_LDADD = $(common_ldadd) $(LIBCURSES)

lmtmetric_SOURCES = lmtmetric.c
lmtmetric_LDADD = $(common_ldadd)

lmtdiagnose_SOURCES = lmtdiagnose.c
lmtdiagnose_LDADD = $(common_ldadd)

if MYSQL
lmtinit_SOURCES = lmtinit.c
lmtinit_LDADD = $(common_ldadd)
endif

man1_MANS = \
	ltop.1
if MYSQL
man8_MANS = \
	lmtinit.1
endif

CLEANFILES = \
	ltop.1 \
	lmtinit.1
