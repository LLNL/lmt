###****************************************************************************
##  AUTHOR:
##    Jim Garlick <garlick@llnl.gov>
##
##  SYNOPSIS:
##    X_AC_CEREBRO
##
##  DESCRIPTION:
##    The default is to build cerebro modules.
##    Cerebro modules can be disabled with --disable-cerebro.
##
##    It is a fatal configure error if cerebro is enabled but its header
##    files are not found.
##
##    CEREBRO can be tested in Makefile.am's, and
##    HAVE_CEREBRO_CEREBRO_CONSTANTS_H is conditionally defined in config.h.
##
###****************************************************************************
#
AC_DEFUN([X_AC_CEREBRO], [
  AC_ARG_ENABLE([cerebro],
    [AS_HELP_STRING([--disable-cerebro], [Build without cerebro support])],
    [enable_cerebro=$enableval], [enable_cerebro=yes])
  AM_CONDITIONAL([CEREBRO], [test x$enable_cerebro == xyes])
  if test x$enable_cerebro == xyes; then
    AC_CHECK_HEADER([cerebro/cerebro_constants.h])
    if test x$ac_cv_header_cerebro_cerebro_constants_h != xyes; then
      AC_MSG_ERROR([Please install cerebro or configure --disable-cerebro])
    fi
  fi
])dnl
