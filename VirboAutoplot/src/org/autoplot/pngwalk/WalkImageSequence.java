package org.autoplot.pngwalk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.util.filesystem.FileSystem;
import org.virbo.autoplot.dom.DebugPropertyChangeSupport;
import org.virbo.datasource.DataSetURI;

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
    private List<WalkImage> existingImages;
    private List<WalkImage> displayImages = new ArrayList();
    //private List<URI> locations;
    private boolean showMissing = false;
    private boolean useSubRange = false;
    private int index;

    private DatumRange timeSpan = null;
    private List<DatumRange> datumRanges = null;
    private List<DatumRange> possibleRanges = null;
    private List<DatumRange> subRange = null;

    /**
     * template used to create list.  This may be null.
     */
    private String template;

    private final PropertyChangeSupport pcs = new DebugPropertyChangeSupport(this);

    //public static final String PROP_SHOWMISSING = "showMissing";
    public static final String PROP_INDEX = "index";
    public static final String PROP_SELECTED_INDECES = "selectedIndeces";
    public static final String PROP_IMAGE_LOADED = "imageLoaded";
    public static final String PROP_THUMB_LOADED = "thumbLoaded";
    public static final String PROP_USESUBRANGE = "useSubRange";
    public static final String PROP_SEQUENCE_CHANGED = "sequenceChanged";
    public static final String PROP_BADGE_CHANGE = "badgeChange";  // For quality control icon badge

    private URI qcFolder = null;                 //Location for quality control files, if used
    private QualityControlSequence qualitySeq;

    private boolean haveThumbs400=true;

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
    public void initialLoad() throws java.io.IOException {
        datumRanges = new ArrayList<DatumRange>();
        subRange = new ArrayList<DatumRange>();
        List<URI> uris;

        if ( template==null ) {
            throw new IllegalStateException("template was null");
        } else {
            try {
                setStatus( "busy: listing "+template );
                uris = WalkUtil.getFilesFor(template, null, datumRanges, false, null);
                if ( uris.size()>0 ) {
                    setStatus( "Done listing "+template );
                } else {
                    setStatus( "warning: Done listing "+template+", and no files were found" );
                }
            } catch (Exception ex) {
                Logger.getLogger(WalkImageSequence.class.getName()).log(Level.SEVERE, null, ex);
                setStatus("error: Error listing " + template+", "+ex.getMessage() );
                throw new java.io.IOException("Error listing "  + template+", "+ex.getMessage() );
            }
        }

        if ( template.equals("file:///") ) {
            haveThumbs400= false;

        } else {

            int splitIndex= WalkUtil.splitIndex( template );

            URI fsRoot;
            fsRoot = DataSetURI.getResourceURI( template.substring(0,splitIndex) );

            try {
                FileSystem fs= FileSystem.create( fsRoot );
                if ( fs.getFileObject("/thumbs400/").exists() ) {
                    String[] result= fs.listDirectory("/thumbs400/");
                    if ( result.length<2 ) {
                        haveThumbs400= false; //TODO: kludge, I expected IOException when dir doesn't exist.
                    }
                } else {
                    haveThumbs400= false;
                }
            } catch ( IOException ex ) {
                haveThumbs400= false;
            }
        }

        //if ( uris.size()>20 ) {uris= uris.subList(0,30); }

        existingImages = new ArrayList<WalkImage>();
        for (int i=0; i < uris.size(); i++) {
            existingImages.add(new WalkImage(uris.get(i),haveThumbs400));
            //System.err.println(i + ": " + datumRanges.get(i));

            String captionString;
            if (datumRanges.get(i) != null) {
                captionString = datumRanges.get(i).toString();//TODO: consider not formatting these until visible.
            } else {
                captionString = uris.get(i).getPath();
                captionString = captionString.substring(captionString.lastIndexOf('/')+1);
            }

            existingImages.get(i).setCaption(captionString);
            existingImages.get(i).setDatumRange(datumRanges.get(i));
        }

        for (DatumRange dr : datumRanges) {
            if (timeSpan == null)
                timeSpan = dr;
            else
                timeSpan = DatumRangeUtil.union(timeSpan, dr);
        }

        if (timeSpan != null) {
            if ( timeSpan.width().divide(datumRanges.get(0).width() ).doubleValue(Units.dimensionless) > 100000 ) {
                System.err.println("way too many possible timespans, limiting to 20000.");
                timeSpan= new DatumRange( timeSpan.min(), timeSpan.min().add( datumRanges.get(0).width().multiply(20000) ) );
            }
            possibleRanges = DatumRangeUtil.generateList(timeSpan, datumRanges.get(0));
        }

        for (WalkImage i : existingImages) {
            i.addPropertyChangeListener(this);
        }
        subRange = possibleRanges;

        rebuildSequence();
    }

    /**
     * show the datumRange requested by selecting it.  If the datum range is
     * within a gap, then select the range immediately following.
     * 
     * @param ds
     */
    void gotoSubrange(DatumRange ds) {
        int idx= -1;
        for ( int i=datumRanges.size()-1; i>=0; i-- ) {
            if ( ds.contains( datumRanges.get(i).min() ) ) {
                idx= i;
                break;
            }
            if ( datumRanges.get(i).contains(ds) ) {
                idx= i;
                break;
            }
            if ( datumRanges.get(i).min().ge( ds.min() ) ) {
                idx=i;
            }
        }
        if ( idx==-1 ) {
            setIndex( datumRanges.size()-1);
        } else {
            setIndex(idx);
        }
    }

    /** Rebuilds the image sequence.  Should be called on initial load and if
     * list content options (showMissing, subrange) are changed.
     */
    private synchronized void rebuildSequence( ) {
        // remember current image so we can update the index appropriately
        WalkImage currentImage = null;
        if(displayImages.size() >  0) currentImage = currentImage();
        if (timeSpan != null) {
            List<DatumRange> displayRange;
            if (isUseSubRange() && subRange.size() > 0) {
                displayRange = subRange;
            } else {
                displayRange = possibleRanges;
            }
            displayImages.clear();

            for (DatumRange dr : displayRange) {
                if (datumRanges.contains(dr)) {
                    displayImages.add(existingImages.get(datumRanges.indexOf(dr)));
                } else if (showMissing && timeSpan != null) {
                    // add missing image placeholder
                    WalkImage ph = new WalkImage(null,haveThumbs400);
                    ph.setCaption(dr.toString());
                    displayImages.add(ph);
                }
            }
        } else {
            displayImages.clear();
            displayImages = new ArrayList<WalkImage>( existingImages );
        }
        if (displayImages.contains(currentImage)) {
            index = displayImages.indexOf(currentImage);
        } else {
            index = 0;
        }
        
        //Bogus property has no meaningful value, only event is important
        pcs.firePropertyChange(PROP_SEQUENCE_CHANGED, false, true);
    }

    /**
     * initialize the quality control sequence.
     * @param qcFolder URI with the password resolved.
     */
    protected void initQualitySequence( URI qcFolder ) {
        try {
            this.qcFolder= qcFolder;
            qualitySeq = new QualityControlSequence(WalkImageSequence.this, qcFolder);
            for (int i = 0; i < displayImages.size(); i++) {
                qualitySeq.getQualityControlRecord(i).addPropertyChangeListener(WalkImageSequence.this); // DANGER
                pcs.firePropertyChange(PROP_BADGE_CHANGE, -1, i);
            }
        } catch (IOException ex) {
            Logger.getLogger(WalkImageSequence.class.getName()).log(Level.SEVERE, null, ex);
            setStatus("warning: "+ ex.toString());
            throw new RuntimeException(ex);
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

    /**
     * return the WalkImage object for the given URI.
     */
    public WalkImage getImage( URI image ) {
        for ( WalkImage i: displayImages ) {
            if ( i.getUri().equals(image) ) return i;
        }
        throw new IllegalStateException("didn't find image for "+image);
    }

    public WalkImage imageAt(int n) {
        if (n<0 || n>displayImages.size()-1) {
            throw new IndexOutOfBoundsException();
        } else {
            return displayImages.get(n);
        }
    }

    public URI getQCFolder() {
        return qcFolder;
    }

    public void setQCFolder( URI folder ) {
        this.qcFolder= folder;
    }

    /**
     * this may be null if even though we are using QualityControl, if the user
     * hasn't logged in yet.
     * @return
     */
    public QualityControlSequence getQualityControlSequence() {
        return this.qualitySeq;
    }
    
    public boolean isShowMissing() {
        return showMissing;
    }

    public void setShowMissing(boolean showMissing) {
        if (showMissing != this.showMissing) {
            this.showMissing = showMissing;
            rebuildSequence();  // fires property change
        }
    }

    public boolean isUseSubRange() {
        return useSubRange;
    }

    public void setUseSubRange(boolean useSubRange) {
        boolean oldSubRange = this.useSubRange;
        this.useSubRange = useSubRange;
        pcs.firePropertyChange(PROP_USESUBRANGE, oldSubRange, useSubRange);
        if(useSubRange != oldSubRange) rebuildSequence();
    }

    /** Set the sequence's active subrange to a range from first to last, inclusive.
     * First and last are indices into the list obtained from <code>getAllTimes()</code>.
     * @param first
     * @param last
     */
    public void setActiveSubrange(int first, int last) {
        subRange = possibleRanges.subList(first, last+1);
        if (isUseSubRange()) rebuildSequence();
    }

    /**
     * Convenience method for to set the subrange to the items intersecting the given range.
     * @param range
     */
    public void setActiveSubrange(DatumRange range) {
        int first=-1;
        int last= -1;
        for ( int i=0; i<possibleRanges.size(); i++ ) {
            if ( possibleRanges.get(i).intersects(range) ) {
                if ( first==-1 ) first=i;
                last= i;
            }
        }
        if ( first!=-1 ) {
            setActiveSubrange( first, last );
        }
    }

    public List<DatumRange> getActiveSubrange() {
        return subRange;
    }

    /** Return the time range covered by this sequence.  This is the total range
     * of available images, not any currently displayed subrange.  Will be null
     * if no date template was used to create the sequence.
     * TODO: Is this needed?
     * @return
     */
    public DatumRange getTimeSpan() {
        return timeSpan;
    }

    /** Return a <code>java.awt.List</code> of the times associated with this sequence.
     * This list will include times associated with missing images, and is not restricted
     * to any currently active subrange.
     * @return
     */
    public List<DatumRange> getAllTimes() {
        return possibleRanges;
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
     * @throws IndexOutOfBoundsException when...
     */
    public void setIndex(int index) {
        if (index == this.index) {
            // do nothing and fire no event
            return;
        }
        if (index < 0 || index >= displayImages.size()) {
            throw new IndexOutOfBoundsException();
        }
        int oldIndex = this.index;
        this.index = index;
        pcs.firePropertyChange(PROP_INDEX, oldIndex, this.index);
    }

    List<Integer> sel= Collections.emptyList();

    public void setSelectedIndeces( List<Integer> sel ) {
        List<Integer> old= this.sel;
        this.sel= sel;
        pcs.firePropertyChange( PROP_SELECTED_INDECES, old, sel );
    }

    public List<Integer> getSelectedIndeces(  ) {
        return this.sel;
    }

    /** Advance the index to the next image in the list.  If the index is already
     * at the last image, do nothing.
     */
    public void next() {
        if (index < displayImages.size() -1) {
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
        setIndex(displayImages.size()-1);
    }

    /** Skip forward or backward by the specified number of images.  Positive
     * numbers skip forward; negative skip backward.  If skipping the requested
     * number of frames would put the index out of range, the skip moves to the
     * last or first image, as appropriate.
     *
     * @param n The number of images to skip.
     */
    public void skipBy(int n) {
        if (index + n > displayImages.size() - 1) {
            setIndex(displayImages.size() - 1);
        } else if (index + n < 0) {
            setIndex(0);
        } else {
            setIndex(index + n);
        }
    }

    public int size() {
        return displayImages.size();
    }

    /**
     * returns null, True or False.
     * @return
     */
    private Boolean doHaveThumbs400() {
        return this.haveThumbs400;
    }

    /**
     * things we fire events for:
     *   PROP_BADGE_CHANGE
     *   and others
     * @param l
     */
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
        if (e.getNewValue() instanceof WalkImage.Status) {
            if ((WalkImage.Status) e.getNewValue() == WalkImage.Status.IMAGE_LOADED ||
                    (WalkImage.Status) e.getNewValue() == WalkImage.Status.THUMB_LOADED) {
                int i = displayImages.indexOf(e.getSource());
                if (i == -1) {
                    if (existingImages.indexOf(e.getSource()) == -1) {
                        //panic because something is very very wrong
                        throw new RuntimeException("Status change from unknown image object");
                    }
                    /* If we get here, this is just a notification from an image that's no
                     * longer displayed because of date filter or "show missing" filter, so do nothing
                     */
                    return;
                }
                // imageLoaded is a bogus property so there's no old value
                // passing an illegal negative value in its place assures event is always fired
                pcs.firePropertyChange(PROP_THUMB_LOADED, -1, i);
                if ((WalkImage.Status) e.getNewValue() == WalkImage.Status.IMAGE_LOADED) {
                    pcs.firePropertyChange(PROP_IMAGE_LOADED, -1, i);
                }
            }
        } else if ( e.getPropertyName().equals( QualityControlRecord.PROP_STATUS ) ) {
            URI imageURI= ((QualityControlRecord)e.getSource()).getImageURI();
            WalkImage im= getImage(imageURI);
            int i= displayImages.indexOf(im);
            pcs.firePropertyChange( PROP_BADGE_CHANGE, -1, i );

        } 
        
        int loadingCount=0;
        int loadedCount=0;
        int thumbLoadingCount=0;
        int thumbLoadedCount=0;
        int sizeThumbCount= 0;
        int totalCount= 0;
        for ( WalkImage i : existingImages ) {
            totalCount++;
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADING ) loadingCount++;
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADED ) loadedCount++;
            if ( i.getStatus()==WalkImage.Status.THUMB_LOADING ) thumbLoadingCount++;
            if ( i.getStatus()==WalkImage.Status.THUMB_LOADED ) thumbLoadedCount++;
            if ( i.getStatus()==WalkImage.Status.SIZE_THUMB_LOADED ) sizeThumbCount++;
        }

/*        if ( loadingCount>5 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }

        if ( thumbLoadingCount>30 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }*/

        long mem= ( Runtime.getRuntime().freeMemory() ) / (1024 * 1024);
        if ( loadingCount==0 && thumbLoadingCount==0) {
            setStatus(""+loadedCount+" of "+totalCount + " images loaded, " + thumbLoadedCount + " thumbs, "
                    + sizeThumbCount+ " exact size are loaded. " + mem + " MB free" );
        } else {
            setStatus("busy: "+loadedCount+" of "+totalCount + " images loaded, " 
                    + loadingCount + " are loading and "+ thumbLoadingCount + " thumbs are loading.  "
                    + sizeThumbCount + " exact size are loaded. " + mem + "MB free");
        }
        
    }

}
