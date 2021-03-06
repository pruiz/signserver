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
package org.signserver.server;

import java.util.List;

/**
 * Common interface for components.
 * 
 * @author Marcus Lundblad
 * @version $Id: IComponent.java 7470 2016-06-14 07:41:02Z malu9369 $
 */
public interface IComponent {
    List<String> getFatalErrors(IServices services);
}
