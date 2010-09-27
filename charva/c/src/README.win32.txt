BUILDING Terminal.dll AND RUNNING THE CHARVA TUTORIAL ON Win32
==============================================================

7 August, 2006.

The file "Terminal.dll", built using MinGW32 version 4.1 and PDCurses 2.6,
is included in the directory %CHARVA_HOME%\c\lib. It may work for you. If not,
you can build a version for your Win32 system using the procedure below.

BUILDING Terminal.dll
=====================
First, download and install the following packages, with the specified
versions or later:

    MinGW32 version 4.1. with all recommended updates
	( http://sourceforge.net/projects/mingw/ )

    MSYS version 1.0.8
	( http://www.mingw.org/msys.shtml )

    PDCurses 2.6
	( http://sourceforge.net/projects/pdcurses/ )


On my system (I have tested this on Windows 98 Second Edition, Windows 2000 and
Windows XP professional):

    MinGW is installed in C:\MinGW

    PDCurses is installed in C:\PDCurses

    CHARVA is installed in C:\charva

    JAVA_HOME is C:\j2sdk1.4.2

(you must change the following steps according to your setup).

I build the PDCurses library as follows, using the bash shell of MSYS (just
click on the MSYS shortcut on the windows desktop to start the bash shell):

	$ cd /c/PDCurses
	$ mkdir obj
	$ cp win32/gccwin32.mak obj/
	$ cd obj

	(Edit gccwin32.mak and change the values of PDCURSES_HOME and SHELL as appropriate)

	$ make -f gccwin32.mak pdcurses.a

(The above commands build the library file "pdcurses.a" in the directory
/c/PDCurses/obj).

Then, to build the Terminal.dll library:

	$ cd /c/charva/c/src

    (Edit Makefile.win32.txt and set JAVA_HOME and PDCURSES_HOME as appropriate)
    
	$ make -f Makefile.win32.txt
	....
	....
	c:\MinGW\bin\dllwrap.exe: no export definition file provided
	creating one, but that may not be what you want

    (YOU CAN IGNORE THE ABOVE ERROR MESSAGE, it doesn't appear to cause a problem).



RUNNING THE TUTORIAL PROGRAM ON Win32
=====================================
Start up a DOS prompt or command shell or whatever you call it.

	C:> cd C:\charva
	C:\charva> wintest.bat


The tutorial application works OK on Windows 2000 and Windows XP. Note that
the "underline" text attribute doesn't work on Win32; it seems this
is a bug in PDCurses.

Rob.
