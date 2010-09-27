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
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Date;


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



//////////////////////////////////////////////////////////////////////////////

/**
 * Class used to define and build RTR (Router) JTable statistics panel for file system container.
 */

public class RTRPanel extends JPanel {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("RTRPanel.debug");

    public final static boolean test = 
	Boolean.getBoolean("RTRPanel.test");

    public final static boolean useDB = true;   //Boolean.getBoolean("useDB");

    FileSystemPanel parentFS;
    String fsName;
    boolean selected;
    int numOfRTRGroups;

    Database database = null;
    int [] rtrGroupIds;

    protected String [] rtrColumnOrder = {"RATE", "PCT_CPU"};

    protected int [] varIds;
    protected String [] rtrMasterColNames;
    protected static String [] rtrPlottableVars;

    //protected String  [] rtrMasterColNames = {"Name", "BW", "%CPU"};
    //protected static String  [] rtrPlottableVars = {"BW", "%CPU"};

    protected VariableInfo [] vi;
    RTRTablePanel [] tablePanes;

    JPanel rgPanel;
    GridBagLayout gbLayout;  // Layout declaration for RTRPanel
    GridBagLayout rgLayout = new GridBagLayout();

    // private Timestamp rtrTimestamp;
    //private JLabel rtrTimestampLabel;

    // Variable declarations for router group visibility mechanism
    int [] rtrGrpOrder;
    int  nRGShown;
    boolean [] rtrGrpShow;

    JMenuItem [] rtrViewMenuItem;  // = new JMenuItem[numOfRTRGroups + 2];

    JPanel rtrButtonPanel;
    JButton rtrViewButton;
    JPopupMenu rtrViewPopup;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for the MDSPanel.
     *
     * @param parentFS Parent FileSystemPanel object containing this RTR panel.
     *
     * @param selected true if the parent FileSystemPanel is selected.
     */

    RTRPanel(FileSystemPanel parentFS, boolean selected) {
	super();

	this.parentFS = parentFS;
	this.fsName = parentFS.getFSName();
	this.selected = selected;

	database = parentFS.getDatabaseClass();


	/*
	// Print out the variable info for the router table
	try {
	    Database.VariableInfo[] ovi = database.getVariableInfo("ROUTER_VARIABLE_INFO");


	    for (int i = 0; i < ovi.length; i++) {
		System.out.println(ovi[i].variableId);
		System.out.println(ovi[i].variableName);
		System.out.println(ovi[i].variableLabel);
		System.out.println(ovi[i].threshType);
		System.out.println(ovi[i].threshVal1);
		System.out.println(ovi[i].threshVal2);
	    }


	} catch (Exception e) {
	    Debug.out("Exception caught while loading VariableInfo from ROUTER_VARIABLE_INFO" +
		      e.getMessage());
	}
	*/


	rtrMasterColNames = new String[rtrColumnOrder.length + 1];
	rtrMasterColNames[0] = new String("Name");

	loadVariableInfoArray();

	// Get the Router Group Ids
	try {
	    //Debug.out("Get rtrGroupIds.");
	    rtrGroupIds = database.getRouterGroupIds();
	} catch (Exception e) {
	    Debug.out("Exception detected while loading current RTR group Ids.\n" +
		      e.getMessage());

	    return;
	}
	numOfRTRGroups = rtrGroupIds.length;
	//Debug.out("rtrGroupIds gotten. length = " + numOfRTRGroups);

	// Dimensioning for visibility variables
	nRGShown = numOfRTRGroups;
	rtrGrpOrder = new int[numOfRTRGroups];
	rtrGrpShow = new boolean[numOfRTRGroups];
	rtrViewMenuItem = new JMenuItem[numOfRTRGroups+2];
	for (int j = 0; j < numOfRTRGroups; j++) {
	    rtrGrpShow[j] = true;
	    rtrGrpOrder[j] = j;
	}

	gbLayout = new GridBagLayout();
	setLayout(gbLayout);
	GridBagConstraints c;

	// View popup button setup
	rtrButtonPanel = new JPanel(new GridLayout(1, 1), false);

	rtrViewButton = new JButton("View");
	rtrViewButton.setSize(new Dimension(50, 20));
	rtrViewButton.addMouseListener(new RTRViewMouseListener());
	rtrButtonPanel.add(rtrViewButton);

	rtrViewPopup = new JPopupMenu();

	rtrViewMenuItem[0] = new JMenuItem("Hide All");
	rtrViewMenuItem[1] = new JMenuItem("Show All");
	rtrViewMenuItem[0].addActionListener(new RTRViewPopupListener());
	rtrViewMenuItem[1].addActionListener(new RTRViewPopupListener());
	rtrViewPopup.add(rtrViewMenuItem[0]);
	rtrViewPopup.add(rtrViewMenuItem[1]);

	for (int k = 0; k < numOfRTRGroups; k++) {
	    rtrViewMenuItem[k+2] = new JMenuItem();
	    if (rtrGrpShow[k])
		rtrViewMenuItem[k+2].setText("Hide Router Group " + k);
	    else
		rtrViewMenuItem[k+2].setText("Show Router Group " + k);

	    rtrViewMenuItem[k+2].addActionListener(new RTRViewPopupListener());
	    rtrViewPopup.add(rtrViewMenuItem[k+2]);
	}

	c = new GridBagConstraints();
	c.gridx = 0;
	c.gridy = 0;
	c.insets = new Insets(2, 0, 0, 2);
	c.anchor = GridBagConstraints.NORTHWEST;
	c.fill = GridBagConstraints.NONE;
	c.weightx = 0.0;  //1.0;
	c.weighty = 0.0;
	gbLayout.setConstraints(rtrButtonPanel, c);

	//rtrButtonPanel.setBackground(Color.green);
	rtrButtonPanel.setMaximumSize(new Dimension(1200, 20));
	rtrButtonPanel.setMaximumSize(new Dimension(50, 20));
	add(rtrButtonPanel);
	
	// Define and place the table panes for each router group, in order.
	rgPanel = new JPanel();
	rgPanel.setLayout(rgLayout);
	rgPanel.setBackground(Color.black);
	GridBagConstraints rgc;

	
	tablePanes = new RTRTablePanel[numOfRTRGroups];
	for (int i = 0; i < numOfRTRGroups; i++) {
	    tablePanes[i] = new RTRTablePanel(i);

	    rgc = new GridBagConstraints();
	    rgc.gridx = 0;
	    rgc.gridy = i;
	    rgc.insets = new Insets(2, 0, 0, 2);
	    rgc.anchor = GridBagConstraints.WEST;
	    rgc.fill = GridBagConstraints.BOTH;
	    rgc.weightx = 1.0;
	    rgc.weighty = 1.0;
	    rgLayout.setConstraints(tablePanes[i], rgc);

	    //Debug.out("Add tablePanes[" + i + "] to rtrPane.");
	    //mdsPane.add(tablePanes[i]);
	    rgPanel.add(tablePanes[i]);
	}

	c = new GridBagConstraints();
	c.gridx = 0;
	c.gridy = 1;
	c.insets = new Insets(2, 0, 0, 2);
	c.anchor = GridBagConstraints.WEST;
	c.fill = GridBagConstraints.BOTH;
	c.weightx = 1.0;
	c.weighty = 1.0;  //1.0;
	gbLayout.setConstraints(rgPanel, c);

	JScrollPane rgScrollPane = new JScrollPane(rgPanel);
	rgScrollPane.getViewport().setBackground(Color.black);
	rgScrollPane.setAlignmentX(RIGHT_ALIGNMENT);

	add(rgScrollPane);
	gbLayout.setConstraints(rgScrollPane, c);

	setPreferredSize(new Dimension(240, 700));
	setMinimumSize(new Dimension(100, 300));  //(240, 700));
	//setMaximumSize(new Dimension(300, 700));

	setAlignmentX(LEFT_ALIGNMENT);

    }  // RTRPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Loads variable info for specific RTR variables and initializes column names
     * to be displayed in the column headers.
     */

    public void loadVariableInfoArray() {
	vi = new Database.VariableInfo[rtrColumnOrder.length];
	rtrPlottableVars = new String[rtrColumnOrder.length];
	varIds = new int[rtrColumnOrder.length];
	try {
	    //vi = new Database.VariableInfo[] {
		//database.getVariableInfo("ROUTER_VARIABLE_INFO", "BANDWIDTH"),
		//database.getVariableInfo("ROUTER_VARIABLE_INFO", "PCT_CPU")
	    //};

	    for (int i = 0; i < rtrColumnOrder.length; i++)
		vi[i] = database.getVariableInfo("ROUTER_VARIABLE_INFO", rtrColumnOrder[i]);

	    if (localDebug) {
		for (int i = 0; i < vi.length; i++) {
		    System.out.println("var ID = " + vi[i].variableId +
				       "\nvarName = " + vi[i].variableName + 
				       "\nVarLab = " + vi[i].variableLabel +
				       "\nthreshType = " + vi[i].threshType +
				       "\nthresh val1 = " + vi[i].threshVal1 +
				       "\nthresh val2 = " + vi[i].threshVal2);
		}
	    }
	} catch (Exception e) {
	    Debug.out("VariableInfo load exception : " + e.getMessage());
	}

	for (int i = 0; i < rtrColumnOrder.length; i++) {
	    for (int j = 0; j < vi.length; j++) {
		if (rtrColumnOrder[i].equals(vi[j].variableName)) {
		    varIds[i] = j;  //vi[j].variableId;
		    rtrMasterColNames[i+1] = vi[j].variableLabel;
		    rtrPlottableVars[i] = vi[j].variableLabel;
		}
	    }
	}

    }  // loadVariableInfoArray


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return array of plottable variables associated with the RTR panel.
     */

    public static String [] getPlottableVars() {

	return rtrPlottableVars;

    }  // getPlottableVars


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return master data array associated with the RTR panel.
     */

    public  Object [][] getMasterData(int groupNum) {

	for (int i = 0; i < tablePanes.length; i++) {
	    if (tablePanes[i].rtrGrp == groupNum)
		return tablePanes[i].routerMasterData;
	}
	Debug.out("Unable to find matching router group for id = " + groupNum);
	return null;

    }  // getMasterData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reload the RTR Panel.
     */

    void reload() {

	if (localDebug)
	    Debug.out("Reloading Router Panel.");

	for (int i = 0; i < tablePanes.length; i++)
	    tablePanes[i].reloadRTRTablePanel(i);

    }  // reload


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class defining listener controlling display of menu used to control the showing & hiding of
     * individual router groups.
     */

    public class RTRViewMouseListener extends MouseAdapter {

	/**
	 * Constructor for mouse listener.
	 */

	public RTRViewMouseListener() {

	    super();

	}  // RTRViewMouseListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Causes menu to be displayed.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	public void mouseReleased(MouseEvent e) {

	    //System.out.println("Release of button " + e.getButton());
	    showPopup(e);

	}  // mouseReleased


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Causes menu to be displayed.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	public void mouseClicked(MouseEvent e) {

	    //System.out.println("Click of button " + e.getButton());
	    showPopup(e);

	}  // mouseClicked


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Show the pop-up menu.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	private void showPopup(MouseEvent e) {

	    rtrViewPopup.show(e.getComponent(), e.getX(), e.getY());

	}  // showPopup
		
    }  // RTRViewMouseListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class defining listener for pop-up menu used to control the showing & hiding of
     * individual router groups.
     */

    public class RTRViewPopupListener implements ActionListener {

	//////////////////////////////////////////////////////////////////////////////


	/**
	 * Constructor for pop-up menu to control display of router groups.
	 */

	public RTRViewPopupListener() {

	    super();

	}  // RTRViewPopupListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 *  The action handler for the router group pop-up menu.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	public void actionPerformed(ActionEvent e) {
	    boolean hideIt;

	    //System.out.println("ActionEvent detected in Router View popup \n" +
			       //e.getActionCommand());

	    String rg = e.getActionCommand();

	    if ("Hide All".equals(rg)) {
		//System.out.println("Hide All Selected...");
		hideAllRTRGroups();
	    } else if ("Show All".equals(rg)) {
		//System.out.println("Show All Selected...");
		showAllRTRGroups();
	    } else {
		if (rg.startsWith("Hide "))
		    hideIt = true;
		else
		    hideIt = false;


		rg = rg.replaceFirst("^[HideShow]* Router Group ", "");
		int rgNum = Integer.parseInt(rg);

		//System.out.println("Decode Router group index = " + rgNum);
		//System.out.println("hideIt = " + hideIt);

		if (hideIt) {
		    rtrGrpShow[rgNum] = false;
		    hideRTRGrpPanel(rgNum);
		    nRGShown--;
		    //System.out.println("set rtrViewMenuItem[" + rgNum + "] = Show");
		    String rvmit = rtrViewMenuItem[rgNum+2].getText();
		    //System.out.println("Menu Item Text WAS " + rvmit);
		    rtrViewMenuItem[rgNum+2].setText("Show Router Group " + rgNum);
		    rvmit = rtrViewMenuItem[rgNum+2].getText();
		    //System.out.println("Menu Item Text IS " + rvmit);
		} else {
		    rtrGrpShow[rgNum] = true;
		    unhideRTRGrpPanel(rgNum);

		    nRGShown++;
		    //System.out.println("set rtrViewMenuItem[" + rgNum + "] = Hide");
		    rtrViewMenuItem[rgNum+2].setText("Hide Router Group " + rgNum);
		}

	    }

	}  // actionPerformed

    }  // RTRViewPopupListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Hide all router group table panels.
     */

    void hideAllRTRGroups() {

	//System.out.print("\n");
	//printRTRPanelOrder("Old panel order");

	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (rtrGrpOrder[i] >= 0) {
		//System.out.println("Remove " + rtrGrpOrder[i] + " from pane # " + i);
		rgPanel.remove(tablePanes[rtrGrpOrder[i]]);
		nRGShown--;
	    }
	}
	//System.out.println("# of router groups shown = " + nRGShown);

	for (int i = 0; i < numOfRTRGroups; i++) {
	    rtrGrpOrder[i] = -1;
	    rtrGrpShow[i] = false;
	    rtrViewMenuItem[i+2].setText("Show Router Group " + i);
	    //System.out.println("Change menuItem Text to Show Router Group " + i);
	}
	nRGShown = 0;

	//printRTRPanelOrder("New panel order");

	this.validate();
	this.repaint();

    }  // hideAllRTRGroups


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Show all router group table panels.
     */

    void showAllRTRGroups() {

	int [] order = new int[numOfRTRGroups];
	GridBagConstraints c;

	//System.out.print("\n");
	//printRTRPanelOrder("Old panel order");

	int p = 0;
	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (rtrGrpOrder[i] != -1)
		order[p++] = rtrGrpOrder[i];
	}
	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (!rtrGrpShow[i]) {
		order[p++] = i;
		int gy = (nRGShown++);  // + 1;
		//System.out.println("Add back router group " + i + " to pane # " + gy);
		rgPanel.add(tablePanes[i]);

		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = gy;
		c.insets = new Insets(8, 0, 0, 5);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;  //1.0;
		c.weighty = 1.0;
		rgLayout.setConstraints(tablePanes[i], c);
	    }
	}
	//System.out.println("# of router groups shown = " + nRGShown);

	for (int i = 0; i < numOfRTRGroups; i++) {
	    rtrGrpOrder[i] = order[i];
	    rtrGrpShow[i] = true;
	    rtrViewMenuItem[i+2].setText("Hide Router Group " + i);
	    //System.out.println("Change menuItem Text to Hide Router Group " + i);
	}

	//printRTRPanelOrder("New panel order");

	this.validate();
	this.repaint();

    }  // showAllRTRGroups


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Hide specified router group table panel.
     *
     * @param index of the router group to hide.
     */

    void hideRTRGrpPanel(int i2Hide) {

	int [] order = new int[numOfRTRGroups];

	//System.out.println("Hide RTRPanel # " + i2Hide);
	//printRTRPanelOrder("Old panel order");

	int p = 0;
	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (rtrGrpOrder[i] != i2Hide)
		order[p++] = rtrGrpOrder[i];
	}
	for (int i = p; i < numOfRTRGroups; i++)
	    order[p++] = -1;
	for (int i = 0; i < numOfRTRGroups; i++)
	    rtrGrpOrder[i] = order[i];

	//printRTRPanelOrder("New panel order");

	reconfigureRTRGrpPanes(-(i2Hide+1));

    }  // hideRTRGrpPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Show specified router group table panel.
     *
     * @param index of the router group to show.
     */

    void unhideRTRGrpPanel(int i2Show) {

	int [] order = new int[numOfRTRGroups];

	//System.out.println("Hide RTRPanel # " + i2Show);
	//printRTRPanelOrder("Old panel order");

	int p = 0;
	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (rtrGrpOrder[i] == -1) {
		order[p++] = i2Show;
		break;
	    } else
		order[p++] = rtrGrpOrder[i];
	}
	for (int i = p; i < numOfRTRGroups; i++)
	    order[p++] = -1;
	for (int i = 0; i < numOfRTRGroups; i++)
	    rtrGrpOrder[i] = order[i];

	//printRTRPanelOrder("New panel order");

	reconfigureRTRGrpPanes(i2Show);
    }  // unhideRTRGrpPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Print router group panel order to STDOUT.
     *
     * @param hdr string to prefix panel order with.
     */

    void printRTRPanelOrder(String hdr) {

	System.out.print(hdr + " = ");
	for (int i = 0; i < numOfRTRGroups; i++) {
	    System.out.print(" " + rtrGrpOrder[i] + " ");
	}
	System.out.println("");

    }  // printRTRPanelOrder


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reconfigure router group panels.
     *
     * @param hsIdx index of specific router group to hise or show. < 0 implies hide.
     */

    protected void reconfigureRTRGrpPanes(int hsIdx) {

	GridBagConstraints c;

	//System.out.println("\nReconfigure Router Group Panes...");
	//System.out.println("nRGShown = " + nRGShown + "   hsIdx = " + hsIdx);

	if (nRGShown > 0) {
	    for (int i = 0; i < numOfRTRGroups; i++) {
		//System.out.print(" " + rtrGrpOrder[i] + " ");
		if ((rtrGrpOrder[i] > 0) && (rtrGrpOrder[i] != hsIdx)) {
		    //System.out.print(" : Remove " + rtrGrpOrder[i] + "  : ");
		    rgPanel.remove(tablePanes[rtrGrpOrder[i]]);
		}
	    }
	    if (hsIdx < 0) {
		hsIdx = (hsIdx * (-1)) - 1;
		//System.out.println("\nRemove Router Group Panel " + hsIdx);
		rgPanel.remove(tablePanes[hsIdx]);
	    }
	}
	//System.out.print("\nAdd back Router Group Panes ...  ");
	for (int i = 0; i < numOfRTRGroups; i++) {
	    if (rtrGrpOrder[i] >= 0) {
		//System.out.print(" " + rtrGrpOrder[i] + " ");
		rgPanel.add(tablePanes[rtrGrpOrder[i]]);

		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = i; // + 1;
		c.insets = new Insets(8, 0, 0, 5);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;  //1.0;
		c.weighty = 1.0;
		rgLayout.setConstraints(tablePanes[rtrGrpOrder[i]], c);
	    }
	}
	//System.out.println("");

	this.validate();
	this.repaint();

    }  // reconfigureRTRGrpPanes


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to define & build the RTR data panel. This is a subpanel of the
     * top-level RTRPanel. This will allow multiple subpanels to be
     * displayed inside the main RTR panel.
     */

    class RTRTablePanel extends JPanel {
	// class RTRTablePanel extends JPanel
	protected JTable rtrTable;
	private JScrollPane rtrScrollPane;
	private RTRTableModel rtrModel;
	private Timestamp rtrTimestamp;
	private JLabel rtrGroupLab;
	private int rtrSortCol = 0;
	private String rtrSortColName = null;
	private JPopupMenu rtrC0Popup;
	private JPopupMenu rtrPopup;
	private int rtrActionCol;
	private String [] rtrDBColSeq = null;
	private int rtrNumCols = 0;
	protected String  [] rtrColNames;
	private boolean [] rtrColOn;
	protected Object [][] routerData = null;
	protected String rtrName = null;
	protected int rtrGrp;
	ArrayList hiddenColumns = new ArrayList(5);
	int nHiddenCols = 0;

	protected Object [][] routerMasterData = null;
	protected int [][] rtrMasterColorIdx = null;
	int [] rtrColumnWidths = null; // Set in initRTRColumnSizes
	private int rtrMasterSortCol = 0;

	private RTRCellRenderer cellRenderer = new RTRCellRenderer();

	private NormalHeaderRenderer columnHdrRenderer =
	    new NormalHeaderRenderer();

	int panelWidth = 0;

	boolean stale = false;
	boolean troubled = false;
	boolean critical = false;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor used to build panel for displaying RTR data.
	 *
	 * @param rtrIdx index of the RTR data to be loaded.
	 */

	RTRTablePanel(int rtrIdx) {

	    super(new BorderLayout(), false);

	    this.rtrGrp = rtrGroupIds[rtrIdx];

	    if (selected) {
		//Debug.out("Load data for " + fsName + " ROUTER pane.");
		try {
		    getRTRData(database, rtrGroupIds[rtrIdx]);
		} catch (Exception e) {
		    Debug.out("Exception caught while loading RTR data.\n");
		    Thread.dumpStack();
		    return;
		}
		//String [] ops = new String[35];
		//for (int j = 0; j < ops.length; j++) {
		    //ops[j] = "rtr_op-" + j;
		//}

		rtrColOn = new boolean[rtrColNames.length];
		for (int i = 0; i < rtrColOn.length; i++)
		    rtrColOn[i] = true;

	    } else {
		if (localDebug)
		    Debug.out("Load data for unselected FS pane " + fsName);

		rtrColNames = new String[rtrMasterColNames.length];
		routerData = new Object[1][rtrMasterColNames.length];
		rtrColOn = new boolean[rtrMasterColNames.length];

		for (int i = 0; i < rtrColOn.length; i++) {
		    if (i == 0)
			routerData[0][i] = "Router abc";
		    else
			routerData[0][i] = "123.45";

		    rtrColOn[i] = true;
		}

	    }

	    JPanel hdrPane = new JPanel(new BorderLayout(), false);
	    rtrGroupLab = new JLabel("Router Group " + this.rtrGrp + "  " +
				     rtrTimestamp.toString());
	    hdrPane.setBackground(new Color(255,151,65));  //(Color.yellow);
	    hdrPane.add(rtrGroupLab, BorderLayout.NORTH);
	    add(hdrPane, BorderLayout.NORTH);


	    // The following block of code is used to initialize the number of
	    // columns and the order of the columns as retrieved from the DB.
	    // As the columns are reordered and deleted, these original values
	    // can be used as a reference to calculate the order in which
	    // the TableModel data array should be loaded.  11/28/2006
	    if (rtrDBColSeq == null) {
		rtrNumCols = rtrColNames.length;
		rtrDBColSeq = new String[rtrNumCols];
		for (int i = 0; i < rtrNumCols; i++) {
		    //System.out.println(i + "  : " + rtrColNames[i]);
		    rtrDBColSeq[i] = rtrColNames[i];
		}
	    }

	    //Debug.out("Create table model object.");
	    rtrModel = rtrTableModelCreate(rtrColNames);
	    //Debug.out("Model created.");
	    //Debug.out("Create JTable object for FS # " + fsName);
	    rtrTable = new JTable(rtrModel);
	    rtrTable.setDefaultRenderer(Object.class, cellRenderer);

	    CellListener cellListener = new CellListener("RTR", this.rtrGrp,
							 rtrTable, database,
							 parentFS, routerMasterData);
	    rtrTable.addMouseListener(cellListener);

	    JTableHeader tableHeader = rtrTable.getTableHeader();
	    tableHeader.addMouseListener(new RTRTableHdrListener(parentFS, rtrTable));

	    // Define the popup menu for the column 0 in this JTable
	    rtrC0Popup = new JPopupMenu();
	    JMenuItem menuItem = new JMenuItem("Reset");
	    menuItem.addActionListener(new RTRPopupListener(rtrTable));
	    rtrC0Popup.add(menuItem);

	    rtrC0Popup.addSeparator();

	    // Define the popup menu for the other columns in this JTable
	    rtrPopup = new JPopupMenu();
	    menuItem = new JMenuItem("Hide");
	    menuItem.addActionListener(new RTRPopupListener(rtrTable));
	    rtrPopup.add(menuItem);

	    menuItem = new JMenuItem("Reset");
	    menuItem.addActionListener(new RTRPopupListener(rtrTable));
	    rtrPopup.add(menuItem);

	    rtrPopup.addSeparator();

	    Enumeration e = rtrTable.getColumnModel().getColumns();
	    while (e.hasMoreElements()) {
		//System.out.println("Set header renderer.");
		((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);
	    }
	    int resizeMode = rtrTable.getAutoResizeMode();
	    //System.out.println("ROUTER table auto-resize mode = " + resizeMode);
	    rtrTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

	    //Debug.out("Create Scroll pane for view into JTable.");
	    rtrScrollPane = new JScrollPane(rtrTable);
	    rtrColumnWidths = new int[rtrMasterColNames.length];
	    initRTRColumnSizes(rtrTable, rtrModel);
	    rtrScrollPane.getViewport().setBackground(Color.black);
	    panelWidth = 235;		

	    setPreferredSize(new Dimension(panelWidth, 35));
	    setMaximumSize(new Dimension(1200, 1200));  //(panelWidth+50, 700));
	    setAlignmentX(LEFT_ALIGNMENT);

	    add(rtrScrollPane, BorderLayout.CENTER);

	    // Suppress auto-create of columns from data model now that they're established.
	    rtrTable.setAutoCreateColumnsFromModel(false);

	}  // RTRTablePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Reload the Router table panel.
	 *
	 * @param tableGroupIdx index of the router group data to reload.
	 */

	void reloadRTRTablePanel(int tableGroupIdx) {

	    rtrGroupLab.setText("Router Group " + this.rtrGrp + "  " +
				     rtrTimestamp.toString());

	    if (localDebug)
		Debug.out("Get RTR data");

	    try {
		getRTRData(database, rtrGroupIds[tableGroupIdx]);
	    } catch (Exception e) {
		Debug.out("Exception caught while loading RTR data.\n");
		e.printStackTrace();
		return;
	    }

	    if (localDebug)
		Debug.out("Set column names for " + rtrNumCols + " columns.");

	    if (localDebug)
		System.out.println("Reload RTR data vector row count = " + routerData.length);

	    synchronized(RTRPanel.this.parentFS.synchObj) {
		rtrModel.setDataVector(routerData, rtrMasterColNames);
	    }

	    if (localDebug)
		System.out.println("RTR setDataVector completed.");

	    Enumeration e = rtrTable.getColumnModel().getColumns();

	    while (e.hasMoreElements()) {

		TableColumn tc = ((TableColumn) e.nextElement());
		tc.setHeaderRenderer(columnHdrRenderer);

		if (rtrColumnWidths != null) {
		    String hdrString = (String)tc.getHeaderValue();
		    int width = 58;
		    for (int i = 0; i < rtrMasterColNames.length; i++) {
			if ((hdrString != null) && hdrString.equals(rtrMasterColNames[i])) {
			    width = rtrColumnWidths[i];
			    break;
			}
		    }
		    tc.setPreferredWidth(width);
		}
	    }

	}  // reloadRTRTablePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Initailize the column widths for the router table.
	 *
	 * @param table JTable containing RTR data.
	 *
	 * @param model model for the RTR JTable.
	 */

	public void initRTRColumnSizes(JTable table, RTRTableModel model) {

	    int rowCount = model.getRowCount();
	    int colCount = model.getColumnCount();
	    int [] colWidth = new int [colCount];
	    TableColumn column = null;
	    Component comp = null;

	    if (localDebug)
		System.out.println("Calculate Column widths for RTR table.");

	    for (int i = 0; i < colCount; i++) {
		int cellWidth = 0;
		column = table.getColumnModel().getColumn(i);
		//String tmpS = model.getColumnName(i);
		//Debug.out("AAAAAA " + tmpS);
		//if (tmpS.indexOf("\n") >= 0)
		    //tmpS = tmpS.substring(0,tmpS.indexOf("\n"));
		//Debug.out("BBBBBB");

		comp = table.getDefaultRenderer(model.getColumnClass(i)).
		    getTableCellRendererComponent(table, model.getColumnName(i),
						  false, false, 0, i);
		cellWidth = comp.getPreferredSize().width + 10;
		//Debug.out("CCCCCC");
		for (int j = 0; j < rowCount; j++) {

		    comp = table.getDefaultRenderer(model.getColumnClass(i)).
			getTableCellRendererComponent(table, model.getValueAt(j, i),
						      false, false, j, i);
		    cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
		}
		if (localDebug)
		    System.out.println("RTR Col " + i + " Max Width = " + cellWidth);

		column.setPreferredWidth(cellWidth+10);
		//Debug.out("Assign " + cellWidth + " +10 to rtrColumnWidths[" + i + "]");
		rtrColumnWidths[i] = cellWidth+10;
	    }	  
     
	}  // initRTRColumnSizes


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class implementing the multi-line column header renderer.
	 */

	public class MultiLineHeaderRenderer
	    extends JList implements TableCellRenderer
	{

	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for the multi-line column header renderer.
	     */

	    public MultiLineHeaderRenderer() {

		setOpaque(true);
		setForeground(UIManager.getColor("TableHeader.foreground"));
		setBackground(UIManager.getColor("TableHeader.background"));
		setBorder(UIManager.getBorder("TableHeader.cellBorder"));
		ListCellRenderer renderer = getCellRenderer();
		((JLabel) renderer).setHorizontalAlignment(JLabel.CENTER);
		setCellRenderer(renderer);

	    }  // MultiLineHeaderRenderer


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns component representing specified cell in JTable.
	     *
	     * @param table the JTable containing the cell being rendered.
	     *
	     * @param value the value to assign to the cell at [row, column]
	     *
	     * @param isSelected true if cell is selected.
	     *
	     * @param hasFocus true if cell has focus.
	     *
	     * @param row the row of the cell to render.
	     *
	     * @param column the column of the cell to render.
	     *
	     * @return the table cell renderer.
	     */

	    public Component getTableCellRendererComponent(JTable table, Object value,
			   boolean isSelected, boolean hasFocus, int row, int column) {

		setFont(table.getFont());
		String str = (value == null) ? "" : value.toString();
		setToolTipText(str);
		setFont(new Font("helvetica", Font.BOLD, 10));
		    
		//if (localDebug)
		    //Debug.out("row = " + row + ",  column = " + column);

		try {
		    if ((rtrSortCol != 0) &&
			rtrTable.getColumnName(column).equals(rtrSortColName)) {
			//rtrColNames[Math.abs(mdsSortCol)-1])) {

			//System.out.println("\nMDS Sorting on column " + rtrSortCol + "\n");
			if (rtrSortCol > 0)
			    str += " ^";
			else
			    str += " v";
		    }
		} catch (Exception e) {

		    Debug.out("Exception caught while rendering header for RTR " +
			      " table. Row = " + row + ",  Column = " + column +
			      ",  value = " + value + "\n" + e.getMessage());
		}

		BufferedReader br = new BufferedReader(new StringReader(str));
		String line;
		Vector v = new Vector();
		try {
		    while ((line = br.readLine()) != null) {
			v.addElement(line);
		    }
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		setListData(v);
		return this;

	    }  // getTableCellRendererComponent

	}  // MultiLineHeaderRenderer


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class implementing the renderer for the router table column headers.
	 */

	class NormalHeaderRenderer extends JLabel implements TableCellRenderer {

	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constrcutor for the renderer.
	     */

	    public NormalHeaderRenderer() {

		super();

	    }  // NormalHeaderRenderer


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Get the renderer component for the router table column header.
	     *
	     * @param table the JTable containing the cell being rendered.
	     *
	     * @param value the value to assign to the cell at [row, column]
	     *
	     * @param isSelected true if cell is selected.
	     *
	     * @param hasFocus true if cell has focus.
	     *
	     * @param row the row of the cell to render.
	     *
	     * @param column the column of the cell to render.
	     *
	     * @return the table cell renderer.
	     */

	    public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {
		setFont(table.getFont());
		String str = (value == null) ? "" : value.toString();
		setToolTipText(str);
		setFont(new Font("helvetica", Font.BOLD, 10));

		boolean usehtml = false;
		if ((str.length() > 8) && str.indexOf(" ") < 8) {
		    str = "<html><b>" + str.replaceFirst("\\s", "<br>");
		    usehtml = true;
		}

		try {
		    if ((rtrSortCol != 0) &&
			rtrTable.getColumnName(column).equals(rtrSortColName)) {
			
			if (rtrSortCol > 0) {
			    str += " ^";
			} else {
			    str += " v";
			}

		    }
		} catch (Exception e) {
		    Debug.out("Exception caught while rendering header for RTR table. Row = " +
			      row + ",  Column = " + column + ",  value = " +
			      value + "\n" + e.getMessage());
		}

		if (usehtml)
		    str += "</b></html>";

		setText(str);

		return this;

	    }  // getTableCellRendererComponent

	}  // NormalHeaderRenderer


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Call the constructor for the router table model.
	 *
	 * @param columnNames array of column names to appear in the table.
	 *
	 * @return table model for the MDS table.
	 */

	private RTRTableModel rtrTableModelCreate(String [] columnNames) {

	    return new RTRTableModel(columnNames);

	}  // rtrTableModelCreate


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class to define the table model for the router data. Extends AbstractTableModel.
	 */

	// Subclass DefaultableModel so that cells can be set to uneditable.
	public class RTRTableModel extends AbstractTableModel {

	    public String [] columnNames = null;
	    public Object [][] data = null;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constrcutor for the router table model.
	     *
	     * @param columnNames array of column names to appear in the table.
	     */

	    protected RTRTableModel(String [] columnNames) {

		this.data = routerData;
		this.columnNames = columnNames;

	    }  // RTRTableModel


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns false, cell not editable.
	     */

	    public  boolean isCellEditable(int row, int column) {

		return false;

	    }  // isCellEditable


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Return value in the specified cell.
	     *
	     * @param row cell row number.
	     *
	     * @param col cell column number.
	     *
	     * @return value contained in the cell. 
	     */

	    public Object getValueAt(int row, int col) {

		try {
		    return data[row][col];
		} catch (ArrayIndexOutOfBoundsException ex) {
		    Debug.out("ArrayIndexOutOfBoundsException : row = " + row + "  col = " + col);
		    return null;
		}

	    }  // getValueAt


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns row count.
	     *
	     * @return row count.
	     */

	    public int getRowCount() {

		return data.length;

	    }  // getRowCount


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns column name.
	     *
	     * @param col index of column for which name is being queried.
	     *
	     * @return name of the specified column.
	     */

	    public String getColumnName(int col) {

		return columnNames[col];

	    }  // getColumnName


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns column count.
	     *
	     * @return column count.
	     */

	    public int getColumnCount() {

		return columnNames.length;

	    }  // getColumnCount


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Set the data vector for the router table.
	     *
	     * @param idata Array containing the values for the RTR table cells.
	     *
	     * @param colNames array containing the names of the columns for the RTR table.
	     */

	    public void setDataVector(Object[][] idata, String [] colNames) {

		this.data = idata;
		this.columnNames = colNames;

		fireTableDataChanged();

	    }  // setDataVector


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Invoke the comparator to sort the specified column.
	     */

	    public void sort(int sortCol) {

		synchronized(RTRPanel.this.parentFS.synchObj) {
		    Arrays.sort(data, new ColumnComparator(sortCol));
		    fireTableChanged(new TableModelEvent(this, 0, data.length));
		}

	    }  // sort

	}  // RTRTableModel


	//////////////////////////////////////////////////////////////////////////////

	/** 
	 * Converts a visible column index to a column index in the model.
	 * Returns -1 if the index does not exist.
	 *
	 * @param table the JTable containing the column being mapped.
	 *
	 * @param vColIndex the view index of the column in question.
	 *
	 * @return the view colummn index.
	 */

	public int toModel(JTable table, int vColIndex) {

	    if (vColIndex >= table.getColumnCount()) {
		return -1;
	    }
	    return table.getColumnModel().getColumn(vColIndex).getModelIndex();

	}  // toModel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Converts a column index in the model to a visible column index.
	 * Returns -1 if the index does not exist.
	 *
	 * @param table the JTable containing the column being mapped.
	 *
	 * @param mColIndex the model index of the column in question.
	 *
	 * @return the view colummn index.
	 */

	public int toView(JTable table, int mColIndex) {

	    for (int c=0; c<table.getColumnCount(); c++) {
		TableColumn col = table.getColumnModel().getColumn(c);
		if (col.getModelIndex() == mColIndex) {
		    return c;
		}
	    }
	    return -1;

	}  // toView


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class that handles the router table header mouse button clicks.
	 */

	public class RTRTableHdrListener extends MouseAdapter {

	    private FileSystemPanel parent;
	    private JTable table;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for the class that handles the router table header mouse button clicks.
	     *
	     * @param parent the FileSystem object to which this RTRPanel belongs.
	     *
	     * @param table the JTable containing the RTR data.
	     */

	    public RTRTableHdrListener(FileSystemPanel parent, JTable table) {
		super();

		this.parent = parent;
		this.table = table;

	    }  // RTRTableHdrListener


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Handler for mouse button release events in the router table column header.
	     *
	     * @param e the MouseEvent causing the handler to be invoked.
	     */

	    public void mouseReleased(MouseEvent e) {
		TableColumnModel columnModel = table.getColumnModel();
		int viewColumn = columnModel.getColumnIndexAtX(e.getX());
		int buttNum = e.getButton();

		int colCount = rtrTable.getColumnCount();
		for (int j = 0; j < colCount; j++) {
		    TableColumn tc = columnModel.getColumn(j);
		    int colWidth = tc.getWidth();
		    String cName = rtrTable.getColumnName(j);
		    for (int i = 0; i < rtrMasterColNames.length; i++) {
			if (rtrMasterColNames[i].equals(cName)) {
			    rtrColumnWidths[i] = colWidth;
			    //System.out.println("RTR column " + cName + " width set to " + colWidth);
			    break;
			}
		    }
		}

		if ((buttNum == 3) && (viewColumn != 0)) {
		    if (localDebug)
			System.out.println("RTR Right mouse button Release detected in col " +
					   viewColumn);

		    rtrActionCol = viewColumn;

		    if (viewColumn != 0) {
			showPopup(e);
		    } else {
			showC0Popup(e);
		    }
		}

	    }  // mouseReleased


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Handler for mouse button click events in the router table column header.
	     *
	     * @param e the MouseEvent causing the handler to be invoked.
	     */

	    public void mouseClicked(MouseEvent e) {

		TableColumnModel columnModel = table.getColumnModel();
		int viewColumn = columnModel.getColumnIndexAtX(e.getX());
		int buttNum = e.getButton();
		if ((viewColumn > rtrNumCols) || (viewColumn < 0))
		    return;

		int dataModelColindx = toModel(table, viewColumn);
		if (dataModelColindx == -1)
		    return;

		if (localDebug)
		    System.out.println("Mouse Click event detected in RTR Table column " +
				       viewColumn + "\nButton # " + buttNum);

		if (buttNum == 1) {
		    String sortID = rtrTable.getColumnName(viewColumn);
		    String lastSortColName = rtrSortColName;
		    int lastSortDir = 1;
		    if (rtrMasterSortCol < 0)
			lastSortDir = -1;
		    rtrSortColName = sortID;
		    int sortColumn = 0;
		    for (int i = 0; i < rtrColNames.length; i++) {
			if (rtrColNames[i].equals(sortID)) {
			    //Debug.out("\nsortID " + sortID + " <==> rtrColNames[" + i + "] " + rtrColNames[i]);
			    sortColumn = i;  //viewColumn;
			    //System.out.println("Sorting on column  " + i + " - " + sortID);
			    break;
			}
		    }
		    rtrSortCol = sortColumn + 1;
		    if ((lastSortColName != null) && (lastSortColName.equals(rtrSortColName)))
			rtrSortCol = rtrSortCol * (-lastSortDir);

		    //System.out.println("Last Sort Col = " + lastSortColName + "  new Sort Col = " +
		    //rtrSortColName + "  last sort dir = " + lastSortDir);
		    if (localDebug)
			System.out.println("RTR sort : " + rtrSortCol + " = " +
					   rtrColNames[Math.abs(rtrSortCol)-1]);


		    // Calculate the column from the master data array
		    sortColumn = 0;
		    for (int i = 0; i < rtrMasterColNames.length; i++) {
			if (rtrMasterColNames[i].equals(sortID)) {
			    sortColumn = i;
			    //System.out.println("Set sorting for Master column  " + i + " - " + sortID);
			    break;
			}
		    }
		    rtrMasterSortCol = sortColumn + 1;
		    if (rtrSortCol < 0)
			rtrMasterSortCol = -rtrMasterSortCol;

		    if (localDebug)
			System.out.println("RTR Master sort : " + rtrMasterSortCol + " = " +
					   rtrMasterColNames[Math.abs(rtrMasterSortCol)-1]);
	    
		    // Sort on selected row
		    if (rtrSortCol != 0) {
			((RTRTableModel)this.table.getModel()).sort(rtrSortCol);
			table.getTableHeader().repaint();
		    }
		} else if (buttNum == 3) {
		    if (localDebug)
			System.out.println("RTR Right mouse button click detected in col " +
					   viewColumn);

		    rtrActionCol = dataModelColindx;

		    if (dataModelColindx != 0) {
			showPopup(e);
		    } else {
			showC0Popup(e);
		    }
		}

	    }  // mouseClicked


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Display the router table column > 0 popup menu.
	     */

	    private void showPopup(MouseEvent e) {

		rtrPopup.show(e.getComponent(), e.getX(), e.getY());

	    }  // showPopup


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Display the router table column 0 popup menu.
	     */

	    private void showC0Popup(MouseEvent e) {

		rtrC0Popup.show(e.getComponent(), e.getX(), e.getY());

	    }  // showC0Popup

	}  // RTRTableHdrListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class that implements the ActionListener for the router JTable
	 */

        public class RTRPopupListener implements ActionListener {

	    private JTable table;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for the ActionListener being implemented.
	     */

	    public RTRPopupListener(JTable table) {
		super();

		this.table = table;

	    }  // RTRPopupListener


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * The action handler for the router table button clicks.
	     *
	     * @param e the ActionEvent that the listener is notified of.
	     */

	    public void actionPerformed(ActionEvent e) {

		parentFS.getParentOf().resetRefresh();

		if (localDebug)
		    System.out.println("ActionEvent detected in RTR popup \n" +
				       e.getActionCommand() + " on Column # " +
				       rtrActionCol);

		if (e.getActionCommand().equals("Hide")) {
		    // Get column name and use this to identify the column number to be deleted.
		    // That is the index in rtrColOn to set to false. The value in rtrActionCol
		    // is the one to use for the rtrTable calls.

		    String cName = rtrTable.getColumnName(rtrActionCol);
		    for (int i = 0; i < rtrColNames.length; i++) {
			if (rtrColNames[i].equals(cName)) {
			    rtrColOn[i] = false;
			    //System.out.println("Setting orig column # " + i + " to false.");
			    break;
			}
		    }
		    TableColumnModel rtrTableColModel = rtrTable.getColumnModel();
		    TableColumn hideCol = rtrTableColModel.getColumn(rtrActionCol);


		    // Add removed column to hiddenColumns ArrayList
		    hiddenColumns.ensureCapacity(nHiddenCols+1);
		    hiddenColumns.add(nHiddenCols++, hideCol);



		    if (localDebug)
			Debug.out("Removing column to removeColumn # " + rtrActionCol);

		    rtrTableColModel.removeColumn(hideCol);
		    rtrNumCols--;

		    JMenuItem menuItem = new JMenuItem("Restore " + cName);
		    menuItem.addActionListener(new RTRPopupListener(rtrTable));
		    rtrPopup.add(menuItem);

		    menuItem = new JMenuItem("Restore " + cName);
		    menuItem.addActionListener(new RTRPopupListener(rtrTable));
		    rtrC0Popup.add(menuItem);

		} else if(e.getActionCommand().startsWith("Restore ")) {
		    String command = e.getActionCommand();
		    //Debug.out("Restore menuItem selected." + command);
		    String restoreColName = command.substring(8);

		    // Add the column back into the table to the right of column
		    // where the action took place.
		    //Debug.out("Restoring columnn " + restoreColName + " at column # " +
		    //rtrActionCol);
		    int restoreCol = 0;
		    while (!rtrMasterColNames[restoreCol].equals(restoreColName))
			restoreCol++;

		    if (restoreCol >= rtrMasterColNames.length) {
			Debug.out("Error matching column names for restoration.");
			return;
		    }
		    //Debug.out("Restoring column " + rtrMasterColNames[restoreCol]);

		    // Locate coulmn to be added back in the hiddenColumns ArrayList
		    ListIterator it = hiddenColumns.listIterator();

		    while (it.hasNext()) {
			TableColumn column = (TableColumn)it.next();
			if (((String)(column.getHeaderValue())).equals(restoreColName)) {
			    rtrTable.getColumnModel().addColumn(column);
			    it.remove();
			    nHiddenCols--;
			    break;
			}
		    }

		    rtrModel.fireTableDataChanged();

		    rtrColOn[restoreCol] = true;
		    rtrNumCols++;


		    // Remove restore menu item from  Popup menu.
		    int cCount = rtrPopup.getComponentCount();
		    for (int i = 3; i < cCount; i++) {
			Component comp = rtrPopup.getComponent(i);
			String compText = ((AbstractButton)comp).getText();
			//System.out.println("Component # " + i + " = " + compText);
			if (command.equals(((AbstractButton)comp).getText())) {
			    rtrPopup.remove(i);
			    break;
			}
		    }
		    cCount = rtrC0Popup.getComponentCount();
		    for (int i = 2; i < cCount; i++) {
			Component comp = rtrC0Popup.getComponent(i);
			String compText = ((AbstractButton)comp).getText();
			//System.out.println("C0 Component # " + i + " = " + compText);
			if (command.equals(((AbstractButton)comp).getText())) {
			    rtrC0Popup.remove(i);
			    break;
			}
		    }

		} else if(e.getActionCommand().equals("Reset")) {

		    // Locate coulmn to be added back in the hiddenColumns ArrayList
		    ListIterator it = hiddenColumns.listIterator();

		    while (it.hasNext()) {
			TableColumn column = (TableColumn)it.next();
			rtrTable.getColumnModel().addColumn(column);
			it.remove();
			nHiddenCols--;
		    }

		    for (int i = 0; i < rtrColOn.length; i++)
			rtrColOn[i] = true;

		    rtrNumCols = rtrMasterColNames.length;

		    // Move TableColumns to the original order.
		    for (int i = 0; i < rtrMasterColNames.length; i++) {
			int istrt = i;
			for (int j = istrt; j < rtrMasterColNames.length; j++) {
			    String tcn = rtrTable.getColumnName(j);
			    if (tcn.equals(rtrMasterColNames[i])) {
				rtrTable.moveColumn(j, i);
			    }
			}
		    }


		    if (localDebug)
			Debug.out("Remove menu items from popup menus.");
		    // Remove JPopup menuitems for the restored columns.
		    int cCount = rtrPopup.getComponentCount();
		    for (int i = 3; i < cCount; i++) {
			Component comp = rtrPopup.getComponent(3);
			if (((AbstractButton)comp).getText().startsWith("Restore "))
			    rtrPopup.remove(3);
		    }

		    if (localDebug)
			Debug.out("Remove menu items from C0 popup menu.");
		    // Remove JPopup menuitems for the restored columns.
		    cCount = rtrC0Popup.getComponentCount();
		    for (int i = 2; i < cCount; i++) {
			Component comp = rtrC0Popup.getComponent(2);
			if (((AbstractButton)comp).getText().startsWith("Restore "))
			    rtrC0Popup.remove(2);
		    }

		    rtrModel.fireTableDataChanged();

		}

	    }  // actionPerformed

	}  // RTRPopupListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class used to render the roouter table cells.
	 */

	class RTRCellRenderer extends DefaultTableCellRenderer {

	    Color [] cellColor = new Color[] {
		Color.black,
		Color.red,  //  gray,  stale color changed on 05/25/07
		Color.yellow,
		Color.red
	    };

	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for cell renderer for router table.
	     */


	    RTRCellRenderer() {

		setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

	    }  // RTRCellRenderer


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns the default table cell renderer.
	     *
	     * @param table the JTable containing the cell being rendered.
	     *
	     * @param value the value to assign to the cell at [row, column]
	     *
	     * @param isSelected true if cell is selected.
	     *
	     * @param hasFocus true if cell has focus.
	     *
	     * @param row the row of the cell to render.
	     *
	     * @param column the column of the cell to render.
	     *
	     * @return the default table cell renderer.
	     */

	    public Component getTableCellRendererComponent(JTable table,
			     Object value, boolean isSelected, boolean hasFocus,
			     int row, int column)
	    {

		final Color lGreen = new Color(150, 200, 150);
		final Color orange = new Color(220, 180, 100);
		final Color aggGrey = new Color(180, 180, 180);
	        final int width = 4;
		String col0Name = table.getColumnName(0);

		//if ((row == 1) && (((String)table.getValueAt(row,0)).startsWith("zeus"))) {
		    //Debug.out(column + " -> Model index " + table.convertColumnIndexToModel(column));
		//}

		Color cellBgColor = Color.black;
		Color cellFgColor = Color.white;
		int rowCnt = table.getRowCount();

		if (row < (rowCnt-4)) {
		    cellBgColor = getCellColor(value,  table.convertColumnIndexToModel(column)-1);
		    if ((cellBgColor == Color.black) || (cellBgColor == Color.red))
			cellFgColor = Color.white;
		    else
			cellFgColor = Color.black;

		    //if (localDebug)
		        //Debug.out(row + "  " + column + "  cellBgColor = " + cellBgColor.toString());
		}

		if (row >= (rowCnt-4)) {
		    //Debug.out("Last 4 rows...");

		    cellFgColor = Color.black;

		    if (critical) {
			cellBgColor =  Color.red;
			cellFgColor =  Color.white;
		    } else if (troubled)
			cellBgColor = orange;
		    else if (stale)
			cellBgColor = aggGrey;
		    else
			cellBgColor = lGreen;
		}

		setBackground(cellBgColor);
		setForeground(cellFgColor);
	    
		setFont(new Font("helvetica", Font.PLAIN, 10));  //font.getSize()));
		if (column > 0)
		    setHorizontalAlignment(LEFT);  // May want to allow setting to RIGHT
		else
		    setHorizontalAlignment(LEFT);
	    
		if (value instanceof Float)
		    setText(TextFormatter.format((Float)value, width));
		else if (value instanceof Integer)
		    setText(TextFormatter.format((Integer)value, width));
		else if (value instanceof Long)
		    setText(TextFormatter.format((Long)value, width));
		else if (value instanceof Double) {
		    setText(TextFormatter.format((Double)value, width));
		} else
		    setText(TextFormatter.format((String)value, width, false));

		return this;  // Was getting ClassCastException here earlier.

	    }  // getTableCellRendererComponent


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Returns Color object used to render the cell containing the specified variable
	     * with the specified value.
	     *
	     * @param val value of the variable to be displayed in cell.
	     *
	     * @param varindx index into vi array for this variable.
	     */

	    public Color getCellColor(Object val,  int varindx) {

		boolean rowCritical = false;
		boolean rowTroubled = false;
		boolean rowStale = false;
	
		if (varindx < 0)
		    return Color.black;

		try {
		    //for (int i = 1; i < routerMasterData[i].length; i++) {
			float fVal = (float)0;
			//int varidx = varIds[i-1];
			int varidx = varIds[varindx];
			if ((vi == null) || (vi.length <= varindx)) {
			    return Color.black;  //Color.black
			} else if (val == null) {
			    rowStale = true;
			    return Color.gray;  //Color.gray
			} else if (vi[varidx].threshType == 0) {
			    return Color.black;  //Color.black
			} else if (vi[varidx].threshType == 1) {
			    //Object val = routerMasterData[irow][i];
			    if (val instanceof Double) {
				if (((Double)(val)).floatValue() != vi[varidx].threshVal1) {
				    //rtrMasterColorIdx[irow][i] = 3;  //Color.red
				    rowCritical = true;
				    return Color.red;
				}
			    } else if (val instanceof Long) {
				if (((Long)(val)).floatValue() != vi[varidx].threshVal1) {
				    //rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				    rowCritical = true;
				    return Color.red;
				}
			    } else if (val instanceof Float) {
				if (((Float)(val)).floatValue() != vi[varidx].threshVal1) {
				    //rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				    rowCritical = true;
				    return Color.red;
				}
			    } else if (val instanceof Integer) {
				if (((Integer)(val)).floatValue() != vi[varidx].threshVal1) {
				    //rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				    rowCritical = true;
				    return Color.red;
				}
			    } else {
				//rtrMasterColorIdx[irow][i] = 0;  //Color.black;
				    return Color.black;
			    }
			} else if (vi[varidx].threshType == 2) {
			    //Object val = routerMasterData[irow][i];
			    if (val instanceof Double) {
				fVal = ((Double)(val)).floatValue();
			    } else if (val instanceof Long) {
				fVal = ((Long)(val)).floatValue();
			    } else if (val instanceof Float) {
				fVal = ((Float)(val)).floatValue();
			    } else if (val instanceof Integer) {
				fVal = ((Integer)(val)).floatValue();
			    }

			    if ((fVal < vi[varidx].threshVal1) ||
				(fVal > vi[varidx].threshVal2)) {
				//rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				rowCritical = true;
				return Color.red;
			    } else {
				//rtrMasterColorIdx[irow][i] = 0;  //Color.black;
				return Color.black;
			    }
			} else if (vi[varidx].threshType == 3) {
			    //Object val = routerMasterData[irow][i];

			    if (val instanceof Double) {
				fVal = ((Double)(val)).floatValue();
			    } else if (val instanceof Long) {
				fVal = ((Long)(val)).floatValue();
			    } else if (val instanceof Float) {
				fVal = ((Float)(val)).floatValue();
			    } else if (val instanceof Integer) {
				fVal = ((Integer)(val)).floatValue();
			    }

			    //if ( test && (val != null))
				//Debug.out("Variable Thresh type = 3, Thresh1 = " + vi[varidx].threshVal1 +
					  //", Thresh2 = " + vi[varidx].threshVal2 + ", value = " + fVal);

			    if (fVal >= vi[varidx].threshVal2) {
				//rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				rowCritical = true;
				//if (test) Debug.out("RED");
				return Color.red;
			    } else if (fVal >= vi[varidx].threshVal1) {
				//rtrMasterColorIdx[irow][i] = 2;  //Color.yellow;
				rowTroubled = true;
				//if (test) Debug.out("YELLOW");
				return Color.yellow;
			    } else {
				//rtrMasterColorIdx[irow][i] = 0;  //Color.black;
				return Color.black;
			    }
			} else if (vi[varidx].threshType == 4) {
			    //Object val = routerMasterData[irow][i];
			    if (val instanceof Double) {
				fVal = ((Double)(val)).floatValue();
			    } else if (val instanceof Long) {
				fVal = ((Long)(val)).floatValue();
			    } else if (val instanceof Float) {
				fVal = ((Float)(val)).floatValue();
			    } else if (val instanceof Integer) {
				fVal = ((Integer)(val)).floatValue();
			    }

			    if (fVal < vi[varidx].threshVal2) {
				//rtrMasterColorIdx[irow][i] = 3;  //Color.red;
				rowCritical = true;
				return Color.red;
			    } else if (fVal < vi[varidx].threshVal1) {
				//rtrMasterColorIdx[irow][i] = 2;  //Color.yellow;
				rowTroubled = true;
				return Color.yellow;
			    } else {
				//rtrMasterColorIdx[irow][i] = 0;  //Color.black;
				return Color.black;
			    }
			}
		    //}  // for (int i = 1; i < routerMasterData[i].length; i++)
		    if (rowCritical) {
			//rtrMasterColorIdx[irow][0] = 3;
			critical = true;
		    } else if (rowTroubled) {
			//rtrMasterColorIdx[irow][0] = 2;
			troubled = true;
		    } else if (rowStale) {
			//rtrMasterColorIdx[irow][0] = 1;
			stale = true;
		    } else {
			//rtrMasterColorIdx[irow][0] = 0;
		    }

		} catch (Exception e) {
		    Debug.out("Exception caught : " + e.getMessage());
		    e.printStackTrace();
		}
		return Color.black;

	    }  // getCellColor

	}  // RTRCellRenderer


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Load router data from database for most recent timestamp.
	 *
	 * @param database Database object used to load data with.
	 *
	 * @param rtrGroupId the router group identifier.
	 */

	protected void getRTRData(Database database, int rtrGroupId)
	    throws Exception 
	{

	    if (localDebug)
		Debug.out("Entering getRTRData");

	    if (localDebug)
	        Debug.out("Get data for latest timestamp.");

	        stale = false;
	        troubled = false;
	        critical = false;

		//
		// Get latest TS_ID from TIMESTAMP_ID table.
		//

		TimeInfo timeinfo = database.getLatestTimeInfo("ROUTER_DATA");
		rtrTimestamp = timeinfo.timestamp;

		//rtrTimestamp = new Timestamp(new java.util.Date().getTime());

		if (localDebug)
		    Debug.out("timestamp = " + rtrTimestamp);

		//
		// Get RTR data for the router group in method arg for latest timestamp.
		//

		if (localDebug)
		    Debug.out("Get RTR data from DB");

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

		    zeroLoadRTRData();
		    return;
		}

		//
		// Get column headers.
		//

		String [] rtrColTypes = {"STRING", "FLOAT", "FLOAT"};

		if (localDebug)
		Debug.out("Check the current column order.");

		rtrColNames = new String[rtrMasterColNames.length];
		for (int i = 0; i < rtrMasterColNames.length; i++)
		    rtrColNames[i] = rtrMasterColNames[i];

		rtrNumCols = rtrMasterColNames.length;


		int aSize = 0;
		boolean [] ammaFlags = null;

		try {
		    aSize = rtrDBData.getSize();

		    //String [] lines = new String[asize];
		    routerMasterData = new Object[aSize+4][rtrMasterColNames.length];
		    rtrMasterColorIdx = new int[aSize][rtrMasterColNames.length];
		    routerData = new Object[aSize+4][rtrNumCols];
		    ammaFlags = new boolean[aSize];

		} catch (Exception e) {
		    Debug.out("error processing Router DB structures.\n" + e.getMessage());
		}

		if (localDebug)
		    Debug.out("aSize = " + aSize);

		// This initial version assumes that there is only a single router for each
	        // file system. When configurations exist, that have multiple routers per
		// file system, this method will need to be modified to extract data for
	        // each and the GUI will require enhancements to handle the display of each.
	        // 12/21/2006  P. Spencer

		try {
		    Float fVal;
		    Long lVal;

		    for (int i = 0; i < rtrDBData.getSize(); i++) {
			routerMasterData[i][0] = rtrDBData.getRouterName(i);

			fVal = rtrDBData.getRate(i);
			if (fVal == null)
			    routerMasterData[i][1] = null;  //new Float(0.0);
			else
			    routerMasterData[i][1] = fVal;

			fVal = rtrDBData.getPctCpu(i);
			if (fVal == null)
			    routerMasterData[i][2] = null;  //new Float(0.0);  //new Double(0.0);
			else
			    routerMasterData[i][2] = fVal;  //new Double(fVal.doubleValue());

			if (test && (fVal != null)) {
			    if (i == 0)
				routerMasterData[i][2] = new Float(95.);
			    else if (i == 3)
				routerMasterData[i][2] = new Float(101.);
			}

			setCellColors(i);

			ammaFlags[i] = true;
		    }


		    //-------------------------------------------------------------------
		    // Get the Aggregate, Max, Min & Avg values for each column from DB

		    // Column 1
		    routerMasterData[aSize][0] = new String("AGGREGATE");
		    routerMasterData[aSize+1][0] = new String("MAXIMUM");
		    routerMasterData[aSize+2][0] = new String("MINIMUM");
		    routerMasterData[aSize+3][0] = new String("AVERAGE");

		    // Rate
		    routerMasterData[aSize][1] = rtrDBData.getRateSum(ammaFlags);
		    routerMasterData[aSize+1][1] = rtrDBData.getRateMax(ammaFlags);
		    routerMasterData[aSize+2][1] = rtrDBData.getRateMin(ammaFlags);
		    routerMasterData[aSize+3][1] = rtrDBData.getRateAvg(ammaFlags);

		    // Percent CPU
		    routerMasterData[aSize][2] = new String("*****");  //rtrDBData.getPctCpuSum(ammaFlags);
		    routerMasterData[aSize+1][2] = rtrDBData.getPctCpuMax(ammaFlags);
		    routerMasterData[aSize+2][2] = rtrDBData.getPctCpuMin(ammaFlags);
		    routerMasterData[aSize+3][2] = rtrDBData.getPctCpuAvg(ammaFlags);

	    //-------------------------------------------------------------------

		} catch (Exception e) {
		    Debug.out("error processing router data from DB.\n" + e.getMessage());

		    System.exit(1);
		}

		try {

		    // Sort if necessary
		    if (rtrSortCol != 0) {
			if (localDebug) {
			    System.out.println("Sort requested for column # " + rtrSortCol);
			    //printArray(routerData);
			}

			Arrays.sort(routerMasterData, new ColumnComparator(rtrMasterSortCol));

			//if (localDebug)
			    //printArray(routerData);

		    }


		    //Debug.out("\naSize = " + aSize + "\nrtrMasterColNames.length = " +
			      //rtrMasterColNames.length + "\nrouterMasterData.length = " +
			      //routerMasterData.length + "\nrouterData.length = " +
			      //routerData.length + "\n");
		    // Transfer sorted data to the array used for the data model
		    for (int i = 0; i < aSize+4; i++) {

			for (int j = 0; j < rtrMasterColNames.length; j++) {
			    //System.out.println("CCC : " + i + ", " + j + " : " + routerMasterData[i][j].toString());
			    //System.out.println("columnMapping[" + j + "] = " + columnMapping[j]);

				routerData[i][j] = routerMasterData[i][j];
			}
		    }

		} catch (Exception e) {
		    Debug.out("error building up ROUTER Object array.\n" + e.getMessage());

		    System.exit(1);
		}

		if (localDebug)
		    Debug.out("Done loading ROUTER data from DB.");

		return;

	}  // getRTRData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Scan the master router data array and apply the variable threshold values to determine
     * the color to be used for rendering the aggregate, min, max & average rows of the JTable.
     *
     * @param data row to be scanned.
     */

	public void setCellColors(int irow) {

	    boolean rowCritical = false;
	    boolean rowTroubled = false;
	    boolean rowStale = false;
	
	    try {
		for (int i = 1; i < routerMasterData[i].length; i++) {
		    float fVal = (float)0;
		    int varidx = varIds[i-1];
		    if (routerMasterData[irow][i] == null) {
			rowStale = true;
		    } if (vi[varidx].threshType == 1) {
			Object val = routerMasterData[irow][i];
			if (val instanceof Double) {
			    if (((Double)(val)).floatValue() != vi[varidx].threshVal1) {
				rowCritical = true;
			    }
			} else if (val instanceof Long) {
			    if (((Long)(val)).floatValue() != vi[varidx].threshVal1) {
				rowCritical = true;
			    }
			} else if (val instanceof Float) {
			    if (((Float)(val)).floatValue() != vi[varidx].threshVal1) {
				rowCritical = true;
			    }
			} else if (val instanceof Integer) {
			    if (((Integer)(val)).floatValue() != vi[varidx].threshVal1) {
				rowCritical = true;
			    }
			}
		    } else if (vi[varidx].threshType == 2) {
			Object val = routerMasterData[irow][i];
			if (val instanceof Double) {
			    fVal = ((Double)(val)).floatValue();
			} else if (val instanceof Long) {
			    fVal = ((Long)(val)).floatValue();
			} else if (val instanceof Float) {
			    fVal = ((Float)(val)).floatValue();
			} else if (val instanceof Integer) {
			    fVal = ((Integer)(val)).floatValue();
			}

			if ((fVal < vi[varidx].threshVal1) ||
			    (fVal > vi[varidx].threshVal2)) {
			    rowCritical = true;
			}
		    } else if (vi[varidx].threshType == 3) {
			Object val = routerMasterData[irow][i];

			if (val instanceof Double) {
			    fVal = ((Double)(val)).floatValue();
			} else if (val instanceof Long) {
			    fVal = ((Long)(val)).floatValue();
			} else if (val instanceof Float) {
			    fVal = ((Float)(val)).floatValue();
			} else if (val instanceof Integer) {
			    fVal = ((Integer)(val)).floatValue();
			}

			if (fVal >= vi[varidx].threshVal2) {
			    rowCritical = true;
			} else if (fVal >= vi[varidx].threshVal1) {
			    rowTroubled = true;
			}
		    } else if (vi[varidx].threshType == 4) {
			Object val = routerMasterData[irow][i];
			if (val instanceof Double) {
			    fVal = ((Double)(val)).floatValue();
			} else if (val instanceof Long) {
			    fVal = ((Long)(val)).floatValue();
			} else if (val instanceof Float) {
			    fVal = ((Float)(val)).floatValue();
			} else if (val instanceof Integer) {
			    fVal = ((Integer)(val)).floatValue();
			}

			if (fVal < vi[varidx].threshVal2) {
			    rowCritical = true;
			} else if (fVal < vi[varidx].threshVal1) {
			    rowTroubled = true;
			}
		    }
		}  // for (int i = 1; i < routerMasterData[i].length; i++)
		if (rowCritical) {
		    critical = true;
		} else if (rowTroubled) {
		    troubled = true;
		} else if (rowStale) {
		    stale = true;
		}

	    } catch (Exception e) {
		Debug.out("Exception caught : " + e.getMessage());
	    }

	}  // setCellColors


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Method used to load dummy data for router when DB is not available. This should
	 * never be invoked. It has been left in for historical reasons.
	 */

	void zeroLoadRTRData () {

	    final String [] aTypes = {"STRING", "FLOAT", "FLOAT"};

	    if (localDebug)
		Debug.out("Loading Failure record for display of RTR data.");
	    if (routerMasterData == null) {
		routerMasterData = new Object[5][rtrMasterColNames.length];

		for (int i = 0; i < rtrMasterColNames.length; i++) {
		    if ("STRING".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerMasterData[j][i] = "FAILURE";
		    }		    
		    if ("DOUBLE".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerMasterData[j][i] = new Double("0.0");
		    }
		    if ("FLOAT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerMasterData[j][i] = new Float("0.0");
		    }
		    if ("INTEGER".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerMasterData[j][i] = new Integer("0");
		    }
		    if ("BIGINT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerMasterData[j][i] = new Long("0");
		    }
		}
	    }

	    if (routerData == null) {
		routerData = new Object[5][rtrNumCols];

		for (int i = 0; i < rtrColNames.length; i++) {
		    if ("STRING".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerData[j][i] = "FAILURE";
		    }		    
		    if ("DOUBLE".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerData[j][i] = new Double("0.0");
		    }
		    if ("FLOAT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerData[j][i] = new Float("0.0");
		    }
		    if ("INTEGER".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerData[j][i] = new Integer("0");
		    }
		    if ("BIGINT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    routerData[j][i] = new Long("0");
		    }
		}

	    }

	}  // zeroLoadRTRData


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * OLD, out of date, fake data loading method. Left in file in case you need
	 * to add fake data capability in the future. It may be useful and it may be
	 * junk. UAYOR.
	 */

	protected void getRTRDataFake(String fsName) throws Exception 
	{
	    if (useDB) {  // Get data from mysql DB.
		System.out.println("useDB flag was used. Get ROUTER data from DB.");
		//getRTRDataFromDB(fsName);
		//System.out.println("ROUTER data loaded from DB.");
		return;
	    }

	    String [] currentColOrder;

	    rtrMasterColNames = new String[3];
	    rtrMasterColNames[0] = "Router\nName";
	    rtrMasterColNames[1] = "BW\n(MB/s)";
	    rtrMasterColNames[2] = "%\nCPU";

	    if (rtrNumCols > 0) {
		rtrColNames = new String[rtrNumCols];
	    } else {
		rtrColNames = new String[3];
		rtrColNames[0] = "Router\nName";
		rtrColNames[1] = "BW\n(MB/s)";
		rtrColNames[2] = "%\nCPU";
	    }

	    // Calculate the ordering of the columns based on the original ordering.
	    int [] columnMapping;
	    if (rtrNumCols > 0) {
		//Debug.out("rtrNumCols = " + rtrNumCols);
		currentColOrder = new String[rtrNumCols];

		/*************************
	    if (rtrDBColSeq != null)
		Debug.out("rtrDBColSeq length = " + rtrDBColSeq.length);
	    else
		Debug.out("rtrDBColSeq is NULL");
		**************************/
		columnMapping = new int[rtrDBColSeq.length];
		for (int i = 0; i < rtrNumCols; i++) {
		    //System.out.println(i);
		    currentColOrder[i] = rtrTable.getColumnName(i);
		    rtrColNames[i] = rtrTable.getColumnName(i);
		}

		//Debug.out("GHI");
		for (int i = 0; i < rtrDBColSeq.length; i++) {
		    //Debug.out("GHI " + i);
		    columnMapping[i] = -1;
		    for (int j = 0; j < rtrNumCols; j++) {
			//Debug.out("GHI " + i + ", " + j);
			if (rtrDBColSeq[i].equals(currentColOrder[j])) {
			    columnMapping[i] = j;
			    break;
			}
		    }
		}
	    } else {
		rtrNumCols = rtrMasterColNames.length;
		columnMapping = new int[rtrMasterColNames.length];
		currentColOrder = new String[rtrNumCols];
		for (int i = 0; i < rtrMasterColNames.length; i++) {
		    currentColOrder[i] = rtrColNames[i];
		    columnMapping[i] = i;
		}
	    }

	    if (localDebug) {
		String outS0 = "";
		String outS1 = "";
		for (int i = 0; i < rtrColNames.length; i++) {
		    outS0 += rtrColNames[i] + "  ";
		    outS1 += "  " + columnMapping[i] + "  ";
		}
		System.out.println("\n" + outS0 + "\n" + outS1);
	    }

	    rtrName = null;

	    if (localDebug)
		Debug.out("Get file system Router data for FS = " + fsName);

	    InputStreamReader inStrRdr = null;;
	    BufferedReader buffRdr= null;
	    try {
		FileInputStream in = new FileInputStream("router.dat");
		inStrRdr = new InputStreamReader(in);
		buffRdr = new BufferedReader(inStrRdr);
	    } catch (IOException e) {
		System.out.println("InputStream creation error.");
		System.out.println(e.getMessage());
		System.exit(0);
	    }

	    Vector v = new Vector();
	    try {
		boolean first = true;
		while (true) {
		    String line = buffRdr.readLine();
		    //System.out.println(line);
		    if (line == null)
			break;

		    if (first) {
			if (line.indexOf("name=") >= 0) {
			    String[] tokens = line.split("=");
			    rtrName = tokens[1];
			    first = false;
			} else {
			    throw new Exception("Format error in router data.");
			}
		    } else {
			v.addElement(line);
		    }
		}
	    } catch (IOException e) {
		System.out.println(e.getMessage());
		Thread.dumpStack();
		System.exit(1);
	    }
	    try {
		buffRdr.close();
	    } catch (IOException e) {
		System.out.println("InputStream close error.");
		System.out.println(e.getMessage());
	    }

	    try {
		int aSize = v.size();

		String [] lines = new String[aSize];
		routerMasterData = new Object[aSize][3];
		//Debug.out("rtrNumCols = " + rtrNumCols);
		routerData = new Object[aSize][rtrNumCols];

		v.copyInto(lines);

		for (int i = 0; i < aSize; i++) {
		    String [] vals = lines[i].split(" ");

		    routerMasterData[i][0] = new String(vals[0]);
		    routerMasterData[i][1] = new Float(Float.valueOf(vals[1]).floatValue());
		    routerMasterData[i][2] = new Float(Float.valueOf(vals[2]).floatValue());
		}

		// Sort if necessary
		if (rtrSortCol != 0) {
		    //System.out.println("Sort requested for column # " + rtrSortCol);
		    //printArray(routerData);
		    Arrays.sort(routerMasterData, new ColumnComparator(rtrMasterSortCol));
		    //printArray(routerData);
		}

		// Transfer sorted data to the array used for the data model
		for (int i = 0; i < aSize; i++) {

		    if (columnMapping[0] >= 0)
			routerData[i][columnMapping[0]] = routerMasterData[i][0];

		    if (columnMapping[1] >= 0)
			routerData[i][columnMapping[1]] = routerMasterData[i][1];

		    if (columnMapping[2] >= 0)
			routerData[i][columnMapping[2]] = routerMasterData[i][2];

		}


	    } catch (Exception e) {
		Debug.out("error processing input vector.\n" + e.getMessage());
	    }

	    if (localDebug)
		Debug.out("Done generating data for router pane.");

	}  // getRTRDataFake


    }  // RTRTablePanel


}  // RTRPanel
