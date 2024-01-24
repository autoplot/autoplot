/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.autoplot.pds;

import gov.nasa.pds.ppi.label.PDSException;
import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.autoplot.datasource.DataSetURI;
import org.das2.util.monitor.NullProgressMonitor;
import org.w3c.dom.Document;

/**
 * 
 * XPATH: /LABEL/TABLE/COLUMN/NAME/text()
 * @author jbf
 */
public class ShowLabelAsXml {
    public static void main( String[] args ) throws MalformedURLException, IOException, PDSException, TransformerException {
        //String lbl="https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2016/2016240/ELECTRONS/JAD_L50_LRS_ELC_ANY_DEF_2016240_V01.LBL";
        String lbl="https://pds-ppi.igpp.ucla.edu/data/GO-J-PWS-5-DDR-PLASMA-DENSITY-FULL-V1.0/DATA/00_JUPITER/FPE_1996_05_25_V01.LBL";
        File f= DataSetURI.getFile( new URL(lbl), new NullProgressMonitor() );
        URL fileUrl;
            
        PDSLabel label = new PDSLabel();
        Document doc;
        if ( !label.parse( f.toPath() ) ) {
            throw new IllegalArgumentException("unable to use file "+lbl);
        }
        doc= label.getDocument();
        
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        String output = writer.getBuffer().toString().replaceAll("\n|\r", "");

        System.err.println( "output to " + new File(".").getAbsolutePath() );
        try ( FileWriter fw= new FileWriter( f.getName() + ".xml") ) {
            fw.append(output);
        }
        
    }
    
}
