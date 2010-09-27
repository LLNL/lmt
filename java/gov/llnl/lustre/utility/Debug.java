package gov.llnl.lustre.utility;
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


public class Debug {

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Returns String of format "[Class.method:999]" for use in debug output.
   *
   * @return debug tag String.
   */

  public static String tag()
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    return getHeader(elements[1]);

  } // tag

  //////////////////////////////////////////////////////////////////////////////

  private static String getHeader(StackTraceElement element)
  {

    String fullClassName = element.getClassName();
    String[] parts = fullClassName.split("\\.");
    String className = parts[parts.length - 1];
    int lineNumber = element.getLineNumber();

    return 
      "[" +
      className + "." +  
      element.getMethodName() +
      (lineNumber > -1 ? ":" + lineNumber : "") +
      "] ";

  } // getHeader

  //////////////////////////////////////////////////////////////////////////////

} // Debug
