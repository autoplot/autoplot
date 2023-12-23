package org.autoplot.pds;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.autoplot.datasource.URISplit;
import org.das2.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author jbf
 */
public class PDS3DataObject {

    private String name;
    private String uri;
    private int rowBytes;
    private int rows;
    private String interchangeFormat;
    private String dataType;
    private int startByte;
    private int items;
    private int itemBytes;
    private int bytes;
    private double validMinimum;
    private double validMaximum;
    private double missingConstant;
    private String unit;
    private String description;

    /*      
            <START_BYTE>86365</START_BYTE>
            <ITEMS>3</ITEMS>
            <ITEM_BYTES>4</ITEM_BYTES>
            <BYTES>12</BYTES>
            <VALID_MINIMUM>-1600000.0</VALID_MINIMUM>
            <VALID_MAXIMUM>1600000.0</VALID_MAXIMUM>
            <MISSING_CONSTANT>9990000.0</MISSING_CONSTANT>
            <UNIT>nT</UNIT>
            <DESCRIPTION>MAG vector in J
     */
    public PDS3DataObject(Node table, Node column) {
        try {
            JSONObject jtable= toJSONObject(table);
            interchangeFormat= jtable.optString("INTERCHANGE_FORMAT", "BINARY");
            rowBytes= jtable.getInt("ROW_BYTES");
            rows= jtable.optInt("ROWS",-1);
            JSONObject j= toJSONObject(column);
            if ( j.has("ITEMS") ) {
                items= j.getInt("ITEMS");
            } else {
                items= -1;
            }
            startByte= j.getInt("START_BYTE");
            bytes= j.getInt("BYTES");
            dataType= j.getString("DATA_TYPE");
            unit= j.optString("UNIT","");
            validMaximum= j.optDouble("VALID_MAXIMUM",Double.POSITIVE_INFINITY);
            validMinimum= j.optDouble("VALID_MINIMUM",Double.NEGATIVE_INFINITY);
            missingConstant= j.optDouble("MISSING_CONSTANT",Double.NaN);
            description= j.optString("DESCRIPTION", "");
        } catch (TransformerException | JSONException ex) {
            throw new IllegalArgumentException("unable to run");
        }
    }

    private JSONObject toJSONObject(Node n) throws TransformerConfigurationException, TransformerException, JSONException {
        TransformerFactory transfac = TransformerFactory.newInstance();
        transfac.setAttribute("indent-number", 4);
        Transformer trans = transfac.newTransformer();

        // Set up desired output format
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        trans.setOutputProperty(OutputKeys.INDENT, "yes");

        ByteArrayOutputStream out= new ByteArrayOutputStream();
        
        //create string from xml tree
        // StringWriter sw = new StringWriter();
        StreamResult streamResult = new StreamResult(out);
        DOMSource source = new DOMSource(n);

        trans.transform(source, streamResult);
        
        JSONObject result= XML.toJSONObject(out.toString());
        return result.getJSONObject(n.getNodeName());
        
    }

    /**
     * return the Autoplot URI to load the data.
     * @param resource the granule (binary or ASCII) file to load.
     * @return the URI.
     */
    public String resolveUri(URL resource) {
        Map<String,String> args= new LinkedHashMap<>();
        args.put( "recLength", String.valueOf(rowBytes) );
        if ( dataType.equals("DATE") || dataType.equals("TIME") ) {
            args.put("type", "time"+bytes);
        } else if ( dataType.equals("ASCII_REAL") ) {
            args.put("type", "ascii"+bytes);
        } else if ( dataType.equals("PC_REAL") ) {
            args.put("type", "float");
            args.put("byteOrder", "little");
        } else if ( dataType.equals("SUN_REAL") || dataType.equals("IEEE_REAL") ) {
            args.put("type", "float");
            args.put("byteOrder", "big");
        } else if ( dataType.equals("LSB_UNSIGNED_INTEGER" ) ) {
            switch (bytes) {
                case 2: 
                    args.put("type", "ushort");
                    break;
                case 4:
                    args.put("type", "uint");
                    break;
                case 8:
                    args.put("type", "ulong");
                    break;
            }
            args.put("byteOrder", "little");
        } else if ( dataType.equals("LSB_INTEGER" ) ) {
            switch (bytes) {
                case 2: 
                    args.put("type", "short");
                    break;
                case 4:
                    args.put("type", "int");
                    break;
                case 8:
                    args.put("type", "long");
                    break;
            }
            args.put("byteOrder", "little");
        } else if ( dataType.equals("MSB_UNSIGNED_INTEGER" ) ) {
            switch (bytes) {
                case 2: 
                    args.put("type", "ushort");
                    break;
                case 4:
                    args.put("type", "uint");
                    break;
                case 8:
                    args.put("type", "ulong");
                    break;
            }
            args.put("byteOrder", "big");
        } else if ( dataType.equals("MSB_INTEGER" ) ) {
            switch (bytes) {
                case 2: 
                    args.put("type", "short");
                    break;
                case 4:
                    args.put("type", "int");
                    break;
                case 8:
                    args.put("type", "long");
                    break;
            }
            args.put("byteOrder", "big");
        } else {
            throw new IllegalArgumentException("unsupported type:" +dataType);
        }
        args.put( "recOffset", String.valueOf(startByte-1));
        if ( missingConstant!=Double.NaN ) args.put( "fillValue", String.valueOf(missingConstant));
        if ( validMaximum!=Double.POSITIVE_INFINITY ) args.put( "validMax", String.valueOf(validMaximum) );
        if ( validMinimum!=Double.NEGATIVE_INFINITY ) args.put( "validMin", String.valueOf(validMinimum) );
        if ( unit.trim().length()!=0 && !args.get("type").startsWith("time") ) args.put( "units", unit );
        if ( items>-1 ) args.put( "dims", "["+items+"]");
        return "vap+bin:" + resource.toString() + "?" + URISplit.formatParams(args) ;
    }
    
    /**
     * return a several line description of the data.
     * @return a several line description of the data.
     */
    public String getDescription() {
        return this.description;
    }
}
