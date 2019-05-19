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
package org.signserver.module.xades.signer;

/**
 * Class containing TSA Properties.
 * 
 * Based on patch contributed by Luis Maia &lt;lmaia@dcc.fc.up.pt&gt;.
 *
 * @author Luis Maia <lmaia@dcc.fc.up.pt>
 * @version $Id$
 */
public class TSAParameters {
    private final String url;
    private final String username;
    private final String password;

    public TSAParameters(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public TSAParameters(String url) {
        this(url, null, null);
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
}
