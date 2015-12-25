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
package org.signserver.ejb;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import org.apache.log4j.Logger;
import org.cesecore.audit.log.SecurityEventsLoggerSessionLocal;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.InvalidWorkerIdException;
import org.signserver.common.NoSuchWorkerException;
import org.signserver.common.ProcessRequest;
import org.signserver.common.ProcessResponse;
import org.signserver.common.RequestContext;
import org.signserver.common.ServiceLocator;
import org.signserver.common.SignServerException;
import org.signserver.common.WorkerIdentifier;
import org.signserver.ejb.interfaces.IDispatcherWorkerSession;
import org.signserver.ejb.interfaces.IGlobalConfigurationSession;
import org.signserver.ejb.interfaces.IInternalWorkerSession;
import org.signserver.ejb.interfaces.IWorkerSession;
import org.signserver.ejb.worker.impl.WorkerManagerSingletonBean;
import org.signserver.server.entities.FileBasedKeyUsageCounterDataService;
import org.signserver.server.entities.IKeyUsageCounterDataService;
import org.signserver.server.entities.KeyUsageCounterDataService;
import org.signserver.server.log.AdminInfo;
import org.signserver.server.nodb.FileBasedDatabaseManager;
import org.signserver.statusrepo.IStatusRepositorySession;

/**
 * Session bean implementing the process and getWorkerId methods in the same way
 * as the WorkerSessionBean. This bean is intended to be used from dispatchers 
 * and not directly through any of the client interfaces.
 *
 * @author Markus Kilås
 * @version $Id$
 */
@Stateless
public class DispatcherWorkerSessionBean implements IDispatcherWorkerSession.ILocal, IDispatcherWorkerSession.IRemote {

    /** Log4j instance for this class. */
    private static final Logger LOG = Logger.getLogger(DispatcherWorkerSessionBean.class);

    private IKeyUsageCounterDataService keyUsageCounterDataService;

    @EJB
    private IGlobalConfigurationSession.ILocal globalConfigurationSession;

    @EJB
    private WorkerManagerSingletonBean workerManagerSession;

    @EJB
    private SecurityEventsLoggerSessionLocal logSession;

    /** Injected by ejb-jar.xml. */
    EntityManager em;

    @Resource
    private SessionContext ctx;

    private WorkerProcessImpl processImpl;
    private final AllServicesImpl servicesImpl = new AllServicesImpl();

    @PostConstruct
    public void create() {
        if (em == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No EntityManager injected. Running without database.");
            }
            keyUsageCounterDataService = new FileBasedKeyUsageCounterDataService(FileBasedDatabaseManager.getInstance());
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("EntityManager injected. Running with database.");
            }
            keyUsageCounterDataService = new KeyUsageCounterDataService(em);
        }
        processImpl = new WorkerProcessImpl(em, keyUsageCounterDataService, workerManagerSession, logSession);
        
        // XXX The lookups will fail on GlassFish V2
        // When we no longer support GFv2 we can refactor this code
        IInternalWorkerSession.ILocal internalSession = null;
        IWorkerSession.ILocal workerSession = null;
        IStatusRepositorySession.ILocal statusSession = null;
        try {
            internalSession = ServiceLocator.getInstance().lookupLocal(IInternalWorkerSession.ILocal.class);
            workerSession = ServiceLocator.getInstance().lookupLocal(IWorkerSession.ILocal.class);
            statusSession = ServiceLocator.getInstance().lookupLocal(IStatusRepositorySession.ILocal.class);
        } catch (NamingException ex) {
            LOG.error("Lookup services failed. This is expected on GlassFish V2: " + ex.getExplanation());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Lookup services failed", ex);
            }
        }
        // Add all services
        servicesImpl.putAll(
                em,
                workerSession,
                globalConfigurationSession,
                logSession, 
                internalSession, ctx.getBusinessObject(IDispatcherWorkerSession.ILocal.class), statusSession);
    }

    @Override
    public ProcessResponse process(final WorkerIdentifier wi,
            final ProcessRequest request, final RequestContext requestContext)
            throws IllegalRequestException, CryptoTokenOfflineException,
            SignServerException {
        requestContext.setServices(servicesImpl);
        return processImpl.process(wi, request, requestContext);
    }

    @Override
    public ProcessResponse process(final AdminInfo adminInfo, final WorkerIdentifier wi,
            final ProcessRequest request, final RequestContext requestContext)
            throws IllegalRequestException, CryptoTokenOfflineException,
            SignServerException {
        requestContext.setServices(servicesImpl);
        return processImpl.process(adminInfo, wi, request, requestContext);
    }

//    @Override
//    public int getWorkerId(String workerName) throws InvalidWorkerIdException {
//        try {
//            return processImpl.getWorkerId(workerName);
//        } catch (NoSuchWorkerException ex) {
//            throw new InvalidWorkerIdException(ex.getMessage());
//        }
//    }

}
