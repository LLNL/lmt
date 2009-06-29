AC_DEFUN([AC_CEREBRO_MODULE_DIR],
[
  if [[ -d /usr/lib64/cerebro ]]; then
    CEREBRO_MODULE_DIR="/usr/lib64/cerebro"
  elif [[ -d /usr/lib/cerebro ]]; then
    CEREBRO_MODULE_DIR="/usr/lib/cerebro"
  else
    CEREBRO_MODULE_DIR=""
  fi

  AC_MSG_CHECKING([for cerebro module directory])
  AC_ARG_WITH(cerebro_module_dir,
  [  --with-cerebro-module-dir=DIR  cerebro module directory],
  [CEREBRO_MODULE_DIR="${with_cerebro_module_dir}"],
  [
  if test "${CEREBRO_MODULE_DIR}" = ""; then
    echo "Please specify a cerebro module directory"
    exit -1
  elif test ! -d "${CEREBRO_MODULE_DIR}"; then
    echo "Cerebro module directory does not exist"
    exit -1
  fi
  ])
  AC_MSG_RESULT($CEREBRO_MODULE_DIR)
  AC_SUBST(CEREBRO_MODULE_DIR)
])
