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
import gov.llnl.lustre.database.Database.VariableInfo;

import gov.llnl.lustre.lwatch.util.Debug;


//////////////////////////////////////////////////////////////////////////////

/**
 * Class used to define and build OSS JTable statistics panel for file system container.
 */

public class OSSPanel extends JPanel {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("OSSPanel.debug");

    public final static boolean test = 
	Boolean.getBoolean("OSSPanel.test");

    public final static boolean useDB = true;   //Boolean.getBoolean("useDB");

    FileSystemPanel parentFS;
    String fsName;
    boolean selected;

    Database database = null;

    private JScrollPane ossScrollPane;
    protected JTable ossTable;
    private OSSTableModel ossModel;
    private Timestamp ossTimestamp;
    private JLabel ossTimestampLabel;
    private int ossSortCol = 0;
    private String ossSortColName = null;
    private JPopupMenu ossC0Popup;
    private JPopupMenu ossPopup;
    private int ossActionCol;
    private String [] ossDBColSeq = null;
    private int ossNumCols = 0;
    protected String  [] ossColNames;
    private boolean [] ossColOn;
    protected Object [][] ossData = null;
    String ossName = null;
    ArrayList hiddenColumns = new ArrayList(5);
    int nHiddenCols = 0;

    boolean stale = false;
    boolean troubled = false;
    boolean critical = false;

    protected String [] ossColumnOrder = {"READ_RATE", "WRITE_RATE", "PCT_CPU",
					  "PCT_KBYTES", "PCT_INODES"};
    protected int [] varIds;
    protected String  [] ossMasterColNames =  {"Oss Name", "Read\n(MB/s)",
	      "Write\n(MB/s)", "%CPU\nUsed", "%Space\nUsed", "%Inodes\nUsed"};
    protected static String  [] ossPlottableVars = {"Read\n(MB/s)", "Write\n(MB/s)",
						    "%CPU\nUsed", "%Space\nUsed", "%Inodes\nUsed"};
    protected Object [][] ossMasterData = null;
    protected int [][] ossMasterColorIdx = null;
    int [] ossColumnWidths = null; // Set in initOSSColumnSizes, Dimensioned in buildOSS
    private VariableInfo [] vi;
    private int ossMasterSortCol = 0;

    private OSSCellRenderer cellRenderer = new OSSCellRenderer();

    private NormalHeaderRenderer columnHdrRenderer =
	new NormalHeaderRenderer();


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for the OSSPanel.
     *
     * @param parentFS Parent FileSystemPanel object containing this OSS panel.
     *
     * @param selected true if the parent FileSystemPanel is selected.
     */

    OSSPanel (FileSystemPanel parentFS, boolean selected) {
	super();

	if (localDebug)
	    Debug.out("Entering OSScrollPanel Constructor.");

	this.parentFS = parentFS;
	this.fsName = parentFS.getFSName();
	this.selected = selected;

	/***
	// Need a DB connection to get OSS Ids
	if (! parentFS.getDBConnected()) {
	    try {
		parentFS.openDBConnection();
	    } catch (Exception e) {
		Debug.out("Exception detected while loading current OSS Ids.\n" +
			  e.getMessage());

		Thread.dumpStack();
		return;
	    }
	    
	    if (parentFS.getDatabaseClass() == null) {
		Debug.out("Error detected while loading current OSS Ids.\n");

		Thread.dumpStack();
		return;
	    }
	}
	***/

	database = parentFS.getDatabaseClass();

	/***********
	try {
	    Database.VariableInfo[] ovi = database.getVariableInfo("OSS_VARIABLE_INFO");
	    for (int i = 0; i < ovi.length; i++) {
		Debug.out("OSS var[" + i + "] :");
		System.out.println(ovi[i].variableId);
		System.out.println(ovi[i].variableName);
		System.out.println(ovi[i].variableLabel);
		System.out.println(ovi[i].threshType);
		System.out.println(ovi[i].threshVal1);
		System.out.println(ovi[i].threshVal2);
	    }
	} catch (Exception e) {
	    Debug.out("Exception caught in Database.getVariableInfo call.\n" + e.getMessage());
	    e.printStackTrace();
	}
	***********/

	loadVariableInfoArray();

	if (selected) {
	    if (localDebug)
		Debug.out("Load data for " + fsName + " OSS pane.");

	    try {
		getOSSData(database);
	    } catch (Exception e) {
		Debug.out("Exception caught while loading OSS data.\n");
		Thread.dumpStack();
		return;
	    }

	    if (localDebug)
		Debug.out("Setting size of ossColOn array to " + ossColNames.length);

	    ossColOn = new boolean[ossColNames.length];
	    for (int i = 0; i < ossColOn.length; i++)
		ossColOn[i] = true;

	} else {
	    if (localDebug)
		Debug.out("Load data for unselected FS pane " + fsName);

	    ossColNames = new String[ossMasterColNames.length];
	    ossData = new Object[1][ossMasterColNames.length];
	    ossColOn = new boolean[ossMasterColNames.length];
	
	    for (int i = 0; i < ossMasterColNames.length; i++) {
		ossColNames[i] = ossMasterColNames[i];

		if (i == 0)
		    ossData[0][0] = new String("OSS_rgb235_1");
		else
		    ossData[0][i] = new String("123456");

		ossColOn[i] = true;
	    }
	}

	// The following block of code is used to initialize the number of
	// columns and the order of the columns as retrieved from the DB.
	// As the columns are reordered and deleted, these original values
	// can be used as a reference to calculate the order in which
	// the TableModel data array should be loaded.  11/28/2006
	if (ossDBColSeq == null) {
	    ossNumCols = ossColNames.length;
	    ossDBColSeq = new String[ossNumCols];
	    for (int i = 0; i < ossNumCols; i++) {
		//System.out.println(i + "  : " + ossColNames[i]);
		ossDBColSeq[i] = ossColNames[i];
	    }
	}

	if (localDebug)
	    Debug.out("Create JPane for hdr and JTable");


	setLayout(new BorderLayout()); // Goes in CENTER panel
	JPanel hdrPane = new JPanel(new BorderLayout(), false);

	if (useDB && selected)
	    ossTimestampLabel = new JLabel("OSS      " + ossTimestamp.toString());
	else
	    ossTimestampLabel = new JLabel("No Timestamp Available");

	ossTimestampLabel.setHorizontalAlignment(JLabel.CENTER);
	hdrPane.setBackground(new Color(255,151,65));  //(Color.yellow);
	hdrPane.add(ossTimestampLabel, BorderLayout.NORTH);

	add(hdrPane, BorderLayout.NORTH);

	if (localDebug)
	    Debug.out("Create table model object.");

	ossModel = ossTableModelCreate(ossColNames);

	if (localDebug) {
	    Debug.out("Model created.");
	    Debug.out("Create JTable object for FS # " + fsName);
	}

	ossTable = new JTable(ossModel);

	if (localDebug)
	    Debug.out("Set Renderer for OSS JTable");

	ossTable.setDefaultRenderer(Object.class, cellRenderer);

	CellListener cellListener = new CellListener("OSS", -1, ossTable,
						     database, parentFS, ossMasterData);
	ossTable.addMouseListener(cellListener);

	if (localDebug)
	    Debug.out("Renderer set for OSS JTable. Get table header.");

	JTableHeader tableHeader = ossTable.getTableHeader();
	tableHeader.addMouseListener(new OSSTableHdrListener(parentFS, ossTable));

	if (localDebug)
	    Debug.out("addMouseListener added to table header.");

	// Define the popup menu for the column 0 in this JTable
	ossC0Popup = new JPopupMenu();
	JMenuItem menuItem = new JMenuItem("Reset");
	menuItem.addActionListener(new OSSPopupListener(ossTable));
	ossC0Popup.add(menuItem);

	ossC0Popup.addSeparator();

	// Define the popup menu for the other columns in this JTable
	ossPopup = new JPopupMenu();
	menuItem = new JMenuItem("Hide");
	menuItem.addActionListener(new OSSPopupListener(ossTable));
	ossPopup.add(menuItem);

	menuItem = new JMenuItem("Reset");
	menuItem.addActionListener(new OSSPopupListener(ossTable));
	ossPopup.add(menuItem);

	ossPopup.addSeparator();

	if (localDebug)
	    Debug.out("setHeaderRenderer for column headers.");

	Enumeration e = ossTable.getColumnModel().getColumns();
	while (e.hasMoreElements()) {
	    //System.out.println("Set header renderer.");
	    ((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);
	}
	ossTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

	if (localDebug)
	    Debug.out("Create Scroll pane for view into JTable.");

	ossScrollPane = new JScrollPane(ossTable);

	//Debug.out("Dimesion ossMasterColNames at ");// + ossMasterColNames.length);
	ossColumnWidths = new int[ossMasterColNames.length];
	//Debug.out("Call initOSSColumnSizes.");
	initOSSColumnSizes(ossTable, ossModel);
	ossScrollPane.getViewport().setBackground(Color.black);

	//ossScrollPane.setPreferredSize(new Dimension(600, 700));
	//ossScrollPane.setMinimumSize(new Dimension(400, 700));
	//ossScrollPane.setMaximumSize(new Dimension(750, 700));
	ossScrollPane.setAlignmentX(RIGHT_ALIGNMENT);

	add(ossScrollPane, BorderLayout.CENTER);
	setBackground(Color.blue);

	// Suppress auto-create of columns from data model now that they're established.
	ossTable.setAutoCreateColumnsFromModel(false);

	if (localDebug)
	    Debug.out("ossPane definition completed.");

    }  // OSSPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Loads variable info for specific OSS variables and initializes column names
     * to be displayed in the column headers.
     */

    public void loadVariableInfoArray() {
	//vi = new Database.VariableInfo[ossColumnOrder.length];
	//varIds = new int[ossColumnOrder.length];
	vi = new Database.VariableInfo[3];
	varIds = new int[3];
	//"READ_RATE", "WRITE_RATE", "PCT_CPU", "PCT_KBYTES", "PCT_INODES"
	//  "PCT_KBYTES" & "PCT_INODES" do NOT exist in the VariableInfo table for
	// OSS data. Wemust either fake it or ignore highlighting of these columns.

	try {

	    //for (int i = 0; i < ossColumnOrder.length; i++)
	        //vi[i] = database.getVariableInfo("OSS_VARIABLE_INFO", ossColumnOrder[i]);

	    vi[0] = database.getVariableInfo("OSS_VARIABLE_INFO", "READ_RATE");
	    vi[1] = database.getVariableInfo("OSS_VARIABLE_INFO", "WRITE_RATE");
	    vi[2] = database.getVariableInfo("OSS_VARIABLE_INFO", "PCT_CPU");

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

	for (int i = 0; i < ossColumnOrder.length; i++) {
	    for (int j = 0; j < vi.length; j++) {
		if (ossColumnOrder[i].equals(vi[j].variableName)) {
		    varIds[i] = j;  //vi[i].variableId;
		    ossMasterColNames[i+1] = vi[j].variableLabel;
		    //ossPlottableVars[i] = vi[j].variableLabel;
		}
	    }
	}

    }  // loadVariableInfoArray


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return array of plottable variables associated with the OSS panel.
     */

    public static String [] getPlottableVars() {

	return ossPlottableVars;

    }  // getPlottableVars


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return master data array associated with the OSS panel.
     */

    public  Object [][] getMasterData() {

	return ossMasterData;

    }  // getMasterData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Initailize the column widths for the OSS table.
     *
     * @param table JTable containing OSS data.
     *
     * @param model model for the OSS JTable.
     */

    public void initOSSColumnSizes(JTable table, OSSTableModel model) {
	int rowCount = model.getRowCount();
	int colCount = model.getColumnCount();
	int [] colWidth = new int [colCount];
	TableColumn column = null;
	Component comp = null;

	if (localDebug)
	    System.out.println("Calculate Column widths for OSS table " + 
			       fsName + "\n" +
			       "rowCnt = " + rowCount + "   colCnt = " + colCount);

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

		if (localDebug)
		    System.out.println(i + ", " + j);

		comp = table.getDefaultRenderer(model.getColumnClass(i)).
		    getTableCellRendererComponent(
		        table, model.getValueAt(j, i),
		        false, false, j, i);

		cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
	    }
	    if (localDebug)
		System.out.println("OSS Col " + i + " Max Width = " + cellWidth);

	    column.setPreferredWidth(cellWidth+10);  //cellWidth);
	    //Debug.out("Assign " + cellWidth + " +10 to ossColumnWidths[" + i + "]");
	    ossColumnWidths[i] = cellWidth+10;
	}
	       
    }  // initOSSColumnSizes


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reload the OSS Panel.
     */

    void reload() {
	if (localDebug)
	    Debug.out("Get OSS data");

	try {
	    getOSSData(database);
	} catch (Exception e) {
	    Debug.out("Exception caught while loading OSS data.\n");
	    e.printStackTrace();
	    return;
	}

	if (ossTimestamp != null)
	    ossTimestampLabel.setText("OSS      " + ossTimestamp.toString());
	else
	    ossTimestampLabel.setText("No timestamp found for this cycle");

	if (localDebug)
	    Debug.out("Reload OSS data vector row count = " + ossData.length);

	synchronized(this.parentFS.synchObj) {
	    ossModel.setDataVector(ossData, ossMasterColNames);
	}

	if (localDebug)
	    Debug.out("OSS setDataVector completed.");

	Enumeration e = ossTable.getColumnModel().getColumns();
	//icnt = 0;
	while (e.hasMoreElements()) {
	    //System.out.println("setHeaderRenderer " + icnt++);
	    //((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);

	    TableColumn tc = ((TableColumn) e.nextElement());
	    tc.setHeaderRenderer(columnHdrRenderer);

	    if (ossColumnWidths != null) {
		String hdrString = (String)tc.getHeaderValue();
		int width = 58;
		for (int i = 0; i < ossMasterColNames.length; i++) {
		    if ((hdrString != null) && hdrString.equals(ossMasterColNames[i])) {
			width = ossColumnWidths[i];
			break;
		    }
		}
		tc.setPreferredWidth(width);
	    }
	}

    }  // reload


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class implementing the multi-line column header renderer.
     */

    class MultiLineHeaderRenderer extends JList implements TableCellRenderer {


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

	    //if (localDebug && (table.getModel() instanceof OSSTableModel))
	    //Debug.out("row = " + row + ",  column = " + column);

	    try {
		if ((ossSortCol != 0) &&
		    ossTable.getColumnName(column).equals(ossSortColName)) {
		    //ossColNames[Math.abs(osSortCol)-1])) {

		    if (ossSortCol > 0)
			str += " ^";
		    else
			str += " v";

		}
	    } catch (Exception e) {
		Debug.out("Exception caught while rendering header for OSS table. Row = " +
			  row + ",  Column = " + column + ",  value = " +
			  value + "\n" + e.getMessage());
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
     * Class implementing the renderer for the OSS table column headers.
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
	 * Get the renderer component for the OSS table column header.
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

	    //if (localDebug && (table.getModel() instanceof OSSTableModel))
	    //if ((table.getModel() instanceof OSSTableModel))
		//Debug.out("row = " + row + ",  column = " + column);

	    try {
		if ((ossSortCol != 0) &&
		    ossTable.getColumnName(column).equals(ossSortColName)) {
		    //ossColNames[Math.abs(ossSortCol)-1])) {

		    if (ossSortCol > 0) {
			//Debug.out(ossSortCol + "\" ^\" on col # " +
			//column + "  " + ossSortColName);
			str += " ^";
		    } else {
			//Debug.out(ossSortCol + "\" v\" on col # " +
			//column + "  " + ossSortColName);
			str += " v";
		    }

		}
	    } catch (Exception e) {
		Debug.out("Exception caught while rendering header for OS table. Row = " +
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
     * Call the constructor for the OSS table model.
     *
     * @param columnNames array column names to appear in the table.
     *
     * @return table model for the OSS table.
     */

     private OSSTableModel ossTableModelCreate(String [] columnNames) {

	return new OSSTableModel(columnNames);

     }  // ossTableModelCreate


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class to define the table model for the OSS data. Extends AbstractTableModel.
     */

    // Subclass DefaultableModel so that cells can be set to uneditable.
    public class OSSTableModel extends AbstractTableModel {

	public String [] columnNames = null;
	public Object [][] data = null;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constrcutor for the OSS table model.
	 *
	 * @param columnNames array of column names to appear in the table.
	 */

	protected OSSTableModel(String [] columnNames) {

	    if (columnNames == null)
		if (localDebug) {
		    Debug.out("columnNames array arg = null");
		    Debug.out("# of columnNames = " + columnNames.length);
		}

	    this.data = ossData;
	    this.columnNames = columnNames;

	    if (localDebug) {
		for (int i = 0; i < columnNames.length; i++)
		    Debug.out(i + " : " + columnNames[i]);
	    }

	}  // OSSTableModel


	/**
	 * Returns false, cell not editable.
	 */

	public  boolean isCellEditable(int row, int column) {
	    return false;
	}  // isCellEditable


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

	    return data[row][col];

	}  // getValueAt


	/**
	 * Returns row count.
	 *
	 * @return row count.
	 */

	public int getRowCount() {

	    return data.length;

	}  // getRowCount


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


	/**
	 * Returns column count.
	 *
	 * @return column count.
	 */

	public int getColumnCount() {

	    return columnNames.length;

	}  // getColumnCount


	/**
	 * Set the data vector for the OSS table.
	 *
	 * @param idata Array containing the values for the OSS table cells.
	 *
	 * @param colNames array containing the names of the columns for the OSS table.
	 */

	public void setDataVector(Object[][] idata, String [] colNames) {

	    this.data = idata;
	    this.columnNames = colNames;

	    fireTableDataChanged();

	}  // setDataVector


	/**
	 * Invoke the comparator to sort the specified column.
	 */

	public void sort(int sortCol) {
	    synchronized(OSSPanel.this.parentFS.synchObj) {
		Arrays.sort(data, new ColumnComparator(sortCol));
		fireTableChanged(new TableModelEvent(this, 0, data.length));
	    }

	}  // sort

    }  // OSSTableModel


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
     * Class that handles the OSS table header mouse button clicks.
     */

    public class OSSTableHdrListener extends MouseAdapter {

	private FileSystemPanel parent;
	private JTable table;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the class that handles the OSS table header mouse button clicks.
	 *
	 * @param parent the FileSystem object to which this OSSPanel belongs.
	 *
	 * @param table the JTable containing the OSS data.
	 */

	public OSSTableHdrListener(FileSystemPanel parent, JTable table) {
	    super();

	    this.parent = parent;
	    this.table = table;

	}  // OSSTableHdrListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for mouse button release events in the OSS table column header.
	 *
	 * @param e the MouseEvent causing the handler to be invoked.
	 */

	public void mouseReleased(MouseEvent e) {
	    TableColumnModel columnModel = table.getColumnModel();
	    int viewColumn = columnModel.getColumnIndexAtX(e.getX());
	    int buttNum = e.getButton();

	    int colCount = ossTable.getColumnCount();
	    for (int j = 0; j < colCount; j++) {
		TableColumn tc = columnModel.getColumn(j);
		int colWidth = tc.getWidth();
		String cName = ossTable.getColumnName(j);
		for (int i = 0; i < ossMasterColNames.length; i++) {
		    if (ossMasterColNames[i].equals(cName)) {
			ossColumnWidths[i] = colWidth;
			//System.out.println("OSS column " + cName + " width set to " + colWidth);
			break;
		    }
		}
	    }


	    if ((buttNum == 3) && (viewColumn != 0)) {
		if (localDebug)
		    System.out.println("OSS Right mouse button Release detected in col " +
				       viewColumn);

		ossActionCol = viewColumn;

		if (viewColumn != 0) {
		    showPopup(e);
		} else {
		    showC0Popup(e);
		}
	    }
	}  // mouseReleased


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for mouse button click events in the OSS table column header.
	 *
	 * @param e the MouseEvent causing the handler to be invoked.
	 */

	public void mouseClicked(MouseEvent e) {

	    TableColumnModel columnModel = table.getColumnModel();
	    int viewColumn = columnModel.getColumnIndexAtX(e.getX());
	    int buttNum = e.getButton();
	    if ((viewColumn > ossNumCols) || (viewColumn < 0))
		return;

	    int dataModelColindx = toModel(table, viewColumn);
	    if (dataModelColindx == -1)
		return;

	    if (localDebug)
		System.out.println("Mouse Click event detected in OSS Table column " +
				   viewColumn + "\nButton # " + buttNum);

	    if (buttNum == 1) {
		String sortID = ossTable.getColumnName(viewColumn);
		String lastSortColName = ossSortColName;
		int lastSortDir = 1;
		if (ossMasterSortCol < 0)
		    lastSortDir = -1;
		ossSortColName = sortID;
		int sortColumn = 0;
		for (int i = 0; i < ossColNames.length; i++) {
		    if (ossColNames[i].equals(sortID)) {
			//Debug.out("\nsortID " + sortID + " <==> ossColNames[" + i + "] " + ossColNames[i]);
			sortColumn = i;  //viewColumn;
			//System.out.println("Sorting on column  " + i + " - " + sortID);
			break;
		    }
		}
		ossSortCol = sortColumn + 1;
		if ((lastSortColName != null) && (lastSortColName.equals(ossSortColName)))
		    ossSortCol = ossSortCol * (-lastSortDir);

		//System.out.println("Last Sort Col = " + lastSortColName + "  new Sort Col = " +
				   //ossSortColName + "  last sort dir = " + lastSortDir);
		if (localDebug)
		    System.out.println("OSS sort : " + ossSortCol + " = " +
				       ossColNames[Math.abs(ossSortCol)-1]);


		// Calculate the column from the master data array
		sortColumn = 0;
		for (int i = 0; i < ossMasterColNames.length; i++) {
		    if (ossMasterColNames[i].equals(sortID)) {
			sortColumn = i;
			//System.out.println("Set sorting for Master column  " + i + " - " + sortID);
			break;
		    }
		}
		ossMasterSortCol = sortColumn + 1;
		if (ossSortCol < 0)
		    ossMasterSortCol = -ossMasterSortCol;

		if (localDebug)
		    System.out.println("OSS Master sort : " + ossMasterSortCol + " = " +
				       ossMasterColNames[Math.abs(ossMasterSortCol)-1]);
	    
		// Sort on selected row
		if (ossSortCol != 0) {
		    ((OSSTableModel)this.table.getModel()).sort(ossSortCol);
		    table.getTableHeader().repaint();
		}
	    } else if (buttNum == 3) {
		if (localDebug)
		    System.out.println("OSS Right mouse button click detected in col " +
				       viewColumn);

		ossActionCol = dataModelColindx;

		if (dataModelColindx != 0) {
		    showPopup(e);
		} else {
		    showC0Popup(e);
		}
	    }
	}  // mouseClicked


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Display the OSS table column > 0 popup menu.
	 */

	private void showPopup(MouseEvent e) {

	    ossPopup.show(e.getComponent(), e.getX(), e.getY());

	}  // showPopup


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Display the OSS table column 0 popup menu.
	 */

	private void showC0Popup(MouseEvent e) {

	    ossC0Popup.show(e.getComponent(), e.getX(), e.getY());

	}  // showC0Popup
	
    }  // OSSTableHdrListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class that implements the ActionListener for the OSS JTable
     */

    public class OSSPopupListener implements ActionListener {

	private JTable table;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the ActionListener being implemented.
	 */

       public OSSPopupListener(JTable table) {
	    super();

	    this.table = table;

       }  //  OSSPopupListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * The action handler for the OSS table button clicks.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	public void actionPerformed(ActionEvent e) {

	    //parentFS.getParentOf().resetRefresh();

	    if (localDebug)
		System.out.println("ActionEvent detected in OSS popup \n" +
			       e.getActionCommand() + " on Column # " +
			       ossActionCol);

	    if (e.getActionCommand().equals("Hide")) {
		// Get column name and use this to identify the column number to be deleted.
		// That is the index in ossColOn to set to false. The value in ossActionCol
		// is the one to use for the ossTable calls.

		String cName = ossTable.getColumnName(ossActionCol);
		for (int i = 0; i < ossColNames.length; i++) {
		    if (ossColNames[i].equals(cName)) {
			//Debug.out("Set ossColOn[" + i + "] to false.");
			ossColOn[i] = false;
			//System.out.println("Setting orig column # " + i + " to false.");
			break;
		    }
		}
		TableColumnModel ossTableColModel = ossTable.getColumnModel();
		TableColumn hideCol = ossTableColModel.getColumn(ossActionCol);
		


		// Add removed column to hiddenColumns ArrayList
		hiddenColumns.ensureCapacity(nHiddenCols+1);
		hiddenColumns.add(nHiddenCols++, hideCol);



		if (localDebug)
		    Debug.out("Removing column to removeColumn # " + ossActionCol);

		ossTableColModel.removeColumn(hideCol);
		ossNumCols--;

		JMenuItem menuItem = new JMenuItem("Restore " + cName);
		menuItem.addActionListener(new OSSPopupListener(ossTable));
		ossPopup.add(menuItem);

		menuItem = new JMenuItem("Restore " + cName);
		menuItem.addActionListener(new OSSPopupListener(ossTable));
		ossC0Popup.add(menuItem);

	    } else if(e.getActionCommand().startsWith("Restore ")) {
		String command = e.getActionCommand();
		//Debug.out("Restore menuItem selected." + command);
		String restoreColName = command.substring(8);

		// Add the column back into the table to the right of column
		// where the action took place.
		//Debug.out("Restoring columnn " + restoreColName + " at column # " +
			  //ossActionCol);
		int restoreCol = 0;
		while (!ossMasterColNames[restoreCol].equals(restoreColName))
		    restoreCol++;

		if (restoreCol >= ossMasterColNames.length) {
		    Debug.out("Error matching column names for restoration.");
		    return;
		}
		//Debug.out("Restoring column " + ossMasterColNames[restoreCol]);

		// Locate coulmn to be added back in the hiddenColumns ArrayList
		ListIterator it = hiddenColumns.listIterator();

		while (it.hasNext()) {
		    TableColumn column = (TableColumn)it.next();
		    if (((String)(column.getHeaderValue())).equals(restoreColName)) {
			ossTable.getColumnModel().addColumn(column);
			it.remove();
			nHiddenCols--;
			break;
		    }
		}

		ossModel.fireTableDataChanged();

		ossColOn[restoreCol] = true;
		ossNumCols++;


		// Remove restore menu item from  Popup menu.
		int cCount = ossPopup.getComponentCount();
		for (int i = 3; i < cCount; i++) {
		    Component comp = ossPopup.getComponent(i);
		    String compText = ((AbstractButton)comp).getText();
		    //System.out.println("Component # " + i + " = " + compText);
		    if (command.equals(((AbstractButton)comp).getText())) {
			ossPopup.remove(i);
			break;
		    }
		}
		cCount = ossC0Popup.getComponentCount();
		for (int i = 2; i < cCount; i++) {
		    Component comp = ossC0Popup.getComponent(i);
		    String compText = ((AbstractButton)comp).getText();
		    //System.out.println("C0 Component # " + i + " = " + compText);
		    if (command.equals(((AbstractButton)comp).getText())) {
			ossC0Popup.remove(i);
			break;
		    }
		}

	    } else if(e.getActionCommand().equals("Reset")) {

		// Locate coulmn to be added back in the hiddenColumns ArrayList
		ListIterator it = hiddenColumns.listIterator();

		while (it.hasNext()) {
		    TableColumn column = (TableColumn)it.next();
		    ossTable.getColumnModel().addColumn(column);
		    it.remove();
		    nHiddenCols--;
		}

		for (int i = 0; i < ossColOn.length; i++)
		    ossColOn[i] = true;

		ossNumCols = ossMasterColNames.length;

		// Move TableColumns to the original order.
		for (int i = 0; i < ossMasterColNames.length; i++) {
		    int istrt = i;
		    for (int j = istrt; j < ossMasterColNames.length; j++) {
			String tcn = ossTable.getColumnName(j);
			if (tcn.equals(ossMasterColNames[i])) {
			    ossTable.moveColumn(j, i);
			}
		    }
		}

		if (localDebug)
		    Debug.out("Remove menu items from popup menus.");
		// Remove JPopup menuitems for the restored columns.
		int cCount = ossPopup.getComponentCount();
		for (int i = 3; i < cCount; i++) {
		    Component comp = ossPopup.getComponent(3);
		    if (((AbstractButton)comp).getText().startsWith("Restore "))
			ossPopup.remove(3);
		}

		if (localDebug)
		    Debug.out("Remove menu items from C0 popup menu.");
		// Remove JPopup menuitems for the restored columns.
		cCount = ossC0Popup.getComponentCount();
		for (int i = 2; i < cCount; i++) {
		    Component comp = ossC0Popup.getComponent(2);
		    if (((AbstractButton)comp).getText().startsWith("Restore "))
			ossC0Popup.remove(2);
		}

		ossModel.fireTableDataChanged();

	    }

	}  // actionPerformed

    }  // OSSPopupListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to render the OSS table cells.
     */

    class OSSCellRenderer extends DefaultTableCellRenderer {

	Color [] cellColor = new Color[] {
	    Color.black,
	    Color.red,  //  gray, stale color changed on 05/25/07
	    Color.yellow,
	    Color.red
	};


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for cell renderer for OSS table.
	 */

	OSSCellRenderer() {

	    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

	}  // OSSCellRenderer


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
	    final Color aggGrey = new Color(180, 180, 180);
	    final int width = 4;
	    //String col0Name = table.getColumnName(0);

	    if (false) {
		if (value != null)
		    System.out.print(row + "  " + column + "  " + value.toString());
		else
		    System.out.print(row + "  " + column);
		for (int i = 0; i < table.getColumnCount(); i++)
		    System.out.print(" " + table.getColumnName(i));

		System.out.print("\n");

		
		System.out.print("View-to-Model mapping  ");
		for (int i = 0; i < table.getColumnCount(); i++)
		    System.out.print(" <" + i + " " + table.convertColumnIndexToModel(i) + ">");

		System.out.print("\n");
	    }

	    Color cellBgColor = Color.black;
	    Color cellFgColor = Color.white;
	    int rowCnt = table.getRowCount();
	    //if (localDebug)
		//Debug.out("rowCnt = " + rowCnt + "   row = " + row);
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
	
	    if ((varindx < 0) || (vi.length <= varindx))
		return Color.black;

	    try {
		float fVal = (float)0;
		//int varidx = varIds[i-1];
		int varidx = varIds[varindx];
		if ((vi == null)) {
		    return Color.black;  //Color.black
		} else if (val == null) {
		    rowStale = true;
		    return Color.gray;  //Color.gray
		} else if (vi[varidx].threshType == 0) {
		    return Color.black;  //Color.black
		} else if (vi[varidx].threshType == 1) {
		    if (val instanceof Double) {
			if (((Double)(val)).floatValue() != vi[varidx].threshVal1) {
			    rowCritical = true;
			    return Color.red;
			}
		    } else if (val instanceof Long) {
			if (((Long)(val)).floatValue() != vi[varidx].threshVal1) {
			    rowCritical = true;
			    return Color.red;
			}
		    } else if (val instanceof Float) {
			if (((Float)(val)).floatValue() != vi[varidx].threshVal1) {
			    rowCritical = true;
			    return Color.red;
			}
		    } else if (val instanceof Integer) {
			if (((Integer)(val)).floatValue() != vi[varidx].threshVal1) {
			    rowCritical = true;
			    return Color.red;
			}
		    } else {
			return Color.black;
		    }
		} else if (vi[varidx].threshType == 2) {
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
			return Color.red;
		    } else {
			return Color.black;
		    }
		} else if (vi[varidx].threshType == 3) {

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
			rowCritical = true;
			//if (test) Debug.out("RED");
			return Color.red;
		    } else if (fVal >= vi[varidx].threshVal1) {
			rowTroubled = true;
			//if (test) Debug.out("YELLOW");
			return Color.yellow;
		    } else {
			return Color.black;
		    }
		} else if (vi[varidx].threshType == 4) {
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
			return Color.red;
		    } else if (fVal < vi[varidx].threshVal1) {
			rowTroubled = true;
			return Color.yellow;
		    } else {
			return Color.black;
		    }
		}

		if (rowCritical) {
		    critical = true;
		} else if (rowTroubled) {
		    troubled = true;
		} else if (rowStale) {
		    stale = true;
		}

	    } catch (Exception e) {
		Debug.out("Exception caught : " + e.getMessage());
		e.printStackTrace();
	    }
	    return Color.black;

	}  // getCellColor

    }  // OSSCellRenderer


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Load OSS data from database for most recent timestamp.
     *
     * @param database Database object used to load data with.
     */

    protected void getOSSData(Database database) throws Exception {

	if (localDebug) {
	    Debug.out("Entering getOSSData");
	    Debug.out("Get data for latest timestamp.");
	}

	stale = false;
	troubled = false;
	critical = false;

	//
	// Get latest TS_ID from TIMESTAMP_ID table.
	//

	TimeInfo timeinfo = database.getLatestTimeInfo("OST_DATA");
	ossTimestamp = timeinfo.timestamp;

	//ossTimestamp = new Timestamp(new java.util.Date().getTime());

	if (localDebug)
	    Debug.out("timestamp = " + ossTimestamp);



	//
	// Get OSS data for latest timestamp.
	//

	if (localDebug)
	    Debug.out("Get OSS data from DB");

	OssData ossDBData = null;
	try {
	    ossDBData = database.getCurrentOssData();
	    if (ossDBData == null)
		throw new Exception("null return from getCurrentOSSData.");
	} catch (Exception e) {
	    Debug.out("Exception detected while loading current OSS data.\n" +
		      e.getMessage());

	    zeroLoadOSSData();
	    return;
	}

	//
	// Get column headers.
	//

	String [] ossColTypes = {"STRING", "DOUBLE", "DOUBLE",
				 "DOUBLE", "DOUBLE", "DOUBLE"};


	if (localDebug)
	    Debug.out("Check the current column order.");


	ossColNames = new String[ossMasterColNames.length];
	for (int i = 0; i < ossMasterColNames.length; i++)
	    ossColNames[i] = ossMasterColNames[i];

	ossNumCols = ossMasterColNames.length;





	int aSize = 0;
	boolean [] ammaFlags = null;
	try {
	    aSize = ossDBData.getSize();

	    //String [] lines = new String[asize];
	    ossMasterData = new Object[aSize+4][ossMasterColNames.length];
	    ossMasterColorIdx = new int[aSize][ossMasterColNames.length];
	    ossData = new Object[aSize+4][ossNumCols];
	    ammaFlags = new boolean[aSize];
	    
	} catch (Exception e) {
	    Debug.out("error processing input vector.\n" + e.getMessage());
	}

	if (localDebug)
	    Debug.out("aSize = " + aSize);

	try {
	    Float fVal;
	    for (int i = 0; i < aSize; i++) {
		//System.out.println("i = " + i);
		ossMasterData[i][0] = ossDBData.getHostname(i);

		// Check Timestamp  & set troubled flag if stale
		Timestamp ots = ossDBData.getTimestamp(i);
		//if (ots == null)
		    //stale = true;
		fVal = ossDBData.getReadRate(i);
		if (fVal == null)
		    ossMasterData[i][1] = fVal; //new Double(0.0);
		else
		    ossMasterData[i][1] = new Double(ossDBData.getReadRate(i).doubleValue());

		fVal = ossDBData.getWriteRate(i);
		if (fVal == null)
		    ossMasterData[i][2] = fVal; //new Double(0.0);
		else
		    ossMasterData[i][2] = new Double(ossDBData.getWriteRate(i).doubleValue());

		fVal = ossDBData.getPctCpu(i);
		if (fVal == null)
		    ossMasterData[i][3] = fVal; //new Double(0.0);
		else
		    ossMasterData[i][3] = new Double(ossDBData.getPctCpu(i).doubleValue());

		fVal = ossDBData.getPctKbytes(i);
		if (fVal == null)
		    ossMasterData[i][4] = fVal; //new Double(0.0);
		else
		    ossMasterData[i][4] = new Double(ossDBData.getPctKbytes(i).doubleValue());

		fVal = ossDBData.getPctInodes(i);
		if (fVal == null)
		    ossMasterData[i][5] = fVal; //new Double(0.0);
		else
		    ossMasterData[i][5] = new Double(ossDBData.getPctInodes(i).doubleValue());

		if (test && (fVal != null)) {
		    if (i == 0)
			ossMasterData[i][3] = new Double(95.);
		    else if (i == 3)
			ossMasterData[i][3] = new Double(101.);
		    else if (i == 7)
			ossMasterData[i][3] = new Double(101.);
		}

		ammaFlags[i] = true;

		setCellColors(i);

		//if (localDebug)
		//System.out.println(ossNames[i] + " : " + reads[i] + " : " +
		//writes[i] + " : " + cpuPercs[i] + " : " + spacePercs[i]);

	    }  // End of "for (int i = 0; i < aSize; i++) {"


	    //-------------------------------------------------------------------
	    // Get the Aggregate, Max, Min & Avg values for each column from DB

	    // Column 1
	    ossMasterData[aSize][0] = new String("AGGREGATE");
	    ossMasterData[aSize+1][0] = new String("MAXIMUM");
	    ossMasterData[aSize+2][0] = new String("MINIMUM");
	    ossMasterData[aSize+3][0] = new String("AVERAGE");

	    // Read Rate
	    ossMasterData[aSize][1] = ossDBData.getReadRateSum(ammaFlags);
	    ossMasterData[aSize+1][1] = ossDBData.getReadRateMax(ammaFlags);
	    ossMasterData[aSize+2][1] = ossDBData.getReadRateMin(ammaFlags);
	    ossMasterData[aSize+3][1] = ossDBData.getReadRateAvg(ammaFlags);

	    // Write Rate
	    ossMasterData[aSize][2] = ossDBData.getWriteRateSum(ammaFlags);
	    ossMasterData[aSize+1][2] = ossDBData.getWriteRateMax(ammaFlags);
	    ossMasterData[aSize+2][2] = ossDBData.getWriteRateMin(ammaFlags);
	    ossMasterData[aSize+3][2] = ossDBData.getWriteRateAvg(ammaFlags);

	    // Percent CPU
	    ossMasterData[aSize][3] = new String("*****");  //ossDBData.getPctCpuSum(ammaFlags);
	    ossMasterData[aSize+1][3] = ossDBData.getPctCpuMax(ammaFlags);
	    ossMasterData[aSize+2][3] = ossDBData.getPctCpuMin(ammaFlags);
	    ossMasterData[aSize+3][3] = ossDBData.getPctCpuAvg(ammaFlags);

	    // Percent KBytes
	    ossMasterData[aSize][4] = new String("*****");  //ossDBData.getPctKbytesSum(ammaFlags);
	    ossMasterData[aSize+1][4] = ossDBData.getPctKbytesMax(ammaFlags);
	    ossMasterData[aSize+2][4] = ossDBData.getPctKbytesMin(ammaFlags);
	    ossMasterData[aSize+3][4] = ossDBData.getPctKbytesAvg(ammaFlags);

	    // Percent Inodes
	    ossMasterData[aSize][5] = new String("*****");  //ossDBData.getPctInodesSum(ammaFlags);
	    ossMasterData[aSize+1][5] = ossDBData.getPctInodesMax(ammaFlags);
	    ossMasterData[aSize+2][5] = ossDBData.getPctInodesMin(ammaFlags);
	    ossMasterData[aSize+3][5] = ossDBData.getPctInodesAvg(ammaFlags);

	    //-------------------------------------------------------------------

	} catch (Exception e) {
	    Debug.out("error processing data from DB.\n" + e.getMessage());

	    System.exit(1);
	}

	try {

	    // Sort if necessary
	    if (ossSortCol != 0) {
		if (localDebug) {
		    System.out.println("Sort requested for column # " + ossSortCol);
		    //printArray(ossData);
		}

		Arrays.sort(ossMasterData, new ColumnComparator(ossMasterSortCol));

		//if (localDebug)
		    //printArray(ossData);

	    }

	    // Transfer sorted data to the array used for the data model
	    for (int i = 0; i < aSize+4; i++) {

		for (int j = 0; j < ossMasterColNames.length; j++) {
		    //System.out.println("CCC : " + i + ", " + j + " : " + ossMasterData[i][j].toString());

		    ossData[i][j] = ossMasterData[i][j];
		}
	    }

	} catch (Exception e) {
	    Debug.out("error building up OSS Object array.\n" + e.getMessage());

	    System.exit(1);
	}

	if (localDebug)
	    Debug.out("Done loading OSS data from DB.");

	return;

    }  // getOSSData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Scan the master OSS data array and apply the variable threshold values to determine
     * the color to be used for rendering the aggregate, min, max & average rows of the JTable.
     *
     * @param data row to be scanned.
     */

    public void setCellColors(int irow) {
	boolean rowCritical = false;
	boolean rowTroubled = false;
	boolean rowStale = false;
	
	try {
	    for (int i = 1; i < ossMasterData[i].length; i++) {
		float fVal = (float)0;
		if (ossMasterData[irow][i] == null) {
		    rowStale = true;
		}

		if (i > vi.length)  // Required due to some vars w/o VarInfo table entry for OSS.
		    continue;

		if (vi[i-1].threshType == 1) {
		    Object val = ossMasterData[irow][i];
		    if (val instanceof Double) {
			if (((Double)(val)).floatValue() != vi[i-1].threshVal1) {
			    rowCritical = true;
			}
		    } else if (val instanceof Long) {
			if (((Long)(val)).floatValue() != vi[i-1].threshVal1) {
			    rowCritical = true;
			}
		    } else if (val instanceof Float) {
			if (((Float)(val)).floatValue() != vi[i-1].threshVal1) {
			    rowCritical = true;
			}
		    } else if (val instanceof Integer) {
			if (((Integer)(val)).floatValue() != vi[i-1].threshVal1) {
			    rowCritical = true;
			}
		    }
		} else if (vi[i-1].threshType == 2) {
		    Object val = ossMasterData[irow][i];
		    if (val instanceof Double) {
			fVal = ((Double)(val)).floatValue();
		    } else if (val instanceof Long) {
			fVal = ((Long)(val)).floatValue();
		    } else if (val instanceof Float) {
			fVal = ((Float)(val)).floatValue();
		    } else if (val instanceof Integer) {
			fVal = ((Integer)(val)).floatValue();
		    }

		    if ((fVal < vi[i-1].threshVal1) ||
			(fVal > vi[i-1].threshVal2)) {
			rowCritical = true;
		    }
		} else if (vi[i-1].threshType == 3) {
		    Object val = ossMasterData[irow][i];
		    if (val instanceof Double) {
			fVal = ((Double)(val)).floatValue();
		    } else if (val instanceof Long) {
			fVal = ((Long)(val)).floatValue();
		    } else if (val instanceof Float) {
			fVal = ((Float)(val)).floatValue();
		    } else if (val instanceof Integer) {
			fVal = ((Integer)(val)).floatValue();
		    }

		    if (fVal >= vi[i-1].threshVal2) {
			rowCritical = true;
		    } else if (fVal >= vi[i-1].threshVal1) {
			rowTroubled = true;
		    }
		} else if (vi[i-1].threshType == 4) {
		    Object val = ossMasterData[irow][i];
		    if (val instanceof Double) {
			fVal = ((Double)(val)).floatValue();
		    } else if (val instanceof Long) {
			fVal = ((Long)(val)).floatValue();
		    } else if (val instanceof Float) {
			fVal = ((Float)(val)).floatValue();
		    } else if (val instanceof Integer) {
			fVal = ((Integer)(val)).floatValue();
		    }

		    if (fVal < vi[i-1].threshVal2) {
			rowCritical = true;
		    } else if (fVal < vi[i-1].threshVal1) {
			rowTroubled = true;
		    }
		}
	    }  // for (int i = 1; i < ossMasterData[i].length; i++)
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
     * Method used to load dummy data for OSS when DB is not available. This should
     * never be invoked. It has been left in for historical reasons.
     */

    void zeroLoadOSSData () {

	final String [] aTypes = {"STRING", "DOUBLE", "DOUBLE", "DOUBLE",
				  "DOUBLE", "DOUBLE"};

	if (ossMasterData == null) {
	    ossMasterData = new Object[5][ossMasterColNames.length];

	    for (int i = 0; i < ossMasterColNames.length; i++) {
		if ("STRING".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossMasterData[j][i] = "FAILURE";
		}		    
		if ("DOUBLE".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossMasterData[j][i] = new Double("0.0");
		}
		if ("FLOAT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossMasterData[j][i] = new Float("0.0");
		}
		if ("INTEGER".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossMasterData[j][i] = new Integer("0");
		}
		if ("BIGINT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossMasterData[j][i] = new Long("0");
		}
	    }
	}

	if (ossData == null) {
	    ossData = new Object[5][ossNumCols];

	    for (int i = 0; i < ossColNames.length; i++) {
		if ("STRING".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossData[j][i] = "FAILURE";
		}		    
		if ("DOUBLE".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossData[j][i] = new Double("0.0");
		}
		if ("FLOAT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossData[j][i] = new Float("0.0");
		}
		if ("INTEGER".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossData[j][i] = new Integer("0");
		}
		if ("BIGINT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ossData[j][i] = new Long("0");
		}
	    }

	}

    }  // zeroLoadOSSData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * OLD, out of date, fake data loading method. Left in file in case you need
     * to add fake data capability in the future. It may be useful and it may be
     * junk. UAYOR.
     */

    protected void getOSSDataFake(String fsName) throws Exception {

	String [] currentColOrder;

	//if (ossNumCols > 0) {
	    //ossColNames = new String[ossNumCols];
	//} else {
	    ossColNames = new String[ossMasterColNames.length];
	    for (int i = 0; i < ossMasterColNames.length; i++)
		ossColNames[i] = ossMasterColNames[i];
	//}

	// Calculate the ordering of the columns based on the original ordering.

	ossNumCols = ossMasterColNames.length;

	ossName = null;

	//System.out.println("Get file system OSS data for FS = " + fsName);
	InputStreamReader inStrRdr = null;;
	BufferedReader buffRdr= null;
	try {
	    FileInputStream in = new FileInputStream("oss.dat");
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
			ossName = tokens[1];
			first = false;
		    } else {
			throw new Exception("Format error in oss data.");
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
	    Debug.out("Number of lines read from oss.dat = " + aSize);
	    Debug.out("Number of columns in 2-D array = " + ossNumCols);
	    //ossData = new String[aSize];

	    String [] lines = new String[aSize];
	    ossMasterData = new Object[aSize][ossMasterColNames.length];
	    ossData = new Object[aSize][ossNumCols];

	    v.copyInto(lines);

	    for (int i = 0; i < aSize; i++) {
		String [] vals = lines[i].split(" ");
		int idxN = 1;

		if ("AGGREGATE".equals(vals[0])) {
		    ossMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("MAXIMUM".equals(vals[0])) {
		    ossMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("MINIMUM".equals(vals[0])) {
		    ossMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("AVERAGE".equals(vals[0])) {
		    ossMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else {
		    ossMasterData[i][0] = new String(vals[0]);
		}

		ossMasterData[i][1] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ossMasterData[i][2] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ossMasterData[i][3] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ossMasterData[i][4] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ossMasterData[i][5] = new Float(Float.valueOf(vals[idxN++]).floatValue());

		//if (localDebug)
		//System.out.println(ossNames[i] + " : " + reads[i] + " : " +
		//writes[i] + " : " + cpuPercs[i] + " : " + spacePercs[i]);
	    }

	    // Sort if necessary
	    if (ossSortCol != 0) {
		System.out.println("Sort requested for column # " + ossSortCol);
		//printArray(ossData);
		Arrays.sort(ossMasterData, new ColumnComparator(ossMasterSortCol));
		//printArray(ossData);
	    }

	    // Transfer sorted data to the array used for the data model
	    for (int i = 0; i < aSize; i++) {
		for (int j = 0; j < ossMasterColNames.length; j++)
		    ossData[i][j] = ossMasterData[i][j];
	    }

	} catch (Exception e) {
	    Debug.out("error processing input vector.\n" + e.getMessage());
	}

	System.out.println("Done generating data for oss pane.");

    }  // getOSSDataFake

}  // OSSPanel
