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
package org.signserver.server.tsa;

import java.math.BigInteger;

import org.apache.log4j.Logger;
import org.signserver.common.GenericSignRequest;
import org.signserver.common.GenericSignResponse;
import org.signserver.common.ProcessResponse;
import org.signserver.common.RequestContext;
import org.signserver.common.SignServerException;
import org.signserver.ejb.interfaces.IInternalWorkerSession;
import org.signserver.server.UsernamePasswordClientCredential;

import java.io.IOException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIStatus;

import org.bouncycastle.tsp.*;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.InvalidWorkerIdException;

/**
 * Fetching time-stamp tokens internally using the internal worker session.
 *
 * @author Marcus Lundblad
 * @author Markus Kilås
 * @version $Id$
 */
public class InternalTimeStampTokenFetcher {
    private static final Logger LOG = Logger.getLogger(InternalTimeStampTokenFetcher.class);

    private final IInternalWorkerSession session;
    private final String workerNameOrId;
    private final String username;
    private final String password;

    public InternalTimeStampTokenFetcher(final IInternalWorkerSession session, final String workerNameOrId,
            final String username, final String password) {
        this.session = session;
        this.workerNameOrId = workerNameOrId;
        this.username = username;
        this.password = password;
    }

    public TimeStampToken fetchToken(byte[] imprint, ASN1ObjectIdentifier digestOID, ASN1ObjectIdentifier reqPolicy) throws IllegalRequestException, CryptoTokenOfflineException, SignServerException, TSPException, IOException {
        int workerId;
        try {
            workerId = Integer.parseInt(workerNameOrId);
        } catch (NumberFormatException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not a workerId, maybe workerName: " + workerNameOrId);
            }
            try {
                workerId = session.getWorkerId(workerNameOrId);
            } catch (InvalidWorkerIdException ex2) {
                throw new SignServerException(ex2.getMessage());
            }
        }

        // Setup the time stamp request
        TimeStampRequestGenerator tsqGenerator = new TimeStampRequestGenerator();
        tsqGenerator.setCertReq(true);
        
        if (reqPolicy != null) {
            tsqGenerator.setReqPolicy(reqPolicy);
        }

        BigInteger nonce = BigInteger.valueOf(System.currentTimeMillis());
        TimeStampRequest request = tsqGenerator.generate(digestOID, imprint, nonce);
        byte[] requestBytes = request.getEncoded();

        final RequestContext context = new RequestContext();

        if (username != null && password != null) {
            UsernamePasswordClientCredential cred
                    = new UsernamePasswordClientCredential(username, password);
            context.put(RequestContext.CLIENT_CREDENTIAL, cred);
            context.put(RequestContext.CLIENT_CREDENTIAL_PASSWORD, cred);
        }

        final ProcessResponse resp = session.process(workerId, new GenericSignRequest(hashCode(), requestBytes), context);

        if (resp instanceof GenericSignResponse) {
            final byte[] respBytes = ((GenericSignResponse) resp).getProcessedData();

            TimeStampResponse response = new TimeStampResponse(respBytes);

            TimeStampToken  tsToken = response.getTimeStampToken();
            if (tsToken == null) {
                throw new SignServerException("TSA '" + workerNameOrId + "' failed to return time stamp token: " + response.getStatusString());
            }

            if(response.getStatus() != PKIStatus.GRANTED && response.getStatus() != PKIStatus.GRANTED_WITH_MODS) {
                throw new SignServerException("Time stamp token not granted: " + response.getStatusString());
            }
            response.validate(request);

            return tsToken;
        } else {
            throw new SignServerException("Unknown response");
        }

    }

}
