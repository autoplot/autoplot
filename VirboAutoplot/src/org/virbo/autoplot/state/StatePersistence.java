/*
 * StatePersistence.java
 *
 * Created on August 8, 2007, 10:47 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.Connector;
import org.virbo.autoplot.dom.DomNode;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class StatePersistence {

    
    private StatePersistence() {
    }

    public static void saveState( File f, Object state ) throws IOException {
        XMLEncoder e = new XMLEncoder( new BufferedOutputStream( new FileOutputStream(f) ) );
        
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

        Document document=null;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(StatePersistence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        Element element = SerializeUtil.getDomElement(document, (DomNode)state);
        document.appendChild(element);
        OutputStream out = new FileOutputStream( new File( f.toString()+"x") );

        DOMImplementation impl = document.getImplementation();
        DOMImplementationLS ls = (DOMImplementationLS)impl.getFeature("LS", "3.0");
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
            String name = serializer.getClass().getSimpleName();
            java.net.URL u = serializer.getClass().getResource(name+".class");
            //System.err.println(u);
            e2.printStackTrace();
        }


        serializer.write(document, output);

        out.close();
    }
    
    public static Object restoreState( File f )  throws IOException {

        InputStreamReader isr = new InputStreamReader( new FileInputStream( f ) );

        try {
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(isr);
            Document document = builder.parse(source);

            if ( document.getDocumentElement().getNodeName().equals("java") ) { // legacy
                isr.close();
                XMLDecoder decode= new XMLDecoder( new FileInputStream(f) );
                Object state= decode.readObject();
                return state;

            } else {
                DomNode n= SerializeUtil.getDomNode( document, document.getDocumentElement() );
                return n;
            }

        } catch (SAXException ex) {
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        } catch (ParseException ex ) {
            throw new RuntimeException(ex);
        }

    }
}
