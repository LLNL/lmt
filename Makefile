SVNURL                          := http://lmt.googlecode.com/svn
VERSION                         := $(shell awk '/[Vv]ersion:/ {print $$2}' META)
RELEASE                         := $(shell awk '/[Rr]elease:/ {print $$2}' META)
BRANCHURL                       := $(SVNURL)/branches/lmt-$(VERSION)
TAGURL                          := $(SVNURL)/tags/lmt-$(VERSION)

ARCH                            := $(shell uname -i)
DISTRO                          := $(shell scripts/distro.sh)
RPMDIR                          := /usr/admin/rpm/llnl/RPMS-$(DISTRO)/$(ARCH)

ifeq (,${ANTPATH})
ANTPATH				:= /usr/local/tools/ant/ant/bin/ant
endif

ifeq (,${JAVA_HOME})
JAVA_HOME			:= /usr
endif

LOCAL_CONTENT                   := COPYING ChangeLog DISCLAIMER META \
                                   Makefile NEWS README lmt.spec \
                                   scripts

# Build with VERBOSE=1 to make rpmbuild verbose
ifneq (,${VERBOSE})
BUILDFLAGS                      += -v
endif

all: lmt

check-vars:
	@echo "Current release:  lmt-$(VERSION)-$(RELEASE)"
	@echo "RPMARGS           $(RPMARGS)"

rpms-working: check-vars
	scripts/build $(BUILDFLAGS) --snapshot . --destination=RPMS/$(ARCH)

rpms rpms-release: check-vars
	scripts/build $(BUILDFLAGS) --project-release=$(RELEASE) --destination=RPMS/$(ARCH) $(TAGURL)

config: check-vars

install: lmt-server
	scripts/install-lmt-server.sh
	
lmt-server: config
	scripts/build-lmt-server.sh

lmt-client: config
	scripts/build-lmt-client.sh

lmt: lmt-server lmt-client

tags:
	ctags -R --exclude=.svn --exclude=.pc server client

clean:
	./scripts/clean-lmt-server.sh
	./scripts/clean-lmt-client.sh
	rm -f tags
	rm -f scripts/build
