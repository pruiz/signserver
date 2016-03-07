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
package org.signserver.module.xmlsigner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.log4j.Logger;
import org.signserver.common.*;
import org.signserver.server.WorkerContext;
import org.signserver.server.archive.Archivable;
import org.signserver.server.archive.DefaultArchivable;
import org.signserver.server.cryptotokens.ICryptoInstance;
import org.signserver.server.cryptotokens.ICryptoTokenV4;
import org.signserver.server.signers.BaseSigner;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * A Signer signing XML documents.
 * 
 * Implements a ISigner and have the following properties:
 * No properties yet
 * 
 * @author Markus Kilås
 * @version $Id$
 */
public class XMLSigner extends BaseSigner {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(XMLSigner.class);
    private static final String CONTENT_TYPE = "text/xml";

    // Property constants
    public static final String SIGNATUREALGORITHM = "SIGNATUREALGORITHM";
    
    /**
     * Addional signature methods not yet covered by
     * javax.xml.dsig.SignatureMethod
     * 
     * Defined in RFC 4051 {@link http://www.ietf.org/rfc/rfc4051.txt}
     */
    private static final String SIGNATURE_METHOD_RSA_SHA256 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    private static final String SIGNATURE_METHOD_RSA_SHA384 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
    private static final String SIGNATURE_METHOD_RSA_SHA512 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
    private static final String SIGNATURE_METHOD_ECDSA_SHA1 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";
    private static final String SIGNATURE_METHOD_ECDSA_SHA256 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
    private static final String SIGNATURE_METHOD_ECDSA_SHA384 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
    private static final String SIGNATURE_METHOD_ECDSA_SHA512 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";

    private String signatureAlgorithm;
    
    @Override
    public void init(final int workerId, final WorkerConfig config,
            final WorkerContext workerContext, final EntityManager workerEM) {
        super.init(workerId, config, workerContext, workerEM);
        
        // Get the signature algorithm
        signatureAlgorithm = config.getProperty(SIGNATUREALGORITHM);
    }

    @Override
    public ProcessResponse processData(ProcessRequest signRequest, RequestContext requestContext) throws IllegalRequestException, CryptoTokenOfflineException, SignServerException {

        ProcessResponse signResponse;

        // Check that the request contains a valid GenericSignRequest object with a byte[].
        if (!(signRequest instanceof GenericSignRequest)) {
            throw new IllegalRequestException("Received request wasn't a expected GenericSignRequest.");
        }
        
        final ISignRequest sReq = (ISignRequest) signRequest;
        
        if (!(sReq.getRequestData() instanceof byte[])) {
            throw new IllegalRequestException("Received request data wasn't a expected byte[].");
        }

        byte[] data = (byte[]) sReq.getRequestData();
        String archiveId = createArchiveId(data, (String) requestContext.get(RequestContext.TRANSACTION_ID));


        String providerName = System.getProperty("jsr105Provider", "org.apache.jcp.xml.dsig.internal.dom.XMLDSigRI");
        XMLSignatureFactory fac;
        try {
            fac = XMLSignatureFactory.getInstance("DOM", (Provider) Class.forName(providerName).newInstance());
        } catch (InstantiationException e) {
            throw new SignServerException("Problem with JSR105 provider", e);
        } catch (IllegalAccessException e) {
            throw new SignServerException("Problem with JSR105 provider", e);
        } catch (ClassNotFoundException e) {
            throw new SignServerException("Problem with JSR105 provider", e);
        }

        Certificate cert;
        Document doc;
        ICryptoInstance crypto = null;
        try {
            crypto = acquireCryptoInstance(ICryptoTokenV4.PURPOSE_SIGN, signRequest, requestContext);

            // Get certificate chain and signer certificate
            final List<Certificate> certs = getSigningCertificateChain(crypto);
            if (certs == null) {
                throw new IllegalArgumentException("Null certificate chain. This signer needs a certificate.");
            }
            List<X509Certificate> x509CertChain = new LinkedList<X509Certificate>();
            for (Certificate c : includedCertificates(certs)) {
                if (c instanceof X509Certificate) {
                    x509CertChain.add((X509Certificate) c);
                    LOG.debug("Adding to chain: " + ((X509Certificate) c).getSubjectDN());
                }
            }
            cert = this.getSigningCertificate(crypto);
            if (LOG.isDebugEnabled()) {
                LOG.debug("SigningCert: " + ((X509Certificate) cert).getSubjectDN());
            }

            // Private key
            final PrivateKey privKey = crypto.getPrivateKey();

            SignedInfo si;
            try {
                final String sigAlg = signatureAlgorithm == null ? getDefaultSignatureAlgorithm(privKey) : signatureAlgorithm;
                Reference ref = fac.newReference("",
                        fac.newDigestMethod(DigestMethod.SHA1, null),
                        Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (XMLStructure) null)),
                        null, null);

                si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS, (XMLStructure) null),
                        fac.newSignatureMethod(getSignatureMethod(sigAlg), null),
                        Collections.singletonList(ref));

            } catch (InvalidAlgorithmParameterException ex) {
                throw new SignServerException("XML signing algorithm error", ex);
            } catch (NoSuchAlgorithmException ex) {
                throw new SignServerException("XML signing algorithm error", ex);
            }

            KeyInfo ki = null;

            if (!x509CertChain.isEmpty()) {
                KeyInfoFactory kif = fac.getKeyInfoFactory();
                X509Data x509d = kif.newX509Data(x509CertChain);

                List<XMLStructure> kviItems = new LinkedList<XMLStructure>();
                kviItems.add(x509d);
                ki = kif.newKeyInfo(kviItems);
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            try {
                // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
                // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
                dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);

                // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
                // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
                dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

                // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(data));
            } catch (SAXException ex) {
                throw new IllegalRequestException("Document parsing error", ex);
            } catch (ParserConfigurationException ex) {
                throw new SignServerException("Document parsing error", ex);
            } catch (IOException ex) {
                throw new SignServerException("Document parsing error", ex);
            }
            DOMSignContext dsc = new DOMSignContext(privKey, doc.getDocumentElement());

            XMLSignature signature = fac.newXMLSignature(si, ki);
            try {
                signature.sign(dsc);
            } catch (MarshalException ex) {
                throw new SignServerException("Signature generation error", ex);
            } catch (XMLSignatureException ex) {
                throw new SignServerException("Signature generation error", ex);
            }
        } finally {
            releaseCryptoInstance(crypto, requestContext);
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans;
        try {
            trans = tf.newTransformer();
            trans.transform(new DOMSource(doc), new StreamResult(bout));
        } catch (TransformerConfigurationException ex) {
            throw new SignServerException("XML transformation error", ex);
        } catch (TransformerException ex) {
            throw new SignServerException("XML transformation error", ex);
        }

        final byte[] signedbytes = bout.toByteArray();
        final Collection<? extends Archivable> archivables = Arrays.asList(new DefaultArchivable(Archivable.TYPE_RESPONSE, CONTENT_TYPE, signedbytes, archiveId));

        if (signRequest instanceof GenericServletRequest) {
            signResponse = new GenericServletResponse(sReq.getRequestID(), signedbytes,
                    cert,
                    archiveId, archivables, CONTENT_TYPE);
        } else {
            signResponse = new GenericSignResponse(sReq.getRequestID(), signedbytes,
                    cert,
                    archiveId, archivables);
        }
        
        // The client can be charged for the request
        requestContext.setRequestFulfilledByWorker(true);
        
        return signResponse;
    }

    /**
     * Get an XMLSec URI for a given signature algorithm in BC style.
     * 
     * @param sigAlg Signature algorithm name in BC style
     * @return The URI for the algo in XMLSec.
     * @throws NoSuchAlgorithmException
     */
    private static String getSignatureMethod(final String sigAlg)
            throws NoSuchAlgorithmException {
        String result;

        if ("SHA1withDSA".equals(sigAlg)) {
            result = SignatureMethod.DSA_SHA1;
        } else if ("SHA1withRSA".equals(sigAlg)) {
            result = SignatureMethod.RSA_SHA1;
        } else if ("SHA256withRSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_RSA_SHA256;
        } else if ("SHA384withRSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_RSA_SHA384;
        } else if ("SHA512withRSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_RSA_SHA512;
        } else if ("SHA1withECDSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_ECDSA_SHA1;
        } else if ("SHA256withECDSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_ECDSA_SHA256;
        } else if ("SHA384withECDSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_ECDSA_SHA384;
        } else if ("SHA512withECDSA".equals(sigAlg)) {
            result = SIGNATURE_METHOD_ECDSA_SHA512;
        } else {
            throw new NoSuchAlgorithmException("XMLSigner does not support algorithm: " + sigAlg);
        }

        return result;
    }
    
    /**
     * Return the default signature algo name given the private key.
     * 
     * @param privKey
     * @return
     */
    private String getDefaultSignatureAlgorithm(final PrivateKey privKey) {
        final String result;

        if (privKey instanceof DSAPrivateKey) {
            result = "SHA1withDSA";
        } else if (privKey instanceof ECPrivateKey) {
            result = "SHA1withECDSA";
        } else {
            result = "SHA1withRSA";
        }

        return result;
    }
}
