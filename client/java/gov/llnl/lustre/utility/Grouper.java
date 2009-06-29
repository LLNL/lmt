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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
  
public class Grouper {

  //////////////////////////////////////////////////////////////////////////////

  private static final boolean debug = Boolean.getBoolean("debug");

  private static Pattern pattern = Pattern.compile(
    "(\\D+)(\\d+)");

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Given an array of individual names, produce a name for the group.  For
   * example, if the input array is {"adev2", "adev3", "adev4", "adev9",
   * "adev13"}, then the output String would be "adev[2-4,9,13]".
   *
   * @param names array of individual names.
   * @return condensed name that represents the entire group of names.
   */

  public static String getNameForGroup(String[] names)
  {

    String groupName = null;

    int nums[] = new int[names.length];

    for (int i = 0; i < names.length; i++) {

      if (debug) System.err.println(Debug.tag() + "names[" + i + "] = " + names[i]);
            
      Matcher matcher = Grouper.pattern.matcher(names[i]);

      if (!matcher.matches()) {
        throw new Error("Invalid name format:  " + names[i]);
      }

      if (debug) {
        System.err.println(Debug.tag() + "matcher.group(1) = " + matcher.group(1));
        System.err.println(Debug.tag() + "matcher.group(2) = " + matcher.group(2));
      }

      String name = matcher.group(1);

      if (i == 0) {
        groupName = name;
      }
      else if (!name.equals(groupName)) {
        throw new Error("Name mismatch:  " + name + " != " + groupName);
      }

      nums[i] = Integer.parseInt(matcher.group(2));

    } //  for i


    if (debug) {
      System.err.print(Debug.tag() + "nums = [");
      for (int i = 0; i < nums.length; i++) {
        if (i > 0) System.err.print(',');
        System.err.print(nums[i]);
      } //  for i
      System.err.println(']');
    }

    if (nums.length == 1) {
      // Special case, only one name was given.
      return groupName + "[" + nums[0] + "]";
    }

    Arrays.sort(nums);

    StringBuffer sb = new StringBuffer();

    sb.append(groupName);
    sb.append('[');

    int leftIndex = 0;

    for (int i = 0; i < nums.length - 1; i++) {
      int diff = nums[i + 1] - nums[i];

      if (diff > 1) {

        if (nums[i] > nums[leftIndex]) {
          sb.append(nums[leftIndex]);
          sb.append('-');
        }
        sb.append(nums[i]);
        sb.append(',');

        leftIndex = i + 1;
      }

    } // for i

    if (nums[nums.length - 1] > nums[leftIndex]) {
      sb.append(nums[leftIndex]);
      sb.append('-');
    }

    sb.append(nums[nums.length - 1]);
    sb.append(']');

    return sb.toString();

  } // getNameForGroup

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Given an array of individual names, produce an array of more condensed
   * names.  For example, if the input array is {"adev2", "adev3", "adev4",
   * "adev9", "adev13"}, then the output would be {"adev[2-4]", "adev[9]",
   * "adev[13]"}.
   *
   * @param names array of individual names.
   * @return array of names for contiguous groups.
   */

  public static String[] getNamesForGroup(String[] names)
  {

    ArrayList arrayList = new ArrayList();

    String groupName = null;


    //
    // Get individual numeric IDs from router names.
    //

    int nums[] = new int[names.length];

    for (int i = 0; i < names.length; i++) {

      if (debug) System.err.println(Debug.tag() + "names[" + i + "] = " + names[i]);
            
      Matcher matcher = Grouper.pattern.matcher(names[i]);

      if (!matcher.matches()) {
        throw new Error("Invalid name format:  " + names[i]);
      }

      if (debug) {
        System.err.println(Debug.tag() + "matcher.group(1) = " + matcher.group(1));
        System.err.println(Debug.tag() + "matcher.group(2) = " + matcher.group(2));
      }

      String name = matcher.group(1);

      if (i == 0) {
        groupName = name;
      }
      else if (!name.equals(groupName)) {
        throw new Error("Name mismatch:  " + name + " != " + groupName);
      }

      nums[i] = Integer.parseInt(matcher.group(2));

    } //  for i


    if (debug) {
      System.err.print(Debug.tag() + "nums = [");
      for (int i = 0; i < nums.length; i++) {
        if (i > 0) System.err.print(',');
        System.err.print(nums[i]);
      } //  for i
      System.err.println(']');
    }

    if (nums.length == 1) {
      // Special case, only one name was given.
      return new String[]{ groupName + "[" + nums[0] + "]"};
    }

    Arrays.sort(nums);

    int leftIndex = 0;

    boolean first = true;

    for (int i = 0; i < nums.length - 1; i++) {

      int diff = nums[i + 1] - nums[i];

      if (diff > 1) {
        
        StringBuffer sb = new StringBuffer("");
        
        if (first) {
          sb.append(groupName);
          sb.append('[');
          first = false;
        }

        if (nums[i] > nums[leftIndex]) {
          sb.append(nums[leftIndex]);
          sb.append('-');
        }
        sb.append(nums[i]);
        sb.append(',');

        arrayList.add(sb.toString());

        leftIndex = i + 1;
      }

    } // for i

    StringBuffer sb = new StringBuffer();
    
    if (first) {
      sb.append(groupName);
      sb.append('[');
    }

    if (nums[nums.length - 1] > nums[leftIndex]) {
      sb.append(nums[leftIndex]);
      sb.append('-');
    }

    sb.append(nums[nums.length - 1]);
    sb.append(']');

    arrayList.add(sb.toString());

    return (String[]) arrayList.toArray(new String[arrayList.size()]);

  } // getNamesForGroup

  //////////////////////////////////////////////////////////////////////////////

  public static void main(String[] args)
  {

    // Quick test.
    System.out.println(Grouper.getNameForGroup(args));


    String[] array = Grouper.getNamesForGroup(args);

    for (int i = 0; i < array.length; i++) {
      System.out.println(array[i]);
    } // for i

  } //  main

  //////////////////////////////////////////////////////////////////////////////

} // Grouper
