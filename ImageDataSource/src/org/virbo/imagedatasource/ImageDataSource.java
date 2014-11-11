/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.imagedatasource;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.das2.util.ImageUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.dsops.Ops;
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

    public ImageDataSource(URI uri) {
        super(uri);
    }

    private double toHSV(int rgb, int channel) {
        double r = (rgb & 0xFF0000) >> 16;
        double g = (rgb & 0x00FF00) >> 8;
        double b = (rgb &     0xFF);

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

        File ff= DataSetURI.getFile(uri, mon);
        if ( ff.length()==0 ) {
            throw new IllegalArgumentException("Image file is empty: "+ff);
        }
        
        BufferedImage image = ImageIO.read(ff);
        //BufferedImage image = ImageIO.read(DataSetURI.getInputStream(uri, mon));
 
        String rot= getParam( "rotate", "0" );
        if ( !rot.equals("0") ) {

            int h= image.getHeight();
            int w= image.getWidth();

            double drot= Double.parseDouble(rot);

            AffineTransform at= AffineTransform.getTranslateInstance( w/2., h/2. );
            at.concatenate( AffineTransform.getRotateInstance( Math.PI*drot/180 ) );
            at.concatenate( AffineTransform.getTranslateInstance( -w/2., -h/2.) );

            BufferedImage dest= new BufferedImage( w, h, image.getType() );

            ((Graphics2D)dest.getGraphics()).drawImage( image,at, null );

            image= dest;
        }
        
        String blur= getParam( "blur", "1" );
        if ( !blur.equals("1") ) {
            int iblur= Integer.parseInt(blur);
            if ( iblur<1 || iblur>51 ) throw new IllegalArgumentException("blur must be between 1 and 51");
            BufferedImage dest= new BufferedImage( image.getWidth(), image.getHeight(), image.getType() );
            int n= iblur*iblur;
            float[] matrix= new float[n];
            for ( int i=0; i<matrix.length; i++) matrix[i]= 1.0f/n;

            BufferedImageOp op = new ConvolveOp( new Kernel( iblur, iblur, matrix) );
            BufferedImage blurredImage = op.filter( image, dest );

            image= blurredImage;
        }
        
        String fog= getParam( "fog", "0" );
        if ( !fog.equals("0") ) {
            int ifog= Integer.parseInt(fog);
            if ( ifog<0 || ifog>100 ) throw new IllegalArgumentException("fog must be between 1 and 100");
            BufferedImage dest= new BufferedImage( image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB );
            
            int color= image.getRGB(0,0);
            
            Graphics2D g= ((Graphics2D)dest.getGraphics());
            g.drawImage( image, new AffineTransform(), null );
            g.setColor( new Color( ( color & 0xFF0000 ) >> 16, ( color & 0x00FF00)  >> 8, color & 0x0000FF, ifog*255/100 ) );
            g.fillRect(0,0,image.getWidth(), image.getHeight());
            
            image= dest;
        }
        
    
        String channel = params.get("channel");

        Color c = null;
        ImageDataSet.ColorOp op = null;

        if (channel != null) {
            if (channel.equals("red")) {
                c = Color.red;
            } else if (channel.equals("green")) {
                c = Color.green;
            } else if (channel.equals("blue")) {
                c = Color.blue;
            } else if (channel.equals("alpha")) {
                if ( image.getSampleModel().getNumBands()<4 ) {
                    throw new IllegalArgumentException("this image has less than three bands, which is interpretted to mean no alpha");
                }
                DDataSet ds= DDataSet.createRank2(image.getWidth(), image.getHeight() );
                for ( int i=0; i<ds.length(); i++ ) {
                    for ( int j=0; j<ds.length(0); j++ ) {
                        ds.putValue(i, j, image.getAlphaRaster().getSample( i,j,0 ) );
                    }
                }
                ds.putProperty( QDataSet.LABEL, "alpha" );
                return ds;
            } else if (channel.equals("greyscale")) {
                op = new ImageDataSet.ColorOp() {

                    public double value(int rgb) {
                        int r = rgb & 0xFF0000 >> 16;
                        int g = rgb & 0x00FF00 >> 8;
                        int b = rgb &     0xFF;
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
            } else {
                throw new IllegalArgumentException("unsupported channel: "+channel );
            }
        }

        ImageDataSet result = new ImageDataSet(image, c, op);

        if ( channel==null ) {
            result.putProperty( QDataSet.RENDER_TYPE, "image" );
        }
        
        String xaxis= getParam( "xaxis", null );
        if ( xaxis!=null ) {
            double[] xform= tryParseArray( xaxis );
            QDataSet xx= Ops.findgen(result.length());
            xx= Ops.subtract( xx, xform[1] );
            xx= Ops.multiply( xx, (xform[2]-xform[0])/(xform[3]-xform[1]) );
            xx= Ops.add( xx, xform[0] );
            ((MutablePropertyDataSet)xx).putProperty( QDataSet.TYPICAL_MIN,xform[0]);
            ((MutablePropertyDataSet)xx).putProperty( QDataSet.TYPICAL_MAX,xform[2]);
            result.putProperty( QDataSet.DEPEND_0, xx );
        }
        String yaxis= getParam( "yaxis", null );
        if ( yaxis!=null ) {
            double[] xform= tryParseArray( yaxis );
            QDataSet yy= Ops.findgen(result.length(0));
            yy= Ops.subtract( yy, xform[1] );
            yy= Ops.multiply( yy, (xform[2]-xform[0])/(xform[3]-xform[1]) );
            yy= Ops.add( yy, xform[0] );
            ((MutablePropertyDataSet)yy).putProperty( QDataSet.TYPICAL_MIN,xform[0]);
            ((MutablePropertyDataSet)yy).putProperty( QDataSet.TYPICAL_MAX,xform[2]);
            result.putProperty( QDataSet.DEPEND_1, yy );
        }
        
        String plotInfo= getParam( "plotInfo", "" );
        if ( !plotInfo.equals("") ) {
            String json= ImageUtil.getJSONMetadata(ff);
            if ( json!=null ) {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                JSONObject plot= plots.getJSONObject( Integer.parseInt(plotInfo) );
                
                JSONObject x= plot.getJSONObject("xaxis");
                QDataSet xx= Ops.findgen(result.length());
                double dxmin= x.getDouble("min");
                double dxmax= x.getDouble("max");
                boolean xlog= x.has("type") && x.get("type").equals("log");
                if ( xlog ) dxmin= Math.log10(dxmin);
                if ( xlog ) dxmax= Math.log10(dxmax);
                xx= Ops.subtract( xx, x.getDouble("left") );
                xx= Ops.multiply( xx, ( dxmax-dxmin ) / ( x.getInt("right") -x.getInt("left") ) );
                xx= Ops.add( xx, dxmin );
                if ( xlog ) xx= Ops.exp10(xx);
                ((MutablePropertyDataSet)xx).putProperty( QDataSet.TYPICAL_MIN,dxmin );
                ((MutablePropertyDataSet)xx).putProperty( QDataSet.TYPICAL_MAX,dxmax );
                result.putProperty( QDataSet.DEPEND_0, xx );

                JSONObject y= plot.getJSONObject("yaxis");
                QDataSet yy= Ops.findgen(result.length(0));
                double dymin= y.getDouble("min");
                double dymax= y.getDouble("max");
                boolean ylog= y.has("type") && y.get("type").equals("log");
                if ( ylog ) dymin= Math.log10(dymin);
                if ( ylog ) dymax= Math.log10(dymax);
                yy= Ops.subtract( yy, y.getDouble("top") );
                yy= Ops.multiply( yy, ( dymax-dymin ) / ( y.getInt("bottom") -y.getInt("top") ) );
                yy= Ops.add( yy, dymin );
                if ( ylog ) yy= Ops.exp10(yy);
                ((MutablePropertyDataSet)yy).putProperty( QDataSet.TYPICAL_MIN,dymin );
                ((MutablePropertyDataSet)yy).putProperty( QDataSet.TYPICAL_MAX,dymax );
                result.putProperty( QDataSet.DEPEND_1, yy );
            }
        }
               
        mon.finished();

        return result;

    }

    public double[] tryParseArray( String s ) {
        s= s.trim();
        if ( s.startsWith("[") && s.endsWith("]") ) s= s.substring(1,s.length()-1);
        String[] ss= s.split(",");
        double[] result= new double[ss.length];
        for ( int i=0; i<result.length; i++ ) {
            result[i]= Double.parseDouble(ss[i]);
        }
        return result;
    }
    /**
     * read useful JPG metadata, such as the Orientation.  This also looks to see if GPS
     * metadata is available.
     * @param mon
     * @return
     * @throws Exception
     */
    public Map<String, Object> getJpegExifMetaData(ProgressMonitor mon) throws Exception {
        InputStream in = DataSetURI.getInputStream(uri, mon);
        Metadata metadata = JpegMetadataReader.readMetadata(in);

        Map<String, Object> map = new LinkedHashMap<String, Object>();

        Directory exifDirectory;

        exifDirectory = metadata.getDirectory(ExifSubIFDDirectory.class);
        if ( exifDirectory!=null ) {
            for (Tag t : exifDirectory.getTags()) {
                map.put(t.getTagName(), t.getDescription());
            }
        }

        exifDirectory = metadata.getDirectory(ExifIFD0Directory.class);
        if ( exifDirectory!=null ) {
            for (Tag t : exifDirectory.getTags()) {
                map.put(t.getTagName(), t.getDescription());
            }
        }

        exifDirectory = metadata.getDirectory(GpsDirectory.class);
        if ( exifDirectory!=null ) {
            for (Tag t : exifDirectory.getTags()) {
                map.put(t.getTagName(), t.getDescription());
            }
        }

        return map;
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {

        String ext = getExt(resourceURI).toLowerCase();

        if (ext.equals(".jpg")) {
            return getJpegExifMetaData(mon);

        } else {

            File f = DataSetURI.getFile(uri, new NullProgressMonitor());

            ImageReader jpegImageReader = ImageIO.getImageReadersByFormatName(ext.substring(1)).next();
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(f);
            boolean seekForwardOnly = true;
            boolean ignoreMetadata = false;

            jpegImageReader.setInput(imageInputStream, seekForwardOnly, ignoreMetadata);
            IIOMetadata imageMetadata = jpegImageReader.getImageMetadata(0);

            Node metaDataRoot = imageMetadata.getAsTree(imageMetadata.getNativeMetadataFormatName());

            Map<String, Object> map;
            map = MetadataUtil.toMetaTree(metaDataRoot);

            return map;
        }
    }
}
