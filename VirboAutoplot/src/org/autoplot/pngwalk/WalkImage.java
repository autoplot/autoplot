package org.autoplot.pngwalk;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    final String uriString;  // Used for sorting
    private URI imgURI;
    private Image im;
    private Image thumb;
    private String caption;
    private Status  status;

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

    public Image getImage() {
        if (im == null && status != Status.LOADING) {
            loadImage();
        }
        return im;
    }

    public Image getThumbnail() {
        // check validity of current thumbnail, i.e. it exists at correct size
        // if not valid, create it from the image
        if (im == null) {
            //loadImage();
        }
        // create thumb

        //return thumb;
        throw new UnsupportedOperationException("Method not implemented.");
    }

    private void loadImage() {
        try {
            URI fsRoot = new URI(URISplit.parse(imgURI.toString()).path);
            FileSystem fs = FileSystem.create(fsRoot);

            String s = imgURI.toString();
            FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/')+1));

            File localFile = fo.getFile();
            im = Toolkit.getDefaultToolkit().createImage(localFile.getPath());

            // Do this to register us as an ImageObserver:
            im.getWidth(this);

        } catch (Exception ex) {
            System.err.println("Error loading image file from " + imgURI.toString());
            Logger.getLogger(WalkImage.class.getName()).log(Level.SEVERE, null, ex);
            status = Status.MISSING;
            throw new RuntimeException(ex);
        }
        status = Status.LOADING;
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
    public int compareTo(WalkImage o) {
        return uriString.compareTo(o.uriString);
    }
}
