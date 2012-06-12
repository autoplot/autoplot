package ftpfs.ftp;

/*
 * FtpBean
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
 * Class: FtpObserver
 * Author: Calvin
 * Date: 25 March 2000
 * Last Updated: 10 April 2000
 *
 * 20120612--add cancel so user can cancel the download.
 * 
 * Updates:
 * This interface was added on version 1.4
 */

/**
 * The class that implement this interface have the ability to monitor the progress 
 * of upload and download files in the FtpBean.<br>
 * You can pass the object which implement this interface to some put/get methods 
 * of the FtpBean object. So that when there are any bytes read from the server, 
 * the byteRead(int) method of the object you passed is invoked. And the byteWrite(int) 
 * method is invoked when any bytes is written to the server side.<br>
 * A sample code is like this:
 * <font size="2">
 * <pre>
 * // Begin, this class implements the FtpObserver interface
 * class Sample implements FtpObserver
 * {
 *
 *     // Skip constructors and many things for simple
 *
 *     public void download()
 *     {
 *         try 
 *         {
 *             // Pass this object which implements FtpObserver interface to the method
 *             ftpbean.getBinaryFile("remotefile", "localfile", this);
 *         } catch(Exception e)
 *         {
 *             System.out.println("Exception!!!");
 *         }
 *     }
 *
 *     public void byteRead(int bytes)
 *     {
 *         System.out.println(bytes + " new bytes are read.");
 *     }
 * 
 *     public void byteWrite(int bytes)
 *     {
 *         System.out.println(bytes + " new bytes are written to server.");
 *     }
 * }
 * </pre>
 * </font>
 *
 */
public interface FtpObserver
{
    /**
     * This method is called every time new bytes are read in downloading process.
     * @param bytes The number of new bytes read from the server.
     * @return true if the process should continue reading, false indicates cancel and the process should stop
     */
    boolean byteRead(int bytes);

    /**
     * This method is called every time new bytes is written to the ftp server in uploading process.
     * @param bytes The number of new bytes write to the server.
     */
    boolean byteWrite(int bytes);
}
