package Hayfevrly.Model;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import java.io.*;
import java.util.Date;

public class Email {


    public static void getMessageBody(Part p, StringBuilder sb, String desiredMimeType) throws Exception {

        if (p instanceof Message) {
            //Call method writeEnvelope
//            Email.writeEnvelope((Message) p);
        }

//        System.out.println("----------------------------");
//        System.out.println("CONTENT-TYPE: " + p.getContentType());

        //check if the content is plain text
        if (p.isMimeType("text/plain")) {
            if (desiredMimeType.equals("text/plain")) {
                sb.append((String) p.getContent());
            }
//            System.out.println("This is plain text");
//            System.out.println("---------------------------");
//            System.out.println((String) p.getContent());
        } else if (p.isMimeType("text/html")) {
            // don't display the html
//            System.out.println("This is html text");
//            System.out.println("---------------------------");
//            System.out.println((String) p.getContent());
        } else if (p.isMimeType("multipart/*")) {
//            System.out.println("This is a Multipart");
//            System.out.println("---------------------------");
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                getMessageBody(mp.getBodyPart(i), sb, desiredMimeType);
            }
        }
        //check if the content is a nested message
        else if (p.isMimeType("message/rfc822")) {
//            System.out.println("This is a Nested Message");
//            System.out.println("---------------------------");
            getMessageBody((Part) p.getContent(), sb, desiredMimeType);
        }
        //check if the content is an inline image
        else if (p.isMimeType("image/jpeg")) {
            System.out.println("--------> image/jpeg");
            Object o = p.getContent();

            InputStream x = (InputStream) o;
            // Construct the required byte array
            System.out.println("x.length = " + x.available());
            int i = 0;
            byte[] bArray = new byte[x.available()];
            while ((i = x.available()) > 0) {
                int result = x.read(bArray);
                if (result == -1) {
                    break;
                }
            }
            FileOutputStream f2 = new FileOutputStream("/tmp/image.jpg");
            f2.write(bArray);
        } else if (p.getContentType().contains("image/")) {
            System.out.println("content type" + p.getContentType());
            File f = new File("image" + new Date().getTime() + ".jpg");
            DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(f)));
            com.sun.mail.util.BASE64DecoderStream test =
                    (com.sun.mail.util.BASE64DecoderStream) p
                            .getContent();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = test.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } else {
            Object o = p.getContent();
            if (o instanceof String) {
                System.out.println("This is a string");
                System.out.println("---------------------------");
                System.out.println((String) o);
            } else if (o instanceof InputStream) {
                System.out.println("This is just an input stream");
                System.out.println("---------------------------");
                InputStream is = (InputStream) o;
                int c;
                while ((c = is.read()) != -1) {
                    System.out.write(c);
                }
            } else {
                System.out.println("This is an unknown type");
                System.out.println("---------------------------");
                System.out.println(o.toString());
            }
        }

    }


    /*
     * This method would print FROM,TO and SUBJECT of the message
     */
    public static void writeEnvelope(Message m) throws Exception {
        System.out.println("This is the message envelope");
        System.out.println("---------------------------");
        Address[] a;

        // FROM
        if ((a = m.getFrom()) != null) {
            for (int j = 0; j < a.length; j++)
                System.out.println("FROM: " + a[j].toString());
        }

        // TO
        if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
            for (int j = 0; j < a.length; j++)
                System.out.println("TO: " + a[j].toString());
        }

        // SUBJECT
        if (m.getSubject() != null)
            System.out.println("SUBJECT: " + m.getSubject());

    }


}
