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
package org.signserver.module.openpgp.signer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.apache.log4j.Logger;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.signserver.common.RequestContext;
import org.signserver.common.SignServerException;
import org.signserver.common.WorkerConfig;
import org.signserver.common.data.SignatureRequest;
import org.signserver.common.data.SignatureResponse;
import org.signserver.server.SignServerContext;
import org.signserver.server.data.impl.CloseableReadableData;
import org.signserver.server.data.impl.CloseableWritableData;
import org.signserver.test.utils.builders.CertBuilder;
import org.signserver.test.utils.builders.CryptoUtils;
import org.signserver.test.utils.mock.MockedCryptoToken;
import org.signserver.test.utils.mock.MockedServicesImpl;
import org.signserver.testutils.ModulesTestCase;

/**
 * Unit tests for the OpenPGPSigner class.
 *
 * @author Markus Kilås
 * @version $Id$
 */
public class OpenPGPSignerUnitTest {
   
    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(OpenPGPSignerUnitTest.class);

    private static MockedCryptoToken tokenRSA;
    private static MockedCryptoToken tokenDSA;
    private static MockedCryptoToken tokenECDSA;
 
    @BeforeClass
    public static void setUpClass() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // RSA
        final KeyPair signerKeyPairRSA = CryptoUtils.generateRSA(1024);
        final Certificate[] certChainRSA =
                new Certificate[] {new JcaX509CertificateConverter().getCertificate(new CertBuilder().
                        setSelfSignKeyPair(signerKeyPairRSA).
                        setNotBefore(new Date()).
                        setSignatureAlgorithm("SHA256withRSA")
                        .build())};
        final Certificate signerCertificateRSA = certChainRSA[0];
        tokenRSA = new MockedCryptoToken(signerKeyPairRSA.getPrivate(), signerKeyPairRSA.getPublic(), signerCertificateRSA, Arrays.asList(certChainRSA), "BC");

        // DSA
        final KeyPair signerKeyPairDSA = CryptoUtils.generateDSA(1024);
        final Certificate[] certChainDSA =
                new Certificate[] {new JcaX509CertificateConverter().getCertificate(new CertBuilder().
                        setSelfSignKeyPair(signerKeyPairDSA).
                        setNotBefore(new Date()).
                        setSignatureAlgorithm("SHA256withDSA")
                        .build())};
        final Certificate signerCertificateDSA = certChainDSA[0];
        tokenDSA = new MockedCryptoToken(signerKeyPairDSA.getPrivate(), signerKeyPairDSA.getPublic(), signerCertificateDSA, Arrays.asList(certChainDSA), "BC");
        
        // ECDSA
        final KeyPair signerKeyPairECDSA = CryptoUtils.generateEcCurve("prime256v1");
        final Certificate[] certChainECDSA =
                new Certificate[] {new JcaX509CertificateConverter().getCertificate(new CertBuilder().
                        setSelfSignKeyPair(signerKeyPairECDSA).
                        setNotBefore(new Date()).
                        setSignatureAlgorithm("SHA256withECDSA")
                        .build())};
        final Certificate signerCertificateECDSA = certChainECDSA[0];
        tokenECDSA = new MockedCryptoToken(signerKeyPairECDSA.getPrivate(), signerKeyPairECDSA.getPublic(), signerCertificateECDSA, Arrays.asList(certChainECDSA), "BC");
    }
    
    /**
     * Test that providing an incorrect value for DETACHEDSIGNATURE
     * gives a fatal error.
     * @throws Exception
     */
    /*For DSS-1969: @Test
    public void testInit_incorrectDetachedSignatureValue() throws Exception {
        LOG.info("testInit_incorrectDetachedSignatureValue");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("DETACHEDSIGNATURE", "_incorrect-value--");
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        String errors = instance.getFatalErrors(new MockedServicesImpl()).toString();
        assertTrue("conf errs: " + errors, errors.contains("DETACHEDSIGNATURE"));
    }*/
    
    /**
     * Test that providing an incorrect value for DIGEST_ALGORITHM
     * gives a fatal error.
     * @throws Exception
     */
    @Test
    public void testInit_incorrectDigestAlgorithmValue() throws Exception {
        LOG.info("testInit_incorrectDigestAlgorithmValue");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("DIGEST_ALGORITHM", "_incorrect-value--");
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        String errors = instance.getFatalErrors(new MockedServicesImpl()).toString();
        assertTrue("conf errs: " + errors, errors.contains("DIGEST_ALGORITHM"));
    }
    
    /**
     * Test that providing an incorrect value for RESPONSE_FORMAT
     * gives a fatal error.
     * @throws Exception
     */
    @Test
    public void testInit_incorrectResponseFormatValue() throws Exception {
        LOG.info("testInit_incorrectResponseFormatValue");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("RESPONSE_FORMAT", "_incorrect-value--");
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        String errors = instance.getFatalErrors(new MockedServicesImpl()).toString();
        assertTrue("conf errs: " + errors, errors.contains("RESPONSE_FORMAT"));
    }

    // TODO: more testInit_*
    
    
    /**
     * Tests that no signing is performed when the worker is misconfigured.
     * @throws java.lang.Exception
     */
    @Test(expected = SignServerException.class)
    public void testNoProcessOnFatalErrors() throws Exception {
        LOG.info("testNoProcessOnFatalErrors");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("DIGEST_ALGORITHM", "_incorrect-value--");
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        final byte[] data = "my-data".getBytes("ASCII");
        signAndVerify(data, tokenRSA, config, null, false, true);
        fail("Should have thrown exception");
    }
    
    
    private void signWithAlgorithm(MockedCryptoToken token, String digestAlgorithmConfig, int expectedDigestAlgorithm) throws Exception {
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        if (digestAlgorithmConfig != null) {
            config.setProperty(OpenPGPSigner.PROPERTY_DIGEST_ALGORITHM, digestAlgorithmConfig);
        }
        boolean armored = true;

        final byte[] data = "my-data".getBytes("ASCII");
        
        SimplifiedResponse response = signAndVerify(data, token, config, new RequestContext(), true, armored);
        assertEquals("hash algorithm", expectedDigestAlgorithm, response.getSignature().getHashAlgorithm());
    }

    /**
     * Tests signing with RESPONSE_FORMAT=BINARY.
     * @throws Exception 
     */
    @Test
    public void testSignWithResponseFormatBinary() throws Exception {
        LOG.info("testSignWithResponseFormatBinary");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("RESPONSE_FORMAT", "BINARY");
        boolean armored = false;
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        final byte[] data = "my-data".getBytes("ASCII");
        signAndVerify(data, tokenRSA, config, null, false, armored);
    }
    
    /**
     * Tests signing with RESPONSE_FORMAT=ARMORED.
     * @throws Exception 
     */
    @Test
    public void testSignWithResponseFormatArmored() throws Exception {
        LOG.info("testSignWithResponseFormatArmored");
        WorkerConfig config = new WorkerConfig();
        config.setProperty("TYPE", "PROCESSABLE");
        config.setProperty("RESPONSE_FORMAT", "ARMORED");
        boolean armored = true;
        OpenPGPSigner instance = createMockSigner(tokenRSA);
        instance.init(1, config, new SignServerContext(), null);

        final byte[] data = "my-data".getBytes("ASCII");
        signAndVerify(data, tokenRSA, config, null, false, armored);
    }
    
    /**
     * Test default signing with RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_default_SHA256() throws Exception {
        LOG.info("testSign_RSA_default_SHA256");
        signWithAlgorithm(tokenRSA, null, PGPUtil.SHA256);
    }
    
    /**
     * Test signing with SHA1 and RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_SHA1() throws Exception {
        LOG.info("testSign_RSA_SHA1");
        signWithAlgorithm(tokenRSA, "SHA1", PGPUtil.SHA1);
    }
    
    /**
     * Test signing with SHA-224 and RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_SHA224() throws Exception {
        LOG.info("testSign_RSA_SHA224");
        signWithAlgorithm(tokenRSA, "SHA-224", PGPUtil.SHA224);
    }
    
    /**
     * Test signing with SHA-384 and RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_SHA384() throws Exception {
        LOG.info("testSign_RSA_SHA384");
        signWithAlgorithm(tokenRSA, "SHA-384", PGPUtil.SHA384);
    }
    
    /**
     * Test signing with SHA-512 and RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_SHA512() throws Exception {
        LOG.info("testSign_RSA_SHA512");
        signWithAlgorithm(tokenRSA, "SHA-512", PGPUtil.SHA512);
    }
    
    /**
     * Test signing with SHA-512 by number and RSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_RSA_SHA512_byNumber() throws Exception {
        LOG.info("testSign_RSA_SHA512_byNumber");
        signWithAlgorithm(tokenRSA, "10", PGPUtil.SHA512); // 10 = SHA-512
    }
    
    /**
     * Test default signing with DSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_DSA_default_SHA256() throws Exception {
        LOG.info("testSign_DSA_default_SHA256");
        signWithAlgorithm(tokenDSA, null, PGPUtil.SHA256);
    }
    
    /**
     * Test signing with SHA1 and DSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_DSA_SHA1() throws Exception {
        LOG.info("testSign_DSA_SHA1");
        signWithAlgorithm(tokenDSA, "SHA1", PGPUtil.SHA1);
    }
    
    /**
     * Test signing with SHA-224 and DSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_DSA_SHA224() throws Exception {
        LOG.info("testSign_DSA_SHA224");
        signWithAlgorithm(tokenDSA, "SHA-224", PGPUtil.SHA224);
    }
    
    /**
     * Test signing with SHA-384 and DSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_DSA_SHA384() throws Exception {
        LOG.info("testSign_DSA_SHA384");
        signWithAlgorithm(tokenDSA, "SHA-384", PGPUtil.SHA384);
    }
    
    /**
     * Test signing with SHA-512 and DSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_DSA_SHA512() throws Exception {
        LOG.info("testSign_DSA_SHA512");
        signWithAlgorithm(tokenDSA, "SHA-512", PGPUtil.SHA512);
    }
    
    /**
     * Test default signing with ECDSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_ECDSA_default_SHA256() throws Exception {
        LOG.info("testSign_ECDSA_default_SHA256");
        signWithAlgorithm(tokenECDSA, null, PGPUtil.SHA256);
    }
    
    /**
     * Test signing with SHA1 and ECDSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_ECDSA_SHA1() throws Exception {
        LOG.info("testSign_ECDSA_SHA1");
        signWithAlgorithm(tokenECDSA, "SHA1", PGPUtil.SHA1);
    }
    
    /**
     * Test signing with SHA-224 and ECDSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_ECDSA_SHA224() throws Exception {
        LOG.info("testSign_ECDSA_SHA224");
        signWithAlgorithm(tokenECDSA, "SHA-224", PGPUtil.SHA224);
    }
    
    /**
     * Test signing with SHA-384 and ECDSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_ECDSA_SHA384() throws Exception {
        LOG.info("testSign_ECDSA_SHA384");
        signWithAlgorithm(tokenECDSA, "SHA-384", PGPUtil.SHA384);
    }
    
    /**
     * Test signing with SHA-512 and ECDSA.
     * @throws java.lang.Exception
     */
    @Test
    public void testSign_ECDSA_SHA512() throws Exception {
        LOG.info("testSign_ECDSA_SHA512");
        signWithAlgorithm(tokenECDSA, "SHA-512", PGPUtil.SHA512);
    }
    

    protected OpenPGPSigner createMockSigner(final MockedCryptoToken token) {
        return new MockedOpenPGPSigner(token);
    }
    
    private SimplifiedResponse signAndVerify(final byte[] data, MockedCryptoToken token, WorkerConfig config, RequestContext requestContext, boolean detached, boolean armored) throws Exception {
        return signAndVerify(data, data, token, config, requestContext, detached, armored);
    }
    
    /**
     * Helper method signing the given data (either the actual data to be signed
     * or if the signer or request implies client-side hashing, the pre-computed
     * hash) and the original data. When detached mode is assumed, the originalData
     * is used to verify the signature.
     * 
     * @param data Data (data to be signed, or pre-computed hash)
     * @param originalData Original data (either the actual data or the data that was pre-hashed)
     * @param token
     * @param config
     * @param requestContext
     * @param detached If true, assume detached
     * @return
     * @throws Exception 
     */
    private SimplifiedResponse signAndVerify(final byte[] data, final byte[] originalData, MockedCryptoToken token, WorkerConfig config, RequestContext requestContext, boolean detached, boolean armored) throws Exception {
        final OpenPGPSigner instance = createMockSigner(token);
        instance.init(1, config, new SignServerContext(), null);

        if (requestContext == null) {
            requestContext = new RequestContext();
        }
        requestContext.put(RequestContext.TRANSACTION_ID, "0000-100-1");

        try (
                CloseableReadableData requestData = ModulesTestCase.createRequestData(data);
                CloseableWritableData responseData = ModulesTestCase.createResponseData(false);
            ) {
            SignatureRequest request = new SignatureRequest(100, requestData, responseData);
            SignatureResponse response = (SignatureResponse) instance.processData(request, requestContext);

            byte[] signedBytes = responseData.toReadableData().getAsByteArray();
            String signed = new String(signedBytes, StandardCharsets.US_ASCII);
                
            if (armored) {
                assertTrue("expecting armored: " + signed, signed.startsWith("-----BEGIN PGP SIGNATURE-----"));
            } else {
                assertFalse("expecting binary: " + signed, signed.startsWith("-----BEGIN PGP SIGNATURE-----"));
            }

            PGPSignature sig;
            
            try (InputStream in = createInputStream(new ByteArrayInputStream(signedBytes), armored)) {
                JcaPGPObjectFactory objectFactory = new JcaPGPObjectFactory(in);
                PGPSignatureList p3 = (PGPSignatureList) objectFactory.nextObject();
                sig = p3.get(0);
            }
            
            final JcaPGPKeyConverter conv = new JcaPGPKeyConverter();
            final X509Certificate x509Cert = (X509Certificate) token.getCertificate(0);
            final PGPPublicKey pgpPublicKey = conv.getPGPPublicKey(getKeyAlg(x509Cert), x509Cert.getPublicKey(), x509Cert.getNotBefore());

            sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pgpPublicKey);
            sig.update(originalData);
            
            assertNotEquals("verified", sig.verify());
            
            return new SimplifiedResponse(signedBytes, sig, pgpPublicKey);
        }
    }
    
    private int getKeyAlg(X509Certificate x509Cert) throws SignServerException {
        final int keyAlg;
        switch (x509Cert.getPublicKey().getAlgorithm()) {
            case "RSA":
                keyAlg = PublicKeyAlgorithmTags.RSA_SIGN;
                break;
            case "EC":
                keyAlg = PublicKeyAlgorithmTags.ECDSA;
                break;
            case "DSA":
                keyAlg = PublicKeyAlgorithmTags.DSA;
                break;
            default:
                throw new SignServerException("Unsupported key algorithm: " + x509Cert.getPublicKey().getAlgorithm());
        }
        return keyAlg;
    }
    
    private BCPGInputStream createInputStream(InputStream in, boolean armored) throws IOException {
        return new BCPGInputStream(armored ? new ArmoredInputStream(in) : in);
    }

}
