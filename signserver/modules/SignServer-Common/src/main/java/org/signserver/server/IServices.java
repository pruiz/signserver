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
package org.signserver.server;

/**
 * Map of services of different types that can be used by implementations.
 *
 * @author Markus Kilås
 * @version $Id: IServices.java 6118 2015-06-23 14:32:00Z netmackan $
 */
public interface IServices {

    <T> T get(Class<? extends T> type);
    <T> T put(Class<? extends T> type, T service);
}
