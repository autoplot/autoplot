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
 * Class Name: FtpException
 * Author: Calvin Tai
 * Date: 20 Aug 1999
 * Last Updated: 20 Aug 1999
 * 
 * Updates:
 * This class was added on version 1.0
 */

/**
 * When there are any FTP command fails in the FTP bean,<br>
 * This exception is throwed.
 */

public class FtpException extends Exception
{
    public FtpException(String message)
    {
        super(message);
    }
}
