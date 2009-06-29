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
my $schema_version = 1.0;
my $fsname;
my $lovname;
my $mountpoint;
my $stripesize = 1048576;
my $stripecount = 2;
my $stripepattern = 0;
my %options = (
        createaccounts => 0,
        createdatabase => 0,
        updaterootpw => 0,
        createtables => 0,
        configfile => "",
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

sub GenPassword
{
    my $i = 0;
    my $passwd = "";
    
    while($i < 12)
    {
        my $char = int(rand(128));
        
        if ($char > 32 && $char < 127)
        {
            $i++;
            if (chr($char) eq "\'" || chr($char) eq "\"" || chr($char) eq "\\")
            {
                $passwd .= chr(92);
            }
            
            $passwd .= chr($char);
        }
    }
    
    return $passwd;
}

sub GenRootPassword
{
    my $passwd = GenPassword();
    print( "update mysql.user set password = PASSWORD\(\'$passwd\'\) where User = \'root\';\n" );
    print( "flush privileges;\n" );
}

sub GenLwatchAccount
{
    my $user = shift(@_);
    my $adminacct = shift(@_);
    my $mysqlhost;

    # For now do not generate passwords for lwatch accounts
    # Just use iptables for security
    #my $passwd = GenPassword();
    my $passwd = "";
    
    if(! $user)
    {
        return;
    }
    
    if($adminacct)
    {
        $mysqlhost = "localhost";
    }
    else
    {
        $mysqlhost = "%";
    }
    
    print( "insert into mysql.user (Host,\n" .
               "User,\n" .
               "Password,\n" .
               "Select_priv,\n" .
               "Insert_priv,\n" .
               "Update_priv,\n" .
               "Delete_priv,\n" .
               "Create_priv,\n" .
               "Drop_priv,\n" .
               "Reload_priv,\n" .
               "Shutdown_priv,\n" .
               "Process_priv,\n" .
               "File_priv,\n" .
               "Grant_priv,\n" .
               "References_priv,\n" .
               "Index_priv,\n" .
               "Alter_priv,\n" .
               "Show_db_priv,\n" .
               "Super_priv,\n" .
               "Create_tmp_table_priv,\n" .
               "Lock_tables_priv,\n" .
               "Execute_priv,\n" .
               "Repl_slave_priv,\n" .
               "Repl_client_priv)\n" .
               #"Create_view_priv,\n" .
               #"Show_view_priv,\n" .
               #"Create_routine_priv,\n" .
               #"Alter_routine_priv,\n" .
               #"Create_user_priv)\n" .
               "values\n" .
               "\(\'$mysqlhost\',\n" .
               "\'$user\',\n" .
               "\'$passwd\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               "\'N\',\n" .
               #"\'N\',\n" .
               #"\'N\',\n" .
               #"\'N\',\n" .
               #"\'N\',\n" .
               #"\'N\',\n" .
               "\'N\');\n"
               );

    # If we know the filesystem name use it.
    if($adminacct)
    {
        print( "insert into mysql.db (Host,\n" .
                   "Db,\n" .
                   "User,\n" .
                   "Select_priv,\n" .
                   "Insert_priv,\n" .
                   "Update_priv,\n" .
                   "Delete_priv,\n" .
                   "Create_priv,\n" .
                   "Drop_priv,\n" .
                   "Grant_priv,\n" .
                   "References_priv,\n" .
                   "Index_priv,\n" .
                   "Alter_priv,\n" .
                   "Create_tmp_table_priv,\n" .
                   "Lock_tables_priv)\n" .
                   #"Create_view_priv,\n" .
                   #"Show_view_priv,\n" .
                   #"Create_routine_priv,\n" .
                   #"Alter_routine_priv,\n" .
                   #"Execute_priv)\n" .
                   "values\n" .
                   "\(\'localhost\',\n" .
                   "\'filesystem%\',\n" .
                   "\'$user\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'N\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\',\n" .
                   "\'Y\');\n"
                   #"\'Y\',\n" .
                   #"\'Y\',\n" .
                   #"\'Y\',\n" .
                   #"\'N\',\n" .
                   #"\'N\');\n"
                   );
    }
    else
    {
        print( "insert into mysql.db (Host,\n" .
                   "Db,\n" .
                   "User,\n" .
                   "Select_priv,\n" .
                   "Insert_priv,\n" .
                   "Update_priv,\n" .
                   "Delete_priv,\n" .
                   "Create_priv,\n" .
                   "Drop_priv,\n" .
                   "Grant_priv,\n" .
                   "References_priv,\n" .
                   "Index_priv,\n" .
                   "Alter_priv,\n" .
                   "Create_tmp_table_priv,\n" .
                   "Lock_tables_priv)\n" .
                   #"Create_view_priv,\n" .
                   #"Show_view_priv,\n" .
                   #"Create_routine_priv,\n" .
                   #"Alter_routine_priv,\n" .
                   #"Execute_priv)\n" .
                   "values\n" .
                   "\(\'%\',\n" .
                   "\'filesystem%\',\n" .
                   "\'$user\',\n" .
                   "\'Y\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   "\'N\',\n" .
                   #"\'N\',\n" .
                   #"\'N\',\n" .
                   #"\'N\',\n" .
                   #"\'N\',\n" .
                   #"\'N\',\n" .
                   "\'N\');\n"
                   );
    }
    print( "flush privileges;\n" );
}

sub CreateFilesystem
{
    my $FILE = $_[0];
    
    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        elsif( $line =~ m/^{/ )
        {
            next;
        }
        
        elsif( $line =~ m/^name\s+(.*)/ )
        {
            $fsname = $1; chomp( $fsname );
        }
        
        elsif( $line =~ m/^mountpoint\s+(.*)/ )
        {
            $mountpoint = $1; chomp( $mountpoint );
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
    
    $lovname = "lov_$fsname";
    
    if( $options{createdatabase} )
    {
        print("create database filesystem_$fsname;\n");
        print("connect filesystem_$fsname;\n");
        
        if( $options{createaccounts} )
        {
            GenLwatchAccount("lwatchadmin", 1);
            GenLwatchAccount("lwatchclient", 0);
        }
        
        if( $options{createtables} )
        {
            CreateTables();
        }
    }
    
    print("insert into FILESYSTEM_INFO (FILESYSTEM_NAME, FILESYSTEM_MOUNT_NAME, SCHEMA_VERSION) values (\'$fsname\', \'$mountpoint\', '$schema_version');\n");
}

sub CreateMDS
{
    my $FILE = $_[0];
    my @hosts;
    my @uuid;
    my $interface;
    my $network;
    my $fstype;
    my $mkfsoptions;
    my $device;
    my $journalsize;
    my $groupupcall;
    
    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        elsif( $line =~ m/^{/ )
        {
            next;
        }
        
        elsif( $line =~ m/^name\s+(.*)/ )
        {
            @hosts = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^uuid\s+(.*)/ )
        {
            @uuid = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^nid\s+(.*?)@(.*)/ )
        {
            $interface = $1; chomp( $interface );
            $network = $2; chomp( $network );
        }
        
        elsif( $line =~ m/^fstype\s+(.*)/ )
        {
            $fstype = $1; chomp( $fstype );
        }
        
        elsif( $line =~ m/^mkfsoptions\s+(.*)/ )
        {
            $mkfsoptions = $1; chomp( $mkfsoptions );
        }
        
        elsif( $line =~ m/^device\s+(.*)/ )
        {
            $device = $1; chomp( $device );
        }
        
        elsif( $line =~ m/^journalsize\s+(.*)/ )
        {
            $journalsize = $1; chomp( $journalsize );
        }
        
        elsif( $line =~ m/^groupupcall\s+(.*)/ )
        {
            $groupupcall = $1; chomp( $groupupcall );
            $groupupcall = "--group_upcall $groupupcall";
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
    
    if (scalar(@uuid) < scalar(@hosts))
    {
        warn("The number of mds names and uuids do not match.\n");
        exit(-1);
    }
    
    for (my $i = 0; $i < scalar(@hosts); $i++)
    {
        my $host = $hosts[$i];
        my $uuid = $uuid[$i];

        # FIXME
        # For now just hardcode FILESYSTEM_ID to 1
        print( "insert into MDS_INFO (FILESYSTEM_ID, MDS_NAME, HOSTNAME, DEVICE_NAME) values (\'1\', \'$uuid\', \'$host\', \'$device\');\n" );
    }
}

sub CreateOST
{
    my $FILE = $_[0];
    my @hosts;
    my @uuid;
    my $interface;
    my $network;
    my $fstype;
    my $mkfsoptions;
    my $mountfsoptions;
    my @device;
    my $journalsize;
    my $skip = 0;
    
    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        elsif( $line =~ m/^{/ )
        {
            next;
        }

        elsif( $line =~ m/^name\s+(.*)/ )
        {
            @hosts = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^uuid\s+(.*)/ )
        {
            @uuid = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^skip\s+(.*)/ )
        {
            $skip = $1;
        }
        
        elsif( $line =~ m/^nid\s+(.*?)@(.*)/ )
        {
            $interface = $1; chomp( $interface );
            $network = $2; chomp( $network );
        }
        
        elsif( $line =~ m/^fstype\s+(.*)/ )
        {
            $fstype = $1; chomp( $fstype );
        }
        
        elsif( $line =~ m/^mkfsoptions\s+(.*)/ )
        {
            $mkfsoptions = $1; chomp( $mkfsoptions );
        }
        
        elsif( $line =~ m/^mountfsoptions\s+(.*)/ )
        {
            $mountfsoptions = $1; chomp( $mountfsoptions );
        }
        
        elsif( $line =~ m/^device\s+(.*)/ )
        {
            @device = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^journalsize\s+(.*)/ )
        {
            $journalsize = $1; chomp( $journalsize );
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
    
    foreach my $tmphost (@hosts)
    {
        # FIXME
        # For now just hardcode FILESYSTEM_ID to 1
        print( "insert into OSS_INFO (FILESYSTEM_ID, HOSTNAME) values (\'1\', \'$tmphost\');\n" );
    }
    
    my $idx = 0;
    my $ostidx = 0;
    while( $idx < scalar(@hosts) && $idx + $skip > 0 )
    {
        my @tmphosts = @hosts[$idx..($idx + $skip - 1)];
        my @tmpuuid = ();
        
        if ( scalar(@uuid) > 1 && scalar(@uuid) == scalar(@hosts) )
        {
            @tmpuuid = @uuid[$idx..($idx + $skip - 1)];
        }
        
        for( my $i = 0; $i < scalar(@device); $i++ )	
        {
            if( ! $device[$i] )
            {
                next;
            }
            
            for( my $j = 0; $j < scalar(@tmphosts); $j++ )
            {
                if( ! $tmphosts[$j] )
                {
                    next;
                }

                # Fix up device name
                my $tmpdevice = $device[$i];
                if( $tmpdevice =~ m#\{NODENAME\}# )
                {
                    $tmpdevice =~ s#\{NODENAME\}#$tmphosts[$j]#;
                }

                # Try to make uuid specification pretty flexible
                my $oststr = "";
                if ( scalar(@uuid) == 1 )
                {
                    $oststr = $uuid[0];
                    if ( $oststr =~ m#\{FSNAME\}# )
                    {
                        $oststr =~ s#\{FSNAME\}#$fsname#;
                    }
                    if ( $oststr =~ m#\{HEXINDEX\}# )
                    {
                        my $tmpidx = sprintf("%04x", $ostidx);
                        $oststr =~ s#\{HEXINDEX\}#$tmpidx#;
                        $ostidx++;
                    }
                    if ( $oststr =~ m#\{INDEX\}# )
                    {
                        my $tmpidx = $i+1;
                        if ( $tmpidx == 1 )
                        {
                            $oststr =~ s#\{INDEX\}##;
                        }
                        else
                        {
                            $oststr =~ s#\{INDEX\}#$tmpidx#;
                        }
                    }
                    if ( $oststr =~ m#\{_INDEX\}# )
                    {
                        my $tmpidx = $i+1;
                        if ( $tmpidx == 1 )
                        {
                            $oststr =~ s#\{_INDEX\}##;
                        }
                        else
                        {
                            $oststr =~ s#\{_INDEX\}#_$tmpidx#;
                        }
                    }
                    if ( $oststr =~ m#\{NODENAME\}# )
                    {
                        $oststr =~ s#\{NODENAME\}#$tmphosts[$j]#;
                    }
                }
                elsif ( scalar(@uuid) == (scalar(@hosts) * scalar(@device)) )
                {
                    if( ! $tmpuuid[$j] )
                    {
                        next;
                    }
                    
                    $oststr = $tmpuuid[$j];
                }
                else
                {
                    warn("The number of ost names and uuids do not match.\n");
                    exit(-1);
                }
                
                print( "insert into OST_INFO (OSS_ID, OST_NAME, HOSTNAME, DEVICE_NAME, OFFLINE) values ((select OSS_ID from OSS_INFO where HOSTNAME=\'$tmphosts[$j]\'), \'$oststr\', \'$tmphosts[$j]\', \'$tmpdevice\', \'0\');\n" );
            }
        }

        $idx += $skip;
    }
}

sub CreateRouter
{
    my $FILE = $_[0];
    my @hosts;
    
    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        elsif( $line =~ m/^{/ )
        {
            next;
        }
        
        elsif( $line =~ m/^name\s+(.*)/ )
        {
            @hosts = Hostlist::expand( $1 );
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
    
    my $cluster = "";
    my $clusteridx = -1;
    foreach my $host ( @hosts )
    {
        my $sth;
        my $tmpcluster = $host;
        $tmpcluster =~ s#\d+$##;
        if( $tmpcluster ne $cluster )
        {
            $cluster = $tmpcluster;
            $clusteridx++;
        }
        print( "insert into ROUTER_INFO (ROUTER_NAME, HOSTNAME, ROUTER_GROUP_ID) values (\'$host\', \'$host\', $clusteridx);\n" );
    }
}

sub CreateClient
{
    my $FILE = $_[0];
    my $name;
    my $nid;
    
    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        elsif( $line =~ m/^{/ )
        {
            next;
        }
        
        elsif( $line =~ m/^name\s+(.*)/ )
        {
            $name = $1; chomp( $name );
        }
        
        elsif( $line =~ m/^nid\s+(.*)/ )
        {
            $nid = $1; chomp( $nid );
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
}

sub CreateOperations
{
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'req_waittime\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'req_qdepth\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'req_active\', \'reqs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'reqbuf_avail\', \'bufs\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_reply\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_getattr\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_setattr\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_read\', \'bytes\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_write\', \'bytes\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_create\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_destroy\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_get_info\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_connect\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_disconnect\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_punch\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_open\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_close\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_statfs\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_san_read\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_san_write\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_sync\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_set_info\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_quotacheck\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ost_quotactl\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_getattr\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_getattr_lock\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_close\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_reint\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_readpage\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_connect\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_disconnect\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_getstatus\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_statfs\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_pin\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_unpin\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_sync\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_done_writing\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_set_info\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_quotacheck\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_quotactl\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_getxattr\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'mds_setxattr\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_enqueue\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_convert\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_cancel\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_bl_callback\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_cp_callback\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'ldlm_gl_callback\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'obd_ping\', \'usec\');\n" );
    print( "insert into OPERATION_INFO (OPERATION_NAME, UNITS) values (\'llog_origin_handle_cancel\', \'usec\');\n" );
}

sub CreateVariables
{
    # OSS vars with no threshholds
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'PCT_MEM\',\'%Mem\', 0);\n" );
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'READ_RATE\',\'Read Rate\', 0);\n" );
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'WRITE_RATE\',\'Write Rate\', 0);\n" );
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'ACTUAL_RATE\',\'Actual Rate\', 0);\n" );

    # OSS vars with threshholds
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1) " .
           "values (\'LINK_STATUS\',\'Link Status\',      1,  1.);\n" );
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_CPU\',     \'%CPU\',           3, 90., 101.);\n" );
    print( "insert into OSS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'ERROR_COUNT\', \'Error Count\',    3,  1., 100.);\n" );

    # OST vars with no threshholds
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'READ_BYTES\',\'Bytes Read\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'WRITE_BYTES\',\'Bytes Written\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'READ_RATE\', \'Read Rate\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'WRITE_RATE\', \'Write Rate\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'KBYTES_FREE\', \'KB Free\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'KBYTES_USED\', \'KB Used\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'INODES_FREE\', \'Inodes Free\', 0);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'INODES_USED\', \'Inodes Used\', 0);\n" );

    # OST vars with threshholds
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_CPU\',    \'%CPU\',    3, 90., 101.);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_KBYTES\', \'%KB\',     3, 95., 100.);\n" );
    print( "insert into OST_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_INODES\', \'%Inodes\', 3, 95., 100.);\n" );

    # MDS vars with no threshholds
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'KBYTES_FREE\',\'KB Free\', 0);\n" );
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'KBYTES_USED\',\'KB Used\', 0);\n" );
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'INODES_FREE\',\'Inodes Free\', 0);\n" );
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'INODES_USED\',\'Inodes Used\', 0);\n" );

    # MDS vars with threshholds
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_CPU\',    \'%CPU\',    3, 90., 101.);\n" );
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_KBYTES\', \'%KB\',     3, 95., 100.);\n" );
    print( "insert into MDS_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_INODES\', \'%Inodes\', 3, 95., 100.);\n" );

    # Router vars with no threshholds
    print( "insert into ROUTER_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'BYTES\',\'Bytes\', 0);\n" );
    print( "insert into ROUTER_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE) values (\'RATE\', \'Rate\', 0);\n" );

    # Router vars with threshholds
    print( "insert into ROUTER_VARIABLE_INFO (VARIABLE_NAME,VARIABLE_LABEL,THRESH_TYPE, THRESH_VAL1, THRESH_VAL2) " .
           "values (\'PCT_CPU\', \'%CPU\', 3, 90., 101.);\n" );
}

sub CreateTables
{
    print(
           "create table FILESYSTEM_INFO (\n" .
               "FILESYSTEM_ID   integer         not null auto_increment,\n" .
               "FILESYSTEM_NAME varchar(128)    not null,\n" .
               "FILESYSTEM_MOUNT_NAME varchar(64) not null,\n" .
               "SCHEMA_VERSION float not null,\n" .
               "primary key (FILESYSTEM_ID),\n" .
               "index(FILESYSTEM_ID)\n" .
           ");\n"
    );

    print(
           "create table OSS_INFO (\n" .
               "OSS_ID          integer         not null auto_increment,\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "HOSTNAME        varchar(128)    not null,\n" .
               "FAILOVERHOST    varchar(128),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "primary key (OSS_ID),\n" .
               "index(OSS_ID)\n" .
           ");\n"
    );

    print(
           "create table OSS_INTERFACE_INFO (\n" .
               "OSS_INTERFACE_ID   integer      not null auto_increment,\n" .
               "OSS_ID             integer      not null,\n" .
               "OSS_INTERFACE_NAME varchar(128) not null,\n" .
               "EXPECTED_RATE      integer,\n" .
               "primary key (OSS_INTERFACE_ID),\n" .
               "index(OSS_INTERFACE_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_INFO (\n" .
               "OST_ID          integer         not null auto_increment,\n" .
               "OSS_ID          integer         not null,\n" .
               "OST_NAME        varchar(128)    not null,\n" .
                # FIXME
                # Add in HOSTNAME for now
               "HOSTNAME        varchar(128)    not null,\n" .
               "OFFLINE         boolean,\n" .
               "DEVICE_NAME     varchar(128),\n" .
               "foreign key(OSS_ID) references OSS_INFO(OSS_ID),\n" .
               "primary key (OST_ID),\n" .
               "index(OST_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_INFO (\n" .
               "MDS_ID          integer         not null auto_increment,\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "MDS_NAME        varchar(128)    not null,\n" .
               "HOSTNAME        varchar(128)    not null,\n" .
               "DEVICE_NAME     varchar(128),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "primary key (MDS_ID),\n" .
               "index(MDS_ID)\n" .
           ");\n"
    );

    print(
           "create table ROUTER_INFO (\n" .
               "ROUTER_ID       integer         not null auto_increment,\n" .
               "ROUTER_NAME     varchar(128)    not null,\n" .
               "HOSTNAME        varchar(128)    not null,\n" .
               "ROUTER_GROUP_ID integer         not null,\n" .
               "primary key(ROUTER_ID),\n" .
               "index(ROUTER_ID)\n" .
           ");\n"
    );

    print(
           "create table OPERATION_INFO (\n" .
               "OPERATION_ID    integer         not null auto_increment,\n" .
               "OPERATION_NAME  varchar(64)     not null unique,\n" .
               "UNITS           varchar(16)     not null,\n" .
               "primary key(OPERATION_ID),\n" .
               "index(OPERATION_ID),\n" .
               "index(OPERATION_NAME)\n" .
           ");"
    );

    print(
           "create table TIMESTAMP_INFO (\n" .
               "TS_ID           int unsigned    not null auto_increment,\n" .
               "TIMESTAMP       datetime        not null,\n" .
               "primary key(TS_ID),\n" .
               "key(TIMESTAMP),\n" .
               "index(TS_ID),\n" .
               "index(TIMESTAMP)\n" .
           ");\n"
    );

    print(
           "create table VERSION (\n" .
               "VERSION_ID      integer         not null auto_increment,\n" .
               "VERSION         varchar(255)    not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "primary key(VERSION_ID),\n" .
               "key(TS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_ID(TS_ID),\n" .
               "index(VERSION_ID),\n" .
               "index(TS_ID)\n" .
           ");\n"
    );

    print(
           "create table EVENT_INFO (\n" .
               "EVENT_ID        integer         not null auto_increment,\n" .
               "EVENT_NAME      varchar(64)     not null,\n" .
               "primary key(EVENT_ID),\n" .
               "index(EVENT_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_VARIABLE_INFO (\n" .
               "VARIABLE_ID     integer         not null auto_increment,\n" .
               "VARIABLE_NAME   varchar(64)     not null,\n" .
               "VARIABLE_LABEL  varchar(64),\n" .
               "THRESH_TYPE     integer,\n" .
               "THRESH_VAL1     float,\n" .
               "THRESH_VAL2     float,\n" .
               "primary key (VARIABLE_ID),\n" .
               "key (VARIABLE_NAME),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );
    
    print(
           "create table OSS_VARIABLE_INFO (\n" .
               "VARIABLE_ID     integer         not null auto_increment,\n" .
               "VARIABLE_NAME   varchar(64)     not null,\n" .
               "VARIABLE_LABEL  varchar(64),\n" .
               "THRESH_TYPE     integer,\n" .
               "THRESH_VAL1     float,\n" .
               "THRESH_VAL2     float,\n" .
               "primary key (VARIABLE_ID),\n" .
               "key (VARIABLE_NAME),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_VARIABLE_INFO (\n" .
               "VARIABLE_ID     integer         not null auto_increment,\n" .
               "VARIABLE_NAME   varchar(64)     not null,\n" .
               "VARIABLE_LABEL  varchar(64),\n" .
               "THRESH_TYPE     integer,\n" .
               "THRESH_VAL1     float,\n" .
               "THRESH_VAL2     float,\n" .
               "primary key (VARIABLE_ID),\n" .
               "key (VARIABLE_NAME),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );
    
    print(
           "create table ROUTER_VARIABLE_INFO (\n" .
               "VARIABLE_ID     integer         not null auto_increment,\n" .
               "VARIABLE_NAME   varchar(64)     not null,\n" .
               "VARIABLE_LABEL  varchar(64),\n" .
               "THRESH_TYPE     integer,\n" .
               "THRESH_VAL1     float,\n" .
               "THRESH_VAL2     float,\n" .
               "primary key (VARIABLE_ID),\n" .
               "key (VARIABLE_NAME),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_DATA (\n" .
               "OST_ID          integer         not null comment \'OST ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "READ_BYTES      bigint                   comment \'READ BYTES\',\n" .
               "WRITE_BYTES     bigint                   comment \'WRITE BYTES\',\n" .
               # FIXME
               # Still need this here as well
               "PCT_CPU         float                    comment \'%CPU\',\n" .
               "KBYTES_FREE     bigint                   comment \'KBYTES FREE\',\n" .
               "KBYTES_USED     bigint                   comment \'KBYTES USED\',\n" .
               "INODES_FREE     bigint                   comment \'INODES FREE\',\n" .
               "INODES_USED     bigint                   comment \'INODES USED\',\n" .
               "primary key (OST_ID,TS_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID)  references TIMESTAMP_INFO(TS_ID),\n" .
               "index(TS_ID),\n" .
               "index(OST_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table OST_OPS_DATA (\n" .
               "OST_ID          integer         not null comment \'OST ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "OPERATION_ID    integer         not null comment \'OP ID\',\n" .
               "SAMPLES         bigint                   comment \'SAMPLES\',\n" .
               "primary key (OST_ID,TS_ID,OPERATION_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID)  references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(OPERATION_ID)  references OPERATION_INFO(OPERATION_ID),\n" .
               "index(TS_ID),\n" .
               "index(OST_ID),\n" .
               "index(OPERATION_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table OSS_DATA (\n" .
               "OSS_ID          integer         not null comment \'OSS ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "PCT_CPU         float                    comment \'%CPU\',\n" .
               "PCT_MEMORY      float                    comment \'%MEM\',\n" .
               "primary key (OSS_ID,TS_ID),\n" .
               "foreign key(OSS_ID) references OSS_INFO(OSS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_ID(TS_ID),\n" .
               "index(TS_ID),\n" .
               "index(OSS_ID)\n" .
           ");\n"
    );

    print(
           "create table OSS_INTERFACE_DATA (\n" .
               "OSS_INTERFACE_ID integer        not null comment \'OSS INTER ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "READ_BYTES      bigint                   comment \'READ BYTES\',\n" .
               "WRITE_BYTES     bigint                   comment \'WRITE BYTES\',\n" .
               "ERROR_COUNT     integer                  comment \'ERR COUNT\',\n" .
               "LINK_STATUS     integer                  comment \'LINK STATUS\',\n" .
               "ACTUAL_RATE     integer                  comment \'ACT RATE\',\n" .
               "primary key (OSS_INTERFACE_ID,TS_ID),\n" .
               "foreign key(OSS_INTERFACE_ID) references OSS_INTERFACE_INFO(OSS_INTERFACE_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_ID(TS_ID),\n" .
               "index(TS_ID),\n" .
               "index(OSS_INTERFACE_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_DATA (\n" .
               "MDS_ID          integer         not null comment \'MDS ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "PCT_CPU         float                    comment \'%CPU\',\n" .
               "KBYTES_FREE     bigint                   comment \'KBYTES FREE\',\n" .
               "KBYTES_USED     bigint                   comment \'KBYTES USED\',\n" .
               "INODES_FREE     bigint                   comment \'INODES FREE\',\n" .
               "INODES_USED     bigint                   comment \'INODES USED\',\n" .
               "primary key (MDS_ID,TS_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "index(TS_ID),\n" .
               "index(MDS_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table MDS_OPS_DATA (\n" .
               "MDS_ID          integer         not null comment \'OST ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "OPERATION_ID    integer         not null comment \'OP ID\',\n" .
               "SAMPLES         bigint                   comment \'SAMPLES\',\n" .
               "SUM             bigint                   comment \'SUM\',\n" .
               "SUMSQUARES      bigint                   comment \'SUM SQUARES\',\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID)  references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(OPERATION_ID)  references OPERATION_INFO(OPERATION_ID),\n" .
               "index(TS_ID),\n" .
               "index(MDS_ID),\n" .
               "index(OPERATION_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );
    
    print(
           "create table ROUTER_DATA (\n" .
               "ROUTER_ID       integer         not null comment \'ROUTER ID\',\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "BYTES           bigint                   comment \'BYTES\',\n" .
               "PCT_CPU         float                    comment \'%CPU\',\n" .
               "primary key (ROUTER_ID,TS_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );
    
    print(
           "create table EVENT_DATA (\n" .
               "EVENT_ID        integer         not null,\n" .
               "TS_ID           int unsigned    not null comment \'TS ID\',\n" .
               "OSS_ID          integer                  comment \'OSS ID\',\n" .
               "OST_ID          integer                  comment \'OST ID\',\n" .
               "MDS_ID          integer                  comment \'MDS ID\',\n" .
               "ROUTER_ID       integer                  comment \'ROUTER ID\',\n" .
               "COMMENT         varchar(4096)            comment \'COMMENT\',\n" .
               "foreign key(EVENT_ID) references EVENT_INFO(EVENT_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "index(EVENT_ID),\n" .
               "index(TS_ID),\n" .
               "index(OST_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table OST_AGGREGATE_HOUR (\n" .
               "OST_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (OST_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(OST_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table OST_AGGREGATE_DAY (\n" .
               "OST_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (OST_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(OST_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_AGGREGATE_WEEK (\n" .
               "OST_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (OST_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(OST_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_AGGREGATE_MONTH (\n" .
               "OST_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (OST_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(OST_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table OST_AGGREGATE_YEAR (\n" .
               "OST_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (OST_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(OST_ID) references OST_INFO(OST_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(OST_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table ROUTER_AGGREGATE_HOUR (\n" .
               "ROUTER_ID       integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (ROUTER_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references ROUTER_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table ROUTER_AGGREGATE_DAY (\n" .
               "ROUTER_ID       integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (ROUTER_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references ROUTER_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table ROUTER_AGGREGATE_WEEK (\n" .
               "ROUTER_ID       integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (ROUTER_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references ROUTER_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table ROUTER_AGGREGATE_MONTH (\n" .
               "ROUTER_ID       integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (ROUTER_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references ROUTER_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table ROUTER_AGGREGATE_YEAR (\n" .
               "ROUTER_ID       integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (ROUTER_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(ROUTER_ID) references ROUTER_INFO(ROUTER_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references ROUTER_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(ROUTER_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_AGGREGATE_HOUR (\n" .
               "MDS_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (MDS_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references MDS_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(MDS_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table MDS_AGGREGATE_DAY (\n" .
               "MDS_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (MDS_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references MDS_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(MDS_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_AGGREGATE_WEEK (\n" .
               "MDS_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (MDS_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references MDS_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(MDS_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table MDS_AGGREGATE_MONTH (\n" .
               "MDS_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (MDS_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references MDS_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(MDS_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
   );

    print(
           "create table MDS_AGGREGATE_YEAR (\n" .
               "MDS_ID          integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "AGGREGATE       float,\n" .
               "MINVAL          float,\n" .
               "MAXVAL          float,\n" .
               "AVERAGE         float,\n" .
               "NUM_SAMPLES     integer,\n" .
               "primary key (MDS_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(MDS_ID) references MDS_INFO(MDS_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references MDS_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(MDS_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table FILESYSTEM_AGGREGATE_HOUR (\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "OST_AGGREGATE   float,\n" .
               "OST_MINVAL      float,\n" .
               "OST_MAXVAL      float,\n" .
               "OST_AVERAGE     float,\n" .
               "primary key (FILESYSTEM_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(FILESYSTEM_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ") MAX_ROWS=2000000000;\n"
    );

    print(
           "create table FILESYSTEM_AGGREGATE_DAY (\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "OST_AGGREGATE   float,\n" .
               "OST_MINVAL      float,\n" .
               "OST_MAXVAL      float,\n" .
               "OST_AVERAGE     float,\n" .
               "primary key (FILESYSTEM_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(FILESYSTEM_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table FILESYSTEM_AGGREGATE_WEEK (\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "OST_AGGREGATE   float,\n" .
               "OST_MINVAL      float,\n" .
               "OST_MAXVAL      float,\n" .
               "OST_AVERAGE     float,\n" .
               "primary key (FILESYSTEM_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(FILESYSTEM_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table FILESYSTEM_AGGREGATE_MONTH (\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "OST_AGGREGATE   float,\n" .
               "OST_MINVAL      float,\n" .
               "OST_MAXVAL      float,\n" .
               "OST_AVERAGE     float,\n" .
               "primary key (FILESYSTEM_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(FILESYSTEM_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );

    print(
           "create table FILESYSTEM_AGGREGATE_YEAR (\n" .
               "FILESYSTEM_ID   integer         not null,\n" .
               "TS_ID           int unsigned    not null,\n" .
               "VARIABLE_ID     integer         not null,\n" .
               "OST_AGGREGATE   float,\n" .
               "OST_MINVAL      float,\n" .
               "OST_MAXVAL      float,\n" .
               "OST_AVERAGE     float,\n" .
               "primary key (FILESYSTEM_ID,TS_ID,VARIABLE_ID),\n" .
               "foreign key(FILESYSTEM_ID) references FILESYSTEM_INFO(FILESYSTEM_ID),\n" .
               "foreign key(TS_ID) references TIMESTAMP_INFO(TS_ID),\n" .
               "foreign key(VARIABLE_ID) references OST_VARIABLE_INFO(VARIABLE_ID),\n" .
               "index(FILESYSTEM_ID),\n" .
               "index(TS_ID),\n" .
               "index(VARIABLE_ID)\n" .
           ");\n"
    );
}

sub ReadConfigFile
{
    my $file = $options{configfile};
    my $FILE;
    
    if( ! $file || ! -e $file )
    {
        return;
    }
    
    open( $FILE, "<", $file );

    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
        }
        
        if( $line =~ /^filesystem/ )
        {
            CreateFilesystem( $FILE );
        }
        
        elsif( $line =~ /^mds/ )
        {
            CreateMDS( $FILE );
        }
        
        elsif( $line =~ /^ost/ )
        {
            CreateOST( $FILE );
        }
        
        elsif( $line =~ /^router/ )
        {
            CreateRouter( $FILE );
        }
        
        elsif( $line =~ /^client/ )
        {
            CreateClient( $FILE );
        }
    }
}

### Main

# Seed the random number generator
srand( time() ^ ($$ + ($$ << 15)) );

for( my $i = 0; $i < scalar( @ARGV ); $i++ )
{
    my $arg = $ARGV[$i];
    my $val = "";
    
    if( ($i + 1) < scalar( @ARGV )  )
    {
        $val = $ARGV[$i+1];
    }
    
    if( $arg eq "-a" )
    {
        $options{createaccounts} = 1;
    }
    elsif( $arg eq "-d" )
    {
        $options{createdatabase} = 1;
    }
    elsif( $arg eq "-r" )
    {
        $options{updaterootpw} = 1;
    }
    elsif( $arg eq "-t" )
    {
        $options{createtables} = 1;
    }
    elsif( $arg eq "-f" )
    {
        $options{configfile} = $val;
        $i++;
    }
}

if($options{updaterootpw})
{
    GenRootPassword();
}

if(!$options{createdatabase} && $options{createaccounts})
{
    GenLwatchAccount("lwatchadmin", 1);
    GenLwatchAccount("lwatchclient", 0);
}

if(!$options{createdatabase} && $options{createtables})
{
    CreateTables();
}

if(-e $options{configfile})
{
    ReadConfigFile();
    CreateOperations();
    CreateVariables();
}

#
# vi:tabstop=4 shiftwidth=4 expandtab
#
