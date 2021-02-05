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
package org.signserver.protocol.ws;

import org.signserver.protocol.ws.client.ICommunicationFault;
import org.signserver.protocol.ws.client.IFaultCallback;

/**
 * TODO: Document me!
 * 
 * @version $Id: FaultCallback.java 3452 2013-04-20 21:32:59Z netmackan $
 */
public class FaultCallback implements IFaultCallback {

    boolean callBackCalled = false;

    @Override
    public void addCommunicationError(ICommunicationFault fault) {
        //System.err.println("ERROR : " + fault.getDescription() + ": HOST : " + fault.getHostName() );

        if (fault.getThrowed() != null) {
            //System.err.print("StackTrace : " );
            //fault.getThrowed().printStackTrace();
        }
        callBackCalled = true;
    }

    public boolean isCallBackCalled() {
        return callBackCalled;
    }
}
