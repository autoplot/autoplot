/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.spase;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.das2.datum.Datum;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.SparseDataSetBuilder;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;
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

    /**
     * expecting the characters within a description.
     */
    private final String STATE_DESCRIPTION= "description";
    
    /**
     * expecting the characters within a field.
     */
    private final String STATE_FIELD= "field";
    
    private ProgressMonitor monitor;
    
    int ncolumn;
    QDataSet bds;
    
    List<String> ids= new ArrayList<String>();
    List<String> descriptions=  new ArrayList<String>(); // one-line describing the data.
    List<Integer> dep0s= new ArrayList<Integer>();
    List<String> datatypes= new ArrayList<String>(); // we only support double and UTC then the ucd is time.epoch.
    List<Integer> arraysizes= new ArrayList<Integer>(); // support for 2-D arrays.  -1,0 or N  -2 means *, -1 means scalar, positive means 2-D array
    List<String> names= new ArrayList<String>();
    List<String> sunits= new ArrayList<String>(); // Equal to UTC for time types.  Can be null.
    List<Units> units= new ArrayList<Units>();
    List<String> fillValues= new ArrayList<String>(); // the fill value representation
    List<String> minValues=  new ArrayList<String>(); // the minimum value representation
    List<String> maxValues=  new ArrayList<String>(); // the maximum value representation
    List<Boolean> stopEnumerations= new ArrayList<Boolean>();  // if true, don't attempt to preserve enumerations.
                    
    DataSetBuilder dataSetBuilder;
    
    /**
     * the number of unique values allowed to be represented by an enumeration.  
     */
    private static final int UNIQUE_ENUMERATION_VALUES_LIMIT = 20000;

    /**
     * the data index within each record.  This might not be the same as the number of fields.
     */
    int index; 
    
    /**
     * the number of fields per record.
     */
    int nelements;
    
    /**
     * element index within a record, used to index the output array.
     */
    int ielement;
    
    /**
     * use TimeLocationUnits like us2000 or CDFTT2000
     */
    private final String UNIT_UTC= "time.epoch";
    
    /**
     * use EnumerationUnits to preserve strings.
     */
    private final String UNIT_ENUM= "UNIT_ENUM";

    private final String DATATYPE_UTC= "time.epoch";
    
    private final double FILL_VALUE= -1e31;
    
    private final int ARRAYSIZE_ANY= -2;
    private final int ARRAYSIZE_SCALAR= -1;
    
    private boolean justHeader= false;
    
    /**
     * the current column describing the time.
     */
    int currentDep0= -1;
    
    boolean lookForCurrentDep0= true;
            
    private StringBuilder valueBuilder= new StringBuilder();
    
    public VOTableReader() {
        
        this.sax = new DefaultHandler() {

            /**
             * initialize the state to STATE_OPEN.
             */
            @Override
            public void startDocument() throws SAXException {
                state= STATE_OPEN;
                monitor.started();
            }
            
            /**
             * As elements come in, we go through the state transitions to keep track of
             * whether we are reading FIELDS, Rows of the dataset, Individual columns, etc.
             */
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if ( localName.equals("TABLE")&& state.equals(STATE_OPEN)  ) {
                    state= STATE_HEADER;
                    nelements= 0;
                } else if ( localName.equals("FIELD") && state.equals(STATE_HEADER) ) {
                    String id= attributes.getValue("ID");
                    String name= attributes.getValue("name");
                    if ( id==null ) { // I believe the schema at http://www.ivoa.net/xml/VOTable/v1.1 indicates this should occur once.
                        id= Ops.safeName(name);
                    }
                    ids.add( id );
                    names.add( name );
                    String dt= attributes.getValue("datatype");
                    if ( dt==null ) throw new IllegalArgumentException("expected to see datatype in FIELD");
                    String ucd= attributes.getValue("ucd");
                    String sunit= attributes.getValue("unit");
                    String arraysize= attributes.getValue("arraysize");
                    
                    if ( dt.equals("char") ) {
                        if ( ( ucd!=null && ucd.equals("time.epoch") ) || ( sunit!=null && sunit.equals("DateTime") ) ) {
                            sunit= UNIT_UTC;
                            datatypes.add( DATATYPE_UTC );
                        } else if ( ( ucd!=null && ucd.equals("time.start") ) ) {
                            sunit= UNIT_UTC;
                            datatypes.add( DATATYPE_UTC );
                        } else if ( ( ucd!=null && ucd.equals("time.stop") ) ) {
                            sunit= UNIT_UTC;
                            datatypes.add( DATATYPE_UTC );
                        } else {
                            sunit= UNIT_ENUM;
                            datatypes.add( dt );
                        }
                    } else {
                        datatypes.add( dt );
                    }
                    if ( arraysize!=null ) {
                        if ( arraysize.equals("*") ) {
                            arraysizes.add( ARRAYSIZE_ANY );
                            if ( !dt.equals("char") ) {
                                logger.warning("only char can have variable length");
                                nelements+= 1;
                            } else {
                                nelements+= 1;
                            }
                        } else {
                            if ( dt.equals("char") ) {
                                arraysizes.add( ARRAYSIZE_SCALAR );
                                nelements+= 1;
                            } else {
                                arraysizes.add( Integer.parseInt(arraysize) );
                                nelements+= Integer.parseInt(arraysize);
                            }
                        }
                    } else {
                        arraysizes.add( ARRAYSIZE_SCALAR );
                        nelements+= 1;
                    }
                    if ( sunit==null ) {
                        units.add(Units.dimensionless);
                        lookForCurrentDep0= true;
                    } else if ( sunit.equals( UNIT_UTC ) ) {
                        units.add(Units.cdfTT2000);
                        if ( lookForCurrentDep0==true  ) {
                            currentDep0= descriptions.size();
                            lookForCurrentDep0= false;
                        }
                    } else if ( sunit.equals( UNIT_ENUM ) ) {
                        units.add( EnumerationUnits.create(id) );
                        lookForCurrentDep0= true;
                    } else {
                        units.add( Units.lookupUnits( sunit) );
                        lookForCurrentDep0= true;
                    }
                    
                    dep0s.add(currentDep0);
                    descriptions.add(null);
                    fillValues.add(null);
                    minValues.add(null);
                    maxValues.add(null);
                    stopEnumerations.add(Boolean.FALSE);
                } else if ( localName.equals("DESCRIPTION") ) {
                    state= STATE_DESCRIPTION;
                    valueBuilder.delete( 0, valueBuilder.length() );
                } else if ( localName.equals("VALUES") ) {
                    String fill= attributes.getValue("null");
                    if ( fill!=null ) {
                        fillValues.set(index,fill);
                    }
                } else if ( localName.equals("MIN") ) { // assume we are within VALUES
                    String x= attributes.getValue("value");
                    if ( x==null ) {
                        logger.info("MIN is missing value attribute");
                    } else {
                        minValues.set(index,x);
                    }
                } else if ( localName.equals("MAX") ) { // assume we are within VALUES
                    String x= attributes.getValue("value");
                    if ( x==null ) {
                        logger.info("MAX is missing value attribute");
                    } else {
                        maxValues.set(index,x);
                    }
                } else if ( localName.equals("DATA") ) {
                    if ( justHeader ) {
                        throw new RuntimeException("we're all done reading the header and dont need the data.");
                    }
                    monitor.setProgressMessage("reading data");
                    state= STATE_DATA;
                    dataSetBuilder= new DataSetBuilder( 2, 100, nelements );
                } else if ( localName.equals("TR") && state.equals(STATE_DATA) ) {
                    state= STATE_RECORD;
                    index= 0;
                } else if ( localName.equals("TD") && state.equals(STATE_RECORD) ) {
                    state= STATE_FIELD;
                    valueBuilder.delete(0, valueBuilder.length() );
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if ( localName.equals("TD") ) {
                    assert state.equals(STATE_FIELD);
                    String s= valueBuilder.toString();
                    //logger.finest( "index:"+index+ " s:"+s );

                    int arraysize= arraysizes.get(index);
                    if ( arraysize>0 ) { // note zero-length array not supported.
                        Units u=  units.get(index);
                        String[] ss= s.trim().split("\\s+");
                        if ( ss.length!=arraysize ) {
                            throw new IllegalArgumentException("values in votable don't match arraysize");
                        }
                        for ( int jj=0; jj<arraysize; jj++ ) {
                            try {
                                dataSetBuilder.putValue( -1, ielement, u.parse( ss[jj] ).doubleValue(u) );
                                ielement++;
                            } catch (ParseException ex) {
                                throw new IllegalArgumentException("unable to parse: "+ss[jj]);
                            }
                        }
                    } else {
                        if ( s.equals(fillValues.get(index) ) ) {
                            dataSetBuilder.putValue( -1, ielement, FILL_VALUE );      
                        } else {
                            try {
                                Units u=  units.get(index);
                                Datum d;
                                if ( u instanceof EnumerationUnits ) {
                                    if ( stopEnumerations.get(index) ) {
                                        d= u.createDatum( 1 );
                                    } else {
                                        d= ((EnumerationUnits)u).createDatum( s );
                                        if ( d.doubleValue(u) > UNIQUE_ENUMERATION_VALUES_LIMIT ) {
                                            stopEnumerations.set(index,true);
                                        }
                                    }
                                } else {
                                    d= u.parse( s );
                                }
                                dataSetBuilder.putValue( -1, ielement, d.doubleValue(u) );                            
                            } catch (ParseException ex) {
                                Logger.getLogger(VOTableReader.class.getName()).log(Level.SEVERE, null, ex);
                                dataSetBuilder.putValue( -1, ielement, FILL_VALUE );
                            }
                        }
                        ielement++;
                    }
                    
                    state= STATE_RECORD;
                    index++;
                    int nrec= dataSetBuilder.getLength();
                    if ( nrec % 1000 == 0 ) {
                        monitor.setProgressMessage("reading data, "+nrec+" records");
                    }
                } else if ( localName.equals("TR") ) {
                    assert state.equals(STATE_RECORD);
                    dataSetBuilder.nextRecord();
                    state= STATE_DATA;
                    index=0;
                    ielement=0;
                    if ( monitor.isCancelled() ) {
                        throw new RuntimeException("reading is interrupted");
                    }
                } else if ( localName.equals("FIELD")  ) {
                    assert state.equals(STATE_HEADER);
                    index++; // counting up items.
                } else if ( localName.equals("DATA") ) {
                    assert state.equals(STATE_HEADER);
                } else if ( localName.equals("DESCRIPTION" ) ) {
                    assert state.equals(STATE_DESCRIPTION);
                    state= STATE_HEADER;
                    descriptions.set((index), valueBuilder.toString() );
                    
                }
            }
            
            
            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if ( STATE_FIELD.equals(state) ) {
                    valueBuilder.append( ch, start, length );
                } else if ( STATE_DESCRIPTION.equals(state) ) {
                    valueBuilder.append( ch, start, length );
                }
            }
        }; 
    }
    
    /**
     * Get the dataset.
     * @return 
     */
    public QDataSet getDataSet() {
        if ( dataSetBuilder==null ) {
            throw new IllegalArgumentException("table has not been read!");
        }
                
        dataSetBuilder.putProperty( QDataSet.BUNDLE_1, formBundleDescriptor() );
        DDataSet result= dataSetBuilder.getDataSet();
        
        for ( int jj=0; jj<ids.size(); jj++ ) {
            if ( stopEnumerations.get(jj)==Boolean.TRUE ) {
                logger.log(Level.INFO, "clear out enumeration at {0}, too many different values.", jj);
                for ( int ii=0; ii<result.length(); ii++ ) {
                    result.putValue( ii, jj, FILL_VALUE );
                    ((MutablePropertyDataSet)result.property(QDataSet.BUNDLE_1)).putProperty( QDataSet.UNITS, jj, null );
                }
            }
        }
        return result;
    }
    
    /**
     * create the bundle descriptor for the data.
     * @return 
     */
    private QDataSet formBundleDescriptor() {
        SparseDataSetBuilder head= new SparseDataSetBuilder(2);
        head.setQube( new int[] { nelements, 0 } ); // all datasets must be or are made to be rank 1.
        
        int ielement=0;
        for ( int ii=0; ii<ids.size(); ii++ ) {
            if ( arraysizes.get(ii)>0 ) {
                for ( int jj=0; jj<arraysizes.get(ii); jj++ ) {
                    head.putProperty( QDataSet.NAME, ielement, ids.get(ii)+"_"+ielement );
                    head.putProperty( QDataSet.LABEL, ielement, names.get(ii) ); 
                    head.putProperty( QDataSet.UNITS, ielement, units.get(ii) ); 
                    if ( fillValues.get(ii)!=null ) {
                        head.putProperty( QDataSet.FILL_VALUE, ielement, FILL_VALUE ); 
                    }
                    if ( minValues.get(ii)!=null ) {
                        try {
                            head.putProperty( QDataSet.VALID_MIN, ielement, units.get(ii).parse(minValues.get(ii)).doubleValue(units.get(ii)) );
                        } catch ( ParseException ex ) {
                            logger.log( Level.INFO, "unable to parse MIN for {0}", ids.get(ii));
                        }
                    }
                    if ( maxValues.get(ii)!=null ) {
                        //TODO: I think there's an inclusive/exclusive property to look for.  VALID_MAX is inclusive.
                        try {
                            head.putProperty( QDataSet.VALID_MAX, ielement, units.get(ii).parse(maxValues.get(ii)).doubleValue(units.get(ii)) );
                        } catch ( ParseException ex ) {
                            logger.log( Level.INFO, "unable to parse MAX for {0}", ids.get(ii));
                        }
                    }
                    ielement++;
                }
                
            } else {
                head.putProperty( QDataSet.NAME, ielement, ids.get(ii) );
                if ( dep0s.get(ii)>-1 && dep0s.get(ii)<ii ) {
                    head.putProperty( QDataSet.DEPENDNAME_0, ielement, ids.get(dep0s.get(ii)) ); 
                }
                head.putProperty( QDataSet.LABEL, ielement, names.get(ii) ); 
                head.putProperty( QDataSet.UNITS, ielement, units.get(ii) ); 
                head.putProperty( QDataSet.TITLE, ielement, descriptions.get(ii) ); 
                if ( fillValues.get(ii)!=null ) {
                   head.putProperty( QDataSet.FILL_VALUE, ielement, FILL_VALUE ); 
                }
                if ( minValues.get(ii)!=null ) {
                    try {
                        head.putProperty( QDataSet.VALID_MIN, ielement, units.get(ii).parse(minValues.get(ii)).doubleValue(units.get(ii)) );
                    } catch ( ParseException ex ) {
                        logger.log( Level.INFO, "unable to parse MIN for {0}", ids.get(ii));
                    }
                }
                if ( maxValues.get(ii)!=null ) {
                    try {
                        head.putProperty( QDataSet.VALID_MAX, ielement, units.get(ii).parse(maxValues.get(ii)).doubleValue(units.get(ii)) );
                    } catch ( ParseException ex ) {
                        logger.log( Level.INFO, "unable to parse MAX for {0}", ids.get(ii));
                    }
                }                
                ielement++;
            }
            //if ( ii>0 ) head.putProperty( QDataSet.DEPENDNAME_0, ielement, ids.get(0) );
        }
        return head.getDataSet();
    }
    
    /**
     * return just the header for the data.  This is just the BUNDLE_1 property of the dataset 
     * that would have been read for the readTable command.
     * @param s String reference to a local file.
     * @param monitor progress monitor for the read.
     * @return the header.  For example h.property( QDataSet.LABEL, 1 ) 
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException 
     */
    public QDataSet readHeader( String s, ProgressMonitor monitor ) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        
        this.monitor= monitor;
        
        xmlReader.setContentHandler(this.sax);
        
        this.justHeader= true;
        
        try {
            xmlReader.parse( s );
        } catch ( RuntimeException ex ) {
            // this is expected.
        }
        
        
        QDataSet lbds= formBundleDescriptor();
        
        monitor.finished();
        
        return lbds;
        
    }
    
    /**
     * read the table from the stream s.
     * @param s String reference to a local file.
     * @param monitor progress monitor will provide line number updates.
     * @return the bundle dataset loaded.
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException 
     */
    public QDataSet readTable( String s, ProgressMonitor monitor ) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        
        this.monitor= monitor;
        
        xmlReader.setContentHandler(this.sax);
        
        xmlReader.parse( s );
        
        QDataSet ds= getDataSet();
        
        monitor.finished();
        
        return ds;
    }
       
    /**
     * read the table from the stream s.
     * @param s String reference to a local file.
     * @return the bundle dataset loaded.
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException 
     */
    public QDataSet readTable( String s ) throws IOException, SAXException, ParserConfigurationException {
        return readTable( s, new NullProgressMonitor() );
    }
    
//    public static void main( String[] args ) throws SAXException, ParserConfigurationException, IOException {
//        
//        //xmlReader.parse( new File("/home/jbf/ct/autoplot/votable/DATA_2012_2012_FGM_KRTP_1M.xml").toURI().toString() );
//        //xmlReader.parse( new File("/home/jbf/ct/autoplot/data/spase/vo-table/Draft_VOTable_EventLList_Std.xml").toURI().toString() );        
//        //xmlReader.parse( new File("/home/jbf/project/autoplot/pdsppi/data/DATA_MAG_HG_1_92S_I.xml").toURI().toString() );
//
//        String s= new File("/home/jbf/project/autoplot/pdsppi/data/DATA_PWS_SA_48S.xml").toURI().toString();
//        long t0= System.currentTimeMillis();
//        QDataSet ds= new VOTableReader().readTable( s );
//        System.err.println( String.format( "Read in %d millis: %s", System.currentTimeMillis()-t0, ds ) );
//        OutputStream out= new FileOutputStream("/tmp/vospase.qds");
//        try {
//            new BundleStreamFormatter().format( ds, out, true );
//            System.err.println( String.format( "Write in %d millis: %s", System.currentTimeMillis()-t0, ds ) );
//        } catch (StreamException ex) {
//            logger.log(Level.SEVERE, ex.getMessage(), ex);
//        } finally {
//            out.close();
//        }
//                
//    }
}
