package example.charva;

import charva.awt.*;
import charvax.swing.*;
import charvax.swing.border.*;
import charva.awt.event.*;
import charvax.swing.table.*;
import java.util.*;

/**
 * Title:        GUI Example
 * Description:  An example to demonstrate the portability of the Charva library.
 * Copyright:    Copyright (c) 2001
 * Company:      Pitman Computer Consulting
 * @author
 * @version 1.0
 */

public class AccountPanel extends JPanel {
    BorderLayout borderLayout1 = new BorderLayout();
    JPanel jPanelNewUser = new JPanel();
    TitledBorder titledBorder1;
    GridBagLayout gridBagLayout1 = new GridBagLayout();
    JLabel jLabel1 = new JLabel();
    JTextField jTextFieldAccountname = new JTextField();
    JLabel jLabel2 = new JLabel();
    JPasswordField jPasswordField1 = new JPasswordField();
    JLabel jLabel3 = new JLabel();
    JPasswordField jPasswordField2 = new JPasswordField();
    JLabel jLabel4 = new JLabel();
    JTextField jTextFieldFullname = new JTextField();
    JButton addButton = new JButton();
    JPanel jPanel1 = new JPanel();
    GridBagLayout gridBagLayout2 = new GridBagLayout();
    JPanel jPanelUsers = new JPanel();
    JButton deleteButton = new JButton();
    TitledBorder titledBorder2;

    // User-added instance variables
    JTable table;
    UserTableModel usermodel = new UserTableModel();

    public AccountPanel() {
        try {
            jbInit();
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        usermodel.addUser(new User("karl", "Karl Marx",
            ("rabid#communist").toCharArray()));
        usermodel.addUser(new User("vlad", "Vladimir Lenin",
            ("looney").toCharArray()));
        table = new JTable(usermodel);
        table.setPreferredScrollableViewportSize(new Dimension(28, 8));
        JScrollPane scrollpane = new JScrollPane(table);
        jPanelUsers.add(scrollpane);
    }

    private void jbInit() throws Exception {
        titledBorder1 = new TitledBorder(BorderFactory.createLineBorder(new Color(153, 153, 153),2),"New User");
        titledBorder2 = new TitledBorder(BorderFactory.createLineBorder(new Color(153, 153, 153),2),"User accounts");
        this.setLayout(borderLayout1);
        jPanelNewUser.setBorder(titledBorder1);
        jPanelNewUser.setLayout(gridBagLayout1);
        jLabel1.setText("Account name: ");
        jTextFieldAccountname.setColumns(10);
        jLabel2.setText("Password: ");
        jPasswordField1.setColumns(15);
        jLabel3.setText("Repeat Password: ");
        jPasswordField2.setColumns(15);
        jLabel4.setText("Full name: ");
        jTextFieldFullname.setColumns(20);
        addButton.setText("Add");
        addButton.addActionListener(new charva.awt.event.ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addButton_actionPerformed(e);
            }
        });
        jPanel1.setLayout(gridBagLayout2);
        deleteButton.setText("Delete");
        deleteButton.addActionListener(new charva.awt.event.ActionListener() {
            public void actionPerformed(ActionEvent e) {
                deleteButton_actionPerformed(e);
            }
        });
        jPanel1.setBorder(titledBorder2);
        this.add(jPanelNewUser, BorderLayout.NORTH);
        jPanelNewUser.add(jLabel1, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jTextFieldAccountname, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jLabel4, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jTextFieldFullname, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jLabel2, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jPasswordField1, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jLabel3, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0
            ,GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(jPasswordField2, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanelNewUser.add(addButton, new GridBagConstraints(0, 4, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jPanel1, BorderLayout.CENTER);
        jPanel1.add(jPanelUsers, new GridBagConstraints(0, 0, 1, 1, 100.0, 100.0
            ,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        jPanel1.add(deleteButton, new GridBagConstraints(1, 0, 1, 1, 10.0, 10.0
            ,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }

    /**
     * User-added inner class.
     */
    class UserTableModel extends AbstractTableModel
    {
        java.util.Vector _users = new java.util.Vector();

        public void addUser(User user_) {
            _users.add(user_);
        }

        public void removeUser(String username_) {
            Enumeration e = _users.elements();
            while (e.hasMoreElements()) {
                User u = (User) e.nextElement();
                if (u.account.equals(username_)) {
                    _users.remove(u);
                    break;
                }
            }
        }

        public int getColumnCount() { return 2; }

        public int getRowCount() { return _users.size(); }

        public String getColumnName(int col) {
            switch (col) {
                case 0: return "User";
                case 1: return "Full Name";
                default: return "Invalid";
            }
        }

        public Object getValueAt(int row_, int column_) {
            // later....
            User user = (User) _users.elementAt(row_);
            switch (column_) {
                case 0:     return user.account;
                case 1:     return user.fullname;
                default:    return "Invalid";
            }
        }
    }

    /**
     * User-added inner class.
     */
    class User {
        public String account;
        public String fullname;
        public char[] password;

        public User(String account_, String fullname_, char[] password_) {
            this.account = account_;
            this.fullname = fullname_;
            this.password = password_;
        }
    }

    void addButton_actionPerformed(ActionEvent e) {
        if (jTextFieldAccountname.getText().length() == 0) {
            JOptionPane.showMessageDialog(null, "Fill in the Account Name", "Error",
                JOptionPane.ERROR_MESSAGE);
            jTextFieldAccountname.requestFocus();
        }
        else if (jTextFieldFullname.getText().length() == 0) {
            JOptionPane.showMessageDialog(null, "Fill in the Full Name", "Error",
                JOptionPane.ERROR_MESSAGE);
            jTextFieldFullname.requestFocus();
        }
        else if ( (String.valueOf(jPasswordField1.getPassword())).equals(
                String.valueOf(jPasswordField2.getPassword()) ) == false) {

            JOptionPane.showMessageDialog(null, "Passwords don't match", "Error",
                JOptionPane.ERROR_MESSAGE);
            jPasswordField1.requestFocus();
        }
        else {
            usermodel.addUser(new User(
                jTextFieldAccountname.getText(),
                jTextFieldFullname.getText(),
                jPasswordField1.getPassword() ));
            usermodel.fireTableDataChanged();
        }
    }

    void deleteButton_actionPerformed(ActionEvent e) {
        int row = table.getSelectedRow();
        if (row != -1) {
            String accountname = (String) usermodel.getValueAt(row, 0);
            usermodel.removeUser(accountname);
            usermodel.fireTableDataChanged();
        }
    }
}
