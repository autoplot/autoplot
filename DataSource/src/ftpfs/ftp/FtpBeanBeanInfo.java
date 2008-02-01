package ftpfs.ftp;

import java.beans.*;

public class FtpBeanBeanInfo extends SimpleBeanInfo
{
    


    // Bean descriptor 
    /*lazy BeanDescriptor*/;
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( FtpBean.class , null );
        
        // Here you can add code for customizisng the BeanDescriptor.
        
        return beanDescriptor;         }
    
    
    // Property identifiers 
    private static final int PROPERTY_replyMessage = 0;
    private static final int PROPERTY_userName = 1;
    private static final int PROPERTY_passiveModeTransfer = 2;
    private static final int PROPERTY_serverName = 3;
    private static final int PROPERTY_socketTimeout = 4;
    private static final int PROPERTY_directory = 5;
    private static final int PROPERTY_reply = 6;
    private static final int PROPERTY_acctInfo = 7;
    private static final int PROPERTY_directoryContentAsString = 8;
    private static final int PROPERTY_directoryContent = 9;
    private static final int PROPERTY_systemType = 10;
    private static final int PROPERTY_port = 11;

    // Property array 
    /*lazy PropertyDescriptor*/;
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[12];
    
        try {
            properties[PROPERTY_replyMessage] = new PropertyDescriptor ( "replyMessage", FtpBean.class, "getReplyMessage", null );
            properties[PROPERTY_userName] = new PropertyDescriptor ( "userName", FtpBean.class, "getUserName", null );
            properties[PROPERTY_passiveModeTransfer] = new PropertyDescriptor ( "passiveModeTransfer", FtpBean.class, "isPassiveModeTransfer", "setPassiveModeTransfer" );
            properties[PROPERTY_serverName] = new PropertyDescriptor ( "serverName", FtpBean.class, "getServerName", null );
            properties[PROPERTY_socketTimeout] = new PropertyDescriptor ( "socketTimeout", FtpBean.class, "getSocketTimeout", "setSocketTimeout" );
            properties[PROPERTY_directory] = new PropertyDescriptor ( "directory", FtpBean.class, "getDirectory", "setDirectory" );
            properties[PROPERTY_reply] = new PropertyDescriptor ( "reply", FtpBean.class, "getReply", null );
            properties[PROPERTY_acctInfo] = new PropertyDescriptor ( "acctInfo", FtpBean.class, "getAcctInfo", null );
            properties[PROPERTY_directoryContentAsString] = new PropertyDescriptor ( "directoryContentAsString", FtpBean.class, "getDirectoryContentAsString", null );
            properties[PROPERTY_directoryContent] = new PropertyDescriptor ( "directoryContent", FtpBean.class, "getDirectoryContent", null );
            properties[PROPERTY_systemType] = new PropertyDescriptor ( "systemType", FtpBean.class, "getSystemType", null );
            properties[PROPERTY_port] = new PropertyDescriptor ( "port", FtpBean.class, "getPort", "setPort" );
        }
        catch( IntrospectionException e) {}
        
        // Here you can add code for customizing the properties array.
        
        return properties;         }
    
    // EventSet identifiers
    private static final int EVENT_propertyChangeListener = 0;

    // EventSet array
    /*lazy EventSetDescriptor*/;
    private static EventSetDescriptor[] getEdescriptor(){
        EventSetDescriptor[] eventSets = new EventSetDescriptor[1];
    
            try {
            eventSets[EVENT_propertyChangeListener] = new EventSetDescriptor ( FtpBean.class, "propertyChangeListener", java.beans.PropertyChangeListener.class, new String[] {"propertyChange"}, "addPropertyChangeListener", "removePropertyChangeListener" );
        }
        catch( IntrospectionException e) {}
        
        // Here you can add code for customizing the event sets array.
        
        return eventSets;         }
    
    // Method identifiers 
    private static final int METHOD_ftpConnect0 = 0;
    private static final int METHOD_ftpConnect1 = 1;
    private static final int METHOD_ftpConnect2 = 2;
    private static final int METHOD_close3 = 3;
    private static final int METHOD_fileDelete4 = 4;
    private static final int METHOD_fileRename5 = 5;
    private static final int METHOD_getAsciiFile6 = 6;
    private static final int METHOD_getAsciiFile7 = 7;
    private static final int METHOD_getAsciiFile8 = 8;
    private static final int METHOD_getAsciiFile9 = 9;
    private static final int METHOD_appendAsciiFile10 = 10;
    private static final int METHOD_putAsciiFile11 = 11;
    private static final int METHOD_getBinaryFile12 = 12;
    private static final int METHOD_getBinaryFile13 = 13;
    private static final int METHOD_getBinaryFile14 = 14;
    private static final int METHOD_getBinaryFile15 = 15;
    private static final int METHOD_getBinaryFile16 = 16;
    private static final int METHOD_getBinaryFile17 = 17;
    private static final int METHOD_getBinaryFile18 = 18;
    private static final int METHOD_getBinaryFile19 = 19;
    private static final int METHOD_putBinaryFile20 = 20;
    private static final int METHOD_putBinaryFile21 = 21;
    private static final int METHOD_putBinaryFile22 = 22;
    private static final int METHOD_putBinaryFile23 = 23;
    private static final int METHOD_putBinaryFile24 = 24;
    private static final int METHOD_putBinaryFile25 = 25;
    private static final int METHOD_appendBinaryFile26 = 26;
    private static final int METHOD_appendBinaryFile27 = 27;
    private static final int METHOD_appendBinaryFile28 = 28;
    private static final int METHOD_toParentDirectory29 = 29;
    private static final int METHOD_makeDirectory30 = 30;
    private static final int METHOD_removeDirectory31 = 31;
    private static final int METHOD_execute32 = 32;

    // Method array 
    /*lazy MethodDescriptor*/;
    private static MethodDescriptor[] getMdescriptor(){
        MethodDescriptor[] methods = new MethodDescriptor[33];
    
        try {
            methods[METHOD_ftpConnect0] = new MethodDescriptor ( FtpBean.class.getMethod("ftpConnect", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_ftpConnect0].setDisplayName ( "" );
            methods[METHOD_ftpConnect1] = new MethodDescriptor ( FtpBean.class.getMethod("ftpConnect", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class}));
            methods[METHOD_ftpConnect1].setDisplayName ( "" );
            methods[METHOD_ftpConnect2] = new MethodDescriptor ( FtpBean.class.getMethod("ftpConnect", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class}));
            methods[METHOD_ftpConnect2].setDisplayName ( "" );
            methods[METHOD_close3] = new MethodDescriptor ( FtpBean.class.getMethod("close", new Class[] {}));
            methods[METHOD_close3].setDisplayName ( "" );
            methods[METHOD_fileDelete4] = new MethodDescriptor ( FtpBean.class.getMethod("fileDelete", new Class[] {java.lang.String.class}));
            methods[METHOD_fileDelete4].setDisplayName ( "" );
            methods[METHOD_fileRename5] = new MethodDescriptor ( FtpBean.class.getMethod("fileRename", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_fileRename5].setDisplayName ( "" );
            methods[METHOD_getAsciiFile6] = new MethodDescriptor ( FtpBean.class.getMethod("getAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_getAsciiFile6].setDisplayName ( "" );
            methods[METHOD_getAsciiFile7] = new MethodDescriptor ( FtpBean.class.getMethod("getAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class, FtpObserver.class}));
            methods[METHOD_getAsciiFile7].setDisplayName ( "" );
            methods[METHOD_getAsciiFile8] = new MethodDescriptor ( FtpBean.class.getMethod("getAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class}));
            methods[METHOD_getAsciiFile8].setDisplayName ( "" );
            methods[METHOD_getAsciiFile9] = new MethodDescriptor ( FtpBean.class.getMethod("getAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class, FtpObserver.class}));
            methods[METHOD_getAsciiFile9].setDisplayName ( "" );
            methods[METHOD_appendAsciiFile10] = new MethodDescriptor ( FtpBean.class.getMethod("appendAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class}));
            methods[METHOD_appendAsciiFile10].setDisplayName ( "" );
            methods[METHOD_putAsciiFile11] = new MethodDescriptor ( FtpBean.class.getMethod("putAsciiFile", new Class[] {java.lang.String.class, java.lang.String.class, java.lang.String.class}));
            methods[METHOD_putAsciiFile11].setDisplayName ( "" );
            methods[METHOD_getBinaryFile12] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class}));
            methods[METHOD_getBinaryFile12].setDisplayName ( "" );
            methods[METHOD_getBinaryFile13] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, FtpObserver.class}));
            methods[METHOD_getBinaryFile13].setDisplayName ( "" );
            methods[METHOD_getBinaryFile14] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, Long.TYPE}));
            methods[METHOD_getBinaryFile14].setDisplayName ( "" );
            methods[METHOD_getBinaryFile15] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, Long.TYPE, FtpObserver.class}));
            methods[METHOD_getBinaryFile15].setDisplayName ( "" );
            methods[METHOD_getBinaryFile16] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_getBinaryFile16].setDisplayName ( "" );
            methods[METHOD_getBinaryFile17] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, Long.TYPE}));
            methods[METHOD_getBinaryFile17].setDisplayName ( "" );
            methods[METHOD_getBinaryFile18] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, FtpObserver.class}));
            methods[METHOD_getBinaryFile18].setDisplayName ( "" );
            methods[METHOD_getBinaryFile19] = new MethodDescriptor ( FtpBean.class.getMethod("getBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, Long.TYPE, FtpObserver.class}));
            methods[METHOD_getBinaryFile19].setDisplayName ( "" );
            methods[METHOD_putBinaryFile20] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, Class.forName("[B")}));
            methods[METHOD_putBinaryFile20].setDisplayName ( "" );
            methods[METHOD_putBinaryFile21] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, Class.forName("[B"), Long.TYPE}));
            methods[METHOD_putBinaryFile21].setDisplayName ( "" );
            methods[METHOD_putBinaryFile22] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_putBinaryFile22].setDisplayName ( "" );
            methods[METHOD_putBinaryFile23] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, FtpObserver.class}));
            methods[METHOD_putBinaryFile23].setDisplayName ( "" );
            methods[METHOD_putBinaryFile24] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, Long.TYPE}));
            methods[METHOD_putBinaryFile24].setDisplayName ( "" );
            methods[METHOD_putBinaryFile25] = new MethodDescriptor ( FtpBean.class.getMethod("putBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, Long.TYPE, FtpObserver.class}));
            methods[METHOD_putBinaryFile25].setDisplayName ( "" );
            methods[METHOD_appendBinaryFile26] = new MethodDescriptor ( FtpBean.class.getMethod("appendBinaryFile", new Class[] {java.lang.String.class}));
            methods[METHOD_appendBinaryFile26].setDisplayName ( "" );
            methods[METHOD_appendBinaryFile27] = new MethodDescriptor ( FtpBean.class.getMethod("appendBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class}));
            methods[METHOD_appendBinaryFile27].setDisplayName ( "" );
            methods[METHOD_appendBinaryFile28] = new MethodDescriptor ( FtpBean.class.getMethod("appendBinaryFile", new Class[] {java.lang.String.class, java.lang.String.class, FtpObserver.class}));
            methods[METHOD_appendBinaryFile28].setDisplayName ( "" );
            methods[METHOD_toParentDirectory29] = new MethodDescriptor ( FtpBean.class.getMethod("toParentDirectory", new Class[] {}));
            methods[METHOD_toParentDirectory29].setDisplayName ( "" );
            methods[METHOD_makeDirectory30] = new MethodDescriptor ( FtpBean.class.getMethod("makeDirectory", new Class[] {java.lang.String.class}));
            methods[METHOD_makeDirectory30].setDisplayName ( "" );
            methods[METHOD_removeDirectory31] = new MethodDescriptor ( FtpBean.class.getMethod("removeDirectory", new Class[] {java.lang.String.class}));
            methods[METHOD_removeDirectory31].setDisplayName ( "" );
            methods[METHOD_execute32] = new MethodDescriptor ( FtpBean.class.getMethod("execute", new Class[] {java.lang.String.class}));
            methods[METHOD_execute32].setDisplayName ( "" );
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

