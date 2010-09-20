Name: 
Version: 
Release: 

# TODO: lmt-client subpackage for lwatch + oltop
# TODO: lmt-utils subpackage for ltop (once ltop can read proc directly)

License: GPL
Group: Applications/System
Summary: Lustre Montitoring Tool
URL: http://sourceforge.net/projects/lmt/
Packager: Jim Garlick <garlick@llnl.gov>
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
#BuildRequires: ant, ant-nodeps
BuildRequires: mysql, mysql-devel
BuildRequires: cerebro >= 1.3-5
BuildRequires: ncurses-devel
BuildRequires: lua-devel
%if 0%{?ch4}
#BuildRequires: java-1.5.0-ibm-devel, java-1.5.0-ibm
#BuildRequires: glibc >= 2.5-18
%else
#BuildRequires: jre >= 1.4.2, java-devel >= 1.4.2
%endif
#%define __spec_install_post /usr/lib/rpm/brp-compress || :
%define debug_package %{nil}

%description
Lustre Monitoring Tool

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

%prep
%setup

%build
%configure
make

%install
rm -rf   $RPM_BUILD_ROOT
make install DESTDIR=$RPM_BUILD_ROOT

%clean
rm -rf $RPM_BUILD_ROOT

%files

%files server
%defattr(-,root,root)
%{_libdir}/cerebro/cerebro_monitor_lmt*
%{_sbindir}/*
%{_bindir}/*
%{_mandir}/man1/*
%{_mandir}/man8/*
%{_datadir}/lmt/*
%dir %{_sysconfdir}/lmt
%config(noreplace) %{_sysconfdir}/lmt/lmt.conf

%files server-agent
%{_libdir}/cerebro/cerebro_metric_lmt*
