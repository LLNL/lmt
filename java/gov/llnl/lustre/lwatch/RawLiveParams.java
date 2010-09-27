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

public class RawLiveParams {
    final static boolean debug = Boolean.getBoolean("debug");
    final static boolean localDebug = 
	Boolean.getBoolean("RawLiveParams.debug");

    PlotFrame2 pf2 = null;

    public RawLiveParams(PlotFrame2 pf) {
	this.pf2 = pf;
    }

    public void displayDialog() {
	RawLiveParamsDialog dialog = new RawLiveParamsDialog(pf2.getFrame());
    }


    class RawLiveParamsDialog extends JDialog {

	final boolean mode = true;
	private JFrame ownerFrame;
	private JPanel mainPane;
	private JDialog rlpDialog;
	JButton cancelBUtton = null;
	JButton continueBUtton = null;
	String msg = "Make changes, Apply or Cancel";

	int liveRR = 15000;
	int liveNtrvl = 7200000;
	boolean cancel = true;

	private JPanel buttonPanel = new JPanel(new FlowLayout());

	LiveModifyPanel liveModifyPane = null;


	public RawLiveParamsDialog(JFrame pfFrame) {
	    super(pfFrame, true);

	    this.ownerFrame = pfFrame;
	    rlpDialog = this;

	    if (localDebug) {
		if (rlpDialog.isModal())
		    Debug.out("rlpDialog is modal.");
		else
		    Debug.out("rlpDialog is NOT modal.");
	    }

	    mainPane = new JPanel();
	    BorderLayout mainLayout = new BorderLayout();
	    mainPane.setLayout(mainLayout);


	    liveModifyPane = new LiveModifyPanel();
	    mainPane.add(liveModifyPane, BorderLayout.NORTH);


	    cancelBUtton = new JButton("Cancel");

	    cancelBUtton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Cancel\" pressed: ");
			cancel = true;
			quit();
		    }
		});

	    buttonPanel.add(cancelBUtton);


	    continueBUtton = new JButton("Apply");

	    continueBUtton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Apply\" pressed: ");

			cancel = false;
			pf2.refreshRate =
			    liveModifyPane.liveRefRateChooser.getLiveRefreshRate();  // MSecs
			pf2.liveDisplayInterval =
			    liveModifyPane.liveNtrvlChooser.getLiveDisplayInterval();  // MSecs
			    

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
	    setSize(340, 175);
	    setLocation(new Point(300, 600));
	    setVisible(true);
	}


	public void quit() {
	    if (localDebug)
		Debug.out("[quit]");

	    pf2.cancel = cancel;

	    setVisible(false);
	    rlpDialog.dispose();
	}

	class WindowH extends WindowAdapter {
	    public void windowActivated(WindowEvent e) {
	    }
	    public void windowClosing(WindowEvent e) {
		if (localDebug)
		    Debug.out("[windowClosing]");
		quit();
	    }
	}

    }


    class LiveModifyPanel extends JPanel implements ActionListener {

	LiveIntervalChooser liveNtrvlChooser;
	LiveRefreshRateChooser liveRefRateChooser;

	LiveModifyPanel() {

	    GridBagLayout lmpgbl = new GridBagLayout();
	    this.setLayout(lmpgbl);
	    GridBagConstraints c;


	    JLabel ntrvLabel = new JLabel("Display Interval");
	    ntrvLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    add(ntrvLabel);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 0;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(ntrvLabel, c);



	    liveNtrvlChooser = new LiveIntervalChooser((int)(pf2.liveDisplayInterval/60000));
	    add(liveNtrvlChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(liveNtrvlChooser, c);


	    JLabel rrLabel = new JLabel("Refresh Rate");
	    rrLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    rrLabel.setHorizontalAlignment(JLabel.CENTER);
	    add(rrLabel);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 0;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(rrLabel, c);


	    liveRefRateChooser = new LiveRefreshRateChooser(pf2.refreshRate/1000);
	    add(liveRefRateChooser);
	    
	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(liveRefRateChooser, c);


	}

	public void actionPerformed(ActionEvent e) {
	    //if (localDebug)
		Debug.out("Clicked Live Modify Change Button.");
	}

	class LiveIntervalChooser extends JPanel implements ActionListener {
	    final JComboBox liveIntervalChooserBox = new JComboBox();
	    final String [] ntrvls = {"15 Min", "30 Min", "1 Hour", "2 Hours", "4 Hours"};
	                          //, "8 Hours", "12 Hours", "24 Hours"};
	    final int [] minuteEquivalents = {15, 30, 60, 120, 240};
					  //, 480, 720, 1440};

	    int selectedLiveInterval;

	    LiveIntervalChooser(int initalNtrvl) {

		boolean match = false;
		for (int i = 0; i < ntrvls.length; i++) {
		    liveIntervalChooserBox.addItem(ntrvls[i]);
		    if (minuteEquivalents[i] == initalNtrvl) {
			match = true;
			liveIntervalChooserBox.setSelectedIndex(i);
			selectedLiveInterval = i;
		    }
		}
		liveIntervalChooserBox.setFont(new Font("helvetica", Font.PLAIN, 10));
		if (!match) {
		    liveIntervalChooserBox.setSelectedIndex(2);
		    selectedLiveInterval = 2;
		}
		liveIntervalChooserBox.addActionListener(this);
		this.add(liveIntervalChooserBox);
	    }

	    long getLiveDisplayInterval() {
		return minuteEquivalents[selectedLiveInterval] * 60000;
	    }

	    public void setLiveDisplayInterval(int intervalMinutes) {
		boolean match = false;
		int i = 0;
		while (i < minuteEquivalents.length && ! match) {
		    if (intervalMinutes == minuteEquivalents[i])
			match = true;

		    i++;
		}
		if (!match)
		    i = 2;
		liveIntervalChooserBox.setSelectedIndex(2);
		selectedLiveInterval = 2;
	    }

	    public void actionPerformed(ActionEvent e) {
		selectedLiveInterval = liveIntervalChooserBox.getSelectedIndex();
		//Debug.out("selectedLiveInterval = " + selectedLiveInterval);

		if (localDebug)
		    Debug.out("Selected Live Interval = " + ntrvls[selectedLiveInterval]);
	    }
	}


	class LiveRefreshRateChooser extends JPanel implements ActionListener {
	    final JComboBox liveRateChooserBox = new JComboBox();
	    final String [] rates = {"5 Seconds", "10 Seconds", "15 Seconds",
				      "30 Seconds", "1 Minute",
				      "2 Minutes", "5 Minutes"};
	    final int [] secondEquivalents = {5, 10, 15, 30, 60, 120, 300};

	    int selectedLiveRefreshRate;

	    LiveRefreshRateChooser(int initalRate) {

		boolean match = false;
		for (int i = 0; i < rates.length; i++) {
		    liveRateChooserBox.addItem(rates[i]);
		    if (secondEquivalents[i] == initalRate) {
			match = true;
			liveRateChooserBox.setSelectedIndex(i);
			selectedLiveRefreshRate = i;
		    }
		}
		liveRateChooserBox.setFont(new Font("helvetica", Font.PLAIN, 10));
		if (!match) {
		    liveRateChooserBox.setSelectedIndex(2);
		    selectedLiveRefreshRate = 2;
		}
		liveRateChooserBox.addActionListener(this);
		this.add(liveRateChooserBox);
	    }

	    int getLiveRefreshRate() {
		return secondEquivalents[selectedLiveRefreshRate] * 1000;
	    }

	    public void setLiveDisplayInterval(int rateSeconds) {
		boolean match = false;
		int i = 0;
		while (i < secondEquivalents.length && ! match) {
		    if (rateSeconds == secondEquivalents[i])
			match = true;

		    i++;
		}
		if (!match)
		    i = 2;
		liveRateChooserBox.setSelectedIndex(2);
		selectedLiveRefreshRate = 2;
	    }

	    public void actionPerformed(ActionEvent e) {
		selectedLiveRefreshRate = liveRateChooserBox.getSelectedIndex();
		//Debug.out("selectedLiveRefreshRate = " + selectedLiveRefreshRate);

		if (localDebug)
		    Debug.out("Selected Live Refresh Rate = " +
			      rates[selectedLiveRefreshRate]);
	    }
	}

    }


}

