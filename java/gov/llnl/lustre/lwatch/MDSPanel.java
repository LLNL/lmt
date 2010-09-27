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
 * Class used to define and build MDS JTable statistics panel for file system container.
 */

public class MDSPanel extends JPanel {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("MDSPanel.debug");

    public final static boolean useDB = true;   //Boolean.getBoolean("useDB");

    FileSystemPanel parentFS;
    String fsName;
    boolean selected;

    Database database = null;
    MdsInfo [] mdsInfo = null;

    protected String  [] mdsMasterColNames = {"Operation", "Samples",
			 "Sample\n/Sec", "Avg\nValue", "Std Dev", "Units"};
    protected static String  [] mdsPlottableVars = {"PCT_CPU", "PCT_KBYTES",
						    "PCT_INODES"};  // Used by PlotFrame2

    //JPanel mdsPane;

    MDSTablePanel [] tablePanes;

    private Timestamp mdsTimestamp;
    private JLabel mdsTimestampLabel;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for the MDSPanel.
     *
     * @param parentFS Parent FileSystemPanel object containing this MDS panel.
     *
     * @param selected true if the parent FileSystemPanel is selected.
     */

    MDSPanel(FileSystemPanel parentFS, boolean selected) {
	super();

	this.parentFS = parentFS;
	this.fsName = parentFS.getFSName();
	this.selected = selected;

	/***
	// Need a DB connection to get MDS Ids
	if (! parentFS.getDBConnected()) {
	    try {
		parentFS.openDBConnection();
	    } catch (Exception e) {
		Debug.out("Exception detected while loading current MDS Ids.\n" +
			  e.getMessage());

		Thread.dumpStack();
		return;
	    }
	    
	    if (parentFS.getDatabaseClass() == null) {
		Debug.out("Error detected while loading current MDS Ids.\n");

		Thread.dumpStack();
		return;
	    }
	}
	***/

	database = parentFS.getDatabaseClass();

	// Get the MDS Ids
	try {
	    //Debug.out("Get mdsInfo.");
	    mdsInfo = database.getMdsInfo();
	    //Debug.out("mdsInfo gotten. length = " + mdsInfo.length +
		      //"  mdsId = " + mdsInfo[0].mdsId);
	} catch (Exception e) {
	    Debug.out("Exception detected while loading current MDS Ids for " +
		      this.fsName + "\n" +
		      e.getMessage());

	    Thread.dumpStack();
	    return;
	}
	int numOfMDSs = mdsInfo.length;

	GridBagLayout gbLayout = new GridBagLayout();
	//mdsPane.setLayout(gbLayout);
	setLayout(gbLayout);
	GridBagConstraints c;

	int tpWidth = 0;
	tablePanes = new MDSTablePanel[numOfMDSs];
	for (int i = 0; i < numOfMDSs; i++) {
	    tablePanes[i] = new MDSTablePanel(i);
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = i;
	    c.insets = new Insets(2, 0, 0, 2);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 1.0;  //1.0;
	    c.weighty = 1.0;
	    gbLayout.setConstraints(tablePanes[i], c);

	    //Debug.out("Add tablePanes[" + i + "] to mdsPane.");
	    //mdsPane.add(tablePanes[i]);
	    tpWidth = Math.max(0,tablePanes[i].panelWidth);
	    add(tablePanes[i]);
	}

	setMinimumSize(new Dimension(100, 300));  //(tpWidth+5, 700));
	setMaximumSize(new Dimension(1200, 1200));  //(tpWidth+30, 700));
	setPreferredSize(new Dimension(tpWidth+5, 700));
	//setBackground(Color.GREEN);

	//setViewportView(mdsPane);
	//getViewport().setBackground(Color.black);

	setAlignmentX(LEFT_ALIGNMENT);

    }  // MDSPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return array of plottable variables associated with the MDS panel.
     */

    public static String [] getPlottableVars() {
	return mdsPlottableVars;
    }  // getPlottableVars


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return master data array associated with the MDS panel.
     */

    public  Object [][] getMasterData(int id) {
	for (int i = 0; i < tablePanes.length; i++) {
	    if (tablePanes[i].mdsId == id)
		return tablePanes[i].mdsMasterData;
	}
	Debug.out("Unable to find matching MDS id for " + id);
	return null;
    }  // getMasterData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reload the MDS Panel.
     */

    void reload() {
	if (localDebug)
	    Debug.out("Reloading MDS Panel.");

	for (int i = 0; i < tablePanes.length; i++)
	    tablePanes[i].reloadMDSTablePanel(i);

    }  // reload


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to define & build the MDS data panel. This is a subpanel of the
     * top-level MDSPanel. At this time there is only one MDS subpanel but in the
     * future there may be more than one. This will allow multiple subpanels to be
     * displayed inside the main MDS panel.
     */

    class MDSTablePanel extends JPanel {

	protected JTable mdsTable;
	private JScrollPane mdsScrollPane;
	private MDSTableModel mdsModel;
	//private Timestamp mdsTimestamp;
	//private JLabel mdsTimestampLabel;
	private int mdsSortCol = 0;
	private String mdsSortColName = null;
	private JLabel mdsLab;
	private JTextField cpuV;
	private JTextField spaceV;
	private JTextField inodeV;
	protected String mdsName;
	protected int mdsId;
	private String pcpu;
	private String pspace;
	private String pinode;
	private JPopupMenu mdsC0Popup;
	private JPopupMenu mdsPopup;
	private int mdsActionCol;
	private String [] mdsDBColSeq = null;
	private int mdsNumCols = 0;
	protected String  [] mdsColNames;
	private boolean [] mdsColOn;
	protected Object [][] mdsData = null;
	ArrayList hiddenColumns = new ArrayList(5);
	int nHiddenCols = 0;

	protected Object [][] mdsMasterData = null;
	int [] mdsColumnWidths = null; // Set in initMDSColumnSizes &
	                               // Dimensioned in buildMDS
	private int mdsMasterSortCol = 0;

	private MDSCellRenderer cellRenderer = new MDSCellRenderer();

	private NormalHeaderRenderer columnHdrRenderer =
	    new NormalHeaderRenderer();

	int panelWidth = 0;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor used to build panel for displaying MDS data.
	 *
	 * @param mdsIdx index of the MDS data to be loaded.
	 */

	MDSTablePanel(int mdsIdx) {
	    super(new BorderLayout(), false);

	    if (selected) {
		if (localDebug)
		    Debug.out("Load data for " + fsName + " MDS pane.");

		try {
		    mdsName = mdsInfo[mdsIdx].mdsName;
		    this.mdsId = mdsInfo[mdsIdx].mdsId;
		    getMDSData(database, mdsInfo[mdsIdx].mdsId);
		} catch (Exception e) {
		    Debug.out("Exception caught while loading MDS data.\n");
		    Thread.dumpStack();
		    return;
		}

		if (localDebug)
		    Debug.out("Data for " + fsName + " MDS pane has been loaded.");

		mdsColOn = new boolean[mdsColNames.length];
		for (int i = 0; i < mdsColOn.length; i++)
		    mdsColOn[i] = true;

		//String [] ops = new String[35];
		//for (int j = 0; j < ops.length; j++) {
		    //ops[j] = "mds_op-" + j;
		//}
	    } else {
		if (localDebug)
		    Debug.out("Load data for unselected FS pane " + fsName);

		mdsColNames = new String[mdsMasterColNames.length];
		mdsData = new Object[1][mdsMasterColNames.length];
		mdsColOn = new boolean[mdsMasterColNames.length];

		for (int i = 0; i < mdsMasterColNames.length; i++) {
		    mdsColNames[i] = mdsMasterColNames[i];

		    if (i == 0)
			mdsData[0][0] = new String("mds_getattr_lock");
		    else
			mdsData[0][i] = new String("123456");

		    mdsColOn[i] = true;
		}
	    }

	    // The following block of code is used to initialize the number of
	    // columns and the order of the columns as retrieved from the DB.
	    // As the columns are reordered and deleted, these original values
	    // can be used as a reference to calculate the order in which
	    // the TableModel data array should be loaded.  11/28/2006
	    if (mdsDBColSeq == null) {
		mdsNumCols = mdsColNames.length;
		mdsDBColSeq = new String[mdsNumCols];
		for (int i = 0; i < mdsNumCols; i++) {
		    //Debug.out(i + "  : " + mdsColNames[i]);
		    mdsDBColSeq[i] = mdsColNames[i];
		}
	    }

	    JPanel hdrPane = new JPanel(new BorderLayout(), false);

	    mdsLab = new JLabel(mdsName + "    " + mdsTimestamp.toString());
	    mdsLab.setHorizontalAlignment(JLabel.CENTER);
	    hdrPane.add(mdsLab, BorderLayout.NORTH);

	    JPanel trioVals = new JPanel(new GridLayout(2,3), false);
	    JLabel cpuLabel = new JLabel("%CPU");
	    trioVals.setBackground(Color.black);
	    cpuLabel.setForeground(Color.white);
	    Color textBackgroundColor = new Color(253, 232, 209);
	    cpuV = new JTextField(" ");
	    MdsVarListener mdsVarListener = new MdsVarListener(parentFS, fsName,
							       mdsId, mdsName, "PCT_CPU");
	    cpuV.addMouseListener(mdsVarListener);
	    cpuV.setEditable(false);
	    cpuV.setBackground(textBackgroundColor);

	    JLabel spaceLabel = new JLabel("%KB");
	    spaceLabel.setForeground(Color.white);
	    spaceV = new JTextField(" ");
	    mdsVarListener = new MdsVarListener(parentFS, fsName,
						mdsId, mdsName, "PCT_KBYTES");
	    spaceV.addMouseListener(mdsVarListener);
	    spaceV.setEditable(false);
	    spaceV.setBackground(textBackgroundColor);

	    JLabel inodeLabel = new JLabel("%Inodes");
	    inodeLabel.setForeground(Color.white);
	    inodeV = new JTextField(" ");
	    mdsVarListener = new MdsVarListener(parentFS, fsName,
						mdsId, mdsName, "PCT_INODES");
	    inodeV.addMouseListener(mdsVarListener);
	    inodeV.setEditable(false);
	    inodeV.setBackground(textBackgroundColor);

	    trioVals.add(cpuLabel);
	    trioVals.add(spaceLabel);
	    trioVals.add(inodeLabel);
	    trioVals.add(cpuV);
	    trioVals.add(spaceV);
	    trioVals.add(inodeV);
	    hdrPane.setBackground(new Color(255,151,65));  //(Color.yellow);

	    hdrPane.add(trioVals, BorderLayout.CENTER);

	    add(hdrPane, BorderLayout.NORTH);


	    // Create the data model for the MDS table

	    if (localDebug)
		Debug.out("Create table model object.");

	    mdsModel = mdsTableModelCreate(mdsColNames);

	    if (localDebug)
		System.out.println("mdsTable row & col count = " +
		    mdsModel.getRowCount() + " , " + mdsModel.getColumnCount());
	    if (localDebug) {
		Debug.out("Model created.");
		Debug.out("Create JTable object for FS # " + fsName);
	    }

	    mdsTable = new JTable(mdsModel);
	    mdsTable.setDefaultRenderer(Object.class, cellRenderer);

	    CellListener cellListener = new CellListener("MDS", this.mdsId,
							 mdsTable, database,
							 parentFS, mdsMasterData);
	    mdsTable.addMouseListener(cellListener);

	    JTableHeader tableHeader = mdsTable.getTableHeader();
	    tableHeader.addMouseListener(new MDSTableHdrListener(parentFS, mdsTable));

	    // Define the popup menu for the column 0 in this JTable
	    mdsC0Popup = new JPopupMenu();
	    JMenuItem menuItem = new JMenuItem("Reset");
	    menuItem.addActionListener(new MDSPopupListener(mdsTable));
	    mdsC0Popup.add(menuItem);

	    mdsC0Popup.addSeparator();

	    // Define the popup menu for the other columns in this JTable
	    mdsPopup = new JPopupMenu();
	    menuItem = new JMenuItem("Hide");
	    menuItem.addActionListener(new MDSPopupListener(mdsTable));
	    mdsPopup.add(menuItem);

	    menuItem = new JMenuItem("Reset");
	    menuItem.addActionListener(new MDSPopupListener(mdsTable));
	    mdsPopup.add(menuItem);

	    mdsPopup.addSeparator();

	    if (localDebug)
		System.out.println("Set multiline Hdr renderer for mds table.");

	    Enumeration e = mdsTable.getColumnModel().getColumns();
	    while (e.hasMoreElements()) {
		//Debug.out("Set multi line header renderer.");
		((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);
	    }
	    mdsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

	    if (localDebug)
		Debug.out("Create Scroll pane for view into JTable.");

	    mdsColumnWidths = new int[mdsMasterColNames.length];
	    initMDSColumnSizes(mdsTable, mdsModel);

	    // Need to wrap JTable in JScrollPane or header doesn't show.
	    mdsScrollPane = new JScrollPane(mdsTable);
	    mdsScrollPane.getViewport().setBackground(Color.black);
	    mdsScrollPane.setAlignmentX(LEFT_ALIGNMENT);
	    //mdsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
	    //mdsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

	    //panelWidth = 0;
	    //for (int i = 0; i < mdsColumnWidths.length; i++) {
		//Debug.out("MDS column " + i + " width = " + mdsColumnWidths[i]);
		//panelWidth += mdsColumnWidths[i];
	    //}

	    //Debug.out("MDS Panel widths sum to " + panelWidth);
	    //if (panelWidth == 0)
	    panelWidth = 355;  //345;		

	    setMinimumSize(new Dimension(100, 300));  //(panelWidth, 700));  //(405, 700));
	    //setMaximumSize(new Dimension(panelWidth+50, 700));
	    setPreferredSize(new Dimension(panelWidth, 700));
	    setAlignmentX(LEFT_ALIGNMENT);

	    add(mdsScrollPane, BorderLayout.CENTER);

	    // Suppress auto-create of columns from data model now that they're established.
	    mdsTable.setAutoCreateColumnsFromModel(false);

	    if (localDebug)
		Debug.out("Done building MDS panel.");

	}  //  MDSTablePanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class implementing the mouse listener used to activate the history plot package.
	 */

	public class MdsVarListener extends MouseAdapter {
	    private long lastClickTime = 0;

	    protected String dataType = "MDS";
	    protected int id;
	    protected FileSystemPanel fileSys;
	    protected String fsName;
	    protected String mdsName;
	    protected String varName;


	    /**
	     * Constructor for the mouse listener used to activate the history plot package.
	     *
	     * @param fsp FileSystemPanel containing this MDS panel.
	     *
	     * @param fsname file system name
	     *
	     * @param mdsid mds index.
	     *
	     * @param mdsname mds name.
	     *
	     * @param varname variable name.
	     */

	    MdsVarListener(FileSystemPanel fsp, String fsname, int mdsId,
				       String mdsname, String varname) {
		this.fileSys = fsp;
		this.fsName = fsname;
		this.id = mdsId;
		this.mdsName = mdsname;
		this.varName = varname;
	    }  // MdsVarListener


	    /**
	     * Mouse clicked handler method.
	     *
	     * @param e MouseEvent causing handler to be called.
	     */

	    public void mouseClicked(MouseEvent e) {
		long currentClickTime = e.getWhen();
		long clickInterval = currentClickTime-lastClickTime;
		lastClickTime = currentClickTime;

		if (SwingUtilities.isLeftMouseButton(e) &&
		    clickInterval< 250) {  //Prefs.DOUBLE_CLICK_INTERVAL.getValue()) {
		    final JFrame f = new JFrame(this.fsName + " Time History plot");

		    f.addWindowListener(new WindowAdapter() {
			    public void windowClosing(WindowEvent e) {
				//Debug.out("[windowClosing]");

				f.dispose();
			    }
			});
		    PlotFrame2 pf = new PlotFrame2(f,
						   this.fileSys,
						   database,
						   "MDS",
						   this.id,
						   this.mdsName,
						   this.varName,
						   this.fileSys.parent.prefs,
						   false);
		    pf.buildUI(f.getContentPane());
		    f.pack();
		    f.setVisible(true);

		    if (pf.updateLive) {
			pf.refreshPlotFrame();
		    }
		}
	    }  // mouseClicked
	}  // MdsVarListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Reload the MDS table panel.
	 *
	 * @param mdsIdx index of the MDS data to reload.
	 */

	void reloadMDSTablePanel(int mdsIdx) {
	    if (localDebug)
		Debug.out("Reloading MDS Table Panel.");

	    try {
		getMDSData(database, mdsInfo[mdsIdx].mdsId);
	    } catch (Exception e) {
		Debug.out("Exception caught while loading MDS data.\n");
		e.printStackTrace();
		return;
	    }

	    if (localDebug)
		Debug.out("Reload MDS data vector row count = " + mdsData.length);

	    synchronized(MDSPanel.this.parentFS.synchObj) {
		mdsModel.setDataVector(mdsData, mdsMasterColNames);  //mdsColNames);
	    }

	    if (localDebug)
		Debug.out("MDS setDataVector completed.");

	    Enumeration e = mdsTable.getColumnModel().getColumns();
	    int icnt = 0;
	    while (e.hasMoreElements()) {
		if (localDebug)
		    Debug.out("Column ." + icnt++);
		//((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);

		TableColumn tc = ((TableColumn) e.nextElement());
		tc.setHeaderRenderer(columnHdrRenderer);

		if (mdsColumnWidths != null) {
		    String hdrString = (String)tc.getHeaderValue();
		    int width = 58;
		    for (int i = 0; i < mdsMasterColNames.length; i++) {
			if ((hdrString != null) && hdrString.equals(mdsMasterColNames[i])) {
			    width = mdsColumnWidths[i];
			    break;
			}
		    }
		    tc.setPreferredWidth(width);
		}
	    }

	    if (localDebug)
		Debug.out("MDS set HeaderRenderer for all columns completed.");

	    try {
		//Debug.out("mdsName ");
		//Debug.out("mdsName " + mdsName);
		mdsLab.setText(mdsName + "    " + mdsTimestamp.toString());
		//Debug.out("pcpu ");
		//Debug.out("pcpu " + pcpu);
		cpuV.setText(pcpu);
		//Debug.out("pspace ");
		//Debug.out("pspace " + pspace);
		spaceV.setText(pspace);
		//Debug.out("pinode ");
		//Debug.out("pinode " + pinode);
		inodeV.setText(pinode);
	    } catch (Exception ex) {
		Debug.out("Exception detected. " + ex.getMessage());
	    }

	}  // reloadMDSTablePanel


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
	    }


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
			     boolean isSelected, boolean hasFocus, int row, int column)
	    {
		setFont(table.getFont());
		String str = (value == null) ? "" : value.toString();
		setToolTipText(str);
		setFont(new Font("helvetica", Font.BOLD, 10));
		    
		if (localDebug && (table.getModel() instanceof MDSTableModel))
		    Debug.out("row = " + row + ",  column = " + column);

		try {
		    if ((mdsSortCol != 0) &&
			mdsTable.getColumnName(column).equals(mdsSortColName)) {
			//mdsColNames[Math.abs(mdsSortCol)-1])) {

			//System.out.println("\nMDS Sorting on column " + mdsSortCol + "\n");
			if (mdsSortCol > 0)
			    str += " ^";
			else
			    str += " v";
		    }
		} catch (Exception e) {

		    Debug.out("Exception caught while rendering header for MDS " +
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
	    }

	}


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class implementing the renderer for the MDS table column headers.
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
	     * Get the renderer component for the MDS table column header.
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
		    if ((mdsSortCol != 0) &&
			mdsTable.getColumnName(column).equals(mdsSortColName)) {
			
			if (mdsSortCol > 0) {
			    str += " ^";
			} else {
			    str += " v";
			}

		    }
		} catch (Exception e) {
		    Debug.out("Exception caught while rendering header for MDS table. Row = " +
			      row + ",  Column = " + column + ",  value = " +
			      value + "\n" + e.getMessage());
		}

		if (usehtml)
		    str += "</b></html>";

		setText(str);

		return this;
	    }

	}  // getTableCellRendererComponent


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Initailize the column widths for the MDS table.
	 *
	 * @param table JTable containing MDS data.
	 *
	 * @param model model for the MDS JTable.
	 */

	public void initMDSColumnSizes(JTable table, MDSTableModel model) {
	    int rowCount = model.getRowCount();
	    int colCount = model.getColumnCount();
	    int [] colWidth = new int [colCount];
	    TableColumn column = null;
	    Component comp = null;

	    if (localDebug)
		System.out.println("Calculate Column widths for MDS table.");

	    for (int i = 0; i < colCount; i++) {
		int cellWidth = 0;
		column = table.getColumnModel().getColumn(i);
		String tmpS = model.getColumnName(i);
		if (tmpS.indexOf("\n") >= 0)
		    tmpS = tmpS.substring(0,tmpS.indexOf("\n"));

		comp = table.getDefaultRenderer(model.getColumnClass(i)).
		    getTableCellRendererComponent(table, tmpS, false, false, 0, i);
		cellWidth = comp.getPreferredSize().width + 10;
		for (int j = 0; j < rowCount; j++) {

		    comp = table.getDefaultRenderer(model.getColumnClass(i)).
			getTableCellRendererComponent(table, model.getValueAt(j, i),
						      false, false, j, i);

		    cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
		}
		if (localDebug)
		    System.out.println("Col " + i + " Max Width = " + cellWidth);

		column.setPreferredWidth(cellWidth+10);
		//Debug.out("Assign " + cellWidth + " +10 to mdsColumnWidths[" + i + "]");
		mdsColumnWidths[i] = cellWidth+10;
	    }	       

	}  // initMDSColumnSizes


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Call the constructor for the MDS table model.
	 *
	 * @param columnNames array of column names to appear in the table.
	 *
	 * @return table model for the MDS table.
	 */

	private MDSTableModel mdsTableModelCreate(String [] columnNames) {

	    return new MDSTableModel(columnNames);

	}  // mdsTableModelCreate


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class to define the table model for the MDS data. Extends AbstractTableModel.
	 */

	// Subclass DefaultableModel so that cells can be set to uneditable.
	public class MDSTableModel extends AbstractTableModel {

	    public String [] columnNames = null;
	    public Object [][] data = null;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constrcutor for the MDS table model.
	     *
	     * @param columnNames array of column names to appear in the table.
	     */

	    protected MDSTableModel(String [] columnNames) {

		this.data = mdsData;
		this.columnNames = columnNames;

		if (localDebug) {
		    Debug.out("# of column names = " + this.columnNames.length);
		    for (int i = 0; i < columnNames.length; i++)
			System.out.println("column " + i + " name = " + this.columnNames[i]);
		}
	    }  // MDSTableModel


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
		    //Debug.out("ArrayIndexOutOfBoundsException : row = " + row +
			      //"  col = " + col);
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
	     * Set the data vector for the MDS table.
	     *
	     * @param idata Array containing the values for the MDS table cells.
	     *
	     * @param colNames array containing the names of the columns for the MDS table.
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
		synchronized(MDSPanel.this.parentFS.synchObj) {
		    Arrays.sort(data, new ColumnComparator(sortCol));
		    fireTableChanged(new TableModelEvent(this, 0, data.length));
		}
	    }  // sort

	}  // MDSTableModel


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
	 * Class that handles the MDS table header mouse button clicks.
	 */

	public class MDSTableHdrListener extends MouseAdapter {

	    private FileSystemPanel parent;
	    private JTable table;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for the class that handles the MDS table header mouse button clicks.
	     *
	     * @param parent the FileSystem object to which this MDSPanel belongs.
	     *
	     * @param table the JTable containing the MDS data.
	     */

	    public MDSTableHdrListener(FileSystemPanel parent, JTable table) {
		super();

		this.parent = parent;
		this.table = table;
	    } // MDSTableHdrListener


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Handler for mouse button release events in the MDS table column header.
	     *
	     * @param e the MouseEvent causing the handler to be invoked.
	     */

	    public void mouseReleased(MouseEvent e) {
		TableColumnModel columnModel = table.getColumnModel();
		int viewColumn = columnModel.getColumnIndexAtX(e.getX());
		int buttNum = e.getButton();

		//Debug.out("mouseReleased : col = " + viewColumn +
		    //",  button # " + buttNum);

	        int colCount = mdsTable.getColumnCount();
		for (int j = 0; j < colCount; j++) {
		    TableColumn tc = columnModel.getColumn(j);
		    int colWidth = tc.getWidth();
		    String cName = mdsTable.getColumnName(j);
		    for (int i = 0; i < mdsMasterColNames.length; i++) {
			if (mdsMasterColNames[i].equals(cName)) {
			    mdsColumnWidths[i] = colWidth;
			    //System.out.println("MDS column " + cName +
			        //" width set to " + colWidth);
			    break;
			}
		    }
		}

		if ((buttNum == 3) && (viewColumn != 0)) {
		    if (localDebug)
			System.out.println(
			    "MDS Right mouse button Release detected in col " +
			    viewColumn);

		    mdsActionCol = viewColumn;

		    if (viewColumn != 0) {
			showPopup(e);
		    } else {
			showC0Popup(e);
		    }
		}
	    }  // mouseReleased


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Handler for mouse button click events in the MDS table column header.
	     *
	     * @param e the MouseEvent causing the handler to be invoked.
	     */

	    public void mouseClicked(MouseEvent e) {

		TableColumnModel columnModel = table.getColumnModel();
		int viewColumn = columnModel.getColumnIndexAtX(e.getX());
		int buttNum = e.getButton();
		if ((viewColumn > mdsNumCols) || (viewColumn < 0))
		    return;

		int dataModelColindx = toModel(table, viewColumn);
		if (dataModelColindx == -1)
		    return;

		if (localDebug)
		    System.out.println("Mouse Click event detected in MDS Table column " +
				       viewColumn + "\nButton # " + buttNum);

		if (buttNum == 1) {
		    String sortID = mdsTable.getColumnName(viewColumn);
		    String lastSortColName = mdsSortColName;
		    int lastSortDir = 1;
		    if (mdsMasterSortCol < 0)
			lastSortDir = -1;
		    mdsSortColName = sortID;
		    int sortColumn = 0;
		    for (int i = 0; i < mdsColNames.length; i++) {
			if (mdsColNames[i].equals(sortID)) {
			    //Debug.out("\nsortID " + sortID + " <==> mdsColNames[" +
			        //i + "] " + mdsColNames[i]);
			    sortColumn = i;  //viewColumn;
			    //System.out.println("Sorting on column  " + i + " - " +
			        //sortID);
			    break;
			}
		    }
		    mdsSortCol = sortColumn + 1;
		    if ((lastSortColName != null) &&
			(lastSortColName.equals(mdsSortColName)))
			mdsSortCol = mdsSortCol * (-lastSortDir);

		    //System.out.println("Last Sort Col = " + lastSortColName +
		        //"  new Sort Col = " + mdsSortColName +
		        //"  last sort dir = " + lastSortDir);
		    if (localDebug)
			System.out.println("MDS sort : " + mdsSortCol + " = " +
					   mdsColNames[Math.abs(mdsSortCol)-1]);

		    // Calculate the column from the master data array
		    sortColumn = 0;
		    for (int i = 0; i < mdsMasterColNames.length; i++) {
			if (mdsMasterColNames[i].equals(sortID)) {
			    sortColumn = i;
			    //System.out.println("Set sorting for Master column  " +
			        //i + " - " + sortID);
			    break;
			}
		    }
		    mdsMasterSortCol = sortColumn + 1;
		    if (mdsSortCol < 0)
			mdsMasterSortCol = -mdsMasterSortCol;

		    if (localDebug)
			System.out.println("MDS Master sort : " + mdsMasterSortCol +
			    " = " + mdsMasterColNames[Math.abs(mdsMasterSortCol)-1]);

		    // Sort on selected row
		    if (mdsSortCol != 0) {
			//parentFS.getParentOf().postponeRefresh(1000); //  msecs
			((MDSTableModel)this.table.getModel()).sort(mdsSortCol);
			table.getTableHeader().repaint();
		    }
		} else if (buttNum == 3) {
		    if (localDebug)
			System.out.println("MDS Right mouse button click detected in col " +
					   viewColumn);

		    mdsActionCol = dataModelColindx;

		    if (dataModelColindx != 0) {
			showPopup(e);
		    } else {
			showC0Popup(e);
		    }
		}
	    }  // mouseClicked


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Display the MDS table column > 0 popup menu.
	     */

	    private void showPopup(MouseEvent e) {

		mdsPopup.show(e.getComponent(), e.getX(), e.getY());

	    }  // showPopup


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Display the MDS table column 0 popup menu.
	     */
	
	    private void showC0Popup(MouseEvent e) {

		mdsC0Popup.show(e.getComponent(), e.getX(), e.getY());

	    }  // showC0Popup
	
	} // MDSTableHdrListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class that implements the ActionListener for the MDS JTable
	 */

	public class MDSPopupListener implements ActionListener {

	    private JTable table;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for the ActionListener being implemented.
	     */

	    public MDSPopupListener(JTable table) {
		super();

		this.table = table;

	    }  // MDSPopupListener


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * The action handler for the MDS table button clicks.
	     *
	     * @param e the ActionEvent that the listener is notified of.
	     */

	    public void actionPerformed(ActionEvent e) {

		parentFS.getParentOf().resetRefresh();

		if (localDebug)
		    System.out.println("ActionEvent detected in MDS popup \n" +
				       e.getActionCommand() + " on Column # " +
				       mdsActionCol);
		if (e.getActionCommand().equals("Hide")) {
		    // Get column name and use this to identify the column number to be
		    // deleted. That is the index in mdsColOn to set to false. The value
		    // in mdsActionCol is the one to use for the mdsTable calls.

		    String cName = mdsTable.getColumnName(mdsActionCol);
		    for (int i = 0; i < mdsColNames.length; i++) {
			if (mdsColNames[i].equals(cName)) {
			    mdsColOn[i] = false;
			    if (localDebug)
				System.out.println("Setting orig column # " + i +
						   " to false.");
			    break;
			}
		    }
		    TableColumnModel mdsTableColModel = mdsTable.getColumnModel();
		    TableColumn hideCol = mdsTableColModel.getColumn(mdsActionCol);


		    // Add removed column to hiddenColumns ArrayList
		    hiddenColumns.ensureCapacity(nHiddenCols+1);
		    hiddenColumns.add(nHiddenCols++, hideCol);



		    if (localDebug)
			Debug.out("Removing column to removeColumn # " + mdsActionCol);

		    mdsTableColModel.removeColumn(hideCol);
		    mdsNumCols--;

		    JMenuItem menuItem = new JMenuItem("Restore " + cName);
		    menuItem.addActionListener(new MDSPopupListener(mdsTable));
		    mdsPopup.add(menuItem);

		    // Need to create a new menuItem for the columnn 0 menu rather
		    // than reusing the previous one. If reused, item only appears
		    // in a single menu. (The last one defined).  ** 12/7/06  Paul **
		    menuItem = new JMenuItem("Restore " + cName);
		    menuItem.addActionListener(new MDSPopupListener(mdsTable));
		    mdsC0Popup.add(menuItem);

		} else if(e.getActionCommand().startsWith("Restore ")) {
		    String command = e.getActionCommand();
		    //Debug.out("Restore menuItem selected." + command);
		    String restoreColName = command.substring(8);

		    // Add the column back into the table to the right of column
		    // where the action took place.
		    //Debug.out("Restoring columnn " + restoreColName + " at column # " +
		        //mdsActionCol);
		    int restoreCol = 0;
		    while (!mdsMasterColNames[restoreCol].equals(restoreColName))
			restoreCol++;

		    if (restoreCol >= mdsMasterColNames.length) {
			Debug.out("Error matching column names for restoration.");
			return;
		    }
		    //Debug.out("Restoring column " + mdsMasterColNames[restoreCol]);

		    // Locate coulmn to be added back in the hiddenColumns ArrayList
		    ListIterator it = hiddenColumns.listIterator();

		    while (it.hasNext()) {
			TableColumn column = (TableColumn)it.next();
			if (((String)(column.getHeaderValue())).equals(restoreColName)) {
			    mdsTable.getColumnModel().addColumn(column);
			    it.remove();
			    nHiddenCols--;
			    break;
			}
		    }

		    mdsModel.fireTableDataChanged();

		    mdsColOn[restoreCol] = true;
		    mdsNumCols++;


		    // Remove restore menu item from  Popup menu.
		    int cCount = mdsPopup.getComponentCount();
		    for (int i = 3; i < cCount; i++) {
			Component comp = mdsPopup.getComponent(i);
			String compText = ((AbstractButton)comp).getText();
			//System.out.println("Component # " + i + " = " + compText);
			if (command.equals(((AbstractButton)comp).getText())) {
			    mdsPopup.remove(i);
			    break;
			}
		    }
		    cCount = mdsC0Popup.getComponentCount();
		    for (int i = 2; i < cCount; i++) {
			Component comp = mdsC0Popup.getComponent(i);
			String compText = ((AbstractButton)comp).getText();
			//System.out.println("C0 Component # " + i + " = " + compText);
			if (command.equals(((AbstractButton)comp).getText())) {
			    mdsC0Popup.remove(i);
			    break;
			}
		    }

		} else if(e.getActionCommand().equals("Reset")) {

		    // Locate coulmn to be added back in the hiddenColumns ArrayList
		    ListIterator it = hiddenColumns.listIterator();

		    while (it.hasNext()) {
			TableColumn column = (TableColumn)it.next();
			mdsTable.getColumnModel().addColumn(column);
			it.remove();
			nHiddenCols--;
		    }

		    for (int i = 0; i < mdsColOn.length; i++)
			mdsColOn[i] = true;

		    mdsNumCols = mdsMasterColNames.length;

		    // Move TableColumns to the original order.
		    for (int i = 0; i < mdsMasterColNames.length; i++) {
			int istrt = i;
			for (int j = istrt; j < mdsMasterColNames.length; j++) {
			    String tcn = mdsTable.getColumnName(j);
			    if (tcn.equals(mdsMasterColNames[i])) {
				mdsTable.moveColumn(j, i);
			    }
			}
		    }

		    if (localDebug)
			Debug.out("Remove menu items from popup menus.");
		    // Remove JPopup menuitems for the restored columns.
		    int cCount = mdsPopup.getComponentCount();
		    for (int i = 3; i < cCount; i++) {
			Component comp = mdsPopup.getComponent(3);
			if (((AbstractButton)comp).getText().startsWith("Restore "))
			    mdsPopup.remove(3);
		    }

		    if (localDebug)
			Debug.out("Remove menu items from C0 popup menu.");
		    // Remove JPopup menuitems for the restored columns.
		    cCount = mdsC0Popup.getComponentCount();
		    for (int i = 2; i < cCount; i++) {
			Component comp = mdsC0Popup.getComponent(2);
			if (((AbstractButton)comp).getText().startsWith("Restore "))
			    mdsC0Popup.remove(2);
		    }

		    mdsModel.fireTableDataChanged();

		}
	    } // actionPerformed

	}  // MDSPopupListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class used to render the MDS table cells.
	 */

	class MDSCellRenderer extends DefaultTableCellRenderer {

	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for cell renderer for MDS table.
	     */

	    MDSCellRenderer() {
		setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

	    }  // MDSCellRenderer


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
							   Object value, boolean isSelected, boolean hasFocus, int row,
							   int column) {

		final Color lGreen = new Color(150, 200, 150);
		final Color orange = new Color(220, 180, 100);
		final int width = 4;
		String col0Name = table.getColumnName(0);

		int rowCnt = table.getRowCount();

		if (value == null) {
		    setBackground(Color.black);
		    setIcon(null);
		    setText(null);
		} else {
		    setBackground(Color.black);
		    setForeground(Color.white);
		}
	    
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

	}  // MDSCellRenderer


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Load MDS data from database for most recent timestamp.
	 *
	 * @param database Database object used to load data with.
	 *
	 * @param mdsId the MDS identifier.
	 */


	protected void getMDSData(Database database, int mdsId) throws Exception 
	{

	    if (localDebug) {
		Debug.out("Entering getMDSDataFromDB");
		Debug.out("Get data for latest timestamp.");
	    }

	    //
	    // Get latest TS_ID from TIMESTAMP_ID table.
	    //

	    TimeInfo timeinfo = database.getLatestTimeInfo("MDS_DATA");
	    mdsTimestamp = timeinfo.timestamp;

	    //mdsTimestamp = new Timestamp(new java.util.Date().getTime());

	    if (localDebug)
		Debug.out("timestamp = " + mdsTimestamp);



	    //
	    // Get MDS data for latest timestamp.
	    //
	    // This initial version assumes that there is only a single mds for each
	    // file system. When configurations exist, that have multiple mds per
	    // file system, this method will need to be modified to extract data for
	    // each and the GUI will require enhancements to handle the display of each.
	    // 12/21/2006  P. Spencer

	    if (localDebug)
		Debug.out("Get MDS data from DB");

	    //MdsInfo [] mdsInfo = null;
	    MdsOpsData mdsOpsData = null;
	    MdsData mdsDBData = null;
	    try {
		//Debug.out("Get mdsInfo.");
		//mdsInfo = database.getMdsInfo();
		//Debug.out("mdsInfo gotten. length = " + mdsInfo.length +
		    //"  mdsId = " + mdsInfo[0].mdsId);
		//mdsOpsData = new MdsOpsData[mdsInfo.length];
		for (int i = 0; i < mdsInfo.length; i++) {
		    mdsOpsData = database.getCurrentMdsOpsData(mdsId);
		    mdsDBData = database.getCurrentMdsData(mdsId);

		    if (mdsOpsData == null)
			throw new Exception("null return from getCurrentMdsOpsData() for " +
					    "mdsId = " + mdsId);

		    if (mdsDBData == null)
			throw new Exception("null return from getCurrentMdsData for " +
					    "mdId = " + mdsId);

		    Float pcpuFloat = mdsDBData.getPctCpu(0);
		    pcpu = TextFormatter.format(pcpuFloat, 6);

		    Float pspaceFloat = mdsDBData.getPctKbytes(0);
		    pspace = TextFormatter.format(pspaceFloat, 6);

		    Float pinodeFloat = mdsDBData.getPctInodes(0);
		    pinode = TextFormatter.format(pinodeFloat, 6);
		}
	    } catch (Exception e) {
		Debug.out("Exception detected while loading current MDS data.\n" +
			  e); //.getMessage());
		e.printStackTrace();

		zeroLoadMDSData();
		return;
	    }

	    //
	    // Get column headers.
	    //

	    String [] mdsColTypes = {"STRING", "BIGINT", "FLOAT",
				     "FLOAT", "FLOAT", "STRING"};

	    if (localDebug)
		Debug.out("Check the current column order.");

	    mdsColNames = new String[mdsMasterColNames.length];
	    for (int i = 0; i < mdsMasterColNames.length; i++)
		mdsColNames[i] = mdsMasterColNames[i];

	    mdsNumCols = mdsMasterColNames.length;

	    int aSize = 0;
	    try {
		aSize = mdsOpsData.getSize();

		//mdsMasterData = new Object[aSize+4][mdsMasterColNames.length];
		//mdsData = new Object[aSize+4][mdsNumCols];
		mdsMasterData = new Object[aSize][mdsMasterColNames.length];
		mdsData = new Object[aSize][mdsNumCols];

	    } catch (Exception e) {
		Debug.out("error processing input vector.\n" + e.getMessage());
	    }

	    if (localDebug)
		Debug.out("aSize = " + aSize);

	    // This initial version assumes that there is only a single mds for each
	    // file system. When configurations exist, that have multiple mds per
	    // file system, this method will need to be modified to extract data for
	    // each and the GUI will require enhancements to handle the display of each.
	    // 12/21/2006  P. Spencer

	    try {
		Float fVal;
		Long lVal;
		for (int i = 0; i < aSize; i++) {
		    //System.out.println("i = " + i);
		    mdsMasterData[i][0] = mdsOpsData.getOpName(i);
		    lVal = mdsOpsData.getSamples(i);
		    if (lVal == null)
			mdsMasterData[i][1] = new Long(0);
		    else
			mdsMasterData[i][1] = mdsOpsData.getSamples(i);  //lVal;

		    fVal = mdsOpsData.getSamplesPerSec(i);
		    if (fVal == null)
			mdsMasterData[i][2] = new Float(0.0);  //new Double(0.0);
		    else
			mdsMasterData[i][2] = fVal;  //new Double(fVal.doubleValue());

		    fVal = mdsOpsData.getAvgVal(i);
		    if (fVal == null)
			mdsMasterData[i][3] = new Float(0.0);  //new Double(0.0);
		    else
			mdsMasterData[i][3] = fVal;  //new Double(fVal.doubleValue());

		    fVal = mdsOpsData.getStdDev(i);
		    if (fVal == null)
			mdsMasterData[i][4] = new Float(0.0);  //new Double(0.0);
		    else
			mdsMasterData[i][4] = fVal;  //new Double(fVal.doubleValue());

		    String units = mdsOpsData.getUnits(i);
		    if (units == null)
			mdsMasterData[i][5] = "UNKNOWN";
		    else
			mdsMasterData[i][5] = units;


		    //if (localDebug)
		        //System.out.println(ostNames[i] + " : " + reads[i] + " : " +
		            //writes[i] + " : " + cpuPercs[i] + " : " + spacePercs[i]);
		}

	    } catch (Exception e) {
		Debug.out("error processing data from DB.\n" + e.getMessage());

		System.exit(1);
	    }

	    try {
		// Sort if necessary
		if (mdsSortCol != 0) {
		    if (localDebug) {
			System.out.println("Sort requested for column # " + mdsSortCol);
			//printArray(mdsData);
		    }

		    Arrays.sort(mdsMasterData, new ColumnComparator(mdsMasterSortCol));

		    //if (localDebug)
		        //printArray(mdsData);

		}

		// Transfer sorted data to the array used for the data model
		for (int i = 0; i < aSize; i++) {

		    for (int j = 0; j < mdsMasterColNames.length; j++) {
			//System.out.println("CCC : " + i + ", " + j + " : " + mdsMasterData[i][j].toString());

			mdsData[i][j] = mdsMasterData[i][j];
		    }
		}

	    } catch (Exception e) {
		Debug.out("error building up MDS 2-D Object array.\n" + e.getMessage());
		System.exit(1);
	    }

	    if (localDebug)
		Debug.out("Done loading MDS data from DB.");

	    return;
	}  // getMDSData


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Method used to load dummy data for MDS when DB is not available. This should
	 * never be invoked. It has been left in for historical reasons.
	 */

	void zeroLoadMDSData () {
	    final String [] aTypes = {"STRING", "BIGINT", "DOUBLE", "DOUBLE",
				      "DOUBLE", "STRING"};

	    if (mdsMasterData == null) {
		mdsMasterData = new Object[5][mdsMasterColNames.length];

		for (int i = 0; i < mdsMasterColNames.length; i++) {
		    if ("STRING".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsMasterData[j][i] = "FAILURE";
		    }		    
		    if ("DOUBLE".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsMasterData[j][i] = new Double("0.0");
		    }
		    if ("FLOAT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsMasterData[j][i] = new Float("0.0");
		    }
		    if ("INTEGER".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsMasterData[j][i] = new Integer("0");
		    }
		    if ("BIGINT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsMasterData[j][i] = new Long("0");
		    }
		}
	    }

	    if (mdsData == null) {
		mdsData = new Object[5][mdsNumCols];

		for (int i = 0; i < mdsColNames.length; i++) {
		    if ("STRING".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsData[j][i] = "FAILURE";
		    }		    
		    if ("DOUBLE".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsData[j][i] = new Double("0.0");
		    }
		    if ("FLOAT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsData[j][i] = new Float("0.0");
		    }
		    if ("INTEGER".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsData[j][i] = new Integer("0");
		    }
		    if ("BIGINT".equals(aTypes[i])) {
			for (int j = 0; j < 5; j++)
			    mdsData[j][i] = new Long("0");
		    }
		}
	    }

	}  // zeroLoadMDSData


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * OLD, out of date, fake data loading method. Left in file in case you need
	 * to add fake data capability in the future. It may be useful and it may be
	 * junk. UAYOR.
	 */

	protected void getMDSDataFake(String fsName) throws Exception 
	{
	    if (useDB) {  // Get data from mysql DB.
		System.out.println("useDB flag was used. Get MDS data from DB.");
		//getMDSDataFromDB(fsName);
		//System.out.println("MDS data loaded from DB.");
		return;
	    }

	    String [] currentColOrder;

	    mdsMasterColNames = new String[5];
	    mdsMasterColNames[0] = "Operation";
	    mdsMasterColNames[1] = "Samples";
	    mdsMasterColNames[2] = "Sample\n/Sec";
	    mdsMasterColNames[3] = "Avg\nValue";
	    mdsMasterColNames[4] = "Units";

	    if (mdsNumCols > 0) {
		mdsColNames = new String[mdsNumCols];
	    } else {
		mdsColNames = new String[5];
		mdsColNames[0] = "Operation";
		mdsColNames[1] = "Samples";
		mdsColNames[2] = "Sample\n/Sec";
		mdsColNames[3] = "Avg\nValue";
		mdsColNames[4] = "Units";
	    }

	    // Calculate the ordering of the columns based on the original ordering.
	    int [] columnMapping;
	    if (mdsNumCols > 0) {
		//Debug.out("mdsNumCols = " + mdsNumCols);
		currentColOrder = new String[mdsNumCols];

		/*************************
	    if (mdsDBColSeq != null)
		Debug.out("mdsDBColSeq length = " + mdsDBColSeq.length);
	    else
		Debug.out("mdsDBColSeq is NULL");
		**************************/
		columnMapping = new int[mdsDBColSeq.length];
		for (int i = 0; i < mdsNumCols; i++) {
		    //System.out.println(i);
		    currentColOrder[i] = mdsTable.getColumnName(i);
		    mdsColNames[i] = mdsTable.getColumnName(i);
		}

		//Debug.out("ABC");
		for (int i = 0; i < mdsDBColSeq.length; i++) {
		    //Debug.out("ABC " + i);
		    columnMapping[i] = -1;
		    for (int j = 0; j < mdsNumCols; j++) {
			//Debug.out("ABC " + i + ", " + j);
			if (mdsDBColSeq[i].equals(currentColOrder[j])) {
			    columnMapping[i] = j;
			    break;
			}
		    }
		}
	    } else {
		mdsNumCols = mdsMasterColNames.length;
		columnMapping = new int[mdsMasterColNames.length];
		currentColOrder = new String[mdsNumCols];
		for (int i = 0; i < mdsMasterColNames.length; i++) {
		    currentColOrder[i] = mdsColNames[i];
		    columnMapping[i] = i;
		}
	    }

	    if (localDebug) {
		String outS0 = "";
		String outS1 = "";
		for (int i = 0; i < mdsColNames.length; i++) {
		    outS0 += mdsColNames[i] + "  ";
		    outS1 += "  " + columnMapping[i] + "  ";
		}
		System.out.println("\n" + outS0 + "\n" + outS1);
	    }

	    mdsName = null;

	    if (localDebug)
		System.out.println("Get file system MDS data for FS = " + fsName);

	    InputStreamReader inStrRdr = null;;
	    BufferedReader buffRdr= null;
	    try {
		FileInputStream in = new FileInputStream("mds.dat");
		inStrRdr = new InputStreamReader(in);
		buffRdr = new BufferedReader(inStrRdr);
	    } catch (IOException e) {
		Debug.out("InputStream creation error.\n" + e.getMessage());
		System.exit(0);
	    }

	    Vector v = new Vector();
	    try {
		boolean first = true;
		boolean second = true;
		boolean third = true;
		boolean fourth = true;
		while (true) {
		    String line = buffRdr.readLine();
		    //System.out.println(line);
		    if (line == null)
			break;

		    if (first) {
			if (line.indexOf("name=") >= 0) {
			    String[] tokens = line.split("=");
			    mdsName = tokens[1];
			    first = false;
			} else {
			    throw new Exception("Format error in mds data.");
			}
		    } else if (second) {
			this.pcpu = line;
			second = false;
		    } else if (third) {
			this.pspace = line;
			third = false;
		    } else if (fourth) {
			this.pinode = line;
			fourth = false;
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
		//mdsData = new String[aSize];

		String [] lines = new String[aSize];
		mdsMasterData = new Object[aSize][5];
		mdsData = new Object[aSize][mdsNumCols];

		v.copyInto(lines);

		for (int i = 0; i < aSize; i++) {
		    String [] vals = lines[i].split(" ");

		    mdsMasterData[i][0] = new String(vals[0]);
		    mdsMasterData[i][1] = new Integer(Integer.decode(vals[1]).intValue());
		    mdsMasterData[i][2] = new Float(Float.valueOf(vals[2]).floatValue());
		    mdsMasterData[i][3] = new Float(Float.valueOf(vals[3]).floatValue());
		    mdsMasterData[i][4] = new String(vals[4]);

		}

		// Sort if necessary
		if (mdsSortCol != 0) {
		    //System.out.println("Sort requested for column # " + mdsSortCol);
		    //printArray(mdsData);
		    Arrays.sort(mdsMasterData, new ColumnComparator(mdsMasterSortCol));
		    //printArray(mdsData);
		}

		// Transfer sorted data to the array used for the data model
		for (int i = 0; i < aSize; i++) {

		    if (columnMapping[0] >= 0)
			mdsData[i][columnMapping[0]] = mdsMasterData[i][0];

		    if (columnMapping[1] >= 0)
			mdsData[i][columnMapping[1]] = mdsMasterData[i][1];

		    if (columnMapping[2] >= 0)
			mdsData[i][columnMapping[2]] = mdsMasterData[i][2];

		    if (columnMapping[3] >= 0)
			mdsData[i][columnMapping[3]] = mdsMasterData[i][3];

		    if (columnMapping[4] >= 0)
			mdsData[i][columnMapping[4]] = mdsMasterData[i][4];

		}

	    } catch (Exception e) {
		Debug.out("error processing input vector." + e.getMessage());
	    }

	    if (localDebug)
		System.out.println("Done generating data for mds pane.");

	}  // getMDSDataFake

    }  // MDSTablePanel


}  // MDSPanel
