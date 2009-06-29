package example.java;

import java.awt.*;
import javax.swing.*;

/**
 * Title:        GUI Example
 * Description:  An example to demonstrate the portability of the Charva library.
 * Copyright:    Copyright (c) 2001
 * Company:      Pitman Computer Consulting
 * @author R Pitman
 * @version 1.0
 */

public class ProxyPanel extends JPanel {
    GridBagLayout gridBagLayout1 = new GridBagLayout();
    JCheckBox proxyCheckBox = new JCheckBox();
    JLabel jLabel1 = new JLabel();
    JTextField jTextFieldHTTPProxy = new JTextField();
    JLabel jLabel2 = new JLabel();
    JTextField jTextFieldHTTPPort = new JTextField();
    JLabel jLabel3 = new JLabel();
    JTextField jTextFieldFTPProxy = new JTextField();
    JLabel jLabel4 = new JLabel();
    JTextField jTextFieldFTPPort = new JTextField();
    JLabel jLabel5 = new JLabel();
    JTextField jTextFieldNoProxy = new JTextField();

    public ProxyPanel() {
        try {
            jbInit();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    private void jbInit() throws Exception {
        proxyCheckBox.setText("Use Proxy");
        this.setLayout(gridBagLayout1);
        jLabel1.setText("HTTP Proxy: ");
        jTextFieldHTTPProxy.setColumns(10);
        jLabel2.setText("Port: ");
        jTextFieldHTTPPort.setColumns(6);
        jLabel3.setText("FTP Proxy: ");
        jTextFieldFTPProxy.setColumns(10);
        jLabel4.setText("Port: ");
        jTextFieldFTPPort.setColumns(6);
        jLabel5.setText("No Proxy for: ");
        jTextFieldNoProxy.setColumns(20);
        this.add(proxyCheckBox, new GridBagConstraints(0, 0, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabel1, new GridBagConstraints(0, 1, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jTextFieldHTTPProxy, new GridBagConstraints(1, 1, 1, 1, 100.0, 100.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabel2, new GridBagConstraints(2, 1, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jTextFieldHTTPPort, new GridBagConstraints(3, 1, 1, 1, 100.0, 100.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabel3, new GridBagConstraints(0, 2, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jTextFieldFTPProxy, new GridBagConstraints(1, 2, 1, 1, 100.0, 100.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabel4, new GridBagConstraints(2, 2, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jTextFieldFTPPort, new GridBagConstraints(3, 2, 1, 1, 100.0, 100.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabel5, new GridBagConstraints(0, 3, 1, 1, 100.0, 100.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jTextFieldNoProxy, new GridBagConstraints(1, 3, 3, 1, 100.0, 100.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }
}