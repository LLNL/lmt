LMT_FULL_JAR=/home/garlick/work/lmt/lmt/client/lmt-complete.jar
LMT_APP_JAR =/home/garlick/work/lmt/lmt/client/lmt-app.jar

everything:
	$(MAKE) charva
	$(MAKE) all
	$(MAKE) fulljar

help:
	@echo "Usage: make all | clean | jar | charva | everything"
	@echo ""
	@echo "make charva  -- First step, to build charva package"
	@echo "make all     -- Second step, to compile all LMT sources"
	@echo "make fulljar -- Third step, to create the final application jar file"
	@echo "-or-"
	@echo "make everything -- To do all of the above"

all:
	@echo "*********************"
	@echo "Building..."
	@echo "*********************"

	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/utility; make all
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/database; make all
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lstat; make all
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/ltop; make all
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lwatch/util; make all
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lwatch; make all

clean:
	@echo "*********************"
	@echo "Cleaning..."
	@echo "*********************"
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/utility; make clean
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/database; make clean
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lstat; make clean
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/ltop; make clean
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lwatch/util; make clean
	@cd /home/garlick/work/lmt/lmt/client/java/gov/llnl/lustre/lwatch; make clean
	@cd /home/garlick/work/lmt/lmt/client; /bin/rm -f $(LMT_APP_JAR) $(LMT_FULL_JAR)
	@cd /home/garlick/work/lmt/lmt/client/charva; /usr/bin/env ANT_HOME="" /usr/bin/ant clean

jar:
	@echo "***********************************"
	@echo "Making LMT application jar file..."
	@echo "***********************************"
	@build/mkjar $(LMT_APP_JAR)
	@echo "==> Created $(LMT_APP_JAR)"

.PHONY: charva
charva:
	@echo "*********************"
	@echo "Making charva package..."
	@echo "*********************"
	@cd /home/garlick/work/lmt/lmt/client/charva; /usr/bin/env ANT_HOME="" /usr/bin/ant all

fulljar:
	@echo "************************************"
	@echo "Making full LMT jar file, including"
	@echo "external jar files..."
	@echo "************************************"
	$(MAKE) jar
	@build/mk-lmt-jar $(LMT_APP_JAR) $(LMT_FULL_JAR)
	@echo "==> Created $(LMT_FULL_JAR)"

runltop:
	@echo "Running ltop from full jar file..."
	/usr/bin/env LD_LIBRARY_PATH=$(LD_LIBRARY_PATH):/home/garlick/work/lmt/lmt/client/charva/c/lib /usr/bin/java -Xmx16m -cp $(LMT_FULL_JAR) gov.llnl.lustre.ltop.Ltop

runlstat:
	@echo "Running lstat from full jar file..."
	/usr/bin/java -Xmx16m -cp $(LMT_FULL_JAR) gov.llnl.lustre.lstat.Lstat

runlwatch:
	@echo "Running lwatch from full jar file..."
	/usr/bin/java -Xmx64m -cp $(LMT_FULL_JAR) gov.llnl.lustre.lwatch.LWatch
