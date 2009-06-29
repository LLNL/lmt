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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import java.io.*;

import gov.llnl.lustre.lwatch.util.Debug;


/**
 * Utility class used for popping up error messages. Invoked from PlotFrame2 class.
 */

public class LwatchAlert {
    final static boolean debug = Boolean.getBoolean("debug");
    final static boolean localDebug = 
	Boolean.getBoolean("LwatchAlert.debug");

    PlotFrame2 pf2 = null;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for LwatchAlert class.
     *
     * @param pf PlotFrame2 class object from which this constructor was invoked.
     */

    public LwatchAlert(PlotFrame2 pf) {

	this.pf2 = pf;

    }  // LwatchAlert


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Method to create dialog for displaying messages.
     *
     * @param mode true if dialog is "modal".
     *
     * @param msg message string to be displayed.
     *
     * @param type 0 - Cancel/Continue.  1 - Acknowledge.
     */

    public void displayDialog(boolean mode, String msg, int type) {
	LwatchAlertDialog dialog = new LwatchAlertDialog(pf2.getFrame(),
							 mode, msg, type);
    }  // displayDialog


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class to create & render the dialog box.
     */

    class LwatchAlertDialog extends JDialog {

	final boolean mode = true;
	private JFrame ownerFrame;
	private JPanel mainPane;
	private JDialog alertDialog;
	JButton cancelBUtton = null;
	JButton continueBUtton = null;

	boolean cancel = true;

	private JPanel buttonPanel = new JPanel(new FlowLayout());


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for class to create & render the dialog box.
	 *
	 * @param pfFrame JFrame used to contain the JDialog.
	 *
	 * @param mode true if dialog is "modal".
	 *
	 * @param msg message string to be displayed.
	 *
	 * @param type 0 - Cancel/Continue.  1 - Acknowledge.
	 */

	public LwatchAlertDialog(JFrame pfFrame, boolean mode, String msg, int type) {
	    super(pfFrame, mode);

	    this.ownerFrame = pfFrame;
	    alertDialog = this;

	    if (localDebug) {
		if (alertDialog.isModal())
		    Debug.out("alertDialog is modal.");
		else
		    Debug.out("alertDialog is NOT modal.");
	    }

	    mainPane = new JPanel();
	    BorderLayout mainLayout = new BorderLayout();
	    mainPane.setLayout(mainLayout);

	    if (type == 0) {  // cancel/continue
		cancelBUtton = new JButton("Cancel");
		cancelBUtton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

			    if (localDebug)
				Debug.out("ActionButton \"Cancel\" pressed.");
			    cancel = true;
			    quit();
			}
		    });
		buttonPanel.add(cancelBUtton);
	    }

	    if (type == 0)
		continueBUtton = new JButton("Continue");
	    else if (type == 1)
		continueBUtton = new JButton("Acknowledge");

	    continueBUtton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Continue\" pressed: ");
			cancel = false;
			quit();
		    }
		});

	    buttonPanel.add(continueBUtton);

	    JTextField jtf = new JTextField(msg);
	    jtf.setHorizontalAlignment(JTextField.CENTER);

	    mainPane.add(jtf, BorderLayout.CENTER);
	    mainPane.add(buttonPanel, BorderLayout.SOUTH);

	    // Handle window system close
	    addWindowListener(new WindowH());

	    getContentPane().add(mainPane);
	    int dialogWidth = (jtf.getPreferredSize()).width + 25;
	    setSize(dialogWidth, 125);
	    setLocation(new Point(300, 600));
	    setVisible(true);

	}  // LwatchAlertDialog


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Cleanup and dispose of JDialog.
	 */

	public void quit() {
	    if (localDebug)
		Debug.out("[quit]");

	    pf2.cancel = cancel;

	    setVisible(false);
	    alertDialog.dispose();

	}  // quit


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class to handle window events.
	 */

	class WindowH extends WindowAdapter {


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Process windowActivated events. NOOP.
	     *
	     * @param e WindowEvent causing this method to be called.
	     */

	    public void windowActivated(WindowEvent e) {

	    }  // windowActivated


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Process windowClosing event.
	     *
	     * @param e WindowEvent causing this method to be called.
	     */

	    public void windowClosing(WindowEvent e) {
		if (localDebug)
		    Debug.out("[windowClosing]");
		quit();

	    }  // windowClosing

	}  // WindowH

    }  // LwatchAlertDialog

}  // LwatchAlert

