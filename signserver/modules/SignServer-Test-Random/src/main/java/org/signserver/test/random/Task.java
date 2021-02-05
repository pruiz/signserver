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
package org.signserver.test.random;

/**
 * Representation of something to do.
 *
 * @author Markus Kilås
 * @version $Id: Task.java 2677 2012-09-19 10:21:28Z netmackan $
 */
public interface Task {
    void run() throws Exception;
}
