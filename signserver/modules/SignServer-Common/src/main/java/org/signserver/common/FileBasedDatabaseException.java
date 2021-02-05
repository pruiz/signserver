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
package org.signserver.common;

/**
 * Exception thrown when there is a problem related to the file based database.
 *
 * @author Markus Kilås
 * @version $Id: FileBasedDatabaseException.java 2629 2012-09-07 12:53:58Z netmackan $
 */
public class FileBasedDatabaseException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public FileBasedDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileBasedDatabaseException(String message) {
        super(message);
    }
    
}
