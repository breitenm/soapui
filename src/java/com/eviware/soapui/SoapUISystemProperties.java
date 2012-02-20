/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui;

/**
 * @author Erik R. Yverling
 * 
 *         This is a container for all system properties used in soapUI core.
 */
public interface SoapUISystemProperties
{
	public final static String TEST_ON_DEMAND_HOST = "soapui.testondemand.host";

	public static final String SOAPUI_SSL_KEYSTORE_LOCATION = "soapui.ssl.keystore.location";
	public static final String SOAPUI_SSL_KEYSTORE_PASSWORD = "soapui.ssl.keystore.password";
}
