package ftpfs.ftp;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * FtpBean			Version 1.4.4
 * Copyright 1999 Calvin Tai
 * E-mail: calvin_tai2000@yahoo.com.hk
 * URL: http://www.geocities.com/SiliconValley/Code/9129/javabean/ftpbean
 *
 * COPYRIGHT NOTICE
 * Copyright 1999 Calvin Tai All Rights Reserved.
 *
 * FtpBean may be modified and used in any application free of charge by
 * anyone so long as this copyright notice and the comments above remain
 * intact. By using this code you agree to indemnify Calvin Tai from any
 * liability that might arise from it's use.
 *
 * Selling the code for this java bean alone is expressly forbidden.
 * In other words, please ask first before you try and make money off of 
 * this java bean as a standalone application. 
 *
 * Obtain permission before redistributing this software over the Internet or
 * in any other medium.  In all cases copyright and header must remain intact.
 */

/*
 * Class Name: FtpBean
 * Author: Calvin Tai
 * Date: 20 Aug 1999
 * Last Updated: 28Mar2002
 *
 * 20120612--add cancel support where the observer can cancel the download.
 * 
 * Note:
 * 1) To turn on debug mode, change the field DEBUG to true,
 *    then re-compile the class file.
 *
 * Updates:
 * version 1.4.5 - 28 Mar 2002
 * 1) Check the reply code and login procedures according to the state diagram specified in RFC959,
 *    added class FtpReplyResourceBundle.
 * 2) Added property account information (acctInfo).
 * 3) Public methods ftpConnect(String, String) and ftpConnect(String, String, String, String) added.
 * 4) Use US-ASCII encoding for both the input, output stream and String to fix the problem where
 *    some system is not using US-ASCII as the default encoding.(e.g. EBCDIC)
 * 5) Handle the string is null in method checkReply
 *
 * version 1.4.4 - 10 March 2001
 * 1) Fixed bug on the getPassiveSocket method which pass incorrect server and port information
 *    when using the SocketOpener class for openning new socket.
 * 2) Fixed bug on the getDataSocket method where the ServerSocket is not closed when using
 *    active mode transfer. This may cause problem if there are too many ServerSocket opened.
 * 3) Fixed bug on most of the get/put file methods where the setTransferType is not enclosed
 *    in the "try" block.
 * 4) Replaced those depreciated methods for multi-thread.
 *
 * version 1.4.3 - 16, Nov 2000
 * 1) Increase code efficiency for private method getBytes(BufferedInputStream, FtpObserver).
 *    This increase the of those get file methods that return a byte array or String.
 * 2) Private method getPassiveDataSocket() changed, so that it read the ip address returned
 *    from the PASV command rather than using the getServerName method as the host.
 * 3) Added the SocketOpener class to deal with add timeout feature for openning Socket.
 * 4) Changed the way to get new Socket in ftpConnect and getPassiveSocket methods to support
 *    the timeout feature if it is being set.
 * 5) Public method getSocketTimeout() added.
 * 6) Public method setSocketTimeout(int) added.
 * 7) Public method getAsciiFile(String, String, String) added.
 * 8) Public method getAsciiFile(String, String, String, FtpObserver) added.
 * 
 * version 1.4.2 - 1, Aug 2000
 * 1) Updated dead lock in close() method when using putBinaryFile(String, String) to upload a
 *    non-existing local file.
 * 
 * version 1.4.1 - 19, May 2000
 * 1) Fixed bug on deadlock may caused by using active transfer mode to get data connection.
 * 2) Fixed bug on deadlock may caused by using passive transfer mode to get data connection.
 * 3) Private method getDataSocket(String[]) changed to getDataSocet(String, long).
 * 4) Public method execute() added.
 * 5) Public method getSystemType() added.
 *
 * version 1.4 - 28, March 2000
 * 1) Private method aquire() changed to acquire().
 * 2) All get/put file methods changed to support the feature of FtpObserver class.
 * 
 * version 1.3.1 - 1, Feb 2000
 * 1) Fixed bug on deadlock during incorrect login.
 * 2) Private method closeSocket() added.
 *
 * version 1.3 - 20, Jan 2000
 * 1) Public method getDirectoryContent() changed to getDirectoryContentAsString().
 * 2) Public method getPassiveModeTransfer() changed to isPassiveModeTransfer().
 * 3) Public method getDirectoryContent() added.
 * 4) Thread safe.
 * 5) Private method aquire added.
 * 6) Private method release added.
 *
 * version 1.2 - 5, Nov 1999
 * 1) Debug mode added.
 * 2) Public method putBinaryFile(String, String) added.
 * 3) Public method putBinaryFile(String, String, long) added.
 * 4) Public method getReply() added.
 * 5) Public method getAsciiFile(String) changed to getAsciiFile(String, String).
 * 6) Public method putAsciiFile(String, String) changed to putAsciiFile(String, String, String).
 * 7) Public method getPassive() changed to getPassiveModeTransfer().
 * 8) Public method setPassive(boolean) changed to setPassiveModeTransfer(boolean).
 *
 * version 1.1 - 21, Aug 1999
 * 1) Provide active data connection (Using PORT command).
 * 2) Public method setPassive(boolean) added.
 * 3) Public method getPassive() added.
 * 4) Fixed bug on getting file with a restarting point as an byte array.
 */

/**
 * This bean provide some basic FTP functions.<br>
 * You can use this bean in some visual development tools (eg. IBM VA) 
 * or just include its files and use its methods.<br>
 * This class is thread safe, only one thread can acquire the object and do some ftp operation at a time.<p>
 * <b><u>How to use it?</u></b><br>
 * To start a new instance of this FTP bean<br>
 * <font color="#0000ff">
 * FtpBean ftp = new FtpBean();
 * </font><br>
 * // To connect to a FTP server<br>
 * <font color="#0000ff">
 * ftp.ftpConnect("servername", "username", "password");
 * </font><br>
 * // Some methods return null if you call them without establish connection first.
 * <br>
 * //Then just call the functions provided by this bean.<br>
 * //After you end the ftp section. Just close the connection.<br>
 * <font color="#0000ff">
 * ftp.close();
 * </font><p>
 * Remarks:<br>
 * 1) Whenever a <b>ftp command failed</b> (eg. Permission denied), the methods throw an FtpException.<br>
 * So you need to catch the FtpException wherever you invoke those methods of this class.<br>
 * 2) This bean use <b>passive mode</b> to establish data connection by <b>default</b>.
 * If this cause problem from firewall of the network, try using active mode:<br>
 * ftp.setPassiveModeTransfer(false);<br>
 * 3) To turn on <b>debug mode</b>, you need to change the source of this class. Then re-compile it.<br>
 * 4) For <b>timeout on creating Socket</b>. if a timeout is being set and operation timeout, a 
 * java.io.InterruptedIOException is throw. This is the case for both passive transfer mode and
 * establishment of connection to the server at the beginning. For active transfer mode, timeout
 * is set in the servers ftpd. If there is timeout, the servers ftp software return an error
 * code which causing the bean to throw a ftp.FtpException.<p>
 * 
 *
 * <b><u>IMPORTANT for using in an Applet:</u></b><br>
 * 1) If you use this bean in an applet and the applet is open to the public,
 * please don't include the user name and password in 
 * the source code of your applet. As anyone who can get your class files can get your 
 * user name and password. It is reasonable to ask the user for user name and password 
 * if you are going to use FTP in the applet.<br>
 * 2) If you use it in an applet, please be aware of the security restriction from the browser.
 * As an unsigned applet can ONLY connect to the host which serves it. Also, some methods in this bean
 * will write/read to the local file system. These methods are also restricted by the browser.<br><br>
 *
 * If you find any bugs in this bean or any comment, please give me a notice at<br>
 * <a href="mailto:calvin_tai2000@yahoo.com.hk">Calvin(calvin_tai2000@yahoo.com.hk)</a><br>
 *
 */ 

public class FtpBean
{
    protected final static String FTP_INIT = "FTP_INIT";
    // Ftp commands set
    protected final static String CMD_ACCT = "ACCT ";
    protected final static String CMD_APPE = "APPE ";
    protected final static String CMD_CWD  = "CWD ";
    protected final static String CMD_CDUP = "CDUP";
    protected final static String CMD_DELE = "DELE ";
    protected final static String CMD_MKD  = "MKD ";
    protected final static String CMD_PASV = "PASV";
    protected final static String CMD_PASS = "PASS ";
    protected final static String CMD_PORT = "PORT ";
    protected final static String CMD_PWD  = "PWD";
    protected final static String CMD_QUIT = "QUIT";
    protected final static String CMD_RMD  = "RMD ";
    protected final static String CMD_REST = "REST ";
    protected final static String CMD_RETR = "RETR ";
    protected final static String CMD_RNTO = "RNTO ";
    protected final static String CMD_RNFR = "RNFR ";
    protected final static String CMD_SITE = "SITE ";
    protected final static String CMD_STOR = "STOR ";
    protected final static String CMD_SYST = "SYST";
    protected final static String CMD_USER = "USER ";
    protected final static String CMD_TYPE = "TYPE ";
    protected final static String CMD_LIST = "LIST";

    // Reply code type, determined by the first digit of the reply code
    protected final static String REPLY_POS_PRE = "1";	// Positive Preliminary Reply
    protected final static String REPLY_POS_CMP = "2";	// Positive Completion Reply
    protected final static String REPLY_POS_INT = "3";	// Positive Intermediate Reply
    protected final static String REPLY_TRA_NEG = "4";	// Transient Negative Completion Reply
    protected final static String REPLY_PER_NEG = "5";	// Permanent Negative Completion Reply
    protected final static String REPLY_UNDEF   = "0";	// Undefined reply, should not be exist.

    private final String TF_MOD_ASCII = "A";
    private final String TF_MOD_BIN   = "I";

    private final String FTP_ENCODING = "US-ASCII";

    private final FtpReplyResourceBundle ftpReplies = new FtpReplyResourceBundle();

    private String acctInfo = "";          // account information
    private String server = "";            // server name
    private String user = "";              // user name
    private String replymessage = "";      // reply message from server
    private String reply = "";             // reply to the command
    private Socket socket;                 // Socket for FTP connection
    private BufferedReader in;             // Input for FTP connection
    private PrintWriter out;               // Output for FTP connection
    private int port = 21;                 // FTP port number, default 21
    private boolean passive = true;        // Passive mode transfer, default true
    private int timeout;                   // Timeout to open socket

    // Needed for thread safety
    private int[] lock = new int[0];               // For synchronized locking
    private boolean acquired = false;              // Acquired by a thread or not
    private Vector thread_spool = new Vector();    // Spool for the waiting threads

    // Needed for some Visual tools
    private PropertyChangeSupport pcs;     // PropertyChangeSupport for visual tools

    final private boolean DEBUG = false;   // True to turn on debug mode

    /**
     * Constructor
     */
    public FtpBean()
    {
        pcs = new PropertyChangeSupport(this);
    }

    /*
     * Add PropertyChangeListener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    /*
     * removePropertyChangeListener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Connect to Ftp server and login.
     * @param server Name of server
     * @param user User name for login
     * @exception FtpException if a ftp error occur (eg. Login fail in this case).
     * @exception IOException if an I/O error occur
     */
    public void ftpConnect(String server, String user)
        throws IOException, FtpException
    {
	ftpConnect(server, user, "", "");
    }

    /**
     * Connect to Ftp server and login.
     * @param server Name of server
     * @param user User name for login
     * @param password Password for login
     * @exception FtpException if a ftp error occur (eg. Login fail in this case).
     * @exception IOException if an I/O error occur
     */
    public void ftpConnect(String server, String user, String password)
        throws IOException, FtpException
    {
	ftpConnect(server, user, password, "");
    }

    /**
     * Connect to FTP server and login.
     * @param server Name of server
     * @param user User name for login
     * @param password Password for login
     * @param acct account information
     * @exception FtpException if a ftp error occur (eg. Login fail in this case).
     * @exception IOException if an I/O error occur
     */
    public void ftpConnect(String server, String user, String password, String acct)
        throws IOException, FtpException
    {
        if(DEBUG)    // Debug message
            System.out.println("FtpBean: Connecting to server " + server);

        acquire();   // Acquire the object

        // Set server name & user name
        setServerName(server);
        setUserName(user);
    	setAcctInfo(acct);

        Proxy proxy= Proxy.NO_PROXY;
        String proxyHost= System.getProperty("ftp.proxyHost");
        if ( proxyHost!=null ) {
            String proxyPort= System.getProperty("ftp.proxyPort","21");
            String nonProxyHosts= System.getProperty("ftp.nonProxyHosts","" );
            if ( !nonProxyHosts.equals("") ) {
                throw new IllegalArgumentException("ftp.nonProxyHosts is not supported, please submit this error");
            }

            proxy= new Proxy( Proxy.Type.valueOf("ftp"), new InetSocketAddress(proxyHost,Integer.parseInt(proxyPort)) );
        }

        // Create socket, get input & output stream
        try
        {
            if (timeout == 0)
	    {
                if ( proxy==Proxy.NO_PROXY ) {
                    socket = new Socket(server, port);
                } else {
                    //untested
                    socket = new Socket(proxy);
                }
            } else
	    {
                /* If a timeout has been set before opening the socket,
                 * use the SocketOpener. Note that if the thread times out,
                 * it is forcibly terminated.
                 */
                if ( proxy==Proxy.NO_PROXY ) {
                    socket = new SocketOpener (server, port).makeSocket(timeout);
                } else {
                    //untested
                    socket = new SocketOpener ( proxy ).makeSocket(timeout);
                }
                 
            }

            in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), FTP_ENCODING));
            out = new PrintWriter(
	              new OutputStreamWriter(socket.getOutputStream(), FTP_ENCODING), true);

            // Read reply code when get connected
            getRespond(FTP_INIT);

            if(DEBUG)    // Debug message
                System.out.println("FtpBean: Connected");

            // Login
            ftpLogin(user, password, acct);        // check if login success
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Close FTP connection.
     * @exception IOException if an I/O error occur
     * @exception FtpException if a ftp error occur
     */
    public void close()
        throws IOException, FtpException
    {
        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_QUIT);

            closeSocket();
            // Set account information, server name & user name to ""
            setServerName("");
            setUserName("");
            setAcctInfo("");
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Delete a file at the FTP server.
     * @param filename Name of the file to be deleted.
     * @exception FtpException if a ftp error occur. (eg. no such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public void fileDelete(String filename)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        try
        {
	    ftpCommand(CMD_DELE, filename);
        } finally
        {
            release();    // Release the object
        }    
    }

    /**
     * Rename a file at the FTP server.
     * @param oldfilename The name of the file to be renamed
     * @param newfilename The new name of the file
     * @exception FtpException if a ftp error occur. (eg. A file named the new file name already in this case.)
     * @exception IOException if an I/O error occur.
     */
    public void fileRename(String oldfilename, String newfilename)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire this object

        try
        {
            ftpCommand(CMD_RNFR, oldfilename);
            ftpCommand(CMD_RNTO, newfilename);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Get an ASCII file from the server and return as String.
     * @param filename Name of ASCII file to be getted.
     * @param separator The line separator you want in the return String (eg. "\r\n", "\n", "\r")
     * @return The Ascii content of the file. It uses parameter 'separator' as the line separator.
     * @exception FtpException if a ftp error occur. (eg. no such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public String getAsciiFile(String filename, String separator)
        throws IOException, FtpException
    {
        return getAsciiFile(filename, separator, (FtpObserver)null);
    }

    /**
     * Get an ASCII file from the server and return as String.
     * @param filename Name of ASCII file to be getted.
     * @param separator The line separator you want in the return String (eg. "\r\n", "\n", "\r").
     * @param observer The observer of the downloading progress
     * @return The Ascii content of the file. It uses parameter 'separator' as the line separator.
     * @exception FtpException if a ftp error occur. (eg. no such file in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpObserver
     */
    public String getAsciiFile(String filename, String separator, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return null;

        String str_content;

        acquire();    // Acquire the object

        try
        {
            setTransferType(true);
	    str_content = new String(getFile(filename, 0, observer), FTP_ENCODING);
        } finally
        {
            release();    // Release the object
        }

        str_content = changeLineSeparator(str_content, "\r\n", separator);
        return str_content;
    }

    /**
     * Get an ascii file from the server and write to local file system.
     * @param ftpfile Name of ascii file in the server side.
     * @param localfile Name of ascii file in the local file system.
     * @param separator The line separator you want in the local ascii file (eg. "\r\n", "\n", "\r").
     * @exception FtpException if a ftp error occur. (eg. no such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public void getAsciiFile(String ftpfile, String localfile, String separator)
        throws IOException, FtpException
    {
	getAsciiFile(ftpfile, localfile, separator, null);
    }

    /**
     * Get an ascii file from the server and write to local file system.
     * @param ftpfile Name of ascii file in the server side.
     * @param localfile Name of ascii file in the local file system.
     * @param separator The line separator you want in the local ascii file (eg. "\r\n", "\n", "\r").
     * @param observer The observer of the downloading progress.
     * @exception FtpException if a ftp error occur. (eg. no such file in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpObserver
     */
    public void getAsciiFile(String ftpfile, String localfile, String separator, FtpObserver observer)
        throws IOException, FtpException
    {
	final int BUF = 1024;
	if(out == null)
	    return;

	acquire();
        
        Socket sock= null;
        BufferedInputStream reader = null;
        RandomAccessFile out= null;
	try
	{
            sock = getDataSocket(CMD_RETR, ftpfile, 0);

            // Read bytes from server
            reader = new BufferedInputStream(
                                             sock.getInputStream());

            // File to write to
	    out = new RandomAccessFile(localfile, "rw");

            int offset;
            byte[] data = new byte[BUF + 1];

            boolean cont= true;
            
            // Loop to read file
            while( cont && (offset = reader.read(data, 0, BUF)) != -1)
            {
                // Last character is '\r', read one more character.
		// It is because the next character may be '\n'. Where "\r\n" is the 
		// line separactor in ASCII transmission. Then it can be replaced.
                if(((char)data[offset - 1] == '\r'))
        	{
                    data[offset] = (byte)reader.read();
		    if(data[offset] != -1)
                        offset++;
                }
                String content = new String(data, 0, offset, FTP_ENCODING);
                content = changeLineSeparator(content, "\r\n", separator);
        	out.writeBytes(content);
                
                if(observer != null) {
                    cont= observer.byteRead(offset);
                }
            }
            
            if ( cont==false ) {
                throw new IOException("Operation cancelled");
            }
            
            getRespond(CMD_RETR);

            if(!reply.substring(0, 3).equals("226"))
            {
                throw new FtpException(reply);    // transfer incomplete
            }
	} finally
	{
	    if ( out!=null ) out.close();
            if ( reader!=null ) reader.close();
	    if ( sock!=null ) sock.close();

            release();
	}
    }

    /**
     * Append an ascii file to the server.
     * <br>Remark:<br>
     * this method convert the line separator of the String content to <br>
     * NVT-ASCII format line separator "\r\n". Then the ftp daemon will <br>
     * convert the NVT-ASCII format line separator into its system line separator.
     * @param filename The name of file
     * @param content The String content of the file
     * @param separator Line separator of the content
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void appendAsciiFile(String filename, String content, String separator)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        content = changeLineSeparator(content, separator, "\r\n");
        byte[] byte_content = content.getBytes(FTP_ENCODING);
 
        acquire();    // Acquire the object

        try
        {
            setTransferType(true);
            appendFile(filename, byte_content);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Save an ascii file to the server.
     * <br>Remark:<br>
     * this method convert the line separator of the String content to <br>
     * NVT-ASCII format line separator "\r\n". Then the ftp daemon will <br>
     * convert the NVT-ASCII format line separator into its system line separator.
     * @param filename The name of file
     * @param content The String content of the file
     * @param separator Line separator of the content
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void putAsciiFile(String filename, String content, String separator)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        content = changeLineSeparator(content, separator, "\r\n");
        byte[] byte_content = content.getBytes(FTP_ENCODING);

        acquire();    // Acquire the object

        try
        {
            setTransferType(true);
            putFile(filename, byte_content, 0);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Get a binary file and return a byte array.
     * @param filename The name of the binary file to be got.
     * @return An array of byte of the content of the binary file.
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public byte[] getBinaryFile(String filename)
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        return getBinaryFile(filename, 0, null);
    }

    /**
     * Get a binary file and return a byte array.
     * @param filename The name of the binary file to be got.
     * @param observer The observer of the downloading progress.
     * @return An array of byte of the content of the binary file.
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public byte[] getBinaryFile(String filename, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        return getBinaryFile(filename, 0, observer);
    }

    /**
     * Get a binary file at a restarting point.
     * Return null if restarting point is less than zero.
     * @param filename Name of binary file to be getted.
     * @param restart Restarting point, ignored if less than or equal to zero.
     * @return An array of byte of the content of the binary file.
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public byte[] getBinaryFile(String filename, long restart)
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        return getBinaryFile(filename, restart, null);
    }

    /**
     * Get a binary file at a restarting point.
     * Return null if restarting point is less than zero.
     * @param filename Name of binary file to be getted.
     * @param restart Restarting point, ignored if less than or equal to zero.
     * @param observer The FtpObserver which monitor this downloading progress
     * @return An array of byte of the content of the binary file.
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpObserver
     */
    public byte[] getBinaryFile(String filename, long restart, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        byte[] content;
        acquire();    // Acquire the object

        try
        {
            setTransferType(false);
            content = getFile(filename, restart, observer);
        } finally
        {
            release();    // Release the object
        }
        return content;
    }

    /**
     * Read file from ftp server and write to a file in local hard disk.
     * This method is much faster than those method which return a byte array<br>
     * if the network is fast enough.<br>
     * <br>Remark:<br>
     * Cannot be used in unsigned applet.
     * @param ftpfile Name of file to be get from the ftp server, can be in full path.
     * @param localfile Name of local file to be write, can be in full path.
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public void getBinaryFile(String ftpfile, String localfile)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        getBinaryFile(ftpfile, localfile, 0, null);
    }

    /**
     * Read file from ftp server and write to a file in local hard disk.
     * This method is much faster than those method which return a byte array<br>
     * if the network is fast enough.<br>
     * <br>Remark:<br>
     * Cannot be used in unsigned applet.
     * @param ftpfile Name of file to be get from the ftp server, can be in full path.
     * @param localfile Name of local file to be write, can be in full path.
     * @param restart Restarting point
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     */
    public void getBinaryFile(String ftpfile, String localfile, long restart)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        getBinaryFile(ftpfile, localfile, restart, null);
    }

    /**
     * Read file from ftp server and write to a file in local hard disk.
     * This method is much faster than those method which return a byte array<br>
     * if the network is fast enough.<br>
     * <br>Remark:<br>
     * Cannot be used in unsigned applet.
     * @param ftpfile Name of file to be get from the ftp server, can be in full path.
     * @param localfile Name of local file to be write, can be in full path.
     * @param observer The FtpObserver which monitor this downloading progress
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpObserver
     */
    public void getBinaryFile(String ftpfile, String localfile, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        getBinaryFile(ftpfile, localfile, 0, observer);
    }

    /**
     * Read from a ftp file and restart at a specific point.
     * This method is much faster than those method which return a byte array<br>
     * if the network is fast enough.<br>
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param ftpfile Name of file to be get from the ftp server, can be in full path.
     * @param localfile File name of local file
     * @param restart Restarting point, ignored if equal or less than zero.
     * @param observer The FtpObserver which monitor this downloading progress
     * @exception FtpException if a ftp error occur. (eg. No such file in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpObserver
     */
    public void getBinaryFile(String ftpfile, String localfile, long restart, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        Socket sock = null;
        RandomAccessFile out=null;
        BufferedInputStream reader= null;
        try
        {
            setTransferType(false);             // Set transfer type to binary
            
            sock = getDataSocket(CMD_RETR ,ftpfile, restart);
 
            // Read bytes from server and write to file.
            reader = new BufferedInputStream(
                                             sock.getInputStream());
            out = new RandomAccessFile(localfile, "rw");
            out.seek(restart);
            readData(reader, out, observer);

            if (reader!=null ) { reader.close(); reader=null; }
            if ( out!=null ) { out.close(); out=null; }
            if ( sock!=null ) { sock.close(); sock=null; }

            getRespond(CMD_RETR);
        } finally
        {
            if (reader!=null ) reader.close();
            if ( out!=null ) out.close();
            if ( sock!=null ) sock.close();
            release();    // Release the object
        }
    }

    /**
     * Put a binary file to the server from an array of byte.
     * @param filename The name of file.
     * @param content The byte array to be written to the server.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void putBinaryFile(String filename, byte[] content)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        putBinaryFile(filename, content, -1);
    }

    /**
     * Put a binary file to the server from an array of byte with a restarting point
     * @param filename The name of file.
     * @param content The byte array to be write to the server.
     * @param restart The restarting point, ingored if less than or equal to zero.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void putBinaryFile(String filename, byte[] content, long restart)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        acquire();    // Acquire the object

        try
        {
            setTransferType(false);
            putFile(filename, content, restart);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Read a file from local hard disk and write to the server.
     * <br>Remark:<br>
     * <br>Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void putBinaryFile(String local_file, String remote_file)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        putBinaryFile(local_file, remote_file, 0, null);
    }

    /**
     * Read a file from local hard disk and write to the server.
     * <br>Remark:<br>
     * <br>Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @param observer The FtpObserver which monitor this uploading progress.
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void putBinaryFile(String local_file, String remote_file, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        putBinaryFile(local_file, remote_file, 0, observer);
    }

    /**
     * Read a file from local hard disk and write to the server with restarting point.
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @param restart The restarting point, ignored if less than or greater than zero.
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void putBinaryFile(String local_file, String remote_file, long restart)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        putBinaryFile(local_file, remote_file, restart, null);
    }

    /**
        * Read a file from local hard disk and write to the server with restarting point.
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @param observer The FtpObserver which monitor this uploading progress
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
    */
    public void putBinaryFile(String local_file, String remote_file, long restart, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        try
        {
            Socket sock = null;
            setTransferType(false);
            RandomAccessFile fin = new RandomAccessFile(local_file, "r");
            sock = getDataSocket(CMD_STOR, remote_file, restart);

            if(restart > 0)
                fin.seek(restart);
            DataOutputStream dout = new DataOutputStream(sock.getOutputStream());
            writeData(fin, dout, observer);
            fin.close();
            dout.close();
            getRespond(CMD_STOR);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Read a file from local hard disk and append a file on the server.
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param filename Name of local file and remote file.
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void appendBinaryFile(String filename)
        throws IOException, FtpException
    {
        appendBinaryFile (filename, filename, null);
    }

    /**
     * Read a file from local hard disk and append to the server with restarting point.
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void appendBinaryFile(String local_file, String remote_file)
        throws IOException, FtpException
    {
        appendBinaryFile (local_file, remote_file, null);
    }

    /**
     * Read a file from local hard disk and append to the server with restarting point.
     * Remark:<br>
     * Cannot be used in unsigned applet.
     * @param local_file Name of local file, can be in full path.
     * @param remote_file Name of file in the ftp server, can be in full path.
     * @param observer The FtpObserver which monitor this uploading progress
     * @exception FtpException if a ftp error occur. (eg. permission denied)
     * @exception IOException if an I/O error occur.
     */
    public void appendBinaryFile(String local_file, String remote_file, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        try
        {
            Socket sock = null;
            setTransferType(false);
            RandomAccessFile fin = new RandomAccessFile(local_file, "r");
            sock = getDataSocket(CMD_APPE, remote_file, 0);

            DataOutputStream dout = new DataOutputStream(sock.getOutputStream());
            writeData(fin, dout, observer);
            fin.close();
            dout.close();
            getRespond(CMD_APPE);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Get current directory name.
     * @return The name of the current directory.
     * @exception FtpException if a ftp error occur.
     * @exception IOException if an I/O error occur.
     */
    public String getDirectory()
        throws IOException, FtpException
    {
        if(out == null)
            return null; 

        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_PWD);
        } finally
        {
            release();    // Release the object
        }

        int first = reply.indexOf("\"");
        int last = reply.lastIndexOf("\"");
        return reply.substring(first + 1, last);
    }

    /**
     * Change directory.
     * @param directory Name of directory
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void setDirectory(String directory)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_CWD, directory);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Change to parent directory.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void toParentDirectory()
        throws IOException, FtpException
    {
        if(out == null)
            return;

        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_CDUP);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Get the content of current directory
     * @return A FtpListResult object, return null if it is not connected.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     * @see FtpListResult
     */
    public FtpListResult getDirectoryContent()
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        String str_list = getDirectoryContentAsString();
        FtpListResult ftplist = new FtpListResult();
        ftplist.parseList(str_list, getSystemType());
        return ftplist;
    }

    /**
     * Get the content of current directory.
     * @return A list of directories, files and links in the current directory.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public String getDirectoryContentAsString()
        throws IOException, FtpException
    {
        if(out == null)
            return null;

        StringBuffer list = new StringBuffer(""); // Directory list
        Socket sock = null;                       // Socket to establish data connection
        acquire();    // Acquire the object

        try
        {
            // get DataSocket for the LIST command.
            // As no restarting point, send 0.
            sock = getDataSocket(CMD_LIST, 0);

            BufferedReader listen = new BufferedReader(
                                        new InputStreamReader(
                                            sock.getInputStream(), FTP_ENCODING));
            // Read bytes from server.
            String line;
            while((line = listen.readLine()) != null)
                list.append(line).append("\n");

            listen.close();
            sock.close();

            getRespond(CMD_LIST);
        } finally
        {
            release();    // Release the object
        }
        return list.toString();
    }

    /**
     * Make a directory in the server.
     * @param directory The name of directory to be made.
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void makeDirectory(String directory)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_MKD ,directory);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Remove a directory in the server
     * @param directory The name of directory to be removed
     * @exception FtpException if a ftp error occur. (eg. permission denied in this case)
     * @exception IOException if an I/O error occur.
     */
    public void removeDirectory(String directory)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_RMD ,directory);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Execute a command using ftp.
     * e.g. chmod 700 file
     * @param exec The command to execute.
     * @exception FtpException if a ftp error occur. (eg. command not understood)
     * @exception IOException if an I/O error occur.
     */
    public void execute(String exec)
        throws IOException, FtpException
    {
        if(out == null)
            return;
        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_SITE, exec);
        } finally
        {
            release();    // Release the object
        }
    }

    /**
     * Get the type of operating system of the server.
     * Return null if it is not currently connected to any ftp server.
     * @return Name of the operating system.
     */
    public String getSystemType()
        throws IOException, FtpException
    {
        if(out == null)
            return null;
        acquire();    // Acquire the object

        try
        {
            ftpCommand(CMD_SYST);
        } finally
        {
            release();    // Release the object
        }

        return reply.substring(4);
    }

    /**
     * Return the port number
     */
    public int getPort()
    {
        return port;
    }

    /**
     * Set port number if the port number of ftp is not 21
     */
    public void setPort(int port)
    {
        acquire();    // Acquire the object
        pcs.firePropertyChange("port",
                                Integer.valueOf(this.port),
                                Integer.valueOf(port));
        this.port = port;
        release();    // Release the object
    }

    /**
     * Set timeout when creating a socket.
     * This include trying to connect to the ftp server at the beginnig and
     * trying to connect to the server in order to establish a data connection.
     * @param timeout Timeout in milliseconds, 0 means infinity.
     */
    public void setSocketTimeout(int timeout) throws SocketException
    {
        acquire();    // Acquire the object
        pcs.firePropertyChange("socketTimeout",
                                Integer.valueOf(this.timeout),
                                Integer.valueOf(timeout));
        this.timeout = timeout;
        release();    // Release the object
    }

    /**
     * Get timeout when creating socket.
     * @return Timeout for creating socket in milliseconds, 0 means infinity.
     */
    public int getSocketTimeout() throws SocketException
    {
        return timeout; // default is 0
    }

    /**
     * Return the account information. Return "" if it is not connected to any server.
     */
    public String getAcctInfo()
    {
	return acctInfo;
    }

    /**
     * Return the server name. Return "" if it is not connected to any server.
     */
    public String getServerName()
    {
        return server;
    }

    /**
     * Return the user name. Return "" if it is not connected to any server.
     */
    public String getUserName()
    {
        return user;
    }

    /**
     * Get reply of the last command.
     * @return Reply of the last comomand<br>for example: 250 CWD command successful
     */
    public String getReply()
    {
        return reply;
    }

    /**
     * Get reply message of the last command.
     * @return Reply message of the last command<br>for example:<br>
     * 250-Please read the file README<br>
     * 250-it was last modified on Wed Feb 10 21:51:00 1999 - 268 days ago
     */
    public String getReplyMessage()
    {
        return replymessage;
    }

    /**
     * Return true if it is using passive transfer mode.
     */
    public boolean isPassiveModeTransfer()
    {
        return passive;
    }

    /**
     * Set passive transfer mode. Default is true.
     * @param passive Using passive transfer if true.
     */
    public void setPassiveModeTransfer(boolean passive)
    {
        acquire();    // Acquire the object
        pcs.firePropertyChange("passiveModeTransfer",
                                Boolean.valueOf(this.passive),
                                Boolean.valueOf(passive));
        this.passive = passive;
        if(DEBUG)    // debug message
            System.out.println("FtpBean: Set passive transfer - " + passive);
        release();    // Release the object
    }

    /*
     * Close the Socket, input and output stream
     */
    private void closeSocket()
        throws IOException
    {
        in.close();
        out.close();
        socket.close();
        in = null;
        out = null;
        socket = null;
    }

    /*
     * Get the reply type by identifying the first digit of the reply code.
     */
    private String getReplyType(String reply_code)
    {
	if(reply_code == null || reply_code.length() <= 0)
	    return REPLY_UNDEF;
	String reply_type = reply_code.substring(0, 1);
	if(!(reply_type.equals(REPLY_POS_PRE) || 
	     reply_type.equals(REPLY_POS_CMP) || 
	     reply_type.equals(REPLY_POS_INT) ||
	     reply_type.equals(REPLY_TRA_NEG) ||
	     reply_type.equals(REPLY_PER_NEG)))
	    return REPLY_UNDEF;
	return reply_type;
    }

    /*
     * Read the respond message from the server's inputstream and assign to replymessage
     */
    private void getRespond(String cmd)
        throws IOException, FtpException
    {
        String line = "";
        String replymessage = "";
        do
        {
            line = in.readLine();
            //System.err.println("FTP Responds: "+line);
            if(!checkReply(line))
                break;
            if ( line.contains("Transfer complete") ) {
                break;
            }
            replymessage = replymessage.concat(line).concat("\n");
        } while(true);
        setReplyMessage(replymessage);
        setReply(line);
	String reply_type = getReplyType(reply);
	String[] valid_replies = null;
	try { valid_replies = ftpReplies.getStringArray(cmd); }
	catch(MissingResourceException e) { throw new FtpException("Valid reply for command '" + cmd + "' not found in reply resource bundle"); }
	boolean valid = false;
	for(int i = 0; i < valid_replies.length; i++)
	{
	    if(reply_type.equals(valid_replies[i]))
	    {
		valid = true;
		break;
	    }
	}
	if(!valid)
	    throw new FtpException(reply);
    }

    /*
     * Login to server, using FTP commands "USER" and "PASS"
     * @param user FTP username
     * @param password FTP Password
     */
    private void ftpLogin(String user, String password, String acct)
        throws IOException, FtpException
    {
        ftpCommand(CMD_USER ,user);        // send user name
	if(getReplyType(reply).equals(REPLY_POS_CMP))
	    return;
        ftpCommand(CMD_PASS, password);    // send password
	if(getReplyType(reply).equals(REPLY_POS_CMP))
	    return;
	ftpCommand(CMD_ACCT, acct);
    }

    /* 
     * Send FTP command to the server.
     * @param command The command to be sent
     */
    private void ftpCommand(String cmd)
        throws IOException, FtpException
    {
	ftpCommand(cmd, "");
    }

    /* 
     * Send FTP command to the server.
     * @param command The command to be sent
     * @param Expected code return from the command.
     */
    private void ftpCommand(String cmd, String param)
        throws IOException, FtpException
    {
        if(out == null)
            return;

        if(DEBUG)    // Debug message
        {
            if(cmd.equals(CMD_PASS))
                System.out.println("FtpBean: Send password");
            else
                System.out.println("FtpBean: Send command \"" + cmd + param + "\"");
        }

        out.print(cmd + param + "\r\n");    // Send cmd
        out.flush();

        getRespond(cmd);
    }

    /*
     * Get a file, return a byte array.
     * @param filename Name of binary file
     * @param restart The restarting point
     * @param observer Observer of the downloading progress
     */
    private byte[] getFile(String filename, long restart, FtpObserver observer)
        throws IOException, FtpException
    {
        if(out == null)
            return null;

        Socket sock = null;
        sock = getDataSocket(CMD_RETR, filename, restart);

        // Read bytes from server
        BufferedInputStream reader = new BufferedInputStream(
                        sock.getInputStream());
        byte[] data = getBytes(reader, observer);
        reader.close();
        sock.close();
        getRespond(CMD_RETR);

        if(!reply.substring(0, 3).equals("226"))
        {
            throw new FtpException(reply);    // transfer incomplete
        }
        return data;
    }

    /*
     * Read bytes continuously from the BufferedInputStream.
     * Add the new read bytes to an byte array.
     * Return the byte array after all bytes are read.
     * If the FtpObserver is not null, it invokes its byteRead method
     * every time there are new bytes read.
     */
    private byte[] getBytes(BufferedInputStream reader, FtpObserver observer)
        throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        int offset;
        byte[] data = new byte[1024];
        boolean cont= true;
        while( cont && (offset = reader.read(data)) != -1)
        {
            out.write(data, 0, offset);
            if(observer != null) {
                cont= observer.byteRead(offset);
            }
        }
        
        if ( !cont ) {
            throw new IOException("Operation cancelled");
        }
        return out.toByteArray();
    }

    private void appendFile(String filename, byte[] content)
        throws IOException, FtpException
    {
        Socket sock = null;
        sock = getDataSocket(CMD_APPE, filename, 0);

        // Write data to server
        DataOutputStream writer = new DataOutputStream(sock.getOutputStream());
        writer.write(content, 0, content.length);
        writer.close();
        sock.close();

        getRespond(CMD_APPE);       
    }
    
    /*
     * Accept a byte array and filename, then save to server
     * @param file Name of file to be saved.
     * @param content The content of the ASCII file.
     * @param restart Restarting point
     */
    private void putFile(String filename, byte[] content, long restart)
        throws IOException, FtpException
    {
        Socket sock = null;
        sock = getDataSocket(CMD_STOR, filename, restart);

        // Write data to server
        DataOutputStream writer = new DataOutputStream(sock.getOutputStream());
        writer.write(content, 0, content.length);
        writer.close();
        sock.close();

        getRespond(CMD_STOR);
    }

    /*
     * Establish data connection for transfer
     */
    private Socket getDataSocket(String command, long restart)
        throws IOException, FtpException
    {
	return getDataSocket(command, "", restart);
    }

    /*
     * Establish data connection for transfer
     */
    private Socket getDataSocket(String command, String param, long restart)
        throws IOException, FtpException
    {
        Socket sock = null;
        ServerSocket ssock = null;

        // Establish data conncetion using passive or active mode.
        if(passive)
            sock = getPassiveDataSocket();
        else
            ssock = getActiveDataSocket();

        // Send the restart command if it is greater than zero
        if(restart > 0)
            ftpCommand(CMD_REST, Long.toString(restart));

        // Send commands like LIST, RETR and STOR
        // These commands will return 125 or 150 when success.
        ftpCommand(command, param);

        // Get Socket object for active mode.
        if(!passive)
	{
            sock = ssock.accept();
	    ssock.close();
	}

        return sock;
    }

    /*
     * Establish data connection in passive mode using "PASV" command
     * Change the server to passive mode. 
     * by the command "PASV", it will return its address
     * and port number that it will listen to.
     * Create a Socket object to that address and port number.
     * Then return the Socket object.
     */
    private Socket getPassiveDataSocket()
        throws IOException, FtpException
    {
	Socket sock = null;

        ftpCommand(CMD_PASV);

        // array that holds the outputed address and port number.
        String[] address = new String[6];

        // Extract the address & port numbers from the string like
	// 227 Entering Passive Mode (192.168.111.1,220,235)
        reply = reply.substring(reply.indexOf('(') + 1, reply.indexOf(')'));

        // put the 'reply' to the array 'address'
        StringTokenizer t = new StringTokenizer(reply, ",");
        for(int i = 0; i < 6; i++)
            address[i] = t.nextToken();

        // Returned server address
        String SRV_IP = address[0] + '.' + address[1] + '.' + address[2] + '.' + address[3];

        // Get the port number
        // Left shift the first number by 8
        int NEW_PORT = (Integer.parseInt(address[4]) << 8) +
                        Integer.parseInt(address[5]);

        if(DEBUG)
	    System.out.println("FtpBean: Extracted Server ip - " + SRV_IP + ", Port Number - " + NEW_PORT);

        // Create a new socket object
	if(timeout == 0)
	{
            sock = new Socket(SRV_IP, NEW_PORT);
	} else
	{
            sock = new SocketOpener(SRV_IP, NEW_PORT).makeSocket(timeout);
	}
        return sock;
    }

    /*
     * Establish data connection in active mode using "PORT" command.
     * It create a ServerSocket object to listen for a port number in local machine.
     * Use port command to tell the server which port the local machine is listenning.
     * Return the ServerSocket object.
     */
    private ServerSocket getActiveDataSocket()
        throws IOException, FtpException
    {
        int[] port_numbers = new int[6];            // Array that contains 

        // Get ip address of local machine. ip address and port numbers
        String local_address = socket.getLocalAddress().getHostAddress(); 

        // Assign the ip address of local machine to the array.
        StringTokenizer st = new StringTokenizer(local_address, ".");
        for(int i = 0; i < 4; i++)
            port_numbers[i] = Integer.parseInt(st.nextToken());

        ServerSocket ssocket = new ServerSocket(0);  // ServerSocket to listen to a random free port number

        int local_port = ssocket.getLocalPort();     // The port number it is listenning to

        // Assign port numbers the array
        port_numbers[4] = ((local_port & 0xff00) >> 8);
        port_numbers[5] = (local_port & 0x00ff);

        // Send "PORT" command to server
        String port_param = "";
        for(int i = 0; i < port_numbers.length; i++)
        {
            port_param = port_param.concat(String.valueOf(port_numbers[i]));
            if(i < port_numbers.length - 1)
                port_param = port_param.concat(",");
        }
        ftpCommand(CMD_PORT, port_param);

        return ssocket;
    }

    /*
     * Set reply of the last command
     */
    private void setReply(String reply)
    {
        pcs.firePropertyChange("reply",
                               this.reply,
                               reply);
        this.reply = reply;
    }

    /*
     * Set reply message and fire property change
     * @param reply The reply message to be set.
     */
    private void setReplyMessage(String replymessage)
    {
        pcs.firePropertyChange("replyMessage",
                                this.replymessage,
                                replymessage);
        this.replymessage = replymessage;
    }

    /*
     * Set account information and fire property change
     * @param acctInfo The account information
     */
    private void setAcctInfo(String acctInfo)
    {
        pcs.firePropertyChange("acctInfo",
                               this.acctInfo,
                               acctInfo);
	this.acctInfo = acctInfo;
    }

    /*
     * Set server name and fire property change
     * @param server The name of the server.
     */
    private void setServerName(String server)
    {
        pcs.firePropertyChange("serverName",
                               this.server,
                               server);
        this.server = server;
    }

    /*
     * Set user name and fire property change
     */
    private void setUserName(String user)
    {
        pcs.firePropertyChange("userName",
                               this.user,
                               user);
        this.user = user;
    }

    /*
     * Set the transfer type
     * @param ascii True for ascii transfer type
     */
    private void setTransferType(boolean ascii)
        throws IOException, FtpException
    {
        if(ascii) { ftpCommand(CMD_TYPE, TF_MOD_ASCII); }
        else { ftpCommand(CMD_TYPE, TF_MOD_BIN); }
    }

    /*
     * Replace the line separator of a text
     */
    private String changeLineSeparator(String text , String old_separator, String new_separator)
    {
        if(DEBUG)    // Debug message
            System.out.println("FtpBean: Converting ASCII format");

        if(old_separator.equals(new_separator))
            return text;
        StringBuffer content= new StringBuffer("");
        int index;
        while((index = text.indexOf(old_separator)) != -1)
        {
            content.append(text.substring(0, index)).append(new_separator);
            text = text.substring(index + old_separator.length());
        }
        if(text.length() > 0)
            content.append(text);
        return content.toString();
    }

    /*
     * Check the input string is a reply code or not
     */
    private boolean checkReply(String str)
    {
	if(str == null)
	    return true;
        // Return true if the fourth character is a space.
        if(str.length() > 3 &&
           str.charAt(3) == ' ' &&
           Character.isDigit(str.charAt(0)) &&
           Character.isDigit(str.charAt(1)) &&
           Character.isDigit(str.charAt(2)))
        {
            return false;
        } else
        {
            return true;
        }
    }

    /*
     * Read the data from the BufferedInputStream object and write to the RandomAccessFile object
     */
    private void readData(BufferedInputStream reader, RandomAccessFile out, FtpObserver observer)
        throws IOException
    {
        int offset;
        byte[] data = new byte[1024];
        
        boolean cont= true;
        while( cont && (offset = reader.read(data)) != -1)
        {
            out.write(data, 0, offset);
            if(observer != null) {
                cont= observer.byteRead(offset);
            }
        }
            
        if ( cont==false ) {
            throw new InterruptedIOException("Operation cancelled");
        }
    }

    /*
     * Write data from the RandomAccessFile object to a DataOutputStream object
     * @param din File to be read from local hard disk
     * @param dout Output stream to write content to the server.
     * @param observer FtpObserver of the uploading process.
     */
    private void writeData(RandomAccessFile din, DataOutputStream dout, FtpObserver observer)
        throws IOException
    {
        int offset;
        byte[] data = new byte[1024];
        
        boolean cont= true;
        while( cont && (offset = din.read(data)) != -1)
        {
            dout.write(data, 0, offset);

            if(observer != null) {
                cont= observer.byteWrite(offset);
            }
        }
        if ( cont==false ) {
            throw new InterruptedIOException("Operation cancelled");
        }
    }

    // Methods for thread safe.
    // All of this thread safe operation are transparent to the programmers who are using this bean.
    // All threads that want to do some ftp operation must acquire this object first.
    // Then release the object after those operation or an exception is throwed.
    // When the object is acquired by a thread, other threads that want to do ftp operation,
    // will be placed in the thread_spool. Then have rights to access this thread when the
    // previous thread is done.
    // Normally, calling the acquire() and release() methods are like this:
    //
    // acquire();
    // try
    // {
    //     // Do operation that may cause Exception here
    // } finally
    // {
    //     release();
    // }
    //
    // This can ensure the thread will release the object even an exception is threw.

    /*
     * Acquire this FtpBean object.
     * If there is a thread acquired this object already. Put itself into the thread spool.
     */
    private void acquire()
    {
        Thread thread = Thread.currentThread();
        synchronized(lock)
        {
            thread_spool.addElement(thread);    // Add thread to thread_spool
            if(DEBUG)   // Debug message
                System.out.println("Add thread to spool, size: " + thread_spool.size());
        }
        try
        {
            while(acquired && !thread_spool.elementAt(0).equals(thread))
                thread.sleep(10);
        } catch(InterruptedException e)
        {
            if(DEBUG)   // Debug message
                System.out.println("Thread interrupted");
            return;
        }
        
        acquired = true;
        if(DEBUG)    // Debug message
            System.out.println("FtpBean: Acquired by thread.");
        /* Old locking
        synchronized(lock)
        {
            
            // Loop if object is acquired by a thread
            // or there are other threads waiting in the thread_spool
            loop: while(acquired || thread_spool.size() > 0)
            {                
                if(thread_spool.contains(thread))
                {
                    if(thread_spool.elementAt(0).equals(thread) && !acquired)
                    {
                        // Object is released by previous thread
                        // And this is the first thread in the thread_spool
                        // Then break the loop.
                        thread_spool.removeElement(thread);
                        break loop;
                    } else
                        lock.notify();    // Notify other threads
                } else
                    thread_spool.addElement(thread);    // Add thread to thread_spool
                // Go to wait
                try { lock.wait(); }
                catch(Exception e) { System.out.println(e); }
                
            }
            // Acquire this object
            acquired= true;             

            if(DEBUG)    // Debug message
                System.out.println("FtpBean: Acquired by thread.");
        }
        */
    }

    /*
     * Release this FtpBean object and notify other waiting threads.
     */
    private void release()
    {
        synchronized(lock)
        {
            thread_spool.removeElementAt (0);
        }
        acquired = true;
        if(DEBUG)    // Debug message
            System.out.println("FtpBean: Released by thread.");
        /*
        synchronized(lock)
        {
            if(DEBUG)    // Debug message
                System.out.println("FtpBean: Released by thread.");

            // Release this object
            acquired = false;

            // Notify other threads
            if(thread_spool.size() > 0)
                lock.notify();
            else
                lock.notifyAll();
        }
        */
    }
}
