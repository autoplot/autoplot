package org.das2.catalog.impl;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.catalog.DasResolveException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Helper functions for catalog nodes that happen to rely on XML data
 *
 * @author cwp
 */
public class XmlUtil {
	
	// Get a dom object from a document in string form
	static Document getXmlDoc(String sData)
		throws IOException, SAXException, ParserConfigurationException
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new InputSource(new StringReader(sData)));
		return doc;
	}
	
	static Object property(Document doc, String sFragment, Object oDefault) 
	{
		throw new UnsupportedOperationException("Not supported yet.");
		
	}
	
	static Object property(
		Document doc, String sFragment, Class expect, Object oDefault
	){
		
		throw new UnsupportedOperationException("Not supported yet.");
		
	}

	static Object property(Document doc, String sFragment) 
		throws DasResolveException 
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	static Object property(Document doc, String sFragment, Class expect) 
		throws DasResolveException 
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
}
