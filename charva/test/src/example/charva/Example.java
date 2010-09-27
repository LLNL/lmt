/* class Example
 */

package example.charva;

import charva.awt.*;
import charva.awt.event.*;
import charvax.swing.*;
import charvax.swing.border.*;
import charvax.swing.event.*;

/**
 * This application demonstrates how a Charva application is structured
 * and how each of the Charva features works.
 */
public class Example
    extends JFrame
    implements ActionListener
{
    public static void main(String[] args)
    {
	Example demo = new Example();
	demo.show();
    }

    /** Constructor
     */
    public Example()
    {
	super("Charva Example");

	// Use an anonymous inner class
	addWindowListener(new WindowAdapter() {
	    public void windowClosing(WindowEvent e) {
		terminate();
	    }
	} );

	JMenuBar menubar = new JMenuBar();

	JMenu fileMenu = new JMenu("File");
	fileMenu.setMnemonic('F');
	JMenuItem exitItem = new JMenuItem("Exit", 'x');
	exitItem.addActionListener(this);
	fileMenu.add(exitItem);

	JMenu layoutMenu = new JMenu("Layout");
	layoutMenu.setMnemonic('L');
	JMenuItem flowLayoutItem = new JMenuItem("FlowLayout", 'F');
	layoutMenu.add(flowLayoutItem);

	JMenuItem borderLayoutItem = new JMenuItem("BorderLayout", 'B');
	layoutMenu.add(borderLayoutItem);

	JMenuItem boxLayoutItem = new JMenuItem("BoxLayout", 'x');
	layoutMenu.add(boxLayoutItem);

	JMenuItem gridbagLayoutItem = new JMenuItem("GridBagLayout", 'G');
	gridbagLayoutItem.addActionListener(this);
	layoutMenu.add(gridbagLayoutItem);

	JMenu dialogMenu = new JMenu("Dialog");
	dialogMenu.setMnemonic('D');

	JMenuItem optionpaneItem = new JMenuItem("JOptionPane", 'O');
	dialogMenu.add(optionpaneItem);

	JMenu selectMenu = new JMenu("Selection");
	selectMenu.setMnemonic('S');

	JMenuItem listItem = new JMenuItem("JList", 'L');
	selectMenu.add(listItem);

	JMenuItem radiobuttonItem = new JMenuItem("JRadioButton", 'R');
	selectMenu.add(radiobuttonItem);

	JMenuItem checkboxItem = new JMenuItem("JCheckBox", 'C');
	selectMenu.add(checkboxItem);

	JMenuItem filechooserItem = new JMenuItem("JFileChooser", 'F');
	selectMenu.add(filechooserItem);

	JMenu textMenu = new JMenu("Text");
	textMenu.setMnemonic('T');

	JMenuItem textfieldItem = new JMenuItem("JTextField", 'T');
	textMenu.add(textfieldItem);

	JMenu miscMenu = new JMenu("Misc");
	miscMenu.setMnemonic('M');
	JMenuItem hidetestItem = new JMenuItem("Hide/Show");
	hidetestItem.addActionListener(this);
	miscMenu.add(hidetestItem);

	menubar.add(fileMenu);
	menubar.add(layoutMenu);
	menubar.add(selectMenu);
	menubar.add(textMenu);
	menubar.add(miscMenu);

	setJMenuBar(menubar);

	Container contentPane = getContentPane();

	/* Add a panel in the north
	 */
	JPanel northpan = new JPanel();
	northpan.setBorder(new TitledBorder("North Panel"));
	JTextArea northtextarea = new JTextArea();
	northtextarea.setColumns(50);
	northtextarea.setRows(3);
	northtextarea.setEditable(false);
	northtextarea.setLineWrap(true);
	northtextarea.setWrapStyleWord(true);
	northtextarea.append(
	    "Use LEFT and RIGHT cursor keys to navigate in the menubar. ");
	northtextarea.append(
	    "Use BACKSPACE to cancel a menu popup. ");
	northtextarea.append(
	    "Use TAB and SHIFT-TAB to navigate between components. ");

	northpan.add(northtextarea);
	contentPane.add(northpan, BorderLayout.NORTH);

	/* Add a panel on the west
	 */
	JPanel westpan = new JPanel();
	westpan.setBorder(new TitledBorder("West Panel"));
	westpan.setLayout(new BoxLayout(westpan, BoxLayout.Y_AXIS));

	JPanel westitem1 = new JPanel();
	westitem1.add(new JLabel("A JButton"));
	JButton button = new JButton("Button");
	button.addActionListener(this);
	westitem1.add(button);
	westpan.add(westitem1);

	JPanel westitem2 = new JPanel();
	westitem2.add(new JLabel("A JTextField"));
	_textfield = new JTextField("this is some text");
	_textfield.addActionListener(this);
	westitem2.add(_textfield);
	westpan.add(westitem2);

	JPanel westitem3 = new JPanel();
	westitem3.add(new JLabel("A JComboBox"));
	String[] comboItems = { "Red", "Green", "Blue", "Mauve", "Pink" };
	_combobox = new JComboBox(comboItems);
	_combobox.addActionListener(this);
	westitem3.add(_combobox);
	westpan.add(westitem3);

	JPanel westitem4 = new JPanel();
	westitem4.add(new JLabel("A JRadioButton"));
	_radiobutton = new JRadioButton("radio button");
	_radiobutton.addActionListener(this);
	westitem4.add(_radiobutton);
	westpan.add(westitem4);

	JPanel westitem5 = new JPanel();
	westitem5.add(new JLabel("A JCheckBox"));
	_checkbox = new JCheckBox("checkbox");
	_checkbox.addActionListener(this);
	westitem5.add(_checkbox);
	westpan.add(westitem5);

	contentPane.add(westpan, BorderLayout.WEST);

	/* Add a panel in the center
	 */
	JPanel centerpan = new JPanel();
	centerpan.setBorder(new TitledBorder("Center Panel"));
	_textarea = new JTextArea();
	_textarea.setColumns(30);
	_textarea.setRows(10);
	_textarea.append("This is an editable text area\n");
	centerpan.add(_textarea);
	contentPane.add(centerpan);

	/* Add a panel for buttons in the south
	 */
	JPanel southpan = new JPanel();
	southpan.setBorder(new TitledBorder("South Panel"));
	JButton exitButton = new JButton("Exit");
	exitButton.addActionListener(this);
	southpan.add(exitButton);
	contentPane.add(southpan, BorderLayout.SOUTH);

	pack();
    }

    public void actionPerformed(ActionEvent e)
    {
	String action = e.getActionCommand();
	Component source = (Component) e.getSource();
	if (source instanceof JMenuItem) {
	    processMenuSelection(e);
	}
	else if (action.equals("Exit")) {
	    terminate();
	}
	else if (action.equals("Button")) {
	    _textarea.append("Button was pressed\n");
	}
	else if (source == _textfield) {
	    _textarea.append("JTextField contains \"" + 
		_textfield.getText() + "\"\n");
	}
	else if (source == _combobox) {
	    _textarea.append(_combobox.getSelectedItem() +
		" was selected in JComboBox\n");
	}
	else if (source == _radiobutton) {
	    _textarea.append("JRadioButton is ");
	    String msg = _radiobutton.isSelected() ? 
		    "selected\n" : "deselected\n";
	    _textarea.append(msg);
	}
	else if (source == _checkbox) {
	    _textarea.append("JCheckBox is ");
	    String msg = _checkbox.isSelected() ? 
		    "selected\n" : "deselected\n";
	    _textarea.append(msg);
	}
    }

    private void processMenuSelection(ActionEvent e)
    {
	String action = e.getActionCommand();
	if (action.equals("Exit")) {
	    hide();
	    terminate();
	}
	else if (action.equals("GridBagLayout")) {
	    GridBagTest dlg = new GridBagTest();
	    dlg.show();
	}
	else if (action.equals("Hide/Show")) {
	    HideShowTest dlg = new HideShowTest();
	    dlg.show();
	}
    }

    private void terminate()
    {
	System.exit(0);
    }

    private JTextField _textfield;
    private JTextArea _textarea;
    private JComboBox _combobox;
    private JRadioButton _radiobutton;
    private JCheckBox _checkbox;
}

/**
 * This class tests the ability to hide and show components.
 */
class HideShowTest
    extends JDialog
    implements ActionListener
{
    /** Constructor
     */
    HideShowTest()
    {
	super();
	setTitle("Test hide/show capability");

	Container contentpane = getContentPane();

	JPanel northpan = new JPanel();
	JButton hideButton = new JButton("Hide");
	hideButton.addActionListener(this);
	northpan.add(hideButton);

	JButton showButton = new JButton("Show");
	showButton.addActionListener(this);
	northpan.add(showButton);

	contentpane.add(northpan, BorderLayout.NORTH);

	JPanel centerpan = new JPanel();
	_text = new JTextField("Now you see the button");
	_button = new JButton("A BUTTON");
	centerpan.add(_text);
	centerpan.add(_button);
	contentpane.add(centerpan, BorderLayout.CENTER);

	JPanel southpan = new JPanel();
	JButton okButton = new JButton("OK");
	okButton.addActionListener(this);
	southpan.add(okButton);
	contentpane.add(southpan, BorderLayout.SOUTH);

	pack();
    }

    public void actionPerformed(ActionEvent e)
    {
	String action = e.getActionCommand();
	if (action.equals("OK")) {
	    hide();
	}
	else if (action.equals("Show")) {
	    if ( !_button.isVisible()) {
		_text.setText("Now you see the button");
		_button.setVisible(true);
	    }
	}
	else if (action.equals("Hide")) {
	    if (_button.isVisible()) {
		_text.setText("Now you don't");
		_button.setVisible(false);
	    }
	}
    }

    JButton _button;
    JTextField _text;
}

/**
 * This class tests the GridBagLayout
 */
class GridBagTest
    extends JDialog
    implements ActionListener, ListSelectionListener
{
    /** Constructor
     */
    public GridBagTest()
    {
	super();
	setTitle("Test GridBagLayout");

	Container contentPane = getContentPane();

	GridBagLayout gbl = new GridBagLayout();
	contentPane.setLayout(gbl);

	_style = new JList(new String[]
	    {	"Serif", "SansSerif", "Monospaced",
		"Dialog", "DialogInput" } );
	_style.setSelectedIndex(0);

	_bold = new JCheckBox("Bold");
	_italic = new JCheckBox("Italic");
	JLabel label = new JLabel("Size: ");
	_size = new JTextField("10", 2);
	_sample = new JTextField("", 20);

	GridBagConstraints gbc = new GridBagConstraints();
	// The following line is for Swing only!
	//gbc.insets = new Insets(5, 5, 5, 5);
	gbc.fill = GridBagConstraints.BOTH;
	gbc.weightx = 0;
	gbc.weighty = 100;
	add(_style, gbc, 0, 0, 1, 3);
	gbc.weightx = 100;
	gbc.fill = GridBagConstraints.NONE;
	gbc.anchor = GridBagConstraints.WEST;
	add(_bold, gbc, 1, 0, 2, 1);
	add(_italic, gbc, 1, 1, 2, 1);
	add(label, gbc, 1, 2, 1, 1);
	gbc.fill = GridBagConstraints.HORIZONTAL;
	add(_size, gbc, 2, 2, 1, 1);
	gbc.anchor = GridBagConstraints.SOUTH;
	gbc.weighty = 0;
	add(_sample, gbc, 0, 3, 4, 1);
	_sample.setText("The quick brown fox");

	JButton okbutton = new JButton("OK");
	okbutton.addActionListener(this);
	add(okbutton, gbc, 2, 4, 1, 1);

	_bold.addActionListener(this);
	_italic.addActionListener(this);
	_style.addListSelectionListener(this);
	_size.addActionListener(this);

	pack();
    }

    private void add(Component c_, GridBagConstraints gbc_,
	int x_, int y_, int w_, int h_)
    {
	gbc_.gridx = x_;
	gbc_.gridy = y_;
	gbc_.gridwidth = w_;
	gbc_.gridheight = h_;
	getContentPane().add(c_, gbc_);
    }

    public void valueChanged(ListSelectionEvent evt)
    {
	updateFont();
    }

    public void actionPerformed(ActionEvent e)
    {
	if (e.getActionCommand().equals("OK")) {
	    setVisible(false);
	}
	else
	    updateFont();
    }

    private void updateFont()
    {
	Font font = new Font(
		(String) _style.getSelectedValue(),
		(_bold.isSelected() ? Font.BOLD : 0) +
		    (_italic.isSelected() ? Font.ITALIC : 0),
		Integer.parseInt(_size.getText()));
	_sample.setFont(font);
	repaint();
    }

    private JList _style;
    private JCheckBox _bold;
    private JCheckBox _italic;
    private JTextField _size;
    private JTextField _sample;
}

