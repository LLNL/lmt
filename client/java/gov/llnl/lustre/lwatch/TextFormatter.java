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

import java.text.DecimalFormat;
import java.io.*;

public class TextFormatter {
    private static final  DecimalFormat decimalFormat =
	new DecimalFormat("#,###,##0.00");

  //////////////////////////////////////////////////////////////////////////////

    static String format(
    final Double value,
    final int width)
  {
    //Debug.out("Format Double " + value.doubleValue());

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      decimalFormat.format(value.doubleValue()) + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

    static String format(
    final Float value,
    final int width)
  {
    //Debug.out("Format Float " + value.floatValue());

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      decimalFormat.format(value.floatValue()) + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

    static String format(
    final Long value,
    final int width)
  {
    //Debug.out("Format Long " + value.longValue());

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      value + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

    static String format(
    final Integer value,
    final int width)
  {
    //Debug.out("Format Integer " + value.intValue());

    if (value == null) {
      return format("****", width, false);
    }

    return format(
      value + "",
      width,
      false);

  } // format

  //////////////////////////////////////////////////////////////////////////////

    static String format(
    final String value,
    final int size,
    final boolean leftJustified)
  {
    //Debug.out("Format String " + value);

    if (value == null) {
      return format("****", size, false);
    }

    if (value.length() >= size) {
      return value;
    }

    final StringBuffer sb = new StringBuffer(size);

    if (leftJustified) {
      sb.append(value);
    }
    
    for (int i = value.length(); i < size; i++) {
      sb.append(' ');
    } // for i

    if (!leftJustified) {
      sb.append(value);
    }

    return sb.toString();

  } // format

  //////////////////////////////////////////////////////////////////////////////

}
