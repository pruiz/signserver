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
package org.signserver.server.archive.test1archiver;

import java.util.Collections;
import org.apache.log4j.Logger;
import org.signserver.common.ArchiveData;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.GenericSignRequest;
import org.signserver.common.GenericSignResponse;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.ProcessRequest;
import org.signserver.common.ProcessResponse;
import org.signserver.common.RequestContext;
import org.signserver.common.RequestMetadata;
import org.signserver.common.SignServerException;
import org.signserver.server.archive.Archivable;
import org.signserver.server.archive.olddbarchiver.ArchiveDataArchivable;
import org.signserver.server.signers.BaseSigner;

/**
 * A signer used by system tests to test the Archiving API and others. Not usable 
 * in production.
 * 
 * @author Markus Kilås
 * @version $Id$
 */
public class Test1Signer extends BaseSigner {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(Test1Signer.class);
    
    public static final String METADATA_FAILREQUEST = "DO_FAIL_REQUEST";

    @Override
    public ProcessResponse processData(final ProcessRequest processRequest,
            final RequestContext requestContext) throws IllegalRequestException,
            CryptoTokenOfflineException, SignServerException {
        LOG.debug(">processData");
        
        final ProcessResponse result;
        final GenericSignRequest request;
        
        if (processRequest instanceof GenericSignRequest) {
            request = (GenericSignRequest) processRequest;
        } else {
            throw new IllegalRequestException("Unexpeted request type: "
                    + processRequest.getClass());
        }
        
        final byte[] signedbytes = "SIGNED".getBytes();
        
        String archiveId = String.valueOf(request.getRequestID()) + "-" + System.currentTimeMillis();
        result = new GenericSignResponse(request.getRequestID(),
                signedbytes, getSigningCertificate(requestContext.getServices()), 
                archiveId,
                Collections.singletonList(new ArchiveDataArchivable(archiveId, new ArchiveData(signedbytes), Archivable.TYPE_RESPONSE)));
        
        // Setting REQUEST_METADATA.DO_FAIL_REQUEST causes this signer to not treat the request as fulfilled
        boolean success = RequestMetadata.getInstance(requestContext).get(METADATA_FAILREQUEST) == null;
        requestContext.setRequestFulfilledByWorker(success);
        
        LOG.debug("<processData");
        return result;
    }
}
