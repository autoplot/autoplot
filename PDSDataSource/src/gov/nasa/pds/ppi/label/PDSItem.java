/* Copyright (c) 2003. All Rights Reserved.
 * 
 * This is the distribution of classes developed at IGPP/UCLA.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *  
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 * 
 * See the COPYING file located in the top-level-directory of
 * the archive of this library for complete text of license.
 *
 */
package gov.nasa.pds.ppi.label;

/**
 * PDSItem is a class that descibes the location within a
 * {@link PDSLabel} of one or more elements.
 *
 * @author      Todd King
 * @author      Planetary Data System
 * @version     1.0, 02/21/03
 * @since       1.0
 */
public class PDSItem {
	/** The index of the first element of the item */ 	
	public int		mStart = -1;
	/** The index of the end element of the item */
	public int		mEnd = -1;
	
 	/** Creates an instance of a PDSItem */
 	public PDSItem() {
 	}
 	
    /** 
     * Determines if a PDSItem is valid. A valid PDSItem is set to 
     * point to a range of elements within a {@link PDSLabel}.
	 * 
     * @return          <code>true</code> if a range is set.
     *					<code>false</code> is no range is set.
     *
     * @since           1.0
     */
	public boolean valid() 
	{ 
		return isValid(); 
	}
	
    /** 
     * Determines if a PDSItem is valid. A valid PDSItem is set to 
     * point to a range of elements within a {@link PDSLabel}.
	 * 
     * @return          <code>true</code> if a range is set.
     *					<code>false</code> is no range is set.
     *
     * @since           1.0
     */
	public boolean isValid() {
		if(mStart == -1) return false;
		if(mEnd == -1) return false;
		return true;
	}
	
    /** 
     * Clears all settings of the item. Once an item is cleared it no longer
     * points to a valid range of elements.
	 * 
     * @since           1.0
     */
	public void empty() {
		mStart = -1;
		mEnd = -1;
	}

	/** Outputs validity, and if valid the start index and end index */
	@Override
	public String toString(){
		if(isValid())
			return "valid:"+mStart+":"+mEnd;
		else
			return "invalid";
	} 
}
