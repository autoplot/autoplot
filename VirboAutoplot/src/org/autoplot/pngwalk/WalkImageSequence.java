package org.autoplot.pngwalk;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;

/**
 * <p>This class maintains a list of <code>WalkImage</code>s and provides functionality
 * for navigating through it.  Whenever the current index is changed, whether explicitly
 * set or via one of the navigation functions, a <code>PropertyChangeEvent</code> is fired.
 * This allows UI code to control the index and view code to respond.</p>
 *
 * <p>Access is also provided to the {@link WalkImage} objects so that view
 * code can retrieve images, thumbnails, etc.</p>
 * 
 * @author Ed Jackson
 */
public class WalkImageSequence  {
    private List<WalkImage> images;
    //private List<URI> locations;
    //private boolean showMissing = false;
    private int index;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    //public static final String PROP_SHOWMISSING = "showMissing";
    public static final String PROP_INDEX = "index";

    /** Create an image sequence based on a URI template.
     *
     * @param template
     */
    public WalkImageSequence(String template) {
        List<DatumRange> datumRanges = new ArrayList<DatumRange>();
        List<URI> uris;
        try {
            uris = WalkUtil.getFilesFor(template, null, datumRanges, false, null);
        } catch (Exception ex) {
            Logger.getLogger(WalkImageSequence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        images = new ArrayList<WalkImage>();
        for (URI u : uris) {
            images.add(new WalkImage(u));
        }
    }

    /** Create an image sequence using an explicit list of image locations.  Images
     * in a list created via this constructor will not have any date/time information
     * associated with them.
     * 
     * @param seq
     */
    // Could we get date/time stamps from files and use those?  Do we care?
    public WalkImageSequence(List<URI> seq) {
        throw new UnsupportedOperationException("Constructor not implemented.");
    }

    public WalkImage currentImage() {
        return imageAt(index);
    }

    public WalkImage imageAt(int n) {
        if (n<0 || n>images.size()-1) {
            throw new IndexOutOfBoundsException();
        } else {
            return images.get(n);
        }
    }

//    public boolean isShowMissing() {
//        return showMissing;
//    }
//
//    public void setShowMissing(boolean showMissing) {
//        boolean oldShowMissing = this.showMissing;
//        this.showMissing = showMissing;
//        pcs.firePropertyChange(PROP_SHOWMISSING, oldShowMissing, this.showMissing);
//    }

    /** Return the current value of the index.
     * 
     * @return
     */
    public int getIndex() {
        return index;
    }

    /** Set the index explicitly.
     *
     * @param index
     */
    public void setIndex(int index) {
        if (index == this.index) {
            // do nothing and fire no event
            return;
        }
        if (index < 0 || index >= images.size()) {
            throw new IndexOutOfBoundsException();
        }
        int oldIndex = this.index;
        this.index = index;
        pcs.firePropertyChange(PROP_INDEX, oldIndex, this.index);
    }

    /** Advance the index to the next image in the list.  If the index is already
     * at the last image, do nothing.
     */
    public void next() {
        if (index < images.size() -1) {
            setIndex(index + 1);
        }
    }

    /** Step the index to the previous image in the list.  If the index is already
     * at the first image, do nothing.
     */
    public void prev() {
        if (index > 0) {
            setIndex(index - 1);
        }
    }

    /** Move the index to the first image in the list.
     * 
     */
    public void first() {
        setIndex(0);
    }

    /** Move the image to the last image in the list.
     * 
     */
    public void last() {
        setIndex(images.size()-1);
    }

    /** Skip forward or backward by the specified number of images.  Positive
     * numbers skip forward; negative skip backward.  If skipping the requested
     * number of frames would put the index out of range, the skip moves to the
     * last or first image, as appropriate.
     *
     * @param n The number of images to skip.
     */
    public void skipBy(int n) {
        if (index + n > images.size() - 1) {
            setIndex(images.size() - 1);
        } else if (index + n < 0) {
            setIndex(0);
        } else {
            setIndex(index + n);
        }
    }

    public int size() {
        return images.size();
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

}
