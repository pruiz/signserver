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
package org.signserver.module.xades.signer;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpressionException;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.ProcessRequest;
import org.signserver.common.ProcessResponse;
import org.signserver.common.RequestContext;
import org.signserver.common.SignServerException;
import org.signserver.server.signers.BaseSigner;
import org.apache.log4j.Logger;
import org.signserver.common.GenericServletRequest;
import org.signserver.common.GenericServletResponse;
import org.signserver.common.GenericSignRequest;
import org.signserver.common.GenericSignResponse;
import org.signserver.common.ISignRequest;
import org.signserver.common.WorkerConfig;
import org.signserver.server.WorkerContext;
import org.signserver.server.archive.Archivable;
import org.signserver.server.archive.DefaultArchivable;
import org.signserver.server.cryptotokens.ICryptoToken;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.ejbca.util.Base64;

import xades4j.UnsupportedAlgorithmException;
import xades4j.XAdES4jException;
import xades4j.algorithms.Algorithm;
import xades4j.algorithms.GenericAlgorithm;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.EnvelopedXmlObject;
import xades4j.production.SignedDataObjects;
import xades4j.production.DataObjectReference;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.production.XadesSigningProfile;
import xades4j.production.XadesTSigningProfile;
import xades4j.production.XadesEpesSigningProfile;
import xades4j.properties.ObjectIdentifier;
import xades4j.properties.DataObjectDesc;
import xades4j.properties.SignaturePolicyBase;
import xades4j.properties.SignaturePolicyIdentifierProperty;
import xades4j.properties.AllDataObjsCommitmentTypeProperty;
import xades4j.providers.KeyingDataProvider;
import xades4j.providers.TimeStampTokenProvider;
import xades4j.providers.SignaturePolicyInfoProvider;
import xades4j.utils.XadesProfileResolutionException;
import xades4j.providers.impl.DefaultAlgorithmsProviderEx;
import xades4j.providers.impl.ExtendedTimeStampTokenProvider;
import xades4j.providers.impl.DefaultBasicSignatureOptionsProvider;

/**
 * A Signer using XAdES to createSigner XML documents.
 * 
 * Based on patch contributed by Luis Maia &lt;lmaia@dcc.fc.up.pt&gt;.
 * 
 * @author Luis Maia <lmaia@dcc.fc.up.pt>
 * @version $Id$
 */
public class XAdESSigner extends BaseSigner {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(XAdESSigner.class);
    
    /** Worker property: XADESFORM. */
    public static final String PROPERTY_XADESFORM = "XADESFORM";
    
    /** Worker property: TSA_URL. */
    public static final String PROPERTY_TSA_URL = "TSA_URL";
    
    /** Worker property: TSA_USERNAME. */
    public static final String PROPERTY_TSA_USERNAME = "TSA_USERNAME";
    
    /** Worker property: TSA_PASSWORD. */
    public static final String PROPERTY_TSA_PASSWORD = "TSA_PASSWORD";
   
    /** Worker property: TSA_HASHALGORITHM. */
    public static final String TSA_HASHALGORITHM = "TSA_HASHALGORITHM";
    
    /** Worker property: COMMITMENT_TYPES. */
    public static final String PROPERTY_COMMITMENT_TYPES = "COMMITMENT_TYPES";
    
    /** Worker property: SIGNATUREALGORITHM. */
    public static final String SIGNATUREALGORITHM = "SIGNATUREALGORITHM";

    /** Worker property: SIGNATURETYPE. */
    public static final String SIGNATURETYPE = "SIGNATURETYPE";

    /** Worker property: SIGNATURENODE. */
    public static final String SIGNATURENODE = "SIGNATURENODE";

    /** Worker property: SIGNATUREPOLICY_URL. */
    public static final String SIGNATUREPOLICY_URL = "SIGNATUREPOLICY_URL";

    /** Worker property: SIGNATUREPOLICY_BYTES. */
    public static final String SIGNATUREPOLICY_BYTES = "SIGNATUREPOLICY_BYTES";

    /** Worker property: SIGNATUREPOLICY_IDENTIFIER. */
    public static final String SIGNATUREPOLICY_IDENTIFIER = "SIGNATUREPOLICY_IDENTIFIER";

    /** Worker property: SIGNATURE_SIGNKEYINFO. */
    public static final String SIGNATURE_SIGNKEYINFO = "SIGNATURE_SIGNKEYINFO";

    /** Worker property: SIGNATURE_INCLUDECHAIN. */
    public static final String SIGNATURE_INCLUDECHAIN = "SIGNATURE_INCLUDECHAIN";

    /** Worker property: DATAOBJSREFS_HASHALGORITHM. */
    public static final String DATAOBJSREFS_HASHALGORITHM = "DATAOBJSREFS_HASHALGORITHM";

    /** Worker property: REFERENCE_PROPERTIES_HASHALGORITHM. */
    public static final String REFERENCE_PROPERTIES_HASHALGORITHM = "REFERENCE_PROPERTIES_HASHALGORITHM";

    public static final String COMMITMENT_TYPES_NONE = "NONE";
    
    /** Default value use if the worker property XADESFORM has not been set. */
    private static final String DEFAULT_XADESFORM = "BES";

    /** Default signature type */
    private static final String DEFAULT_SIGNATURETYPE = "Enveloping";
    
    private static final String CONTENT_TYPE = "text/xml";
    
    private LinkedList<String> configErrors;
    private XAdESSignerParameters parameters;
    private String tsaHashAlgorithm;
    
    private Collection<AllDataObjsCommitmentTypeProperty> commitmentTypes;
   
    private Profiles xadesProfile = Profiles.BES;
    private String signatureAlgorithm;
    private SignatureTypes signatureType = SignatureTypes.Enveloping;
    private String signatureNode;
    private URL signaturePolicyUrl;
    private byte[] signaturePolicyBytes;
    private String signaturePolicyIdentifier;
    private boolean signatureIncludeChain = true;
    private boolean signatureSignKeyInfo = true;
    private String dataObjsReferencesHashAlgorithm;
    private String referencePropertiesHashAlgorithm;
    
    /**
     * Addional signature methods not yet covered by
     * javax.xml.dsig.SignatureMethod
     * 
     * Defined in RFC 4051 {@link http://www.ietf.org/rfc/rfc4051.txt}
     */
    static final String SIGNATURE_METHOD_RSA_SHA256 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    static final String SIGNATURE_METHOD_RSA_SHA384 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
    static final String SIGNATURE_METHOD_RSA_SHA512 =
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
    static final String SIGNATURE_METHOD_ECDSA_SHA1 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";
    static final String SIGNATURE_METHOD_ECDSA_SHA256 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
    static final String SIGNATURE_METHOD_ECDSA_SHA384 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
    static final String SIGNATURE_METHOD_ECDSA_SHA512 =
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";
    
    /**
     * The default time stamp token implementation, can be overridden by the unit tests.
     */
    private Class<? extends TimeStampTokenProvider> timeStampTokenProviderImplementation =
            ExtendedTimeStampTokenProvider.class;
    
    /** 
     * Electronic signature forms defined in ETSI TS 101 903 V1.4.1 (2009-06)
     * section 4.4.
     */
    public enum Profiles {
        BES,
        EPES,
        T,
        C
    }
   
    /**
     * XAdES Signature type: Enveloped, Enveloping, Detached. 
     */
    public enum SignatureTypes {
        Enveloping,
        Enveloped
        // TODO: Detached
    } 
    
    /**
     * Commitment types defined in ETSI TS 101 903 V1.4.1 (2009-06).
     * section 7.2.6.
     * @see xades4j.properties.AllDataObjsCommitmentTypeProperty
     */
    public enum CommitmentTypes {
        PROOF_OF_APPROVAL(AllDataObjsCommitmentTypeProperty.proofOfApproval()),
        PROOF_OF_CREATION(AllDataObjsCommitmentTypeProperty.proofOfCreation()),
        PROOF_OF_DELIVERY(AllDataObjsCommitmentTypeProperty.proofOfDelivery()),
        PROOF_OF_ORIGIN(AllDataObjsCommitmentTypeProperty.proofOfOrigin()),
        PROOF_OF_RECEIPT(AllDataObjsCommitmentTypeProperty.proofOfReceipt()),
        PROOF_OF_SENDER(AllDataObjsCommitmentTypeProperty.proofOfSender());
        
        CommitmentTypes(AllDataObjsCommitmentTypeProperty commitmentType) {
            prop = commitmentType;
        }
        
        AllDataObjsCommitmentTypeProperty getProp() {
            return prop;
        }
        
        AllDataObjsCommitmentTypeProperty prop;
    }

    /**
     * Loads configuration settings reated to Signature Policy generation.
     */ 
    private void parseSignaturePolicyConfig(final WorkerConfig config, final LinkedList<String> configErrors) {
        final String signaturePolicyUrlString = config.getProperties().getProperty(SIGNATUREPOLICY_URL);
        final String signaturePolicyIdentifierString = config.getProperties().getProperty(SIGNATUREPOLICY_IDENTIFIER);
        final String signaturePolicyBytesString = config.getProperties().getProperty(SIGNATUREPOLICY_BYTES);

        if (signaturePolicyUrlString != null) {
            try {
                signaturePolicyUrl = new URL(signaturePolicyUrlString);
            } catch (MalformedURLException ex) {
                configErrors.add("Invalid SignaturePolicyUrl: " + ex.getMessage());
            }
        }
 
        if (signaturePolicyIdentifierString != null) {
            signaturePolicyIdentifier = signaturePolicyIdentifierString.trim();
        }

        if (signaturePolicyBytesString != null) {
            signaturePolicyBytes = Base64.decode(signaturePolicyBytesString.getBytes());
        }

        if ((signaturePolicyIdentifier == null && signaturePolicyBytes == null && signaturePolicyUrl == null)
            || (signaturePolicyIdentifier == null && signaturePolicyUrl == null)
            || (signaturePolicyBytes == null && signaturePolicyUrl == null))
        {
            configErrors.add("At least " + SIGNATUREPOLICY_URL + ", or " + SIGNATUREPOLICY_IDENTIFIER + " and " + SIGNATUREPOLICY_BYTES 
                            + " are required when " + PROPERTY_XADESFORM + " is " + xadesProfile.toString());
        }
    }

    @Override
    public void init(final int signerId, final WorkerConfig config, final WorkerContext workerContext, final EntityManager em) {
        super.init(signerId, config, workerContext, em);
        LOG.trace(">init");
        
        // Configuration errors
        configErrors = new LinkedList<String>();
        
        // PROPERTY_XADESFORM
        final String xadesForm = config.getProperties().getProperty(PROPERTY_XADESFORM, XAdESSigner.DEFAULT_XADESFORM);
        try {
            xadesProfile = Profiles.valueOf(xadesForm);
        } catch (IllegalArgumentException ex) {
            configErrors.add("Incorrect value for property " + PROPERTY_XADESFORM + ": \"" + xadesForm + "\"");
        }
        
        // PROPERTY_TSA_URL, PROPERTY_TSA_USERNAME, PROPERTY_TSA_PASSWORD
        TSAParameters tsa = null;
        if (xadesProfile == Profiles.T) {
            final String tsaUrl = config.getProperties().getProperty(PROPERTY_TSA_URL);
            final String tsaUsername = config.getProperties().getProperty(PROPERTY_TSA_USERNAME);
            final String tsaPassword = config.getProperties().getProperty(PROPERTY_TSA_PASSWORD);
            
            if (tsaUrl == null) {
                configErrors.add("Property " + PROPERTY_TSA_URL + " is required when " + PROPERTY_XADESFORM + " is " + Profiles.T);
            } else {
                tsa = new TSAParameters(tsaUrl, tsaUsername, tsaPassword);
            }

            tsaHashAlgorithm = config.getProperties().getProperty(TSA_HASHALGORITHM); // TODO: Validate hash algorithm name.
        }

        // Only load SignaturePolicy-related settings if selected profile is atleast EPES.
        if (xadesProfile != Profiles.BES) parseSignaturePolicyConfig(config, configErrors);

        // TODO: Other configuration options
        final String commitmentTypesProperty = config.getProperties().getProperty(PROPERTY_COMMITMENT_TYPES);
        
        commitmentTypes = new LinkedList<AllDataObjsCommitmentTypeProperty>();
        
        if (commitmentTypesProperty != null) {
            if ("".equals(commitmentTypesProperty)) {
                configErrors.add("Commitment types can not be empty");
            } else if (!COMMITMENT_TYPES_NONE.equals(commitmentTypesProperty)) {
                for (final String part : commitmentTypesProperty.split(",")) {
                    final String type = part.trim();

                    try {
                        commitmentTypes.add(CommitmentTypes.valueOf(type).getProp());
                    } catch (IllegalArgumentException e) {
                        configErrors.add("Unkown commitment type: " + type);
                    }
                }
            }
        }

        parameters = new XAdESSignerParameters(xadesProfile, tsa);
        
        // Get the signature algorithm, hash algorithms, etc.
        signatureAlgorithm = config.getProperties().getProperty(SIGNATUREALGORITHM);
        dataObjsReferencesHashAlgorithm = config.getProperties().getProperty(DATAOBJSREFS_HASHALGORITHM);
        referencePropertiesHashAlgorithm = config.getProperties().getProperty(REFERENCE_PROPERTIES_HASHALGORITHM);

        // Get the signature type
        final String signatureTypeString = config.getProperties().getProperty(SIGNATURETYPE, DEFAULT_SIGNATURETYPE);

        try {
            signatureType = SignatureTypes.valueOf(signatureTypeString);
        } catch (IllegalArgumentException ex) {
            configErrors.add("Incorrect value for property " + SIGNATURETYPE + ": \"" + signatureTypeString + "\"");
        }

        if (signatureType == SignatureTypes.Enveloped)
        {
            signatureNode = config.getProperties().getProperty(SIGNATURENODE);
            if (signatureNode == null)
            {
                configErrors.add("A signature node must be specified if SignatureType == Enveloped");
            }
        }

        final String signatureIncludeChainString = config.getProperties().getProperty(SIGNATURE_INCLUDECHAIN);
        if (signatureIncludeChainString != null) {
            signatureIncludeChain = Boolean.parseBoolean(signatureIncludeChainString);
        }

        final String signatureSignKeyInfoString = config.getProperties().getProperty(SIGNATURE_SIGNKEYINFO);
        if (signatureSignKeyInfoString != null) {
            signatureSignKeyInfo = Boolean.parseBoolean(signatureSignKeyInfoString);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Worker " + workerId + " configured: " + parameters);
            if (!configErrors.isEmpty()) {
                LOG.error("Worker " + workerId + " configuration error(s): " + configErrors);
            }
        }
        
        LOG.trace("<init");
    }

    @Override
    public ProcessResponse processData(ProcessRequest signRequest, RequestContext requestContext) throws IllegalRequestException, CryptoTokenOfflineException, SignServerException {

        // Check that the request contains a valid GenericSignRequest object with a byte[].
        if (!(signRequest instanceof GenericSignRequest)) {
            throw new IllegalRequestException("Recieved request wasn't a expected GenericSignRequest.");
        }
        
        final ISignRequest sReq = (ISignRequest) signRequest;
        if (!(sReq.getRequestData() instanceof byte[])) {
            throw new IllegalRequestException("Recieved request data wasn't a expected byte[].");
        }

        if (!configErrors.isEmpty()) {
            throw new SignServerException("Worker is misconfigured");
        }
        
        
        final byte[] data = (byte[]) sReq.getRequestData();
        final String archiveId = createArchiveId(data, (String) requestContext.get(RequestContext.TRANSACTION_ID));
        final byte[] signedbytes;
        
        try {
            // Parse
            final XadesSigner signer = createSigner(parameters);
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(new ByteArrayInputStream(data));

            // Sign
            final Node node = doc.getDocumentElement();
            SignedDataObjects dataObjs;

            if (signatureType == SignatureTypes.Enveloping)
            {
                dataObjs = new SignedDataObjects(new EnvelopedXmlObject(node));
                signer.sign(dataObjs, doc);
            } 
            else if (signatureType == SignatureTypes.Enveloped)
            {
                String refUri;
                final XPath xpath = XPathFactory.newInstance().newXPath();
                Element elementToSign = (Element)xpath.evaluate(signatureNode, doc, XPathConstants.NODE);

                if (elementToSign == null) 
                    throw new SignServerException("Unable to find SignatureNode at the specified Xml document.");

                if (elementToSign.hasAttribute("Id"))
                    refUri = '#' + elementToSign.getAttribute("Id");
                else
                {
                    if (elementToSign.getParentNode().getNodeType() != Node.DOCUMENT_NODE)
                        throw new IllegalArgumentException("Element without Id must be the document root");
                    refUri = "";
                }

                DataObjectDesc dataObjRef = new DataObjectReference(refUri).withTransform(new EnvelopedSignatureTransform());
                dataObjs = new SignedDataObjects(dataObjRef);
                signer.sign(dataObjs, elementToSign);
            }
            else
            {
                throw new SignServerException("Invalid SignatureType setting.");
            }
            
            for (final AllDataObjsCommitmentTypeProperty commitmentType : commitmentTypes) {
                    dataObjs = dataObjs.withCommitmentType(commitmentType);
            }

            // Render result
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            trans.transform(new DOMSource(doc), new StreamResult(bout));
            signedbytes = bout.toByteArray();

        } catch (SAXException ex) {
            throw new IllegalRequestException("Document parsing error", ex);
        } catch (IOException ex) {
            throw new SignServerException("Document parsing error", ex);
        } catch (ParserConfigurationException ex) {
            throw new SignServerException("Document parsing error", ex);
        } catch (XadesProfileResolutionException ex) {
            throw new SignServerException("Exception in XAdES profile resolution", ex);
        } catch (XAdES4jException ex) {
            throw new SignServerException("Exception signing document", ex);
        } catch (TransformerException ex) {
            throw new SignServerException("Transformation failure", ex);
        } catch (XPathExpressionException ex) {
            throw new SignServerException("Exception finding SignatureNode using XPath", ex);
        }
        
        // Response
        final ProcessResponse response;
        final Collection<? extends Archivable> archivables = Arrays.asList(new DefaultArchivable(Archivable.TYPE_RESPONSE, CONTENT_TYPE, signedbytes, archiveId));
        if (signRequest instanceof GenericServletRequest) {
            response = new GenericServletResponse(sReq.getRequestID(), signedbytes, getSigningCertificate(), archiveId, archivables, CONTENT_TYPE);
        } else {
            response = new GenericSignResponse(sReq.getRequestID(), signedbytes, getSigningCertificate(), archiveId, archivables);
        }
        return response;
    }

    /**
     * Creates the signer implementation given the parameters.
     *
     * @param params Parameters such as XAdES form and TSA properties.
     * @return The signer implementation
     * @throws SignServerException In case an unsupported XAdES form was specified
     * @throws XadesProfileResolutionException if the dependencies of the signer cannot be resolved
     * @throws CryptoTokenOfflineException If the private key is not available
     */
    private XadesSigner createSigner(final XAdESSignerParameters params) throws SignServerException, XadesProfileResolutionException, CryptoTokenOfflineException {
        // Setup key and certificiates
        final List<X509Certificate> xchain = new LinkedList<X509Certificate>();
        for (Certificate cert : this.getSigningCertificateChain()) {
            if (cert instanceof X509Certificate) {
                xchain.add((X509Certificate) cert);
            }
            // Some XADES Validator (ie. MITyCXADES) fail when
            // SigningCertificate includes more than one Cert node.
            if (!signatureIncludeChain) break;
        }
        final KeyingDataProvider kdp = new CertificateAndChainKeyingDataProvider(xchain, this.getCryptoToken().getPrivateKey(ICryptoToken.PURPOSE_SIGN));
        
        // Signing profile
        XadesSigningProfile xsp;
        switch (params.getXadesForm()) {
            case BES:
                xsp = new XadesBesSigningProfile(kdp)
                    .withBasicSignatureOptionsProvider(new SignatureOptionsProvider())
                    .withAlgorithmsProviderEx(new AlgorithmsProvider());
                break;
            case EPES:
                xsp = new XadesEpesSigningProfile(kdp, new SignaturePolicyProvider())
                    .withBasicSignatureOptionsProvider(new SignatureOptionsProvider())
                    .withAlgorithmsProviderEx(new AlgorithmsProvider());
                break;
            case T:
                xsp = new XadesTSigningProfile(kdp)
                    .withBasicSignatureOptionsProvider(new SignatureOptionsProvider())
                    .withTimeStampTokenProvider(timeStampTokenProviderImplementation)
                    .withBinding(TSAParameters.class, params.getTsaParameters())
                    .withAlgorithmsProviderEx(new AlgorithmsProvider());

                if (signaturePolicyUrl != null)
                    xsp = ((XadesTSigningProfile)xsp).withPolicyProvider(new SignaturePolicyProvider());
                break;
            case C:
            default:
                throw new SignServerException("Unsupported XAdES profile configured");
        }
        
        return (XadesSigner) xsp.newSigner();
    }

    @Override
    protected List<String> getFatalErrors() {
        final LinkedList<String> errors = new LinkedList<String>(super.getFatalErrors());
        errors.addAll(configErrors);
        return errors;
    }

    public XAdESSignerParameters getParameters() {
        return parameters;
    }
    
    /**
     * Used by the unit test to override the time stamp token provider.
     * 
     * @param implementation
     */
    public void setTimeStampTokenProviderImplementation(final Class<? extends TimeStampTokenProvider> implementation) {
        timeStampTokenProviderImplementation = implementation;
    }
    
    /**
     * Implemenation of {@link xades4j.providers.AlgorithmsProviderEx} using the
     * signature algorithm configured for the worker (or the default values).
     */
    private class AlgorithmsProvider extends DefaultAlgorithmsProviderEx {

        @Override
        public Algorithm getSignatureAlgorithm(String keyAlgorithmName)
                throws UnsupportedAlgorithmException {
            if (signatureAlgorithm == null) {
                if ("EC".equals(keyAlgorithmName)) {
                    // DefaultAlgorithmsProviderEx only handles RSA and DSA
                    return new GenericAlgorithm(SIGNATURE_METHOD_ECDSA_SHA1);
                }
                // use default xades4j behavior when not configured for the worker
                return super.getSignatureAlgorithm(keyAlgorithmName);
            }
            
            if ("SHA1withRSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SignatureMethod.RSA_SHA1);
            } else if ("SHA256withRSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_RSA_SHA256);
            } else if ("SHA384withRSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_RSA_SHA384); 
            } else if ("SHA512withRSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_RSA_SHA512);
            } else if ("SHA1withDSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SignatureMethod.DSA_SHA1);
            } else if ("SHA1withECDSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_ECDSA_SHA1);
            } else if ("SHA256withECDSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_ECDSA_SHA256);
            } else if ("SHA384withECDSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_ECDSA_SHA384);
            } else if ("SHA512withECDSA".equals(signatureAlgorithm)) {
                return new GenericAlgorithm(SIGNATURE_METHOD_ECDSA_SHA512);
            } else {
                throw new UnsupportedAlgorithmException("Unsupported signature algorithm", signatureAlgorithm);
            }
        }

        private String getDigestAlgorithmForName(String name)
        {
            if ("SHA1".equals(name)) return MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1;
            else if ("SHA256".equals(name)) return MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256;
            else if ("SHA384".equals(name)) return MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA384;
            else if ("SHA512".equals(name)) return MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA512;

            return null;
        }


        private String getDigestAlgorithmFromSignatureAlgorithm()
        {
            if (signatureAlgorithm == null) return null;
            final Integer pos = signatureAlgorithm.indexOf("with");
            return pos >= 0 ? getDigestAlgorithmForName(signatureAlgorithm.substring(0, pos)) : null;
        }

        @Override
        public String getDigestAlgorithmForDataObjsReferences()
        {
            if (dataObjsReferencesHashAlgorithm != null) {
                return getDigestAlgorithmForName(dataObjsReferencesHashAlgorithm);
            }

            final String alg = getDigestAlgorithmFromSignatureAlgorithm();
            return alg != null ? alg : super.getDigestAlgorithmForDataObjsReferences();
        }

        @Override
        public String getDigestAlgorithmForReferenceProperties()
        {
            if (referencePropertiesHashAlgorithm != null) {
                return getDigestAlgorithmForName(referencePropertiesHashAlgorithm);
            }

            final String alg = getDigestAlgorithmFromSignatureAlgorithm();
            return alg != null ? alg : super.getDigestAlgorithmForReferenceProperties();
        }

        @Override
        public String getDigestAlgorithmForTimeStampProperties()
        {
            if (tsaHashAlgorithm != null) {
                return getDigestAlgorithmForName(tsaHashAlgorithm);
            }

            final String alg = getDigestAlgorithmFromSignatureAlgorithm();
            return alg != null ? alg : super.getDigestAlgorithmForTimeStampProperties();
        }
    }

    /** 
     * Implementation of {@link xades4j.providers.SignaturePolicyInfoProvider} using the
     * policy details specified by worker's configuration.
     */
    private class SignaturePolicyProvider implements SignaturePolicyInfoProvider
    {
        // This performs lazy-loading of signature policy bytes, to avoid 
        // issues when network may not be available during signserver's 
        // initialization. (pruiz)
        private byte[] getSignaturePolicyBytes() throws IOException {

            synchronized(signaturePolicyUrl) {
                if (signaturePolicyBytes == null) {
                    InputStream stream = null;

                    try {
                        stream = signaturePolicyUrl.openStream();
                        signaturePolicyBytes = IOUtils.toByteArray(stream);
                    }
                    finally {
                        if (stream != null) stream.close();
                    }
                }
            }
            return signaturePolicyBytes;
        }

        public SignaturePolicyBase getSignaturePolicy() {
            try {
                final String identifier = signaturePolicyIdentifier != null ? signaturePolicyIdentifier : signaturePolicyUrl.toString();
                return new SignaturePolicyIdentifierProperty(new ObjectIdentifier(identifier), getSignaturePolicyBytes());
            } catch (IOException ex) {
                LOG.error("Unable to load SignaturePolicyIdentifier from url: " + signaturePolicyUrl.toString());
                throw new ProviderException("Unable to load SignaturePolicyIdentifier from url: " + signaturePolicyUrl.toString());
            }
        }
    }

    /**
     * Extension of {@link xades4j.providers.impl.DefaultBasicSignatureOptionsProvider}
     * used to indicate signing certificate's KeyInfo should be accounted for during
     * signature computation.
     */
    private class SignatureOptionsProvider extends DefaultBasicSignatureOptionsProvider {
        @Override
        public boolean signSigningCertificate() {
            return signatureSignKeyInfo;
        }
    }
}
/* vim: set nowrap softtabstop=4 shiftwidth=4 smartindent smarttab expandtab */
