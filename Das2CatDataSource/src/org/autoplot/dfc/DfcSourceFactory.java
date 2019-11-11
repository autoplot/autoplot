/* Copyright (C) 2019 Chris Piker
 * 
 * License: Whatever the rest of Autoplot is using
 */
package org.autoplot.dfc;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.Collections;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.CompletionContext;
import java.util.List;
import org.autoplot.datasource.URISplit;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.catalog.DasNode;
import org.das2.util.catalog.DasNodeFactory;
import org.das2.util.catalog.DasSrcNode;
import org.das2.util.catalog.DasResolveException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;

/**  Generates data sources based off URIs of the form: vap+dfc:LOCATION
 *
 * @author cwp
 */
public class DfcSourceFactory extends AbstractDataSourceFactory
{
	private static final Logger logger = Logger.getLogger("apdss.dfc");
	
	@Override
	public DataSource getDataSource(URI uri) throws Exception
	{
		ProgressMonitor mon = new NullProgressMonitor();
		DasNode node = DasNodeFactory.getNearestNode(uri.toString(), mon, false);
		return new DfcSource(uri, node);
	}
	
	/** This data source is pretty much only discovery, so of course we return true here.
	 * @return true
	 */
	@Override
    public boolean supportsDiscovery() { return true; }
	 
	 /** We have to reject the URI in order to pop up the inspection dialog box. 
	  * This makes sense because if the URI is good, folks just want to see the data.
	  * 
	  * @param sUrl
	  * @param lProblems
	  * @param mon
	  * @return 
	  */
	@Override
	public boolean reject(String sUrl, List<String> lProblems, ProgressMonitor mon) {
		URISplit split = URISplit.parse(sUrl);
		Map<String,String> params= URISplit.parseParams(split.params);
		 
		// If the URI provided does not reference a source type then it's not complete
		String sNodeUrl = null;
		if( ! sUrl.equals("vap+dc:")) sNodeUrl = split.file;
		
		DasNode node;
		try{
			node = DasNodeFactory.getNode(sNodeUrl, mon, false);
		} catch(DasResolveException | IOException | ParseException ex){
			return true;
		}
		 
		if(node == null) return true;
		 
		if(!node.isSrc()) return true;
		 
		DasSrcNode srcNode = (DasSrcNode)node;
		 
		// If the query passes, then there is no need for the source dialog
		return  ! srcNode.queryVerify(params); 
    }

	@Override
	public List<CompletionContext> getCompletions(
		CompletionContext cc, ProgressMonitor mon
	) throws Exception {
		
		return Collections.emptyList();
	}
}