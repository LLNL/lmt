package gov.llnl.lustre.ltop;
//  ===============================================================================
//  Copyright (C) 2007, Lawrence Livermore National Security, LLC.
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

import charva.awt.Color;
import charva.awt.ColorPair;
import charva.awt.EventQueue;
import charva.awt.Toolkit;
import charva.awt.event.AWTEvent;
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.utility.Debug;
import gov.llnl.lustre.utility.Grouper;
import jargs.gnu.CmdLineParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
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


/**
   
  Things to do:

  (x) Better error handling when not able to connect to a database.  May want
      to do something better than throwing an exception and dying.
  ( ) Remove filesystemSync.
  ( ) Some sort of error log screen.  Would come up as first screen if 
      something is wrong.

*/




public class Ltop {

  //////////////////////////////////////////////////////////////////////////////

  private static final boolean localDebug = Boolean.getBoolean("Ltop.debug");
  private static final boolean debug = Boolean.getBoolean("debug") || localDebug;

  private static final int DEFAULT_INTERVAL = 5; // sec

  private static final int SCROLL_INCREMENT = 5;

  // Used to hold option values.
  private static int interval;
  private static int count;

  private static int cycles;

  //private static ArrayList deviceList = new ArrayList();
  //private static Device device;

  // Currently selected database connection.
  private static Database database;

  //private static HashMap fileSystemMap = new HashMap();

  private static final  DecimalFormat decimalFormat =
    new DecimalFormat("#,###,##0.00");

  private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
  private static final SimpleDateFormat simpleDateFormat =
    new SimpleDateFormat(dateFormat, Locale.US);

  //private static final Properties properties = new Properties();

  private static Toolkit toolkit = null;

  private static final int HELP_MODE           = 1;
  private static final int MDS_MODE            = 2;
  private static final int OSS_EXTENDED_MODE   = 3;
  private static final int OSS_MODE            = 4;
  private static final int OST_EXTENDED_MODE   = 5;
  private static final int OST_MODE            = 6;
  private static final int ROUTER_SUMMARY_MODE = 7;
  private static final int ROUTER_GROUP_MODE   = 8;
  private static final int ROUTER_MODE         = 9;
  private static final int FILESYSTEM_MODE     = 10;
  private static final int ERROR_MODE          = 11;

  private static int mode = OST_MODE;

  private static int row = 0;
  private static int maxRow = 0;
  


  private static int scrollOffset = 0;



  private static int screenRows;
  private static int screenColumns;
  
  private static Timer drawTimer;
  private static Timer resizeTimer;

  //private static TimerTask timerTask;

  private static String filesystemName = null;

  // Synchronize drawing and screen update operations.
  private static Object drawSync = new Object();

  // Synchronize filesystem changes
  private static Object filesystemSync = new Object();

  private static int filesystemIndex = 0;
  private static Database.FilesystemInfo[] filesystemInfo;

  // Storage for connected databases.
  private static Database[] databases;

  //////////////////////////////////////////////////////////////////////////////

  private static class MyTimerTask extends TimerTask {

    public void run(){

      if (debug) System.err.println(Debug.tag() + "cycles = " + Ltop.cycles);

      Ltop.draw();

    } // run

  } // MyTimerTask

  //////////////////////////////////////////////////////////////////////////////

  private static void draw()
  {

    if (debug) System.err.println(Debug.tag() + "enter");


    // Sync so that we don't attempt to do multiple draws at once -- draw
    // requests can come from multiple threads.
    synchronized (Ltop.drawSync) {

      try {

        Ltop.toolkit.resetClipRect();
        Ltop.row = Ltop.scrollOffset;
        Ltop.toolkit.setCursor(0, Ltop.row);  
        Ltop.toolkit.clear();  


        // Sync so that filesystem doesn't change while we are 
        // generating reports.
        synchronized (Ltop.filesystemSync) {

          switch (Ltop.mode) {

            case OSS_MODE:
            case OSS_EXTENDED_MODE:
              Ltop.reportOss();
              break;
            case OST_MODE:
            case OST_EXTENDED_MODE:
              Ltop.reportOst();
              break;
            case MDS_MODE:
              Ltop.reportMds();
              break;
            case ROUTER_MODE:
              Ltop.reportRouter();
              break;
            case ROUTER_SUMMARY_MODE:
              Ltop.reportRouterAll();
              break;
            case ROUTER_GROUP_MODE:
              Ltop.reportRouterGroup();
              break;
            case FILESYSTEM_MODE:
              Ltop.reportFilesystem();
              break;
            case HELP_MODE:
              Ltop.help();
              break;
            case ERROR_MODE:
              Ltop.reportError();
              break;
            default:
              throw new Error("Illegal mode value:  " + Ltop.mode);

          } //  switch 

        } // synchronized


        Ltop.toolkit.sync();

      }
      catch (Exception e) {
        if (debug) System.err.println(Debug.tag() + "saw exception " + e.getClass());
        e.printStackTrace(System.err);
        Ltop.close();
        Ltop.dumpThrowable(e, System.out);
        System.exit(1);
      }

    } // synchronize


    if (Ltop.count > 0) {
      Ltop.cycles++;
      if (Ltop.cycles >= Ltop.count) {
        Ltop.close();
        return;
      }
    }

  } // draw

  //////////////////////////////////////////////////////////////////////////////

  public static void main(final String[] cmdLine)
  {

    try {

      
//       if (Ltop.toolkit.hasColors()) {

//         Ltop.toolkit.startColors();

//         Ltop.toolkit.initColorPair(Toolkit.WHITE,   Toolkit.BLACK,   Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.GREEN,   Toolkit.GREEN,   Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.RED,     Toolkit.RED,     Toolkit.BLACK);
//         Ltop.toolkit.initColorPair(Toolkit.CYAN,    Toolkit.CYAN,    Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.BLACK,   Toolkit.WHITE,   Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.MAGENTA, Toolkit.MAGENTA, Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.BLUE,    Toolkit.BLUE,    Toolkit.WHITE);
//         Ltop.toolkit.initColorPair(Toolkit.YELLOW,  Toolkit.YELLOW,  Toolkit.WHITE);


//         Ltop.toolkit.setDefaultBackground(Color.black);
//         Ltop.toolkit.setDefaultForeground(Color.white);

//       }





      if (debug) System.err.println(Debug.tag() + "Start");

      final String [] args = Ltop.processOptions(cmdLine);

      Ltop.processArgs(args);

      Ltop.getDatabases();

      Ltop.database = Ltop.databases[Ltop.filesystemIndex];

      Ltop.toolkit = Toolkit.getDefaultToolkit();

      Ltop.screenRows = Ltop.toolkit.getScreenRows();
      Ltop.screenColumns = Ltop.toolkit.getScreenColumns();

      // Start task to monitor window resizes.
      Ltop.handleWindowResize();

      // Start thread to monitor keyboard input.
      Ltop.handleKeyboardInput();


      if (debug) {
        System.err.println(Debug.tag() + "interval = " + Ltop.interval);
        System.err.println(Debug.tag() + "count = " + Ltop.count);
      }

      // Start task to draw screen at intervals.
      Ltop.drawTimer = new Timer();
      MyTimerTask timerTask = new MyTimerTask();
      Ltop.drawTimer.scheduleAtFixedRate(timerTask, 0L, Ltop.interval*1000L);


    }
    catch (Exception e) {
      if (debug) System.err.println(Debug.tag() + "saw exception " + e.getClass());
      Ltop.close();
      Ltop.dumpThrowable(e, System.out);
      System.exit(1);
    }


  } // main

  //////////////////////////////////////////////////////////////////////////////

  private static void changeFilesystem(int inc)
  throws Exception
  {

    //
    // Synchronized so that we don't change to new filesystem while old one is 
    // still in use.
    //
    
    // 
    synchronized (Ltop.filesystemSync) {

      int newIndex = Ltop.filesystemIndex;
      
      do {
        
        newIndex += inc;
        
        if (debug) System.err.println(Debug.tag() + "newIndex = " + newIndex);
        
        if (newIndex < 0 || newIndex >= Ltop.databases.length) return;
        
      }
      while (!Ltop.databases[newIndex].isConnected());

      
      if (debug) System.err.println(
        Debug.tag() + "switch from " +  Ltop.filesystemIndex + " to " + newIndex);
      
      Ltop.database = Ltop.databases[newIndex];


      //
      //  Will only change to new filesystem if previous call does not throw 
      //  an exception.
      //

      Ltop.filesystemIndex = newIndex;
      Ltop.filesystemName = Ltop.databases[Ltop.filesystemIndex]
        .getFilesystemInfo().filesystemName;
      
    } //  synchronized

  } // changeFileSystem

  //////////////////////////////////////////////////////////////////////////////

  private static String[] processOptions(final String[] cmdLine)
  throws Exception
  {

    final CmdLineParser parser = new CmdLineParser();

    final CmdLineParser.Option routerSummaryOpt =
      parser.addBooleanOption('a', "router_summary");

    final CmdLineParser.Option routerGroupOpt =
      parser.addBooleanOption('g', "router_group");

    final CmdLineParser.Option filesystemOpt =
      parser.addBooleanOption('f', "filesystem");

    final CmdLineParser.Option helpOpt =
      parser.addBooleanOption('h', "help");

    final CmdLineParser.Option listOpt =
      parser.addBooleanOption('l', "list");

    final CmdLineParser.Option intervalOpt =
      parser.addIntegerOption('i', "interval");

    final CmdLineParser.Option mdsOpt =
      parser.addBooleanOption('m', "mds");

    final CmdLineParser.Option ostOpt =
      parser.addBooleanOption('o', "ost");

    final CmdLineParser.Option ossOpt =
      parser.addBooleanOption('O', "oss");

    final CmdLineParser.Option routerOpt =
      parser.addBooleanOption('r', "router");
    
    final CmdLineParser.Option xostOpt =
      parser.addBooleanOption('x', "xost");

    final CmdLineParser.Option xossOpt =
      parser.addBooleanOption('X', "xoss");

    
    try {

      parser.parse(cmdLine);

    }
    catch (CmdLineParser.UnknownOptionException e) {
      Ltop.close();
      Ltop.dumpThrowable(e, System.out);
      System.exit(1);
    }

    final boolean help = 
      ((Boolean) parser.getOptionValue(helpOpt, Boolean.FALSE)).booleanValue();

    if (help) {
      Ltop.close();
      Ltop.usage();
      System.exit(0);
    }

    final boolean list = 
      ((Boolean) parser.getOptionValue(listOpt, Boolean.FALSE)).booleanValue();

    if (list) {
      Ltop.close();
      Ltop.list();
      System.exit(0);
    }
    

    Ltop.interval =
      ((Integer) parser.getOptionValue(
        intervalOpt, new Integer(Ltop.DEFAULT_INTERVAL))).intValue();

    boolean filesystem =
      ((Boolean) parser.getOptionValue(
        filesystemOpt, Boolean.FALSE)).booleanValue();
    
    boolean mds =
      ((Boolean) parser.getOptionValue(
        mdsOpt, Boolean.FALSE)).booleanValue();

    boolean ost =
      ((Boolean) parser.getOptionValue(
        ostOpt, Boolean.FALSE)).booleanValue();

    boolean oss =
      ((Boolean) parser.getOptionValue(
        ossOpt, Boolean.FALSE)).booleanValue();

    boolean router =
      ((Boolean) parser.getOptionValue(
        routerOpt, Boolean.FALSE)).booleanValue();

    boolean routerGroup =
      ((Boolean) parser.getOptionValue(
        routerGroupOpt, Boolean.FALSE)).booleanValue();
    
    boolean routerSummary =
      ((Boolean) parser.getOptionValue(
        routerSummaryOpt, Boolean.FALSE)).booleanValue();
    
    boolean xoss = 
      ((Boolean) parser.getOptionValue(
        xossOpt, Boolean.FALSE)).booleanValue();

    boolean xost = 
      ((Boolean) parser.getOptionValue(
        xostOpt, Boolean.FALSE)).booleanValue();

    if (debug) {
      System.err.println(Debug.tag() + "filesystem = "    + router);
      System.err.println(Debug.tag() + "interval = "      + interval);
      System.err.println(Debug.tag() + "mds = "           + mds);
      System.err.println(Debug.tag() + "oss = "           + oss);
      System.err.println(Debug.tag() + "ost = "           + ost);
      System.err.println(Debug.tag() + "router = "        + router);
      System.err.println(Debug.tag() + "routerGroup = "   + routerGroup);
      System.err.println(Debug.tag() + "routerSummary = " + routerSummary);
      System.err.println(Debug.tag() + "xoss = "          + xoss);
      System.err.println(Debug.tag() + "xost = "          + xost);
    }

    if (filesystem)         Ltop.mode = Ltop.FILESYSTEM_MODE;
    else if (mds)           Ltop.mode = Ltop.MDS_MODE;
    else if (oss)           Ltop.mode = Ltop.OSS_MODE;
    else if (ost)           Ltop.mode = Ltop.OST_MODE;
    else if (router)        Ltop.mode = Ltop.ROUTER_MODE;
    else if (routerGroup)   Ltop.mode = Ltop.ROUTER_GROUP_MODE;
    else if (routerSummary) Ltop.mode = Ltop.ROUTER_SUMMARY_MODE;
    else if (xoss)          Ltop.mode = Ltop.OSS_EXTENDED_MODE;
    else if (xost)          Ltop.mode = Ltop.OST_EXTENDED_MODE;
    
    return parser.getRemainingArgs(); // args other than option flags

  } // processOptions

  //////////////////////////////////////////////////////////////////////////////

  private static void processArgs(String[] args)
  throws Exception
  {

    if (args.length == 1) {
      Ltop.filesystemName = args[0];
    }
    else if (args.length > 1) {
      Ltop.close();
      System.out.println("Incorrect number of arguments");
      System.exit(1);
    }
    
  } // processArgs

  //////////////////////////////////////////////////////////////////////////////

  private static void reportOst()
  throws Exception
  {

    if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
      Ltop.doDateHeader(
        "OST_DATA",
        Ltop.filesystemName + ":  OST Extended Report");
    }
    else {
      Ltop.doDateHeader("OST_DATA", Ltop.filesystemName + ":  OST Report");
    }

    final int width = 15;

    final Database.OstData ostData = Ltop.database.getCurrentOstData();

    //int cursesColor = Color.getCursesColor(Color.red, Color.blue);
    

    Ltop.print(
      Ltop.format("OST Name", width, true) +
      Ltop.format("Read (MB/s)", width, false) +
      Ltop.format("Write (MB/s)", width, false),
      Toolkit.A_BOLD);

    if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {

      Ltop.print(
        //Ltop.format("%CPU Used", width, false) +
        Ltop.format("%Space Used", width, false) +
        Ltop.format("%Inodes Used", width, false),
        Toolkit.A_BOLD);
      
    }

    Ltop.println();

    boolean[] indices = new boolean[ostData.getSize()];
    int count = 0;              // count of OSTs that we are reporting on.

    for (int i = 0; i < ostData.getSize(); i++) {

      indices[i] = true;
      count++;

      Ltop.print(
        Ltop.format(ostData.getOstName(i), width, true) +
        Ltop.format(ostData.getReadRate(i), width) +
        Ltop.format(ostData.getWriteRate(i), width));
        
      if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
        Ltop.print(
          //Ltop.format(ostData.getPctCpu(i), width) +
          Ltop.format(ostData.getPctKbytes(i), width) +
          Ltop.format(ostData.getPctInodes(i), width));

      }

      Ltop.println();


    } // for i


    if (count > 1) {

      //
      // Divider
      //

      final String dashes = "--------------------".substring(0, width - 5);

      
      Ltop.print(
        Ltop.format(dashes, width, true) +
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false));
      
      if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
        Ltop.print(
          //Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false));

      }
      
      Ltop.println();


      //
      // Max
      //

      Ltop.print(
        Ltop.format("Maximum", width, true), Toolkit.A_BOLD);
      
      Ltop.print(
        Ltop.format(ostData.getReadRateMax(indices), width) +
        Ltop.format(ostData.getWriteRateMax(indices), width),
        Toolkit.A_BOLD);
      

      if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
        Ltop.print(
          //Ltop.format(ostData.getPctCpuMax(indices), width) +
          Ltop.format(ostData.getPctKbytesMax(indices), width) +
          Ltop.format(ostData.getPctInodesMax(indices), width),
          Toolkit.A_BOLD);
      }
      
      Ltop.println();


      //
      // Average
      //

      Ltop.print(
        Ltop.format("Average", width, true), Toolkit.A_BOLD);

      Ltop.print(
        Ltop.format(ostData.getReadRateAvg(indices), width) +
        Ltop.format(ostData.getWriteRateAvg(indices), width),
        Toolkit.A_BOLD);

      if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
        Ltop.print(
          //Ltop.format(ostData.getPctCpuAvg(indices), width) +
          Ltop.format(ostData.getPctKbytesAvg(indices), width) +
          Ltop.format(ostData.getPctInodesAvg(indices), width),
          Toolkit.A_BOLD);

      }

      Ltop.println();

      //
      // Aggregate
      //

      Ltop.print(
        Ltop.format("Aggregate", width, true), Toolkit.A_BOLD);

      Ltop.print(
        Ltop.format(ostData.getReadRateSum(indices), width) +
        Ltop.format(ostData.getWriteRateSum(indices), width),
        Toolkit.A_BOLD);


      if (Ltop.mode == Ltop.OST_EXTENDED_MODE) {
        Ltop.print(
          //Ltop.format(" ", width) + 
          Ltop.format(ostData.getPctKbytes(indices), width) +
          Ltop.format(ostData.getPctInodes(indices), width),
          Toolkit.A_BOLD);
      }
      
    }
    
  } // reportOst

  //////////////////////////////////////////////////////////////////////////////

  private static void reportOss()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    // fixme:  change to OSS_DATA when OSS_DATA table is populated.
    if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
      Ltop.doDateHeader(
        "OST_DATA",
        Ltop.filesystemName + ":  OSS Extended Report");
    }
    else {
      Ltop.doDateHeader("OST_DATA", Ltop.filesystemName + ":  OSS Report");
    }

    final int width = 15;

    final Database.OssData ossData = Ltop.database.getCurrentOssData();

    Ltop.print(
      Ltop.format("OSS Name", width, false) +
      Ltop.format("Read (MB/s)", width, false) +
      Ltop.format("Write (MB/s)", width, false),
      Toolkit.A_BOLD);

    if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {

      Ltop.print(
        Ltop.format("%CPU Used", width, false) +
        Ltop.format("%Space Used", width, false) +
        Ltop.format("%Inodes Used", width, false),
        Toolkit.A_BOLD);
      
    }

    Ltop.println();

    boolean[] indices = new boolean[ossData.getSize()];
    int count = 0;              // count of OSTs that we are reporting on.

    for (int i = 0; i < ossData.getSize(); i++) {

      indices[i] = true;
      count++;

      Ltop.print(
        Ltop.format(ossData.getHostname(i), width, false) +
        Ltop.format(ossData.getReadRate(i), width) +
        Ltop.format(ossData.getWriteRate(i), width));
        
      if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
        Ltop.print(
          Ltop.format(ossData.getPctCpu(i), width) +
          Ltop.format(ossData.getPctKbytes(i), width) +
          Ltop.format(ossData.getPctInodes(i), width));

      }

      Ltop.println();


    } // for i


    if (count > 1) {

      //
      // Divider
      //

      final String dashes = "--------------------".substring(0, width - 5);

      
      Ltop.print(
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false));
      
      if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
        Ltop.print(
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false));

      }
      
      Ltop.println();


      //
      // Max
      //

      Ltop.print(
        Ltop.format("Maximum", width, false), Toolkit.A_BOLD);
      
      Ltop.print(
        Ltop.format(ossData.getReadRateMax(indices), width) +
        Ltop.format(ossData.getWriteRateMax(indices), width),
        Toolkit.A_BOLD);
      

      if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
        Ltop.print(
          Ltop.format(ossData.getPctCpuMax(indices), width) +
          Ltop.format(ossData.getPctKbytesMax(indices), width) +
          Ltop.format(ossData.getPctInodesMax(indices), width),
          Toolkit.A_BOLD);
      }
      
      Ltop.println();


      //
      // Average
      //

      Ltop.print(
        Ltop.format("Average", width, false), Toolkit.A_BOLD);

      Ltop.print(
        Ltop.format(ossData.getReadRateAvg(indices), width) +
        Ltop.format(ossData.getWriteRateAvg(indices), width),
        Toolkit.A_BOLD);

      if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
        Ltop.print(
          Ltop.format(ossData.getPctCpuAvg(indices), width) +
          Ltop.format(ossData.getPctKbytesAvg(indices), width) +
          Ltop.format(ossData.getPctInodesAvg(indices), width),
          Toolkit.A_BOLD);

      }

      Ltop.println();

      //
      // Aggregate
      //

      Ltop.print(
        Ltop.format("Aggregate", width, false), Toolkit.A_BOLD);

      Ltop.print(
        Ltop.format(ossData.getReadRateSum(indices), width) +
        Ltop.format(ossData.getWriteRateSum(indices), width),
        Toolkit.A_BOLD);
       
      if (Ltop.mode == Ltop.OSS_EXTENDED_MODE) {
        Ltop.print(
          Ltop.format(" ", width) + 
          Ltop.format(ossData.getPctKbytes(indices), width) +
          Ltop.format(ossData.getPctInodes(indices), width),
          Toolkit.A_BOLD);
      }
      
    }
    
  } // reportOss

  //////////////////////////////////////////////////////////////////////////////

  private static void reportRouter()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");
    
    Ltop.doDateHeader(
      "ROUTER_DATA",
      Ltop.filesystemName + ":  Router Detailed Report");

    final int width = 15;

    final int[] ids = Ltop.database.getRouterGroupIds();

    for (int j = 0; j < ids.length; j++) {

      final Database.RouterData routerData =
        Ltop.database.getCurrentRouterData(ids[j]);

      if (j > 0) Ltop.println();

      Ltop.println(
        Ltop.format("Router Name", width, false) +
        Ltop.format("Rate (MB/s)",   width, false) +
        Ltop.format("%CPU Used",   width, false),
        Toolkit.A_BOLD);
      
      boolean[] indices = new boolean[routerData.getSize()];
      
      for (int i = 0; i < routerData.getSize(); i++) {

        indices[i] = true;

        Ltop.println(
          Ltop.format(routerData.getRouterName(i), width, false) +
          Ltop.format(routerData.getRate(i), width) +
          Ltop.format(routerData.getPctCpu(i), width));
        
      } // for i

      if (routerData.getSize() > 1) {

        //
        // Divider
        //

        final String dashes = "--------------------".substring(0, width - 5);

        Ltop.println(
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false));

        //
        // Max
        //

        Ltop.print(
          Ltop.format("Maximum", width, false), Toolkit.A_BOLD);

        Ltop.println(
          Ltop.format(routerData.getRateMax(indices), width) +
          Ltop.format(routerData.getPctCpuMax(indices), width),
          Toolkit.A_BOLD);

        //
        // Average
        //

        Ltop.print(
          Ltop.format("Average", width, false), Toolkit.A_BOLD);

        Ltop.println(
          Ltop.format(routerData.getRateAvg(indices), width) +
          Ltop.format(routerData.getPctCpuAvg(indices), width),
          Toolkit.A_BOLD);
        
        //
        // Aggregate
        //

        Ltop.print(
          Ltop.format("Aggregate", width, false),  Toolkit.A_BOLD);

        Ltop.println(
          Ltop.format(routerData.getRateSum(indices), width),
          Toolkit.A_BOLD);
        
      }

    } // for j

  } // reportRouter

  //////////////////////////////////////////////////////////////////////////////

  private static void reportRouterAll()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    Ltop.doDateHeader("ROUTER_DATA", Ltop.filesystemName + ":  Router Summary");
    
    float rateMax = 0.0f;
    float rateSum = 0.0f;
    float rateAve = 0.0f;

    float pctCpuMax = 0.0f;
    float pctCpuSum = 0.0f;
    float pctCpuAve = 0.0f;

    int count = 0;


    final int width = 15;

    final int[] ids = Ltop.database.getRouterGroupIds();


    Ltop.println(
      Ltop.format("Router Name", width, false) +
      Ltop.format("Rate (MB/s)",     width, false) +
      Ltop.format("%CPU Used",   width, false),
      Toolkit.A_BOLD);

    for (int j = 0; j < ids.length; j++) {

      final Database.RouterData routerData =
        Ltop.database.getCurrentRouterData(ids[j]);

      boolean[] indices = new boolean[ routerData.getSize()];
      
      for (int i = 0; i < routerData.getSize(); i++) {

        indices[i] = true;


        Float rate = routerData.getRate(i);
        Float pctCpu = routerData.getPctCpu(i);

        Ltop.println(
          Ltop.format(routerData.getRouterName(i), width, false) +
          Ltop.format(rate, width) +
          Ltop.format(pctCpu, width));

        if (rate != null) {
          
          if (rate.floatValue() > rateMax)
            rateMax = rate.floatValue();
          
          rateSum += rate.floatValue();
        }

        if (pctCpu != null) {

          if (pctCpu.floatValue() > pctCpuMax)
            pctCpuMax = pctCpu.floatValue();
          
          pctCpuSum += pctCpu.floatValue();
        }

        count++;
        
      } // for i

    } // for j


    if (count > 0) {
      rateAve = rateSum/((float) count);
      pctCpuAve = pctCpuSum/((float) count);
    }

    //
    // Divider
    //
    
    final String dashes = "--------------------".substring(0, width - 5);
    
    Ltop.println(
      Ltop.format(dashes, width, false) +
      Ltop.format(dashes, width, false) +
      Ltop.format(dashes, width, false));


    //
    // Max
    //
    
    Ltop.print(
      Ltop.format("Maximum", width, false), Toolkit.A_BOLD);
    
    Ltop.println(
      Ltop.format(rateMax, width) +
      Ltop.format(pctCpuMax, width),
      Toolkit.A_BOLD);
    
    //
    // Average
    //
    
    Ltop.print(
      Ltop.format("Average", width, false), Toolkit.A_BOLD);
    
    Ltop.println(
      Ltop.format(rateAve, width) +
      Ltop.format(pctCpuAve, width),
      Toolkit.A_BOLD);
    
    //
    // Aggregate
    //
    
    Ltop.print(
      Ltop.format("Aggregate", width, false),  Toolkit.A_BOLD);
    
    Ltop.println(
      Ltop.format(rateSum, width),
      Toolkit.A_BOLD);
    
  } // reportRouterAll
  
  /////////////////////////////////////////////////////////////////////////////

  private static void reportRouterGroup()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    Ltop.doDateHeader(
      "ROUTER_DATA", 
      Ltop.filesystemName + ":  Router Group Report");

    float rateMaxMax = 0.0f;
    float rateAvgMax = 0.0f;
    float rateAggMax = 0.0f;
    float cpuMaxMax = 0.0f;
    float cpuAvgMax = 0.0f;
    float rateAggAgg = 0.0f;

    
    final int width1 = 16;
    final int width2 = 10;
    
    final String dashes1 = "--------------------".substring(0, width1 - 5);
    final String dashes2 = "--------------------".substring(0, width2 - 3);


    final int[] ids = Ltop.database.getRouterGroupIds();

    String lab1 = "Rate (MB/s)";
    String lab2 = "%CPU Used";

    int len1 = lab1.length();
    int len2 = lab2.length();

    // Centering calculations for labels
    int w1 = 2*width1 + width2+ len1/2 - 1 ;
    int w2 = 3*width1 + 2*width2 + (width2 + len2)/2 - w1 - 1;


    Ltop.println(
      Ltop.format(lab1, w1, false) +
      Ltop.format(lab2, w2, false),
      Toolkit.A_BOLD);
    
    Ltop.println(
      Ltop.format("Router Group", width1, false) +
      Ltop.format("Max",          width1, false) +
      Ltop.format("Avg",          width2, false) +
      Ltop.format("Agg",          width2, false) +
      Ltop.format("Max",          width1, false) +
      Ltop.format("Avg",          width2, false),
      Toolkit.A_BOLD);
    
    for (int j = 0; j < ids.length; j++) {

      final Database.RouterData routerData =
        Ltop.database.getCurrentRouterData(ids[j]);

      int size = routerData.getSize();


      boolean[] indices = new boolean[size];
      String[] names = new String[size];

      for (int i = 0; i < size; i++) {
        indices[i] = true;      // get info for all routers
        names[i] = routerData.getRouterName(i);
      } // for i

      //Ltop.print(Ltop.format(Grouper.getNameForGroup(names), width1, false));

//       String[] parts = Ltop.wrap("   ", Grouper.getNameForGroup(names), width1);

//       for (int k = 0; k < parts.length; k++) {
        
//         Ltop.print(Ltop.format(parts[k], width1, true));
//         if (k < parts.length - 1) Ltop.println();
        
//       } //  for k
      



      String[] ranges = Grouper.getNamesForGroup(names);
      
      for (int k = 0; k < ranges.length; k++) {
        
        Ltop.print(Ltop.format(ranges[k], width1, false));
         if (k < ranges.length - 1) Ltop.println();
         
      } //  for k
      


      Float rateMax = routerData.getRateMax(indices);
      Float rateAvg = routerData.getRateAvg(indices);
      Float rateAgg = routerData.getRateSum(indices);
      Float cpuMax = routerData.getPctCpuMax(indices);
      Float cpuAvg = routerData.getPctCpuAvg(indices);
      
      Ltop.print(Ltop.format(rateMax,  width1));
      Ltop.print(Ltop.format(rateAvg,  width2));
      Ltop.print(Ltop.format(rateAgg,  width2));
      Ltop.print(Ltop.format(cpuMax, width1));
      Ltop.print(Ltop.format(cpuAvg, width2));
            
      Ltop.println();

      if (ids.length > 1 && j < ids.length - 1) Ltop.println();

      rateMaxMax  = Ltop.max(rateMaxMax,  rateMax);
      rateAvgMax  = Ltop.max(rateAvgMax,  rateAvg);
      rateAggMax  = Ltop.max(rateAggMax,  rateAgg);
      cpuMaxMax = Ltop.max(cpuMaxMax, cpuMax);
      cpuAvgMax = Ltop.max(cpuAvgMax, cpuAvg);

      if (rateAgg != null) {
        rateAggAgg += rateAgg.floatValue();
      }

    } // for j

    Ltop.println(
      Ltop.format(dashes1, width1, false) +
      Ltop.format(dashes2, width1, false) +
      Ltop.format(dashes2, width2, false) +
      Ltop.format(dashes2, width2, false) +
      Ltop.format(dashes2, width1, false) +
      Ltop.format(dashes2, width2, false));

    Ltop.println(
      Ltop.format("Maximum",     width1, false) +
      Ltop.format(rateMaxMax,  width1) +
      Ltop.format(rateAvgMax,  width2) +
      Ltop.format(rateAggMax,  width2) +
      Ltop.format(cpuMaxMax, width1) +
      Ltop.format(cpuAvgMax, width2),
      Toolkit.A_BOLD);

    Ltop.println(
      Ltop.format("Aggregate", width1, false) +
      Ltop.format(rateAggAgg,    width1 + 2*width2),
      Toolkit.A_BOLD);


  } // reportRouterGroup

  /////////////////////////////////////////////////////////////////////////////

  private static void reportMds()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    Ltop.doDateHeader(
      "MDS_DATA",
      Ltop.filesystemName + ":  Metadata Server Report");
    
    final int width = 15;
    
    final Database.MdsInfo[] mdsInfo = Ltop.database.getMdsInfo();
    
    for (int j = 0; j < mdsInfo.length; j++) {

      //
      // MDS Data
      //

      final Database.MdsData mdsData =
        Ltop.database.getCurrentMdsData(mdsInfo[j].mdsId);

      Ltop.println(
        Ltop.format("MDS Name",    width, false) +
        Ltop.format("%CPU Used",   width, false) +
        Ltop.format("%Space Used", width, false) +
        Ltop.format("%Inode Used", width, false),
        Toolkit.A_BOLD);
    
      boolean[] indices = new boolean[mdsData.getSize()];
    
      for (int i = 0; i < mdsData.getSize(); i++) {
      
        indices[i] = true;

        Ltop.println(
          Ltop.format(mdsData.getMdsName(i),   width, false) +
          Ltop.format(mdsData.getPctCpu(i),    width) +
          Ltop.format(mdsData.getPctKbytes(i), width) +
          Ltop.format(mdsData.getPctInodes(i), width));
        
      } // for i
    
      if (mdsData.getSize() > 1) {

        final String dashes = "--------------------".substring(0, width - 5);

        Ltop.println(
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false) +
          Ltop.format(dashes, width, false));

        Ltop.println(
          Ltop.format("Average", width, false), Toolkit.A_BOLD);


        Ltop.println(
          Ltop.format(mdsData.getPctCpuAvg(indices), width) +
          Ltop.format(mdsData.getPctKbytesAvg(indices), width) +
          Ltop.format(mdsData.getPctInodesAvg(indices), width));
               
      }


      //
      // MDS Ops Data
      //

      final Database.MdsOpsData mdsOpsData =
        Ltop.database.getCurrentMdsOpsData(mdsInfo[j].mdsId);

      Ltop.println();

      Ltop.println(
        Ltop.format("Operation",   width + 10, false) +
        Ltop.format("Samples",     width, false) +
        Ltop.format("Samples/sec", width, false) +
        Ltop.format("Avg Value",   width, false) +
        Ltop.format("Std Dev",     width, false) +
        Ltop.format("Units",       width, false),
        Toolkit.A_BOLD);
    
      for (int i = 0; i < mdsOpsData.getSize(); i++) {

        Ltop.println(
          Ltop.format(mdsOpsData.getOpName(i),        width + 10, false) +
          Ltop.format(mdsOpsData.getSamples(i),       width) +
          Ltop.format(mdsOpsData.getSamplesPerSec(i), width) +
          Ltop.format(mdsOpsData.getAvgVal(i),        width) +
          Ltop.format(mdsOpsData.getStdDev(i),        width) +
          Ltop.format(mdsOpsData.getUnits(i),         width, false));

        
      } // for i
    
    } //  for j
    
  } // reportMds

  //////////////////////////////////////////////////////////////////////////////

  private static void reportFilesystem()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    Ltop.doDateHeader("OST_DATA", "Filesystem Summary");
    
    final int width = 15;

    float readRateAgg = 0.0f;
    float writeRateAgg = 0.0f;

    long kbytesFreeAgg = 0L;
    long kbytesUsedAgg = 0L;
    
    long inodesFreeAgg = 0L;
    long inodesUsedAgg = 0L;

    
    String currentName =
      Ltop.databases[Ltop.filesystemIndex].getFilesystemInfo().filesystemName;

//     final Database.FilesystemData[] filesystemData =
//       Database.getCurrentFilesystemData();

    Ltop.print(
      Ltop.format("Filesystem",     width, false) +
      Ltop.format("Read (MB/s)",    width, false) +
      Ltop.format("Write (MB/s)",   width, false) +
      Ltop.format("%Space Used",  width, false) +
      Ltop.format("%Inodes Used", width, false),
      Toolkit.A_BOLD);
      

    Ltop.println();

    for (int i = 0; i < Ltop.databases.length; i++) {

      Database.FilesystemInfo filesystemInfo =
        Ltop.databases[i].getFilesystemInfo();

      if (filesystemInfo.filesystemName.equals(currentName)) {

        Ltop.printReverse(filesystemInfo.filesystemName, width, false);
      }
      else {
        Ltop.print(
          Ltop.format(filesystemInfo.filesystemName, width, false));
      }

      if (!Ltop.databases[i].isConnected()) {

        Ltop.printReverse(
          "******************* Failed to connect ******************",
          width*4,
          false);
        Ltop.println();

      }
      else {

        Database.FilesystemData filesystemData =
          Ltop.databases[i].getCurrentFilesystemData();

        Ltop.print(
          Ltop.format(filesystemData.readRate,       width) +
          Ltop.format(filesystemData.writeRate,      width) +
          Ltop.format(filesystemData.pctKbytes,      width) +
          Ltop.format(filesystemData.pctInodes,      width));
        
        Ltop.println();
        
        readRateAgg += Ltop.value(filesystemData.readRate);
        writeRateAgg += Ltop.value(filesystemData.writeRate);
        
        kbytesFreeAgg += Ltop.value(filesystemData.kbytesFree);
        kbytesUsedAgg += Ltop.value(filesystemData.kbytesUsed);
        
        inodesFreeAgg += Ltop.value(filesystemData.inodesFree);
        inodesUsedAgg += Ltop.value(filesystemData.inodesUsed);
        
      }

      } // for i


    if (Ltop.databases.length > 1) {

      //
      // Divider
      //

      final String dashes = "--------------------".substring(0, width - 5);

      
      Ltop.print(
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false) +
        Ltop.format(dashes, width, false));
          
      Ltop.println();
            
      //
      // Aggregate
      //
      
      Ltop.print(
        Ltop.format("Aggregate", width, false), Toolkit.A_BOLD);
      
      Ltop.print(
        Ltop.format(readRateAgg, width) +
        Ltop.format(writeRateAgg, width) +
        Ltop.format(Database.percentUsed(kbytesUsedAgg, kbytesFreeAgg), width) +
        Ltop.format(Database.percentUsed(inodesUsedAgg, inodesFreeAgg), width),
        Toolkit.A_BOLD);
    }
    
  } // reportFilesystem
  
  //////////////////////////////////////////////////////////////////////////////

  private static void reportError()
  throws Exception
  {

    Ltop.println(
      Ltop.format(
        "--- Connection Error Report ---  " +
        "(press any key to continue, or 'q' to quit)",
        Ltop.screenColumns,
        true),
      Toolkit.A_REVERSE);

    Ltop.println();

    for (int i = 0; i < Ltop.databases.length; i++) {

      Database.FilesystemInfo filesystemInfo =
        Ltop.databases[i].getFilesystemInfo();
      
      Ltop.println(filesystemInfo.filesystemName, Toolkit.A_REVERSE);
      Ltop.println();

      Exception ex = Ltop.databases[i].getConnectException();

      if (ex == null) {

        Ltop.print("   No errors.");
        Ltop.println();

      }
      else {

        String[] msg = Ltop.wrap("   ", ex.getMessage(), Ltop.screenColumns);
        
        for (int j = 0; j < msg.length; j++) {

          Ltop.print(msg[j]);
          Ltop.println();

        } //  for j

      }

      Ltop.println();

    } // for i

  } // reportError

  //////////////////////////////////////////////////////////////////////////////

  private static String[] expand(final String expression)
  throws Exception
  {

    final ArrayList arrayList = new ArrayList();

    final String[] split = Ltop.split(expression);

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

          if (debug) System.err.println(Debug.tag() + "parts[0] = " + parts[0]);

          final int id = Integer.parseInt(parts[0].trim());

          arrayList.add(prefix + id);
        }
        else if (parts.length == 2) {
        
          if (debug) {
            System.err.println(Debug.tag() + "parts[0] = " + parts[0]);
            System.err.println(Debug.tag() + "parts[1] = " + parts[1]);
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

  private static void help()
  {

    Ltop.println();
    Ltop.println("Commands:");
    Ltop.println();
    Ltop.println("   a - show router summary");
    Ltop.println("   e - show connection error report");
    Ltop.println("   f - show summary info for all filesystems");
    Ltop.println("   g - show router group report");
    Ltop.println("   h - display this help");
    Ltop.println("   m - show MDS report");
    Ltop.println("   o - show OST report");
    Ltop.println("   O - show OSS report");
    Ltop.println("   q - quit program");
    Ltop.println("   r - show detailed router report");
    Ltop.println("   x - show extended OST report");
    Ltop.println("   X - show extended OSS report");
    Ltop.println();
    Ltop.println("   up arrow - select previous filesystem");
    Ltop.println("   down arrow - select next filesystem");
    Ltop.println();
    Ltop.println("   page up - scroll display up");
    Ltop.println("   page down - scroll display down");


  } // help

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final Float value,
    final int width)
  {

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      Ltop.decimalFormat.format(value.floatValue()) + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final float value,
    final int width)
  {

    return format(
      Ltop.decimalFormat.format(value) + "",
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
    final int value,
    final int width)
  {

    return format(
      value + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

  private static String format(
    final String value,
    final int size)
  {

    return Ltop.format(
      value,
      size,
      false);

  } //  format

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

  private static void println(String value) {

    Ltop.println(value, Toolkit.A_NORMAL);

  } // println

  //////////////////////////////////////////////////////////////////////////////

  private static void println(String value, int attrib) {

    Ltop.toolkit.addString(
      value,
      attrib,
      0);
    
    Ltop.row++;
    Ltop.maxRow = Ltop.row;
    Ltop.toolkit.setCursor(0, row);   // (col, row)


  } // println

  //////////////////////////////////////////////////////////////////////////////

  private static void println(
    String value,
    int attrib,
    int colorPair)
  {

    Ltop.toolkit.addString(
      value,
      attrib,
      colorPair);
    
    Ltop.row++;
    Ltop.maxRow = Ltop.row;
    Ltop.toolkit.setCursor(0, row);   // (col, row)


  } // println


  //////////////////////////////////////////////////////////////////////////////

  private static void println() {

    Ltop.row++;
    Ltop.maxRow = Ltop.row;
    Ltop.toolkit.setCursor(0, row);   // (col, row)

  } // println

  //////////////////////////////////////////////////////////////////////////////

  private static void print(String value) {

    Ltop.print(value, Toolkit.A_NORMAL);

  } // print

  //////////////////////////////////////////////////////////////////////////////

  private static void print(
    String value,
    int attrib) {
    
    Ltop.toolkit.addString(
      value,
      attrib,
      0);

  } // print

  //////////////////////////////////////////////////////////////////////////////

  private static void print(
    String value,
    int attrib,
    int colorPair)
  {
    
    Ltop.toolkit.addString(
      value,
      attrib,
      colorPair);

  } // print

  //////////////////////////////////////////////////////////////////////////////

  private static void printReverse(
    String value,
    int size,
    final boolean leftJustified)
  {

    String padded = Ltop.format(value, size, leftJustified);


    int start = -1;
    int stop = padded.length();

    for (int i = 0; i < padded.length(); i++) {

      if (padded.charAt(i) != ' ') {
        start = i;
        break;
      }

    } // for i


    if (start < 0) {
      // Value was all blanks.
      Ltop.print(padded);
      return;
    }
      
    for (int i = padded.length(); i > 0; i--) {

      if (padded.charAt(i - 1) != ' ') {
        stop = i;
        break;
      }

    } // for i



    if (start > 0) {
      Ltop.print(padded.substring(0, start));
    }

    
    Ltop.print(
      padded.substring(start, stop),
      Toolkit.A_REVERSE);


    if (stop < padded.length()) {
      Ltop.print(padded.substring(stop));
    }

  } // printReverse

  //////////////////////////////////////////////////////////////////////////////


  private static void handleKeyboardInput()
  throws Exception
  {
 
    Thread thread = new Thread() {
      public void run() {
         
        try {
           

          File file = new File(toolkit.getTtyName());
           
          FileInputStream fis = new FileInputStream(file);
           
          top: while (true) {
             
            int ch = fis.read();

            if (debug) System.err.println(Debug.tag() + ch + " " + ((char) ch));

            //
            // Ignore escape sequences generated by xterm due to mouse clicks.
            //

            while (true) {

              if (ch == '\033') {

                ch = fis.read();
                if (debug) System.err.println(
                  Debug.tag() + ch + " " + ((char) ch));

                if (ch == '[') {

                  ch = fis.read();
                  if (debug) System.err.println(
                    Debug.tag() + ch + " " + ((char) ch));

                  if (ch == '5') {

                    ch = fis.read();
                    if (debug) System.err.println(
                      Debug.tag() + ch + " " + ((char) ch));
                    
                    if (ch == '~') {
                      
                      // Page up
                      Ltop.scrollOffset += Ltop.SCROLL_INCREMENT;
                      if (Ltop.scrollOffset > 0) Ltop.scrollOffset = 0;
                      Ltop.draw();
                      if (debug) System.err.println( Debug.tag() + "page up");
                      
                    }

                    continue top;

                  }

                  if (ch == '6') {

                    ch = fis.read();
                    if (debug) System.err.println(
                      Debug.tag() + ch + " " + ((char) ch));

                    if (ch == '~') {                      
                      
                      // Page down

                      if (Ltop.maxRow >=  Ltop.screenRows) {
                        Ltop.scrollOffset -= Ltop.SCROLL_INCREMENT;
                      }
                      Ltop.draw();
                      if (debug) System.err.println( Debug.tag() + "page down");
                      
                    }

                    continue top;

                  }

                  if (ch == 'M') {
                    // throw away 3 more chars
                    fis.read();
                    fis.read();
                    fis.read();
                    //ch = fis.read();
                    continue top;
                  }
                }
                else if (ch == 'O') {

                  ch = fis.read();
                  if (debug) System.err.println(
                    Debug.tag() + ch + " " + ((char) ch));
                  
                  if (ch == 'A' || ch == 'D') {
                    // Up arrow
                    Ltop.changeFilesystem(-1);
                    Ltop.draw();
                    continue top;
                  }
                  else if (ch == 'B' || ch == 'C') {
                    // Down arrow
                    Ltop.changeFilesystem(1);
                    Ltop.draw();
                    continue top;
                  }

                }
                
              }
              else {
                break;
              }

            } // while

            if (ch == 'q' || ch == '\003') {
              Ltop.close();
              System.exit(0);
            }
 
            int oldMode =  Ltop.mode;

            if      (ch == 'O') Ltop.mode = Ltop.OSS_MODE;
            else if (ch == 'X') Ltop.mode = Ltop.OSS_EXTENDED_MODE;
            else if (ch == 'a') Ltop.mode = Ltop.ROUTER_SUMMARY_MODE;
            else if (ch == 'e') Ltop.mode = Ltop.ERROR_MODE;
            else if (ch == 'f') Ltop.mode = Ltop.FILESYSTEM_MODE;
            else if (ch == 'g') Ltop.mode = Ltop.ROUTER_GROUP_MODE;
            else if (ch == 'h') Ltop.mode = Ltop.HELP_MODE;
            else if (ch == 'm') Ltop.mode = Ltop.MDS_MODE;
            else if (ch == 'o') Ltop.mode = Ltop.OST_MODE;
            else if (ch == 'r') Ltop.mode = Ltop.ROUTER_MODE;
            else if (ch == 'x') Ltop.mode = Ltop.OST_EXTENDED_MODE;

            else {
              if (debug) {
                System.err.flush();
              }
              // Unexcpected input -- switch to help screen.
              Ltop.mode = Ltop.HELP_MODE;
              Ltop.toolkit.beep();
            }
             
            // Scroll to top if changes modes.
            if (Ltop.mode != oldMode) Ltop.scrollOffset = 0;

            Ltop.draw();
            
          } // while
        }
        catch (Exception e) {
          if (debug) {
            System.err.println(Debug.tag() + "saw exception " + e.getClass());
            e.printStackTrace(System.err);
          }
          Ltop.close();
          Ltop.dumpThrowable(e, System.out);
          System.exit(1);
        }
 
      } // run
    }; // thread
 
    thread.start();
 
  } // handleKeyboardInput

  //////////////////////////////////////////////////////////////////////////////

  private static void handleWindowResize()
  {

    //
    // A bit of a hack to redraw contents when window is resized.  Better way
    // would be to wait for window resize event, but so far I can't see how to 
    // get that from Charva.
    //

    Ltop.resizeTimer = new Timer();
    TimerTask timerTask = new TimerTask() {

      public void run(){

        synchronized (Ltop.drawSync) {
          Ltop.toolkit.sync();
        }

        int newRows = Ltop.toolkit.getScreenRows();
        int newColumns = Ltop.toolkit.getScreenColumns();

        if (newRows != Ltop.screenRows || newColumns != Ltop.screenColumns) {

          Ltop.screenRows = newRows;
          Ltop.screenColumns = newColumns;
          
          Ltop.draw();

        }


      } // run

    };

    Ltop.resizeTimer.schedule(timerTask, 0L, 250L);

  } // handleWindowResize

  //////////////////////////////////////////////////////////////////////////////

  private static void usage()
  {

    System.out.println("Usage:");
    System.out.println("       ltop [ option ] [ <filesystem> ]");
    System.out.println();
    System.out.println("       Options are:");
    System.out.println();
    System.out.println("            -a, --router_summary");
    System.out.println("                  initally show router summary report.");
    System.out.println();
    System.out.println("            -f, --filesystem");
    System.out.println("                  initally show filesystem summary.");
    System.out.println();
    System.out.println("            -g, --router_group");
    System.out.println("                  initally show router group report.");
    System.out.println();
    System.out.println("            -h, --help");
    System.out.println("                  display this help");
    System.out.println();
    System.out.println("            -i <secs>, --interval=<secs>");
    System.out.println("                  override the default 5 sec display interval.");
    System.out.println();
    System.out.println("            -l, --list");
    System.out.println("                  list available filesystems");
    System.out.println();
    System.out.println("            -m, --mds");
    System.out.println("                  initially show MDS report");
    System.out.println();
    System.out.println("            -o, --ost");
    System.out.println("                  initally show OST report (default)");
    System.out.println();
    System.out.println("            -O, --oss");
    System.out.println("                  initally show OSS report");
    System.out.println();
    System.out.println("            -r, --router");
    System.out.println("                  initially show router report");
    System.out.println();
    System.out.println("            -x, --xost");
    System.out.println("                  initially show extended OST report");
    System.out.println();
    System.out.println("            -X, --xoss");
    System.out.println("                  initially show extended OSS report");
    System.out.println();
    System.out.println("       Examples:");
    System.out.println();
    System.out.println("         ltop -x");
    System.out.println("         ltop --ost ti2");
    System.out.println();

  } // usage
  
  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * List available filesystems.
   */
  
  private static void list()
  throws Exception
  {

    Database[] databases = Database.getAllDatabases();

 
    if (databases == null || databases.length == 0) {
      System.out.println("No filesystem information avalable.");
      System.exit(1);
    }

    for (int i = 0; i < databases.length; i++) {
      System.out.println(databases[i].getFilesystemInfo().filesystemName);
    } // for i
    
  } // list

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Print header with timestamp.
   * @param tableName table used to extract latest timestamp from.
   * @param label arbitrary text to print following timestamp.
   */

  private static void doDateHeader(
    String tableName,
    String label)
  throws Exception
  {
    
    Database.TimeInfo timeInfo = Ltop.database.getLatestTimeInfo(tableName);

    StringBuffer sb = new StringBuffer();
    
    synchronized (Ltop.simpleDateFormat) {
      
      sb.append(
        Ltop.simpleDateFormat.format(timeInfo.timestamp).toString());
      
    } // synchronized
    
    sb.append(" --- ");
    sb.append(label);

    Ltop.println(
      Ltop.format(sb.toString(), Ltop.screenColumns, true),
      Toolkit.A_REVERSE);

    Ltop.println();
    
    
  } // doDateHeader

  //////////////////////////////////////////////////////////////////////////////

  private static void getDatabases()
  throws Exception
  {

    Ltop.databases = Database.getAllDatabases();

    if (Ltop.databases == null || Ltop.databases.length == 0) {
      Ltop.close();
      System.out.println("No filesystem information avalable.");
      System.exit(1);
    }

    //
    // See if there were any connection errors and start in error screen
    // if there were.
    //
    for (int i = 0; i <  Ltop.databases.length; i++) {

      if (Ltop.databases[i].getConnectException() != null) {

        if (debug) System.err.println(Debug.tag() + 
          Ltop.databases[i].getConnectException().getMessage());

        Ltop.mode = ERROR_MODE;
        break;
      }

    } // 




    int index = 0;
    
    if (Ltop.filesystemName == null) {

      //
      //  Set default database.  Use first available.
      //
      
      for (int i = 0; i <  Ltop.databases.length; i++) {

        if (Ltop.databases[i].isConnected()) {

          Ltop.filesystemName =
            Ltop.databases[i].getFilesystemInfo().filesystemName;

          Ltop.filesystemIndex = i;

          return;

        }

      } //  for i

      Ltop.close();
      System.out.println("No available filesystems located.");
      System.exit(1);

    }
    else {

      boolean found = false;

      for (int i = 0; i < Ltop.databases.length; i++) {

        if (Ltop.filesystemName.equals(
              Ltop.databases[i].getFilesystemInfo().filesystemName))
        {
          Ltop.filesystemIndex = i;
          found = true;
          break;
        }

      } //  for i
      
      if (!found) {
        Ltop.close();
        System.out.println("Unknown filesystem '" + Ltop.filesystemName + "'.");
        System.out.println("Filesystem name must be one of:");

        for (int i = 0; i <  Ltop.databases.length; i++) {
          System.out.println("   " +
            Ltop.databases[i].getFilesystemInfo().filesystemName);
        } // for i

        System.exit(1);
      }
    }

  } // getDatabases

  //////////////////////////////////////////////////////////////////////////////

  private static float max(float value1, Float value2)
  {
    
    if (value2 != null && value2.floatValue() > value1) {

      return value2.floatValue();

    }

    return value1;

  } // max

  //////////////////////////////////////////////////////////////////////////////

  private static float value(Float value)
  {

    if (value == null) return 0.0f;

    return value.floatValue();

  } //  value

  //////////////////////////////////////////////////////////////////////////////

  private static long value(Long value)
  {

    if (value == null) return 0L;

    return value.longValue();

  } //  value

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Stop output and shutdown curses.  After calling this method it will be
   * safe to output to System.out again.
  */

  private static void close()
  {

    synchronized (Ltop.drawSync) {
      
      // Stop watching for resize
      if (Ltop.resizeTimer != null) {
        Ltop.resizeTimer.cancel();
      }

      // Stop producing output
      if (Ltop.drawTimer != null) {
        Ltop.drawTimer.cancel();
      }

      // Shut down curses.
      if (Ltop.toolkit != null) {
        Ltop.toolkit.close();
      }

    } // synchronized


//     try {
//       Thread.currentThread().sleep(5000);
//     }
//     catch (InterruptedException e) {}


    //System.out.println(Debug.tag() + "exit");


  } // close

  //////////////////////////////////////////////////////////////////////////////

  private static void dumpThrowable(
    Throwable throwable,
    PrintStream printStream)
  {

    while (throwable != null) {

      printStream.println(throwable.getMessage());
      throwable = throwable.getCause();

    } // while

  } //  dumpThrowable

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Substitute tabs with spaces so that String length calculations will be
   * correct.
   */

  private static String clean(String value)
  {

    StringBuffer sb = new StringBuffer();

    for (int i = 0; i < value.length(); i++) {

      char ch = value.charAt(i);

      switch (ch) {

        case '\t': sb.append("     ");   break; // use 5 spaces for tabs
        default: sb.append(ch);          break;

      } // switch 

    } //  for i

    return sb.toString();

  } // clean

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Wrap long strings so that they fit on the screen.
   *
   * @param prefix append this String to beginning of all output Strings.
   * @param value String to be wrapped.
   * @param width maximum width of output Strings
   * @return array of output Strings.
   */

  private static String[] wrap(
    String prefix,
    String value,
    int width)
  {

    String[] parts = value.split("\\n");

    ArrayList list = new ArrayList();

    for (int i = 0; i < parts.length; i++ ) {
      
      StringBuffer sb = new StringBuffer(clean(parts[i]));
      
      while (sb.length() > 0) {
        
        int end = Math.min(sb.length(), width - prefix.length());
        
        list.add(prefix + sb.substring(0, end));
        sb.delete(0, end);
        
      } // while
      
    } // for i

    return (String[]) list.toArray(new String[list.size()]);

  } // wrap

  //////////////////////////////////////////////////////////////////////////////

} //  Ltop

