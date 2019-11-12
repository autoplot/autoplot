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

import org.das2.util.monitor.ProgressMonitor;

/** All directory node objects can have children
 *
 * @author cwp
 */
public interface DasDirNode extends DasNode {
	
	/** List child nodes of this directory type item
	 * 
	 * @return A list of child node string ids.  Return values may be used in the get node
	 *         function below.
	 */
	public String[] list();
	
	/** Get a child node by it's ID.
	 * 
	 * @param sChildId
	 * @return The child node, or null if no child node has the id sChildId
	 */
	public DasNode get(String sChildId);
	
	/** Given a child object get the complete path to it
	 * @param child The child object for which the path is desired.  
	 * @return 
	 */
	public String childPath(DasNode child);
	
	/** Get the separator string to place after my path but before then names of any 
	 * child items when generating a child path string.
	 * 
	 * @param mon The separator may be defined in the object data which may trigger
	 *            Phase-2 construction.  Since this is a network operation a progress
	 *            monitor may be supplied.
	 * @return The path separator string, which may be zero length (and that's okay).
	 */
	public String pathSeparator(ProgressMonitor mon);
	
	/** Walk down a given sub-path and retrieve a fully constructed descendant node.
	 *
	 * @param sSubPath A sub-path to resolve into a fully loaded node.  If this node is a 
	 *        root catalog, then the sub-path is the complete path.
	 * @param mon A progress monitor since sub-node lookup can involve network operations
	 * @return The full constructed child node object.
	 * @throws org.das2.util.catalog.DasResolveException If the given sub-path could not
	 *         be resolved by the catalog.
	 */
	public DasNode resolve(String sSubPath, ProgressMonitor mon)
		throws DasResolveException;
	
	/** Walk down a given sub-path as far as possible and retrieve the closest descendant
	 * FIXME: Find a better name for this function, maybe resolveDeepest
	 * 
	 * @param sSubPath A sub-path to resolve into a fully loaded node.  If this node is a
	 *        root catalog, then the sub-path is the complete path.
	 * @param mon A progress monitor since sub-node lookup can involve network operations
	 * @return The deepest resolvable node which may just be "this", or even the "parent"
	 *         in cases where this node is a stub that can't even resolve itself.
	 */
	public DasNode nearest(String sSubPath, ProgressMonitor mon);

}
