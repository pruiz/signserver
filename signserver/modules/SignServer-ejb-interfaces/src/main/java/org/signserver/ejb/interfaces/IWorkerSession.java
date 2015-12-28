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
package org.signserver.ejb.interfaces;

import org.signserver.common.WorkerIdentifier;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.ejb.Local;
import javax.ejb.Remote;
import org.cesecore.audit.AuditLogEntry;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.util.query.QueryCriteria;
import org.signserver.common.ArchiveDataVO;
import org.signserver.common.ArchiveMetadata;
import org.signserver.common.AuthorizedClient;
import org.signserver.common.CryptoTokenAuthenticationFailureException;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.ICertReqData;
import org.signserver.common.ISignerCertReqInfo;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.InvalidWorkerIdException;
import org.signserver.common.KeyTestResult;
import org.signserver.common.OperationUnsupportedException;
import org.signserver.common.ProcessRequest;
import org.signserver.common.ProcessResponse;
import org.signserver.common.QueryException;
import org.signserver.common.RequestContext;
import org.signserver.common.SignServerException;
import org.signserver.common.UnsupportedCryptoTokenParameter;
import org.signserver.common.WorkerConfig;
import org.signserver.common.WorkerStatus;
import org.signserver.server.cryptotokens.TokenSearchResults;
import org.signserver.server.log.AdminInfo;

/**
 * Interface for the worker session bean.
 *
 * @version $Id$
 */
public interface IWorkerSession {

    /**
     * Returns the current status of a processalbe.
     *
     * Should be used with the cmd-line status command.
     * @param wi of the signer
     * @return a WorkerStatus class
     */
    WorkerStatus getStatus(WorkerIdentifier wi) throws InvalidWorkerIdException;

    /**
     * Returns the Id of a worker given a name
     *
     * @param workerName of the worker, cannot be null
     * @return The Id of a named worker or 0 if no such name exists
     */
    int getWorkerId(String workerName) throws InvalidWorkerIdException;

    /**
     * Method used when a configuration have been updated. And should be
     * called from the commandline.
     *
     * @param workerId of the worker that should be reloaded, or 0 to reload
     * reload of all available workers
     */
    void reloadConfiguration(int workerId);

    /**
     * Method used to activate the signtoken of a signer.
     * Should be called from the command line.
     *
     * @param signerId of the signer
     * @param authenticationCode (PIN) used to activate the token.
     * @throws CryptoTokenOfflineException
     * @throws CryptoTokenAuthenticationFailureException
     */
    void activateSigner(WorkerIdentifier signerId, String authenticationCode)
            throws CryptoTokenAuthenticationFailureException,
            CryptoTokenOfflineException, InvalidWorkerIdException;

    /**
     * Method used to deactivate the signtoken of a signer.
     * Should be called from the command line.
     *
     * @param signerId of the signer
     * @return true if deactivation was successful
     * @throws CryptoTokenOfflineException
     * @throws CryptoTokenAuthenticationFailureException
     */
    boolean deactivateSigner(WorkerIdentifier signerId) throws CryptoTokenOfflineException,
            InvalidWorkerIdException;

    /**
     * Returns the current configuration of a worker. Only the worker properties
     * are included in the WorkerConfig instance returned.
     * Prior to version 3.7.0 the returned WorkerConfig instance also contained
     * authorized clients and the signer certificate and chain.
     * Use the dedicated methods to retrieve this data.
     *
     * Observe that this config might not be active until a reload command
     * has been excecuted.
     *
     * @param signerId
     * @return the current (not always active) configuration
     */
    WorkerConfig getCurrentWorkerConfig(int signerId);

    /**
     * Sets a parameter in a workers configuration.
     *
     * Observe that the worker isn't activated with this config until reload
     * is performed.
     *
     * @param workerId
     * @param key
     * @param value
     */
    void setWorkerProperty(int workerId, String key, String value);

    /**
     * Removes a given worker's property.
     *
     * @param workerId
     * @param key
     * @return true if the property did exist and was removed othervise false
     */
    boolean removeWorkerProperty(int workerId, String key);

    /**
     * Method that returns a collection of AuthorizedClient of
     * client certificate sn and issuerid accepted for a given signer.
     *
     * @param signerId
     * @return Sorted collection of authorized clients
     */
    Collection<AuthorizedClient> getAuthorizedClients(int signerId);

    /**
     * Method adding an authorized client to a signer.

     * @param signerId
     * @param authClient
     */
    void addAuthorizedClient(int signerId, AuthorizedClient authClient);

    /**
     * Removes an authorized client from a signer.
     *
     * @param signerId
     * @param authClient
     */
    boolean removeAuthorizedClient(int signerId, AuthorizedClient authClient);

    /**
     * Method used to let a signer generate a certificate request
     * using the signers own genCertificateRequest method.
     *
     * @param signerId id of the signer
     * @param certReqInfo information used by the signer to create the request
     * @param explicitEccParameters false should be default and will use
     * NamedCurve encoding of ECC public keys (IETF recommendation), use true
     * to include all parameters explicitly (ICAO ePassport requirement).
     */
    ICertReqData getCertificateRequest(WorkerIdentifier signerId,
            ISignerCertReqInfo certReqInfo, boolean explicitEccParameters)
            throws CryptoTokenOfflineException, InvalidWorkerIdException;

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
     */
    ICertReqData getCertificateRequest(WorkerIdentifier signerId,
            ISignerCertReqInfo certReqInfo, boolean explicitEccParameters, 
            boolean defaultKey) throws CryptoTokenOfflineException,
            InvalidWorkerIdException;
    
    /**
     * Method used to let a signer generate a certificate request
     * using the signers own genCertificateRequest method given a key alias.
     * 
     * @param signerId ID of the signer
     * @param certReqInfo information used by the signer to create the request
     * @param explicitEccParameters false should be default and will use
     * NamedCurve encoding of ECC public keys (IETF recommendation), use true
     * to include all parameters explicitly (ICAO ePassport requirement).
     * @param keyAlias key alias to use in the crypto token.
     * @return Certificate request data
     * @throws CryptoTokenOfflineException
     * @throws InvalidWorkerIdException 
     */
    ICertReqData getCertificateRequest(WorkerIdentifier signerId,
            ISignerCertReqInfo certReqInfo, boolean explicitEccParameters,
            String keyAlias)
            throws CryptoTokenOfflineException, InvalidWorkerIdException;
            

    /**
     * Method returning the current signing certificate for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate if the worker is a signer and it has
     * been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     */
    Certificate getSignerCertificate(WorkerIdentifier signerId)
            throws CryptoTokenOfflineException;
    
    /**
     * Method returning the current signing certificate for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate if the worker is a signer and it has
     * been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     */
    byte[] getSignerCertificateBytes(WorkerIdentifier signerId)
            throws CryptoTokenOfflineException;

    /**
     * Method returning the current signing certificate chain for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate chain if the worker is a signer and it
     * has been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     */
    public List<Certificate> getSignerCertificateChain(WorkerIdentifier signerId)
            throws CryptoTokenOfflineException;
    
    /**
     * Method returning the signing certificate chain for the signer given
     * a key alias.
     * 
     * @param signerId
     * @param alias
     * @return The certificate chain, or null if there is no chain for the
     *         given alias
     * @throws CryptoTokenOfflineException 
     * @throws InvalidWorkerIdException
     */
    public List<Certificate> getSignerCertificateChain(WorkerIdentifier signerId,
                                                       String alias)
            throws CryptoTokenOfflineException, InvalidWorkerIdException;
    
    /**
     * Method returning the current signing certificate chain for the signer.
     * @param signerId Id of signer
     * @return Current signing certificate chain if the worker is a signer and it
     * has been configured. Otherwise null or an exception is thrown.
     * @throws CryptoTokenOfflineException In case the crypto token or the worker
     * is not active
     */
    public List<byte[]> getSignerCertificateChainBytes(WorkerIdentifier signerId)
            throws CryptoTokenOfflineException;

    /**
     * Gets the last date the specified worker can do signings.
     * @param workerId Id of worker to check.
     * @return The last date or null if no last date (=unlimited).
     * @throws CryptoTokenOfflineException In case the cryptotoken is offline
     * for some reason.
     */
    Date getSigningValidityNotAfter(WorkerIdentifier workerId)
            throws CryptoTokenOfflineException;

    /**
     * Gets the first date the specified worker can do signings.
     * @param workerId Id of worker to check.
     * @return The first date or null if no last date (=unlimited).
     * @throws CryptoTokenOfflineException In case the cryptotoken is offline
     * for some reason.
     */
    Date getSigningValidityNotBefore(WorkerIdentifier workerId)
            throws CryptoTokenOfflineException;

    /**
     * Returns the value of the KeyUsageCounter for the given workerId. If no
     * certificate is configured for the worker or the current key does not yet
     * have a counter in the database -1 is returned.
     * @param workerId
     * @return Value of the key usage counter or -1
     * @throws CryptoTokenOfflineException
     */
    long getKeyUsageCounterValue(final WorkerIdentifier workerId) 
            throws CryptoTokenOfflineException;

    /**
     * Attempt to remove the specified key with the key alias.
     *
     * @param signerId of worker
     * @param alias of key to remove
     * @return true if the key was removed or false if the removal failed or 
     * the worker or crypto token does not support key removal
     * @throws CryptoTokenOfflineException in case the token was not activated
     * @throws InvalidWorkerIdException in case the worker could not be fined
     * @throws KeyStoreException for keystore related errors
     * @throws SignServerException in case the key alias could not be found etc
     */
    boolean removeKey(WorkerIdentifier signerId, String alias) 
            throws CryptoTokenOfflineException, InvalidWorkerIdException, 
            KeyStoreException, SignServerException;
    
    /**
     * Generate a new keypair.
     * @param signerId Id of signer
     * @param keyAlgorithm Key algorithm
     * @param keySpec Key specification
     * @param alias Name of the new key
     * @param authCode Authorization code
     * @throws CryptoTokenOfflineException
     * @throws IllegalArgumentException
     */
    String generateSignerKey(WorkerIdentifier signerId, String keyAlgorithm,
            String keySpec, String alias, char[] authCode)
            throws CryptoTokenOfflineException, InvalidWorkerIdException;

    /**
     * Tests the key identified by alias or all keys if "all" specified.
     *
     * @param signerId Id of signer
     * @param alias Name of key to test or "all" to test all available
     * @param authCode Authorization code
     * @return Collection with test results for each key
     * @throws CryptoTokenOfflineException
     * @throws KeyStoreException
     */
    Collection<KeyTestResult> testKey(final WorkerIdentifier signerId, final String alias,
            char[] authCode) throws CryptoTokenOfflineException,
            InvalidWorkerIdException, KeyStoreException;
    
    /**
     * Method used to upload a certificate to a signers active configuration.
     *
     * @param signerId id of the signer
     * @param signerCert the certificate used to sign signature requests
     * @param scope one of GlobalConfiguration.SCOPE_ constants
     */
    void uploadSignerCertificate(int signerId, byte[] signerCert,
            String scope) throws CertificateException;

    /**
     * Method used to upload a complete certificate chain to a configuration
     *
     * @param signerId id of the signer
     * @param signerCerts the certificate chain used to sign signature requests
     * @param scope one of GlobalConfiguration.SCOPE_ constants
     */
    void uploadSignerCertificateChain(int signerId,
            Collection<byte[]> signerCerts, String scope)
             throws CertificateException;

    /**
     * Method used to import a complete certificate chain to a crypto token.
     * 
     * @param signerId ID of the signer
     * @param signerCerts the certificate chain to upload
     * @param alias key alias to use in the token
     * @param authenticationCode authentication code used for the key entry,
     *                          or use the authentication code used when activating
     *                          the token if null
     * @throws CryptoTokenOfflineException
     * @throws CertificateException
     * @throws OperationUnsupportedException 
     */
    void importCertificateChain(WorkerIdentifier signerId, List<byte[]> signerCerts,
                                String alias,char[] authenticationCode)
            throws CryptoTokenOfflineException, CertificateException,
                   OperationUnsupportedException;
    
    /**
     * Methods that generates a free worker id that can be used for new signers.
     */
    int genFreeWorkerId();

    /**
     * Find all archivables related to an ArchiveId from the given signer. Both REQUEST, RESPONSE and 
     * possibly other Archivable types are returned.
     * @param signerId id of the signer
     * @param archiveId the Id of te archive data
     * @return List of all ArchiveDataVO related to one archiveId
     */
    List<ArchiveDataVO> findArchiveDataFromArchiveId(int signerId, String archiveId);
    
    /**
     * Find all archivables related to an requestIP from the given signer. Both REQUEST, RESPONSE and 
     * possibly other Archivable types are returned.
     * @param signerId id of the signer
     * @param requestIP the IP of the client
     * @return List of all ArchiveDataVO
     */
    List<ArchiveDataVO> findArchiveDatasFromRequestIP(int signerId,
            String requestIP);
    
    /** 
     * Find all archivables related to an request certificate from the given signer. Both REQUEST, RESPONSE and 
     * possibly other Archivable types are returned.
     * @param signerId id of the signer
     * @param serialNumber the serialnumber of the certificate
     * making the request
     * @param issuerDN the issuer of the client certificate
     * @return List of all ArchiveDataVO
     */
    List<ArchiveDataVO> findArchiveDatasFromRequestCertificate(int signerId,
            BigInteger serialNumber, String issuerDN);
    
    /**
     * Query contents of archive.
     * Returns meta data entries of archive entries matching query criteria.
     * 
     * @param startIndex Start index of first result (0-based)
     * @param max Maximum number of results returned, 0 means all matching results
     * @param criteria Search criteria for matching results
     * @param includeData If true, include actual archive data in entries
     * @return List of metadata objects describing matching entries
     */
    List<ArchiveMetadata> searchArchive(int startIndex,
            int max, QueryCriteria criteria, boolean includeData)
            throws AuthorizationDeniedException; 
    
    /**
     * Query contents of archive based on list of uniqueIds (primary key in DB).
     * 
     * @param uniqueIds List of IDs to fetch meta data for
     * @param includeData If true, include actual archive data in entries
     * @return List of archive data objects
     * @throws AuthorizationDeniedException
     */
    List<ArchiveMetadata> searchArchiveWithIds(List<String> uniqueIds,
            boolean includeData)
            throws AuthorizationDeniedException;
    
    /**
     * Help method that returns all worker, either signers or services defined
     * in the global configuration.
     * @param workerType can either be GlobalConfiguration.WORKERTYPE_ALL,
     * _SIGNERS or _SERVICES
     * @return A List if Integers of worker Ids, never null.
     */
    List<Integer> getWorkers(int workerType);

    @Remote
    interface IRemote extends IWorkerSession {

        String JNDI_NAME = "signserver/WorkerSessionBean/remote";
        
        List<? extends AuditLogEntry> selectAuditLogs(int startIndex, int max, QueryCriteria criteria, String logDeviceId) throws AuthorizationDeniedException;
        
        /**
         * Queries the specified worker's crypto token.
         *
         * @param workerId Id of worker to query
         * @param startIndex Start index of first result (0-based)
         * @param max Maximum number of results to return
         * @param qc Search criteria for matching results
         * @param includeData If 'false' only the alias and key type is included, otherwise all information available is returned
         * @return the search result
         * @throws OperationUnsupportedException in case the search operation is not supported by the worker
         * @throws CryptoTokenOfflineException in case the token is not in a searchable state
         * @throws QueryException in case the query could not be understood or could not be executed
         * @throws InvalidWorkerIdException in case the worker ID is not existing
         * @throws AuthorizationDeniedException in case the operation was not allowed
         * @throws SignServerException in case of any other problem
         */
        TokenSearchResults searchTokenEntries(WorkerIdentifier workerId, final int startIndex, final int max, final QueryCriteria qc, final boolean includeData, final Map<String, Object> params) throws 
                InvalidWorkerIdException,
                AuthorizationDeniedException,
                CryptoTokenOfflineException,
                QueryException,
                InvalidAlgorithmParameterException,
                UnsupportedCryptoTokenParameter,
                OperationUnsupportedException;
    }

    /**
     * Local EJB interface.
     * This interface has mirror methods for all methods of the parent interface
     * related to audit logging, taking an additional AdminInfo instance.
     */
    @Local
    interface ILocal extends IWorkerSession { 
        String JNDI_NAME = "signserver/WorkerSessionBean/local";
        
        /**
         * Select a set of events to be audited.
         * 
         * @param token identifier of the entity performing the task.
         * @param startIndex Index where select will start. Set to 0 to start from the beginning.
         * @param max maximum number of results to be returned. Set to 0 to use no limit.
         * @param criteria Criteria defining the subset of logs to be selected.
         * @param logDeviceId identifier of the AuditLogDevice
         * 
         * @return The audit logs to the given criteria
         * @throws AuthorizationDeniedException 
         */
        List<? extends AuditLogEntry> selectAuditLogs(AdminInfo adminInfo, int startIndex, int max, QueryCriteria criteria, String logDeviceId) throws AuthorizationDeniedException;
        
        /**
         * Method used to remove a key from a crypto token used by a worker.
         *
         * @param adminInfo administrator info
         * @param signerId id of the worker
         * @param alias key alias of key to remove
         * @return true if removal was successful.
         * @throws CryptoTokenOfflineException if crypto token was not activated or could not be
         * @throws InvalidWorkerIdException if the specified worker id does not exist
         * @throws KeyStoreException for keystore related errors
         * @throws SignServerException for other errors
         */
        boolean removeKey(AdminInfo adminInfo, WorkerIdentifier signerId, String alias) 
            throws CryptoTokenOfflineException, InvalidWorkerIdException, 
            KeyStoreException, SignServerException;
        
        /**
         * Generate a new keypair.
         * 
         * @param adminInfo Administrator info
         * @param signerId Id of signer
         * @param keyAlgorithm Key algorithm
         * @param keySpec Key specification
         * @param alias Name of the new key
         * @param authCode Authorization code
         * @throws CryptoTokenOfflineException
         * @throws IllegalArgumentException
         */
        String generateSignerKey(final AdminInfo adminInfo, WorkerIdentifier signerId, String keyAlgorithm,
                String keySpec, String alias, char[] authCode)
                        throws CryptoTokenOfflineException, InvalidWorkerIdException;
        
        /**
         * Tests the key identified by alias or all keys if "all" specified.
         *
         * @param adminInfo Administrator info
         * @param signerId Id of signer
         * @param alias Name of key to test or "all" to test all available
         * @param authCode Authorization code
         * @return Collection with test results for each key
         * @throws CryptoTokenOfflineException
         * @throws KeyStoreException
         */
        Collection<KeyTestResult> testKey(final AdminInfo adminInfo, final WorkerIdentifier signerId, String alias,
                char[] authCode)
                        throws CryptoTokenOfflineException, InvalidWorkerIdException,
                        KeyStoreException;
    
        /**
         * Sets a parameter in a workers configuration.
         *
         * Observe that the worker isn't activated with this config until reload
         * is performed.
         *
         * @param adminInfo
         * @param workerId
         * @param key
         * @param value
         */
        void setWorkerProperty(final AdminInfo adminInfo, int workerId, String key, String value);
        
        /**
         * Removes a given worker's property.
         *
         * @param adminInfo
         * @param workerId
         * @param key
         * @return true if the property did exist and was removed othervise false
         */
        boolean removeWorkerProperty(final AdminInfo adminInfo, int workerId, String key);
            
        /**
         * Method adding an authorized client to a signer.
         * 
         * @param adminInfo
         * @param signerId
         * @param authClient
         */
        void addAuthorizedClient(final AdminInfo adminInfo, int signerId, AuthorizedClient authClient);
            
        /**
         * Method removing an authorized client to a signer.
         * 
         * @param adminInfo
         * @param signerId
         * @param authClient
         * @return true if the client was authorized to the signer
         */
        boolean removeAuthorizedClient(final AdminInfo adminInfo, int signerId,
                    AuthorizedClient authClient);
            
        /**
         * Method used to let a signer generate a certificate request
         * using the signers own genCertificateRequest method.
         *
         * @param adminInfo Administrator info
         * @param signerId id of the signer
         * @param certReqInfo information used by the signer to create the request
         * @param explicitEccParameters false should be default and will use
         * NamedCurve encoding of ECC public keys (IETF recommendation), use true
         * to include all parameters explicitly (ICAO ePassport requirement).
         */
        ICertReqData getCertificateRequest(final AdminInfo adminInfo, WorkerIdentifier signerId,
                ISignerCertReqInfo certReqInfo,
                final boolean explicitEccParameters,
                final boolean defaultKey) throws
                CryptoTokenOfflineException, InvalidWorkerIdException;
        
        /**
         * Method that gets the signing certificate chain given a key alias.
         * 
         * @param adminInfo
         * @param signerId
         * @param alias
         * @return Certificate chain, or null if no such alias exists in the token
         * @throws CryptoTokenOfflineException
         * @throws InvalidWorkerIdException
         */
        List<Certificate> getSigningCertificateChain(AdminInfo adminInfo,
                                                     WorkerIdentifier signerId,
                                                     String alias)
                throws CryptoTokenOfflineException, InvalidWorkerIdException;
            
        /**
         * Method used to let a signer generate a certificate request
         * using the signers own genCertificateRequest method.
         *
         * @param adminInfo Administrator info
         * @param signerId id of the signer
         * @param certReqInfo information used by the signer to create the request
         * @param explicitEccParameters false should be default and will use
         * NamedCurve encoding of ECC public keys (IETF recommendation), use true
         * to include all parameters explicitly (ICAO ePassport requirement).
         * @param defaultKey true if the default key should be used otherwise for
         * instance use next key.
         */
        ICertReqData getCertificateRequest(final AdminInfo adminInfo, final WorkerIdentifier signerId,
                final ISignerCertReqInfo certReqInfo,
                final boolean explicitEccParameters) throws
                CryptoTokenOfflineException, InvalidWorkerIdException;
        
        /**
         * Method used to let a signer generate a certificate request
         * using the signers own genCertificateRequest method. Using the specified
         * key alias from the crypto token.
         *
         * @param adminInfo Administrator info
         * @param signerId id of the signer
         * @param certReqInfo information used by the signer to create the request
         * @param explicitEccParameters false should be default and will use
         * NamedCurve encoding of ECC public keys (IETF recommendation), use true
         * to include all parameters explicitly (ICAO ePassport requirement).
         * @param keyAlias key alias to use from the crypto token
         * @return certificate request data
         * @throws CryptoTokenOfflineException
         * @throws InvalidWorkerIdException
         */
        ICertReqData getCertificateRequest(final AdminInfo adminInfo, final WorkerIdentifier signerId,
                final ISignerCertReqInfo certReqInfo,
                final boolean explicitEccParameters, final String keyAlias)
                throws CryptoTokenOfflineException, InvalidWorkerIdException;
        
        /**
         * Get keystore data, used by the KeystoreInConfigCryptoToken.
         * 
         * @param adminInfo Administrator info
         * @param signerId ID of the signer
         * @return Keystore data
         */
        byte[] getKeystoreData(final AdminInfo adminInfo, final int signerId);
        
        /**
         * Set keystore data, used by the KeystoreInConfigCryptoToken
         * @param adminInfo Administator info
         * @param signerId ID of the signer
         * @param keystoreData Keystore data to set
         */
        void setKeystoreData(final AdminInfo adminInfo, final int signerId,
                final byte[] keystoreData);
            
        /**
         * Method used to upload a certificate to a signers active configuration.
         *
         * @param adminInfo Administrator info
         * @param signerId id of the signer
         * @param signerCert the certificate used to sign signature requests
         * @param scope one of GlobalConfiguration.SCOPE_ constants
         */
        void uploadSignerCertificate(final AdminInfo adminInfo, int signerId, byte[] signerCert,
                String scope) throws CertificateException;
            
        /**
         * Method used to upload a complete certificate chain to a configuration
         *
         * @param adminInfo Administrator info
         * @param signerId id of the signer
         * @param signerCerts the certificate chain used to sign signature requests
         * @param scope one of GlobalConfiguration.SCOPE_ constants
         */
        void uploadSignerCertificateChain(final AdminInfo adminInfo, int signerId,
                Collection<byte[]> signerCerts, String scope) throws CertificateException;

        
        /**
         * Method used to import a complete certificate chain to a crypto token.
         * 
         * @param adminInfo
         * @param signerId ID of the signer
         * @param signerCerts the certificate chain to upload
         * @param alias key alias to use in the token
         * @param authenticationCode authentication code for the key entry,
         *                           or null to use the token authentication code
         * @throws CryptoTokenOfflineException
         * @throws CertificateException
         * @throws OperationUnsupportedException 
         */
        void importCertificateChain(AdminInfo adminInfo, WorkerIdentifier signerId,
                List<byte[]> signerCerts, String alias, char[] authenticationCode)
                throws CryptoTokenOfflineException, CertificateException,
                       OperationUnsupportedException;
        
        /**
         * Method used when a configuration have been updated. And should be
         * called from the commandline.
         *
         * @param adminInfo Administrator information
         * @param workerId of the worker that should be reloaded, or 0 to reload
         * reload of all available workers
         */
        void reloadConfiguration(final AdminInfo adminInfo, int workerId);

        /**
         * Query contents of archive.
         * Returns meta data entries of archive entries matching query criteria.
         * 
         * @param adminInfo Administrator information
         * @param startIndex Start index of first result (0-based)
         * @param max Maximum number of results returned, 0 means all matching results
         * @param criteria Search criteria for matching results
         * @param includeData If true, archive data is included in the meta data entries
         * @return List of metadata objects describing matching entries
         */
        List<ArchiveMetadata> searchArchive(AdminInfo adminInfo, 
                int startIndex, int max, QueryCriteria criteria,
                boolean includeData)
            throws AuthorizationDeniedException;
        
        /**
         * Query contents of archive based on list of unique IDs
         * (primary key in DB).
         * 
         * @param adminInfo Administrator information
         * @param uniqueIds List of unique IDs to fetch entries for
         * @param includeData If true, archive data is included in the meta data entries
         * @return List of archive data objects
         * @throws AuthorizationDeniedException 
         */
        List<ArchiveMetadata> searchArchiveWithIds(final AdminInfo adminInfo, 
                final List<String> uniqueIds, 
                final boolean includeData)
                throws AuthorizationDeniedException;
        
        /**
         * Queries the specified worker's crypto token.
         *
         * @param adminInfo Administrator information
         * @param workerId Id of worker to query
         * @param startIndex Start index of first result (0-based)
         * @param max Maximum number of results to return
         * @param qc Search criteria for matching results
         * @param includeData If 'false' only the alias and key type is included, otherwise all information available is returned
         * @return the search result
         * @throws OperationUnsupportedException in case the search operation is not supported by the worker
         * @throws CryptoTokenOfflineException in case the token is not in a searchable state
         * @throws QueryException in case the query could not be understood or could not be executed
         * @throws InvalidWorkerIdException in case the worker ID is not existing
         * @throws AuthorizationDeniedException in case the operation was not allowed
         * @throws SignServerException in case of any other problem
         */
        TokenSearchResults searchTokenEntries(final AdminInfo adminInfo, WorkerIdentifier workerId, final int startIndex, final int max, final QueryCriteria qc, final boolean includeData, final Map<String, Object> params) throws
            InvalidWorkerIdException,
            AuthorizationDeniedException,
            CryptoTokenOfflineException,
            QueryException,
            InvalidAlgorithmParameterException,
            UnsupportedCryptoTokenParameter,
            OperationUnsupportedException;
    }
}
