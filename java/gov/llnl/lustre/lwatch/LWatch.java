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
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;
import java.lang.Runtime;
import java.lang.Integer;
import java.util.Timer;
import java.util.TimerTask;

import gov.llnl.lustre.lwatch.util.Debug;

import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.FilesystemInfo;

/**
 * Java GUI class for displaying statistics on the Lustre file system.
 * 
 */

public class LWatch extends JFrame
                    implements ActionListener {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("LWatch.debug");


    public final static boolean useDB = true;  //Boolean.getBoolean("useDB");


    static final String NAME = "LWatch";
    static final int EDIT_INDEX = 0;

    private static int numberOfFS;   // = 2;
    private String[] fsID;   // = {"ti1", "ti2",};
    private boolean[] fsMade;   // = new boolean[numberOfFS];
    private int nMdsOps = 14;
    private int nOsts = 120;
    private int nRouters = 25;

    private JTabbedPane tabbedPane = new JTabbedPane();
    //private JPanel tabHolderPane = new JPanel();

    protected FileSystemPanel [] fsPanes;

    protected int selectedTab = 0;

    JFrame watchMgrFrame = null;

    JMenuBar menuBar;
    JMenu fileMenu;
    JMenuItem prefsMenuItem;
    JMenuItem refreshMenuItem;
    JMenuItem quitMenuItem;

    JMenu configMenu;
    JMenuItem [] configMenuItem = new JMenuItem[5];  // Fix when OSS is supported

    Point lWatchLoc = null;
    Dimension lWatchSize = null;
    int refreshRate = 5000;  //5000;  // interval in msecs

    // Some new variables to control keeping connection alive. Added 11/16/04
    protected Timer refreshTimer = null;
    protected long refreshStopTime;
    protected long refreshMaxInactivity = 0;
    protected int refreshInterval = 0;
    protected long nextRefreshTime;

    private boolean ignoreEvent = true;

    Database [] allDBs;
    String [] dbNameMap;
    int nConnectedDBs = 0;
    Database.FilesystemInfo[] fsInfo;

    protected Prefs prefs;

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Construct frame to display file system statistics.
   *
   *          
   */

    public LWatch() {
	super();

	prefs = new Prefs();

	if (prefs.tableRefreshRate > 0)
	    refreshRate = prefs.tableRefreshRate;

	try {
	    allDBs = Database.getAllDatabases();
	} catch (Exception e) {
	    Debug.out("Fatal error encountered while connecting to DBs.\n" +
		      e.getMessage());
	    Thread.dumpStack();
	}

	fsInfo = new FilesystemInfo[allDBs.length];
	dbNameMap = new String[allDBs.length];
	for (int i = 0; i < allDBs.length; i++) {
	    if (allDBs[i].isConnected())
		nConnectedDBs++;

	    fsInfo[i] = allDBs[i].getFilesystemInfo();
	    dbNameMap[i] = fsInfo[i].filesystemName;

	    if (i == 0) continue;
	    int inc = 0;
	    for (int j = 0; j < i; j++) {
		if (fsInfo[i].filesystemName.equals(fsInfo[j].filesystemName))
		    inc ++;
	    }
	    if (inc > 0)
		dbNameMap[i] += "_" + inc;
	}
	/***
	try {
	    fsInfo = Database.getFilesystemInfo();
	} catch (Exception e) {
	    Debug.out("Unable to read file system info. Aborting.");
	    System.exit(0);
	}
	***/
	if (localDebug) {
	    Debug.out("# of entries in the fsInfo array = " + fsInfo.length);
	    for (int i = 0; i < fsInfo.length; i++) {
		System.out.println("\n" + i + " filesystemId = " +
				   fsInfo[i].filesystemId + "\n" +
				   "  filesystemName = " + fsInfo[i].filesystemName + "\n" +
				   "  lmtDbHost = " + fsInfo[i].lmtDbHost + "\n" +
				   "  filesystemMountName = " + fsInfo[i].filesystemMountName);
		if (!allDBs[i].isConnected())
		    System.out.println("Unable to establish DB connection.");
	    }
	}

	numberOfFS = fsInfo.length;
	if (fsInfo.length <= 0) {
	    System.out.println("Invalid or non-existent \".lmtrc\" file " + 
			       "detected. Aborting.");
	    System.exit(0);
	}

	fsID = new String[numberOfFS];
	fsMade = new boolean[numberOfFS];
	for (int i = 0; i < numberOfFS; i++) {
	    //fsID[i] = fsInfo[i].filesystemName;
	    fsID[i] = dbNameMap[i];
	    if (!allDBs[i].isConnected())
		fsID[i] += " NOT Connected";
	}

	// Initialize the file system panels to NOT yet "made".
	for (int i = 0; i < numberOfFS; i++)
	    fsMade[i] = false;

	// Load last window size and location
	//loadLWatchPrefs();
	//System.out.println("Starting tMgr Location = " + tMgrLoc +
			   //"  Size = " + tMgrSize);
	fsPanes = new FileSystemPanel[numberOfFS];

	watchMgrFrame = new JFrame("LWatch-lustre");
	watchMgrFrame.setBackground(Color.black);

	createLWatchContainer(selectedTab);
	tabbedPane.setSelectedIndex(selectedTab);

	// Handle window system close
	watchMgrFrame.addWindowListener(new WindowH());

	menuBarSetup();



	//watchMgrFrame.getContentPane().add(tabHolderPane);
	watchMgrFrame.getContentPane().add(tabbedPane);
        watchMgrFrame.pack();
	//watchMgrFrame.setSize(1150, 710);
        watchMgrFrame.setVisible(true);

	setRefresh(refreshRate, 3600000);

    }  // LWatch

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class for handling window events.
     */

    class WindowH extends WindowAdapter {

	/**
	 * Window opened event handler.
	 *
	 * @param WindowEvent object causing handler activation.
	 */

	public void windowOpened(WindowEvent e) {
	    if (localDebug)
		Debug.out("[windowOpened]");
	}  //  windowOpened

	/**
	 * Window activated event handler.
	 *
	 * @param WindowEvent object causing handler activation.
	 */

	public void windowActivated(WindowEvent e) {
	    //if (localDebug)
		//Debug.out("[windowActivated]");
	    //if (firstTime) {
		//firstTime = false;
		//mainPane.initializeFocus();
		//getRootPane().setDefaultButton(actionButtons.getButton("OK"));
		//}
	}  //  windowActivated

	/**
	 * Window deactivated event handler.
	 *
	 * @param WindowEvent object causing handler activation.
	 */

	public void windowDeactivated(WindowEvent e) {
	    //if (localDebug)
		//Debug.out("[windowDeactivated]");
	    //if (firstTime) {
		//firstTime = false;
		//mainPane.initializeFocus();
		//getRootPane().setDefaultButton(actionButtons.getButton("OK"));
		//}
	}  // windowDeactivated

	/**
	 * Window closing event handler.
	 *
	 * @param WindowEvent object causing handler activation.
	 */

	public void windowClosing(WindowEvent e) {
	    if (localDebug)
		Debug.out("[windowClosing]");
	    prefs.prefsWrite();
	    quit();
	}  // windowClosing

	/**
	 * Window closed event handler.
	 *
	 * @param WindowEvent object causing handler activation.
	 */

	public void windowClosed(WindowEvent e) {
	    if (localDebug)
		Debug.out("[windowClosed]");
	    quit();
	}  // windowClosed
    }  // WindowH


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class for handling Tab events.
     */

    class LwatchTabH extends ComponentAdapter {

	/**
	 * Component hidden event handler.
	 *
	 * @param ComponentEvent object causing handler activation.
	 */

	public void componentHidden(ComponentEvent event) {
	}  // componentHidden

	/**
	 * Component moved event handler.
	 *
	 * @param ComponentEvent object causing handler activation.
	 */

	public void componentMoved(ComponentEvent event) {
	}  // componentMoved

	/**
	 * Component resized event handler.
	 *
	 * @param ComponentEvent object causing handler activation.
	 */

	public void componentResized(ComponentEvent event) {
	}  // componentResized

	/**
	 * Component shown event handler.
	 *
	 * @param ComponentEvent object causing handler activation.
	 */

	public void componentShown(ComponentEvent event) {
	    if (localDebug) {
		Debug.out("Tab event = " + event.paramString());
		System.out.println("componentShown event detected : " +
				   event.getComponent().getName());
	    }
	    if (ignoreEvent) {
		//System.out.println("Ignore event the first time it occurs.");
		ignoreEvent = false;
		return;
	    }
	    try {
		int idx = 0;
		String tabName = event.getComponent().getName();
		//while ((idx < fsPanes.length) &&
		       //(!fsPanes[idx].getName().equals(tabName)))
		while ((idx < fsPanes.length) &&
		       (!dbNameMap[idx].equals(tabName)))
		    idx++;

		if (localDebug)
		    Debug.out("Tab " + idx + " was selected.  fsMade = " + fsMade[idx]);
		if (idx < fsPanes.length) {
		    //Debug.out("Reload fsPane for " + event.getComponent().getName());
		    selectedTab = idx;
		    if ( ! fsMade[idx] ) {
			fsPanes[idx].makeFSPanel(true);
			fsMade[idx] = true;
			fsPanes[idx].validate();  // Need this to make it show now.

			// If prefs reported any panels turned off do it now.
			fsPanes[idx].reconfigurePanes();
		    } else {
			fsPanes[idx].reloadFSPanel();
		    }
		    updateHideShowMenuItems();
		} else
		    throw new Exception("FileSystemPanel / Tab name mismatch.");
	    } catch (Exception e) {
		System.out.println("Error loading file system panel. Aborting.\n" +
				   e.getMessage());
		System.exit(1);
	    }
	}  // componentShown

    }  //  LwatchTabH


    /**
     * Update the file system hide/show menu items based on panelShow setting.
     */

    void updateHideShowMenuItems() {

	if (fsPanes[selectedTab].panelShow[0])
	    configMenuItem[0].setText("Hide MDS");
	else
	    configMenuItem[0].setText("Show MDS");

	if (fsPanes[selectedTab].panelShow[1])
	    configMenuItem[1].setText("Hide OST");
	else
	    configMenuItem[1].setText("Show OST");

	if (fsPanes[selectedTab].panelShow[2])
	    configMenuItem[2].setText("Hide OSS");
	else
	    configMenuItem[2].setText("Show OSS");

	if (fsPanes[selectedTab].panelShow[3])
	    configMenuItem[3].setText("Hide RTR");
	else
	    configMenuItem[3].setText("Show RTR");

    }  // updateHideShowMenuItems

    /**
     * Menu bar setup method.
     */

    void menuBarSetup() {

	menuBar = new JMenuBar();
	watchMgrFrame.setJMenuBar(menuBar);

	fileMenu = new JMenu("File");
	menuBar.add(fileMenu);

	prefsMenuItem = new JMenuItem("Preferences");
	prefsMenuItem.addActionListener(this);
	fileMenu.add(prefsMenuItem);

	refreshMenuItem = new JMenuItem("Refresh Off");
	refreshMenuItem.addActionListener(this);
	fileMenu.add(refreshMenuItem);

	quitMenuItem = new JMenuItem("Quit");
	quitMenuItem.addActionListener(this);
	fileMenu.add(quitMenuItem);

	configMenu = new JMenu("Configure");
	menuBar.add(configMenu);

	if (fsPanes[selectedTab].panelShow[0])
	    configMenuItem[0] = new JMenuItem("Hide MDS");
	else
	    configMenuItem[0] = new JMenuItem("Show MDS");
	configMenuItem[0].addActionListener(this);
	configMenu.add(configMenuItem[0]);

	if (fsPanes[selectedTab].panelShow[1])
	    configMenuItem[1] = new JMenuItem("Hide OST");
	else
	    configMenuItem[1] = new JMenuItem("Show OST");
	configMenuItem[1].addActionListener(this);
	configMenu.add(configMenuItem[1]);

	if (fsPanes[selectedTab].panelShow[2])
	    configMenuItem[2] = new JMenuItem("Hide OSS");
	else
	    configMenuItem[2] = new JMenuItem("Show OSS");
	configMenuItem[2].addActionListener(this);
	configMenu.add(configMenuItem[2]);

	if (fsPanes[selectedTab].panelShow[3])
	    configMenuItem[3] = new JMenuItem("Hide RTR");
	else
	    configMenuItem[3] = new JMenuItem("Show RTR");
	configMenuItem[3].addActionListener(this);
	configMenu.add(configMenuItem[3]);

	configMenuItem[4] = new JMenuItem("Reset");
	configMenuItem[4].addActionListener(this);
	configMenu.add(configMenuItem[4]);

    }  // menuBarSetup

    /**
     * Action performed event handler.
     *
     * @param ActionEvent menu event firing handler call.
     */

    public void actionPerformed(ActionEvent e) {
	JMenuItem source = (JMenuItem)(e.getSource());
	//System.out.println("Menu source = " + source.getText());

	if ("Quit".equals(source.getText())) {
	    prefs.prefsWrite();
	    System.exit(0);
	} else if ("Preferences".equals(source.getText())) {
	    PrefsModifier prefsMod = new PrefsModifier(LWatch.this, prefs);
	    prefsMod.displayDialog();

	    refreshRate = prefs.tableRefreshRate;
	    setRefresh(refreshRate, 3600000);
	} else if ("Refresh Off".equals(source.getText())) {
	    stopRefresh();
	    refreshMenuItem.setText("Refresh On");
	} else if ("Refresh On".equals(source.getText())) {
	    startRefresh();
	    refreshMenuItem.setText("Refresh Off");
	} else if (source.getText().startsWith("Hide")) {
	    String item = "";
	    int index = 0;
	    if (source.getText().endsWith("MDS")) {
		fsPanes[selectedTab].panelShow[0] = false;
		item = "MDS";
		index = 0;
	    } else if (source.getText().endsWith("OST")) {
		fsPanes[selectedTab].panelShow[1] = false;
		item = "OST";
		index = 1;
	    } else if (source.getText().endsWith("OSS")) {
		fsPanes[selectedTab].panelShow[2] = false;
		item = "OSS";
		index = 2;
	    } else if (source.getText().endsWith("RTR")) {
		fsPanes[selectedTab].panelShow[3] = false;
		item = "RTR";
		index = 3;
	    }


	    fsPanes[selectedTab].nPanelsShown--;
	    //System.out.println("\nChange menuItem " + index + " to \"Show " + item + "\"");
	    configMenuItem[index].setText("Show " + item);
	    fsPanes[selectedTab].hidePanel(index);
	} else if (source.getText().startsWith("Show")) {
	    String item = "";
	    int index = 0;
	    if (source.getText().endsWith("MDS")) {
		fsPanes[selectedTab].panelShow[0] = true;
		item = "MDS";
		index = 0;
	    } else if (source.getText().endsWith("OST")) {
		fsPanes[selectedTab].panelShow[1] = true;
		item = "OST";
		index = 1;
	    } else if (source.getText().endsWith("OSS")) {
		fsPanes[selectedTab].panelShow[2] = true;
		item = "OSS";
		index = 2;
	    } else if (source.getText().endsWith("RTR")) {
		fsPanes[selectedTab].panelShow[3] = true;
		item = "RTR";
		index = 3;
	    }

	    fsPanes[selectedTab].nPanelsShown++;
	    //System.out.println("\nChange menuItem " + index + " to \"Hide " + item + "\"");
	    configMenuItem[index].setText("Hide " + item);
	    fsPanes[selectedTab].unhidePanel(index);
	} else if (source.getText().startsWith("Reset")) {
	    String [] fsPaneItems = {"MDS", "OST", "OSS", "RTR"};
	    fsPanes[selectedTab].resetPanes();

	    // Reset Config menu item labels. Add OSS logic when supported.
	    for (int i = 0; i < 4; i++) {
		fsPanes[selectedTab].panelShow[i] = true;
		configMenuItem[i].setText("Hide " + fsPaneItems[i]);
	    }
	}
    }  // actionPerformed


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Refresh file system panel
     */

    public void refreshFSPanel() {
	try {
	    if (localDebug)
		Debug.out("selectedTab = " + selectedTab +
			  "  name = " + fsPanes[selectedTab].getName());

	    synchronized (fsPanes[selectedTab].synchObj) {
		fsPanes[selectedTab].reloadFSPanel();
	    }

	} catch (Exception e) {
	    Debug.out("Exception caught during refresh of " +
		      fsPanes[selectedTab].getName() + "\n" + e.getMessage());
	}
    }  // refreshFSPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Create the LWatch container.
     *
     * @param setTab the selected file system tab.
     */

    public void createLWatchContainer(int selTab) {

	//System.out.println("# of fsIDs = " + fsID.length + " : numberOfFS = " + numberOfFS);
	boolean selected = false;
	if ((selTab >= numberOfFS) || (selTab < 0))
	    selTab = 0;
	for (int i = 0; i < numberOfFS; i++) {
	    if (selTab == i)
		selected = true;
	    else
		selected = false;
	    try {
		fsPanes[i] = new FileSystemPanel(this, fsInfo[i], allDBs[i], dbNameMap[i]);

		if (selected) {
		    fsPanes[i].makeFSPanel(selected);
		    fsMade[i] = true;

		    // If prefs reported any panels turned off do it now.
		    fsPanes[i].reconfigurePanes();
		}
	    } catch (Exception ex) {
		System.err.println("Exception caught : " + ex.getMessage());
		ex.printStackTrace();
		System.exit(1);
	    }
	    fsPanes[i].addComponentListener(new LwatchTabH());
	    tabbedPane.addTab(fsID[i], fsPanes[i]);
	    if (!allDBs[i].isConnected()) {
		//tabbedPane.setForegroundAt(i, Color.red);
		tabbedPane.setEnabledAt(i, false);
	    }
	}

    }  // createLWatchContainer


    //////////////////////////////////////////////////////////////////////////////

    //===============================================================================
    //  Refresh scheduling
    //===============================================================================

    /**
     * Start the auto-refresh for the file system statistics tables.
     */

    synchronized protected void startRefresh() {
	//Debug.out(" " + this + "  keepAliveMaxInactivity = " + keepAliveMaxInactivity);
	if (refreshMaxInactivity > 0) {
	    //Debug.out("Actually do it : " + this);
	    resetRefresh();
	    refreshTimer = new Timer();
	    refreshTimer.scheduleAtFixedRate(new RefreshTask(), 0, 2*1000);
	}
    }  // startRefresh

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Stop the auto-refresh for the file system statistics tables.
     */

    protected void stopRefresh() {
	//Debug.out(" " + this);
	if (refreshTimer != null) {
	    refreshTimer.cancel();
	    refreshTimer = null;
	}
    }  // stopRefresh

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Set the auto-refresh parameters for the file system statistics tables.
     *
     * @param interval the interval in msecs between refresh events.
     * @param maxTime stop time for refresh.
     */

    synchronized public void setRefresh(int interval, int maxTime) {
	if (localDebug)
	    Debug.out("Setting RefreshInterval to : " + interval + "   " + this);
	//Debug.out("Setting RefreshInterval to : " + interval + "   " + this);
	refreshInterval = interval;
	refreshMaxInactivity = maxTime;
	stopRefresh();
	startRefresh();
    }  // setRefresh


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class for executing refresh thread.
     */

    class RefreshTask extends TimerTask {

	/**
	 * run method for RefreshTask class.
	 */

	public void run() {
	    long currentTime = System.currentTimeMillis();
	    if (currentTime > nextRefreshTime) {
		//System.out.println("\nREFRESH\n");
		//refreshFSPanel();

		Runnable refresh = new Runnable() {
			public void run() {
			    LWatch.this.refreshFSPanel();
			}
		    };

		SwingUtilities.invokeLater(refresh);

		    nextRefreshTime =
			currentTime + refreshInterval;
		}
	}  // run

    }  // RefreshTask


    /**
     * Reset the auto-refresh for the file system statistics tables.
     */

    synchronized void resetRefresh() {
	//Debug.out(" " + this);
	long currentTime = System.currentTimeMillis();
	nextRefreshTime = currentTime+refreshInterval;
	refreshStopTime = currentTime+refreshMaxInactivity;
    }  // resetRefresh


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Cleanup and quit LWatch.
     */


    void quit() {
	if (localDebug)
	    Debug.out("[LWatch quit]");

	// Close any DB connections that may be open
	if (useDB) {
	    for (int i = 0; i < numberOfFS; i++) {
		if (allDBs[i].isConnected()) {
		    try {
			allDBs[i].close();
		    } catch (Exception e) {
			Debug.out("Exception detected during DB close : " +
					   e.getMessage());
		    }
		}
	    }
	}

	watchMgrFrame.dispose();

	System.exit(0);

    }  // quit

    //////////////////////////////////////////////////////////////////////////////

    /**
     * Main method for LWatch class.
     */

    public static void main(String argv[]) {
	if (localDebug)
	    Debug.out("Starting...");

	LWatch jw = new LWatch();

    }  // main

}  // LWatch


