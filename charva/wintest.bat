@echo off
rem "This batchfile runs the Charva test program."
rem "It is intended to be run from the '%CHARVA_HOME%'
rem "directory in a DOS command shell."
rem "It expects to find Terminal.dll in the directory
rem '%CHARVA_HOME%\c\lib', and the charva.jar file in the directory
rem '%CHARVA_HOME%\java\lib'.
rem "Last Modified: 2006/8/14 by Rob Pitman <rob@pitman.co.za>"

rem Check that we are in the right directory to run this script.
if not exist "c\lib\Terminal.dll" goto noDLL
rem if not exist "java\lib\charva.jar" goto noJAR

rem JAVA_HOME must be set to the JDK or JRE installation directory 
rem (for example, C:\jdk1.4 or C:\jre1.4)
rem set JAVA_HOME=C:\j2sdk1.4.2
if not exist %JAVA_HOME% goto noJAVA_HOME

rem Uncomment the next line to log keystrokes and debug key-mappings 
rem (the script file is "script.charva.txt").
rem set TEST_OPTS="-Dcharva.script.record=script.charva.txt"

rem Uncomment the following line to play back a script that was previously
rem recorded using "charva.script.record".
rem This line will cause the script to loop three times, at a speeded-up rate (5 times the speed of the recording).
rem ACTUALLY THIS DOESN'T SEEM TO WORK ON WINDOWS. IT WORKS ON LINUX.
rem set TEST_OPTS="-Dcharva.script.playbackFile=script.charva.txt -Dcharva.script.playbackLoops=3 -Dcharva.script.playbackRate=5"

rem Uncomment the following option to test for memory leaks.
rem set TEST_OPTS=%TEST_OPTS% -Xrunhprof:heap=sites

rem Comment out the following option to disable colors.
set TEST_OPTS=%TEST_OPTS% -Dcharva.color=1

rem Uncomment the following line if you want to debug the application
rem using an IDE such as IntelliJ IDEA (I believe that other IDEs such
rem as NetBeans and JBuilder have the same capability).
rem set TEST_OPTS=%TEST_OPTS% -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005


%JAVA_HOME%\bin\java %TEST_OPTS% -cp .;test/classes;java/lib/commons-logging.jar;java/lib/log4j-1.2.8.jar;java/dist/lib/charva.jar -Djava.library.path=c\lib tutorial.charva.Tutorial
goto end


:noJAVA_HOME
echo The JAVA_HOME environment variable is not set!
goto end

:noDLL
echo The Terminal.dll library is not available!
goto end

:noJAR
echo The charva.jar file is not available!
goto end

:end
