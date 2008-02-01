package ftpfs.ftp;

import java.beans.*;

public class FtpListResultBeanInfo extends SimpleBeanInfo
{
    

    // Bean descriptor
    /*lazy BeanDescriptor*/;
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( FtpListResult.class , null );
        
        // Here you can add code for customizing the BeanDescriptor.
        
        return beanDescriptor;         }
    
    
    // Property identifiers 
    private static final int PROPERTY_groupWritable = 0;
    private static final int PROPERTY_group = 1;
    private static final int PROPERTY_ftDir = 2;
    private static final int PROPERTY_ownerExecutable = 3;
    private static final int PROPERTY_type = 4;
    private static final int PROPERTY_globalReadable = 5;
    private static final int PROPERTY_ownerWritable = 6;
    private static final int PROPERTY_owner = 7;
    private static final int PROPERTY_ftCharDev = 8;
    private static final int PROPERTY_groupReadable = 9;
    private static final int PROPERTY_permission = 10;
    private static final int PROPERTY_date = 11;
    private static final int PROPERTY_globalExecutable = 12;
    private static final int PROPERTY_name = 13;
    private static final int PROPERTY_ftFile = 14;
    private static final int PROPERTY_groupExecutable = 15;
    private static final int PROPERTY_ftLink = 16;
    private static final int PROPERTY_globalWritable = 17;
    private static final int PROPERTY_ownerReadable = 18;
    private static final int PROPERTY_ftBlkDev = 19;
    private static final int PROPERTY_size = 20;

    // Property array 
    /*lazy PropertyDescriptor*/;
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[21];
    
        try {
            properties[PROPERTY_groupWritable] = new PropertyDescriptor ( "groupWritable", FtpListResult.class, "isGroupWritable", null );
            properties[PROPERTY_group] = new PropertyDescriptor ( "group", FtpListResult.class, "getGroup", null );
            properties[PROPERTY_ftDir] = new PropertyDescriptor ( "ftDir", FtpListResult.class, "getFtDir", null );
            properties[PROPERTY_ownerExecutable] = new PropertyDescriptor ( "ownerExecutable", FtpListResult.class, "isOwnerExecutable", null );
            properties[PROPERTY_type] = new PropertyDescriptor ( "type", FtpListResult.class, "getType", null );
            properties[PROPERTY_globalReadable] = new PropertyDescriptor ( "globalReadable", FtpListResult.class, "isGlobalReadable", null );
            properties[PROPERTY_ownerWritable] = new PropertyDescriptor ( "ownerWritable", FtpListResult.class, "isOwnerWritable", null );
            properties[PROPERTY_owner] = new PropertyDescriptor ( "owner", FtpListResult.class, "getOwner", null );
            properties[PROPERTY_ftCharDev] = new PropertyDescriptor ( "ftCharDev", FtpListResult.class, "getFtCharDev", null );
            properties[PROPERTY_groupReadable] = new PropertyDescriptor ( "groupReadable", FtpListResult.class, "isGroupReadable", null );
            properties[PROPERTY_permission] = new PropertyDescriptor ( "permission", FtpListResult.class, "getPermission", null );
            properties[PROPERTY_date] = new PropertyDescriptor ( "date", FtpListResult.class, "getDate", null );
            properties[PROPERTY_globalExecutable] = new PropertyDescriptor ( "globalExecutable", FtpListResult.class, "isGlobalExecutable", null );
            properties[PROPERTY_name] = new PropertyDescriptor ( "name", FtpListResult.class, "getName", null );
            properties[PROPERTY_ftFile] = new PropertyDescriptor ( "ftFile", FtpListResult.class, "getFtFile", null );
            properties[PROPERTY_groupExecutable] = new PropertyDescriptor ( "groupExecutable", FtpListResult.class, "isGroupExecutable", null );
            properties[PROPERTY_ftLink] = new PropertyDescriptor ( "ftLink", FtpListResult.class, "getFtLink", null );
            properties[PROPERTY_globalWritable] = new PropertyDescriptor ( "globalWritable", FtpListResult.class, "isGlobalWritable", null );
            properties[PROPERTY_ownerReadable] = new PropertyDescriptor ( "ownerReadable", FtpListResult.class, "isOwnerReadable", null );
            properties[PROPERTY_ftBlkDev] = new PropertyDescriptor ( "ftBlkDev", FtpListResult.class, "getFtBlkDev", null );
            properties[PROPERTY_size] = new PropertyDescriptor ( "size", FtpListResult.class, "getSize", null );
        }
        catch( IntrospectionException e) {}
        
        // Here you can add code for customizing the properties array.
        
        return properties;         }
    
    // EventSet identifiers

    // EventSet array
    /*lazy EventSetDescriptor*/;
    private static EventSetDescriptor[] getEdescriptor(){
        EventSetDescriptor[] eventSets = new EventSetDescriptor[0];
        
        // Here you can add code for customizing the event sets array.
        
        return eventSets;         }
    
    // Method identifiers
    private static final int METHOD_next0 = 0;

    // Method array 
    /*lazy MethodDescriptor*/;
    private static MethodDescriptor[] getMdescriptor(){
        MethodDescriptor[] methods = new MethodDescriptor[1];
    
        try {
            methods[METHOD_next0] = new MethodDescriptor ( FtpListResult.class.getMethod("next", new Class[] {}));
            methods[METHOD_next0].setDisplayName ( "" );
        }
        catch( Exception e) {}
        
        // Here you can add code for customizing the methods array.
        
        return methods;         }
    
    
    private static final int defaultPropertyIndex = -1;
    private static final int defaultEventIndex = -1;
    
    

    
    // Here you can add code for customizing the Superclass BeanInfo.
    

    
    /**
     * Gets the bean's <code>BeanDescriptor</code>s.
     *
     * @return BeanDescriptor describing the editable
     * properties of this bean.  May return null if the
     * information should be obtained by automatic analysis.
     */
    public BeanDescriptor getBeanDescriptor ()
    {
        return getBdescriptor ();
    }
    
    /**
     * Gets the bean's <code>PropertyDescriptor</code>s.
     *
     * @return An array of PropertyDescriptors describing the editable
     * properties supported by this bean.  May return null if the
     * information should be obtained by automatic analysis.
     * <p>
     * If a property is indexed, then its entry in the result array will
     * belong to the IndexedPropertyDescriptor subclass of PropertyDescriptor.
     * A client of getPropertyDescriptors can use "instanceof" to check
     * if a given PropertyDescriptor is an IndexedPropertyDescriptor.
     */
    public PropertyDescriptor[] getPropertyDescriptors ()
    {
        return getPdescriptor ();
    }
    
    /**
     * Gets the bean's <code>EventSetDescriptor</code>s.
     *
     * @return  An array of EventSetDescriptors describing the kinds of
     * events fired by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public EventSetDescriptor[] getEventSetDescriptors ()
    {
        return getEdescriptor ();
    }
    
    /**
     * Gets the bean's <code>MethodDescriptor</code>s.
     *
     * @return  An array of MethodDescriptors describing the methods
     * implemented by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public MethodDescriptor[] getMethodDescriptors ()
    {
        return getMdescriptor ();
    }
    
    /**
     * A bean may have a "default" property that is the property that will
     * mostly commonly be initially chosen for update by human's who are
     * customizing the bean.
     * @return  Index of default property in the PropertyDescriptor array
     * 		returned by getPropertyDescriptors.
     * <P>	Returns -1 if there is no default property.
     */
    public int getDefaultPropertyIndex ()
    {
        return defaultPropertyIndex;
    }
    
    /**
     * A bean may have a "default" event that is the event that will
     * mostly commonly be used by human's when using the bean.
     * @return Index of default event in the EventSetDescriptor array
     *		returned by getEventSetDescriptors.
     * <P>	Returns -1 if there is no default event.
     */
    public int getDefaultEventIndex ()
    {
        return defaultEventIndex;
    }
}

