package org.autoplot.pngwalk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.virbo.autoplot.dom.DebugPropertyChangeSupport;

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
public class WalkImageSequence implements PropertyChangeListener  {
    private List<WalkImage> images;
    //private List<URI> locations;
    private boolean showMissing = false;
    private int index;

    private DatumRange timeSpan = null;
    private List<DatumRange> datumRanges = null;

    /**
     * template used to create list.  This may be null.
     */
    private String template;

    private final PropertyChangeSupport pcs = new DebugPropertyChangeSupport(this);

    //public static final String PROP_SHOWMISSING = "showMissing";
    public static final String PROP_INDEX = "index";
    public static final String PROP_IMAGE_LOADED = "imageLoaded";
    public static final String PROP_THUMB_LOADED = "thumbLoaded";
    public static final String PROP_SEQUENCE_CHANGED = "sequenceChanged";

    /** Create an image sequence based on a URI template.
     *
     * @param template a template, or null will produce an empty walk sequence.
     */
    public WalkImageSequence( String template ) {
        this.template= template;
        //call initialLoad before any other methods.
    }

    /**
     * do the initial listing of the remote filesystem.  This should not
     * be done on the event thread, and should be done before the
     * sequence is used.
     */
    public void initialLoad() { 
        datumRanges = new ArrayList<DatumRange>();
        List<URI> uris;

        if ( template==null ) {
            uris= new ArrayList<URI>();
        } else {
            try {
                setStatus( "busy: listing "+template );
                uris = WalkUtil.getFilesFor(template, null, datumRanges, false, null);
                setStatus( "done listing "+template );
            } catch (Exception ex) {
                Logger.getLogger(WalkImageSequence.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        }

        //if ( uris.size()>20 ) {uris= uris.subList(0,30); }

        images = new ArrayList<WalkImage>();
        for (int i=0; i < uris.size(); i++) {
            images.add(new WalkImage(uris.get(i)));
            //System.err.println(i + ": " + datumRanges.get(i));

            String captionString;
            if (datumRanges.get(i) != null) {
                captionString = datumRanges.get(i).toString();
            } else {
                captionString = uris.get(i).getPath();
                captionString = captionString.substring(captionString.lastIndexOf('/')+1);
            }

            images.get(i).setCaption(captionString);
        }

        //DatumRange span = null;
        for (DatumRange dr : datumRanges) {
            if (timeSpan == null)
                timeSpan = dr;
            else
                timeSpan = DatumRangeUtil.union(timeSpan, dr);
        }

        for (WalkImage i : images) {
            i.addPropertyChangeListener(this);
        }
    }

//commented until questions are resolved.
//    /** Create an image sequence using an explicit list of image locations.  Images
//     * in a list created via this constructor will not have any date/time information
//     * associated with them.
//     *
//     * @param seq
//     */
//    // Could we get date/time stamps from files and use those?  Do we care?
//    public WalkImageSequence(List<URI> seq) {
//        throw new UnsupportedOperationException("Constructor not implemented.");
//    }

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

    public boolean isShowMissing() {
        return showMissing;
    }

    public void setShowMissing(boolean showMissing) {
        if (showMissing != this.showMissing) {
            this.showMissing = showMissing;

            //Bogus property has no meaningful value
            pcs.firePropertyChange(PROP_SEQUENCE_CHANGED, false, true);
        }
    }

    /** Return the time range covered by this sequence.  This is the total range
     * of available images, not any currently displayed subrange.  Will be null
     * if no date template was used to create the sequence.
     *
     * @return
     */
    public DatumRange getTimeSpan() {
        return timeSpan;
    }


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

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }


    public String getTemplate() {
        return this.template;
    }

    protected String status = "idle";
    public static final String PROP_STATUS = "status";

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        String oldStatus = this.status;
        this.status = status;
        pcs.firePropertyChange(PROP_STATUS, oldStatus, status);
    }

    // Get status changes from the images in the list
    public void propertyChange(PropertyChangeEvent e) {
        if ((WalkImage.Status)e.getNewValue() == WalkImage.Status.IMAGE_LOADED ||
                (WalkImage.Status)e.getNewValue() == WalkImage.Status.THUMB_LOADED)  {
            int i = images.indexOf(e.getSource());
            if (i == -1) {
                //panic because something is very very wrong
                throw new RuntimeException("Status change from unknown image object");
            }
            // imageLoaded is a bogus property so there's no old value
            // passing an illegal negative value in its place assures event is always fired
            pcs.firePropertyChange(PROP_THUMB_LOADED, -1, i);
            if ((WalkImage.Status)e.getNewValue() == WalkImage.Status.IMAGE_LOADED)
                pcs.firePropertyChange(PROP_IMAGE_LOADED, -1, i);
        }
        
        int loadingCount=0;
        int loadedCount=0;
        int thumbLoadingCount=0;
        int thumbLoadedCount=0;
        for ( WalkImage i : images ) {
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADING ) loadingCount++;
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADED ) loadedCount++;
            if ( i.getStatus()==WalkImage.Status.THUMB_LOADING ) thumbLoadingCount++;
            if ( i.getStatus()==WalkImage.Status.THUMB_LOADED ) thumbLoadedCount++;
        }

/*        if ( loadingCount>5 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }

        if ( thumbLoadingCount>30 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }*/
        
        if ( loadingCount==0 && thumbLoadingCount==0) {
            setStatus(""+loadedCount+" images loaded.");
        } else {
            setStatus("busy: "+loadedCount+" images loaded, " + loadingCount + " are loading and "+ thumbLoadingCount + " thumbs are loading.  ");
        }
    }

}
