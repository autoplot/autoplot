/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.html;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class HtmlParserCallback extends HTMLEditorKit.ParserCallback {

    private static final Logger logger= Logger.getLogger("apdss.html");

    int state;
    int tableCount = -1;
    int itable=0;
    String stable= null;

    boolean inTable = false;
    List<String> currentRow;

    String currentField;
    int fieldCount = -1;
    boolean inField = false;
    boolean inRow = false;
    String fieldText = "";

    boolean isHeader= false;

    int icolspan;

    AsciiTableMaker ascii= new AsciiTableMaker();

    List<String> tables= new ArrayList();

    void setTable( String name ) {
        this.stable= name;
        try {
            itable= Integer.parseInt(name);
        } catch ( NumberFormatException ex ) {
            itable= -1;
        }
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

    @Override
    public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        if ( t==HTML.Tag.TABLE ) {
            tableCount++;
            String tableName= (String)a.getAttribute("id");
            if ( tableName==null ) tableName= ""+tableCount;
            tables.add(tableName);
            if ( itable>-1 ) {
                if ( tableCount==itable ) inTable= true;
            } else {
                if ( stable.equals( a.getAttribute("id") ) ) inTable= true;
            }
        } else if ( inTable ) {
            if (t == HTML.Tag.TR) {
                currentRow = new ArrayList<String>();
                inRow = true;
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
        if (t == HTML.Tag.TABLE) {
            if ( inTable ) inTable= false;
        } else if ( inTable ) {
            if (t == HTML.Tag.TR) {
                inRow = false;
                if (fieldCount == -1) {
                    fieldCount = currentRow.size();
                }
                if (currentRow.size() != fieldCount) {
                    logger.fine("skipping row because of field count");
                }
                if ( isHeader ) {
                    ascii.addHeader( currentRow );
                } else {
                    ascii.addRecord( currentRow );
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

    public QDataSet getDataSet() {
        DDataSet result= ascii.getDataSet();
        if ( itable==-1 ) {
            result.putProperty( QDataSet.NAME, stable );
        }
        return result;
    }

    public List<String> getTables() {
        return new ArrayList<String>(tables);
    }

}
