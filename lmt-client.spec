Name: 
Version: 
Release: 

License: GPL
Group: Applications/System
Summary: Lustre Montitoring Tools Client
URL: http://sourceforge.net/projects/lmt/
Packager: Jim Garlick <garlick@llnl.gov>
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildRequires: ant, ant-nodeps
BuildRequires: ncurses-devel
BuildRequires: jre >= 1.5.0, java-devel >= 1.5.0
Requires: jre >= 1.5.0, ncurses
%define __spec_install_post /usr/lib/rpm/brp-compress || :
%define debug_package %{nil}

%define lmtlibdir %{_datadir}/lmt/lib

%description
Lustre Monitoring Tools (LMT) Client

%prep
%setup

%build
%configure
make

%install
rm -rf   $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{_bindir}
mkdir -p $RPM_BUILD_ROOT%{lmtlibdir}

cp scripts/lwatch              $RPM_BUILD_ROOT%{_bindir}
cp scripts/lstat               $RPM_BUILD_ROOT%{_bindir}
cp scripts/ltop                $RPM_BUILD_ROOT%{_bindir}/oltop
cp lmt-complete.jar            $RPM_BUILD_ROOT%{lmtlibdir}/lmt-complete.jar
cp charva/c/lib/libTerminal.so $RPM_BUILD_ROOT%{lmtlibdir}/libTerminal.so
#rm -rf charva/c/lib/libTerminal.so.debug

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,root,root)
%doc ChangeLog NEWS DISCLAIMER COPYING
%{_bindir}/lwatch
%{_bindir}/lstat
%{_bindir}/oltop
%dir %{_datadir}/lmt
%dir %{lmtlibdir}
%attr(0644,root,root) %{lmtlibdir}/lmt-complete.jar
%attr(0644,root,root) %{lmtlibdir}/libTerminal.so
