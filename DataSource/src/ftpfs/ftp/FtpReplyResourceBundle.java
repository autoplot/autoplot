package ftpfs.ftp;

import java.util.ListResourceBundle;

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
 * Updates:
 * version 1.4.5 - 28 Mar 2002
 * 1) Added this class.
 */

/**
 * This class is used to store the valid reply code for various ftp commands.
 */
class FtpReplyResourceBundle extends ListResourceBundle
{
    public Object[][] getContents()
    {
	return cmdGrps;
    }

    static final Object[][] cmdGrps = {
	{ FtpBean.FTP_INIT, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_ACCT, new String[] { FtpBean.REPLY_POS_CMP } },
        { FtpBean.CMD_APPE, new String[] { FtpBean.REPLY_POS_PRE, FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_CDUP, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_CWD , new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_DELE, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_LIST, new String[] { FtpBean.REPLY_POS_PRE, FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_MKD , new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_PASV, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_PASS, new String[] { FtpBean.REPLY_POS_CMP, FtpBean.REPLY_POS_INT } },
	{ FtpBean.CMD_PORT, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_PWD , new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_QUIT, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_RETR, new String[] { FtpBean.REPLY_POS_PRE, FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_RNFR, new String[] { FtpBean.REPLY_POS_INT } },
	{ FtpBean.CMD_RNTO, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_REST, new String[] { FtpBean.REPLY_POS_INT } },
	{ FtpBean.CMD_RMD , new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_SITE, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_STOR, new String[] { FtpBean.REPLY_POS_PRE, FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_SYST, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_TYPE, new String[] { FtpBean.REPLY_POS_CMP } },
	{ FtpBean.CMD_USER, new String[] { FtpBean.REPLY_POS_INT, FtpBean.REPLY_POS_CMP } }
    };
}
