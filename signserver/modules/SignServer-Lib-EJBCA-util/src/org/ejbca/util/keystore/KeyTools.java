/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
 
package org.ejbca.util.keystore;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.ArrayList;
import java.util.Properties;
import javax.crypto.Cipher;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.ec.EC5Util;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.ejbca.core.model.ca.catoken.CATokenConstants;
import org.ejbca.core.model.util.AlgorithmTools;
import org.ejbca.cvc.PublicKeyEC;
import org.ejbca.util.CertTools;
import org.ejbca.util.FileTools;


/**
 * Tools to handle common key and keystore operations.
 *
 * @version $Id: KeyTools.java 8245 2009-11-04 12:42:07Z anatom $
 */
public class KeyTools {
    private static Logger log = Logger.getLogger(KeyTools.class);

    /** The name of Suns pkcs11 implementation */
    public static final String SUNPKCS11CLASS = "sun.security.pkcs11.SunPKCS11";
    public static final String IAIKPKCS11CLASS = "iaik.pkcs.pkcs11.provider.IAIKPkcs11";
        
    /**
     * Prevent from creating new KeyTools object
     */
    private KeyTools() {
    }

    /**
     * Generates a keypair
     *
     * @param keyspec specification of keys to generate, typical value is 1024 for RSA or DSA keys, or prime192v1 for ECDSA keys
     * @param keyalg algorithm of keys to generate, typical value is RSA, DSA or ECDSA, see org.ejbca.core.model.ca.catoken.CATokenConstants.KEYALGORITHM_XX
     * 
     * @see org.ejbca.core.model.ca.catoken.CATokenConstants
     * @see org.bouncycastle.asn1.x9.X962NamedCurves
     * @see org.bouncycastle.asn1.nist.NISTNamedCurves
     * @see org.bouncycastle.asn1.sec.SECNamedCurves
     * 
     * @return KeyPair the generated keypair
     * @throws InvalidAlgorithmParameterException 
     * @see org.ejbca.core.model.ca.catoken.CATokenConstants#KEYALGORITHM_RSA
     */
    public static KeyPair genKeys(String keySpec, String keyAlg)
        throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
    	if (log.isTraceEnabled()) {
            log.trace(">genKeys("+keySpec+", "+keyAlg+")");    		
    	}

        KeyPairGenerator keygen = KeyPairGenerator.getInstance(keyAlg, "BC");
        if (StringUtils.equals(keyAlg, CATokenConstants.KEYALGORITHM_ECDSA)) {
        	org.bouncycastle.jce.spec.ECParameterSpec ecSpec = null;
        	if ( (keySpec == null) || StringUtils.equals(keySpec,"implicitlyCA") ) {
        		log.debug("Generating implicitlyCA encoded ECDSA key pair");
            	// If the keySpec is null, we have "implicitlyCA" defined EC parameters
        		// The parameters were already installed when we installed the provider
        		// We just make sure that ecSpec == null here
        	} else {
        		log.debug("Generating named curve ECDSA key pair: "+keySpec);
            	// We have EC keys
            	ecSpec = ECNamedCurveTable.getParameterSpec(keySpec); 
            	if (ecSpec == null) {
            		throw new InvalidAlgorithmParameterException("keySpec "+keySpec+" is invalid for ECDSA.");
            	}
        	}
        	keygen.initialize(ecSpec, new SecureRandom());
        } else {
        	// RSA or DSA keys
        	int keysize = Integer.parseInt(keySpec);
            keygen.initialize(keysize);
        }

        KeyPair keys = keygen.generateKeyPair();

        if (log.isDebugEnabled()) {
            PublicKey pk = keys.getPublic();
        	int len = getKeyLength(pk);
            log.debug("Generated " + keys.getPublic().getAlgorithm() + " keys with length " + len);        	
        }
		log.trace("<genKeys()");
        return keys;
    } // genKeys

    /** An ECDSA key can be stripped of the curve parameters so it only contains the public point, and this is not enough to 
     * use the key for verification. However, if we know the curve name we can fill in the curve parameters and get a usable EC public key 
     * 
     * @param pk PublicKey that might miss parameters, of parameters are there we do not touch the public key just return it unchanged
     * @param keySpec name of curve for example brainpoolp224r1
     * @return PublicKey with parameters from the named curve
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws InvalidKeySpecException 
     */
    public static PublicKey getECPublicKeyWithParams(PublicKey pk, String keySpec) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    	PublicKey ret = pk;
    	if ( (pk instanceof PublicKeyEC) && (keySpec != null) ) {
    		PublicKeyEC pkec = (PublicKeyEC) pk;
    		// The public key of IS and DV certificate do not have any parameters so we have to do some magic to get a complete EC public key
    		ECParameterSpec spec = pkec.getParams();
    		if (spec == null) {
    			// we did not have the parameter specs, lets create them because we know which curve we are using
    			org.bouncycastle.jce.spec.ECParameterSpec bcspec = ECNamedCurveTable.getParameterSpec(keySpec);
    			java.security.spec.ECPoint p = pkec.getW();
    			org.bouncycastle.math.ec.ECPoint ecp = EC5Util.convertPoint(bcspec.getCurve(), p, false);
    			ECPublicKeySpec pubKey = new ECPublicKeySpec(ecp, bcspec);
		        KeyFactory keyfact = KeyFactory.getInstance("ECDSA", "BC");
    	        ret = keyfact.generatePublic(pubKey);
    		}
    	} 
    	return ret;
    }
    
    public static PublicKey getECPublicKeyWithParams(PublicKey pk, PublicKey pkwithparams) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    	PublicKey ret = pk;
    	if ( (pk instanceof PublicKeyEC) && (pkwithparams instanceof PublicKeyEC) ) {
    		PublicKeyEC pkec = (PublicKeyEC) pk;
    		// The public key of IS and DV certificate do not have any parameters so we have to do some magic to get a complete EC public key
    		ECParameterSpec spec = pkec.getParams();
    		if (spec == null) {
        		PublicKeyEC pkecp = (PublicKeyEC) pkwithparams;
        		ECParameterSpec pkspec = pkecp.getParams();
        		if (pkspec != null) {
        			org.bouncycastle.jce.spec.ECParameterSpec bcspec = EC5Util.convertSpec(pkspec, false);
        			java.security.spec.ECPoint p = pkec.getW();
        			org.bouncycastle.math.ec.ECPoint ecp = EC5Util.convertPoint(pkspec, p, false);
        			ECPublicKeySpec pubKey = new ECPublicKeySpec(ecp, bcspec);
    		        KeyFactory keyfact = KeyFactory.getInstance("ECDSA", "BC");
    		        ret = keyfact.generatePublic(pubKey);        			
        		} else {
        			log.info("pkwithparams does not have any params.");
        		}
    		}
    	} else {
    		log.info("Either pk or pkwithparams is not a PublicKeyEC: "+pk.toString()+", "+pkwithparams.toString());
    	}
    	return ret;
    }

    /**
     * Gets the key length of supported keys
     * @param pk PublicKey used to derive the keysize
     * @return -1 if key is unsupported, otherwise a number >= 0. 0 usually means the length can not be calculated, 
     * for example if the key is an EC key and the "implicitlyCA" encoding is used.
     */
	public static int getKeyLength(PublicKey pk) {
		int len = -1;
		if (pk instanceof RSAPublicKey) {
			RSAPublicKey rsapub = (RSAPublicKey) pk;
			len = rsapub.getModulus().bitLength();
		} else if (pk instanceof JCEECPublicKey) {
			JCEECPublicKey ecpriv = (JCEECPublicKey) pk;
			org.bouncycastle.jce.spec.ECParameterSpec spec = ecpriv.getParameters();
			if (spec != null) {
				len = spec.getN().bitLength();				
			} else {
				// We support the key, but we don't know the key length
				len = 0;
			}
		} else if (pk instanceof ECPublicKey) {
			ECPublicKey ecpriv = (ECPublicKey) pk;
			java.security.spec.ECParameterSpec spec = ecpriv.getParams();
			if (spec != null) {
				len = spec.getOrder().bitLength(); // does this really return something we expect?
			} else {
				// We support the key, but we don't know the key length
				len = 0;
			}
		} else if (pk instanceof DSAPublicKey) {
			DSAPublicKey dsapub = (DSAPublicKey) pk;
			if ( dsapub.getParams() != null ) {
				len = dsapub.getParams().getP().bitLength();
			} else {
				len = dsapub.getY().bitLength();
			}
		} 
		return len;
	}

    /**
     * Gets the key AlgorithmParameterSpec of supported keys. Can be used to initialize a KeyPairGenerator to generate a key of equal type and size.
     * @param pk PublicKey used to derive the AlgorithmParameterSpec
     * @return null if key is unsupported, otherwise a AlgorithmParameterSpec.
     */
	public static AlgorithmParameterSpec getKeyGenSpec(PublicKey pk) {
		AlgorithmParameterSpec ret = null;
		if (pk instanceof RSAPublicKey) {
			log.debug("getKeyGenSpec: RSA");
			RSAPublicKey rpk = (RSAPublicKey)pk;
			ret = new RSAKeyGenParameterSpec(getKeyLength(pk), rpk.getPublicExponent());
		} else if (pk instanceof DSAPublicKey) {
			log.debug("getKeyGenSpec: DSA");
			DSAPublicKey dpk = (DSAPublicKey)pk;
			DSAParams params = dpk.getParams();
			ret = new DSAParameterSpec(params.getP(), params.getQ(), params.getG());
		} else if (pk instanceof ECPublicKey) {
			log.debug("getKeyGenSpec: ECPublicKey");
			ECPublicKey ecpub = (ECPublicKey) pk;
			java.security.spec.ECParameterSpec sunsp = ecpub.getParams();
			EllipticCurve ecurve = new EllipticCurve(sunsp.getCurve().getField(), sunsp.getCurve().getA(), sunsp.getCurve().getB());
			//ECParameterSpec par = new ECNamedCurveSpec(null, sunsp.getCurve(), sunsp.getGenerator(), sunsp.getOrder(), BigInteger.valueOf(sunsp.getCofactor()));
			ECParameterSpec params = new ECParameterSpec(ecurve, sunsp.getGenerator(), sunsp.getOrder(), sunsp.getCofactor());
			if (log.isDebugEnabled()) {
				log.debug("Fieldsize: "+params.getCurve().getField().getFieldSize());
				EllipticCurve curve = params.getCurve();
				log.debug("CurveA: "+curve.getA().toString(16));
				log.debug("CurveB: "+curve.getB().toString(16));
				log.debug("CurveSeed: "+curve.getSeed());
				ECFieldFp field = (ECFieldFp)curve.getField();
				log.debug("CurveSfield: "+field.getP().toString(16));
				ECPoint p = params.getGenerator();
				log.debug("Generator: "+p.getAffineX().toString(16)+", "+p.getAffineY().toString(16));
				log.debug("Order: "+params.getOrder().toString(16));
				log.debug("CoFactor: "+params.getCofactor());				
			}
			ret = params;
		} else if (pk instanceof JCEECPublicKey) {
			log.debug("getKeyGenSpec: JCEECPublicKey");
			JCEECPublicKey ecpub = (JCEECPublicKey) pk;
			org.bouncycastle.jce.spec.ECParameterSpec bcsp = ecpub.getParameters();
			ECCurve curve = bcsp.getCurve();
			//TODO: this probably does not work for key generation with the Sun PKCS#11 provider. Maybe seed needs to be set to null as above? Or something else, the BC curve is it the same?
			ECParameterSpec params = new ECNamedCurveSpec(null, curve, bcsp.getG(), bcsp.getN(), bcsp.getH());
			ret = params;
			//EllipticCurve ecc = new EllipticCurve(curve.)
			//ECParameterSpec sp = new ECParameterSpec(, bcsp.getG(), bcsp.getN(), bcsp.getH().intValue());
		}
		return ret;
	}

    /**
     * Retrieves the certificate chain from a keystore.
     *
     * @param keyStore the keystore, which has been loaded and opened.
     * @param privateKeyAlias the alias of the privatekey for which the certchain belongs.
     *
     * @return array of Certificate, or null if no certificates are found.
     */
    public static Certificate[] getCertChain(KeyStore keyStore, String privateKeyAlias)
        throws KeyStoreException {
    	if (log.isTraceEnabled()) {
    		log.trace(">getCertChain: alias='" + privateKeyAlias + "'");
    	}
        Certificate[] certchain = keyStore.getCertificateChain(privateKeyAlias);
        if (certchain == null) {
            return null;
        }
        log.debug("Certchain retrieved from alias '" + privateKeyAlias + "' has length " +
            certchain.length);

        if (certchain.length < 1) {
            log.error("Cannot load certificate chain with alias '" + privateKeyAlias +
                "' from keystore.");
            if (log.isTraceEnabled()) {
            	log.trace("<getCertChain: alias='" + privateKeyAlias + "', retlength=" + certchain.length);
            }
            return certchain;
        } else if (certchain.length > 0) {
            if (CertTools.isSelfSigned((X509Certificate) certchain[certchain.length - 1])) {
                log.debug("Issuer='" +
                    CertTools.getIssuerDN((X509Certificate) certchain[certchain.length - 1]) +
                    "'.");
                log.debug("Subject='" +
                    CertTools.getSubjectDN((X509Certificate) certchain[certchain.length - 1]) +
                    "'.");
                if (log.isTraceEnabled()) {
                	log.trace("<getCertChain: alias='" + privateKeyAlias + "', retlength=" + certchain.length);
                }
                return certchain;
            }
        }

        // If we came here, we have a cert which is not root cert in 'cert'
        ArrayList array = new ArrayList();

        for (int i = 0; i < certchain.length; i++) {
            array.add(certchain[i]);
        }

        boolean stop = false;

        while (!stop) {
            X509Certificate cert = (X509Certificate) array.get(array.size() - 1);
            String ialias = CertTools.getPartFromDN(CertTools.getIssuerDN(cert), "CN");
            Certificate[] chain1 = keyStore.getCertificateChain(ialias);

            if (chain1 == null) {
                stop = true;
            } else {
                log.debug("Loaded certificate chain with length " + chain1.length +
                    " with alias '" + ialias + "'.");

                if (chain1.length == 0) {
                    log.error("No RootCA certificate found!");
                    stop = true;
                }

                for (int j = 0; j < chain1.length; j++) {
                    array.add(chain1[j]);

                    // If one cert is slefsigned, we have found a root certificate, we don't need to go on anymore
                    if (CertTools.isSelfSigned((X509Certificate) chain1[j])) {
                        stop = true;
                    }
                }
            }
        }

        Certificate[] ret = new Certificate[array.size()];

        for (int i = 0; i < ret.length; i++) {
            ret[i] = (X509Certificate) array.get(i);
            log.debug("Issuer='" + CertTools.getIssuerDN((X509Certificate) ret[i]) + "'.");
            log.debug("Subject='" + CertTools.getSubjectDN((X509Certificate) ret[i]) + "'.");
        }
        if (log.isTraceEnabled()) {
        	log.trace("<getCertChain: alias='" + privateKeyAlias + "', retlength=" + ret.length);
        }
        return ret;
    } // getCertChain

    /**
     * create the subject key identifier.
     *
     * @param pubKey the public key
     *
     * @return SubjectKeyIdentifer asn.1 structure
     */
    public static SubjectKeyIdentifier createSubjectKeyId(PublicKey pubKey) {
        try {
            ByteArrayInputStream bIn = new ByteArrayInputStream(pubKey.getEncoded());
            SubjectPublicKeyInfo info = new SubjectPublicKeyInfo((ASN1Sequence) new ASN1InputStream(
                        bIn).readObject());

            return new SubjectKeyIdentifier(info);
        } catch (Exception e) {
            throw new RuntimeException("error creating key");
        }
    } // createSubjectKeyId

    /** Creates a SUN or IAIK PKCS#11 provider using the passed in pkcs11 library. First we try to see if the IAIK provider is available,
     * because it supports more algorithms. If the IAIK provider is not available in the classpath, we try the SUN provider.
     * 
     * @param slot pkcs11 slot number or null if a config file name is provided as fileName
     * @param fileName the manufacturers provided pkcs11 library (.dll or .so) or config file name if slot is null 
     * @param isIndex specifies if the slot is a slot number or a slotIndex
     * @param attributesFile a file specifying PKCS#11 attributes (used mainly for key generation) in the format specified in the "JavaTM PKCS#11 Reference Guide", http://java.sun.com/javase/6/docs/technotes/guides/security/p11guide.html
     * 
     * Example contents of attributes file:
     * 
     * attributes(generate,CKO_PRIVATE_KEY,*) = {
     *  CKA_PRIVATE = true
     *  CKA_SIGN = true
     *  CKA_DECRYPT = true
     *  CKA_TOKEN = true
     * }
     * 
     * See also html documentation for PKCS#11 HSMs in EJBCA.
     * 
     * @return AuthProvider of type "sun.security.pkcs11.SunPKCS11" or 
     * @throws IOException if the pkcs11 library can not be found, or the PKCS11 provider can not be created.
     */ 
    public static Provider getP11Provider(final String slot, final String fileName,
                                          final boolean isIndex, final String attributesFile) throws IOException {
    	if (StringUtils.isEmpty(fileName)) {
    		throw new IOException("A file name must be supplied.");
    	}
    	final File libFile = new File(fileName);
    	if ( !libFile.isFile() || !libFile.canRead() ) {
    		throw new IOException("The file "+fileName+" can't be read.");
    	}
        if ( slot==null )
            return getP11Provider(new FileInputStream(fileName), null);

        // Properties for the SUN PKCS#11 provider
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	PrintWriter pw = new PrintWriter(baos);
    	pw.println("name = "+libFile.getName()+"-slot"+slot);
    	pw.println("library = "+libFile.getCanonicalPath());

        final int slotNr;
        try {
            if (slot.length()>0)
                slotNr = Integer.parseInt(slot);
            else
                slotNr = -1;
        } catch( NumberFormatException e ) {
            throw new IOException("Slot nr "+slot+" not an integer.");
        }
    	if ( slotNr>=0 ) {
        	pw.println("slot"+(isIndex ? "ListIndex":"")+" = "+slot);    		
    	}
    	if (attributesFile != null) {
    		byte[] attrs = FileTools.readFiletoBuffer(attributesFile);
    		pw.println(new String(attrs));
    	}
    	pw.flush();
    	pw.close();
    	if (log.isDebugEnabled()) {
    		log.debug(baos.toString());
    	}

        // Properties for the IAIK PKCS#11 provider
    	Properties prop = new Properties();
    	prop.setProperty("PKCS11_NATIVE_MODULE", libFile.getCanonicalPath());
    	// If using Slot Index it is denoted by brackets in iaik
    	prop.setProperty("SLOT_ID", isIndex ? ("["+slot+"]") : slot);    
    	if (log.isDebugEnabled()) {
    		log.debug(prop.toString());
    	}
        return getP11Provider(new ByteArrayInputStream(baos.toByteArray()), prop);
    }
    /**
     * 
     * @param is for the SUN PKCS#11 provider
     * @param prop for the IAIK PKCS#11 provider
     * @return Java security Provider for a PCKS#11 token
     * @throws IOException if neither the IAIK or the SUN provider can be created
     */
    private static Provider getP11Provider(final InputStream is, Properties prop) throws IOException {

        // We will construct the PKCS11 provider (sun.security..., or iaik...) using reflection, because 
        // the sun class does not exist on all platforms in jdk5, and we want to be able to compile everything.
        // The below code replaces the single line (for the SUN provider):
        //   return new SunPKCS11(new ByteArrayInputStream(baos.toByteArray()));

        // We will first try to construct the more competent IAIK provider, if it exists in the classpath
        // if that does not exist, we will revert back to use the SUN provider
        if (prop!=null) try {
            final Class implClass = Class.forName(IAIKPKCS11CLASS);
            log.info("Using IAIK PKCS11 provider: "+IAIKPKCS11CLASS);
            // iaik PKCS11 has Properties as constructor argument
            return (Provider)implClass.getConstructor(Properties.class).newInstance(new Object[] {prop});
        } catch (Exception e) {
            // do nothing here. Sun provider is tested below.
        }
        try {
            // Sun PKCS11 has InputStream as constructor argument
            final Class implClass = Class.forName(SUNPKCS11CLASS);
            log.info("Using SUN PKCS11 provider: "+SUNPKCS11CLASS);
            return (Provider)implClass.getConstructor(InputStream.class).newInstance(new Object[] {is});
        } catch (Exception e2) {
            log.error("Error constructing pkcs11 provider: "+e2.getMessage());
            IOException ioe = new IOException("Error constructing pkcs11 provider: "+e2.getMessage());
            ioe.initCause(e2);
            throw ioe;
        }
    }
    /**
     * @param Input stream for sun configuration file.
     * @return The Sun provider
     * @throws IOException
     */
    public static Provider getSunP11Provider(final InputStream is) throws IOException {
        return getP11Provider(is, null);
    }

    /**
     * Detect if "Unlimited Strength" Policy files hase bean properly installed.
     * 
     * @return true if key strength is limited
     */
    public static boolean isUsingExportableCryptography()
    {
    	boolean returnValue = true;
    	try {
    		int keylen = Cipher.getMaxAllowedKeyLength("DES");
    		log.debug("MaxAllowedKeyLength for DES is: "+keylen);
			if (  keylen == Integer.MAX_VALUE ) {
				returnValue = false;
			}
		} catch (NoSuchAlgorithmException e) {
		}
		return returnValue;
    }
    
    /**
     * Sign provided data with specified private key and algortihm
     * 
     * @param privateKey the private key
     * @param signatureAlgorithm e.g. as returned by caToken.getCATokenInfo().getSignatureAlgorithm()
     * @param data the data to sign
     * @return the signature
     */
    public static byte[] signData(PrivateKey privateKey , String signatureAlgorithm, byte[] data) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
    	Signature signer = Signature.getInstance(signatureAlgorithm);
        signer.initSign(privateKey);
        signer.update(data);
        return (signer.sign());
    }

    /**
     * Verify signed data with specified public key, algorith and signature
     * 
     * @param publicKey the public key
     * @param signatureAlgorithm e.g. as returned by caToken.getCATokenInfo().getSignatureAlgorithm()
     * @param data the data to verify
     * @param signature the signature
     * @return true if the signature is ok 
     */
    public static boolean verifyData(PublicKey publicKey , String signatureAlgorithm, byte[] data, byte[] signature) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature signer = Signature.getInstance(signatureAlgorithm);
        signer.initVerify(publicKey);
        signer.update(data);
        return (signer.verify(signature));    	

    }

    /** Testing a key pair to verify that it is possible to first sign and then verify with it.
     * 
     * @param priv
     * @param pub
     * @param provider A provider used for signing with the private key, or null if "BC" should be used.
     * @throws Exception
     */
    public static void testKey(PrivateKey priv, PublicKey pub, String provider) throws Exception {
        final byte input[] = "Lillan gick pa vagen ut, motte dar en katt...".getBytes();
        final byte signBV[];
        String testSigAlg = (String)AlgorithmTools.getSignatureAlgorithms(pub).iterator().next();
        if ( testSigAlg == null ) {
        	testSigAlg = "SHA256WithRSA";
        }
        {
        	String prov = "BC";
        	if (provider != null) {
        		prov = provider;
        	}
            Signature signature = Signature.getInstance(testSigAlg, prov);
            signature.initSign( priv );
            signature.update( input );
            signBV = signature.sign();
        }{
            Signature signature = Signature.getInstance(testSigAlg, "BC");
            signature.initVerify(pub);
            signature.update(input);
            if ( !signature.verify(signBV) ) {
                throw new InvalidKeyException("Not possible to sign and then verify with key pair.");
            }
        }
    }

} // KeyTools
