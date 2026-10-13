
package org.autoplot.pds;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import static org.autoplot.pds.PdsDataSourceFactory.getFileResource;
import org.autoplot.pds4.FieldType;
import org.autoplot.pds4.Pds4Reader;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.NumberUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class Pds4DataSource extends AbstractDataSource {
    public Pds4DataSource(URI uri) {
        super(uri);
    }
    
    /**
     * Read the XML file into a document.
     * @param f the file
     * @return the document object
     * @throws IOException
     * @throws SAXException
     */
    public static Document readXML( File f ) throws IOException, SAXException {
        DocumentBuilder builder= null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            //TODO: ChatGPT has some nice security settings, see server-java project in HAPI
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

        Document document;
        
        try (InputStream in = new FileInputStream(f)) {
            InputSource source = new InputSource( in );
            document = builder.parse(source);
        }     
        
        return document;
    }
        
    /**
     * given the bundle, figure out which files should be loaded to implement the time range.  This will call recursively
     * into this code for each item.  This unimplemented stub returns an empty dataset.
     * //TODO: implement me
     * @param doc the xml document
     * @param mon progress monitor
     * @return rank 0 stub
     * @throws Exception 
     */
    public org.das2.qds.QDataSet getDataSetFromBundle(Document doc,ProgressMonitor mon) throws Exception {
        
        XPathExpression xp= XPathFactory.newInstance().newXPath().compile(
                "//Product_Bundle/Bundle_Member_Entry/lidvid_reference/text()");
        String lidvid= (String)xp.evaluate( doc, XPathConstants.STRING );
        
        if ( lidvid.trim().length()==0 ) {
            throw new IllegalArgumentException("lidvid is empty or not found at "+
                    "//Product_Bundle/Bundle_Member_Entry/lidvid_reference/text()");
        }
        
        return Ops.dataset(lidvid,Units.nominal());
    }
    
    /**
     * given the collection, figure out which files should be loaded to implement the time range.  This will call recursively
     * into this code for each item.  This unimplemented stub returns an empty dataset.
     * //TODO: implement me
     * @param doc the xml document
     * @param mon progress monitor
     * @return rank 0 stub
     * @throws Exception 
     */
    public org.das2.qds.QDataSet getDataSetFromCollection(Document doc,ProgressMonitor mon) throws Exception {
        
        XPathExpression xp= XPathFactory.newInstance().newXPath().compile(
                "//Product_Collection/File_Area_Inventory/File/file_name/text()");
        String csvfile= (String)xp.evaluate( doc, XPathConstants.STRING );
        
        if ( csvfile.trim().length()==0 ) {
            throw new IllegalArgumentException("file name is empty or not found at "+
                "//Product_Collection/File_Area_Inventory/File/file_name/text()");
        }
        
        return Ops.dataset(csvfile,Units.nominal());
    }
    
    private static void addAxisArray( Node n,  Map<Integer,String> axisNames ) throws XPathExpressionException {
        XPathFactory factory= XPathFactory.newInstance();
        XPath xpath= factory.newXPath();
        String name =   (String)xpath.evaluate( "axis_name", n, XPathConstants.STRING );
        Double sequence_number = (Double)xpath.evaluate( "sequence_number", n, XPathConstants.NUMBER );
        axisNames.put( sequence_number.intValue(), name );
    }    
    
    /**
     * return the name of the independent parameter that works in this axis.
     * This currently assumes the first node with this axisName is the 
     * independent axis.
     * 
     * For example, with https://space.physics.uiowa.edu/voyager/data/voyager-2-pws-wf/data/1987/vg2_pws_wf_1987-04-21T17_v1.0.xml,
     * if axisName=='time' then the result will be "Epoch"
     * 
     * This shows where this logic fails:
     * https://pds-ppi.igpp.ucla.edu/data/maven-swea-calibrated/data/arc_pad/2016/03/mvn_swe_l2_arcpad_20160316_v04_r01.xml
     * For this file, I had to kludge in a test for the pitch angles.
     * 
     * @param doc the xml document
     * @param axisName the axis name
     * @return null or the independent variable for the axis.
     * @throws javax.xml.xpath.XPathExpressionException
     */
    public static String resolveIndependentAxis( Document doc, String axisName ) throws XPathExpressionException {
            
        XPathFactory factory= XPathFactory.newInstance();
        XPath xpath= factory.newXPath();

        String s=  "Product_Observational/File_Area_Observational/Array[Axis_Array/axis_name='"+axisName +"']";
        NodeList oo=   (NodeList) xpath.evaluate( s, doc, XPathConstants.NODESET );

        // jbf: I don't see how one can resolve the independent parameter properly.
        // I'll go through and find the lowest rank data with the axis.
        // "pitch angle" -> "pa"
        if ( oo.getLength()>0 ) {
            int best=0;
            for ( int i=0; i<oo.getLength(); i++ ) {
                Node o = oo.item(i);
                String name = (String)xpath.evaluate( "name", o, XPathConstants.STRING );
                if ( axisName.equals("pitch angle") && name.equals("pa") ) {  //kludge for mvn_swe_l2_arcpad_20160316_v04_r01.xml
                    best= i;
                }
            }
            Node o=  oo.item(best);

            String axes= (String)xpath.evaluate( "axes", o, XPathConstants.STRING );
            
            if ( Integer.parseInt(axes)==1 ) {
                String name = (String)xpath.evaluate( "name", o, XPathConstants.STRING );
                return name;
            } else {
                return null;
            }
            
        }
        
        return null;
    }
    
    /**
     * look through the PDS label document to see if dependencies can be 
     * identified.  Presently, this is simply one other dataset with the 
     * same axis (as in sample_offset) or the same axis name as something
     * that has a time unit (Epoch).  
     * @see https://space.physics.uiowa.edu/voyager/data/voyager-2-pws-wf/data/1987/vg2_pws_wf_1987-04-21T17_v0.9.xml
     * @param doc the parsed document for the label XML
     * @param depend the name of the data for the dependent variable, e.g. Waveform
     * @return ( Epoch, sample_offset, Waveform ) 
     * @throws javax.xml.xpath.XPathExpressionException
     */
    public static List<String> seekDependencies( Document doc, List<String> depend ) throws XPathExpressionException {
        if ( depend.size()==1 ) { // always will have one element.
        
            XPathFactory factory= XPathFactory.newInstance();
            XPath xpath= factory.newXPath();
        
            String name= depend.get(0);
            
            Map<Integer,String> axisNames= new LinkedHashMap<>();
            
            NodeList oo= (NodeList) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Axis_Array", doc, XPathConstants.NODESET );
            
            for ( int i=0; i<oo.getLength(); i++ ) {
                Node n = oo.item(i);
                addAxisArray( n, axisNames );
            }
            
            if ( axisNames.get(4)!=null ) {
                String n1= resolveIndependentAxis( doc, axisNames.get(1) );
                String n2= resolveIndependentAxis( doc, axisNames.get(2) );
                String n3= resolveIndependentAxis( doc, axisNames.get(3) );
                String n4= resolveIndependentAxis( doc, axisNames.get(4) );
                depend= new LinkedList<>(depend);
                depend.add(0,n1);
                depend.add(1,n2);
                depend.add(2,n3);
                if ( n4!=null && !n4.equals(name) ) {
                    depend.add(3,n4);
                }                
            } else if ( axisNames.get(3)!=null ) {
                String n1= resolveIndependentAxis( doc, axisNames.get(1) );
                String n2= resolveIndependentAxis( doc, axisNames.get(2) );
                String n3= resolveIndependentAxis( doc, axisNames.get(3) );
                depend= new LinkedList<>(depend);
                depend.add(0,n1);
                depend.add(1,n2);
                if ( n3!=null && !n3.equals(name) ) {
                    depend.add(2,n3);
                }                
            } else if ( axisNames.get(2)!=null ) {
                String n1= resolveIndependentAxis( doc, axisNames.get(1) );
                String n2= resolveIndependentAxis( doc, axisNames.get(2) );
                depend= new LinkedList<>(depend);
                depend.add(0,n1);
                if ( n2!=null && !n2.equals(name) ) {
                    depend.add(1,n2);
                }
            } else if ( axisNames.get(1)!=null ) {
                String n1= resolveIndependentAxis( doc, axisNames.get(1) );
                depend= new LinkedList<>(depend);
                if ( n1!=null && !n1.equals(name) ) {
                    depend.add(0,n1);
                }
            }
            
        }
        
        return depend;
    }

    private static boolean isTimeType( Pds4Reader.Field ff ) {
        if ( ff.getType()==FieldType.ASCII_STRING && ff.name.equals("UTC") ) { //https://pds-ppi.igpp.ucla.edu/data/cassini-caps-calibrated/data-ion/2010/001_031_JAN/ION_201000100_V01.xml
            return true;
        }
        FieldType ft= ff.getType();
        return ft.isTime();
//        return ft==FieldType.ASCII_DATE ||  
//                ft==FieldType.ASCII_DATE_DOY ||
//                ft==FieldType.ASCII_DATE_TIME ||
//                ft==FieldType.ASCII_DATE_TIME_DOY_UTC ||
//                ft==FieldType.ASCII_DATE_TIME_UTC ||
//                ft==FieldType.ASCII_DATE_TIME_YMD ||
//                ft==FieldType.ASCII_DATE_TIME_YMD_UTC ;          
    }    
    
    /**
     * bootstrap routine for getting data from fields of a TableObject.  TODO: rewrite so that
     * multiple fields are read at once.
     * @param t
     * @param columnName
     * @return
     * @throws IOException 
     */
    private QDataSet getFromTable( Pds4Reader.Table t, String[] columnNames ) throws Exception {
        
        int ncols= columnNames.length;
        int[] icols= new int[ncols];
        
        DataSetBuilder dsb= new DataSetBuilder(2,100,ncols);
        
        int currentColumn= -1;
        int firstColumn= -1;
        for ( int i=0; i<ncols; i++ ) {
            int icol= -1;
            List<Pds4Reader.Field> fields= t.fields;
            for ( int j=0; j<fields.size(); j++ ) {
                if ( fields.get(j).name.equals(columnNames[i]) ) {
                    icol= j;
                    break;
                }
            }
            //TODO: what is returned when column isn't found?
            if ( icol==currentColumn ) {
                icols[i]= currentColumn + ( i-firstColumn );
            } else {
                icols[i]= icol;
                currentColumn= icol;
                firstColumn= i;
            }
            Pds4Reader.Field fieldDescription= t.fields.get(icol);
            dsb.setName( i, fieldDescription.name );
            dsb.setLabel( i, fieldDescription.name );
            //TODO: Larry has nice descriptions.  How to get at those? https://space.physics.uiowa.edu/pds/cassini-rpws-electron_density/data/2006/rpws_fpe_2006-141_v1.xml
            //TODO: also https://pds-ppi.igpp.ucla.edu/data/cassini-caps-fitted-parameters/Data/CAS_CAPS_FITTED_PARAMETERS_WILSON_V01.xml?Sc_lat&X=Utc
            if ( isTimeType(fieldDescription) ) {
                dsb.setUnits(i, Units.us2000);
            } else if ( fieldDescription.getType()==FieldType.ASCII_STRING ) {
                dsb.setUnits(i, Units.nominal(fieldDescription.name) );
            } else {
                dsb.setUnits(i, Units.dimensionless ); // TODO: how to get "unit" from label
            }
        }
        
        boolean doTimeCheck= true; // allow ASCII_STRING to contain times, flipping from nominal units to time location units.
        
        
        Pds4Reader.Cursor c= t.cursor();
        Object[] record;
        
        while ( ( record= c.next() )!=null ) {
            for ( int i=0; i<ncols; i++ ) {
                try {
                    Object o= record[icols[i]];
                    if ( doTimeCheck ) {
                        if ( o instanceof String ) {
                            String s= (String)o;
                            if ( DatumRangeUtil.parseISO8601(s)!=null && !( dsb.getUnits(i) instanceof NumberUnits ) ) {
                                dsb.setUnits( i, Units.us2000 );
                            }
                        } else {
                            dsb.putValue( -1, i, dsb.getUnits(i).getFillDatum() );
                        }
                    }
                    if ( o instanceof String ) {
                        dsb.putValue( -1, i, (String)o );
                    } else {
                        dsb.putValue( -1, i, (Double)o );
                    }
                } catch (ParseException ex) {
                    dsb.putValue( -1, i, dsb.getUnits(i).getFillDatum() );
                }
            }
            doTimeCheck= false;
            dsb.nextRecord();
        }
        
        return dsb.getDataSet();
    }
    
    private void addMetadata( String name, MutablePropertyDataSet result1, Document doc ) {
        if ( result1!=null && doc!=null ) {
            try {
                Units units=null;
                XPathFactory factory= XPathFactory.newInstance();
                XPath xpath= factory.newXPath();
                
                String sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Element_Array/unit/text()", doc );
                sunits= sunits.trim();
                if ( sunits.length()>0 && units==null ) {
                    result1.putProperty( QDataSet.UNITS, Units.lookupUnits(sunits) );
                }
                if ( units==null || !UnitsUtil.isTimeLocation(units) ) {
                    String labl= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/name/text()", doc );
                    if ( labl.length()==0 ) labl= name;
                    result1.putProperty( QDataSet.LABEL, labl );
                    String title= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/description/text()", doc );
                    if ( title.length()>0 ) {
                        result1.putProperty( QDataSet.TITLE, title.trim() );
                    }
                }
                
                String sfillValue= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Special_Constants/invalid_constant/text()", doc );
                if ( sfillValue.length()==0 )
                    sfillValue= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Special_Constants/missing_constant/text()", doc );
                String svalidMax= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Special_Constants/valid_maximum/text()", doc );
                String svalidMin= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Special_Constants/valid_minimum/text()", doc );
                if ( sfillValue.trim().length()>0 ) {
                    double fillValue= Double.parseDouble(sfillValue);
                    result1.putProperty( QDataSet.FILL_VALUE, fillValue );
                }
                if ( svalidMax.trim().length()>0 ) {
                    double validMax= Double.parseDouble(svalidMax);
                    if ( Math.log10(validMax)>-50  ) {  //https://pds-ppi.igpp.ucla.edu/data/maven-static-c/data/c6_32e64m/2014/10/mvn_sta_l2_c6-32e64m_20141013_v02_r01.xml?TT2000
                        result1.putProperty( QDataSet.VALID_MAX, validMax );
                    } else {
                        logger.log(Level.WARNING, "Unbelievable value found for Special_Constants/valid_maximum, ignoring: {0}", svalidMax);
                    }
                }
                if ( svalidMin.trim().length()>0 ) {
                    double validMin= Double.parseDouble(svalidMin);
                    result1.putProperty( QDataSet.VALID_MIN, validMin );
                }   
            } catch (XPathExpressionException ex) {
                Logger.getLogger(Pds4DataSource.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception, URISyntaxException, ParserConfigurationException, SAXException, XPathExpressionException {
        String name= getParam("arg_0","");
        
        URISplit split= URISplit.parse( getURI() );
        
        URL labelUrl= split.resourceUri.toURL();
        
        Pds4Reader.DataObject data= getDataObject(labelUrl, name, mon);
            
        File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
        Document doc= readXML(xmlfile);
        
        if ( doc.getDocumentElement().getNodeName().equals("Product_Bundle") ) {
            return getDataSetFromBundle(doc,mon);
        }
        
        if ( doc.getDocumentElement().getNodeName().equals("Product_Collection")) {
            return getDataSetFromCollection(doc,mon);
        }
        
        URL fileUrl;
        fileUrl = PdsDataSourceFactory.getFileResource( labelUrl, mon );
        
        DataSetURI.getFile( fileUrl,mon );
        
        

        
        File labelFile = DataSetURI.getFile(labelUrl,mon );
                    
        Pds4Reader.Product label;
        try {
            label = Pds4Reader.open( labelFile.toPath() ); 
        } catch ( java.lang.NoClassDefFoundError ex ) {
            throw new Exception("Java 8 must be used to read PDS4 files.  Please use dmg, exe, deb, or rpm releases, or the single jar with Java 8.");
        }
        
        QDataSet result=null;
                        
        try {
            List<String> names= new ArrayList<>();
            String X= getParam("X","");
            if ( !X.equals("") ) {
                X = X.replaceAll("\\+", " ");
                names.add(X);
            }
            String Y= getParam("Y","");
            if ( !Y.equals("") ) {
                Y = Y.replaceAll("\\+", " ");
                names.add(Y);
            }

            String Z= getParam("Z","");
            if ( !Z.equals("") ) {
                Z = Z.replaceAll("\\+", " ");
                names.add(Z);
            }

            if ( !name.equals("") ) {
                name = name.replaceAll("\\+", " ");
                names.add(name);
            }

            List<String> names1= seekDependencies(doc, names );
            boolean okay= true;
            for ( int i=0; i<names1.size(); i++ ) {
                if ( names1.get(i)==null ) okay=false;
            }
            if ( okay ) {
                names= names1;
            }

            List<Pds4Reader.DataObject> objects= label.objects;
            
            // See if there's an obvious connection between table columns (and
            // the first time column)

            if ( names.size()==1 ) {
                for ( Pds4Reader.DataObject d: objects ) {
                    if ( d instanceof Pds4Reader.Table ) {
                        Pds4Reader.Table t= (Pds4Reader.Table)d;
                        if ( isTimeType( t.fields.get(0) ) ) {
                            String dep0name= t.fields.get(0).name;
                            List<String> newNames= new ArrayList<>(names);
                            for ( int i=0; i<names.size(); i++ ) {
                                name= names.get(i);
                                if ( name==null ) {
                                    
                                } else {
                                    for ( Pds4Reader.Field fd: t.fields ) {
                                        if ( name.equals( fd.name ) ) { 
                                            if ( !newNames.get(0).equals(dep0name) ) {
                                                newNames.add( 0, dep0name );
                                            }
                                        }
                                    }
                                }
                            }
                            if ( newNames.size()>names.size() ) names= newNames;
                        }
                    }
                }
            }

            QDataSet[] results= new QDataSet[names.size()];

            // see which parameters will come from tables.
            for ( Pds4Reader.DataObject d : objects ) {
                if ( d instanceof Pds4Reader.Table ) {
                    Pds4Reader.Table t= (Pds4Reader.Table)d;
                    List<String> tableColumnNames= new ArrayList<>();
                    List<Integer> datasetColumnIndexes= new ArrayList<>();

                    for ( int i=0; i<names.size(); i++ ) {
                        name= names.get(i);
                        if ( name==null ) {

                        } else {
                            for ( Pds4Reader.Field fd: t.fields ) {
                                if ( name.equals( fd.name ) ) { 
                                    tableColumnNames.add( fd.name );
                                    datasetColumnIndexes.add(i);
                                }
                            }
                        }
                    }
                    if ( !tableColumnNames.isEmpty() ) {
                        QDataSet bresults= getFromTable( t, tableColumnNames.toArray(new String[tableColumnNames.size()]) );
                        int iii=0;
                        while ( iii<datasetColumnIndexes.size() ) {
                            int i= datasetColumnIndexes.get(iii);
                            name= tableColumnNames.get(iii);
                            int iii2= i+1;
                            while ( iii2<datasetColumnIndexes.size() && datasetColumnIndexes.get(iii2)==i ) {
                                iii2++;
                            }
                            ArrayDataSet result1;
                            if ( iii2-iii>1 ) {
                                result1= DDataSet.copy( Ops.trim1( bresults, i, i+(iii2-iii) ) );
                            } else {
                                result1= DDataSet.copy( Ops.unbundle( bresults, i ) );
                            }
                            results[i]= result1;
                            Units units= (Units) result1.property(QDataSet.UNITS);

                            XPathFactory factory= XPathFactory.newInstance();
                            XPath xpath= factory.newXPath();

                            String s;

                            s = "Table_Character/Record_Character/Field_Character[name='"+name+"']/";
                            String sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"unit/text()", doc );
                            sunits= sunits.trim();
                            if ( sunits.length()==0 ) {
                                s= "Table_Binary/Record_Binary/Field_Binary[name='"+name+"']/";
                                sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"unit/text()", doc );
                                sunits= sunits.trim();
                            }
                            if ( sunits.length()==0 ) {
                                s= "Table_Binary/Record_Binary/Group_Field_Character/Field_Character[name='"+name+"']/";
                                sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"unit/text()", doc );
                                sunits= sunits.trim();
                            }
                            if ( sunits.length()==0 ) {
                                s= "Table_Binary/Record_Binary/Group_Field_Binary/Field_Binary[name='"+name+"']/";
                                sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"unit/text()", doc );                        
                                sunits= sunits.trim();
                            }

                            if ( sunits.length()>0 ) {
                                result1.putProperty( QDataSet.UNITS, Units.lookupUnits(sunits) );
                            }
                            if ( units==null || !UnitsUtil.isTimeLocation(units) ) {
                                String labl= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"name/text()", doc ); // TODO: Stupid, isn't this?
                                if ( labl.length()==0 ) labl= name;
                                ((MutablePropertyDataSet)results[i]).putProperty( QDataSet.LABEL, labl );
                                String title= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"description/text()", doc );
                                if ( title.length()>0 ) {
                                    title= DocumentUtil.createTitleFrom(title);
                                    result1.putProperty( QDataSet.TITLE, title );
                                }

                                String sfillValue= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"Special_Constants/invalid_constant/text()", doc );
                                if ( sfillValue.length()==0 ) 
                                    sfillValue= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"Special_Constants/missing_constant/text()", doc );
                                String svalidMax= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"Special_Constants/valid_maximum/text()", doc );
                                String svalidMin= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/"+s+"Special_Constants/valid_minimum/text()", doc );
                                if ( sfillValue.trim().length()>0 ) {
                                    double fillValue= Double.parseDouble(sfillValue);
                                    result1.putProperty( QDataSet.FILL_VALUE, fillValue );
                                }
                                if ( svalidMax.trim().length()>0 ) {
                                    double validMax= Double.parseDouble(svalidMax);
                                    result1.putProperty( QDataSet.VALID_MAX, validMax );
                                }
                                if ( svalidMin.trim().length()>0 ) {
                                    double validMin= Double.parseDouble(svalidMin);
                                    result1.putProperty( QDataSet.VALID_MIN, validMin );
                                }
                            }

                            iii= iii2;
                        }
                    }
                }
            }

            for ( int i=0; i<names.size(); i++ ) {
                if ( results[i]!=null ) continue;
                name= names.get(i);
                for ( Pds4Reader.DataObject d : objects ) {
                    if ( d instanceof Pds4Reader.Array ) {
                        Pds4Reader.Array a= (Pds4Reader.Array)d;
                        if ( a.name.equals(name) ) {
                            String delegateUri= resolveUri(a);

                            DataSource delegate= DataSetURI.getDataSource(delegateUri);
                            QDataSet ds= delegate.getDataSet( mon.getSubtaskMonitor( "dataset "+ i ) );
                            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(name) );
                            ds= Ops.putProperty( ds, QDataSet.LABEL, name );
                            ds= Ops.putProperty(ds, QDataSet.DESCRIPTION, DocumentUtil.cleanDescriptionString( a.description ) );
                            HashMap<String,Object> user= new HashMap<>();
                            user.put("delegate_uri",delegateUri);
                            ds= Ops.putProperty( ds, QDataSet.USER_PROPERTIES, user );
                            boolean isCDF= fileUrl.getFile().toLowerCase().endsWith(".cdf") 
                                    && ( a.element.dataType==FieldType.SIGNEDMSB8 || a.element.dataType==FieldType.SIGNEDLSB8 );
                            addMetadata(name, (MutablePropertyDataSet)ds, doc);
                            if ( isCDF && ( name.equalsIgnoreCase("Epoch") || name.equalsIgnoreCase("tt2000") ) ) { 
                                logger.fine("Epoch kludge results in CDF_TT2000 units");
                                Units units= Units.cdfTT2000;
                                ds= Ops.putProperty( ds, QDataSet.UNITS, units );
                            }
                            results[i]= ds;
                        }
                    }
                }
            }

            //for ( DataObject d : objects ) {
            //    if ( d!=null ) d.closeChannel();
            //}
            
            // check for missing data and throw useful error.
            ArrayList<String> missing= new ArrayList();
            for ( int i=0; i<results.length; i++ ) {
                if ( results[i]==null ) {
                    missing.add(names.get(i));                    
                }
            }
            if ( !missing.isEmpty() ) {
                throw new IllegalArgumentException("Unable to locate data for names: "+ String.join(",", missing));
            }
            
            if ( result==null ) {
                switch (results.length) {
                    case 1:
                        result= results[0];
                        break;
                    case 2:
                        result= Ops.link( results[0], results[1] );
                        break;
                    case 3:
                        try {
                            result= Ops.link( results[0], results[1], results[2] );
                        } catch ( Exception ex ) {
                            ((MutablePropertyDataSet)results[2]).putProperty(QDataSet.DEPEND_1,null);
                            result= results[2];
                        }
                        break;
                    case 4:
                        try {
                            result= Ops.link( results[0], results[1], results[2], results[3] );
                        } catch ( Exception ex ) {
                            ((MutablePropertyDataSet)results[3]).putProperty(QDataSet.DEPEND_1,null);
                            ((MutablePropertyDataSet)results[3]).putProperty(QDataSet.DEPEND_2,null);
                            result= results[3];
                        }
                        break;
                    default:
                        break;
                }
            }
        } finally {
            //label.close();
        }
        
        if ( result instanceof MutablePropertyDataSet ) {
            ((MutablePropertyDataSet)result).makeImmutable();
        }
        
        return result;
        
        
    }
        
    /**
     * return a list of parameters.
     * @param url
     * @param mon
     * @return 
     */
    public static Map<String,String> getDataObjectNames( URL url, ProgressMonitor mon) throws Exception {
        
        URL fileUrl= getFileResource( url, mon );
        File xmlfile = DataSetURI.getFile( url,new NullProgressMonitor());
        File file= DataSetURI.getFile( fileUrl,mon );
            
        Map<String,String> result= new LinkedHashMap<>();
        
        Pds4Reader.Product label= Pds4Reader.open( xmlfile.toPath() );
        
        try {
            
            List<Pds4Reader.DataObject> objects=  label.objects;
            
            for ( Pds4Reader.DataObject d : objects ) {
                if ( d instanceof Pds4Reader.Table ) {
                    Pds4Reader.Table t= (Pds4Reader.Table)d;
                    //TODO: can there be more than one table?
                    for ( Pds4Reader.Field fd: t.fields ) {
                        result.put( fd.name, fd.name + " of a table" );
                    }
                }
            }

            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(false);
            DocumentBuilder builder = domFactory.newDocumentBuilder();
            Document doc = builder.parse(xmlfile);

            for ( Pds4Reader.DataObject d: objects ) {
                if ( d instanceof Pds4Reader.Array ) {
                    Pds4Reader.Array a= (Pds4Reader.Array)d;
                    //result.put( a.getName(), a.getDescription() ); //TODO: update PDS4 library
                    String n= a.name;
                    XPathExpression xp= XPathFactory.newInstance().newXPath().compile(
                        "//Product_Observational/File_Area_Observational/Array[name='"+n+"']");
                    org.w3c.dom.Node n1= (org.w3c.dom.Node)xp.evaluate(doc,XPathConstants.NODE);
                    XPathExpression xp2= XPathFactory.newInstance().newXPath().compile(
                        "Axis_Array/axis_name/text()");

                    org.w3c.dom.NodeList nn= (org.w3c.dom.NodeList)xp2.evaluate(n1,XPathConstants.NODESET);
                    String[] ss= new String[nn.getLength()];
                    for ( int i=0; i<ss.length; i++ ) {
                        ss[i]= nn.item(i).getTextContent();
                    }
                    result.put( a.name, a.name + " ("+ String.join(",", ss)+")");
                }
            }
            
            for ( Pds4Reader.DataObject d : objects ) {
                //if ( d!=null ) d.closeChannel();
            }
            
        } finally {
            //label.close();
        }

        return result;
    }

    
    
    private Pds4Reader.DataObject getDataObject( URL url, String name, ProgressMonitor mon ) throws MalformedURLException, ParseException, Exception {
        
        File file= getFile(uri, mon);
        
        Pds4Reader.Product label = Pds4Reader.open( file.toPath() ); // this works
        
        Pds4Reader.DataObject result=null;
        
        try {
            List<Pds4Reader.DataObject> objects = label.objects;
            
            for ( Pds4Reader.DataObject d : objects ) {
                if ( d instanceof Pds4Reader.Table ) {
                    Pds4Reader.Table t=(Pds4Reader.Table)d;
                    for ( Pds4Reader.Field fd: t.fields ) {
                        if ( name.startsWith( fd.name ) ) { //TODO: why startsWith?
                             result= t;
                        }
                    }
                } else if ( d instanceof Pds4Reader.Array ) {
                    Pds4Reader.Array a= (Pds4Reader.Array)d;
                    if ( a.name.equals(name) ) {
                        result= a;
                    }
                }
            }
        } finally {
            //label.close();
        }
        
        return result;
    }

    /**
     * given the array object, what is the binary URI to read this?
     * @param a
     * @return 
     */
    private String resolveUri(Pds4Reader.Array array) {
          FieldType fieldType = array.element.dataType;

        String type;
        switch (fieldType) {
            case SIGNEDBYTE:   type = "byte";   break;
            case UNSIGNEDBYTE: type = "ubyte";  break;

            case SIGNEDLSB2:
            case SIGNEDMSB2:   type = "short";  break;

            case UNSIGNEDLSB2:
            case UNSIGNEDMSB2: type = "ushort"; break;

            case SIGNEDLSB4:
            case SIGNEDMSB4:   type = "int";    break;

            case UNSIGNEDLSB4:
            case UNSIGNEDMSB4: type = "uint";   break;

            case SIGNEDLSB8:
            case SIGNEDMSB8:   type = "long";   break;

            case UNSIGNEDLSB8:
            case UNSIGNEDMSB8: type = "ulong";  break;

            case IEEE754LSBSINGLE:
            case IEEE754MSBSINGLE:
                type = "float";
                break;

            case IEEE754LSBDOUBLE:
            case IEEE754MSBDOUBLE:
                type = "double";
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported array element type: " + fieldType);
        }

        int[] shape = array.shape;
        if (shape.length < 1 || shape.length > 4) {
            throw new IllegalArgumentException(
                    "Unsupported array rank: " + shape.length);
        }

        if (array.firstIndexFastest && shape.length > 1) {
            throw new IllegalArgumentException(
                    "First Index Fastest requires reordering after reading");
        }

        // vap+bin does not implement PDS4 scaling through these parameters.
        if (array.element.scalingFactor != 1.0
                || array.element.valueOffset != 0.0) {
            throw new IllegalArgumentException(
                    "PDS4 scaling must be applied after reading");
        }

        // One record contains all dimensions following the first axis.
        long itemsPerRecord = 1;
        for (int i = 1; i < shape.length; i++) {
            itemsPerRecord = Math.multiplyExact(itemsPerRecord, shape[i]);
        }

        long recordBytes = Math.multiplyExact(
                itemsPerRecord, fieldType.getByteWidth());

        long byteLength = Math.multiplyExact(
                recordBytes, (long) shape[0]);

        // The current binary reader uses int-sized buffers and bit strides.
        if (recordBytes > Integer.MAX_VALUE / 8
                || byteLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Array exceeds the binary reader's size limits");
        }

        Map<String, String> args = new LinkedHashMap<>();
        args.put("type", type);
        args.put("byteOffset", Long.toString(array.offset));
        args.put("byteLength", Long.toString(byteLength));
        args.put("recLength", Long.toString(recordBytes));
        args.put("recCount", Integer.toString(shape[0]));
        args.put("recOffset", "0");

        ByteOrder order = fieldType.getByteOrder();
        if (order != null) {
            args.put("byteOrder",
                    order == ByteOrder.LITTLE_ENDIAN ? "little" : "big");
        }

        if (shape.length > 1) {
            StringBuilder dims = new StringBuilder("[");
            for (int i = 1; i < shape.length; i++) {
                if (i > 1) dims.append(",");
                dims.append(shape[i]);
            }
            dims.append("]");
            args.put("dims", dims.toString());
        }

        if (!array.element.unit.isEmpty()) {
            args.put("units", array.element.unit);
        }

        if (array.element.validMinimum != null) {
            args.put("validMin", array.element.validMinimum);
        }
        if (array.element.validMaximum != null) {
            args.put("validMax", array.element.validMaximum);
        }

        // The binary reader supports one fillValue. Multiple distinct PDS4
        // special constants require additional processing after reading.
        String fill = null;
        for (Map.Entry<String, String> entry
                : array.element.specialConstants.entrySet()) {
            String name = entry.getKey();
            if (name.equals("valid_minimum")
                    || name.equals("valid_maximum")) {
                continue;
            }

            String value = entry.getValue();
            if (fill != null && !fill.equals(value)) {
                throw new IllegalArgumentException(
                        "Multiple special constants require postprocessing");
            }
            fill = value;
        }
        if (fill != null) {
            args.put("fillValue", fill);
        }

        return "vap+bin:" + array.resource.toASCIIString()
            + "?" + URISplit.formatParams(args);
    }

}
