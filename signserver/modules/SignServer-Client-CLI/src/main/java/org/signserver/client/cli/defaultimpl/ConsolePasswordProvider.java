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
package org.signserver.client.cli.defaultimpl;

/**
 * Interface for providers of a ConsolePasswordReader.
 *
 * @author Markus Kilås
 * @version $Id: ConsolePasswordProvider.java 6237 2015-09-15 12:42:48Z malu9369 $
 */
public interface ConsolePasswordProvider {
    ConsolePasswordReader createConsolePasswordReader();
}
