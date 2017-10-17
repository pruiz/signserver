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
package org.signserver.client.cli.defaultimpl;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import javax.xml.ws.soap.SOAPFaultException;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.signserver.cli.spi.AbstractCommand;
import org.signserver.cli.spi.CommandFailureException;
import org.signserver.cli.spi.IllegalCommandArgumentsException;
import org.signserver.client.cli.spi.FileSpecificHandlerFactory;
import org.signserver.common.AccessDeniedException;
import org.signserver.common.AuthorizationRequiredException;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.SignServerException;
import org.signserver.protocol.ws.client.SignServerWSClientFactory;

/**
 * Command Line Interface (CLI) for signing documents.
 *
 * @author Markus Kilås
 * @version $Id$
 */
public class SignDocumentCommand extends AbstractCommand implements ConsolePasswordProvider {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(SignDocumentCommand.class);

    /** ResourceBundle with internationalized StringS. */
    private static final ResourceBundle TEXTS = ResourceBundle.getBundle(
            "org/signserver/client/cli/defaultimpl/ResourceBundle");

    private static final String DEFAULT_CLIENTWS_WSDL_URL = "/signserver/ClientWSService/ClientWS?wsdl";
    
    /** System-specific new line characters. **/
    private static final String NL = System.getProperty("line.separator");

    /** The name of this command. */
    private static final String COMMAND = "signdocument";

    /** Option WORKERID. */
    public static final String WORKERID = "workerid";

    /** Option WORKERNAME. */
    public static final String WORKERNAME = "workername";

    /** Option DATA. */
    public static final String DATA = "data";

    /** Option HOST. */
    public static final String HOST = "host";

    /** Option INFILE. */
    public static final String INFILE = "infile";

    /** Option OUTFILE. */
    public static final String OUTFILE = "outfile";

    /** Option INDIR. */
    public static final String INDIR = "indir";

    /** Option OUTDIR. */
    public static final String OUTDIR = "outdir";
    
    /** Option THREADS. */
    public static final String THREADS = "threads";
    
    /** Option REMOVEFROMINDIR. */
    public static final String REMOVEFROMINDIR = "removefromindir";
    
    /** Option ONEFIRST. */
    public static final String ONEFIRST = "onefirst";
    
    /** Option STARTALL. */
    public static final String STARTALL = "startall";

    /** Option PORT. */
    public static final String PORT = "port";

    public static final String SERVLET = "servlet";

    /** Option PROTOCOL. */
    public static final String PROTOCOL = "protocol";

    /** Option USERNAME. */
    public static final String USERNAME = "username";

    /** Option PASSWORD. */
    public static final String PASSWORD = "password";

    /** Option PDFPASSWORD. */
    public static final String PDFPASSWORD = "pdfpassword";

    /** Option METADATA. */
    public static final String METADATA = "metadata";

    /** The command line options. */
    private static final Options OPTIONS;

    private static final int DEFAULT_THREADS = 1;

    /**
     * Protocols that can be used for accessing SignServer.
     */
    public static enum Protocol {
        /** The SignServerWS interface. */
        WEBSERVICES,
        
        /** The ClientWS interface. */
        CLIENTWS,

        /** The HTTP interface. */
        HTTP
    }

    static {
        OPTIONS = new Options();
        OPTIONS.addOption(WORKERID, true,
                TEXTS.getString("WORKERID_DESCRIPTION"));
        OPTIONS.addOption(WORKERNAME, true,
                TEXTS.getString("WORKERNAME_DESCRIPTION"));
        OPTIONS.addOption(DATA, true,
                TEXTS.getString("DATA_DESCRIPTION"));
        OPTIONS.addOption(INFILE, true,
                TEXTS.getString("INFILE_DESCRIPTION"));
        OPTIONS.addOption(OUTFILE, true,
                TEXTS.getString("OUTFILE_DESCRIPTION"));
        OPTIONS.addOption(HOST, true,
                TEXTS.getString("HOST_DESCRIPTION"));
        OPTIONS.addOption(PORT, true,
                TEXTS.getString("PORT_DESCRIPTION"));
        OPTIONS.addOption(SERVLET, true,
                TEXTS.getString("SERVLET_DESCRIPTION"));
        OPTIONS.addOption(PROTOCOL, true,
                TEXTS.getString("PROTOCOL_DESCRIPTION"));
        OPTIONS.addOption(USERNAME, true,
                TEXTS.getString("USERNAME_DESCRIPTION"));
        OPTIONS.addOption(PASSWORD, true,
                TEXTS.getString("PASSWORD_DESCRIPTION"));
        OPTIONS.addOption(PDFPASSWORD, true,
                TEXTS.getString("PDFPASSWORD_DESCRIPTION"));
        OPTIONS.addOption(METADATA, true,
                TEXTS.getString("METADATA_DESCRIPTION"));
        OPTIONS.addOption(INDIR, true,
                TEXTS.getString("INDIR_DESCRIPTION"));
        OPTIONS.addOption(OUTDIR, true,
                TEXTS.getString("OUTDIR_DESCRIPTION"));
        OPTIONS.addOption(THREADS, true,
                TEXTS.getString("THREADS_DESCRIPTION"));
        OPTIONS.addOption(REMOVEFROMINDIR, false,
                TEXTS.getString("REMOVEFROMINDIR_DESCRIPTION"));
        OPTIONS.addOption(ONEFIRST, false,
                TEXTS.getString("ONEFIRST_DESCRIPTION"));
        OPTIONS.addOption(STARTALL, false,
                TEXTS.getString("STARTALL_DESCRIPTION"));
        for (Option option : KeyStoreOptions.getKeyStoreOptions()) {
            OPTIONS.addOption(option);
        }
    }

    /** ID of worker who should perform the operation. */
    private int workerId;

    /** Name of worker who should perform the operation. */
    private String workerName;

    /** Data to sign. */
    private String data;

    /** Hostname or IP address of the SignServer host. */
    private String host;

    /** TCP port number of the SignServer host. */
    private Integer port;

    private String servlet = "/signserver/process";

    /** File to read the data from. */
    private File inFile;

    /** File to read the signed data to. */
    private File outFile;

    /** Directory to read files from. */
    private File inDir;
    
    /** Directory to write files to. */
    private File outDir;
    
    /** Number of threads to use when running in batch mode. */
    private Integer threads;
    
    /** If the successfully processed files should be removed from indir. */
    private boolean removeFromIndir;
    
    /** If one request should be set first before starting the remaining threads. */
    private boolean oneFirst;
    
    /** If all should be started directly (ie not oneFirst). */
    private boolean startAll;

    /** Protocol to use for contacting SignServer. */
    private Protocol protocol = Protocol.HTTP;

    private String username;
    private String password;
    private boolean promptForPassword;

    private String pdfPassword;

    private final KeyStoreOptions keyStoreOptions = new KeyStoreOptions();

    /** Meta data parameters passed in */
    private Map<String, String> metadata;
    
    private FileSpecificHandlerFactory handlerFactory;
    
    @Override
    public String getDescription() {
        return "Request a document to be signed by SignServer";
    }

    @Override
    public String getUsages() {
        StringBuilder footer = new StringBuilder();
        footer.append(NL)
            .append("Sample usages:").append(NL)
            .append("a) ").append(COMMAND).append(" -workername XMLSigner -data \"<root/>\"").append(NL)
            .append("b) ").append(COMMAND).append(" -workername XMLSigner -infile /tmp/document.xml").append(NL)
            .append("c) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -truststore truststore.jks -truststorepwd changeit").append(NL)
            .append("d) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -keystore superadmin.jks -keystorepwd foo123").append(NL)
            .append("e) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -metadata param1=value1 -metadata param2=value2").append(NL)
            .append("f) ").append(COMMAND).append(" -workerid 3 -indir ./input/ -removefromindir -outdir ./output/ -threads 5").append(NL);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final HelpFormatter formatter = new HelpFormatter();
        
        try (PrintWriter pw = new PrintWriter(bout)) {
            formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH, "signdocument <-workername WORKERNAME | -workerid WORKERID> [options]",  getDescription(), OPTIONS, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, footer.toString());
        }
        
        return bout.toString();
    }

    /**
     * Reads all the options from the command line.
     *
     * @param line The command line to read from
     */
    private void parseCommandLine(final CommandLine line)
        throws IllegalCommandArgumentsException, CommandFailureException {
        if (line.hasOption(WORKERNAME)) {
            workerName = line.getOptionValue(WORKERNAME, null);
        }
        if (line.hasOption(WORKERID)) {
            workerId = Integer.parseInt(line.getOptionValue(WORKERID, null));
        }
        host = line.getOptionValue(HOST, KeyStoreOptions.DEFAULT_HOST);
        if (line.hasOption(PORT)) {
            port = Integer.parseInt(line.getOptionValue(PORT));
        }
        if (line.hasOption(SERVLET)) {
            servlet = line.getOptionValue(SERVLET, null);
        }
        if (line.hasOption(DATA)) {
            data = line.getOptionValue(DATA, null);
        }
        if (line.hasOption(INFILE)) {
            inFile = new File(line.getOptionValue(INFILE, null));
        }
        if (line.hasOption(OUTFILE)) {
            outFile = new File(line.getOptionValue(OUTFILE, null));
        }
        if (line.hasOption(INDIR)) {
            inDir = new File(line.getOptionValue(INDIR, null));
        }
        if (line.hasOption(OUTDIR)) {
            outDir = new File(line.getOptionValue(OUTDIR, null));
        }
        if (line.hasOption(THREADS)) {
            threads = Integer.parseInt(line.getOptionValue(THREADS, null));
        }
        if (line.hasOption(REMOVEFROMINDIR)) {
            removeFromIndir = true;
        }
        if (line.hasOption(ONEFIRST)) {
            oneFirst = true;
        }
        if (line.hasOption(STARTALL)) {
            startAll = true;
        }
        if (line.hasOption(PROTOCOL)) {
            protocol = Protocol.valueOf(line.getOptionValue(
                    PROTOCOL, null));
            
            // if the protocol is WS and -servlet is not set, override the servlet URL
            // with the default one for the WS servlet
            if (Protocol.WEBSERVICES.equals(protocol) &&
            	!line.hasOption(SERVLET)) {
            	servlet = SignServerWSClientFactory.DEFAULT_WSDL_URL;
            }
            if ((Protocol.CLIENTWS.equals(protocol)) &&
            	!line.hasOption(SERVLET)) {
            	servlet = DEFAULT_CLIENTWS_WSDL_URL;
            }
        }
        if (line.hasOption(USERNAME)) {
            username = line.getOptionValue(USERNAME, null);
        }
        if (line.hasOption(PASSWORD)) {
            password = line.getOptionValue(PASSWORD, null);
        }
        if (line.hasOption(PDFPASSWORD)) {
            pdfPassword = line.getOptionValue(PDFPASSWORD, null);
        }
        
        if (line.hasOption(METADATA)) {
            metadata = MetadataParser.parseMetadata(line.getOptionValues(METADATA));
        }
        
        try {
            final ConsolePasswordReader passwordReader = createConsolePasswordReader();
            keyStoreOptions.parseCommandLine(line, passwordReader, out);

            // Prompt for user password if not given
            if (username != null && password == null) {
                promptForPassword = true;
                out.print("Password for user '" + username + "': ");
                out.flush();
                password = new String(passwordReader.readPassword());
            }
        } catch (IOException ex) {
            throw new IllegalCommandArgumentsException("Failed to read password: " + ex.getLocalizedMessage());
        } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException ex) {
            throw new IllegalCommandArgumentsException("Failure setting up keystores: " + ex.getMessage());
        }
    }
    
    /**
     * @return a ConsolePasswordReader that can be used to read passwords
     */
    @Override
    public ConsolePasswordReader createConsolePasswordReader() {
        return new DefaultConsolePasswordReader();
    }

    /**
     * Checks that all mandatory options are given.
     */
    private void validateOptions() throws IllegalCommandArgumentsException {
        if (workerName == null && workerId == 0) {
            throw new IllegalCommandArgumentsException(
                    "Missing -workername or -workerid");
        } else if (data == null && inFile == null && inDir == null && outDir == null) {
            throw new IllegalCommandArgumentsException("Missing -data, -infile or -indir");
        }
        
        if (inDir != null && outDir == null) {
            throw new IllegalCommandArgumentsException("Missing -outdir");
        }
        if (data != null && inFile != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -data and -infile");
        }
        if (data != null && inDir != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -data and -indir");
        }
        if (inFile != null && inDir != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -infile and -indir");
        }

        if (inDir != null && inDir.equals(outDir)) {
            throw new IllegalCommandArgumentsException("Can not specify the same directory as -indir and -outdir");
        }
        
        if (inDir == null & threads != null) {
            throw new IllegalCommandArgumentsException("Can not specify -threads unless -indir");
        }

        if (threads != null && threads < 1) {
            throw new IllegalCommandArgumentsException("Number of threads must be > 0");
        }
        
        if (startAll && oneFirst) {
            throw new IllegalCommandArgumentsException("Can not specify both -onefirst and -startall");
        }
        
        if ((startAll || oneFirst) && (inDir == null)) {
            throw new IllegalCommandArgumentsException("The options -onefirst and -startall only supported in batch mode. Specify -indir.");
        }
        
        // Default to use oneFirst if username is specified and not startall
        if(!startAll && username != null) {
            oneFirst = true;
        }

        keyStoreOptions.validateOptions();
    }

    /**
     * Creates a DocumentSigner using the choosen protocol.
     *
     * @return a DocumentSigner using the choosen protocol
     * @throws MalformedURLException in case an URL can not be constructed
     * using the given host and port
     */
    private DocumentSigner createSigner(final FileSpecificHandler handler,
                                        final String currentPassword)
            throws MalformedURLException {
        final DocumentSigner signer;

        keyStoreOptions.setupHTTPS(); // TODO: Should be done earlier and only once (not for each signer)

        if (port == null) {
            if (keyStoreOptions.isUsePrivateHTTPS()) {
                port = KeyStoreOptions.DEFAULT_PRIVATE_HTTPS_PORT;
            } else if (keyStoreOptions.isUseHTTPS()) {
                port = KeyStoreOptions.DEFAULT_PUBLIC_HTTPS_PORT;
            } else {
                port = KeyStoreOptions.DEFAULT_HTTP_PORT;
            }
        }
        
        if (handler.isSignatureInputHash()) {
            metadata.put("USING_CLIENTSUPPLIED_HASH", "true");
        }
        // TODO: include digest algorithm when client-side
        //metadata.put("CLIENTSIDE_HASHDIGESTALGORITHM", digestAlgorithm);
        
        final String typeId = handler.getFileTypeIdentifier();

        if (typeId != null) {
            metadata.put("FILE_TYPE", typeId);
        }

        switch (protocol) {
            case WEBSERVICES: {
                LOG.debug("Using SignServerWS as procotol");
            
                final String workerIdOrName;
                if (workerId == 0) {
                    workerIdOrName = workerName;
                } else {
                    workerIdOrName = String.valueOf(workerId);
                }

                signer = new WebServicesDocumentSigner(
                    host,
                    port,
                    servlet,
                    workerIdOrName,
                    keyStoreOptions.isUseHTTPS(),
                    username, currentPassword,
                    pdfPassword, metadata);
                break;
            }
            case CLIENTWS: {
                LOG.debug("Using ClientWS as procotol");
            
                final String workerIdOrName;
                if (workerId == 0) {
                    workerIdOrName = workerName;
                } else {
                    workerIdOrName = String.valueOf(workerId);
                }

                signer = new ClientWSDocumentSigner(
                    host,
                    port,
                    servlet,
                    workerIdOrName,
                    keyStoreOptions.isUseHTTPS(),
                    username, currentPassword,
                    pdfPassword, metadata);
                break;
            }
            case HTTP:
            default: {
                LOG.debug("Using HTTP as procotol");
                final URL url = new URL(keyStoreOptions.isUseHTTPS() ? "https" : "http", host, port, servlet);
                if (workerId == 0) {
                    signer = new HTTPDocumentSigner(url, workerName, username, currentPassword, pdfPassword, metadata);
                } else {
                    signer = new HTTPDocumentSigner(url, workerId, username, currentPassword, pdfPassword, metadata);
                }
            }
        }
        return signer;
    }

    /**
     * Execute the signing operation.
     * @param manager for managing the threads
     * @param inFile directory
     * @param outFile directory
     */
    protected void runBatch(TransferManager manager, final File inFile, final File outFile) {
        InputStream fin = null;
        try {
            final long size;

            Map<String, Object> requestContext = new HashMap<>();
            if (inFile == null) {
                byte[] bs = data.getBytes();
                fin = new ByteArrayInputStream(bs);
                size = bs.length;
            } else {
                requestContext.put("FILENAME", inFile.getName());
                fin = new BufferedInputStream(new FileInputStream(inFile));
                size = inFile.length();
            }
            runFile(manager, requestContext, inFile, fin, size, outFile);
        } catch (FileNotFoundException ex) {
            LOG.error(MessageFormat.format(TEXTS.getString("FILE_NOT_FOUND:"),
                    ex.getLocalizedMessage()));
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException ex) {
                    LOG.error("Error closing file", ex);
                }
            }
        }
    }
    
    private void initFileSpecificHandlerFactory(final boolean clientSide)
            throws CommandFailureException {
        final ServiceLoader<? extends FileSpecificHandlerFactory> factoryLoader =
                ServiceLoader.load(FileSpecificHandlerFactory.class);
        
        try {
            for (final FileSpecificHandlerFactory factory : factoryLoader) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Trying factory: " + factory.getClass().getName());
                }
                if (!clientSide ||
                    (clientSide && factory.canCreateClientSideCapableHandler())) {
                    this.handlerFactory = factory;
                    return;
                }
            }
        } catch (ServiceConfigurationError e) {
            throw new CommandFailureException("Error loading command factories: " + e.getLocalizedMessage());
        }

        throw new CommandFailureException("Could not find suitable file handler factory");
    }

    /**
     * Runs the signing operation for one file.
     *
     * @param manager for the threads
     * @param requestContext for the request
     * @param inFile directory
     * @param bytes to sign
     * @param outFile directory
     */
    private void runFile(TransferManager manager, Map<String, Object> requestContext, final File inFile, final InputStream bytes, final long size, final File outFile) {  // TODO: merge with runBatch ?, inFile here is only used when removing the file
        try {
            OutputStream outStream = null;

            try (final FileSpecificHandler handler =
                    createFileSpecificHandler(handlerFactory, bytes, size, outFile)) {
                if (outFile == null) {
                    outStream = System.out;
                } else {
                    outStream = new FileOutputStream(outFile);
                }
                // TODO: handle optional digestalgorithm param (for client-side contruction)
                final InputSource inputSource = handler.produceSignatureInput(null);
                final DocumentSigner signer =
                        createSigner(handler, manager == null ? password : manager.getPassword());
                
                // Take start time
                final long startTime = System.nanoTime();
        
                final OutputStream os;
                
                // TODO: this should depend on client-side contruction
                boolean clientSide = false;
                if (clientSide) {
                    os = new ByteArrayOutputStream();
                } else {
                    os = outStream;
                }
                
                // Get the data signed
                signer.sign(inputSource.getInputStream(), inputSource.getSize(), os, requestContext);
                
                handler.assemble(new OutputCollector(os, clientSide));
                
                // Take stop time
                final long estimatedTime = System.nanoTime() - startTime;
                
                if (LOG.isInfoEnabled()) {
                    LOG.info("Wrote " + outFile + ".");
                    LOG.info("Processing " + (inFile == null ? "" : inFile.getName()) + " took "
                        + TimeUnit.NANOSECONDS.toMillis(estimatedTime) + " ms.");
                }
            } catch (NoSuchAlgorithmException ex) {
                // TODO: include digest algorithm in case of error
                LOG.error("Unknown digest algorithm");
            } finally {
                if (outStream != null && outStream != System.out) {
                    outStream.close();
                }
            }

            if (removeFromIndir && inFile != null && inFile.exists()) {
                if (inFile.delete()) {
                    LOG.info("Removed " + inFile);
                } else {
                    LOG.error("Could not remove " + inFile);
                    if (manager != null) {
                        manager.registerFailure();
                    }
                }
            }
            if (manager != null) {
                manager.registerSuccess(); // Login must have worked
            }
        } catch (FileNotFoundException ex) {
            LOG.error("Failure for " + (inFile == null ? "" : inFile.getName()) + ": " + MessageFormat.format(TEXTS.getString("FILE_NOT_FOUND:"),
                    ex.getLocalizedMessage()));
            if (manager != null) {
                manager.registerFailure();
            }
        } catch (SOAPFaultException ex) {
            if (ex.getCause() instanceof AuthorizationRequiredException) {
                final AuthorizationRequiredException authEx =
                        (AuthorizationRequiredException) ex.getCause();
                LOG.error("Authorization failure for " + (inFile == null ? "" : inFile.getName()) + ": " + authEx.getMessage());
            } else if (ex.getCause() instanceof AccessDeniedException) {
                final AccessDeniedException authEx =
                        (AccessDeniedException) ex.getCause();
                LOG.error("Access defined failure for " + (inFile == null ? "" : inFile.getName()) + ": " + authEx.getMessage());
            }
            LOG.error(ex);
        } catch (HTTPException ex) {
            LOG.error("Failure for " + (inFile == null ? "" : inFile.getName()) + ": HTTP Error " + ex.getResponseCode() + ": " + ex.getResponseMessage());
            
            if (manager != null) {
                if (ex.getResponseCode() == 401) { // Only abort for authentication failure
                    if (promptForPassword) {
                        // If password was not specified at command line, ask again for it
                        manager.tryAgainWithNewPassword(inFile);
                    } else {
                        manager.abort();
                    }
                } else {
                    manager.registerFailure();
                }
            }
        } catch (IllegalRequestException | CryptoTokenOfflineException | SignServerException | IOException ex) {
            LOG.error("Failure for " + (inFile == null ? "" : inFile.getName()) + ": " + ex.getMessage());
            if (manager != null) {
                manager.registerFailure();
            }
        }
    }
    
    private FileSpecificHandler createFileSpecificHandler(final FileSpecificHandlerFactory handlerFactory,
                                                          final File inFile,
                                                          final File outFile)
            throws IOException {
        // TODO: handle optional file type argument and client-side contruction
        return handlerFactory.createHandler(inFile, outFile, false);
    }
    
    private FileSpecificHandler createFileSpecificHandler(final FileSpecificHandlerFactory handlerFactory,
                                                          final InputStream inStream,
                                                          final long size,
                                                          final File outFile)
            throws IOException {
        // TODO: handle optional file type argument and client-side contruction
        return handlerFactory.createHandler(inStream, size, outFile, false);
    }

    @Override
    public int execute(String[] args) throws IllegalCommandArgumentsException, CommandFailureException {
        try {
            // Parse the command line
            parseCommandLine(new GnuParser().parse(OPTIONS, args));
            validateOptions();
            // TODO: handle the client-side option here
            initFileSpecificHandlerFactory(false);

            if (inFile != null) {
                LOG.debug("Will request for single file " + inFile);
                runBatch(null, inFile, outFile);
            } else if(inDir != null) {
                LOG.debug("Will request for each file in directory " + inDir);
                File[] inFiles = inDir.listFiles();
                if (inFiles == null || inFiles.length == 0) {
                    LOG.error("No input files");
                    return 1;
                }
                final TransferManager producer = new TransferManager(inFiles, username, password, this, out, oneFirst);
                
                if (threads == null) {
                    threads = DEFAULT_THREADS;
                }
                final int threadCount = threads > inFiles.length ? inFiles.length : threads;
                final ArrayList<TransferThread> consumers = new ArrayList<>();
                
                final Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {                        
                        LOG.error("Unexpected failure in thread " + t.getName() + ". Aborting.", e);
                        producer.abort();
                    }
                };
                
                for (int i = 0; i < threadCount; i++) {
                    final TransferThread t = new TransferThread(i, producer);
                    t.setUncaughtExceptionHandler(handler);
                    consumers.add(t);
                }
                
                // Start the threads
                for (TransferThread consumer : consumers) {
                    consumer.start();
                }
                
                // Wait for the threads to finish
                try {
                    for (TransferThread w : consumers) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Waiting for thread " + w.getName());
                        }
                        w.join();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Thread " + w.getName() + " stopped");
                        }
                    }
                } catch (InterruptedException ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Interupted when waiting for thread: " + ex.getMessage());
                    }
                }
                
                if (producer.isAborted()) {
                    throw new CommandFailureException("Aborted due to failure.");
                }
                
                if (producer.hasFailures()) {
                    throw new CommandFailureException("At least one file failed.");
                }
                
            } else {
                LOG.debug("Will requst for the specified data");
                runBatch(null, null, outFile);
            }
                
            return 0;
        } catch (ParseException ex) {
            throw new IllegalCommandArgumentsException(ex.getMessage());
        }
    }
    
    /**
     * Thread for running the upload/download of the data.
     */
    @SuppressWarnings("PMD.DoNotUseThreads") // Not an JEE application
    private class TransferThread extends Thread {
        private final int id;
        private final TransferManager producer;

        public TransferThread(int id, TransferManager producer) {
            super("transfer-" + id);
            this.id = id;
            this.producer = producer;
        }
        
        @Override
        public void run() {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Starting " + getName() + "...");
            }
            File file;
            while ((file = producer.nextFile()) != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Sending " + file + "...");
                }
                runBatch(producer, file, new File(outDir, file.getName()));
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace(id + ": No more work.");
            }
        }
    }
    
}
