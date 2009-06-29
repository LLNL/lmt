##*****************************************************************************
## $Id: ac_debug.m4,v 1.3 2005/05/09 16:02:11 achu Exp $
##*****************************************************************************
#  AUTHOR:
#    Chris Dunlap <cdunlap@llnl.gov>
#
#  SYNOPSIS:
#    AC_DEBUG
#
#  DESCRIPTION:
#    Adds support for the "--enable-debug" configure script option.
#    If CFLAGS are not passed to configure, they will be set based
#    on whether debugging has been enabled.  Also, the NDEBUG macro
#    (used by assert) will be set accordingly.
#
#  WARNINGS:
#    This macro must be placed after AC_PROG_CC or equivalent.
##*****************************************************************************

AC_DEFUN([AC_DEBUG],
[
  AC_MSG_CHECKING([whether debugging is enabled])
  AC_ARG_ENABLE([debug],
    AC_HELP_STRING([--enable-debug], [enable debugging code for development]),
    [ case "$enableval" in
        yes) ac_debug=yes ;;
        no)  ac_debug=no ;;
        *)   AC_MSG_RESULT([doh!])
             AC_MSG_ERROR([bad value "$enableval" for --enable-debug]) ;;
      esac
    ]
  )
  if test "$ac_debug" = yes; then
    if test -z "$ac_save_CFLAGS"; then
      test "$ac_cv_prog_cc_g" = yes && CFLAGS="-g -Werror-implicit-function-declaration"
      test "$GCC" = yes && CFLAGS="$CFLAGS -Wall"
    fi
    AC_DEFINE([CEREBRO_DEBUG], [1], [Define to 1 for Cerebro debugging])
    MANPAGE_DEBUG=1
  else
    if test -z "$ac_save_CFLAGS"; then
      test "$GCC" = yes && CFLAGS="-g -O2 -Wall -fno-strict-aliasing" || CFLAGS="-g -O2"
	  LDFLAGS="${LDFLAGS--s}"
    fi
    AC_DEFINE([NDEBUG], [1],
      [Define to 1 if you are building a production release.])
    MANPAGE_DEBUG=0
  fi
  AC_SUBST(MANPAGE_DEBUG)
  AC_MSG_RESULT([${ac_debug=no}])
])
