package gov.llnl.lustre.lwatch;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import java.io.*;

import gov.llnl.lustre.lwatch.util.Debug;

import gov.llnl.lustre.lwatch.Prefs;

// Database imports
import gov.llnl.lustre.database.Database;
import gov.llnl.lustre.database.Database.*;


/**
 * Class to provide GUI to allow modification of preferences.
 */

public class PrefsModifier {
    final static boolean debug = Boolean.getBoolean("debug");
    final static boolean localDebug = 
	Boolean.getBoolean("PrefsModifier.debug");

    int liveRR = 15000;
    int liveNtrvl = 7200000;
    int tableRR = 5000;
    boolean cancel = true;

    public long liveDisplayInterval = liveNtrvl;
    public int liveRefreshRate = liveRR;
    public int plotGranularity = Database.RAW;
    public boolean showIcons = false;
    public int tableRefreshRate = tableRR;
    public boolean showMDS = true;
    public boolean showOST = true;
    public boolean showOSS = true;
    public boolean showRTR = true;

    JFrame lwf = null;

    Prefs prefs = null;

    PrefsDialog dialog = null;


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for Prefs class invoked by LWatch.
     */

    public PrefsModifier(JFrame jf, Prefs lwPrefs) {  // For use by LWatch

	this.lwf = jf;

	prefs = lwPrefs;

	showMDS = prefs.showMDS;
	showOST = prefs.showOST;
	showOSS = prefs.showOSS;
	showRTR = prefs.showRTR;
	tableRefreshRate = prefs.tableRefreshRate;
	liveDisplayInterval = prefs.liveDisplayInterval;
	liveRefreshRate = prefs.liveRefreshRate;
	plotGranularity = prefs.plotGranularity;
	showIcons = prefs.showPlotIcons;

    }  // PrefsModifier


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for Prefs class invoked via main method..
     */

    public PrefsModifier() {  //For use with Static main method...
	this.lwf = new JFrame();
	this.lwf.addWindowListener(new WindowH());
	this.lwf.setVisible(true);

	prefs = new Prefs();

	showMDS = prefs.showMDS;
	showOST = prefs.showOST;
	showOSS = prefs.showOSS;
	showRTR = prefs.showRTR;
	tableRefreshRate = prefs.tableRefreshRate;
	liveDisplayInterval = prefs.liveDisplayInterval;
	liveRefreshRate = prefs.liveRefreshRate;
	plotGranularity = prefs.plotGranularity;
	showIcons = prefs.showPlotIcons;

	//this.lwf = jf;

    }  // PrefsModifier


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class to handle window events.
     */

    class WindowH extends WindowAdapter {


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handle activated window event.
	 */

	public void windowActivated(WindowEvent e) {
	}  // windowActivated


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Handle closing window event.
	 */

	public void windowClosing(WindowEvent e) {
	    if (localDebug)
		Debug.out("[windowClosing]");
	    if (dialog != null)
		dialog.quit();

	}  // windowClosing

    }  // WindowH


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Method to invoke method for building the dialog.
     */

    public void displayDialog() {

	//PrefsDialog dialog = new PrefsDialog(lwf);
	dialog = new PrefsDialog(lwf);

    }  // displayDialog


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to define and build preferences dialog.
     */

    class PrefsDialog extends JDialog {

	final boolean mode = true;
	private JFrame ownerFrame;
	private JPanel mainPane;
	private JDialog rlpDialog;
	JButton cancelButton = null;
	JButton continueButton = null;
	JButton saveButton = null;
	JButton dismissButton = null;
	String msg = "Make changes, Apply or Cancel";

	private JPanel buttonPanel = new JPanel(new FlowLayout());

	private JTabbedPane tabbedPane = new JTabbedPane();
	protected ModificationPanel [] modPanes;
	protected String [] tabHdr = {"Tables", "Plot Frame"};


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for the dialog builder.
	 */

	public PrefsDialog(JFrame pfFrame) {

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

	    int selectedTab = 0;
	    modPanes = new ModificationPanel[2];
	    createModificationPanels(selectedTab);
	    tabbedPane.setSelectedIndex(selectedTab);


	    mainPane.add(tabbedPane, BorderLayout.CENTER);
	    mainPane.setVisible(true);

	    cancelButton = new JButton("Cancel");

	    cancelButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Cancel\" pressed:  modPane = " + modPanes[0].getName());
			cancel = true;
			quit();
		    }
		});

	    buttonPanel.add(cancelButton);


	    saveButton = new JButton("Save");

	    saveButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Save\" pressed:  modPane = " + modPanes[0].getName());
			prefs.prefsWrite();
		    }
		});

	    buttonPanel.add(saveButton);


	    continueButton = new JButton("Apply");

	    continueButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Apply\" pressed: modPane = " + modPanes[0].getName());
			if (modPanes[1].liveNtrvlChooser == null)
			    Debug.out("modPanes[1].liveNtrvlChooser = null");

			cancel = false;

			int tableRRIndex = modPanes[0].tableRefreshRateChooser.getSelectedIndex();
			tableRefreshRate = modPanes[0].rrSecondEquivalents[tableRRIndex] * 1000;

			showMDS = modPanes[0].mdsShowChooser.isSelected();
			showOST = modPanes[0].ostShowChooser.isSelected();
			showOSS = modPanes[0].ossShowChooser.isSelected();
			showRTR = modPanes[0].rtrShowChooser.isSelected();


			int liveRefreshRateIndex =
			    modPanes[1].liveRefRateChooser.getSelectedIndex();  // MSecs
			liveRefreshRate = modPanes[1].rrSecondEquivalents[liveRefreshRateIndex] * 1000;

			int liveDisplayIntervalIndex =
			    modPanes[1].liveNtrvlChooser.getSelectedIndex();  // MSecs
			liveDisplayInterval = modPanes[1].intervalMinuteEquivalents[liveDisplayIntervalIndex];
			plotGranularity = modPanes[1].granChooser.getSelectedIndex() + 1;
			showIcons = modPanes[1].iconShowChooser.isSelected();


			if (localDebug)
			    Debug.out("Table refresh rate = " + tableRefreshRate + "\nShow MDS = " +
				      showMDS + "\nShow OST = " + showOST + "\nShow OSS = " + showOSS +
				      "\nShow RTR = " + showRTR +
				      "\nrefreshRate = " + liveRefreshRate + " sec.   interval = " +
				      liveDisplayInterval + " min." +
				      "\nPlot Granularity = " + plotGranularity +
				      "\nShow Plot Icons = " + showIcons);

			// Assign preference values to Prefs variables

			prefs.showMDS = showMDS;
			prefs.showOST = showOST;
			prefs.showOSS = showOSS;
			prefs.showRTR = showRTR;
			prefs.tableRefreshRate = tableRefreshRate;

			prefs.liveDisplayInterval = liveDisplayInterval;
			prefs.liveRefreshRate = liveRefreshRate;
			prefs.plotGranularity = plotGranularity;
			prefs.showPlotIcons = showIcons;

			//prefs.prefsWrite();

			//quit();
		    }
		});

	    buttonPanel.add(continueButton);


	    dismissButton = new JButton("Dismiss");

	    dismissButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {

			if (localDebug)
			    Debug.out("ActionButton \"Dismiss\" pressed:  modPane = " + modPanes[0].getName());
			quit();
		    }
		});

	    buttonPanel.add(dismissButton);

	    mainPane.add(buttonPanel, BorderLayout.SOUTH);

	    // Handle window system close
	    addWindowListener(new WindowH());

	    getContentPane().add(mainPane);

	    // Set the initial size & location of the dialog window.
	    setSize(340, 250);
	    setLocation(new Point(300, 600));
	    setVisible(true);

	}  // PrefsDialog


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Cleanup and quit.
	 */

	public void quit() {

	    if (localDebug)
		Debug.out("[quit]");

	    //lwf.cancel = cancel;

	    setVisible(false);
	    rlpDialog.dispose();

	}  // quit


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Create the modfication panels.
	 */

	void createModificationPanels(int selectedIndex) {
	    
	    for (int i = 0; i < modPanes.length; i++) {
		modPanes[i] = new ModificationPanel(i);

		tabbedPane.addTab(tabHdr[i], modPanes[i]);
	    }

	}  // createModificationPanels

    }  // PrefsDialog


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Class used to define various panels for different preference groupings.
     */

    class ModificationPanel extends JPanel implements ActionListener {

	Chooser liveNtrvlChooser;
	Chooser liveRefRateChooser;
	Chooser granChooser;
	BoolChooser iconShowChooser;

	//Chooser realtimeNtrvlChooser;
	//Chooser realtimeRefRateChooser;
	Chooser tableRefreshRateChooser;

	BoolChooser mdsShowChooser;
	BoolChooser ostShowChooser;
	BoolChooser ossShowChooser;
	BoolChooser rtrShowChooser;

	private final int TABLESFRAME = 0;
	private final int PLOTFRAME = 1;

	final String [] paneName = {"TablesFrame", "PlotFrame"};

	final String [] ntrvls = {"15 Min", "30 Min", "1 Hour", "2 Hours",
				  "4 Hours"};
	final int [] intervalMinuteEquivalents = {15, 30, 60, 120, 240};

	final String [] plotRRates = {"5 Seconds", "10 Seconds", "15 Seconds",
				      "30 Seconds", "1 Minute",
				      "2 Minutes", "5 Minutes"};
	final int [] rrSecondEquivalents = {5, 10, 15, 30, 60, 120, 300};

	final String [] tableRRates = {"5 Seconds", "10 Seconds", "15 Seconds",
				       "30 Seconds"};

	final String [] histGranularity = {"Raw", "Hour", "Day", "Week", "Month", "Year", "Heartbeat"};

	final int [] grains = {1, 2, 3, 4, 5, 6, 7};


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor for modificatio panels.
	 */

	ModificationPanel(int i) {
	    super();
	    setName(paneName[i]);

	    switch (i) {
	    case TABLESFRAME: {
		makeTablePrefsPane();
		break;
	    }

	    case PLOTFRAME: {
		makePlotFramePrefsPane();
		break;
	    }

	    }
	}  // ModificationPanel


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Build the preferences panel for the tables window.
	 */

	public void  makeTablePrefsPane() {
	    GridBagLayout lmpgbl = new GridBagLayout();
	    this.setLayout(lmpgbl);
	    GridBagConstraints c;


	    JLabel ntrvLabel = new JLabel("Boolean Choices");
	    ntrvLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    ntrvLabel.setHorizontalAlignment(JLabel.CENTER);
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

	    mdsShowChooser = new BoolChooser("Show MDS", prefs.showMDS);
	    add(mdsShowChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(mdsShowChooser, c);

	    ostShowChooser = new BoolChooser("Show OST", prefs.showOST);
	    add(ostShowChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 2;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(ostShowChooser, c);

	    ossShowChooser = new BoolChooser("Show OSS", prefs.showOSS);
	    add(ossShowChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 3;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(ossShowChooser, c);

	    rtrShowChooser = new BoolChooser("Show RTR", prefs.showRTR);
	    add(rtrShowChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 4;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(rtrShowChooser, c);

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


	    tableRefreshRateChooser = new Chooser(tableRRates, rrate2index(prefs.tableRefreshRate));
	    add(tableRefreshRateChooser);
	    
	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(tableRefreshRateChooser, c);


	}  // makeTablePrefsPane


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert refresh rate seconds.
	 *
	 * @param rrate refresh rate in msecs.
	 *
	 * @return refresh rate seconds.
	 */

	protected int rrate2index(int rrate) {
	    rrate /= 1000;

	    int mi = 0;
	    while (rrate != rrSecondEquivalents[mi] && mi < rrSecondEquivalents.length)
		mi++;

	    if (mi >= rrSecondEquivalents.length)
		mi = 0;

	    //Debug.out("Match rate for " + rrate + "   mi = " + mi);

	    return mi;

	}  // rrate2index


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Calculate interval index from interval value.
	 *
	 * @param ntrvl history display interval in minutes.
	 *
	 * @return index of history display interval.
	 */

	protected int ntrvl2index(long ntrvl) {

	    int mi = 0;
	    while (ntrvl != intervalMinuteEquivalents[mi] && mi < intervalMinuteEquivalents.length)
		mi++;

	    if (mi >= intervalMinuteEquivalents.length)
		mi = 2;

	    //Debug.out("Match interval for " + ntrvl + "   mi = " + mi);

	    return mi;

	}  // ntrvl2index


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * convert granularity to granularity index.
	 *
	 * @param grain history plot time granularity.
	 *
	 * @return granularity index.
	 */

	protected int gran2index(int grain) {

	    return grain - 1;

	}  // gran2index


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Build the preferences panel for the history plot window.
	 */

	public void  makePlotFramePrefsPane() {
	    GridBagLayout lmpgbl = new GridBagLayout();
	    this.setLayout(lmpgbl);
	    GridBagConstraints c;


	    JLabel granLabel = new JLabel("Granularity");
	    granLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    granLabel.setHorizontalAlignment(JLabel.CENTER);
	    add(granLabel);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(granLabel, c);


	    granChooser = new Chooser(histGranularity, gran2index(prefs.plotGranularity));
	    add(granChooser);
	    
	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 2;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(granChooser, c);



	    JLabel ntrv0Label = new JLabel("Heartbeat");
	    ntrv0Label.setFont(new Font("helvetica", Font.BOLD, 10));
	    ntrv0Label.setHorizontalAlignment(JLabel.CENTER);
	    add(ntrv0Label);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 0;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(ntrv0Label, c);


	    JLabel ntrvLabel = new JLabel("Display Interval");
	    ntrvLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    ntrvLabel.setHorizontalAlignment(JLabel.CENTER);
	    add(ntrvLabel);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(ntrvLabel, c);



	    liveNtrvlChooser = new Chooser(ntrvls, ntrvl2index(prefs.liveDisplayInterval));
	    add(liveNtrvlChooser);

	    c = new GridBagConstraints();
	    c.gridx = 1;
	    c.gridy = 2;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(liveNtrvlChooser, c);



	    JLabel rr0Label = new JLabel("Heartbeat");
	    rr0Label.setFont(new Font("helvetica", Font.BOLD, 10));
	    rr0Label.setHorizontalAlignment(JLabel.CENTER);
	    add(rr0Label);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 0;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(rr0Label, c);

	    JLabel rrLabel = new JLabel("Refresh Rate");
	    rrLabel.setFont(new Font("helvetica", Font.BOLD, 10));
	    rrLabel.setHorizontalAlignment(JLabel.CENTER);
	    add(rrLabel);

	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 1;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 0.0;  //1.0;
	    lmpgbl.setConstraints(rrLabel, c);


	    liveRefRateChooser = new Chooser(plotRRates, rrate2index(prefs.liveRefreshRate));
	    add(liveRefRateChooser);
	    
	    c = new GridBagConstraints();
	    c.gridx = 2;
	    c.gridy = 2;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(liveRefRateChooser, c);


	    iconShowChooser = new BoolChooser("Show Plot Icons", prefs.showPlotIcons);
	    add(iconShowChooser);

	    c = new GridBagConstraints();
	    c.gridx = 0;
	    c.gridy = 3;
	    c.insets = new Insets(8, 2, 2, 5);
	    c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    c.weightx = 0.0;  //1.0;
	    c.weighty = 1.0;  //1.0;
	    lmpgbl.setConstraints(iconShowChooser, c);

	}  // makePlotFramePrefsPane


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Action method for class.
	 *
	 * @param e actionEvent detected.
	 */

	public void actionPerformed(ActionEvent e) {
	    //if (localDebug)
		Debug.out("Clicked Live Modify Change Button.");

	}  // actionPerformed


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Class defining boolean chooser.
	 */

	public class BoolChooser extends JPanel implements ActionListener {
	    JCheckBox checkBox = new JCheckBox();
	    JLabel label;
	    boolean chosen = false;
	    FlowLayout flay = new FlowLayout();


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for boolean chooser.
	     *
	     * @param labelString label to display next to checkbox.
	     *
	     * @param initialChoice initial setting for checkbox.
	     */

	    BoolChooser(String labelString, boolean initialChoice) {

		super();

		setLayout(flay);
		label = new JLabel(labelString);
		add(label);

		checkBox.setSelected(initialChoice);
		this.chosen = initialChoice;
		checkBox.addActionListener(this);
		add(checkBox);

	    }  // BoolChooser


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * True if checkbox is selected.
	     */

	    boolean isSelected() {

		return checkBox.isSelected();

	    }  // isSelected


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Set checkbox selection status.
	     *
	     * @param choice set checkbox to selected if true.
	     */

	    void setSelected(boolean choice) {

		checkBox.setSelected(choice);
		chosen = checkBox.isSelected();

	    }  // setSelected


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Action handler for checkbox.
	     */

	    public void actionPerformed(ActionEvent e) {

		chosen = checkBox.isSelected();
		//Debug.out("chosen = " + chosen);

		if (localDebug)
		    Debug.out("Checkbox chosen = " + chosen);

	    }  // actionPerformed

	}  // BoolChooser


	//////////////////////////////////////////////////////////////////////////////

	/**
	 * Generic chooser for selecting from a list of values.
	 */

	public class Chooser extends JPanel implements ActionListener {

	    JComboBox chooserBox = new JComboBox();
	    //String [] choices = null;
	    int nChoices = 0;
	    int selectedIndex = 0;


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Constructor for list chooser.
	     *
	     * @param values array of string values to present in the list.
	     *
	     * @param initialChoice initial list element chosen.
	     */

	    Chooser(String [] values, int initialChoice) {

		nChoices = values.length;
		for (int i = 0; i < values.length; i++) {
		    //choices[i] = values[i];
		    chooserBox.addItem(values[i]);
		}

		chooserBox.setFont(new Font("helvetica", Font.PLAIN, 10));
		if (initialChoice < 0 && initialChoice >= values.length)
		    initialChoice = 0;

		chooserBox.setSelectedIndex(initialChoice);
		
		chooserBox.addActionListener(this);
		this.add(chooserBox);

	    }  // Chooser


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Return selected index.
	     */

	    public int getSelectedIndex() {

		return selectedIndex;

	    }  // getSelectedIndex


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Set a specific list element to selected.
	     *
	     * @param index index of element to set as selected.
	     */

	    public void setSelectedIndex(int index) {

		if (index < 0 && index >= nChoices)
		    index = 0;
		chooserBox.setSelectedIndex(index);
		selectedIndex = index;

	    }  // setSelectedIndex


	    //////////////////////////////////////////////////////////////////////////////

	    /**
	     * Action handler for chooser.
	     *
	     * @param e action event causing handler to be invoked.
	     */

	    public void actionPerformed(ActionEvent e) {

		selectedIndex = chooserBox.getSelectedIndex();
		//Debug.out("selectedIndex = " + selectedIndex);

		if (localDebug)
		    Debug.out("Selected Index = " + ntrvls[selectedIndex]);

	    }  // actionPerformed

	}  // Chooser

    }  // ModificationPanel


    //////////////////////////////////////////////////////////////////////////////

    /**
     * Main method. Useful for debugging.
     */

    public static void main(String argv[]) {
	//if (localDebug)
	    //Debug.out("Starting...");

	//ControlFrame cf = new ControlFrame();
	//JFrame f = new JFrame();
	//f.setVisible(true);

	//PrefsModifier prefsMod = new PrefsModifier(cf);
	PrefsModifier prefsMod = new PrefsModifier();
	prefsMod.displayDialog();

    }  // main


}  // PrefsModifier

