
CLASSPATH=/home/garlick/work/lmt/lmt/client/java:/home/garlick/work/lmt/lmt/client/java/jars/commons-logging.jar:/home/garlick/work/lmt/lmt/client/java/jars/jargs.jar:/home/garlick/work/lmt/lmt/client/java/jars/jcommon-1.0.6.jar:/home/garlick/work/lmt/lmt/client/java/jars/jfreechart-1.0.5.jar:/home/garlick/work/lmt/lmt/client/java/jars/mysql-connector-java-5.0.4-bin.jar:/home/garlick/work/lmt/lmt/client/charva/java/dist/lib/charva.jar

CLASSES= Lstat.class  \

SOURCES=*.java

.SUFFIXES: .java .class

this:	$(CLASSES)

all: 
	@echo Compiling *.java
	@/usr/bin/javac -g -classpath $(CLASSPATH) *.java

clean:
	rm -f *.class

.java.class:
	@echo Compiling $<
	@/usr/bin/javac -g -classpath $(CLASSPATH) $<
