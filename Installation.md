Installation has been simplified in LMT version 3.

# Quick Install Instructions

## To get _ltop_ working

* Install **cerebro** and **lmt-server-agent** on Lustre servers (OSS, MDS)
* Install **cerebro** and **lmt-server** on management node
* Restart _cerebrod_ on Lustre servers.
* Run _ltop_ (included in **lmt-server**) on management node.

## To get MySQL working on management node

* Install **mysql-server** package
* Run _mysql_secure_installation_ or equiv, then _msyql -p /usr/share/lmt/mkusers.sql_ as root, after customizing for your site.
* Set up _/etc/lmt/lmt.conf_ 
* Create databases for each file system to be monitored with _lmtinit -a fsname_.
* Restart _cerebrod_.

# Detailed Installation Instructions

## Get ltop Working

The **lmt-server-agent** package contains Cerebro plugins that periodically
read Lustre _/proc_ values, convert them to strings, and push the strings
into the Cerebro monitoring network.
Install this package on Lustre servers, including all MDS, OSS, and
optionally LNET routers, and test that LMT is correctly parsing _/proc_.
Pick an OSS and run this on it (output edited for readability):

        # /usr/sbin/lmtmetric -m ost
        ost: 2;tycho1;0.137269;9.561285;
        lc1-OST0000;112367742;113304682;449470968;1929120176;6344724;4294968016;0;137;0;0;0;694;245;COMPLETE 138/138 0s remaining;
        lc1-OST0008;114812669;115748383;459250676;1929120176;6344724;4294968016;0;137;0;0;0;694;262;COMPLETE 138/138 0s remaining;
        lc1-OST0010;113690420;114627066;454761680;1929120176;6344724;4294968016;0;137;0;0;0;694;240;COMPLETE 138/138 0s remaining;

Run this on your MDS (output edited for readability):

        # /usr/sbin/lmtmetric -m osc
        osc: 1;tycho-mds1;lc1-OST0000;F;lc1-OST0001;F;lc1-OST0002;F;lc1-OST0003;F;lc1-OST0004;F;lc1-OST0005;F;lc1-OST0006;F;...
        # /usr/sbin/lmtmetric -m mdt
        mdt: 1;tycho-mds1;0.015762;1.269578;
        lc1-MDT0000;413833009;465522098;1655332036;1688473892;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;2;0;0...

If there are any errors, open a bug in the LMT issue tracker.

Now restart Cerebro on your Lustre servers:

        # /sbin/service cerebrod restart

Verify that _cerebrod_ is still running on your Lustre servers.
Since LMT plugins are shared libraries running in _cerebrod_'s address space,
a segfault for example could crash _cerebrod_.
If this occurs, open a bug in the LMT issue tracker.

LMT data should now be present on the Cerebro monitoring network.
Install the **lmt-server** package on your LMT server node and verify
that you can see live data with _ltop_:

        $ /usr/bin/ltop
        Filesystem: lc1
            Inodes:    443.956m total,     49.295m used ( 11%),    394.662m free
             Space:    172.188t total,    129.573t used ( 75%),     42.615t free
           Bytes/s:      0.000g read,       0.000g write,                 0 IOPS
           MDops/s:      0 open,        0 close,       0 getattr,       0 setattr
                         0 link,        0 unlink,      0 mkdir,         0 rmdir
                         0 statfs,      0 rename,      0 getxattr
         OST S        OSS   Exp   CR rMB/s wMB/s  IOPS   LOCKS  LGR  LCR %cpu %mem %spc 
        0000 F     tycho1   137    0     0     0     0       0    0    0    1   10   77
        0001 F     tycho2   137    0     0     0     0       0    0    0    0    9   76
        0002 F     tycho3   137    0     0     0     0       0    0    0    0    9   76
        0003 F     tycho4   137    0     0     0     0       0    0    0    0   10   76
        0004 F     tycho5   137    0     0     0     0       0    0    0    1    9   76
        0005 F     tycho6   137    0     0     0     0       0    0    0    0   10   76
        0006 F     tycho7   137    0     0     0     0       0    0    0    0    9   76
        0007 F     tycho8   137    0     0     0     0       0    0    0    0    9   76
        0008 F     tycho1   137    0     0     0     0       0    0    0    1   10   76
        0009 F     tycho2   137    0     0     0     0       0    0    0    0    9   76
        000a F     tycho3   137    0     0     0     0       0    0    0    0    9   76
        000b F     tycho4   137    0     0     0     0       0    0    0    0   10   77
        000c F     tycho5   137    0     0     0     0       0    0    0    1    9   75
        000d F     tycho6   137    0     0     0     0       0    0    0    0   10   76
        000e F     tycho7   137    0     0     0     0       0    0    0    0    9   76
        000f F     tycho8   137    0     0     0     0       0    0    0    0    9   74

If you don't, you may need to debug your Cerebro configuration.
A few things to check are

* Does _cerebro-stat -l_ show \_lmt\_ prefixed metrics?
* Does _cerebro-stat -m metricname_ show live data?
* Are you speaking/listening on the correct interfaces?
* Can the network you are using pass multicast traffic?

Visit the Cerebro sourceforge project site for further cerebro info.

## Get MySQL or MariaDB Working

Install and configure the MySQL/MariaDB server on your LMT server node.
Users starting from scratch with MySQL will want to do something like this:

        # /sbin/service mysqld start
        # msyql_secure_installation

Both MySQL and MariaDB use libraries to connect to the running database. To verify if the required libraries are installed, you can use the following command:

        # ls /usr/lib64/*/plugin/caching_sha2_password.so

If the library is installed, you should see something similar to:

        /usr/lib64/mariadb/plugin/caching_sha2_password.so

If not, you must install *MariaDB-shared* and *MariaDB-devel*. 

For MySQL, you must also install the equivalent connector-c libraries, specifically *mysql-connector-c-shared* and *mysql-connector-c-devel*, to ensure compatibility with your setup.

The database will need two LMT users, one for read-write access to the
database, and one for read-only access.  Use _/usr/share/lmt/mkusers.sql_
as a template, running as a privileged mysql user:

        # Example script for creating LMT MySQL users.
        
        CREATE USER 'lwatchclient'@'localhost';
        GRANT SHOW DATABASES        ON *.* TO 'lwatchclient'@'localhost';
        GRANT SELECT                ON *.* TO 'lwatchclient'@'localhost';
        
        CREATE USER 'lwatchadmin'@'localhost' IDENTIFIED BY 'mypass';
        GRANT SHOW DATABASES        ON *.* TO 'lwatchadmin'@'localhost';
        GRANT SELECT,INSERT,DELETE  ON *.* TO 'lwatchadmin'@'localhost';
        GRANT CREATE,DROP           ON *.* TO 'lwatchadmin'@'localhost';
        
        FLUSH PRIVILEGES;

Next, configure _/etc/lmt/lmt.conf_ with the usernames and passwords
you just added to the database:

        lmt_db_host = nil
        lmt_db_port = 0
        lmt_db_rouser = "lwatchclient"
        lmt_db_ropasswd = nil
        lmt_db_rwuser = "lwatchadmin"
        lmt_db_rwpasswd = "mypass"

Alternatively you can put the password (all by itself) in _/etc/lmt/rwpasswd_,
make that file readable only by root, and use the following line in place
of the _lmt_db_rwuser_ line in your _lmt.conf_:

        f = io.open("/etc/lmt/rwpasswd")
        if (f) then
          lmt_db_rwpasswd = f:read("*l")
          f:close()
        else
          lmt_db_rwpasswd = nil
        end

This restricts write access to the database to only the root user.
Next, add a database for each file system you wish to monitor.
For example if your file system is named _test_:

        # /usr/sbin/lmtinit -a test

lmtinit will use the read-write account from lmt.conf when adding or deleting
databases.  You can list the file systems that have databases with:

        # /usr/sbin/lmtinit -l

_lmtinit_ will use the read-only account from _lmt.conf_ when listing databases.

Restart Cerebro on the lmt-server node so the LMT Cerebro plugin will pick
up the new database info:

        # /sbin/service cerebrod restart

To see if data is being added to your database, run the _lmtsh_ utility a
couple of times and watch the tables that end in __DATA_.
The row count should be increasing:

        # /usr/sbin/lmtsh -f test
        test> t
        Available tables for test:
                                    Table Name   Row Count
                                    EVENT_DATA   0
                                    EVENT_INFO   0
                      FILESYSTEM_AGGREGATE_DAY   225
                     FILESYSTEM_AGGREGATE_HOUR   4977
                    FILESYSTEM_AGGREGATE_MONTH   18
                     FILESYSTEM_AGGREGATE_WEEK   45
                     FILESYSTEM_AGGREGATE_YEAR   9
                               FILESYSTEM_INFO   1
                             MDS_AGGREGATE_DAY   125
                            MDS_AGGREGATE_HOUR   2770
                           MDS_AGGREGATE_MONTH   10
                            MDS_AGGREGATE_WEEK   25
                            MDS_AGGREGATE_YEAR   5
                                      MDS_DATA   398042
                                      MDS_INFO   1
                                  MDS_OPS_DATA   31270065
                             MDS_VARIABLE_INFO   7
                                OPERATION_INFO   81
                                      OSS_DATA   1589675
                                      OSS_INFO   4
                            OSS_INTERFACE_DATA   0
                            OSS_INTERFACE_INFO   0
                             OSS_VARIABLE_INFO   7
                             OST_AGGREGATE_DAY   10800
                            OST_AGGREGATE_HOUR   238896
                           OST_AGGREGATE_MONTH   864
                            OST_AGGREGATE_WEEK   2160
                            OST_AGGREGATE_YEAR   432
                                      OST_DATA   19074487
                                      OST_INFO   48
                                  OST_OPS_DATA   0
                             OST_VARIABLE_INFO   11
                          ROUTER_AGGREGATE_DAY   3
                         ROUTER_AGGREGATE_HOUR   3
                        ROUTER_AGGREGATE_MONTH   3
                         ROUTER_AGGREGATE_WEEK   3
                         ROUTER_AGGREGATE_YEAR   3
                                   ROUTER_DATA   428097
                                   ROUTER_INFO   1
                          ROUTER_VARIABLE_INFO   3
                                TIMESTAMP_INFO   470209
                                       VERSION   0
        test> 
