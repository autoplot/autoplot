/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.xmlfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.spase.SpaseRecordDataSource;
import org.autoplot.spase.VOTableReader;
import org.autoplot.spase.XMLTypeCheck;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.ProgressMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Reads the XML and delegates to SPASE, HELM, or VOTABLE, and soon more.
 * @author jbf
 */
public class XmlfileDataSource extends AbstractDataSource {

    public XmlfileDataSource(URI uri) {
        super(uri);
    }

    Object type;
    
    /**
     * the DOM of the XML file
     */
    Document document;
    
    @Override
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
                SpaseRecordDataSource srds= new SpaseRecordDataSource(uri);
                return srds.getDataSet(mon);

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
    
}
