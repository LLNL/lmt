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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.JScrollPane;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.border.Border;
import java.util.Enumeration;


// Refresh timer imports
import java.util.Vector;
import java.lang.Integer;
import java.util.Timer;
import java.util.TimerTask;


// Database imports
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.*;

// sql imports
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;

import gov.llnl.lustre.database.Database.TimeInfo;

import gov.llnl.lustre.lwatch.util.Debug;


/**
 * Class defining the panels used to display individual file system tables
 * and controls.
 */

public class FileSystemPanel extends JPanel {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("FileSystemPanel.debug");

    public final static boolean useDB = true;  //Boolean.getBoolean("useDB");

    public Object synchObj = new Object();

    private static final int numberOfPanes = 4;

    JSplitPane splitPane0;
    JSplitPane splitPane1;
    JSplitPane splitPane2;

    JPanel[] ePanel;

    private MDSPanel mdsPane;
    private OSTPanel ostPane;
    private OSSPanel ossPane;
    private RTRPanel rtrPane;
    //private RTRScrollPanel rtrScrollPane;


    LWatch parent;
    protected String fsName;
    protected Database.FilesystemInfo fsInfo;

    private Database database = null;
    private boolean dbConnected = false;

    protected int [] panelOrder = new int[numberOfPanes];
    protected int nPanelsShown = numberOfPanes;
    protected boolean [] panelShow = new boolean[numberOfPanes];

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class defining the panels used to display individual file system tables
     * and controls.
     *
     * @param parent parent cotnainer class for this class.
     * @param fsInfoStruct file system info structure from Database.
     * @param db database containing data for this file system.
     * @param fsName name of this file system.
     */

    public FileSystemPanel(LWatch parent,
			   FilesystemInfo fsInfoStruct, Database db,
			   String fsName) {
	super(new BorderLayout(), false);

	this.parent = parent;
	this.fsInfo = fsInfoStruct;
	this.database = db;
	this.dbConnected = true;
	this.fsName = fsName;
	//this.fsName = fsInfoStruct.filesystemName;
	setName(fsName);

	if (localDebug)
	    Debug.out("File System Panel Name = " + this.fsName);


    }  // FileSystemPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Build the panels for this file system.
     *
     * @param selected denotes whether this file system is currently selected.
     */

    protected void makeFSPanel(boolean selected)
	throws Exception 
    {
	// Initialize some menu item hide/show variables
	for (int i = 0; i < numberOfPanes; i++) {
	    panelShow[i] = true;
	    panelOrder[i] = i;

	    if (i == 0 && !parent.prefs.showMDS) {
		if (localDebug) {
		    Debug.out("Show MDS Preference set to false.");
		}
		panelShow[i] = false;
	    } else if (i == 1 && !parent.prefs.showOST) {
		if (localDebug) {
		    Debug.out("Show OST Preference set to false.");
		}
		panelShow[i] = false;
	    } else if (i == 2 && !parent.prefs.showOSS) {
		if (localDebug) {
		    Debug.out("Show OSS Preference set to false.");
		}
		panelShow[i] = false;
	    } else if (i == 3 && !parent.prefs.showRTR) {
		if (localDebug) {
		    Debug.out("Show Router Preference set to false.");
		}
		panelShow[i] = false;
	    }
	}


	//Open a connection to the DB for this file system
	if (localDebug) {
	    try {
		//Database.FilesystemData = database.getCurrentFilesystemData();
		int [] gids = database.getRouterGroupIds();
		Debug.out("\nRouterGroupIds gotten # = " + gids.length + "\n");
	    } catch (Exception e) {
		Debug.out("Exception generated by getRouterGroupIds() " +
			  e.getMessage());
		e.printStackTrace();
	    }
	}

	//openDBConnection();  // Connected DB passed as arg in constructor.
	if (database == null) {
	    throw (new Exception("DB connect failure for file system " +
				 fsName));
	}

	//System.out.println(fsName + " : " + selected);
	JLabel filler = new JLabel(fsName);
	filler.setHorizontalAlignment(JLabel.CENTER);
	//GridBagLayout fsLayout = new GridBagLayout();  // ***
	//setLayout(fsLayout);  //***
	setBackground(Color.green);  //(new Color(253, 232, 209));

	// Define the Panels for WEST CENTER & EAST areas

	if (localDebug)
	    System.out.println("Build MDS Panel");

	mdsPane = new MDSPanel(this, selected);
	mdsPane.setBackground(new Color(253, 232, 209));  //Color.black);

	if (localDebug)
	    System.out.println("Build OST Panel");

	ostPane = new OSTPanel(this, selected);
	ostPane.setBackground(Color.black);

	if (localDebug)
	    System.out.println("Build OSS Panel");

	ossPane = new OSSPanel(this, selected);
	ossPane.setBackground(Color.black);

	if (localDebug)
	    System.out.println("Build ROUTER Panel");

	rtrPane = new RTRPanel(this, selected);
	rtrPane.setBackground(Color.black);

	ePanel = new JPanel[numberOfPanes];


	// ***
	// Configure the Layout for the panels.
	GridBagConstraints c;

	if (mdsPane != null) {
	    ePanel[0] = new JPanel(new BorderLayout(), false);
	    ePanel[0].setBackground(Color.black);
	    ePanel[0].add(mdsPane, BorderLayout.CENTER);
	} else if (localDebug) {
	    Debug.out("mdsPane is null.");
	}


	if (ostPane != null) {
	    ePanel[1] = new JPanel(new BorderLayout(), false);
	    ePanel[1].setBackground(Color.black);
	    ePanel[1].add(ostPane, BorderLayout.CENTER);
	} else if (localDebug) {
	    Debug.out("ostPane is null.");
	}


	if (ossPane != null) {
	    ePanel[2] = new JPanel(new BorderLayout(), false);
	    ePanel[2].setBackground(Color.black);
	    ePanel[2].add(ossPane, BorderLayout.CENTER);
	} else if (localDebug) {
	    Debug.out("ossPane is null.");
	}


	if (rtrPane != null) {
	    ePanel[3] = new JPanel(new BorderLayout(), false);
	    ePanel[3].setBackground(Color.black);
	    ePanel[3].add(rtrPane, BorderLayout.CENTER);
	} else if (localDebug) {
	    Debug.out("rtrPane is null.");
	}


	//System.out.println("Set up Split Pane 0");
	splitPane0 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	splitPane0.setDividerSize(4);
	splitPane0.setLeftComponent(ePanel[0]);
	splitPane0.setRightComponent(ePanel[1]);
	splitPane0.setDividerLocation(0.5);
	splitPane0.setResizeWeight(0.5);
	splitPane0.setContinuousLayout(true);

	//System.out.println("Set up Split Pane 1");
	splitPane1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	splitPane1.setDividerSize(4);
	splitPane1.setLeftComponent(splitPane0);
	splitPane1.setRightComponent(ePanel[2]);
	splitPane1.setDividerLocation(0.5);
	splitPane1.setResizeWeight(0.5);
	splitPane1.setContinuousLayout(true);

	add(splitPane1);  //, BorderLayout.CENTER);

	// When OSSPanel is working, uncomment follwoing block and
	// change set Left/Right Components in previous 2 blocks and
	// following block to place panels in correct order.

	//System.out.println("Set up Split Pane 2");
	splitPane2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	splitPane2.setDividerSize(4);
	splitPane2.setLeftComponent(splitPane1);
	splitPane2.setRightComponent(ePanel[3]);
	splitPane2.setDividerLocation(0.5);
	splitPane2.setResizeWeight(0.5);
	splitPane2.setContinuousLayout(true);

	add(splitPane2, BorderLayout.CENTER);


	//  NEW code controlling which panels are visible based on prefs
	int p = 0;
	nPanelsShown = 0;
	for (int i = 0; i < numberOfPanes; i++) {
	    if (panelShow[i]) {
		panelOrder[p++] = i;
		nPanelsShown++;
		if (localDebug) {
		    Debug.out("Panel[" + i + "] is  shown.");
		}
	    } else if (localDebug) {
		Debug.out("Panel[" + i + "] is NOT shown.");
	    }
	}
	if (localDebug) {
	    Debug.out("nPanelsShown = " + nPanelsShown);
	}
	for (int i = p; i < numberOfPanes; i++) {
	    //panelOrder[p++] = -1;
	    if (localDebug) {
		Debug.out("panelOrder[" + i + "] set to -1.");
	    }
	    panelOrder[i] = -1;
	}

	// End of NEW code

	//ePanel[0] = mdsPane;
	//ePanel[1] = ostPane;
	//ePanel[2] = rtrPane;
	//ePanel[3] = ossPane;


    }  // makeFSPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Hide one of the panels for the file system.
     *
     * @param i2Hide index of the panel to be hidden.
     */

    void hidePanel(int i2Hide) {
	int [] order = new int[numberOfPanes];

	//printPanelOrder("Old panel order");

	int p = 0;
	for (int i = 0; i < numberOfPanes; i++) {
	    if (panelOrder[i] != i2Hide)
		order[p++] = panelOrder[i];
	}
	for (int i = p; i < numberOfPanes; i++)
	    order[p++] = -1;
	for (int i = 0; i < numberOfPanes; i++)
	    panelOrder[i] = order[i];

	//printPanelOrder("New panel order");

	reconfigurePanes();

    }  // hidePanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Unhide one of the panels for the file system.
     *
     * @param i2Show index of the panel to be un-hidden.
     */

    void unhidePanel(int i2Show) {
	int [] order = new int[numberOfPanes];

	//printPanelOrder("Old panel order");

	int p = 0;
	for (int i = 0; i < numberOfPanes; i++) {
	    if (panelOrder[i] == -1) {
		order[p++] = i2Show;
		break;
	    } else
		order[p++] = panelOrder[i];
	}
	for (int i = p; i < numberOfPanes; i++)
	    order[p++] = -1;
	for (int i = 0; i < numberOfPanes; i++)
	    panelOrder[i] = order[i];

	//printPanelOrder("New panel order");

	reconfigurePanes();
    }  // unhidePanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reset the panels for the file system.
     */

    void resetPanes() {
	int [] order = new int[numberOfPanes];

	for (int i = 0; i < numberOfPanes; i++)
	    panelOrder[i] = i;

	nPanelsShown = numberOfPanes;
	reconfigurePanes();
    }  // resetPanes

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Print the panel order for the file system to STDOUT.
     */

    void printPanelOrder(String hdr) {
	System.out.print(hdr + " = ");
	for (int i = 0; i < numberOfPanes; i++) {
	    System.out.print(" " + panelOrder[i] + " ");
	}
	System.out.println("");
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reconfigure the panels for the file system based on order and number shown.
     */

    protected void reconfigurePanes() {

	if (localDebug) {
	    System.out.println("Reconfigure : ");

	    System.out.println("nPanelsShown = " + nPanelsShown);
	    System.out.print("New panel order = ");
	    for (int i = 0; i < numberOfPanes; i++) {
		System.out.print(" " + panelOrder[i] + " ");
	    }
	    System.out.println("");
	}
	

	if (nPanelsShown == 0) {
	    //System.out.println("pCount = " + nPanelsShown);

	    splitPane2.removeAll();

	    splitPane2.setDividerSize(4);
	    splitPane2.setLeftComponent(new JPanel());
	    splitPane2.setRightComponent(null);
	    splitPane2.setDividerLocation(0.5);
	    splitPane2.setResizeWeight(0.5);
	    splitPane2.setContinuousLayout(true);

	    splitPane2.updateUI();

	    
	} else if (nPanelsShown == 1) {
	    //System.out.println("pCount = " + nPanelsShown);

	    splitPane2.removeAll();

	    splitPane2.setDividerSize(4);
	    splitPane2.setLeftComponent(ePanel[panelOrder[0]]);
	    splitPane2.setRightComponent(null);
	    splitPane2.setDividerLocation(0.5);
	    splitPane2.setResizeWeight(0.5);
	    splitPane2.setContinuousLayout(true);

	    splitPane2.updateUI();


	    
	} else if (nPanelsShown == 2) {
	    //System.out.println("pCount = " + nPanelsShown);

	    splitPane2.removeAll();

	    splitPane2.setDividerSize(4);
	    splitPane2.setLeftComponent(ePanel[panelOrder[0]]);
	    splitPane2.setRightComponent(ePanel[panelOrder[1]]);
	    splitPane2.setDividerLocation(0.5);
	    splitPane2.setResizeWeight(0.5);
	    splitPane2.setContinuousLayout(true);

	    splitPane2.updateUI();

	    
	} else if (nPanelsShown == 3) {
	    //System.out.println("pCount = " + nPanelsShown[selectedTab]);

	    splitPane2.removeAll();

	    splitPane1.setDividerSize(4);
	    splitPane1.setLeftComponent(ePanel[panelOrder[0]]);
	    splitPane1.setRightComponent(ePanel[panelOrder[1]]);
	    splitPane1.setDividerLocation(0.5);
	    splitPane1.setResizeWeight(0.5);
	    splitPane1.setContinuousLayout(true);

	    splitPane2.setDividerSize(4);
	    splitPane2.setLeftComponent(splitPane1);
	    splitPane2.setRightComponent(ePanel[panelOrder[2]]);
	    splitPane2.setDividerLocation(0.5);
	    splitPane2.setResizeWeight(0.5);
	    splitPane2.setContinuousLayout(true);

	    splitPane1.updateUI();
	    splitPane2.updateUI();


	} else if (nPanelsShown == 4) {
	    //System.out.println("pCount = " + nPanelsShown[selectedTab]);

	    splitPane0.removeAll();
	    splitPane1.removeAll();
	    splitPane2.removeAll();

	    splitPane0.setDividerSize(4);
	    splitPane0.setLeftComponent(ePanel[panelOrder[0]]);
	    splitPane0.setRightComponent(ePanel[panelOrder[1]]);
	    splitPane0.setDividerLocation(0.5);
	    splitPane0.setResizeWeight(0.5);
	    splitPane0.setContinuousLayout(true);

	    splitPane1.setDividerSize(4);
	    splitPane1.setLeftComponent(splitPane0);
	    splitPane1.setRightComponent(ePanel[panelOrder[2]]);
	    splitPane1.setDividerLocation(0.5);
	    splitPane1.setResizeWeight(0.5);
	    splitPane1.setContinuousLayout(true);

	    splitPane2.setDividerSize(4);
	    splitPane2.setLeftComponent(splitPane1);
	    splitPane2.setRightComponent(ePanel[panelOrder[3]]);
	    splitPane2.setDividerLocation(0.5);
	    splitPane2.setResizeWeight(0.5);
	    splitPane2.setContinuousLayout(true);

	    splitPane0.updateUI();
	    splitPane1.updateUI();
	    splitPane2.updateUI();

	}

	splitPane0.setDividerSize(4);
	splitPane1.setDividerSize(4);
	splitPane2.setDividerSize(4);


	splitPane0.validate();
	splitPane1.validate();
	splitPane2.validate();

    }  // reconfigurePanes


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the parent container for this file system panel.
     *
     * @return the LWatch parent container class object.
     */

    public LWatch getParentOf() {

	return this.parent;

    }  // getParentOf

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the name associated with this file system panel.
     *
     * @return file system name string.
     */

    public String getFSName() {

	return this.fsName;

    }  // getFSName


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether the database is connected for this file system.
     *
     * @return boolean denoting whether database is connected or not.
     */

    protected boolean getDBConnected() {

	return this.dbConnected;

    }  // getDBConnected

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the database class object associated with this file system panel.
     *
     * @return the Database class object.
     */

    protected Database getDatabaseClass() {

	return this.database;

    }  // getDatabaseClass

    //public void close() {
	//
	// Do any cleanup and shutdown necessary to leave things in 
	// a easonable state.

	// Close down the DB

	/***
	if (useDB && dbConnected) {
	    try {
		database.close();
	    } catch (Exception e) {
		System.out.println("Exception detected during DB close : " +
				   e.getMessage());
	    }
	}
	***/

    //}


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Returns array of plottable variables for this panel type.
     *
     * @param type name of the panel type to get plottable variables for (MDS, OST, OSS, RTR).
     *
     * @return plottable variable string array for the specified panel.
     */

    public static String [] getPlottableVars(String type) {
	if ("OST".equals(type)) {
	    return OSTPanel.getPlottableVars();
	} else if ("OSS".equals(type)) {
	    return OSSPanel.getPlottableVars();
	} else if ("RTR".equals(type)) {
	    return RTRPanel.getPlottableVars();
	} else if ("MDS".equals(type)) {
	    return MDSPanel.getPlottableVars();
	} else {
	    return null;
	}

    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the master data array for this panel type.
     *
     * @param type name of the panel type to get plottable variables for (MDS, OST, OSS, RTR).
     *
     * @return master data array for the specified panel.
     */

    public Object [][] getMasterData(String type, int subId) {
	if ("OST".equals(type)) {
	    return ostPane.getMasterData();
	} else if ("OSS".equals(type)) {
	    return ossPane.getMasterData();
	} else if ("RTR".equals(type)) {
	    return rtrPane.getMasterData(subId);
	} else if ("MDS".equals(type)) {
	    return mdsPane.getMasterData(subId);
	} else {
	    return null;
	}

    }  // getMasterData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return the current column order for a specific table.
     *
     * @param numCols number of columns in the table.
     * @param dbColSeq column sequence for the table
     * @param table JTable being interrogated.
     * @param colNames Names of the columns from the table object.
     *
     * @return Array mapping current table columns to original column sequence.
     */

    int [] getCurrentColumnOrder (int numCols, String [] dbColSeq,
				  JTable table, String[] colNames) {

	// Calculate the ordering of the columns based on the original ordering.

	//Debug.out("numCols = " + numCols);
	String [] currentColOrder = new String[numCols];
	/*************************
	    if (dbColSeq != null)
		Debug.out("dbColSeq length = " + dbColSeq.length);
	    else
		Debug.out("dbColSeq is NULL");
	**************************/
	int [] columnMapping = new int[dbColSeq.length];
	for (int i = 0; i < numCols; i++) {
	    //System.out.println(i);
	    //System.out.println(i + " : Col Name = " + table.getColumnName(i));
	    currentColOrder[i] = table.getColumnName(i);
	    colNames[i] = table.getColumnName(i);
	}

	//Debug.out("ABC");
	for (int i = 0; i < dbColSeq.length; i++) {
	    //Debug.out("ABC " + i);
	    columnMapping[i] = -1;
	    for (int j = 0; j < numCols; j++) {
		//Debug.out("ABC " + i + ", " + j);
		if (dbColSeq[i].equals(currentColOrder[j])) {
		    columnMapping[i] = j;
		    break;
		}
	    }
	}

	return columnMapping;

    }  // getCurrentColumnOrder


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reload the individual panes that make up this file system panel.
     */

    public void reloadFSPanel() throws Exception 
    {
	if (localDebug)
	    Debug.out("Load file system panel for FS = " + this.fsName);

	//int fsidx = 0;
	//while ((!parent.fsPanes[fsidx].getName().equals(fsName)) &&
	       //(fsidx < parent.fsPanes.length)) {
	    //fsidx++;
	    //}

	    //if (localDebug)
	    //System.out.println("Panel index = " + fsidx);

	//parent.selectedTab = fsidx;
	parent.resetRefresh();

	if (localDebug)
	    Debug.out("Relaod MDS Panel...");

	mdsPane.reload();

	if (localDebug)
	    Debug.out("Relaod OST Panel...");

	ostPane.reload();

	if (localDebug)
	    Debug.out("Relaod OSS Panel...");

	ossPane.reload();

	if (localDebug)
	    Debug.out("Relaod ROUTER Panel...");

	rtrPane.reload();


	if (localDebug)
	    Debug.out("File system reload complete.");

    }  // reloadFSPanel

}  // FilesystemPanel
