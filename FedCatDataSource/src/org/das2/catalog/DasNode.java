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


/** A single node from the das2 federated catalog
 *
 * @author cwp
 */
public interface DasNode
{
	/** Get the node type.
	 * @return A string representing the node type
	 */
	public String type();
	
	/** get the node name
	 * @return  The human readable name of the node, not it's path ID */
	public String name();
	
	/** Get a property of the node, or the default
	 * 
	 * Fragment path navigation avoids intermediate lookups.  Unless the default is
	 * used, the return types are limited to the following:
	 * 
	 *   String
	 *   Integer
	 *   Double
	 *   String
	 *   List<Object>
	 *   Map<String, Object> 
	 * 
	 * @param sFragment  The fragment path, for example "tech_contact/0/email"
	 * @param oDefault   The default object to return if nothing exists at the
	 *                   fragment location
	 * @return 
	 */
	public Object property(String sFragment, Object oDefault);
	
	/** Get a property of the node with an expected return type, or the default
	 * 
	 * @param sFragment
	 * @param oDefault
	 * @param expect
	 * @return 
	 */
	public Object property(String sFragment, Class expect, Object oDefault);
	
	
	/** Get a property of a node, or throw
	 * 
	 * @param sFragment
	 * @return
	 * @throws DasResolveException 
	 */
	public Object property(String sFragment) throws DasResolveException;
	
	/** Get a property of a node with an expected type, or throw
	 * 
	 * @param sFragment
	 * @param expect
	 * @return
	 * @throws DasResolveException 
	 */
	public Object property(String sFragment, Class expect) throws DasResolveException;
	
	
	/** Get a property of a node given a fragment path with expected return types
	
	/** get the node path
	 * @return  The catalog path to this node.  For root nodes this is null */
	public String path();
	
	/** A summary of the node
	 * @return A short string describing the node, not an info dump of the contents */
	@Override
	public String toString();
	
	/** Is this object a detached root of a catalog tree.
	 * Note that the build in root URLs are always detached roots because there is no
	 * higher node to find.  A detached source or info node can still be a root node 
	 * <b>without</b> also being a directory.
	 * 
	 * @return true if no higher node is reachable from this one.
	 */
	public boolean isRoot();
	
	/** Can this catalog node provide data
	 * @return true if this node describes one or more data sources
	 */
	public boolean isSrc();
	
	/** Can this catalog node have the sub-nodes?
	 * @return  true if this node can have child nodes, not that it necessarily
	 *          contains any. */
	public boolean isDir();
	
	/** Is this object an information node.
	 * @return true if this node in the catalog provides a description of a mission, 
	 *         spacecraft, instrument, person or any other item category
	 */
	public boolean isInfo();

	/** Return the highest node reachable by this catalog node.  
	 * @return the highest node reachable by this catalog node, which may just be itself.
	 */
	public DasNode getRoot();
	
}
