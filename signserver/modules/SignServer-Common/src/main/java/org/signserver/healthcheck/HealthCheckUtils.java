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
package org.signserver.healthcheck;

import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import org.apache.log4j.Logger;

/**
 * Utility methods related to the Healt check functionality.
 * 
 * @version $Id$
 */
public class HealthCheckUtils {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(HealthCheckUtils.class);

    public static List<String> checkMemory(int minfreememory) {
        final LinkedList<String> result = new LinkedList<>();
        if (minfreememory >= Runtime.getRuntime().freeMemory()) {
            result.add("Error Virtual Memory is about to run out, currently free memory :" + Runtime.getRuntime().freeMemory());
        }
        return result;
    }

    public static List<String> checkDB(final EntityManager em, final String checkDBString) {
        final LinkedList<String> result = new LinkedList<>();
        try {
            em.createNativeQuery(checkDBString).getResultList();
        } catch (Exception e) {
            result.add("Error creating connection to SignServer Database.");
            LOG.error("Error creating connection to SignServer Database.", e);
        }
        return result;
    }
}
