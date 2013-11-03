/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.spase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.SparseDataSetBuilder;
import org.virbo.dsutil.DataSetBuilder;
import org.virbo.qstream.BundleStreamFormatter;
import org.virbo.qstream.SimpleStreamFormatter;
import org.virbo.qstream.StreamException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Convert VOTable into QDataSet.  This will return a bundle of parameters.
 * 
 * @author jbf
 */
public class VOTableReader {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.votable");
    
    DefaultHandler sax;

    String state;
    
    private final String STATE_OPEN= "open";
    
    /**
     * In header we are reading the fields to get column names.
     */
    private final String STATE_HEADER= "header";
    
    /**
     * We are after the header.
     */
    private final String STATE_DATA= "data";
    
    /**
     * In record we are reading each of the fields.
     */
    private final String STATE_RECORD= "record";
    
    private final String STATE_FIELD= "field";
    
    int ncolumn;
    QDataSet bds;
    
    List<String> ids;
    List<String> datatypes; // we only support double and UTC then the ucd is time.epoch.
    List<String> names;
    List<String> sunits; // Equal to UTC for time types.  Can be null.
    List<Units> units;
    DataSetBuilder dataSetBuilder;
    
    /**
     * the index within each record.
     */
    int index; 
    
    private final String UNIT_UTC= "time.epoch";

    private final String DATATYPE_UTC= "time.epoch";
    
    public VOTableReader() {
        this.sax = new DefaultHandler() {

            /**
             * initialize the state to STATE_OPEN.
             */
            @Override
            public void startDocument() throws SAXException {
                state= STATE_OPEN;
            }
            
            
            /**
             * As elements come in, we go through the state transitions to keep track of
             * whether we are reading FIELDS, Rows of the dataset, Individual columns, etc.
             */
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if ( localName.equals("TABLE")&& state.equals(STATE_OPEN)  ) {
                    state= STATE_HEADER;
                    ids= new ArrayList<String>();
                    datatypes= new ArrayList<String>();
                    names= new ArrayList<String>();
                    units= new ArrayList<Units>();
                } else if ( localName.equals("FIELD") && state.equals(STATE_HEADER) ) {
                    ids.add( attributes.getValue("ID") );
                    String dt= attributes.getValue("datatype");
                    String ucd= attributes.getValue("ucd");
                    String sunit;
                    if ( dt.equals("char") ) {
                        if ( ucd!=null && ucd.equals("time.epoch") ) {
                            sunit= UNIT_UTC;
                            datatypes.add( DATATYPE_UTC );
                        } else {
                            sunit= attributes.getValue("unit"); // just make it dimensionless.  Note null is allowed.
                            datatypes.add( dt );
                        }
                    } else {
                        datatypes.add( dt );
                        sunit= attributes.getValue("unit");
                    }    
                    if ( sunit==null ) {
                        units.add(Units.dimensionless);
                    } else if ( sunit.equals("time.epoch") ) {
                        units.add(Units.cdfTT2000);
                    } else {
                        units.add( SemanticOps.lookupUnits( sunit) );
                    }
                    names.add( attributes.getValue("name") );
                    index++;
                } else if ( localName.equals("DATA") ) {
                    state= STATE_DATA;
                    dataSetBuilder= new DataSetBuilder( 2, 100, names.size() );
                } else if ( localName.equals("TR") && state.equals(STATE_DATA) ) {
                    state= STATE_RECORD;
                    index= 0;
                } else if ( localName.equals("TD") && state.equals(STATE_RECORD) ) {
                    state= STATE_FIELD;
                }
                //super.startElement(uri, localName, qName, attributes); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if ( STATE_FIELD.equals(state) ) {
                    String s= new String( ch, start, length );
                    if ( s.trim().length()>0 ) {
                        try {
                            Units u=  units.get(index);
                            Datum d= u.parse( s );
                            dataSetBuilder.putValue( -1, index, d.doubleValue(u) );
                        } catch (ParseException ex) {
                            Logger.getLogger(VOTableReader.class.getName()).log(Level.SEVERE, null, ex);
                            dataSetBuilder.putValue( -1, index, -1e31 );
                        }
                        index++;
                        if ( index==units.size() ) {
                            dataSetBuilder.nextRecord();
                            state= STATE_RECORD;
                            index= 0;
                        }
                    }
                }
            }
            
        }; 
    }
    
    public QDataSet getDataSet() {
        SparseDataSetBuilder head= new SparseDataSetBuilder(2);
        head.setQube( new int[] { ids.size(), 0 } ); // all datasets are rank 1.
        for ( int ii=0; ii<ids.size(); ii++ ) {
            head.putProperty( QDataSet.NAME, ii, ids.get(ii) );
            head.putProperty( QDataSet.LABEL, ii, names.get(ii) ); 
            head.putProperty( QDataSet.UNITS, ii, units.get(ii) ); 
        }
        dataSetBuilder.putProperty( QDataSet.BUNDLE_1, head.getDataSet() );
        return dataSetBuilder.getDataSet();
    }
    
    public static void main( String[] args ) throws SAXException, ParserConfigurationException, IOException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        
        VOTableReader t= new VOTableReader();
        
        xmlReader.setContentHandler(t.sax);
        
        long t0= System.currentTimeMillis();
        
        xmlReader.parse( new File("/home/jbf/ct/autoplot/votable/DATA_2012_2012_FGM_KRTP_1M.xml").toURI().toString() );
        
        QDataSet ds= t.getDataSet();
        System.err.println( String.format( "Read in %d millis: %s", System.currentTimeMillis()-t0, ds ) );
        
        t0= System.currentTimeMillis();
        OutputStream out= new FileOutputStream("/tmp/vospase.qds");
        try {
            new BundleStreamFormatter().format( ds, out, false );
            System.err.println( String.format( "Write in %d millis: %s", System.currentTimeMillis()-t0, ds ) );
        } catch (StreamException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            out.close();
        }
    }
}
