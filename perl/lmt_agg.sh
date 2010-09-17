#!/bin/sh

#===============================================================================
#                                    lmt_agg.sh
#-------------------------------------------------------------------------------
#  Purpose:     Runs the various aggregate generation scripts
#  Notes:       
#       This is part of LMT2 -- the second generation of Lustre Monitoring
#       Tools.
#
#       This is the top-level aggregate script; it invokes all of the 
#       subordinate aggregation scripts.
#
#       Runs: lmt_update_[ost|mds|router_agg]
#             -- Generate hourly aggregate table from raw data
#             lmt_update_other_agg 
#             -- Generate daily|weekly|monthly|yearly agg tables from hourly agg table
#
# Usage:
#      lmt_agg.sh [-keep] [-bindir /path/to/utils] [-logdir /path/to/logs]
#                     [-c /path/to/lmtrc] filesys1 [filesys2 ...]
#
#===============================================================================

# Unless overridden with -bindir...
lmtDir="/usr/sbin"
# Unless overridden with -logdir...
logDir="/var/tmp"
# Unless overridden with -c...
lmtrc="/usr/share/lmt/cron/lmtrc"

keeplog=0

while [ $# -gt 0 ]
do
   case $1 in
      -c)
	  shift
	  lmtrc="-c $1"
	  shift
	  ;;
      -bindir)
	  shift
          lmtDir=$1
          shift
          ;;
      -logdir)
	  shift
          logDir=$1
          shift
          ;;
      -keep)
          keeplog=1
          shift
          ;;
      *)
          FILESYSTEMS="$FILESYSTEMS $1"
          shift
          ;;
   esac
done

if [ -z $FILESYSTEMS ]
then
    echo "*** Error: must specify at least one filesystem name"
    exit 1
fi

if [ ! -r "$lmtDir/lmt_update_ost_agg" ]
then
    echo "***  Error: Could not locate lmt_update_ost_agg script in dir: $lmtDir"
    exit 1
fi

if [ ! $lmtDir="/usr/sbin" ]
then
    if [ -z $PERL5LIB ]
    then
	PERL5LIB="$lmtDir/../lib/perl"
    else
	PERL5LIB="${PERL5LIB}:$lmtDir/../lib/perl"
    fi
    export PERL5LIB
fi

for filesys in $FILESYSTEMS
do
 
  logFile="${logDir}/agg-$filesys-`date +%Y%m%d_%T`"
  touch $logFile

  echo "#####################" >> $logFile
  echo "#    OST - $filesys"   >> $logFile
  echo "#####################" >> $logFile
  echo "" >> $logFile

  echo Updating hourly ost agg table for $filesys >> $logFile
  $lmtDir/lmt_update_ost_agg $lmtrc -f $filesys >> $logFile 2>&1
  echo Updating other ost agg tables for $filesys >> $logFile
  $lmtDir/lmt_update_other_agg $lmtrc -o -f $filesys >> $logFile 2>&1
  echo Updating filesys-level ost tables for $filesys >> $logFile
  $lmtDir/lmt_update_fs_agg $lmtrc -f $filesys >> $logFile 2>&1

  echo "#####################" >> $logFile
  echo "#   ROUTER - $filesys" >> $logFile
  echo "#####################" >> $logFile
  echo "" >> $logFile

  echo Updating hourly router agg table for $filesys >> $logFile
  $lmtDir/lmt_update_router_agg $lmtrc -f $filesys >> $logFile 2>&1
  echo Updating other router agg tables for $filesys >> $logFile
  $lmtDir/lmt_update_other_agg $lmtrc -r -f $filesys >> $logFile 2>&1

  echo "#####################" >> $logFile
  echo "#    MDS - $filesys"   >> $logFile
  echo "#####################" >> $logFile
  echo "" >> $logFile

  echo Updating hourly mds agg table for $filesys >> $logFile
  $lmtDir/lmt_update_mds_agg $lmtrc -f $filesys >> $logFile 2>&1
  echo Updating other mds agg tables for $filesys >> $logFile
  $lmtDir/lmt_update_other_agg $lmtrc -m -f $filesys >> $logFile 2>&1
done

if [ ! -z $logFile ] 
then
   echo "" >> $logFile
   echo "*** Aggregate Table Update Complete ***" >> $logFile

   errs=`grep -i error $logFile 2>&1 | wc -l`
   sqls=`grep -i sql   $logFile 2>&1 | wc -l`

   if [ $keeplog = 0 -a $errs = 0 -a $sqls = 0 ]
   then
      # Delete log file if no errors detected.
      /bin/rm -f $logFile
   fi
fi

exit 0
