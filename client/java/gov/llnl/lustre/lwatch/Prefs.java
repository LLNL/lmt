package gov.llnl.lustre.lwatch;

import java.awt.*;
import java.io.*;
import javax.swing.*;

import gov.llnl.lustre.lwatch.util.Debug;

// Database imports
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.*;


/**
 * Class that provides the loading and storing of preferences to a disk file.
 */

public class Prefs {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("Prefs.debug");

    String userHome = System.getProperty("user.home");
    File prefsFile = null;

    public boolean showMDS = true;
    public boolean showOST = true;
    public boolean showOSS = true;
    public boolean showRTR = true;
    public int tableRefreshRate = 5000;  // MSecs

    public int liveRefreshRate = 15000;  // MSecs
    public long liveDisplayInterval = 120;  // Minutes


    // Still to be implemented

    public int plotGranularity = Database.RAW;
    public int plotInterval = 60;  // Minutes
    public boolean showPlotIcons = false;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for Prefs class.
     */

    Prefs() {

	//System.out.println("user.home = " + userHome);

	File f = new File(userHome + "/.sdm");
	if (! f.exists()) {
	    if ( !f.mkdir()) {
		Debug.out("Error, unable to create prefs directory " +
			  userHome + "/.sdm");
	    }
	}

	f = new File(userHome + "/.sdm/lwatch");
	if (! f.exists()) {
	    if ( !f.mkdir()) {
		Debug.out("Error, unable to create prefs directory " +
			  userHome + "/.sdm/lwatch");
	    }
	}
	prefsFile = new File(userHome + "/.sdm/lwatch/lwatch.prefs");

	prefsLoad();

    }  // Prefs


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Read preferences from file.
     */

    public void prefsLoad() {

	if (localDebug)
	    Debug.out("Load prefs from file if it exists.");

	if (! prefsFile.exists()) {
	    Debug.out("prefs file does NOT exist...");
	    return;
	}

	// Open an input stream/buffered reader.
	InputStream inStream;
	try {
	    inStream = new FileInputStream(prefsFile);
	} catch (java.io.FileNotFoundException e) {
	    Debug.out("Preferences file not found.\n" + e.getMessage());
	    return;
	}
	if (inStream == null) {
	    Debug.out("input stream is null...");
	    return;
	}

	InputStreamReader isr = new InputStreamReader(inStream);
	BufferedReader bisr = new BufferedReader(isr);

	String line;
	try {
	    while ((line = bisr.readLine()) != null) {
		//Debug.out("Line input = [" + line + "]");
		if (line.indexOf("showMDS") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			if ("false".equals(parts[1]))
			    showMDS = false;
			else
			    showMDS = true;
		    }
		} else if (line.indexOf("showOST") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			if ("false".equals(parts[1]))
			    showOST = false;
			else
			    showOST = true;
		    }
		} else if (line.indexOf("showOSS") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			if ("false".equals(parts[1]))
			    showOSS = false;
			else
			    showOSS = true;
		    }
		    //Debug.out("showOSS = " + showOSS);
		} else if (line.indexOf("showRTR") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			if ("false".equals(parts[1]))
			    showRTR = false;
			else
			    showRTR = true;
		    }
		} else if (line.indexOf("tableRefreshRate") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			tableRefreshRate = Integer.parseInt(parts[1]);
			//Debug.out("tableRefreshRate is " + tableRefreshRate);
		    }
		} else if (line.indexOf("liveDisplayNtrvl") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			liveDisplayInterval = Long.parseLong(parts[1]);
		    }
		} else if (line.indexOf("liveRefreshRate") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			liveRefreshRate = Integer.parseInt(parts[1]);
		    }
		} else if (line.indexOf("plotGranularity") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			plotGranularity = Integer.parseInt(parts[1]);
		    }
		} else if (line.indexOf("showPlotIcons") >= 0) {
		    String [] parts = line.split("=");
		    if (parts.length > 1) {
			if ("false".equals(parts[1]))
			    showPlotIcons = false;
			else
			    showPlotIcons = true;
		    }
		    //Debug.out("showPlotIcons = " + showPlotIcons);
		}
			
	    }

	     inStream.close();

	} catch (IOException e) {
	    Debug.out("IOException caught while loading preferences.\n" + e.getMessage());
	    e.printStackTrace();
	}

    }  // prefsLoad


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Write preferences to file.
     */

    public void prefsWrite() {

	if (localDebug) {
	    Debug.out("Write prefs to ~/.sdm/lwatch/lwatch.prefs");
	    Debug.out("getPath() returns " + prefsFile.getPath());
	}

	try {
	    if (! prefsFile.exists()) {
		if (!prefsFile.createNewFile()) {
		    throw new IOException("Error, creating new prefs file " +
			      prefsFile.toString());
		}
	    }
	    // Dump some defaults into the new prefs file.

	    // Open an output stream/buffered writer.
	    FileOutputStream prefsOutStream = null;
	    OutputStreamWriter outStrWrtr = null;
	    BufferedWriter buffWrtr = null;

	    prefsOutStream = new FileOutputStream(prefsFile);
	    outStrWrtr = new OutputStreamWriter(prefsOutStream);
	    buffWrtr = new BufferedWriter(outStrWrtr);

	    String p1 = "lwatch.TableFrame.showMDS=" + showMDS + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.TableFrame.showOST=" + showOST + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.TableFrame.showOSS=" + showOSS + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.TableFrame.showRTR=" + showRTR + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.TableFrame.tableRefreshRate=" + tableRefreshRate + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.PlotFrame.liveDisplayNtrvl=" + liveDisplayInterval + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.PlotFrame.liveRefreshRate=" + liveRefreshRate + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.PlotFrame.plotGranularity=" + plotGranularity + "\n";
	    buffWrtr.write(p1);

	    p1 = "lwatch.PlotFrame.showPlotIcons=" + showPlotIcons + "\n";
	    buffWrtr.write(p1);

	    //Debug.out("liveDisplayInterval = " + liveDisplayInterval +
		      //"  liveRefreshRate = " + liveRefreshRate);

	    buffWrtr.close();

	} catch (IOException e) {
	    Debug.out("Exception caught creating prefs file\n" +
		      e.getMessage());
	}

    }  // prefsWrite


    public static void main(String argv[]) {
	//if (localDebug)
	    //Debug.out("Starting...");

	Prefs prefs = new Prefs();

	System.exit(0);
    }  // main

}  // Prefs
