package gov.llnl.lustre.lwatch;
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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import java.io.*;

import java.util.Vector;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.DateFormat;
import java.lang.StringBuffer;
import java.text.SimpleDateFormat;


import gov.llnl.lustre.lwatch.util.Debug;

// JFreeChart imports
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Day;
import org.jfree.data.time.Week;
import org.jfree.data.time.Month;
import org.jfree.data.time.Year;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.RefineryUtilities;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.LegendItem;
import org.jfree.data.general.SeriesException;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.axis.LogarithmicAxis;

// Database imports
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.*;
import java.util.Locale;

// sql imports
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;

// Timer imports for live update of raw data
import java.util.Timer;
import java.util.TimerTask;


//////////////////////////////////////////////////////////////////////////////

/**
 * Class used to plot historical data for different file system components.
 */

public class PlotFrame2 { //extends JApplet {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("PlotFrame.debug");

    public final static boolean limitedDebug = 
	Boolean.getBoolean("PlotFrameLimited.debug");

    public final static boolean timerOn =
	Boolean.getBoolean("timer");

    private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
    private static final SimpleDateFormat simpleDateFormat = 
	new SimpleDateFormat(dateFormat, Locale.US);

    private JFrame thpFrame = null;
    private PlotFrame2 pf2 = null;

    private JLabel label;
    private JLabel idLabel;
    boolean cancel = true;

    private ControlPanel controls;
    StatControlPanel scPane;
    private VarStatPane vsPane;
    //private StatsPanel vsPane;
    private JPanel statPanel;
    private JPanel statsReportPane;
    private JLabel ammaLabel;
    //private JScrollPane statPanel;
    private String [] cats2Plot = null;
    private String [] vars2Plot = null;
    private String [] crvs2Plot = null;
    //private double [][][] rawData = null;
    private float [][] rawData = null;
    private long [][] rawTimes = null;
    private float [][] rawDataN = null;
    private long [][] rawTimesN = null;
    //private double [][] overviewData = null;
    private int  [] dataPointCount = null;
    private Color [] legendColors = null;
    private String [] yAxisLabs = null;
    private String [] varNames = null;

    private long timeRangeBegin = 0;
    private long timeRangeEnd = 0;

    int nRowsSelected = 0;
    int nColsSelected = 0;
    int nCurvesSelected = 0;
    boolean noVarSelected = true;
    int ovIDX = 0;

    JPanel plotPanel;
    //JPanel chartContainerPane;
    ChartContainerPanel chartContainerPane;
    ChartPanel chartPanel = null;
    JFreeChart chart = null;
    OverView wideView = null;
    GridBagLayout ccgbl;

    GridBagLayout ppgbl;
    GridBagConstraints ppc;

    //private double [] aggVal;
    //private double [] maxVal;
    //private double [] minVal;
    //private double [] avgVal;

    private double aggVal;
    private double maxVal;
    private double minVal;
    private double avgVal;

    private float [] ovRangeMax = null;
    private float [] ovRangeMin = null;

    private boolean useDuration = true;
    private boolean useLogRangeAxis = false;
    private boolean showIcons = false;

    private YearChooser yrChooser;
    private MonthChooser monthChooser;
    private DayChooser dayChooser;
    private HourChooser hourChooser;
    private MinuteChooser minuteChooser;

    private YearChooser yrEndChooser;
    private MonthChooser monthEndChooser;
    private DayChooser dayEndChooser;
    private HourChooser hourEndChooser;
    private MinuteChooser minuteEndChooser;

    private IntegerChooser yrDurChooser;
    private IntegerChooser monthDurChooser;
    private IntegerChooser dayDurChooser;
    private IntegerChooser hourDurChooser;
    private IntegerChooser minuteDurChooser;

    //private DurationChooser durationChooser;
    private ThinningChooser thinningChooser;

    private GranularityChooser granularityChooser;
    
    private FileSystemPanel fileSys = null;
    private String fsName;                      // File System Id
    private Database database;
    private String type = null;
    private int subIdent;
    private String rowID;
    private String colID;
    private Color unselectedBG = new Color(255, 229,174);
    private Color selectedBG = new Color(214, 194,255);

    private PlotDescriptor pD = null;
    private PlotDescriptor lastRefreshPlotParams = null;

    double yLo = 0.0;
    double yHi = 100.0;

    Color [] curveColor = new Color[9];

    String [] mdsPlottableVars = null;
    String [] ostPlottableVars = FileSystemPanel.getPlottableVars("OST");
    String [] ossPlottableVars = FileSystemPanel.getPlottableVars("OSS");
    String [] rtrPlottableVars = FileSystemPanel.getPlottableVars("RTR");
    Object [][] masterData = null;

    private final long SIXMONTHS = (long)86400000 * (long)183;  // in MSec
    private final long ONEYEAR = (long)86400000 * (long)365;  // in MSec
    private final long ONEDAY = (long)86400000;  // in MSec
    private final long HALFDAY = (long)43200000;  // in MSec
    private final long HOUR = (long)3600000;  // in MSec
    private final double [] tRate = {5000.0,        // milliseconds in RAW
				     3600000.0,     // milliseconds in HOUR
				     86400000.0,    // milliseconds in DAY
				     604800000.0,   // milliseconds in WEEK
				     2592000000.0,  // milliseconds in MONTH
				     31536000000.0, // milliseconds in YEAR
				     5000.0};       // milliseconds in HEARTBEAT

    private final long [] div = {(long)5000, HOUR, ONEDAY, ONEDAY * (long)(7),
				 ONEDAY * (long)(30), ONEYEAR, (long)5000};

    // Initial interval and granularity values. Get from prefs or history in the future.
    int intialGranularity = Database.RAW;
    Timestamp inittsEndPlot = new Timestamp(System.currentTimeMillis());
    long inittsPlotInterval = (long)(3600*1*1000);  // 1 hour ago
    Timestamp inittsStartPlot = new Timestamp(inittsEndPlot.getTime() - inittsPlotInterval);

    Timestamp tsStart;
    Timestamp tsEnd;
    //long duration = (long)86400000 * (long)365;  // milliseconds (24 hours * days in a year)
    long duration = (long)(3600*4*1000);  // milliseconds (4 hours)
    Timestamp tsEndPlot = inittsEndPlot;
    Timestamp tsStartPlot = inittsStartPlot;
    Timestamp ovEndPlot = new Timestamp(tsEndPlot.getTime());
    Timestamp ovStartPlot = new Timestamp(tsEndPlot.getTime() - (long)(2 * inittsPlotInterval));
    int granularity = intialGranularity;

    // Flags denoting which curves to plot. Default = Avg.
    boolean loadAggregate = false;
    boolean loadMinimum = false;
    boolean loadMaximum = false;
    boolean loadAverage = true;

    String yAxisLabel = "";

    int startIndex;  // = 0;
    int stopIndex;  // = 23;
    int ovStartIndex;  // = 0;  //9499;
    int ovStopIndex;  // = 23;  //9999;
    int indexMapping = 1;  // each lower plot unit = 5 upper plot units
    int arrayLimit = 0;
    int arrayLimitN = 0;

    // Timing variable declarations
    long dbAccess = 0;
    long loadMethodTot = 0;

    boolean isAnAggPlot = false;

    // Variables used to perform live update of raw data

    //new LiveUpdate = null;
    JLabel liveModLabel = null;
    JLabel rlIntervalLabel = null;
    JLabel rlRateLabel = null;
    ChangeLivePanel changeLiveActivator = null;
    JButton liveModifyButt;


    private final int HEARTBEAT = 7;
    public boolean updateLive = false;
    private boolean initialLoad = true;
    private boolean fromRefresh = true;

    int refreshRate = 15000;  //5000;  // interval in msecs
    private Timer refreshTimer = null;
    private long refreshStopTime;
    private long refreshMaxInactivity = 0;
    private int refreshInterval = 0;
    private long nextRefreshTime;

    long liveDisplayInterval = 2 * HOUR;

    private long lastLiveNtrvlStart = tsStartPlot.getTime();
    private long lastLiveNtrvlEnd = tsEndPlot.getTime();

    Cursor hourglassCursor = new Cursor(Cursor.WAIT_CURSOR);
    Cursor normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);

    JButton cpHideButt = null;
    JButton ovpHideButt = null;

    Dimension lastCPDimension = null;
    Dimension lastOVDimension = null;

    boolean skipThisUpdate = false;

    Prefs prefs;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for the historical plot class.
     */

    public PlotFrame2(JFrame frame,
		      FileSystemPanel fsp,
		      Database db,
		      String type,
		      int subId,
		      String row,
		      String col,
		      Prefs pprefs,
		      boolean isAnAggPlotRequest)
    {
	this.pf2 = this;
	this.thpFrame = frame;
	this.fileSys = fsp;
	this.database = db;  // Should be connected already
	this.fsName = fsp.fsName;
	this.type = type;
	this.subIdent = subId;  // Spec for mdsId & router group. Ignore when type == "OST"
	this.rowID = row;
	this.colID = col;
	this.yAxisLabel = col.replaceAll("\\n"," ");
	this.isAnAggPlot = isAnAggPlotRequest;
	this.prefs = pprefs;

	this.thpFrame.addWindowListener(new WindowH());

	if (localDebug)
	    Debug.out("type = " + type + "\nsubId = " + subId +
		      "\nrow = " + row + "\ncol = " + col +
		      "\novStartPlot = " + ovStartPlot);

	// Apply parameters from preferences

	refreshRate = prefs.liveRefreshRate;
	liveDisplayInterval = prefs.liveDisplayInterval * 60000;  // Converted to MSec.
	granularity = prefs.plotGranularity;
	this.showIcons = prefs.showPlotIcons;
	intialGranularity = granularity;
	if (isAnAggPlot &&
	    ((granularity == Database.RAW) || (granularity == HEARTBEAT))) {
	    LwatchAlert lwAlert = new LwatchAlert(pf2);
	    lwAlert.displayDialog(true,  // modal dialog
				  "RAW & HEARTBEAT granularity not supported for " +
				  "aggregate plots. Using HOUR.",
				  1);  // type  1 = info with "continue
	    granularity = Database.HOUR;
	}

	inittsStartPlot = calcStartPlotInterval(granularity);
	tsStartPlot = inittsStartPlot;

	//Debug.out("Plot granualrity after prefs check = " + granularity);
	//Debug.out("Start plot interval = " + inittsStartPlot);

	if (granularity == HEARTBEAT) {
	    //granularity = Database.RAW;
	    //intialGranularity = Database.RAW;
	    updateLive = true;
	}

	if (localDebug)
	    Debug.out("tsStartPlot -- tsEndPlot = " + tsStartPlot + " -- " + tsEndPlot);

	// Get connection to database
	/****
	if (fsp.getDBConnected()) {
	    database = fsp.getDatabaseClass();
	} else {
	    try {
		fsp.openDBConnection();
	    } catch (Exception e) {
		if (localDebug)
		    Debug.out("Exception caught during openDBConnection.\n" +
			      e.getMessage());
		// Frame disposal is done in the CellListener class where
		// constructor call is made.
		return;
	    }
	}
	****/

	if ("OST".equals(this.type)) {
	    try {

		Database.VariableInfo[] ovi = database.getVariableInfo("OST_VARIABLE_INFO");

		yAxisLabs = new String[ovi.length];
		varNames = new String[ovi.length];
		for (int i = 0; i < ovi.length; i++) {
		    varNames[i] = ovi[i].variableName;
		    yAxisLabs[i] = ovi[i].variableLabel;
		}


		int varId = -1;
		for (int i = 0; i < ostPlottableVars.length; i++) {
		    if (ostPlottableVars[i].equals(this.colID)) {
			if (localDebug)
			    Debug.out(i + " ostPlottableVars[i] = " + ostPlottableVars[i]);

			for (int j = 0; j < ovi.length; j++) {
			    if (ostPlottableVars[i].equals(ovi[j].variableLabel))
				varId = ovi[j].variableId;
			}

			//varId = varMap[i];
			break;
		    }
		}
		if (localDebug)
		    Debug.out("Initial variable Id Match = " + varId);


		if (localDebug) {
		    for (int i = 0; i < ovi.length; i++) {
			System.out.println(ovi[i].variableId);
			System.out.println(ovi[i].variableName);
			System.out.println(ovi[i].variableLabel);
			System.out.println(ovi[i].threshType);
			System.out.println(ovi[i].threshVal1);
			System.out.println(ovi[i].threshVal2);
		    }
		}

		//
		if (varId >= 0)
		    this.colID = ovi[varId-1].variableLabel;

		// Reduce plottableVars by 2 because there's no data for PCT_KBYTES & PCT_INODES
		ostPlottableVars = new String[ovi.length];  //[ovi.length-2];
		for (int i = 0; i < ovi.length; i++) {  //ovi.length-2; i++) { 
		    ostPlottableVars[ovi[i].variableId-1] = ovi[i].variableLabel;
		}
	    
	    } catch (Exception e) {
		if (localDebug)
		    Debug.out("Error getting OstVariableInfo. \n" +
			      e.getMessage());
	    }
	} else if ("RTR".equals(this.type)) {
	    try {
		Database.VariableInfo[] rvi = database.getVariableInfo("ROUTER_VARIABLE_INFO");

		if (localDebug) {
		    for (int i = 0; i < rvi.length; i++) {
			System.out.println(rvi[i].variableId);
			System.out.println(rvi[i].variableName);
			System.out.println(rvi[i].variableLabel);
			System.out.println(rvi[i].threshType);
			System.out.println(rvi[i].threshVal1);
			System.out.println(rvi[i].threshVal2);
		    }
		}

		yAxisLabs = new String[rvi.length];
		varNames = new String[rvi.length];
		for (int i = 0; i < rvi.length; i++) {
		    varNames[i] = rvi[i].variableName;
		    yAxisLabs[i] = rvi[i].variableLabel;
		}

		rtrPlottableVars = new String[rvi.length];
		int varId = -1;
		for (int i = 0; i < rtrPlottableVars.length; i++) {
		    rtrPlottableVars[i] = rvi[i].variableLabel;
		    if (rtrPlottableVars[i].equals(this.colID)) {
			if (localDebug)
			    Debug.out(i + " rtrPlottableVars[i] = " + rtrPlottableVars[i]);

			varId = rvi[i].variableId;
		    }
		}
		if (localDebug)
		    Debug.out("Initial variable Id Match = " + varId +
			      "\n var label = " + rvi[varId-1].variableLabel);

		if (varId >= 0)
		    this.colID = rvi[varId-1].variableLabel;

		// Need to calculate the router ID for clicked router.
		int rtrGroupId = subId;
		String rtrName = row;

		RouterData rtrDBData = null;

		try {
		    //Debug.out("Get getCurrentRouterData.");
		    rtrDBData = database.getCurrentRouterData(rtrGroupId);
		    if (rtrDBData == null)
			throw new Exception("null return from getCurrentRouterData " +
					    "for reouter group " + rtrGroupId);
		} catch (Exception e) {
		    Debug.out("Exception detected while loading  data for router group " +
			      rtrGroupId + "\n" +
			      e.getMessage());

		    return;
		}

		int rtrID = -1;
		for (int i = 0; i < rtrDBData.getSize(); i++) {
		    if (rtrName.equals(rtrDBData.getRouterName(i))) {
			rtrID = rtrDBData.getRouterId(i);
		    }
		}
		if (localDebug)
		    Debug.out("Router name = " + rtrName +
			      "  group Id = " + rtrGroupId +
			      "  router Id = " + rtrID +
			      "\nVariable name = " + this.colID +
			      "   Variable Id = " + varId);


	    } catch (Exception e) {
		if (localDebug)
		    Debug.out("Error getting RouterVariableInfo. \n" +
			      e.getMessage());
	    }

	} else if ("MDS".equals(this.type)) {
	    //Debug.out("MDS data historical data plot requested.");
	    try {
		Database.VariableInfo[] mvi = database.getVariableInfo("MDS_VARIABLE_INFO");
		yAxisLabs = new String[mvi.length];
		varNames = new String[mvi.length];
		for (int i = 0; i < mvi.length; i++) {
		    varNames[i] = mvi[i].variableName;
		    yAxisLabs[i] = mvi[i].variableLabel;
		}

		mdsPlottableVars = new String[mvi.length];
		int varId = -1;
		for (int i = 0; i < mvi.length; i++) {
		    mdsPlottableVars[i] = mvi[i].variableLabel;
		    //Debug.out(mdsPlottableVars[i] + " <--> " + this.colID);
		    if (mvi[i].variableName.equals(this.colID)) {
		 	if (localDebug)
			    Debug.out(i + " mdsPlottableVars[i] = " + mdsPlottableVars[i]);

			varId = mvi[i].variableId;
			//Debug.out("Found variable match for " + this.colID);
		    }
		}

		if (varId >= 0)
		    this.colID = mvi[varId-1].variableLabel;

		//Debug.out("varId = " + varId);

		if (localDebug) {
		    for (int i = 0; i < mvi.length; i++) {
			System.out.println(mvi[i].variableId);
			System.out.println(mvi[i].variableName);
			System.out.println(mvi[i].variableLabel);
			System.out.println(mvi[i].threshType);
			System.out.println(mvi[i].threshVal1);
			System.out.println(mvi[i].threshVal2);
		    }
		}
	    } catch (Exception e) {
		if (localDebug)
		    Debug.out("Error getting MdsVariableInfo. \n" +
			      e.getMessage());
	    }
	} else {
	    Debug.out("Unidentified device type encountered. Unable to proceed.");
	    return;
	}


	// Set up starting and ending timestamps for initial DB extraction
	long nowMilli = System.currentTimeMillis();
	try {
	    tsEnd = new Timestamp(nowMilli);

	    if (localDebug)
		Debug.out("Time of latestest time info = " + tsEnd);
	} catch (Exception e) {
	    if (localDebug)
		Debug.out("Exception during inital Timestamp generation.\n" +
			  e.getMessage());
	}
	tsStart = new Timestamp(nowMilli - duration);
	if (localDebug) {
	    Debug.out("Initial duration = " + duration);
	    Debug.out("TsStart initial setting = " + tsStart);
	}


	// Master data is used to get the row names (MSD Id, OST Ids, RTR Ids)
	// used in loading the control panel widgets.
	if (!"MDS".equals(type)) {
	    this.masterData = fsp.getMasterData(type, subId);
	} else {
	    this.masterData = new Object[1][1];
	    this.masterData[0][0] = row;
	}

	String varName = null;
	if ("OST".equals(type)) {
	    if (localDebug) {
		Debug.out("OST ostPlottableVars.length = " + ostPlottableVars.length +
			  "\nthis.colID = " + this.colID);
		
	    }
	    for (int i = 0; i < ostPlottableVars.length; i++) {
		//Debug.out(i + "  " +this.colID + " <==> " + ostPlottableVars[i]);
		if (this.colID.equals(ostPlottableVars[i])) {
		    varName = this.colID;
		    if (localDebug)
			Debug.out("Plottable variable " + this.colID + " selected.");
		    break;
		}
	    }

	} else if ("OSS".equals(type)) {

	    for (int i = 0; i < ossPlottableVars.length; i++) {
		if (this.colID.equals(ossPlottableVars[i])) {
		    varName = this.colID;
		    if (localDebug)
			Debug.out("Plottable variable " + this.colID + " selected.");
		    break;
		}
	    }

	} else if ("RTR".equals(type)) {
	    //Debug.out("# of rtrPlottableVars = " + rtrPlottableVars.length);

	    for (int i = 0; i < rtrPlottableVars.length; i++) {
		//Debug.out(i + "  Compare " + col + " rtrPlottableVars[i] = " +
			  //rtrPlottableVars[i]);
		if (this.colID.equals(rtrPlottableVars[i])) {
		    varName = this.colID;
		    if (localDebug)
			Debug.out("Plottable variable " + this.colID + " selected.");
		    break;
		}
	    }

	} else {  // Assuming MDS
	    
	    for (int i = 0; i < mdsPlottableVars.length; i++) {
		if (this.colID.equals(mdsPlottableVars[i])) {
		    varName = this.colID;
		    if (localDebug)
			Debug.out("Plottable variable " + this.colID + " selected.");
		    break;
		}
	    }

	}
	cats2Plot = new String[1];
	cats2Plot[0] = this.rowID;
	vars2Plot = new String[1];
	if (varName != null) {
	    //Debug.out("Variable selected = " + varName);
	    vars2Plot[0] = varName;
	    noVarSelected = false;
	}
	crvs2Plot = new String[1];
	crvs2Plot[0] = "Avg";
	if (isAnAggPlot) {
	    crvs2Plot[0] = rowID;
	    loadAverage = false;
	    if ("Agg".equals(rowID))
		loadAggregate = true;
	    else if ("Max".equals(rowID))
		loadMaximum = true;
	    else if ("Min".equals(rowID))
		loadMinimum = true;
	    else if ("Avg".equals(rowID))
		loadAverage = true;
	}
	    
	ovRangeMax = new float[1];
	ovRangeMin = new float[1];
	nRowsSelected = 1;
	nColsSelected = 1;
	nCurvesSelected = 1;
	if (varName != null) {
	    //getRawData(row, varName, 0, 1);
	    String [] defCurves = {"Avg"};
	    if (isAnAggPlot) {
		defCurves[0] = rowID;
	    }
	    dataPointCount = new int[1];

	    if (localDebug)
		Debug.out("tsStartPlot -- tsEndPlot = " + tsStartPlot + " -- " + tsEndPlot);

	    loadHistoricalData(this.type, this.subIdent, this.rowID,
			       this.colID, defCurves, 0, 1);

	    if (localDebug)
		Debug.out("tsStartPlot -- tsEndPlot = " + tsStartPlot + " -- " + tsEndPlot);

	    if (dataPointCount[0] <= 0) {
		if (localDebug)
		    Debug.out("Zero-length array result from data load request.");
		LwatchAlert lwAlert = new LwatchAlert(this);
		lwAlert.displayDialog(true,  // modal = true
				      "DB request yielded no data.",
				      1);  // type  1 = info with "continue"
	    }
	    if (dataPointCount[0] > 0) {
		tsStart = new Timestamp(rawTimes[0][0]);
		tsEnd = new Timestamp(rawTimes[0][dataPointCount[0]-1]);
		tsEndPlot = new Timestamp(rawTimes[0][dataPointCount[0]-1]);
		tsStartPlot = new Timestamp(Math.max(rawTimes[0][0],
						     rawTimes[0][dataPointCount[0]-1]-inittsPlotInterval));
		ovEndPlot = new Timestamp(tsEnd.getTime());
		ovStartPlot = new Timestamp(Math.max(rawTimes[0][0],
					    rawTimes[0][dataPointCount[0]-1]-(inittsPlotInterval*(long)2)));
	    //} else {  // Commented out because previous conditional excludes this result
		//long nowMillis = System.currentTimeMillis();
		//tsStart = new Timestamp(nowMillis - (4 * 3600000));  // 4 Hours
		//tsEnd = new Timestamp(nowMillis);
		//tsEndPlot = new Timestamp(nowMillis);
		//tsStartPlot = new Timestamp(nowMillis - (1 * 3600000));
		//ovEndPlot = new Timestamp(nowMillis);
		//ovStartPlot = new Timestamp(nowMillis - (2 * 3600000));
	    }
	    if (localDebug)
		Debug.out("ovStartPlot = " + ovStartPlot + "  ovEndPlot = " + ovEndPlot);

	    /***
	    if (localDebug) {
		Date begT = new Date(rawTimes[ovIDX][startIndex]);
		Date endT = new Date(rawTimes[ovIDX][stopIndex]);
		Debug.out("Start/stop raw time adjusted to " + begT.toString() + " - " + endT.toString());
		Debug.out("Start/stop index = " + startIndex + " - " + stopIndex);
 		Debug.out("Overview start/stop index = " + ovStartIndex + " - " + ovStopIndex);
	    }
	    ***/

	    if (localDebug) {
		Debug.out("Raw time range " + tsStart + " - " + tsEnd);
	    }

	} else {
	    if (localDebug)
		Debug.out("loadHistoricalData Skipped due to null varName.");
	}

	lastRefreshPlotParams = new PlotDescriptor();

	initColors();

	if (localDebug)
	    Debug.out("tsStartPlot -- tsEndPlot = " + tsStartPlot + " -- " + tsEndPlot);

    }  // PlotFrame2


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a TimeStamp representing the start time of the plot interval.
     *
     * @param grain the plot granularity.
     */

    Timestamp calcStartPlotInterval(int grain) {

	long plotInterval = (long)(3600*1*1000);  // 1 hour ago
	//Timestamp inittsStartPlot = new Timestamp(inittsEndPlot.getTime() - inittsPlotInterval);

	switch (grain) {

	case Database.RAW: {
	    plotInterval = (long)(3600*1000);  // 1 hour ago
	    this.duration = plotInterval * 2;

	    break;
	}

	case Database.HOUR: {
	    plotInterval = (long)(48*3600*1000);  // 2 days ago
	    this.duration = plotInterval * 30;

	    break;
	}

	case Database.DAY: {
	    plotInterval = (long)(365*24) * (long)(3600*1000);  // 1 year ago
	    this.duration = plotInterval * 2;

	    break;
	}

	case Database.WEEK: {
	    plotInterval = (long)(365*24) * (long)(3600*1000);  // 1 year ago
	    this.duration = plotInterval * 2;

	    break;
	}

	case Database.MONTH: {
	    plotInterval = (long)(10*365*24) * (long)(3600*1000);  // 10 years ago
	    this.duration = plotInterval * 2;

	    break;
	}

	case Database.YEAR: {
	    plotInterval = (long)(10*365*24) * (long)(3600*1000);  // 10 years ago
	    this.duration = plotInterval * 20;

	    break;
	}

	case HEARTBEAT: {
	    plotInterval =  prefs.liveDisplayInterval * (long)(60000);  // 10 years ago
	    this.duration = plotInterval;
	    tsEndPlot = inittsEndPlot;
	    tsStartPlot = new Timestamp(tsEndPlot.getTime() - plotInterval);

	    break;
	}

	}

	inittsPlotInterval = plotInterval;

	Timestamp spi = new Timestamp(inittsEndPlot.getTime() - plotInterval);
	inittsStartPlot = spi;

	//Debug.out("grain = " + grain + "  end plot = " + inittsEndPlot + "   start plot = " + spi);


	return spi;

    }  // calcStartPlotInterval



    //////////////////////////////////////////////////////////////////////////////

    /**
     * Initialize the curve colors used in the chart.
     */

    void initColors() {

	curveColor[0] = Color.green;
	curveColor[1] = Color.yellow;
	curveColor[2] = Color.red;
	curveColor[3] = Color.blue;
	curveColor[4] = Color.white;
	curveColor[5] = Color.orange;
	curveColor[6] = Color.cyan;
	curveColor[7] = Color.magenta;
	curveColor[8] = Color.pink;

    }  // initColors


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the frame containing this plot.
     */

    public JFrame getFrame() {

	return this.thpFrame;

    }  // getFrame


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Update the control widgets to reflect the current time settings.
     */

    public void updateControlValues() {
	int tindx = ovIDX/nCurvesSelected;

	int [] daysInMonth = {31, 28, 31,30, 31, 30, 31, 31, 30, 31, 30, 31};
	//long xstart = rawTimes[tindx][startIndex];
	//long xstop = rawTimes[tindx][stopIndex];

	/***
	    if (localDebug) {
		Debug.out("\nxstart before = " + (new Date(xstart)).toString() +
			  "  stopIndex = " + startIndex);
		Debug.out("xstop before = " + (new Date(xstop)).toString() +
			  "  stopIndex = " + stopIndex);
   	    }

	    if (xstop < (3600000*48)) {  // Invalid time value
		while ((stopIndex > 0) && ((rawTimes[ovIDX][stopIndex]) < (3600000*48))) {
		    if (localDebug)
			Debug.out(stopIndex + "  " + xstop);
		    xstop = rawTimes[tindx][--stopIndex];
		}
	    }
	***/

	if (limitedDebug) {
	    Debug.out("dataPointCount[0] = " + dataPointCount[0]);
	    Debug.out("tsStartPlot after = " + tsStartPlot);
	    Debug.out("tsEndPlot after = " + tsEndPlot + "\n");
	}

	Date dstart = new Date(tsStartPlot.getTime());  //xstart);
	Date dstop = new Date(tsEndPlot.getTime());  //xstop);
	GregorianCalendar cal = new GregorianCalendar();

	if (localDebug)
	    Debug.out("dstart = " + dstart.toString() + "\ndstop = " +
		      dstop.toString());

	// Set the Start widgets.
	cal.setTime(dstart);
	int yrstart = cal.get(Calendar.YEAR);
	yrChooser.setSelectedYear(yrstart);
	int mnthstart = cal.get(Calendar.MONTH);
	monthChooser.setSelectedMonth(mnthstart);
	int daystart = cal.get(Calendar.DAY_OF_MONTH);
	dayChooser.setSelectedDay(daystart);
	int hrstart = cal.get(Calendar.HOUR_OF_DAY);
	hourChooser.setSelectedHour(hrstart);
	int minstart = cal.get(Calendar.MINUTE);
	minuteChooser.setSelectedMinute(minstart);


	// Set the End widgets.
	cal.setTime(dstop);
	int yrstop = cal.get(Calendar.YEAR);
	yrEndChooser.setSelectedYear(yrstop);
	int mnthstop = cal.get(Calendar.MONTH);
	monthEndChooser.setSelectedMonth(mnthstop);
	int daystop = cal.get(Calendar.DAY_OF_MONTH);
	dayEndChooser.setSelectedDay(daystop);
	int hrstop = cal.get(Calendar.HOUR_OF_DAY);
	hourEndChooser.setSelectedHour(hrstop);
	int minstop = cal.get(Calendar.MINUTE);
	minuteEndChooser.setSelectedMinute(minstop);

	// Set the Duration widgets.
	long durationhours = (tsEndPlot.getTime() - tsStartPlot.getTime()) / HOUR;
	//Debug.out("Length of chart plot interval = " + durationhours + " hours");
	int yrDur = yrstop - yrstart;
	//Debug.out("yrDur = " + yrDur);
	int mnthDur = mnthstop - mnthstart;
	//Debug.out("mnthDur = " + mnthDur);
	if (mnthDur < 0) {
	    yrDur = Math.max(0, yrDur-1);
	    mnthDur = 12 - mnthstart + mnthstop;
	    //Debug.out("Adjusted mnthDur = " + mnthDur);
	}
	int dayDur = daystop - daystart;
	//Debug.out("dayDur = " + dayDur);
	if (dayDur < 0) {
	    mnthDur = Math.max(0, mnthDur - 1);
	    dayDur = daysInMonth[mnthstart] - daystart + daystop;
	    //Debug.out("Adjusted dayDur = " + dayDur);
	}
	int hrDur = hrstop - hrstart;
	//Debug.out("hrDur = " + hrDur);
	if (hrDur < 0) {
	    dayDur = Math.max(0, dayDur - 1);
	    hrDur = 24 - hrstart + hrstop;
	    //Debug.out("Adjusted hrDur = " + hrDur);
	}
	int minDur = minstop - minstart;
	//Debug.out("minDur = " + minDur + "\n");
	if (minDur < 0) {
	    hrDur = Math.max(0, hrDur - 1);
	    minDur = 60 - minstart + minstop;
	    //Debug.out("Adjusted minDur = " + minDur + "\n");
	}

	yrDurChooser.setSelected(yrDur);
	monthDurChooser.setSelected(mnthDur);
	dayDurChooser.setSelected(dayDur);
	hourDurChooser.setSelected(hrDur);
	minuteDurChooser.setSelected(minDur);

    }  // updateControlValues


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Route the data load request to the real load method. This method could be
     * eliminated since the specific loaders were consolidated into a single method.
     */

    void loadHistoricalData(String type, int subId, String cat, String var,
			    String[] curves, int varNum, int varTot) {

	try {

	    if ("MDS".equals(type)) {
		loadHistorical___Data(type, subId, cat, var, curves,
		                      varNum, varTot);
	    } else if ("OST".equals(type)) {
		loadHistorical___Data(type, subId, cat, var, curves,
	                              varNum, varTot);
	    } else if ("OSS".equals(type)) {
		//loadHistorical___Data(cat, var, curves, varNum, varTot);
	    } else {  // Assume type is RTR
		loadHistorical___Data(type, subId, cat, var, curves,
	                              varNum, varTot);
	    }
	} catch (java.lang.Exception e) {
	    Debug.out("Exception caught :\n" + e.getMessage());
	    e.printStackTrace();
	    System.exit(0);
	}

    }  // loadHistoricalData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the OSt identifier from the ost name.
     *
     * @param cat OST name for which identifer is required.
     */

    int calcOSTId(String cat) throws java.lang.Exception {
	int ostId = -1;
	for (int i = 0; i < masterData.length; i++) {
	    if (masterData[i][0].equals(cat)) {
		ostId = i + 1;
		break;
	    }
	}
	if (ostId == -1)
	    throw new java.lang.Exception("Unable to calculate OST Id.");

	return ostId;

    }  // calcOSTId


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the OST variable identifier for the variable to be plotted.
     *
     * @param var name of variable to be plotted.
     */

    int calcOSTVariableId(String var) throws java.lang.Exception {
	//int [] varMap = {1, 2, 3, 4, 5, 6, 7, 8, 9}; //, 10, 11}; // Derived from OstVariableInfo
	int [] varMap = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

	int varId = -1;
	for (int i = 0; i < ostPlottableVars.length; i++) {
	    if (ostPlottableVars[i].equals(var)) {
		varId = varMap[i];
		break;
	    }
	}
	if (varId < 0) {
	    //
	}

	if (varId == -1)
	    throw new java.lang.Exception("Unable to calculate OST variable Id.");

	return varId;

    }  // calcOSTVariableId


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Calculate the router identifier from the router name.
     *
     * @param cat router name for which identifer is required.
     */

    int calcRTRId(String cat) throws java.lang.Exception {

	int rtrID = -1;

	try{
	    // Need to calculate the router ID for clicked router.
	    int rtrGroupId = subIdent;
	    String rtrName = cat;

	    RouterData rtrDBData = null;

	    try {
		//Debug.out("Get getCurrentRouterData.");
		rtrDBData = database.getCurrentRouterData(rtrGroupId);
		if (rtrDBData == null)
		    throw new Exception("null return from getCurrentRouterData " +
					"for reouter group " + rtrGroupId);
	    } catch (Exception e) {
		Debug.out("Exception detected while loading  data for router group " +
			  rtrGroupId + "\n" +
			  e.getMessage());

		return -1;
	    }

	    for (int i = 0; i < rtrDBData.getSize(); i++) {
		if (rtrName.equals(rtrDBData.getRouterName(i))) {
		    rtrID = rtrDBData.getRouterId(i);
		}
	    }
	    if (limitedDebug)
		Debug.out("Router name = " + rtrName + "  group Id = " + rtrGroupId +
			  "  router Id = " + rtrID);

	} catch (Exception e) {
	    if (localDebug)
		Debug.out("Error calculating router Id. \n" +
			  e.getMessage());
	}

	return rtrID;

    }  // calcRTRId


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the router variable identifier for the variable to be plotted.
     *
     * @param var name of variable to be plotted.
     */

    int calcRTRVariableId(String var) throws java.lang.Exception {

	int varId = -1;

	try {
	    Database.VariableInfo[] rvi = database.getVariableInfo("ROUTER_VARIABLE_INFO");

	    for (int i = 0; i < rtrPlottableVars.length; i++) {
		if (rtrPlottableVars[i].equals(var)) {
		    if (localDebug)
			Debug.out(i + " rtrPlottableVars[i] = " + rtrPlottableVars[i]);

		    for (int j = 0; j < rvi.length; j++) {
			if (rtrPlottableVars[i].equals(rvi[j].variableLabel))
			    varId = rvi[j].variableId;
		    }

		    break;
		}
	    }
	    if (limitedDebug)
		Debug.out("Initial variable (" + var + ") Id Match = " + varId +
			  "\n var label = " + rvi[varId-1].variableLabel);
	} catch (Exception e) {
	    Debug.out("Error calculating variable Id.\n" + e.getMessage());
	}

	return varId;

    }  // calcRTRVariableId


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the MDS variable identifier for the variable to be plotted.
     *
     * @param var name of variable to be plotted.
     */

    int calcMDSVariableId(String var) throws java.lang.Exception {

	int varId = -1;

	try {
	    Database.VariableInfo[] mvi = database.getVariableInfo("MDS_VARIABLE_INFO");

	    for (int i = 0; i < mdsPlottableVars.length; i++) {
		if (mdsPlottableVars[i].equals(var)) {
		    if (localDebug)
			Debug.out(i + " mdsPlottableVars[i] = " + mdsPlottableVars[i]);

		    for (int j = 0; j < mvi.length; j++) {
			if (mdsPlottableVars[i].equals(mvi[j].variableLabel))
			    varId = mvi[j].variableId;
		    }

		    break;
		}
	    }
	    if (limitedDebug)
		Debug.out("Initial variable (" + var + ") Id Match = " + varId +
			  "\n var label = " + mvi[varId-1].variableLabel);
	} catch (Exception e) {
	    Debug.out("Error calculating variable Id.\n" + e.getMessage());
	}

	return varId;

    }  // calcMDSVariableId


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Load historical data for a specific variable.
     *
     * @param type data type (MDS, OST, OSS, RTR).
     *
     * @param subId sub identifier (MDS or Router group) to load data for.
     *
     * @param cat name of MDS, OST, RTR to load data for.
     *
     * @param var name of variable to load data for.
     *
     * @param curves list of curve names that are selected.
     *
     * @param varNum number of the variable (n of m) to be plotted.
     *
     * @param varTot total number of variables to be plotted.
     */

    void loadHistorical___Data(String type, int subId, String cat, String var,
			       String[] curves, int varNum, int varTot) 
	throws java.lang.Exception
    {

	if (localDebug)
	    Debug.out("type = " + type + "  subId = " + subId + "\ncat = " +
		      cat + "  var = " + var + "  curves.length = " + curves.length +
		      "\nvarNum = " + varNum + "  varTot = " + varTot);

	long loadBeg = 0;
	if (timerOn) {
	    loadBeg = System.currentTimeMillis();
	}

	//double[] divisors = {1000000., 1000000., 1000000., 1000000., 1000.0, 1000.0,
			     //1.0, 1.0, 1.0};  //, 1.0, 1.0};
	//String[] yAxisLabs = {"MBytes", "MBytes", "MBytes / Second", "MBytes / Second",
			      //"KBytes Free", "KBytes Used" , "Inodes Free", "Inodes Used",
			      //"Percent CPU"}; //, "Percent KBytes", "Percent INodes"};

	if (localDebug)
	    Debug.out("Entering loadHistorical___Data  var arg = " + var);
	int devId = -1;
	int fsId = -1;
	int varId = -1;
	String varName = "";
	if ("OST".equals(type)) {
	    if (!isAnAggPlot)
		devId = calcOSTId(cat);
	    else
		fsId = database.getFilesystemInfo().filesystemId;

	    varId = calcOSTVariableId(var);

	} else if ("RTR".equals(type)) {
	    if (!isAnAggPlot)
		devId = calcRTRId(cat);

	    varId = calcRTRVariableId(var);

	} else if ("MDS".equals(type)) {
	    devId = subId;
	    varId = calcMDSVariableId(var);
	}

	varName = varNames[varId-1];
	if (varNum == 0)
	    yAxisLabel = yAxisLabs[varId-1];

	/***
	Database.VariableInfo[] ovi = database.getVariableInfo("ROUTER_VARIABLE_INFO");

	if (localDebug) {
	    for (int i = 0; i < ovi.length; i++) {
		System.out.println(ovi[i].variableId);
		System.out.println(ovi[i].variableName);
		System.out.println(ovi[i].variableLabel);
		System.out.println(ovi[i].threshType);
		System.out.println(ovi[i].threshVal1);
		System.out.println(ovi[i].threshVal2);
	    }
	}
	***/

	if ( isAnAggPlot && ((granularity == Database.RAW) || (granularity == HEARTBEAT))) {
	    LwatchAlert lwAlert = new LwatchAlert(pf2);
	    lwAlert.displayDialog(true,  // modal dialog
				  "RAW & HEARTBEAT granularity not supported for " +
				  "aggregate plots.",
				  1);  // type  1 = info with "continue
	    //granularity = Database.HOUR;
	    return;
	}

	Timestamp sampNtrvlStart = null;
	Timestamp sampNtrvlEnd = null;
	if ( (!updateLive) || initialLoad ) {
	    sampNtrvlStart = new Timestamp(tsStart.getTime());
	    sampNtrvlEnd = new Timestamp(tsEnd.getTime());
	} else {
	    sampNtrvlEnd = new Timestamp(System.currentTimeMillis());
	    sampNtrvlStart = new Timestamp(lastLiveNtrvlEnd+100);  //1000);  // + 1);
	}

	int resolution = granularity;
	if (resolution == HEARTBEAT)
	    resolution = 1;


	if (limitedDebug)
	    Debug.out("Calling get" + type + "AggregateData w/\ngranularity = " +
		      resolution +
		      "\nOST Id = " + devId +
		      "\nvarName = " + varName + "\n# of curves = " +
		      curves.length + "\nsampNtrvlStart = " +
		      sampNtrvlStart + "\nsampNtrvlEnd = " + sampNtrvlEnd +
		      "\nupdateLive = " + updateLive +
		      "  initialLoad = " + initialLoad +
		      "\nfsId = " + fsId);

	//Debug.out("Loading " + varName + " over interval " + sampNtrvlStart +
		  //" --> " + sampNtrvlEnd);

	AggregateData [] aggDat = null;
	try {
	    long accessBeg = 0;
	    if (timerOn) {
		accessBeg = System.currentTimeMillis();
	    }

	    if ("OST".equals(type)) {
		if (!isAnAggPlot)
		    aggDat = database.getOstAggregateData(resolution,
							  devId,
							  varName,
							  sampNtrvlStart,
							  sampNtrvlEnd);
		else {
		    if (limitedDebug)
			Debug.out("\nCalling getFilesystemAggregateData" +
				  " w/\ngranularity = " + resolution +
				  "\nfsId = " + fsId +
				  "\nvarName = " + varName + "\n# of curves = " +
				  curves.length + "\nsampNtrvlStart = " +
				  sampNtrvlStart + "\nsampNtrvlEnd = " + sampNtrvlEnd +
				  "\nupdateLive = " + updateLive +
				  "  initialLoad = " + initialLoad);

		    aggDat = database.getFilesystemAggregateData(resolution,
								 fsId,
								 varName,
								 sampNtrvlStart,
								 sampNtrvlEnd);
		}
	    } else if ("RTR".equals(type)) {
		if (!isAnAggPlot)
		    aggDat = database.getRouterAggregateData(resolution,
							     devId,
							     varName,
							     sampNtrvlStart,
							     sampNtrvlEnd);
		else {
		    if (limitedDebug)
			Debug.out("\nCalling getRouterGroupAggregateData" +
				  " w/\ngranularity = " + resolution +
				  "\nRouter Group = " + this.subIdent +
				  "\nvarName = " + varName + "\n# of curves = " +
				  curves.length + "\nsampNtrvlStart = " +
				  sampNtrvlStart + "\nsampNtrvlEnd = " + sampNtrvlEnd +
				  "\nupdateLive = " + updateLive +
				  "  initialLoad = " + initialLoad);
		    aggDat = database.getRouterGroupAggregateData(resolution,
							     this.subIdent,  // In this case, router grp
							     varName,
							     sampNtrvlStart,
							     sampNtrvlEnd);


		}
	    } else if ("MDS".equals(type)) {
		aggDat = database.getMdsAggregateData(resolution,
						      devId,
						      varName,
						      sampNtrvlStart,
						      sampNtrvlEnd);
	    }

	    if (timerOn) {
		dbAccess = (System.currentTimeMillis() - accessBeg) / 1000;
	    }
	} catch (Exception e) {
	    Debug.out("Error detected loading aggregate data for " +
		      devId + "  variable = " + varName + "\ngranularity = " +
		      granularity + "\ntStart = " + sampNtrvlStart +
		      "\ntEnd = " + sampNtrvlEnd);
	    e.printStackTrace();
	}
	if (localDebug) {
	    if (aggDat == null)
		Debug.out("getAggregateData call returned null array for " + varName);
	    else
		Debug.out("getAggregateData call returned array of size " + aggDat.length + " for " + varName);
	}

	if (aggDat.length <= 0) {
	    if (granularity != HEARTBEAT) {
		LwatchAlert lwAlert = new LwatchAlert(pf2);
		lwAlert.displayDialog(true,  // modal dialog
				      "Database load request yielded no data.",
				      1);  // type  1 = info with "continue
	    }

	    if (localDebug)
		Debug.out("Zero length return from get" + type +
			  "AggregateData call for type " + granularity + " data.");
	    return;
	}

	//if (localDebug)
	    //Debug.out("ResultSet rs = " + rs.toString());


	boolean reachedStart = false;
	boolean reachedEnd = false;
	//if (localDebug)
	    //Debug.out("Grab times for interval check");
	long startT = sampNtrvlStart.getTime();
	long endT = sampNtrvlEnd.getTime();
	long asizeL = endT - startT;

	if (localDebug)
	    Debug.out("endT - startT = " + asizeL);

	if (granularity == Database.HOUR)
	    asizeL /= 3600L * 1000L;
	else if (granularity == Database.DAY)
	    asizeL /= 24L * 3600L * 1000L;
	else if (granularity == Database.WEEK)
	    asizeL /= 7L * 24L * 3600L * 1000L;
	else if (granularity == Database.MONTH) {
	    asizeL /= 31L * 24L * 3600L * 1000L;
	} else if (granularity == Database.YEAR) {
	    asizeL /= 365L * 24L * 3600L * 1000L;
	} else  //  Database.RAW or HEARTBEAT
	    asizeL /= 5000L;

	if (localDebug)
	    Debug.out("endT = " + endT + "  startT = " + startT + "\nasize = " + asizeL);

	// Add some slop to cover end points
	int asize = (int)(asizeL + 100L);
	if (asizeL <= 0L) {
	    if (localDebug)
		Debug.out("asizeL calculated to be <= 0. Returning.");
	    return;
	}
	if (limitedDebug)
	    Debug.out("endT = " + endT + "  startT = " + startT + "\nasize = " + asize);


	if (limitedDebug)
	    Debug.out("# of array elements for " + varId + " = " + asize);


	if (varNum == 0) {

	    if (localDebug)
		Debug.out("updateLive = " + updateLive + "   initialLoad = " + initialLoad);

	    if ((!updateLive) || initialLoad) {
		if (localDebug)
		    Debug.out("Dimension rawData to  [" + varTot + "][" + asize + "]");
		rawData = new float[varTot][asize];
		int varDim = varTot / curves.length;
		if (localDebug) {
		    Debug.out("Dimension rawData to  [" + varDim + "][" + asize + "]\n\n");
		}
		rawTimes = new long[varDim][asize];
		arrayLimit = asize;
	    } else {
		if (localDebug)
		    Debug.out("Dimension rawDataN to  [" + varTot + "][" + asize + "]");
		rawDataN = new float[varTot][asize];
		int varDim = varTot / curves.length;
		if (localDebug) {
		    Debug.out("Dimension rawDataN to  [" + varDim + "][" + asize + "]");
		    Debug.out("varTot = " + varTot + "   curves.length = " + curves.length + "\n\n");
		}
		rawTimesN = new long[varDim][asize];
		arrayLimitN = asize;
	    }
	}

	if (localDebug)
	    Debug.out("Size of aggregate structure array = " + asize);

	//Debug.out("Grab # of curves/var selected");
	int numCurvesPerVar = 0;
	if (controls == null) {
	    numCurvesPerVar = 1;

	} else {
	    numCurvesPerVar = curves.length;  //controls.getCurvePanel().getNumCurvesSelected();

	}
	//Debug.out("# of curves/var selected = " + numCurvesPerVar);


	String[] lines = new String[asize];
	if (varNum == 0) {
	    if (wideView != null)
		wideView.first = true;
	}

	if (localDebug)
	    Debug.out("varNum = " + varNum + "   varTot = " + varTot + 
		      "  asize = " + asize + "   numCurvesPerVar = " +
		      numCurvesPerVar);
	try {
	    if ((!updateLive) || (initialLoad)) {
		for (int i = 0; i < numCurvesPerVar; i++) {
		    // curves[i] names the curve to load. (Agg, Max, Min or Avg)
		    int idx1 = varNum * numCurvesPerVar + i;
		    dataPointCount[idx1] = Math.min(asize, arrayLimit);

		    if (limitedDebug) {
			Debug.out("Data array size for curve " + curves[i] + " = " +
				  dataPointCount[idx1] + "\n varNum = " + varNum +
				  "  i = " + i + "  numCurvesPerVar = " +
				  numCurvesPerVar + "\nidx1 = " + idx1 + "  arrayLimit = " + 
				  arrayLimit + "\naggDat.length = " + aggDat.length);
			int testval = Math.min(arrayLimit, aggDat.length);
			Debug.out("Math.min(" + arrayLimit + ", " +
				  aggDat.length + ") = " + testval);
			Debug.out("rawData.length = " + rawData.length + "  idx1 = " + idx1);
			Debug.out("rawData[" + idx1 + "].length = " + rawData[idx1].length);
		    }

		    int ptCount = -1;
		    int badValues = 0;
		    boolean rangeSet = false;
		    for (int j = 0; j < Math.min(arrayLimit, aggDat.length); j++) {
			//if (localDebug)
			//Debug.out("i = " + i + "   j = " + j);

			long timeVal = aggDat[j].timestamp.getTime();
			if (timeVal > 10000) {
			    ptCount++;
			    if (i == 0) {
				rawTimes[varNum][ptCount] = timeVal;
			    }

			    //if (localDebug)
			        //Debug.out("i = " + i + "  timeVal[" + j + "] = " +
			        //timeVal + "  " + new Date((long)timeVal).toString());

			    if ("Agg".equals(curves[i])) {
				//Debug.out("Move Agg data to rawData array.");
				if (aggDat != null && aggDat[0].hasAggregate)
				    rawData[idx1][ptCount] = aggDat[j].aggregate;
				else
				    rawData[idx1][ptCount] = (float)(-1.0);
			    } else if ("Max".equals(curves[i])) {
				//Debug.out("Move Max data for " + j + " to rawData array.");
				rawData[idx1][ptCount] = aggDat[j].maxval;
			    } else if ("Min".equals(curves[i])) {
				//Debug.out("Move Min data to rawData array.");
				rawData[idx1][ptCount] = aggDat[j].minval;
			    } else if ("Avg".equals(curves[i])) {
				//Debug.out("Move Avg data to rawData array.");
				//if (limitedDebug) {
				    //Debug.out("idx1 = " + idx1 + "  ptCount = " + ptCount +
				        //"  j = " +j);
				    //if (aggDat[j] == null)
				        //Debug.out("aggDat[" + j + "] = null");
			        //}
				rawData[idx1][ptCount] = aggDat[j].average;
			    }
			    if (! rangeSet) {
				ovRangeMax[idx1] = rawData[idx1][ptCount];
				ovRangeMin[idx1] = rawData[idx1][ptCount];
				rangeSet = true;
			    } else {
				ovRangeMax[idx1] = Math.max(ovRangeMax[idx1],
							    rawData[idx1][ptCount]);
				ovRangeMin[idx1] = Math.min(ovRangeMin[idx1],
							    rawData[idx1][ptCount]);
			    }


			} else {
			    badValues++;
			}

		    }   //  for (int j = 0; j < asize; j++)

		    dataPointCount[idx1] = ptCount + 1;  //Math.min(asize, arrayLimit);

		    if ((aggDat.length > 0) &&
			(!aggDat[0].hasAggregate) && ("Agg".equals(curves[i]))) {
			//Debug.out(varName + " does NOT contain aggregate data");
			dataPointCount[idx1] = -999;
			//continue;  // Skip to end of loop. Continue w/ next iteration.
		    }


		    if (limitedDebug) {  //(localDebug) {
			System.out.println("Out of " + Math.min(arrayLimit, aggDat.length) + 
					   " pts. " + dataPointCount[idx1] + " were good & " +
					   badValues + " were bad.");
			Debug.out("Out of " + Math.min(arrayLimit, aggDat.length) + " pts. " +
				  dataPointCount[idx1] + " were good & " +
				  badValues + " were bad.");
			Debug.out("ov " + idx1 + " Y vals ranges from " + ovRangeMin[idx1] + 
			  " to " + ovRangeMax[idx1]);
			Debug.out("ov " + idx1 + " time ranges from " + rawTimes[idx1][0] + 
				  " to " + rawTimes[idx1][dataPointCount[idx1]-1] + " \n" +
				  new Timestamp(rawTimes[idx1][0]) + " to " +
				  new Timestamp(rawTimes[idx1][dataPointCount[idx1]-1]));
		    }

		    if (localDebug) {
			Debug.out("i = " + i + "  rawTimes[0] = " + rawTimes[varNum][0] + "  " +
				  new Date(rawTimes[varNum][0]).toString());
			int lasti = ptCount - 1;
			if (lasti >= 0)
			    Debug.out("i = " + i + "  rawTimes[" + lasti + "] = " +
				      rawTimes[varNum][lasti] + "  " +
				      new Date(rawTimes[varNum][lasti]).toString());
		    }

		    //avgVal[idx1] = aggVal[idx1] / ((double)asize);

		    //if (localDebug)
		        //Debug.out("Computed Avg val for curve " + idx1 + " = " + avgVal[idx1]);

		    // Set the range min/max values used for plotting.
		    yLo = ovRangeMin[idx1];
		    yHi = ovRangeMax[idx1] + (ovRangeMax[idx1] - ovRangeMin[idx1])/10.;
	    

		    //Debug.out("Data range from " + rawTimes[varNum][0] +
		              //" to " + rawTimes[varNum][asize-1] +
		              //"\n y Range : " +
		              //yLo + " to " + yHi);
		}  // for (int i = 0; i < numCurvesPerVar; i++)

	    } else {  // update live is true && not initialLoad

		boolean timesUpdated = false;
		boolean visited = false;
		//for (int i = 0; i < numCurvesPerVar; i++) {
		for (int i = 0; i < curves.length; i++) {
		    // curves[i] names the curve to load. (Agg, Max, Min or Avg)
		    //int idx1 = varNum * numCurvesPerVar + i;
		    int idx1 = varNum * curves.length + i;

		    if (rawDataN == null || idx1 >= rawDataN.length) {
			//Debug.out(idx1 + " >=  rawDataN.length, SKIPPING.");
			skipThisUpdate = true;
			return;
		    }
		    //dataPointCount[idx1] = Math.min(asize, arrayLimit);

		    if (limitedDebug) {
			Debug.out("Data array size for curve " + curves[i] + " = " +
				  dataPointCount[idx1] + "\n varNum = " + varNum +
				  "  i = " + i + "  numCurvesPerVar = " +
				  numCurvesPerVar + "\nidx1 = " + idx1 + "  arrayLimitN = " + 
				  arrayLimitN + "\naggDat.length = " + aggDat.length);
			int testval = Math.min(arrayLimit, aggDat.length);
			Debug.out("Math.min(" + arrayLimit + ", " + aggDat.length +
				  ") = " + testval);
			Debug.out("rawDataN[" + idx1 + "].length = " + rawDataN[idx1].length);
		    }

		    int ptCount = 0;  //-1;
		    int badValues = 0;
		    //boolean rangeSet = false;
		    for (int j = 0; j < Math.min(arrayLimitN, aggDat.length); j++) {
			//if (localDebug)
			//Debug.out("i = " + i + "   j = " + j);

			long timeVal = aggDat[j].timestamp.getTime();
			if (timeVal > 10000L) {
			    //ptCount++;
			    if (i == 0) {
				//if (j == 0)
				    //Debug.out("Loading update data for var = " + varNum + "\n");
				if (varNum >= rawTimesN.length) {
				    //Debug.out("updateLive = " + updateLive + "   initialLoad = " + initialLoad);
				    //Debug.out("Problem with incremental raw Time array length.\n" +
					      //"varNum = " + varNum + "   rawTimesN.length = " + rawTimesN.length);

				    //Thread.dumpStack();
				    //System.out.println("\n");
				    //Debug.out("SKIPPING varnum = " + varNum + "   idx1 = " + idx1);
				    skipThisUpdate = true;
				    return;
				}
				if (timeVal <= rawTimes[varNum][dataPointCount[varNum]-1])
				    continue;
				//Debug.out("Assign " + aggDat[j].timestamp + " to rawTimesN[" + varNum + "][" + ptCount + "]");

				rawTimesN[varNum][ptCount] = timeVal;
			    }

			    //if (localDebug)
			        //Debug.out("i = " + i + "  timeVal[" + j + "] = " +
			        //timeVal + "  " + new Date((long)timeVal).toString());

			    if ("Agg".equals(curves[i])) {
				//Debug.out("Move Agg data to rawDataN array.");
				rawDataN[idx1][ptCount] = aggDat[j].aggregate;
			    } else if ("Max".equals(curves[i])) {
				//Debug.out("Move Max data for " + j + " to rawDataN array.");
				rawDataN[idx1][ptCount] = aggDat[j].maxval;
			    } else if ("Min".equals(curves[i])) {
				//Debug.out("Move Min data to rawDataN array.");
				rawDataN[idx1][ptCount] = aggDat[j].minval;
			    } else if ("Avg".equals(curves[i])) {
				//Debug.out("Move Avg data to rawDataN array.  " + ptCount + " <= " + j);
				//if (limitedDebug) {
				    //Debug.out("idx1 = " + idx1 + "  ptCount = " + ptCount +
				        //"  j = " +j);
				    //if (aggDat[j] == null)
				        //Debug.out("aggDat[" + j + "] = null");
			        //}
				rawDataN[idx1][ptCount] = aggDat[j].average;
			    }
			    //if (! rangeSet) {
			        //ovRangeMax[idx1] = rawData[idx1][ptCount];
			        //ovRangeMin[idx1] = rawData[idx1][ptCount];
			        //rangeSet = true;
			    //} else {
				ovRangeMax[idx1] = Math.max(ovRangeMax[idx1],
							    rawDataN[idx1][ptCount]);
				ovRangeMin[idx1] = Math.min(ovRangeMin[idx1],
							    rawDataN[idx1][ptCount]);
			    //}
				ptCount++;

			} else {
			    badValues++;
			}

		    }   //  for (int j = 0; j < asize; j++)
		

		    if (limitedDebug) {
			Debug.out("# of new data points added = " + ptCount);
			if (dataPointCount[idx1] > 0) {
			    Debug.out("Length of rawTimes[" + varNum + " = " +
				      rawTimes[varNum].length);
			    Debug.out("Last Time from original dataset = " +
				      new Timestamp(rawTimes[varNum][dataPointCount[idx1]-1]));
			    Debug.out("New times added :");
			    for (int j = 0; j < ptCount; j++)
				Debug.out(j + " = " + new Timestamp(rawTimesN[varNum][j]));
			} else
			    Debug.out("dataPointCount[" + idx1 + "] = " + dataPointCount[idx1]);
		    }

		    //dataPointCount[idx1] = ptCount + 1;  //Math.min(asize, arrayLimit);

		    // Shift off oldest ptCount values from rawData and rawTimes arrays
		    if (ptCount < rawData[idx1].length) {
			//Debug.out("Update curve " + idx1 + " with " + ptCount + " points.");
			// Shift off oldest ptCount values from rawData and rawTimes arrays
			if (ptCount > 0) {
			    for (int j = ptCount; j < dataPointCount[idx1]; j++) {
				if ((aggDat[0].hasAggregate) || (!"Agg".equals(curves[i]))) {
				    rawData[idx1][j-ptCount] = rawData[idx1][j];
				    if (!timesUpdated) {  //(i == 0) {
					if (j == ptCount)
					    //Debug.out("Shift " + ptCount + " points off trailing edge for var # " + varNum +
						      //"   dataPointCount = " + dataPointCount[idx1]);
					if (limitedDebug) {
					    int kk = j - ptCount;
					    Debug.out("Shift values to rawTimes[" +
						      varNum + "][" + kk + "] <==  rawTimes[" +
						      varNum + "][" + j + "]");
					}
					rawTimes[varNum][j-ptCount] = rawTimes[varNum][j];
				    }
				}
			    }

			    // Append new values to end of rawData and rawTimes arrays
			    for (int j = 0; j < ptCount; j++) {
				int ishift = dataPointCount[idx1]+(j-ptCount);

				if (limitedDebug) {
				    Debug.out("Assign " + new Timestamp(rawTimesN[varNum][j]) +
					      " from New " + j + " to Old " + ishift);
				}
				if ((aggDat[0].hasAggregate) || (!"Agg".equals(curves[i]))) {
				    if (limitedDebug)
					Debug.out("Assign new values for " +
						  curves[i] + " rawData[" +
						  idx1 + "][" + ishift +
						  "] <==  rawDataN[" +
						  idx1 + "][" + j + "]");

				    rawData[idx1][ishift] = rawDataN[idx1][j];

				    if (!timesUpdated) {
					//if (j == 0)
					    //Debug.out("Append " + ptCount + " to end for var # " +
						      //varNum + "  ishift = " + ishift + "\n");
					if (limitedDebug)
					    Debug.out("Assign new values to rawTimes[" +
						      varNum + "][" + ishift +
						      "] <==  rawTimesN[" +
						      varNum + "][" + j + "]\n");
					rawTimes[varNum][ishift] = rawTimesN[varNum][j];
					visited = true;
				    }
				}
			    }
			    if (visited)
				timesUpdated = true;
			}

		    } else {
			if (varNum == 0) {
			    rawData = null;
			    rawTimes = null;
			    rawData = new float[varTot][ptCount+100];
			    rawTimes = new long[nRowsSelected*nColsSelected][ptCount+100];
			}
			dataPointCount[idx1] = ptCount;
			for (int j = 0; j < ptCount; j++) {
			    rawData[idx1][j] = rawDataN[idx1][j];
			    rawTimes[idx1][j] = rawTimesN[idx1][j];
			}
		    }

		    // Set the range min/max values used for plotting.
		    yLo = ovRangeMin[idx1];
		    yHi = ovRangeMax[idx1] + (ovRangeMax[idx1] - ovRangeMin[idx1])/10.;
	    

		}  // for (int i = 0; i < numCurvesPerVar; i++)
	    }  // End of    if ((!updateLive) || (initialLoad)) {
	} catch (Exception e) {
	    Debug.out("Exception caught while processing AggregateData array.\n" +
		      e.getMessage());
	    e.printStackTrace();
	}

	if (timerOn) {
	    loadMethodTot = (System.currentTimeMillis() - loadBeg) / 1000;
	    System.out.println("\nOST  Variable  varNum = " + cat + "  " + var +
			       "  " + varNum);
	    if ((!updateLive) || initialLoad)
		System.out.println("Total number of array values returned from DB = " +
				   arrayLimit);
	    else
		System.out.println("Total number of array values returned from DB = " +
				   arrayLimitN);
	    System.out.println("Total time for loadHistorical___Data = " +
			       loadMethodTot + " seconds.");
	    System.out.println("Total DB access time for loadHistorical___Data = " +
			       dbAccess + " seconds.");
	}
	// Done

    }  // loadHistorical___Data


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Build the GUI.
     *
     * @param container container in which GUI will be built.
     */

    void buildUI(Container container) {
	container.setLayout(new BorderLayout());

	plotPanel = new JPanel();
	ppgbl = new GridBagLayout();
	plotPanel.setLayout(ppgbl);
	plotPanel.setBackground(Color.black);

	chartContainerPane = new ChartContainerPanel(this);
	plotPanel.add(chartContainerPane);

	ppc = new GridBagConstraints();
	ppc.gridx = 0;
	ppc.gridy = 0;
	ppc.insets = new Insets(2, 2, 0, 2);  //(8, 4, 0, 5);
	ppc.anchor = GridBagConstraints.NORTH;
	ppc.fill = GridBagConstraints.BOTH;
	ppc.weightx = 1.0;  //1.0;
	ppc.weighty = .75;  //0.0;
	ppgbl.setConstraints(chartContainerPane, ppc);

	// Add panel for the overview data and pan & zoom control
	wideView = new OverView();  //(this);
	plotPanel.add(wideView);

	ppc = new GridBagConstraints();
	ppc.gridx = 0;
	ppc.gridy = 1;

	// Insets are Top, Left, Bottom, Right
	ppc.insets = new Insets(0, 76, 10, 18);  //(8, 4, 0, 5);
	ppc.anchor = GridBagConstraints.NORTH;
	ppc.fill = GridBagConstraints.BOTH;
	ppc.weightx = 1.0;
	ppc.weighty = 0.25;  //0.15;  //1.0;
	ppgbl.setConstraints(wideView, ppc);
	//

	container.add(plotPanel, BorderLayout.CENTER);

	scPane = new StatControlPanel();

	//controls = new ControlPanel();

	JPanel idAndHideControlPanel = new JPanel();
	FlowLayout iaccLayout = new FlowLayout(FlowLayout.LEFT);
	idAndHideControlPanel.setLayout(iaccLayout);

	if (rawData != null)
	    label = new JLabel("Panel dimension : " +
			       chartContainerPane.getWidth() + " X " +
			       chartContainerPane.getHeight());

	else
	    label = new JLabel("Error: accessing raw data from \"timehist.dat\"");

	idLabel = new JLabel(fsName + " (" + type + ") Time History Plot");
	idAndHideControlPanel.add(idLabel);

	cpHideButt = new JButton("Hide Controls");
	cpHideButt.setFont(new Font("helvetica", Font.BOLD, 10));
	cpHideButt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    if (buttonLabel.indexOf("Hide") < 0) {
				showControls();
				cpHideButt.setText("Hide Controls");
			    } else if (buttonLabel.indexOf("Show") < 0) {
				hideControls();
				cpHideButt.setText("Show Controls");
			    }

			    //catPanel.selectAll();
			}
		    });
	idAndHideControlPanel.add(cpHideButt);

	ovpHideButt = new JButton("Hide Overview Plot");
	ovpHideButt.setFont(new Font("helvetica", Font.BOLD, 10));
	ovpHideButt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    if (buttonLabel.indexOf("Hide") < 0) {
				showOverviewPlot();
				ovpHideButt.setText("Hide  Overview Plot");
			    } else if (buttonLabel.indexOf("Show") < 0) {
				hideOverviewPlot();
				ovpHideButt.setText("Show  Overview Plot");
			    }
			    //catPanel.selectAll();
			}
		    });
	idAndHideControlPanel.add(ovpHideButt);
    
	container.add(scPane, BorderLayout.SOUTH);
	//container.add(idLabel, BorderLayout.NORTH);
	container.add(idAndHideControlPanel, BorderLayout.NORTH);

	//ra.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
	chartContainerPane.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
	label.setAlignmentX(
	    java.awt.Component.LEFT_ALIGNMENT);  // Unecessary, but won't hurt.

	updateControlValues();
	//this.thpFrame.pack();
	//this.thpFrame.setVisible(true);


	//  Added timer start in case HEARTBEAT came thru as prefs granularity.
	if (granularity == HEARTBEAT) {
	    //setRefresh(refreshRate, 3600000);

	    refreshPlotFrame();
	}

    }  // buildUI


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Hide the portion of the frame containing the control wodgets.
     */

    void hideControls() {

	Dimension pf2Size = thpFrame.getSize();
	//Dimension chartSize = chartPanel.getSize();
	//Dimension ovSize = wideView.getSize();
	lastCPDimension = scPane.getSize();


	//Debug.out("PlotFrame2 size = " + pf2Size.toString());
	//Debug.out("Chart size = " + chartSize.toString());
	//Debug.out("Overview size = " + ovSize.toString());
	//Debug.out("Control panel size = " + cpSize.toString());

	thpFrame.setSize(pf2Size.width, pf2Size.height - lastCPDimension.height);

	scPane.setVisible(false);
	thpFrame.validate();

    }  // hideControls


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Un-hide the portion of the frame containing the control wodgets.
     */

    void showControls() {

	Dimension pf2Size = thpFrame.getSize();
	Dimension chartSize = chartPanel.getSize();
	thpFrame.setSize(pf2Size.width, pf2Size.height + lastCPDimension.height);
	scPane.setSize(pf2Size.width,lastCPDimension.height);

	scPane.setVisible(true);
	chartPanel.setSize(chartSize.width, chartSize.height);
	thpFrame.validate();

    }  // showControls


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Hide the portion of the frame containing the overview (pan/zoom) control.
     */

    void hideOverviewPlot() {

	Dimension pf2Size = thpFrame.getSize();
	//Dimension chartSize = chartPanel.getSize();
        lastOVDimension = wideView.getSize();
	//Dimension cpSize = scPane.getSize();

	thpFrame.setSize(pf2Size.width, pf2Size.height - lastOVDimension.height);

	wideView.setVisible(false);
	thpFrame.validate();

    }  // hideOverviewPlot


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Un-hide the portion of the frame containing the overview (pan/zoom) control.
     */

    void showOverviewPlot() {

	Dimension pf2Size = thpFrame.getSize();
	Dimension chartSize = chartPanel.getSize();
	thpFrame.setSize(pf2Size.width, pf2Size.height + lastOVDimension.height);
	wideView.setSize(pf2Size.width, lastOVDimension.height);
	chartPanel.setSize(chartSize.width, chartSize.height);
	
	wideView.setVisible(true);
	thpFrame.validate();

    }  // showOverviewPlot


    /*    ***
    public static void main(String argv[]) {

	JFrame f = new JFrame("PlotFrame Demo");

	f.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) {
		    System.exit(0);
		}
	    });

	PlotFrame2 pf = new PlotFrame2("Test", "Demo", "OP1", "Density");
	pf.buildUI(f.getContentPane());
	f.pack();
	f.setVisible(true);
    }
    ***   */


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Calculate the text string associated with the specific category, variable &
     * curve to be plotted in the statistics band above control widgets.
     *
     * @param catVarCurve category variable & curve for which label is calculated.
     */

    String getDataText(String catVarCurve) {

	//if (granularity == Database.RAW) return "";
	if (rawData == null) return " : Agg = ???  Max = ???  Min = ???  Avg = ???";

	String [] cv = catVarCurve.split(" : ");
	//Debug.out("arg = " + catVarCurve + "\nnRowsSelected = " + nRowsSelected +
		  //"  nColsSelected = " + nColsSelected + "  nCurvesSelected = " +
		  //nCurvesSelected + "  cv.length = " + cv.length);
	//Debug.out("cats2Plot.length = " + cats2Plot.length + "  vars2Plot.length = " +
		  //vars2Plot.length + "  crvs2Plot.length = " + crvs2Plot.length);

	if ((!isAnAggPlot && (cats2Plot == null)) || (vars2Plot == null) ||
	    (!isAnAggPlot && (cats2Plot.length == 0)) || (vars2Plot.length == 0)) {
	    //if (cats2Plot == null)
		//Debug.out("cats2Plot is null.");
	    //Debug.out("catVarCurve = " + catVarCurve);
	    return " : Max = ???  Min = ???  Avg = ???";
	}

	//Debug.out("# of rows selected = " + nRowsSelected);
	for (int i = 0; i < nRowsSelected; i++) {
	    for (int j = 0; j < nColsSelected; j++) {
		for (int k = 0; k < nCurvesSelected; k++) {
		    //Debug.out("i, j, k = " + i + ", " + j + ", " + k);
		    if ((cv[0].equals(cats2Plot[i]) && cv[1].equals(vars2Plot[j]) &&
			cv[2].equals(crvs2Plot[k])) ||
			(isAnAggPlot && cv[1].equals(vars2Plot[j]) &&
			 cv[2].equals(crvs2Plot[k]))) {

			int statidx = i * nColsSelected * nCurvesSelected +
			    j * nCurvesSelected + k;
			//Debug.out("Return stats for " + cats2Plot[i] + ":" +
				  //vars2Plot[j] + "  index = " + statidx);

			if (dataPointCount[statidx] <= 0) {
			    return " : No Aggregate Data For This Curve";
			}

			if (localDebug)
			    Debug.out("calculate Max : Min : Avg for " + catVarCurve +
				      "\ncurve # " + statidx + "  # of pts in curve = " +
				      dataPointCount[statidx]);

			aggVal = 0.0;
			boolean firsttime = true;
			maxVal = 0.;
			minVal = maxVal;
			//r (int ii = Math.max(0, startIndex);
			     // < Math.min(dataPointCount[statidx], stopIndex); ii++) {
			int tindx = ovIDX / nCurvesSelected;
			long t0 = tsStartPlot.getTime();
			long t1 = tsEndPlot.getTime();

			int ptCnt = 0;
			for (int ii = 0; ii < dataPointCount[statidx]; ii++) {
			    if ((rawTimes[tindx][ii] >= t0) && (rawTimes[tindx][ii] <= t1)) {
				ptCnt++;
				aggVal += rawData[statidx][ii];
				if (firsttime) {
				    maxVal = rawData[statidx][ii];
				    minVal = maxVal;
				    firsttime = false;
				} else {
				    maxVal = Math.max(maxVal, rawData[statidx][ii]);
				    minVal = Math.min(minVal, rawData[statidx][ii]);
				}
			    }
			}
			//Debug.out("# of points in interval = " + ptCnt);
			avgVal = aggVal / ((double) ptCnt);

			return  " : Max = " +
			    TextFormatter.format(new Double(maxVal), 4) +
			    "  Min = " +
			    TextFormatter.format(new Double(minVal), 4) +
			    "  Avg = " +
			    TextFormatter.format(new Double(avgVal), 4);

			//return " : Agg = " + aggVal[j] + "  Max = " +
			    //maxVal[j] + "  Min = " + minVal[j] +
			    //"  Avg = " + avgVal[j];
		    }
		}
	    }
	}
	//Debug.out("Unable to define stats. Returning ???");
	return " : Max = ???  Min = ???  Avg = ???";
	    
    }  // getDataText


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Get the plot curve color for a specific category, variable & curve.
     */

    Color getDataColor(String catVarCurve) {

	if (localDebug)
	    Debug.out("catVarCurve arg = " + catVarCurve);

	String [] cv = catVarCurve.split(" : ");

	if (legendColors == null)
	    return Color.lightGray;

	if ((!isAnAggPlot && (cats2Plot == null)) || (vars2Plot == null) ||
	    (!isAnAggPlot && (cats2Plot.length == 0)) || (vars2Plot.length == 0))
	    return Color.lightGray;

	int cNumb = -1;  //0;
	int jdx = 0;
	for (int i = 0; i < cats2Plot.length; i++) {
	    for (int j = 0; j < vars2Plot.length; j++) {
		for (int k = 0; k < nCurvesSelected; k++) {
		    if (dataPointCount[jdx] > 0) {
			cNumb++;
			if (cNumb == legendColors.length)
			    cNumb = 0;
		    }
		    if ((cv[0].equals(cats2Plot[i]) && cv[1].equals(vars2Plot[j]) &&
			 cv[2].equals(crvs2Plot[k])) || (isAnAggPlot &&
							 cv[1].equals(vars2Plot[j]) &&
							 cv[2].equals(crvs2Plot[k]))) {
			if ((dataPointCount[jdx] > 0) && (legendColors.length > cNumb))
			    return legendColors[cNumb];
			else
			    break;
		    }
		    jdx++;
		}
	    }
	}

	return Color.lightGray;

    }  // getDataColor


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to implement the chart container panel to hold the JFreeChart
     * time series plot.
     */

    public class ChartContainerPanel extends JPanel {

	Dimension preferredSize = new Dimension(1000, 350);  //(1000, 350);


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for chart container panel.
	 *
	 * @param f PlotFrame2 object to contain this panel.
	 */

	public ChartContainerPanel(PlotFrame2 f) {

	    super();

	    //setDoubleBuffered(true);

	    //setPreferredSize(preferredSize);

	    ccgbl = new GridBagLayout();
	    setLayout(ccgbl);
	    GridBagConstraints c;

	    //chartPanel = (ChartPanel) createChartPanel();
	    chartPanel = createChartPanel();
	    //chartPanel.setPreferredSize(preferredSize);
	    chartPanel.setMouseZoomable(true, false);
	    add(chartPanel);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 0;
	    c.insets = new Insets(2, 2, 0, 2);  //(8, 4, 0, 5);
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 1.0;  //1.0;
	    c.weighty = 1.0;
	    ccgbl.setConstraints(chartPanel, c);

	}  // ChartContainerPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return preferred size for this panel.
	 */

	public Dimension getPreferredSize() {

	    //Debug.out("preferredSize = " + preferredSize);
	    return preferredSize;

	}  // getPreferredSize


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Create the chart panel.
	 */

	public  ChartPanel createChartPanel() {

	    JFreeChart chart = createChart(createDataset());
	    return new ChartPanel(chart);

	}  // createChartPanel

    }  // ChartContainerPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Define the JFreeChart chart.
     *
     * @param dataset dataset to be plotted in the chart.
     */

    public JFreeChart createChart(XYDataset dataset) {

	// Load data for settings from last "Refresh" time
	String [] selectedRows = lastRefreshPlotParams.getCategories();
	String [] selectedVars = lastRefreshPlotParams.getVariables();
	String [] selectedCurves = lastRefreshPlotParams.getCurves();

	int savedGranularity = granularity;
	granularity = lastRefreshPlotParams.getGranularity();

	boolean showLegend = true;
	//if (((isAnAggPlot) && (nColsSelected * nCurvesSelected > 8)) ||
	    //(nRowsSelected * nColsSelected * nCurvesSelected > 8))
	    //showLegend = false;
	if (((isAnAggPlot) && (selectedVars.length * selectedCurves.length > 8)) ||
	    (selectedRows.length * selectedVars.length * selectedCurves.length > 8))
	    showLegend = false;
	    

	//if (fromRefresh && (nRowsSelected > 0 && nColsSelected > 0 && nCurvesSelected > 0) &&
	    //(!noVarSelected)) {
	if (fromRefresh && (selectedRows.length > 0 && selectedVars.length > 0 && selectedCurves.length > 0) &&
	    (!noVarSelected)) {
	    //Debug.out("nRowsSelected = " + nRowsSelected +
		      //"   nColsSelected = " + nColsSelected +
		      //"   nCurvesSelected = " + nCurvesSelected);

	    pD = new PlotDescriptor();
	}

	chart = ChartFactory.createTimeSeriesChart(
	    fsName + " - " + type + " data",  // title
	    "Date",             // x-axis label
	    yAxisLabel,         // y-axis label
	    dataset,            // data
	    showLegend,               // create legend
	    false,              // generate tooltips
	    false               // generate URLs
            );

	chart.setBackgroundPaint(Color.black);  //white);  // Border color
	chart.setBorderVisible(true);
	TextTitle tTitle = chart.getTitle();
	tTitle.setPaint(Color.white);

	XYPlot plot = (XYPlot) chart.getPlot();
	plot.setBackgroundPaint(Color.black);  //lightGray);
	plot.setDomainGridlinePaint(Color.white);
	plot.setRangeGridlinePaint(Color.white);
	plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
	plot.setDomainCrosshairVisible(false);
	plot.setRangeCrosshairVisible(false);

	// Check need for logorihmic axis
	if (useLogRangeAxis) {
	    LogarithmicAxis rangeAxis2 = new LogarithmicAxis(yAxisLabel);
	    plot.setRangeAxis(0, rangeAxis2);
	}

	// Default axis annotation is in black. So if the chart background
	// Paint was set to black above, we need to set axis labels, 
	// tick labels and tick marks to be a contrasting color.
	plot.getDomainAxis().setLabelPaint(Color.white);
	plot.getDomainAxis().setTickLabelPaint(Color.white);
	plot.getDomainAxis().setTickMarkPaint(Color.white);
	plot.getRangeAxis().setLabelPaint(Color.white);
	plot.getRangeAxis().setTickLabelPaint(Color.white);
	plot.getRangeAxis().setTickMarkPaint(Color.white);

	// Grab the colors from the legend.
	//int nCurves = nRowsSelected * nColsSelected * nCurvesSelected;
	int nCurves = selectedRows.length * selectedVars.length * selectedCurves.length;
	if (localDebug) {
	    Debug.out("selectedRows.length, selectedVars.length, selectedCurves.length = " + 
		      selectedRows.length + ", " + selectedVars.length +
		      ", " + selectedCurves.length);
	    Debug.out("Dimension legendColors to size = " + nCurves);
	}
	legendColors = new Color[nCurves];
	LegendItemCollection lic = plot.getLegendItems();
	for (int i = 0; i < lic.getItemCount(); i++) {
	    LegendItem li = lic.get(i);
	    legendColors[i] = (Color) li.getLinePaint();
	    //if (localDebug)
	        //Debug.out("Line Paint for legend " + i + " = " +
	        //legendColors[i]);
	}
        
	XYItemRenderer r = plot.getRenderer();
	if (r instanceof XYLineAndShapeRenderer) {
	    XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
	    renderer.setBaseShapesVisible(showIcons);  // Controls icons at data pts.
	    renderer.setBaseShapesFilled(false);
	}
        
	DateAxis axis = (DateAxis) plot.getDomainAxis();
	if (granularity == Database.HOUR)
	    axis.setDateFormatOverride(new SimpleDateFormat("MM/dd HH"));  //("dd-MMM-yy"));
	else if (granularity == Database.DAY)
	    axis.setDateFormatOverride(new SimpleDateFormat("MM/dd/yy"));  //("dd-MMM-yy"));
	else if (granularity == Database.WEEK)
	    axis.setDateFormatOverride(new SimpleDateFormat("MM/dd/yy"));  //("dd-MMM-yy"));
	else if (granularity == Database.MONTH)
	    axis.setDateFormatOverride(new SimpleDateFormat("MM/yy"));  //("dd-MMM-yy"));
	else if (granularity == Database.YEAR)
	    axis.setDateFormatOverride(new SimpleDateFormat("yyyy HH:mm"));  //("dd-MMM-yy"));
	else  // granualrity == Database.RAW or HEARTBEAT
	    axis.setDateFormatOverride(new SimpleDateFormat("MM/dd HH:mm:ss"));  //("dd-MMM-yy"));

	// Reset granularity;
	granularity = savedGranularity;

	return chart;

    }  // createChart


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Create the dataset for the set of TimeSeries curves to be plotted.
     */

    public  XYDataset createDataset() {

	// Load data for settings from last "Refresh" time
	String [] selectedRows = lastRefreshPlotParams.getCategories();
	String [] selectedVars = lastRefreshPlotParams.getVariables();
	String [] selectedCurves = lastRefreshPlotParams.getCurves();

	int savedGranularity = granularity;
	granularity = lastRefreshPlotParams.getGranularity();

	if (localDebug)
	    Debug.out("Granularity used for timeSeries generation is " + granularity);

	//int nLines = nRowsSelected * nColsSelected * nCurvesSelected;
	int nLines = selectedRows.length * selectedVars.length * selectedCurves.length;
	//TimeSeries [] ts = new TimeSeries[nRowsSelected * nColsSelected *
					  //nCurvesSelected];
	TimeSeries [] ts = new TimeSeries[nLines];

	int row = 0;
	int col = 0;
	int crv = -1;
	long tmBeg = tsStartPlot.getTime();
	long tmEnd = tsEndPlot.getTime();

	for (int i = 0; i < nLines; i++) {
	    //if (localDebug)
	        //Debug.out("Plot curve # " + i);

	    //if (dataPointCount[i] <=0) {
		//Debug.out("dataPointCount[" + i + "] <= 0. Skip it.");
		//}

	    crv++;
	    if (crv == selectedCurves.length) {
		crv = 0;
		col++;
		if (col == selectedVars.length) {
		    col = 0;
		    row++;
		}
	    }
	    int tindx = i / selectedCurves.length;

	    //String catvar = cats2Plot[row] + " " + vars2Plot[col] + crvs2Plot[crv];
	    String catvar = selectedRows[row] + " " + selectedVars[col] + selectedCurves[crv];
	    //Debug.out("Plot line for " + catvar);
	    if (granularity == Database.HOUR)
		ts[i] = new TimeSeries(catvar, Hour.class);
	    else if (granularity == Database.DAY)
		ts[i] = new TimeSeries(catvar, Day.class);
	    else if (granularity == Database.WEEK)
		ts[i] = new TimeSeries(catvar, Week.class);
	    else if (granularity == Database.MONTH)
		ts[i] = new TimeSeries(catvar, Month.class);
	    else if (granularity == Database.YEAR)
		ts[i] = new TimeSeries(catvar, Year.class);
	    else if ((granularity == Database.RAW) ||(granularity == HEARTBEAT)) {
		//if (limitedDebug) {
		    //System.out.println("\n\n" + i + "  " + catvar);
		//}
		ts[i] = new TimeSeries(catvar, Second.class);
	    }

	    if (rawData == null) {
		if (localDebug)
		    Debug.out("ERROR     RawData = null");
		return null;
	    }

	    if (rawData[i] == null) {
		if (localDebug)
		    Debug.out("ERROR     RawData[" + i + "] = null");
		return null;
	    }

	    int dsSize = dataPointCount[i];  //rawData[i].length;

	    if (localDebug) {
		Debug.out("Size of " + catvar + " data set # " + i + " = " + dsSize);
	    }

	    int includeCnt = 0;
	    for (int j = 0; j <= dataPointCount[i]-1; j++) {
		//if (localDebug)
		    //Debug.out("tindx " + tindx + "  j " + j + " secs " +
		                //rawTimes[tindx][j]);
		long rawT = rawTimes[tindx][j];
		if ((rawT >= tmBeg) && (rawT <= tmEnd)) {
		    float rangeVal = rawData[i][j];
		    if (useLogRangeAxis && (rangeVal <= 0.0))
			rangeVal = 1;

		    try {
			if (granularity == Database.HOUR)
			    ts[i].add(new Hour(new Date(rawT)), rangeVal, false);
			else if (granularity == Database.DAY)
			    ts[i].add(new Day(new Date(rawT)), rangeVal, false);
			else if (granularity == Database.WEEK)
			    ts[i].add(new Week(new Date(rawT)), rangeVal, false);
			else if (granularity == Database.MONTH)
			    ts[i].add(new Month(new Date(rawT)), rangeVal, false);
			else if (granularity == Database.YEAR)
			    ts[i].add(new Year(new Date(rawT)), rangeVal, false);
			else if ((granularity == Database.RAW) ||
				 (granularity == HEARTBEAT)) {
			    includeCnt++;
			    //if (localDebug) {
			        //System.out.println(i + " " + j + " " +
						   //(new Second(new Date(rawT))).toString() +
						   //"  " + rangeVal);
			    //}
			    ts[i].add(new Second(new Date(rawT)), rangeVal, false);

			}
		    } catch (org.jfree.data.general.SeriesException e) {
			if (localDebug)
			    Debug.out("Exception detected while calculating Timestamp objects. Problem at rawT = " + rawT + "\n" +
				      e.getMessage());
		    }
		}
	    }
	    if (localDebug)
		Debug.out("# of points included in time series = " + includeCnt +
			  "\n# of curves to be plotted = " + nLines);
	}
	if (localDebug)
	    Debug.out("\nTimeSeries array generation complete\n");

	// *****************************************************************
	//  More than 150 demo applications are included with the JFreeChar
	//  Developer Guide...for more information, see
	//
	//  >   http://www.object-refinery.com/jfreechart/guide.html
	//
	// ******************************************************************

	TimeSeriesCollection dataset = new TimeSeriesCollection();

	for (int i = 0; i < nLines; i++) {
	    if (dataPointCount[i] >0) {
		if (localDebug)
		    Debug.out("Add timeSeries # " + i + " to chart dataset.");
		dataset.addSeries(ts[i]);
	    }
	}

	// Reset granularity;
	granularity = savedGranularity;

	return dataset;

    }  // createDataset



    //////////////////////////////////////////////////////////////////////////////

    /**
     * Replot the chart, overview and update the control widgets with the latest data
     * retrieved during this heartbeat refresh cycle.
     */

    void updateXYPlotPanel() {

	//System.out.println("\nupdateXYPlotPanel");

	fromRefresh = false;

	//String [] selectedRows = controls.getCategoryPanel().getSelected();
	//String [] selectedVars = controls.getVariablePanel().getSelected();
	//int vTot = nRowsSelected * nColsSelected * nCurvesSelected;

	// Load up cats, vars & curves from "lastRefreshPlotParams"
	//%%%
	String [] selectedRows = lastRefreshPlotParams.getCategories();
	String [] selectedVars = lastRefreshPlotParams.getVariables();
	String [] selectedCurves = lastRefreshPlotParams.getCurves();

	/***
	Debug.out("selected categories :");
	for (int i = 0; i < selectedRows.length; i++)
	    System.out.println(selectedRows[i]);
	Debug.out("selected variables :");
	for (int i = 0; i < selectedVars.length; i++)
	    System.out.println(selectedVars[i]);
	Debug.out("selected curves :");
	for (int i = 0; i < selectedCurves.length; i++)
	    System.out.println(selectedCurves[i]);
	***/

	int vTot = selectedRows.length * selectedVars.length * selectedCurves.length;

	int savedGranularity = granularity;
	granularity = lastRefreshPlotParams.getGranularity();

	// Calculate the difference between the end of the live update interval and
	// the current time. If it's long enough then load data for that period and
	// create a new dataset for those values.

	long now = System.currentTimeMillis();
	if ((now - lastLiveNtrvlEnd) < 5000)
	    return;

	lastLiveNtrvlStart = tsStart.getTime();
	lastLiveNtrvlEnd = tsEnd.getTime();

	for (int irow = 0; irow < selectedRows.length; irow++) {
	    //cats2Plot[irow] = selectedRows[irow];
	    for (int ivar = 0; ivar < selectedVars.length; ivar++) {
		//vars2Plot[ivar] = selectedVars[ivar];
		int index = irow * selectedVars.length + ivar;
		if (localDebug)
		    Debug.out("Read data for row, col = " +
			      irow + ", " + ivar);

		//loadHistoricalData(this.type, this.subIdent,
				   //selectedRows[irow], selectedVars[ivar],
				   //crvs2Plot, index, vTot);

		thpFrame.setCursor(hourglassCursor);

		// loadHistorical___Data footprint:
		//void loadHistorical___Data(String type, int subId, String cat, String var,
					   //String[] curves, int varNum, int varTot) 

		loadHistoricalData(this.type, this.subIdent,
				   selectedRows[irow], selectedVars[ivar],
				   selectedCurves, index, vTot);

		thpFrame.setCursor(normalCursor);

		/***
		if (dataPointCount[0] <= 0) {
		    if (localDebug)
			Debug.out("Zero-length array result from data load request.");
		    if (granularity == HEARTBEAT) {
			LwatchAlert lwAlert = new LwatchAlert(this);
			lwAlert.displayDialog(true,  // modal = true
					      "No data found. Hardware may be down.",
					      1);  // type  1 = info with "continue
		    }

		    redrawChartPanel();
		    wideView.repaint();

		    return;
		}
		***/

		//%%%tsStart = new Timestamp(rawTimes[0][0]);
		//%%%tsEnd = new Timestamp(rawTimes[0][dataPointCount[0]-1]);

	    }
	}

	if (skipThisUpdate) {
	    //Debug.out("\nSKIPPING update...\n");
	    skipThisUpdate = false;
	    return;
	}

	// Check for No data found
	boolean someDataFound = false;
	for (int i = 0; i < vTot; i++) {
	    if (dataPointCount[i] > 0) {
		someDataFound = true;
		break;
	    }
	}
	if (!someDataFound) {
	    LwatchAlert lwAlert = new LwatchAlert(this);
	    lwAlert.displayDialog(true,  // modal = true
				  "No data found.",
				  1);  // type  1 = info with "continue

	    redrawChartPanel();
	    wideView.repaint();


	    // Reset granularity;
	    granularity = savedGranularity;

	    return;
	}


	// Set up the overview plot interval
	nCurvesSelected = controls.getCurvePanel().getNumCurvesSelected();
	int tindx = ovIDX / nCurvesSelected;
	int uindx = tindx;
	if (dataPointCount[uindx] < 0) {
	    int kk = 0;
	    while ((kk < nCurvesSelected) && (dataPointCount[uindx] < 0))
		uindx++;
	}
	tsStart = new Timestamp(rawTimes[tindx][0]);
	tsEnd = new Timestamp(rawTimes[tindx][dataPointCount[uindx]-1]);
	tsStartPlot = new Timestamp(rawTimes[tindx][0]);
	tsEndPlot = new Timestamp(rawTimes[tindx][dataPointCount[uindx]-1]);
	ovEndPlot = new Timestamp(tsStartPlot.getTime());
	ovStartPlot = new Timestamp(tsStartPlot.getTime());
	//%%%

	thpFrame.setCursor(hourglassCursor);

	redrawChartPanel();

	//Graphics2D g2 = (Graphics2D) chartPanel.getGraphics();
	//XYPlot plot = (XYPlot) chart.getPlot();
	//TimeSeriesCollection tsColl = (TimeSeriesCollection) plot.getDataset();
	//tsColl.removeAllSeries();
	//plot.setDataset(createDataset());

	wideView.repaint();

	// Reset granularity;
	granularity = savedGranularity;

	updateControlValues();

	thpFrame.setCursor(normalCursor);

	//int datasetIndex = 0;
	//plot.render(g2, chartPanel.getScreenDataArea(), datasetIndex,
		    //chartPanel.getChartRenderingInfo().getPlotInfo(), null);

    }  // updateXYPlotPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Start heartbeat refresh scheduling.
     */

    synchronized protected void startRefresh() {
	//Debug.out(" " + this + "  keepAliveMaxInactivity = " + keepAliveMaxInactivity);
	if (refreshMaxInactivity > 0) {
	    //Debug.out("Actually do it : " + this);
	    resetRefresh();
	    refreshTimer = new Timer();
	    refreshTimer.scheduleAtFixedRate(new RefreshTask(), 0, 2*1000);
	}
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Stop heartbeat refresh.
     */

    protected void stopRefresh() {
	//Debug.out(" " + this);
	if (refreshTimer != null) {
	    refreshTimer.cancel();
	    refreshTimer = null;
	}
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Set heartbeat refresh interval.
     */

    synchronized public void setRefresh(int interval, int maxTime) {
	//if (localDebug)
	    //Debug.out("Setting RefreshInterval to : " + interval + "   " + this);
	//Debug.out("Setting RefreshInterval to : " + interval + "   " + this);
	refreshInterval = interval;
	refreshMaxInactivity = maxTime;
	stopRefresh();
	startRefresh();
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing heartbeat timer refresh of history plot.
     */

    class RefreshTask extends TimerTask {
	public void run() {
	    //synchronized (BaseFtpFileSystem.this) {
		long currentTime = System.currentTimeMillis();
		if (currentTime > nextRefreshTime) {
		    //System.out.println("\nREFRESH\n");
		    updateXYPlotPanel();
		    nextRefreshTime =
			currentTime + refreshInterval;
		}
	    //}
	}
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reset heartbeat refresh timer.
     */

    synchronized void resetRefresh() {
	//Debug.out(" " + this);
	long currentTime = System.currentTimeMillis();
	nextRefreshTime = currentTime+refreshInterval;
	refreshStopTime = currentTime+refreshMaxInactivity;
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Truncate string to specified length.
     *
     * @param s numeric string to truncate.
     *
     * @param decimalDigits number of digits to right of deciaml point to preserve.
     */

    String truncateVal(String s, int decimalDigits) {

	if (s.indexOf(".") < 0)
	    return s;

	if (s.length() <= (s.indexOf(".")+decimalDigits+1))
	    return s;

	return s.substring(0, s.indexOf(".")+decimalDigits+1);
	
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class to build statistics and control panel.
     */

    class StatControlPanel extends JPanel {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for statistics and control panel.
	 */

	StatControlPanel() {
	    super();

	    BorderLayout borderLayout = new BorderLayout();
	    setLayout(borderLayout);

	    JPanel statsAndControlsPane = new JPanel();

	    statsReportPane = new JPanel(new BorderLayout());
	    if ((cats2Plot != null) && (vars2Plot != null)) {
		//if (localDebug)
		    //Debug.out(cats2Plot[0] + " : " + vars2Plot[0] +
			      //" : Agg = " + aggVal[0] +
			      //"  Max = " + maxVal[0] + "  Min = " + minVal[0] + 
			      //"  Avg = " + avgVal[0]);

		//ammaLabel = new JLabel(cats2Plot[0] + " : " + vars2Plot[0] + " : Avg" +
				       //getDataText(cats2Plot[0] + " : " +
						   //vars2Plot[0] + " : Avg"));
		if (!isAnAggPlot)
		    ammaLabel = new JLabel(cats2Plot[0] + " : " + vars2Plot[0] + " : " +
					   crvs2Plot[0] +
					   getDataText(cats2Plot[0] + " : " +
						       vars2Plot[0] + " : " + crvs2Plot[0]));
		else
		    ammaLabel = new JLabel(" : " + vars2Plot[0] + " : " +
					   crvs2Plot[0] +
					   getDataText(cats2Plot[0] + " : " +
						       vars2Plot[0] + " : " + crvs2Plot[0]));
		/***
				       " : Agg = " +
				       TextFormatter.format(new Double(aggVal), 4) +
				       "  Max = " +
				       TextFormatter.format(new Double(maxVal), 4) +
				       "  Min = " +
				       TextFormatter.format(new Double(minVal), 4) +
				       "  Avg = " +
				       TextFormatter.format(new Double(avgVal), 4));
		***/
		    //Debug.out("legendColors[0] = " + legendColors[0]);
		    statsReportPane.setBackground(legendColors[0]);
		//} else {
		    //Debug.out("At least one of aggVal, maxVal, minVal or avgVal is null");
		//}
	    } else {
		ammaLabel = new JLabel("No Variables Chosen");
		statsReportPane.setBackground(Color.lightGray);
	    }
	    statsReportPane.add(ammaLabel, BorderLayout.CENTER);
	    add(statsReportPane, BorderLayout.NORTH);

	    GridBagLayout gbl = new GridBagLayout();
	    statsAndControlsPane.setLayout(gbl);
	    GridBagConstraints c;

	    statPanel = new JPanel();
	    //statPanel.setBackground(Color.black);
	    //if (vars2Plot != null) {
	    vsPane = new VarStatPane(cats2Plot, vars2Plot, crvs2Plot);  //,
				     //aggVal, maxVal,
				     //minVal, avgVal);

		if (vsPane == null)
		    Debug.out("Problem creating VarStatPane object vsPane.");

		statPanel.add(vsPane);
	    //}
	    statsAndControlsPane.add(statPanel);
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 0;
	    c.insets = new Insets(2, 2, 0, 2);  //(8, 4, 0, 5);
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.5;  //1.0;
	    c.weighty = 0.0;
	    gbl.setConstraints(statPanel, c);


	    controls = new ControlPanel();
	    statsAndControlsPane.add(controls);
	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 0;
	    c.insets = new Insets(8, 4, 0, 5);
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.5;  //1.0;
	    c.weighty = 0.0;
	    gbl.setConstraints(controls, c);

	    add(statsAndControlsPane, BorderLayout.CENTER);

	}  // StatControlPanel

    }  // StatControlPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing controls to allow modification of time interval, variables,
     * categories and curves to plot.
     */

    class ControlPanel extends JPanel {

	CurvePanel crvPanel;
	VariablePanel varPanel;
	CategoryPanel catPanel;
	EndDurationPanel endP;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for Control panel.
	 */

	ControlPanel() {

	    super();

	    BorderLayout controlLayout = new BorderLayout();
	    setLayout(controlLayout);

	    GridBagConstraints c;

	    Date now = new Date();

	    if (localDebug)
		Debug.out("\n" + DateFormat.getTimeInstance().format(now) + "\n");


	    // Add panel for statistics
	    //statPanel.setMinimumSize(new Dimension(200, 200));
	    //add(statPanel, BorderLayout.WEST);  //NORTH);


	    //--------------------------------------------------------------
	    // Add Controls
	    //--------------------------------------------------------------

	    JPanel conPanel = new JPanel();
	    GridBagLayout conLayout = new GridBagLayout();
	    conPanel.setLayout(conLayout);

	    // Configure the Layout for the panel.

	    //--------------------------------------------------------------
	    // Start Selection Control Widgets
	    //--------------------------------------------------------------
	    JLabel startLabel = new JLabel("       Start");
	    startLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    conPanel.add(startLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //0.0;
	    c.weighty = 0.0;
	    conLayout.setConstraints(startLabel, c);

	    JLabel yrStartLabel = new JLabel("Year");
	    yrStartLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    yrStartLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(yrStartLabel);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 0;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(yrStartLabel, c);


	    yrChooser = new YearChooser("start");
	    conPanel.add(yrChooser);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(yrChooser, c);


	    JLabel monthStartLabel = new JLabel("Month");
	    monthStartLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    monthStartLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(monthStartLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 0;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(monthStartLabel, c);

	    monthChooser = new MonthChooser("start");
	    conPanel.add(monthChooser);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(monthChooser, c);


	    JLabel dayStartLabel = new JLabel("Day");
	    dayStartLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    dayStartLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(dayStartLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 3;
	    c.gridy = 0;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(dayStartLabel, c);

	    dayChooser = new DayChooser("start");
	    conPanel.add(dayChooser);

	    c = new GridBagConstraints();
	    c.gridx = 3;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(dayChooser, c);


	    JLabel hrStartLabel = new JLabel("Hour");
	    hrStartLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    hrStartLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(hrStartLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 4;
	    c.gridy = 0;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(hrStartLabel, c);

	    hourChooser = new HourChooser("start");
	    conPanel.add(hourChooser);

	    c = new GridBagConstraints();
	    c.gridx = 4;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(hourChooser, c);


	    JLabel minStartLabel = new JLabel("Min");
	    minStartLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    minStartLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(minStartLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 5;
	    c.gridy = 0;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(minStartLabel, c);

	    minuteChooser = new MinuteChooser("start");
	    conPanel.add(minuteChooser);

	    c = new GridBagConstraints();
	    c.gridx = 5;
	    c.gridy = 1;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(minuteChooser, c);


	    //--------------------------------------------------------------
	    // End Selection Control Widgets
	    //--------------------------------------------------------------
	    endP = new EndDurationPanel();
	    conPanel.add(endP);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 3;
	    c.gridheight = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(endP, c);


	    JLabel yrEndLabel = new JLabel("Year");
	    yrEndLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    yrEndLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(yrEndLabel);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(yrEndLabel, c);


	    yrEndChooser = new YearChooser("end");
	    conPanel.add(yrEndChooser);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(yrEndChooser, c);


	    JLabel monthEndLabel = new JLabel("Month");
	    monthEndLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    monthEndLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(monthEndLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(monthEndLabel, c);

	    monthEndChooser = new MonthChooser("end");
	    conPanel.add(monthEndChooser);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(monthEndChooser, c);


	    JLabel dayEndLabel = new JLabel("Day");
	    dayEndLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    dayEndLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(dayEndLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 3;
	    c.gridy = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(dayEndLabel, c);

	    dayEndChooser = new DayChooser("end");
	    conPanel.add(dayEndChooser);

	    c = new GridBagConstraints();
	    c.gridx = 3;
	    c.gridy = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(dayEndChooser, c);


	    JLabel hrEndLabel = new JLabel("Hour");
	    hrEndLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    hrEndLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(hrEndLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 4;
	    c.gridy = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(hrEndLabel, c);

	    hourEndChooser = new HourChooser("end");
	    conPanel.add(hourEndChooser);

	    c = new GridBagConstraints();
	    c.gridx = 4;
	    c.gridy = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(hourEndChooser, c);


	    JLabel minEndLabel = new JLabel("Min");
	    minEndLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    minEndLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(minEndLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 5;
	    c.gridy = 2;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(minEndLabel, c);

	    minuteEndChooser = new MinuteChooser("end");
	    conPanel.add(minuteEndChooser);

	    c = new GridBagConstraints();
	    c.gridx = 5;
	    c.gridy = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(minuteEndChooser, c);


	    //--------------------------------------------------------------
	    // Duration Selection Control Widgets
	    //--------------------------------------------------------------

	    yrDurChooser = new IntegerChooser(0, 5, 0);
	    conPanel.add(yrDurChooser);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 4;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(yrDurChooser, c);


	    monthDurChooser = new IntegerChooser(0, 12, 0);
	    conPanel.add(monthDurChooser);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 4;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(monthDurChooser, c);


	    dayDurChooser = new IntegerChooser(0, 31, 2);
	    conPanel.add(dayDurChooser);

	    c = new GridBagConstraints();
	    c.gridx = 3;
	    c.gridy = 4;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(dayDurChooser, c);


	    hourDurChooser = new IntegerChooser(0, 24, 0);
	    conPanel.add(hourDurChooser);

	    c = new GridBagConstraints();
	    c.gridx = 4;
	    c.gridy = 4;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(hourDurChooser, c);


	    minuteDurChooser = new IntegerChooser(0, 60, 0);
	    conPanel.add(minuteDurChooser);

	    c = new GridBagConstraints();
	    c.gridx = 5;
	    c.gridy = 4;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(minuteDurChooser, c);


	    //--------------------------------------------------------------
	    // Thinning Control Widgets
	    //--------------------------------------------------------------
	    /***
	    JLabel thinningLabel = new JLabel(" Thinning Method");
	    thinningLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    conPanel.add(thinningLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 5;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(thinningLabel, c);

	    thinningChooser = new ThinningChooser();
	    conPanel.add(thinningChooser);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 6;
	    c.insets = new Insets(5, 0, 10, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(thinningChooser, c);
	    ***/


	    //--------------------------------------------------------------
	    //Show Icon Control Widgets
	    //--------------------------------------------------------------
	    IconControl showIconsControl = new IconControl("Show Icons", showIcons);
	    showIconsControl.setFont(new Font("helvetica", Font.BOLD, 10));
	    conPanel.add(showIconsControl);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 5;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(showIconsControl, c);


	    //--------------------------------------------------------------
	    // Log Axis Control Widget
	    //--------------------------------------------------------------
	    AxisControl rangeAxisControl = new AxisControl("Use Log Range Axis",
							   useLogRangeAxis);
	    rangeAxisControl.setFont(new Font("helvetica", Font.BOLD, 10));
	    conPanel.add(rangeAxisControl);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 6;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(rangeAxisControl, c);


	    //--------------------------------------------------------------
	    //Live Upate Control Widgets
	    //--------------------------------------------------------------
	    /*****
	    liveUpdate = new LiveUpdate("Live Update", updateLive);
	    liveUpdate.setFont(new Font("helvetica", Font.BOLD, 10));
	    conPanel.add(liveUpdate);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 7;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(liveUpdate, c);
	    *****/

	    JPanel rawLiveParamPanel = new JPanel();
	    GridBagLayout rlppLayout = new GridBagLayout();
	    rawLiveParamPanel.setLayout(rlppLayout);
	    GridBagConstraints rlppc = null;




	    liveModLabel = new JLabel("Heartbeat Parameters");
	    liveModLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    if (updateLive)
		liveModLabel.setForeground(Color.black);
	    else
		liveModLabel.setForeground(Color.gray);
	    liveModLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    rawLiveParamPanel.add(liveModLabel);

	    rlppc = new GridBagConstraints();
	    rlppc.gridx = 0;
	    rlppc.gridy = 0;
	    //rlppc.gridwidth = 2;
	    rlppc.insets = new Insets(8, 0, 0, 5);
	    rlppc.anchor = GridBagConstraints.WEST;
	    rlppc.fill = GridBagConstraints.BOTH;
	    rlppc.weightx = 0.0;  //1.0;
	    rlppc.weighty = 1.0;
	    rlppLayout.setConstraints(liveModLabel, rlppc);

	    long ldi = liveDisplayInterval / 60000; // minutes
	    String ldiString = String.valueOf(ldi) + " minutes";
	    if (ldi >= (long) 60) {
		ldi /= (long) 60;
		ldiString = String.valueOf(ldi) + " Hours";
	    }

	    rlIntervalLabel = new JLabel("Interval = " + ldiString);
	    rlIntervalLabel.setFont(new Font("helvetica", Font.PLAIN, 10));
	    if (updateLive)
		rlIntervalLabel.setForeground(Color.black);
	    else
		rlIntervalLabel.setForeground(Color.gray);
	    rlIntervalLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    rawLiveParamPanel.add(rlIntervalLabel);

	    rlppc = new GridBagConstraints();
	    rlppc.gridx = 0;
	    rlppc.gridy = 1;
	    rlppc.insets = new Insets(8, 0, 0, 5);
	    rlppc.anchor = GridBagConstraints.WEST;
	    rlppc.fill = GridBagConstraints.BOTH;
	    rlppc.weightx = 0.0;  //1.0;
	    rlppc.weighty = 1.0;
	    rlppLayout.setConstraints(rlIntervalLabel, rlppc);


	    int rlr = refreshRate / 1000;
	    String rlrString = String.valueOf(rlr) + " Seconds";
	    if (rlr > 60) {
		rlr /= 60;
		rlrString = String.valueOf(rlr) + " Minutes";
	    }

	    rlRateLabel = new JLabel("Refresh Rate = " + rlrString);
	    rlRateLabel.setFont(new Font("helvetica", Font.PLAIN, 10));
	    if (updateLive)
		rlRateLabel.setForeground(Color.black);
	    else
		rlRateLabel.setForeground(Color.gray);
	    rlRateLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    rawLiveParamPanel.add(rlRateLabel);

	    rlppc = new GridBagConstraints();
	    rlppc.gridx = 0;
	    rlppc.gridy = 2;
	    rlppc.insets = new Insets(8, 0, 0, 5);
	    rlppc.anchor = GridBagConstraints.WEST;
	    rlppc.fill = GridBagConstraints.BOTH;
	    rlppc.weightx = 0.0;  //1.0;
	    rlppc.weighty = 1.0;
	    rlppLayout.setConstraints(rlRateLabel, rlppc);




	    conPanel.add(rawLiveParamPanel);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 5;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(rawLiveParamPanel, c);


	    changeLiveActivator = new ChangeLivePanel();
	    conPanel.add(changeLiveActivator);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 6;
	    c.insets = new Insets(8, 0, 0, 5);  //8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    conLayout.setConstraints(changeLiveActivator, c);


	    //--------------------------------------------------------------
	    // Granularity Control Widgets
	    //--------------------------------------------------------------
	    JLabel granularityLabel = new JLabel("Granularity");
	    granularityLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    granularityLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    conPanel.add(granularityLabel);
	    
	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 5;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(granularityLabel, c);

	    granularityChooser = new GranularityChooser();
	    conPanel.add(granularityChooser);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 6;
	    c.insets = new Insets(5, 0, 10, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(granularityChooser, c);


	    //--------------------------------------------------------------
	    // Curve Selection Control Widgets
	    //--------------------------------------------------------------
	    JLabel plotListLabel = new JLabel("Data");
	    plotListLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    plotListLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    c = new GridBagConstraints();
	    c.gridx = 6;
	    c.gridy = 0;
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    c.weightx = 0.0;
	    c.weighty = 1.0;
	    conPanel.add(plotListLabel);

	    //Debug.out("Type of data = " + type);
	    crvPanel = new CurvePanel();
	    //JScrollPane crvScrollPane = new JScrollPane(crvPanel);
	    //crvScrollPane.setPreferredSize(new Dimension(80, 25));
	    //conPanel.add(crvScrollPane);
	    crvPanel.setPreferredSize(new Dimension(80, 25));
	    conPanel.add(crvPanel);

	    c = new GridBagConstraints();
	    c.gridx = 6;
	    c.gridy = 1;
	    c.gridheight = 3;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //0.0;
	    c.weighty = 1.0;
	    //conLayout.setConstraints(crvScrollPane, c);
	    conLayout.setConstraints(crvPanel, c);


	    //--------------------------------------------------------------
	    // Variable Selection Control Widgets
	    //--------------------------------------------------------------
	    JLabel varListLabel = new JLabel(type + " Variables");
	    varListLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    varListLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    c = new GridBagConstraints();
	    c.gridx = 7;
	    c.gridy = 0;
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    c.weightx = 0.0;
	    c.weighty = 1.0;
	    conPanel.add(varListLabel);

	    //Debug.out("Type of data = " + type);
	    varPanel = new VariablePanel(type);
	    JScrollPane vpScrollPane = new JScrollPane(varPanel);
	    vpScrollPane.setPreferredSize(new Dimension(140, 25));
	    conPanel.add(vpScrollPane);

	    c = new GridBagConstraints();
	    c.gridx = 7;
	    c.gridy = 1;
	    c.gridheight = 5;
	    c.insets = new Insets(8, 0, 0, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.4;  //0.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(vpScrollPane, c);


	    //--------------------------------------------------------------
	    // Category Selection Control Widgets
	    //--------------------------------------------------------------
	    JLabel categoryListLabel = new JLabel(type);
	    categoryListLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    categoryListLabel.setHorizontalAlignment(SwingConstants.CENTER);
	    c = new GridBagConstraints();
	    c.gridx = 8;
	    c.gridy = 0;
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    c.weightx = 0.0;
	    c.weighty = 1.0;
	    if (!isAnAggPlot) {
		conPanel.add(categoryListLabel);
		conLayout.setConstraints(categoryListLabel, c);
	    }

	    //Debug.out("Type of data = " + type);
	    catPanel = new CategoryPanel(type);
	    JScrollPane cpScrollPane = new JScrollPane(catPanel);
	    cpScrollPane.setPreferredSize(new Dimension(140, 25));
	    if (!isAnAggPlot) {
		conPanel.add(cpScrollPane);

		c = new GridBagConstraints();
		c.gridx = 8;
		c.gridy = 1;
		c.gridheight = 5;
		c.insets = new Insets(8, 0, 0, 5);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0.6;  //0.0;
		c.weighty = 1.0;
		conLayout.setConstraints(cpScrollPane, c);

		JPanel selectAllPanel = new JPanel();
		GridLayout glo = new GridLayout(2,0);
		selectAllPanel.setLayout(glo);

		JButton selectAllCats = new JButton("SelectAll");
		selectAllCats.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    catPanel.selectAll();
			}
		    });
		selectAllPanel.add(selectAllCats);

		JButton deselectAllCats = new JButton("DeselectAll");
		deselectAllCats.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    catPanel.deselectAll();
			}
		    });
		selectAllPanel.add(deselectAllCats);

		conPanel.add(selectAllPanel);
		c = new GridBagConstraints();
		c.gridx = 8;
		c.gridy = 6;
		c.gridheight = 2;
		c.insets = new Insets(8, 0, 0, 5);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0.6;  //0.0;
		c.weighty = 1.0;
		conLayout.setConstraints(selectAllPanel, c);
	    }


	    //--------------------------------------------------------------
	    // Refresh (Submit) Button
	    //--------------------------------------------------------------
	    JButton refresh = new JButton("Refresh");
	    refresh.setBackground(new Color(142, 255, 195));
	    refresh.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    refreshPlotFrame();
			}
		    });
	    conPanel.add(refresh);

	    c = new GridBagConstraints();
	    c.gridx = 3;  // 4
	    c.gridy = 6;
	    c.insets = new Insets(8, 0, 10, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;
	    conLayout.setConstraints(refresh, c);

	    //--------------------------------------------------------------

	    add(conPanel, BorderLayout.CENTER);

	}  // ControlPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the CategoryPanel object to be used in the control panel.
	 */

	CategoryPanel getCategoryPanel() {

	    return catPanel;

	}  // getCategoryPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the VariablePanel object to be used in the control panel.
	 */

	VariablePanel getVariablePanel() {

	    return varPanel;

	}  // getVariablePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the CurvePanel object to be used in the control panel.
	 */

	CurvePanel getCurvePanel() {

	    return crvPanel;
	}  // getCurvePanel


    }  // ControlPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing scrollable container for the statistics buttons.
     */

    class VarStatPane extends JScrollPane {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Contructor for the panel.
	 *
	 * @param cats category array allowing selection of OST, & router.
	 */

	VarStatPane(String [] cats, String [] vars, String [] curves) {  //,
		    //double [] sums, double [] maxs,
		    //double [] mins, double [] avgs) {

	    super();

	    setPreferredSize(new Dimension(180, 220));  //(160, 200));

	    //int eTot = cats.length * vars.length;
	    //String [] listEntries = new String[eTot];
	    //int inext = 0;
	    //for (int i = 0; i < cats.length; i++) {
	        //for (int j = 0; j < vars.length; j++) {
		//    listEntries[inext++] = cats[i] + " - " + vars[j];
		//}
	    //}

	    //JList statsList = new JList(listEntries);
	    //setViewportView(statsList);
	    //getViewport().setBackground(Color.black);

	    StatsPanel statsPanel = new StatsPanel(cats, vars, curves);  //,
						   //sums, maxs, mins, avgs);
	    setViewportView(statsPanel);
	    //setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
	    //setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

	}  // VarStatPane

    }  // VarStatPane


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing button controls for the statistics pane.
     */

    class StatsPanel extends JPanel {

	String [] catNames;
	String [] varNames;
	//double [] aggVals;
	//double [] maxVals;
	//double [] minVals;
	//double [] avgVals;

	//JTextField aggValText;
	//JTextField maxValText;
	//JTextField minValText;
	//JTextField avgValText;

	int loopLimit = 0;
	JButton [] buttons;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for button panel for control of statistics display.
	 */

	StatsPanel(String [] cats, String [] vars, String [] curves) {

	    super();

	    setBackground(Color.lightGray);
	    if (localDebug)
		Debug.out("Create using " + vars[0]);
	    this.catNames = cats;
	    if (vars != null)
		this.varNames = vars;
	    else {
		this.varNames = new String[1];
		this.varNames[0] = "None Selected";
	    }

	    //this.aggVals = sums;
	    //this.maxVals = maxs;
	    //this.minVals = mins;
	    //this.avgVals = avgs;

	    loopLimit = cats.length * vars.length;
	    if (loopLimit > 0) {
		GridBagLayout gridbag = new GridBagLayout();
		setLayout(gridbag);
		GridBagConstraints c;
		//GridLayout gridLay = new GridLayout(0,1);
		//setLayout(gridLay);

		buttons = new JButton[loopLimit];
		int colNumb = -1;  //0;
		int jdx = 0;
		for (int i = 0; i < cats.length; i++) {
		    for (int j = 0; j < vars.length; j++) {
			for (int k = 0; k < curves.length; k++) {
			    c = new GridBagConstraints();
			    c.gridx = 0;
			    c.gridy = i*vars.length*curves.length + j*curves.length + k;
			    c.insets = new Insets(0, 0, 0, 0);
			    c.anchor = GridBagConstraints.NORTH;
			    c.fill = GridBagConstraints.BOTH;  //HORIZONTAL;
			    c.weightx = 1.0;
			    if (i < (loopLimit-1))
				c.weighty = 0.0;  //1.0;
			    else
				c.weighty = 0.0;  //1.0;

			    if (!isAnAggPlot)
				buttons[i] = new JButton(cats[i] + " : " + vars[j] +
							 " : " + curves[k]);
			    else
				buttons[i] = new JButton(" : " + vars[j] + " : " + curves[k]);
			    if (dataPointCount[jdx] > 0) {
				    colNumb++;
				    //if (colNumb == curveColor.length)
				    //colNumb = 0;
				    //buttons[i].setBackground(curveColor[colNumb]);
				    if (colNumb == legendColors.length)
					colNumb = 0;
				    if (localDebug) {
					Debug.out("i, j, k, jdx = " + i + ", " +
						  j + ", " + k + ", " + jdx +
						  "  colNumb = " + colNumb +
						  "  color = " + legendColors[colNumb]);
				    }
				    buttons[i].setBackground(legendColors[colNumb]);
			    } else {
				buttons[i].setBackground(Color.lightGray);
				if (localDebug) {
				    Debug.out("i, j, k, jdx = " + i + ", " +
					      j + ", " + k + ", " + jdx +
					      "  setting button color to lightGray.");
				}
			    }
			    jdx++;
			    buttons[i].setFont(new Font("helvetica", Font.PLAIN, 10));
			    buttons[i].setRolloverEnabled(true);
			    buttons[i].setMultiClickThreshhold(250);
	
		    //buttons[i].setToolTipText("Agg = " +
						  //new Double(aggVals[j]).toString() +
						  //"\nAvg = " + 
						  //new Double(avgVals[j]).toString());
			    //buttons[i].setPreferredSize(new Dimension(175, 24));
			    //buttons[i].setMaximumSize(new Dimension(175, 24));
			    gridbag.setConstraints(buttons[i], c);
			    buttons[i].addActionListener(new ActionListener() {
				    public void actionPerformed(ActionEvent e) {
					String buttonLabel = e.getActionCommand();
					//System.out.println(buttonLabel +
					    //" button pressed.");
					String buttText = buttonLabel +
					    getDataText(buttonLabel);
					Color buttColor = getDataColor(buttonLabel);

					processButtonAction(buttonLabel, buttText,
							    buttColor);
				}
			    });

			    add(buttons[i]);
			}
		    }
		}
	    }

	}  // StatsPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Button action handler for statistics control panel.
	 */

	void processButtonAction(String buttonLabel, String bText, Color bColor) {
	    if (localDebug)
		Debug.out(buttonLabel + " button pressed.");

	    ammaLabel.setText(bText);
	    statsReportPane.setBackground(bColor);


	    if (limitedDebug) {
		Debug.out("cats2Plot.length = " + cats2Plot.length +
			  "\nvars2Plot.length = " + vars2Plot.length +
			  "\ncrvs2Plot.length = " + crvs2Plot.length);
		for (int i = 0; i < cats2Plot.length; i++)
		    Debug.out("cats2Plot[" + i + "] = " + cats2Plot[i]);
		for (int i = 0; i < vars2Plot.length; i++)
		    Debug.out("vars2Plot[" + i + "] = " + vars2Plot[i]);
		for (int i = 0; i < crvs2Plot.length; i++)
		    Debug.out("crvs2Plot[" + i + "] = " + crvs2Plot[i]);
	    }

	    // New mod to allow different curves to become overview curve.
	    //                                      Added on May 30, 2007
	    // Load overviewData with data associated with button pressed.
	    // cats2Plot, vars2Plot, crvs2Plot,

	    String [] catvarcurve = buttonLabel.split(" : ");
	    int icat = -1;
	    int ivar = -1;
	    int icurve = -1;
	    int i = 0;
	    while ((icat < 0) && (i < cats2Plot.length)) {
		if (catvarcurve[0].equals(cats2Plot[i]))
		    icat = i;
		i++;
	    }
	    if (isAnAggPlot)
		icat = 0;

	    i = 0;
	    while ((ivar < 0) && (i < vars2Plot.length)) {
		if (catvarcurve[1].equals(vars2Plot[i]))
		    ivar = i;
		i++;
	    }
	    i = 0;
	    while ((icurve < 0) && (i < crvs2Plot.length)) {
		if (catvarcurve[2].equals(crvs2Plot[i]))
		    icurve = i;
		i++;
	    }
	    if (localDebug)
		Debug.out("New cat var curve = [" + icat + "] [" + ivar + "] [" +
			  icurve + "]");
		/***
		    for (int i = 0; i < numCurvesPerVar; i++) {
		    // curves[i] names the curve to load. (Agg, Max, Min or Avg)
		    int idx1 = varNum * numCurvesPerVar + i;
		    dataPointCount[idx1] = asize;

		    for (int j = 0; j < Math.min(arrayLimit, asize); j++) {
		        rawData[idx1][j][1]
		***/

	    if (icat < 0 || ivar < 0 || icurve < 0) {
		if (localDebug) {
		    Debug.out("Error, invalid data element caculated.");
		    Debug.out("icat = " + icat + " ivar = " + ivar +
			      " icurve = " + icurve);
		}
		LwatchAlert lwAlert = new LwatchAlert(pf2);
		lwAlert.displayDialog(true,  // modal dialog
				      "Error, invalid data element caculated.",
				      1);  // type  1 = info with "continue
		return;
	    }

	    ovIDX = icat * vars2Plot.length * crvs2Plot.length +
		ivar * crvs2Plot.length + icurve;

	    if (localDebug) {
		Debug.out("nRowsSelected = " + nRowsSelected + "  nColsSelected = " +
			  nColsSelected + "  nCurvesSelected = " + nCurvesSelected);
		Debug.out("ovIDX = " + ovIDX + "  size = " + dataPointCount[ovIDX]);
		Debug.out("rawData size = " + rawData[ovIDX].length);
	    }

	    /***
	    overviewData = new double[dataPointCount[ovIDX]][2];
	    for (i = 0; i < dataPointCount[ovIDX]; i++) {
		overviewData[i][0] = rawData[ovIDX][i][0];
 		overviewData[i][1] = rawData[ovIDX][i][1];
	    }
	    ***/

	    // Reset overview plot range to the range for the selected row, column, curve.
	    yLo = ovRangeMin[ovIDX];
	    yHi = ovRangeMax[ovIDX] + (ovRangeMax[ovIDX] - ovRangeMin[ovIDX])/10.;

	    //wideView.updateRawXIndices(wideView.xb, wideView.xe);
	    wideView.repaint();

	    //Debug.out("ovStopIndex = " + ovStopIndex +
		      //"   dataPointCount[ovIDX] = " + dataPointCount[ovIDX] +
		      //"\narrayLimit = " + arrayLimit);
	    //Debug.out("startIndex & stopIndex = " + startIndex + " & " + stopIndex);

	    if (ovStopIndex >= dataPointCount[ovIDX])
		ovStopIndex = dataPointCount[ovIDX] - 1;

	    if (arrayLimit > dataPointCount[ovIDX])
		arrayLimit = dataPointCount[ovIDX];

	}  // processButtonAction

    }  // StatsPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a string representaion of a numeric value that is zero-padded to the desired length.
     *
     * @param iVal numeric value to be 
     *
     * @param desiredLength desired length of return string.
     */

    String pad(int iVal, int desiredLength) {

	String iString = Integer.toString(iVal);
	if (iString.length() < desiredLength) {
	    for (int i = iString.length(); i < desiredLength; i++) {
		iString = "0" + iString;
	    }
	}
	return iString;

    }  // pad


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return number of days in month. Wgasa leap years.
     */

    int daysInMonth(int inMonth) {

	int [] dim = {31, 28, 31, 30, 31, 30, 31, 31 ,30, 31, 30, 31};
	return dim[inMonth-1];

    }  // daysInMonth


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reset the the plot intervals and control widgets and selections to those
     * stored in the the plot descriptor.
     *
     * @param plotDescr PlotDescriptor from which values are retrieved.
     */

    void resetIntervals(PlotDescriptor plotDescr) {

	if (plotDescr == null) return;

	tsStart = new Timestamp(plotDescr.tsS.getTime());
	tsEnd = new Timestamp(plotDescr.tsE.getTime());
	tsStartPlot = new Timestamp(plotDescr.tsSP.getTime());
	tsEndPlot = new Timestamp(plotDescr.tsEP.getTime());
	ovStartPlot = new Timestamp(plotDescr.ovSP.getTime());
	ovEndPlot = new Timestamp(plotDescr.tovEP.getTime());

	granularity = plotDescr.oldGran;

	nRowsSelected = plotDescr.nRow;
	nColsSelected = plotDescr.nCol;
	nCurvesSelected = plotDescr.nCrv;
	if (limitedDebug)
	    Debug.out("nRowsSelected = " + nRowsSelected + " plotDescr.nRow = " + plotDescr.nRow); 

	loadAggregate = plotDescr.lAgg;
	loadMaximum = plotDescr.lMax;
	loadMinimum = plotDescr.lMin;
	loadAverage = plotDescr.lAvg;

	int vTot = nRowsSelected * nColsSelected * nCurvesSelected;
	ovRangeMax = new float[vTot];
	ovRangeMin = new float[vTot];
	dataPointCount = new int[vTot];
	for (int i = 0; i < vTot; i++) {
	    ovRangeMax[i] = plotDescr.rngMax[i];
	    ovRangeMin[i] = plotDescr.rngMin[i];
	    dataPointCount[i] = plotDescr.datCnt[i];
	}


	cats2Plot = new String[plotDescr.cats.length];
	vars2Plot = new String[plotDescr.vars.length];
	crvs2Plot = new String[plotDescr.crvs.length];

	for (int i = 0; i < plotDescr.cats.length; i++) {
	    cats2Plot[i] = plotDescr.cats[i];
	    //Debug.out("Assign " + plotDescr.cats[i] + " to cats2Plot[" + i + "]");
	}
	controls.catPanel.setSelected(cats2Plot);

	for (int i = 0; i < plotDescr.vars.length; i++) {
	    vars2Plot[i] = plotDescr.vars[i];
	    //Debug.out("Assign " + plotDescr.vars[i] + " to vars2Plot[" + i + "]");
	}
	controls.varPanel.setSelected(vars2Plot);

	for (int i = 0; i < plotDescr.crvs.length; i++)
	    crvs2Plot[i] = plotDescr.crvs[i];
	controls.crvPanel.setSelected(crvs2Plot);

	updateControlValues();
	granularityChooser.setGranularity(granularity);
	//redrawChartPanel();
	wideView.repaint();
    }  // resetIntervals


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to store the history plot state.
     */

    class PlotDescriptor {

	int nRow = 0;
	int nCol = 0;
	int nCrv = 0;

	Timestamp tsS = null;
	Timestamp tsE = null;
	Timestamp tsSP = null;
	Timestamp tsEP = null;
	Timestamp ovSP = null;
	Timestamp tovEP = null;

	int oldGran = -1;

	String [] cats = null;
	String [] vars = null;
	String [] crvs = null;

	int vTot = 0;

	boolean lAgg;
	boolean lMax;
	boolean lMin;
	boolean lAvg;

	float [] rngMax = null;
	float [] rngMin = null;

	int [] datCnt = null;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	public PlotDescriptor() {

	    nRow = nRowsSelected;
	    nCol = nColsSelected;
	    nCrv = nCurvesSelected;

	    tsS = new Timestamp(tsStart.getTime());
	    tsE = new Timestamp(tsEnd.getTime());
	    tsSP = new Timestamp(tsStartPlot.getTime());
	    tsEP = new Timestamp(tsEndPlot.getTime());
	    ovSP = new Timestamp(ovStartPlot.getTime());
	    tovEP = new Timestamp(ovEndPlot.getTime());

	    oldGran = granularity;

	    cats = new String[cats2Plot.length];
	    vars = new String[vars2Plot.length];
	    crvs = new String[crvs2Plot.length];

	    lAgg = loadAggregate;
	    lMax = loadMaximum;
	    lMin = loadMinimum;
	    lAvg = loadAverage;

	    rngMax = new float[ovRangeMax.length];
	    rngMin = new float[ovRangeMin.length];

	    datCnt = new int[dataPointCount.length];

	    vTot = nRowsSelected * nColsSelected * nCurvesSelected;

	    for (int i = 0; i < cats2Plot.length; i++) {
		cats[i] = cats2Plot[i];
		if (limitedDebug)
		    Debug.out("Store " + cats2Plot[i] + " in cats[" + i + "]");
	    }
	    for (int i = 0; i < vars2Plot.length; i++) {
		vars[i] = vars2Plot[i];
		if (limitedDebug)
		    Debug.out("Store " + vars2Plot[i] + " in vars[" + i + "]");
	    }
	    for (int i = 0; i < crvs2Plot.length; i++) {
		crvs[i] = crvs2Plot[i];
		if (limitedDebug)
		    Debug.out("Store " + crvs2Plot[i] + " in crvs[" + i + "]");
	    }

	    for (int i = 0; i < vTot; i++) {
		rngMax[i] = ovRangeMax[i];
		rngMin[i] = ovRangeMin[i];
		datCnt[i] = dataPointCount[i];
	    }

	}  // PlotDescriptor


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the granularity stored in this plot descriptor.
	 */

	int getGranularity() {

	    return oldGran;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the categories stored in this plot descriptor.
	 */

	String [] getCategories() {

	    return cats;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the variables stored in this plot descriptor.
	 */

	String [] getVariables() {

	    return vars;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the curves stored in this plot descriptor.
	 */

	String [] getCurves() {

	    return crvs;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the number of categories stored in this plot descriptor.
	 */

	int getNCatsSelected() {

	    return nRowsSelected;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the number of variables stored in this plot descriptor.
	 */

	int getNVarsSelected() {

	    return nColsSelected;

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the number of curves stored in this plot descriptor.
	 */

	int getNCurvesSelected() {

	    return nCurvesSelected;

	}

    }  // PlotDescriptor


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Redraw the plot frame based on either pan/zoom event or
     * heartbeat refresh timer trip.
     */

    void refreshPlotFrame() {

	// Stop any refreshTimer controlled tasks.
	stopRefresh();

	//Debug.out("startIndex & stopIndex = " + startIndex + " & " + stopIndex);

	fromRefresh = true;
	ovIDX = 0;

	// Calculate the number of curves to be plotted
	nRowsSelected = controls.getCategoryPanel().getNumSelected();
	if (isAnAggPlot)
	    nRowsSelected = 1;
	nColsSelected = controls.getVariablePanel().getNumSelected();
	nCurvesSelected = controls.getCurvePanel().getNumCurvesSelected();

	if (nRowsSelected == 0 || nColsSelected == 0 || nCurvesSelected == 0) {
	    if (localDebug)
		Debug.out("Insufficient selection for generating a history plot.");
	    LwatchAlert lwAlert = new LwatchAlert(this);
	    lwAlert.displayDialog(true,  // modal = true
				  "Insufficient selection for generating a history plot.",
				  1);  // type  1 = info with "continue

	    resetIntervals(pD);

	    return;
	}

	granularity = granularityChooser.getGranularity();

	if (isAnAggPlot && (granularity == Database.RAW || granularity == HEARTBEAT)) {
	    LwatchAlert lwAlert = new LwatchAlert(this);
	    lwAlert.displayDialog(true,  // modal = true
				  "Invalid granularity setting for aggregate plot. " +
				  "Raw & Raw Live not allowed at this time.",
				  1);  // type  1 = info with "continue

	    resetIntervals(pD);

	    return;
	}

	if (granularity == HEARTBEAT)
	    updateLive = true;
	else
	    updateLive = false;


	String [] selectedRows;
	if (!isAnAggPlot)
	    selectedRows = controls.getCategoryPanel().getSelected();
	else {
	    selectedRows = new String[1];
	    selectedRows[0] = "Aggregate";
	}

	String [] selectedVars = controls.getVariablePanel().getSelected();
	//String [] selectedCurves = controls.getCurvePanel().getSelected();
	loadAggregate = controls.getCurvePanel().isAggregateSet();
	loadMinimum = controls.getCurvePanel().isMinimumSet();
	loadMaximum = controls.getCurvePanel().isMaximumSet();
	loadAverage = controls.getCurvePanel().isAverageSet();

	//rawData = null;
	int vTot = nRowsSelected * nColsSelected * nCurvesSelected;
	ovRangeMax = new float[vTot];
	ovRangeMin = new float[vTot];
	dataPointCount = new int[vTot];
	if (localDebug)
	    Debug.out("nrows, ncols, nCurves, vTot = " + nRowsSelected + " " +
		      nColsSelected + " " + nCurvesSelected + " " + vTot);
	cats2Plot = new String[nRowsSelected];
	vars2Plot = new String[nColsSelected];
	crvs2Plot = controls.getCurvePanel().getSelected();  //new String[nCurvesSelected];
	//aggVal = new double[vTot];
	//maxVal = new double[vTot];
	//minVal = new double[vTot];
	//avgVal = new double[vTot];

	// Grab values from the control panel so that start & end timestamps,
	// granularity,  and aggregate data types can be determined.

	// Starting Timestamp calculation
	int yearBeg = yrChooser.getYearSelected();
	int monthBeg = monthChooser.getMonthSelected() + 1;
	int dayBeg = dayChooser.getDaySelected();
	int hourBeg = hourChooser.getHourSelected();
	int minuteBeg = minuteChooser.getMinuteSelected();
	//if (localDebug)
	    //Debug.out(pad(monthBeg,2) + "/" + pad(dayBeg,2) + "/" +
		      //yearBeg + "  " + pad(hourBeg,2) + ":" + pad(minuteBeg,2));

	
	// Ending Timestamp calculation
	int yearEnd;
	int monthEnd;
	int dayEnd;
	int hourEnd;
	int minuteEnd;
	if (!useDuration) {  // End date/time defined by "End" definition
	    if (localDebug)
		Debug.out("End time defined by END Spec.");
	    yearEnd = yrEndChooser.getYearSelected();
	    monthEnd = monthEndChooser.getMonthSelected() + 1;
	    dayEnd = dayEndChooser.getDaySelected();
	    hourEnd = hourEndChooser.getHourSelected();
	    minuteEnd = minuteEndChooser.getMinuteSelected();
	    if (yearBeg == yearEnd && monthBeg == monthEnd && dayBeg == dayEnd &&
		hourBeg == hourEnd && minuteBeg == minuteEnd) {
		//Debug.out("Same begin & end times yield zero-length time interval.");
		LwatchAlert lwAlert = new LwatchAlert(this);
		lwAlert.displayDialog(true,  // modal = true
				      "zero-length time interval specified.",
				      1);  // type  1 = info with "continue

		resetIntervals(pD);

		return;
	    }
	} else {  // End date/time defined by "Duration" definition
	    if (localDebug)
		Debug.out("End time defined by DURATION Spec.");
	    yearEnd = yearBeg + yrDurChooser.getValueSelected();
	    monthEnd = monthBeg + monthDurChooser.getValueSelected();
	    dayEnd = dayBeg + dayDurChooser.getValueSelected();
	    hourEnd = hourBeg + hourDurChooser.getValueSelected();
	    minuteEnd = minuteBeg + minuteDurChooser.getValueSelected();
	    if (minuteEnd > 59) {
		hourEnd++;
		minuteEnd -= 59;
	    }
	    if (hourEnd > 23) {
		dayEnd++;
		hourEnd -= 23;
	    }
	    if (dayEnd > daysInMonth(monthBeg)) {
		monthEnd ++;
		dayEnd -= daysInMonth(monthBeg);
	    }
	    if (monthEnd > 12) {
		yearEnd++;
		monthEnd -= 12;
	    }
	}
	if (localDebug)
	    Debug.out(pad(monthEnd,2) + "/" + pad(dayEnd,2) +
		      "/" + yearEnd + "  " + pad(hourEnd,2) + ":"
		      + pad(minuteEnd,2));

	long nowMillis = System.currentTimeMillis();	

	String intervalBegString = Integer.toString(yearBeg) + "-" +
	    pad(monthBeg,2) + "-" + pad(dayBeg,2) + " " + pad(hourBeg,2) + ":" +
	    pad(minuteBeg,2) + ":00";
	String intervalEndString = Integer.toString(yearEnd) + "-" +
	    pad(monthEnd,2) + "-" + pad(dayEnd,2) + " " + pad(hourEnd,2) + ":" +
	    pad(minuteEnd,2) + ":00";

	if (limitedDebug) {
	    Debug.out("intervalBegString = " + intervalBegString + "\n" +
		      "intervalEndString = " + intervalEndString);
	    try {  //  Remove try block when debugging completed.
		long dtStart = simpleDateFormat.parse(intervalBegString).getTime();
		long dtEnd = simpleDateFormat.parse(intervalEndString).getTime();
		String cStart = (new Date(dtStart)).toString();
		String cEnd = (new Date(dtEnd)).toString();
		Debug.out("recalc of startDateTime = " + cStart + "\nendDateTime = " + cEnd);
	    } catch (java.text.ParseException e) {
		Debug.out("Exception detected while calculating Timestamp objects.\n" +
			  e.getMessage());

		resetIntervals(pD);
		return;
	    }
	}  // End of  -- if (localDebug)

	if (granularity == HEARTBEAT) {
	    tsStart = new Timestamp(nowMillis - (liveDisplayInterval));
	    tsEnd = new Timestamp(nowMillis);
	}

	long begTimeLong = -1;
	long endTimeLong = -1;
	try {

	    if (granularity == HEARTBEAT) {
		begTimeLong = tsStart.getTime();
		endTimeLong = tsEnd.getTime();
	    } else {
		// simpleDateFormat.parse(dateTimeString).getTime() returns
		// the number of milliseconds since January 1, 1970, 00:00:00 GMT
		begTimeLong = simpleDateFormat.parse(intervalBegString).getTime();

		endTimeLong = simpleDateFormat.parse(intervalEndString).getTime();
		if (endTimeLong > nowMillis)
		    endTimeLong = nowMillis;
	    }

	    tsStartPlot = new Timestamp(begTimeLong);
	    tsEndPlot = new Timestamp(endTimeLong);

	    if (begTimeLong >= endTimeLong) {
		LwatchAlert lwAlert = new LwatchAlert(this);
		lwAlert.displayDialog(true,  // modal = true
				      "Invalid time interval <= zero or " +
				      "future time specified.",
				      1);  // type  1 = info with "continue

		resetIntervals(pD);

		return;
	    }

	    double tNtrvl = (double)(endTimeLong - begTimeLong);
	    long nT = (long)(tNtrvl / tRate[granularity-1]);
	    //Debug.out((long)tNtrvl + " / " + (long)tRate[granularity-1] + " = " + nT);
	    //Debug.out("Chart container pane width = " + chartContainerPane.getWidth());
	    if ((nT > ((double)chartContainerPane.getWidth()*1.5)) && 
		((chartContainerPane.getWidth() > 100))) {
		LwatchAlert lwAlert = new LwatchAlert(this);

		lwAlert.displayDialog(true,  // modal = true
				      "Plot contains more data than # of pixels in plot window. " +
				      "Usefulness will be questionable.",   // The message to put in the window
				      0);    // type  0 = info with "cancel" & "continue"
		if (cancel) {
		    //Debug.out("Request canceled. Reset values...");
		    resetIntervals(pD);

		    return;
		}
	    }

	    /****
	    Runtime.getRuntime().gc();
	    long freeMemory = Runtime.getRuntime().freeMemory();
	    long totalMemory = Runtime.getRuntime().totalMemory();
	    long maxMemory = Runtime.getRuntime().maxMemory();
	    long usedMemory = totalMemory-freeMemory;
	    double percentOfMax = (double) usedMemory / (double) maxMemory;

	    if (true)  //(limitedDebug)
		Debug.out("\n\nPrior to GC :\n" +
			  "\nfreeMemory = " + freeMemory +
			  "   maxMemory = " + maxMemory +
			  "\ntotalMemory = " + totalMemory +
			  "   usedMemory = " + usedMemory +
			  "\n% of Max used = " + percentOfMax);
	    ****/

	    if (limitedDebug)
		Debug.out("tsStart, tsEnd = " + tsStart + ", " + tsEnd +
			  "\ntsStartPlot, tsEndPlot = " + tsStartPlot + ", " + tsEndPlot);

	    if ((granularity == Database.RAW) || (granularity == HEARTBEAT)) {
		int estimatedTime = (int)((nRowsSelected * nColsSelected *
					   nCurvesSelected) * 
					  (((endTimeLong - begTimeLong) / ONEDAY) + 1) * 5.0);
		double percentOfMem = (double)(nRowsSelected * nColsSelected * nCurvesSelected) * 
				(((double)(endTimeLong - begTimeLong) / ONEDAY) + 1.0) * 3.5;
		percentOfMem *= (double)((double)(1024*1024*200)/
					 (double)(Runtime.getRuntime().maxMemory()));

		if ((estimatedTime > 15) || (percentOfMem > 80.0)) {
		    //Debug.out("Requested Raw data retrieval of 5 or more days." +
			      //" This may take a while.");
		    String msg = "Requested data ";
		    if (estimatedTime > 15)
			msg += "may take up to " + estimatedTime + " secs to retrieve";

		    if (percentOfMem > 80.0) {
			if (estimatedTime > 15)
			    msg +=  " & ";
			msg += "requires 80% or more of memory";
		    }
		    msg += ".";

		    LwatchAlert lwAlert = new LwatchAlert(this);
		    lwAlert.displayDialog(true,  // modal = true
					  msg,   // The message to put in the window
					  0);    // type  0 = info with "cancel" & "continue"
		    if (cancel) {
			//Debug.out("Request canceled. Reset values...");
			resetIntervals(pD);

			return;
		    }
		}

		if (granularity == Database.RAW) {
		    tsStart = new Timestamp(tsStartPlot.getTime() - HALFDAY);
		    tsEnd = new Timestamp(tsEndPlot.getTime() + HALFDAY);
		}
	    } else if (granularity == Database.HOUR) {
		tsStart = new Timestamp(tsStartPlot.getTime() - SIXMONTHS);
		tsEnd = new Timestamp(tsEndPlot.getTime() + SIXMONTHS);
	    } else {  // Granularity > 1 hour. Grab everything.
		tsStart = new Timestamp((long) SIXMONTHS*10);
		tsEnd = new Timestamp(new java.util.Date().getTime());
	    }

	    if (tsEnd.getTime() > nowMillis)
		tsEnd = new Timestamp(nowMillis);

	    if (tsStart.getTime() >= tsEnd.getTime()) {

		if (granularity == HEARTBEAT) {
		    tsStart = new Timestamp(nowMillis - (liveDisplayInterval));
		    tsEnd = new Timestamp(nowMillis);
		} else if (granularity == Database.RAW)
		    tsStart = new Timestamp(nowMillis - HALFDAY);
		else if (granularity == Database.HOUR)
		    tsStart = new Timestamp(nowMillis - SIXMONTHS);
		else
		    tsStart = new Timestamp(ONEYEAR * 37);
	    }

	    if (limitedDebug)
		Debug.out("granularity = " + granularity +
			  "\ntsStart, tsEnd = " + tsStart + ", " + tsEnd);

	} catch (java.text.ParseException e) {
	    Debug.out("Exception detected while building Timestamp objects.\n" +
		      e.getMessage());

	    resetIntervals(pD);
	    return;
	}


	rawData = null;
	initialLoad = true;
	int zeroReturnCount = 0;

	for (int irow = 0; irow < nRowsSelected; irow++) {
	    cats2Plot[irow] = selectedRows[irow];
	    for (int ivar = 0; ivar < nColsSelected; ivar++) {
		vars2Plot[ivar] = selectedVars[ivar];
		int index = irow * nColsSelected + ivar;

		if (localDebug)
		    Debug.out("Read data for row, col, curve = " +
			      irow + ", " + ivar);

		//getRawData(selectedRows[irow], selectedVars[ivar], index, vTot);
		thpFrame.setCursor(hourglassCursor);
		loadHistoricalData(this.type, this.subIdent,
				   selectedRows[irow], selectedVars[ivar],
				   crvs2Plot, index, vTot);
		thpFrame.setCursor(normalCursor);

		for (int kk = 0; kk < crvs2Plot.length; kk++) {
		    int kkdx = irow * nColsSelected * nCurvesSelected +
		    ivar * nCurvesSelected + kk;
		    if (dataPointCount[kkdx] <= 0)
			zeroReturnCount++;
		}

		/****
		if (dataPointCount[0] <= 0) {
		    if (!isAnAggPlot || (vTot == 1)) {
			if (localDebug)
			    Debug.out("Zero-length array result from data load request.");
			LwatchAlert lwAlert = new LwatchAlert(this);
			lwAlert.displayDialog(true,  // modal = true
					      "DB request yielded no data.",
					      1);  // type  1 = info with "continue

			//redrawChartPanel();
			//wideView.repaint();

			continue;
		    }
		}
		****/

		//%%%tsStart = new Timestamp(rawTimes[0][0]);
		//%%%tsEnd = new Timestamp(rawTimes[0][dataPointCount[0]-1]);

		// Set up the overview plot interval
		if (granularity == HEARTBEAT) {
		    ovEndPlot = new Timestamp(tsEndPlot.getTime());
		    ovStartPlot = new Timestamp(tsStartPlot.getTime());
		} else if (granularity == Database.RAW) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
				  tsEndPlot.getTime()+HOUR));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
							 tsStartPlot.getTime()-HOUR));
		} else if (granularity == Database.HOUR) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
				  tsEndPlot.getTime()+((long)(2)*ONEDAY)));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
							 tsStartPlot.getTime()-((long)(2)*ONEDAY)));
		} else if (granularity == Database.DAY) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
						       tsEndPlot.getTime()+((long)(30)*ONEDAY)));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
							 tsStartPlot.getTime()-((long)(30)*ONEDAY)));
		    //Debug.out("ovStartPlot = " + ovStartPlot + "  --  ovEndPlot = " + ovEndPlot);
		} else if (granularity == Database.WEEK) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
				  tsEndPlot.getTime()+(long)(ONEYEAR)));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
				  tsStartPlot.getTime()-(long)(ONEYEAR)));
		} else if (granularity == Database.MONTH) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
				  tsEndPlot.getTime()+(long)(ONEYEAR)));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
				  tsStartPlot.getTime()-(long)(ONEYEAR)));
		} else if (granularity == Database.YEAR) {
		    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
				  tsEndPlot.getTime()+(long)(ONEYEAR)));
		    ovStartPlot = new Timestamp(Math.max(tsStart.getTime(),
				  tsStartPlot.getTime()-(long)(ONEYEAR)));
		}
		if (localDebug)
		    Debug.out("After loadHistoricalData, plot interval = " +
			      tsStartPlot + " " + tsEndPlot);
		/*********
		if (arrayLimit <= 1000) {  // was 1000
		    ovStartIndex = 0;
		    ovStopIndex = arrayLimit - 1;
		} else {
		    //ovStartIndex = Math.max(0, (startIndex+stopIndex)/2 - 500);  // was 500
		    //ovStopIndex = Math.min(arrayLimit - 1, (startIndex+stopIndex)/2 + 500);
		    int midIdx = (startIndex + stopIndex) / 2;
		    startIndex = Math.max(0, midIdx - 400);
		    stopIndex = Math.min(arrayLimit - 1, midIdx + 400);
		    ovStartIndex = Math.max(0, midIdx - 500);
		    ovStopIndex = Math.min(arrayLimit - 1, midIdx + 500);
		}
		**********/
	    }
	}
	initialLoad = false;

	if (zeroReturnCount == vTot) {
	    if (localDebug)
		Debug.out("Zero-length array result from data load request.");
	    LwatchAlert lwAlert = new LwatchAlert(this);
	    lwAlert.displayDialog(true,  // modal = true
				  "DB request yielded no data.",
				  1);  // type  1 = info with "continue
	}

	/*****
	long tDivisor = getTimeDivisor(granularity);
	long timeNtrvls = (tsEnd.getTime() - tsStart.getTime()) / tDivisor;
	long plotTimeNtrvls = (tsEndPlot.getTime() - tsStartPlot.getTime()) / tDivisor;
	if ( (plotTimeNtrvls - timeNtrvls) <= 1000 ) {
	    ovStartPlot = new Timestamp(tsStart.getTime());
	    ovEndPlot = new Timestamp(tsEnd.getTime());
	} else {
	    ovStartPlot = new Timestamp(tsStartPlot.getTime() - 500*tDivisor);
	    ovEndPlot = new Timestamp(tsEndPlot.getTime() + 500*tDivisor);
	}
	*****/
	// Reset overview plot range to row, column, curve zero.
	yLo = ovRangeMin[ovIDX];
	yHi = ovRangeMax[ovIDX] + (ovRangeMax[ovIDX] - ovRangeMin[ovIDX])/10.;

	//Debug.out("startIndex & stopIndex = " + startIndex + " & " + stopIndex);

	// Update the statsReportPane with data for curve 0
	if ((cats2Plot.length > 0) && (vars2Plot.length > 0) && (crvs2Plot.length > 0)) {
	    String buttText = cats2Plot[0] + " : " + vars2Plot[0] + " : " + crvs2Plot[0] +
		getDataText(cats2Plot[0] + " : " + vars2Plot[0] + " : " + crvs2Plot[0]);
	    Color buttColor = getDataColor(cats2Plot[0] + " : " + vars2Plot[0] +
					   " : " + crvs2Plot[0]);
	    ammaLabel.setText(buttText);
	    //Debug.out("ammaLabel text = " + buttText);
	    statsReportPane.setBackground(buttColor);
	} else {
	    ammaLabel.setText("Incomplete Data Selection");
	    statsReportPane.setBackground(Color.lightGray);
	}

	if (localDebug)
	    Debug.out("Draw new set of curves.");

	thpFrame.setCursor(hourglassCursor);

	updateControlValues();
	lastRefreshPlotParams = new PlotDescriptor();

	redrawChartPanel();
	wideView.repaint();

	/****
	plotPanel.remove(wideView);
	wideView = new OverView();
	plotPanel.add(wideView);

	ppc = new GridBagConstraints();
	ppc.gridx = 0;
	ppc.gridy = 1;

	// Insets are Top, Left, Bottom, Right
	ppc.insets = new Insets(0, 76, 10, 18);  //(8, 4, 0, 5);
	ppc.anchor = GridBagConstraints.NORTH;
	ppc.fill = GridBagConstraints.BOTH;
	ppc.weightx = 1.0;
	ppc.weighty = 0.25;  //0.15;  //1.0;
	ppgbl.setConstraints(wideView, ppc);

	plotPanel.invalidate();
	plotPanel.validate();
	****/

	thpFrame.setCursor(normalCursor);

	if (localDebug)
	    Debug.out("Build new set of stat displays. # of vars = " + vTot);

	refreshVariableStats();

	if (granularity == HEARTBEAT) {
	    setRefresh(refreshRate, 3600000);
	} else {
	    stopRefresh();
	}

	/****
	long freeMemory = Runtime.getRuntime().freeMemory();
	long totalMemory = Runtime.getRuntime().totalMemory();
	long maxMemory = Runtime.getRuntime().maxMemory();
	long usedMemory = totalMemory-freeMemory;
	double percentOfMax = (double) usedMemory / (double) maxMemory;

	Debug.out("\n\nAfter to DB load & new plots with " +
		  "\n# of variables = " + vTot + " & granularity = " + granularity +
		  "\ninterval = " + tsStart + "   ---   " + tsEnd +
		  "\nfreeMemory = " + freeMemory + "   maxMemory = " + maxMemory +
		  "\ntotalMemory = " + totalMemory + "   usedMemory = " + usedMemory +
		  "\n% of Max used = " + percentOfMax);
	***/

	//Debug.out("startIndex & stopIndex = " + startIndex + " & " + stopIndex);

    }  // refreshPlotFrame


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the number of milliseconds in the granularity unit being used.
     *
     * @param the granularity setting.
     */

    public long getTimeDivisor(int tGranularity) {

	if (tGranularity == Database.HOUR)
	    return (long)(3600 * 1000);
	else if (tGranularity == Database.DAY)
	    return (long)(24 * 3600 * 1000);
	else if (tGranularity == Database.WEEK)
	    return (long)(7 * 24 * 3600 * 1000);
	else if (tGranularity == Database.MONTH)
	    return (long)(31 * 24 *3600 * 1000);
	else if (tGranularity == Database.YEAR)
	    return (long)(365 * 24 *3600 * 1000);
	else  //  Database.RAW  or HEARTBEAT
	    return (long)(5000);

    }  // getTimeDivisor


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Redraw the chart panel.
     */

    void redrawChartPanel() {

	chartContainerPane.removeAll();
	chartPanel = null;
	JFreeChart chart = createChart(createDataset());
	chartPanel =  new ChartPanel(chart);

	chartPanel.setPreferredSize(new java.awt.Dimension(
	    chartContainerPane.getWidth(),
	    chartContainerPane.getHeight()));

	chartPanel.setMouseZoomable(true, false);
	chartContainerPane.add(chartPanel);

	ppc = new GridBagConstraints();
	ppc.gridx = 0;
	ppc.gridy = 0;
	ppc.insets = new Insets(2, 2, 0, 2);  //(8, 4, 0, 5);
	ppc.anchor = GridBagConstraints.NORTH;
	ppc.fill = GridBagConstraints.BOTH;
	ppc.weightx = 1.0;  //1.0;
	ppc.weighty = 0.75;  //0.0;
	ppgbl.setConstraints(chartContainerPane, ppc);


	chartContainerPane.invalidate();
	chartContainerPane.validate();

    }  // redrawChartPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Redraw the variable statistics panel.
     */

    void refreshVariableStats() {

	if (vsPane != null)
	    statPanel.remove(vsPane);

	vsPane = null;

	if (vars2Plot != null) {
	    for (int i = 0; i < cats2Plot.length; i++) {
		for (int j = 0; j < vars2Plot.length; j++) {
		    for (int k = 0; k < crvs2Plot.length; k++) {
			vsPane = new VarStatPane(cats2Plot, vars2Plot, crvs2Plot);  //,
						 //aggVal, maxVal,
						 //minVal, avgVal);

			//vsPane = new StatsPanel(cats2Plot, vars2Plot, aggVal, maxVal,
			                          //minVal, avgVal);
		    }
		}
	    }
	    if (vsPane != null) {
		vsPane.setVisible(true);
		statPanel.add(vsPane);
		if (localDebug)
		    Debug.out("New vsPane component count = " +
			      vsPane.getComponentCount());
	    }
	}
	statPanel.invalidate();
	statPanel.validate();

    }  // refreshVariableStats


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing category selction panel. Categories refer to MDS, OST, OSS and RTR
     */

    class CategoryPanel extends JPanel {  // implements ActionListener {
	//String [] vars;
	JList varList;
	int loopLimit = 0;
	JButton [] buttons;
	String eType;
	int lastSelection = -1;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	CategoryPanel (String tType) {
	    super();

	    this.eType = tType;
	    GridBagLayout gridbag = new GridBagLayout();
	    setLayout(gridbag);
	    setBackground(unselectedBG);
	    GridBagConstraints c;

	    loopLimit = masterData.length;
	    if (! "MDS".equals(this.eType))
		loopLimit -= 4;
	    /******
	    if ("OST".equals(tType)) {
		loopLimit = fileSys.ostMasterData.length;
	    } else if ("RTR".equals(tType)) {
		loopLimit = fileSys.rtrMasterData.length;
	    } else {  // We're going to assume MDS
		loopLimit = fileSys.mdsMasterData.length;
	    }
	    ******/

	    buttons = new JButton[loopLimit];
	    for (int i = 0; i < loopLimit; i++) {
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = i+1;
		c.anchor = GridBagConstraints.NORTH;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		if (i < (loopLimit-1))
		    c.weighty = 0.0;
		else
		    c.weighty = 1.0;

		/******
		if ("OST".equals(tType)) {
		    buttons[i] = new JButton((String)fileSys.ostMasterData[i][0]);
		} else if ("RTR".equals(tType)) {
		    buttons[i] = new JButton((String)fileSys.routerMasterData[i][0]);
		} else {  // We're going to assume MDS
		    buttons[i] = new JButton((String)fileSys.mdsMasterData[i][0]);
		}
		******/

		buttons[i] = new JButton((String)masterData[i][0]);

		if (rowID.equals(buttons[i].getText())) {
		    buttons[i].setBackground(selectedBG);
		    buttons[i].setSelected(true);
		} else {
		    buttons[i].setBackground(unselectedBG);
		    buttons[i].setSelected(false);
		}

		buttons[i].setFont(new Font("helvetica", Font.PLAIN, 10));
		//buttons[i].setForeground(scheme.getColor(Scheme.HEADING1));
		buttons[i].setRolloverEnabled(true);
		buttons[i].setMultiClickThreshhold(250);
		gridbag.setConstraints(buttons[i], c);
		buttons[i].addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    processButtonAction(e, buttonLabel);
			}
		    });
		add(buttons[i]);
	    }

	}  // CategoryPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the number of categories that are selected.
	 */

	int getNumSelected() {

	    //if (localDebug)
		//Debug.out("Loop limit = " + loopLimit);
	    int nsel = 0;
	    for (int i = 0; i < loopLimit; i++) {
		//if (localDebug)
		    //System.out.print("buttons[" + i + "] = " +
				     //buttons[i].getText());

		if (buttons[i].isSelected()) {
		    nsel++;
		    //if (localDebug)
			//System.out.println(" SELECTED");
		} else {
		    //if (localDebug)
			//System.out.println(" NOT");
		}
	    }
	    return nsel;

	}  // getNumSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return selected categories.
	 *
	 * @return array of selected category names.
	 */

	String [] getSelected() {

	    int nsel = this.getNumSelected();
	    String [] selCats = new String[nsel];
	    int it = 0;
	    try {
		for (int i = 0; i < loopLimit; i++) {
		    if (buttons[i].isSelected())
			selCats[it++] = buttons[i].getText();
		}
	    } catch (IndexOutOfBoundsException e) {
		if (localDebug)
		    Debug.out("Index limit = " + nsel + "  index value = " + it);
		throw new IndexOutOfBoundsException(e.getMessage());
	    }
	    return selCats;

	}  // getSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selection state of the categories specified in the array argument.
	 *
	 * @param cats2BSet array of names of categories to be set.
	 */

	void setSelected(String [] cats2BSet) {

	    if (cats2BSet.length <= 0)
		return;

	    for (int i = 0; i < loopLimit; i++) {
		boolean slctd = false;
		for (int j = 0; j < cats2BSet.length; j++) {
		    if (buttons[i].getText().equals(cats2BSet[j]))
			slctd = true;
		}
		if (slctd && !buttons[i].isSelected()) {
		    buttons[i].setSelected(true);
		    buttons[i].setBackground(selectedBG);
		    if (limitedDebug)
			Debug.out("Setting " + buttons[i].getText() + " to selected.");
		} else if (!slctd && buttons[i].isSelected()) {
		    buttons[i].setSelected(false);
		    buttons[i].setBackground(unselectedBG);
		    if (limitedDebug)
			Debug.out("Setting " + buttons[i].getText() + " to unselected.");
		}
	    }

	    this.repaint();

	}  // setSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Select all categories.
	 */

	void selectAll() {

	    for (int i = 0; i < loopLimit; i++) {
		buttons[i].setSelected(true);
		buttons[i].setBackground(selectedBG);
	    }

	}  // selectAll


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Deselect all categories.
	 */

	void deselectAll() {

	    for (int i = 0; i < loopLimit; i++) {
		buttons[i].setSelected(false);
		buttons[i].setBackground(unselectedBG);
	    }

	}  // deselectAll


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Button action handler for category selector panel.
	 *
	 * @param e button event causing handler to be activated.
	 *
	 * @param buttonLabel category label from the button pressed.
	 */

	void processButtonAction(ActionEvent e, String buttonLabel) {

	    /******
	    if ("OST".equals(eType)) {
		loopLimit = fileSys.ostMasterData.length;
	    } else if ("RTR".equals(eType)) {
		loopLimit = fileSys.routerMasterData.length;
	    } else {  // We're going to assume MDS
		loopLimit = fileSys.mdsMasterData.length;
	    }
	    ******/

	    int modifiers = e.getModifiers();
	    boolean shifted = false;
	    if ((modifiers & ActionEvent.SHIFT_MASK) != 0) {
		//Debug.out("Shift click occurred.");
		shifted = true;
	    }

	    loopLimit = masterData.length;
	    if (! "MDS".equals(this.eType))
		loopLimit -= 4;

	    int selIdx = -1;
	    for (int i = 0; i < loopLimit; i++) {
		if (buttonLabel.equals(masterData[i][0])) {
		    selIdx = i;
		    break;
		}
	    }
	    if (selIdx >= 0) {
		if (localDebug)
		    Debug.out("Button " + buttonLabel + " clicked.");
		if (buttons[selIdx].isSelected()) {
		    buttons[selIdx].setSelected(false);
		    buttons[selIdx].setBackground(unselectedBG);
		} else {
		    buttons[selIdx].setSelected(true);
		    buttons[selIdx].setBackground(selectedBG);
		}
		if ( shifted && (lastSelection >= 0) && (lastSelection != selIdx)) {
		    int inc = 1;
		    if (selIdx < lastSelection)
			inc = -1;
		    for (int j = lastSelection+inc; j != selIdx; j += inc) {
			//Debug.out(j + "button isSelected = " + buttons[j].isSelected());
			buttons[j].setSelected(!buttons[j].isSelected());
			if (buttons[j].isSelected())
			    buttons[j].setBackground(selectedBG);
			else
			    buttons[j].setBackground(unselectedBG);
		    }
		}
		lastSelection = selIdx;
	    } else {
		if (localDebug)
		    Debug.out("Unable to match click for button " + buttonLabel);
	    }
	}  // processButtonAction

    }  // CategoryPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to build the variable selection panel.
     */

    class VariablePanel extends JPanel {

	JList varList;
	int loopLimit = 0;
	JButton [] buttons;
	String eType;
	int lastSelection = -1;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	VariablePanel (String tType) {

	    super();

	    this.eType = tType;
	    GridBagLayout gridbag = new GridBagLayout();
	    setLayout(gridbag);
	    setBackground(unselectedBG);
	    GridBagConstraints c;

	    loopLimit = 0;
	    if ("OST".equals(tType)) {
		loopLimit = ostPlottableVars.length;
		//Debug.out("# of OST varaibles in selection list = " + loopLimit);
	    } else if ("RTR".equals(tType)) {
		loopLimit = rtrPlottableVars.length;
	    } else {  // We're going to assume MDS
		loopLimit = mdsPlottableVars.length;
	    }

	    buttons = new JButton[loopLimit];
	    for (int i = 0; i < loopLimit; i++) {
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = i+1;
		c.anchor = GridBagConstraints.NORTH;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		if (i < (loopLimit-1))
		    c.weighty = 0.0;
		else
		    c.weighty = 1.0;

		if ("OST".equals(tType)) {
		    buttons[i] = new JButton(ostPlottableVars[i]);
		} else if ("RTR".equals(tType)) {
		    buttons[i] = new JButton(rtrPlottableVars[i]);
		} else {  // We're going to assume MDS
		    buttons[i] = new JButton(mdsPlottableVars[i]);
		}

		if (colID.equals(buttons[i].getText())) {
		    buttons[i].setBackground(selectedBG);
		    buttons[i].setSelected(true);
		} else {
		    buttons[i].setBackground(unselectedBG);
		    buttons[i].setSelected(false);
		}

		buttons[i].setFont(new Font("helvetica", Font.PLAIN, 10));
		//buttons[i].setForeground(scheme.getColor(Scheme.HEADING1));
		buttons[i].setRolloverEnabled(true);
		buttons[i].setMultiClickThreshhold(250);
		gridbag.setConstraints(buttons[i], c);
		buttons[i].addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    processButtonAction(e, buttonLabel);
			}
		    });
		add(buttons[i]);

	    }

	}  // VariablePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the number of selected variables.
	 */

	int getNumSelected() {

	    int nsel = 0;
	    for (int i = 0; i < loopLimit; i++) {
		if (buttons[i].isSelected())
		    nsel++;
	    }
	    return nsel;

	}  // getNumSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return names of selected variables.
	 *
	 * @return array of selected variable names.
	 */

	String [] getSelected() {

	    int nsel = this.getNumSelected();
	    String [] selVars = new String[nsel];
	    int it = 0;
	    try {
		for (int i = 0; i < loopLimit; i++) {
		    if (buttons[i].isSelected())
			selVars[it++] = buttons[i].getText();
		}
	    } catch (IndexOutOfBoundsException e) {
		if (localDebug)
		    Debug.out("Index limit = " + nsel + "  index value = " + it);
		throw new IndexOutOfBoundsException(e.getMessage());
	    }
	    return selVars;

	}  // getSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set variables specified in array argument to selected.
	 *
	 * @param vars2BSet array of names of variables to be set.
	 */

	void setSelected(String [] vars2BSet) {

	    if (vars2BSet.length <= 0)
		return;

	    for (int i = 0; i < loopLimit; i++) {
		boolean slctd = false;
		for (int j = 0; j < vars2BSet.length; j++) {
		    if (buttons[i].getText().equals(vars2BSet[j]))
			slctd = true;
		}
		if (slctd && !buttons[i].isSelected()) {
		    buttons[i].setSelected(true);
		    buttons[i].setBackground(selectedBG);
		} else if (!slctd && buttons[i].isSelected()) {
		    buttons[i].setSelected(false);
		    buttons[i].setBackground(unselectedBG);
		}
	    }

	    this.repaint();

	}  // setSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Button action handler.
	 *
	 * @param e button event causing handler to be activated.
	 *
	 * @param buttonLabel variable label from the button pressed.
	 */

	void processButtonAction(ActionEvent e, String buttonLabel) {

	    int modifiers = e.getModifiers();
	    boolean shifted = false;
	    if ((modifiers & ActionEvent.SHIFT_MASK) != 0) {
		//Debug.out("Shift click occurred.");
		shifted = true;
	    }

	    if ("OST".equals(eType)) {
		loopLimit = ostPlottableVars.length;
	    } else if ("RTR".equals(eType)) {
		loopLimit = rtrPlottableVars.length;
	    } else {  // We're going to assume MDS
		loopLimit = mdsPlottableVars.length;
	    }
	    int selIdx = -1;
	    for (int i = 0; i < loopLimit; i++) {
		if ("OST".equals(eType) &&
		    buttonLabel.equals(ostPlottableVars[i])) {
		    selIdx = i;
		    break;
		} else if ("RTR".equals(eType) &&
			   buttonLabel.equals(rtrPlottableVars[i])) {
		    selIdx = i;
		    break;
		} else if ("MDS".equals(eType) &&
			   buttonLabel.equals(mdsPlottableVars[i])) {
		    selIdx = i;
		    break;
		}
	    }
	    if (selIdx >= 0) {
		if (localDebug)
		    Debug.out("Button " + buttonLabel + " clicked.");

		if (buttons[selIdx].isSelected()) {
		    buttons[selIdx].setSelected(false);
		    buttons[selIdx].setBackground(unselectedBG);
		} else {
		    buttons[selIdx].setSelected(true);
		    buttons[selIdx].setBackground(selectedBG);
		}
		if ( shifted && (lastSelection >= 0) && (lastSelection != selIdx)) {
		    int inc = 1;
		    if (selIdx < lastSelection)
			inc = -1;
		    for (int j = lastSelection+inc; j != selIdx; j += inc) {
			//Debug.out(j + "button isSelected = " + buttons[j].isSelected());
			buttons[j].setSelected(!buttons[j].isSelected());
			if (buttons[j].isSelected())
			    buttons[j].setBackground(selectedBG);
			else
			    buttons[j].setBackground(unselectedBG);
		    }
		}
		lastSelection = selIdx;
	    } else {
		if (localDebug)
		    Debug.out("Unable to match click for button " + buttonLabel);
	    }

	}  // processButtonAction

    }  //   // VariablePanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to build the curve selection panel.
     */

    class CurvePanel extends JPanel {  // implements ActionListener {
	final String [] choices = {"Agg", "Max", "Min", "Avg"};
	int loopLimit;
	JButton [] buttons;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	CurvePanel () {

	    super();

	    loopLimit = choices.length;
	    GridBagLayout gridbag = new GridBagLayout();
	    setLayout(gridbag);
	    setBackground(unselectedBG);
	    GridBagConstraints c;

	    buttons = new JButton[loopLimit];
	    for (int i = 0; i < loopLimit; i++) {
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = i+1;
		c.anchor = GridBagConstraints.NORTH;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		if (i < (loopLimit-1))
		    c.weighty = 0.0;
		else
		    c.weighty = 1.0;

		buttons[i] = new JButton(choices[i]);
		buttons[i].setBackground(unselectedBG);
		buttons[i].setFont(new Font("helvetica", Font.PLAIN, 10));
		//buttons[i].setForeground(scheme.getColor(Scheme.HEADING1));
		buttons[i].setRolloverEnabled(true);
		buttons[i].setMultiClickThreshhold(250);
		//Debug.out("rowID = " + rowID);
		if ((!isAnAggPlot) &&
		    (((i == 0) && (loadAggregate)) || ((i == 1) && (loadMaximum)) ||
		     ((i == 2) && (loadMinimum)) || ((i == 3) && (loadAverage)))) {
		    if (localDebug)
			Debug.out("Set " + choices[i] + " to selected.");
		    buttons[i].setSelected(true);
		    buttons[i].setBackground(selectedBG);
		} else if (isAnAggPlot && ((i == 0) && "Agg".equals(rowID) && loadAggregate) ||
			   ((i == 1) && "Max".equals(rowID) && loadMaximum) ||
			   ((i == 2) && "Min".equals(rowID) && loadMinimum) ||
			   ((i == 3) && "Avg".equals(rowID) && loadAverage)) {
		    if (localDebug)
			Debug.out("Set " + choices[i] + " to selected.");
		    buttons[i].setSelected(true);
		    buttons[i].setBackground(selectedBG);
		} else {
		    if (localDebug)
			Debug.out("Set " + choices[i] + " to NOT selected.");
		    buttons[i].setSelected(false);
		}

		gridbag.setConstraints(buttons[i], c);
		buttons[i].addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    String buttonLabel = e.getActionCommand();
			    //System.out.println(buttonLabel + " button pressed.");

			    processButtonAction(buttonLabel);
			}
		    });
		add(buttons[i]);
	    }

	}  // CurvePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	int getNumCurvesSelected() {

	    int count = 0;
	    for (int i = 0; i < buttons.length; i++)
		if (buttons[i].isSelected())
		    count++;

	    return count;

	}  // getNumCurvesSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	String [] getSelected() {

	    String [] crvNames = {"Agg", "Max", "Min" , "Avg"};
	    String [] cNames = new String[getNumCurvesSelected()];
	    int idx = 0;
	    for (int i = 0; i < buttons.length; i++)
		if (buttons[i].isSelected())
		    cNames[idx++] = crvNames[i];

	    return cNames;

	}  // 

	void setSelected(String [] crvs2BSet) {

	    if (crvs2BSet.length <= 0)
		return;

	    for (int i = 0; i < buttons.length; i++) {
		boolean slctd = false;
		for (int j = 0; j < crvs2BSet.length; j++) {
		    if (choices[i].equals(crvs2BSet[j]))
			slctd = true;
		}
		if (slctd && !buttons[i].isSelected()) {
		    buttons[i].setSelected(true);
		    buttons[i].setBackground(selectedBG);
		} else if (!slctd && buttons[i].isSelected()) {
		    buttons[i].setSelected(false);
		    buttons[i].setBackground(unselectedBG);
		}
	    }

	    this.repaint();

	}  // setSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return state of aggregate button.
	 *
	 * @return true if aggregate button is set.
	 */

	boolean isAggregateSet() {

	    return buttons[0].isSelected();

	}  // isAggregateSet


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return state of maximum button.
	 *
	 * @return true if maximum button is set.
	 */

	boolean isMaximumSet() {

	    return buttons[1].isSelected();

	}  // isMaximumSet


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return state of minimum button.
	 *
	 * @return true if minimum button is set.
	 */

	boolean isMinimumSet() {

	    return buttons[2].isSelected();

	}  // isMinimumSet


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Return state of average button.
	 *
	 * @return true if average button is set.
	 */

	boolean isAverageSet() {

	    return buttons[3].isSelected();

	}  // isAverageSet


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Button action handler.
	 *
	 * @param buttonLabel curve label from the button pressed.
	 */

	void processButtonAction(String buttonLabel) {

	    int selIdx = -1;
	    for (int i = 0; i < loopLimit; i++) {
		if (buttonLabel.equals(choices[i])) {
		    selIdx = i;
		    break;
		}
	    }
	    if (selIdx >= 0) {
		if (localDebug)
		    Debug.out("Button " + buttonLabel + " clicked.");
		if (buttons[selIdx].isSelected()) {
		    buttons[selIdx].setSelected(false);
		    buttons[selIdx].setBackground(unselectedBG);
		} else {
		    buttons[selIdx].setSelected(true);
		    buttons[selIdx].setBackground(selectedBG);
		}
	    } else {
		if (localDebug)
		    Debug.out("Unable to match click for button " + buttonLabel);
	    }

	}  // processButtonAction

    }  // CurvePanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class defining axis type control.
     */

    class AxisControl extends JCheckBox implements ActionListener {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	AxisControl(String label, boolean selected) {

	    super(label, selected);

	    addActionListener(this);

	}  // AxisControl


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for axis control. Used to toggle between log axis and linear axis.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    if (useLogRangeAxis)
		useLogRangeAxis = false;
	    else
		useLogRangeAxis = true;

	    redrawChartPanel();

	    //Debug.out("useLogRangeAxis = " + useLogRangeAxis);

	}

    }  // AxisControl


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to build icon control and handle icon visibility settings.
     */

    class IconControl extends JCheckBox implements ActionListener {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	IconControl(String label, boolean selected) {

	    super(label, selected);

	    addActionListener(this);

	}  // IconControl


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  Handler for icon control. Used to toggle between show & hide icons.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    if (showIcons)
		showIcons = false;
	    else
		showIcons = true;

	    redrawChartPanel();
	    wideView.repaint();

	    //Debug.out("showIcons = " + showIcons);

	}  // actionPerformed

    }  // IconControl


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class defining control widgets for time interval end and duration.
     */

    class EndDurationPanel extends JPanel implements ActionListener {
	JRadioButton endButton;
	JRadioButton durButton;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 */

	EndDurationPanel() {
	    super();

	    //setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	    GridBagLayout gridbag = new GridBagLayout();
	    setLayout(gridbag);
	    GridBagConstraints c;

	    JPanel endDefPanel = new JPanel();
	    FlowLayout epfLayout = new FlowLayout(FlowLayout.LEFT);
	    endDefPanel.setLayout(epfLayout);
	    endButton = new JRadioButton("End");
	    endButton.setFont(new Font("helvetica", Font.BOLD, 10));
	    endButton.addActionListener(this);
	    endButton.setActionCommand("E");
	    endDefPanel.add(endButton);
	    JButton nowButt = new JButton("Now");
	    nowButt.setFont(new Font("helvetica", Font.BOLD, 10));
	    nowButt.setActionCommand("NOW");
	    nowButt.addActionListener(this);
	    endDefPanel.add(nowButt);
	    add(endDefPanel);
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 0;
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    c.weightx = 1.0;
	    c.weighty = 0.5;
	    //gridbag.setConstraints(endButton, c);
	    gridbag.setConstraints(endDefPanel, c);

	    JPanel durDefPanel = new JPanel();
	    FlowLayout dpfLayout = new FlowLayout(FlowLayout.LEFT);
	    durDefPanel.setLayout(dpfLayout);
	    durButton = new JRadioButton("Duration");
	    durButton.setFont(new Font("helvetica", Font.BOLD, 10));
	    durButton.addActionListener(this);
	    durButton.setSelected(true);
	    durButton.setActionCommand("D");
	    durDefPanel.add(durButton);
	    add(durDefPanel);
	    //add(durButton);
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 1;
	    c.anchor = GridBagConstraints.NORTH;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    c.weightx = 1.0;
	    c.weighty = 0.5;
	    //gridbag.setConstraints(durButton, c);
	    gridbag.setConstraints(durDefPanel, c);
	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for time interval end and duration control.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {
	    if ("D".equals(e.getActionCommand())) {
		if (localDebug)
		    Debug.out("Plot extent set to \"DURATION\"");
		useDuration = true;
		endButton.setSelected(false);
		durButton.setSelected(true);
	    } else if ("E".equals(e.getActionCommand())) {
		if (localDebug)
		    Debug.out("Plot extent set to \"END\"");
		useDuration = false;
		durButton.setSelected(false);
		endButton.setSelected(true);
	    } else if ("NOW".equals(e.getActionCommand())) {
		useDuration = false;
		durButton.setSelected(false);
		endButton.setSelected(true);
		// Set the "End" widgets to the current time.
		Date dstop = new Date(System.currentTimeMillis());
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(dstop);
		int yrstop = cal.get(Calendar.YEAR);
		yrEndChooser.setSelectedYear(yrstop);
		int mnthstop = cal.get(Calendar.MONTH);
		monthEndChooser.setSelectedMonth(mnthstop);
		int daystop = cal.get(Calendar.DAY_OF_MONTH);
		dayEndChooser.setSelectedDay(daystop);
		int hrstop = cal.get(Calendar.HOUR_OF_DAY);
		hourEndChooser.setSelectedHour(hrstop);
		int minstop = cal.get(Calendar.MINUTE);
		minuteEndChooser.setSelectedMinute(minstop);
	    }
	}
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class providing definition and controls for choosing from a list of years.
     */

    class YearChooser extends JPanel implements ActionListener {
	final JComboBox yearBox = new JComboBox();
	Date now = new Date();
	String dateString = DateFormat.getDateInstance().format(now);
	String [] chunks = dateString.split("\\s+");
	int endYear = Integer.parseInt(chunks[chunks.length-1]);
	int begYear = endYear - 5;
	int selectedYear;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor.
	 *
	 * @param startend.
	 */

	YearChooser(String startend) {
	    boolean start = false;
	    if ("start".equals(startend))
		start = true;

	    int yr2Select;
	    String [] dt;
	    if (start) {
		dt = tsStartPlot.toString().split("\\s+");
	    } else {
		dt = tsEndPlot.toString().split("\\s+");
	    }
	    String [] ymd = dt[0].split("-");
	    yr2Select = Integer.parseInt(ymd[0]);
	    //Debug.out("Year to select = " + yr2Select);

	    //System.out.println("YearChooser Constructor. begYear = " + begYear);
	    int selIdx = 0;
	    for (int i = endYear; i >= begYear; i--) {
		//System.out.println("Adding year + " + Integer.toString(i));
		yearBox.addItem(Integer.toString(i));
		if (i == yr2Select)
		    selIdx = i - endYear;
	    }
	    yearBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    yearBox.setSelectedIndex(selIdx);
	    yearBox.addActionListener(this);
	    selectedYear = yr2Select;  //endYear;
	    this.add(yearBox);
	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected year to that in the specified argument.
	 *
	 * @param year integer year to be selected.
	 */

	void setSelectedYear(int year) {
	    selectedYear = year;
	    int selIdx = endYear - year;
	    yearBox.setSelectedIndex(selIdx);
	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the year that is selected.
	 *
	 * @return integer year selected.
	 */

	int getYearSelected() {
	    return selectedYear;
	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Year chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {
	    selectedYear = endYear - yearBox.getSelectedIndex();
	    if (localDebug)
		Debug.out("Selected Year = " + selectedYear);
	}
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of months.
     */

    class MonthChooser extends JPanel implements ActionListener {
	final JComboBox monthBox = new JComboBox();
	final String [] months = {"Jan", "Feb", "Mar",
				  "Apr", "May", "Jun",
				  "Jul", "Aug", "Sep",
				  "Oct", "Nov", "Dec"};
	Date now = new Date();
	String dateString = DateFormat.getDateInstance().format(now);
	String [] chunks = dateString.split("\\s+");
	String thisMonth = chunks[0];
	int selectedMonth;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	MonthChooser(String startend) {

	    boolean start = false;
	    if ("start".equals(startend))
		start = true;

	    int mnth2Select;
	    String [] dt;
	    if (start) {
		dt = tsStart.toString().split("\\s+");
	    } else {
		dt = tsEnd.toString().split("\\s+");
	    }
	    String [] ymd = dt[0].split("-");
	    mnth2Select = Integer.parseInt(ymd[1]) - 1;

	    selectedMonth = mnth2Select;  //0;
	    for (int i = 0; i < 12; i++) {
		//System.out.println("Adding month + " + months[i]);
		monthBox.addItem(months[i]);
		//if (months[i].equals(thisMonth))
		    //selectedMonth = i;
	    }
	    monthBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    monthBox.setSelectedIndex(selectedMonth);
	    monthBox.addActionListener(this);
	    this.add(monthBox);

	}  // MonthChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected month to that in the specified argument.
	 *
	 * @param month integer month to be selected.
	 */

	void setSelectedMonth(int month) {

	    selectedMonth = month;
	    monthBox.setSelectedIndex(month);

	}  // setSelectedMonth


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the month that is selected.
	 *
	 * @return integer month selected.
	 */

	int getMonthSelected() {

	    return selectedMonth;

	}  // getMonthSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Month chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedMonth = monthBox.getSelectedIndex();
	    if (localDebug)
		Debug.out("Selected Month = " + months[selectedMonth]);

	}  // actionPerformed

    }  // MonthChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of days.
     */

    class DayChooser extends JPanel implements ActionListener {

	final JComboBox dayBox = new JComboBox();
	Date now = new Date();
	String dateString = DateFormat.getDateInstance().format(now);
	String [] chunks = dateString.split("\\s+");
	String tmpS = chunks[1].replaceAll("\\D", "");
	int today = Integer.parseInt(tmpS);
	int selectedDay;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	DayChooser(String startend) {

	    boolean start = false;
	    if ("start".equals(startend))
		start = true;
	    int day2Select;
	    String [] dt;
	    if (start) {
		dt = tsStart.toString().split("\\s+");
	    } else {
		dt = tsEnd.toString().split("\\s+");
	    }
	    String [] ymd = dt[0].split("-");
	    day2Select = Integer.parseInt(ymd[2]);

	    for (int i = 1; i < 32; i++) {
		//System.out.println("Adding month + " + i);
		dayBox.addItem(Integer.toString(i));
	    }
	    dayBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    dayBox.setSelectedIndex(day2Select-1);
	    selectedDay = day2Select;  //today;
	    dayBox.addActionListener(this);
	    this.add(dayBox);

	}  // DayChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected day to that in the specified argument.
	 *
	 * @param day integer day to be selected.
	 */

	void setSelectedDay(int day) {

	    selectedDay = day;
	    dayBox.setSelectedIndex(day-1);

	}  // setSelectedDay


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the day that is selected.
	 *
	 * @return integer day selected.
	 */

	int getDaySelected() {

	    return selectedDay;

	}  // getDaySelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Day chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedDay = dayBox.getSelectedIndex() + 1;
	    if (localDebug)
		Debug.out("Selected Day = " + selectedDay);

	}  // actionPerformed

    }  // DayChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of hours.
     */

    class HourChooser extends JPanel implements ActionListener {

	final JComboBox hourBox = new JComboBox();
	int selectedHour;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	HourChooser(String startend) {

	    boolean start = false;
	    if ("start".equals(startend))
		start = true;
	    Date now = new Date();
	    String timeString = DateFormat.getTimeInstance().format(now);
	    String [] chunks = timeString.split("\\s+");

	    int hr2Select;
	    String [] dt;
	    if (start) {
		dt = tsStart.toString().split("\\s+");
	    } else {
		dt = tsEnd.toString().split("\\s+");
	    }
	    String [] hms = dt[1].split(":");
	    hr2Select = Integer.parseInt(hms[0]) - 1;

	    //String hms = chunks[0];
	    //boolean pm = false;
	    //if ("PM".equals(chunks[1]))
	        //pm = true;
	    //String [] hmsArray = hms.split(":");
	    //int thisHour = Integer.valueOf(hmsArray[0]).intValue();
	    //if (pm && (thisHour < 12))
	    //thisHour += 12;

	    for (int i = 0; i < 24; i++) {
		//System.out.println("Adding month + " + i);
		hourBox.addItem(Integer.toString(i));
		//if (thisHour == i)
		if (hr2Select == i)
		    hourBox.setSelectedIndex(i);
	    }
	    hourBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    selectedHour = hr2Select;  //thisHour;
	    hourBox.addActionListener(this);
	    this.add(hourBox);

	}  // HourChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected hour to that in the specified argument.
	 *
	 * @param hour integer hour to be selected.
	 */

	void setSelectedHour(int hour) {

	    //Debug.out("hour arg = " + hour);
	    selectedHour = hour;
	    hourBox.setSelectedIndex(hour);

	}  // setSelectedHour


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the hour that is selected.
	 *
	 * @return integer hour selected.
	 */

	int getHourSelected() {

	    return selectedHour;

	}  // getHourSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Hour chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedHour = hourBox.getSelectedIndex();
	    if (selectedHour > 23) selectedHour = 0;
	    if (localDebug)
		Debug.out("Selected Hour = " + selectedHour);

	}  // actionPerformed

    }  // HourChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of minutes.
     */

    class MinuteChooser extends JPanel implements ActionListener {

	final JComboBox minuteBox = new JComboBox();
	int selectedMinute;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	MinuteChooser(String startend) {

	    boolean start = false;
	    if ("start".equals(startend))
		start = true;
	    Date now = new Date();
	    String timeString = DateFormat.getTimeInstance().format(now);
	    String [] chunks = timeString.split("\\s+");

	    int min2Select;
	    String [] dt;
	    if (start) {
		dt = tsStart.toString().split("\\s+");
	    } else {
		dt = tsEnd.toString().split("\\s+");
	    }
	    String [] hms = dt[1].split(":");
	    min2Select = Integer.parseInt(hms[1]);
	    if (localDebug)
		Debug.out("minute to select = " + min2Select);

	    //String hms = chunks[0];
	    //boolean pm = false;
	    //if ("PM".equals(chunks[1]))
		//pm = true;
	    //String [] hmsArray = hms.split(":");
	    ///int thisMinute = Integer.valueOf(hmsArray[1]).intValue();

	    for (int i = 0; i < 60; i++) {
		//System.out.println("Adding minute + " + i);
		minuteBox.addItem(Integer.toString(i));
	    }
	    minuteBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    minuteBox.setSelectedIndex(min2Select);  //thisMinute);
	    selectedMinute = min2Select;  //thisMinute;
	    minuteBox.addActionListener(this);
	    this.add(minuteBox);

	}  // MinuteChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected minute to that in the specified argument.
	 *
	 * @param minute integer minute to be selected.
	 */

	void setSelectedMinute(int minute) {

	    selectedMinute = minute;
	    minuteBox.setSelectedIndex(minute); //(minute-1);

	}  // setSelectedMinute


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the minute that is selected.
	 *
	 * @return integer minute selected.
	 */

	int getMinuteSelected() {

	    return selectedMinute;

	}  // getMinuteSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Minute chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {


	    selectedMinute = minuteBox.getSelectedIndex();
	    if (localDebug)
		Debug.out("Selected Minute = " + selectedMinute);

	}  // actionPerformed

    }  // MinuteChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of integers.
     */

    class IntegerChooser extends JPanel implements ActionListener {
	final JComboBox intBox = new JComboBox();
	int selectedValue;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 */

	IntegerChooser(int min, int max, int def) {

	    for (int i = min; i <= max; i++) {
		//System.out.println("Adding integer + " + i);
		intBox.addItem(Integer.toString(i));
		if (i == def) {
		    intBox.setSelectedIndex(i);
		    selectedValue = i;
		}
	    }
	    intBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    intBox.addActionListener(this);
	    this.add(intBox);

	}  // IntegerChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the integer value that is selected.
	 *
	 * @return integer value selected.
	 */

	int getValueSelected() {

	    return selectedValue;

	}  // getValueSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected integer value to that in the specified argument.
	 *
	 * @param selval integer value to be selected.
	 */

	void setSelected(int selval) {

	    intBox.setSelectedIndex(selval);
	    selectedValue = selval;

	}  // setSelected


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Integer chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedValue = intBox.getSelectedIndex();
	    if (localDebug)
		Debug.out("Selected Value = " + selectedValue);

	}  // actionPerformed

    }  // IntegerChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     *  Class providing definition and controls for choosing from a list of thinning
     * parameters. Not currently used but left in place in case it needs to be added
     * at a later date.
     */

    class ThinningChooser extends JPanel implements ActionListener {

	final JComboBox thinningBox = new JComboBox();
	final String [] methods = {"Maximum", "Minimum", "Average"};
	int selectedMethod;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for thinning chooser.
	 */

	ThinningChooser() {

	    for (int i = 0; i < 3; i++) {
		//System.out.println("Adding minute + " + i);
		thinningBox.addItem(methods[i]);
	    }
	    thinningBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    thinningBox.setSelectedIndex(2);
	    selectedMethod = 2;
	    thinningBox.addActionListener(this);
	    this.add(thinningBox);

	}  // ThinningChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the thinning method that is selected.
	 *
	 * @return integer thinning method selected.
	 */

	int getThinningMethod() {

	    return selectedMethod;

	}  // getThinningMethod


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Thinning chooser action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedMethod = thinningBox.getSelectedIndex();
	    if (localDebug)
		Debug.out("Selected Thinning Method = " + methods[selectedMethod]);

	}  // actionPerformed

    }  // ThinningChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class providing dialog used to control the heartbeat mode paramters.
     */

    class ChangeLivePanel extends JPanel implements ActionListener {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for heartbeat mode paramter control dialog.
	 */

	ChangeLivePanel() {

	    GridBagLayout clpgbl = new GridBagLayout();
	    this.setLayout(clpgbl);
	    GridBagConstraints c;

	    liveModifyButt = new JButton("Change");

	    liveModifyButt.addActionListener(this);
	    liveModifyButt.setFont(new Font("helvetica", Font.BOLD, 10));
	    liveModifyButt.setEnabled(updateLive);
	    liveModifyButt.setSize(new Dimension(42, 12));
	    add(liveModifyButt);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 0;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    clpgbl.setConstraints(liveModifyButt, c);

	    //setPreferredSize(new Dimension(45, 15));
	    //setMaximumSize(new Dimension(45, 15));

	}  // ChangeLivePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Heartbeat parameter control dialog action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {
	    if (localDebug)
		Debug.out("Clicked Live Change Button.");


	    RawLiveParams rawLiveParams = new RawLiveParams(pf2);
	    rawLiveParams.displayDialog();

	    if (PlotFrame2.this.cancel) {
		//Debug.out("Heartbeat Param change canceled.");
	    } else {
		stopRefresh();

		long ldi = liveDisplayInterval / 60000; // minutes
		String ldiString = String.valueOf(ldi) + " minutes";
		if (ldi >= (long) 60) {
		    ldi /= (long) 60;
		    ldiString = String.valueOf(ldi) + " Hours";
		}
		rlIntervalLabel.setText("Interval = " + ldiString);

		int rlr = refreshRate / 1000;
		String rlrString = String.valueOf(rlr) + " Seconds";
		if (rlr > 60) {
		    rlr /= 60;
		    rlrString = String.valueOf(rlr) + " Minutes";
		}
		rlRateLabel.setText("Refresh Rate = " + rlrString);

		refreshPlotFrame();
	    }

	}  // actionPerformed

    }  // ChangeLivePanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class providing control widget for the plot granularity.
     */

    class GranularityChooser extends JPanel implements ActionListener {

	final JComboBox granularityBox = new JComboBox();
	final String [] grains = {"Raw", "Hour", "Day", "Week",
				  "Month", "Year", "Heartbeat"};
	int selectedGranularity;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the plot granularity controller.
	 */

	GranularityChooser() {

	    for (int i = 0; i < grains.length; i++) {
		//System.out.println("Adding minute + " + i);
		granularityBox.addItem(grains[i]);
	    }
	    granularityBox.setFont(new Font("helvetica", Font.PLAIN, 10));
	    //granularityBox.setSelectedIndex(0);
	    granularityBox.setSelectedIndex(granularity-1);
	    //selectedGranularity = intialGranularity - 1;
	    selectedGranularity = granularity - 1;
	    granularityBox.addActionListener(this);
	    this.add(granularityBox);

	}  // GranularityChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the granularity that is selected.
	 *
	 * @return integer granularity that is selected.
	 */

	int getGranularity() {

	    return selectedGranularity + 1;

	}  // getGranularity


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Set the selected granularity value to that in the specified argument.
	 *
	 * @param grain integer granularity value to be selected.
	 */

	public void setGranularity(int grain) {

	    granularityBox.setSelectedIndex(grain-1);
	    selectedGranularity = grain - 1;

	}  // setGranularity


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Granularity control action handler.
	 *
	 * @param e the action causing this handler to be activated.
	 */

	public void actionPerformed(ActionEvent e) {

	    selectedGranularity = granularityBox.getSelectedIndex();
	    //Debug.out("selectedGranularity = " + selectedGranularity);

	    if (selectedGranularity == 6) {
		liveModifyButt.setEnabled(true);
		liveModLabel.setForeground(Color.black);
		rlIntervalLabel.setForeground(Color.black);
		rlRateLabel.setForeground(Color.black);
	    } else {
		liveModifyButt.setEnabled(false);
		liveModLabel.setForeground(Color.gray);
		rlIntervalLabel.setForeground(Color.gray);
		rlRateLabel.setForeground(Color.gray);
	    }

	    if (localDebug)
		Debug.out("Selected Granularity = " + grains[selectedGranularity]);

	}  // actionPerformed

    }  // GranularityChooser


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to compute, draw and handle controls for the pan/zoom overview plot panel.
     */

    class OverView extends JPanel {

	Point point = null;
	//ApplicationFrame pf;
	Dimension preferredSize = new Dimension(1000, 70);  //50);
	private final int xMargin = 15;  //0;  //10;
	private final int yMargin = 0;  //10;
	Color curveColor = Color.magenta;
	Color beige = new Color(225, 196, 143);
	Color brown = new Color(104, 78,49);

	int pWid;
	int pHyt;

	Point pressPt = null;
	Point releasePt = null;
	boolean pressed = false;
	int xIndexLo;
	int xIndexHi;
	double rawXLo;
	double rawXHi;
	int xb;
	int xe;

	double xSpan;
	double ySpan;

	double xMap;
	double yMap;

	int dragX;
	int newXLB;
	int newXLE;

	int xL;
	int xR;
	String clickedItem = null;
	public boolean first = true;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constrcutor for the pan/zoom control, overview plot panel.
	 */

	public OverView() {   //(ApplicationFrame f) {


	    //this.pf = f;

	    //Border raisedBevel = BorderFactory.createRaisedBevelBorder();
	    //Border loweredBevel = BorderFactory.createLoweredBevelBorder();
	    //Border compound = BorderFactory.createCompoundBorder
		//(raisedBevel, loweredBevel);

	    //setBorder(compound);

	    addMouseListener(new MouseAdapter() {

		    public void mousePressed(MouseEvent e) {

			// If a HEARTBEAT display, we don't want any interaction going on.
			if (granularity == HEARTBEAT)
			    return;

			int x = e.getX();
			int y = e.getY();
			if (pressPt == null) {
			    pressPt = new Point(x, y);
			} else {
			    pressPt.x = x;
			    pressPt.y = y;
			}

			x = Math.max(0, x-xMargin);

			//System.out.println("xL = " + xL + "   xR = " + xR +
					   //"  x = " + x +
					   //"   pressPt.x = " + pressPt.x);
			if ((pressPt.x >= xL) && (pressPt.x <= xR)) {

			    pressed = true;
			    dragX = pressPt.x;
			    newXLB = xL;  //xb;
			    newXLE = xR;  //xe;
			    //System.out.println("Mouse Pressed...");
			    //System.out.println("ra Clicked in wide view data segment." +
					       //" startIndex, stopIndex = " + startIndex +
					       //", " + stopIndex + "  xb, xe = " +
					       //xb + ", " + xe);
			}

			clickedItem = null;

			if ((((Math.abs(pressPt.x - xL) < 8) && (pressPt.x < xR)) ||
			     (pressPt.x < xL)) && (y < pHyt-20)) {
			    clickedItem = "low";
			    //System.out.println("Clicked in Low box xb, xL, dragX = " +
					       //xb + ", " + xL + ", " + dragX);
			} else if ((((Math.abs(pressPt.x - xR) < 8) && (pressPt.x > xL)) ||
				   (x > xR)) && (y < pHyt-20)) {
			    clickedItem = "hi";
			    //System.out.println("Clicked in High box xe, xR, dragX = " +
					       //xe + ", " + xR + ", " + dragX);
			} else if (((pressPt.x - xMargin) < 0) &&
				   (y >= pHyt-20)) {
			    clickedItem = "leftshift";
			    //System.out.println("Clicked in Left Shift arrow");
			} else if ((x > pWid) &&
				   (y >= pHyt-20)) {
			    clickedItem = "rightshift";
			    //System.out.println("Clicked in Right Shift arrow");
			} else {
			    clickedItem = null;
			    //System.out.println("Clicked in No box x = " + x +
					       //"  xL = " + xL + "  xR = " + xR);
			}

		    }  // mousePressed

		    public void mouseReleased(MouseEvent e) {

			boolean doit;
			doit = false;

			// If a HEARTBEAT display, we don't want any interaction going on.
			if (granularity == HEARTBEAT)
			    return;

			int x = e.getX();
			int y = e.getY();
			int avgDL = rawTimes[ovIDX/nCurvesSelected].length - 1;  // / 2;
			//System.out.println("mouseReleased " + x + ", " + y);
			if (releasePt == null) {
			    releasePt = new Point(x, y);
			} else {
			    releasePt.x = x;
			    releasePt.y = y;
			}


			if ((clickedItem != null) &&
			    ("leftshift".equals(clickedItem)) &&
			    (ovStartPlot.getTime() > tsStart.getTime())) {

			    //Debug.out("Left shift current ovStart & ovStop = " +
				      //ovStartPlot + " - " + ovEndPlot);

			    if (limitedDebug)
				Debug.out("ovStartPlot - ovEndPlot = " + ovStartPlot +
				    " - " + ovEndPlot + "\ntsStart - tsEnd = " +
				    tsStart + " - " + tsEnd);

			    long startT = ovStartPlot.getTime();
			    long endT = ovEndPlot.getTime();
			    long currentOVTRange = endT - startT;
			    long shiftAmount = currentOVTRange / 2;
			    ovStartPlot = new Timestamp(Math.max(startT - shiftAmount,
								 tsStart.getTime()));
			    ovEndPlot = new Timestamp(Math.min(tsEnd.getTime(),
					    ovStartPlot.getTime() + currentOVTRange));

			    long midT = (ovStartPlot.getTime()+ovEndPlot.getTime())/2;
			    long ntrvlW = (ovEndPlot.getTime()-ovStartPlot.getTime())/10;
			    tsStartPlot = new Timestamp(Math.max(tsStart.getTime(),
								 midT-ntrvlW));
			    tsEndPlot = new Timestamp(Math.min(tsEnd.getTime(), midT+ntrvlW));

			    if (limitedDebug)
				Debug.out("ovStartPlot - ovEndPlot = " + ovStartPlot +
				    " - " + ovEndPlot + "\ntsStartPlot - tsEndPlot = " +
				    tsStartPlot + " - " + tsEndPlot);

			    // Set first flag to true to force calculation of overview
			    // window shaded rect to coincide with chart data interval.
			    //                               05/30/2007  P. Spencer
			    first = true;

			    repaint();
			    doit = true;

			    //Debug.out("New ovStart & ovStop = " + ovStartIndex +
				      //" - " + ovStopIndex);

			} else if ((clickedItem != null) &&
				   ("rightshift".equals(clickedItem)) &&
				   (ovEndPlot.getTime() < (tsEnd.getTime()))) {

			    //Debug.out("Right shift current ovStart & ovStop = " +
				      //ovStartIndex + " - " + ovStopIndex);

			    if (limitedDebug)
				Debug.out("ovStartPlot - ovEndPlot = " + ovStartPlot +
				    " - " + ovEndPlot + "\ntsStart - tsEnd = " +
				    tsStart + " - " + tsEnd);

			    long startT = ovStartPlot.getTime();
			    long endT = ovEndPlot.getTime();
			    long currentOVTRange = endT - startT;
			    long shiftAmount = currentOVTRange / 2;
			    ovEndPlot = new Timestamp(Math.min(endT + shiftAmount,
							       tsEnd.getTime()));
			    ovStartPlot = new Timestamp(Math.max(ovEndPlot.getTime() -
							currentOVTRange, tsStart.getTime()));
			    long midT = (ovStartPlot.getTime()+ovEndPlot.getTime())/2;
			    long ntrvlW = (ovEndPlot.getTime()-ovStartPlot.getTime())/10;
			    tsStartPlot = new Timestamp(Math.max(tsStart.getTime(),
								 midT-ntrvlW));
			    tsEndPlot = new Timestamp(Math.min(tsEnd.getTime(), midT+ntrvlW));

			    if (limitedDebug)
				Debug.out("ovStartPlot - ovEndPlot = " + ovStartPlot +
				    " - " + ovEndPlot + "\ntsStartPlot - tsEndPlot = " +
				    tsStartPlot + " - " + tsEndPlot);

			    // Set first flag to true to force calculation of overview
			    // window shaded rect to coincide with chart data interval.
			    //                               05/30/2007  P. Spencer
			    first = true; 

			    repaint();
			    doit = true;

			    //Debug.out("New ovStart & ovStop = " + ovStartIndex +
				      //" - " + ovStopIndex);

			} else if (pressed) {
			    if (limitedDebug) {
				Debug.out("Mouse press, release = " + pressPt.x + " - " +
					  releasePt.x);
			    }
			    xL = newXLB;
			    xR = newXLE;
			}

			if (((pressPt != null) && (releasePt != null) &&
			     (pressPt.x != releasePt.x)) || doit) {

			    //if (! doit) updateRawXIndices(xb, xe);
			    if (! doit) {
				double ratioL = (double) (xL-xMargin)/(double) pWid;
				double ratioR = (double) (xR-xMargin)/(double) pWid;
				double ovspD = (double) ovStartPlot.getTime();
				double ovepD = (double) ovEndPlot.getTime();
				if (limitedDebug) {
				    Debug.out("tsStartPlot before = " + tsStartPlot.getTime());
				    Debug.out("tsEndPlot before = " + tsEndPlot.getTime());
				    Debug.out("ovStartPlot = " + ovStartPlot.getTime());
				    Debug.out("ovEndPlot = " + ovEndPlot.getTime());
				}
				if (limitedDebug) {
				    Debug.out("xL = " + xL + "  pWid = " + pWid +
					      "  ratio xL/pWid = " + ratioL);
				    Debug.out("xb, xe = " + xb + ", " + xe);
				}
				long newTStartP = (long)((ratioL * (ovepD-ovspD)) + ovspD);
				long newTEndP =   (long)((ratioR * (ovepD-ovspD)) + ovspD);

				tsStartPlot = new Timestamp(newTStartP);
				tsEndPlot = new Timestamp(newTEndP);

				if (limitedDebug) {
				    Debug.out("(long)tsStartPlot after = " + newTStartP);
				    Debug.out("tsStartPlot after = " + tsStartPlot);
				    Debug.out("(long)tsEndPlot after = " + newTEndP);
				    Debug.out("tsEndPlot after = " + tsEndPlot);
				}
			    }

			    // Update ammaLabel with adjusted Agg, Max, Min and Avg values
			    String bText = ammaLabel.getText();
			    String [] rcc = bText.split(" : ");
			    if (rcc.length > 2) {
				if (!(rcc[2].equals("Agg") || rcc[2].equals("Max") ||
				      rcc[2].equals("Min") || rcc[2].equals("Avg"))) {
				    rcc[2] = crvs2Plot[0];
				}
				ammaLabel.setText(rcc[0] + " : " + rcc[1] + " : " +
						  rcc[2] + getDataText(bText));
			    }

			    // Update the start & stop/duration widgets with the values from
			    // overview panel.
			    updateControlValues();

			    chartContainerPane.remove((Component)chartPanel);
			    chartPanel = chartContainerPane.createChartPanel();
			    chartPanel.setPreferredSize(new java.awt.Dimension(1000, 350));
			    chartPanel.setMouseZoomable(true, false);
			    chartContainerPane.add(chartPanel);  //
			    GridBagConstraints c;
			    c = new GridBagConstraints();
			    c.gridx = 0;
			    c.gridy = 1;
			    //                   Top, Left, Bottom, Right
			    c.insets = new Insets(2, 2, 0, 2);  //(8, 4, 0, 5);
			    c.anchor = GridBagConstraints.NORTH;
			    c.fill = GridBagConstraints.BOTH;
			    c.weightx = 1.0;  //1.0;
			    c.weighty = 1.0;
			    ccgbl.setConstraints(chartPanel, c);
			    chartContainerPane.invalidate();
			    chartContainerPane.validate();

			    // When the following 2 statements are uncommented, the overview
			    // highlight exactly matches the chart plot. The problem is that
			    // this causes an annoying jump of approx. a dozen pixels in the
			    // highlight interval in the overview plot.

			    //first = true; 
			    //repaint();
			}

			pressed = false;

		    }  // mouseReleased

		});  // addMouseListener(new MouseAdapter() {}


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     *
	     */

	    addMouseMotionListener(new MouseMotionAdapter() {

		public void mouseDragged(MouseEvent e) {

		    // If a HEARTBEAT display, we don't want any interaction going on.
		    if (granularity == HEARTBEAT)
			return;

		    int x = e.getX();
		    int y = e.getY();
		    //System.out.println("Dragged xL = " + xL + "   xR = " + xR +
				       //"  x, y = " + x + ", " + y);
		    //System.out.println("mouseDragged " + x + ", " + y);

		    if (pressed) {
			if (clickedItem != null && "low".equals(clickedItem)) {
			    //System.out.println("Sliding Left Edge. x & xL = " + x + " & " + xL);
			    if ((x >= 0) && (x < (xR-8))) {
				xL = Math.max(xMargin, xL + x - dragX);  //+= xDist;
			    } else if ((x >= 0) && (x >= (xR-8))) {
				xL = xR - 8;
			    }
			    newXLB = xL;
			    xb = xL;
			    dragX = x;
			} else if (clickedItem != null && "hi".equals(clickedItem)) {
			    //System.out.println("Sliding Right Edge. x & xR = " + x + " & " + xR);
			    if ((x < pWid) && (x > (xL+8))) {
				xR = Math.min(pWid+xMargin, xR + x - dragX);  //+= xDist;
			    } else if((x < pWid) && (x <= (xL+8))) {
				xR = xL + 8;
			    }
			    newXLE = xR;
			    xe = xR;
			    dragX = x;
			} else {
			    //System.out.println("Sliding whole window.");

			    //System.out.println("xb = " + xb + "  x = " + x +
				       //"  dragX = " + dragX + "  newXLB = " + newXLB);

			    newXLB += x - dragX;
			    newXLE += x - dragX;

			    newXLB = Math.max(xMargin, newXLB);
			    newXLE = Math.min(newXLE, pWid+xMargin);
			    xb = newXLB;
			    xe = newXLE;
			    dragX = x;
			    if (limitedDebug) {
				Debug.out("xb, xe = " + xb + ", " + xe);
			    }
			    //System.out.println("newXLB = " + newXLB + "  xb = " + xb +
					       //"  dragX = " + dragX);
			}

		 	repaint();

		    }

		}  // mouseDragged

	    });  // addMouseMotionListener(new MouseMotionAdapter() {}

	} // OverView


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Get the preferred size of this container.
	 *
	 * @return Dimension of the preferred size.
	 */

	public Dimension getPreferredSize() {

	    return preferredSize;

	}  // getPreferredSize


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Overridden drawing method.
	 *
	 * @param g Graphics context for this plot.
	 */

	public void paintComponent(Graphics g) {

	    super.paintComponent(g);  // paint background

	    pWid = getWidth() - xMargin*2;
	    pHyt = getHeight() - yMargin*2 - 20;
	    //System.out.println("Panel dimensions : " + pWid + " X " + pHyt);
	    setBackground(Color.black);

	    if (rawData == null)
		return;

	    drawXYPlot(g);

	}  // paintComponent
    

	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Draw the overview plot with pan/zoom controls.
	 *
	 * @param g Graphics context for this plot.
	 */

	void drawXYPlot(Graphics g) {

	    //Debug.out("Start drawXYPlot");

	    if (nCurvesSelected <= 0 || nColsSelected <= 0 || nRowsSelected <= 0)
		return;

	    pWid = getWidth() - xMargin*2;
	    pHyt = getHeight() - yMargin*2 - 20;  // The last 20 is the slider width
	    int tindx = ovIDX / nCurvesSelected;

	    if (localDebug)
		Debug.out("rawData length = " + rawTimes[tindx].length);  // +
	                //"  OverviewData length = " + overviewData.length);

	    double xLo = 0.0;
	    double xHi = 1.0;
	    
	    if (localDebug)
		Debug.out("\n**** ovrstart/stop & length = " + ovStartPlot +
			  " " + ovEndPlot + " " + rawTimes[tindx].length);

	    xLo = (double)ovStartPlot.getTime();
	    xHi = (double)ovEndPlot.getTime();

	    if (dataPointCount[ovIDX] <= 0) {
		yLo = 0.0;
		yHi = 100.0;
	    } else {
		yLo = ovRangeMin[ovIDX];
		yHi = ovRangeMax[ovIDX] +
		    ((ovRangeMax[ovIDX] - ovRangeMin[ovIDX]) / 10.);
	    }

	    xSpan = (xHi - xLo);// + 1.0;
	    ySpan = (yHi - yLo);// + 1.0;
	    xMap = xSpan / (double)(pWid);
	    yMap = ySpan / (double)(pHyt);

	    if (ySpan < (double) (0.1)) {
		yLo -= (double) (1.0);
		yHi += (double) (1.0);
		ySpan = (yHi - yLo);// + 1.0;
		yMap = ySpan / (double)(pHyt);
	    }
	    int inc = 1;  //(int)(xMap);

	    if (localDebug) {
		Debug.out("xLo, xHi = " + xLo + ", " + xHi);
		Debug.out("xSpan, xMap = " + xSpan + ", " + xMap);
		Debug.out("yLo, yHi = " + yLo + ", " + yHi);
		Debug.out("ySpan, yMap = " + ySpan + ", " + yMap);
		Debug.out("xMargin = " + xMargin);
	    }

	    double gXInc = (xHi - xLo) / ((double) 10.);
	    double gYInc = ySpan / ((double) 10.);

	    // Erase any existing plots
	    g.setColor(Color.black);
	    g.fillRect(0, 0, pWid, pHyt);

	    // If this is a HEARTBEAT display, we don't want any interaction going on.
	    if (granularity == HEARTBEAT)
		return;

	    // Set background of upper plot rectangle to white.
	    //if (localDebug)
	        //Debug.out("\nstartIndex = " + startIndex + "  stopIndex = " + stopIndex);

	    if (first) {
		//if (startIndex < stopIndex) {
		    rawXLo = (double)tsStartPlot.getTime();
		    rawXHi = (double)tsEndPlot.getTime();
		    if (localDebug) {
			Debug.out("tsStartPlot = " + tsStartPlot);
			Debug.out("tsEndPlot = " + tsEndPlot);
			Debug.out("ovStartPlot = " + ovStartPlot);
			Debug.out("ovEndPlot = " + ovEndPlot);
			Debug.out("rawXLo = " + (long)rawXLo + " rawXHi = " +
				  (long)rawXHi);
			double rxxL = rawXLo - xLo;
			Debug.out("rawXLo - xLo = " + (long)rxxL);
			rxxL = rxxL/xMap;
			Debug.out("(rawXLo - xLo) / xMap = " + (long)rxxL);
			double rxxH = rawXHi - xLo;
			Debug.out("rawXHi - xLo = " + (long)rxxH);
			rxxH = rxxH/xMap;
			Debug.out("(rawXHi - xLo) / xMap = " + (long)rxxH);
		    }
		    xb = (int)((rawXLo - xLo) / xMap) + xMargin;
		    xe = ((int)((rawXHi - xLo) / xMap) + xMargin);
		    //Debug.out("(first) Paint white rectangle. X-range " +
			      //xb + " - " + xe);
		    g.setColor(Color.white);
		    g.fillRect(xb, 0, xe-xb, pHyt);

		//}
	    } else {
		//Debug.out("Paint white rectangle. X-range " +
			  //xb + " - " + xe);

		g.setColor(Color.white);
		g.fillRect(xb, 0, xe-xb, pHyt);
		
	    }

	    //
	    // Draw a grid around the plot area
	    //
	    g.setColor(Color.gray);

	    int ixInc = (int) ((xHi - xLo) / (xMap*10.));
	    int yip = (int)((yHi - yLo) / yMap) + yMargin;
	    for (int ii = 1; ii < 10; ii++) {
		g.drawLine(ixInc*ii + xMargin, yip, ixInc*ii + xMargin, yMargin);
	    }


	    double gy = yLo + gYInc;
	    while (gy < yHi) {
		g.drawLine(xMargin,
			   (int)((gy - yLo) / yMap) + yMargin,
			   (int)((xHi - xLo) / xMap) + xMargin,
			   (int)((gy - yLo) / yMap) + yMargin);
		gy += gYInc;
	    }

	    // Add beige expanders on the left & right sides.
	    g.setColor(beige);
	    g.fillRect(xb, 0, 8, pHyt);
	    g.fillRect(xe-8, 0, 8, pHyt);

	    // Add hash marks to the left & right expanders
	    g.setColor(brown);
	    g.fillRect(xb, pHyt/2, 8, 2);
	    g.fillRect(xb, pHyt/2+6, 8, 2);
	    g.fillRect(xb, pHyt/2-6, 8, 2);

	    g.fillRect(xe-8, pHyt/2, 8, 2);
	    g.fillRect(xe-8, pHyt/2+6, 8, 2);
	    g.fillRect(xe-8, pHyt/2-6, 8, 2);


	    if (first) {
		xL = xb;
		xR = xe;
		first = false;
	    }

	    // Draw box for lower slider
	    g.setColor(brown);
	    //g.fillRect(0, pHyt+1, pWid-1, 18);
	    g.fillRect(xMargin, pHyt+1, (int)((xHi - xLo) / xMap), 18);

	    g.setColor(beige);
	    g.fillRect(xb, pHyt+1, xe-xb, 18);

	    g.setColor(brown);
	    int midPt = xb + ((xe-xb) / 2);
	    g.fillRect(midPt, pHyt, 2, pHyt+20);
	    if (xe > 10) {
		g.fillRect(midPt-6, pHyt, 2, pHyt+20);
		g.fillRect(midPt+6, pHyt, 2, pHyt+20);
	    }

	    //
	    // Draw left & right data shift arrow widgets
	    //
	    int rEndBoxX = xMargin+(int)((xHi - xLo) / xMap);

	    if (ovStartPlot.getTime() > tsStart.getTime())
		g.setColor(Color.green);
	    else
		g.setColor(Color.red);
	    g.fillRect(0, pHyt+1, xMargin, 18);


	    //System.out.println("overviewData.length = " + overviewData.length + " ovStopIndex = " + ovStopIndex);  
	    if (ovEndPlot.getTime() >= (tsEnd.getTime()))
		g.setColor(Color.red);
	    else
		g.setColor(Color.green);

	    g.fillRect(xMargin+(int)((xHi - xLo) / xMap), pHyt+1, xMargin, 18);


	    if (ovStartPlot.getTime() > tsStart.getTime())
		g.setColor(Color.black);
	    else
		g.setColor(Color.red);
	    int [] lArrowX = new int[]{12, 12, 3};
	    int [] lArrowY = new int[]{pHyt+16, pHyt+4, pHyt+10};
	    g.fillPolygon(lArrowX, lArrowY, 3);


	    if (ovEndPlot.getTime() >= (tsEnd.getTime()))
		g.setColor(Color.red);
	    else
		g.setColor(Color.black);
	    int [] rArrowX = new int[]{rEndBoxX+2, rEndBoxX+2, rEndBoxX+10};
	    int [] rArrowY = new int[]{pHyt+16, pHyt+4, pHyt+10};
	    g.fillPolygon(rArrowX, rArrowY, 3);

	    g.setColor(Color.white);

	    int x1 = xMargin;
	    int y1 = (int)((yHi - yLo) / yMap) + yMargin;
	    int x2 = (int)((xHi - xLo) / xMap) + xMargin;
	    int y2 = y1;
	    g.drawLine(x1, y1, x2, y2);  // Bottom-Left to Bottom-Right
	    //System.out.println("drawLine(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");

	    x1 = x2;
	    y2 = yMargin;
	    g.drawLine(x1, y1, x2, y2);  // Bottom-Right to Top-Right
	    //System.out.println("drawLine(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");

	    y1 = yMargin;
	    x2 = xMargin;
	    g.drawLine(x1, y1, x2, y2);  // Top-Right to Top-Left
	    //System.out.println("drawLine(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");

	    x1 = xMargin;
	    y2 = (int)((yHi - yLo) / yMap) + yMargin;
	    g.drawLine(x1, y1, x2, y2);  // Top-Left to Bottom-Left
	    //System.out.println("drawLine(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");

	    //System.out.println("yLo = " + yLo + "  yHi = " + yHi + "  yMap = " + yMap + "  yMargin = " + yMargin);
	    //g.setColor(Color.cyan);
	    //g.drawLine(5, 0, 5, 68);


	    //
	    // Plot the data
	    //
	    if (localDebug)
		Debug.out("Plot Overview curve " + ovIDX + "  using Y range of " +
			  yLo + " -- " + yHi);
	    int colNumb = 0;
	    int xCLim = (int)((xHi - xLo) / xMap) + xMargin;

	    //System.out.println("Plotting curve # " + ovIDX);
	    int cNumb = -1;
	    for (int i = 0; i <= ovIDX; i++) {
		if (dataPointCount[i] >= 0)
		    cNumb++;
	    }
	    if (dataPointCount[ovIDX] >= 0) {
		//Debug.out("Plot curve w/ legendColors[" + cNumb + "] = " +
			  //legendColors[cNumb]);

		g.setColor(legendColors[cNumb]);  //[ovIDX]);  //(curveColor);
		long t0 = ovStartPlot.getTime();
		long t1 = ovEndPlot.getTime();

		for (int i = inc; i < dataPointCount[ovIDX]; i+=inc) {
		    //System.out.println(i + " : " +t0 + "  [" + rawTimes[tindx][i] + "]  " + t1);
		    if ((rawTimes[tindx][i-inc] >= t0) && (rawTimes[tindx][i] <= t1)) {

			//Debug.out(i + "  y = " + rawData[ovIDX][i]);

			int px0 = (int)(((double)rawTimes[tindx][i-inc] - xLo) / xMap) +
			    xMargin;
			int py0 = (int)((yHi - rawData[ovIDX][i-inc]) / yMap) + yMargin;
			int px1 = (int)(((double)rawTimes[tindx][i] - xLo) / xMap) +
			    xMargin;
			int py1 = (int)((yHi - rawData[ovIDX][i]) / yMap) + yMargin;

			if ((px0 >= xMargin) && (px1 <= xCLim)) {
			    if (showIcons)
				g.setColor(legendColors[cNumb]);  //[ovIDX]);

			    g.drawLine(px0, py0, px1, py1);

			    if (showIcons) {
				if (px0 >= xb && px0 <= xe)
				    g.setColor(brown);

				g.drawLine(px0, py0-3, px0, py0+3);
			    }
			}
		    }
		}
	    }

	}  // drawXYPlot

    }  // OverView


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Window handler for the the PlotFrame2 class.
     */

    class WindowH extends WindowAdapter {

	public void windowOpened(WindowEvent e) {

	    if (localDebug)
		Debug.out("[windowOpened]");

	}  // windowOpened

	public void windowActivated(WindowEvent e) {

	    if (localDebug)
		Debug.out("[windowActivated]");

	}  // windowActivated

	public void windowDeactivated(WindowEvent e) {

	    if (localDebug)
		Debug.out("[windowDeactivated]");

	}  // windowDeactivated

	public void windowClosing(WindowEvent e) {

	    if (localDebug)
		Debug.out("[windowClosing]");

	}  // windowClosing

	public void windowClosed(WindowEvent e) {


	    if (localDebug)
		Debug.out("[windowClosed]");
	    stopRefresh();

	}  // windowClosed

    }  // WindowH


}  // PlotFrame2
