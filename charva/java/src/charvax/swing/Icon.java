/*
 * @(#)Icon.java	1.16 03/12/19
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package charvax.swing;


/**
 * A small fixed size picture, typically used to decorate components.
 * 
 * @see javax.swing.ImageIcon
 */

public interface Icon 
{
    
    /**
     * Returns the icon's width.
     *
     * @return an int specifying the fixed width of the icon.
     */
    int getIconWidth();

    /**
     * Returns the icon's height.
     *
     * @return an int specifying the fixed height of the icon.
     */
    int getIconHeight();
}
