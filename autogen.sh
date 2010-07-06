#!/bin/sh

echo "Running aclocal ... "
aclocal --force -I config
echo "Running libtoolize ... "
libtoolize --automake --copy --force
echo "Running autoheader ... "
autoheader --force
echo "Running automake ... "
automake --copy --add-missing --force
echo "Running autoconf ... "
autoconf --force
echo "Cleaning up ..."
mv aclocal.m4 config/
rm -rf autom4te.cache
echo "Now run ./configure to configure lmt for your environment."

