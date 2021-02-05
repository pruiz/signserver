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
package org.signserver.common.data;

import org.signserver.common.ProcessRequest;

/**
 * Data holder wrapping a legacy ProcessRequest.
 *
 * @author Markus Kilås
 * @version $Id: LegacyRequest.java 7669 2016-08-24 20:01:10Z netmackan $
 */
public class LegacyRequest extends Request {

    private final ProcessRequest legacyRequest;

    public LegacyRequest(ProcessRequest legacyRequest) {
        this.legacyRequest = legacyRequest;
    }

    public ProcessRequest getLegacyRequest() {
        return legacyRequest;
    }
    
}
