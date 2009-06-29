package gov.llnl.lustre.database;

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

import gov.llnl.lustre.utility.Debug;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import gov.llnl.lustre.database.Database;
import java.sql.SQLException;


/**
 * @deprecated
 * Class for iterating through ResultSet from OST_DATA table.
 */

class OstRawDataIterator implements Iterator {

  //////////////////////////////////////////////////////////////////////////////

  private static final boolean debug = Boolean.getBoolean("debug");

  private ResultSet resultSet;

  private boolean hasNext = false;


  //////////////////////////////////////////////////////////////////////////////

  OstRawDataIterator(ResultSet resultSet)
  {

    this.resultSet = resultSet;

    try {
      this.hasNext = this.resultSet.next();
    }
    catch (SQLException e) {
      throw new Error(e);
    }


  } //OstRawDataIterator

  //////////////////////////////////////////////////////////////////////////////

  public boolean hasNext()
  {

    return this.hasNext;

  } // hasNext

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Return next Database.OstRawData object.
   * @return next Database.OstRawData object as type Object.
   */

  public Object next()
  {

    if (!this.hasNext) {
      throw new NoSuchElementException();
    }

    final Database.OstRawData ostRawData = new Database.OstRawData();
    
    int index = 1;

    try {
      ostRawData.timestamp  =           this.resultSet.getTimestamp(index++);
      ostRawData.tsId       = (Long)    this.resultSet.getObject(index++);
      ostRawData.ostId      = (Integer) this.resultSet.getObject(index++);
      ostRawData.readBytes  = (Long)    this.resultSet.getObject(index++);
      ostRawData.writeBytes = (Long)    this.resultSet.getObject(index++);
      ostRawData.pctCpu     = (Float)   this.resultSet.getObject(index++);
      ostRawData.kbytesFree = (Long)    this.resultSet.getObject(index++);
      ostRawData.kbytesUsed = (Long)    this.resultSet.getObject(index++);
      ostRawData.inodesFree = (Long)    this.resultSet.getObject(index++);
      ostRawData.inodesUsed = (Long)    this.resultSet.getObject(index++);

      this.hasNext = this.resultSet.next();

    }
    catch (SQLException e) {
      throw new Error(e);
    }

    return ostRawData;

  } // next

  //////////////////////////////////////////////////////////////////////////////

  public void remove()
  {

    throw new UnsupportedOperationException();

  } // remove

  //////////////////////////////////////////////////////////////////////////////

} // OstRawDataInterator


