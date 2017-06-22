/*
 * SpaseRecordDataSource.java
 *
 * Created on October 8, 2007, 6:15 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.spase;

import java.io.File;
import java.io.FileInputStream;
import org.autoplot.metatree.SpaseMetadataModel;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSource extends AbstractDataSource {

    private static final Logger logger= Logger.getLogger("apdss.spase");
    
    /**
     * the DataSource URL found within Bob's SPASE record.
     * at /Spase/NumericalData/AccessInformation/AccessURL/URL
     */
    URL url;
    
    /**
     * the DOM of the SPASE record
     */
    Document document;
    
    /**
     * DataSource delegate that will do the loading
     */
    DataSource delegate;

    Object type;

    /** Creates a new instance of SpaseRecordDataSource */
    public SpaseRecordDataSource( URI uri ) throws IllegalArgumentException, IOException, SAXException, Exception {
        super(uri);
        try {
            this.url= new URL( uri.getSchemeSpecificPart() );
        } catch (MalformedURLException ex) {
            logger.warning("Failed to convert URI to URL");
            throw new RuntimeException(ex);
        }
        
    }
    
    private String findSurl() {
        
        String[] lookFor= new String[] { "Spase", "NumericalData", "AccessInformation", "AccessURL", "URL" };
        
        NodeList list= document.getElementsByTagName(lookFor[0]);
        Element pos= (Element)list.item(0);

        if ( pos==null ) {
            throw new IllegalArgumentException("Unable to find node Space/NumericalData/AccessInformation/AccessURL/URL in "+url );
        }
        
        for ( int i=1; i<lookFor.length; i++ ) {
            list= pos.getElementsByTagName(lookFor[i]);
            pos= (Element)list.item(0);
        }
        
        String result=null;
        
        list= pos.getChildNodes();
        for (int k = 0; k < list.getLength(); k++) {
            Node child = list.item(k);
            // really should to do this recursively
            if (child.getNodeType() == Node.TEXT_NODE) {
                result= child.getNodeValue();
            }
        }
        
        return result;
        
    }
    
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();
        mon.setProgressMessage( "parse xml file");
        
        File f= DataSetURI.getFile( uri, mon.getSubtaskMonitor("get file") );
        type= new XMLTypeCheck().calculateType(f);
        
        if ( type!=XMLTypeCheck.TYPE_VOTABLE ) {  // this type uses a SAX parser.
            readXML(mon.getSubtaskMonitor("readXML")); // creates the document object...
        } else {
            document= null;
        }

        String surl=null;

        try {
            
            XPathFactory factory= XPathFactory.newInstance();
            XPath xpath= factory.newXPath();

            if ( type==XMLTypeCheck.TYPE_SPASE ) {
                surl= (String) xpath.evaluate( "//Spase/NumericalData/AccessInformation/AccessURL/URL/text()", document );
                //surl= findSurl();
                
                if ( surl.trim().length()==0 ) {
                    surl= (String) xpath.evaluate( "//Spase/Granule/Source/URL/text()", document );
                    if ( surl.trim().length()==0 ) {
                        throw new IllegalArgumentException("Expected to find URI in //Spase/Granule/Source/URL/text()");
                    } else {
                        throw new IllegalArgumentException("Granule is found at: "+surl+", unable to read" );
                    }
                }
                
                delegate= DataSetURI.getDataSource( DataSetURI.getURIValid( surl ) );

                mon.setProgressMessage("reading "+delegate.getURI() );
                
                QDataSet result= delegate.getDataSet(mon.getSubtaskMonitor("get delegate"));

                return result;

            } else if ( type==XMLTypeCheck.TYPE_HELM ) {

                NodeList nl= (NodeList) xpath.evaluate( "//Eventlist/Event", document, XPathConstants.NODESET );

                DataSetBuilder timespans= new DataSetBuilder(2,100,2);
                DataSetBuilder description= new DataSetBuilder(1,100);

                EnumerationUnits eu= EnumerationUnits.create("eventDesc");

                description.putProperty(QDataSet.UNITS,eu );
                timespans.putProperty(QDataSet.UNITS, Units.us2000 );
                timespans.putProperty(QDataSet.BINS_1,"min,max");

                mon.setTaskSize(nl.getLength());
                mon.setProgressMessage( "reading events" );

                for ( int j=0; j<nl.getLength(); j++ ) {
                    Node item= nl.item(j);
                    mon.setTaskProgress(j);
                    if ( mon.isCancelled() ) throw new CancelledOperationException("User pressed cancel");

                    String desc= (String) xpath.evaluate( "Description/text()", item, XPathConstants.STRING );

                    String startDate= (String) xpath.evaluate( "TimeSpan/StartDate/text()", item, XPathConstants.STRING );
                    String stopDate= (String) xpath.evaluate( "TimeSpan/StopDate/text()", item, XPathConstants.STRING );
                    if ( startDate.compareTo(stopDate)>0 ) {
                        //throw new IllegalArgumentException("startDate is after stopDate");
                        Exception e= new IllegalArgumentException("StartDate is after StopDate: "+startDate );
                        e.printStackTrace();
                        timespans.putValue(j, 0, Units.us2000.parse(startDate).doubleValue(Units.us2000) );
                        timespans.putValue(j, 1, Units.us2000.parse(startDate).doubleValue(Units.us2000) );
                        description.putValue(j,eu.createDatum(e.getMessage()).doubleValue(eu) );
                        continue;
                    }
                    description.putValue(j,eu.createDatum( desc ).doubleValue(eu) );
                    timespans.putValue(j, 0, Units.us2000.parse(startDate).doubleValue(Units.us2000) );
                    timespans.putValue(j, 1, Units.us2000.parse(stopDate).doubleValue(Units.us2000) );
                }

                DDataSet dd= description.getDataSet();
                dd.putProperty( QDataSet.DEPEND_0, timespans.getDataSet() );

                String title= (String)xpath.evaluate("//Eventlist/ResourceHeader/Description", document, XPathConstants.STRING );
                dd.putProperty( QDataSet.TITLE,title );
                
                return dd;

            } else if ( type==XMLTypeCheck.TYPE_VOTABLE ) {

                VOTableReader r= new VOTableReader();
                
                QDataSet result= r.readTable( f.toString(), mon.getSubtaskMonitor("read votable") );
                
                QDataSet bds= (QDataSet) result.property(QDataSet.BUNDLE_1);
                
                QDataSet ttag;
                QDataSet data= null;
                
                // allow for the second column to be the timetags.
                Units u0= (Units) bds.property(QDataSet.UNITS,0);
                Units u1= null;
                if ( bds.length()>0 ) {
                    u1= (Units) bds.property(QDataSet.UNITS,1);
                }
                
                int ii= 0;
                if ( ( u0==null || !UnitsUtil.isTimeLocation( u0 ) ) && ( u1!=null && UnitsUtil.isTimeLocation(u1) ) ) {
                    if ( bds.length()>1 ) {
                        ii=1;
                        u0= u1;
                        if ( bds.length()>2 ) {
                            u1= (Units)bds.property(QDataSet.UNITS,2 );
                        }
                    }
                }
                
                if ( bds.length()>1 ) {
                    if ( u0!=null && u1!=null && UnitsUtil.isTimeLocation(u0) && UnitsUtil.isTimeLocation(u1) ) {
                        MutablePropertyDataSet wttag= DataSetOps.applyIndex( result, 1, Ops.linspace(ii,ii+1,2), false );
                        wttag.putProperty(QDataSet.BINS_1,QDataSet.VALUE_BINS_MIN_MAX);
                        wttag.putProperty(QDataSet.BUNDLE_1,null); // TODO: Really, I have to delete this???
                        wttag.putProperty(QDataSet.UNITS,u0);
                        
                        ttag= wttag;
                        if ( bds.length()==(ii+2) ) {
                            EnumerationUnits eu= EnumerationUnits.create("eventDesc");
                            MutablePropertyDataSet mdata= Ops.replicate( eu.createDatum("").doubleValue(eu), ttag.length() );
                            mdata.putProperty(QDataSet.UNITS,eu);
                            data= mdata;
                        }
                    } else {
                        ttag= DataSetOps.unbundle( result,0 );
                    }
                } else {
                    ttag= DataSetOps.unbundle( result,0 );
                }
                
                if ( data==null ) {
                    URISplit split= URISplit.parse(this.uri);
                    Map<String,String> args= URISplit.parseParams(split.params);
                    String arg0= args.get(URISplit.PARAM_ARG_0);
                    if ( arg0==null ) {
                        data= DataSetOps.unbundle( result, result.length(0)-1 );
                    } else {
                        data= DataSetOps.unbundle( result, args.get(URISplit.PARAM_ARG_0) ); // typical route
                    }
                }
                
                return Ops.link( ttag, data );

            } else {
                throw new IllegalArgumentException( "Unsupported XML type, root node should be Spase or Eventlist"); // see else above
            }

        } catch ( XPathExpressionException ex) {
            throw new IllegalArgumentException("unable to get /Spase/NumericalData/AccessInformation/AccessURL/URL(): "+ex.getMessage() );
        } catch ( MalformedURLException ex) {
            throw new IllegalArgumentException("Spase record AccessURL is malformed: "+surl );
        } catch ( Exception ex ) {
            throw ex;
        } finally {
            mon.finished();
        }
    }
    
    /**
     *
     * @param monitor the value of monitor
     * @throws IOException
     * @throws SAXException
     */
    private void readXML( ProgressMonitor mon ) throws IOException, SAXException {
        DocumentBuilder builder= null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
        File f= DataSetURI.getFile( uri, mon );
        InputStream in= new FileInputStream(f);
        
        try {
            InputSource source = new InputSource( in );
            document = builder.parse(source);

            //Node n= document.getDocumentElement();

            //String localName= n.getNodeName();
            //int i= localName.indexOf(":");
            //if ( i>-1  ) {
            //    localName= localName.substring(i+1);
            //}

        } finally {
            in.close();
        } 
        
    }
    
    @Override
    public Map<String,Object> getMetadata( ProgressMonitor mon ) throws Exception {

        if ( type.equals(XMLTypeCheck.TYPE_VOTABLE) ) {
            return new HashMap<String, Object>();
        } else if ( document==null ) {
            readXML(mon);
        }

        // If we're using a DOM Level 2 implementation, then our Document
        // object ought to implement DocumentTraversal
        DocumentTraversal traversal = (DocumentTraversal)document;
        
        NodeFilter filter = new NodeFilter() {
            public short acceptNode(Node n) {
                if (n.getNodeType() == Node.TEXT_NODE) {
                    // Use trim() to strip off leading and trailing space.
                    // If nothing is left, then reject the node
                    if (((Text)n).getData().trim().length() == 0)
                        return NodeFilter.FILTER_REJECT;
                }
                return NodeFilter.FILTER_ACCEPT;
            }
        };
        
        // This set of flags says to "show" all node types except comments
        int whatToShow = NodeFilter.SHOW_ALL & ~NodeFilter.SHOW_COMMENT;
        
        
        Node root= document.getFirstChild();
        
        // Create a TreeWalker using the filter and the flags
        TreeWalker walker = traversal.createTreeWalker( root, whatToShow,
                filter, false);
        
        //TreeModel tree= new DOMTreeWalkerTreeModel(walker);
        DOMWalker walk= new DOMWalker(walker);
        return walk.getAttributes( walk.getRoot() );
        
    }
    
    @Override
    public Map<String, Object> getProperties() {
        try {
            return new SpaseMetadataModel().properties( getMetadata( new NullProgressMonitor() ) );
        } catch (Exception ex) {
            return Collections.singletonMap( "Exception", (Object)ex );
        } 
    }

    @Override
    public MetadataModel getMetadataModel() {
        if ( type==XMLTypeCheck.TYPE_SPASE ) {
            return new SpaseMetadataModel();
        } else {
            return null;
        }
    }
    
    
}
