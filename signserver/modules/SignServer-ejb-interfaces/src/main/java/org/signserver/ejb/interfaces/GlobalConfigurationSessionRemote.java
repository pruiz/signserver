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
package org.signserver.ejb.interfaces;

import javax.ejb.Remote;

/**
 * Common interface containing all the session bean methods.
 *
 * @version $Id: GlobalConfigurationSessionRemote.java 6969 2015-12-29 18:24:25Z netmackan $
 */
@Remote
public interface GlobalConfigurationSessionRemote extends GlobalConfigurationSession {
}
