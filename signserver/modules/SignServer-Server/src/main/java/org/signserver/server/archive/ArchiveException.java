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
package org.signserver.server.archive;

/**
 * Exception thrown if archiving fails for some reason.
 *
 * @author Markus Kilås
 * @version $Id: ArchiveException.java 2841 2012-10-16 08:31:40Z netmackan $
 */
public class ArchiveException extends Exception {

    public ArchiveException(Throwable cause) {
        super(cause);
    }

    public ArchiveException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArchiveException(String message) {
        super(message);
    }

}
