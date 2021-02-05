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
package org.signserver.module.signerstatusreport;

/**
 * Capable of constructing an report.
 * 
 * @author Markus Kilås
 * @version $Id: ReportBuilder.java 2300 2012-04-02 11:22:03Z netmackan $
 */
public interface ReportBuilder {

    /**
     * @return The newly produced report
     */
    CharSequence buildReport();
    
}
