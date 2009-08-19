SVNURL                          := http://lmt.googlecode.com/svn
TRUNKURL			:= $(SVNURL)/trunk
TAGURL                          := $(SVNURL)/tags/lmt-$(VERSION)

VERSION                         := $(shell awk '/[Vv]ersion:/ {print $$2}' META)
RELEASE                         := $(shell awk '/[Rr]elease:/ {print $$2}' META)
ARCH                            := $(shell uname -i)
BUILDFLAGS			:= --destination=RPMS/$(ARCH)

all: lmt

lmt: lmt-server lmt-client

install: lmt-server
	scripts/install-lmt-server.sh
lmt-server:
	scripts/build-lmt-server.sh
lmt-client:
	scripts/build-lmt-client.sh

rpms: rpms-release
rpms-release:
	scripts/build $(BUILDFLAGS) --project-release=$(RELEASE) $(TAGURL)
rpms-trunk:
	scripts/build $(BUILDFLAGS) --snapshot $(TRUNKURL)
rpms-working:
	scripts/build $(BUILDFLAGS) --snapshot .

tags:
	ctags -R --exclude=.svn --exclude=.pc server client

clean:
	./scripts/clean-lmt-server.sh
	./scripts/clean-lmt-client.sh
	rm -f tags
