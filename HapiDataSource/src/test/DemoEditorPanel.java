package test;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.autoplot.hapi.HapiDataSourceEditorPanel;
import org.das2.util.monitor.NullProgressMonitor;

/**
 *
 * @author jbf
 */
public class DemoEditorPanel {
    public static void main(String[] args ) throws Exception {
        HapiDataSourceEditorPanel edit= new HapiDataSourceEditorPanel();
        edit.prepare("vap+hapi:", null, new NullProgressMonitor() );
        edit.setURI("vap+hapi:");
        JDialog dia= new JDialog();
        dia.setContentPane(edit.getPanel());
        dia.setResizable(true);
        dia.pack();
        dia.setVisible(true);
        System.err.println(edit.getURI());
    }
}
