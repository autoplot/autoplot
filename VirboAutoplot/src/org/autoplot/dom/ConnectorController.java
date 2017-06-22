/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.dom;

import org.das2.graph.ColumnColumnConnector;

/**
 * Controller class for a connector
 * @author jbf
 */
public class ConnectorController extends DomNodeController  {
    
    Connector connector;
    Application dom;
    ColumnColumnConnector dasConnector;
    
    public ConnectorController( Application dom, Connector connector ) {
        super(connector);
        this.connector= connector;
        this.dom= dom;
    }
    
    public void bindTo( ColumnColumnConnector c ) {
        ApplicationController ac= dom.controller;
        dasConnector= c;
        ac.bind( this.connector, Connector.PROP_BOTTOMCURTAIN, c, ColumnColumnConnector.PROP_BOTTOM_CURTAIN ); 
        ac.bind( this.connector, Connector.PROP_CURTAINOPACITYPERCENT, c, ColumnColumnConnector.PROP_CURTAIN_OPACITY_PERCENT );
        ac.bind( this.connector, Connector.PROP_FILL, c, ColumnColumnConnector.PROP_FILL );
        ac.bind( this.connector, Connector.PROP_FILLCOLOR, c, ColumnColumnConnector.PROP_FILL_COLOR );
        ac.bind( this.connector, Connector.PROP_VISIBLE, c, "visible" );
        ac.bind( this.connector, Connector.PROP_COLOR, c, "foreground" );
    }
    
    public ColumnColumnConnector getDasConnector() {
        return this.dasConnector;
    }
    
    public void removeBindings() {
        ApplicationController ac= dom.controller;
        ac.unbind( this.connector );
        //TODO: undelete the bindings.
    }

}
