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
package org.signserver.testutils;

/**
 * TODO: Document me!
 * 
 * @version $Id: ExitException.java 2943 2012-11-06 09:54:30Z netmackan $
 */
public class ExitException extends SecurityException {

    private static final long serialVersionUID = -4443566376708240848L;

    public final int number;

    /**
     * @param message
     */
    ExitException(int nr) {
        super("System exit with status " + nr);
        number = nr;
    }
}
