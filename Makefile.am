SUBDIRS = liblsd libproc liblmt scripts
if MYSQL
SUBDIRS += liblmtdb
endif

SUBDIRS += utils test etc
if CEREBRO
SUBDIRS += cerebro
endif

rpmdirs:
	mkdir -p $(top_builddir)/rpm/SPECS
	mkdir -p $(top_builddir)/rpm/SOURCES
	mkdir -p $(top_builddir)/rpm/BUILD
	mkdir -p $(top_builddir)/rpm/RPMS
	mkdir -p $(top_builddir)/rpm/SRPMS

rpm: dist rpmdirs
	rpmbuild -ta --define "_topdir $(realpath $(top_builddir)/rpm)" $(DIST_ARCHIVES)

EXTRA_DIST = META lmt.spec
