package org.signserver.common;

/**
 * Interface used for requests to WorkerSession.process method. Should
 * be implemented by all types of signers.
 *
 * @author Philip Vendil
 * @version $Id: ISignRequest.java 7565 2016-06-28 08:01:12Z malu9369 $
 */
public interface ISignRequest {

    /**
     * Should contain a unique request id used to identify the request.
     * 
     * @return Request ID
     */
    int getRequestID();

    /**
     * Should contain the data that should be signed.
     * 
     * @return Request data
     */
    Object getRequestData();
}
