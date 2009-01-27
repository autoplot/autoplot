/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import javax.imageio.ImageIO;
import org.virbo.dataset.QDataSet;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class TestServlet {

    enum SyncType {

        SyncSpaced, Sync, Simultaneous, Spaced
    }    //SyncType sync= SyncType.SyncSpaced;
    //SyncType sync= SyncType.Sync;
    SyncType sync = SyncType.Simultaneous;
    //SyncType sync= SyncType.Spaced;
    final long spaceMillis = 100;
    final int requestCount = 200;
    File outputFolder = new File("/tmp/testservlet/");
    

    {
        outputFolder.mkdirs();
    }

    private interface StatusCallback {

        public void score(int id, long score);
    }

    void loadImage(final int runnumber, final String surl, final StatusCallback status) {
        Runnable run = new Runnable() {

            public void run() {
                try {

                    if (sync == SyncType.SyncSpaced || sync == SyncType.Spaced) {
                        Thread.sleep(spaceMillis);
                    }
                    URL url = new URL(surl + "&requestId=" + runnumber);

                    long t0 = System.currentTimeMillis();
                    InputStream in = url.openStream();

                    transfer(in, new FileOutputStream(new File(outputFolder, "" + runnumber + ".png")));

                    BufferedImage image = ImageIO.read(new File(outputFolder, "" + runnumber + ".png"));
                    status.score(runnumber, System.currentTimeMillis() - t0);

                    int whiteCount = 0;
                    for (int i = 0; i < image.getWidth(); i++) {
                        for (int j = 0; j < image.getHeight(); j++) {
                            whiteCount += image.getRGB(i, j) == -1 ? 1 : 0;
                        }
                    }
                    System.err.println("##" + runnumber + "#: " + whiteCount);
                    ImageIO.write(image, "png", new FileOutputStream(new File(outputFolder, "" + runnumber + ".png")));

                    if (whiteCount < 227564) System.exit(0);
                } catch (Exception ex) {
                    System.err.println("##" + runnumber + "#: Exception!!! ###");

                    ex.printStackTrace();
                    status.score(runnumber, -999);
                }

            }
        };
        if (sync == SyncType.SyncSpaced || sync == SyncType.Sync) {
            run.run();
        } else {
            new Thread(run).start();
        }
    }

    private static void transfer(InputStream in, FileOutputStream fileOutputStream) throws IOException {
        ReadableByteChannel ic = Channels.newChannel(in);
        FileChannel oc = fileOutputStream.getChannel();
        ByteBuffer buf= ByteBuffer.allocateDirect(10000);
        int read= ic.read(buf);
        while ( read>-1 ) {
            buf.flip();
            oc.write(buf);
            buf.flip();
            read= ic.read(buf);
        }
        ic.close();
        oc.close();
    }

    public static void main(String[] args) throws Exception {
        new TestServlet().doTest();
    }

    public void doTest() throws Exception {
        //String surl = "http://localhost:8080/AutoplotServlet/SimpleServlet?url=ftp%3A%2F%2Fnssdcftp.gsfc.nasa.gov%2Fspacecraft_data%2Fomni%2Fomni2_%25Y.dat%3Fcolumn%3Dfield17%26fill%3D999.9%26depend0%3Dfield0%26timeFormat%3D%25Y%2B%25j%2B%25H%26timerange%3D1976%2Bthrough%2B1977&process=&format=image%2Fpng";


        String apsurl = "file:///media/redbook/data.backup/examples/bin/binary.das2Stream.bin";
        //String surl = "http://localhost:8080/AutoplotServlet/SimpleServlet?url=ftp%3A%2F%2Fnssdcftp.gsfc.nasa.gov%2Fspacecraft_data%2Fomni%2Fomni2_%25Y.dat%3Fcolumn%3Dfield17%26fill%3D999.9%26depend0%3Dfield0%26timeFormat%3D%25Y%2B%25j%2B%25H%26timerange%3D1976%2Bthrough%2B1977&process=&font=sans-8&format=image%2Fpng&width=700&height=400&column=&row=&timeRange=&renderType=&color=&fillColor=&foregroundColor=&backgroundColor=";
        int height = 400; //(int)(Math.random()*100+400);
        int width = 700; //(int)(Math.random()*100+700);
        String surlf = "http://localhost:8080/AutoplotServlet/SimpleServlet?url=SURL&format=image%2Fpng&width=" + width + "&height=" + height;

        final DataSetBuilder builder = new DataSetBuilder(1, requestCount);
        final DataSetBuilder xbuilder = new DataSetBuilder(1, requestCount);

        for (int i = 0; i < requestCount; i++) {
            String surl2 = "file:///media/redbook/data.backup/examples/bin/binary.das2Stream.bin?byteOffset=" + (i * 16) + "&byteLength=480&type=float";
            String surl = surlf.replace("SURL", URLEncoder.encode(surl2));
            System.err.println("i: " + i + "   surl: " + surl.substring(0, 30) + "...");
            loadImage(i, surl, new StatusCallback() {

                public void score(int id, long score) {
                    builder.putValue(0, score);
                    builder.nextRecord();
                    xbuilder.putValue(0, id);
                    xbuilder.nextRecord();
                }
            });
        }

        while (builder.getLength() < requestCount) {
            Thread.sleep(100);
        }

        builder.putProperty(QDataSet.DEPEND_0, xbuilder.getDataSet());
        QDataSet result = builder.getDataSet();

        File dataFile = new File(outputFolder, "timing.qds");

        ScriptContext.formatDataSet(result, dataFile.toString());

        AutoPlotUI.main(new String[]{dataFile.toString()});
    }
}
