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
package org.signserver.server.log;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Custom wrapper for Log4J ErrorHandler, so we can check if logging was successful or not.
 * Copied from CESeCore <code>org.cesecore.audit.impl.log4j.Log4jDeviceErrorHandler</code>
 * 
 * @author Marcus Lundblad
 * @version $Id: Log4jDeviceErrorHandler.java 3186 2013-01-14 12:01:17Z malu9369 $
 */
class Log4jDeviceErrorHandler implements ErrorHandler {
        
        private boolean ok = true;
        
        private final ErrorHandler errorHandler;
        
        public Log4jDeviceErrorHandler(final ErrorHandler errorHandler) {
                this.errorHandler = errorHandler;
        }
        
        /** @return true if this error handler has not been invoked since last call to this method. */
        public boolean isOk() {
                if (ok) {
                        return true;
                }
                ok = true;      // Reset
                return false;
        }

        @Override
        public void error(final String arg0) {
                errorHandler.error(arg0);
                ok = false;
        }

        @Override
        public void error(final String arg0, final Exception arg1, final int arg2) {
                errorHandler.error(arg0, arg1, arg2);
                ok = false;
        }

        @Override
        public void error(final String arg0, final Exception arg1, final int arg2, final LoggingEvent arg3) {
                errorHandler.error(arg0, arg1, arg2, arg3);
                ok = false;
        }

        @Override
        public void setAppender(final Appender arg0) {
                errorHandler.setAppender(arg0);
        }

        @Override
        public void setBackupAppender(final Appender arg0) {
                errorHandler.setBackupAppender(arg0);
        }

        @Override
        public void setLogger(final Logger arg0) {
                errorHandler.setLogger(arg0);
        }

        @Override
        public void activateOptions() {
                errorHandler.activateOptions();
        }
}
