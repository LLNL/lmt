package gov.llnl.lustre.lstat;
//  ===============================================================================
//  Copyright (c) 2007, The Regents of the University of California.
//  Produced at the Lawrence Livermore National Laboratory.
//  Written by C. Morrone, H. Wartens, P. Spencer, N. O'Neill, J. Long
//  UCRL-CODE-232438.
//  All rights reserved.
//  
//  This file is part of Lustre Monitoring Tools, version 2. 
//  For details, see http://sourceforge.net/projects/lmt/.
//  
//  Please also read Our Notice and GNU General Public License, available in the
//  COPYING file in the source distribution.
//  
//  This program is free software; you can redistribute it and/or modify it under
//  the terms of the GNU General Public License (as published by the Free Software
//  Foundation) version 2, dated June 1991.
//  
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY
//  WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY or FITNESS FOR A
//  PARTICULAR PURPOSE.  See the terms and conditions of the GNU General Public
//  License for more details.
//  
//  You should have received a copy of the GNU General Public License along with
//  this program; if not, write to the Free Software Foundation, Inc., 59 Temple
//  Place, Suite 330, Boston, MA 02111-1307 USA
//  ===============================================================================

import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.utility.Debug;
import jargs.gnu.CmdLineParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Lstat {

  //////////////////////////////////////////////////////////////////////////////

  private static final boolean debug = Boolean.getBoolean("debug");
  private static final boolean test = Boolean.getBoolean("test");

  private static final int DEFAULT_INTERVAL = 5; // sec

  // Used to hold option values.
  private static int interval;
  private static int count;
  private static boolean all;
  private static boolean extended;
  private static boolean mds;
  private static boolean ost;
  private static boolean router;

  private static ArrayList deviceList = new ArrayList();

  private static HashMap fileSystemMap = new HashMap();

  private static final  DecimalFormat decimalFormat =
    new DecimalFormat("###,###,###,##0.00");

  private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
  private static final SimpleDateFormat simpleDateFormat =
    new SimpleDateFormat(dateFormat, Locale.US);

  //private static final Properties properties = new Properties();

  private static Database.FilesystemInfo[] filesystemInfo;

  //////////////////////////////////////////////////////////////////////////////

  private static class Device {

    final private String fileSystem;
    private Database database = null;
    final private Set ostNameSet = new HashSet(); 
    private boolean useAll = true;

    Device(
      String fileSystem,
      String[] ostNames)
    throws Exception
    {

      if (debug) System.out.println(Debug.tag() + "fileSystem = " + fileSystem);

      this.fileSystem = fileSystem;

      if (ostNames.length > 0) this.useAll = false;

      for (int i = 0; i < ostNames.length; i++) {
        this.ostNameSet.add(ostNames[i]);
      } // 


      //
      // Find this fileSystem in FilesystemInfo
      //
      int index = 0;
      boolean found = false;
      for (int i = 0; i < Lstat.filesystemInfo.length; i++) {
        
        if (fileSystem.equals(filesystemInfo[i].filesystemName)) {
          index = i;
          found = true;
          break;
        }

      } // for i

      if (!found) {
        System.out.println("Unknown filesystem '" + fileSystem + "'.");
        System.out.println("Filesystem name must be one of:");
        
        for (int i = 0; i <  filesystemInfo.length; i++) {
          System.out.println("   " + filesystemInfo[i].filesystemName);
        } // for i
        
        System.exit(1);
      }


      //
      // Connect to database.
      //
      

      this.database = new Database(Lstat.filesystemInfo[index]);
//         Lstat.filesystemInfo[index].lmtDbHost,
//         Lstat.filesystemInfo[index].lmtDbPort + "",
//         Lstat.filesystemInfo[index].lmtDbName,
//         Lstat.filesystemInfo[index].lmtDbUsername,
//         Lstat.filesystemInfo[index].lmtDbAuth);
      
      this.database.connect();
      
      Exception e = this.database.getConnectException();
      
      if (e != null) {
        
        System.out.println(e.toString());
        System.exit(1);
        
      }
      
    } //  Device

    String getFileSystem()
    {
      return this.fileSystem;
    } // getFileSystem

    //     int[] getOstIds()
    //     {
    //       return this.ostIds;
    //     } // getOstIds
    
    Database getDatabase()
    {
      return this.database;
    }

    boolean hasOst(final String ostName)
    {

      if (this.useAll) return true;

      return this.ostNameSet.contains(ostName);

    } // hasOst


  } // Device

  //////////////////////////////////////////////////////////////////////////////

  public static void main(final String[] cmdLine)
  throws Exception
  {


    try {

      if (debug) System.out.println(Debug.tag() + "Start");

      final String [] args = Lstat.processOptions(cmdLine);

      if (debug) System.out.println(Debug.tag() + "here");

      Lstat.filesystemInfo = Database.getAllFilesystemInfo();

      try {
        Lstat.processArgs(args);
        if (debug) System.out.println(Debug.tag() + "here");
      }
      catch (Exception e) {
        if (debug) System.out.println(Debug.tag() + e.toString());
        throw e;
      }


    
      if (test) {
        Lstat.test();
        return;
      }


      if (debug) {
        System.out.println(Debug.tag() + "interval = " + Lstat.interval);
        System.out.println(Debug.tag() + "count = " + Lstat.count);
      }

      final Timer timer = new Timer();

      final TimerTask timeTask = new TimerTask() {
      
        private int cycles = 0;
      
        public void run(){

          try {

            if (debug) System.out.println(Debug.tag() + "cycles = " + cycles);
            
            
            try {
              
              for (int i = 0; i < Lstat.deviceList.size(); i++) {
            
                Device device = (Device) Lstat.deviceList.get(i);
            
                if (debug) System.out.println(Debug.tag() + device);
            
                Database database = device.getDatabase();

                if (Lstat.all || Lstat.ost || (!Lstat.router && !Lstat.mds)) 
                  Lstat.reportOst(device);
                if (Lstat.all || Lstat.router) Lstat.reportRouter(device);
                if (Lstat.all || Lstat.mds) Lstat.reportMds(device);

              } //  for i
        
            }
            catch (Exception e) {
              e.printStackTrace(System.out);
              if (e.getCause() != null) {
                e.getCause().printStackTrace(System.out);
              }
              System.exit(1);
            }

            if (Lstat.count > 0) {
              this.cycles++;
              if (this.cycles >= Lstat.count) {
                timer.cancel();
                Lstat.quit();
                return;
              }
            }

          }
          catch (Throwable t) {
            // Catchall for the timer thread.
            t.printStackTrace();
            System.exit(1);
          }

        } // run
      };

      timer.scheduleAtFixedRate(timeTask, 0L, Lstat.interval*1000L);

    }
    catch (Throwable t) {
      // Catchall for the main thread.
      t.printStackTrace();
      System.exit(1);
    }


  } // main

  //////////////////////////////////////////////////////////////////////////////

  private static String[] processOptions(final String[] cmdLine)
  throws Exception
  {

    final CmdLineParser parser = new CmdLineParser();

    final CmdLineParser.Option allOpt =
      parser.addBooleanOption('a', "all");

    final CmdLineParser.Option countOpt =
      parser.addIntegerOption('c', "count");

    final CmdLineParser.Option extendedOpt =
      parser.addBooleanOption('x', "extended");

    final CmdLineParser.Option helpOpt =
      parser.addBooleanOption('h', "help");

    final CmdLineParser.Option intervalOpt =
      parser.addIntegerOption('i', "interval");

    final CmdLineParser.Option mdsOpt =
      parser.addBooleanOption('m', "mds");

    final CmdLineParser.Option ostOpt =
      parser.addBooleanOption('o', "ost");

    final CmdLineParser.Option routerOpt =
      parser.addBooleanOption('r', "router");
    
    try {

      parser.parse(cmdLine);

    }
    catch (CmdLineParser.UnknownOptionException e) {
      System.out.println(e.getMessage());
      System.exit(1);
    }

    final boolean help = 
      ((Boolean) parser.getOptionValue(helpOpt, Boolean.FALSE)).booleanValue();

    if (help) {
      Lstat.usage();
      System.exit(0);
    }
    
    Lstat.all =
      ((Boolean) parser.getOptionValue(allOpt, Boolean.FALSE)).booleanValue();

    Lstat.count =
      ((Integer) parser.getOptionValue(countOpt, new Integer(0))).intValue();

    Lstat.extended =
      ((Boolean) parser.getOptionValue(
        extendedOpt, Boolean.FALSE)).booleanValue();

    Lstat.interval =
      ((Integer) parser.getOptionValue(
        intervalOpt, new Integer(Lstat.DEFAULT_INTERVAL))).intValue();

    Lstat.mds =
      ((Boolean) parser.getOptionValue(
        mdsOpt, Boolean.FALSE)).booleanValue();

    Lstat.ost = Lstat.extended || 
      ((Boolean) parser.getOptionValue(ostOpt, Boolean.FALSE)).booleanValue();

    Lstat.router =
      ((Boolean) parser.getOptionValue(
        routerOpt, Boolean.FALSE)).booleanValue();
    
    if (debug) {
      System.out.println(Debug.tag() + "all = "      + Lstat.all);
      System.out.println(Debug.tag() + "count = "    + Lstat.count);
      System.out.println(Debug.tag() + "extended = " + Lstat.extended);
      System.out.println(Debug.tag() + "interval = " + Lstat.interval);
      System.out.println(Debug.tag() + "mds = "      + Lstat.mds);
      System.out.println(Debug.tag() + "ost = "      + Lstat.ost);
      System.out.println(Debug.tag() + "router = "   + Lstat.extended);
    }
    
    return parser.getRemainingArgs(); // args other than option flags

  } // processOptions

  //////////////////////////////////////////////////////////////////////////////

  private static void processArgs(String[] args)
  throws Exception
  {

    //
    // First, see if we have trailing interval or count values.
    //

    boolean hasUltimate = false;
    boolean hasPenultimate = false;

    int ultimate = 0;
    int penultimate = 0;
    int argsIndex = args.length;
    
    if (args.length > 0) {

      String value = args[args.length - 1];
      
      try {

        ultimate = Integer.parseInt(value);
        hasUltimate = true;
        argsIndex--;

        if (args.length > 1) {

          value = args[args.length - 2];
      
          try {
            penultimate = Integer.parseInt(value);
            hasPenultimate = true;
            argsIndex--;
          }
          catch (NumberFormatException e){}
            
        }

      }
      catch (NumberFormatException e){}

    }

    if (debug) {
      System.out.println(Debug.tag() + "hasPenultimate = " + hasPenultimate);
      System.out.println(Debug.tag() + "hasUltimate = " + hasUltimate);
      System.out.println(Debug.tag() + "penultimate = " + penultimate);
      System.out.println(Debug.tag() + "ultimate = " + ultimate);
    }

    if (hasUltimate) {
      if (hasPenultimate) {
        Lstat.interval = penultimate;
        Lstat.count = ultimate;
      }
      else {
        Lstat.interval = ultimate;
      }
    }

    //
    // Supply default.
    //

    if (argsIndex == 0) {
      args = new String[]{ Lstat.filesystemInfo[0].filesystemName };
      argsIndex = 1;
    }

    //
    // Read through args and collect information in a HashMap of HashSets.
    // This allows us to transparently handle any redundant data the a user
    // might have entered.
    //

    for (int i = 0; i < argsIndex; i++) {
     
      String fileSystem = null;
      String[] ostNames = null;

      final String spec = args[i];
     
      final int colonIndex = spec.indexOf(':');

      if (colonIndex < 0) {

        fileSystem = spec;

      }
      else {

        fileSystem = spec.substring(0, colonIndex);

        if (spec.length() - 1 > colonIndex) {
          final String ostSpec = spec.substring(colonIndex + 1);
          //ostNames = ostSpec.split(",");
          ostNames = Lstat.expand(ostSpec);
        }

      }

      HashSet ostSet = (HashSet) Lstat.fileSystemMap.get(fileSystem);

      if (ostSet == null) {
        ostSet = new HashSet();
        Lstat.fileSystemMap.put(fileSystem, ostSet);
      }

      if (ostNames != null) {
        for (int j = 0; j < ostNames.length; j++) {
          ostSet.add(ostNames[j]);
        } //  for j
      }

    } // for i


    //
    // Now go through all the stuff we read in and create the appropriate
    // Device objects.
    //

    final Set entrySet = Lstat.fileSystemMap.entrySet();
    final Iterator iterator = entrySet.iterator();

    while(iterator.hasNext()) {

      final Map.Entry mapEntry = (Map.Entry) iterator.next();

      String fileSystem = (String) mapEntry.getKey();
      HashSet ostSet = (HashSet) mapEntry.getValue();

      String[] ostNames = (String[]) ostSet.toArray(new String[ostSet.size()]);

      final Device device = new Device(fileSystem, ostNames);
      
      Lstat.deviceList.add(device);

    } // while
    
  } // processArgs

  //////////////////////////////////////////////////////////////////////////////

  private static void reportOst(final Device device)
  throws Exception
  {
    
    Lstat.doDateHeader(device, "OST_DATA");

    final int width = 15;
    final Database database = device.getDatabase();
    final Database.OstData ostData = database.getCurrentOstData();

    System.out.println();

    System.out.print(Lstat.format("[OST Name]",   width, false));
    System.out.print(Lstat.format("[Read MB/s]",  width, false));
    System.out.print(Lstat.format("[Write MB/s]", width, false));

    if (Lstat.extended) {
      System.out.print(Lstat.format("[%CPU Used]",    width, false));
      System.out.print(Lstat.format("[%Space Used]",  width, false));
      System.out.print(Lstat.format("[%Inodes Used]", width, false));
    }

    System.out.println();


    boolean[] indices = new boolean[ostData.getSize()];
    int count = 0;              // count of OSTs that we are reporting on.

    for (int i = 0; i < ostData.getSize(); i++) {

      if (device.hasOst(ostData.getOstName(i))) {


        indices[i] = true;
        count++;

        System.out.print(Lstat.format(ostData.getOstName(i), width, false));

        System.out.print(Lstat.format(ostData.getReadRate(i), width));
        System.out.print(Lstat.format(ostData.getWriteRate(i), width));

        if (Lstat.extended) {
          System.out.print(Lstat.format(ostData.getPctCpu(i), width));
          System.out.print(Lstat.format(ostData.getPctKbytes(i), width));
          System.out.print(Lstat.format(ostData.getPctInodes(i), width));
        }

        System.out.println();
      }

    } // for i


    if (count > 1) {

      //
      // Divider
      //

      final String dashes = "--------------------".substring(0, width - 5);

      System.out.print(Lstat.format(dashes, width, false));
      System.out.print(Lstat.format(dashes, width, false));
      System.out.print(Lstat.format(dashes, width, false));
    
      if (Lstat.extended) {
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
      }

      System.out.println();

      //
      // Min
      //

      System.out.print(Lstat.format("[Min]", width, false));
      System.out.print(Lstat.format(ostData.getReadRateMin(indices), width));
      System.out.print(Lstat.format(ostData.getWriteRateMin(indices), width));
    
      if (Lstat.extended) {
        System.out.print(Lstat.format(ostData.getPctCpuMin(indices), width));
        System.out.print(Lstat.format(ostData.getPctKbytesMin(indices), width));
        System.out.print(Lstat.format(ostData.getPctInodesMin(indices), width));
      }

      System.out.println();

      //
      // Max
      //

      System.out.print(Lstat.format("[Max]", width, false));
      System.out.print(Lstat.format(ostData.getReadRateMax(indices), width));
      System.out.print(Lstat.format(ostData.getWriteRateMax(indices), width));
    
      if (Lstat.extended) {
        System.out.print(Lstat.format(ostData.getPctCpuMax(indices), width));
        System.out.print(Lstat.format(ostData.getPctKbytesMax(indices), width));
        System.out.print(Lstat.format(ostData.getPctInodesMax(indices), width));
      }

      System.out.println();

      //
      // Average
      //

      System.out.print(Lstat.format("[Average]", width, false));
      System.out.print(Lstat.format(ostData.getReadRateAvg(indices), width));
      System.out.print(Lstat.format(ostData.getWriteRateAvg(indices), width));
    
      if (Lstat.extended) {
        System.out.print(Lstat.format(ostData.getPctCpuAvg(indices), width));
        System.out.print(Lstat.format(ostData.getPctKbytesAvg(indices), width));
        System.out.print(Lstat.format(ostData.getPctInodesAvg(indices), width));
      }

      System.out.println();



      //
      // Aggregate
      //

      System.out.print(Lstat.format("[Aggregate]", width, false));
      System.out.print(Lstat.format(ostData.getReadRateSum(indices), width));
      System.out.print(Lstat.format(ostData.getWriteRateSum(indices), width));
    
      System.out.println();

    }

  } // reportOst

  //////////////////////////////////////////////////////////////////////////////

  private static void reportRouter(final Device device)
  throws Exception
  {

    Lstat.doDateHeader(device, "ROUTER_DATA");
    
    final int width = 15;

    final Database database = device.getDatabase();

    final int[] ids = database.getRouterGroupIds();

    for (int j = 0; j < ids.length; j++) {

      System.out.println();

      final Database.RouterData routerData =
        database.getCurrentRouterData(ids[j]);

      System.out.print(Lstat.format("[Router Name]", width, false));
      System.out.print(Lstat.format("[BW MB/s]",     width, false));
      System.out.print(Lstat.format("[%CPU Used]",   width, false));

      System.out.println();

      boolean[] indices = new boolean[ routerData.getSize()];

      for (int i = 0; i < routerData.getSize(); i++) {

        indices[i] = true;

        System.out.print(
          Lstat.format(routerData.getRouterName(i), width, false));
        System.out.print(Lstat.format(routerData.getRate(i), width));
        System.out.print(Lstat.format(routerData.getPctCpu(i), width));

        System.out.println();
      
      } // for i

      if (routerData.getSize() > 1) {

        //
        // Divider
        //

        final String dashes = "--------------------".substring(0, width - 5);

        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.println();

        //
        // Min
        //

        System.out.print(Lstat.format("[Min]", width, false));
        System.out.print(
          Lstat.format(routerData.getRateMin(indices), width));
        System.out.print(Lstat.format(routerData.getPctCpuMin(indices), width));
        System.out.println();

        //
        // Max
        //

        System.out.print(Lstat.format("[Max]", width, false));
        System.out.print(
          Lstat.format(routerData.getRateMax(indices), width));
        System.out.print(Lstat.format(routerData.getPctCpuMax(indices), width));
        System.out.println();

        //
        // Average
        //

        System.out.print(Lstat.format("[Average]", width, false));
        System.out.print(
          Lstat.format(routerData.getRateAvg(indices), width));
        System.out.print(Lstat.format(routerData.getPctCpuAvg(indices), width));
        System.out.println();

        //
        // Aggregate
        //

        System.out.print(Lstat.format("[Aggregate]", width, false));
        System.out.print(
          Lstat.format(routerData.getRateSum(indices), width));
        System.out.println();

      }

    } // for j

  } // reportRouter

  //////////////////////////////////////////////////////////////////////////////

  private static void reportMds(final Device device)
  throws Exception
  {

    Lstat.doDateHeader(device, "MDS_DATA");
    
    final int width = 15;
    
    final Database database = device.getDatabase();

    final Database.MdsInfo[] mdsInfo = database.getMdsInfo();

    for (int j = 0; j < mdsInfo.length; j++) {

      //
      // MDS Data
      //

      final Database.MdsData mdsData =
        database.getCurrentMdsData(mdsInfo[j].mdsId);

      System.out.println();

      System.out.print(Lstat.format("[MDS Name]",    width, false));
      System.out.print(Lstat.format("[%CPU Used]",   width, false));
      System.out.print(Lstat.format("[%Space Used]", width, false));
      System.out.print(Lstat.format("[%Inode Used]", width, false));
    
      System.out.println();
    
      boolean[] indices = new boolean[mdsData.getSize()];
    
      for (int i = 0; i < mdsData.getSize(); i++) {
      
        indices[i] = true;
      
        System.out.print(Lstat.format(mdsData.getMdsName(i), width, false));
        System.out.print(Lstat.format(mdsData.getPctCpu(i), width));
        System.out.print(Lstat.format(mdsData.getPctKbytes(i), width));
        System.out.print(Lstat.format(mdsData.getPctInodes(i), width));
      
        System.out.println();
      
      } // for i
    
      if (mdsData.getSize() > 1) {

        final String dashes = "--------------------".substring(0, width - 5);
    
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.print(Lstat.format(dashes, width, false));
        System.out.println();
    
        System.out.print(Lstat.format("[Average]", width, false));
        System.out.print(Lstat.format(mdsData.getPctCpuAvg(indices), width));
        System.out.print(Lstat.format(mdsData.getPctKbytesAvg(indices), width));
        System.out.print(Lstat.format(mdsData.getPctInodesAvg(indices), width));
        System.out.println();

      }


      //
      // MDS Ops Data
      //

      final Database.MdsOpsData mdsOpsData =
        database.getCurrentMdsOpsData(mdsInfo[j].mdsId);

      System.out.println();

      System.out.print(Lstat.format("[Operation]",   width + 5, false));
      System.out.print(Lstat.format("[Samples]",     width, false));
      System.out.print(Lstat.format("[Samples/sec]", width, false));
      System.out.print(Lstat.format("[Avg Value]",   width, false));
      System.out.print(Lstat.format("[Std Dev]",     width, false));
      System.out.print(Lstat.format("[Units]",       width, false));
    
      System.out.println();
    
      for (int i = 0; i < mdsOpsData.getSize(); i++) {
      
        System.out.print(
          Lstat.format(mdsOpsData.getOpName(i), width + 5, false));
        System.out.print(Lstat.format(mdsOpsData.getSamples(i), width));
        System.out.print(Lstat.format(mdsOpsData.getSamplesPerSec(i), width));
        System.out.print(Lstat.format(mdsOpsData.getAvgVal(i), width));
        System.out.print(Lstat.format(mdsOpsData.getStdDev(i), width));
        System.out.print(Lstat.format(mdsOpsData.getUnits(i), width, false));
      
        System.out.println();
      
        //if (debug) System.out.println(
        //  "std dev = <" + mdsOpsData.getStdDev(i) + ">");


      } // for i
    

    } //  for j
    
  } // reportMds

  //////////////////////////////////////////////////////////////////////////////

  private static String[] expand(final String expression)
  throws Exception
  {

    final ArrayList arrayList = new ArrayList();

    final String[] split = Lstat.split(expression);

    for (int k = 0; k < split.length; k++) {

      final String exp = split[k].trim();
      final int lbi = exp.indexOf('[');
      final int rbi = exp.indexOf(']');

      if (lbi < 0 && rbi < 0) {
        arrayList.add(exp);
        continue;
      }

      if (lbi > 0 && rbi < 0) throw new Exception("Unmatched '[':  " + exp);
      if (lbi < 0 && rbi > 0) throw new Exception("Unmatched ']':  " + exp);
      if (rbi - lbi == 1 ) 
        throw new Exception("Empty range specification:  " + exp);
      if (rbi + 1 < exp.length())
        throw new Exception("Improper syntax:  " + exp);

      final String prefix = exp.substring(0, lbi);

      final String content = exp.substring(lbi + 1, rbi);

      final String[] terms = content.split(",");

      for (int i = 0; i < terms.length; i++) {

        final String[] parts = terms[i].split("-");

        if (parts.length == 1) {

          if (debug) System.out.println(Debug.tag() + "parts[0] = " + parts[0]);

          final int id = Integer.parseInt(parts[0].trim());

          arrayList.add(prefix + id);
        }
        else if (parts.length == 2) {
        
          if (debug) {
            System.out.println(Debug.tag() + "parts[0] = " + parts[0]);
            System.out.println(Debug.tag() + "parts[1] = " + parts[1]);
          }

          final int id0 = Integer.parseInt(parts[0].trim());
          final int id1 = Integer.parseInt(parts[1].trim());
        
          for (int j = id0; j <= id1; j++) {
            arrayList.add(prefix + j);
          }
        
        }
        else {
          throw new Exception("Improper syntax");
        }

      } //  for i

    } //  for k

    return (String[]) arrayList.toArray(new String[arrayList.size()]);

  } // expand

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Split OST expression into separate terms for use in expand method.
   */

  private static String[] split(final String value)
  {

    final ArrayList arrayList = new ArrayList();
    final StringBuffer term = new StringBuffer();
    boolean inBracket = false;

    for (int i = 0; i < value.length(); i++) {

      final char c = value.charAt(i);

      if (c == '[') inBracket = true;
      else if (c == ']') inBracket = false;


      if (!inBracket && c == ',') {
        arrayList.add(term.toString());
        term.setLength(0);
        continue;
      }
        
      term.append(c);

    } // for i

    arrayList.add(term.toString());

    return (String[]) arrayList.toArray(new String[arrayList.size()]);


  } // split

  //////////////////////////////////////////////////////////////////////////////


  private static void quit()
  {
    
    // Any cleanup goes here.

  } // quit

  //////////////////////////////////////////////////////////////////////////////

  private static void usage()
  {

    System.out.println("Usage:");
    System.out.println("       lstat [ options... ] [ <DEVICE> [ ... ] ]  [ <interval> [ <count> ] ]");
    System.out.println();
    System.out.println("         <DEVICE>  = [<filesystem>:]<OST>[,...]");
    System.out.println("         <OST>     = <ost_name>|<ost_name_root>\"[\"(<i>-<j>|<i>)[,...]\"]\" ");
    System.out.println();
    System.out.println("       Options are:");
    System.out.println();
    System.out.println("            -a, --all");
    System.out.println("                  show OST, MDS, and router stats");
    System.out.println();
    System.out.println("            -c COUNT, --count=COUNT");
    System.out.println("                  cycle display COUNT times before exiting");
    System.out.println();
    System.out.println("            -h, --help");
    System.out.println("                  display this help");
    System.out.println();
    System.out.println("            -i INTERVAL, --interval=INTERVAL");
    System.out.println("                  cycle display every INTERVAL seconds");
    System.out.println();
    System.out.println("            -m, --mds");
    System.out.println("                  show MDS stats");
    System.out.println();
    System.out.println("            -o, --ost");
    System.out.println("                  show OST stats (default)");
    System.out.println();
    System.out.println("            -r, --router");
    System.out.println("                  show router stats");
    System.out.println();
    System.out.println("            -x, --extended");
    System.out.println("                  show extended OST stats");
    System.out.println();
    System.out.println("       Examples:");
    System.out.println();
    System.out.println("         lstat -x foo:OST_ilc[2-3,5] bar:OST_ilc");
    System.out.println("         lstat --mds --router foo:OST_ilc[2-3,5],OST_ilc10 5 10");
    System.out.println();


  } // usage

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final Float value,
    final int width)
  {


    if (value == null) {
      return format("****", width, false);
    }

    if (value.isNaN()) {
      return format("NaN", width, false);
    }

    return format(
      Lstat.decimalFormat.format(value.floatValue()) + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final Long value,
    final int width)
  {

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      value + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final String value,
    final int size,
    final boolean leftJustified)
  {

    if (value.length() >= size) {
      return value;
    }

    final StringBuffer sb = new StringBuffer(size);

    if (leftJustified) {
      sb.append(value);
    }
    
    for (int i = value.length(); i < size; i++) {
      sb.append(' ');
    } // for i

    if (!leftJustified) {
      sb.append(value);
    }

    return sb.toString();

  } // format

  //////////////////////////////////////////////////////////////////////////////

  /**
   * A place to put test code.  Run if "test" flag is set.
   */

  private static void test()
  throws Exception
  {


    if (true) {

      Device device = (Device) Lstat.deviceList.get(0);
      Database database = device.getDatabase();

      
      int groups[] = database.getRouterGroupIds();


      for (int i = 0; i < groups.length; i++) {

        System.out.println("router group " + i + "  --------------------------------------");
        
        Database.AggregateData data[] = database.getRouterGroupAggregateData(
          Database.HOUR,
          i,
          "BYTES",
          new Timestamp(
            Lstat.simpleDateFormat.parse("2007-08-02 14:00:00").getTime()),
          new Timestamp(
            Lstat.simpleDateFormat.parse("2007-08-06 14:00:00").getTime()));
    
        for (int j = 0; j < data.length; j++) {
          
          Timestamp timestamp = data[j].timestamp;
          float aggregate     = data[j].aggregate;
          float minVal        = data[j].minval;
          float maxVal        = data[j].maxval;
          float ave           = data[j].average;
          long numSamples     = data[j].numSamples;
          
          System.out.println(
            timestamp +
            " aggregate  = " +  Lstat.decimalFormat.format(aggregate) +
            " minVal  = " + minVal +
            " maxVal  = " + maxVal +
            " ave  = " + ave +
            " numSamples  = " + numSamples);
          
        } // for j
        


      } // for i




    }


    if (false) {

      Device device = (Device) Lstat.deviceList.get(0);
      Database database = device.getDatabase();

      Iterator iterator = database.getOstRawData(
        1,
        new Timestamp(
          Lstat.simpleDateFormat.parse("2007-04-01 16:00:00").getTime()),
        new Timestamp(
          Lstat.simpleDateFormat.parse("2007-04-01 18:00:00").getTime()));



      while (iterator.hasNext()) {

        Database.OstRawData ostRawData = (Database.OstRawData) iterator.next();

        System.out.println();
        System.out.println(Debug.tag() + "ostRawData.timestamp = "  + ostRawData.timestamp);
        System.out.println(Debug.tag() + "ostRawData.ostId = "      + ostRawData.ostId);
        System.out.println(Debug.tag() + "ostRawData.tsId = "       + ostRawData.tsId);
        System.out.println(Debug.tag() + "ostRawData.readBytes = "  + ostRawData.readBytes);
        System.out.println(Debug.tag() + "ostRawData.writeBytes = " + ostRawData.writeBytes );
        System.out.println(Debug.tag() + "ostRawData.pctCpu = "     + ostRawData.pctCpu);
        System.out.println(Debug.tag() + "ostRawData.kbytesFree = " + ostRawData.kbytesFree);
        System.out.println(Debug.tag() + "ostRawData.kbytesUsed = " + ostRawData.kbytesUsed );
        System.out.println(Debug.tag() + "ostRawData.inodesFree = " + ostRawData.inodesFree);
        System.out.println(Debug.tag() + "ostRawData.inodesUsed = " + ostRawData.inodesUsed );

      } //  while

    }

    if (false) {
      
      Device device = (Device) Lstat.deviceList.get(0);
      Database database = device.getDatabase();

      Database.TimeInfo timeInfo = database.getLatestTimeInfo("OST_DATA");
      System.out.println("ost time = " + timeInfo.timestamp);

      timeInfo = database.getLatestTimeInfo("MDS_DATA");
      System.out.println("mds time = " + timeInfo.timestamp);

      timeInfo = database.getLatestTimeInfo("ROUTER_DATA");
      System.out.println("router time = " + timeInfo.timestamp);
      
    }
    
    if (false) {

      long time0 = System.currentTimeMillis();

      Database.FilesystemInfo[] filesystemInfo = Database.getAllFilesystemInfo();

      long delta =  System.currentTimeMillis() - time0;
      double secs = delta/1000.0;
      System.out.println("secs = " + secs); 

      for (int i = 0; i < filesystemInfo.length; i++) {
      
        System.out.println(
          "filesystemId        = " + filesystemInfo[i].filesystemId);
        System.out.println(
          "filesystemName      = " + filesystemInfo[i].filesystemName);
        System.out.println(
          "lmtDbHost           = " + filesystemInfo[i].lmtDbHost);
        System.out.println(
          "lmtDbPort           = " + filesystemInfo[i].lmtDbPort);
        System.out.println(
          "lmtDbName           = " + filesystemInfo[i].lmtDbName);
        System.out.println(
          "lmtDbUsername       = " + filesystemInfo[i].lmtDbUsername);
        System.out.println(
          "lmtDbAuth           = " + filesystemInfo[i].lmtDbAuth);
        System.out.println(
          "filesystemMountName = " + filesystemInfo[i].filesystemMountName);

      } // for i

    }

    if (false) {

      Device device = (Device) Lstat.deviceList.get(0);
      Database database = device.getDatabase();
      
      long time0 = System.currentTimeMillis();
      
      
      Database.VariableInfo[] vi = new Database.VariableInfo[]{
        database.getVariableInfo("OST_VARIABLE_INFO", "READ_BYTES"),
        database.getVariableInfo("OST_VARIABLE_INFO", "WRITE_BYTES"),
        database.getVariableInfo("OST_VARIABLE_INFO", "PCT_CPU"),
        database.getVariableInfo("OST_VARIABLE_INFO", "KBYTES_FREE"),
        database.getVariableInfo("OST_VARIABLE_INFO", "KBYTES_USED"),
        database.getVariableInfo("OST_VARIABLE_INFO", "INODES_FREE"),
        database.getVariableInfo("OST_VARIABLE_INFO", "INODES_USED"),
        database.getVariableInfo("OST_VARIABLE_INFO", "READ_RATE"),
        database.getVariableInfo("OST_VARIABLE_INFO", "WRITE_RATE"),
        database.getVariableInfo("OST_VARIABLE_INFO", "PCT_KBYTES"),
        database.getVariableInfo("OST_VARIABLE_INFO", "PCT_INODES"),
        
        database.getVariableInfo("MDS_VARIABLE_INFO", "KBYTES_FREE"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "KBYTES_USED"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "INODES_FREE"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "INODES_USED"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "PCT_CPU"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "PCT_KBYTES"),
        database.getVariableInfo("MDS_VARIABLE_INFO", "PCT_INODES"),
        
        database.getVariableInfo("OSS_VARIABLE_INFO", "PCT_MEM"),
        database.getVariableInfo("OSS_VARIABLE_INFO", "READ_RATE"),
        database.getVariableInfo("OSS_VARIABLE_INFO", "WRITE_RATE"),
        database.getVariableInfo("OSS_VARIABLE_INFO", "ACTUAL_RATE"),
        database.getVariableInfo("OSS_VARIABLE_INFO", "LINK_STATUS"),
        database.getVariableInfo("OSS_VARIABLE_INFO", "PCT_CPU"),
        
        database.getVariableInfo("ROUTER_VARIABLE_INFO", "BYTES"),
        database.getVariableInfo("ROUTER_VARIABLE_INFO", "PCT_CPU"),
        database.getVariableInfo("ROUTER_VARIABLE_INFO", "RATE")
        
      };  
      
      long delta =  System.currentTimeMillis() - time0;
      double secs = delta/1000.0;
      
      System.out.println("secs = " + secs); 
      
      for (int i = 0; i < vi.length; i++) {
        
        System.out.println(
          vi[i].variableId + " " +
          vi[i].variableName + " " +
          vi[i].variableLabel + " " +
          vi[i].threshType + " " +
          vi[i].threshVal1 + " " +
          vi[i].threshVal2);
        
      } //  next i
      
      
      Database.AggregateData[] oad = database.getOstAggregateData(
        Database.RAW,
        1,
        "READ_RATE",
        new Timestamp(
          Lstat.simpleDateFormat.parse("2007-06-06 14:00:00").getTime()),
        new Timestamp(
          Lstat.simpleDateFormat.parse("2007-07-06 13:00:00").getTime()));
      
      
      for (int i = 0; i < oad.length; i++) {
        
        Timestamp timestamp = oad[i].timestamp;
        float aggregate     = oad[i].aggregate;
        float minVal        = oad[i].minval;
        float maxVal        = oad[i].maxval;
        float ave           = oad[i].average;
        long numSamples     = oad[i].numSamples;

        System.out.println(
          timestamp +
          " aggregate  = " +  Lstat.decimalFormat.format(aggregate) +
          " minVal  = " + minVal +
          " maxVal  = " + maxVal +
          " ave  = " + ave +
          " numSamples  = " + numSamples);

      } // for i
    
    }

  } // test

  //////////////////////////////////////////////////////////////////////////////
  
  private static void doDateHeader(Device device, String tableName)
  throws Exception
  {

    Database database = device.getDatabase();
      
    Database.TimeInfo timeInfo  = database.getLatestTimeInfo(tableName);
    
    System.out.println();
    System.out.print("------- ");
    
    synchronized(Lstat.dateFormat) {
      System.out.print(
        Lstat.format(
          //new Date() + "",
          simpleDateFormat.format(timeInfo.timestamp) + "",
          Lstat.dateFormat.length(),
          false));
    } // synchronized
    
    System.out.print(" -------");
    System.out.println();
    
  } // doDateHeader
  
  //////////////////////////////////////////////////////////////////////////////

} //  Lstat

