/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.ImageVectorDataSetRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.MutatorLock;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.metatree.MetadataUtil;

/**
 * PanelController manages the Panel, for example resolving the datasource and loading the dataset.
 *
 * Three state flags:
 *   * resetRanges means all work needs to be done
 *   * resetPanel means we might need to introduce child panels and reset the rendertype
 *   * resetRenderType means we might need to refresh the peer.
 * @author jbf
 */
public class PanelController extends DomNodeController {

    private static final String PENDING_RESET_RANGE = "resetRanges";
    private static final String PENDING_SET_DATASET= "setDataSet";

    Logger logger = Logger.getLogger("vap.panelController");
    private Application dom;
    private Panel panel;
    private DataSourceFilter dsf; // This is the one we are listening to.
    /**
     * switch over between fine and course points.
     */
    public static final int SYMSIZE_DATAPOINT_COUNT = 500;
    public static final int LARGE_DATASET_COUNT = 30000;

    public PanelController(final ApplicationModel model, final Application dom, final Panel panel) {
        super(panel);
        panel.controller = this;
        this.dom = dom;
        this.panel = panel;

        panel.addPropertyChangeListener(Panel.PROP_RENDERTYPE, panelListener);
        panel.addPropertyChangeListener(Panel.PROP_DATASOURCEFILTERID, panelListener);
        panel.addPropertyChangeListener(Panel.PROP_COMPONENT, panelListener);
    }

    /**
     * return child panels, which are panels that share a datasource but pull out
     * a component of the data.
     * @return
     */
    public List<Panel> getChildPanels() {
        ArrayList<Panel> result= new ArrayList();
        for ( Panel pp: dom.panels ) {
            if ( pp.getParentPanel().equals( panel.getId() ) ) result.add(pp);
        }
        return result;
    }

    /**
     * set the child panels.
     * @param panels
     */
    protected void setChildPanels(List<Panel> panels) {
        for ( Panel p: panels ) {
            p.setParentPanel(panel.getId());
        }
    }

    /**
     * set the parent panel.  this is used when copying.
     * @param p
     */
    protected void setParentPanel(Panel p) {
        panel.setParentPanel( p.getId() );
    }

    /**
     * return the parent panel, or null if the panel doesn't have a parent.
     * @return
     */
    public Panel getParentPanel() {
        if ( panel.getParentPanel().equals("") ) {
            return null;
        } else {
            for ( Panel pp: dom.panels ) {
                if ( pp.getId().equals( panel.getParentPanel() ) ) return pp;
            }
            return null; // TODO: maybe throw exception!
        }
    }

    /**
     * remove any bindings and listeners
     */
    void unbindDsf() {
        dsf.removePropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, dsfListener);
        dsf.removePropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, dsfListener);
        dsf.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsf.controller.removePropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
    }
    PropertyChangeListener dsfListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(DataSourceFilter.PROP_SLICEDIMENSION) || evt.getPropertyName().equals(DataSourceFilter.PROP_TRANSPOSE)) {
                logger.fine("property change in DSF means I need to autorange: "+evt.getPropertyName());
                setResetRanges(true);
                maybeSetPlotAutorange();
            }
        }
    };
    PropertyChangeListener panelListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            logger.fine("panelListener: " + evt.getPropertyName() + " " + evt.getOldValue() + "->" + evt.getNewValue());
            if (evt.getPropertyName().equals(Panel.PROP_RENDERTYPE)) {
                RenderType newRenderType = (RenderType) evt.getNewValue();
                RenderType oldRenderType = (RenderType) evt.getOldValue();
                Panel parentPanel= getParentPanel();
                if (parentPanel != null) {
                    parentPanel.setRenderType(newRenderType);
                } else {
                    if ( axisDimensionsChange(oldRenderType, newRenderType) ) {
                        resetPanel(getDataSourceFilter().getController().getFillDataSet(), panel.getRenderType());
                    } else {
                        doResetRenderType(newRenderType);
                    }
                    setResetPanel(false);
                }
            } else if (evt.getPropertyName().equals(Panel.PROP_DATASOURCEFILTERID)) {
                changeDataSourceFilter();
                if ( !dom.getController().isValueAdjusting() ) {
                    setResetPanel(true);
                    setResetRanges(true);
                    updateDataSet();
                }
            } else if ( evt.getPropertyName().equals( Panel.PROP_COMPONENT ) ) {
                String newv= (String)evt.getNewValue();
                if ( DataSetOps.changesDimensions( (String)evt.getOldValue(), newv ) ) {
                    logger.finer("component property change requires we reset render and dimensions: "+(String)evt.getOldValue() +"->"+ (String)evt.getNewValue());
                    setResetPanel(true);
                    setResetRanges(true);
                    if ( !dom.getController().isValueAdjusting() ) maybeSetPlotAutorange();
                }
                if ( newv.startsWith("|") ) dom.getOptions().setDataVisible(true);
                updateDataSet();
            } else if ( evt.getPropertyName().equals( Panel.PROP_LEGENDLABEL ) ) {
                panel.setAutolabel(false);
            }
        }
    };

    private boolean needNewChildren(String[] labels, List<Panel> childPanels) {
        if ( childPanels.size()==0 ) return true;
        List<String> ll= Arrays.asList(labels);
        for ( Panel p: childPanels ) {
            if ( !ll.contains( p.getComponent() ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * the DataSourceFilter id has changed, so we need to stop listening to the
     * old one and connect to the new one.  Also, if the old dataSourceFilter is
     * now an orphan, delete it from the application.
     */
    private void changeDataSourceFilter() {
        if (dsf != null) {
            unbindDsf();
            List<DomNode> usages= DomUtil.dataSourceUsages(dom, dsf.getId() );
            if ( usages.size()==0 ) {
                dom.controller.deleteDataSourceFilter(dsf);
            }
        }

        assert (panel.getDataSourceFilterId() != null);
        if ( panel.getDataSourceFilterId().equals("") ) return;

        dsf = dom.controller.getDataSourceFilterFor(panel);

        if ( dsf==null ) {
            throw new NullPointerException("couldn't find the data for this panel");
        } else {
            dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, dsfListener);
            dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, dsfListener);
        }
        setDataSourceFilterController( dsf.controller );
    }

    private Color deriveColor( Color color, int i ) {

        float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (colorHSV[2] < 0.7f) {
            colorHSV[2] = 0.7f;
        }
        if (colorHSV[1] < 0.7f) {
            colorHSV[1] = 0.7f;
        }
        return Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]);
    }

    private QDataSet processDataSet(String c, QDataSet fillDs) throws RuntimeException {
        String label= null;
        if (c.length() > 5 && c.startsWith("|")) {
            // slice and collapse specification
            fillDs = DataSetOps.sprocess(c, fillDs);
        } else {
            if (!panel.getComponent().equals("") && fillDs.length() > 0 && fillDs.rank() == 2) {
                String[] labels = SemanticOps.getComponentLabels(fillDs);
                if (panel.getComponent().equals("X")) {
                    fillDs = DataSetOps.slice1(fillDs, 0);
                    label = labels[0];
                } else if (panel.getComponent().equals("Y")) {
                    fillDs = DataSetOps.slice1(fillDs, 1);
                    label = labels[1];
                } else if (panel.getComponent().equals("Z")) {
                    fillDs = DataSetOps.slice1(fillDs, 2);
                    label = labels[2];
                } else {
                    for (int i = 0; i < labels.length; i++) {
                        if (labels[i].equals(panel.getComponent())) {
                            fillDs = DataSetOps.slice1(fillDs, i);
                            label = labels[i];
                            break;
                        }
                    }
                }
                if (label == null && !isPendingChanges()) {
                    RuntimeException ex = new RuntimeException("component not found " + panel.getComponent());
                    throw ex;
                }
            }
        }
        return fillDs;
    }

    /**
     * calculate the interpretted metadata after the slicing.
     * @param c
     * @param properties
     * @return
     */
    Map<String,Object> processProperties( String c, Map<String,Object> properties ) {
        if (c.length() > 5 && c.startsWith("|")) {
            // slice and collapse specification
            properties = MetadataUtil.sprocess(c, properties );
        }
        return properties;  
    }

    private boolean rendererAcceptsData(QDataSet fillDs) {
        if ( getRenderer() instanceof SpectrogramRenderer ) {
            if ( fillDs.rank()==3 ) {
                QDataSet dep0= (QDataSet) fillDs.property( QDataSet.DEPEND_0 );  // only support das2 tabledataset scheme.
                if ( dep0!=null ) return false;
                return rendererAcceptsData( DataSetOps.slice0(fillDs,0) );
            } else {
                return fillDs.rank()==2;
            }
        } else if ( getRenderer() instanceof SeriesRenderer) {
            return fillDs.rank()==1;
        } else if ( getRenderer() instanceof ImageVectorDataSetRenderer ) {
            return fillDs.rank()==1;
        } else {
            return true;
        }
    }

    /**
     * set the dataset that will be plotted.  If the component property is non-null, then
     * additional filtering will be performed.  See http://papco.org/wiki/index.php/DataReductionSpecs
     * @param fillDs
     * @throws IllegalArgumentException
     */
    private void setDataSet(QDataSet fillDs) throws IllegalArgumentException {

        // since we might delete sibling panels here, make sure each panel is still part of the application
        if (!Arrays.asList(dom.getPanels()).contains(panel)) {
            return;
        }

        String c= panel.getComponent();
        try {
            fillDs = processDataSet(c, fillDs );
            _setDataSet(fillDs);
        } catch ( RuntimeException ex ) {
            if (getRenderer() != null) {
                getRenderer().setException(ex);
                getRenderer().setDataSet(null);
                _setDataSet(null);
            } else {
                throw ex;
            }
            return;
        }

        if (getRenderer() != null) {
            if (rendererAcceptsData(fillDs)) {
                getRenderer().setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
            } else {
                getRenderer().setDataSet(null);
                getRenderer().setException(new Exception("renderer cannot plot " + fillDs));
            }
        }

//        Plot plot= this.dom.getController().getPlotFor(panel);
//        List<Panel> panels= dom.controller.getPanelsFor( plot );
//        if ( DomUtil.oneFamily(panels) && plot.isAutolabel() ) {
//            final DataSourceFilter dsf = getDataSourceFilter();
//            String reduceRankString = dsf.controller.getReduceDataSetString();
//            if (dsf.controller.getReduceDataSetString() != null) {
//                // kludge to update title
//                String title = dom.controller.getPlot().getTitle(); //TODO: fix
//                Pattern p = Pattern.compile("(.*)!c(.+)=(.+)");
//                Matcher m = p.matcher(title);
//                if (m.matches()) {
//                    title = m.group(1) + "!c" + reduceRankString;
//                    plot.setTitle(title);
//                }
//            }
//        }
    }

    /**
     * the current dataset plotted.  
     */
    public static final String PROP_DATASET = "dataSet";

    protected QDataSet dataSet = null;

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void _setDataSet(QDataSet dataSet) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet;
        if ( panel.getLegendLabel().contains("%{") && renderer!=null ) {
            String s= (String)getLabelConverter().convertForward(panel.getLegendLabel());
            renderer.setLegendLabel(s);
        }
        propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }


    PropertyChangeListener fillDataSetListener = new PropertyChangeListener() {

        public synchronized void propertyChange(PropertyChangeEvent evt) {
            changesSupport.performingChange( this, PENDING_SET_DATASET );
            if (!Arrays.asList(dom.getPanels()).contains(panel)) {
                return;  // TODO: kludge, I was deleted. I think this can be removed now.  The applicationController was preventing GC.
            }
            QDataSet fillDs = dsf.controller.getFillDataSet();
            logger.fine("" + panel + " got new dataset: " + fillDs + "  resetPanel=" + resetPanel + "  resetRanges=" + resetRanges);
            updateDataSet();
            changesSupport.changePerformed( this, PENDING_SET_DATASET );
        }

        public String toString() {
            return "" + PanelController.this;
        }

    };

    /**
     * get the dataset from the dataSourceFilter, and plot it possibly after
     * slicing component.
     * @throws IllegalArgumentException
     */
    private void updateDataSet() throws IllegalArgumentException {
        QDataSet fillDs = dsf.controller.getFillDataSet();
        if (fillDs != null) {
            if (resetPanel) {
                if (panel.getComponent().equals("")) {
                    RenderType renderType = AutoplotUtil.getRenderType(fillDs);
                    panel.renderType = renderType;
                    resetPanel(fillDs, renderType);
                    setResetPanel(false);
                } else if ( panel.getComponent().startsWith("|") ) {
                    QDataSet fillDs2 = processDataSet( panel.getComponent(), fillDs );
                    RenderType renderType = AutoplotUtil.getRenderType(fillDs2);
                    panel.renderType = renderType;
                    resetPanel(fillDs2, renderType);
                    setResetPanel(false);
                } else {
                    if (renderer == null) maybeCreateDasPeer();
                    if (resetRanges) doResetRanges();
                    setResetPanel(false);
                }
            } else if (resetRanges) {
                doResetRanges();
                setResetRanges(false);
            } else if (resetRenderType) {
                doResetRenderType(panel.getRenderType());
            }
        }
        if (fillDs == null) {
            if (getRenderer() != null) {
                getRenderer().setDataSet(null);
                getRenderer().setException(null); // remove leftover message.
            }
        } else {
            setDataSet(fillDs);
        }
    }

    /**
     * true indicates that the new renderType makes the axis dimensions change.
     * For example, switching from spectrogram to series (to get a stack of components)
     * causes the z axis to become the yaxis.
     * @param oldRenderType
     * @param newRenderType
     */
    private boolean axisDimensionsChange( RenderType oldRenderType, RenderType newRenderType ) {
        if ( oldRenderType==newRenderType ) return false;
        if ( newRenderType==RenderType.digital ) return false;
        if ( oldRenderType==RenderType.spectrogram && newRenderType==RenderType.nnSpectrogram ) {
            return false;
        } else if ( newRenderType==RenderType.nnSpectrogram && oldRenderType==RenderType.spectrogram ) {
            return false;
        } else if ( newRenderType==RenderType.spectrogram || newRenderType==RenderType.nnSpectrogram ) {
            return true;
        } else {
            if ( oldRenderType==RenderType.spectrogram || oldRenderType==RenderType.nnSpectrogram ) {
                return true;
            } else {
                return false;
            }
        }
    }

    private static String[] getDimensionNames( QDataSet ds ) {

        String[] depNames = new String[ds.rank()];
        for (int i = 0; i < ds.rank(); i++) {
            depNames[i] = "dim" + i;
            QDataSet dep0 = (QDataSet) ds.property("DEPEND_" + i);
            if (dep0 != null) {
                String dname = (String) dep0.property(QDataSet.NAME);
                if (dname != null) {
                    depNames[i] = dname;
                }
            }
        }

        return depNames;
    }

    /**
     * guess the best sprocess to reduce the rank to something we can display.
     * guess the best dimension to slice by default, based on metadata.  Currently,
     * this looks for the names lat, lon, and angle.
     *
     * @param fillDs
     * @return sprocess string like "slice1(0)"
     */
    private static String guessSlice( QDataSet fillDs ) {
        String[] depNames= getDimensionNames(fillDs);

        int lat = -1, lon = -1;

        int[] slicePref = new int[]{2, 2, 2}; // slicePref big means more likely to slice.
        for (int i = 0; i < depNames.length; i++) {
            String n = depNames[i].toLowerCase();
            if (n.startsWith("lat")) {
                slicePref[i] = 0;
                lat = i;
            } else if (n.startsWith("lon")) {
                slicePref[i] = 0;
                lon = i;
            } else if (n.contains("time") ) {
                slicePref[i] = 1;
            } else if (n.contains("epoch") ) {
                slicePref[i] = 1;
            } else if (n.contains("angle")) {
                slicePref[i] = 4;
            } else if (n.contains("alpha") ) { // commonly used for pitch angle in space physics
                slicePref[i] = 4;
            } else if (n.contains("bundle")) {
                slicePref[i] = 4;
            }
        }

        int sliceIndex = 0;
        int bestSlice = 0;
        for (int i = 0; i < 3; i++) {
            if (slicePref[i] > bestSlice) {
                sliceIndex = i;
                bestSlice = slicePref[i];
            }
        }

        String result= "|slice"+sliceIndex+"(0)";
        if (lat > -1 && lon > -1 && lat < lon) {
            result+="|transpose()";
        }

        //if ( fillDs.rank()>3 ) {
        //    result="|slice0(0)"+result;
        //}

        return result;

    }

    /**
     * preconditions:
     *   the new renderType has been identified.
     *   The dataset to be rendered has been identified.
     * postconditions:
     *   old child panels have been deleted.
     *   child panels have been added when needed.
     * @param fillDs
     * @param renderType
     */
    private void resetPanel(QDataSet fillDs, RenderType renderType) {
        logger.finest("resetPanel(" + fillDs + " " + renderType+") panel="+ panel );
        if (renderer != null) {
            renderer.setActive(true);
        }

        if (fillDs != null) {

            boolean shouldSlice= fillDs.rank()>2;

            boolean shouldHaveChildren= fillDs.rank() == 2
                    &&  ( renderType != RenderType.spectrogram 
                    && renderType != RenderType.nnSpectrogram
                    && renderType != RenderType.digital )
                    &&  fillDs.length(0) < 12;

            String[] labels = null;
            if ( shouldHaveChildren ) labels= SemanticOps.getComponentLabels(fillDs);

            boolean weShallAddChildren=
                    !dom.controller.isValueAdjusting()
                    && shouldHaveChildren
                    && needNewChildren( labels, getChildPanels() );

            if ( !shouldHaveChildren || weShallAddChildren ) { // delete any old child panels
                List<Panel> childPanels= getChildPanels();
                for ( Panel p : childPanels ) {
                    if ( dom.panels.contains(p) ) {  // kludge to avoid runtime exception.  Why is it deleted twice?
                        dom.controller.deletePanel(p);
                    }
                }
            }

            if ( !shouldSlice ) doResetRenderType(panel.getRenderType());
            setResetPanel(false);

            if ( resetRanges && !shouldSlice ) {
                doResetRanges();
                setResetRanges(false);
            }

            if ( shouldHaveChildren ) {
                renderer.setActive(false);
                panel.setDisplayLegend(false);
            }

            if ( shouldSlice ) {
                String component= guessSlice( fillDs );
                String existingComponent= panel.getComponent();
                if ( !existingComponent.equals("") ) {
                    panel.setComponent( existingComponent + component );
                } else {
                    panel.setComponent(component);  // it'll reenter this code now.  problem--autorange is based on slice.
                }
                doResetRenderType(panel.getRenderType());
                return;
            }

            // add additional panels when it's a bundle of rank1 datasets.
            if ( weShallAddChildren ) {

                MutatorLock lock = dom.controller.mutatorLock();
                lock.lock();

                Color c = panel.getStyle().getColor();
                Color fc= panel.getStyle().getFillColor();
                Plot domPlot = dom.controller.getPlotFor(panel);
                List<Panel> cp = new ArrayList<Panel>(fillDs.length(0));
                for (int i = 0; i < fillDs.length(0); i++) {
                    Panel cpanel = dom.controller.copyPanel(panel, domPlot, dsf);
                    cpanel.controller.getRenderer().setActive(false);
                    cp.add(cpanel);
                    cpanel.setParentPanel( panel.getId() );
                    cpanel.getStyle().setColor(deriveColor(c, i));
                    cpanel.getStyle().setFillColor( deriveColor(fc,i).brighter() );
                    cpanel.setComponent(labels[i]);
                    cpanel.setDisplayLegend(true);
                    if ( cpanel.isAutolabel() ) cpanel.setLegendLabel(labels[i].trim());
                    cpanel.setRenderType(panel.getRenderType()); // this creates the das2 SeriesRenderer.
                    cpanel.controller.setDataSet(fillDs);
                }
                for ( Panel cpanel: cp ) {
                    cpanel.controller.getRenderer().setActive(true);
                }
                renderer.setActive(false);
                setChildPanels(cp);
                lock.unlock();
            }

        } else {
            doResetRenderType(panel.getRenderType());
            
        }
    }

    /**
     * When the data source changes, we will need to autorange so that the axis
     * units are set correctly and things are in a consistent state.  One exception
     * to this is when we are doing state transistions with save/load redo/undo, where
     * we need to avoid autoranging.  Note a kludge in ApplicationController sync
     * sets resetRanges to false after the load.
     */
    PropertyChangeListener dataSourceDataSetListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( !dom.getController().isValueAdjusting() ) {
                if ( panel.getComponent().startsWith("|") ) panel.component=""; //TODO yuck danger code.
            }
            setResetPanel(true);
            setResetRanges(true);
            panel.setAutolabel(true);
            maybeSetPlotAutorange();
        }
    };

    /**
     * we'd like the plot to autorange, so check to see if we are the only
     * panel, and if so, set its autorange and autolabel flags.
     */
    private void maybeSetPlotAutorange() {
        Plot p= dom.controller.getPlotFor(panel);
        if ( p==null ) return;
        List<Panel> panels= dom.controller.getPanelsFor(p);
        if ( DomUtil.oneFamily(panels) ) {
            p.getXaxis().setAutorange(true);
            p.getYaxis().setAutorange(true);
            p.getZaxis().setAutorange(true);
            p.getXaxis().setAutolabel(true);
            p.getYaxis().setAutolabel(true);
            p.getZaxis().setAutolabel(true);
            p.setAutolabel(true);
        }
    }
    private void setDataSourceFilterController(final DataSourceController dsc) {
        dsc.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsc.addPropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
    }
    /**
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    public static final String PROP_RESETRANGES = "resetRanges";
    protected boolean resetRanges = false;

    public boolean isResetRanges() {
        return resetRanges;
    }

    public void setResetRanges(boolean resetRanges) {
        boolean oldResetRanges = this.resetRanges;
        this.resetRanges = resetRanges;
        propertyChangeSupport.firePropertyChange(PROP_RESETRANGES, oldResetRanges, resetRanges);
    }

    /**
     * true indicates the controller should install a new renderer to implement the
     * renderType selection.  This may mean that we introduce or remove child panels.
     * This implies resetRenderType.
     */
    public static final String PROP_RESETPANEL = "resetPanel";
    protected boolean resetPanel = true;

    public boolean isResetPanel() {
        return resetPanel;
    }

    public void setResetPanel(boolean resetPanel) {
        boolean oldResetPanel = this.resetPanel;
        this.resetPanel = resetPanel;
        propertyChangeSupport.firePropertyChange(PROP_RESETPANEL, oldResetPanel, resetPanel);
    }

    /**
     * true indicates the peer should be reset to the current renderType.
     */
    public static final String PROP_RESETRENDERTYPE = "resetRenderType";
    protected boolean resetRenderType = false;

    public boolean isResetRenderType() {
        return resetRenderType;
    }

    public void setResetRenderType(boolean resetRenderType) {
        boolean oldResetRenderType = this.resetRenderType;
        this.resetRenderType = resetRenderType;
        propertyChangeSupport.firePropertyChange(PROP_RESETRENDERTYPE, oldResetRenderType, resetRenderType);
    }

    protected Renderer renderer = null;

    public Renderer getRenderer() {
        return renderer;
    }

    private void setRenderer(Renderer renderer) {
        Renderer oldRenderer= this.renderer;
        this.renderer = renderer;
        ApplicationController ac = this.dom.controller;
        ac.unbindImpl(node);
        if (renderer instanceof SeriesRenderer) {
            bindToSeriesRenderer((SeriesRenderer) renderer);
            bindToSpectrogramRenderer(new SpectrogramRenderer(null, null));
        } else if (renderer instanceof SpectrogramRenderer) {
            bindToSpectrogramRenderer((SpectrogramRenderer) renderer);
            bindToSeriesRenderer(new SeriesRenderer());
        } else if (renderer instanceof ImageVectorDataSetRenderer) {
            bindToImageVectorDataSetRenderer((ImageVectorDataSetRenderer) renderer);
        } else {
            bindToSpectrogramRenderer(new SpectrogramRenderer(null, null));
            bindToSeriesRenderer(new SeriesRenderer());
        }
        JMenuItem mi= ac.getPlotFor(panel).getController().getPanelPropsMenuItem();
        if ( mi!=null ) mi.setIcon( renderer.getListIcon() );
        renderer.setId( "rend_"+panel.getId());
        ac.bind(panel, Panel.PROP_LEGENDLABEL, renderer, Renderer.PROP_LEGENDLABEL, getLabelConverter() );
        ac.bind(panel, Panel.PROP_DISPLAYLEGEND, renderer, Renderer.PROP_DRAWLEGENDLABEL);
        ac.bind(panel, Panel.PROP_ACTIVE, renderer, Renderer.PROP_ACTIVE );
    }

    /**
     * Do initialization to get the panel and attached plot to have reasonable
     * settings.
     * preconditions:
     *   renderType has been identified for the panel.
     * postconditions:
     *   panel's plotDefaults are set based on metadata and autoranging.
     *   listening plot may invoke its resetZoom method.
     *
     * @param autorange
     */
    private synchronized void doResetRanges() {
        logger.finest("doResetRanges...");
        setStatus("busy: do autorange");
        changesSupport.performingChange(this, PENDING_RESET_RANGE);

        Plot plot = dom.controller.getPlotFor(panel);

        Panel panelCopy = (Panel) panel.copy();
        panelCopy.setId("");
        panelCopy.setParentPanel("");
        panelCopy.getPlotDefaults().syncTo( plot, Arrays.asList(DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID) );

        DataSourceController dsc= getDataSourceFilter().getController();

        QDataSet fillDs = processDataSet( panel.getComponent(), getDataSourceFilter().controller.getFillDataSet() );
        Map props= processProperties( panel.getComponent(), dsc.getFillProperties() ); //TODO: support components

        if (dom.getOptions().isAutolabelling()) { //TODO: this is pre-autolabel property.

            doMetadata(panelCopy, props, fillDs );

            String reduceRankString = getDataSourceFilter().controller.getReduceDataSetString();
            if (dsf.controller.getReduceDataSetString() != null) { //TODO remove dsf slicing
                String title = panelCopy.getPlotDefaults().getTitle();
                title += "!c" + reduceRankString;
                panelCopy.getPlotDefaults().setTitle(title);
            }
            if ( !panel.getComponent().equals("") ) {
                String title = panelCopy.getPlotDefaults().getTitle();
                title += "!c%{CONTEXT}";
                panelCopy.getPlotDefaults().setTitle(title);
            }
        }

        if (dom.getOptions().isAutoranging()) { //this is pre-autorange property, but saves time if we know we won't be autoranging.
            
            doAutoranging( panelCopy,props,fillDs );

            Renderer newRenderer = getRenderer();
            if (newRenderer instanceof SeriesRenderer && fillDs != null) {
                QDataSet d = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                if (d != null) {
                    ((SeriesRenderer) newRenderer).setCadenceCheck((d.property(QDataSet.CADENCE) != null));
                } else {
                    ((SeriesRenderer) newRenderer).setCadenceCheck(true);
                }
            }
            
        }

        if ( panel.getComponent().equals("") && panel.isAutolabel() ) panel.setLegendLabel( panelCopy.getLegendLabel() );

        panelCopy.getPlotDefaults().getXaxis().setAutorange(true); // this is how we distinguish it from the original, useless plot defaults.
        panelCopy.getPlotDefaults().getYaxis().setAutorange(true);
        panelCopy.getPlotDefaults().getZaxis().setAutorange(true);

        if ( logger.isLoggable(Level.FINEST) ) {
            logger.finest( String.format( "done, autorange  x:%s, y:%s ",
                    panelCopy.getPlotDefaults().getXaxis().getRange().toString(),
                    panelCopy.getPlotDefaults().getYaxis().getRange().toString() ) );
        }

        panel.setPlotDefaults( panelCopy.getPlotDefaults() );
        // and hope that the plot is listening.

        setStatus("done, autorange");

        changesSupport.changePerformed(this, PENDING_RESET_RANGE);
    }


    /**
     * extract properties from the data and metadata to get axis labels, fill values, and
     * preconditions:
     *    fillData is set.
     *    fillProperties is set.
     * postconditions:
     *    metadata is inspected to get axis labels, fill values, etc.
     *    renderType is determined and set.
     * @param autorange
     * @param interpretMetadata
     */
    private static void doMetadata( Panel panelCopy, Map<String,Object> properties, QDataSet fillDs ) {

        panelCopy.getPlotDefaults().getXaxis().setLabel("");
        panelCopy.getPlotDefaults().getYaxis().setLabel("");
        panelCopy.getPlotDefaults().getZaxis().setLabel("");
        panelCopy.getPlotDefaults().setTitle("");
        panelCopy.setLegendLabel("");
        
        doInterpretMetadata(panelCopy, properties, panelCopy.getRenderType());

        PanelUtil.unitsCheck(properties, fillDs); // DANGER--this may cause overplotting problems in the future by removing units

    }

    private static void doInterpretMetadata( Panel panelCopy, Map properties, RenderType spec) {

        Object v;
        final Plot plotDefaults = panelCopy.getPlotDefaults();

        if ((v = properties.get(QDataSet.TITLE)) != null) {
            plotDefaults.setTitle((String) v);
        }
        String legendLabel= null;
        if ((v = properties.get(QDataSet.NAME)) != null) {
            legendLabel= (String)v;
        }
        if ((v = properties.get(QDataSet.LABEL)) != null) {
            legendLabel= (String)v;
        }
        if ( legendLabel!=null ) {
            panelCopy.setLegendLabel((String) legendLabel);
        }

        if ( spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {
            if ( (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getZaxis().setLog(v.equals("log"));
            }

            if ( (v = properties.get(QDataSet.LABEL)) != null) {
                plotDefaults.getZaxis().setLabel((String) v);
            }

            if ( (v = properties.get(QDataSet.DEPEND_1)) != null) {
                Map m = (Map) v;
                Object v2 = m.get(QDataSet.LABEL);
                if (v2 != null) {
                    plotDefaults.getYaxis().setLabel((String) v2);
                }

            }
        } else { // hugeScatter okay
            if ( (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getYaxis().setLog(v.equals("log"));
            }

            if ( (v = properties.get(QDataSet.LABEL)) != null) {
                plotDefaults.getYaxis().setLabel((String) v);
            }

            if (spec == RenderType.colorScatter) {
                v = properties.get(QDataSet.PLANE_0);
                if (v != null) {
                    Map m = (Map) v;
                    Object v2 = m.get(QDataSet.LABEL);
                    if (v2 != null) {
                        plotDefaults.getZaxis().setLabel((String) v2);
                    }
                } else {
                    plotDefaults.getZaxis().setLabel("");
                }
            } else {
                plotDefaults.getZaxis().setLabel("");
            }

        }

        if ((v = properties.get(QDataSet.DEPEND_0)) != null) {
            Map m = (Map) v;
            Object v2 = m.get(QDataSet.LABEL);
            if ( v2 != null) {
                plotDefaults.getXaxis().setLabel((String) v2);
            }

        }

    }

    /**
     * guess the cadence of the dataset tags, putting the rank 0 dataset cadence
     * into the property QDataSet.CADENCE of the tags ds.  fillDs is used to
     * identify missing values, which are skipped for the cadence guess.
     *
     * @param xds the tags for which the cadence is determined.
     * @param fillDs a dependent dataset possibly will fill values, or null.
     */
    private static void guessCadence( MutablePropertyDataSet xds, QDataSet fillDs ) {
        if ( xds.length()<2 ) return;

        RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(xds, fillDs);
        if ( cadence!=null && "log".equals(cadence.property(QDataSet.SCALE_TYPE) ) ) {
            xds.putProperty( QDataSet.SCALE_TYPE, "log" );
        }
        xds.putProperty(QDataSet.CADENCE, cadence);
    }

    /**
     * this is the old updateFillSeries and updateFillSpectrogram code.  This calculates
     * ranges and preferred symbol settings, and puts the values in panelCopy.plotDefaults.
     * The dom Plot containing this panel should be listening for changes in panel.plotDefaults,
     * and can then decide if it wants to use the autorange settings.
     * @param cpanel
     * @param props
     * @param spec
     */
    private static void doAutoranging( Panel panelCopy, Map<String,Object> props, QDataSet fillDs ) {

        RenderType spec = panelCopy.getRenderType();

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }


        if (spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.rank()>1 ) {
                    yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                }
            }

            guessCadence((MutablePropertyDataSet) xds, null);
            guessCadence((MutablePropertyDataSet) yds, null);

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, (Map) props.get(QDataSet.DEPEND_1));

            //QDataSet hist= getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;
            //if ( false && hist!=null ) {
            //    desc= AutoplotUtil.autoRange( hist, fillDs, props );
            //} else {
                desc = AutoplotUtil.autoRange( fillDs, props );
            //}

            panelCopy.getPlotDefaults().getZaxis().setRange(desc.range);
            panelCopy.getPlotDefaults().getZaxis().setLog(desc.log);
            panelCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            panelCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);
            panelCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            panelCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);

        } else {

            boolean isSeries;
            QDataSet depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            isSeries = (depend0 == null || DataSetUtil.isMonotonic(depend0));
            if (isSeries) {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.SOLID);
            } else {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
            }

            panelCopy.getStyle().setLineWidth(1.0f);

            QDataSet hist= null; //getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;
            if ( false && hist!=null ) {
                desc= AutoplotUtil.autoRange( hist, fillDs, props );
            } else {
                desc = AutoplotUtil.autoRange( fillDs, props );
            }

            panelCopy.getPlotDefaults().getYaxis().setLog(desc.log);
            panelCopy.getPlotDefaults().getYaxis().setRange(desc.range);

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            guessCadence((MutablePropertyDataSet) xds, fillDs);

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            panelCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            panelCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);

            if (spec == RenderType.colorScatter) {
                AutoplotUtil.AutoRangeDescriptor zdesc = AutoplotUtil.autoRange((QDataSet) fillDs.property(QDataSet.PLANE_0),
                        (Map) props.get(QDataSet.PLANE_0));
                panelCopy.getPlotDefaults().getZaxis().setLog(zdesc.log);
                panelCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                panelCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);

            }

            if (fillDs.length() > LARGE_DATASET_COUNT) {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
                panelCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                panelCopy.getStyle().setSymbolSize(1.0);
            } else {
                panelCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                    panelCopy.getStyle().setSymbolSize(1.0);
                } else {
                    panelCopy.getStyle().setSymbolSize(3.0);
                }

            }

        }
    }

    /**
     * return the plot containing this panel's renderer, or null.
     * @return the plot containing this panel's renderer, or null.
     */
    public DasPlot getDasPlot() {
        Plot p= dom.controller.getPlotFor(panel);
        if ( p==null ) return null;
        return p.controller.getDasPlot();
    }

    private DasColorBar getColorbar() {
        Plot p= dom.controller.getPlotFor(panel);
        if ( p==null ) throw new IllegalArgumentException("no plot found for panel");
        return p.controller.getDasColorBar();
    }

    /**
     * return the data source and filter for this panel.
     * @return
     */
    public DataSourceFilter getDataSourceFilter() {
        return dsf;
    }

    protected void maybeCreateDasPeer(){
        Renderer oldRenderer = getRenderer();
        Renderer newRenderer = AutoplotUtil.maybeCreateRenderer( panel.getRenderType(), oldRenderer, getColorbar() );

        if (oldRenderer != newRenderer || getDasPlot()!=newRenderer.getParent() ) {
            if ( oldRenderer != newRenderer ) setRenderer(newRenderer);

            DasPlot plot = getDasPlot();

            DasPlot oldPlot=null;
            if (oldRenderer != null) {
                oldPlot= oldRenderer.getParent();
                if ( oldPlot!=null && oldPlot!=getDasPlot() ) oldRenderer.getParent().removeRenderer(oldRenderer);
                if ( oldRenderer!=newRenderer ) plot.removeRenderer(oldRenderer);
            }
            if ( oldPlot==null || oldRenderer!=newRenderer ) plot.addRenderer(newRenderer);

            logger.finest("plot.addRenderer "+plot+" "+newRenderer);
            if (getDataSourceFilter().controller.getFillDataSet() != null) {
                // this is danger code, I think inserted to support changing render type.
                QDataSet fillDs= processDataSet(panel.getComponent(), getDataSourceFilter().controller.getFillDataSet());
                setDataSet(fillDs);
            }
        }

    }

    /**
     * used to explicitly set the rendering type.  This installs a das2 renderer
     * into the plot to implement the render type.
     *
     * preconditions:
     *   renderer type has been identified.
     * postconditions:
     *   das2 renderer peer is created and bindings made.
     * @param renderType
     */
    public void doResetRenderType(RenderType renderType) {
        Panel parentPanel= getParentPanel();
        if ( parentPanel != null ) {
            parentPanel.setRenderType(renderType);
            return;
        }

        for ( Panel ch: getChildPanels() ) {
            ch.renderType= renderType;  // we don't want to enter doResetRenderType.
            ch.getController().maybeCreateDasPeer();
        }

        maybeCreateDasPeer();
    }

    public synchronized void bindToSeriesRenderer(SeriesRenderer seriesRenderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(panel.style, "lineWidth", seriesRenderer, "lineWidth");
        ac.bind(panel.style, "color", seriesRenderer, "color");
        ac.bind(panel.style, "symbolSize", seriesRenderer, "symSize");
        ac.bind(panel.style, "symbolConnector", seriesRenderer, "psymConnector");
        ac.bind(panel.style, "plotSymbol", seriesRenderer, "psym");
        ac.bind(panel.style, "fillColor", seriesRenderer, "fillColor");
        ac.bind(panel.style, "fillToReference", seriesRenderer, "fillToReference");
        ac.bind(panel.style, "reference", seriesRenderer, "reference");
        ac.bind(panel.style, "antiAliased", seriesRenderer, "antiAliased");
    }

    public void bindToSpectrogramRenderer(SpectrogramRenderer spectrogramRenderer) {
        ApplicationController ac = this.dom.controller;

        ac.bind(panel.style, "rebinMethod", spectrogramRenderer, "rebinner");
        if ( spectrogramRenderer.getColorBar()!=null )
            ac.bind(panel.style, "colortable", spectrogramRenderer.getColorBar(), "type");

    }

    public void bindToImageVectorDataSetRenderer(ImageVectorDataSetRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(panel.style, "color", renderer, "color");
    }

    /**
     * special converter that fills in %{CONTEXT} macro, or inserts it when label is consistent with macro.
     * @return
     */
    private Converter getLabelConverter() {
        return new Converter() {
            @Override
            public Object convertForward(Object value) {
                String title= (String)value;
                if ( title.contains("%{CONTEXT}" ) ) {
                    String contextStr="";
                    if ( panel!=null ) {
                        if ( dataSet!=null ) {
                            contextStr= DataSetUtil.contextAsString(dataSet);
                        }
                    }
                    title= title.replaceAll("%\\{CONTEXT\\}", contextStr );
                }
                return title;
            }

            @Override
            public Object convertReverse(Object value) {
                String title= (String)value;
                String ptitle=  panel.getLegendLabel();
                if (ptitle.contains("%{CONTEXT}") ) {
                    String[] ss= ptitle.split("%\\{CONTEXT\\}",-2);
                    if ( title.startsWith(ss[0]) && title.endsWith(ss[1]) ) {
                        return ptitle;
                    }
                }
                return title;
            }
        };
    }

    @Override
    public boolean isPendingChanges() {
        return getDataSourceFilter().controller.isPendingChanges() || super.isPendingChanges();
    }

    private void setStatus(String string) {
        this.dom.controller.setStatus(string);
    }

    public String toString() {
        return "" + this.panel + " controller";
    }
}
