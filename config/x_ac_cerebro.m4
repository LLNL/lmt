##*****************************************************************************
#  AUTHOR:
#    Jim Garlick <garlick@llnl.gov>
#
#  SYNOPSIS:
#    X_AC_CEREBRO
#
#  DESCRIPTION:
#    If cerebro support is not explicitly disabled, verify the presense
#    of cerebro.h and:
#    . set CEREBRO automake conditional (test in Makefile.am's)
#    . set HAVE_CEREBRO_H in config.h
#    It is a fatal configure error if cerebro is enabled but not installed.
##****************************************************************************
#
AC_DEFUN([X_AC_CEREBRO], [
  AC_ARG_ENABLE([cerebro],
    [AS_HELP_STRING([--disable-cerebro], [Build without cerebro support])],
    [enable_cerebro=$enableval], [enable_cerebro=yes])
  AM_CONDITIONAL([CEREBRO], [test x$enable_cerebro == xyes])
  if test x$enable_cerebro == xyes; then
    AC_CHECK_HEADER([cerebro.h])
    if test x$ac_cv_header_cerebro_h != xyes; then
      AC_MSG_ERROR([Please install cerebro or configure --disable-cerebro])
    fi
    CEREBRO_LIBS=-lcerebro
    AC_SUBST([CEREBRO_LIBS])
    AC_DEFINE(HAVE_CEREBRO, 1, [Define if using cerebro])
  fi
])dnl
