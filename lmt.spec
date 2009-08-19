Name: 
Version: 
Release: 

License: GPL
Group: Applications/System
Summary: Lustre Montitoring Tools
URL: http://sourceforge.net/projects/lmt/
Packager: Christopher J. Morrone <morrone2@llnl.gov>
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildRequires: autoconf, automake, libtool
BuildRequires: ant, ant-nodeps
BuildRequires: mysql, mysql-devel
BuildRequires: cerebro >= 1.3-5
BuildRequires: jre-ibm >= 1.4.2, java-devel-ibm >= 1.4.2
BuildRequires: ncurses-devel
%if 0%{?ch4}
BuildRequires: glibc >= 2.5-18
%endif
%define __spec_install_post /usr/lib/rpm/brp-compress || :
%define debug_package %{nil}

%description
Lustre Monitoring Tools (LMT)

%package server
Summary: Lustre Monitoring Tools Server
Group: Applications/System
Requires: cerebro >= 1.3, mysql-server >= 4.1.20, perl-DBI
%description server
Lustre Monitoring Tools (LMT) Server

%package server-agent
Summary: Lustre Monitoring Tools Server Agent
Group: Applications/System
Requires: cerebro >= 1.3
%description server-agent
Lustre Monitoring Tools (LMT) Server Agent

%package client
Summary: Lustre Monitoring Tools Client
Group: Applications/System
Requires: jre >= 1.4.2, ncurses
%description client
Lustre Monitoring Tools (LMT) Client

%prep
%setup

%build
make

%install
rm -rf   $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{_bindir}
mkdir -p $RPM_BUILD_ROOT%{_sbindir}
mkdir -p $RPM_BUILD_ROOT/etc/init.d
mkdir -p $RPM_BUILD_ROOT/usr/share/lmt/etc
mkdir -p $RPM_BUILD_ROOT/usr/share/lmt/lib/perl
mkdir -p $RPM_BUILD_ROOT/usr/share/lmt/cron
mkdir -p $RPM_BUILD_ROOT%{_mandir}/man8

# Server Files
DESTDIR="$RPM_BUILD_ROOT" make install
rm -rf $RPM_BUILD_ROOT%{_libdir}/cerebro/cerebro_metric_lmt*.a
rm -rf $RPM_BUILD_ROOT%{_libdir}/cerebro/cerebro_metric_lmt*.la
rm -rf $RPM_BUILD_ROOT%{_libdir}/cerebro/cerebro_monitor_lmt*.a
rm -rf $RPM_BUILD_ROOT%{_libdir}/cerebro/cerebro_monitor_lmt*.la

cp server/src/scripts/create_lmtconfig.pl $RPM_BUILD_ROOT%{_sbindir}/create_lmtconfig
cp server/src/scripts/create_schema-1.1.sql $RPM_BUILD_ROOT/usr/share/lmt/etc
cp server/src/scripts/upgrade_lmtconfig-1.1.pl $RPM_BUILD_ROOT/usr/sbin/upgrade_lmtconfig-1.1

cp server/cron/lmt_update_fs_agg          $RPM_BUILD_ROOT%{_sbindir}
cp server/cron/lmt_update_mds_agg         $RPM_BUILD_ROOT%{_sbindir}
cp server/cron/lmt_update_ost_agg         $RPM_BUILD_ROOT%{_sbindir}
cp server/cron/lmt_update_other_agg       $RPM_BUILD_ROOT%{_sbindir}
cp server/cron/lmt_update_router_agg      $RPM_BUILD_ROOT%{_sbindir}
cp server/cron/lmt_agg.sh                 $RPM_BUILD_ROOT%{_sbindir}
cp server/lib/perl/LMT.pm                 $RPM_BUILD_ROOT/usr/share/lmt/lib/perl/
cp server/cron/lmtrc                      $RPM_BUILD_ROOT/usr/share/lmt/cron/
cp server/cron/lmt_agg.cron               $RPM_BUILD_ROOT/usr/share/lmt/cron/
cp doc/create_lmtconfig.8                 $RPM_BUILD_ROOT%{_mandir}/man8

# Client Files
cp client/scripts/lwatch              $RPM_BUILD_ROOT%{_bindir}
cp client/scripts/lstat               $RPM_BUILD_ROOT%{_bindir}
cp client/scripts/ltop                $RPM_BUILD_ROOT%{_bindir}
cp client/lmt-complete.jar            $RPM_BUILD_ROOT/usr/share/lmt/lib/lmt-complete.jar
cp client/charva/c/lib/libTerminal.so $RPM_BUILD_ROOT/usr/share/lmt/lib/libTerminal.so
cp client/etc/lmtrc                   $RPM_BUILD_ROOT/usr/share/lmt/etc/lmtrc
rm -rf client/charva/c/lib/libTerminal.so.debug

%clean
rm -rf $RPM_BUILD_ROOT

%files server
%defattr(-,root,root)
%doc ChangeLog NEWS DISCLAIMER COPYING
%{_libdir}/cerebro/cerebro_monitor_lmt*
/usr/sbin/create_lmtconfig
/usr/sbin/upgrade_lmtconfig-1.1
/usr/sbin/lmt_update_fs_agg
/usr/sbin/lmt_update_mds_agg
/usr/sbin/lmt_update_ost_agg
/usr/sbin/lmt_update_other_agg
/usr/sbin/lmt_update_router_agg
/usr/sbin/lmt_agg.sh
/usr/share/lmt/cron/lmt_agg.cron
/usr/share/lmt/etc/create_schema-1.1.sql
%attr(0640,root,root) %config(noreplace) /usr/share/lmt/cron/lmtrc
%attr(0644,root,root) /usr/share/lmt/lib/perl/LMT.pm
%{_mandir}/man8/create_lmtconfig.8.gz

%files server-agent
%defattr(-,root,root)
%{_libdir}/cerebro/cerebro_metric_lmt*

%files client
%defattr(-,root,root)
/usr/bin/lwatch
/usr/bin/lstat
/usr/bin/ltop
%dir /usr/share/lmt
%dir /usr/share/lmt/lib
%dir /usr/share/lmt/etc
%attr(0644,root,root) /usr/share/lmt/lib/lmt-complete.jar
%attr(0644,root,root) /usr/share/lmt/lib/libTerminal.so
%attr(0644,root,root) %config(noreplace) /usr/share/lmt/etc/lmtrc

%post server
if [ -x %{_initrddir}/cerebrod ]; then
    %{_initrddir}/cerebrod condrestart
fi

%post server-agent
if [ -x %{_initrddir}/cerebrod ]; then
    %{_initrddir}/cerebrod condrestart
fi
