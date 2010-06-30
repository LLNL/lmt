package example.java;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * Title:        GUI Example
 * Description:  An example to demonstrate the portability of the Charva library.
 * Copyright:    Copyright (c) 2001
 * Company:      Pitman Computer Consulting
 * @author R Pitman
 * @version 1.0
 */

public class CookiePanel extends JPanel {
    GridBagLayout gridBagLayout1 = new GridBagLayout();
    JCheckBox jCheckBoxEnableCookies = new JCheckBox();
    JPanel jPanelDomainSpecific = new JPanel();
    Border border1;
    TitledBorder titledBorder1;
    JPanel jPanelDefaultPolicy = new JPanel();
    JRadioButton jRadioButtonDefaultReject = new JRadioButton();
    JRadioButton jRadioButtonDefaultAsk = new JRadioButton();
    TitledBorder titledBorder2;
    JPanel jPanelDomainPolicy = new JPanel();
    TitledBorder titledBorder3;
    BorderLayout borderLayout1 = new BorderLayout();
    JRadioButton jRadioButtonDefaultAccept = new JRadioButton();
    JPanel jPanelRadioButtons = new JPanel();
    JRadioButton jRadioButtonDomainReject = new JRadioButton();
    JRadioButton jRadioButtonDomainAsk = new JRadioButton();
    JRadioButton jRadioButtonDomainAccept = new JRadioButton();
    JPanel jPanelButtons = new JPanel();
    JButton jButtonDelete = new JButton();
    JButton jButtonChange = new JButton();
    ButtonGroup buttonGroupDefault = new ButtonGroup();
    ButtonGroup buttonGroupDomain = new ButtonGroup();
    JPanel jPanelDomain = new JPanel();
    JTextField jTextField1 = new JTextField();
    JLabel jLabel1 = new JLabel();
    FlowLayout flowLayout1 = new FlowLayout();

    public CookiePanel() {
        try {
            jbInit();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        buttonGroupDefault.add(jRadioButtonDefaultAccept);
        buttonGroupDefault.add(jRadioButtonDefaultAsk);
        buttonGroupDefault.add(jRadioButtonDefaultReject);
        buttonGroupDomain.add(jRadioButtonDomainAccept);
        buttonGroupDomain.add(jRadioButtonDomainAsk);
        buttonGroupDomain.add(jRadioButtonDomainReject);

        Object[][] cells = {
            {"java.sun.com", "accept"},
            {"amazon.com", "ask"},
            {"evil.hacker.org", "deny"},
            {"microsoft.com", "deny"},
            {"ibm.com", "ask"},
            {"oracle.com", "accept"},
            {"netscape.com", "accept"},
            {"borland.com", "accept"},
            {"rsasecurity.com", "accept"},
            {"linux.org", "accept"},
            {"redhat.com", "accept"},
            {"linuxjournal.com", "accept"},
            {"bea.com", "accept"},
            {"sick.perverts.org", "deny"} };
        String[] columnNames = { "Domain", "Policy" };
        JTable table = new JTable(cells, columnNames);
        JScrollPane scrollpane = new JScrollPane(table);
        table.setPreferredScrollableViewportSize(new Dimension(200, 200));
        jPanelDomainSpecific.add(scrollpane);
    }
    private void jbInit() throws Exception {
        border1 = BorderFactory.createLineBorder(Color.black,2);
        titledBorder1 = new TitledBorder(border1,"Domain-specific settings");
        titledBorder2 = new TitledBorder(BorderFactory.createLineBorder(new Color(153, 153, 153),2),"Default policy");
        titledBorder3 = new TitledBorder(BorderFactory.createLineBorder(new Color(153, 153, 153),2),"Domain policy");
        jCheckBoxEnableCookies.setText("Enable Cookies");
        this.setLayout(gridBagLayout1);
        jPanelDomainSpecific.setBorder(titledBorder1);
        jRadioButtonDefaultReject.setText("Reject");
        jRadioButtonDefaultAsk.setText("Ask");
        jPanelDefaultPolicy.setBorder(titledBorder2);
        jPanelDomainPolicy.setBorder(titledBorder3);
        jPanelDomainPolicy.setLayout(borderLayout1);
        jRadioButtonDefaultAccept.setSelected(true);
        jRadioButtonDefaultAccept.setText("Accept");
        jRadioButtonDomainReject.setText("Reject");
        jRadioButtonDomainAsk.setText("Ask");
        jRadioButtonDomainAccept.setSelected(true);
        jRadioButtonDomainAccept.setText("Accept");
        jButtonDelete.setText("Delete");
        jButtonChange.setText("Change");
        jTextField1.setColumns(20);
        jLabel1.setText("Domain: ");
        jPanelDomain.setLayout(flowLayout1);
        flowLayout1.setAlignment(FlowLayout.LEFT);
        this.add(jCheckBoxEnableCookies, new GridBagConstraints(0, 0, 1, 1, 0.0, 20.0
            ,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jPanelDomainSpecific, new GridBagConstraints(0, 1, 2, 2, 100.0, 100.0
            ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jPanelDefaultPolicy, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        jPanelDefaultPolicy.add(jRadioButtonDefaultAccept, null);
        jPanelDefaultPolicy.add(jRadioButtonDefaultAsk, null);
        jPanelDefaultPolicy.add(jRadioButtonDefaultReject, null);
        this.add(jPanelDomainPolicy, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0
            ,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelDomainPolicy.add(jPanelRadioButtons, BorderLayout.CENTER);
        jPanelRadioButtons.add(jRadioButtonDomainAccept, null);
        jPanelRadioButtons.add(jRadioButtonDomainAsk, null);
        jPanelRadioButtons.add(jRadioButtonDomainReject, null);
        jPanelDomainPolicy.add(jPanelButtons, BorderLayout.SOUTH);
        jPanelButtons.add(jButtonChange, null);
        jPanelButtons.add(jButtonDelete, null);
        jPanelDomainPolicy.add(jPanelDomain, BorderLayout.NORTH);
        jPanelDomain.add(jLabel1, null);
        jPanelDomain.add(jTextField1, null);
    }
}