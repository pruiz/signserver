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

import org.cesecore.util.CertTools;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import static org.signserver.common.util.PropertiesConstants.AUTHORIZED_CLIENTS;
import static org.signserver.common.util.PropertiesConstants.KEYSTORE_DATA;
import static org.signserver.common.util.PropertiesConstants.SIGNERCERT;
import static org.signserver.common.util.PropertiesConstants.SIGNERCERTCHAIN;

/**
 * 
 * Class used to store signer specific configuration.
 * 
 * @author Philip Vendil 2007 jan 23
 * @version $Id$
 */
public class ProcessableConfig {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(ProcessableConfig.class);
    
    
    private WorkerConfig workerConfig;

    public ProcessableConfig(WorkerConfig workerConfig) {
        this.workerConfig = workerConfig;
        if (get(AUTHORIZED_CLIENTS) == null) {
            put(AUTHORIZED_CLIENTS, new HashSet<AuthorizedClient>());
        }
        if (get(SIGNERCERT) == null) {
            put(SIGNERCERT, "");
        }
        if (get(SIGNERCERTCHAIN) == null) {
            put(SIGNERCERTCHAIN, "");
        }

        put(WorkerConfig.CLASS, this.getClass().getName());
    }

    private void put(String key, Serializable value) {
        if (value instanceof String) {
            workerConfig.setProperty(key, (String) value);
        } else {
            workerConfig.getData().put(key, value);
        }
    }

    private Serializable get(String key) {
        final String value = workerConfig.getProperty(key);
        if (value == null) {
            final Object o = workerConfig.getData().get(key);
            if (o instanceof Serializable) {
                return (Serializable) o;
            } else {
                return null;
            }
        }
        return value;
    }

    /**
     * Adds a Certificate SN to the collection of authorized clients	  
     * 
     * @param client the AuthorizedClient to add
     */
    @SuppressWarnings("unchecked")
    public void addAuthorizedClient(AuthorizedClient client) {
        ((HashSet<AuthorizedClient>) get(AUTHORIZED_CLIENTS)).add(client);
    }

    /**
     * Removes a Certificate SN from the collection of authorized clients	  
     * 
     * @param client the AuthorizedClient to remove
     */
    @SuppressWarnings("unchecked")
    public boolean removeAuthorizedClient(AuthorizedClient client) {
        Iterator<AuthorizedClient> iter = ((HashSet<AuthorizedClient>) get(AUTHORIZED_CLIENTS)).iterator();
        while (iter.hasNext()) {
            AuthorizedClient next = iter.next();
            if (next.getCertSN().equals(client.getCertSN()) && next.getIssuerDN().equals(client.getIssuerDN())) {
                return ((HashSet<AuthorizedClient>) get(AUTHORIZED_CLIENTS)).remove(next);
            }
        }
        return false;
    }

    /**
     * 	  
     * Gets a collection of authorized client certificates
     * 
     * @return a Collection of String containing the certificate serial number.
     */
    @SuppressWarnings("unchecked")
    public Collection<AuthorizedClient> getAuthorizedClients() {
        ArrayList<AuthorizedClient> result = new ArrayList<AuthorizedClient>();
        Iterator<AuthorizedClient> iter = ((HashSet<AuthorizedClient>) get(AUTHORIZED_CLIENTS)).iterator();
        while (iter.hasNext()) {
            result.add(iter.next());
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Checks if a certificate is in the list of authorized clients
     * @param clientCertificate
     * @return true if client is authorized.
     */
    @SuppressWarnings("unchecked")
    public boolean isClientAuthorized(X509Certificate clientCertificate) {
        AuthorizedClient client = new AuthorizedClient(clientCertificate.getSerialNumber().toString(16), clientCertificate.getIssuerDN().toString());

        return ((HashSet<AuthorizedClient>) get(AUTHORIZED_CLIENTS)).contains(client);
    }

    /**
     * Method used to fetch a signers certificate from the config
     * @return the signer certificate stored or null if no certificate have been uploaded.
     * 
     */
    public X509Certificate getSignerCertificate() {
        X509Certificate result = null;
        String stringcert = (String) get(SIGNERCERT);
        if (stringcert == null || stringcert.equals("")) {
            stringcert = (String) get(WorkerConfig.getNodeId() + "." + SIGNERCERT);
        }

        if (stringcert != null && !stringcert.equals("")) {
            Collection<?> certs;
            try {
                certs = CertTools.getCertsFromPEM(new ByteArrayInputStream(stringcert.getBytes()));
                if (certs.size() > 0) {
                    result = (X509Certificate) certs.iterator().next();
                }
            } catch (CertificateException e) {
                LOG.error(e);
            } catch (IllegalStateException e) {
                LOG.error(e);
            }

        }

        if (result == null) {
            // try fetch certificate from certificate chain
            Collection<?> chain = getSignerCertificateChain();
            if (chain != null) {
                Iterator<?> iter = chain.iterator();
                while (iter.hasNext()) {
                    X509Certificate next = (X509Certificate) iter.next();
                    if (next.getBasicConstraints() == -1) {
                        result = next;
                    }
                }
            }
        }
        return result;

    }

    /**
     * Method used to store a signers certificate in the config
     * @param signerCert
     * 
     */
    public void setSignerCertificate(X509Certificate signerCert, String scope) {
        ArrayList<Certificate> list = new ArrayList<Certificate>();
        list.add(signerCert);
        if (scope.equals(GlobalConfiguration.SCOPE_GLOBAL)) {
            try {
                String stringcert =
                        new String(CertTools.getPemFromCertificateChain(list));
                put(SIGNERCERT, stringcert);
            } catch (CertificateException e) {
                LOG.error(e);
            }
        } else {
            try {
                String stringcert =
                        new String(CertTools.getPemFromCertificateChain(list));
                put(WorkerConfig.getNodeId() + "." + SIGNERCERT, stringcert);
            } catch (CertificateException e) {
                LOG.error(e);
            }
        }

    }

    /**
     * Method used to fetch a signers certificate chain from the config
     * @return the signer certificate stored or null if no certificates have been uploaded.
     * 
     */
    @SuppressWarnings("unchecked")
    public List<Certificate> getSignerCertificateChain() {
        List<Certificate> result = null;
        String stringcert = (String) get(SIGNERCERTCHAIN);
        if (stringcert == null || stringcert.equals("")) {
            stringcert = (String) get(WorkerConfig.getNodeId() + "." + SIGNERCERTCHAIN);
        }

        if (stringcert != null && !stringcert.equals("")) {
            try {
                result = CertTools.getCertsFromPEM(new ByteArrayInputStream(stringcert.getBytes()));
            } catch (CertificateException e) {
                LOG.error(e);
            } catch (IllegalStateException e) {
                LOG.error(e);
            }
        }
        return result;
    }

    /**
     * Method used to store a signers certificate in the config
     * @param signerCert
     * 
     */
    public void setSignerCertificateChain(Collection<Certificate> signerCertificateChain, String scope) {
        if (scope.equals(GlobalConfiguration.SCOPE_GLOBAL)) {
            try {
                String stringcert = new String(CertTools.getPEMFromCerts(signerCertificateChain));
                put(SIGNERCERTCHAIN, stringcert);
            } catch (CertificateException e) {
                LOG.error(e);
            }
        } else {
            try {
                String stringcert = new String(CertTools.getPEMFromCerts(signerCertificateChain));
                put(WorkerConfig.getNodeId() + "." + SIGNERCERTCHAIN, stringcert);
            } catch (CertificateException e) {
                LOG.error(e);
            }
        }
    }

    public WorkerConfig getWorkerConfig() {
        return workerConfig;
    }
    
    /**
     * Get the keystore data used by the KeystoreInConfigCryptoToken.
     * 
     * @return Keystore data in PKCS#12 format
     */
    public byte[] getKeystoreData() {
        final String keystoreDataString =
                (String) workerConfig.getData().get(KEYSTORE_DATA);
        
        if (keystoreDataString != null) {
            return Base64.decode(keystoreDataString);
        }
        
        return null;
    }
    
    /**
     * Set the keystore data used by the KeystoreInConfigCryptoToken.
     * 
     * @param keystoreData 
     */
    public void setKeystoreData(final byte[] keystoreData) {
        workerConfig.getData().put(KEYSTORE_DATA, new String(Base64.encode(keystoreData)));
    }
}
