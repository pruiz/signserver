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

/**
 * General implementation of a ISignerCertReqData that
 * contains a Base64 encoded byte array containing the certificate
 * request data.
 * 
 * Legacy class, use AbstractCertReqData or Pkcs10CertReqData instead.
 * 
 * @author Philip Vendil 2007 feb 19
 * @version $Id: Base64SignerCertReqData.java 10715 2019-04-30 11:29:06Z netmackan $
 * @see AbstractCertReqData
 * @see Pkcs10CertReqData
 */
public class Base64SignerCertReqData implements ICertReqData {

    private static final long serialVersionUID = 1L;
    byte[] base64CertReq = null;

    /**
     * No-arg constructor used by JAXB.
     */
    public Base64SignerCertReqData() {
    }

    public Base64SignerCertReqData(byte[] base64CertReq) {
        super();
        this.base64CertReq = base64CertReq;
    }

    public byte[] getBase64CertReq() {
        return base64CertReq;
    }

    public void setBase64CertReq(byte[] base64CertReq) {
        this.base64CertReq = base64CertReq;
    }
}
