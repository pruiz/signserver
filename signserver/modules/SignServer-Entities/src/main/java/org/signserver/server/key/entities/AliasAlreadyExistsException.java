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
package org.signserver.server.key.entities;

/**
 *
 * @author Markus Kilås
 * @version $Id$
 */
public class AliasAlreadyExistsException extends Exception {
    
    private String alias;

    public AliasAlreadyExistsException(String keyAlias) {
        super("Duplicate alias: " + keyAlias);
        this.alias = keyAlias;
    }

    public String getAlias() {
        return alias;
    }
    
}
