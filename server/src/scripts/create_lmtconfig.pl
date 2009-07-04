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
use Getopt::Long qw/ :config posix_default no_ignore_case/;
use File::Basename;

my %ostlist;
my $schema_version = 1.1;
my $mountpoint;
my $stripesize = 1048576;
my $stripecount = 2;
my $stripepattern = 0;
my %options = (
        createaccounts => 0,
        createdatabase => "",
        updaterootpw => 0,
        configfile => "",
        ldevfile => "/etc/ldev.conf",
    );
my $path_schema = "/usr/share/lmt/etc/create_schema-1.1.sql";
my $prog = basename($0);
my $usage = <<EOF;
Usage: $prog [OPTIONS]...

  -r, --update-rootpw         Generate SQL to set root to random password
  -a, --create-accounts       Generate SQL to create LMT accounts
  -d, --create-database FS    Generate SQL to populate database for FS
  -f, --config-file FILE      Specify filesystem config instead of ldev.conf
  -F, --ldev-file FILE        Override path to /etc/ldev.conf

NOTE: FS configuration is by default taken from /etc/ldev.conf.
You may override this path with -F or you may use the original LMT2
config file syntax by using -f.  See $prog(8) for more information.
EOF

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

sub cat
{
    my ($name) = @_;

    open FILE, "<$name" or die "cannot open $name: $!";
    while (<FILE>) {
        print;
    }
    close FILE; 
}

sub sql_filesystem
{
    print("insert into FILESYSTEM_INFO (FILESYSTEM_NAME, FILESYSTEM_MOUNT_NAME, SCHEMA_VERSION) values (\'$options{createdatabase}\', \'\', '$schema_version');\n");
}

sub sql_mds
{
    my ($host, $uuid, $device) = @_;
    # FIXME: for now just hardcode FILESYSTEM_ID to 1
    print( "insert into MDS_INFO (FILESYSTEM_ID, MDS_NAME, HOSTNAME, DEVICE_NAME) values (\'1\', \'$uuid\', \'$host\', \'$device\');\n" );
}

sub sql_oss
{
    my ($hostname) = @_;
    # FIXME: for now just hardcode FILESYSTEM_ID to 1
    print( "insert into OSS_INFO (FILESYSTEM_ID, HOSTNAME) values (\'1\', \'$hostname\');\n" );
}

sub sql_ost
{
    my ($hostname, $oststr, $device) = @_;

    print( "insert into OST_INFO (OSS_ID, OST_NAME, HOSTNAME, DEVICE_NAME, OFFLINE) values ((select OSS_ID from OSS_INFO where HOSTNAME=\'$hostname\'), \'$oststr\', \'$hostname\', \'$device\', \'0\');\n" );
}

sub sql_router
{
    my ($host, $clusteridx) = @_;

    print( "insert into ROUTER_INFO (ROUTER_NAME, HOSTNAME, ROUTER_GROUP_ID) values (\'$host\', \'$host\', $clusteridx);\n" );
}

sub CreateMDS
{
    my $FILE = $_[0];
    my @hosts;
    my @uuid;
    my $device;
    
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
            @hosts = hostlist_expand( $1 );
        }
        
        elsif( $line =~ m/^uuid\s+(.*)/ )
        {
            @uuid = hostlist_expand( $1 );
        }
        
        elsif( $line =~ m/^device\s+(.*)/ )
        {
            $device = $1; chomp( $device );
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
        sql_mds($host, $uuid, $device);
    }
}

sub CreateOST
{
    my $FILE = $_[0];
    my @hosts;
    my @uuid;
    my @device;
    my $skip = 0;
    my $startidx = 0;
    
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
            @hosts = hostlist_expand( $1 );
        }
        
        elsif( $line =~ m/^uuid\s+(.*)/ )
        {
            @uuid = hostlist_expand( $1 );
        }

        elsif( $line =~ m/^startidx\s+(.*)/ )
        {
            $startidx = $1; chomp( $startidx );
        }
      
        elsif( $line =~ m/^skip\s+(.*)/ )
        {
            $skip = $1;
        }
        
        elsif( $line =~ m/^device\s+(.*)/ )
        {
            @device = hostlist_expand( $1 );
        }
        
        elsif( $line =~ m/^}/ )
        {
            last;
        }
    }
    
    foreach (@hosts)
    {
        sql_oss($_);
    }
    
    my $idx = 0;
    my $ostidx = $startidx;
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
                        $oststr =~ s#\{FSNAME\}#$options{createdatabase}#;
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
               
                sql_ost($tmphosts[$j], $oststr, $tmpdevice); 
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
            @hosts = hostlist_expand( $1 );
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
        sql_router($host, $clusteridx);
    }
}

sub CreateFromConfigFile
{
    my $file = $options{configfile};
    my $FILE;
    
    open( $FILE, "<", $file ) or die "$file: $!";

    while( my $line = <$FILE> )
    {
        $line = lrtrim( $line );

        # Skip over comments
        if( $line =~ m/^#/ )
        {
            next;
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
    }
 
}

sub CreateFromLdevFile
{
    my $line = 0;
    my %l2f = ();
    my %label2local = ();
    my %label2dev = ();
    my @oss = ();

    open (CONF, "< $options{ldevfile}") or die "$options{ldevfile}: $!\n";
    while (<CONF>) {
        $line++;
        s/#.*//;
        next if (/^(\s)*$/);
        chomp;
        my ($local, $foreign, $label, $dev, $j) = split;
        $l2f{$local} = $foreign;

        $label2dev{$label} = $dev;
        $label2local{$label} = $local;
    }
    close CONF;

    foreach (sort keys %label2local) {
        my ($fsname, $type, $hexindex) = parse_label($_);

        next if ($fsname ne $options{createdatabase});

        my $local = $label2local{$_};
        my $dev = $label2dev{$_};
        my $label = $_;

        if ($type eq "ost") {
            push @oss, $label2local{$_};
            sql_ost($local, $label, $dev);
        } elsif ($type eq "mdt") {
            sql_mds($local, $label, $dev);
        }
    }
    map { sql_oss ($_) } uniq (sortn (@oss));
}

# hostlist_expand()
# turn a hostname range into a list of hostnames. Try to autodetect whether
# a quadrics-style range or a normal hostname range was passed in.
# Borrowed from Hostlist.pm (gendersllnl) by Chu and Garlick (GPL).
#
sub hostlist_expand
{
        my ($list) = @_;

        if ($list =~ /\[/ && $list !~ /[^[]*\[.+\]/) {
            # Handle case of no closing bracket - just return
            return ($list);
        }

        # matching "[" "]" pair with stuff inside will be considered a quadrics
        # range:
        if ($list =~ /[^[]*\[.+\]/) {
                # quadrics ranges are separated by whitespace in RMS -
                # try to support that here
                $list =~ s/\s+/,/g;

                # 
                # Replace ',' chars internal to "[]" with ':"
                #
                while ($list =~ s/(\[[^\]]*),([^\[]*\])/$1:$2/) {}

                return map { expand_quadrics_range($_) } split /,/, $list;

        } else {
                return map { 
                            s/(\w*?)(\d+)-(\1|)(\d+)/"$2".."$4"/ 
                                               || 
                                          s/(.+)/""/; 
                            map {"$1$_"} eval; 
                           } split /,/, $list;
        }
}
                        
# expand_quadrics_range
#
# expand nodelist in quadrics form 
# Borrowed from Hostlist.pm (gendersllnl) by Chu and Garlick (GPL).
#
sub expand_quadrics_range 
{
        my ($list) = @_;
        my ($pfx, $ranges, $suffix) = split(/[\[\]]/, $list, 3);

        return $list if (!defined $ranges);

        return map {"$pfx$_$suffix"} 
                   map { s/^(\d+)-(\d+)$/"$1".."$2"/ ? eval : $_ } 
                       split(/,|:/, $ranges);
}

sub parse_label
{
    /(\w+)-(OST|MDT|MGT)([0-9a-fA-F]{4})/;
    my $fsname = $1;
    my $type = $2; $type =~ tr/A-Z/a-z/;
    my $hexindex = $3;

    return ($fsname, $type, $hexindex);
}

sub uniq
{
    my %saw = ();
    return grep(!$saw{$_}++, @_);
}

# Sort a group of alphanumeric strings by the last group of digits on
# those strings, if such exists (good for numerically suffixed host lists).
# Borrowed from genders.pl [credit: Al Chu].
sub sortn
{
    map {$$_[0]} sort {($$a[1]||0)<=>($$b[1]||0)} map {[$_,/(\d*)$/]} @_;
}

sub usage
{
    print STDERR "$usage";
    exit 0;
}

### Main

# Seed the random number generator
srand( time() ^ ($$ + ($$ << 15)) );

usage() if !@ARGV;
my $rc = GetOptions (
    "update-rootpw|r!"        => \$options{updaterootpw},
    "create-accounts|a!"      => \$options{createaccounts},
    "create-database|d=s"     => \$options{createdatabase},
    "config-file|f=s"         => \$options{configfile},
    "ldev-file|F=s"           => \$options{ldevfile},
);
usage() if !$rc || @ARGV;

if($options{updaterootpw})
{
    GenRootPassword();
}
if($options{createaccounts})
{
    GenLwatchAccount("lwatchadmin", 1);
    GenLwatchAccount("lwatchclient", 0);
}
if($options{createdatabase})
{
    print("create database filesystem_$options{createdatabase};\n");
    print("connect filesystem_$options{createdatabase};\n");
    sql_filesystem();

    cat($path_schema);

    if($options{configfile})
    {
        CreateFromConfigFile();
    }
    else
    {
        CreateFromLdevFile();
    }
}

#
# vi:tabstop=4 shiftwidth=4 expandtab
#
