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
package org.signserver.module.cmssigner;

import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import org.apache.log4j.Logger;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.signserver.common.*;
import org.signserver.server.IServices;
import org.signserver.server.WorkerContext;
import org.signserver.server.archive.Archivable;
import org.signserver.server.archive.DefaultArchivable;
import org.signserver.server.cryptotokens.ICryptoInstance;
import org.signserver.server.cryptotokens.ICryptoToken;
import org.signserver.server.signers.BaseSigner;

/**
 * A Signer signing arbitrary content and produces the result in
 * Cryptographic Message Syntax (CMS) - RFC 3852.
 *
 * @author Markus Kilås
 * @version $Id$
 */
public class CMSSigner extends BaseSigner {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(CMSSigner.class);

    /** Content-type for the produced data. */
    private static final String CONTENT_TYPE = "application/pkcs7-signature";
    
    // Property constants
    public static final String SIGNATUREALGORITHM_PROPERTY = "SIGNATUREALGORITHM";
    public static final String DETACHEDSIGNATURE_PROPERTY = "DETACHEDSIGNATURE";
    public static final String ALLOW_SIGNATURETYPE_OVERRIDE_PROPERTY = "ALLOW_DETACHEDSIGNATURE_OVERRIDE";

    private LinkedList<String> configErrors;
    private String signatureAlgorithm;

    private boolean detachedSignature;
    private boolean allowDetachedSignatureOverride;

    @Override
    public void init(final int workerId, final WorkerConfig config,
            final WorkerContext workerContext, final EntityManager workerEM) {
        super.init(workerId, config, workerContext, workerEM);

        // Configuration errors
        configErrors = new LinkedList<String>();

        // Get the signature algorithm
        signatureAlgorithm = config.getProperty(SIGNATUREALGORITHM_PROPERTY);

        // Detached signature
        final String detachedSignatureValue = config.getProperty(DETACHEDSIGNATURE_PROPERTY);
        if (detachedSignatureValue == null || Boolean.FALSE.toString().equalsIgnoreCase(detachedSignatureValue)) {
            detachedSignature = false;
        } else if (Boolean.TRUE.toString().equalsIgnoreCase(detachedSignatureValue)) {
            detachedSignature = true;
        } else {
            configErrors.add("Incorrect value for property " + DETACHEDSIGNATURE_PROPERTY + ". Expecting TRUE or FALSE.");
        }

        // Allow detached signature override
        final String allowDetachedSignatureOverrideValue = config.getProperty(ALLOW_SIGNATURETYPE_OVERRIDE_PROPERTY);
        if (allowDetachedSignatureOverrideValue == null || Boolean.FALSE.toString().equalsIgnoreCase(allowDetachedSignatureOverrideValue)) {
            allowDetachedSignatureOverride = false;
        } else if (Boolean.TRUE.toString().equalsIgnoreCase(allowDetachedSignatureOverrideValue)) {
            allowDetachedSignatureOverride = true;
        } else {
            configErrors.add("Incorrect value for property " + ALLOW_SIGNATURETYPE_OVERRIDE_PROPERTY + ". Expecting TRUE or FALSE.");
        }
    }

    @Override
    public ProcessResponse processData(final ProcessRequest signRequest,
            final RequestContext requestContext) throws IllegalRequestException,
            CryptoTokenOfflineException, SignServerException {

        ProcessResponse signResponse;

        // Check that the request contains a valid GenericSignRequest object
        // with a byte[].
        if (!(signRequest instanceof GenericSignRequest)) {
            throw new IllegalRequestException(
                    "Recieved request wasn't a expected GenericSignRequest.");
        }
        
        final ISignRequest sReq = (ISignRequest) signRequest;
        
        if (!(sReq.getRequestData() instanceof byte[])) {
            throw new IllegalRequestException(
                    "Recieved request data wasn't a expected byte[].");
        }

        if (!configErrors.isEmpty()) {
            throw new SignServerException("Worker is misconfigured");
        }

        byte[] data = (byte[]) sReq.getRequestData();
        final String archiveId = createArchiveId(data, (String) requestContext.get(RequestContext.TRANSACTION_ID));

        X509Certificate cert = null;
        List<Certificate> certs = null;
        ICryptoInstance crypto = null;
        try {
            crypto = acquireCryptoInstance(ICryptoToken.PURPOSE_SIGN, signRequest, requestContext);
            cert = (X509Certificate) getSigningCertificate(crypto);
            if (LOG.isDebugEnabled()) {
                LOG.debug("SigningCert: " + cert);
            }
            
            // Get certificate chain and signer certificate
            certs = includedCertificates(this.getSigningCertificateChain(crypto));
            if (certs == null) {
                throw new IllegalArgumentException("Null certificate chain. This signer needs a certificate.");
            }
            
            final CMSSignedDataGenerator generator
                    = new CMSSignedDataGenerator();
            final String sigAlg = signatureAlgorithm == null ? getDefaultSignatureAlgorithm(crypto.getPublicKey()) : signatureAlgorithm;
            final ContentSigner contentSigner = new JcaContentSignerBuilder(sigAlg).setProvider(crypto.getProvider()).build(crypto.getPrivateKey());
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                     new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                     .build(contentSigner, cert));

            generator.addCertificates(new JcaCertStore(certs));
            final CMSTypedData content = new CMSProcessableByteArray(data);

            // Should the content be detached or not
            final boolean detached;
            final Boolean detachedRequested = getDetachedSignatureRequest(requestContext);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Detached signature configured: " + detachedSignature + "\n"
                        + "Detached signature requested: " + detachedRequested);
            }
            if (detachedRequested == null) {
                detached = detachedSignature;
            } else {
                if (detachedRequested) {
                    if (!detachedSignature && !allowDetachedSignatureOverride) {
                        throw new IllegalRequestException("Detached signature requested but not allowed");
                    }
                } else {
                    if (detachedSignature && !allowDetachedSignatureOverride) {
                        throw new IllegalRequestException("Non detached signature requested but not allowed");
                    }
                }
                detached = detachedRequested;
            }

            final CMSSignedData signedData = generator.generate(content, !detached);

            final byte[] signedbytes = signedData.getEncoded();
            final Collection<? extends Archivable> archivables = Arrays.asList(new DefaultArchivable(Archivable.TYPE_RESPONSE, CONTENT_TYPE, signedbytes, archiveId));

            if (signRequest instanceof GenericServletRequest) {
                signResponse = new GenericServletResponse(sReq.getRequestID(),
                        signedbytes,
                        cert,
                        archiveId, archivables, CONTENT_TYPE);
            } else {
                signResponse = new GenericSignResponse(sReq.getRequestID(),
                        signedbytes,
                        cert,
                        archiveId, archivables);
            }
            
            // Suggest new file name
            final Object fileNameOriginal = requestContext.get(RequestContext.FILENAME);
            if (fileNameOriginal instanceof String) {
                requestContext.put(RequestContext.RESPONSE_FILENAME, fileNameOriginal + ".p7s");
            }
            
            // The client can be charged for the request
            requestContext.setRequestFulfilledByWorker(true);
            
            return signResponse;
        } catch (OperatorCreationException ex) {
            LOG.error("Error initializing signer", ex);
            throw new SignServerException("Error initializing signer", ex);
        } catch (CertificateEncodingException ex) {
            LOG.error("Error constructing cert store", ex);
            throw new SignServerException("Error constructing cert store", ex);
        } catch (CMSException ex) {
            LOG.error("Error constructing CMS", ex);
            throw new SignServerException("Error constructing CMS", ex);
        } catch (IOException ex) {
            LOG.error("Error constructing CMS", ex);
            throw new SignServerException("Error constructing CMS", ex);
        } finally {
            releaseCryptoInstance(crypto, requestContext);
        }
    }
    
    private String getDefaultSignatureAlgorithm(final PublicKey publicKey) {
        final String result;

        if (publicKey instanceof ECPublicKey) {
            result = "SHA1withECDSA";
        }  else if (publicKey instanceof DSAPublicKey) {
            result = "SHA1withDSA";
        } else {
            result = "SHA1withRSA";
        }

        return result;
    }

    @Override
    protected List<String> getFatalErrors(final IServices services) {
        final LinkedList<String> errors = new LinkedList<String>(super.getFatalErrors(services));
        errors.addAll(configErrors);
        return errors;
    }

    /**
     * Read the request metadata property for DETACHEDSIGNATURE if any.
     * Note that empty String is treated as an unset property.
     * @param context to read from
     * @return null if no DETACHEDSIGNATURE request property specified otherwise
     * true or false.
     */
    private static Boolean getDetachedSignatureRequest(final RequestContext context) {
        Boolean result = null;
        final String value = RequestMetadata.getInstance(context).get(DETACHEDSIGNATURE_PROPERTY);
        if (value != null && !value.isEmpty()) {
            result = Boolean.parseBoolean(value);
        }
        return result;
    }
}
