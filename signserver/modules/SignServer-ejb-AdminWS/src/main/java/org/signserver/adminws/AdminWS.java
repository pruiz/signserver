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
package org.signserver.adminws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.annotation.PostConstruct;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;

import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.cesecore.audit.AuditLogEntry;
import org.cesecore.audit.audit.SecurityEventsAuditorSessionLocal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.util.query.Criteria;
import org.cesecore.util.query.Elem;
import org.cesecore.util.query.QueryCriteria;
import org.cesecore.util.query.clauses.Order;
import org.cesecore.util.query.elems.Term;
import org.signserver.common.*;
import org.signserver.common.data.CertificateValidationRequest;
import org.signserver.common.data.CertificateValidationResponse;
import org.signserver.common.data.DocumentValidationRequest;
import org.signserver.common.data.DocumentValidationResponse;
import org.signserver.common.data.LegacyRequest;
import org.signserver.common.data.LegacyResponse;
import org.signserver.common.data.Request;
import org.signserver.common.data.Response;
import org.signserver.common.data.SODRequest;
import org.signserver.common.data.SODResponse;
import org.signserver.common.data.SignatureRequest;
import org.signserver.common.data.SignatureResponse;
import org.signserver.ejb.interfaces.ProcessSessionLocal;
import org.signserver.server.CertificateClientCredential;
import org.signserver.server.IClientCredential;
import org.signserver.server.UsernamePasswordClientCredential;
import org.signserver.server.log.AdminInfo;
import org.signserver.ejb.interfaces.WorkerSessionLocal;
import org.signserver.ejb.interfaces.GlobalConfigurationSessionLocal;
import org.signserver.server.data.impl.CloseableReadableData;
import org.signserver.server.data.impl.CloseableWritableData;
import org.signserver.server.data.impl.DataFactory;
import org.signserver.server.data.impl.DataUtils;
import org.signserver.server.data.impl.UploadConfig;
import org.signserver.validationservice.common.ValidateRequest;
import org.signserver.validationservice.common.ValidateResponse;

/**
 * Class implementing the Admin WS interface.
 *
 * This class contains web service implementations for almost all EJB methods.
 * @author Markus Kilås
 * @version $Id$
 */
@WebService(serviceName = "AdminWSService")
@Stateless
public class AdminWS {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(AdminWS.class);

    private static final String HTTP_AUTH_BASIC_AUTHORIZATION = "Authorization";
    
    private static final HashSet<String> LONG_COLUMNS = new HashSet<>();
    private static final HashSet<String> INT_COLUMNS = new HashSet<>();
    
    static {
        LONG_COLUMNS.add(AuditLogEntry.FIELD_TIMESTAMP);
        LONG_COLUMNS.add(AuditLogEntry.FIELD_SEQUENCENUMBER);
        LONG_COLUMNS.add(ArchiveMetadata.TIME);
        INT_COLUMNS.add(ArchiveMetadata.SIGNER_ID);
        INT_COLUMNS.add(ArchiveMetadata.TYPE);
    }

    @Resource
    private WebServiceContext wsContext;

    @EJB
    private WorkerSessionLocal worker;
    
    @EJB
    private ProcessSessionLocal processSession;

    @EJB
    private GlobalConfigurationSessionLocal global;
    
    @EJB
    private SecurityEventsAuditorSessionLocal auditor;

    private DataFactory dataFactory;
        
    @PostConstruct
    protected void init() {
        dataFactory = DataUtils.createDataFactory();
    }
    
    private Set<ClientEntry> getWSClients(final String propertyName) {
        final String adminsProperty = global.getGlobalConfiguration().getProperty(
                GlobalConfiguration.SCOPE_GLOBAL, propertyName);
        
        if (adminsProperty == null) {
            LOG.warn(String.format("No %s global property set.", propertyName));
            return new HashSet<>();
        } else {
            return ClientEntry.clientEntriesFromProperty(adminsProperty);
        }
    }

    /**
     * Returns the Id of a worker given a name
     *
     * @param workerName of the worker, cannot be null
     * @return The Id of a named worker or 0 if no such name exists
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getWorkerId")
    public int getWorkerId(
            @WebParam(name = "workerName") final String workerName)
            throws AdminNotAuthorizedException {
        requireAdminAuthorization("getWorkerId", workerName);

        try {
            return worker.getWorkerId(workerName);
        } catch (InvalidWorkerIdException ex) {
            return 0;
        }
    }

    /**
     * Returns the current status of a processalbe.
     * Should be used with the cmd-line status command.
     * 
     * @param workerId of the signer
     * @return a WorkerStatus class
     * @throws InvalidWorkerIdException If the worker ID is invalid
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getStatus")
    public WSWorkerStatus getStatus(
            @WebParam(name = "workerId") final int workerId)
            throws InvalidWorkerIdException, AdminNotAuthorizedException {
        requireAdminAuthorization("getStatus", String.valueOf(workerId));

        final WSWorkerStatus result;
        final WorkerStatus status = worker.getStatus(new WorkerIdentifier(workerId));
        if (status == null) {
            result = null;
        } else {
            result = new WSWorkerStatus();
            result.setActiveConfig(status.getActiveSignerConfig()
                    .getProperties());
            result.setHostname(status.getHostname());
            result.setOk(status.getFatalErrors().isEmpty() ? null : "offline");
            result.setWorkerId(workerId);

            final ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
            status.displayStatus(new PrintStream(bout1), false);
            result.setStatusText(bout1.toString());

            final ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            status.displayStatus(new PrintStream(bout2), true);
            result.setCompleteStatusText(bout2.toString());
        }
        return result;
    }


    /**
     * Method used when a configuration have been updated. And should be
     * called from the commandline.
     *
     * @param workerId of the worker that should be reloaded, or 0 to reload
     * reload of all available workers
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "reloadConfiguration")
    public void reloadConfiguration(@WebParam(name = "workerId") int workerId)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("reloadConfiguration",
                String.valueOf(workerId));
        
        worker.reloadConfiguration(adminInfo, workerId);
    }

    /**
     * Method used to activate the signtoken of a signer.
     * Should be called from the command line.
     *
     * @param signerId of the signer
     * @param authenticationCode (PIN) used to activate the token.
     * @throws CryptoTokenOfflineException
     * @throws CryptoTokenAuthenticationFailureException
     * @throws InvalidWorkerIdException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "activateSigner")
    public void activateSigner(@WebParam(name = "signerId") int signerId,
            @WebParam(name = "authenticationCode") String authenticationCode)
            throws CryptoTokenAuthenticationFailureException,
            CryptoTokenOfflineException, InvalidWorkerIdException,
            AdminNotAuthorizedException {
        requireAdminAuthorization("activateSigner", String.valueOf(signerId));
        
        worker.activateSigner(new WorkerIdentifier(signerId), authenticationCode);
    }

    /**
     * Method used to deactivate the signtoken of a signer.
     * Should be called from the command line.
     *
     * @param signerId of the signer
     * @return true if deactivation was successful
     * @throws CryptoTokenOfflineException
     * @throws InvalidWorkerIdException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "deactivateSigner")
    public boolean deactivateSigner(@WebParam(name = "signerId") int signerId)
                throws CryptoTokenOfflineException,
            InvalidWorkerIdException, AdminNotAuthorizedException {
        requireAdminAuthorization("deactivateSigner", String.valueOf(signerId));
        
        return worker.deactivateSigner(new WorkerIdentifier(signerId));
    }

/////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the current configuration of a worker.
     *
     * Observe that this config might not be active until a reload command
     * has been excecuted.
     *
     * @param workerId
     * @return the current (not always active) configuration
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getCurrentWorkerConfig")
    public WSWorkerConfig getCurrentWorkerConfig(
            @WebParam(name = "workerId") final int workerId)
            throws AdminNotAuthorizedException {
        requireAdminAuthorization("getCurrentWorkerConfig",
                String.valueOf(workerId));
        
        return new WSWorkerConfig(worker.getCurrentWorkerConfig(workerId)
                .getProperties());
    }

    /**
     * Sets a parameter in a workers configuration.
     *
     * Observe that the worker isn't activated with this config until reload
     * is performed.
     *
     * @param workerId
     * @param key
     * @param value
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "setWorkerProperty")
    public void setWorkerProperty(
            @WebParam(name = "workerId") final int workerId,
            @WebParam(name = "key") final String key,
            @WebParam(name = "value") final String value)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("setWorkerProperty",
                String.valueOf(workerId), key);

        worker.setWorkerProperty(adminInfo, workerId, key, value);
    }

    /**
     * Removes a given worker's property.
     *
     * @param workerId
     * @param key
     * @return true if the property did exist and was removed othervise false
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "removeWorkerProperty")
    public boolean removeWorkerProperty(
            @WebParam(name = "workerId") final int workerId,
            @WebParam(name = "key") final String key)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("removeWorkerProperty",
                String.valueOf(workerId), key);
        
        return worker.removeWorkerProperty(adminInfo, workerId, key);
    }

    /**
     * Method that returns a collection of AuthorizedClient of
     * client certificate sn and issuerid accepted for a given signer.
     *
     * @param workerId
     * @return Sorted collection of authorized clients
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getAuthorizedClients")
    public Collection<AuthorizedClient> getAuthorizedClients(
            @WebParam(name = "workerId") final int workerId)
            throws AdminNotAuthorizedException {
        requireAdminAuthorization("getAuthorizedClients",
                String.valueOf(workerId));
        
        return worker.getAuthorizedClients(workerId);
    }

    /**
     * Method adding an authorized client to a signer.

     * @param workerId
     * @param authClient
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "addAuthorizedClient")
    public void addAuthorizedClient(@WebParam(name = "workerId") final int workerId,
            @WebParam(name = "authClient") final AuthorizedClient authClient)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("addAuthorizedClient", 
                String.valueOf(workerId), authClient.getCertSN(),
                authClient.getIssuerDN());
        
        worker.addAuthorizedClient(adminInfo, workerId, authClient);
    }

    /**
     * Removes an authorized client from a signer.
     *
     * @param workerId
     * @param authClient
     * @return True if the autorized client was removed
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "removeAuthorizedClient")
    public boolean removeAuthorizedClient(
            @WebParam(name = "workerId") final int workerId,
            @WebParam(name = "authClient") final AuthorizedClient authClient) 
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("removeAuthorizedClient",
                String.valueOf(workerId), authClient.getCertSN(),
                authClient.getIssuerDN());
        
        return worker.removeAuthorizedClient(adminInfo, workerId, authClient);
    }

    /**
     * Method used to let a signer generate a certificate request
     * using the signers own genCertificateRequest method.
     *
     * @param signerId id of the signer
     * @param certReqInfo information used by the signer to create the request
     * @param explicitEccParameters false should be default and will use
     * NamedCurve encoding of ECC public keys (IETF recommendation), use true
     * to include all parameters explicitly (ICAO ePassport requirement).
     * @return Base64-encoded certificate request
     * @throws CryptoTokenOfflineException 
     * @throws InvalidWorkerIdException 
     * @throws AdminNotAuthorizedException 
     */
    @WebMethod(operationName = "getPKCS10CertificateRequest")
    public Base64SignerCertReqData getPKCS10CertificateRequest(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "certReqInfo") final PKCS10CertReqInfo certReqInfo,
            @WebParam(name = "explicitEccParameters")
                final boolean explicitEccParameters)
            throws CryptoTokenOfflineException,
            InvalidWorkerIdException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("getPKCS10CertificateRequest",
                String.valueOf(signerId));
        
        final ICertReqData data = worker.getCertificateRequest(adminInfo, new WorkerIdentifier(signerId),
                certReqInfo, explicitEccParameters);
        if (!(data instanceof Base64SignerCertReqData)) {
            throw new RuntimeException("Unsupported cert req data");
        }
        return (Base64SignerCertReqData) data;
    }

    /**
     * Method used to let a signer generate a certificate request
     * using the signers own genCertificateRequest method.
     *
     * @param signerId id of the signer
     * @param certReqInfo information used by the signer to create the request
     * @param explicitEccParameters false should be default and will use
     * NamedCurve encoding of ECC public keys (IETF recommendation), use true
     * to include all parameters explicitly (ICAO ePassport requirement).
     * @param defaultKey true if the default key should be used otherwise for
     * instance use next key.
     * @return Base64-encoded certificate request
     * @throws CryptoTokenOfflineException 
     * @throws InvalidWorkerIdException 
     * @throws AdminNotAuthorizedException 
     */
    @WebMethod(operationName = "getPKCS10CertificateRequestForKey")
    public Base64SignerCertReqData getPKCS10CertificateRequestForKey(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "certReqInfo") final PKCS10CertReqInfo certReqInfo,
            @WebParam(name = "explicitEccParameters")
                final boolean explicitEccParameters,
            @WebParam(name = "defaultKey") final boolean defaultKey)
                throws CryptoTokenOfflineException, InvalidWorkerIdException,
                AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("getPKCS10CertificateRequestForKey",
                String.valueOf(signerId));
        
        final ICertReqData data = worker.getCertificateRequest(adminInfo, new WorkerIdentifier(signerId),
                certReqInfo, explicitEccParameters, defaultKey);
        if (!(data instanceof Base64SignerCertReqData)) {
            throw new RuntimeException("Unsupported cert req data");
        }
        return (Base64SignerCertReqData) data;
    }

    @WebMethod(operationName = "getPKCS10CertificateRequestForAlias")
    public Base64SignerCertReqData getPKCS10CertificateRequestForAlias(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "certReqInfo") final PKCS10CertReqInfo certReqInfo,
            @WebParam(name = "explicitEccParameters")
                final boolean explicitEccParameters,
            @WebParam(name = "keyAlias") final String keyAlias)
                throws CryptoTokenOfflineException, InvalidWorkerIdException,
                    AdminNotAuthorizedException {
        
        final AdminInfo adminInfo = requireAdminAuthorization("getPKCS10CertificateRequestForKey",
                String.valueOf(signerId));
        
        final ICertReqData data = worker.getCertificateRequest(adminInfo, new WorkerIdentifier(signerId),
                certReqInfo, explicitEccParameters, keyAlias);
        if (!(data instanceof Base64SignerCertReqData)) {
            throw new RuntimeException("Unsupported cert req data");
        }
        return (Base64SignerCertReqData) data;
    }
    
    /**
     * Method returning the current signing certificate for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate if the worker is a signer and it has
     * been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getSignerCertificate")
    public byte[] getSignerCertificate(
            @WebParam(name = "signerId") final int signerId)
            throws CryptoTokenOfflineException, AdminNotAuthorizedException {
        requireAdminAuthorization("getSignerCertificate",
                String.valueOf(signerId));
        
        return worker.getSignerCertificateBytes(new WorkerIdentifier(signerId));
    }

    /**
     * Method returning the current signing certificate chain for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate chain if the worker is a signer and it
     * has been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getSignerCertificateChain")
    public List<byte[]> getSignerCertificateChain(
            @WebParam(name = "signerId") final int signerId)
            throws CryptoTokenOfflineException, AdminNotAuthorizedException {
        requireAdminAuthorization("getSignerCertificateChain",
                String.valueOf(signerId));
        
        return worker.getSignerCertificateChainBytes(new WorkerIdentifier(signerId));
    }

    /**
     * Gets the last date the specified worker can do signings.
     * @param workerId Id of worker to check.
     * @return The last date or null if no last date (=unlimited).
     * @throws CryptoTokenOfflineException In case the cryptotoken is offline
     * for some reason.
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getSigningValidityNotAfter")
    public Date getSigningValidityNotAfter(
            @WebParam(name = "workerId") final int workerId)
            throws CryptoTokenOfflineException, AdminNotAuthorizedException {
        requireAdminAuthorization("getSigningValidityNotAfter",
                String.valueOf(workerId));
        
        return worker.getSigningValidityNotAfter(new WorkerIdentifier(workerId));
    }

    /**
     * Gets the first date the specified worker can do signings.
     * @param workerId Id of worker to check.
     * @return The first date or null if no last date (=unlimited).
     * @throws CryptoTokenOfflineException In case the cryptotoken is offline
     * for some reason.
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getSigningValidityNotBefore")
    public Date getSigningValidityNotBefore(
            @WebParam(name = "workerId") final int workerId)
            throws CryptoTokenOfflineException, AdminNotAuthorizedException {
        requireAdminAuthorization("getSigningValidityNotBefore", 
                String.valueOf(workerId));
        
        return worker.getSigningValidityNotBefore(new WorkerIdentifier(workerId));
    }

    /**
     * Returns the value of the KeyUsageCounter for the given workerId. If no
     * certificate is configured for the worker or the current key does not yet
     * have a counter in the database -1 is returned.
     * @param workerId
     * @return Value of the key usage counter or -1
     * @throws CryptoTokenOfflineException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getKeyUsageCounterValue")
    public long getKeyUsageCounterValue(
            @WebParam(name = "workerId") final int workerId)
            throws CryptoTokenOfflineException, AdminNotAuthorizedException {
        requireAdminAuthorization("getKeyUsageCounterValue",
                String.valueOf(workerId));

        return worker.getKeyUsageCounterValue(new WorkerIdentifier(workerId));
    }

    /**
     * Method used to remove a key from a signer.
     *
     * @param signerId id of the signer
     * @param purpose on of ICryptoTokenV4.PURPOSE_ constants
     * @return true if removal was successful.
     * @throws InvalidWorkerIdException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "destroyKey")
    public boolean destroyKey(@WebParam(name = "signerId") final int signerId,
            @WebParam(name = "purpose") final int purpose)
            throws InvalidWorkerIdException, AdminNotAuthorizedException {
        requireAdminAuthorization("destroyKey", String.valueOf(signerId));
        
        // destroyKey has been replaced with removeKey operation
        LOG.warn("Operation destroyKey no longer supported. Use removeKey instead.");
        return false;
    }

    /**
     * Generate a new keypair.
     * @param signerId Id of signer
     * @param keyAlgorithm Key algorithm
     * @param keySpec Key specification
     * @param alias Name of the new key
     * @param authCode Authorization code
     * @return Key alias of generated key
     * @throws CryptoTokenOfflineException
     * @throws InvalidWorkerIdException If the worker ID is invalid
     * @throws AdminNotAuthorizedException If the admin is not authorized
     * @throws IllegalArgumentException
     */
    @WebMethod(operationName = "generateSignerKey")
    public String generateSignerKey(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "keyAlgorithm") final String keyAlgorithm,
            @WebParam(name = "keySpec") final String keySpec,
            @WebParam(name = "alias") final String alias,
            @WebParam(name = "authCode") final String authCode)
            throws CryptoTokenOfflineException, InvalidWorkerIdException,
            AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("generateSignerKey", String.valueOf(signerId),
                keyAlgorithm, keySpec, alias);
        
        return worker.generateSignerKey(adminInfo, new WorkerIdentifier(signerId), keyAlgorithm, keySpec, alias,
                authCode == null ? null : authCode.toCharArray());
    }

    /**
     * Tests the key identified by alias or all keys if "all" specified.
     *
     * @param signerId Id of signer
     * @param alias Name of key to test or "all" to test all available
     * @param authCode Authorization code
     * @return Collection with test results for each key
     * @throws CryptoTokenOfflineException
     * @throws InvalidWorkerIdException
     * @throws KeyStoreException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "testKey")
    @SuppressWarnings("deprecation") // We support the old KeyTestResult class as well
    public Collection<KeyTestResult> testKey(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "alias") final String alias,
            @WebParam(name = "authCode") final String authCode)
            throws CryptoTokenOfflineException,
            InvalidWorkerIdException, KeyStoreException,
            AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("testKey", String.valueOf(signerId), alias);

        // Workaround for KeyTestResult first placed in wrong package
        final Collection<KeyTestResult> results;
        Collection<?> res = worker.testKey(adminInfo, new WorkerIdentifier(signerId), alias, authCode == null ? null : authCode.toCharArray());
        if (res.size() < 1) {
            results = new LinkedList<>();
        } else {
            results = new LinkedList<>();
            for (Object o : res) {
                if (o instanceof KeyTestResult) {
                    results.add((KeyTestResult) o);
                }
            }
        }

        return results;
    }
    
    /** 
     * Method used to remove a key from the crypto token used by the worker. 
     *
     * @param signerId id of worker
     * @param alias key alias of key to remove
     * @return true if removal was successful.
     * @throws CryptoTokenOfflineException if crypto token was not activated or 
     * could not be
     * @throws InvalidWorkerIdException if the specified worker id does not 
     * exist
     * @throws KeyStoreException for keystore related errors
     * @throws SignServerException for other errors
     * @throws AdminNotAuthorizedException if the administrator was not 
     * authorized to perform the operation
     */
    @WebMethod(operationName = "removeKey")
    public boolean removeKey(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "alias") final String alias)
            throws CryptoTokenOfflineException,
            InvalidWorkerIdException, KeyStoreException,
            SignServerException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("removeKey", String.valueOf(signerId), alias);

        return worker.removeKey(adminInfo, new WorkerIdentifier(signerId), alias);
    }

    /**
     * Method used to upload a certificate to a signers active configuration.
     *
     * @param signerId id of the signer
     * @param signerCert the certificate used to sign signature requests
     * @param scope one of GlobalConfiguration.SCOPE_ constants
     * @throws IllegalRequestException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "uploadSignerCertificate")
    public void uploadSignerCertificate(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "signerCert") final byte[] signerCert,
            @WebParam(name = "scope") final String scope)
            throws IllegalRequestException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("uploadSignerCertificate",
                String.valueOf(signerId));
        
        try {
            worker.uploadSignerCertificate(adminInfo, signerId, signerCert, scope);
        } catch (CertificateException ex) {
            // Log stacktrace and only pass on description to client
            LOG.error("Unable to parse certificate", ex);
            throw new IllegalRequestException("Unable to parse certificate");
        }
    }

    /**
     * Method used to upload a complete certificate chain to a configuration
     *
     * @param signerId id of the signer
     * @param signerCerts the certificate chain used to sign signature requests
     * @param scope one of GlobalConfiguration.SCOPE_ constants
     * @throws IllegalRequestException
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "uploadSignerCertificateChain")
    public void uploadSignerCertificateChain(
            @WebParam(name = "signerId") final int signerId,
            @WebParam(name = "signerCerts") final List<byte[]> signerCerts,
            @WebParam(name = "scope") final String scope)
                throws IllegalRequestException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("uploadSignerCertificateChain",
                String.valueOf(signerId));
        
        try {
            worker.uploadSignerCertificateChain(adminInfo, signerId, signerCerts, scope);
        } catch (CertificateException ex) {
            // Log stacktrace and only pass on description to client
            LOG.error("Unable to parse certificate", ex);
            throw new IllegalRequestException("Unable to parse certificate");
        }
    }

    /**
     * Method setting a global configuration property. For node. prefix will the
     * node id be appended.
     * @param scope one of the GlobalConfiguration.SCOPE_ constants
     * @param key of the property should not have any scope prefix, never null
     * @param value the value, never null.
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "setGlobalProperty")
    public void setGlobalProperty(
            @WebParam(name = "scope") final String scope,
            @WebParam(name = "key") final String key,
            @WebParam(name = "value") final String value)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("setGlobalProperty", key);
        
        global.setProperty(adminInfo, scope, key, value);
    }

    /**
     * Method used to remove a property from the global configuration.
     * @param scope one of the GlobalConfiguration.SCOPE_ constants
     * @param key of the property should start with either glob. or node.,
     * never null
     * @return true if removal was successful, othervise false.
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "removeGlobalProperty")
    public boolean removeGlobalProperty(
            @WebParam(name = "scope") final String scope,
            @WebParam(name = "key") final String key)
            throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("removeGlobalProperty", key);
        
        return global.removeProperty(adminInfo, scope, key);
    }

    /**
     * Method that returns all the global properties with Global Scope and Node
     * scopes properties for this node.
     * @return A GlobalConfiguration Object, never null
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "getGlobalConfiguration")
    public WSGlobalConfiguration getGlobalConfiguration()
            throws AdminNotAuthorizedException {
        requireAdminAuthorization("getGlobalConfiguration");
        
        final WSGlobalConfiguration result;
        final GlobalConfiguration config = global.getGlobalConfiguration();
        if (config == null) {
            result = null;
        } else {
            result = new WSGlobalConfiguration();
            final Properties props = new Properties();
            final Enumeration<String> en = config.getKeyEnumeration();
            while (en.hasMoreElements()) {
                final String key = en.nextElement();
                props.setProperty(key, config.getProperty(key));
            }
            result.setConfig(props);
            result.setState(config.getState());
            result.setAppVersion(config.getAppVersion());
            result.setClusterClassLoaderEnabled(false);
            result.setRequireSigning(false);
            result.setUseClassVersions(false);
        }
        return result;
    }

    /**
     * Help method that returns all worker, either signers or services defined
     * in the global configuration.
     * @param workerType can either be GlobalConfiguration.WORKERTYPE_ALL,
     * _SIGNERS or _SERVICES
     * @return A List if Integers of worker Ids, never null.
     * @throws AdminNotAuthorizedException
     */
    @WebMethod(operationName = "getWorkers")
    public List<Integer> getWorkers(
            @WebParam(name = "workerType") final int workerType)
                throws AdminNotAuthorizedException {
        requireAdminAuthorization("getWorkers", String.valueOf(workerType));

        if (workerType == WorkerConfig.WORKERTYPE_ALL) {
            return worker.getAllWorkers();
        } else {
            return worker.getWorkers(WorkerType.fromType(workerType));
        }
    }

    /**
     * Method that is used after a database crash to restore all cached data to
     * database.
     * @throws ResyncException if resync was unsuccessfull
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "globalResync")
    public void globalResync() throws ResyncException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("globalResync");
        
        global.resync(adminInfo);
    }

    /**
     * Method to reload all data from database.
     * 
     * @throws AdminNotAuthorizedException If the admin is not authorized
     */
    @WebMethod(operationName = "globalReload")
    public void globalReload() throws AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("globalReload");
        
        global.reload(adminInfo);
    }

    /**
     * Method for requesting a collection of requests to be processed by
     * the specified worker.
     *
     * @param workerIdOrName Name or ID of the worker who should process the
     * request
     * @param requests Collection of serialized (binary) requests.
     * @return Collection of response data
     * @throws InvalidWorkerIdException 
     * @throws IllegalRequestException 
     * @throws CryptoTokenOfflineException 
     * @throws SignServerException 
     * @throws AdminNotAuthorizedException If the admin is not authorized
     *
     * @see RequestAndResponseManager#serializeProcessRequest(org.signserver.common.ProcessRequest)
     * @see RequestAndResponseManager#parseProcessRequest(byte[])
     */
    @WebMethod(operationName = "process")
    public java.util.Collection<byte[]> process(
            @WebParam(name = "workerIdOrName") final String workerIdOrName,
            @WebParam(name = "processRequest") Collection<byte[]> requests)
            throws InvalidWorkerIdException, IllegalRequestException,
            CryptoTokenOfflineException, SignServerException,
            AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAdminAuthorization("process", workerIdOrName);

        final Collection<byte[]> result = new LinkedList<>();

        final X509Certificate[] clientCerts = getClientCertificates();
        final X509Certificate clientCertificate;
        if (clientCerts != null && clientCerts.length > 0) {
            clientCertificate = clientCerts[0];
        } else {
            clientCertificate = null;
        }
        // Requests from authenticated administrators are considered to come 
        // from the local host and is set to null. This is also the same as 
        // when requests are over EJB calls.
        final String ipAddress = null;

        final RequestContext requestContext = new RequestContext(
                clientCertificate, ipAddress);

        IClientCredential credential;
        if (clientCertificate instanceof X509Certificate) {
            final X509Certificate cert = (X509Certificate) clientCertificate;
            LOG.debug("Authentication: certificate");
            credential = new CertificateClientCredential(
                    cert.getSerialNumber().toString(16),
                    cert.getIssuerDN().getName());
        } else {
            final HttpServletRequest servletRequest =
                (HttpServletRequest) wsContext.getMessageContext()
                .get(MessageContext.SERVLET_REQUEST);
            // Check is client supplied basic-credentials
            final String authorization = servletRequest.getHeader(
                    HTTP_AUTH_BASIC_AUTHORIZATION);
            if (authorization != null) {
                LOG.debug("Authentication: password");

                final String decoded[] = new String(Base64.decode(
                        authorization.split("\\s")[1])).split(":", 2);

                credential = new UsernamePasswordClientCredential(
                        decoded[0], decoded[1]);
            } else {
                LOG.debug("Authentication: none");
                credential = null;
            }
        }
        requestContext.put(RequestContext.CLIENT_CREDENTIAL, credential);

        for (byte[] requestBytes : requests) {
            final ProcessRequest req;
            try {
                req = RequestAndResponseManager.parseProcessRequest(
                        requestBytes);
            } catch (IOException ex) {
                LOG.error("Error parsing process request", ex);
                throw new IllegalRequestException(
                        "Error parsing process request", ex);
            }
            
            // TODO: Duplicated in SignServerWS, AdminWS, ProcessSessionBean (remote)
            CloseableReadableData requestData = null;
            CloseableWritableData responseData = null;
            try {
                final Request req2;
                boolean propertiesRequest = false;
                
                // Use the new request types with large file support for
                // GenericSignRequest and GenericValidationRequest
                if (req instanceof GenericSignRequest) {
                    byte[] data = ((GenericSignRequest) req).getRequestData();
                    int requestID = ((GenericSignRequest) req).getRequestID();
                    
                    // Upload handling (Note: close in finally clause)
                    UploadConfig uploadConfig = UploadConfig.create(global);
                    requestData = dataFactory.createReadableData(data, uploadConfig.getMaxUploadSize(), uploadConfig.getRepository());
                    responseData = dataFactory.createWritableData(requestData, uploadConfig.getRepository());
                    req2 = new SignatureRequest(requestID, requestData, responseData);
                } else if (req instanceof GenericValidationRequest) {
                    byte[] data = ((GenericValidationRequest) req).getRequestData();
                    int requestID = ((GenericValidationRequest) req).getRequestID();
                    
                    // Upload handling (Note: close in finally clause)
                    UploadConfig uploadConfig = UploadConfig.create(global);
                    requestData = dataFactory.createReadableData(data, uploadConfig.getMaxUploadSize(), uploadConfig.getRepository());
                    req2 = new DocumentValidationRequest(requestID, requestData);
                } else if (req instanceof ValidateRequest) {
                    ValidateRequest vr = (ValidateRequest) req;
                    req2 = new CertificateValidationRequest(vr.getCertificate(), vr.getCertPurposesString());
                } else if (req instanceof SODSignRequest) {
                    SODSignRequest sodReq = (SODSignRequest) req;
                    req2 = new SODRequest(sodReq.getRequestID(), sodReq.getDataGroupHashes(), sodReq.getLdsVersion(), sodReq.getUnicodeVersion(), responseData);
                } else if (req instanceof GenericPropertiesRequest) {
                    GenericPropertiesRequest propReq = (GenericPropertiesRequest) req;
                    propertiesRequest = true;
                    
                    // Upload handling (Note: close in finally clause)
                    UploadConfig uploadConfig = UploadConfig.create(global);
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    propReq.getProperties().store(bout, null);
                    requestData = dataFactory.createReadableData(bout.toByteArray(), uploadConfig.getMaxUploadSize(), uploadConfig.getRepository());
                    responseData = dataFactory.createWritableData(requestData, uploadConfig.getRepository());
                    req2 = new SignatureRequest(propReq.hashCode(), requestData, responseData);
                } else {
                    // Passthrough for all legacy requests
                    req2 = new LegacyRequest(req);
                }

                Response resp = processSession.process(adminInfo, WorkerIdentifier.createFromIdOrName(workerIdOrName), req2, requestContext);
    
                ProcessResponse processResponse;
                if (resp instanceof SignatureResponse) {
                    SignatureResponse sigResp = (SignatureResponse) resp;
                    if (propertiesRequest) {
                        Properties properties = new Properties();
                        properties.load(responseData.toReadableData().getAsInputStream());
                        processResponse = new GenericPropertiesResponse(properties);
                    } else {
                        processResponse = new GenericSignResponse(sigResp.getRequestID(), responseData.toReadableData().getAsByteArray(), sigResp.getSignerCertificate(), sigResp.getArchiveId(), sigResp.getArchivables());
                    }
                } else if (resp instanceof DocumentValidationResponse) {
                    DocumentValidationResponse docResp = (DocumentValidationResponse) resp;
                    processResponse = new GenericValidationResponse(docResp.getRequestID(), docResp.isValid(), convert(docResp.getCertificateValidationResponse()), responseData.toReadableData().getAsByteArray());
                } else if (resp instanceof CertificateValidationResponse) {
                    CertificateValidationResponse certResp = (CertificateValidationResponse) resp;
                    processResponse = new ValidateResponse(certResp.getValidation(), certResp.getValidCertificatePurposes());
                } else if (resp instanceof SODResponse) {
                    SODResponse sodResp = (SODResponse) resp;
                    processResponse = new SODSignResponse(sodResp.getRequestID(), responseData.toReadableData().getAsByteArray(), sodResp.getSignerCertificate(), sodResp.getArchiveId(), sodResp.getArchivables());
                } else if (resp instanceof LegacyResponse) {
                    processResponse = ((LegacyResponse) resp).getLegacyResponse();
                } else {
                    throw new SignServerException("Unexpected response type: " + resp);
                }
                
                try {
                    result.add(RequestAndResponseManager.serializeProcessResponse(processResponse));
                } catch (IOException ex) {
                    LOG.error("Error serializing process response", ex);
                    throw new IllegalRequestException(
                            "Error serializing process response", ex);
                }                
            } catch (FileUploadBase.SizeLimitExceededException ex) {
                LOG.error("Maximum content length exceeded: " + ex.getLocalizedMessage());
                throw new IllegalRequestException("Maximum content length exceeded");
            } catch (FileUploadException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Upload failed", ex);
                }
                throw new IllegalRequestException("Upload failed: " + ex.getLocalizedMessage());
            } catch (IOException ex) {
                throw new SignServerException("IO error", ex);
            } finally {
                if (requestData != null) {
                    try {
                        requestData.close();
                    } catch (IOException ex) {
                        LOG.error("Unable to remove temporary upload file: " + ex.getLocalizedMessage());
                    }
                }
                if (responseData != null) {
                    try {
                        responseData.close();
                    } catch (IOException ex) {
                        LOG.error("Unable to remove temporary response file: " + ex.getLocalizedMessage());
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Method used to import a certificate chain to a crypto token.
     * 
     * @param workerId ID of (crypto)worker
     * @param certChain Certificate chain to import
     * @param alias Alias to import into in the crypto token
     * @param authCode Set if the alias is protected by an individual authentication
     *                 code. If null, uses the authentication code used when activating
     *                 the token
     * @throws CryptoTokenOfflineException
     * @throws CertificateException
     * @throws OperationUnsupportedException
     * @throws AdminNotAuthorizedException 
     */
    @WebMethod(operationName = "importCertificateChain")
    public void importCertificateChain(
            @WebParam(name="workerId") final int workerId,
            @WebParam(name="certificateChain") final List<byte[]> certChain,
            @WebParam(name="alias") final String alias,
            @WebParam(name="authenticationCode") final String authCode)
            throws CryptoTokenOfflineException, CertificateException,
                   OperationUnsupportedException, AdminNotAuthorizedException {
        final AdminInfo adminInfo =
                requireAdminAuthorization("importCertificateChain",
                                          String.valueOf(workerId), String.valueOf(alias));
        worker.importCertificateChain(adminInfo, new WorkerIdentifier(workerId), certChain, alias,
                                      authCode == null ? null : authCode.toCharArray());
    }
    
    
    /**
     * Query the audit log.
     *
     * @param startIndex Index where select will start. Set to 0 to start from the beginning.
     * @param max maximum number of results to be returned.
     * @param conditions List of conditions defining the subset of logs to be selected.
     * @param orderings List of ordering conditions for ordering the result.
     * @return List of log entries
     * @throws SignServerException In case of internal failures
     * @throws AdminNotAuthorizedException  In case the administrator was not authorized to perform the operation
     */
    @WebMethod(operationName="queryAuditLog")
    public List<WSAuditLogEntry> queryAuditLog(@WebParam(name="startIndex") int startIndex, @WebParam(name="max") int max, @WebParam(name="condition") final List<QueryCondition> conditions, @WebParam(name="ordering") final List<QueryOrdering> orderings) throws SignServerException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireAuditorAuthorization("queryAuditLog", String.valueOf(startIndex), String.valueOf(max));
        
        // For now we only query one of the available audit devices
        Set<String> devices = auditor.getQuerySupportingLogDevices();
        if (devices.isEmpty()) {
            throw new SignServerException("No log devices available for querying");
        }
        final String device = devices.iterator().next();

        final List<Elem> elements = toElements(conditions);
        final QueryCriteria qc = QueryCriteria.create();
        
        for (QueryOrdering order : orderings) {
            qc.add(new Order(order.getColumn(), Order.Value.valueOf(order.getOrder().name())));
        }
        
        if (!elements.isEmpty()) {
            qc.add(andAll(elements, 0));
        }
        
        try {
            return toLogEntries(worker.selectAuditLogs(adminInfo, startIndex, max, qc, device));
        } catch (AuthorizationDeniedException ex) {
            throw new AdminNotAuthorizedException(ex.getMessage());
        }
    }
    
    /**
     * Convert to WS model LogEntry:s.
     */
    private List<WSAuditLogEntry> toLogEntries(final List<? extends AuditLogEntry> entries) {
        final List<WSAuditLogEntry> results = new LinkedList<>();
        for (AuditLogEntry entry : entries) {
            results.add(WSAuditLogEntry.fromAuditLogEntry(entry));
        }
        return results;
    }

    /**
     * Query the archive.
     * 
     * @param startIndex Index where select will start. Set to 0 to start from the beginning.
     * @param max maximum number of results to be returned.
     * @param conditions List of conditions defining the subset of the archive to be presented.
     * @param orderings List of ordering conditions for ordering the result.
     * @param includeData Set to true if archive data should be included in the result set 
     * @return List of archive entries (the archive data is not included).
     * @throws SignServerException
     * @throws AdminNotAuthorizedException
     */
    @WebMethod(operationName="queryArchive")
    public List<WSArchiveMetadata> queryArchive(@WebParam(name="startIndex") int startIndex,
            @WebParam(name="max") int max, @WebParam(name="condition") final List<QueryCondition> conditions,
            @WebParam(name="ordering") final List<QueryOrdering> orderings,
            @WebParam(name="includeData") final boolean includeData)
                    throws SignServerException, AdminNotAuthorizedException {
        final AdminInfo adminInfo = requireArchiveAuditorAuthorization("queryArchive", String.valueOf(startIndex), String.valueOf(max));

        final List<Elem> elements = toElements(conditions);
        final QueryCriteria qc = QueryCriteria.create();
        
        for (QueryOrdering order : orderings) {
            qc.add(new Order(order.getColumn(), Order.Value.valueOf(order.getOrder().name())));
        }
        
        if (!elements.isEmpty()) {
            qc.add(andAll(elements, 0));
        }
        
        try {
            return toArchiveEntries(worker.searchArchive(adminInfo, startIndex,
                    max, qc, includeData));
        } catch (AuthorizationDeniedException ex) {
            throw new AdminNotAuthorizedException(ex.getMessage());
        }
    }
    
    @WebMethod(operationName="queryArchiveWithIds")
    public List<WSArchiveMetadata> queryArchiveWithIds(@WebParam(name="uniqueIds") List<String> uniqueIds,
            @WebParam(name="includeData") boolean includeData)
            throws SignServerException, AdminNotAuthorizedException {
        final AdminInfo adminInfo =
                requireArchiveAuditorAuthorization("queryArchiveWithIds");

        try {
            return toArchiveEntries(worker.searchArchiveWithIds(adminInfo, uniqueIds, includeData));
        } catch (AuthorizationDeniedException ex) {
            throw new AdminNotAuthorizedException(ex.getMessage());
        }
    }
    
    @WebMethod(operationName="queryTokenEntries")
    public WSTokenSearchResults queryTokenEntries(@WebParam(name="workerId") int workerId, @WebParam(name="startIndex") int startIndex, @WebParam(name="max") int max, @WebParam(name="condition") final List<QueryCondition> conditions, @WebParam(name="ordering") final List<QueryOrdering> orderings, @WebParam(name="includeData") boolean includeData) throws OperationUnsupportedException, CryptoTokenOfflineException, QueryException, InvalidWorkerIdException, AuthorizationDeniedException, SignServerException, AdminNotAuthorizedException {
        try {
            final AdminInfo adminInfo = requireAdminAuthorization("queryTokenEntries", String.valueOf(workerId), String.valueOf(startIndex), String.valueOf(max));
            final List<Elem> elements = toElements(conditions);
            final QueryCriteria qc = QueryCriteria.create();
            
            for (QueryOrdering order : orderings) {
                qc.add(new Order(order.getColumn(), Order.Value.valueOf(order.getOrder().name())));
            }
            
            if (!elements.isEmpty()) {
                qc.add(andAll(elements, 0));
            }
            
            return WSTokenSearchResults.fromTokenSearchResults(worker.searchTokenEntries(adminInfo, new WorkerIdentifier(workerId), startIndex, max, qc, includeData, Collections.<String, Object>emptyMap()));
        } catch (InvalidAlgorithmParameterException ex) {
            throw new SignServerException("Crypto token expects supported parameters", ex);
        } catch (UnsupportedCryptoTokenParameter ex) {
            throw new SignServerException("Crypto token expects parameters", ex);
        }
    }

    /**
     * Convert to WS model ArchiveEntry:s
     * 
     * @param entries
     * @return
     */
    private List<WSArchiveMetadata> toArchiveEntries(final List<? extends ArchiveMetadata> entries) {
        final List<WSArchiveMetadata> results = new LinkedList<>();
        
        for (final ArchiveMetadata entry : entries) {
            results.add(WSArchiveMetadata.fromArchiveMetadata(entry));
        }
        
        return results;
    }
    
    /**
     * Convert to the CESeCore model Elem:s.
     */
    private List<Elem> toElements(final List<QueryCondition> conditions) {
        final LinkedList<Elem> results = new LinkedList<>();
        
        if (conditions != null) {
            for (QueryCondition cond : conditions) {
                final Object value;
                if (LONG_COLUMNS.contains(cond.getColumn())) {
                    value = Long.parseLong(cond.getValue());
                } else if (INT_COLUMNS.contains(cond.getColumn())) {
                    value = Integer.parseInt(cond.getValue());
                } else {
                    value = cond.getValue();
                }
                results.add(new Term(cond.getOperator(), cond.getColumn(), value));
            }
        }

        return results;
    }
    
    /**
     * Tie together the list of Elem:s to a tree of AND operations.
     * This uses a recursive implementation not expected to work for larger 
     * lists of Elem:s, however as the number of columns are limited it is not 
     * expected to be a real problem.
     * 
     * @param elements
     * @param index Recursive index
     * @return Tree of and-criteria elements
     */
    protected Elem andAll(final List<Elem> elements, final int index) {
        if (index >= elements.size() - 1) {
            return elements.get(index);
        } else {
            return Criteria.and(elements.get(index), andAll(elements, index + 1));
        }
    }

    private AdminInfo requireAdminAuthorization(final String operation,
            final String... args) throws AdminNotAuthorizedException {
        LOG.debug(">requireAdminAuthorization");

        final X509Certificate[] certificates = getClientCertificates();
        if (certificates == null || certificates.length == 0) {
            throw new AdminNotAuthorizedException(
                    "Administrator not authorized to resource. "
                    + "Client certificate authentication required.");
        } else {
           final boolean authorized = isAdminAuthorized(certificates[0]);
           final X509Certificate cert = certificates[0];

           log(cert, authorized, operation, args);

           if (!authorized) {
               throw new AdminNotAuthorizedException(
                       "Administrator not authorized to resource.");
           }
           
           return new AdminInfo(cert.getSubjectDN().getName(),
                   cert.getIssuerDN().getName(), cert.getSerialNumber());
        }
    }
    
    private AdminInfo requireAuditorAuthorization(final String operation,
            final String... args) throws AdminNotAuthorizedException {
        LOG.debug(">requireAuditorAuthorization");

        final X509Certificate[] certificates = getClientCertificates();
        if (certificates == null || certificates.length == 0) {
            throw new AdminNotAuthorizedException(
                    "Auditor not authorized to resource. "
                    + "Client certificate authentication required.");
        } else {
           final boolean authorized = isAuditorAuthorized(certificates[0]);
           final X509Certificate cert = certificates[0];

           log(cert, authorized, operation, args);

           if (!authorized) {
               throw new AdminNotAuthorizedException(
                       "Auditor not authorized to resource.");
           }
           
           return new AdminInfo(cert.getSubjectDN().getName(),
                   cert.getIssuerDN().getName(), cert.getSerialNumber());
        }
    }
    
    private AdminInfo requireArchiveAuditorAuthorization(final String operation,
            final String... args) throws AdminNotAuthorizedException {
        LOG.debug(">requireArchiveAuditorAuthorization");

        final X509Certificate[] certificates = getClientCertificates();
        if (certificates == null || certificates.length == 0) {
            throw new AdminNotAuthorizedException(
                    "Archive auditor not authorized to resource. "
                    + "Client certificate authentication required.");
        } else {
           final boolean authorized = isArchiveAuditorAuthorized(certificates[0]);
           final X509Certificate cert = certificates[0];

           log(cert, authorized, operation, args);

           if (!authorized) {
               throw new AdminNotAuthorizedException(
                       "Archive auditor not authorized to resource.");
           }
           
           return new AdminInfo(cert.getSubjectDN().getName(),
                   cert.getIssuerDN().getName(), cert.getSerialNumber());
        }
    }

    private void log(final X509Certificate certificate, 
            final boolean authorized, final String operation,
            final String... args) {
        final StringBuilder line = new StringBuilder()
                .append("ADMIN OPERATION")
                .append("; ")
                
                .append("subjectDN=")
                .append(SignServerUtil.getTokenizedSubjectDNFromCert(certificate))
                .append("; ")
                
                .append("serialNumber=")
                .append(certificate.getSerialNumber().toString(16))
                .append("; ")
                
                .append("issuerDN=")
                .append(SignServerUtil.getTokenizedIssuerDNFromCert(certificate))
                .append("; ")
                
                .append("authorized=")
                .append(authorized)
                .append("; ")
                
                .append("operation=")
                .append(operation)
                .append("; ")
                
                .append("arguments=");
        for (String arg : args) {
            line.append(arg.replace(";", "\\;").replace("=", "\\="));
            line.append(",");
        }
        line.append(";");
        LOG.info(line.toString());
    }

    private boolean isAdminAuthorized(final X509Certificate cert) { 
        final String allowAnyWSAdminProp = global.getGlobalConfiguration().getProperty(
                GlobalConfiguration.SCOPE_GLOBAL, "ALLOWANYWSADMIN");
        final boolean allowAnyWSAdmin = allowAnyWSAdminProp != null ?
                Boolean.parseBoolean(allowAnyWSAdminProp) : false;
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("allow any admin: " + allowAnyWSAdmin);
        }

        if (allowAnyWSAdmin) {
            return true;
        } else {
            return hasAuthorization(cert, getWSClients("WSADMINS"));
        }
    }
    
    private boolean isAuditorAuthorized(final X509Certificate cert) { 
        return hasAuthorization(cert, getWSClients("WSAUDITORS"));
    }
    
    private boolean isArchiveAuditorAuthorized(final X509Certificate cert) {
        return hasAuthorization(cert, getWSClients("WSARCHIVEAUDITORS"));
    }
    
    private boolean hasAuthorization(final X509Certificate cert,
            final Set<ClientEntry> authSet) {
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking authorization for: SN: " +
                    cert.getSerialNumber().toString(16) +
                    " issuer: " + cert.getIssuerDN() + " agains admin set: " +
                    authSet);
        }

        return authSet.contains(new ClientEntry(cert.getSerialNumber(), SignServerUtil.getTokenizedIssuerDNFromCert(cert)));
    }

    private X509Certificate[] getClientCertificates() {
        final HttpServletRequest req =
                (HttpServletRequest) wsContext.getMessageContext()
                .get(MessageContext.SERVLET_REQUEST);
        final X509Certificate[] certificates =
                (X509Certificate[]) req.getAttribute(
                    "javax.servlet.request.X509Certificate");
        return certificates;
    }
    
    private ValidateResponse convert(CertificateValidationResponse from) {
        return new ValidateResponse(from.getValidation(), from.getValidCertificatePurposes());
    }

    // "Insert Code > Add Web Service Operation")

    
}
