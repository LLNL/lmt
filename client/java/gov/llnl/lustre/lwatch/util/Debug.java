package gov.llnl.lustre.lwatch.util;
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

import java.io.*;


/**
 *
 *  The Debug class is used to print debug messages.  Messages will be 
 *  automatically prefaced with the string "ClassName.MethodName:LineNumber:  ".
 *  Output will default to System.err.
 *
 */

public class Debug {

  //////////////////////////////////////////////////////////////////////////////

  // Flag addition of thread info to message headers.
  private final static boolean threadDebug = Boolean.getBoolean("threadDebug");

  private static PrintStream printStream = System.err; // default

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Get the current PrintStream.
   *
   *  @return PrintStream to which debug messages are output.
   */
  
  public static PrintStream getStream()
  {
    
    return Debug.printStream;
    
  } // getStream
  
  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Set the current PrintStream.
   *
   *  @param printStream set the PrintStream to which debug messages are output.
   */
 
  public static void setStream(PrintStream printStream)
  {

    Debug.printStream = printStream;

  } // setStream

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given message if the debug System property is set.
   *
   *  @param msg message to be printed.
   *
   */

  public static void out(String msg)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg);

  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given message if the flag is set.
   *
   *  @param flag print message only if flag is true.
   *
   *  @param msg message to be printed.
   *
   */

  public static void out(boolean flag, String msg)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg);

  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *
   *  Print the given message without regard to the value of the 'debug' System
   *  property.
   *
   *  @param msg message to be printed.
   *
   *  @deprecated Use Debug.out instead.
   *  
   */

  public static void outAlways(String msg)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg);

  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given message (without a newline) if the 'debug' System 
   *  property is set.
   *
   *  @param msg message to be printed.
   *
   */

  public static void outNoLn(String msg)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.print(header + msg);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Check if debug flag is set.
   *
   *  @return true if 'debug' system property is true.
   *
   *  @deprecated Just use Boolean.getBoolean("debug") directly.
   */

  public static boolean isOn()
  {

    return Boolean.getBoolean("debug");

  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *
   */

  public static void out(
    String msg0,
    String msg1)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *
   */

  public static void out(
    String msg0,
    String msg1,
    String msg2)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *
   */

  public static void out(
    String msg0,
    String msg1,
    String msg2,
    String msg3)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   */

  public static void out(
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   *  @param msg5 sixth message to be printed.
   */

  public static void out(
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4,
    String msg5)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
    printStream.println(header + msg5);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   *  @param msg5 sixth message to be printed.
   *  @param msg6 seventh message to be printed.
   */

  public static void out(
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4,
    String msg5,
    String msg6)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
    printStream.println(header + msg5);
    printStream.println(header + msg6);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the debug System property is set.
   *
   *  @param msgs array of messages to be printed.
   *
   */

  public static void out(String[] msgs)
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    for (int i = 0; i < msgs.length; i++) {
      printStream.println(header + msgs[i]);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print messages only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print message only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1,
    String msg2)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print message only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1,
    String msg2,
    String msg3)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print messages only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print messages only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   *  @param msg5 sixth message to be printed.
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4,
    String msg5)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
    printStream.println(header + msg5);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print messages only if flag is true.
   *  @param msg0 first message to be printed.
   *  @param msg1 second message to be printed.
   *  @param msg2 third message to be printed.
   *  @param msg3 fourth message to be printed.
   *  @param msg4 fifth message to be printed.
   *  @param msg5 sixth message to be printed.
   *  @param msg6 seventh message to be printed.
   */

  public static void out(
    boolean flag,
    String msg0,
    String msg1,
    String msg2,
    String msg3,
    String msg4,
    String msg5,
    String msg6)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    printStream.println(header + msg0);
    printStream.println(header + msg1);
    printStream.println(header + msg2);
    printStream.println(header + msg3);
    printStream.println(header + msg4);
    printStream.println(header + msg5);
    printStream.println(header + msg6);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   *  Print the given messages if the flag is set.
   *
   *  @param flag print messages only if flag is true.
   *  @param msgs array of messages to be printed.
   *
   */

  public static void out(
    boolean flag,
    String[] msgs)
  {
    if (!flag) return;

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    for (int i = 0; i < msgs.length; i++) {
      printStream.println(header + msgs[i]);
    }
  }

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
      (threadDebug ? ": " + Thread.currentThread().getName() : "") +
      "] ";
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Shows who called the method that the call to calledFrom appears in.  
   * Information will be written to current printStream (which is System.err
   * by default).
   */ 

  public static void calledFrom()
  {

    StackTraceElement[] elements = (new Throwable()).getStackTrace();
    String header = getHeader(elements[1]);

    if (elements.length < 3) {
      printStream.println(header + "called from <not available>");
      return;
    }

    String caller = getHeader(elements[2]);

    printStream.println(header + "called from " + caller);

  } // calledFrom

  //////////////////////////////////////////////////////////////////////////////

}
