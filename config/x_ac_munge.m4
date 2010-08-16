##*****************************************************************************
#  AUTHOR:
#    Jim Garlick <garlick@llnl.gov>
#
#  SYNOPSIS:
#    X_AC_MUNGE
#
#  DESCRIPTION:
#    If munge support is not explicitly disabled, verify the presense
#    of munge.h and:
#    . set MUNGE automake conditional (test in Makefile.am's)
#    . set HAVE_MUNGE_H in config.h
#    It is a fatal configure error if munge is enabled but not installed.
##****************************************************************************
#
AC_DEFUN([X_AC_MUNGE], [
  AC_ARG_ENABLE([munge],
    [AS_HELP_STRING([--disable-munge], [Build without munge support])],
    [enable_munge=$enableval], [enable_munge=yes])
  AM_CONDITIONAL([MUNGE], [test x$enable_munge == xyes])
  if test x$enable_munge == xyes; then
    AC_CHECK_HEADERS([munge.h])
    if test x$ac_cv_header_munge_h != xyes; then
      AC_MSG_ERROR([Please install munge or configure --disable-munge])
    fi
    MUNGE_LIBS=-lmunge
    AC_SUBST([MUNGE_LIBS])
    AC_DEFINE(HAVE_MUNGE, 1, [Define if using munge])
  fi
])dnl
