
package org.autoplot.hapi;

/**
 * static class containing constants.  See
 * https://github.com/hapi-server/data-specification
 * 
 * @author jbf
 */
public final class HapiSpec {

    private HapiSpec() {}
    
    /**
     * the text to append to a HAPI server to request the catalog.
     * @see https://github.com/hapi-server/data-specification#catalog
     */
    protected static final String CATALOG_URL= "catalog";

    /**
     * the text to append to a HAPI server to request parameter info.
     * @see https://github.com/hapi-server/data-specification#catalog
     */
    protected static final String INFO_URL = "info";

    /**
     * the text to append to a HAPI server to request server capabilities.
     * @see https://github.com/hapi-server/data-specification#capabilities
     */
    protected static final String CAPABILITIES_URL = "capabilities";

    /**
     * the text to append to a HAPI server to request parameter data.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String DATA_URL = "data";
    
    /**
     * ID parameter of a URL, used in the info and data requests.
     * @see https://github.com/hapi-server/data-specification#info
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_ID = "id";

    /**
     * start time parameter of a URL, used in the data requests.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_START = "start";
    
    /**
     * stop time parameter of a URL, used in the data requests.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_STOP = "stop";

    /**
     * time.min parameter of a URL, used in the data requests.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_TIMEMIN = "time.min";

    /**
     * time.max parameter of a URL, used in the data requests.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_TIMEMAX = "time.max";
    
    /**
     * parameters parameter of a URL, used in the data requests.
     * @see https://github.com/hapi-server/data-specification#data
     */
    protected static final String URL_PARAM_PARAMETERS = "parameters";

    /**
     * @see https://github.com/hapi-server/data-specification#catalog
     */
    protected static final String CATALOG= "catalog";
        
    /**
     * @deprecated, see HAPI_OUTPUT_FORMATS
     * @see https://github.com/hapi-server/data-specification#capabilities
     */
    protected static final String FORMATS = "formats";
    
    /**
     * @see https://github.com/hapi-server/data-specification#capabilities
     */
    protected static final String OUTPUT_FORMATS= "outputFormats";
    
    /**
     * @see https://github.com/hapi-server/data-specification#capabilities
     */
    protected static final String BINARY = "binary";
    
    /**
     * some HAPI servers have optional title for IDs.  
     * @see https://github.com/hapi-server/data-specification#catalog
     */
    protected static final String TITLE = "title";    

    /**    
     * HAPI server parameters tag of JSON response.
     * @see https://github.com/hapi-server/data-specification#info
     */
    protected static final String PARAMETERS = "parameters";

    
}
