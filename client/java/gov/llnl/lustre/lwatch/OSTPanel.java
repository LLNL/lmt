package gov.llnl.lustre.lwatch;
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
//import java.text.DecimalFormat;

import gov.llnl.lustre.database.Database.TimeInfo;
import gov.llnl.lustre.database.Database.VariableInfo;

import gov.llnl.lustre.lwatch.util.Debug;


//////////////////////////////////////////////////////////////////////////////

/**
 * Class used to define and build OST JTable statistics panel for file system container.
 */

public class OSTPanel extends JPanel {

    private final static boolean debug = Boolean.getBoolean("debug");

    public final static boolean localDebug = 
	Boolean.getBoolean("OSTPanel.debug");

    public final static boolean test = 
	Boolean.getBoolean("OSTPanel.test");

    public final static boolean useDB = true;   //Boolean.getBoolean("useDB");

    //private static final  DecimalFormat decimalFormat =
	//new DecimalFormat("#,###,##0.00");

    FileSystemPanel parentFS;
    String fsName;
    boolean selected;

    Database database = null;

    private JScrollPane ostScrollPane;
    protected JTable ostTable;
    private OSTTableModel ostModel;
    private Timestamp ostTimestamp;
    private JLabel ostTimestampLabel;
    private int ostSortCol = 0;
    private String ostSortColName = null;
    private JPopupMenu ostC0Popup;
    private JPopupMenu ostPopup;
    private int ostActionCol;
    private String [] ostDBColSeq = null;
    private int ostNumCols = 0;
    protected String  [] ostColNames;
    private boolean [] ostColOn;
    protected Object [][] ostData = null;
    String ostName = null;
    ArrayList hiddenColumns = new ArrayList(5);
    int nHiddenCols = 0;

    boolean stale = false;
    boolean troubled = false;
    boolean critical = false;

    protected String [] ostColumnOrder = {"READ_RATE", "WRITE_RATE", "PCT_CPU",
					  "PCT_KBYTES", "PCT_INODES"};
    protected int [] varIds;
    protected String [] ostMasterColNames;
    protected static String [] ostPlottableVars;

    /*
    protected String [] ostMasterColNames =  {"Ost Name", "Read\n(MB/s)",
	      "Write\n(MB/s)", "%CPU\nUsed", "%Space\nUsed", "%Inodes\nUsed"};
    protected static String [] ostPlottableVars = {"Read\n(MB/s)", "Write\n(MB/s)",
	      "%CPU\nUsed", "%Space\nUsed", "%Inodes\nUsed"};
    */


    protected Object [][] ostMasterData = null;
    protected int [][] ostMasterColorIdx = null;
    int [] ostColumnWidths = null; // Set in initOSTColumnSizes, Dimensioned in buildOST
    private VariableInfo [] vi;
    private int ostMasterSortCol = 0;

    private OSTCellRenderer cellRenderer = new OSTCellRenderer();

    //private MultiLineHeaderRenderer columnHdrRenderer =
	//new MultiLineHeaderRenderer();

    private NormalHeaderRenderer columnHdrRenderer =
	new NormalHeaderRenderer();


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for the OSTPanel.
     *
     * @param parentFS Parent FileSystemPanel object containing this OST panel.
     *
     * @param selected true if the parent FileSystemPanel is selected.
     */

    OSTPanel (FileSystemPanel parentFS, boolean selected) {
	super();

	if (localDebug)
	    Debug.out("Entering OSTScrollPanel Constructor.");

	this.parentFS = parentFS;
	this.fsName = parentFS.getFSName();
	this.selected = selected;

	ostMasterColNames = new String[ostColumnOrder.length + 1];
	ostMasterColNames[0] = new String("Ost Name");

	database = parentFS.getDatabaseClass();

	/***
	try {
	    Database.VariableInfo[] ovi = database.getVariableInfo("OST_VARIABLE_INFO");
	    for (int i = 0; i < ovi.length; i++) {
		Debug.out("OST var[" + i + "] :");
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
	***/

	loadVariableInfoArray();

	if (selected) {
	    if (localDebug)
		Debug.out("Load data for " + fsName + " OST pane.");

	    try {
		getOSTData(database);
	    } catch (Exception e) {
		Debug.out("Exception caught while loading OST data.\n");
		Thread.dumpStack();
		return;
	    }

	    if (localDebug)
		Debug.out("Setting size of ostColOn array to " + ostColNames.length);

	    ostColOn = new boolean[ostColNames.length];
	    for (int i = 0; i < ostColOn.length; i++)
		ostColOn[i] = true;

	} else {
	    if (localDebug)
		Debug.out("Load data for unselected FS pane " + fsName);

	    ostColNames = new String[ostMasterColNames.length];
	    ostData = new Object[1][ostMasterColNames.length];
	    ostColOn = new boolean[ostMasterColNames.length];
	
	    for (int i = 0; i < ostMasterColNames.length; i++) {
		ostColNames[i] = ostMasterColNames[i];

		if (i == 0)
		    ostData[0][0] = new String("OST_rgb235_1");
		else
		    ostData[0][i] = new String("123456");

		ostColOn[i] = true;
	    }
	}

	// The following block of code is used to initialize the number of
	// columns and the order of the columns as retrieved from the DB.
	// As the columns are reordered and deleted, these original values
	// can be used as a reference to calculate the order in which
	// the TableModel data array should be loaded.  11/28/2006
	if (ostDBColSeq == null) {
	    ostNumCols = ostColNames.length;
	    ostDBColSeq = new String[ostNumCols];
	    for (int i = 0; i < ostNumCols; i++) {
		//System.out.println(i + "  : " + ostColNames[i]);
		ostDBColSeq[i] = ostColNames[i];
	    }
	}

	if (localDebug)
	    Debug.out("Create JPane for hdr and JTable");


	setLayout(new BorderLayout()); // Goes in CENTER panel
	JPanel hdrPane = new JPanel(new BorderLayout(), false);

	if (useDB && selected)
	    ostTimestampLabel = new JLabel("OST      " + ostTimestamp.toString());
	else
	    ostTimestampLabel = new JLabel("No Timestamp Available");

	ostTimestampLabel.setHorizontalAlignment(JLabel.CENTER);
	hdrPane.setBackground(new Color(255,151,65));  //(Color.yellow);
	hdrPane.add(ostTimestampLabel, BorderLayout.NORTH);

	add(hdrPane, BorderLayout.NORTH);

	if (localDebug)
	    Debug.out("Create table model object.");

	ostModel = ostTableModelCreate(ostColNames);

	if (localDebug) {
	    Debug.out("Model created.");
	    Debug.out("Create JTable object for FS # " + fsName);
	}

	ostTable = new JTable(ostModel);

	if (localDebug)
	    Debug.out("Set Renderer for OST JTable");

	ostTable.setDefaultRenderer(Object.class, cellRenderer);

	CellListener cellListener = new CellListener("OST", -1, ostTable,
						     database, parentFS, ostMasterData);
	ostTable.addMouseListener(cellListener);

	if (localDebug)
	    Debug.out("Renderer set for OST JTable. Get table header.");

	JTableHeader tableHeader = ostTable.getTableHeader();
	tableHeader.addMouseListener(new OSTTableHdrListener(parentFS, ostTable));

	if (localDebug)
	    Debug.out("addMouseListener added to table header.");

	// Define the popup menu for the column 0 in this JTable
	ostC0Popup = new JPopupMenu();
	JMenuItem menuItem = new JMenuItem("Reset");
	menuItem.addActionListener(new OSTPopupListener(ostTable));
	ostC0Popup.add(menuItem);

	ostC0Popup.addSeparator();

	// Define the popup menu for the other columns in this JTable
	ostPopup = new JPopupMenu();
	menuItem = new JMenuItem("Hide");
	menuItem.addActionListener(new OSTPopupListener(ostTable));
	ostPopup.add(menuItem);

	menuItem = new JMenuItem("Reset");
	menuItem.addActionListener(new OSTPopupListener(ostTable));
	ostPopup.add(menuItem);

	ostPopup.addSeparator();

	if (localDebug)
	    Debug.out("setHeaderRenderer for column headers.");

	Enumeration e = ostTable.getColumnModel().getColumns();
	while (e.hasMoreElements()) {
	    //System.out.println("Set header renderer.");
	    ((TableColumn) e.nextElement()).setHeaderRenderer(columnHdrRenderer);
	}
	ostTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

	if (localDebug)
	    Debug.out("Create Scroll pane for view into JTable.");

	ostScrollPane = new JScrollPane(ostTable);

	//Debug.out("Dimesion ostMasterColNames at ");// + ostMasterColNames.length);
	ostColumnWidths = new int[ostMasterColNames.length];
	//Debug.out("Call initOSTColumnSizes.");
	initOSTColumnSizes(ostTable, ostModel);
	ostScrollPane.getViewport().setBackground(Color.black);

	//ostScrollPane.setPreferredSize(new Dimension(600, 700));
	//ostScrollPane.setMinimumSize(new Dimension(400, 700));
	//ostScrollPane.setMaximumSize(new Dimension(750, 700));
	ostScrollPane.setAlignmentX(RIGHT_ALIGNMENT);

	add(ostScrollPane, BorderLayout.CENTER);
	setBackground(Color.blue);

	// Suppress auto-create of columns from data model now that they're established.
	ostTable.setAutoCreateColumnsFromModel(false);

	if (localDebug)
	    Debug.out("ostPane definition completed.");

    }  // OSTPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Loads variable info for specific OST variables and initializes column names
     * to be displayed in the column headers.
     */

    public void loadVariableInfoArray() {
	vi = new Database.VariableInfo[ostColumnOrder.length];
	ostPlottableVars = new String[ostColumnOrder.length];
	varIds = new int[ostColumnOrder.length];
	try {

	    //vi = new Database.VariableInfo[] {
		//database.getVariableInfo("OST_VARIABLE_INFO", "READ_RATE"),
		//database.getVariableInfo("OST_VARIABLE_INFO", "WRITE_RATE"),
		//database.getVariableInfo("OST_VARIABLE_INFO", "PCT_CPU"),
		//database.getVariableInfo("OST_VARIABLE_INFO", "PCT_KBYTES"),
		//database.getVariableInfo("OST_VARIABLE_INFO", "PCT_INODES")
	    //};

	    for (int i = 0; i < ostColumnOrder.length; i++)
		vi[i] = database.getVariableInfo("OST_VARIABLE_INFO", ostColumnOrder[i]);

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

	for (int i = 0; i < ostColumnOrder.length; i++) {
	    if (localDebug)
		Debug.out(i + "  vi[i].variableLabel = " + vi[i].variableLabel);
	    for (int j = 0; j < vi.length; j++) {
		if (ostColumnOrder[i].equals(vi[j].variableName)) {
		    varIds[i] = j;  //vi[i].variableId;
		    ostMasterColNames[i+1] = vi[j].variableLabel;
		    ostPlottableVars[i] = vi[j].variableLabel;
		}
	    }
	}

    }  // loadVariableInfoArray


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return array of plottable variables associated with the OST panel.
     */

    public static String [] getPlottableVars() {

	return ostPlottableVars;

    }  // getPlottableVars


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Return master data array associated with the OST panel.
     */

    public  Object [][] getMasterData() {

	return ostMasterData;

    }  // getMasterData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Initailize the column widths for the OST table.
     *
     * @param table JTable containing OST data.
     *
     * @param model model for the OST JTable.
     */

    public void initOSTColumnSizes(JTable table, OSTTableModel model) {
	int rowCount = model.getRowCount();
	int colCount = model.getColumnCount();
	int [] colWidth = new int [colCount];
	TableColumn column = null;
	Component comp = null;

	if (localDebug)
	    Debug.out("Calculate Column widths for OST table " + 
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

		//if (localDebug)
		    //Debug.out(i + ", " + j);

		comp = table.getDefaultRenderer(model.getColumnClass(i)).
		    getTableCellRendererComponent(
		        table, model.getValueAt(j, i),
		        false, false, j, i);

		cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
	    }
	    if (localDebug)
		Debug.out("OST Col " + i + " Max Width = " + cellWidth);

	    column.setPreferredWidth(cellWidth+10);  //cellWidth);
	    //Debug.out("Assign " + cellWidth + " +10 to ostColumnWidths[" + i + "]");
	    ostColumnWidths[i] = cellWidth+10;
	}
	       
    }  // initOSTColumnSizes


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Reload the OST Panel.
     */

    void reload() {
	if (localDebug)
	    Debug.out("Get OST data");

	try {
	    getOSTData(database);
	} catch (Exception e) {
	    Debug.out("Exception caught while loading OST data.\n");
	    e.printStackTrace();
	    return;
	}

	if (ostTimestamp != null)
	    ostTimestampLabel.setText("OST      " + ostTimestamp.toString());
	else
	    ostTimestampLabel.setText("No timestamp found for this cycle");

	if (localDebug)
	    Debug.out("Reload OST data vector row count = " + ostData.length);

	synchronized(this.parentFS.synchObj) {
	    ostModel.setDataVector(ostData, ostMasterColNames);
	}

	if (localDebug)
	    Debug.out("OST setDataVector completed.");

	Enumeration e = ostTable.getColumnModel().getColumns();
	while (e.hasMoreElements()) {
	    //System.out.println("setHeaderRenderer " + icnt++);

	    TableColumn tc = ((TableColumn) e.nextElement());
	    tc.setHeaderRenderer(columnHdrRenderer);

	    if (ostColumnWidths != null) {
		String hdrString = (String)tc.getHeaderValue();
		//Debug.out("Table col hdr value = " + hdrString);
		int width = 58;
		for (int i = 0; i < ostMasterColNames.length; i++) {
		    if ((hdrString != null) && hdrString.equals(ostMasterColNames[i])) {
			width = ostColumnWidths[i];
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

	    //if (localDebug && (table.getModel() instanceof OSTTableModel))
	    //if ((table.getModel() instanceof OSTTableModel))
		//Debug.out("row = " + row + ",  column = " + column);

	    try {
		if ((ostSortCol != 0) &&
		    ostTable.getColumnName(column).equals(ostSortColName)) {
		    //ostColNames[Math.abs(ostSortCol)-1])) {

		    if (ostSortCol > 0) {
			//Debug.out("Adding \"^\" to table column header.");
			str += " ^";
		    } else {
			//Debug.out("Adding \"v\" to table column header.");
			str += " v";
		    }

		}
	    } catch (Exception e) {
		Debug.out("Exception caught while rendering header for OST table. Row = " +
			  row + ",  Column = " + column + ",  value = " +
			  value + "\n" + e.getMessage());
	    }

	    BufferedReader br = new BufferedReader(new StringReader(str));
	    String line;
	    Vector v = new Vector();
	    try {
		while ((line = br.readLine()) != null) {
		    //Debug.out("v.addElement(" + line + ")");
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
     * Class implementing the renderer for the OST table column headers.
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
	 * Get the renderer component for the OST table column header.
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

	    //if (localDebug && (table.getModel() instanceof OSTTableModel))
	    //if ((table.getModel() instanceof OSTTableModel))
		//Debug.out("row = " + row + ",  column = " + column);

	    try {
		if ((ostSortCol != 0) &&
		    ostTable.getColumnName(column).equals(ostSortColName)) {
		    //ostColNames[Math.abs(ostSortCol)-1])) {

		    if (ostSortCol > 0) {
			//Debug.out(ostSortCol + "\" ^\" on col # " +
			//column + "  " + ostSortColName);
			str += " ^";
		    } else {
			//Debug.out(ostSortCol + "\" v\" on col # " +
			//column + "  " + ostSortColName);
			str += " v";
		    }

		}
	    } catch (Exception e) {
		Debug.out("Exception caught while rendering header for OST table. Row = " +
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
     * Call the constructor for the OST table model.
     *
     * @param columnNames array column names to appear in the table.
     *
     * @return table model for the OST table.
     */

    private OSTTableModel ostTableModelCreate(String [] columnNames) {

	return new OSTTableModel(columnNames);

    }  // ostTableModelCreate


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class to define the table model for the OST data. Extends AbstractTableModel.
     */

    // Subclass DefaultableModel so that cells can be set to uneditable.
    public class OSTTableModel extends AbstractTableModel {
				      //implements TableModelListener {
	public String [] columnNames = null;
	public Object [][] data = null;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constrcutor for the OST table model.
	 *
	 * @param columnNames array of column names to appear in the table.
	 */

	protected OSTTableModel(String [] columnNames) {

	    if (columnNames == null)
		if (localDebug) {
		    Debug.out("columnNames array arg = null");
		    Debug.out("# of columnNames = " + columnNames.length);
		}

	    this.data = ostData;
	    this.columnNames = columnNames;

	    if (localDebug) {
		for (int i = 0; i < columnNames.length; i++)
		    Debug.out(i + " : " + columnNames[i]);
	    }

	}  // OSTTableModel


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
	 * Set the data vector for the OST table.
	 *
	 * @param idata Array containing the values for the OST table cells.
	 *
	 * @param colNames array containing the names of the columns for the OST table.
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
	    synchronized(OSTPanel.this.parentFS.synchObj) {
		Arrays.sort(data, new ColumnComparator(sortCol));
		//fireTableCellUpdated(-1, Math.abs(sortCol)-1);
		TableModelEvent tabModelEvent = new TableModelEvent(this, 0, data.length);
		//Debug.out("After sort, fireTableChanged for event " +
			  //tabModelEvent.toString());
		fireTableChanged(tabModelEvent);
	    }

	}  // sort

    }  // OSTTableModel



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
     * Class that handles the OST table header mouse button clicks.
     */

    public class OSTTableHdrListener extends MouseAdapter {

	private FileSystemPanel parent;
	private JTable table;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the class that handles the OST table header mouse button clicks.
	 *
	 * @param parent the FileSystem object to which this OSTPanel belongs.
	 *
	 * @param table the JTable containing the OST data.
	 */

	public OSTTableHdrListener(FileSystemPanel parent, JTable table) {
	    super();

	    this.parent = parent;
	    this.table = table;

	}  // OSTTableHdrListener
	

	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for mouse button release events in the OST table column header.
	 *
	 * @param e the MouseEvent causing the handler to be invoked.
	 */

	public void mouseReleased(MouseEvent e) {
	    TableColumnModel columnModel = table.getColumnModel();
	    int viewColumn = columnModel.getColumnIndexAtX(e.getX());
	    int buttNum = e.getButton();

	    int colCount = ostTable.getColumnCount();
	    for (int j = 0; j < colCount; j++) {
		TableColumn tc = columnModel.getColumn(j);
		int colWidth = tc.getWidth();
		String cName = ostTable.getColumnName(j);
		for (int i = 0; i < ostMasterColNames.length; i++) {
		    if (ostMasterColNames[i].equals(cName)) {
			ostColumnWidths[i] = colWidth;
			//System.out.println("OST column " + cName + " width set to " + colWidth);
			break;
		    }
		}
	    }
	    //table.getTableHeader().repaint();


	    if ((buttNum == 3) && (viewColumn != 0)) {
		if (localDebug)
		    System.out.println("OST Right mouse button Release detected in col " +
				       viewColumn);

		ostActionCol = viewColumn;

		if (viewColumn != 0) {
		    showPopup(e);
		} else {
		    showC0Popup(e);
		}
	    }

	}  // mouseReleased


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handler for mouse button click events in the OST table column header.
	 *
	 * @param e the MouseEvent causing the handler to be invoked.
	 */

	public void mouseClicked(MouseEvent e) {

	    TableColumnModel columnModel = table.getColumnModel();
	    int viewColumn = columnModel.getColumnIndexAtX(e.getX());
	    int buttNum = e.getButton();
	    if ((viewColumn > ostNumCols) || (viewColumn < 0))
		return;

	    int dataModelColindx = toModel(table, viewColumn);
	    if (dataModelColindx == -1)
		return;

	    if (localDebug)
		System.out.println("Mouse Click event detected in OST Table column " +
				   viewColumn + "\nButton # " + buttNum);

	    if (buttNum == 1) {
		String sortID = ostTable.getColumnName(viewColumn);
		String lastSortColName = ostSortColName;
		int lastSortDir = 1;
		if (ostMasterSortCol < 0)
		    lastSortDir = -1;
		ostSortColName = sortID;
		int sortColumn = 0;
		for (int i = 0; i < ostColNames.length; i++) {
		    if (ostColNames[i].equals(sortID)) {
			//Debug.out("\nsortID " + sortID + " <==> ostColNames[" + i + "] " + ostColNames[i]);
			sortColumn = i;  //viewColumn;
			//System.out.println("Sorting on column  " + i + " - " + sortID);
			break;
		    }
		}
		ostSortCol = sortColumn + 1;
		if ((lastSortColName != null) && (lastSortColName.equals(ostSortColName)))
		    ostSortCol = ostSortCol * (-lastSortDir);

		//System.out.println("Last Sort Col = " + lastSortColName + "  new Sort Col = " +
				   //ostSortColName + "  last sort dir = " + lastSortDir);
		if (localDebug)
		    System.out.println("OST sort : " + ostSortCol + " = " +
				       ostColNames[Math.abs(ostSortCol)-1]);


		// Calculate the column from the master data array
		sortColumn = 0;
		for (int i = 0; i < ostMasterColNames.length; i++) {
		    if (ostMasterColNames[i].equals(sortID)) {
			sortColumn = i;
			//System.out.println("Set sorting for Master column  " + i + " - " + sortID);
			break;
		    }
		}
		ostMasterSortCol = sortColumn + 1;
		if (ostSortCol < 0)
		    ostMasterSortCol = -ostMasterSortCol;

		if (localDebug)
		    System.out.println("OST Master sort : " + ostMasterSortCol + " = " +
				       ostMasterColNames[Math.abs(ostMasterSortCol)-1]);
	    
		// Sort on selected row
		if (ostSortCol != 0) {
		    //Debug.out("Do sort for ostSortCol = " + ostSortCol);
		    //parentFS.getParentOf().postponeRefresh(1000); //  msecs
		    ((OSTTableModel)this.table.getModel()).sort(ostSortCol);
		    table.getTableHeader().repaint();
		}
	    } else if (buttNum == 3) {
		if (localDebug)
		    System.out.println("OST Right mouse button click detected in col " +
				       viewColumn);

		ostActionCol = dataModelColindx;

		if (dataModelColindx != 0) {
		    showPopup(e);
		} else {
		    showC0Popup(e);
		}
	    }
	}  // mouseClicked


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Display the OST table column > 0 popup menu.
	 */

       private void showPopup(MouseEvent e) {

	   ostPopup.show(e.getComponent(), e.getX(), e.getY());

       }  // showPopup


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Display the OST table column 0 popup menu.
	 */

       private void showC0Popup(MouseEvent e) {

	   ostC0Popup.show(e.getComponent(), e.getX(), e.getY());

       }  // showC0Popup
	
    }  // OSTTableHdrListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class that implements the ActionListener for the OST JTable
     */

    public class OSTPopupListener implements ActionListener {

	private JTable table;


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the ActionListener being implemented.
	 */

	public OSTPopupListener(JTable table) {
	    super();

	    this.table = table;

	}  // OSTPopupListener


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * The action handler for the OST table button clicks.
	 *
	 * @param e the ActionEvent that the listener is notified of.
	 */

	public void actionPerformed(ActionEvent e) {

	    //parentFS.getParentOf().resetRefresh();

	    if (localDebug)
		System.out.println("ActionEvent detected in OST popup \n" +
			       e.getActionCommand() + " on Column # " +
			       ostActionCol);

	    if (e.getActionCommand().equals("Hide")) {
		// Get column name and use this to identify the column number to be deleted.
		// That is the index in ostColOn to set to false. The value in ostActionCol
		// is the one to use for the ostTable calls.

		String cName = ostTable.getColumnName(ostActionCol);
		for (int i = 0; i < ostColNames.length; i++) {
		    if (ostColNames[i].equals(cName)) {
			//Debug.out("Set ostColOn[" + i + "] to false.");
			ostColOn[i] = false;
			//System.out.println("Setting orig column # " + i + " to false.");
			break;
		    }
		}
		TableColumnModel ostTableColModel = ostTable.getColumnModel();
		TableColumn hideCol = ostTableColModel.getColumn(ostActionCol);
		


		// Add removed column to hiddenColumns ArrayList
		hiddenColumns.ensureCapacity(nHiddenCols+1);
		hiddenColumns.add(nHiddenCols++, hideCol);



		if (localDebug)
		    Debug.out("Removing column to removeColumn # " + ostActionCol);

		ostTableColModel.removeColumn(hideCol);
		ostNumCols--;

		JMenuItem menuItem = new JMenuItem("Restore " + cName);
		menuItem.addActionListener(new OSTPopupListener(ostTable));
		ostPopup.add(menuItem);

		menuItem = new JMenuItem("Restore " + cName);
		menuItem.addActionListener(new OSTPopupListener(ostTable));
		ostC0Popup.add(menuItem);

	    } else if(e.getActionCommand().startsWith("Restore ")) {
		String command = e.getActionCommand();
		//Debug.out("Restore menuItem selected." + command);
		String restoreColName = command.substring(8);

		// Add the column back into the table to the right of column
		// where the action took place.
		//Debug.out("Restoring columnn " + restoreColName + " at column # " +
			  //ostActionCol);
		int restoreCol = 0;
		while (!ostMasterColNames[restoreCol].equals(restoreColName))
		    restoreCol++;

		if (restoreCol >= ostMasterColNames.length) {
		    Debug.out("Error matching column names for restoration.");
		    return;
		}
		//Debug.out("Restoring column " + ostMasterColNames[restoreCol]);

		// Locate coulmn to be added back in the hiddenColumns ArrayList
		ListIterator it = hiddenColumns.listIterator();

		while (it.hasNext()) {
		    TableColumn column = (TableColumn)it.next();
		    if (((String)(column.getHeaderValue())).equals(restoreColName)) {
			ostTable.getColumnModel().addColumn(column);
			it.remove();
			nHiddenCols--;
			break;
		    }
		}

		ostModel.fireTableDataChanged();

		ostColOn[restoreCol] = true;
		ostNumCols++;


		// Remove restore menu item from  Popup menu.
		int cCount = ostPopup.getComponentCount();
		for (int i = 3; i < cCount; i++) {
		    Component comp = ostPopup.getComponent(i);
		    String compText = ((AbstractButton)comp).getText();
		    //System.out.println("Component # " + i + " = " + compText);
		    if (command.equals(((AbstractButton)comp).getText())) {
			ostPopup.remove(i);
			break;
		    }
		}
		cCount = ostC0Popup.getComponentCount();
		for (int i = 2; i < cCount; i++) {
		    Component comp = ostC0Popup.getComponent(i);
		    String compText = ((AbstractButton)comp).getText();
		    //System.out.println("C0 Component # " + i + " = " + compText);
		    if (command.equals(((AbstractButton)comp).getText())) {
			ostC0Popup.remove(i);
			break;
		    }
		}

	    } else if(e.getActionCommand().equals("Reset")) {

		// Locate coulmn to be added back in the hiddenColumns ArrayList
		ListIterator it = hiddenColumns.listIterator();

		while (it.hasNext()) {
		    TableColumn column = (TableColumn)it.next();
		    ostTable.getColumnModel().addColumn(column);
		    it.remove();
		    nHiddenCols--;
		}

		for (int i = 0; i < ostColOn.length; i++)
		    ostColOn[i] = true;

		ostNumCols = ostMasterColNames.length;

		// Move TableColumns to the original order.
		for (int i = 0; i < ostMasterColNames.length; i++) {
		    int istrt = i;
		    for (int j = istrt; j < ostMasterColNames.length; j++) {
			String tcn = ostTable.getColumnName(j);
			if (tcn.equals(ostMasterColNames[i])) {
			    ostTable.moveColumn(j, i);
			}
		    }
		}

		if (localDebug)
		    Debug.out("Remove menu items from popup menus.");
		// Remove JPopup menuitems for the restored columns.
		int cCount = ostPopup.getComponentCount();
		for (int i = 3; i < cCount; i++) {
		    Component comp = ostPopup.getComponent(3);
		    if (((AbstractButton)comp).getText().startsWith("Restore "))
			ostPopup.remove(3);
		}

		if (localDebug)
		    Debug.out("Remove menu items from C0 popup menu.");
		// Remove JPopup menuitems for the restored columns.
		cCount = ostC0Popup.getComponentCount();
		for (int i = 2; i < cCount; i++) {
		    Component comp = ostC0Popup.getComponent(2);
		    if (((AbstractButton)comp).getText().startsWith("Restore "))
			ostC0Popup.remove(2);
		}

		ostModel.fireTableDataChanged();

	    }

	}  // actionPerformed

    }  // OSTPopupListener


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to render the OST table cells.
     */

    class OSTCellRenderer extends DefaultTableCellRenderer {

	Color [] cellColor = new Color[] {
	    Color.black,
	    Color.red,    // was gray,  changee on 5/25/07
	    Color.yellow,
	    Color.red
	};


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for cell renderer for OST table.
	 */

	OSTCellRenderer() {
	    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
	}  // OSTCellRenderer


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
	
	    if (varindx < 0)
		return Color.black;

	    try {
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

    }  // OSTCellRenderer


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Load OST data from database for most recent timestamp.
     *
     * @param database Database object used to load data with.
     */

    protected void getOSTData(Database database) throws Exception 
    {
	if (localDebug)
	    Debug.out("\nEntering getOSTData\nGet data for latest timestamp.");

	stale = false;
	troubled = false;
	critical = false;

	//
	// Get latest TS_ID from TIMESTAMP_ID table.
	//

	TimeInfo timeinfo = database.getLatestTimeInfo("OST_DATA");
	ostTimestamp = timeinfo.timestamp;

	//ostTimestamp = new Timestamp(new java.util.Date().getTime());

	if (localDebug)
	    Debug.out("timestamp = " + ostTimestamp);



	//
	// Get OST data for latest timestamp.
	//

	if (localDebug)
	    Debug.out("Get OST data from DB");

	OstData ostDBData = null;
	try {
	    ostDBData = database.getCurrentOstData();
	    if (ostDBData == null)
		throw new Exception("null return from Database.getCurrentOstData()");
	} catch (Exception e) {
	    Debug.out("Exception detected while loading current OST data.\n" +
		      e.getMessage());

	    zeroLoadOSTData();
	    return;
	}

	//
	// Get column headers.
	//

	String [] ostColTypes = {"STRING", "DOUBLE", "DOUBLE",
				 "DOUBLE", "DOUBLE", "DOUBLE"};


	if (localDebug)
	    Debug.out("Check the current column order.");


	ostColNames = new String[ostMasterColNames.length];
	for (int i = 0; i < ostMasterColNames.length; i++)
	    ostColNames[i] = ostMasterColNames[i];

	ostNumCols = ostMasterColNames.length;





	int aSize = 0;
	boolean [] ammaFlags = null;
	try {
	    aSize = ostDBData.getSize();

	    //String [] lines = new String[asize];
	    ostMasterData = new Object[aSize+4][ostMasterColNames.length];
	    ostMasterColorIdx = new int[aSize][ostMasterColNames.length];
	    ostData = new Object[aSize+4][ostNumCols];
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
		ostMasterData[i][0] = ostDBData.getOstName(i);

		// Check Timestamp  & set troubled flag if stale
		Timestamp ots = ostDBData.getTimestamp(i);
		//if (ots == null)
		    //stale = true;
		fVal = ostDBData.getReadRate(i);
		if (fVal == null)
		    ostMasterData[i][1] = fVal; //new Double(0.0);
		else
		    ostMasterData[i][1] = new Double(ostDBData.getReadRate(i).doubleValue());

		fVal = ostDBData.getWriteRate(i);
		if (fVal == null)
		    ostMasterData[i][2] = fVal; //new Double(0.0);
		else
		    ostMasterData[i][2] = new Double(ostDBData.getWriteRate(i).doubleValue());

		fVal = ostDBData.getPctCpu(i);
		if (fVal == null)
		    ostMasterData[i][3] = fVal; //new Double(0.0);
		else
		    ostMasterData[i][3] = new Double(ostDBData.getPctCpu(i).doubleValue());

		fVal = ostDBData.getPctKbytes(i);
		if (fVal == null)
		    ostMasterData[i][4] = fVal; //new Double(0.0);
		else
		    ostMasterData[i][4] = new Double(ostDBData.getPctKbytes(i).doubleValue());

		fVal = ostDBData.getPctInodes(i);
		if (fVal == null)
		    ostMasterData[i][5] = fVal; //new Double(0.0);
		else
		    ostMasterData[i][5] = new Double(ostDBData.getPctInodes(i).doubleValue());

		if (test && (fVal != null)) {
		    if (i == 0)
			ostMasterData[i][3] = new Double(95.);
		    else if (i == 3)
			ostMasterData[i][4] = new Double(101.);
		    else if (i == 7)
			ostMasterData[i][5] = new Double(101.);
		}

		ammaFlags[i] = true;

		setCellColors(i);

		//if (localDebug)
		//System.out.println(ostNames[i] + " : " + reads[i] + " : " +
		//writes[i] + " : " + cpuPercs[i] + " : " + spacePercs[i]);

	    }  // End of "for (int i = 0; i < aSize; i++) {"


	    //-------------------------------------------------------------------
	    // Get the Aggregate, Max, Min & Avg values for each column from DB

	    // Column 1
	    ostMasterData[aSize][0] = new String("AGGREGATE");
	    ostMasterData[aSize+1][0] = new String("MAXIMUM");
	    ostMasterData[aSize+2][0] = new String("MINIMUM");
	    ostMasterData[aSize+3][0] = new String("AVERAGE");

	    // Read Rate
	    ostMasterData[aSize][1] = ostDBData.getReadRateSum(ammaFlags);
	    ostMasterData[aSize+1][1] = ostDBData.getReadRateMax(ammaFlags);
	    ostMasterData[aSize+2][1] = ostDBData.getReadRateMin(ammaFlags);
	    ostMasterData[aSize+3][1] = ostDBData.getReadRateAvg(ammaFlags);

	    // Write Rate
	    ostMasterData[aSize][2] = ostDBData.getWriteRateSum(ammaFlags);
	    ostMasterData[aSize+1][2] = ostDBData.getWriteRateMax(ammaFlags);
	    ostMasterData[aSize+2][2] = ostDBData.getWriteRateMin(ammaFlags);
	    ostMasterData[aSize+3][2] = ostDBData.getWriteRateAvg(ammaFlags);

	    // Percent CPU
	    ostMasterData[aSize][3] = new String("*****");  //ostDBData.getPctCpuSum(ammaFlags);
	    ostMasterData[aSize+1][3] = ostDBData.getPctCpuMax(ammaFlags);
	    ostMasterData[aSize+2][3] = ostDBData.getPctCpuMin(ammaFlags);
	    ostMasterData[aSize+3][3] = ostDBData.getPctCpuAvg(ammaFlags);

	    // Percent KBytes
	    ostMasterData[aSize][4] = new String("*****");  //ostDBData.getPctKbytesSum(ammaFlags);
	    ostMasterData[aSize+1][4] = ostDBData.getPctKbytesMax(ammaFlags);
	    ostMasterData[aSize+2][4] = ostDBData.getPctKbytesMin(ammaFlags);
	    ostMasterData[aSize+3][4] = ostDBData.getPctKbytesAvg(ammaFlags);

	    // Percent Inodes
	    ostMasterData[aSize][5] = new String("*****");  //ostDBData.getPctInodesSum(ammaFlags);
	    ostMasterData[aSize+1][5] = ostDBData.getPctInodesMax(ammaFlags);
	    ostMasterData[aSize+2][5] = ostDBData.getPctInodesMin(ammaFlags);
	    ostMasterData[aSize+3][5] = ostDBData.getPctInodesAvg(ammaFlags);

	    //-------------------------------------------------------------------

	} catch (Exception e) {
	    Debug.out("error processing data from DB.\n" + e.getMessage());

	    System.exit(1);
	}

	try {

	    // Sort if necessary
	    if (ostSortCol != 0) {
		if (localDebug) {
		    System.out.println("Sort requested for column # " + ostSortCol);
		    //printArray(ostData);
		}

		Arrays.sort(ostMasterData, new ColumnComparator(ostMasterSortCol));

		//if (localDebug)
		    //printArray(ostData);

	    }

	    // Transfer sorted data to the array used for the data model
	    for (int i = 0; i < aSize+4; i++) {

		for (int j = 0; j < ostMasterColNames.length; j++) {
		    //System.out.println("CCC : " + i + ", " + j + " : " + ostMasterData[i][j].toString());

		    ostData[i][j] = ostMasterData[i][j];
		}
	    }

	} catch (Exception e) {
	    Debug.out("error building up OST 2-D Object array.\n" + e.getMessage());

	    System.exit(1);
	}

	if (localDebug)
	    Debug.out("Done loading OST data from DB.");

	return;

    }  // getOSTData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Scan the master OST data array and apply the variable threshold values to determine
     * the color to be used for rendering the aggregate, min, max & average rows of the JTable.
     *
     * @param data row to be scanned.
     */

    public void setCellColors(int irow) {
	boolean rowCritical = false;
	boolean rowTroubled = false;
	boolean rowStale = false;
	
	try {
	    for (int i = 1; i < ostMasterData[i].length; i++) {
		float fVal = (float)0;
		int varidx = varIds[i-1];
		if (ostMasterData[irow][i] == null) {
		    rowStale = true;
		}
		if (vi[varidx].threshType == 1) {
		    Object val = ostMasterData[irow][i];
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
		    Object val = ostMasterData[irow][i];
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
		    Object val = ostMasterData[irow][i];
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
		    Object val = ostMasterData[irow][i];
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
	    }  // for (int i = 1; i < ostMasterData[i].length; i++)
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
     * Method used to load dummy data for OST when DB is not available. This should
     * never be invoked. It has been left in for historical reasons.
     */

    void zeroLoadOSTData () {
	final String [] aTypes = {"STRING", "DOUBLE", "DOUBLE", "DOUBLE",
				  "DOUBLE", "DOUBLE"};

	if (ostMasterData == null) {
	    ostMasterData = new Object[5][ostMasterColNames.length];

	    for (int i = 0; i < ostMasterColNames.length; i++) {
		if ("STRING".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostMasterData[j][i] = "FAILURE";
		}		    
		if ("DOUBLE".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostMasterData[j][i] = new Double("0.0");
		}
		if ("FLOAT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostMasterData[j][i] = new Float("0.0");
		}
		if ("INTEGER".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostMasterData[j][i] = new Integer("0");
		}
		if ("BIGINT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostMasterData[j][i] = new Long("0");
		}
	    }
	}

	if (ostData == null) {
	    ostData = new Object[5][ostNumCols];

	    for (int i = 0; i < ostColNames.length; i++) {
		if ("STRING".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostData[j][i] = "FAILURE";
		}		    
		if ("DOUBLE".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostData[j][i] = new Double("0.0");
		}
		if ("FLOAT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostData[j][i] = new Float("0.0");
		}
		if ("INTEGER".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostData[j][i] = new Integer("0");
		}
		if ("BIGINT".equals(aTypes[i])) {
		    for (int j = 0; j < 5; j++)
			ostData[j][i] = new Long("0");
		}
	    }

	}

    }  // zeroLoadOSTData


    //////////////////////////////////////////////////////////////////////////////

    /**
     * OLD, out of date, fake data loading method. Left in file in case you need
     * to add fake data capability in the future. It may be useful and it may be
     * junk. UAYOR.
     */

    protected void getOSTDataFake(String fsName) throws Exception 
    {

	String [] currentColOrder;

	
	//if (ostNumCols > 0) {
	    //ostColNames = new String[ostNumCols];
	//} else {
	    ostColNames = new String[ostMasterColNames.length];
	    for (int i = 0; i < ostMasterColNames.length; i++)
		ostColNames[i] = ostMasterColNames[i];
	//}

	// Calculate the ordering of the columns based on the original ordering.

	ostNumCols = ostMasterColNames.length;

	ostName = null;

	//System.out.println("Get file system OST data for FS = " + fsName);
	InputStreamReader inStrRdr = null;;
	BufferedReader buffRdr= null;
	try {
	    FileInputStream in = new FileInputStream("ost.dat");
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
			ostName = tokens[1];
			first = false;
		    } else {
			throw new Exception("Format error in ost data.");
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
	    Debug.out("Number of lines read from ost.dat = " + aSize);
	    Debug.out("Number of columns in 2-D array = " + ostNumCols);
	    //ostData = new String[aSize];

	    String [] lines = new String[aSize];
	    ostMasterData = new Object[aSize][ostMasterColNames.length];
	    ostData = new Object[aSize][ostNumCols];

	    v.copyInto(lines);

	    for (int i = 0; i < aSize; i++) {
		String [] vals = lines[i].split(" ");
		int idxN = 1;

		if ("AGGREGATE".equals(vals[0])) {
		    ostMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("MAXIMUM".equals(vals[0])) {
		    ostMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("MINIMUM".equals(vals[0])) {
		    ostMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else if ("AVERAGE".equals(vals[0])) {
		    ostMasterData[i][0] = new String(vals[0]);
		    idxN = 1;
		} else {
		    ostMasterData[i][0] = new String(vals[0]);
		}

		ostMasterData[i][1] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ostMasterData[i][2] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ostMasterData[i][3] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ostMasterData[i][4] = new Float(Float.valueOf(vals[idxN++]).floatValue());
		ostMasterData[i][5] = new Float(Float.valueOf(vals[idxN++]).floatValue());

		//if (localDebug)
		//System.out.println(ostNames[i] + " : " + reads[i] + " : " +
		//writes[i] + " : " + cpuPercs[i] + " : " + spacePercs[i]);
	    }

	    // Sort if necessary
	    if (ostSortCol != 0) {
		System.out.println("Sort requested for column # " + ostSortCol);
		//printArray(ostData);
		Arrays.sort(ostMasterData, new ColumnComparator(ostMasterSortCol));
		//printArray(ostData);
	    }

	    // Transfer sorted data to the array used for the data model
	    for (int i = 0; i < aSize; i++) {
		for (int j = 0; j < ostMasterColNames.length; j++)
		    ostData[i][j] = ostMasterData[i][j];
	    }

	} catch (Exception e) {
	    Debug.out("error processing input vector.\n" + e.getMessage());
	}

	System.out.println("Done generating data for ost pane.");

    }  // getOSTDataFake

}  // OSTPanel
