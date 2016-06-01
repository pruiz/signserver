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
package org.signserver.protocol.ws.server;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import org.apache.log4j.Logger;
import org.signserver.common.*;
import org.signserver.ejb.interfaces.GlobalConfigurationSessionLocal;
import org.signserver.ejb.interfaces.ProcessSessionLocal;
import org.signserver.ejb.interfaces.WorkerSessionLocal;
import org.signserver.healthcheck.HealthCheckUtils;
import org.signserver.protocol.ws.*;
import org.signserver.server.CredentialUtils;
import org.signserver.server.log.AdminInfo;
import org.signserver.server.log.IWorkerLogger;
import org.signserver.server.log.LogMap;
import org.signserver.server.log.Loggable;
import org.signserver.server.nodb.FileBasedDatabaseManager;

/**
 * Implementor of the ISignServerWS interface.
 * 
 * @author Philip Vendil
 * @version $Id$
 */
@Stateless
@WebService(wsdlLocation = "META-INF/wsdl/SignServerWSService.wsdl",
targetNamespace = "gen.ws.protocol.signserver.org")
public class SignServerWS implements ISignServerWS {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(SignServerWS.class);
    
    @Resource
    private WebServiceContext wsContext;
    
    private static final String HTTP_AUTH_BASIC_AUTHORIZATION = "Authorization";
    
    @EJB
    private GlobalConfigurationSessionLocal globalconfigsession;
    
    @EJB
    private WorkerSessionLocal workersession;
    
    @EJB
    private ProcessSessionLocal processSession;
    
    /** EntityManager is conditionally injected from ejb-jar.xml. */
    private EntityManager em;
    
    private String checkDBString = "Select count(*) from signerconfigdata";

    private int minimumFreeMemory = 1;
    
    @Override
    public Collection<WorkerStatusWS> getStatus(String workerIdOrName)
            throws InvalidWorkerIdException {
        LOG.debug("WS getStatus called");
        ArrayList<WorkerStatusWS> retval = new ArrayList<>();

        final LinkedList<String> errors = new LinkedList<>();

        if (FileBasedDatabaseManager.getInstance().isUsed()) {
            errors.addAll(FileBasedDatabaseManager.getInstance().getFatalErrors());
        } else {
            errors.addAll(HealthCheckUtils.checkDB(em, getCheckDBString()));
        }
        if (errors.isEmpty()) {            
            errors.addAll(HealthCheckUtils.checkMemory(getMinimumFreeMemory()));
        }

        if (!workerIdOrName.equalsIgnoreCase(ISignServerWS.ALLWORKERS)) {
            // Specified WorkerId
            if (errors.isEmpty()) {
                errors.addAll(checkSigner(WorkerIdentifier.createFromIdOrName(workerIdOrName)));
            }
            WorkerStatusWS resp = new WorkerStatusWS();
            resp.setWorkerName(workerIdOrName);
            if (errors.isEmpty()) {
                resp.setOverallStatus(WorkerStatusWS.OVERALLSTATUS_ALLOK);
            } else {
                final StringBuilder buff = new StringBuilder();
                for (final String error : errors) {
                    buff.append(error).append("\n");
                }
                resp.setOverallStatus(WorkerStatusWS.OVERALLSTATUS_ERROR);
                resp.setErrormessage(buff.toString());
            }
            retval.add(resp);
        } else {
            // All Workers
            List<Integer> signers = getWorkerSession().getAllWorkers();
            for (int next : signers) {
                if (errors.isEmpty()) {
                    errors.addAll(checkSigner(new WorkerIdentifier(next)));
                }

                WorkerStatusWS resp = new WorkerStatusWS();
                resp.setWorkerName("" + next);
                if (errors.isEmpty()) {
                    resp.setOverallStatus(WorkerStatusWS.OVERALLSTATUS_ALLOK);
                } else {
                    final StringBuilder buff = new StringBuilder();
                    for (final String error : errors) {
                        buff.append(error).append("\n");
                    }
                    resp.setOverallStatus(WorkerStatusWS.OVERALLSTATUS_ERROR);
                    resp.setErrormessage(buff.toString());
                }
                retval.add(resp);
            }
        }
        return retval;
    }

    private List<String> checkSigner(WorkerIdentifier wi) throws InvalidWorkerIdException {
        final LinkedList<String> result = new LinkedList<>();
        final WorkerStatus status = getWorkerSession().getStatus(wi);
        for (String error : status.getFatalErrors()) {
            result.add("Worker " + status.getWorkerId() + ": " + error + "\n");
        }
        return result;
    }

    /**
     * @see  org.signserver.protocol.ws.ISignServerWS#process(String, Collection)
     */
    @Override
    public Collection<ProcessResponseWS> process(String workerIdOrName,
            Collection<ProcessRequestWS> requests)
            throws InvalidWorkerIdException, IllegalRequestException,
            CryptoTokenOfflineException, SignServerException {
        ArrayList<ProcessResponseWS> retval = new ArrayList<>();

        final HttpServletRequest servletRequest =
                (HttpServletRequest) wsContext.getMessageContext().get(MessageContext.SERVLET_REQUEST);
        String requestIP = getRequestIP();
        X509Certificate clientCertificate = getClientCertificate();
        final RequestContext requestContext = new RequestContext(clientCertificate, requestIP);

        // Add credentials to the context
        CredentialUtils.addToRequestContext(requestContext, servletRequest, clientCertificate);

        final LogMap logMap = LogMap.getInstance(requestContext);

        final String xForwardedFor = servletRequest.getHeader(RequestContext.X_FORWARDED_FOR);
        
        // Add HTTP specific log entries
        logMap.put(IWorkerLogger.LOG_REQUEST_FULLURL, new Loggable() {
            @Override
            public String logValue() {
                return servletRequest.getRequestURL().append("?")
                        .append(servletRequest.getQueryString()).toString();
            }
        });
                
        logMap.put(IWorkerLogger.LOG_REQUEST_LENGTH, new Loggable() {
            @Override
            public String logValue() {
                return servletRequest.getHeader("Content-Length");
            }
        });
                
        logMap.put(IWorkerLogger.LOG_XFORWARDEDFOR, new Loggable() {
            @Override
            public String logValue() {
                return xForwardedFor;
            }
        });
        
        if (xForwardedFor != null) {
            requestContext.put(RequestContext.X_FORWARDED_FOR, xForwardedFor);
        }
        
        final WorkerIdentifier wi = WorkerIdentifier.createFromIdOrName(workerIdOrName);

        ArrayList<Certificate> signerCertificateChain = getSignerCertificateChain(wi);

        for (ProcessRequestWS next : requests) {
            ProcessRequest req;
            try {
                req = RequestAndResponseManager.parseProcessRequest(next.getRequestData());
            } catch (IOException e1) {
                LOG.error("Error parsing process request", e1);
                throw new IllegalRequestException(e1.getMessage());
            }
            
            Map<String, String> metadata = next.getRequestMetadata();
            if (metadata == null) {
                requestContext.remove(RequestContext.REQUEST_METADATA);
            } else {
                requestContext.put(RequestContext.REQUEST_METADATA, metadata);
            }
            
            final String fileName = metadata.get(RequestContext.FILENAME);

            if (fileName != null) {
                requestContext.put(RequestContext.FILENAME, fileName);
                logMap.put(IWorkerLogger.LOG_FILENAME, new Loggable() {
                    @Override
                    public String logValue() {
                        return fileName;
                    }
                });
            }
            
            if (wi.hasName()) {
                logMap.put(IWorkerLogger.LOG_WORKER_NAME, new Loggable() {
                    @Override
                    public String logValue() {
                        return wi.getName();
                    }
                });
            }
            if (wi.hasId()) {
                logMap.put(IWorkerLogger.LOG_WORKER_ID, new Loggable() {
                    @Override
                    public String logValue() {
                        return String.valueOf(wi.getId());
                    }
                });
            }

            ProcessResponse resp = getProcessSession().process(new AdminInfo("Client user", null, null),
                    wi, req, requestContext);
            ProcessResponseWS wsresp = new ProcessResponseWS();
            try {
                wsresp.setResponseData(RequestAndResponseManager.serializeProcessResponse(resp));
            } catch (IOException e1) {
                LOG.error("Error parsing process response", e1);
                throw new SignServerException(e1.getMessage());
            }
            if (resp instanceof ISignResponse) {
                wsresp.setRequestID(((ISignResponse) resp).getRequestID());
                try {
                    wsresp.setWorkerCertificate(new Certificate(((ISignResponse) resp).getSignerCertificate()));
                    wsresp.setWorkerCertificateChain(signerCertificateChain);
                } catch (CertificateEncodingException e) {
                    LOG.error(e);
                }

            }
            retval.add(wsresp);
        }
        return retval;
    }

    private ArrayList<Certificate> getSignerCertificateChain(WorkerIdentifier wi) throws InvalidWorkerIdException {
        ArrayList<Certificate> retval = null;
        try {
            WorkerStatus ws = getWorkerSession().getStatus(wi);
            ProcessableConfig sc = new ProcessableConfig(ws.getActiveSignerConfig());
            Collection<java.security.cert.Certificate> signerCertificateChain = sc.getSignerCertificateChain();

            if (signerCertificateChain != null) {
                retval = new ArrayList<>();
                for (Iterator<java.security.cert.Certificate> iterator = signerCertificateChain.iterator(); iterator.hasNext();) {
                    retval.add(new Certificate(iterator.next()));
                }
            }
        } catch (CertificateEncodingException e) {
            LOG.error(e);
        }
        return null;
    }

    private X509Certificate getClientCertificate() {
        MessageContext msgContext = wsContext.getMessageContext();
        HttpServletRequest request = (HttpServletRequest) msgContext.get(MessageContext.SERVLET_REQUEST);
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");

        if (certificates != null) {
            return certificates[0];
        }
        return null;
    }

    private String getRequestIP() {
        MessageContext msgContext = wsContext.getMessageContext();
        HttpServletRequest request = (HttpServletRequest) msgContext.get(MessageContext.SERVLET_REQUEST);

        return request.getRemoteAddr();
    }

    private int getMinimumFreeMemory() {
        final String minMemory = CompileTimeSettings.getInstance().getProperty(
                CompileTimeSettings.HEALTHECK_MINIMUMFREEMEMORY);
        if (minMemory != null) {
            try {
                minimumFreeMemory = Integer.parseInt(minMemory.trim());
            } catch (NumberFormatException e) {
                LOG.error("Error: SignServerWS badly configured, setting healthcheck.minimumfreememory should only contain integers");
            }
        }
        return minimumFreeMemory;
    }

    private String getCheckDBString() {
        final String dbString = CompileTimeSettings.getInstance().getProperty(
                CompileTimeSettings.HEALTHECK_CHECKDBSTRING);
        if (dbString != null) {
            checkDBString = dbString;
        }
        return checkDBString;
    }

    private WorkerSessionLocal getWorkerSession() {
        return workersession;
    }
    
    private ProcessSessionLocal getProcessSession() {
        return processSession;
    }

    private GlobalConfigurationSessionLocal getGlobalConfigurationSession() {
        return globalconfigsession;
    }
}
