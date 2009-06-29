package gov.llnl.lustre.database;

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

import gov.llnl.lustre.utility.Debug;
import gov.llnl.lustre.utility.TimeIt;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Utility class for performing data query operations on the Lustre monitor
 * database.
 */

public class Database {

  //////////////////////////////////////////////////////////////////////////////

  //
  // Class variables
  //

  private static final boolean localDebug = Boolean.getBoolean("Database.debug");
  private static final boolean debug = Boolean.getBoolean("debug") || localDebug;

  public static final String GLOBAL_LMTRC_FILE_NAME="/usr/share/lmt/etc/lmtrc";

  public static final int ASC = 1;
  public static final int DESC = 2;

  public static final float MEBI = (float) 1024*1024; // mebibyte definition

  // Aggregation levels.
  public static final int RAW   =  1;
  public static final int HOUR  =  2;
  public static final int DAY   =  3;
  public static final int WEEK  =  4;
  public static final int MONTH =  5;
  public static final int YEAR  =  6;

  // Filesystem info from lmtrc file.
  private static FilesystemInfo[] allFilesystemInfo;

  private static List lmtrcList = new ArrayList();

  private static String lmtrcPath;


  //
  // Instance variables
  //

  private boolean isConnected = false;

  private PreparedStatement ossDataQuery;
  private PreparedStatement ostNameQuery;
  private PreparedStatement ostDataQuery;
  private PreparedStatement ostRawDataQuery;
  private PreparedStatement routerDataQuery;
  private PreparedStatement mdsDataQuery;
  private PreparedStatement mdsOpsDataQuery;
  private PreparedStatement routerGroupsQuery;
  private PreparedStatement mdsInfoQuery;
  private PreparedStatement ostVariableInfoQuery;

  private PreparedStatement ostAggregateHourDataQuery;
  private PreparedStatement ostAggregateDayDataQuery;
  private PreparedStatement ostAggregateWeekDataQuery;
  private PreparedStatement ostAggregateMonthDataQuery;
  private PreparedStatement ostAggregateYearDataQuery;

  private PreparedStatement ostAggregateHourDataQuery2;
  private PreparedStatement ostAggregateDayDataQuery2;
  private PreparedStatement ostAggregateWeekDataQuery2;
  private PreparedStatement ostAggregateMonthDataQuery2;
  private PreparedStatement ostAggregateYearDataQuery2;

  private PreparedStatement filesystemAggregateHourDataQuery;
  private PreparedStatement filesystemAggregateDayDataQuery;
  private PreparedStatement filesystemAggregateWeekDataQuery;
  private PreparedStatement filesystemAggregateMonthDataQuery;
  private PreparedStatement filesystemAggregateYearDataQuery;

  private PreparedStatement filesystemAggregateHourDataQuery2;
  private PreparedStatement filesystemAggregateDayDataQuery2;
  private PreparedStatement filesystemAggregateWeekDataQuery2;
  private PreparedStatement filesystemAggregateMonthDataQuery2;
  private PreparedStatement filesystemAggregateYearDataQuery2;

  private PreparedStatement routerAggregateHourDataQuery;
  private PreparedStatement routerAggregateDayDataQuery;
  private PreparedStatement routerAggregateWeekDataQuery;
  private PreparedStatement routerAggregateMonthDataQuery;
  private PreparedStatement routerAggregateYearDataQuery;

  //   private PreparedStatement routerGroupAggregateHourDataQuery;
  //   private PreparedStatement routerGroupAggregateDayDataQuery;
  //   private PreparedStatement routerGroupAggregateWeekDataQuery;
  //   private PreparedStatement routerGroupAggregateMonthDataQuery;
  //   private PreparedStatement routerGroupAggregateYearDataQuery;
  
  private PreparedStatement mdsAggregateHourDataQuery;
  private PreparedStatement mdsAggregateDayDataQuery;
  private PreparedStatement mdsAggregateWeekDataQuery;
  private PreparedStatement mdsAggregateMonthDataQuery;
  private PreparedStatement mdsAggregateYearDataQuery;

  private PreparedStatement mdsAggregateHourDataQuery2;
  private PreparedStatement mdsAggregateDayDataQuery2;
  private PreparedStatement mdsAggregateWeekDataQuery2;
  private PreparedStatement mdsAggregateMonthDataQuery2;
  private PreparedStatement mdsAggregateYearDataQuery2;

  private PreparedStatement routerIdsForGroupQuery;


  private Statement ostDataStatement; 

  private Connection connection;

  private FilesystemInfo filesystemInfo;
  private Exception connectException;

  private String hostName;
  private String port;
  private String databaseName;
  private String userName;
  private String password;

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding key & value pairs. Used for storing entries from 
   * lmtrc file.
   */

  private static class KeyValuePair {

    public String key;
    public String value;

    public KeyValuePair(
      String key,
      String value)
    {
      this.key = key;
      this.value = value;
    }

  } // KeyValuePair

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Base raw data class.
   */

  public static class RawData {

    public Timestamp timestamp;
    public Long tsId;

  } // RawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding OST raw data.
   */

  public static class OstRawData extends RawData {

    public Integer ostId;
    public String ostName;      // fixme: want to remove this
    public Long readBytes;
    public Long writeBytes;
    public Float pctCpu;
    public Long kbytesFree;
    public Long kbytesUsed;
    public Long inodesFree;
    public Long inodesUsed;

  }; // OstRawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding processed OST data.  Null entries indicate that there
   * was not enough information in the database to calculate the particular
   * derived value or that the value is null in the database.
   */

  public static class OstData {

    private Integer[] ostId;
    private String[] ostName;
    private Timestamp[] timestamp;
    private Long[] tsId;
    private Float[] readRate;
    private Float[] writeRate;
    private Float[] pctCpu;
    private Float[] pctKbytes;
    private Float[] pctInodes;
    private Long[] kbytesFree;
    private Long[] kbytesUsed;
    private Long[] inodesFree;
    private Long[] inodesUsed;

    final private int size;

    public OstData(int size) {
      this.size = size;
      this.ostId      = new Integer[size];
      this.ostName    = new String[size];
      this.timestamp  = new Timestamp[size];
      this.tsId       = new Long[size];
      this.readRate   = new Float[size];
      this.writeRate  = new Float[size];
      this.pctCpu     = new Float[size];
      this.pctKbytes  = new Float[size];
      this.pctInodes  = new Float[size];
      this.kbytesFree = new Long[size];
      this.kbytesUsed = new Long[size];
      this.inodesFree = new Long[size];
      this.inodesUsed = new Long[size];
    } // OstData

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */

    public int getSize() {
      return  this.size;
    } // getSize

    public Float getReadRateAvg(final boolean[] indices) { return Database.average(this.readRate, indices); }
    public Float getReadRateSum(final boolean[] indices) { return Database.sum(this.readRate, indices); }
    public Float getReadRateMin(final boolean[] indices) { return Database.min(this.readRate, indices); }
    public Float getReadRateMax(final boolean[] indices) { return Database.max(this.readRate, indices); }

    public Float getWriteRateAvg(final boolean[] indices) { return Database.average(this.writeRate, indices); }
    public Float getWriteRateSum(final boolean[] indices) { return Database.sum(this.writeRate, indices); }
    public Float getWriteRateMin(final boolean[] indices) { return Database.min(this.writeRate, indices); }
    public Float getWriteRateMax(final boolean[] indices) { return Database.max(this.writeRate, indices); }

    public Float getPctCpuAvg(final boolean[] indices) { return Database.average(this.pctCpu, indices); }
    public Float getPctCpuSum(final boolean[] indices) { return Database.sum(this.pctCpu, indices); }
    public Float getPctCpuMin(final boolean[] indices) { return Database.min(this.pctCpu, indices); }
    public Float getPctCpuMax(final boolean[] indices) { return Database.max(this.pctCpu, indices); }

    public Float getPctKbytes(final boolean[] indices) {
      return Database.percentUsed(
        this.getKbytesUsedSum(indices),
        this.getKbytesFreeSum(indices));
    }

    public Float getPctKbytesAvg(final boolean[] indices) { return Database.average(this.pctKbytes, indices); }
    public Float getPctKbytesMin(final boolean[] indices) { return Database.min(this.pctKbytes, indices); }
    public Float getPctKbytesMax(final boolean[] indices) { return Database.max(this.pctKbytes, indices); }

    public Float getPctInodes(final boolean[] indices) {
      return Database.percentUsed(
        this.getInodesUsedSum(indices),
        this.getInodesFreeSum(indices));
    }

    public Float getPctInodesAvg(final boolean[] indices) { return Database.average(this.pctInodes, indices); }
    public Float getPctInodesMin(final boolean[] indices) { return Database.min(this.pctInodes, indices); }
    public Float getPctInodesMax(final boolean[] indices) { return Database.max(this.pctInodes, indices); }

    public Float getKbytesFreeAvg(final boolean[] indices) { return Database.average(this.kbytesFree, indices); }
    public Long  getKbytesFreeSum(final boolean[] indices) { return Database.sum(this.kbytesFree, indices); }
    public Long  getKbytesFreeMin(final boolean[] indices) { return Database.min(this.kbytesFree, indices); }
    public Long  getKbytesFreeMax(final boolean[] indices) { return Database.max(this.kbytesFree, indices); }

    public Float getKbytesUsedAvg(final boolean[] indices) { return Database.average(this.kbytesUsed, indices); }
    public Long  getKbytesUsedSum(final boolean[] indices) { return Database.sum(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMin(final boolean[] indices) { return Database.min(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMax(final boolean[] indices) { return Database.max(this.kbytesUsed, indices); }

    public Float getInodesFreeAvg(final boolean[] indices) { return Database.average(this.inodesFree, indices); }
    public Long  getInodesFreeSum(final boolean[] indices) { return Database.sum(this.inodesFree, indices); }
    public Long  getInodesFreeMin(final boolean[] indices) { return Database.min(this.inodesFree, indices); }
    public Long  getInodesFreeMax(final boolean[] indices) { return Database.max(this.inodesFree, indices); }

    public Float getInodesUsedAvg(final boolean[] indices) { return Database.average(this.inodesUsed, indices); }
    public Long  getInodesUsedSum(final boolean[] indices) { return Database.sum(this.inodesUsed, indices); }
    public Long  getInodesUsedMin(final boolean[] indices) { return Database.min(this.inodesUsed, indices); }
    public Long  getInodesUsedMax(final boolean[] indices) { return Database.max(this.inodesUsed, indices); }

    public void setOstId(final int index, final Integer ostId)           { this.ostId[index]      = ostId; }
    public void setOstName(final int index, final String ostName)        { this.ostName[index]    = ostName; }
    public void setTimestamp(final int index, final Timestamp timestamp) { this.timestamp[index]  = timestamp; }
    public void setTsId(final int index, final Long  tsId)               { this.tsId[index]       = tsId; }
    public void setReadRate(final int index, final Float readRate)       { this.readRate[index]   = readRate; }
    public void setWriteRate(final int index, final Float writeRate)     { this.writeRate[index]  = writeRate; }
    public void setPctCpu(final int index, final Float pctCpu)           { this.pctCpu[index]     = pctCpu; }
    public void setPctKbytes(final int index, final Float pctKbytes)     { this.pctKbytes[index]  = pctKbytes; }
    public void setPctInodes(final int index, final Float pctInodes)     { this.pctInodes[index]  = pctInodes; }
    public void setKbytesFree(final int index, final Long kbytesFree)    { this.kbytesFree[index] = kbytesFree; }
    public void setKbytesUsed(final int index, final Long kbytesUsed)    { this.kbytesUsed[index] = kbytesUsed; }
    public void setInodesFree(final int index, final Long inodesFree)    { this.inodesFree[index] = inodesFree; }
    public void setInodesUsed(final int index, final Long inodesUsed)    { this.inodesUsed[index] = inodesUsed; }

    public Integer getOstId(final int index)       { return this.ostId[index]; }
    public String getOstName(final int index)      { return this.ostName[index]; }
    public Timestamp getTimestamp(final int index) { return this.timestamp[index]; }
    public Long  getTsId(final int index)          { return this.tsId[index]; }
    public Float getReadRate(final int index)      { return this.readRate[index]; }
    public Float getWriteRate(final int index)     { return this.writeRate[index]; }
    public Float getPctCpu(final int index)        { return this.pctCpu[index]; }
    public Float getPctKbytes(final int index)     { return this.pctKbytes[index]; }
    public Float getPctInodes(final int index)     { return this.pctInodes[index]; }
    public Long getKbytesFree(final int index)     { return this.kbytesFree[index]; }
    public Long getKbytesUsed(final int index)     { return this.kbytesUsed[index]; }
    public Long getInodesFree(final int index)     { return this.inodesFree[index]; }
    public Long getInodesUsed(final int index)     { return this.inodesUsed[index]; }
    
  }; // OstData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding processed OSS data.  Null entries indicate that there
   * was not enough information in the database to calculate the particular
   * derived value or that the value is null in the database.
   */

  public static class OssData {

    private Integer[]   ossId;
    private Integer[]   filesystemId;
    private String[]    failoverhost;
    private String[]    hostname;
    private Timestamp[] timestamp;
    private Long[]      tsId;
    private Float[]     readRate;
    private Float[]     writeRate;
    private Float[]     pctCpu;
    private Float[]     pctKbytes;
    private Float[]     pctInodes;
    private Long[]      kbytesFree;
    private Long[]      kbytesUsed;
    private Long[]      inodesFree;
    private Long[]      inodesUsed;

    final private int size;

    public OssData(int size) {
      this.size = size;
      this.ossId        = new Integer[size];
      this.filesystemId = new Integer[size];
      this.failoverhost = new String[size];
      this.hostname     = new String[size];
      this.timestamp    = new Timestamp[size];
      this.tsId         = new Long[size];
      this.readRate     = new Float[size];
      this.writeRate    = new Float[size];
      this.pctCpu       = new Float[size];
      this.pctKbytes    = new Float[size];
      this.pctInodes    = new Float[size];
      this.kbytesFree   = new Long[size];
      this.kbytesUsed   = new Long[size];
      this.inodesFree   = new Long[size];
      this.inodesUsed   = new Long[size];
    } // OssData

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */

    public int getSize() {
      return  this.size;
    } // getSize

    public Float getReadRateAvg(final boolean[] indices) { return Database.average(this.readRate, indices); }
    public Float getReadRateSum(final boolean[] indices) { return Database.sum(this.readRate, indices); }
    public Float getReadRateMin(final boolean[] indices) { return Database.min(this.readRate, indices); }
    public Float getReadRateMax(final boolean[] indices) { return Database.max(this.readRate, indices); }

    public Float getWriteRateAvg(final boolean[] indices) { return Database.average(this.writeRate, indices); }
    public Float getWriteRateSum(final boolean[] indices) { return Database.sum(this.writeRate, indices); }
    public Float getWriteRateMin(final boolean[] indices) { return Database.min(this.writeRate, indices); }
    public Float getWriteRateMax(final boolean[] indices) { return Database.max(this.writeRate, indices); }

    public Float getPctCpuAvg(final boolean[] indices) { return Database.average(this.pctCpu, indices); }
    public Float getPctCpuSum(final boolean[] indices) { return Database.sum(this.pctCpu, indices); }
    public Float getPctCpuMin(final boolean[] indices) { return Database.min(this.pctCpu, indices); }
    public Float getPctCpuMax(final boolean[] indices) { return Database.max(this.pctCpu, indices); }

    public Float getPctKbytes(final boolean[] indices) {
      return Database.percentUsed(
        this.getKbytesUsedSum(indices),
        this.getKbytesFreeSum(indices));
    }

    public Float getPctKbytesAvg(final boolean[] indices) { return Database.average(this.pctKbytes, indices); }
    public Float getPctKbytesMin(final boolean[] indices) { return Database.min(this.pctKbytes, indices); }
    public Float getPctKbytesMax(final boolean[] indices) { return Database.max(this.pctKbytes, indices); }

    public Float getPctInodes(final boolean[] indices) {
      return Database.percentUsed(
        this.getInodesUsedSum(indices),
        this.getInodesFreeSum(indices));
    }

    public Float getPctInodesAvg(final boolean[] indices) { return Database.average(this.pctInodes, indices); }
    public Float getPctInodesMin(final boolean[] indices) { return Database.min(this.pctInodes, indices); }
    public Float getPctInodesMax(final boolean[] indices) { return Database.max(this.pctInodes, indices); }

    public Float getKbytesFreeAvg(final boolean[] indices) { return Database.average(this.kbytesFree, indices); }
    public Long  getKbytesFreeSum(final boolean[] indices) { return Database.sum(this.kbytesFree, indices); }
    public Long  getKbytesFreeMin(final boolean[] indices) { return Database.min(this.kbytesFree, indices); }
    public Long  getKbytesFreeMax(final boolean[] indices) { return Database.max(this.kbytesFree, indices); }

    public Float getKbytesUsedAvg(final boolean[] indices) { return Database.average(this.kbytesUsed, indices); }
    public Long  getKbytesUsedSum(final boolean[] indices) { return Database.sum(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMin(final boolean[] indices) { return Database.min(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMax(final boolean[] indices) { return Database.max(this.kbytesUsed, indices); }

    public Float getInodesFreeAvg(final boolean[] indices) { return Database.average(this.inodesFree, indices); }
    public Long  getInodesFreeSum(final boolean[] indices) { return Database.sum(this.inodesFree, indices); }
    public Long  getInodesFreeMin(final boolean[] indices) { return Database.min(this.inodesFree, indices); }
    public Long  getInodesFreeMax(final boolean[] indices) { return Database.max(this.inodesFree, indices); }

    public Float getInodesUsedAvg(final boolean[] indices) { return Database.average(this.inodesUsed, indices); }
    public Long  getInodesUsedSum(final boolean[] indices) { return Database.sum(this.inodesUsed, indices); }
    public Long  getInodesUsedMin(final boolean[] indices) { return Database.min(this.inodesUsed, indices); }
    public Long  getInodesUsedMax(final boolean[] indices) { return Database.max(this.inodesUsed, indices); }

    public void setOssId(final int index, final Integer ossId)               { this.ossId[index]        = ossId; }
    public void setFilesystemId(final int index, final Integer filesystemId) { this.filesystemId[index] = filesystemId; }
    public void setFailoverhost(final int index, final String failoverhost)  { this.failoverhost[index] = failoverhost; }
    public void setHostname(final int index, final String hostname)          { this.hostname[index]     = hostname; }
    public void setTimestamp(final int index, final Timestamp timestamp)     { this.timestamp[index]    = timestamp; }
    public void setTsId(final int index, final Long  tsId)                   { this.tsId[index]         = tsId; }
    public void setReadRate(final int index, final Float readRate)           { this.readRate[index]     = readRate; }
    public void setWriteRate(final int index, final Float writeRate)         { this.writeRate[index]    = writeRate; }
    public void setPctCpu(final int index, final Float pctCpu)               { this.pctCpu[index]       = pctCpu; }
    public void setPctKbytes(final int index, final Float pctKbytes)         { this.pctKbytes[index]    = pctKbytes; }
    public void setPctInodes(final int index, final Float pctInodes)         { this.pctInodes[index]    = pctInodes; }
    public void setKbytesFree(final int index, final Long kbytesFree)        { this.kbytesFree[index]   = kbytesFree; }
    public void setKbytesUsed(final int index, final Long kbytesUsed)        { this.kbytesUsed[index]   = kbytesUsed; }
    public void setInodesFree(final int index, final Long inodesFree)        { this.inodesFree[index]   = inodesFree; }
    public void setInodesUsed(final int index, final Long inodesUsed)        { this.inodesUsed[index]   = inodesUsed; }

    public Integer   getOssId(final int index)        { return this.ossId[index]; }
    public Integer   getFilesystemId(final int index) { return this.filesystemId[index]; }
    public String    getFailoverhost(final int index) { return this.failoverhost[index]; }
    public String    getHostname(final int index)     { return this.hostname[index]; }
    public Timestamp getTimestamp(final int index)    { return this.timestamp[index]; }
    public Long      getTsId(final int index)         { return this.tsId[index]; }
    public Float     getReadRate(final int index)     { return this.readRate[index]; }
    public Float     getWriteRate(final int index)    { return this.writeRate[index]; }
    public Float     getPctCpu(final int index)       { return this.pctCpu[index]; }
    public Float     getPctKbytes(final int index)    { return this.pctKbytes[index]; }
    public Float     getPctInodes(final int index)    { return this.pctInodes[index]; }
    public Long      getKbytesFree(final int index)   { return this.kbytesFree[index]; }
    public Long      getKbytesUsed(final int index)   { return this.kbytesUsed[index]; }
    public Long      getInodesFree(final int index)   { return this.inodesFree[index]; }
    public Long      getInodesUsed(final int index)   { return this.inodesUsed[index]; }
    
  }; // OssData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding router raw data.
   */

  private static class RouterRawData extends RawData {

    public int routerId;
    public String routerName;
    public Long bytes;
    public Float pctCpu;

  }; // RouterRawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding router data.  Null entries indicate that there
   * was not enough information in the database to calculate the particular
   * derived value or that the value is null in the database.
   */
  public static class RouterData {

    private int[] routerId;
    private String[] routerName;
    private Timestamp[] timestamp;
    private Long[] tsId;
    private Long[] bytes;
    private Float[] rate;
    private Float[] pctCpu;
    
    final private int size;


    /**
     * Construct object to hold data for a number of routers.
     * @param size number routers this object will hold data for
     */
    public RouterData(int size) {
      this.size = size;
      this.routerId   = new int[size];
      this.routerName = new String[size];
      this.timestamp  = new Timestamp[size];
      this.tsId       = new Long[size];
      this.bytes      = new Long[size];
      this.rate       = new Float[size];
      this.pctCpu     = new Float[size];
    } // RouterData

    /**
     * Returns the number of routers this object holds data for.
     * @return the number of routers.
     */
    public int getSize() {
      return  this.size;
    } // getSize

    public Float getBytesAvg(final boolean[] indices) { return Database.average(this.bytes, indices); }
    public Long getBytesSum(final boolean[] indices) { return Database.sum(this.bytes, indices); }
    public Long getBytesMin(final boolean[] indices) { return Database.min(this.bytes, indices); }
    public Long getBytesMax(final boolean[] indices) { return Database.max(this.bytes, indices); }

    public Float getRateAvg(final boolean[] indices) { return Database.average(this.rate, indices); }
    public Float getRateSum(final boolean[] indices) { return Database.sum(this.rate, indices); }
    public Float getRateMin(final boolean[] indices) { return Database.min(this.rate, indices); }
    public Float getRateMax(final boolean[] indices) { return Database.max(this.rate, indices); }

    public Float getPctCpuAvg(final boolean[] indices) { return Database.average(this.pctCpu, indices); }
    public Float getPctCpuSum(final boolean[] indices) { return Database.sum(this.pctCpu, indices); }
    public Float getPctCpuMin(final boolean[] indices) { return Database.min(this.pctCpu, indices); }
    public Float getPctCpuMax(final boolean[] indices) { return Database.max(this.pctCpu, indices); }

    public void setRouterId(final int index, final int routerId)         { this.routerId[index]   = routerId; }
    public void setRouterName(final int index, final String routerName)  { this.routerName[index] = routerName; }
    public void setTimestamp(final int index, final Timestamp timestamp) { this.timestamp[index]  = timestamp; }
    public void setTsId(final int index, final Long tsId)                { this.tsId[index]       = tsId; }
    public void setBytes(final int index, final Long bytes)              { this.bytes[index]      = bytes; }
    public void setRate(final int index, final Float rate)               { this.rate[index]       = rate; }
    public void setPctCpu(final int index, final Float pctCpu)           { this.pctCpu[index]     = pctCpu; }

    public int getRouterId(final int index)        { return this.routerId[index]; }
    public String getRouterName(final int index)   { return this.routerName[index]; }
    public Timestamp getTimestamp(final int index) { return this.timestamp[index]; }
    public Long getRouterTsId(final int index)     { return this.tsId[index]; }
    public Long getBytes(final int index)          { return this.bytes[index]; }
    public Float getRate(final int index)          { return this.rate[index]; }
    public Float getPctCpu(final int index)        { return this.pctCpu[index]; }

  }; // RouterData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding MDS raw data.
   */

  private static class MdsRawData extends RawData {
    
    public String mdsName;
    public Float pctCpu;
    public Long kbytesFree;
    public Long kbytesUsed;
    public Long inodesFree;
    public Long inodesUsed;

  }; // MdsRawData

  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * Class for mds data.  Null entries indicate that there
   * was not enough information in the database to calculate the particular
   * derived value or that the value is null in the database.
   */

  public static class MdsData {

    private String[] mdsName;
    private Timestamp[] timestamp;
    private Long[] tsId;
    private Float[] pctCpu;
    private Float[] pctKbytes;
    private Float[] pctInodes;
    private Long[] kbytesFree;
    private Long[] kbytesUsed;
    private Long[] inodesFree;
    private Long[] inodesUsed;

    final private int size;

    public MdsData(int size) {
      this.size = size;
      this.mdsName    = new String[size];
      this.timestamp  = new Timestamp[ size];
      this.tsId       = new Long[size];
      this.pctCpu     = new Float[size];
      this.pctKbytes  = new Float[size];
      this.pctInodes  = new Float[size];
      this.kbytesFree = new Long[size];
      this.kbytesUsed = new Long[size];
      this.inodesFree = new Long[size];
      this.inodesUsed = new Long[size];
    } // OstData

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */

    public int getSize() {
      return  this.size;
    } // getSize

    public Float getPctCpuAvg(final boolean[] indices) { return Database.average(this.pctCpu, indices); }
    public Float getPctCpuSum(final boolean[] indices) { return Database.sum(this.pctCpu, indices); }
    public Float getPctCpuMin(final boolean[] indices) { return Database.min(this.pctCpu, indices); }
    public Float getPctCpuMax(final boolean[] indices) { return Database.max(this.pctCpu, indices); }

    public Float getPctKbytesAvg(final boolean[] indices) { return Database.average(this.pctKbytes, indices); }
    public Float getPctKbytesSum(final boolean[] indices) { return Database.sum(this.pctKbytes, indices); }
    public Float getPctKbytesMin(final boolean[] indices) { return Database.min(this.pctKbytes, indices); }
    public Float getPctKbytesMax(final boolean[] indices) { return Database.max(this.pctKbytes, indices); }

    public Float getPctInodesAvg(final boolean[] indices) { return Database.average(this.pctInodes, indices); }
    public Float getPctInodesSum(final boolean[] indices) { return Database.sum(this.pctInodes, indices); }
    public Float getPctInodesMin(final boolean[] indices) { return Database.min(this.pctInodes, indices); }
    public Float getPctInodesMax(final boolean[] indices) { return Database.max(this.pctInodes, indices); }

    public Float getKbytesFreeAvg(final boolean[] indices) { return Database.average(this.kbytesFree, indices); }
    public Long  getKbytesFreeSum(final boolean[] indices) { return Database.sum(this.kbytesFree, indices); }
    public Long  getKbytesFreeMin(final boolean[] indices) { return Database.min(this.kbytesFree, indices); }
    public Long  getKbytesFreeMax(final boolean[] indices) { return Database.max(this.kbytesFree, indices); }

    public Float getKbytesUsedAvg(final boolean[] indices) { return Database.average(this.kbytesUsed, indices); }
    public Long  getKbytesUsedSum(final boolean[] indices) { return Database.sum(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMin(final boolean[] indices) { return Database.min(this.kbytesUsed, indices); }
    public Long  getKbytesUsedMax(final boolean[] indices) { return Database.max(this.kbytesUsed, indices); }

    public Float getInodesFreeAvg(final boolean[] indices) { return Database.average(this.inodesFree, indices); }
    public Long  getInodesFreeSum(final boolean[] indices) { return Database.sum(this.inodesFree, indices); }
    public Long  getInodesFreeMin(final boolean[] indices) { return Database.min(this.inodesFree, indices); }
    public Long  getInodesFreeMax(final boolean[] indices) { return Database.max(this.inodesFree, indices); }

    public Float getInodesUsedAvg(final boolean[] indices) { return Database.average(this.inodesUsed, indices); }
    public Long  getInodesUsedSum(final boolean[] indices) { return Database.sum(this.inodesUsed, indices); }
    public Long  getInodesUsedMin(final boolean[] indices) { return Database.min(this.inodesUsed, indices); }
    public Long  getInodesUsedMax(final boolean[] indices) { return Database.max(this.inodesUsed, indices); }

    public void setMdsName(final int index, final String mdsName)        { this.mdsName[index]    = mdsName; }
    public void setTimestamp(final int index, final Timestamp timestamp) { this.timestamp[index]  = timestamp; }
    public void setTsId(final int index, final Long  tsId)               { this.tsId[index]       = tsId; }
    public void setPctCpu(final int index, final Float pctCpu)           { this.pctCpu[index]     = pctCpu; }
    public void setPctKbytes(final int index, final Float pctKbytes)     { this.pctKbytes[index]  = pctKbytes; }
    public void setPctInodes(final int index, final Float pctInodes)     { this.pctInodes[index]  = pctInodes; }
    public void setKbytesFree(final int index, final Long kbytesFree)    { this.kbytesFree[index] = kbytesFree; }
    public void setKbytesUsed(final int index, final Long kbytesUsed)    { this.kbytesUsed[index] = kbytesUsed; }
    public void setInodesFree(final int index, final Long inodesFree)    { this.inodesFree[index] = inodesFree; }
    public void setInodesUsed(final int index, final Long inodesUsed)    { this.inodesUsed[index] = inodesUsed; }

    public String getMdsName(final int index)      { return this.mdsName[index]; }
    public Timestamp getTimestamp(final int index) { return this.timestamp[index]; }
    public Long  getTsId(final int index)          { return this.tsId[index]; }
    public Float getPctCpu(final int index)        { return this.pctCpu[index]; }
    public Float getPctKbytes(final int index)     { return this.pctKbytes[index]; }
    public Float getPctInodes(final int index)     { return this.pctInodes[index]; }
    public Long getKbytesFree(final int index)     { return this.kbytesFree[index]; }
    public Long getKbytesUsed(final int index)     { return this.kbytesUsed[index]; }
    public Long getInodesFree(final int index)     { return this.inodesFree[index]; }
    public Long getInodesUsed(final int index)     { return this.inodesUsed[index]; }

  }; // MdsData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding MDS ops raw data.
   */

  private static class MdsOpsRawData extends RawData {

    public int opId;
    public String opName;
    public String units;
    public Long samples;
    //public Float samplesPerSec;
    //public Float avgVal;
    public Long sum;
    public Long sumSquares;

  }; // MdsOpsRawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for MDS ops data.  Null entries indicate that there
   * was not enough information in the database to calculate the particular
   * derived value or that the value is null in the database.
   */

  public static class MdsOpsData {

    private int[] opId;
    private String[] opName;
    private String[] units;
    private Timestamp[] timestamp;
    private Long[] tsId;
    private Long[] samples;
    private Float[] samplesPerSec;
    private Float[] avgVal;
    private Long[] sum;
    private Long[] sumSquares;
    private Float[] stdDev;

    final private int size;

    public MdsOpsData(int size) {
      this.size = size;
      opId          = new int[size];
      opName        = new String[size];
      units         = new String[size];
      timestamp     = new Timestamp[size];
      tsId          = new Long[size];
      samples       = new Long[size];
      samplesPerSec = new Float[size];
      avgVal        = new Float[size];
      sum           = new Long[size];
      sumSquares    = new Long[size];
      stdDev        = new Float[size];
    } // MdsOpsData

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */

    public int getSize() {
      return  this.size;
    } // getSize


    public void setOpId(final int index, final int opId)                     { this.opId[index]          = opId; }
    public void setOpName(final int index, final String opName)              { this.opName[index]        = opName; }
    public void setUnits(final int index, final String units)                { this.units[index]         = units; }
    public void setTimestamp(final int index, final Timestamp timestamp)     { this.timestamp[index]     = timestamp; }
    public void setTsId(final int index, final Long tsId)                    { this.tsId[index]          = tsId; }
    public void setSamples(final int index, final Long samples)              { this.samples[index]       = samples; }
    public void setSamplesPerSec(final int index, final Float samplesPerSec) { this.samplesPerSec[index] = samplesPerSec; }
    public void setAvgVal(final int index, final Float avgVal)               { this.avgVal[index]        = avgVal; }
    public void setSum(final int index,final  Long sum)                      { this.sum[index]           = sum; }
    public void setSumSquares(final int index, final Long sumSquares)        { this.sumSquares[index]    = sumSquares; }
    public void setStdDev(final int index, final Float stdDev)               { this.stdDev[index]        = stdDev; }

    public Timestamp getTimestamp(final int index) { return this.timestamp[index]; }
    public Long  getTsId(final int index)          { return this.tsId[index]; }
    public int getOpId(final int index)            { return this.opId[index]; }
    public String getOpName(final int index)       { return this.opName[index]; }
    public String getUnits(final int index)        { return this.units[index]; }
    public Long getSamples(final int index)        { return this.samples[index]; }
    public Float getSamplesPerSec(final int index) { return this.samplesPerSec[index]; }
    public Float getAvgVal(final int index)        { return this.avgVal[index]; }
    public Long getSum(final int index)            { return this.sum[index]; }
    public Long getSumSquares(final int index)     { return this.sumSquares[index]; }
    public Float getStdDev(final int index)        { return this.stdDev[index]; }


  }; // MdsOpsData

  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * Class for holding MDS info from MDS_INFO table.
   */

  public static class MdsInfo {

    public int mdsId;;
    public String mdsName;
    public String hostName;
    public String deviceName;

  }; // MdsInfoData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Base class for aggregate data.
   */

  public static class AggregateData {
    
    public Timestamp timestamp;
    public float aggregate;
    public float minval;
    public float maxval;
    public float average;
    public long numSamples;

    // Flag for variables where aggregate field makes sense.  Can depend on
    // whether we are aggregating over time or over a collection.  For example
    // READ_BYTES and WRITE_BYTES are the only variables that make sense to 
    // aggregate over time.
    public boolean hasAggregate = false;

  }; // AggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding OST aggregate data.
   */

//   public static class OstAggregateData extends AggregateData {

//   }; // OstAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding ROUTER aggregate data.
   */

//   public static class RouterAggregateData extends AggregateData {

//   }; // RouterAggregateData


  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding filesystem aggregate data.
   */

//   public static class FilesystemAggregateData {
    
//     public Timestamp timestamp;
//     public float ostAggregate;
//     public float ostMinval;
//     public float ostMaxval;
//     public float ostAverage;

//     // Flag for variables where aggregate field makes sense (
//     // READ_BYTES and WRITE_BYTES are the only ones).
//     public boolean hasAggregate = false;

//   }; // FilesystemAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding generic time sequence data.
   */

  private static class TimeSequenceData {
    
    public Timestamp timestamp;
    public float value;

  }; // TimeSequenceData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Caluclate sum of values in array.
   *
   * @param values array of values to be summed.
   * @param indices array of flags that indicate which elements should be
   *        included in sum.
   *
   * @return the sum of the selected elements.
   */

  private static Float sum(
    final Float[] values,
    final boolean[] indices)
  {

    float sumf = 0.0f;

    for (int i = 0; i < values.length; i++) {
      if (!indices[i]) continue;
      if (values[i] == null) continue;
      sumf += values[i].floatValue();
    } //  for i

    return new Float(sumf);
    
  } // sum

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Caluclate average of values in array.
   *
   * @param values array of values to be averaged.
   * @param indices array of flags that indicate which elements should be
   *        included in average.
   *
   * @return the average of the selected elements.
   */

  private static Float average(
    final Float[] values,
    final boolean[] indices)
  {

    float avgf = 0.0f;

    int count= 0;
    for (int i = 0; i < values.length; i++) {
      if (!indices[i]) continue;
      count++;
      if (values[i] == null) continue;
      avgf += values[i].floatValue();
    } //  for i

    if (count > 0) {
      avgf /= (float) count;
    }

    return new Float(avgf);

  } // average

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find minimum value in array.
   *
   * @param values array of values to be searched.
   * @param indices array of flags that indicate which elements should be
   *        included in search for minimum.
   *
   * @return the minimum value of the selected elements.
   */

  private static Float min(
    final Float[] values,
    final boolean[] indices)
  {

    float minf = 0.0f;
    boolean first = true;

    for (int i = 0; i < values.length; i++) {

      if (!indices[i]) continue;
      if (values[i] == null) continue;

      final float value = values[i].floatValue();

      if (first) {
        minf = value;
        first = false;
      }
      else {
        minf = Math.min(minf, value);
      }

    } //  for i

    if (first) {
      // Nothing was evaluated.
      return null;
    }

    return new Float(minf);

  } // min

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find minimum value in array.
   *
   * @param values array of values to be searched.
   * @param indices array of flags that indicate which elements should be
   *        included in search for minimum.
   *
   * @return the minimum value of the selected elements.
   */

  private static Long min(
    final Long[] values,
    final boolean[] indices)
  {

    long minl = 0;
    boolean first = true;

    for (int i = 0; i < values.length; i++) {

      if (!indices[i]) continue;
      if (values[i] == null) continue;

      final long value = values[i].longValue();

      if (first) {
        minl = value;
        first = false;
      }
      else {
        minl = Math.min(minl, value);
      }

    } //  for i

    if (first) {
      // Nothing was evaluated.
      return null;
    }

    return new Long(minl);

  } // min

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find maximum value in array.
   *
   * @param values array of values to be searched.
   * @param indices array of flags that indicate which elements should be
   *        included in search for maximum.
   *
   * @return the maximum value of the selected elements.
   */

  private static Float max(
    final Float[] values,
    final boolean[] indices)
  {

    float maxf = 0.0f;
    boolean first = true;

    for (int i = 0; i < values.length; i++) {

      if (!indices[i]) continue;
      if (values[i] == null) continue;

      final float value = values[i].floatValue();

      if (first) {
        maxf = value;
        first = false;
      }
      else {
        maxf = Math.max(maxf, value);
      }

    } //  for i

    if (first) {
      // Nothing was evaluated.
      return null;
    }

    return new Float(maxf);

  } // max

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find maximum value in array.
   *
   * @param values array of values to be searched.
   * @param indices array of flags that indicate which elements should be
   *        included in search for maximum.
   *
   * @return the maximum value of the selected elements.
   */

  private static Long max(
    final Long[] values,
    final boolean[] indices)
  {

    long maxl = 0;
    boolean first = true;

    for (int i = 0; i < values.length; i++) {

      if (!indices[i]) continue;
      if (values[i] == null) continue;

      final long value = values[i].longValue();

      if (first) {
        maxl = value;
        first = false;
      }
      else {
        maxl = Math.max(maxl, value);
      }

    } //  for i

    if (first) {
      // Nothing was evaluated.
      return null;
    }

    return new Long(maxl);

  } // max

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Caluclate sum of values in array.
   *
   * @param values array of values to be summed.
   * @param indices array of flags that indicate which elements should be
   *        included in sum.
   *
   * @return the sum of the selected elements.
   */

  private static Long sum(
    final Long[] values,
    final boolean[] indices)
  {

    long suml = 0;

    for (int i = 0; i < values.length; i++) {
      if (!indices[i]) continue;
      if (values[i] == null) continue;
      suml += values[i].longValue();
    } //  for i

    return new Long(suml);

  } // sum

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Caluclate average of values in array.
   *
   * @param values array of values to be averaged.
   * @param indices array of flags that indicate which elements should be
   *        included in average.
   *
   * @return the average of the selected elements.
   */

  private static Float average(
    final Long[] values,
    final boolean[] indices)
  {

    float avgf = 0.0f;

    int count = 0;
    for (int i = 0; i < indices.length; i++) {
      if (!indices[i]) continue;
      count++;
      if (values[i] == null) continue;
      avgf += (float) values[i].longValue();
    } //  for i

    if (count > 0) {
      avgf /= (float) count;
    }

    return new Float(avgf);

  } // average

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding time information.
   */

  public static class TimeInfo {

    public int tsId;
    public Timestamp timestamp;

  };  // TimeInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding info from *_VARIABLE_INFO tables.
   */
  
  public static class VariableInfo {

    public int    variableId;
    public String variableName;
    public String variableLabel;
    public int    threshType;
    public float  threshVal1;
    public float  threshVal2;

  }; // VariableInfo 

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding info from the FILESYSTEM_INFO table.
   */
  
  public static class FilesystemInfo {

    public int    filesystemId;
    public String filesystemName;
    public String lmtDbHost;
    public int    lmtDbPort = -1;
    public String lmtDbName;
    public String lmtDbUsername;
    public String lmtDbAuth;
    public String filesystemMountName;


    public String toString() {
      return 
        lmtDbUsername +
        "@" +
        lmtDbHost +
        (lmtDbPort < 0 ? "" : ":" + lmtDbPort) +
        "/" +
        lmtDbName;
    }

  }; //  FilesystemInfo 
  
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding a record from the OST_INFO table.
   */
  
  public static class OstInfo {

    public int ostId;
    public String failoverhost;
    public String deviceName;
    public String hostname;
    public boolean offline;
    public int ossId;
    public String ostName;

  }; // OstInfo 

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding all info from the OST_INFO table.
   */
  
  public static class OstInfoHolder {
    
    final private OstInfo[] ostInfo;
    private Hashtable ostNameHashtable = null;

    public OstInfoHolder(OstInfo[] ostInfo)
    {

      this.ostInfo = ostInfo;
      this.ostNameHashtable = new Hashtable(ostInfo.length);

      for (int i = 0; i < ostInfo.length; i++) {
        this.ostNameHashtable.put(new Integer(i), this.ostInfo[i]);
      } //  for i

    } // OstInfoHolder

    public String getOstName(final int ostId)
    {
      
      return (String) this.ostNameHashtable.get(new Integer(ostId));

    } // getOstName


    public OstInfo[] getOstInfo()
    {
 
     return this.ostInfo;

    } // getOstInfo

  }; // OstInfoHolder

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding info from the OSS_INFO table.
   */
  
  public static class OssInfo {

    public int ossId;
    public int filesystemId;
    public String failoverhost;
    public String hostname;

  }; // OssInfo 

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding all info from the OSS_INFO table.
   */
  
  public static class OssInfoHolder {
    
    final private OssInfo[] ossInfo;
    private Hashtable ossHostnameHashtable = null;

    public OssInfoHolder(OssInfo[] ossInfo)
    {

      this.ossInfo = ossInfo;
      this.ossHostnameHashtable = new Hashtable(ossInfo.length);

      for (int i = 0; i < ossInfo.length; i++) {
        this.ossHostnameHashtable.put(new Integer(i), this.ossInfo[i]);
      } //  for i

    } // OstInfoHolder

    public String getOssName(final int ossId)
    {
      
      return (String) this.ossHostnameHashtable.get(new Integer(ossId));

    } // getOssName

    public OssInfo[] getOssInfo()
    {
 
     return this.ossInfo;

    } // getOssInfo

  }; // OssInfoHolder

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Class for holding summary data for an entire Filesystem.
   */

  public static class FilesystemData {

    // Flag set to true if we could not get data for this filesystem.
    public boolean failure = false; // fixme:  get rid of this

    public int filesystemId;
    public String filesystemName;
    public String filesystemMountName;

    public Float readRate;
    public Float readRateAvg;
    public Float readRateMin;
    public Float readRateMax;

    public Float writeRate;
    public Float writeRateAvg;
    public Float writeRateMin;
    public Float writeRateMax;

    public Float pctCpuAvg;
    public Float pctCpuMin;
    public Float pctCpuMax;

    public Float pctKbytes;
    public Float pctKbytesAvg;
    public Float pctKbytesMin;
    public Float pctKbytesMax;

    public Float pctInodes;
    public Float pctInodesAvg;
    public Float pctInodesMin;
    public Float pctInodesMax;

    public Long  kbytesFree;
    public Float kbytesFreeAvg;
    public Long  kbytesFreeMin;
    public Long  kbytesFreeMax;

    public Long  kbytesUsed;
    public Float kbytesUsedAvg;
    public Long  kbytesUsedMin;
    public Long  kbytesUsedMax;

    public Long  inodesFree;
    public Float inodesFreeAvg;
    public Long  inodesFreeMin;
    public Long  inodesFreeMax;

    public Long  inodesUsed;
    public Float inodesUsedAvg;
    public Long  inodesUsedMin;
    public Long  inodesUsedMax;

    private String hostName;
    private String port;
    private String databaseName;
    private String userName;
    private String password;

  }; // FilesystemData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Construct connection to database.
   *
   * @param filesystemInfo object containing information needed to connect to
   *        the desired relational database (note: the filesystemId field
   *        will be filled in by the Database.connect method and should be left
   *        empty). 
   *          
   */

  public Database(FilesystemInfo filesystemInfo)
  {

    this.filesystemInfo = filesystemInfo;
    
    this.hostName     = filesystemInfo.lmtDbHost;
    this.port         = filesystemInfo.lmtDbPort + "";
    this.databaseName = filesystemInfo.lmtDbName;
    this.userName     = filesystemInfo.lmtDbUsername;
    this.password     = filesystemInfo.lmtDbAuth;

  } // Database

  //////////////////////////////////////////////////////////////////////////////

  /**
   * @deprecated
   * Construct connection to database.
   *
   * @param hostName name of host on which database server resides.  Can be
   *        "localhost" if database server is running on local machine.
   * @param port port number that database server is listening on.  Default
   *        port for MySQL is 3306.
   * @param databaseName name of database within server to connect to.  For 
   *        example "filesystem_ti3".
   * @param userName user name to use for database connection.
   * @param password password for given userName.
   */

  public Database(
    final String hostName,
    final String port,
    final String databaseName,
    final String userName,
    final String password)
  {

    this.hostName = hostName;
    this.port = port;
    this.databaseName = databaseName;
    this.userName = userName;
    this.password = password;
    
  } // Database

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Establish connection to relational database.
   */

  public void connect()
  //  throws Exception
  {

    if (this.isConnected) {
      // No-op.
      return;
    }

    final String url =
      "jdbc:mysql://" +
      this.hostName +
      ":" +
      this.port + 
      "/" +
      this.databaseName;

    if (debug) {
      System.err.println(Debug.tag() + "url = " + url);
      System.err.println(Debug.tag() + "userName = " + userName);
      System.err.println(Debug.tag() + "password = " + password);
    }
    

    try {

      Class.forName("com.mysql.jdbc.Driver");

      this.connection =  DriverManager.getConnection(
        url + "?connectTimeout=3000",  // 3 sec timeout for connection attempt
        userName,
        password);
      
      this.createPreparedStatements();
      
      this.ostDataStatement = this.connection.createStatement();
      

      //
      // Get filesystemId from FILESYSTEM_INFO table.
      //

      final Statement statement = this.connection.createStatement();
      final ResultSet resultSet = statement.executeQuery(
        "select FILESYSTEM_ID from FILESYSTEM_INFO " +
        "where FILESYSTEM_NAME = '" + this.filesystemInfo.filesystemName + "'");
      
      try {
        if (!resultSet.next()) {
          throw new Exception("Problem getting info for " +
            this.filesystemInfo.filesystemName +
            " from FILESYSTEM_INFO table -- empty result set.");
        }
        
        this.filesystemInfo.filesystemId = resultSet.getInt(1);

        if (debug) System.err.println(
          Debug.tag() + "filesystemId = " + this.filesystemInfo.filesystemId);

      }      
      finally {
        resultSet.close();
        statement.close();
      }
      
    }
    catch (Exception e) {

      // Record exception so we can use it later if we want.
      this.connectException = e;
      return;

    }

    this.isConnected = true;

  } // connect

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Tells whether or not this instance is connected.
   * @return true if this instance is connected to relational database.
   */

  public boolean isConnected()
  {

    return this.isConnected;

  } // isConnected

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get FilesystemInfo for this Database.
   * @return FilesystemInfo object for this Database.
   */

  public FilesystemInfo getFilesystemInfo()
  {

    return this.filesystemInfo;

  } // getFileSystemInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get Exception that occured when attempting to connect, if any.
   */

  public Exception getConnectException()
  {

    return this.connectException;

  } // getConnectException

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Instantiate the various prepared statements we will be using.
   */

  private void createPreparedStatements()
  throws
  Exception
  {
    this.ossDataQuery = this.connection.prepareStatement(
      "select MAX(TIMESTAMP),HOSTNAME,x1.OSS_ID,TS_ID,PCT_CPU,PCT_MEMORY " +
      "from (select * from OSS_INFO) as x1 left join (select OSS_ID," +
      "OSS_DATA.TS_ID,PCT_CPU,PCT_MEMORY,TIMESTAMP from OSS_DATA," +
      "TIMESTAMP_INFO where OSS_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "TIMESTAMP >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) as x2 " +
      "on x1.OSS_ID=x2.OSS_ID group by HOSTNAME order by OSS_ID");
	
    this.ostNameQuery = this.connection.prepareStatement(
      "select OST_ID from OST_INFO where OST_NAME = ?");

    this.ostDataQuery = this.connection.prepareStatement(
      "select TIMESTAMP,OST_NAME,x1.OST_ID,TS_ID,READ_BYTES,WRITE_BYTES," +
      "PCT_CPU,KBYTES_FREE,KBYTES_USED,INODES_FREE,INODES_USED from " +
      "(select * from OST_INFO where OFFLINE=false) as x1 left join " +
      "(select OST_ID,OST_DATA.TS_ID,READ_BYTES,WRITE_BYTES,PCT_CPU," +
      "KBYTES_FREE,KBYTES_USED,INODES_FREE,INODES_USED,TIMESTAMP from " +
      "OST_DATA,TIMESTAMP_INFO where " +
      "OST_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "TIMESTAMP >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) as x2 on " +
      "x1.OST_ID=x2.OST_ID");

    this.routerDataQuery = this.connection.prepareStatement(
      "select x1.ROUTER_ID,TIMESTAMP,ROUTER_NAME,x2.TS_ID,BYTES, " +
      "PCT_CPU from (select * from ROUTER_INFO where ROUTER_GROUP_ID=?) " +
      "as x1 left join (select ROUTER_ID,ROUTER_DATA.TS_ID,BYTES," +
      "PCT_CPU,TIMESTAMP " +
      "from ROUTER_DATA,TIMESTAMP_INFO " +
      "where ROUTER_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "TIMESTAMP >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) as x2 " +
      "on x1.ROUTER_ID=x2.ROUTER_ID");

    this.routerGroupsQuery = this.connection.prepareStatement(
      "select distinct ROUTER_GROUP_ID from ROUTER_INFO " +
      "order by ROUTER_GROUP_ID");

    this.mdsDataQuery = this.connection.prepareStatement(
      "select MDS_NAME,TIMESTAMP,TS_ID,PCT_CPU," +
      "KBYTES_FREE,KBYTES_USED,INODES_FREE,INODES_USED " +
      "from MDS_INFO " +
      "left join (select MDS_ID,MDS_DATA.TS_ID,PCT_CPU,KBYTES_FREE," +
      "KBYTES_USED,INODES_FREE,INODES_USED,TIMESTAMP " +
      "from MDS_DATA,TIMESTAMP_INFO " +
      "where MDS_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "TIMESTAMP >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) as x1 " +
      "on MDS_INFO.MDS_ID=x1.MDS_ID where MDS_INFO.MDS_ID=?");

    this.mdsInfoQuery = this.connection.prepareStatement(
      "select MDS_ID,MDS_NAME,HOSTNAME,DEVICE_NAME from MDS_INFO");

    this.mdsOpsDataQuery = this.connection.prepareStatement(
      "select MDS_OPS_DATA.TS_ID,TIMESTAMP,MDS_OPS_DATA.OPERATION_ID," +
      "OPERATION_NAME,UNITS,SAMPLES,SUM,SUMSQUARES " +
      "from  MDS_OPS_DATA,OPERATION_INFO, TIMESTAMP_INFO where " +
      "MDS_ID=? and " +
      "MDS_OPS_DATA.OPERATION_ID=OPERATION_INFO.OPERATION_ID and " +
      "MDS_OPS_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "TIMESTAMP>=DATE_SUB(NOW(),INTERVAL 30 SECOND)");


    //
    // OST aggregate
    //

    this.ostAggregateHourDataQuery = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString("HOUR"));

    this.ostAggregateDayDataQuery = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString("DAY"));

    this.ostAggregateWeekDataQuery = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString("WEEK"));

    this.ostAggregateMonthDataQuery = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString("MONTH"));

    this.ostAggregateYearDataQuery = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString("YEAR"));

    this.ostAggregateHourDataQuery2 = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString2("HOUR"));

    this.ostAggregateDayDataQuery2 = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString2("DAY"));

    this.ostAggregateWeekDataQuery2 = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString2("WEEK"));

    this.ostAggregateMonthDataQuery2 = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString2("MONTH"));

    this.ostAggregateYearDataQuery2 = this.connection.prepareStatement(
      Database.generateOstAggregateQueryString2("YEAR"));


    //
    // Filesystem aggregate
    //

    this.filesystemAggregateHourDataQuery = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString("HOUR"));

    this.filesystemAggregateDayDataQuery = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString("DAY"));

    this.filesystemAggregateWeekDataQuery = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString("WEEK"));

    this.filesystemAggregateMonthDataQuery = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString("MONTH"));

    this.filesystemAggregateYearDataQuery = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString("YEAR"));

    this.filesystemAggregateHourDataQuery2 = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString2("HOUR"));

    this.filesystemAggregateDayDataQuery2 = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString2("DAY"));

    this.filesystemAggregateWeekDataQuery2 = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString2("WEEK"));

    this.filesystemAggregateMonthDataQuery2 = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString2("MONTH"));

    this.filesystemAggregateYearDataQuery2 = this.connection.prepareStatement(
      Database.generateFilesystemAggregateQueryString2("YEAR"));


    //
    // ROUTER aggregate
    //

    this.routerAggregateHourDataQuery = this.connection.prepareStatement(
      Database.generateRouterAggregateQueryString("HOUR"));

    this.routerAggregateDayDataQuery = this.connection.prepareStatement(
      Database.generateRouterAggregateQueryString("DAY"));

    this.routerAggregateWeekDataQuery = this.connection.prepareStatement(
      Database.generateRouterAggregateQueryString("WEEK"));

    this.routerAggregateMonthDataQuery = this.connection.prepareStatement(
      Database.generateRouterAggregateQueryString("MONTH"));

    this.routerAggregateYearDataQuery = this.connection.prepareStatement(
      Database.generateRouterAggregateQueryString("YEAR"));


    //
    // ROUTER group aggregate
    //

    //     this.routerGroupAggregateHourDataQuery = this.connection.prepareStatement(
    //       Database.generateRouterGroupAggregateQueryString("HOUR"));
    
    //     this.routerGroupAggregateDayDataQuery = this.connection.prepareStatement(
    //       Database.generateRouterGroupAggregateQueryString("DAY"));
    
    //     this.routerGroupAggregateWeekDataQuery = this.connection.prepareStatement(
    //       Database.generateRouterGroupAggregateQueryString("WEEK"));
    
    //     this.routerGroupAggregateMonthDataQuery = this.connection.prepareStatement(
    //       Database.generateRouterGroupAggregateQueryString("MONTH"));
    
    //     this.routerGroupAggregateYearDataQuery = this.connection.prepareStatement(
    //       Database.generateRouterGroupAggregateQueryString("YEAR"));


    //
    // MDS aggregate
    //

    this.mdsAggregateHourDataQuery = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString("HOUR"));

    this.mdsAggregateDayDataQuery = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString("DAY"));

    this.mdsAggregateWeekDataQuery = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString("WEEK"));

    this.mdsAggregateMonthDataQuery = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString("MONTH"));

    this.mdsAggregateYearDataQuery = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString("YEAR"));

    this.mdsAggregateHourDataQuery2 = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString2("HOUR"));

    this.mdsAggregateDayDataQuery2 = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString2("DAY"));

    this.mdsAggregateWeekDataQuery2 = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString2("WEEK"));

    this.mdsAggregateMonthDataQuery2 = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString2("MONTH"));

    this.mdsAggregateYearDataQuery2 = this.connection.prepareStatement(
      Database.generateMdsAggregateQueryString2("YEAR"));


    this.ostVariableInfoQuery = this.connection.prepareStatement(
      "select * from OST_VARIABLE_INFO order by VARIABLE_ID");

    this.ostRawDataQuery = this.connection.prepareStatement(
      "select " +
      "TIMESTAMP," +
      "OST_DATA.TS_ID," +
      "OST_ID," + 
      "READ_BYTES," +
      "WRITE_BYTES," +
      "PCT_CPU," +
      "KBYTES_FREE," +
      "KBYTES_USED," +
      "INODES_FREE," +
      "INODES_USED " +
      "from OST_DATA, TIMESTAMP_INFO " +
      "where OST_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "OST_ID=? and " +
      "TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");

    this.routerIdsForGroupQuery = connection.prepareStatement(
      "select ROUTER_ID from ROUTER_INFO where ROUTER_GROUP_ID=? " +
      "order by ROUTER_ID");

  } // createPreparedStatements

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating query strings for OST aggregate tables.
   */

  private static String generateOstAggregateQueryString(final String level)
  {
    
    return 
      "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES from " +
      "OST_AGGREGATE_" + level + ",TIMESTAMP_INFO,OST_VARIABLE_INFO where " +
      "OST_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "OST_AGGREGATE_" + level +".VARIABLE_ID=OST_VARIABLE_INFO.VARIABLE_ID " +
      "and OST_ID=? and VARIABLE_NAME=? and TIMESTAMP>=? and TIMESTAMP<=? " +
      "order by TIMESTAMP";

  } // generateOstAggregateQueryString

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating queries on OST aggregate tables that return two
   * variables at a time.
   */

  private static String generateOstAggregateQueryString2(final String level)
  {
    
    return
      "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES," +
      "VARIABLE_NAME from " +
      "OST_AGGREGATE_" + level + ",TIMESTAMP_INFO,OST_VARIABLE_INFO where " +
      "OST_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "OST_AGGREGATE_" + level +".VARIABLE_ID=OST_VARIABLE_INFO.VARIABLE_ID " +
      "and OST_ID=? and (VARIABLE_NAME=? or VARIABLE_NAME=?) and " +
      "TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP";

  } // generateOstAggregateQueryString2

  //////////////////////////////////////////////////////////////////////////////


  /**
   * Helper method for creating queries on filesystem aggregate tables.
   */

  private static String generateFilesystemAggregateQueryString(
    final String level)
  {
    
    return 
      "select TIMESTAMP,OST_AGGREGATE,OST_MINVAL,OST_MAXVAL,OST_AVERAGE from " +
      "FILESYSTEM_AGGREGATE_" + level + ",TIMESTAMP_INFO,OST_VARIABLE_INFO where " +
      "FILESYSTEM_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "FILESYSTEM_AGGREGATE_" + level +".VARIABLE_ID=OST_VARIABLE_INFO.VARIABLE_ID " +
      "and FILESYSTEM_ID=? and VARIABLE_NAME=? and TIMESTAMP>=? and TIMESTAMP<=? " +
      "order by TIMESTAMP";

  } // generateFilesystemAggregateQueryString

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating queries on filesystem aggregate tables that
   * return two variables at a time.
   */

  private static String generateFilesystemAggregateQueryString2(
    final String level)
  {
    
    return
      "select TIMESTAMP,OST_AGGREGATE,OST_MINVAL,OST_MAXVAL,OST_AVERAGE," +
      "VARIABLE_NAME from " +
      "FILESYSTEM_AGGREGATE_" + level + ",TIMESTAMP_INFO,OST_VARIABLE_INFO where " +
      "FILESYSTEM_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "FILESYSTEM_AGGREGATE_" + level +".VARIABLE_ID=OST_VARIABLE_INFO.VARIABLE_ID " +
      "and FILESYSTEM_ID=? and (VARIABLE_NAME=? or VARIABLE_NAME=?) and " +
      "TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP";

  } // generateFilesystemAggregateQueryString2


  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating query strings for ROUTER aggregate tables.
   */

  private static String generateRouterAggregateQueryString(final String level)
  {
    
    return 
      "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES from " +
      "ROUTER_AGGREGATE_" + level + ",TIMESTAMP_INFO,ROUTER_VARIABLE_INFO where " +
      "ROUTER_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "ROUTER_AGGREGATE_" + level +".VARIABLE_ID=ROUTER_VARIABLE_INFO.VARIABLE_ID " +
      "and ROUTER_ID=? and VARIABLE_NAME=? and TIMESTAMP>=? and TIMESTAMP<=? " +
      "order by TIMESTAMP";

  } // generateRouterAggregateQueryString

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating query strings for ROUTER aggregate tables.
   */

  //   private static String generateRouterGroupAggregateQueryString(
  //     final String level)
  //   {
  
  //     return 
  //       "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES from " +
  //       "ROUTER_AGGREGATE_" + level + ",TIMESTAMP_INFO,ROUTER_VARIABLE_INFO,ROUTER_INFO where " +
  //       "ROUTER_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
  //       "ROUTER_AGGREGATE_" + level +".VARIABLE_ID=ROUTER_VARIABLE_INFO.VARIABLE_ID and " +
  //       "ROUTER_AGGREGATE_" + level +".ROUTER_ID=ROUTER_INFO.ROUTER_ID " +
  //       "and ROUTER_GROUP_ID=? and VARIABLE_NAME=? and TIMESTAMP>=? and TIMESTAMP<=? " +
  //       "order by TIMESTAMP";
  
  //   } // generateRouterGroupAggregateQueryString
  
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating query strings for MDS aggregate tables.
   */

  private static String generateMdsAggregateQueryString(final String level)
  {
    
    return 
      "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES from " +
      "MDS_AGGREGATE_" + level + ",TIMESTAMP_INFO,MDS_VARIABLE_INFO where " +
      "MDS_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "MDS_AGGREGATE_" + level +".VARIABLE_ID=MDS_VARIABLE_INFO.VARIABLE_ID " +
      "and MDS_ID=? and VARIABLE_NAME=? and TIMESTAMP>=? and TIMESTAMP<=? " +
      "order by TIMESTAMP";

  } // generateMdsAggregateQueryString

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Helper method for creating queries on MDS aggregate tables that return two
   * variables at a time.
   */

  private static String generateMdsAggregateQueryString2(final String level)
  {
    
    return
      "select TIMESTAMP,AGGREGATE,MINVAL,MAXVAL,AVERAGE,NUM_SAMPLES," +
      "VARIABLE_NAME from " +
      "MDS_AGGREGATE_" + level + ",TIMESTAMP_INFO,MDS_VARIABLE_INFO where " +
      "MDS_AGGREGATE_" + level +".TS_ID=TIMESTAMP_INFO.TS_ID and " +
      "MDS_AGGREGATE_" + level +".VARIABLE_ID=MDS_VARIABLE_INFO.VARIABLE_ID " +
      "and MDS_ID=? and (VARIABLE_NAME=? or VARIABLE_NAME=?) and " +
      "TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP";

  } // generateMdsAggregateQueryString2

  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * Get latest available time information contained in the given table.  Table
   * must contain a TS_ID column.
   *
   * @param tableName name of table to examine.
   * @return latest TimeInfo from given table.
   */

  public TimeInfo getLatestTimeInfo(final String tableName)
  throws
    Exception
  {

    final TimeInfo timeInfo = new TimeInfo();
    final Statement statement = this.connection.createStatement();
    final ResultSet resultSet = statement.executeQuery(
      "select TS_ID, TIMESTAMP from TIMESTAMP_INFO " +
      "where TS_ID = (select max(TS_ID) from " + tableName + ")");
    
    try {
      if (!resultSet.next()) {
        throw new Exception("Problem getting latest TimeInfo:  empty result set.");
      }
      
      timeInfo.tsId = resultSet.getInt(1);
      timeInfo.timestamp = resultSet.getTimestamp(2);
    }      
    finally {
      resultSet.close();
      statement.close();
    }

    return timeInfo;
 
  } // getLatestTimeInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current data from the OST_DATA table.
   *
   * @return OstData object containing requested data.
   */

  public OstData getCurrentOstData()
  throws Exception
  {

    final TreeMap treeMap = new TreeMap();
    final TimeIt timeIt = new TimeIt();
    final ResultSet resultSet = this.ostDataQuery.executeQuery();
    
    if (debug) System.err.println(Debug.tag() + timeIt.getTime() + " secs");

    try {
    
      while (resultSet.next()) {
    
        final OstRawData ostRawData = new OstRawData();

        int index = 1;
        ostRawData.timestamp  = resultSet.getTimestamp(index++);
        ostRawData.ostName    = resultSet.getString(index++);
        ostRawData.ostId      = (Integer) resultSet.getObject(index++);
        ostRawData.tsId       = (Long)    resultSet.getObject(index++);
        ostRawData.readBytes  = (Long)    resultSet.getObject(index++);
        ostRawData.writeBytes = (Long)    resultSet.getObject(index++);
        ostRawData.pctCpu     = (Float)   resultSet.getObject(index++);
        ostRawData.kbytesFree = (Long)    resultSet.getObject(index++);
        ostRawData.kbytesUsed = (Long)    resultSet.getObject(index++);
        ostRawData.inodesFree = (Long)    resultSet.getObject(index++);
        ostRawData.inodesUsed = (Long)    resultSet.getObject(index++);

        if (debug) {
          System.err.println(Debug.tag() + "ostRawData.timestamp = "  + ostRawData.timestamp);
          System.err.println(Debug.tag() + "ostRawData.ostName = "    + ostRawData.ostName);
          System.err.println(Debug.tag() + "ostRawData.ostId = "      + ostRawData.ostId);
          System.err.println(Debug.tag() + "ostRawData.tsId = "       + ostRawData.tsId);
          System.err.println(Debug.tag() + "ostRawData.readBytes = "  + ostRawData.readBytes);
          System.err.println(Debug.tag() + "ostRawData.writeBytes = " + ostRawData.writeBytes );
          System.err.println(Debug.tag() + "ostRawData.pctCpu = "     + ostRawData.pctCpu);
          System.err.println(Debug.tag() + "ostRawData.kbytesFree = " + ostRawData.kbytesFree);
          System.err.println(Debug.tag() + "ostRawData.kbytesUsed = " + ostRawData.kbytesUsed );
          System.err.println(Debug.tag() + "ostRawData.inodesFree = " + ostRawData.inodesFree);
          System.err.println(Debug.tag() + "ostRawData.inodesUsed = " + ostRawData.inodesUsed );
        }

        OstRawData[] dataPair = (OstRawData[]) treeMap.get(ostRawData.ostId);
      
        if (dataPair == null) {
          dataPair = new OstRawData[2];
          treeMap.put(ostRawData.ostId, dataPair);
        }
       
        // Add raw data to the dataPair array stored in the treeMap.
        Database.addRawData(ostRawData, dataPair);

      } // while

    }
    finally {
      resultSet.close();
    }

    // Process raw data.
    return Database.processRawOstData(treeMap);

  } //  getCurrentOstData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current OSS data.
   *
   * @return OssData object containing requested data.
   */

  public OssData getCurrentOssData()
  throws Exception
  {

    final OssInfo[] ossInfo = this.getOssInfo();
    final OstInfo[] ostInfo = this.getOstInfo();

    //
    // Create map of ostId to corresponding ossId
    //

    final Hashtable ostIdToOssId = new Hashtable();

    for (int i = 0; i < ostInfo.length; i++) {
      
      final Integer ossId = new Integer(ostInfo[i].ossId);
      
      ostIdToOssId.put(
        new Integer(ostInfo[i].ostId),
        ossId);
      
    } // for i
    
    final OstData ostData = this.getCurrentOstData();
    
    //
    // Create map of ostData index to corresponding ossId
    //

    int[] indexToOssId = new int[ostData.getSize()];

    for (int j = 0; j < ostData.getSize(); j++) {
      
      final Integer ostId = ostData.getOstId(j);
      indexToOssId[j] = ((Integer) ostIdToOssId.get(ostId)).intValue();
      
    } // for j
    

    //
    // OSS data is constructed mainly from OST data.
    //

    final OssData ossData = new OssData(ossInfo.length);
    final TimeIt timeIt = new TimeIt();
    final ResultSet resultSet = this.ossDataQuery.executeQuery();

    if (debug) System.err.println(Debug.tag() + timeIt.getTime() + " secs");

    for (int i = 0; i < ossInfo.length; i++) {
      try {
        if (resultSet.next()) {
	  int index = 1;
	  Timestamp timestamp = resultSet.getTimestamp(index++);
	  String host = resultSet.getString(index++);
	  Integer ossid = (Integer) resultSet.getObject(index++);
	  Long tsid = (Long) resultSet.getObject(index++);
	  Float pctcpu = (Float) resultSet.getObject(index++);
	  Float pctmem = (Float) resultSet.getObject(index++);

          ossData.setOssId(i, ossid);
          ossData.setHostname(i, host);
	  ossData.setPctCpu(i, pctcpu);

          Float readRate  = null;
          Float writeRate = null;

          for (int j = 0; j < ostData.getSize(); j++) {
        
            if (ossid.intValue() == indexToOssId[j]) {

              if (ostData.getReadRate(j) != null) {
                if (readRate == null) {
                  readRate = ostData.getReadRate(j);
                }
                else {
                  readRate = new Float(
                    readRate.floatValue() + ostData.getReadRate(j).floatValue());
                }
              }

              if (ostData.getWriteRate(j) != null) {
                if (writeRate == null) {
                  writeRate = ostData.getWriteRate(j);
                }
                else {
                  writeRate = new Float(
                    writeRate.floatValue() + ostData.getWriteRate(j).floatValue());
                }
              }

              if (ostData.getTimestamp(j) != null) {
                // Use latest values
                ossData.setPctKbytes(i,  ostData.getPctKbytes(j));
                ossData.setPctInodes(i,  ostData.getPctInodes(j));
                ossData.setKbytesFree(i, ostData.getKbytesFree(j));
                ossData.setKbytesUsed(i, ostData.getKbytesUsed(j));
                ossData.setInodesFree(i, ostData.getInodesFree(j));
                ossData.setInodesUsed(i, ostData.getInodesUsed(j));
              }
            }
          } // for j

          ossData.setReadRate(i,  readRate);
          ossData.setWriteRate(i, writeRate);
        }
      }
      catch (Exception e) {
        throw e;
      }
    } // for i

    try {
      resultSet.close();
    }
    catch (Exception e) {
      throw e;
    }

    return ossData;

  } //  getCurrentOssData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current data from the ROUTER_DATA table.
   *
   * @return array of RouterData objects.
   */

  public RouterData getCurrentRouterData(final int routerGroupId)
  throws Exception
  {

    final TreeMap treeMap = new TreeMap();
    final TimeIt timeIt = new TimeIt();
 
    this.routerDataQuery.setInt(1, routerGroupId);

    final ResultSet resultSet = this.routerDataQuery.executeQuery();

    RouterData routerData = null;

    try {

      if (debug) {
        System.err.println(Debug.tag());
        describeResultSet(resultSet);
        System.err.println(Debug.tag() + timeIt.getTime() + " secs");
      }

      while (resultSet.next()) {
    
        final RouterRawData routerRawData = new RouterRawData();

        int index = 1;
        routerRawData.routerId   = resultSet.getInt(index++);
        routerRawData.timestamp  = resultSet.getTimestamp(index++);
        routerRawData.routerName = resultSet.getString(index++);
        routerRawData.tsId       = (Long)  resultSet.getObject(index++);
        routerRawData.bytes      = (Long)  resultSet.getObject(index++);
        routerRawData.pctCpu     = (Float) resultSet.getObject(index++);

        final Integer id = new Integer(routerRawData.routerId);
        RouterRawData[] dataPair = (RouterRawData[]) treeMap.get(id);
      
        if (dataPair == null) {
          dataPair = new RouterRawData[2];
          treeMap.put(id, dataPair);
        }
       
        // Add raw data to the dataPair array stored in the treeMap.
        Database.addRawData(routerRawData, dataPair);

      } // while
    }
    finally {
      resultSet.close();
    }

    // Process raw data.
    routerData = Database.processRawRouterData(treeMap);


    return routerData;

  } //  getCurrentRouterData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current data from the MDS_DATA table.
   *
   * @return array of MdsData objects.
   */

  public MdsData getCurrentMdsData(final int mdsId)
  throws Exception
  {

    final TreeMap treeMap = new TreeMap();
    final TimeIt timeIt = new TimeIt();

    this.mdsDataQuery.setInt(1, mdsId);
    
    final ResultSet resultSet = this.mdsDataQuery.executeQuery();
    
    if (debug) System.err.println(Debug.tag() + timeIt.getTime() + " secs");
    

    try {
      while (resultSet.next()) {
    
        final MdsRawData mdsRawData = new MdsRawData();

        int index = 1;
        mdsRawData.mdsName    = resultSet.getString(index++);
        mdsRawData.timestamp  = resultSet.getTimestamp(index++);
        mdsRawData.tsId       = (Long)  resultSet.getObject(index++);
        mdsRawData.pctCpu     = (Float) resultSet.getObject(index++);
        mdsRawData.kbytesFree = (Long)  resultSet.getObject(index++);
        mdsRawData.kbytesUsed = (Long)  resultSet.getObject(index++);
        mdsRawData.inodesFree = (Long)  resultSet.getObject(index++);
        mdsRawData.inodesUsed = (Long)  resultSet.getObject(index++);


        // Retain mechanism for calculating rates over time, even though we
        // are not currently doing any such calculations.  So this is all
        // overkill at the moment.

        final Integer id = new Integer(mdsId);
        MdsRawData[] dataPair = (MdsRawData[]) treeMap.get(id);
      
        if (dataPair == null) {
          dataPair = new MdsRawData[2];
          treeMap.put(id, dataPair);
        }

        // Add raw data to the dataPair array stored in the treeMap.
        Database.addRawData(mdsRawData, dataPair);

      } // while
    }
    finally {
      resultSet.close();
    }

    // Process raw data.
    return Database.processRawMdsData(treeMap);

  } // getCurrentMdsData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current data from the MDS_OPS_DATA table.
   *
   * @return an MdsOpsData object.
   */

  public MdsOpsData getCurrentMdsOpsData(final int mdsId)
  throws Exception
  {

    final TreeMap treeMap = new TreeMap();
    final TimeIt timeIt = new TimeIt();

    this.mdsOpsDataQuery.setInt(1, mdsId);
    final ResultSet resultSet = this.mdsOpsDataQuery.executeQuery();
    
    if (debug) System.err.println(Debug.tag() + timeIt.getTime() + " secs");
    
    try {
      while (resultSet.next()) {
    
        final MdsOpsRawData mdsOpsRawData = new MdsOpsRawData();

        int index = 1;
        mdsOpsRawData.tsId          = (Long)  resultSet.getObject(index++);
        mdsOpsRawData.timestamp     = resultSet.getTimestamp(index++);
        mdsOpsRawData.opId          = resultSet.getInt(index++);
        mdsOpsRawData.opName        = resultSet.getString(index++);
        mdsOpsRawData.units         = resultSet.getString(index++);
        mdsOpsRawData.samples       = (Long)  resultSet.getObject(index++);
        //mdsOpsRawData.samplesPerSec = (Float) resultSet.getObject(index++);
        //mdsOpsRawData.avgVal        = (Float) resultSet.getObject(index++);
        mdsOpsRawData.sum           = (Long)  resultSet.getObject(index++);
        mdsOpsRawData.sumSquares    = (Long)  resultSet.getObject(index++);

        // Retain mechanism for calculating rates over time, even though we
        // are not currently doing any such calculations.

        //Integer id = new Integer(mdsOpsRawData.opName);
        MdsOpsRawData[] dataPair = (MdsOpsRawData[]) treeMap.get(mdsOpsRawData.opName);
      
        if (dataPair == null) {
          dataPair = new MdsOpsRawData[2];
          treeMap.put(mdsOpsRawData.opName, dataPair);
        }

        // Add raw data to the dataPair array stored in the treeMap.
        Database.addRawData(mdsOpsRawData, dataPair);

      } // while

    }
    finally {
      resultSet.close();
    }

    // Process raw data.
    return Database.processRawMdsOpsData(treeMap);

  } // getCurrentMdsOpsData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current Filesystem summary data.
   *
   * @param database Database connection to use to get data.
   * @return a FilesystemData object.
   */

  public static FilesystemData getCurrentFilesystemData(
    Database database)
  throws Exception
  {

    final OstData ostData = database.getCurrentOstData();
    
    final FilesystemData filesystemData = Database.getFilesystemDataFromOstData(ostData);
    final FilesystemInfo filesystemInfo = database.getFilesystemInfo();

    filesystemData.filesystemId        = filesystemInfo.filesystemId;
    filesystemData.filesystemName      = filesystemInfo.filesystemName;
    filesystemData.filesystemMountName = filesystemInfo.filesystemMountName;
    
    return filesystemData;
    
  } // getCurrentFilesystemData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get current Filesystem summary data for this instance.
   *
   * @return a FilesystemData object.
   */

  public FilesystemData getCurrentFilesystemData()
  throws Exception
  {

    final OstData ostData = this.getCurrentOstData();
    
    final FilesystemData filesystemData = Database.getFilesystemDataFromOstData(ostData);
    final FilesystemInfo filesystemInfo = this.getFilesystemInfo();

    filesystemData.filesystemId        = filesystemInfo.filesystemId;
    filesystemData.filesystemName      = filesystemInfo.filesystemName;
    filesystemData.filesystemMountName = filesystemInfo.filesystemMountName;
    
    return filesystemData;
    
  } // getCurrentFilesystemData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get connections to all databases listed in the lmtrc file.
   *
   * @return array of connected Database objects.  Null elements indicate that a 
   *         particular connection could not be made.
   * @throw Exception if there is a problem other than a connection problem 
   *        (connection problems are recorded in Database.connectException).
   */

  public static Database[] getAllDatabases()
  throws Exception
  {

    final FilesystemInfo[] filesystemInfo = Database.getAllFilesystemInfo();
    final Database[] databases = new Database[filesystemInfo.length];
    Thread[] threads = new Thread[databases.length];

    for (int i = 0; i < filesystemInfo.length; i++) {
      
      final int ii = i;

      //
      // Done in parallel just to speed things up in case multiple databases
      // are unreachable.  This way we done have to wait in series for all
      // connection time-outs to occur.
      //

      threads[i] = new Thread("Connect to database " + i) {
        
        public void run() {
                    
          //
          // Connect to database
          //
          
          databases[ii] = new Database(filesystemInfo[ii]);
          
          databases[ii].connect();
          
        } // run
        
      }; // Thread

      threads[i].start();

    } //  for i

    for (int i = 0; i < filesystemInfo.length; i++) {
      threads[i].join();
    }

    return databases;
      
  } // getAllDatabases

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Examine all OST data for the current filesystem to produce summary 
   * information for an entire filesystem.
   *
   * @param ostData OstData object from getCurrentOstData method call.
   * @return summary filesystem information as FilesystemData object.
   */

  private static FilesystemData getFilesystemDataFromOstData(
    final OstData ostData)
  {
    
    boolean[] indices = new boolean[ostData.getSize()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = true;
    } // for i
    
    
    final FilesystemData filesystemData = new FilesystemData();
    
    filesystemData.readRate    = ostData.getReadRateSum(indices);
    filesystemData.readRateAvg = ostData.getReadRateAvg(indices);
    filesystemData.readRateMin = ostData.getReadRateMin(indices);
    filesystemData.readRateMax = ostData.getReadRateMax(indices);
    
    filesystemData.writeRate    = ostData.getWriteRateSum(indices);
    filesystemData.writeRateAvg = ostData.getWriteRateAvg(indices);
    filesystemData.writeRateMin = ostData.getWriteRateMin(indices);
    filesystemData.writeRateMax = ostData.getWriteRateMax(indices);
    
    filesystemData.pctCpuAvg = ostData.getPctCpuAvg(indices);
    filesystemData.pctCpuMin = ostData.getPctCpuMin(indices);
    filesystemData.pctCpuMax = ostData.getPctCpuMax(indices);
    
    filesystemData.pctKbytesAvg = ostData.getPctKbytesAvg(indices);
    filesystemData.pctKbytesMin = ostData.getPctKbytesMin(indices);
    filesystemData.pctKbytesMax = ostData.getPctKbytesMax(indices);
    
    filesystemData.pctInodesAvg = ostData.getPctInodesAvg(indices);
    filesystemData.pctInodesMin = ostData.getPctInodesMin(indices);
    filesystemData.pctInodesMax = ostData.getPctInodesMax(indices);
    
    filesystemData.kbytesFree    = ostData.getKbytesFreeSum(indices);
    filesystemData.kbytesFreeAvg = ostData.getKbytesFreeAvg(indices);
    filesystemData.kbytesFreeMin = ostData.getKbytesFreeMin(indices);
    filesystemData.kbytesFreeMax = ostData.getKbytesFreeMax(indices);
    
    filesystemData.kbytesUsed    = ostData.getKbytesUsedSum(indices);
    filesystemData.kbytesUsedAvg = ostData.getKbytesUsedAvg(indices);
    filesystemData.kbytesUsedMin = ostData.getKbytesUsedMin(indices);
    filesystemData.kbytesUsedMax = ostData.getKbytesUsedMax(indices);
    
    filesystemData.inodesFree    = ostData.getInodesFreeSum(indices);
    filesystemData.inodesFreeAvg = ostData.getInodesFreeAvg(indices);
    filesystemData.inodesFreeMin = ostData.getInodesFreeMin(indices);
    filesystemData.inodesFreeMax = ostData.getInodesFreeMax(indices);
    
    filesystemData.inodesUsed    = ostData.getInodesUsedSum(indices);
    filesystemData.inodesUsedAvg = ostData.getInodesUsedAvg(indices);
    filesystemData.inodesUsedMin = ostData.getInodesUsedMin(indices);
    filesystemData.inodesUsedMax = ostData.getInodesUsedMax(indices);

    filesystemData.pctKbytes = Database.percentUsed(
      filesystemData.kbytesUsed,
      filesystemData.kbytesFree);

    filesystemData.pctInodes = Database.percentUsed(
      filesystemData.inodesUsed,
      filesystemData.inodesFree);
    
    return filesystemData;
    
  } //  getFilesystemDataFromOstData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get array of all router ids for a given group.
   *
   * @param routerGroupId return all router ids for this group.
   * @return array of router ids from ROUTER_INFO table that are part of the
   *         given router group.
   */
  
  public int[] getRouterIdsForGroup(
    int routerGroupId)
  throws
    Exception
  {

    this.routerIdsForGroupQuery.setInt(1, routerGroupId);

    final ResultSet resultSet = this.routerIdsForGroupQuery.executeQuery();
    final ArrayList arrayList = new ArrayList();
    
    try {

      while (resultSet.next()) {
        final int id = resultSet.getInt(1);
        arrayList.add(new Integer(id));
      } // while
    }
    finally {
      resultSet.close();
    }

    int[] ids = new int[arrayList.size()];

    for (int i = 0; i < arrayList.size(); i++) {
      ids[i] = ((Integer) arrayList.get(i)).intValue();
    } // for i      

    return ids;

  } // getRouterIdsForGroup

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get array of all router group ids.
   *
   * @return array of router group ids from ROUTER_INFO table.
   */

  public int[] getRouterGroupIds()
  throws Exception
  {

    final ResultSet resultSet = this.routerGroupsQuery.executeQuery();
    final ArrayList arrayList = new ArrayList();
    
    try {

      while (resultSet.next()) {
        final int id = resultSet.getInt(1);
        arrayList.add(new Integer(id));
      } // while
    }
    finally {
      resultSet.close();
    }

    int[] ids = new int[arrayList.size()];

    for (int i = 0; i < arrayList.size(); i++) {
      ids[i] = ((Integer) arrayList.get(i)).intValue();
    } // for i      

    return ids;

  } //  getRouterGroupIds

  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * Get MDS info from MDS_INFO table.
   *
   * @return MDS info as an array of MdsInfo objects.
   */

  public MdsInfo[] getMdsInfo()
  throws Exception
  {

    final ResultSet resultSet = this.mdsInfoQuery.executeQuery();
    
    final ArrayList arrayList = new ArrayList();
    
    try {

      while (resultSet.next()) {
        
        final MdsInfo mdsInfo = new MdsInfo();
        
        mdsInfo.mdsId      = resultSet.getInt(1);
        mdsInfo.mdsName    = resultSet.getString(2);
        mdsInfo.hostName   = resultSet.getString(3);
        mdsInfo.deviceName = resultSet.getString(4);
        
        arrayList.add(mdsInfo);
      
      } // while
    }
    finally {
      resultSet.close();
    }
    
    return (MdsInfo[]) arrayList.toArray(new MdsInfo[arrayList.size()]);

  } // getMdsInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Add a raw data sample to dataPair array.  Data is added so that
   * pairData holds the latest two samples, if available.
   */

  private static void addRawData(
    final RawData rawData,
    RawData[] dataPair)
  {

    //
    // Extra comparisions are there to allow for proper handling of duplicate
    // records.
    //
    // dataPair[0] is timestep n
    // dataPair[1] is timestep n + 1
    //


    if (debug) {
      System.err.println(Debug.tag() + "rawData = " + rawData);
      System.err.println(Debug.tag() + "rawData.tsId = " + rawData.tsId);
    }


    if (dataPair[1] == null) {

      // Initial addition

      dataPair[1] = rawData;

    }
    else if (dataPair[0] == null) {

      // Second addition

      if (rawData.tsId.compareTo(dataPair[1].tsId) > 0) {

        dataPair[0] = dataPair[1];
        dataPair[1] = rawData;


      }
      else if (rawData.tsId.compareTo(dataPair[1].tsId) < 0) {

        dataPair[0] = rawData;

      }

    }
    else {
      
      // Any additional additions

      if (rawData.tsId.compareTo(dataPair[1].tsId) > 0) {

        dataPair[0] = dataPair[1];
        dataPair[1] = rawData;

      }
      else if (
        rawData.tsId.compareTo(dataPair[1].tsId) < 0 &&
        rawData.tsId.compareTo(dataPair[0].tsId) > 0)
      {
        
        dataPair[0] =  rawData;
        
      }

    }

    if (dataPair[1] == null) {
      // Should never be null at this point.  Other parts of the code assume that
      // it will not be null, so we need to throw an error if it is.
      throw new Error("dataPair[1] is null");
    }


  } // addRawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate derived values from raw OST data.
   */

  private static OstData processRawOstData(
    final TreeMap treeMap)
  {

    if (debug) System.err.println(
      Debug.tag() + "treeMap.size() = " + treeMap.size());

    final OstData ostData = new OstData(treeMap.size());
    final Iterator iterator = treeMap.values().iterator();
        
    int index = 0;

    while (iterator.hasNext()) {

      // pairData holds raw OST data for n and n+1 timesteps.

      final OstRawData[] pairData = (OstRawData[]) iterator.next();

      if (debug) {
        System.err.println(Debug.tag() + "pairData[0] = " + pairData[0]);
        System.err.println(Debug.tag() + "pairData[1] = " + pairData[1]);
      }

      // We assume pairData always has one non-null entry in element #1

      ostData.setOstId(     index, pairData[1].ostId);
      ostData.setTimestamp( index, pairData[1].timestamp);
      ostData.setOstName(   index, pairData[1].ostName);
      ostData.setTsId(      index, pairData[1].tsId);
      ostData.setPctCpu(    index, pairData[1].pctCpu);
      ostData.setKbytesFree(index, pairData[1].kbytesFree);
      ostData.setKbytesUsed(index, pairData[1].kbytesUsed);
      ostData.setInodesFree(index, pairData[1].inodesFree);
      ostData.setInodesUsed(index, pairData[1].inodesUsed);

      final Float pctKbytes = Database.percentUsed(
        pairData[1].kbytesUsed,
        pairData[1].kbytesFree);

      ostData.setPctKbytes(index, pctKbytes);

      final Float pctInodes = Database.percentUsed(
        pairData[1].inodesUsed,
        pairData[1].inodesFree);

      ostData.setPctInodes(index, pctInodes);

      if (pairData[0] == null) {

        // We don't have two recent samples, so can't do rate calc.
        ostData.setReadRate(index, null);
        ostData.setWriteRate(index, null);

      }
      else {

        //
        // Rate at time n+1 is calulated from difference between 
        // values at n+1 and n.
        //

        final float delta = Database.timeDiff(
          pairData[1].timestamp,
          pairData[0].timestamp);

        ostData.setReadRate(
          index,
          Database.rate(pairData[1].readBytes, pairData[0].readBytes, delta, MEBI));
          
        ostData.setWriteRate(
          index,
          Database.rate(pairData[1].writeBytes, pairData[0].writeBytes, delta, MEBI));

        if (debug) {
          System.err.println(Debug.tag() + "pairData[0].readBytes = " + pairData[0].readBytes);
          System.err.println(Debug.tag() + "pairData[1].readBytes = " + pairData[1].readBytes);
          System.err.println(Debug.tag() + "delta = " + delta);
        }

      }

      index++;

    } // while

    return ostData;

  } // processRawOstData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate derived values from raw router data.
   */

  private static RouterData processRawRouterData(
    final TreeMap treeMap)
  {

    if (debug) System.err.println(
      Debug.tag() + "treeMap.size() = " + treeMap.size());
    
    final RouterData routerData = new RouterData(treeMap.size());
    final Iterator iterator = treeMap.values().iterator();

    int index = 0;

    while (iterator.hasNext()) {

      // pairData holds raw router data for n and n+1 timesteps.

      final RouterRawData[] pairData = (RouterRawData[]) iterator.next();

      // We assume pairData always has one non-null entry in element #1

      routerData.setRouterId(index, pairData[1].routerId);
      routerData.setTimestamp(index, pairData[1].timestamp);
      routerData.setRouterName(index, pairData[1].routerName);
      routerData.setTsId(index, pairData[1].tsId);
      routerData.setBytes(index, pairData[1].bytes);
      routerData.setPctCpu(index, pairData[1].pctCpu);

      if (pairData[0] == null) {

        // We don't have two recent sample, so can't do rate calc.
        routerData.setRate(index, null);

      }
      else {

        final float delta = Database.timeDiff(
          pairData[1].timestamp,
          pairData[0].timestamp);
                
        routerData.setRate(
          index,
          Database.rate(pairData[1].bytes, pairData[0].bytes, delta, MEBI));

      }


      index++;

    } // while

    return routerData;


  } // processRawRouterData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate derived values from raw MDS data.
   */

  private static MdsData processRawMdsData(
    final TreeMap treeMap)
  {

    if (debug) System.err.println(
      Debug.tag() + "treeMap.size() = " + treeMap.size());


    final MdsData mdsData = new MdsData(treeMap.size());

    final Iterator iterator = treeMap.values().iterator();

    int index = 0;

    while (iterator.hasNext()) {

      // pairData holds raw OST data for n and n+1 timesteps.

      final MdsRawData[] pairData = (MdsRawData[]) iterator.next();

      if (debug) {
        System.err.println(Debug.tag() + "pairData[0] = " + pairData[0]);
        System.err.println(Debug.tag() + "pairData[1] = " + pairData[1]);
      }

      // We assume pairData always has one non-null entry in element #1

      mdsData.setMdsName(   index, pairData[1].mdsName);
      mdsData.setTimestamp( index, pairData[1].timestamp);
      mdsData.setTsId(      index, pairData[1].tsId);
      mdsData.setPctCpu(    index, pairData[1].pctCpu);
      mdsData.setKbytesFree(index, pairData[1].kbytesFree);
      mdsData.setKbytesUsed(index, pairData[1].kbytesUsed);
      mdsData.setInodesFree(index, pairData[1].inodesFree);
      mdsData.setInodesUsed(index, pairData[1].inodesUsed);

      final Float pctKbytes = Database.percentUsed(
        pairData[1].kbytesUsed,
        pairData[1].kbytesFree);
      
      mdsData.setPctKbytes(index, pctKbytes);

      final Float pctInodes = Database.percentUsed(
        pairData[1].inodesUsed,
        pairData[1].inodesFree);
      
      mdsData.setPctInodes(index, pctInodes);

      index++;

    } // while

    return mdsData;

  } // processRawMdsData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate derived values from raw MDS ops data.
   */

  private static MdsOpsData processRawMdsOpsData(
    final TreeMap treeMap)
  {

    if (debug) System.err.println(
      Debug.tag() + "treeMap.size() = " + treeMap.size());


    final MdsOpsData mdsOpsData = new MdsOpsData(treeMap.size());

    final Iterator iterator = treeMap.values().iterator();

    int index = 0;

    while (iterator.hasNext()) {

      // pairData holds raw data for n and n+1 timesteps.

      final MdsOpsRawData[] pairData = (MdsOpsRawData[]) iterator.next();

      if (debug) {
        System.err.println(Debug.tag() + "pairData[0] = " + pairData[0]);
        System.err.println(Debug.tag() + "pairData[1] = " + pairData[1]);
      }

      // We assume pairData always has one non-null entry in element #1

      mdsOpsData.setTsId(         index, pairData[1].tsId);
      mdsOpsData.setTimestamp(    index, pairData[1].timestamp);
      mdsOpsData.setOpId(         index, pairData[1].opId);
      mdsOpsData.setOpName(       index, pairData[1].opName);
      mdsOpsData.setUnits(        index, pairData[1].units);

      Long samples = null;
      Long sum = null;
      Long sumSquares = null;

      if (pairData[0] != null) {
      
        samples    = Database.diff(pairData[1].samples,    pairData[0].samples);
        sum        = Database.diff(pairData[1].sum,        pairData[0].sum);
        sumSquares = Database.diff(pairData[1].sumSquares, pairData[0].sumSquares);
        
      }

      mdsOpsData.setSamples(index, samples);
      mdsOpsData.setSum(index, sum);
      mdsOpsData.setSumSquares(index, sumSquares);

      if (samples != null && samples.longValue() > 0 && sum != null) {

        // We have enough info to calculate average value.

        mdsOpsData.setAvgVal(
          index,
          new Float(((float) sum.longValue())/((float) samples.longValue()))
          );

      }

      if (samples != null && samples.longValue() > 1 && sum != null && sumSquares != null) {

        // We have enough info to calculate std. deviation.

        final double num = (double) samples.longValue();
        final double sumSqs = (double) sumSquares.longValue();
        final double sqSums = (double) (sum.longValue()*sum.longValue());
        final Float stdDev = new Float(
          Math.sqrt(Math.max(num*sumSqs - sqSums, 0.0d)/(num*(num - 1.0d))));

        mdsOpsData.setStdDev(index, stdDev);

      }
      
      if (pairData[0] == null) {

        // We don't have two recent samples, so can't do rate calc.
        mdsOpsData.setSamplesPerSec(index, null);

      }
      else {

        final float delta = Database.timeDiff(
          pairData[1].timestamp,
          pairData[0].timestamp);

        mdsOpsData.setSamplesPerSec(
          index,
          Database.rate(pairData[1].samples, pairData[0].samples, delta, 1.0f));

        if (debug) {
          System.err.println(Debug.tag() + "pairData[0].samples = " + pairData[0].samples);
          System.err.println(Debug.tag() + "pairData[1].samples = " + pairData[1].samples);
          System.err.println(Debug.tag() + "delta = " + delta);
        }

      }

      index++;

    } // while

    return mdsOpsData;

  } // processRawMdsOpsData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * @deprecated
   * Get raw data from OST_DATA table.
   * 
   * @param ostId OST_ID to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return Iterator containing OstRawData objects.
   */

  public Iterator getOstRawData(
    final int ostId,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    this.ostRawDataQuery.setInt(1, ostId);
    this.ostRawDataQuery.setTimestamp(2, startTimestamp);
    this.ostRawDataQuery.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = this.ostRawDataQuery.executeQuery();
    
    return new OstRawDataIterator(resultSet);

  } //  getOstRawData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from OST_DATA as if it were aggregate data.  Will calculate 
   * variables that exist in OST_VARIABLE_INFO but not appear in OST_DATA.
   *
   * @param ostId OST_ID to return data for.
   * @param variableName name of variable to return data for.
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   */

  private AggregateData[] getOstDataAsAggregate(
    final int ostId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "ostId = " + ostId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    AggregateData[] oad = null;

    boolean kbytes = false;
    boolean read = false;

    if ((read = "READ_RATE".equals(variableName)) || "WRITE_RATE".equals(variableName)) {

      if (debug)  System.err.println(Debug.tag() + "calc rate values");


      //
      // Derive rate values
      //

      final String var = read ? "READ_BYTES" : "WRITE_BYTES";

      final TimeSequenceData[] tsd = getOstTimeSequenceData(
        ostId,
        var,
        startTimestamp,
        endTimestamp);

      if (tsd.length == 0) {

        oad = new AggregateData[0];

      }
      else {

        //
        // Note that we return one less datapoint than the source has.  This is
        // because the database query to find the latest point before
        // startTimestamp is very inefficient.  Missing one point should not
        // matter that much - people can always select a bigger interval if they
        // want to see the missing point.  So we, somewhat arbitrarily, decide 
        // that the rate at point i is calculated from values at i-1 and i.
        //

        oad = new AggregateData[tsd.length - 1];

        Database.aggregateRateCalc(oad, tsd);

      }

    }
    else if ("READ_BYTES".equals(variableName) || "WRITE_BYTES".equals(variableName)) {

      //
      // Derive values
      //

      final TimeSequenceData[] tsd = getOstTimeSequenceData(
        ostId,
        variableName,
        startTimestamp,
        endTimestamp);

      if (tsd.length == 0) {

        oad = new AggregateData[0];

      }
      else {

        //
        // Note that we return one less datapoint than the source has.  This is
        // because the database query to find the latest point before
        // startTimestamp is very inefficient.  Missing one point should not
        // matter that much - people can always select a bigger interval if they
        // want to see the missing point.
        //
    
        oad = new AggregateData[tsd.length - 1];

        Database.aggregateDiffCalc(oad, tsd);

      }

    }
    else if ((kbytes = "PCT_KBYTES".equals(variableName)) || "PCT_INODES".equals(variableName)) {
      
      if (debug) System.err.println(Debug.tag() + "calc percent values");

      //
      // Derive percent values
      //

      String var0 = null;
      String var1 = null;

      if (kbytes) {
        var0 = "KBYTES_USED";
        var1 = "KBYTES_FREE";
      }
      else {
        var0 = "INODES_USED";
        var1 = "INODES_FREE";
      }
      
      final Object[] vars = getOstTimeSequenceData2(
        ostId,
        var0,
        var1,
        startTimestamp,
        endTimestamp);
      
      final TimeSequenceData[] used = (TimeSequenceData[]) vars[0];
      final TimeSequenceData[] free = (TimeSequenceData[]) vars[1];
      
      oad = new AggregateData[used.length];

      Database.aggregatePercentUsedCalc(oad, used, free);
      
    }
    else {

      if (debug)  System.err.println(Debug.tag() + "process non-derived values");

      //
      // For non-derived variables, we just package things up and send them off.
      //

      final TimeSequenceData[] tsd = getOstTimeSequenceData(
        ostId,
        variableName,
        startTimestamp,
        endTimestamp);
    
      oad = new AggregateData[tsd.length];
    
      Database.aggregateFill(oad, tsd);

    }
    
    return oad;    
    
  } // getOstDataAsAggregate

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get a single variable from OST_DATA for a specific time interval.
   *
   * @param ostId OST_ID to return data for.
   * @param variableName name of variable to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   * @return array of TimeSequenceData objects.
   */

  private TimeSequenceData[] getOstTimeSequenceData(
    final int ostId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    final PreparedStatement ps = this.connection.prepareStatement(
      "select TIMESTAMP," + variableName + " from OST_DATA,TIMESTAMP_INFO " +
        "where OST_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and OST_ID=?" +
      " and TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");
    
    ps.setInt(1, ostId);
    ps.setTimestamp(2, startTimestamp);
    ps.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = ps.executeQuery();
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {
      
      final TimeSequenceData tsd = new TimeSequenceData();

      tsd.timestamp = resultSet.getTimestamp(1);
      tsd.value = resultSet.getFloat(2);

      arrayList.add(tsd);

    } // while

    resultSet.close();
    ps.close();

    return (TimeSequenceData[])
      arrayList.toArray(new TimeSequenceData[arrayList.size()]);

  } // getOstTimeSequenceData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get two variables from OST_DATA for a specific time interval.
   * Done in a single query so that time sequence will match.  Mainly used to
   * get USED and FREE values at the same time for percent-used calculations.
   *
   * @param ostId OST_ID to return data for.
   * @param variableName0 name of first variable to return data for.
   * @param variableName1 name of second variable to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   * @return two element array of Object corresponding to the two requested 
   *         variables.  Each element contains an array of TimeSequenceData
   *         objects.
   */

  private Object[] getOstTimeSequenceData2(
    final int ostId,
    final String variableName0,
    final String variableName1,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "ostId = " + ostId);
      System.err.println(Debug.tag() + "variableName0 = " + variableName0);
      System.err.println(Debug.tag() + "variableName1 = " + variableName1);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    final PreparedStatement ps = this.connection.prepareStatement(
      "select TIMESTAMP," + variableName0 + "," + variableName1 +
      " from OST_DATA,TIMESTAMP_INFO " +
        "where OST_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and OST_ID=?" +
      " and TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");
    
    ps.setInt(1, ostId);
    ps.setTimestamp(2, startTimestamp);
    ps.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = ps.executeQuery();

    final ArrayList arrayList0 = new ArrayList();
    final ArrayList arrayList1 = new ArrayList();

    while (resultSet.next()) {
      
      final TimeSequenceData tsd0 = new TimeSequenceData();
      final TimeSequenceData tsd1 = new TimeSequenceData();

      final Timestamp timestamp = resultSet.getTimestamp(1);

      tsd0.timestamp = timestamp;
      tsd0.value = resultSet.getFloat(2);

      tsd1.timestamp = timestamp;
      tsd1.value = resultSet.getFloat(3);

      arrayList0.add(tsd0);
      arrayList1.add(tsd1);

    } // while

    resultSet.close();
    ps.close();

    Object[] returnVal = new Object[2];

    returnVal[0] = (TimeSequenceData[])
      arrayList0.toArray(new TimeSequenceData[arrayList0.size()]);

    returnVal[1] = (TimeSequenceData[])
      arrayList1.toArray(new TimeSequenceData[arrayList1.size()]);

    return returnVal;

  } // getOstTimeSequenceData2

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from OST aggregate tables for a particular OST and variable.  Will
   * generate PCT_KBYTES and PCT_INODES variable data from USED and FREE 
   * variables stored in database.  All other variable data is returned as is
   * from the database.
   *
   * @param level aggregation level (RAW, HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param ostId OST_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  public AggregateData[] getOstAggregateData(
    final int level,
    final int ostId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {


    if (level == Database.RAW) {

      //
      //  Special case of raw data.
      //

      return getOstDataAsAggregate(
        ostId,
        variableName,
        startTimestamp,
        endTimestamp);

    }


    String freeVar = null;
    String usedVar = null;

    if ("PCT_KBYTES".equals(variableName)) {
      
      freeVar = "KBYTES_FREE";
      usedVar = "KBYTES_USED";

    }
    else if ("PCT_INODES".equals(variableName)) {

      freeVar = "INODES_FREE";
      usedVar = "INODES_USED";

    }
    else if ("READ_RATE".equals(variableName) || "WRITE_RATE".equals(variableName)) {


      //
      // Need to scale rates.
      //
      
      AggregateData[] oad = this.getOstAggregateDataDirect(
        level,
        ostId,
        variableName,
        startTimestamp,
        endTimestamp);
      
      for (int i = 0; i < oad.length; i++) {

        // Note: READ_RATE and WRITE_RATE don't have meaningful aggregate.
        oad[i].minval  /= MEBI;
        oad[i].maxval  /= MEBI;
        oad[i].average /= MEBI;

      } // for i

      return oad;

    }
    else {

      //
      // Direct extraction of non-derived data.
      //

      return 
        this.getOstAggregateDataDirect(
          level,
          ostId,
          variableName,
          startTimestamp,
          endTimestamp);

    }

    //
    // PCT_KBYTES and PCT_INODES are not stored in the database and need to
    // be calculated on the fly from USED and FREE values.
    //

    final Object[] vars =  this.getOstAggregateDataDirect2(
      level,
      ostId,
      usedVar,
      freeVar,
      startTimestamp,
      endTimestamp);

    final AggregateData[] used = (AggregateData[]) vars[0];
    final AggregateData[] free = (AggregateData[]) vars[1];

    AggregateData[] pct =  new AggregateData[free.length];

    for (int i = 0; i < free.length; i++) {

      pct[i] = new AggregateData();
      pct[i].timestamp  = free[i].timestamp;
      pct[i].numSamples = free[i].numSamples;

      //
      // This calc will be incorrect if the size of the disk space has changed
      // over the sample interval.  We assume this happens very infrequently and 
      // we will just live with it if it happens.
      //
      pct[i].average = Database.percentUsed(used[i].average, free[i].average);

      //
      // Assume that 'used' is at maximum when 'free' is at minimum, and
      // vice versa.
      //
      pct[i].minval = Database.percentUsed(used[i].minval, free[i].maxval); 
      pct[i].maxval = Database.percentUsed(used[i].maxval, free[i].minval); 

    } // for i

    return pct;

  } //  getOstAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from OST aggregate tables for a particular OST and variable.  Can 
   * only retrieve actual variables that exist in the database -- does not
   * handle calculated variables.
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param ostId OST_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  private AggregateData[] getOstAggregateDataDirect(
    final int level,
    final int ostId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "ostId = " + ostId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.ostAggregateHourDataQuery;
        break;

      case DAY:
        preparedStatement = this.ostAggregateDayDataQuery;
        break;

      case WEEK:
        preparedStatement = this.ostAggregateWeekDataQuery;
        break;

      case MONTH:
        preparedStatement = this.ostAggregateMonthDataQuery;
        break;

      case YEAR:
        preparedStatement = this.ostAggregateYearDataQuery;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName) ||
      "WRITE_BYTES".equals(variableName);
    
    preparedStatement.setInt(1, ostId);
    preparedStatement.setString(2, variableName);
    preparedStatement.setTimestamp(3, startTimestamp);
    preparedStatement.setTimestamp(4, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {

      final AggregateData oad = new AggregateData();

      oad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        oad.hasAggregate  = true;
        oad.aggregate  = resultSet.getFloat(2);
      }

      oad.minval     = resultSet.getFloat(3);
      oad.maxval     = resultSet.getFloat(4);
      oad.average    = resultSet.getFloat(5);
      oad.numSamples = resultSet.getLong(6);

      arrayList.add(oad);

    } // while

    resultSet.close();

    return (AggregateData[])
      arrayList.toArray(new AggregateData[arrayList.size()]);

  } //  getOstAggregateDataDirect

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from OST aggregate tables for two variables.  Ensures that both 
   * variables are returned with the same time sequence (as might not
   * happen if two separate queries were used).  Can  only retrieve actual
   * variables that exist in the database -- does not handle calculated
   * variables.
   *
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param ostId OST_ID to return data for.
   * @param variableName0 name of first variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param variableName1 name of second variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return Object array containing two arrays of AggregateData.
   */

  private Object[] getOstAggregateDataDirect2(
    final int level,
    final int ostId,
    final String variableName0,
    final String variableName1,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "ostId = " + ostId);
      System.err.println(Debug.tag() + "variableName0 = " + variableName0);
      System.err.println(Debug.tag() + "variableName1 = " + variableName1);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.ostAggregateHourDataQuery2;
        break;

      case DAY:
        preparedStatement = this.ostAggregateDayDataQuery2;
        break;

      case WEEK:
        preparedStatement = this.ostAggregateWeekDataQuery2;
        break;

      case MONTH:
        preparedStatement = this.ostAggregateMonthDataQuery2;
        break;

      case YEAR:
        preparedStatement = this.ostAggregateYearDataQuery2;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName0) ||
      "WRITE_BYTES".equals(variableName0);
    
    preparedStatement.setInt(1, ostId);
    preparedStatement.setString(2, variableName0);
    preparedStatement.setString(3, variableName1);
    preparedStatement.setTimestamp(4, startTimestamp);
    preparedStatement.setTimestamp(5, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList0 = new ArrayList();
    final ArrayList arrayList1 = new ArrayList();

    while (resultSet.next()) {

      final AggregateData oad = new AggregateData();

      oad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        oad.hasAggregate  = true;
        oad.aggregate  = resultSet.getFloat(2);
      }
      oad.minval     = resultSet.getFloat(3);
      oad.maxval     = resultSet.getFloat(4);
      oad.average    = resultSet.getFloat(5);
      oad.numSamples = resultSet.getLong(6);
      final String variableName = resultSet.getString(7);

      if (variableName0.equals(variableName)) {
        arrayList0.add(oad);
      }
      else {
        arrayList1.add(oad);
      }

    } // while

    resultSet.close();

    Object[] returnVal = new Object[2];

    returnVal[0] = (AggregateData[])
      arrayList0.toArray(new AggregateData[arrayList0.size()]);
    
    returnVal[1] = (AggregateData[])
      arrayList1.toArray(new AggregateData[arrayList1.size()]);

    return returnVal;

  } //  getOstAggregateDataDirect2


  //////////////////////////////////////////////////////////////////////////////




  // work in progress





  /**
   * Get data from OST aggregate tables for a particular OSS and variable.  Will
   * generate PCT_KBYTES and PCT_INODES variable data from USED and FREE 
   * variables stored in database.  All other variable data is returned as is
   * from the database.
   *
   * @param level aggregation level (RAW, HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param ossId OSS_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

//   public AggregateData[] getOssAggregateData(
//     final int level,
//     final int ossId,
//     final String variableName,
//     final Timestamp startTimestamp,
//     final Timestamp endTimestamp)
//   throws 
//     Exception
//   {


//     //final OssInfo[] ossInfo = this.getOssInfo();
//     final OstInfo[] ostInfo = this.getOstInfo();

//     ArrayList ostList = new ArrayList();

//     for (int i = 0; i < ostInfo; i++) {
//       if (ostInfo[i].ossId == ossId) {
//         ostList.add(ostInfo[i].ostId);
//       }
//     } //  for i


    



//     //    if (level == Database.RAW) {

//     //       //
//     //       //  Special case of raw data.
//     //       //

//     //       return getOstDataAsAggregate(
//     //         ostId,
//     //         variableName,
//     //         startTimestamp,
//     //         endTimestamp);

//     //     }


//     //     String freeVar = null;
//     //     String usedVar = null;

//     //     if ("PCT_KBYTES".equals(variableName)) {
      
//     //       freeVar = "KBYTES_FREE";
//     //       usedVar = "KBYTES_USED";

//     //     }
//     //     else if ("PCT_INODES".equals(variableName)) {

//     //       freeVar = "INODES_FREE";
//     //       usedVar = "INODES_USED";

//     //     }
//     //     else if ("READ_RATE".equals(variableName) || "WRITE_RATE".equals(variableName)) {


//     //       //
//     //       // Need to scale rates.
//     //       //
      
//     //       AggregateData[] oad = this.getOstAggregateDataDirect(
//     //         level,
//     //         ostId,
//     //         variableName,
//     //         startTimestamp,
//     //         endTimestamp);
      
//     //       for (int i = 0; i < oad.length; i++) {

//     //         // Note: READ_RATE and WRITE_RATE don't have meaningful aggregate.
//     //         oad[i].minval  /= MEBI;
//     //         oad[i].maxval  /= MEBI;
//     //         oad[i].average /= MEBI;

//     //       } // for i

//     //       return oad;

//     //     }
//     //     else {

//     //       //
//     //       // Direct extraction of non-derived data.
//     //       //

//     //       return 
//     //         this.getOstAggregateDataDirect(
//     //           level,
//     //           ostId,
//     //           variableName,
//     //           startTimestamp,
//     //           endTimestamp);

//     //     }

//     //     //
//     //     // PCT_KBYTES and PCT_INODES are not stored in the database and need to
//     //     // be calculated on the fly from USED and FREE values.
//     //     //

//     //     final Object[] vars =  this.getOstAggregateDataDirect2(
//     //       level,
//     //       ostId,
//     //       usedVar,
//     //       freeVar,
//     //       startTimestamp,
//     //       endTimestamp);

//     //     final AggregateData[] used = (AggregateData[]) vars[0];
//     //     final AggregateData[] free = (AggregateData[]) vars[1];

//     //     AggregateData[] pct =  new AggregateData[free.length];

//     //     for (int i = 0; i < free.length; i++) {

//     //       pct[i] = new AggregateData();
//     //       pct[i].timestamp  = free[i].timestamp;
//     //       pct[i].numSamples = free[i].numSamples;

//     //       //
//     //       // This calc will be incorrect if the size of the disk space has changed
//     //       // over the sample interval.  We assume this happens very infrequently and 
//     //       // we will just live with it if it happens.
//     //       //
//     //       pct[i].average = Database.percentUsed(used[i].average, free[i].average);

//     //       //
//     //       // Assume that 'used' is at maximum when 'free' is at minimum, and
//     //       // vice versa.
//     //       //
//     //       pct[i].minval = Database.percentUsed(used[i].minval, free[i].maxval); 
//     //       pct[i].maxval = Database.percentUsed(used[i].maxval, free[i].minval); 

//     //     } // for i

//     return pct;

//   } //  getOssAggregateData







  // work in progress






  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from ROUTER_DATA as if it were aggregate data.  Will calculate 
   * variables that exist in ROUTER_VARIABLE_INFO but not appear in ROUTER_DATA.
   *
   * @param routerId ROUTER_ID to return data for.
   * @param variableName name of variable to return data for.
   *        ROUTER_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   */

  private AggregateData[] getRouterDataAsAggregate(
    final int routerId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "routerId = " + routerId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    AggregateData[] rad = null;

    if ("RATE".equals(variableName)) {

      if (debug)  System.err.println(Debug.tag() + "calc rate values");


      //
      // Derive rate values
      //

      final TimeSequenceData[] tsd = getRouterTimeSequenceData(
        routerId,
        "BYTES",
        startTimestamp,
        endTimestamp);

      if (tsd.length == 0) {

        rad = new AggregateData[0];

      }
      else {

        //
        // Note that we return one less datapoint than the source has.  This is
        // because the database query to find the latest point before
        // startTimestamp is very inefficient.  Missing one point should not
        // matter that much - people can always select a bigger interval if they
        // want to see the missing point.  So we, somewhat arbitrarily, decide 
        // that the rate at point i is calculated from values at i-1 and i.
        //

        rad = new AggregateData[tsd.length - 1];

        Database.aggregateRateCalc(rad, tsd);

      }

    }
    else if ("BYTES".equals(variableName)) {

      //
      // Derive values
      //

      final TimeSequenceData[] tsd = getRouterTimeSequenceData(
        routerId,
        variableName,
        startTimestamp,
        endTimestamp);

      if (tsd.length == 0) {

        rad = new AggregateData[0];

      }
      else {

        //
        // Note that we return one less datapoint than the source has.  This is
        // because the database query to find the latest point before
        // startTimestamp is very inefficient.  Missing one point should not
        // matter that much - people can always select a bigger interval if they
        // want to see the missing point.
        //
    
        rad = new AggregateData[tsd.length - 1];

        Database.aggregateDiffCalc(rad, tsd);

      }

    }
    else {

      if (debug)  System.err.println(Debug.tag() + "process non-derived values");

      //
      // For non-derived variables, we just package things up and send them off.
      //

      final TimeSequenceData[] tsd = getRouterTimeSequenceData(
        routerId,
        variableName,
        startTimestamp,
        endTimestamp);
    
      rad = new AggregateData[tsd.length];

      Database.aggregateFill(rad, tsd);

    }
    
    return rad;    
    
  } // getRouterDataAsAggregate

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get a single variable from ROUTER_DATA for a specific time interval.
   *
   * @param routerId ROUTER_ID to return data for.
   * @param variableName name of variable to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   * @return array of TimeSequenceData objects.
   */

  private TimeSequenceData[] getRouterTimeSequenceData(
    final int routerId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    final PreparedStatement ps = this.connection.prepareStatement(
      "select TIMESTAMP," + variableName + " from ROUTER_DATA,TIMESTAMP_INFO " +
        "where ROUTER_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and ROUTER_ID=?" +
      " and TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");
    
    ps.setInt(1, routerId);
    ps.setTimestamp(2, startTimestamp);
    ps.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = ps.executeQuery();
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {
      
      final TimeSequenceData tsd = new TimeSequenceData();

      tsd.timestamp = resultSet.getTimestamp(1);
      tsd.value = resultSet.getFloat(2);

      arrayList.add(tsd);

    } // while

    resultSet.close();
    ps.close();

    return (TimeSequenceData[])
      arrayList.toArray(new TimeSequenceData[arrayList.size()]);

  } // getRouterTimeSequenceData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from ROUTER aggregate tables for a particular ROUTER and variable.
   * RATE data will be scaled to produce output in mebibytes. All other
   * variable data is returned as is from the database.
   *
   * @param level aggregation level (RAW, HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param routerId ROUTER_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        ROUTER_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  public AggregateData[] getRouterAggregateData(
    final int level,
    final int routerId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {


    if (level == Database.RAW) {

      //
      //  Special case of raw data.
      //

      return getRouterDataAsAggregate(
        routerId,
        variableName,
        startTimestamp,
        endTimestamp);

    }



    if ("RATE".equals(variableName)) {


      // Need to scale rates.
      //
      
      AggregateData[] rad = this.getRouterAggregateDataDirect(
        level,
        routerId,
        variableName,
        startTimestamp,
        endTimestamp);
      
      for (int i = 0; i < rad.length; i++) {

        // Note: RATE doesn't have a meaningful aggregate for a single
        // router aggregated over time -- so we don't calculate one.
        rad[i].minval  /= MEBI;
        rad[i].maxval  /= MEBI;
        rad[i].average /= MEBI;

      } // for i

      return rad;

    }

    //
    // Direct extraction of non-derived data.
    //
    
    return 
      this.getRouterAggregateDataDirect(
        level,
        routerId,
        variableName,
        startTimestamp,
        endTimestamp);
    
    
  } //  getRouterAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from ROUTER aggregate tables for a particular ROUTER and variable.
   * Can  only retrieve actual variables that exist in the database -- does not
   * handle calculated variables.
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param routerId ROUTER_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        ROUTER_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  private AggregateData[] getRouterAggregateDataDirect(
    final int level,
    final int routerId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "routerId = " + routerId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.routerAggregateHourDataQuery;
        break;

      case DAY:
        preparedStatement = this.routerAggregateDayDataQuery;
        break;

      case WEEK:
        preparedStatement = this.routerAggregateWeekDataQuery;
        break;

      case MONTH:
        preparedStatement = this.routerAggregateMonthDataQuery;
        break;

      case YEAR:
        preparedStatement = this.routerAggregateYearDataQuery;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = "BYTES".equals(variableName);
    
    preparedStatement.setInt(1, routerId);
    preparedStatement.setString(2, variableName);
    preparedStatement.setTimestamp(3, startTimestamp);
    preparedStatement.setTimestamp(4, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {

      final AggregateData rad = new AggregateData();

      rad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        rad.hasAggregate  = true;
        rad.aggregate  = resultSet.getFloat(2);
      }

      rad.minval     = resultSet.getFloat(3);
      rad.maxval     = resultSet.getFloat(4);
      rad.average    = resultSet.getFloat(5);
      rad.numSamples = resultSet.getLong(6);

      arrayList.add(rad);

    } // while

    resultSet.close();

    return (AggregateData[])
      arrayList.toArray(new AggregateData[arrayList.size()]);

  } //  getRouterAggregateDataDirect

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from ROUTER aggregate tables for a particular ROUTER group and
   * variable.  RATE data will be scaled to produce output in mebibytes.  All
   * other variable data is returned as is from the database.
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param routerGroupId ROUTER_GROUP_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        ROUTER_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  public AggregateData[] getRouterGroupAggregateData(
    final int level,
    final int routerGroupId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {


    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "routerGroupId = " + routerGroupId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }


    if (level == Database.RAW) {
      throw new Error("RAW level not supported");
    }

    // Get ids for all routers in the given group.
    int[] ids = this.getRouterIdsForGroup(routerGroupId);
    
    // Array to hold timesequence data for all routers.
    float data[][] = new float[ids.length][];

    Timestamp times[] = null;


    // It's possible that some routers will return more data than others
    // because data might have been added between queries for the various
    // routers.  We need to keep track of the smallest number of values
    // returned by any router.
    int minIndex = Integer.MAX_VALUE;

    for (int i = 0; i < ids.length; i++) {
      
      AggregateData[] rad = getRouterAggregateData(
        level,
        ids[i],
        variableName,
        startTimestamp,
        endTimestamp);
    

      minIndex = Math.min(minIndex, rad.length);


      if (i == ids.length - 1) {
        // Last iteration -- we know what final value of minIndex will be.
        times = new Timestamp[minIndex];
        for (int j = 0; j < minIndex; j++) {
          times[j] = rad[j].timestamp;
        } // for j
      }

      data[i] = new float[rad.length];

      if ("BYTES".equals(variableName)) {

        for (int j = 0; j < rad.length; j++) {
          data[i][j] = rad[j].aggregate;
        } //  for j

      }
      else if ("RATE".equals(variableName) || "PCT_CPU".equals(variableName)) {

        for (int j = 0; j < rad.length; j++) {
          data[i][j] = rad[j].average;
        } //  for j

      }
      else {
        throw new Error("Unknown variable name " + variableName);
      }
  
    } // for i
    

    //
    // Return data only for timestamps where we have data from 
    // all routers.
    //

    AggregateData returnVal[] = new AggregateData[minIndex];
    
    for (int j = 0; j < minIndex; j++) {

      float sum = 0.0f;
      float min = Float.MAX_VALUE;
      float max = Float.MIN_VALUE;

      for (int i = 0; i < ids.length; i++) {

        sum += data[i][j];
        min = Math.min(min, data[i][j]);
        max = Math.max(max, data[i][j]);

      } // for i

      returnVal[j] = new AggregateData();
      returnVal[j].aggregate = sum;
      returnVal[j].hasAggregate = true;
      returnVal[j].minval = min;
      returnVal[j].maxval = max;
      returnVal[j].average = sum/((float) ids.length);
      returnVal[j].numSamples = ids.length;
      returnVal[j].timestamp = times[j];


    } // for j
    
    
    return returnVal;
    
  } // getRouterGroupAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from MDS_DATA as if it were aggregate data.  Will calculate 
   * variables that exist in MDS_VARIABLE_INFO but not appear in MDS_DATA.
   *
   * @param mdsId MDS_ID to return data for.
   * @param variableName name of variable to return data for.
   *        MDS_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   */

  private AggregateData[] getMdsDataAsAggregate(
    final int mdsId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "mdsId = " + mdsId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    AggregateData[] mad = null;

    boolean kbytes = false;

    if ((kbytes = "PCT_KBYTES".equals(variableName)) || "PCT_INODES".equals(variableName)) {
      
      if (debug) System.err.println(Debug.tag() + "calc percent values");

      //
      // Derive percent values
      //

      String var0 = null;
      String var1 = null;

      if (kbytes) {
        var0 = "KBYTES_USED";
        var1 = "KBYTES_FREE";
      }
      else {
        var0 = "INODES_USED";
        var1 = "INODES_FREE";
      }
      
      final Object[] vars = getMdsTimeSequenceData2(
        mdsId,
        var0,
        var1,
        startTimestamp,
        endTimestamp);
      
      final TimeSequenceData[] used = (TimeSequenceData[]) vars[0];
      final TimeSequenceData[] free = (TimeSequenceData[]) vars[1];
      
      mad = new AggregateData[used.length];

      Database.aggregatePercentUsedCalc(mad, used, free);
      
    }
    else {

      if (debug)  System.err.println(Debug.tag() + "process non-derived values");

      //
      // For non-derived variables, we just package things up and send them off.
      //

      final TimeSequenceData[] tsd = getMdsTimeSequenceData(
        mdsId,
        variableName,
        startTimestamp,
        endTimestamp);
    
      mad = new AggregateData[tsd.length];
    
      Database.aggregateFill(mad, tsd);

    }
    
    return mad;    
    
  } // getMdsDataAsAggregate

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get a single variable from MDS_DATA for a specific time interval.
   *
   * @param mdsId MDS_ID to return data for.
   * @param variableName name of variable to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   * @return array of TimeSequenceData objects.
   */

  private TimeSequenceData[] getMdsTimeSequenceData(
    final int mdsId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    final PreparedStatement ps = this.connection.prepareStatement(
      "select TIMESTAMP," + variableName + " from MDS_DATA,TIMESTAMP_INFO " +
        "where MDS_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and MDS_ID=?" +
      " and TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");
    
    ps.setInt(1, mdsId);
    ps.setTimestamp(2, startTimestamp);
    ps.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = ps.executeQuery();
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {
      
      final TimeSequenceData tsd = new TimeSequenceData();

      tsd.timestamp = resultSet.getTimestamp(1);
      tsd.value = resultSet.getFloat(2);

      arrayList.add(tsd);

    } // while

    resultSet.close();
    ps.close();

    return (TimeSequenceData[])
      arrayList.toArray(new TimeSequenceData[arrayList.size()]);

  } // getMdsTimeSequenceData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get two variables from MDS_DATA for a specific time interval.
   * Done in a single query so that time sequence will match.  Mainly used to
   * get USED and FREE values at the same time for percent-used calculations.
   *
   * @param mdsId MDS_ID to return data for.
   * @param variableName0 name of first variable to return data for.
   * @param variableName1 name of second variable to return data for.
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   * @return two element array of Object corresponding to the two requested 
   *         variables.  Each element contains an array of TimeSequenceData
   *         objects.
   */

  private Object[] getMdsTimeSequenceData2(
    final int mdsId,
    final String variableName0,
    final String variableName1,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "mdsId = " + mdsId);
      System.err.println(Debug.tag() + "variableName0 = " + variableName0);
      System.err.println(Debug.tag() + "variableName1 = " + variableName1);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    final PreparedStatement ps = this.connection.prepareStatement(
      "select TIMESTAMP," + variableName0 + "," + variableName1 +
      " from MDS_DATA,TIMESTAMP_INFO " +
        "where MDS_DATA.TS_ID=TIMESTAMP_INFO.TS_ID and MDS_ID=?" +
      " and TIMESTAMP>=? and TIMESTAMP<=? order by TIMESTAMP");
    
    ps.setInt(1, mdsId);
    ps.setTimestamp(2, startTimestamp);
    ps.setTimestamp(3, endTimestamp);

    final ResultSet resultSet = ps.executeQuery();

    final ArrayList arrayList0 = new ArrayList();
    final ArrayList arrayList1 = new ArrayList();

    while (resultSet.next()) {
      
      final TimeSequenceData tsd0 = new TimeSequenceData();
      final TimeSequenceData tsd1 = new TimeSequenceData();

      final Timestamp timestamp = resultSet.getTimestamp(1);

      tsd0.timestamp = timestamp;
      tsd0.value = resultSet.getFloat(2);

      tsd1.timestamp = timestamp;
      tsd1.value = resultSet.getFloat(3);

      arrayList0.add(tsd0);
      arrayList1.add(tsd1);

    } // while

    resultSet.close();
    ps.close();

    Object[] returnVal = new Object[2];

    returnVal[0] = (TimeSequenceData[])
      arrayList0.toArray(new TimeSequenceData[arrayList0.size()]);

    returnVal[1] = (TimeSequenceData[])
      arrayList1.toArray(new TimeSequenceData[arrayList1.size()]);

    return returnVal;

  } // getMdsTimeSequenceData2

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from MDS aggregate tables for a particular MDS and variable.  Will
   * generate PCT_KBYTES and PCT_INODES variable data from USED and FREE 
   * variables stored in database.  All other variable data is returned as is
   * from the database.
   *
   * @param level aggregation level (RAW, HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param mdsId MDS_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        MDS_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  public AggregateData[] getMdsAggregateData(
    final int level,
    final int mdsId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {


    if (level == Database.RAW) {

      //
      //  Special case of raw data.
      //

      return getMdsDataAsAggregate(
        mdsId,
        variableName,
        startTimestamp,
        endTimestamp);

    }


    String freeVar = null;
    String usedVar = null;

    if ("PCT_KBYTES".equals(variableName)) {
      
      freeVar = "KBYTES_FREE";
      usedVar = "KBYTES_USED";

    }
    else if ("PCT_INODES".equals(variableName)) {

      freeVar = "INODES_FREE";
      usedVar = "INODES_USED";

    }
    else {

      //
      // Direct extraction of non-derived data.
      //

      return 
        this.getMdsAggregateDataDirect(
          level,
          mdsId,
          variableName,
          startTimestamp,
          endTimestamp);

    }

    //
    // PCT_KBYTES and PCT_INODES are not stored in the database and need to
    // be calculated on the fly from USED and FREE values.
    //

    final Object[] vars =  this.getMdsAggregateDataDirect2(
      level,
      mdsId,
      usedVar,
      freeVar,
      startTimestamp,
      endTimestamp);

    final AggregateData[] used = (AggregateData[]) vars[0];
    final AggregateData[] free = (AggregateData[]) vars[1];

    AggregateData[] pct =  new AggregateData[free.length];

    for (int i = 0; i < free.length; i++) {

      pct[i] = new AggregateData();
      pct[i].timestamp  = free[i].timestamp;
      pct[i].numSamples = free[i].numSamples;

      //
      // This calc will be incorrect if the size of the disk space has changed
      // over the sample interval.  We assume this happens very infrequently and 
      // we will just live with it if it happens.
      //
      pct[i].average = Database.percentUsed(used[i].average, free[i].average);

      //
      // Assume that 'used' is at maximum when 'free' is at minimum, and
      // vice versa.
      //
      pct[i].minval = Database.percentUsed(used[i].minval, free[i].maxval); 
      pct[i].maxval = Database.percentUsed(used[i].maxval, free[i].minval); 

    } // for i

    return pct;

  } //  getMdsAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from MDS aggregate tables for a particular MDS and variable.  Can 
   * only retrieve actual variables that exist in the database -- does not
   * handle calculated variables.
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param mdsId MDS_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        MDS_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  private AggregateData[] getMdsAggregateDataDirect(
    final int level,
    final int mdsId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "mdsId = " + mdsId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.mdsAggregateHourDataQuery;
        break;

      case DAY:
        preparedStatement = this.mdsAggregateDayDataQuery;
        break;

      case WEEK:
        preparedStatement = this.mdsAggregateWeekDataQuery;
        break;

      case MONTH:
        preparedStatement = this.mdsAggregateMonthDataQuery;
        break;

      case YEAR:
        preparedStatement = this.mdsAggregateYearDataQuery;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName) ||
      "WRITE_BYTES".equals(variableName);
    
    preparedStatement.setInt(1, mdsId);
    preparedStatement.setString(2, variableName);
    preparedStatement.setTimestamp(3, startTimestamp);
    preparedStatement.setTimestamp(4, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {

      final AggregateData oad = new AggregateData();

      oad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        oad.hasAggregate  = true;
        oad.aggregate  = resultSet.getFloat(2);
      }

      oad.minval     = resultSet.getFloat(3);
      oad.maxval     = resultSet.getFloat(4);
      oad.average    = resultSet.getFloat(5);
      oad.numSamples = resultSet.getLong(6);

      arrayList.add(oad);

    } // while

    resultSet.close();

    return (AggregateData[])
      arrayList.toArray(new AggregateData[arrayList.size()]);

  } //  getMdsAggregateDataDirect

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from MDS aggregate tables for two variables.  Ensures that both 
   * variables are returned with the same time sequence (as might not
   * happen if two separate queries were used).  Can  only retrieve actual
   * variables that exist in the database -- does not handle calculated
   * variables.
   *
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param mdsId MDS_ID to return data for.
   * @param variableName0 name of first variable to return data for (see
   *        MDS_VARIABLE_INFO table).
   * @param variableName1 name of second variable to return data for (see
   *        MDS_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return Object array containing two arrays of AggregateData.
   */

  private Object[] getMdsAggregateDataDirect2(
    final int level,
    final int mdsId,
    final String variableName0,
    final String variableName1,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "mdsId = " + mdsId);
      System.err.println(Debug.tag() + "variableName0 = " + variableName0);
      System.err.println(Debug.tag() + "variableName1 = " + variableName1);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.mdsAggregateHourDataQuery2;
        break;

      case DAY:
        preparedStatement = this.mdsAggregateDayDataQuery2;
        break;

      case WEEK:
        preparedStatement = this.mdsAggregateWeekDataQuery2;
        break;

      case MONTH:
        preparedStatement = this.mdsAggregateMonthDataQuery2;
        break;

      case YEAR:
        preparedStatement = this.mdsAggregateYearDataQuery2;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName0) ||
      "WRITE_BYTES".equals(variableName0);
    
    preparedStatement.setInt(1, mdsId);
    preparedStatement.setString(2, variableName0);
    preparedStatement.setString(3, variableName1);
    preparedStatement.setTimestamp(4, startTimestamp);
    preparedStatement.setTimestamp(5, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList0 = new ArrayList();
    final ArrayList arrayList1 = new ArrayList();

    while (resultSet.next()) {

      final AggregateData oad = new AggregateData();

      oad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        oad.hasAggregate  = true;
        oad.aggregate  = resultSet.getFloat(2);
      }
      oad.minval     = resultSet.getFloat(3);
      oad.maxval     = resultSet.getFloat(4);
      oad.average    = resultSet.getFloat(5);
      oad.numSamples = resultSet.getLong(6);
      final String variableName = resultSet.getString(7);

      if (variableName0.equals(variableName)) {
        arrayList0.add(oad);
      }
      else {
        arrayList1.add(oad);
      }

    } // while

    resultSet.close();

    Object[] returnVal = new Object[2];

    returnVal[0] = (AggregateData[])
      arrayList0.toArray(new AggregateData[arrayList0.size()]);
    
    returnVal[1] = (AggregateData[])
      arrayList1.toArray(new AggregateData[arrayList1.size()]);

    return returnVal;

  } //  getMdsAggregateDataDirect2

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from filesystem aggregate tables for a particular filesystem and
   * variable.  Will generate PCT_KBYTES and PCT_INODES variable data from USED
   * and FREE  variables stored in database.  All other variable data is
   * returned as is
   * from the database.
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param filesystemId FILESYSTEM_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of AggregateData
   */

  public AggregateData[] getFilesystemAggregateData(
    final int level,
    final int filesystemId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {


    String freeVar = null;
    String usedVar = null;

    if ("PCT_KBYTES".equals(variableName)) {
      
      freeVar = "KBYTES_FREE";
      usedVar = "KBYTES_USED";

    }
    else if ("PCT_INODES".equals(variableName)) {

      freeVar = "INODES_FREE";
      usedVar = "INODES_USED";

    }
    else {

      //
      // Direct extraction of non-derived data.
      //

      return 
        this.getFilesystemAggregateDataDirect(
          level,
          filesystemId,
          variableName,
          startTimestamp,
          endTimestamp);

    }

    //
    // PCT_KBYTES and PCT_INODES are not stored in the database and need to
    // be calculated on the fly from USED and FREE values.
    //

    final Object[] vars =  this.getFilesystemAggregateDataDirect2(
      level,
      filesystemId,
      usedVar,
      freeVar,
      startTimestamp,
      endTimestamp);

    final AggregateData[] used = (AggregateData[]) vars[0];
    final AggregateData[] free = (AggregateData[]) vars[1];

    AggregateData[] pct =  new AggregateData[free.length];

    for (int i = 0; i < free.length; i++) {

      pct[i] = new AggregateData();
      pct[i].timestamp  = free[i].timestamp;

      //
      // This calc will be incorrect if the size of the disk space has changed
      // over the sample interval.  We assume this happens very infrequently and 
      // we will just live with it if it happens.
      //
      pct[i].average = Database.percentUsed(
        used[i].average,
        free[i].average);

      //
      // Assume that 'used' is at maximum when 'free' is at minimum, and
      // vice versa.
      //
      pct[i].minval = Database.percentUsed(
        used[i].minval,
        free[i].maxval); 

      pct[i].maxval = Database.percentUsed(
        used[i].maxval,
        free[i].minval); 

    } // for i

    return pct;

  } //  getFilesystemAggregateData

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from filesystem aggregate tables for a particular filesystem and
   * variable.  Can  only retrieve actual variables that exist in the database
   * -- does not handle calculated variables (i.e., percents and rates).
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param filesystemId FILESYSTEM_ID to return data for.
   * @param variableName name of variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return array of FilesystemAggregateData
   */

  private AggregateData[] getFilesystemAggregateDataDirect(
    final int level,
    final int filesystemId,
    final String variableName,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "filesystemId = " + filesystemId);
      System.err.println(Debug.tag() + "variableName = " + variableName);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.filesystemAggregateHourDataQuery;
        break;

      case DAY:
        preparedStatement = this.filesystemAggregateDayDataQuery;
        break;

      case WEEK:
        preparedStatement = this.filesystemAggregateWeekDataQuery;
        break;

      case MONTH:
        preparedStatement = this.filesystemAggregateMonthDataQuery;
        break;

      case YEAR:
        preparedStatement = this.filesystemAggregateYearDataQuery;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName) ||
      "WRITE_BYTES".equals(variableName);
    
    preparedStatement.setInt(1, filesystemId);
    preparedStatement.setString(2, variableName);
    preparedStatement.setTimestamp(3, startTimestamp);
    preparedStatement.setTimestamp(4, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList = new ArrayList();

    while (resultSet.next()) {

      final AggregateData fad = new AggregateData();

      fad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        fad.hasAggregate  = true;
        fad.aggregate  = resultSet.getFloat(2);
      }

      fad.minval     = resultSet.getFloat(3);
      fad.maxval     = resultSet.getFloat(4);
      fad.average    = resultSet.getFloat(5);

      arrayList.add(fad);

    } // while

    resultSet.close();

    return (AggregateData[])
      arrayList.toArray(new AggregateData[arrayList.size()]);

  } //  getFilesystemAggregateDataDirect

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get data from filesystem aggregate tables for two variables.  Ensures that
   * both  variables are returned with the same time sequence (as might not
   * happen if two separate queries were used).  Can  only retrieve actual
   * variables that exist in the database -- does not handle calculated
   * variables (i.e., percents and rates).
   *
   *
   * @param level aggregation level (HOUR, DAY, WEEK, MONTH, or YEAR).
   * @param filesystemId FILESYSTEM_ID to return data for.
   * @param variableName0 name of first variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param variableName1 name of second variable to return data for (see
   *        OST_VARIABLE_INFO table).
   * @param startTimestamp return data starting at this time.
   * @param endTimestamp return data ending at this time.
   *
   * @return Object array containing two arrays of FilesystemAggregateData.
   */

  private Object[] getFilesystemAggregateDataDirect2(
    final int level,
    final int filesystemId,
    final String variableName0,
    final String variableName1,
    final Timestamp startTimestamp,
    final Timestamp endTimestamp)
  throws 
    Exception
  {

    if (debug) {
      System.err.println(Debug.tag() + "level = " + level);
      System.err.println(Debug.tag() + "filesystemId = " + filesystemId);
      System.err.println(Debug.tag() + "variableName0 = " + variableName0);
      System.err.println(Debug.tag() + "variableName1 = " + variableName1);
      System.err.println(Debug.tag() + "startTimestamp = " + startTimestamp);
      System.err.println(Debug.tag() + "endTimestamp = " + endTimestamp);
    }

    PreparedStatement preparedStatement = null;

    switch (level) {

      case HOUR:
        preparedStatement = this.filesystemAggregateHourDataQuery2;
        break;

      case DAY:
        preparedStatement = this.filesystemAggregateDayDataQuery2;
        break;

      case WEEK:
        preparedStatement = this.filesystemAggregateWeekDataQuery2;
        break;

      case MONTH:
        preparedStatement = this.filesystemAggregateMonthDataQuery2;
        break;

      case YEAR:
        preparedStatement = this.filesystemAggregateYearDataQuery2;
        break;

      default: throw new Error("Illegal level " + level);

    } // switch

    final boolean hasAggregate = 
      "READ_BYTES".equals(variableName0) ||
      "WRITE_BYTES".equals(variableName0);
    
    preparedStatement.setInt(1, filesystemId);
    preparedStatement.setString(2, variableName0);
    preparedStatement.setString(3, variableName1);
    preparedStatement.setTimestamp(4, startTimestamp);
    preparedStatement.setTimestamp(5, endTimestamp);
    final ResultSet resultSet =  preparedStatement.executeQuery();
    
    final ArrayList arrayList0 = new ArrayList();
    final ArrayList arrayList1 = new ArrayList();

    while (resultSet.next()) {

      final AggregateData fad = new AggregateData();

      fad.timestamp  = resultSet.getTimestamp(1);

      if (hasAggregate) {
        fad.hasAggregate  = true;
        fad.aggregate  = resultSet.getFloat(2);
      }
      fad.minval     = resultSet.getFloat(3);
      fad.maxval     = resultSet.getFloat(4);
      fad.average    = resultSet.getFloat(5);

      final String variableName = resultSet.getString(6);

      if (variableName0.equals(variableName)) {
        arrayList0.add(fad);
      }
      else {
        arrayList1.add(fad);
      }

    } // while

    resultSet.close();

    Object[] returnVal = new Object[2];

    returnVal[0] = (AggregateData[])
      arrayList0.toArray(new AggregateData[arrayList0.size()]);
    
    returnVal[1] = (AggregateData[])
      arrayList1.toArray(new AggregateData[arrayList1.size()]);

    return returnVal;

  } //  getFilesystemAggregateDataDirect2

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get variable info for a particular variable in a particular table.
   *
   * @param tableName name of table containing the variable info, e.g. 
   *        "OST_VARIABLE_INFO".
   * @param variableName name of the variable to return info for, e.g. 
   *        "PCT_CPU".
   * @return VariableInfo object containing the requested information.
   */

  public VariableInfo getVariableInfo(
    final String tableName,
    final String variableName)
  throws Exception
  {
  
    final Statement statement = this.connection.createStatement();
    
    final ResultSet resultSet =  statement.executeQuery(
      "select " +
      "VARIABLE_ID," +
      "VARIABLE_NAME," +
      "VARIABLE_LABEL," +
      "THRESH_TYPE," +
      "THRESH_VAL1," +
      "THRESH_VAL2 " +
      "from " +
      tableName + 
      " where VARIABLE_NAME = '" +
      variableName + "'");

    try {
      
      resultSet.next();

      final VariableInfo vi = new VariableInfo();
      
      vi.variableId    = resultSet.getInt(1);
      vi.variableName  = resultSet.getString(2);
      vi.variableLabel = resultSet.getString(3);
      vi.threshType    = resultSet.getInt(4);
      vi.threshVal1    = resultSet.getFloat(5);
      vi.threshVal2    = resultSet.getFloat(6);

      return vi;
      
    }
    finally {
      resultSet.close();
      statement.close();
    }

  } // getVariableInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get all variable info for a particular table.
   *
   * @param tableName name of table containing the variable info, e.g. 
   *        "OST_VARIABLE_INFO".
   * @return VariableInfo object array containing the requested information.
   */

  public VariableInfo[] getVariableInfo(
    final String tableName)
  throws Exception
  {
  
    final Statement statement = this.connection.createStatement();
    
    final ResultSet resultSet = statement.executeQuery(
      "select " +
      "VARIABLE_ID," +
      "VARIABLE_NAME," +
      "VARIABLE_LABEL," +
      "THRESH_TYPE," +
      "THRESH_VAL1," +
      "THRESH_VAL2 " +
      "from " +
      tableName);

    final ArrayList arrayList = new ArrayList();

    try {

      while (resultSet.next()) {
        
        final VariableInfo vi = new VariableInfo();
        
        vi.variableId    = resultSet.getInt(1);
        vi.variableName  = resultSet.getString(2);
        vi.variableLabel = resultSet.getString(3);
        vi.threshType    = resultSet.getInt(4);
        vi.threshVal1    = resultSet.getFloat(5);
        vi.threshVal2    = resultSet.getFloat(6);
        
        arrayList.add(vi);

      }
    }
    finally {
      resultSet.close();
      statement.close();
    }

    return 
      (VariableInfo[]) arrayList.toArray(new VariableInfo[arrayList.size()]);


  } // getVariableInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Close database connection.
   */

  public void close()
  throws Exception
  {

    this.connection.close();

  } // close

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Return name for given SQL type.
   *
   * @param type SQL type constant from java.sql.Types.
   * @return String name for type.
   */

  public static String getSqlTypeName(final int type)
  {
    
    switch (type) {

      case Types.ARRAY:          return "ARRAY";
      case Types.BIGINT:         return "BIGINT";
      case Types.BINARY:         return "BINARY";
      case Types.BIT:            return "BIT";
      case Types.BLOB:           return "BLOB";
      case Types.BOOLEAN:        return "BOOLEAN";
      case Types.CHAR:           return "CHAR";
      case Types.CLOB:           return "CLOB";
      case Types.DATALINK:       return "DATALINK";
      case Types.DATE:           return "DATE";
      case Types.DECIMAL:        return "DECIMAL";
      case Types.DISTINCT:       return "DISTINCT";
      case Types.DOUBLE:         return "DOUBLE";
      case Types.FLOAT:          return "FLOAT";
      case Types.INTEGER:        return "INTEGER";
      case Types.JAVA_OBJECT:    return "JAVA_OBJECT";
      case Types.LONGVARBINARY:  return "LONGVARBINARY";
      case Types.LONGVARCHAR:    return "LONGVARCHAR";
      case Types.NULL:           return "NULL";
      case Types.NUMERIC:        return "NUMERIC";
      case Types.OTHER:          return "OTHER";
      case Types.REAL:           return "REAL";
      case Types.REF:            return "REF";
      case Types.SMALLINT:       return "SMALLINT";
      case Types.STRUCT:         return "STRUCT";
      case Types.TIME:           return "TIME";
      case Types.TIMESTAMP:      return "TIMESTAMP";
      case Types.TINYINT:        return "TINYINT";
      case Types.VARBINARY:      return "VARBINARY";
      case Types.VARCHAR:        return "VARCHAR";
      default:                   return "Unknown";

    } // switch
      
  } // getSqlTypeName
    
  //////////////////////////////////////////////////////////////////////////////

  private static void describeResultSet(final ResultSet resultSet)
  throws Exception
  {

    final ResultSetMetaData rsmd = resultSet.getMetaData();

    for (int i = 1; i <= rsmd.getColumnCount(); i++) {

      System.err.println(rsmd.getColumnName(i) + ": " +
        getSqlTypeName(rsmd.getColumnType(i)));

    } //  for i

  } // describeResultSet

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Return filesystem info, loading it from lmtrc file if necessary.
   *
   * @return filesystem info as array of FilesystemInfo objects.
   */

  public static FilesystemInfo[] getAllFilesystemInfo()
    throws Exception
  {
    
    if (Database.allFilesystemInfo == null) {

      Database.loadFilesystemInfo();

    }

    return Database.allFilesystemInfo;

  } // getAllFilesystemInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * @deprecated
   * Get information from the OST_INFO table.
   *
   * @return array of OstInfo instances.
   */

  public  OstInfo[] getOstInfo()
  throws Exception
  {

    final Statement statement = this.connection.createStatement();

    final ResultSet resultSet =  statement.executeQuery(
      "select " +
      "OST_ID," +
      "OSS_INFO.FAILOVERHOST," +
      "DEVICE_NAME," +
      "OST_INFO.HOSTNAME," +
      "OFFLINE," +
      "OST_INFO.OSS_ID," +
      "OST_NAME " +
      "from OST_INFO,OSS_INFO " +
      "where OST_INFO.OSS_ID=OSS_INFO.OSS_ID " +
      "order by OST_ID");

    final ArrayList list = new ArrayList();

    try {

      while (resultSet.next()) {

        final OstInfo oi = new OstInfo();
        int index = 1;

        oi.ostId        = resultSet.getInt(index++);
        oi.failoverhost = resultSet.getString(index++);
        oi.deviceName   = resultSet.getString(index++);
        oi.hostname     = resultSet.getString(index++);
        oi.offline      = resultSet.getBoolean(index++);
        oi.ossId        = resultSet.getInt(index++);
        oi.ostName      = resultSet.getString(index++);

        list.add(oi);

      } // while

    }
    finally {
      resultSet.close();
      statement.close();
    }

    return (OstInfo[]) list.toArray(new OstInfo[list.size()]);

  } // getOstInfo

  //////////////////////////////////////////////////////////////////////////////
  
  /**
   * Get information from the OST_INFO table.
   *
   * @return OstInfoHolder object.
   */

  public  OstInfoHolder getOstInfoHolder()
  throws Exception
  {

    final Statement statement = this.connection.createStatement();

    final ResultSet resultSet =  statement.executeQuery(
      "select " +
      "OST_ID," +
      "OSS_INFO.FAILOVERHOST," +
      "DEVICE_NAME," +
      "OST_INFO.HOSTNAME," +
      "OFFLINE," +
      "OST_INFO.OSS_ID," +
      "OST_NAME " +
      "from OST_INFO,OSS_INFO " +
      "where OST_INFO.OSS_ID=OSS_INFO.OSS_ID " +
      "order by OST_ID");

    final ArrayList list = new ArrayList();

    try {

      while (resultSet.next()) {

        final OstInfo oi = new OstInfo();
        int index = 1;

        oi.ostId        = resultSet.getInt(index++);
        oi.failoverhost = resultSet.getString(index++);
        oi.deviceName   = resultSet.getString(index++);
        oi.hostname     = resultSet.getString(index++);
        oi.offline      = resultSet.getBoolean(index++);
        oi.ossId        = resultSet.getInt(index++);
        oi.ostName      = resultSet.getString(index++);

        list.add(oi);

      } // while

    }
    finally {
      resultSet.close();
      statement.close();
    }

    final OstInfo[] ostInfo = (OstInfo[]) list.toArray(new OstInfo[list.size()]);

    return new OstInfoHolder(ostInfo);

  } // getOstInfoHolder

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get information from the OSS_INFO table.
   *
   * @return array of OstInfo instances.
   */

  public  OssInfo[] getOssInfo()
  throws Exception
  {

    final Statement statement = this.connection.createStatement();

    final ResultSet resultSet =  statement.executeQuery(
      "select " +
      "OSS_ID," +
      "FILESYSTEM_ID," +
      "HOSTNAME," +
      "FAILOVERHOST from OSS_INFO order by OSS_ID");

    final ArrayList list = new ArrayList();

    try {

      while (resultSet.next()) {

        final OssInfo oi = new OssInfo();
        int index = 1;

        oi.ossId        = resultSet.getInt(index++);
        oi.filesystemId = resultSet.getInt(index++);
        oi.hostname     = resultSet.getString(index++);
        oi.failoverhost = resultSet.getString(index++);

        list.add(oi);

      } // while

    }
    finally {
      resultSet.close();
      statement.close();
    }

    return (OssInfo[]) list.toArray(new OssInfo[list.size()]);

  } // getOssInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get all information from the OSS_INFO table.
   *
   * @return OstInfoHolder object.
   */

  public  OssInfoHolder getOssInfoHolder()
  throws Exception
  {

    final Statement statement = this.connection.createStatement();

    final ResultSet resultSet =  statement.executeQuery(
      "select " +
      "OSS_ID," +
      "FILESYSTEM_ID," +
      "HOSTNAME," +
      "FAILOVERHOST from OSS_INFO order by OSS_ID");

    final ArrayList list = new ArrayList();

    try {

      while (resultSet.next()) {

        final OssInfo oi = new OssInfo();
        int index = 1;

        oi.ossId        = resultSet.getInt(index++);
        oi.filesystemId = resultSet.getInt(index++);
        oi.hostname     = resultSet.getString(index++);
        oi.failoverhost = resultSet.getString(index++);

        list.add(oi);

      } // while

    }
    finally {
      resultSet.close();
      statement.close();
    }

    final OssInfo[] ossInfo =  (OssInfo[]) list.toArray(new OssInfo[list.size()]);

    return new OssInfoHolder(ossInfo);

  } // getOssInfoHolder

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate percent used value from used and free values.
   *
   * @param used amount of resource in use.
   * @param free amount of resourse not in use.
   * @return percent of resource in use, or null if either of the arguments
   *         is null.
   */

  public static Float percentUsed(
    final Long used,
    final Long free)
  {
    
    Float value = null;
    if (used != null && free !=null) {

      final float usedf = (float) used.longValue();

      if (usedf == 0.0f) {
        return new Float(0.0f);
      }

      final float freef = (float) free.longValue();

      value = new Float(100.0*(usedf/(usedf + freef)));
    }
    
    return value;

  } // percent

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate percent used value from used and free values.
   *
   * @param used amount of resource in use.
   * @param free amount of resourse not in use.
   * @return percent of resource in use, or null if either of the arguments
   *         is null.
   */

  public static Float percentUsed(
    final Float used,
    final Float free)
  {
    
    Float value = null;
    if (used != null && free !=null) {

      final float usedf = used.floatValue();

      if (usedf == 0.0f) {
        return new Float(0.0f);
      }

      final float freef = free.floatValue();

      value = new Float(100.0*(usedf/(usedf + freef)));
    }
    
    return value;

  } // percent

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate percent used value from used and free values.
   *
   * @param used amount of resource in use.
   * @param free amount of resourse not in use.
   * @return percent of resource in use.
   */

  public static float percentUsed(
    final long used,
    final long free)
  {
    
    return Database.percentUsed((float) used, (float) free);

  } // percent

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate percent used value from used and free values.
   *
   * @param used amount of resource in use.
   * @param free amount of resourse not in use.
   * @return percent of resource in use.
   */

  public static float percentUsed(
    final float used,
    final float free)
  {
    
    if (used == 0.0f) {
      return 0.0f;
    }

    return 100.0f*used/(used + free);

  } // percent

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find available lmtrc file and load content into lmtrcList.
   */

  private static void loadLmtrc()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");


    //
    // Check for system property override.
    //

    String rcFileName = System.getProperty("lmtrc");

    if (rcFileName != null) {

      final File rcFile = new File(rcFileName);
      
      if (rcFile.exists()) {
        Database.loadLmtrcInfo(rcFile, Database.lmtrcList);
        Database.lmtrcPath = rcFile.getPath();
        return;
      }

      throw new Exception(
        "Could not find resource file " +
        rcFile.getPath() + ".");  

    }


    //
    // Read local lmtrc file.
    //

    final String homeDirName = System.getProperty("user.home");
    rcFileName = homeDirName + "/.lmtrc";
    
    final File rcFile = new File(rcFileName);

    if (rcFile.exists()) {
      Database.loadLmtrcInfo(rcFile, Database.lmtrcList);
      Database.lmtrcPath = rcFile.getPath();
      return;
    }


    //
    // Read global lmtrc file.
    //

    final File globalRcFile = new File(Database.GLOBAL_LMTRC_FILE_NAME);

    if (globalRcFile.exists()) {
      Database.loadLmtrcInfo(globalRcFile, Database.lmtrcList);
      Database.lmtrcPath = globalRcFile.getPath();
      return;
    }

    
    throw new Exception(
      "Could find neither global resource file " +
       globalRcFile.getPath() +
      ", nor local resource file " +
       rcFile.getPath() + ".");    

  } // loadLmtrc

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Load filesystem information from lmtrcList into filesystemInfo array.
   */

  private static void loadFilesystemInfo()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");
    
    Database.loadLmtrc();

    final String prefix = "filesys.";

    // Stores mapping between numeric index in lmtrc file and index where
    // object is stored in arrayList.
    final HashMap hashMap = new HashMap();

    // Used to construct Database.filesystemInfo array.
    final ArrayList arrayList = new ArrayList();
    
    for (int i = 0; i < Database.lmtrcList.size(); i++) {

      final KeyValuePair keyValuePair = (KeyValuePair) Database.lmtrcList.get(i);

      final String key = keyValuePair.key;

      if (!key.startsWith(prefix)) continue;

      final String value = keyValuePair.value;

      final int dotIndex = key.lastIndexOf('.');

      // Numeric index from lmtrc file.
      final String index = key.substring(prefix.length(), dotIndex);

      final String field = key.substring(dotIndex + 1);

      FilesystemInfo filesystemInfo = null;
      final Integer location = (Integer) hashMap.get(index);

      try {

        if (location == null) {

          if (debug) System.err.println(Debug.tag() + "add index " + index);
          
          filesystemInfo = new FilesystemInfo();
          //filesystemInfo.filesystemId = Integer.parseInt(index);
                    
          arrayList.add(filesystemInfo);

          // Store mapping between lmtrc index and position in arrayList.
          hashMap.put(index, new Integer(arrayList.size() - 1));

        }
        else {

          filesystemInfo = (FilesystemInfo) arrayList.get(location.intValue());

        }
        
        if ("name".equals(field))           filesystemInfo.filesystemName = value;
        else if ("mountname".equals(field)) filesystemInfo.filesystemMountName = value;
        else if ("dbname".equals(field))    filesystemInfo.lmtDbName = value;
        else if ("dbhost".equals(field))    filesystemInfo.lmtDbHost = value;
        else if ("dbport".equals(field))    filesystemInfo.lmtDbPort = Integer.parseInt(value);
        else if ("dbuser".equals(field))    filesystemInfo.lmtDbUsername = value;
        else if ("dbauth".equals(field))    filesystemInfo.lmtDbAuth = value;
        else {

          throw new Exception(
            "Unexpexted key '" +
            key +
            "' found in " +
            Database.lmtrcPath); 

        }

      }
      catch (NumberFormatException e) {

        throw new Exception(
          "Problem parsing numeric value '" +
          value +
          "' for key '" +
          key +
          "' from " +
          Database.lmtrcPath); 

      }

    } // while

    Database.allFilesystemInfo = (FilesystemInfo[])
      arrayList.toArray(new FilesystemInfo[arrayList.size()]);

    Database.checkFilesystemInfo();

  } // loadFilesystemInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Check that we have all the information we need for each filesystem we
   * know about.
   *
   * @throws Exception if any information is missing.
   */

  private static void checkFilesystemInfo()
  throws Exception
  {

    if (debug) System.err.println(Debug.tag() + "enter");

    final String prefix = "filesys.";

    for (int i = 0; i < Database.allFilesystemInfo.length; i++) {

      final FilesystemInfo filesystemInfo = Database.allFilesystemInfo[i];

      String field = null;

      if (filesystemInfo.filesystemName == null)           field = "name";
      else if (filesystemInfo.filesystemMountName == null) field = "mountname";
      else if (filesystemInfo.lmtDbName == null)           field = "dbname";
      else if (filesystemInfo.lmtDbHost == null)           field = "dbhost";
      else if (filesystemInfo.lmtDbPort < 0)               field = "dbport";
      else if (filesystemInfo.lmtDbUsername == null)       field = "dbuser";
      else if (filesystemInfo.lmtDbAuth == null)           field = "dbauth";

      if (field != null) {
        
        throw new Exception(
          "Missing key '" + prefix + filesystemInfo.filesystemId + "." + field +
          "' from " + Database.lmtrcPath);

      }

    } // for i

  } // checkFilesystemInfo

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Read given file and load content into List.  Comments and blank lines are
   * skipped.
   *
   * @param file lmtrc file
   * @param list List into which content is placed.
   */

  private static void loadLmtrcInfo(
    final File file,
    final List list)
  throws Exception
  {

    final BufferedReader br =
      new BufferedReader(
        new InputStreamReader(
          new FileInputStream(file)));

    int count = 0;

    while (true) {

      final String line = br.readLine();
      if (line == null) break;
      
      count++;

      // Skip comments
      if (line.trim().startsWith("#")) continue;

      final int index = line.indexOf('=');

      // Not a comment or an assignment line.
      if (index < 0) continue;

      if (index == 0) {
        // No key given.
        throw new Exception(
          file.getName() + ": error at line " + count +
          ":  no key given.");
      }

      String value = "";

      if (index < line.length() - 1) {
        // Non-empty value given
        value = line.substring(index + 1);
      }

      final String key = line.substring(0, index).trim();
      
      if (key.length() == 0) {
        // Blank key.
        throw new Exception(
          file.getName() + ": error at line " + count +
          ":  zero length key.");
      }

      list.add(new KeyValuePair(key, value));

    } // while

    br.close();

  } // loadProperties

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate difference between values at consecutive timesteps.  Allows for
   * count reset due to restarting collectors/servers.
   *
   * @param x1 value at timestep n + 1
   * @param x0 value at timestep n
   * @return difference between values
   */

  private static Long diff(
    final Long x1,
    final Long x0)
  {

    if (x1 == null || x0 == null) {
      return null;
    }

    final long xx1 = x1.longValue();
    final long xx0 = x0.longValue();
    
    if (xx1 < xx0) {
      // Collector must have restarted and reset cumulative count to zero.
      return x1;                // treat x0 as being zero.
    }

    return new Long(xx1 - xx0);

  } // diff

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate difference between values at consecutive timesteps.  Allows for
   * count reset due to restarting collectors/servers.
   *
   * @param x1 value at timestep n + 1
   * @param x0 value at timestep n
   * @return difference between values
   */

  private static float diff(
    final float x1,
    final float x0)
  {

    
    if (x1 < x0) {
      // Collector must have restarted and reset cumulative count to zero.
      return x1;                // treat x0 as being zero.
    }

    return x1 - x0;

  } // diff

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate time difference in seconds between two timestamps.
   *
   * @param t1 timestamp for timestep n + 1
   * @param t0 timestamp for timestep n
   * @return time diffence in seconds.
   */

  private static float timeDiff(
    final Timestamp t1,
    final Timestamp t0)
  {
    
    return ((float) (t1.getTime() - t0.getTime()))/1000.0f;

  } //  timeDiff

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate rate for some variable.
   *
   * @param x1 value at timestep n + 1
   * @param x0 value at timestep n
   * @param time difference between timesteps n + 1 and n
   * @param factor intermediate result is divided by this to give final answer 
   */

  private static float rate(
    final float x1,
    final float x0,
    final float timeDelta,
    final float factor)
  {

    if (timeDelta == 0.0f) return 0.0f;

    if (x1 < x0) {
      // Collector must have restarted and reset cumulative count to zero --
      // assume x0 is zero.
      return x1/timeDelta/factor;
    }

    return (x1 - x0)/timeDelta/factor;

  } //  rate

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Calculate rate for some variable.
   *
   * @param x1 value at timestep n + 1
   * @param x0 value at timestep n
   * @param time difference between timesteps n + 1 and n
   * @param factor intermediate result is divided by this to give final answer 
   */

  private static Float rate(
    final Long x1,
    final Long x0,
    final float timeDelta,
    final float factor)
  {

    if (x1 == null || x0 == null) {
      return null;
    }

    final float xx1 = (float) x1.longValue();
    final float xx0 = (float) x0.longValue();

    if (xx1 < xx0) {
      // Collector must have restarted and reset cumulative count to zero.
      // Assume xx0 is zero (or close to it).
      return  new Float(xx1/timeDelta/factor);
    }

    return  new Float((xx1 - xx0)/timeDelta/factor);

  } //  rate

  //////////////////////////////////////////////////////////////////////////////

  private static void aggregateRateCalc(
    AggregateData[] aggregateData,
    TimeSequenceData[] timeSequenceData)
  {

    for (int i = 0; i < aggregateData.length; i++) {
      
      aggregateData[i] = new AggregateData();
      
      final float delta = Database.timeDiff(
        timeSequenceData[i + 1].timestamp,
        timeSequenceData[i].timestamp);
      
      final float value = Database.rate(
        timeSequenceData[i + 1].value,
        timeSequenceData[i].value,
        delta,
        MEBI);
      
      // Note: Rates don't have a meaningful aggregate, so we leave the 
      // hasAggregate field to its default of false.
      aggregateData[i].timestamp    =  timeSequenceData[i + 1].timestamp;
      aggregateData[i].minval       =  value;
      aggregateData[i].maxval       =  value;
      aggregateData[i].average      =  value;
      aggregateData[i].numSamples   = 1;
      
    } // for i
    
  } // aggregateRateCalc

  //////////////////////////////////////////////////////////////////////////////

  private static void aggregateDiffCalc(
    AggregateData[] aggregateData,
    TimeSequenceData[] timeSequenceData)
  {

    for (int i = 0; i < aggregateData.length; i++) {
      
      aggregateData[i] = new AggregateData();
      
      final float delta = Database.diff(
        timeSequenceData[i + 1].value,
        timeSequenceData[i].value);
      
      aggregateData[i].timestamp    = timeSequenceData[i + 1].timestamp;
      aggregateData[i].aggregate    = delta;
      aggregateData[i].minval       = delta;
      aggregateData[i].maxval       = delta;
      aggregateData[i].average      = delta;
      aggregateData[i].numSamples   = 1;
      aggregateData[i].hasAggregate = true;
      
    } // for i
    
  } // aggregateDiffCalc

  //////////////////////////////////////////////////////////////////////////////

  private static void aggregateFill(
    AggregateData[] aggregateData,
    TimeSequenceData[] timeSequenceData)
  {

    for (int i = 0; i < aggregateData.length; i++) {
      
      aggregateData[i] = new AggregateData();
      
      aggregateData[i].timestamp  =  timeSequenceData[i].timestamp;
      aggregateData[i].minval     =  timeSequenceData[i].value;
      aggregateData[i].maxval     =  timeSequenceData[i].value;
      aggregateData[i].average    =  timeSequenceData[i].value;
      aggregateData[i].numSamples = 1;
      
    } // for i
    
  } // aggregateDiffCalc

  //////////////////////////////////////////////////////////////////////////////

  private static void aggregatePercentUsedCalc(
    AggregateData[] aggregateData,
    TimeSequenceData[] used,
    TimeSequenceData[] free)
  {

    for (int i = 0; i < used.length; i++) {
      
      aggregateData[i] = new AggregateData();
      
      final float value = Database.percentUsed(used[i].value, free[i].value);
      
      aggregateData[i].timestamp  =  used[i].timestamp;
      aggregateData[i].minval     =  value;
      aggregateData[i].maxval     =  value;
      aggregateData[i].average    =  value;
      aggregateData[i].numSamples = 1;
      
    } // for i

  } // aggregatePercentUsedCalc

  //////////////////////////////////////////////////////////////////////////////

} // Database


