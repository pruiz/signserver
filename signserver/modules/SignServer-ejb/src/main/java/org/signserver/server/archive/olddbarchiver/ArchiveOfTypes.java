/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.server.archive.olddbarchiver;

/**
 * Representation of what to archive.
 *
 * @author Markus Kilås
 * @version $Id: ArchiveOfTypes.java 2844 2012-10-16 14:18:09Z netmackan $
 */
 public enum ArchiveOfTypes {
    
    /** Archive only the request. */
    REQUEST, 
    
    /** Archive only the response. */
    RESPONSE, 
    
    /** Archive both the request and response. */
    REQUEST_AND_RESPONSE
    
}
