package org.autoplot.pngwalk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.autoplot.pngwalk.WalkUtil.splitIndex;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.util.filesystem.FileSystem;
import org.autoplot.dom.DebugPropertyChangeSupport;
import org.autoplot.datasource.DataSetURI;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.FileSystemUtil;

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

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
 
    // list of all possible images, without limit.
    private List<WalkImage> existingImages;
    
    // list of the visible images, limited by "Limit range to" gui.
    private List<WalkImage> displayImages = new ArrayList();
    
    //private List<URI> locations;
    private boolean showMissing = false;
    private boolean useSubRange = false;
    private int index;

    private DatumRange timeSpan = null;
    
    // list of ranges for each existing image file.
    private List<DatumRange> datumRanges = null;
    
    // list of ranges, including gaps between files.
    private List<DatumRange> possibleRanges = null;

    /**
     * true if the list of possibleRanges is more than the list of datumRanges
     */
    private boolean containsGaps;
    
    private List<DatumRange> subRange = null;

    /**
     * template used to create list.  This may be null.
     */
    private final String template;
    
    /**
     * the location of the base of the pngwalk.
     */
    private URI baseURI;

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
    private String qcFilter=""; // limits what is shown.

    private boolean haveThumbs400=true;
    
    private boolean limitWarning= false;
    
    
    /** Create an image sequence based on a URI template.
     *
     * @param template a template, or null will produce an empty walk sequence.
     */
    public WalkImageSequence( String template ) {
        this.template= template;
        int i= WalkUtil.splitIndex(template);
        if ( i==-1 ) {
            throw new IllegalArgumentException("template does not contain /.  Hrmph.");
        }
        try {
            this.baseURI= new URI(template.substring(0,i));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
        //call initialLoad before any other methods.
    }

    private DatumRange timerange = null;

    public static final String PROP_TIMERANGE = "timerange";

    /**
     * constraint for the limit of the time ranges listed.  Note timespan
     * is what was found.  This does not appear to be the current image
     * timerange.
     * @return 
     */
    public DatumRange getTimerange() {
        return timerange;
    }

    /**
     * constraint for the limit of the time ranges listed.  Note timespan
     * is what was found.
     * 
     * @param timerange 
     */
    public void setTimerange(DatumRange timerange) {
        DatumRange oldTimerange = this.timerange;
        this.timerange = timerange;
        pcs.firePropertyChange(PROP_TIMERANGE, oldTimerange, timerange);
    }

    /**
     * return true if the files contain both start and end.
     * TODO: this is a quick and dirty implementation that needs to be
     * done thoroughly.  
     * @return 
     */
    private boolean templateHasExplicitEnd( ) {
        if ( template.contains("$(Y;end)") ) return true;
        return false;
    }
    
    /**
     * do the initial listing of the remote filesystem.  This should not
     * be done on the event thread, and should be done before the
     * sequence is used.
     * @throws java.io.IOException
     */
    public void initialLoad() throws java.io.IOException {
        datumRanges = new ArrayList();
        subRange = new ArrayList();
        List<URI> uris;

        if ( template==null ) {
            throw new IllegalStateException("template was null");
        } else {
            try {
                setStatus( "busy: listing "+template );
                uris = WalkUtil.getFilesFor(template, timerange, datumRanges, false, null);
                if ( !uris.isEmpty() ) {
                    setStatus( "Done listing "+template );
                } else {
                    setStatus( "warning: no files found in "+template );
                }
            } catch ( IOException | URISyntaxException | ParseException | IllegalArgumentException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                setStatus("error: Error listing " + template+", "+ex.getMessage() );
                throw new java.io.IOException("Error listing "  + template+", "+ex.getMessage() );
            } finally {
                setStatus( " " );
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
        FileStorageModel fsm=null;
        {
            String sansArgs= template;
            int i = splitIndex(sansArgs);
            URI surls= DataSetURI.getResourceURI(sansArgs.substring(0, i+1));
            FileSystem fs = FileSystem.create( surls );
            String spec= sansArgs.substring(i+1);
            if ( TimeParser.isSpec(spec) ) fsm= FileStorageModel.create( fs, spec );        
        }
        
        boolean isTimeRanges= false;
        boolean eachIsAllTime= false;
        if ( uris.size()>0 ) {
            // See https://sourceforge.net/p/autoplot/bugs/2718/
            if ( datumRanges.get(0)!=null && UnitsUtil.isTimeLocation(datumRanges.get(0).getUnits()) ) {
                isTimeRanges= true;
                if ( datumRanges.get(0).width().ge( Units.days.createDatum(2.92e6) ) ) { // year 1000 to 9000
                    eachIsAllTime= true;
                }
            }
        }
        
        existingImages = new ArrayList<>();
        for (int i=0; i < uris.size(); i++) {
            existingImages.add(new WalkImage(uris.get(i),haveThumbs400));
            //System.err.println(i + ": " + datumRanges.get(i));

            String captionString;
            int splitIndex= WalkUtil.splitIndex( template );
            DatumRange range= datumRanges.get(i);
            if ( range != null) {
                if ( eachIsAllTime ) {
                    captionString = "";
                } else {
                    captionString = datumRanges.get(i).toString();//TODO: consider not formatting these until visible.
                }
                if ( ( template.contains("*") || template.contains("$x") || template.contains("$(x;")) && fsm!=null ) {
                    String cs= uris.get(i).toString();
                    if ( template.startsWith("file:///") && cs.length()>6 && cs.charAt(6)!='/' ) {
                        splitIndex -= 2;
                    }
                    if ( cs.length()>13 && cs.substring(8,13).equals("user@") ) {
                        splitIndex += 5;
                    }
                    //TODO: file:///tmp/ap/$(x;name=p1)_$(x;name=p2).png should be handled
                    String x1= fsm.getField( "x", cs.substring(splitIndex+1) );  
                    captionString = captionString + " " + x1;   
                }
            } else {
                captionString = FileSystemUtil.uriDecode(uris.get(i).toString());
                if ( template.startsWith("file:///") && captionString.length()>6 && captionString.charAt(6)!='/' ) {
                    splitIndex-= 2;
                }
                String cs= captionString;
                if ( cs.length()>13 && cs.substring(8,13).equals("user@") ) {
                    splitIndex+=5;
                }
                captionString = cs.substring(splitIndex+1);
            }

            existingImages.get(i).setCaption(captionString);
            existingImages.get(i).setDatumRange(datumRanges.get(i));
        }

        for (DatumRange dr : datumRanges) {
            if (timeSpan == null) {
                timeSpan = dr;
            } else {
                timeSpan = DatumRangeUtil.union(timeSpan, dr);
            }
        }

        if (timeSpan != null) {
            long approximateSize= (long)( timeSpan.width().divide(datumRanges.get(0).width() ).value() );
            boolean verySparse= ((double)datumRanges.size()) / approximateSize < 0.1;
            boolean nonMonoNonUniq=false;
            if ( !datumRanges.isEmpty() ) {
                DatumRange lastdr= datumRanges.get(0);
                for ( DatumRange dr : datumRanges.subList(1,datumRanges.size()) ) {
                    if ( dr.intersects(lastdr) ) { 
                        nonMonoNonUniq=true;
                        break;
                    }
                }
            }
            if ( templateHasExplicitEnd() 
                 || approximateSize > 100000 
                 || verySparse 
                 || nonMonoNonUniq ) {
                possibleRanges = datumRanges;
                containsGaps= false;
            } else {
                possibleRanges = DatumRangeUtil.generateList(timeSpan, datumRanges.get(0));
                containsGaps= true;
            }
        }
        
        // There's a funny bug where things aren't regularly spaced, because we
        // realign at year boundaries.  Just use the original ranges in this 
        // case.
        if ( possibleRanges!=null && datumRanges.size()>possibleRanges.size() ) {
            logger.info("jumps in cadence, just use original ranges");
            possibleRanges= datumRanges;
        }
        

        for (WalkImage i : existingImages) {
            i.addPropertyChangeListener(this);
        }
        subRange = possibleRanges;

        rebuildSequence();
    }

    /**
     * return the index of the datumRange completely containing this
     * interval.
     * @param dr 
     * @return -1 if no range contains the subrange, the index of the DatumRange otherwise.
     */
    protected int indexOfSubrange( DatumRange dr ) {
        int idx= -1;
        for ( int i=datumRanges.size()-1; i>=0; i-- ) {
            if ( datumRanges.get(i)==null ) {
                logger.info("ranges are not available");
                return -1;
            }
            if ( dr.contains( datumRanges.get(i).min() ) ) {
                idx= i;
                break;
            }
            if ( datumRanges.get(i).contains(dr) ) {
                idx= i;
                break;
            }
        }
        return idx;
        
    }
    
    /**
     * show the datumRange requested by selecting it.  The range that intersects is selected.
     * If the datum range is within a gap in time, then select the time range immediately following.
     * 
     * @param ds
     */
    void gotoSubrange(DatumRange ds) {
        int idx= -1;
        
        if ( datumRanges.isEmpty() || datumRanges.get(0)==null ) {
            logger.log(Level.INFO, "pngwalk does not have time ranges for each image ({0})", template);
            return;
        }

        for ( int i=0; i<size(); i++ ) {
            if ( ds.intersects( imageAt(i).getDatumRange() ) ) {
                idx= i;
                break;
            }
        }
        
        if ( idx==-1 ) {
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
        }
        if ( idx==-1 ) {
            setIndex( datumRanges.size()-1);
        } else {
            setIndex(idx);
        }
    }

    private final Lock lock = new ReentrantLock();
   
    /** Rebuilds the image sequence.  Should be called on initial load and if
     * list content options (showMissing, subrange) are changed.
     */
    private void rebuildSequence( ) {
        
        lock.lock();
        
        try {
            // remember current image so we can update the index appropriately
            WalkImage currentImage = null;
            if(displayImages.size() >  0) currentImage = currentImage();

            if ( timeSpan != null || qcFilter.length()>0 ) {
                List<DatumRange> displayRange;
                if (isUseSubRange() && subRange.size() > 0) {
                    displayRange = subRange;
                } else {
                    displayRange = possibleRanges;
                }

                limitWarning = possibleRanges!=null && possibleRanges.size()==20000;

                List<QualityControlRecord.Status> statuses=null;
                if ( qualitySeq!=null ) {
                    statuses = new ArrayList<>(this.datumRanges.size());
                    for ( int i=0; i<datumRanges.size(); i++ ) {
                        statuses.add(i,qualitySeq.getQualityControlRecordNoSubRange(i).getStatus() );
                    }
                }

                HashSet<QualityControlRecord.Status> allowedStatuses= new HashSet<>();
                if ( qcFilter.length()>0 ) {
                    for ( int i=0; i<qcFilter.length(); i++ ) {
                        char ch= qcFilter.charAt(i);
                        switch (ch) {
                            case 'o':
                                allowedStatuses.add( QualityControlRecord.Status.OK );
                                break;
                            case 'p':
                                allowedStatuses.add( QualityControlRecord.Status.PROBLEM );
                                break;
                            case 'i':
                                allowedStatuses.add( QualityControlRecord.Status.IGNORE );
                                break;
                            case 'u':
                                allowedStatuses.add( QualityControlRecord.Status.UNKNOWN );
                                break;
                            default:
                                break;
                        }
                    }
                }

                if ( displayRange!=null ) {
                    displayImages.clear();
                    
                    boolean hasX= this.template.contains("$x") || this.template.contains("*") || this.template.contains("$(x;") || this.template.contains("$(x,");
                    boolean hasXLogic= this.template!=null && hasX 
                                && datumRanges.size()==existingImages.size();
                    int i=0;
                    
                    int idisplayRange=0;
                    
                    for (DatumRange dr : displayRange) {
                        
                        int ind;
                        if ( containsGaps ) {
                            ind= datumRanges.indexOf(dr);
                        } else {
                            ind= idisplayRange;
                        }
                        
                        idisplayRange++;
                        
                        if ( ind>-1 ) {
                            if ( qualitySeq!=null && qcFilter.length()>0 ) {
                                assert statuses!=null;
                                if ( !allowedStatuses.contains( statuses.get(ind) ) ) {
                                    continue;
                                }
                            }
                            if ( hasXLogic ) {
                                if ( qualitySeq!=null && qcFilter.length()>0 ) {
                                    displayImages.add(existingImages.get(ind)); // TODO: I don't understand this code
                                } else {
                                    displayImages.add(existingImages.get(ind));
                                }
                                i++;
                            } else {
                                displayImages.add(existingImages.get(ind));//TODO: suspect this is very inefficient.
                            }
                        } else if (showMissing && timeSpan != null) {
                            if ( qualitySeq!=null && qcFilter.length()>0 ) {
                                continue;
                            }
                            // add missing image placeholder
                            WalkImage ph = new WalkImage(null,haveThumbs400);
                            ph.setCaption(dr.toString());
                            displayImages.add(ph);
                        } else {
                            logger.fine("I don't think we should get here (but we do, harmless).");
                        }
                    }
                } else {
                    displayImages.clear();
                    for ( int ind=0; ind<existingImages.size(); ind++ ) {
                        if ( qualitySeq!=null && qcFilter.length()>0 ) {
                            assert statuses!=null;
                            if ( !allowedStatuses.contains( statuses.get(ind) ) ) {
                                continue;
                            }
                        }
                        displayImages.add(existingImages.get(ind));
                    }
                }
            } else {
                displayImages.clear();
                displayImages = new ArrayList<>( existingImages );
            }
            if (displayImages.contains(currentImage)) {
                index = displayImages.indexOf(currentImage);
            } else {
                index = 0;
            }
        } finally {
            lock.unlock();
        }
        
        //Bogus property has no meaningful value, only event is important
        pcs.firePropertyChange(PROP_SEQUENCE_CHANGED, false, true);
    }

    protected boolean isLimitWarning() {
        return this.limitWarning;
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
            logger.log(Level.SEVERE, ex.getMessage(), ex);
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
     * @param image the location
     * @return the object modeling this image.
     */
    public WalkImage getImage( URI image ) {
        for ( WalkImage i: displayImages ) {
            if ( i.getUri().equals(image) ) return i;
        }
        throw new IllegalStateException("didn't find image for "+image);
    }

    /**
     * return the image of the subrange.
     * @param n
     * @return 
     */
    public WalkImage imageAt(int n) {
        if (n<0 || n>displayImages.size()-1) {
            throw new IndexOutOfBoundsException("index must be within 0-"+(displayImages.size()-1)+": "+n);
        } else {
            return displayImages.get(n);
        }
    }

    /**
     * get the image in the sequence, regardless of the subrange.
     * @param n
     * @return 
     */
    public WalkImage imageAtNoSubRange(int n) {
        if (n<0 || n>existingImages.size()-1) {
            throw new IndexOutOfBoundsException("index must be within 0-"+(displayImages.size()-1)+": "+n);
        } else {
            return existingImages.get(n);
        }
    }
    
    /**
     * return the location of the PNGWalk, which should contain the image files.
     * @return 
     */
    public URI getBaseUri() {
        return baseURI;
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

    /**
     * true if the sequence has been generated so that gaps could be shown.
     * @return true if the sequence has been generated so that gaps could be shown.
     */
    public boolean isContainsGaps() {
        return containsGaps;
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
    
    /**
     * string containing combination of "opi" meaning that each should be shown.
     * "" will show everything.  The list can include:<ul>
     * <li>o okay are shown
     * <li>p problem are shown
     * <li>i ignore are shown
     * <li>u is unknown/unmarked
     * </ul>
     * @param s 
     */
    public void setQCFilter( String s ) {
        if ( s==null ) throw new NullPointerException("qcfilter cannot be null, set to empty string to clear");
        String oldQcFilter= this.qcFilter;
        this.qcFilter= s;
        if ( !qcFilter.equals(oldQcFilter ) ) {
            rebuildSequence();
        }
    }
    
    /**
     * returns the current QC filter, where "" means no filter, otherwise 
     * the characters indicate:
     * <ul>
     * <li>o okay are shown
     * <li>p problem are shown
     * <li>i ignore are shown
     * </ul>
     * @return the filter, for example "" or "op" for okay and problem.
     */
    public String getQCFilter() {
        return this.qcFilter;
    }

    /**
     * return the active subrange of the sequence.  This is the portion of the pngwalk being used.
     * @return the subrange of the sequence.
     * @see https://sourceforge.net/p/autoplot/feature-requests/493/
     */
    public List<DatumRange> getActiveSubrange() {
        return subRange;
    }

    /** Return the time range covered by this sequence.  This is the total range
     * of available images, not any currently displayed subrange.  Will be null
     * if no date template was used to create the sequence.
     * @return the time range covered by this sequence.
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

    /** Return the current value of the index, where index is that of the displayImages.
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
        if ( index<0 ) {
            index= 0;
        }
        if ( index>= displayImages.size() ) {
            index= displayImages.size()-1;
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

    /**
     * return the number of images in the sequence.
     * @return the number of images in the sequence.
     */
    public int size() {
        return displayImages.size();
    }

    ///**
    // * returns null, True or False.
    // * @return
    // */
    //private Boolean doHaveThumbs400() {
    //    return this.haveThumbs400;
    //}

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

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }


    /**
     * return the template representing the sequence.
     * @return the template
     */
    public String getTemplate() {
        return this.template;
    }

    protected String status = "idle";
    public static final String PROP_STATUS = "status";

    /**
     * get the current status
     * @return 
     */
    public String getStatus() {
        return status;
    }

    /**
     * set the current status, which is echoed back to the scientist.
     * @param status the status
     */
    protected void setStatus(String status) {
        String oldStatus = this.status;
        this.status = status;
        pcs.firePropertyChange(PROP_STATUS, oldStatus, status);
    }


    // Get status changes from the images in the list
    @Override
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
        //int thumbLoadedCount=0;
        //int sizeThumbCount= 0;
        int totalCount= 0;
        for ( WalkImage i : existingImages ) {
            totalCount++;
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADING ) loadingCount++;
            if ( i.getStatus()==WalkImage.Status.IMAGE_LOADED ) loadedCount++;
            if ( i.getStatus()==WalkImage.Status.THUMB_LOADING ) thumbLoadingCount++;
            //if ( i.getStatus()==WalkImage.Status.THUMB_LOADED ) thumbLoadedCount++;
            //if ( i.getStatus()==WalkImage.Status.SIZE_THUMB_LOADED ) sizeThumbCount++;
        }

/*        if ( loadingCount>5 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }

        if ( thumbLoadingCount>30 ) {
            System.err.println( Thread.currentThread() );
            new Exception().printStackTrace();
        }*/

        //long mem= ( Runtime.getRuntime().freeMemory() ) / (1024 * 1024);
        if ( loadingCount==0 && thumbLoadingCount==0) {
            if ( limitWarning ) {
                setStatus("<html>"+loadedCount+" of "+totalCount + " images loaded.  Limitations of the PNG Walk Tool prevent use of the entire series." );
            } else {
                setStatus(""+loadedCount+" of "+totalCount + " images loaded." );
            }
        } else {
            setStatus("busy: "+loadedCount+" of "+totalCount + " images loaded, " 
                    + loadingCount + " are loading and "+ thumbLoadingCount + " thumbs are loading.");
        }
        
    }

    /**
     * returns the index of the name, or -1 if the name is not found.  This is not the full 
     * filename, but instead just the part of the name within the walk.  For example,
     * For example if getTemplate is file:/tmp/$Y$m$d.gif, then the setSelectedName might be 20141111.gif.  
     * @param name the file name.
     * @return the index, or -1 if the name is not found.
     */
    public int findIndex( String name ) {
        for ( int i=0; i<displayImages.size(); i++ ) {
            WalkImage img= displayImages.get(i);
            if ( img.getUri().toString().endsWith(name) ) {
                return i;
            }
        }
        return -1;
    }

    /**
     * return the name of the selected image.  This is not the full 
     * filename, but instead just the part of the name within the walk.  For example,
     * For example if getTemplate is file:/tmp/$Y$m$d.gif, then the setSelectedName might be 20141111.gif.  
     * @return the name of the selected image.
     */
    public String getSelectedName() {
        WalkImage img= displayImages.get( getIndex() );
        String surl= img.getUri().toString();
        
        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        String spec= sansArgs.substring(i+1);        
        return spec;
    }

    /**
     * cause clients listing to PROP_SEQUENCE_CHANGED to get a message to
     * update.
     */
    void fireBadgeChanged() {
        pcs.firePropertyChange(PROP_BADGE_CHANGE,  -1, getIndex() );
    }
    
    @Override
    public String toString() {
        return this.template + " ("+this.size()+")";
    }

}
