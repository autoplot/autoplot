
package org.autoplot.pds;

import gov.nasa.pds.label.Label;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.label.object.FieldDescription;
import gov.nasa.pds.label.object.TableObject;
import gov.nasa.pds.label.object.TableRecord;
import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumUtil;
import org.das2.datum.NumberUnits;
import org.das2.datum.TimeUtil;
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
 * PDS4 file source.  This is pointed at PDS4 xml files and will return data
 * they describe.
 * @author jbf
 */
public class Pds3DataSource extends AbstractDataSource {

    public Pds3DataSource(URI uri) {
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
            
            if ( axisNames.get(2)!=null ) {
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
                if ( !n1.equals(name) ) {
                    depend.add(0,n1);
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
    
    
    @Override
    public org.das2.qds.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String name= getParam("arg_0","");
        
        URISplit split= URISplit.parse( getURI() );
            
        File lblfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
        
        PDSLabel label= new PDSLabel();
        if ( !label.parse(lblfile.toPath()) ) {
            throw new IllegalArgumentException("Unable to parse label: "+getURI() );
        }
        
        Document doc= label.getDocument();
                           
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
        
        for ( int i=0; i<names.size(); i++ ) {
            if ( results[i]!=null ) continue;
            name= names.get(i);            
            
            PDS3DataObject obj= PdsDataSourceFactory.getDataObjectPds3( lblfile.toURL(), name );
            
            String datafile;
            URL fileUrl;
            
            if ( label.filePointers().size()==1 ) {
                datafile= (String)label.filePointers().get(0);
                fileUrl= new URL( split.resourceUri.toURL(), datafile );
            } else {
                throw new IllegalArgumentException("multiple file pointers not accepted");
            }
            
            String uri= obj.resolveUri( fileUrl );
            
            DataSource delegate= DataSetURI.getDataSource(uri);
            QDataSet ds= delegate.getDataSet( mon.getSubtaskMonitor( "dataset "+ i ) );
            ds= Ops.putProperty( ds, QDataSet.NAME, name );
            ds= Ops.putProperty( ds, QDataSet.LABEL, name );
            ds= Ops.putProperty( ds, QDataSet.DESCRIPTION, obj.getDescription() );
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
