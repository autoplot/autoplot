
package org.autoplot.spase;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.das2.qds.DDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SparseDataSetBuilder;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
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
    
    List<String> ids= new ArrayList<>();
    List<String> descriptions=  new ArrayList<>(); // one-line describing the data.
    List<Integer> dep0s= new ArrayList<>();
    List<String> datatypes= new ArrayList<>(); // we only support double and UTC then the ucd is time.epoch.
    List<Integer> arraysizes= new ArrayList<>(); // support for 2-D arrays.  -1,0 or N  -2 means *, -1 means scalar, positive means 2-D array
    List<String> names= new ArrayList<>();
    List<String> sunits= new ArrayList<>(); // Equal to UTC for time types.  Can be null.
    List<Units> units= new ArrayList<>();
    List<String> fillValues= new ArrayList<>(); // the fill value representation
    List<String> minValues=  new ArrayList<>(); // the minimum value representation
    List<String> maxValues=  new ArrayList<>(); // the maximum value representation
    List<Boolean> stopEnumerations= new ArrayList<>();  // if true, don't attempt to preserve enumerations.
                    
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
    
    private HashSet<String> warnings= new HashSet<>();
    
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
                        } else if ( name.equalsIgnoreCase("UTC") ) {
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
                    if ( null==sunit ) {
                        units.add(Units.dimensionless);
                        lookForCurrentDep0= true;
                    } else switch (sunit) {
                        case UNIT_UTC:
                            units.add(Units.cdfTT2000);
                            if ( lookForCurrentDep0==true  ) {
                                currentDep0= descriptions.size();
                                lookForCurrentDep0= false;
                            }   break;
                        case UNIT_ENUM:
                            units.add( EnumerationUnits.create(id) );
                            lookForCurrentDep0= true;
                            break;
                        default:
                            units.add( Units.lookupUnits( sunit) );
                            lookForCurrentDep0= true;
                            break;
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
                } else if ( localName.equalsIgnoreCase("TR") && state.equals(STATE_DATA) ) {
                    state= STATE_RECORD;
                    index= 0;
                } else if ( localName.equalsIgnoreCase("TD") && state.equals(STATE_RECORD) ) {
                    state= STATE_FIELD;
                    valueBuilder.delete(0, valueBuilder.length() );
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if ( localName.equalsIgnoreCase("TD") ) {
                    assert state.equals(STATE_FIELD);
                    String s= valueBuilder.toString();
                    int slen=s.length();
                    if ( slen>1 && s.charAt(0)=='"' && s.charAt(slen-1)=='"' ) { // pop off quotes, which were in stream from DITDOS.
                        s= s.substring(1,slen-1);
                    }
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
                                if ( !warnings.contains(ex.getMessage()) ) {
                                    Logger.getLogger(VOTableReader.class.getName()).log(Level.SEVERE, null, ex);
                                    warnings.add(ex.getMessage());
                                }
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
                } else if ( localName.equalsIgnoreCase("TR") ) {
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
     * Get the dataset.  If no records were read, then a zero-length dataset is
     * returned.
     * @return 
     */
    public QDataSet getDataSet() {
        if ( dataSetBuilder==null ) {
            throw new IllegalArgumentException("table has not been read!");
        }
                
        dataSetBuilder.putProperty( QDataSet.BUNDLE_1, formBundleDescriptor() );
        if ( dataSetBuilder.getLength()>0 ) {
            DDataSet result= dataSetBuilder.getDataSet();

            for ( int jj=0; jj<ids.size(); jj++ ) {
                if ( Boolean.TRUE.equals( stopEnumerations.get(jj) ) ) {
                    logger.log(Level.INFO, "clear out enumeration at {0}, too many different values.", jj);
                    for ( int ii=0; ii<result.length(); ii++ ) {
                        result.putValue( ii, jj, FILL_VALUE );
                        ((MutablePropertyDataSet)result.property(QDataSet.BUNDLE_1)).putProperty( QDataSet.UNITS, jj, null );
                    }
                }
            }
            return result;
            
        } else {
            return DDataSet.createRank2(0,10);
            
        }
    }
    
    /**
     * create the bundle descriptor for the data.
     * @return 
     */
    private QDataSet formBundleDescriptor() {
        SparseDataSetBuilder head= new SparseDataSetBuilder(2);
        head.setLength( nelements ); // all datasets must be or are made to be rank 1.
        
        int ielement1=0;
        for ( int ii=0; ii<ids.size(); ii++ ) {
            if ( arraysizes.get(ii)>0 ) {
                int first= ielement1;
                for ( int jj=0; jj<arraysizes.get(ii); jj++ ) {
                    head.putProperty( QDataSet.NAME, ielement1, ids.get(ii)+"_"+ielement1 );
                    head.putProperty( QDataSet.LABEL, ielement1, names.get(ii) ); 
                    head.putProperty( QDataSet.UNITS, ielement1, units.get(ii) ); 
                    head.putProperty( QDataSet.TITLE, ielement1, descriptions.get(ii) );
                    if ( jj==0 ) {
                        head.putValue( ielement1, 0, arraysizes.get(ii) );
                        head.putProperty( QDataSet.ELEMENT_LABEL, ielement1, names.get(ii) );
                        head.putProperty( QDataSet.ELEMENT_NAME, ielement1, ids.get(ii) );
                        head.putProperty( QDataSet.DEPEND_1, ielement1, Ops.findgen(arraysizes.get(ii)) );
                    }
                    head.putProperty( QDataSet.START_INDEX, ielement1, first );
                    
                    if ( fillValues.get(ii)!=null ) {
                        head.putProperty( QDataSet.FILL_VALUE, ielement1, FILL_VALUE ); 
                    }
                    if ( minValues.get(ii)!=null ) {
                        try {
                            head.putProperty( QDataSet.VALID_MIN, ielement1, units.get(ii).parse(minValues.get(ii)).doubleValue(units.get(ii)) );
                        } catch ( ParseException ex ) {
                            logger.log( Level.INFO, "unable to parse MIN for {0}", ids.get(ii));
                        }
                    }
                    if ( maxValues.get(ii)!=null ) {
                        //TODO: I think there's an inclusive/exclusive property to look for.  VALID_MAX is inclusive.
                        try {
                            head.putProperty( QDataSet.VALID_MAX, ielement1, units.get(ii).parse(maxValues.get(ii)).doubleValue(units.get(ii)) );
                        } catch ( ParseException ex ) {
                            logger.log( Level.INFO, "unable to parse MAX for {0}", ids.get(ii));
                        }
                    }
                    ielement1++;
                }
                
            } else {
                head.putProperty( QDataSet.NAME, ielement1, ids.get(ii) );
                if ( dep0s.get(ii)>-1 && dep0s.get(ii)<ii ) {
                    head.putProperty( QDataSet.DEPENDNAME_0, ielement1, ids.get(dep0s.get(ii)) ); 
                }
                head.putProperty( QDataSet.LABEL, ielement1, names.get(ii) ); 
                head.putProperty( QDataSet.UNITS, ielement1, units.get(ii) ); 
                head.putProperty( QDataSet.TITLE, ielement1, descriptions.get(ii) ); 
                if ( fillValues.get(ii)!=null ) {
                   head.putProperty( QDataSet.FILL_VALUE, ielement1, FILL_VALUE ); 
                }
                if ( minValues.get(ii)!=null ) {
                    try {
                        head.putProperty( QDataSet.VALID_MIN, ielement1, units.get(ii).parse(minValues.get(ii)).doubleValue(units.get(ii)) );
                    } catch ( ParseException ex ) {
                        logger.log( Level.INFO, "unable to parse MIN for {0}", ids.get(ii));
                    }
                }
                if ( maxValues.get(ii)!=null ) {
                    try {
                        head.putProperty( QDataSet.VALID_MAX, ielement1, units.get(ii).parse(maxValues.get(ii)).doubleValue(units.get(ii)) );
                    } catch ( ParseException ex ) {
                        logger.log( Level.INFO, "unable to parse MAX for {0}", ids.get(ii));
                    }
                }                
                ielement1++;
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
