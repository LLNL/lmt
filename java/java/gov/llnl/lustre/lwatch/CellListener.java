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

// Lifted from Neale Smith's TableView.java class from
// within the hopper package.

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

// Database imports
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.*;

import gov.llnl.lustre.lwatch.util.Debug;


//////////////////////////////////////////////////////////////////////////////

/**
 * Class used to implement mouse listener that controls history plot activation.
 */

class CellListener extends MouseAdapter implements MouseMotionListener {

    private int state = 0;
    private int startRow;
    private int startCol;
    private int lastRow;
    private int lastCol;
    //private RectangularBitmap lastMap = null;
    //private RectangularBitmap map = null;
    private MouseEvent pressEvent = null;
    private long lastClickTime = 0;

    protected String dataType;
    protected int id;
    protected JTable table;
    protected Database database;
    protected FileSystemPanel fileSys;
    protected String fsName;
    protected Object [][] masterData;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for CellListener class. Used for panels with no sub-panels.
     *
     * @param type data type of table cell where MouseEvent occurred. (OST & OSS).
     *
     * @param table JTable where MouseEvent occurred.
     *
     * @param db Database associated with the table.
     *
     * @param fsp FileSystemPanel containing the table.
     *
     * @param data data array assocaited with the table.
     */

    public CellListener(String type, JTable table, Database db,
			FileSystemPanel fsp, Object [][] data) {
	super();

	this.dataType = type;
	this.table = table;
	this.database = db;
	this.fileSys = fsp;
	this.masterData = data;
	this.fsName = fsp.getFSName();
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for CellListener class.  Used for Panels with sub-panels. 
     *
     * @param type data type of table cell where MouseEvent occurred. (MDS & RTR).
     *
     * @param id MDS identifier associated with the MDS table.
     *
     * @param table JTable where MouseEvent occurred.
     *
     * @param db Database associated with the table.
     *
     * @param fsp FileSystemPanel containing the table.
     *
     * @param data data array assocaited with the table.
     */

    // Another constructor with id arg to allow specification of
    // mdsId or router group number.
    public CellListener(String type, int id, JTable table, Database db,
			FileSystemPanel fsp, Object [][] data) {
	super();

	this.dataType = type;
	this.id = id;
	this.table = table;
	this.database = db;
	this.fileSys = fsp;
	this.masterData = data;
	this.fsName = fsp.getFSName();
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Handler method for mousePressed event. Noop.
     *
     * @param e MouseEvent causing this handler method to be invoked.
     */

    public void mousePressed(MouseEvent e) {
	/*****
	//table.setAutoscrolls(true);
	//resetFocus();
 	Point p = new Point(e.getX(), e.getY());
	//Rectangle cellRect = table.getCellRect(0, 0, true);
	//if (cellRect.height == 0)
	    //return;
	//int row = p.y/cellRect.height;
	//int col = p.x/cellRect.width;

 	int cCount = table.getColumnCount();
 	int row = -1;
 	int col = -1;
	int xAccum = 0;
	int i = 0;
	while ((xAccum < p.x) && (i < cCount)) {
	    Rectangle cellRect = table.getCellRect(0, i, true);
	    if (cellRect.height == 0)
		return;

	    if ((xAccum < p.x) && ((xAccum+cellRect.width) > p.x)) {
		row = p.y/cellRect.height;
		col = i;
		break;
	    }
	    xAccum += cellRect.width;
	    i++;
	}
	if ((row < 0) || (col < 0))
	    return;
	    
	//System.out.println("mousePressed event from row, column = " +
			   //row + ", " + col);
        *****/
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Handler method for mouseReleased event. NOOP.
     *
     * @param e MouseEvent causing this handler method to be invoked.
     */

    public void mouseReleased(MouseEvent e) {

	state = 0;

    }  // mouseReleased


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Handler method for mouseClicked event.
     *
     * @param e MouseEvent causing this handler method to be invoked.
     */

    public void mouseClicked(MouseEvent e) {
	long currentClickTime = e.getWhen();
	long clickInterval = currentClickTime-lastClickTime;
	lastClickTime = currentClickTime;
	Point p = new Point(e.getX(), e.getY());
	final String [] aggRowStringEquiv = {"Agg", "Max", "Min", "Avg"};



	int cCount = table.getColumnCount();
	int row = -1;
	int col = -1;
	int xAccum = 0;
	int i = 0;
	while ((xAccum < p.x) && (i < cCount)) {
	    Rectangle cellRect = table.getCellRect(0, i, true);
	    if (cellRect.height == 0)
		return;

	    if ((xAccum < p.x) && ((xAccum+cellRect.width) > p.x)) {
		row = p.y/cellRect.height;
		col = i;
		break;
	    }
	    xAccum += cellRect.width;
	    i++;
	}

	if (row < 0 || col < 0) {
	    state = 0;
	    return;
	}

	if (table.getColumnName(col).endsWith("Name"))
	    return;


	//System.out.println("mouseClicked event from row, column = " +
			   //row + ", " + col);

	if (SwingUtilities.isLeftMouseButton(e) &&
	    clickInterval< 250) {  //Prefs.DOUBLE_CLICK_INTERVAL.getValue()) {
	    //System.out.println("Double click of left mouse button in cell (" +
			       //row + ", " + col + ")");

	    final JFrame f = new JFrame(this.fsName + " Time History plot");

	    f.addWindowListener(new WindowAdapter() {
		    public void windowClosing(WindowEvent e) {
			//Debug.out("[windowClosing]");

			f.dispose();
		    }
		});

	    //System.out.println("fileSys = " + this.fileSys);
	    //System.out.println("dataType = " + this.dataType);
	    //System.out.println("masterData = " + masterData);
	    //System.out.println("ColumnName = " + table.getColumnName(col));

	    // Calculate the MDS, OST or RTR Id which may have been moved from column 0.

	    boolean match = false;
	    int aggIndex = -1;
	    for (int j = 0; j < masterData[0].length; j++) {
		for (int k = 0; k < masterData.length-4; k++) {
		    if (table.getValueAt(row,j).equals(masterData[k][0])) {
			match = true;
			row = k;
			break;
		    }
		}
		if (match) break;
		if ("AGGREGATE".equals(table.getValueAt(row,j))) {
		    match = true;
		    aggIndex = 0;
		    break;
		}
		if ("MAXIMUM".equals(table.getValueAt(row,j))) {
		    match = true;
		    aggIndex = 1;
		    break;
		}
		if ("MINIMUM".equals(table.getValueAt(row,j))) {
		    match = true;
		    aggIndex = 2;
		    break;
		}
		if ("AVERAGE".equals(table.getValueAt(row,j))) {
		    match = true;
		    aggIndex = 3;
		    break;
		}
	    }
	    if (!match) {  // || ("RTR".equals(this.dataType) && (aggIndex > -1))) {
		//Debug.out("\nUnable to identify row Id.\n");
		return;
	    }

	    if (("OST".equals(this.dataType) || "RTR".equals(this.dataType)) && (aggIndex > -1)) {
		PlotFrame2 pf = new PlotFrame2(f,
					       this.fileSys,
					       this.database,
					       this.dataType,
					       this.id,
					       aggRowStringEquiv[aggIndex],
					       this.table.getColumnName(col),
					       fileSys.parent.prefs,
					       true);
		pf.buildUI(f.getContentPane());
		f.pack();
		f.setVisible(true);

	    } else if ("OST".equals(this.dataType) || "RTR".equals(this.dataType)) {
		PlotFrame2 pf = new PlotFrame2(f,
					       this.fileSys,
					       this.database,
					       this.dataType,
					       this.id,
					       (String)this.masterData[row][0],
					       this.table.getColumnName(col),
					       fileSys.parent.prefs,
					       false);
		pf.buildUI(f.getContentPane());
		f.pack();
		f.setVisible(true);

		if (pf.updateLive) {
		    pf.refreshPlotFrame();
		}
	    }
	}

	state = 0;

    }  // mouseClicked


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Handler method for mouseDragged event. NOOP.
     *
     * @param e MouseEvent causing this handler method to be invoked.
     */

    public void mouseDragged(MouseEvent e) {

    }  // mouseDragged


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Handler method for mouseMoved event. NOOP.
     *
     * @param e MouseEvent causing this handler method to be invoked.
     */

    public void mouseMoved(MouseEvent e) {

    }  // mouseMoved
}

