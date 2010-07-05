##*****************************************************************************
#  AUTHOR:
#    Jim Garlick <garlick@llnl.gov>
#
#  SYNOPSIS:
#    X_AC_MYSQL
#
#  DESCRIPTION:
#    If mysql support is not disabled, verify the presence of the mysql_config
#    executable, then:
#    . set MYSQL_CFLAGS and MYSQL_LIBS
#    . set HAVE_MYSQL in config.h
#    . set MYSQL automake conditional (test in Makefile.am's)
#    It is a fatal configure error if mysql is enabled but not installed.
##*****************************************************************************
#
AC_DEFUN([X_AC_MYSQL], [
  MYSQL_CFLAGS=""
  MYSQL_LIBS=""
  AC_ARG_ENABLE([mysql],
    [AS_HELP_STRING([--disable-mysql], [Build without database support])],
    [enable_mysql=$enableval], [enable_mysql=yes])
  if test x$enable_mysql == xyes; then
    AC_PATH_PROG([MYSQL_CONFIG], [mysql_config], no)
    if test x$MYSQL_CONFIG == xno; then
      AC_MSG_ERROR([Please install mysql or configure --disable-mysql])
    fi
    MYSQL_CFLAGS=`$MYSQL_CONFIG --include`
    MYSQL_LIBS=`$MYSQL_CONFIG --libs`
    AC_DEFINE(HAVE_MYSQL, 1, [Define if using MySQL])
  fi
  AM_CONDITIONAL([MYSQL], [test x$enable_mysql == xyes])
  AC_SUBST([MYSQL_CFLAGS])
  AC_SUBST([MYSQL_LIBS])
])dnl
