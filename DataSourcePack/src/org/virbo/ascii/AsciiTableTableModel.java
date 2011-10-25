/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;
import org.virbo.dsutil.AsciiParser;
import org.virbo.dsutil.AsciiParser.RecordParser;

/**
 *
 * @author jbf
 */
public class AsciiTableTableModel extends AbstractTableModel implements ColSpanTableCellRenderer.ColSpanTableModel {

    String[] lines;
    int lineStart; // line number of the first line.
    int lineCount; // number of lines in the current buffer
    List<Integer> recCountAtLineStart;  // the number of records at line / LINE_BUFFER_COUNT
    String[] fields;

    boolean[] isRecord;
    int lineNumber;
    int recCount;
    int fieldCount=12;
    private final static int LINE_BUFFER_COUNT=100;

    AsciiTableTableModel() {
        lines = null;
        lineNumber = -1;
    }

    public int getColumnCount() {
        return fieldCount;
    }

    public int getRowCount() {
        return recCount;
    }

    /**
     * returns true if the row is believed to be a record.  If it is not,
     * then the entire line is returned for each column, and the
     * ColSpanTableCellRenderer should be used.
     *
     * @param row
     * @return
     */
    public synchronized boolean isRecord( int row ) {
        return this.isRecord[row - lineStart];
    }

    public boolean isColSpan( int row, int column ) {
        return ! isRecord(row);
    }

    public synchronized Object getValueAt(int row, int column) {
        if (row < lineStart || row >= lineStart + lineCount) {
            readLines(row / LINE_BUFFER_COUNT * LINE_BUFFER_COUNT, LINE_BUFFER_COUNT );
        }
        if (lineCount == 0 || recParser == null) {
            return "";
        }
        if (lineNumber != row) {
            fields= new String[recParser.fieldCount()];
            //if ( parser.isHeader( row, lines[Math.max(0,row-lineStart-1)], lines[row-lineStart], 1 ) ) {
            if ( parser.isHeader( row, lines[Math.max(0,row-lineStart-1)], lines[row-lineStart], 1 ) ) {
                this.isRecord[row - lineStart] = false;
            } else {
                if ( recParser.splitRecord(lines[row-lineStart], fields) ) {
                    this.isRecord[row - lineStart] = true;
                } else {
                    this.isRecord[row - lineStart] = false;
                }
            }
        }
        if (this.isRecord[row - lineStart]) {
            if (fields.length <= column) {
                return "";
            } else {
                return fields[column];
            }
        } else {
            return lines[row - lineStart];
        }

    }

    public synchronized String getLine(int skip) {
        readLines( skip, LINE_BUFFER_COUNT );
        if (lineCount > 0) {
            return lines[skip - lineStart];
        } else {
            return null;
        }
    }

    private synchronized void readLines( int lineNumber, int count ) {
        if (file == null) {
            lines = null;
            lineCount = 0;
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
            isRecord= new boolean[count];

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
        this.recCount = countLines();
        fireTableDataChanged();
        propertyChangeSupport.firePropertyChange(PROP_FILE, oldFile, file);
    }

    private int countLines() {
        BufferedReader reader = null;

        try {
            int lineCount1 = 0;
            reader = new BufferedReader(new FileReader(file));
            String s = reader.readLine();
            while (s != null) {
                lineCount1++;
                s = reader.readLine();
            }
            return lineCount1;
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

    public synchronized RecordParser getRecParser() {
        return recParser;
    }

    public void setRecParser(RecordParser recParser) {
        RecordParser oldRecParser = this.recParser;
        this.recParser = recParser;
        this.fieldCount= recParser.fieldCount();
        fireTableStructureChanged();
        fireTableDataChanged();
        propertyChangeSupport.firePropertyChange(PROP_RECPARSER, oldRecParser, recParser);
    }

    protected AsciiParser parser = null;
    public static final String PROP_PARSER = "parser";

    public AsciiParser getParser() {
        return parser;
    }

    public void setParser(AsciiParser parser) {
        AsciiParser oldParser = this.parser;
        this.parser = parser;
        fireTableDataChanged();
        propertyChangeSupport.firePropertyChange(PROP_PARSER, oldParser, parser);
    }

    
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
