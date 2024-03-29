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
package org.signserver.common;

import java.security.cert.Certificate;

/**
 * Interface used in responses from the WorkerSession.process method. Should
 * be implemented by all types of signers.
 *
 * @author Philip Vendil
 * @version $Id: ISignResponse.java 7565 2016-06-28 08:01:12Z malu9369 $
 */
public interface ISignResponse extends IArchivableProcessResponse {

    /**
     * Should contain a unique request id used to link to the request.
     * 
     * @return Request ID
     */
    int getRequestID();

    /**
     * Method returning the certificate used for the signature
     * 
     * @return the Certificate that was used to sign.
     */
    Certificate getSignerCertificate();
}
