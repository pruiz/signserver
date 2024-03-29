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
package org.signserver.web;

import java.io.File;

/**
 * Servlet for downloading the AdminGUI.
 *
 * @author Markus Kilås
 * @version $Id: AdminGUIDistServlet.java 7739 2016-09-27 07:36:09Z malu9369 $
 */
public class AdminGUIDistServlet extends AbstractDistServlet {
    @Override
    protected File getFile() {
        return settings.getAdminGUIDistFile();
    }
}
