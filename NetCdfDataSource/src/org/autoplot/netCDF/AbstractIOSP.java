package org.autoplot.netCDF;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.filter.Filter;
import org.jdom.input.SAXBuilder;

import ucar.ma2.DataType;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Structure;
import ucar.nc2.Variable;

import ucar.unidata.io.RandomAccessFile;

public abstract class AbstractIOSP {
    //TODO: look into using "bufferSize" attribute for IOSP

    private Properties _properties;
    private String _iospParam;
    
    /**
     * JDOM Element defining the "netcdf" element that uses this IOSP.
     */
    private Element _ncElement;

    /**
     * Number of time samples found in the data source.
     */
    private int _ntim;
    
    /**
     * Return false. Netcdf will not automatically determine that this IOSP should handle the given file.
     * Use this IOSP only if it is specified in the ncml.
     */
    public boolean isValidFile(RandomAccessFile raf) throws IOException {
        return false;
    }

    /**
     * Called by NetCDF passing in the contents of the ncml "iospParam" attribute.
     * Assumes that message is a String of key,value pairs: "k1=v1 k2=v2"
     * Ignores return value.
     */
    public Object sendIospMessage(Object message) {
        _iospParam = (String) message;
        
        if (message == null) return null;
        
        //parse and save in _properties
        _properties = new Properties();
        String[] props = ((String) message).split("\\s"); //split on white space
        for (String string : props) {
            if (string.contains("=")) {
                int i= string.indexOf("=");
                _properties.put(string.substring(0,i), string.substring(i+1) );
            }
        }
        
        return null;
    }

    protected String getIospParam() {
        return _iospParam;
    }
    
    protected String getProperty(String name) {
        if (_properties == null) return null;
        return _properties.getProperty(name);
    }

    protected Element readNcmlElement(String ncmlLocation) throws IOException {
        String ncml_file = ncmlLocation;
        
        
        //Read ncml file using JDOM and extract the "netcdf" element.
        //May refer to nested "netcdf" element by using "#id"
//        String ncml_file = getProperty("ncml"); //specified in the iospParam attribute in the ncml
//        String[] s = ncml_file.split("#");
//        String link = getProperty("id"); //must set iospParam: id=foo and have that att set in the netcdf element, TODO: just match iospParam att?
//        if (s.length == 2) {
//            ncml_file = s[0];
//            link = s[1];
//        }
        
        // Use the iospParam attribute in the ncml to find appropriate netcdf component if we have an aggregation.
        //TODO: refactor, not right to trigger on existence of iospParam
        String link = getIospParam();
 
        Element ncElement = null;
        try {
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(ncml_file);
            ncElement = doc.getRootElement();
            
            if (link != null) {
                //find the "netcdf" element with the "id" attribute = link
                //ncElement = ncElement.getChildren("netcdf", ns);
                Filter filter = new ElementFilter("netcdf");
                Iterator it = ncElement.getDescendants(filter);
                while (it.hasNext()) {
                    Element e = (Element) it.next();
                    String id = e.getAttributeValue("iospParam");
                    if (link.equals(id)) {
                        ncElement = e;
                        break;
                    }
                    //TODO: error if not found
                }
            }
        } catch (JDOMException e) {
            throw new IOException(e.getMessage());
        } 
        
        _ncElement = ncElement; //internal copy
        return ncElement;
    }
    
    protected Element getNetcdfElement() {
        return _ncElement;
    }

    /**
     * Create the dimensions for the ncfile.
     * Assumes no more than one of any type.
     */
    protected Dimension makeDimension(Element element) {
        Dimension dim = null;
        
        String name = element.getAttributeValue("name");
        String length = element.getAttributeValue("length");
        int n = 0; //default to zero length, 
        if (length != null) n = Integer.parseInt(length);
// Require setting length of time dim in ncaml for now, TODO: override getLength as needed?
//        else if (name.equals("time")) { //length for time dimension unknown
//            //need to have number of time samples to make time dim
//            //Note, this will cause the times to be read
//            //TODO: how to deal with large ts? 
//            //TODO: can we use varlength?
//            n = getTimes().length;
//        }
        if (name.equals("time")) _ntim = n;
        
        dim = new Dimension(name, n, true, true, false);

        return dim;
    }

    protected int getLength() {
        return _ntim;
    }
    
    protected Variable makeVariable(NetcdfFile ncfile, Element element) throws IOException {
        Variable var = null;
        String name = getVariableName(element);
        String shape = element.getAttributeValue("shape"); //may be null for scalar
        String type = element.getAttributeValue("type");
        if ("Structure".equals(type)) {
            //var = makeStructure(ncfile, element);
            Structure struct = new Structure(ncfile, null, null, name);
            struct.setDimensions(shape);
            //Make member variables
            List<Element> vars = element.getChildren("variable", element.getNamespace());
            //_nvar = vars.size();
            for (Element e : vars) {
                Variable v = makeVariable(ncfile, e);
                v.setParentStructure(struct);
                struct.addMemberVariable(v);
            }      
            var = struct;
        } else {
            var = new Variable(ncfile, null, null, name);
            var.setDataType(DataType.getType(type)); //TODO: support other types, use info from ncml
            var.setDimensions(shape);
        }
       //TODO: support pluggable variable types here? e.g. image, could they just fall out from shape?
        // delegate to TSSVar to make ncvar? should be indep of tsds
        
        return var;
    }
    
    /**
     * Return the original name for the time variable.
     * Look for the "time" variable in the NcML.
     * Return the "orgName" if it exists or "time" otherwise.
     */
    protected String getTimeVarName() {
        String vname = "time";
        Element ncElement = getNetcdfElement();
        List<Element> vars = ncElement.getChildren("variable", ncElement.getNamespace());
        for (Element element : vars) {
            if (vname.equals(element.getAttributeValue("name"))) {
                // This will be the original name that the ncfile knows about.
                vname = getVariableName(element);
                break;
            }
        }
        return vname;
    }
    
    /**
     * Extract the name of a variable from the NcML element that defines it.
     * Use the original name if we have it.
     */
    protected String getVariableName(Element varElement) {
        String name = varElement.getAttributeValue("orgName"); 
        if (name == null) name = varElement.getAttributeValue("name");
        return name;
    }
}
