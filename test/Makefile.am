check_PROGRAMS = \
	tparse \
	tproc \
	tbrw_stats \
	tstats \
	trecov \
	tosc \
	tlnet \
	tuuid

TESTS_ENVIRONMENT = env

TESTS = \
	t00-tparse \
	t03-parse-brw_stats \
	t04-parse-cpu-mem \
	t05-parse-stats \
	t06-parse-recovery \
	t07-parse-oscinfo \
	t08-parse-lnet \
	t09-parse-uuid

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
tstats_SOURCES = tstats.c
trecov_SOURCES = trecov.c
tosc_SOURCES = tosc.c
