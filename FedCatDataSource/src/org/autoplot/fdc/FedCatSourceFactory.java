/* Copyright (C) 2019 Chris Piker, Jeremy Faden
 * 
 * This package, org.autoplot.dfc, is part Autoplot <autoplot.org>.  It provides an
 * interface to the Das2 Federated Catalog (DFC) system.
 *
 * Autoplot is open source
 */
package org.autoplot.fdc;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.CompletionContext;
import java.util.List;
import org.autoplot.datasource.URISplit;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.catalog.DasNode;
import org.das2.catalog.DasNodeFactory;
import org.das2.catalog.DasSrcNode;
import org.das2.catalog.DasResolveException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;

/**  Generates data sources based off URIs of the form: vap+dfc:LOCATION
 *
 * @author cwp
 */
public class FedCatSourceFactory extends AbstractDataSourceFactory
{
	private static final Logger LOGGER = Logger.getLogger("apdss.dc");

	/** Break a catalog URI down into parts
	 * 
	 * Federated Catalog Paths are very different from many Autoplot URIs,
	 * provide parsing for these.
	 * 
	 * @param sUri   - A full Autoplot URI including the type, should start with 
	 *                     vap+dc
	 * @return A Map<String,String> that always has the following keys:
	 *         "handler" - Should always be 'vap+dc'
	 *         "rootUrl" - The URL to the root node, will often be null, meaning that the
	 *                     URLs must be found in the global catalog
	 *         "path"    - The sub path of interest offset from the root URL
	 *         "params"  - The query parameters.  Will be null for default datasets
	 */
	/* public static Map<String,String> splitDasCatUri(String sUri) {
		Map<String, String> map = new HashMap<>();
		map.put("handler", null);  // Should always be "vap+dc"
		map.put("rootUrl", null);  // Will be null for global catalog
		map.put("path", null);     // The path from the root item
		map.put("params", null);   // The query parameters
		
		int i = sUri.indexOf(":");
		if(i < 0) return map; // Null case 
		
		String sHndrl = sUri.substring(0, i);
		if(!sHndrl.equals("vap+dc"))
			throw new IllegalArgumentException("Not a catalog URI, 'vap+dc:' prefix missing.");
		
		
		
		sUri = sUri.substring(i);
		i = sUri.indexOf('?');
		if(i == -1){
			// no params case
			
		}
		
		
		return map;
	}
	*/
	
	@Override
	public DataSource getDataSource(URI uri) throws Exception
	{
		ProgressMonitor mon = new NullProgressMonitor();
		DasNode node = DasNodeFactory.getNearestNode(uri.toString(), mon, false);
		return new FedCatSource(uri, node);
	}
	
	/** This data source is pretty much only discovery, so of course we return true here.
	 * @return true
	 */
	@Override
    public boolean supportsDiscovery() { return true; }

	@Override
	public boolean isFileResource() {
		return false;
	}

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
		Map<String,String> dParams = URISplit.parseParams(split.params);
		 
		// If the URI provided does not reference a source type then it's not complete
		String sNodeUrl = DasNodeFactory.defaultDataPath();
		//String sNodeUrl = null;
		if( ! sUrl.equals("vap+dc:")) sNodeUrl = split.file;
		
		DasNode node;
		try{
			node = DasNodeFactory.getNode(sNodeUrl, mon, false);
		} catch(DasResolveException | IOException | ParseException ex){
			return true;
		}
		
		if(node == null) return true;  // Need an actual node to load data
		if(!node.isSrc()) return true; // And it should be a source node
		 
		DasSrcNode srcNode = (DasSrcNode)node;
		 
		// If the query passes, then there is no need for the source dialog
		return  ! srcNode.queryVerify(dParams); 
    }

	@Override
	public List<CompletionContext> getCompletions(
		CompletionContext cc, ProgressMonitor mon
	) throws Exception {
		
		String sUrl = cc.surl;
		URISplit split = URISplit.parse(sUrl);
		//Map<String,String> dParams = URISplit.parseParams(split.params);
		
		//FIXME: Go into filesystem completions if URL starts with a common
		//       filesystem type, such as https://, file://, etc.
		
		String sNodeUrl = DasNodeFactory.defaultDataPath();
		//String sNodeUrl = null;
		if( ! sUrl.equals("vap+dc:")) sNodeUrl = split.file;
		
		DasNode node;
		try{
			node = DasNodeFactory.getNode(sNodeUrl, mon, false);
		} catch(DasResolveException | IOException | ParseException ex){
			return Collections.emptyList();
		}
		
		if(node == null) return Collections.emptyList();  // Need node for completions
		
		List<CompletionContext> lComp = new ArrayList<>();
		
		/*  Directory completions don't really work here.
		if(node.isSrc()){
			DasDirNode dir = (DasDirNode)node;
			String aList[] = dir.list();
			String sSep = dir.pathSeparator(mon);
			for(String sSub: aList){
				CompletionContext ccChild;
				DasNode child = dir.get(sSub);
				// Args are:
				// 1. Context, 2. 
				ccChild = new CompletionContext(
					CompletionContext.CONTEXT_PARAMETER_NAME, dir.childPath(child),
					sSep + sSub, child.type()
				);
				
				lComp.add(ccChild);
			}
		}
		*/
		
		// Next up, source completions...
		return lComp;
	}
}