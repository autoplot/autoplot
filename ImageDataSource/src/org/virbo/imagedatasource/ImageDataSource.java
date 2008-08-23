/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.imagedatasource;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifDirectory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.metatree.MetadataUtil;
import org.w3c.dom.Node;

/**
 *
 * @author jbf
 */
class ImageDataSource extends AbstractDataSource {

    public static final int CHANNEL_HUE = 1;
    public static final int CHANNEL_SATURATION = 2;
    public static final int CHANNEL_VALUE = 3;

    public ImageDataSource(URL url) {
        super(url);
    }

    private final double toHSV(int rgb, int channel) {
        double r = (rgb & 0xFF0000) >> 16;
        double g = (rgb & 0x00FF00) >> 8;
        double b = (rgb & 0x0000FF) >> 0;

        r = r / 255;
        g = g / 255;
        b = b / 255; // Scale to unity.

        double minVal = Math.min(Math.min(r, g), b);
        double maxVal = Math.max(Math.max(r, g), b);
        double delta = maxVal - minVal;

        double value = maxVal;

        if (channel == CHANNEL_VALUE) {
            return value * 100;
        }
        double hue = 0, sat;

        if (delta == 0) {
            hue = 0;
            sat = 0;
        } else {
            sat = delta / maxVal;
            double del_R = (((maxVal - r) / 6) + (delta / 2)) / delta;
            double del_G = (((maxVal - g) / 6) + (delta / 2)) / delta;
            double del_B = (((maxVal - b) / 6) + (delta / 2)) / delta;

            if (r == maxVal) {
                hue = del_B - del_G;
            } else if (g == maxVal) {
                hue = (1 / 3) + del_R - del_B;
            } else if (b == maxVal) {
                hue = (2 / 3) + del_G - del_R;
            }

            if (hue < 0) {
                hue += 1;
            }
            if (hue > 1) {
                hue -= 1;
            }
            hue *= 360;
            sat *= 100;
        }

        if (channel == CHANNEL_HUE) {
            return hue;
        } else {
            return sat;
        }

    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();

        //BufferedImage image = ImageIO.read(DataSetURL.getFile(url, mon));
        BufferedImage image = ImageIO.read(DataSetURL.getInputStream(url, mon));

        String channel = params.get("channel");

        Color c = null;
        ImageDataSet.ColorOp op = null;

        if ( channel==null ) throw new IllegalArgumentException("channel not specified");
        if (channel.equals("red")) {
            c = Color.red;
        } else if (channel.equals("green")) {
            c = Color.green;
        } else if (channel.equals("blue")) {
            c = Color.blue;
        } else if (channel.equals("greyscale")) {
            op = new ImageDataSet.ColorOp() {

                public double value(int rgb) {
                    int r = rgb & 0xFF0000 >> 16;
                    int g = rgb & 0x00FF00 >> 8;
                    int b = rgb & 0x0000FF >> 0;
                    return 0.3 * r + 0.59 * g + 0.11 * b;
                }
            };
        } else if (channel.equals("hue")) {
            op = new ImageDataSet.ColorOp() {

                public double value(int rgb) {
                    return toHSV(rgb, CHANNEL_HUE);
                }
            };
        } else if (channel.equals("saturation")) {
            op = new ImageDataSet.ColorOp() {

                public double value(int rgb) {
                    return toHSV(rgb, CHANNEL_SATURATION);
                }
            };
        } else if (channel.equals("value")) {
            op = new ImageDataSet.ColorOp() {

                public double value(int rgb) {
                    return toHSV(rgb, CHANNEL_VALUE);
                }
            };
        }

        ImageDataSet result = new ImageDataSet(image, c, op);

        mon.finished();

        return result;

    }

    public Map<String, Object> getJpegExifMetaData( ProgressMonitor mon) throws Exception {
        InputStream in= DataSetURL.getInputStream( url, mon );
        Metadata metadata = JpegMetadataReader.readMetadata(in);

        Map<String, Object> map = new HashMap<String, Object>();

        Directory exifDirectory = metadata.getDirectory(ExifDirectory.class);

        for (Iterator i = exifDirectory.getTagIterator(); i.hasNext();) {
            Tag t = (Tag) i.next();

            map.put(t.getTagName(), t.getDescription());
        }

        return map;
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        
        String ext= getExt(resourceURL);
        
        if ( ext.equals(".jpg") ) {
            return getJpegExifMetaData(mon);
            
        } else {

            File f = DataSetURL.getFile(url, new NullProgressMonitor());

            Map<String, Object> map = new HashMap<String, Object>();

            ImageReader jpegImageReader = ImageIO.getImageReadersByFormatName(ext.substring(1)).next();
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(f);
            boolean seekForwardOnly = true;
            boolean ignoreMetadata = false;

            jpegImageReader.setInput(imageInputStream, seekForwardOnly, ignoreMetadata);
            IIOMetadata imageMetadata = jpegImageReader.getImageMetadata(0);

            Node metaDataRoot = imageMetadata.getAsTree(imageMetadata.getNativeMetadataFormatName());

            map = MetadataUtil.toMetaTree(metaDataRoot);

            return map;
        }
    }
}
