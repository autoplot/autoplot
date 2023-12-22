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
 * PDSException implements an Exception class. Used by other
 * classes to return error information.
 *
 * @author      Todd King
 * @author      Planetary Data System
 * @version     1.0, 02/17/05
 * @since       1.0
 */
public class PDSException extends Exception {

   public PDSException() { }

   public PDSException(String msg) {
      super(msg);
   }

   public PDSException(Throwable cause) {
	   super(cause.getMessage(), cause);
   }
}
