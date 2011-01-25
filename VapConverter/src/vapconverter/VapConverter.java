/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package vapconverter;

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.persistence.DatumPersistenceDelegate;
import org.das2.persistence.DatumRangePersistenceDelegate;
import org.das2.persistence.UnitsPersistenceDelegate;
import org.das2.util.AboutUtil;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DomNode;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.state.BindingPersistenceDelegate;
import org.virbo.autoplot.state.ConnectorPersistenceDelegate;
import org.virbo.autoplot.state.SerializeUtil;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.state.TypeSafeEnumPersistenceDelegate;
import org.virbo.autoplot.state.Vap1_00Scheme;
import org.virbo.autoplot.state.VapScheme;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * convert old vap files that were based on JavaBeans' XMLEncoder to
 * das2-based file save.  Both are .vap files, but we dropped the old
 * JavaBeans format because as the DOM evolved, it couldn't evolve as
 * well.
 * 
 * @author jbf
 */
public class VapConverter {
    public void doConvert( String oldFile, String newFile ) throws IOException {
        InputStream in=
                new FileInputStream( oldFile );
        XMLDecoder decode = new XMLDecoder( in );

        // add a direct reference to these guys for compile-all script.
        new DatumRangePersistenceDelegate();
        new UnitsPersistenceDelegate();
        new DatumPersistenceDelegate();
        new TypeSafeEnumPersistenceDelegate();
        new BindingPersistenceDelegate();
        new ConnectorPersistenceDelegate();

        Object state = decode.readObject();

        Application app = (Application) state;
        for ( Panel p : app.getPanels()) {
            if (p.getRenderType() == null) {
                p.setRenderTypeAutomatically(RenderType.series);
            }
        }

        //StatePersistence.saveState( new File( newFile ), app );
        //Taken from StatePersistence so that we could promote the file to the
        //latest version.
        Document document=null;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(StatePersistence.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }

        VapScheme scheme= new Vap1_00Scheme();
        Element element = SerializeUtil.getDomElement( document, (DomNode)state, scheme );

        Element vap= document.createElement("vap");
        vap.appendChild(element);
        vap.setAttribute( "domVersion", scheme.getId() );
        vap.setAttribute( "appVersionTag", AboutUtil.getReleaseTag() );

        document.appendChild(vap);

        // possibly apply transforms here to make a more recent vap file.
        StatePersistence.writeDocument( new File( newFile ), document );

    }
}
