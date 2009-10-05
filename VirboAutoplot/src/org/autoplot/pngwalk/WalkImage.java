package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.io.File;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.virbo.datasource.URISplit;

/**
 * A class to manage an image for the PNGWalk tool. Handles downloading and
 * thumbnail generation.
 * 
 * @author Ed Jackson
 */
public class WalkImage implements Comparable<WalkImage>, ImageObserver {

    public static final BufferedImage LOADING_IMAGE = initLoadingImage();

    final String uriString;  // Used for sorting
    private URI imgURI;
    private BufferedImage im;
    private BufferedImage thumb;
    private int thumbSize = 0;
    private String caption;
    private Status  status;

    public URI getUri() {
        return imgURI;
    }

    public enum Status {
        UNKNOWN,
        LOADING,
        LOADED,
        MISSING,
        ERROR;
    }

    public WalkImage(URI uri) {
        imgURI = uri;
        uriString = uri.toString();
        status = Status.UNKNOWN;
        // image and thumbnail are initialized lazily
    }

    public BufferedImage getImage() {
        if (im == null && status != Status.LOADING) {
            loadImage();
        }
        return im;
    }

    public BufferedImage getThumbnail(int size) {
        // check validity of current thumbnail, i.e. it exists at correct size
        // if not valid, create it from the image

        if (thumbSize != size) {
            if(im == null) loadImage();
            double aspect = (double)im.getWidth()/(double)im.getHeight();

            int height = (int)Math.round(Math.sqrt((size*size)/(aspect*aspect + 1)));
            int width = (int)Math.round(height*aspect);
    
            BufferedImageOp resizeOp = new ScalePerspectiveImageOp(im.getWidth(), im.getHeight(), 0, 0, width, height, 0, 1, 1, 0, false);
            thumb = resizeOp.filter(im, null);

            thumbSize = size;
        }
        //temporarily just return the image
        return thumb;
    }

    private void loadImage() {
        //TODO: Put image loading in a different thread so this method doesn't block on slow sites
        try {
            //Image tkImage;

            URI fsRoot = new URI(URISplit.parse(imgURI.toString()).path);
            FileSystem fs = FileSystem.create(fsRoot);

            String s = imgURI.toString();
            FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/')+1));

            File localFile = fo.getFile();

            im = ImageIO.read(localFile);
            
        } catch (Exception ex) {
            System.err.println("Error loading image file from " + imgURI.toString());
            Logger.getLogger(WalkImage.class.getName()).log(Level.SEVERE, null, ex);
            status = Status.MISSING;
            throw new RuntimeException(ex);
        }
        status = Status.LOADING;
    }

    private static BufferedImage initLoadingImage() {
        BufferedImage li = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = li.createGraphics();
        g2.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
        g2.fillRoundRect(0, 0, 100, 100, 10, 10);
        //TODO: Add text or hourglass or something
        return li;
    }

    // Implements ImageObserver
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        if ((infoflags & ImageObserver.ALLBITS) != 0) {
            //im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            //im.getGraphics().drawImage(img, 0, 0, null);
            status = Status.LOADED;
            return false;
        }
        return true;
    }

    // Implementing the Comparable interface lets List sort
    //TODO: Compare on date information first
    public int compareTo(WalkImage o) {
        return uriString.compareTo(o.uriString);
    }
}
