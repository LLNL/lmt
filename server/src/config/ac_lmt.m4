AC_DEFUN([AC_LMT],
[
  AC_MSG_CHECKING([for whether to build lmt modules])
  AC_ARG_WITH([lmt],
    AC_HELP_STRING([--with-lmt], [Build lmt modules]),
    [ case "$withval" in
        no)  ac_lmt_test=no ;;
        yes) ac_lmt_test=yes ;;
        *)   ac_lmt_test=yes ;;
      esac
    ]
  )
  AC_MSG_RESULT([${ac_lmt_test=yes}])

  if test "$ac_lmt_test" = "yes"; then
     AC_DEFINE([WITH_LMT], [1], [Define if you want the lmt module.])
     MANPAGE_LMT=0
     ac_with_lmt=yes
  else
     MANPAGE_LMT=0
     ac_with_lmt=no
  fi

  AC_SUBST(MANPAGE_LMT)
])
