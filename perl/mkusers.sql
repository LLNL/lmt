# Example script for creating LMT MySQL users.

CREATE USER 'lwatchclient';
GRANT SHOW DATABASES ON *.* TO 'lwatchclient';
GRANT SELECT ON         *.* TO 'lwatchclient';

CREATE USER 'lwatchadmin'@'localhost' IDENTIFIED BY 'mypass';
GRANT SHOW DATABASES ON *.* TO 'lwatchadmin'@'localhost';
GRANT SELECT,INSERT  ON *.* TO 'lwatchadmin'@'localhost';
GRANT CREATE,DROP    ON *.* TO 'lwatchadmin'@'localhost';

FLUSH PRIVILEGES;
