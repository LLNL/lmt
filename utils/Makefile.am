AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../liblsd -I../libproc -I../liblmt -I../liblmtdb

sbin_PROGRAMS = lmtmetric lmtdiagnose ltop

common_ldadd = \
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
lmtdiagnose_LDADD += $(top_builddir)/liblmtdb/liblmtdb.la
endif
