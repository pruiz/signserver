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
package org.signserver.client.cli.validationservice;

import org.signserver.cli.spi.AbstractCommandFactory;
import org.signserver.client.cli.spi.ClientCommandFactory;

/**
 * CommandFactory for the validation client.
 *
 * @author Markus Kilås
 * @version $Id: ValidationClientCommandFactory.java 2301 2012-04-02 11:53:49Z netmackan $
 */
public class ValidationClientCommandFactory extends AbstractCommandFactory implements ClientCommandFactory {

    @Override
    protected void registerCommands() {
        // Top level commands
        put("validatecertificate", ValidateCertificateCommand.class);
    }

}
