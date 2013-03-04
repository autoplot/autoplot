/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeBodyPart;
import javax.activation.DataHandler;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

/**
 * send email via localhost instead of posting to server.
 *
 * @author jbf
 */
public class SendEmailFeedback {

    protected static void sendEmail(String report) throws AddressException, MessagingException {
        // demo send mail.
        String to = "faden@cottagesystems.com";

        //to = "feedback@delta.physics.uiowa.edu"
        String frm = "autoplot-errors@cottagesystems.com";

        Properties props = new java.util.Properties();

        //  props.put("mail.smtp.host", 'mercury.physics.uiowa.edu')
        props.put("mail.smtp.host", "localhost");
        props.put("mail.debug", "true");

        Session session = Session.getInstance(props);

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(frm));
        InternetAddress[] address = new InternetAddress[]{new InternetAddress(to)};
        msg.setRecipients(Message.RecipientType.TO, address);
        msg.setSubject("[errorReport]");
        msg.setSentDate( new java.util.Date() );

        // Set message content

        MimeMultipart multipart = new MimeMultipart();

        MimeBodyPart messageBodyPart = new MimeBodyPart();

        messageBodyPart.setDataHandler( new DataHandler( report, "text/xml") );
        multipart.addBodyPart(messageBodyPart);

        msg.setContent(multipart);

        // Send the message
        Transport.send(msg);

    }
}
