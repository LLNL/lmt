check_PROGRAMS = \
	tparse \
	tlustre \
	tproc \
	tbrw_stats

TESTS_ENVIRONMENT = env

TESTS = \
	t00-tparse \
	t01-lmtconfig-sanity \
	t02-lmtconfig-sanity \
	t03-parse-brw_stats

XFAIL_TESTS =

CLEANFILES = *.out *.diff

AM_CFLAGS = @GCCWARN@

AM_CPPFLAGS = \
	-I../liblsd -I../libproc -I../liblmt

LDADD = \
        $(top_builddir)/liblmt/liblmt.la \
        $(top_builddir)/libproc/libproc.la \
        $(top_builddir)/liblsd/liblsd.la
	
tparse_SOURCES = tparse.c
tbrw_stats_SOURCES = tbrw_stats.c
tproc_SOURCES = tproc.c
tlustre_SOURCES = tlustre.c
