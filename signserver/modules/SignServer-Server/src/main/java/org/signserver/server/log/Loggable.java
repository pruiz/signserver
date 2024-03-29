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
package org.signserver.server.log;

/**
 * Interface for logging a single value.
 * Workers will implement this interace for log values when setting
 * the log map to enable logger implementations to do lazy evaluation
 * of log strings.
 * 
 * @author Marcus Lundblad
 * @version $Id: Loggable.java 7534 2016-06-21 11:22:43Z malu9369 $
 */
public interface Loggable {
    /**
     * Gets a log string for this instance of Loggable.
     * 
     * @return The log string value
     */
    @Override
    String toString();
}
