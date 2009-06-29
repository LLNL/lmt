#!/usr/bin/perl
# =============================================================================
#  Copyright (c) 2007, The Regents of the University of California.
#  Produced at the Lawrence Livermore National Laboratory.
#  Written by C. Morrone, H. Wartens, P. Spencer, N. O'Neill, J. Long
#  UCRL-CODE-232438.
#  All rights reserved.
#
#  This file is part of LMT-2. For details, see
#  http://sourceforge.net/projects/lmt/.
#
#  Please also read Our Notice and GNU General Public License, available in the
#  COPYING file in the source distribution.
#
#  This program is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License (as published by the Free
#  Software Foundation) version 2, dated June 1991.
#
#  This program is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the terms and conditions of the GNU
#  General Public License for more details.
#
#  You should have received a copy of the GNU General Public License along with
#  this program; if not, write to the Free Software Foundation, Inc., 59 Temple
#  Place, Suite 330, Boston, MA 02111-1307 USA
# =============================================================================

$| = 1;

use strict;
use Hostlist;

my %ostlist;
my $schema_version = 1.1;
my $fsname;
my $lovname;
my $mountpoint;
my $stripesize = 1048576;
my $stripecount = 2;
my $stripepattern = 0;
my $old_schema_version = 0;
my %options = (
        filesystem => "",
        user => "lwatchadmin",
    );

sub usage
{
    print( STDERR "$0 [-t] -f <config_file>\n" );
}

sub dbconnect
{
}

sub ltrim
{
    my $arg;
    
    if( scalar( @_ ) <= 0 )
    {
        return "";
    }
    
    $arg = $_[0];
    $arg =~ s#^\s+##;
    return $arg;
}

sub rtrim
{
    my $arg;
    
    if( scalar( @_ ) <= 0 )
    {
        return "";
    }
    
    $arg = $_[0];
    $arg =~ s#\s+$##;
    return $arg;
}

sub lrtrim
{
    my $arg;
    
    if( scalar( @_ ) <= 0 )
    {
        return "";
    }
    
    $arg = $_[0];
    $arg =~ s#^\s+##;
    $arg =~ s#\s+$##;
    return $arg;
}

#
#
sub UpdateDbVersion
{
}

#
#
sub UpdateOperations
{
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'req_waittime\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'req_qdepth\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'req_active\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'reqbuf_avail\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_reply\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_getattr\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_setattr\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_read\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_write\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_create\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_destroy\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_get_info\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_connect\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_disconnect\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_punch\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_open\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_close\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_statfs\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_san_read\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_san_write\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_sync\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_set_info\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_quotacheck\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ost_quotactl\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_getattr\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_getattr_lock\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_close\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_reint\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_readpage\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_connect\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_disconnect\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_getstatus\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_statfs\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_pin\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_unpin\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_sync\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_done_writing\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_set_info\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_quotacheck\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_quotactl\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_getxattr\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'mds_setxattr\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_enqueue\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_convert\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_cancel\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_bl_callback\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_cp_callback\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'ldlm_gl_callback\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'obd_ping\';\n" );
    print( "delete from OPERATION_INFO where OPERATION_NAME=\'llog_origin_handle_cancel\';\n" );

    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'open\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'close\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mknod\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'link\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'unlink\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mkdir\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'rmdir\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'rename\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'getxattr\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'setxattr\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'iocontrol\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'get_info\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'set_info_async\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'attach\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'detach\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'setup\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'precleanup\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'cleanup\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'process_config\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'postrecov\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'add_conn\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'del_conn\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'connect\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'reconnect\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'disconnect\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'statfs\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'statfs_async\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'packmd\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'unpackmd\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'checkmd\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'preallocate\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'precreate\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'create\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'destroy\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'setattr\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'setattr_async\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'getattr\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'getattr_async\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'brw\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'brw_async\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'prep_async_page\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'reget_short_lock\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'release_short_lock\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'queue_async_io\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'queue_group_io\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'trigger_group_io\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'set_async_flags\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'teardown_async_page\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'merge_lvb\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'adjust_kms\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'punch\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'sync\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'migrate\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'copy\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'iterate\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'preprw\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'commitrw\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'enqueue\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'match\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'change_cbdata\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'cancel\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'cancel_unused\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'join_lru\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'init_export\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'destroy_export\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'extent_calc\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'llog_init\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'llog_finish\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'pin\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'unpin\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'import_event\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'notify\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'health_check\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'quotacheck\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'quotactl\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'quota_adjust_quint\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ping\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'register_page_removal_cb\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'unregister_page_removal_cb\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'register_lock_cancel_cb\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'unregister_lock_cancel_cb\', \'reqs\');\n" );

    print( "update FILESYSTEM_INFO set SCHEMA_VERSION=\'1.1\' where FILESYSTEM_NAME=\'$options{filesystem}\' and SCHEMA_VERSION=\'1\';\n" );
}

### Main

for( my $i = 0; $i < scalar( @ARGV ); $i++ )
{
    my $arg = $ARGV[$i];
    my $val = "";
    
    if( ($i + 1) < scalar( @ARGV )  )
    {
        $val = $ARGV[$i+1];
    }
    
    if( $arg eq "-f" )
    {
        $options{filesystem} = $val;
        $i++;
    }
    elsif( $arg eq "-u" )
    {
        $options{user} = $val;
        $i++;
    }
}

if ( ! $options{filesystem} )
{
    print( STDERR "Please specify a filesystem to upgrade...\n" );
    exit -1;
}

$old_schema_version = `mysql --skip-column-names -u $options{user} filesystem_$options{filesystem} -e\"select SCHEMA_VERSION from FILESYSTEM_INFO;\"`;
chomp( $old_schema_version );
if ( $old_schema_version != "1" )
{
    exit -1;
}

UpdateOperations();

#
# vi:tabstop=4 shiftwidth=4 expandtab
#
