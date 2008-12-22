/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.ascii;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.DefaultTableModel;
import org.virbo.dsutil.AsciiParser.RecordParser;

/**
 *
 * @author jbf
 */
public class AsciiTableTableModel extends DefaultTableModel {
    
    String[] lines;
    int lineStart;
    int lineCount;
    String[] fields;
    int lineNumber;
    int recCount;
    
    AsciiTableTableModel() {
        lines= null;
        lineNumber= -1;
    }

    @Override
    public int getColumnCount() {
        return 12;
    }

    @Override
    public int getRowCount() {
        return recCount;
    }

    @Override
    public synchronized Object getValueAt(int row, int column) {
        if ( row<lineStart || row >= lineStart+lineCount ) {
            readLines( row/10*10, 10 );
        }
        if ( lineCount==0 || recParser==null ) {
            return "";
        }
        if ( lineNumber!=row ) {
            fields= recParser.fields(lines[row-lineStart]);
        }
        if ( fields.length <=column ) {
            return "";
        } else {
            return fields[column];
        }
        
    }

    public String getLine(int skip) {
        readLines( skip, 20 );
        if ( lineCount>0 ) {
            return lines[skip-lineStart];
        } else {
            return null;
        }
    }
    
    private synchronized void readLines( int lineNumber, int count ) {
        if ( file==null ) {
            lines=null;
            lineCount= 0;
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String s;
            for (int i = 0; i < lineNumber; i++) {
                s = reader.readLine();
            }
            lines = new String[count];
            for (int i = 0; i < count; i++) {
                lines[i] = reader.readLine();
            }
            lineStart = lineNumber;
            lineCount = count;
        } catch (IOException ex) {
            Logger.getLogger(AsciiTableTableModel.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(AsciiTableTableModel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    protected File file = null;
    public static final String PROP_FILE = "file";

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        File oldFile = this.file;
        this.file = file;
        this.recCount= countLines();
        fireTableDataChanged();
        propertyChangeSupport.firePropertyChange(PROP_FILE, oldFile, file);
    }
    
    private int countLines() {
        BufferedReader reader = null;
        
        try {
            int lineCount = 0;
            reader = new BufferedReader(new FileReader(file));
            String s = reader.readLine();
            while (s != null) {
                lineCount++;
                s = reader.readLine();
            }
            return lineCount;
        } catch (IOException ex) {
            Logger.getLogger(AsciiTableTableModel.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(AsciiTableTableModel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return -1;
    }
    
    protected RecordParser recParser = null;
    public static final String PROP_RECPARSER = "recParser";

    public RecordParser getRecParser() {
        return recParser;
    }

    public void setRecParser(RecordParser recParser) {
        RecordParser oldRecParser = this.recParser;
        this.recParser = recParser;
        propertyChangeSupport.firePropertyChange(PROP_RECPARSER, oldRecParser, recParser);
    }

    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
