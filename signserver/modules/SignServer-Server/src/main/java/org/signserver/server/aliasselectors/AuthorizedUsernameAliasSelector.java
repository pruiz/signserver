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
package org.signserver.server.aliasselectors;

import java.util.Collections;
import java.util.List;
import javax.persistence.EntityManager;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.ProcessRequest;
import org.signserver.common.RequestContext;
import org.signserver.common.SignServerException;
import org.signserver.common.WorkerConfig;
import org.signserver.server.IProcessable;
import org.signserver.server.UsernamePasswordClientCredential;
import org.signserver.server.WorkerContext;
import org.signserver.server.cryptotokens.CryptoTokenHelper;

/**
 * Alias selector implementation selecting a key alias based
 * on an authorized username from the request.
 * 
 * @author Marcus Lundblad
 * @version $Id$
 */
public class AuthorizedUsernameAliasSelector implements AliasSelector {

    public static String PROPERTY_ALIAS_PREFIX = "ALIAS_PREFIX";
    
    private String prefix;
    private String defaultKey;
    
    @Override
    public void init(final int workerId, final WorkerConfig config,
                     final WorkerContext workerContext, EntityManager workerEM) {
        prefix = config.getProperty(PROPERTY_ALIAS_PREFIX, "");
        defaultKey = config.getProperty(CryptoTokenHelper.PROPERTY_DEFAULTKEY);
    }

    @Override
    public String getAlias(final int purpose, final IProcessable processble,
                           final ProcessRequest signRequest,
                           final RequestContext requestContext)
            throws IllegalRequestException, CryptoTokenOfflineException, SignServerException {
        if (requestContext != null) {
            final Object cred =
                    requestContext.get(RequestContext.CLIENT_CREDENTIAL);

            if (cred != null && cred instanceof UsernamePasswordClientCredential) {
                final String username =
                        ((UsernamePasswordClientCredential) cred).getUsername();

                return prefix + username;
            } else {
                return null;
            }
        } else {
            return defaultKey;
        }
    }

    @Override
    public List<String> getFatalErrors() {
        return Collections.emptyList();
    }
    
}
