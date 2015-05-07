
package org.virbo.autoplot.dom;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 * Class for containing the logic of how macros are implemented.
 * https://sourceforge.net/p/autoplot/feature-requests/426/
 * 
 * This currently supports just the plotElement and plot.
 * 
 * @author faden@cottagesystems.com
 */
public class LabelConverter extends Converter {
    
    private static Logger logger= LoggerManager.getLogger("autoplot.dom.labelConverter");
            
    PlotElement plotElement;
    Plot plot;
    Application dom;
    
    private PlotElement getFocusPlotElement() {
        PlotElement pe;
        if ( plotElement!=null ) {
            pe= plotElement;
        } else {
            List<PlotElement> pes= DomUtil.getPlotElementsFor(dom, plot);
            if ( pes.size()==1 ) {
                pe= pes.get(0);
            } else if ( pes.isEmpty() ) {
                pe= null;
            } else {
                logger.warning("multiple plot elements found, using first");
                pe= pes.get(0);
            }
        }
        return pe;
    }
    
    @Override
    public Object convertForward(Object value) {
        PlotElement pe= getFocusPlotElement();
        
        String title= (String)value;
        if ( title.contains("CONTEXT" ) ) {
            if ( pe!=null ) {
                QDataSet dataSet= pe.getController().getDataSet();
                if ( dataSet!=null ) {
                    String contextStr= DataSetUtil.contextAsString(dataSet);
                    title= insertString( title, "CONTEXT", contextStr );
                }
            }
        }
        if ( title.contains("USER_PROPERTIES" ) ) {
            if ( pe!=null ) {
                QDataSet dataSet= pe.getController().getDataSet();
                if ( dataSet!=null ) {
                    Map<String,Object> props= (Map<String, Object>) dataSet.property(QDataSet.USER_PROPERTIES);
                    title= DomUtil.resolveProperties( title, "USER_PROPERTIES", props );
                }
            }
        }
        
        if ( title.contains("METADATA" ) ) {
            if ( pe!=null ) {
                DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, pe.getDataSourceFilterId() );
                if ( dsf!=null ) { // ought not to be!
                    Map<String,Object> props= (Map<String, Object>) dsf.getController().getRawProperties(); //TODO: this is a really old name that needs updating...
                    title= DomUtil.resolveProperties( title, "METADATA", props );
                }
            }
        }
                
        if ( title.contains("TIMERANGE") ) {
            DatumRange tr= PlotElementControllerUtil.getTimeRange( dom, pe );
            if ( tr==null ) {
                title= insertString( title, "TIMERANGE", "(no timerange)" );
            } else {
                title= insertString( title, "TIMERANGE",tr.toString() );
            }
        }
        //logger.fine("<--"+value + "-->"+title );
        //see convertReverse, which must be done as well.
        if ( title.contains("COMPONENT") ) {
            String ss="";
            if ( pe!=null ) {
                ss= pe.getComponent();
            }
            title= insertString( title, "COMPONENT", ss );
        }
        
        return title;
    }

    @Override
    public Object convertReverse(Object value) {
        String title= (String)value;
        
        String ptitle;
        if ( plotElement!=null ) {
            PlotElement pe= getFocusPlotElement();
            ptitle=  pe.getLegendLabel();
        } else {
            ptitle= plot.getTitle();
        }
        
        if ( containsString( ptitle, "CONTEXT", title) ) {
            title= ptitle;
        } else if ( ptitle.contains( "%{USER_PROPERTIES" ) ) { //kludgy
            title= ptitle;
        } else if ( ptitle.contains( "%{METADATA" ) ) { //kludgy
            title= ptitle;
        } else if ( containsString( ptitle, "TIMERANGE", title ) ) {
            title= ptitle;
        } else if ( containsString( ptitle, "COMPONENT", title ) ) {
            title= ptitle;
        }
        return title;
    }

    /**
     * replace %{LABEL} or $(LABEL) with value.
     * @param title
     * @param label
     * @param value
     * @return
     */
    protected static String insertString( String title, String label, String value ) {
        String search;
        search= "%{"+label+"}";
        if ( title.contains( search ) ) {
            title= title.replace( search, value );
        }
        search= "$("+label+")";
        if ( title.contains( search ) ) {
            title= title.replace( search, value );
        }
        return title;
    }

    /**
     * return true if %{LABEL} or $(LABEL) is found.
     * @param ptitle
     * @param label
     * @param value
     * @return
     */
    protected static boolean containsString( String ptitle, String label, String value ) {
        String search;
        String[] ss=null;
        search= "%{"+label+"}";
        if ( ptitle.contains( search ) ) {
            ss= ptitle.split("%\\{"+label+"\\}",-2);
        } else {
            search= "$("+label+")";
            if ( ptitle.contains( search ) ) {
                ss= ptitle.split("\\$\\("+label+"\\)",-2);
            }
        }
        if ( ss!=null && value.startsWith(ss[0]) && value.endsWith(ss[1]) ) {
            return true;
        } else {
            return false;
        }
    }    
}
