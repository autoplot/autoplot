/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.html;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class HtmlParserCallback extends HTMLEditorKit.ParserCallback {

    private static final Logger logger= Logger.getLogger("apdss.html");

    int tableCount = -1;
    int itable=0;
    String stable= null;

    boolean inTable = false;
    List<String> currentRow;

    int fieldCount = -1;
    boolean inField = false;
    String fieldText = "";
    
    int recordCount= -1;

    boolean isHeader= false;

    int icolspan;

    AsciiTableMaker ascii= new AsciiTableMaker();

    List<String> tables= new ArrayList();

    /**
     * set the table to read.
     * @param name 
     */
    void setTable( String name ) {
        int i= name.indexOf(":");
        if ( i>-1 ) name= name.substring(0,i);
        this.stable= name;
        try {
            itable= Integer.parseInt(name);
        } catch ( NumberFormatException ex ) {
            itable= -1;
        }
    }

    /**
     * set the units for the columns.
     * @param units 
     */
    void setUnits(String units) {
        ascii.setUnits( units );
    }
    
    @Override
    public void handleText(char[] data, int pos) {
        if (inField) {
            fieldText += new String(data);
            if (fieldText.length() > 30) {
                fieldText = fieldText.substring(0, 27) + "...";
            }
        }

    }
    
    private List<String> currentTableName= new ArrayList(); // tables can be nested
    int nest=0;
    
    @Override
    public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        logger.log(Level.FINE, "startTag {0} @{1}", new Object[]{t, pos});        
        if ( t==HTML.Tag.TABLE ) {
            tableCount++;
            nest++;
            recordCount= 0;
            String tableName= (String)a.getAttribute("id");
            if ( tableName==null ) tableName= ""+tableCount;
            currentTableName.add( tableName );
            if ( itable>-1 ) {
                if ( tableCount==itable ) inTable= true;
            } else {
                if ( stable.equals( a.getAttribute("id") ) ) inTable= true;
            }
        } else if ( inTable ) {
            if (t == HTML.Tag.TR) {
                currentRow = new ArrayList<String>();
            } else if (t == HTML.Tag.TH) {
                String colspan= (String) a.getAttribute(HTML.Attribute.COLSPAN);
                icolspan= ( colspan!=null ) ? Integer.parseInt(colspan) : 1;
                inField = true;
                fieldText = "";
                isHeader= true;
            } else if (t == HTML.Tag.TD) {
                inField = true;
                fieldText = "";
                isHeader= false;
            }
        }
    }

    @Override
    public void handleEndTag(HTML.Tag t, int pos) {
        logger.log(Level.FINE, "endTag {0} @{1}", new Object[]{t, pos});        
        if (t == HTML.Tag.TABLE) {
            nest--;
            String dim;
            if ( fieldCount>-1 ) {
                dim= recordCount + " rows, "+fieldCount+ " colums";
            } else {
                dim= recordCount + " rows";
            }
            //if ( nest==0 ) {
                if ( currentTableName.isEmpty() ) throw new IllegalArgumentException("table html syntax");
                tables.add( currentTableName.remove(0) + ": "+dim );
            //}
            if ( inTable ) inTable= false;
        } else if ( inTable ) {
            if (t == HTML.Tag.TR) {
                if (fieldCount == -1) {
                    fieldCount = currentRow.size();
                }
                if (currentRow.size() != fieldCount) {
                    logger.fine("skipping row because of field count");
                    return;
                }
                if ( isHeader ) {
                    ascii.addHeader( currentRow );
                } else {
                    if ( ascii.hasHeader()==false ) {
                        List<String> values= new ArrayList();
                        boolean haveNumber= false;
                        for ( int i=0; i<currentRow.size(); i++ ) {
                            values.add("field"+i);
                            try {
                                Double.parseDouble(currentRow.get(i));
                                haveNumber= true;
                            } catch ( NumberFormatException ex ) {
                                
                            }
                        }
                        if ( haveNumber==false ) { // https://commons.apache.org/proper/commons-math/userguide/optimization.html uses the first row as headers.
                            ascii.addHeader( currentRow );
                        } else {
                            ascii.addHeader( values );
                        }
                    } else {
                        recordCount++;
                        ascii.addRecord( currentRow );
                    }
                }
                
            } else if (t == HTML.Tag.TH) {
                inField = false;
                currentRow.add(fieldText);
                for ( int i=1; i<icolspan; i++ ) {
                    currentRow.add(fieldText);
                }
                fieldText = "";
            } else if (t == HTML.Tag.TD) {
                inField = false;
                currentRow.add(fieldText);
                fieldText = "";
            }
        } 
    }

    /**
     * return the collected dataset
     * @return 
     */
    public QDataSet getDataSet() {
        DDataSet result= ascii.getDataSet();
        if ( itable==-1 ) {
            result.putProperty( QDataSet.NAME, stable );
        }
        return result;
    }

    /**
     * return a list of table names "name: &lt;dims&gt;
     * @return 
     */
    public List<String> getTables() {
        return new ArrayList<>(tables);
    }

}
