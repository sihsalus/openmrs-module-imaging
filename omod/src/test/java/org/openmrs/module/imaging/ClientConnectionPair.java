/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.imaging;

import org.openmrs.module.imaging.api.client.OrthancHttpClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientConnectionPair {
	
	private final OrthancHttpClient client;
	
	private final HttpURLConnection connection;
	
	public ClientConnectionPair(OrthancHttpClient client, HttpURLConnection connection) {
		this.client = client;
		this.connection = connection;
	}
	
	public OrthancHttpClient getClient() {
		return client;
	}
	
	public HttpURLConnection getConnection() {
		return connection;
	}
	
	public static ClientConnectionPair setupMockClientWithStatus(int statusCode, String method, String path,
	        String errorMessage, OrthancConfiguration config) throws IOException {
		OrthancHttpClient mockClient = mock(OrthancHttpClient.class);
		HttpURLConnection mockCon = mock(HttpURLConnection.class);
		when(
		    mockClient.createConnection(eq(method), eq(config.getOrthancBaseUrl()), eq(path),
		        eq(config.getOrthancUsername()), eq(config.getOrthancPassword()))).thenReturn(mockCon);
		
		when(mockCon.getResponseCode()).thenReturn(statusCode);
		
		InputStream errorStream = new ByteArrayInputStream(errorMessage.getBytes(StandardCharsets.UTF_8));
		when(mockCon.getErrorStream()).thenReturn(errorStream);
		
		return new ClientConnectionPair(mockClient, mockCon);
	}
}
