##
# Prologue
##
AC_INIT([lmt])
AC_CONFIG_AUX_DIR([config])
AC_CONFIG_SRCDIR([NEWS])
AC_CANONICAL_SYSTEM
X_AC_META
X_AC_EXPAND_INSTALL_DIRS

##
# Automake support
##
AM_INIT_AUTOMAKE($META_NAME, $META_VERSION)
AM_CONFIG_HEADER([config/config.h])
AM_MAINTAINER_MODE

##
# Checks for programs
##
AC_PROG_CC
if test "$GCC" = yes; then
  GCCWARN="-Wall -Werror"
  AC_SUBST([GCCWARN])
  AC_SUBST([GCCWARNRPC])
fi
AC_PROG_RANLIB
AC_PROG_LIBTOOL
AC_LIB_LTDL

##
# Checks for header files.
##
AC_HEADER_STDC
AC_CHECK_HEADERS( \
  getopt.h \
)

##
# Checks for typedefs, structures, and compiler characteristics
## 
AC_C_BIGENDIAN
AC_C_CONST

##
# Checks for library functions
##
AC_CHECK_FUNCS( \
  getopt_long \
  vsnprintf \
  vsscanf \
)
X_AC_CHECK_PTHREADS
X_AC_CURSES
X_AC_PATH_MYSQL
##
# For list.c, hostlist.c, hash.c
##
AC_DEFINE(WITH_LSD_FATAL_ERROR_FUNC, 1, [Define lsd_fatal_error])
AC_DEFINE(WITH_LSD_NOMEM_ERROR_FUNC, 1, [Define lsd_nomem_error])
AC_DEFINE(WITH_LSD_LIST_MYSQL_COMPAT, 1,
   [Disable lsd list features that conflict with mysql internals])

##
# Epilogue
##
AC_CONFIG_FILES( \
  Makefile \
  liblsd/Makefile \
  libproc/Makefile \
  liblmt/Makefile \
  utils/Makefile \
  cerebro/Makefile \
  cerebro/metric/Makefile \
  cerebro/monitor/Makefile \
)
AC_OUTPUT
