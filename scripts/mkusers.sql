# Example script for creating LMT MySQL users.
#
# To improve security, 
# 1. change the lwatchadmin password!
# 2. instead of granting to *.*, grant privileges to to filesystem_yourfs.*
#
# To run lwatch remotely, add 'lwatchclient'@'hostname' users for each host.
#
# Assumes /etc/lmt/lmt.conf contains:
#   lmt_db_rouser = "lwatchclient"
#   lmt_db_ropasswd = nil
CREATE USER 'lwatchclient'@'localhost';
GRANT SHOW DATABASES        ON *.* TO 'lwatchclient'@'localhost';
GRANT SELECT                ON *.* TO 'lwatchclient'@'localhost';

# Assumes /etc/lmt/lmt.conf contains:
#   lmt_db_rwuser = "lwatchadmin"
#   lmt_db_rwpasswd = "mypass"  -- or use the separate file example
CREATE USER 'lwatchadmin'@'localhost' IDENTIFIED BY 'mypass';
GRANT SHOW DATABASES        ON *.* TO 'lwatchadmin'@'localhost';
GRANT SELECT,INSERT,DELETE  ON *.* TO 'lwatchadmin'@'localhost';
GRANT CREATE,DROP           ON *.* TO 'lwatchadmin'@'localhost';

FLUSH PRIVILEGES;
