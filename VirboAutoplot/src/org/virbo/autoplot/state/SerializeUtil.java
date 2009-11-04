/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.state;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import org.virbo.autoplot.dom.*;
import java.beans.BeanInfo;
import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.DasLogger;
import org.virbo.autoplot.RenderType;
import org.virbo.qstream.SerializeDelegate;
import org.virbo.qstream.SerializeRegistry;
import org.virbo.qstream.XMLSerializeDelegate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class SerializeUtil {

    static {
        SerializeRegistry.register( BindingModel.class, new BindingModelSerializeDelegate() );
        SerializeRegistry.register( Connector.class, new ConnectorSerializeDelegate() );
        SerializeRegistry.register( Datum.class, new DatumSerializeDelegate() );
        //SerializeRegistry.register( Displayable.class, new EnumSerializeDelegate() );
        SerializeRegistry.register( Enum.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( Color.class, new ColorSerializeDelegate() );
        SerializeRegistry.register( DasColorBar.Type.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( DefaultPlotSymbol.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( PsymConnector.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( SpectrogramRenderer.RebinnerEnum.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( RenderType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( PlotSymbol.class, new TypeSafeEnumSerializeDelegate() );
    }
    
    public static Element getDomElement( Document document, DomNode node ) {
        Logger log= DasLogger.getLogger( DasLogger.SYSTEM_LOG );


        try {
            String elementName = node.getClass().getName();

            DomNode defl= node.getClass().newInstance();

            elementName = elementName.replaceAll("\\$", "\\_dollar_");
            Element element = null;
            try {
                element = document.createElement(elementName);
            } catch (Exception e) {
                System.err.println(e);
                throw new RuntimeException(e);
            }
            BeanInfo info = BeansUtil.getBeanInfo(node.getClass());

            PropertyDescriptor[] properties = info.getPropertyDescriptors();

            for ( int i=0; i<properties.length; i++ ) {
                PropertyDescriptor pd= properties[i];
                String propertyName= pd.getName();

                if ( propertyName.equals("controller") ) { //special node should runtime data
                    continue;
                }

                log.fine( "serializing property "+propertyName + " of " +elementName + " id=" + node.getId());

                Method readMethod= pd.getReadMethod();
                Method writeMethod= pd.getWriteMethod();

                if ( writeMethod==null || readMethod==null ) {
                     log.info( "skipping property "+propertyName +" of "+elementName+", failed to find read and write method." );
                     continue;
                }

                Object value= null;
                try {
                    value = readMethod.invoke(node, new Object[0]);
                } catch (IllegalAccessException ex) {
                    log.log(Level.SEVERE, null, ex);
                    continue;
                } catch (IllegalArgumentException ex) {
                    log.log(Level.SEVERE, null, ex);
                    continue;
                } catch (InvocationTargetException ex) {
                    log.log(Level.SEVERE, null, ex);
                    continue;
                }

                if ( value==null ) {
                    log.info( "skipping property "+propertyName+" of "+elementName+", value is null." );
                    continue;
                }

                if ( propertyName.equals("id") && ((String)value).length()>0 ) {
                    element.setAttribute( propertyName, (String)value );
                    continue;
                }

                IndexedPropertyDescriptor ipd=null;
                if ( pd instanceof IndexedPropertyDescriptor ) {
                    ipd= (IndexedPropertyDescriptor)pd;
                }

                if ( value instanceof DomNode ) {
                    // special optimization, only serialize at the first reference to DCC, afterwards just use name
                    Element propertyElement= document.createElement( "property" );
                    propertyElement.setAttribute("name", propertyName );
                    propertyElement.setAttribute("type", "DomNode" );
                    Element child= getDomElement( document, (DomNode)value );
                    propertyElement.appendChild(child);
                    element.appendChild(propertyElement);

                } else if ( ipd!=null && ( DomNode.class.isAssignableFrom( ipd.getIndexedPropertyType() ) ) ) {
                    // serialize each element of the array.  Assumes order doesn't change
                    Element propertyElement= document.createElement( "property" );
                    propertyElement.setAttribute( "name", propertyName );
                    propertyElement.setAttribute( "class", ipd.getIndexedPropertyType().getName() );
                    propertyElement.setAttribute( "length", String.valueOf( Array.getLength(value) ) );
                    for ( int j=0; j<Array.getLength(value); j++ ) {
                        Object value1= Array.get( value, j );
                        Element child= getDomElement( document, (DomNode)value1 );
                        propertyElement.appendChild(child);
                    }
                    element.appendChild(propertyElement);
                } else if ( ipd!=null ) {
                    Element propertyElement= document.createElement( "property" );
                    propertyElement.setAttribute( "name", propertyName );
                    propertyElement.setAttribute( "class", ipd.getIndexedPropertyType().getName() );
                    propertyElement.setAttribute( "length", String.valueOf( Array.getLength(value) ) );
                    for ( int j=0; j<Array.getLength(value); j++ ) {
                        Object value1= Array.get( value, j );
                        Element child= getElementForLeafNode( document, ipd.getIndexedPropertyType(), value1, null );
                        propertyElement.appendChild(child);
                    }
                    element.appendChild(propertyElement);

                } else {
                    Object defltValue= DomUtil.getPropertyValue( defl, pd.getName() );
                    boolean isDef= defltValue==value || (defltValue!=null && defltValue.equals(value) );

                    Element prop= getElementForLeafNode( document, pd.getPropertyType(), value, defltValue );
                    if ( prop==null ) {
                        log.warning( "unable to serialize "+ propertyName );
                        continue;
                    }
                    prop.setAttribute("name", pd.getName() );
                    element.appendChild( prop );
                    if ( !isDef ) {
                        element.setAttribute( "default", "" );
                    }
                }

            }

            return element;
            
        } catch (Exception ex) {
            Logger.getLogger(SerializeUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * return the Element, or null if we can't handle it
     * @param document
     * @param node
     * @return
     */
    public static Element getElementForLeafNode( Document document, Class propClass, Object value, Object defltValue ) {
        boolean isDef= defltValue==value || (defltValue!=null && defltValue.equals(value) );

        SerializeDelegate sd= SerializeRegistry.getDelegate(propClass);
        if ( sd==null ) {
            return null;
        } else {
            Element prop = document.createElement("property");
            if ( sd instanceof XMLSerializeDelegate ) {
                prop.appendChild( ((XMLSerializeDelegate)sd).xmlFormat(document,value) );
                prop.setAttribute("type", sd.typeId(value.getClass()));
            } else {
                prop.setAttribute("type", sd.typeId(value.getClass()));
                prop.setAttribute("value", sd.format(value));
                if ( !isDef ) {
                    if ( defltValue==null ) {
                        prop.setAttribute("default", "null" );                        
                    } else {
                        prop.setAttribute("default", sd.format(defltValue) );
                    }
                }
            }
            return prop;
        }
    }

    public static Object getLeafNode( Document document,Element element ) throws ParseException {
        String type= element.getAttribute("type");
        SerializeDelegate sd= SerializeRegistry.getByName(type);
        if ( sd==null ) {
            sd= SerializeRegistry.getByName(type);
            throw new IllegalArgumentException("unable to find serialize delegate for \""+type+"\"");
        }
        if ( element.hasChildNodes() ) {
            return ((XMLSerializeDelegate)sd).xmlParse((Element) element.getChildNodes().item(0));
        } else {
            return sd.parse(type, element.getAttribute("value") );
        }
    }

    public static DomNode getDomNode( Document document, Element element ) throws ParseException {
        try {
            DomNode node = null;
            node = (DomNode) Class.forName( element.getNodeName() ).newInstance();

            BeanInfo info = BeansUtil.getBeanInfo(node.getClass());

            PropertyDescriptor[] properties = info.getPropertyDescriptors();
            Map<String,PropertyDescriptor> pp= new HashMap();
            for ( int i=0; i<properties.length; i++ ) {
                pp.put( properties[i].getName(), properties[i] );
            }

            if ( element.hasAttribute("id") ) {
                node.setId( element.getAttribute("id") );
            }

            NodeList kids= element.getChildNodes();

            for ( int i=0; i<kids.getLength(); i++ ) {
                Node k= kids.item(i);
                if ( k instanceof Element ) {
                    Element e= (Element)k;

                    System.err.println( e.getNodeName() + "  " + e.getAttribute("name") );
                    if ( e.getAttribute("name").equals("options") ) {
                        System.err.println("here");
                    }

                    PropertyDescriptor pd= pp.get( e.getAttribute("name") );
                    String slen= e.getAttribute("length");
                    if ( slen!=null && slen.length()>0 ) {
                        Class c= Class.forName(e.getAttribute("class"));
                        int n= Integer.parseInt(e.getAttribute("length"));
                        Object arr= Array.newInstance( c,n );
                        if ( DomNode.class.isAssignableFrom(c) ) {
                            NodeList arraykids= e.getChildNodes();
                            int ik=0;
                            for ( int j=0; j<n; j++ ) { //DANGER
                                while ( !( arraykids.item(ik) instanceof Element ) ) ik++;
                                DomNode c1= getDomNode( document, (Element)arraykids.item(ik) );
                                Array.set( arr, j, c1 );
                            }
                            pd.getWriteMethod().invoke( node, arr );
                        } else {
                            NodeList arraykids= e.getChildNodes();
                            int ik=0;
                            for ( int j=0; j<n; j++ ) { //DANGER
                                Object c1=null;
                                while ( !( arraykids.item(ik) instanceof Element ) ) ik++;
                                c1 = getLeafNode(document, (Element) arraykids.item(ik));
                                Array.set( arr, j, c1 );
                            }
                            pd.getWriteMethod().invoke( node, arr );
                        }
                    } else {
                        String stype= e.getAttribute("type");
                        if ( !stype.equals("DomNode") ) {
                            Object child= getLeafNode( document, e );
                            pd.getWriteMethod().invoke( node, child );
                        } else {
                            Node childElement= e.getFirstChild();
                            while ( !( childElement instanceof Element ) ) childElement= childElement.getNextSibling();
                            DomNode child= getDomNode( document, (Element)childElement );
                            pd.getWriteMethod().invoke( node, child );
                        }
                    }
                }
            }

            return node;

        } catch (Exception ex) {
            Logger.getLogger(SerializeUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public static void main( String[] args ) throws FileNotFoundException, SAXException, IOException, ParseException {
        InputStreamReader isr = new InputStreamReader( new FileInputStream( "/home/jbf/tmp/foo2.vapx" ) );

        try {
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(isr);
            Document document = builder.parse(source);

            DomNode n= getDomNode( document, document.getDocumentElement() );

        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
