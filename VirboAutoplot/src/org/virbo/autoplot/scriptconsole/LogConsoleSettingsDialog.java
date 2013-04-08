/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * LogConsoleSettingsDialog.java
 *
 * Created on May 19, 2009, 7:32:38 AM
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import org.virbo.autoplot.AutoplotUtil;

/**
 * Settings GUI for the Log Console dialog.  The log console is more complex than it first seems, in that it
 * is actually receiving messages from loggers, not just stdout and stderr.  (See http://docs.oracle.com/javase/1.4.2/docs/guide/util/logging/overview.html,
 * but in short messages are sent to hierarchical named channels with verbosity levels.)  
 * @author jbf
 */
public class LogConsoleSettingsDialog extends javax.swing.JDialog {

    LogConsole console;

	/**
	 * Just a list of all the log levels defined in {@link Level}
	 */
	private static final Level[] LOG_LEVELS = {
		Level.OFF,
		Level.SEVERE,
		Level.WARNING,
		Level.INFO,
		Level.CONFIG,
		Level.FINE,
		Level.FINER,
		Level.FINEST,
		Level.ALL,
		null,
	};

	private class LogLevelComboBoxModel implements ComboBoxModel {
		private Logger logger;
		private LogLevelComboBoxModel(Logger logger) {
			if (logger == null) {
				throw new NullPointerException("logger must be non-null");
			}
			this.logger = logger;
		}

		public void setSelectedItem(Object anItem) {
			Level level = (Level)anItem;
			logger.setLevel(level);
                        boolean invisible= true;
                        StringBuilder err= new StringBuilder();
                        for ( Handler h: logger.getHandlers() ) {
                            if ( h.getLevel().intValue()>level.intValue() ) {
                                err.append("handler filters data: ").append(h).append( "\n");
                            } else {
                                invisible= false;
                            }
                        }
                        if ( invisible ) {
                            err.append( String.format( "No handlers (of %d) will show this log level: %s", logger.getHandlers().length, level ) );
                        }
                        if ( err.length()>0 ) {
                            AutoplotUtil.showMessageDialog( LogConsoleSettingsDialog.this, 
                                    err.toString(), "Misconfigured Logger", JOptionPane.OK_OPTION );
                        }
		}

		public Object getSelectedItem() {
			return logger.getLevel();
		}

		public int getSize() {
			return LOG_LEVELS.length;
		}

		public Object getElementAt(int index) {
			return LOG_LEVELS[index];
		}

		public void addListDataListener(javax.swing.event.ListDataListener l) {
			//No need to implement for a static model
		}

		public void removeListDataListener(javax.swing.event.ListDataListener l) {
			//No need to implement for a static model
		}
	}

	private static class LogLevelCellRenderer implements ListCellRenderer {
		private Logger logger;
		private ListCellRenderer delegate;
		private JComponent component;

		private LogLevelCellRenderer(ListCellRenderer delegate, Logger logger) {
			this.delegate = delegate;
			this.logger = logger;
			if (delegate instanceof JComponent)
				this.component = (JComponent)delegate;
		}

		public Component getListCellRendererComponent(
				JList list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
		{
			//We just need to handle null as a special case
			if (value == null) {
				Logger anscestor = logger;
                                if ( logger.getParent()==null ) { // root logger.
                                    value= logger.getLevel();
                                } else {
                                    do {
                                            anscestor = anscestor.getParent();
                                            if ( anscestor==null ) {
                                                new Exception("anscestor is null").printStackTrace();
                                                value = "NULL"; // I don't think this happens...
                                            } else {
                                                value = anscestor.getLevel();
                                            }
                                    } while (value == null);
                                    value = "INHERITED(" + value + ")";


                                    if (component != null) {
                                            String name = anscestor.getName();
                                            if (name.equals(""))
                                                    name = "<anonymous>";
                                            component.setToolTipText("inherited from " + name);
                                    }
                                }
			}
			else {
				((JComponent)delegate).setToolTipText(null);
			}
			return delegate.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
		}
		
	}

    /** Creates new form LogConsoleSettingsDialog */
    public LogConsoleSettingsDialog(java.awt.Frame parent, boolean modal, LogConsole console ) {
        super(parent, modal);
        setTitle("Log Console Settings");
        initComponents();
        initLogSettings();
        setLocationRelativeTo(parent);
        
        verbosityPanel.validate();
        this.console= console;
        jScrollPane1.getVerticalScrollBar().setUnitIncrement(16);
    }

    private void initLogSettings() {
        String[] sloggers;

        HashSet otherLoggers= new HashSet( org.das2.util.LoggerManager.getLoggers() );
        otherLoggers.addAll( org.das2.datum.LoggerManager.getLoggers() );
        sloggers= (String[])otherLoggers.toArray( new String[otherLoggers.size()] );
        Arrays.sort(sloggers);

        GridBagConstraints c = new GridBagConstraints();

        c.gridy= 0;
        c.insets= new Insets(0,10,0,10);
        for ( String slogger: sloggers ) {
            Logger logger= Logger.getLogger(slogger);
            JLabel l= new JLabel(slogger);
            c.weightx= 0.4;
            c.gridx= 0;
			c.fill = GridBagConstraints.NONE;
            verbosityPanel.add( l,c );
            JComboBox cb= new JComboBox( new LogLevelComboBoxModel(logger) );
			cb.setRenderer(new LogLevelCellRenderer(cb.getRenderer(),logger));
            c.gridx= 1;
            c.weightx= 0.6;
			c.fill = GridBagConstraints.HORIZONTAL;
            verbosityPanel.add( cb,c );
            c.gridy++;
        }

    }

    private void updateSearchText() {
        console.setSearchText( searchForTextField.getText() );
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        loggerIDCheckBox = new javax.swing.JCheckBox();
        timeStampsCheckBox = new javax.swing.JCheckBox();
        logLevelCheckBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        searchForTextField = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        verbosityPanel = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        loggerIDCheckBox.setText("logger ID");
        loggerIDCheckBox.setToolTipText("identifies the logger posting the message");
        loggerIDCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggerIDCheckBoxActionPerformed(evt);
            }
        });

        timeStampsCheckBox.setText("timing");
        timeStampsCheckBox.setToolTipText("Show time of the message, in seconds before the most recent message.");
        timeStampsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStampsCheckBoxActionPerformed(evt);
            }
        });

        logLevelCheckBox.setText("log levels");
        logLevelCheckBox.setToolTipText("show the log level (verbosity) of the messages.");
        logLevelCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logLevelCheckBoxActionPerformed(evt);
            }
        });

        jLabel1.setText("Verbosity of the log channels:");
        jLabel1.setToolTipText("<html>Autoplot uses Java Logging, which allows messages to be sent to named channels with a verbosity level.   Set verbosity to finer levels to see messages intended for developers.");

        jLabel2.setText("Highlite Lines Matching:");
        jLabel2.setToolTipText("Enter a regular expression.  Lines containing this will be highlited.");

        searchForTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchForTextFieldActionPerformed(evt);
            }
        });
        searchForTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                searchForTextFieldFocusLost(evt);
            }
        });

        verbosityPanel.setAlignmentY(0.0F);
        verbosityPanel.setLayout(new java.awt.GridBagLayout());
        jScrollPane1.setViewportView(verbosityPanel);

        jButton1.setText("Okay");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 497, Short.MAX_VALUE))
                    .add(jLabel1)
                    .add(layout.createSequentialGroup()
                        .add(loggerIDCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeStampsCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(logLevelCheckBox))
                    .add(layout.createSequentialGroup()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchForTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 172, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 113, Short.MAX_VALUE)
                        .add(jButton1)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 266, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeStampsCheckBox)
                    .add(logLevelCheckBox)
                    .add(loggerIDCheckBox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(searchForTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton1))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void loggerIDCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggerIDCheckBoxActionPerformed
        console.setShowLoggerId( loggerIDCheckBox.isSelected() );
        console.update();
}//GEN-LAST:event_loggerIDCheckBoxActionPerformed

    private void timeStampsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStampsCheckBoxActionPerformed
        console.setShowTimeStamps( timeStampsCheckBox.isSelected() );
        console.update();
}//GEN-LAST:event_timeStampsCheckBoxActionPerformed

    private void logLevelCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logLevelCheckBoxActionPerformed
        console.setShowLevel( logLevelCheckBox.isSelected() );
        console.update();
}//GEN-LAST:event_logLevelCheckBoxActionPerformed

    private void searchForTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchForTextFieldActionPerformed
        updateSearchText();
}//GEN-LAST:event_searchForTextFieldActionPerformed

    private void searchForTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_searchForTextFieldFocusLost
        updateSearchText();
    }//GEN-LAST:event_searchForTextFieldFocusLost

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        this.setVisible(false);
    }//GEN-LAST:event_jButton1ActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                LogConsoleSettingsDialog dialog = new LogConsoleSettingsDialog(new javax.swing.JFrame(), true, null );
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox logLevelCheckBox;
    private javax.swing.JCheckBox loggerIDCheckBox;
    private javax.swing.JTextField searchForTextField;
    private javax.swing.JCheckBox timeStampsCheckBox;
    private javax.swing.JPanel verbosityPanel;
    // End of variables declaration//GEN-END:variables

}
