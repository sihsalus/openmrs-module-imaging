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
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.imaging.api.client;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.imaging.OrthancConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrthancHttpClientTest {

	private OrthancHttpClient httpClient;

	@Before
	public void setUp() {
		httpClient = new OrthancHttpClient();
	}

	@Test
	public void testCreateConnection() throws IOException {
		String method = "POST";
		String url = "http://localhost:8052";
		String path = "/system";
		String userName = "orthanc";
		String password = "orthanc";

		HttpURLConnection con = httpClient.createConnection(method, url, path, userName, password);

		assertEquals("POST", con.getRequestMethod());
		String expectedAuth = "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes());
		assertFalse(con.getUseCaches());
	}

	@Test
	public void testSendOrthancQuery() throws IOException {
		HttpURLConnection con = mock(HttpURLConnection.class);
		OutputStream os = mock(OutputStream.class);
		when(con.getOutputStream()).thenReturn(os);

		String query = "{\"query\":\"test\"}";
		httpClient.sendOrthancQuery(con, query);

		verify(con).setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		verify(con).setRequestProperty("charset", "utf-8");
		verify(con).setRequestProperty("Content-Length", Integer.toString(query.getBytes().length));
		verify(con).setDoOutput(true);
		verify(con).getOutputStream();
	}

	@Test
    public void testThrowConnectionException() throws IOException {
        OrthancConfiguration config = mock(OrthancConfiguration.class);
        when(config.getOrthancBaseUrl()).thenReturn("http://localhost:8052");

        HttpURLConnection con = mock(HttpURLConnection.class);
        when(con.getResponseCode()).thenReturn(500);
        when(con.getResponseMessage()).thenReturn("Internal Server Error");

        IOException exception = assertThrows(IOException.class, () ->
            OrthancHttpClient.throwConnectionException(config, con)
        );
        assertTrue(exception.getMessage().contains("Request to Orthanc server " + config.getOrthancBaseUrl() + " failed with error"));
    }

	@Test
	public void testIsOrthancReachable_unreachableServer() throws IOException {
		OrthancConfiguration config = mock(OrthancConfiguration.class);
		when(config.getOrthancBaseUrl()).thenReturn("http://127.0.0.1:1");
		when(config.getOrthancProxyUrl()).thenReturn("");
		when(config.getOrthancPassword()).thenReturn("orthanc");
		when(config.getOrthancUsername()).thenReturn("orthanc");

		boolean reachable = httpClient.isOrthancReachable(config);
		assertFalse(reachable);
	}

	@Test
	public void testGetStatus() throws IOException {
		HttpURLConnection con = mock(HttpURLConnection.class);
		when(con.getResponseCode()).thenReturn(200);
		assertEquals(200, httpClient.getStatus(con));
	}

	@Test
	public void testGetResponseStream() throws IOException {
		HttpURLConnection con = mock(HttpURLConnection.class);
		ByteArrayInputStream inputStream = new ByteArrayInputStream("response".getBytes());
		when(con.getInputStream()).thenReturn(inputStream);
		assertEquals(inputStream, httpClient.getResponseStream(con));
	}

	@Test
	public void testGetErrorMessage() throws IOException {
		HttpURLConnection con = mock(HttpURLConnection.class);
		when(con.getResponseMessage()).thenReturn("Not Found");
		assertEquals("Not Found", httpClient.getErrorMessage(con));
	}

}
