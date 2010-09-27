package example.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;


/**
 * Title:        GUI Example
 * Description:  An example to demonstrate the portability of the Charva library.
 * Copyright:    Copyright (c) 2001
 * Company:      Pitman Computer Consulting
 * @author
 * @version 1.0
 */

public class AppFrame extends JFrame {
    JPanel centerPanel = new JPanel();
    JPanel southPanel = new JPanel();
    JButton cancelButton = new JButton();
    JButton applyButton = new JButton();
    JButton okButton = new JButton();
    FlowLayout flowLayout1 = new FlowLayout();
    JTabbedPane tabbedPane = new JTabbedPane();
    BorderLayout borderLayout1 = new BorderLayout();
    ProxyPanel proxyPanel = new ProxyPanel();
    CookiePanel cookiePanel = new CookiePanel();
    JMenuBar jMenuBar = new JMenuBar();
    JMenu fileMenu = new JMenu();
    JMenu optionMenu = new JMenu();
    JMenu helpMenu = new JMenu();
    JMenuItem exitMenuItem = new JMenuItem();
    AccountPanel accountPanel = new AccountPanel();

    public AppFrame() {
        try {
            jbInit();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        setJMenuBar(jMenuBar);
    }
    public static void main(String[] args) {
        AppFrame appFrame1 = new AppFrame();
        appFrame1.pack();
        appFrame1.setVisible(true);
    }
    private void jbInit() throws Exception {
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cancelButton_actionPerformed(e);
            }
        });
        applyButton.setText("Apply");
        okButton.setText("OK");
        southPanel.setLayout(flowLayout1);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        centerPanel.setLayout(borderLayout1);
        fileMenu.setText("File");
        optionMenu.setText("Options");
        helpMenu.setText("Help");
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exitMenuItem_actionPerformed(e);
            }
        });
        this.setTitle("CHARVA Example");
        this.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                this_keyPressed(e);
            }
        });
        this.getContentPane().add(centerPanel, BorderLayout.CENTER);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);
        tabbedPane.add(proxyPanel, "Proxy");
        tabbedPane.add(cookiePanel, "Cookies");
        tabbedPane.add(accountPanel, "Accounts");
        this.getContentPane().add(southPanel, BorderLayout.SOUTH);
        southPanel.add(okButton, null);
        southPanel.add(applyButton, null);
        southPanel.add(cancelButton, null);
        jMenuBar.add(fileMenu);
        jMenuBar.add(optionMenu);
        jMenuBar.add(helpMenu);
        fileMenu.add(exitMenuItem);
    }

    void exitMenuItem_actionPerformed(ActionEvent e) {
        terminate();
    }

    void this_keyPressed(KeyEvent e) {
        int keycode = e.getKeyCode();
        if (keycode == KeyEvent.VK_F1)
            tabbedPane.setSelectedIndex(0);
        else if (keycode == KeyEvent.VK_F2)
            tabbedPane.setSelectedIndex(1);
        else if (keycode == KeyEvent.VK_F3)
            tabbedPane.setSelectedIndex(2);
    }

    void cancelButton_actionPerformed(ActionEvent e) {
        terminate();
    }

    private void terminate() {
        System.exit(0);
    }
}
