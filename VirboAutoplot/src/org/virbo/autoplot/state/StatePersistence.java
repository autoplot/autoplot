/*
 * StatePersistence.java
 *
 * Created on August 8, 2007, 10:47 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import java.beans.XMLDecoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerConfigurationException;
import org.das2.util.AboutUtil;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DomNode;
import org.virbo.autoplot.dom.PlotElement;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Plot;

/**
 *
 * @author jbf
 */
public class StatePersistence {

    
    private StatePersistence() {
    }

    /**
     * return the current DOM version, describing the scheme of the DOM.  
     * @return
     */
    public static AbstractVapScheme currentScheme() {
        return new Vap1_07Scheme();
    }

    public static void saveState( File f, Object state ) throws IOException {
        saveState( f, state, "" );
    }

    /**
     * Save the Object (DOM application) to a file.
     * @param f the file target where the state is saved
     * @param state the Object to be saved, in Autoplot's case it's the DOM.
     * @param sscheme empty string or the name of a scheme to target, such as "1.06"
     * @throws IOException
     */
    public static void saveState( File f, Object state, String sscheme ) throws IOException {
        /* XMLEncoder e = new XMLEncoder( new BufferedOutputStream( new FileOutputStream(f) ) );
        
        e.setPersistenceDelegate( DatumRange.class, new DatumRangePersistenceDelegate() );
        e.setPersistenceDelegate( Units.class, new UnitsPersistenceDelegate() );
        e.setPersistenceDelegate( Datum.class, new DatumPersistenceDelegate() );
        e.setPersistenceDelegate( Datum.Double.class, new DatumPersistenceDelegate() );
        e.setPersistenceDelegate( DasColorBar.Type.class, new TypeSafeEnumPersistenceDelegate() );
        e.setPersistenceDelegate( DefaultPlotSymbol.class, new TypeSafeEnumPersistenceDelegate() );
        e.setPersistenceDelegate( BindingModel.class, new BindingPersistenceDelegate() );
        e.setPersistenceDelegate( Connector.class, new ConnectorPersistenceDelegate() );

        //e.setPersistenceDelegate( ApplicationModel.RenderType.class, new TypeSafeEnumPersistenceDelegate() );
        
        e.setExceptionListener( new ExceptionListener() {
            public void exceptionThrown(Exception e) {
                e.printStackTrace();
            }
        } );
        e.writeObject(state);
        e.close();
        */
        saveState( new FileOutputStream(f), state, sscheme );
    }
        
    public static void saveState( OutputStream out, Object state, String sscheme ) throws IOException {
        Document document=null;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(StatePersistence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }

        VapScheme scheme;
        if ( sscheme.equals("") ) {
            scheme= new Vap1_07Scheme();
        } else if ( sscheme.equals("1.06") ) {
            scheme= new Vap1_06Scheme();
        } else {
            throw new IllegalArgumentException("output scheme not supported: "+sscheme);
        }
        Element element = SerializeUtil.getDomElement( document, (DomNode)state, scheme );

        Element vap= document.createElement("vap");
        vap.appendChild(element);
        vap.setAttribute( "domVersion", scheme.getId() );
        vap.setAttribute( "appVersionTag", AboutUtil.getReleaseTag() );

        document.appendChild(vap);

        if ( sscheme.length()>0 ) {
            try {
                doConvert( document, scheme.getId(), sscheme );
            } catch ( TransformerException ex ) {
                ex.printStackTrace();
                // throw new IOException("Unable to export to version "+sscheme,ex); //TODO: JAVA1.6 will set initial cause
                IOException result= new IOException("Unable to export to version "+sscheme );
                result.initCause(ex);
                throw result;
            }
        }

        writeDocument( out, document);
    }


    /**
     * write the document out to the file, hiding the details of the serializer.
     * @param out outputStream
     * @param document XML document object
     * @return
     * @throws LSException
     * @throws DOMException
     * @throws FileNotFoundException
     */
    public static void writeDocument( OutputStream out, Document document) throws IOException {
        DOMImplementation impl = document.getImplementation();
        DOMImplementationLS ls = (DOMImplementationLS) impl.getFeature("LS", "3.0");
        LSSerializer serializer = ls.createLSSerializer();
        LSOutput output = ls.createLSOutput();
        output.setEncoding("UTF-8");
        output.setByteStream(out);
        try {
            if (serializer.getDomConfig().canSetParameter("format-pretty-print", Boolean.TRUE)) {
                serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
            }
        } catch (Error e2) {
            // Ed's nice trick for finding the implementation
            //String name = serializer.getClass().getSimpleName();
            //java.net.URL u = serializer.getClass().getResource(name + ".class");
            //System.err.println(u);
            e2.printStackTrace();
        }
        serializer.write(document, output);

        out.close();

    }
    /**
     * write the document out to the file, hiding the details of the serializer.
     * @param f
     * @param document
     * @return
     * @throws LSException
     * @throws DOMException
     * @throws FileNotFoundException
     */
    public static void writeDocument(File f, Document document) throws FileNotFoundException, IOException {
        OutputStream out = new FileOutputStream( f );
        writeDocument( out, document );
    }


    /**
     * return the first child, if any, with the given tag name.
     * @param parent
     * @param tagName
     * @return return the child or null if no such child exists.
     */
    public static Element getChildElement( Element parent, String tagName ) {
        NodeList nl= parent.getChildNodes();
        for ( int i=0; i<nl.getLength(); i++ ) {
            Node item = nl.item(i);
            if ( item instanceof Element && ((Element)item).getTagName().equals(tagName) ) {
                return (Element)item;
            }
        }
        return null;
    }

    /**
     * restore the XML file, possibly promoting it.
     * @param f
     * @return
     * @throws IOException
     */
    public static Object restoreState( File f )  throws IOException {
        InputStream in =new FileInputStream( f );
        Object result= restoreState( in );
        in.close();
        return result;
    }

    private static Document doConvert( Document document, String domVersion, String currentVersion ) throws TransformerConfigurationException, TransformerException {
        double srcVersion= Double.parseDouble(domVersion);
        double dstVersion= Double.parseDouble(currentVersion);

        if (srcVersion > dstVersion) {
            // downgrade future versions.  This is experimental, but slightly
            // better than not allowing use.  This is intended to smooth
            // transitions to new autoplot versions.  Future vap files
            // that use future features will not load properly.
            for ( double s=srcVersion; s>dstVersion; s=s-0.01 ) {
                Source src = new DOMSource( document );

                DOMResult res = new DOMResult( );

                String fname= String.format( Locale.US, "Vap_%4.2f_to_%4.2f",
                        s, s-0.01 );
                fname= fname.replaceAll("\\.","_") + ".xsl";

                InputStream xsl = StatePersistence.class.getResourceAsStream(fname);
                if ( xsl==null ) {
                    throw new RuntimeException("Unable to find "+fname+".");
                }
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer tr = factory.newTransformer( new StreamSource(xsl) );

                tr.transform(src, res);
                document= ((Document)res.getNode());
            }
        }

        return document;


    }

    /**
     * restore the XML on the inputStream, possibly promoting it.
     * @param in, an input stream that starts with the xml.  This will be left open.
     * @return
     * @throws IOException
     */
    public static Object restoreState( InputStream in )  throws IOException {
        PushbackInputStream pbin= new PushbackInputStream(in,10);

        if ( pbin.available()<5 ) {
            System.err.println("less than 5 chars available, can't check");
        } else {
            byte[] five= new byte[5];
            pbin.read(five);
            String magic= new String( five );
            if ( !( magic.equals("<?xml") || magic.equals("<vap ") || magic.equals("<java") ) ) {
                throw new IllegalArgumentException("expected to find document that started with \"<?xml\" , this starts with \""+magic+"\"." );
            }
            pbin.unread(five);
        }
        
        InputStreamReader isr= new InputStreamReader( pbin );
        Application state;
        String domVersion;

        try {
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(isr);
            Document document = builder.parse(source);

            if ( document.getDocumentElement().getNodeName().equals("java") ) { // legacy support

                domVersion= "0.99";
                importLegacyVap(document.getDocumentElement());

                ByteArrayOutputStream baos= new ByteArrayOutputStream(10000);
                
                //throw new RuntimeException("It is no longer possible to convert old " +
                //        "files at runtime.  Contact Autoplot group at Google groups " +
                //        "for conversion help." );
                System.err.println("importing legacy vap file v0.99. ");
                System.err.println("These must be rewritten to new vap format, support will be dropped.");

                DOMImplementation impl = document.getImplementation();
                DOMImplementationLS ls = (DOMImplementationLS) impl.getFeature("LS", "3.0");
                LSSerializer serializer = ls.createLSSerializer();
                LSOutput output = ls.createLSOutput();
                output.setEncoding("UTF-8");
                output.setByteStream(baos);
                serializer.write(document, output);
                baos.close();
                
                XMLDecoder decode= new XMLDecoder( new ByteArrayInputStream( baos.toByteArray() ) );

                // add a direct reference to these guys for compile-all script.
                new DatumRangePersistenceDelegate();
                new UnitsPersistenceDelegate();
                new DatumPersistenceDelegate() ;
                new TypeSafeEnumPersistenceDelegate() ;
                new BindingPersistenceDelegate() ;
                new ConnectorPersistenceDelegate();

                state= (Application) decode.readObject();

                Application app= (Application)state;
                for ( PlotElement p: app.getPlotElements() ) {
                    if ( p.getRenderType()==null ) {
                        p.setRenderTypeAutomatically( RenderType.series );
                    }
                }

                for ( Plot p: app.getPlots() ) {
                    p.getZaxis().setVisible(false);
                    List<PlotElement> pes= DomUtil.getPlotElementsFor(app, p);
                    for ( PlotElement pe: pes ) {
                        RenderType rt= pe.getRenderType();
                        if ( rt==RenderType.spectrogram || rt==RenderType.nnSpectrogram || rt==RenderType.colorScatter ) {
                            p.getZaxis().setVisible(true);
                        }
                    }
                }
            } else {
                Element root= document.getDocumentElement();
                if ( root.getNodeName().equals("exceptionReport") ) {
                    NodeList maybeVap= root.getElementsByTagName("vap");
                    if ( maybeVap.getLength()==1 ) {
                        root= (Element)maybeVap.item(0);
                    } else {
                        throw new IllegalArgumentException("exception report doesn't have vap node");
                    }
                }
                
                domVersion= root.getAttribute("domVersion");
                String currentVersion= currentScheme().getId();
                if ( domVersion.startsWith("v") ) {
                    domVersion= domVersion.substring(1).replace('_','.');
                }

                if ( ! domVersion.equals(currentVersion) ) {

                    double srcVersion= Double.parseDouble(domVersion);
                    double dstVersion= Double.parseDouble(currentVersion);

                    if (srcVersion > dstVersion) {
                        // downgrade future versions.  This is experimental, but slightly
                        // better than not allowing use.  This is intended to smooth
                        // transitions to new autoplot versions.  Future vap files
                        // that use future features will not load properly.
                        for ( double s=srcVersion; s>dstVersion; s=s-0.01 ) {
                            Source src = new DOMSource( root );

                            DOMResult res = new DOMResult( );

                            String fname= String.format( Locale.US, "Vap_%4.2f_to_%4.2f",
                                    s, s-0.01 );
                            fname= fname.replaceAll("\\.","_") + ".xsl";

                            InputStream xsl = StatePersistence.class.getResourceAsStream(fname);
                            if ( xsl==null ) {
                                throw new RuntimeException("Unable to find "+fname+".");
                            }
                            TransformerFactory factory = TransformerFactory.newInstance();
                            Transformer tr = factory.newTransformer(new StreamSource(xsl));

                            tr.transform(src, res);
                            root= (Element)res.getNode().getFirstChild();
                        }

                    } else {

                        // upgrade old vap file versions.
                        for ( double s=srcVersion; s<dstVersion; s=s+0.01 ) {
                            Source src = new DOMSource( root );

                            DOMResult res = new DOMResult( );

                            String fname= String.format( Locale.US, "Vap_%4.2f_to_%4.2f",
                                    s, s+0.01 );
                            fname= fname.replaceAll("\\.","_") + ".xsl";

                            InputStream xsl = StatePersistence.class.getResourceAsStream(fname);
                            if ( xsl==null ) {
                                throw new RuntimeException("Unable to find "+fname+".");
                            }
                            TransformerFactory factory = TransformerFactory.newInstance();
                            Transformer tr = factory.newTransformer(new StreamSource(xsl));

                            tr.transform(src, res);
                            root= (Element)res.getNode().getFirstChild();

                        }
                    }
                }

                Element dom= getChildElement( root, "Application" );
                state= (Application) SerializeUtil.getDomNode( dom, new Vap1_07Scheme() ); //TODO: I don't think this is used any more.

            }

            if ( domVersion.compareTo("1.00")<0 ) { // make all ranging automatic
                Plot[] pp= state.getPlots();        // file:///home/jbf/ct/hudson/vap/Cluster1_HEEA_slices.vap motivated
                for ( int i=0; i<pp.length; i++ ) {
                    pp[i].getXaxis().setAutoRange(true);
                    pp[i].getYaxis().setAutoRange(true);
                    pp[i].getZaxis().setAutoRange(true);
                }
            } else if ( domVersion.compareTo("1.06")<=0 ) {
                // file:///home/jbf/ct/hudson/vap/ninePanels.vap shows that old vap files often didn't have the autorange cleared
                // when changes were made.  Now the code properly handles these, so autorange needs to be turned off when loading vaps.
                // This showed that 1.06 files would have this problem too: file:/home/jbf/ct/hudson/vap/cassini_kp.vap
                Plot[] pp= state.getPlots();        
                for ( int i=0; i<pp.length; i++ ) {
                    pp[i].getXaxis().setAutoRange(false);
                    pp[i].getYaxis().setAutoRange(false);
                    pp[i].getZaxis().setAutoRange(false);
                }
            }

            return state;

        } catch (ParseException ex) {
            Logger.getLogger(StatePersistence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } catch (TransformerException ex) {
            Logger.getLogger(StatePersistence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * hack the dom to make it class-compatible.
     * @param doc
     */
    private static void importLegacyVap( Element element ) {
        NodeList nl= element.getChildNodes();
        for ( int i=0; i<nl.getLength(); i++ ) {
            Node n= nl.item(i);
            if ( n.getNodeName().equals("void") ) {
                NamedNodeMap nn= n.getAttributes();
                Node prop= nn.getNamedItem("property");
                if ( prop!=null ) {
                    if ( prop.getNodeValue().equals("autorange") ) prop.setNodeValue("autoRange");
                    if ( prop.getNodeValue().equals("autolabel") ) prop.setNodeValue("autoLabel");
                    if ( prop.getNodeValue().equals("panels") ) prop.setNodeValue("plotElements");
                    if ( prop.getNodeValue().equals("parentPanel") ) prop.setNodeValue("parent");
                }
            } else if ( n.getNodeName().equals("object") ) {
                NamedNodeMap nn= n.getAttributes();

                Node prop= nn.getNamedItem("class");
                if ( prop==null ) {
                    continue;
                }
                if ( prop.getNodeValue().equals("org.virbo.autoplot.dom.Panel") ) {
                    prop.setNodeValue("org.virbo.autoplot.dom.PlotElement");
                } else if ( prop.getNodeValue().equals("org.virbo.autoplot.dom.PanelStyle") ) {
                    prop.setNodeValue("org.virbo.autoplot.dom.PlotElementStyle");
                }
            } else if ( n.getNodeName().equals("array") ) {
                NamedNodeMap nn= n.getAttributes();

                Node prop= nn.getNamedItem("class");
                if ( prop==null ) {
                    continue;
                }
                if ( prop.getNodeValue().equals("org.virbo.autoplot.dom.Panel") ) {
                    prop.setNodeValue("org.virbo.autoplot.dom.PlotElement");
                } else if ( prop.getNodeValue().equals("org.virbo.autoplot.dom.PanelStyle") ) {
                    prop.setNodeValue("org.virbo.autoplot.dom.PlotElementStyle");
                }
            }

            if ( n.hasChildNodes() && n instanceof Element ) {
                importLegacyVap( (Element) n );
            }
        }
    }
}
