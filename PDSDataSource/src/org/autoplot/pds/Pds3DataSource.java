
package org.autoplot.pds;

import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.autoplot.metatree.MetadataUtil;
import org.das2.datum.Units;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * PDS4 file source.  This is pointed at PDS4 xml files and will return data
 * they describe.
 * @author jbf
 */
public class Pds3DataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("apdss.pds");
    
    public Pds3DataSource(URI uri) {
        super(uri);
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
     * here is the one place for this logic which identifies if the column contains timetags.
     * @param dataType
     * @param unit
     * @return true if this column will be interpreted as time tags.
     */
    public static boolean isTimeTag( String dataType, String unit ) {
        return dataType.equals("DATE") 
            || dataType.equals("TIME") 
            || ( dataType.equals("CHARACTER") && ( unit.equals("UTC") || unit.equals("TIME") ) );
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
            List<String> result= new ArrayList<>();
            
            XPathFactory factory= XPathFactory.newInstance();
            XPath xpath= factory.newXPath();
        
            String name= depend.get(0);
            
            //DocumentUtil.dumpToXML(doc, new File("/home/jbf/peek."+name+".xml"));
            
            NodeList oo= (NodeList) xpath.evaluate( "//LABEL/TABLE/COLUMN[NAME='"+name+"']", doc, XPathConstants.NODESET );
            //TODO: If it's a CONTAINER, then we don't find it.  See https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2016/2016240/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2016240_V01.LBL?DATA
            if ( oo.getLength()==1 ) {  // we found it
                Node time= (Node) xpath.evaluate( "//LABEL/TABLE/COLUMN[1]", doc, XPathConstants.NODE );
                String dataType= (String)xpath.evaluate( "DATA_TYPE/text()", time, XPathConstants.STRING );
                String units= (String)xpath.evaluate( "UNIT/text()", time, XPathConstants.STRING );
                if ( isTimeTag( dataType, units) ) {
                    String timeName= (String)xpath.evaluate( "NAME/text()", time, XPathConstants.STRING );
                    result.add(timeName);
                    result.add(name);
                    depend= result;
                }
            }
            
        }
        
        return depend;
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

    private static Map<String, Object> transferAndCleanMeta( Iterator<Entry<String,Object>> entries , Map<String,Object> result ) throws Exception {
        while ( entries.hasNext() ) {
            Entry<String,Object> entry=  entries.next();
            String key= entry.getKey();
            Object value= entry.getValue();
            if ( value instanceof Map ) {
                Map<String,Object> childResult= new LinkedHashMap<>();
                transferAndCleanMeta( ((Map) value).entrySet().iterator(), childResult );
                value= childResult;
            } else {
                if ( key.equals("DESCRIPTION") && value instanceof String ) {
                    value= DocumentUtil.cleanString( (String)value );
                }
                if ( key.equals("CONTAINER")  && value instanceof JSONArray ) {                    
                    JSONArray ja= (JSONArray)value;
                    DocumentUtil.cleanJSONArray(ja);
                }
            }
            result.put( key, value );
        }
        return result;
    }
    
    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        URISplit split= URISplit.parse( getURI() );
        
        String lbl= split.file;
        File f= getFile( split.resourceUri,mon);

        PDSLabel label = new PDSLabel();
        Document doc;
        if ( !label.parse( f.getPath() ) ) {
            throw new IllegalArgumentException("unable to use file "+lbl);
        }
        doc= label.getDocument();
        //doc.getChildNodes()
        
        Map<String,Object> metadata= DocumentUtil.convertDocumentToMap(doc);
        
        LinkedHashMap<String,Object> result= new LinkedHashMap<>();
        
        String name= URISplit.parseParams(split.params).get("arg_0");
        PDS3DataObject obj= Pds3DataSourceFactory.getDataObjectPds3( split.resourceUri.toURL(), name );
        
        transferAndCleanMeta( obj.getMetadata().entrySet().iterator(), result );
        result.put( "_label", metadata.get("LABEL") );
        
        return result;
                           
    }
    
    
    @Override
    public org.das2.qds.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String name= getParam("arg_0","");
        
        URISplit split= URISplit.parse( getURI() );
        
        URL labelUrl= split.resourceUri.toURL();
        File xmlfile = DataSetURI.getFile( labelUrl,new NullProgressMonitor());
        
        Document doc= Pds3DataSourceFactory.getDocumentWithImports( labelUrl );
            
        PDSLabel label= new PDSLabel();
        // we need to parse this twice because it contains the name of the data file as well.
        if ( !label.parse( xmlfile.getPath() ) ) {
            throw new IllegalArgumentException("unable to use file "+labelUrl);
        }
        
        List<String> names= new ArrayList<>();
        String X= getParam("X","");
        if ( !X.equals("") ) {
            names.add(X);
        }
        String Y= getParam("Y","");
        if ( !Y.equals("") ) {
            names.add(Y);
        }
        
        String Z= getParam("Z","");
        if ( !Z.equals("") ) {
            names.add(Z);
        }

        if ( !name.equals("") ) {
            names.add(name);
        }

        names= seekDependencies(doc, names );
            
        QDataSet result=null;
        QDataSet[] results= new QDataSet[names.size()];
         
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        
        String datafile = (String) xpath.evaluate("/LABEL/POINTER[@object=\"ASCII_TABLE\"]",doc,XPathConstants.STRING);
        if ( datafile.length()==0 ) {
            datafile = (String) xpath.evaluate("/LABEL/POINTER[@object=\"BINARY_TABLE\"]",doc,XPathConstants.STRING);
        }
        if ( datafile.length()==0 ) {
            datafile = (String) xpath.evaluate("/LABEL/POINTER[@object=\"TABLE\"]",doc,XPathConstants.STRING);
        }
        if ( datafile.length()==0 ) {
            datafile= (String)xpath.evaluate("/LABEL/FILE/POINTER[@object='SPREADSHEET']/text()",doc,XPathConstants.STRING);
        }
        FilePointer fp;
        if ( !datafile.equals("") ) {
            fp= new FilePointer(labelUrl, datafile );
        } else {
            // /project/cassini/pds/DATA/RPWS_WAVEFORM_FULL/T20090XX/T2009096/T2009096_2_5KHZ1_WFRFR.LBL
            String l = labelUrl.getFile();
            int i1= l.lastIndexOf("/");
            l= l.substring(i1+1).replace(".LBL",".DAT");
            fp= new FilePointer(labelUrl, l );
        }
        
        for ( int i=0; i<names.size(); i++ ) {
            if ( results[i]!=null ) continue;
            name= names.get(i);            
            
            PDS3DataObject obj= Pds3DataSourceFactory.getDataObjectPds3( labelUrl, name );
                        
            String delegateUri= obj.resolveUri( fp.getUrl() );
            logger.log(Level.FINE, "loading PDS data using delegate URI {0}", delegateUri);
            DataSource delegate= DataSetURI.getDataSource(delegateUri);
            QDataSet ds= delegate.getDataSet( mon.getSubtaskMonitor( "dataset "+ i ) );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, name );
            ds= Ops.putProperty(ds, QDataSet.DESCRIPTION, DocumentUtil.cleanDescriptionString( obj.getDescription() ) );
            HashMap<String,Object> user= new HashMap<>();
            user.put("delegate_uri",delegateUri);
            ds= Ops.putProperty( ds, QDataSet.USER_PROPERTIES, user );
            results[i]= ds;
            
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
                default:
                    break;
            }
        }
        
        if ( result instanceof MutablePropertyDataSet ) {
            ((MutablePropertyDataSet)result).makeImmutable();
        }
        
        return result;
    }
        
}
