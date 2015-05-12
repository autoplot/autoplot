
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
    boolean multiplePEWarning= false;
    
    private PlotElement getFocusPlotElement() {
        PlotElement pe;
        multiplePEWarning= false;
        if ( plotElement!=null ) {
            pe= plotElement;
        } else {
            List<PlotElement> pes= DomUtil.getPlotElementsFor(dom, plot);
            if ( pes.size()==1 ) {
                pe= pes.get(0);
            } else if ( pes.isEmpty() ) {
                pe= null;
            } else {
                pe= pes.get(0);
                multiplePEWarning= true;
            }
        }
        return pe;
    }
    
    @Override
    public Object convertForward(Object value) {
        PlotElement pe= getFocusPlotElement();
        
        String title= (String)value;
        boolean done= false;
        
        while ( !done ) {
            int unresolvedIndex= title.indexOf("%{");
            
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
            if ( title.contains("{PROPERTIES" ) ) {  // ${PROPERTIES.DEPEND_0.UNITS}
                if ( pe!=null ) {
                    QDataSet dataSet= pe.getController().getDataSet();
                    int i1= title.indexOf("{PROPERTIES")+12; // 12 includes the dot following
                    int i2= title.indexOf("}",i1);
                    String prop= title.substring(i1,i2);
                    String[] ss= prop.split("\\.",-2);
                    Object o=null;
                    if ( dataSet!=null ) {
                        for (String s : ss) {
                            o = dataSet.property(s);
                            if ( o instanceof QDataSet ) {
                                dataSet= (QDataSet)o;
                            }
                            if ( o==null ) break;
                        }
                    }
                    if ( o==null ) o="";
                    title= insertString( title, title.substring(i1-11,i2), o.toString() );
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
            
            int newUnresolvedIndex= title.indexOf("%{");
            
            done= ( newUnresolvedIndex==-1 || newUnresolvedIndex==unresolvedIndex );
            
        }
        
        if ( multiplePEWarning && ! title.equals(value) ) {
            logger.fine("multiple plot elements found, using first");
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
        } else if ( ptitle.contains( "%{PROPERTIES" ) ) { //kludgy
            title= ptitle;
        } else if ( ptitle.contains( "%{METADATA" ) ) { //kludgy
            title= ptitle;
        } else if ( containsString( ptitle, "TIMERANGE", title ) ) {
            title= ptitle;
        } else if ( containsString( ptitle, "COMPONENT", title ) ) {
            title= ptitle;
        }
        
        if ( multiplePEWarning && !title.equals(value) ) {
            logger.fine("multiple plot elements found, using first");              
        }
        return title;
    }

    /**
     * replace %{LABEL} or $(LABEL) with value.
     * @param title the string containing the macro.
     * @param label the label to replace, such as METADATA.SPACECRAFT.
     * @param value the value to insert
     * @return the new string with the value inserted.
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
