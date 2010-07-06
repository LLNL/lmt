AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = -I../liblsd -I../libproc -I../liblmt

sbin_PROGRAMS = tproc tlustre otop lmtmetric lmtdiagnose

common_ldadd = \
	$(top_builddir)/liblmt/liblmt.la \
	$(top_builddir)/libproc/libproc.la \
	$(top_builddir)/liblsd/liblsd.la

tproc_SOURCES = tproc.c
tproc_LDADD = $(common_ldadd)

tlustre_SOURCES = tlustre.c
tlustre_LDADD = $(common_ldadd)

otop_SOURCES = otop.c
otop_LDADD = $(common_ldadd) $(LIBCURSES)

lmtmetric_SOURCES = lmtmetric.c
lmtmetric_LDADD = $(common_ldadd)

lmtdiagnose_SOURCES = lmtdiagnose.c
lmtdiagnose_LDADD = $(common_ldadd) @CEREBRO_LIBS@
