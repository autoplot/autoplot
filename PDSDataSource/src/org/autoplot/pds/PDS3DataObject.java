package org.autoplot.pds;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Iterator;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.autoplot.datasource.URISplit;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.w3c.dom.Node;

/**
 *
 * @author jbf
 */
public class PDS3DataObject {

    private String name;
    private String uri;
    
    /**
     * number of records or bytes into the file, 1 is the first offset into the file.
     */
    private FilePointer filePointer;
    private int recordBytes; // number of bytes in each record.
    private int rowBytes; // number of bytes taken by each row of the dataset.
    private int rowPrefixBytes; // additional bytes offset for each record (often 0).
    private int rowSuffixBytes; // additional bytes after the bytes of each record (often 0).
    private int rows;
    private String interchangeFormat;
    private String dataType;
    private int startByte;
    private int items;
    private int itemBytes;
    private int bytes;
    private String dims; // dimensions for rank 2 or higher data, like "[64,48]" or "[3]"
    private double validMinimum;
    private double validMaximum;
    private double missingConstant;
    private String unit;
    private String description;
    
    /**
     * spreadsheet field number, 1 is the first column.
     */
    private int fieldNumber;
    
    JSONObject labelJSONObject;
    JSONObject columnJSONObject;
    JSONObject tableJSONObject;
    
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
    public PDS3DataObject( Node label, Node table, Node column) {
        try {
            labelJSONObject= toJSONObject( label );
            JSONObject jtable= toJSONObject(table);
            tableJSONObject= jtable;
            interchangeFormat= jtable.optString("INTERCHANGE_FORMAT", "ASCII");
            rowBytes= jtable.getInt("ROW_BYTES");
            recordBytes= labelJSONObject.optInt("RECORD_BYTES",-1);
            rowPrefixBytes= jtable.optInt("ROW_PREFIX_BYTES",0);
            rowSuffixBytes= jtable.optInt("ROW_SUFFIX_BYTES",0);
            rows= jtable.optInt("ROWS",-1);
            JSONObject j= toJSONObject(column);
            columnJSONObject= j;
            j.toString(4);
            if ( j.has("ITEMS") ) {
                items= j.getInt("ITEMS");
            } else {
                items= -1;
            }

            startByte= j.optInt("START_BYTE",0);
            if ( items>1 ) {
                bytes= j.optInt("BYTES",-1);
                if ( bytes>-1 ) bytes= bytes / items;
            } else {
                bytes= j.optInt("BYTES",-1);
            }
            
            if ( column.getNodeName().equals("CONTAINER") ) {
                // all other properties come from the innermost node.
                XPathFactory factory = XPathFactory.newInstance();    
                XPath xpath = factory.newXPath();
                Node column1= (Node) xpath.evaluate("COLUMN",column,XPathConstants.NODE);
                if ( column1==null ) {
                    column1= (Node) xpath.evaluate("CONTAINER/COLUMN",column,XPathConstants.NODE);
                    String dim0=(String)xpath.evaluate("REPETITIONS/text()",column,XPathConstants.STRING);
                    String dim1=(String)xpath.evaluate("CONTAINER/REPETITIONS/text()",column,XPathConstants.STRING);
                    dims= "["+dim0+","+dim1+"]";
                } else {
                    dims= "["+(String)xpath.evaluate("REPETITIONS/text()",column,XPathConstants.STRING)+"]";
                }
                j= toJSONObject(column1);
            }
                
            dataType= j.optString("DATA_TYPE","");
            fieldNumber= j.optInt("FIELD_NUMBER",-1);

            unit= j.optString("UNIT","");
            validMaximum= j.optDouble("VALID_MAXIMUM",Double.POSITIVE_INFINITY);
            validMinimum= j.optDouble("VALID_MINIMUM",Double.NEGATIVE_INFINITY);
            missingConstant= j.optDouble("MISSING_CONSTANT",Double.NaN);
            if ( Double.isNaN(missingConstant) ) {
                missingConstant= j.optDouble("INVALID_CONSTANT",Double.NaN);
            }
            description= j.optString("DESCRIPTION", "");
        } catch (TransformerException | JSONException ex) {
            throw new IllegalArgumentException("unable to run",ex);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(PDS3DataObject.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * convert the Document into a JSON object which is easier to work with.
     * @param n
     * @return
     * @throws TransformerConfigurationException
     * @throws TransformerException
     * @throws JSONException 
     */
    protected static JSONObject toJSONObject(Node n) throws TransformerConfigurationException, TransformerException, JSONException {
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
        if ( interchangeFormat.equals("ASCII") && fieldNumber>-1 ) {
            return getAsciiUri(resource);
        } else {
            return getBinaryUri(resource) ;
        }
    }
        
    private String getAsciiUri(URL resource) {
        Map<String,String> args= new LinkedHashMap<>();
        if ( filePointer!=null ) {
            switch (filePointer.getOffsetUnits()) {
                case LINES:
                    args.put("skipLines",String.valueOf(filePointer.getOffset()));
                    break;
                case BYTES:
                    int offsetBytes=filePointer.getOffset();
                    offsetBytes= (int)(offsetBytes * 0.98); // fudge factor, because of newlines?
                    args.put("skipBytes",String.valueOf(offsetBytes));
                    break;
                default:
                    throw new IllegalArgumentException("unsupported file pointer");
            }
        }
        args.put("column",String.valueOf(fieldNumber-1));
        return "vap+txt:" + resource.toString() + "?" + URISplit.formatParams(args) ;
    }
    /**
     * return a several line description of the data.
     * @return a several line description of the data.
     */

    private String getBinaryUri(URL resource) throws IllegalArgumentException {
        Map<String,String> args= new LinkedHashMap<>();
        if ( recordBytes>-1 ) {
            args.put( "recLength", String.valueOf(recordBytes) );
        } else {
            args.put( "recLength", String.valueOf(rowPrefixBytes+rowBytes+rowSuffixBytes) );
        }
        if ( dataType.equals("DATE") || dataType.equals("TIME") || ( dataType.equals("CHARACTER") && unit.equals("UTC") ) ) {
            args.put("type", "time"+bytes);
        } else if ( dataType.equals("ASCII_REAL") ) {
            args.put("type", "ascii"+bytes);
        } else if ( dataType.equals("ASCII_INTEGER") ) {
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
        } else if ( dataType.equals("MSB_INTEGER") ) {
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
        } else if ( dataType.equals("UNSIGNED_INTEGER") && bytes==1 ) {
            args.put("type","ubyte");
        } else if ( dataType.equals("LSB_BIT_STRING" ) ) {
            args.put("type","ubyte");
            args.put("dims","["+bytes+"]");
        } else {
            throw new IllegalArgumentException("unsupported type:" +dataType);
        }
        if ( this.filePointer!=null ) {
            switch (this.filePointer.getOffsetUnits()) {
                case LINES:
                    args.put("byteOffset", String.valueOf(this.filePointer.getOffset()*this.rowBytes) );
                    break;
                case BYTES:
                    args.put("byteOffset", String.valueOf(this.filePointer.getOffset()) );
                    break;
                default:
                    throw new IllegalArgumentException("Hmmm, uncoded case.  Contact Jeremy Faden.");
            }
        }
        args.put( "recOffset", String.valueOf(startByte-1+rowPrefixBytes));
        if ( missingConstant!=Double.NaN ) args.put( "fillValue", String.valueOf(missingConstant));
        if ( validMaximum!=Double.POSITIVE_INFINITY ) args.put( "validMax", String.valueOf(validMaximum) );
        if ( validMinimum!=Double.NEGATIVE_INFINITY ) args.put( "validMin", String.valueOf(validMinimum) );
        if ( unit.trim().length()!=0 && !args.get("type").startsWith("time") ) args.put( "units", unit );
        if ( items>-1 ) {
            args.put( "dims", "["+items+"]");
        } else if ( dims!=null ) {
            args.put( "dims", dims);
        }
        return "vap+bin:" + resource.toString() + "?" + URISplit.formatParams(args) ;
    }
    
    /**
     * return a several line description of the data.
     * @return a several line description of the data.
     */
    public String getDescription() {
        return this.description;
    }

    
    private Map<String, Object> getMetadata( JSONObject jo ) {
        Map<String,Object> result= new LinkedHashMap<>();
        Iterator it= jo.keys();
        while( it.hasNext() ) {
            String k= (String)it.next();
            try {
                Object v= jo.get(k);
                if ( v instanceof JSONObject ) {
                    result.put( k, getMetadata((JSONObject)v) );
                } else {
                    result.put( k, v );
                }
            } catch (JSONException ex) {
            }
        }
        return result;
    }
    
    public Map<String, Object> getMetadata() {
        Map<String,Object> result= getMetadata(columnJSONObject);
        result.put("_table", getMetadata(tableJSONObject));
        result.put("_label", getMetadata(labelJSONObject));
        return result;
    }

    public FilePointer getFilePointer() {
        return filePointer;
    }
    
    /**
     * the offset into the file, where 1 is the beginning.
     * @param fileOffset 
     */
    public void setFilePointer( FilePointer p ) {
        this.filePointer = p;
    }
    
    
}
