##*****************************************************************************
#  AUTHOR:
#    Jim Garlick <garlick@llnl.gov>
#
#  SYNOPSIS:
#    X_AC_PATH_MYSQL
#
#  DESCRIPTION:
#    Call mysql_config to get settings for MYSQL_CFLAGS and MYSQL_LIBS
#
#  WARNINGS:
#    This macro must be placed after AC_PROG_CC.
##*****************************************************************************

AC_DEFUN([X_AC_PATH_MYSQL], [
  AC_ARG_WITH([mysql-include-path],
    [AS_HELP_STRING([--with-mysql-include-path], [location of MySQL headers])],
    [MYSQL_CFLAGS="-I$withval"],
    [MYSQL_CFLAGS="`mysql_config --include 2>/dev/null`"])
  AC_SUBST([MYSQL_CFLAGS])
  
  AC_ARG_WITH([mysql-lib-path],
    [AS_HELP_STRING([--with-mysql-lib-path], [location of MySQL libraries])],
    [MYSQL_LIBS="-L$withval -lmysqlclient"],
    [MYSQL_LIBS="`mysql_config --libs 2>/dev/null`"])
  AC_SUBST([MYSQL_LIBS])
  if test -n "$MYSQL_LIBS"; then
    x_ac_path_mysql_LIBS="$LIBS"
    x_ac_path_mysql_CFLAGS="$CFLAGS"
    AC_CACHE_CHECK([for valid MySQL development environment], x_ac_mysql_ok,
       [LIBS="$LIBS $MYSQL_LIBS"; CFLAGS="$CFLAGS $MYSQL_CFLAGS"
        AC_TRY_LINK(
          [#include <mysql.h>],
          [mysql_init(0); ],
          x_ac_mysql_ok=yes, x_ac_mysql_ok=no)])
    if test "$x_ac_mysql_ok" = "yes"; then
        AC_DEFINE([HAVE_MYSQL_H],[],[Define if you have mysql.h])
    else
        unset MYSQL_LIBS MYSQL_CFLAGS
    fi
    LIBS="$x_ac_path_mysql_LIBS"
    CFLAGS="$x_ac_path_mysql_CFLAGS"
  fi
])dnl
