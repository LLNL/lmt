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

import java.util.Arrays;
import java.util.Comparator;

import gov.llnl.lustre.lwatch.util.Debug;



/**
 * Comparator utility class used for sorting JTable columns.
 */

class ColumnComparator implements Comparator
{
    public final static boolean localDebug = 
	Boolean.getBoolean("LWatch.debug");

    private int sortColumn = 0;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for ColumnComparator class.
     *
     * @param sortCol column number to sort.
     */

    public ColumnComparator(int sortCol) {
	super();

	if (localDebug)
	    Debug.out("Set Comparator sort column to " + sortCol);

	this.sortColumn = sortCol;
	    
    }


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for ColumnComparator class.
     *
     * @param obj1 first of the two objects to compare.
     *
     * @param obj2 second of the two objects to compare.
     *
     * @return integer value specifying result of the comparison of the two objects.
     */

    public int compare(Object obj1, Object obj2)
    {
	int result = 0;

	if (sortColumn == 0)
	    return -1;

	Object[] objArray1 = (Object[]) obj1;
	Object[] objArray2 = (Object[]) obj2;

	int columnIdx = Math.abs(sortColumn) - 1;

	/* Sort on columnIdx element of each array */
	if ((objArray1[0] instanceof String) &&
	    "AVERAGE".equals((String)objArray1[0])) {
	    return 0;  //1;
	} else if ((objArray2[0] instanceof String) &&
		   "AVERAGE".equals((String)objArray2[0])) {
	    return 0;  //-1;
	} else if ((objArray1[0] instanceof String) &&
		   "MINIMUM".equals((String)objArray1[0])) {
	    return 0;  //1;
	} else if ((objArray2[0] instanceof String) &&
		   "MINIMUM".equals((String)objArray2[0])) {
	    return 0;  //-1;
	} else if ((objArray1[0] instanceof String) &&
		   "MAXIMUM".equals((String)objArray1[0])) {
	    return 0;  //1;
	} else if ((objArray2[0] instanceof String) &&
		   "MAXIMUM".equals((String)objArray2[0])) {
	    return 0;  //-1;
	} else if ((objArray1[0] instanceof String) &&
		   "AGGREGATE".equals((String)objArray1[0])) {
	    return 0;  //1;
	} else if ((objArray2[0] instanceof String) &&
		   "AGGREGATE".equals((String)objArray2[0])) {
	    return 0;  //-1;
	} else if (objArray1[columnIdx] == null) {
	    result = -1;
	} else if (objArray2[columnIdx] == null) {
	    result = 1;
	} else if (objArray1[columnIdx] instanceof Float) {
	    float f1 = ((Float)objArray1[columnIdx]).floatValue();
	    float f2 = ((Float)objArray2[columnIdx]).floatValue();
	    if (f1 < f2)
		result = -1;
	    else if (f2 < f1)
		result = 1;
	} else if (objArray1[columnIdx] instanceof Double) {
	    double f1 = ((Double)objArray1[columnIdx]).doubleValue();
	    double f2 = ((Double)objArray2[columnIdx]).doubleValue();
	    if (f1 < f2)
		result = -1;
	    else if (f2 < f1)
		result = 1;
	} else if (objArray1[columnIdx] instanceof Integer) {
	    int i1 = ((Integer)objArray1[columnIdx]).intValue();
	    int i2 = ((Integer)objArray2[columnIdx]).intValue();
	    if (i1 < i2)
		result = -1;
	    else if (i2 < i1)
		result = 1;
	} else if (objArray1[columnIdx] instanceof Long) {
	    long i1 = ((Long)objArray1[columnIdx]).longValue();
	    long i2 = ((Long)objArray2[columnIdx]).longValue();
	    if (i1 < i2)
		result = -1;
	    else if (i2 < i1)
		result = 1;
	} else {
	    result = (((String)objArray1[columnIdx])).compareTo((String)objArray2[columnIdx]);
	}

	if (sortColumn < 0)
	    result = -result;
 
	return result;
    }  // compare

}  // ColumnComparator
    
