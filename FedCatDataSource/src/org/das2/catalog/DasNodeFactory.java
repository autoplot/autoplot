/* This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */

package org.das2.catalog;

import java.io.IOException;
import java.text.ParseException;
import org.das2.catalog.impl.NodeFactory;
import org.das2.util.monitor.ProgressMonitor;

/** Public static generator functions for das2 federated catalog node objects.
 *
 * @author cwp
 */
public class DasNodeFactory
{
	/** Get a node from the global node map by URL. 
	 * 
	 * This function tries to load and return the node for the given URL.  If the file
    * portion of the node is a recognized filesystem type then that exact URL is 
    * attempted.  For example:
    *
    * https://space.physics.uiowa.edu/juno/test/random_source.data
    * 
	 * would trigger a filesystem type lookup that expects an exact match.  While a URL
	 * such as:
	 * 
    * tag:das2.org,2012:test:/uiowa/juno/random_collection/das2
	 * 
	 * For space savings, tag:das2.org,2012: may be left off of the given URLs.
	 * 
	 * If nothing can be matched, null is return.  The resulting parsed node is saved
	 * in a cache to avoid repeated network traffic.
	 * 
	 * @param sUrl
	 * @param mon
	 * @param bReload - Reload the node definition from the original source
	 * @return The node requested, or throws an error
	 * @throws org.das2.catalog.DasResolveException
	 * @throws java.io.IOException
	 * @throws java.text.ParseException
	 */
	public static DasNode getNode(String sUrl, ProgressMonitor mon, boolean bReload) 
		throws DasResolveException, IOException, ParseException {
		
		return NodeFactory.getNode(sUrl, mon, bReload);
	}
	
	/** Kind of like traceroute, try to resolve successively longer paths until
	 * you get to one that fails.  For filesystem type URLS (http:, file:, etc.)
	 * this is the same as getNode().
	 * 
	 * @param sUrl An autoplot URL
	 * @param mon
	 * @param bReload
	 * @return The nearest loadable DasNode for the path specified.
	 * @throws org.das2.catalog.DasResolveException if a Filesystem style URL cannot
	 *         be loaded as a DasNode.
	 */
	public static DasNode getNearestNode(String sUrl, ProgressMonitor mon, boolean bReload) 
		throws DasResolveException 
	{
		return NodeFactory.getNearestNode(sUrl, mon, bReload);
	}

	public static String defaultDataPath()
	{
		return NodeFactory.DEFAULT_DATA_PATH;
	}
	
	public static String defaultTestPath()
	{
		return NodeFactory.DEFAULT_DATA_PATH;
	}
}
