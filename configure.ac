##
# Prologue
##
AC_INIT([lmt], [3.2.11])
AC_CONFIG_AUX_DIR([config])
AC_CONFIG_HEADERS([config/config.h])
AC_CONFIG_SRCDIR([NEWS])
AC_CANONICAL_SYSTEM
X_AC_EXPAND_INSTALL_DIRS

##
# Automake support
##
AM_INIT_AUTOMAKE(1.9 tar-pax)
AM_MAINTAINER_MODE

##
# Checks for programs
##
AC_PROG_CC
if test "$GCC" = yes; then
  GCCWARN="-Wall -Werror -Werror-implicit-function-declaration"
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
  lua.h
)

##
# Checks for typedefs, structures, and compiler characteristics
## 
AC_C_BIGENDIAN
AC_C_CONST

##
# Checks for standard libraries
##
AC_CHECK_LIB(m, cos)

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
X_AC_CHECK_COND_LIB(lua, luaL_newstate)

##
# Checks for package feature enablement and requisite libs
##
X_AC_MYSQL
X_AC_CEREBRO

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
  lmt.spec \
  Makefile \
  liblsd/Makefile \
  libproc/Makefile \
  liblmt/Makefile \
  liblmtdb/Makefile \
  utils/Makefile \
  cerebro/Makefile \
  cerebro/metric/Makefile \
  cerebro/monitor/Makefile \
  test/Makefile \
  etc/Makefile \
  etc/lmt.conf \
  etc/lmt.conf.5 \
  utils/ltop.1 \
  utils/lmtinit.8 \
  scripts/Makefile \
  scripts/LMT.pm \
  scripts/lmt_agg.cron.8 \
  scripts/lmtsh.8 \
)

AC_CONFIG_FILES([scripts/lmtsh], \
       [chmod +x scripts/lmtsh])
AC_CONFIG_FILES([scripts/lmt_update_fs_agg], \
       [chmod +x scripts/lmt_update_fs_agg])
AC_CONFIG_FILES([scripts/lmt_update_mds_agg], \
       [chmod +x scripts/lmt_update_mds_agg])
AC_CONFIG_FILES([scripts/lmt_update_ost_agg], \
       [chmod +x scripts/lmt_update_ost_agg])
AC_CONFIG_FILES([scripts/lmt_update_other_agg], \
       [chmod +x scripts/lmt_update_other_agg])
AC_CONFIG_FILES([scripts/lmt_update_router_agg], \
       [chmod +x scripts/lmt_update_router_agg])
AC_CONFIG_FILES([scripts/lmt_agg.cron], \
       [chmod +x scripts/lmt_agg.cron])

AC_OUTPUT
