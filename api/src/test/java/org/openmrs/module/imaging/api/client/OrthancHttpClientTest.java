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
		assertEquals("http://localhost:8052/system", con.getURL().toString());
		assertFalse(con.getUseCaches());
		assertFalse(con.getInstanceFollowRedirects());
		assertEquals(10000, con.getConnectTimeout());
		assertEquals(60000, con.getReadTimeout());
	}

	@Test
	public void rejectsInvalidProtocolsCredentialsAndCrossOriginPathsBeforeConnecting() {
		String[] urls = { "ftp://synthetic.invalid", "file:///tmp/synthetic", "http://user:secret@synthetic.invalid", "bad url" };
		for (String url : urls) {
			assertThrows(IOException.class, () -> httpClient.createConnection("GET", url, "/system", "", ""));
		}
		assertThrows(IOException.class, () -> httpClient.createConnection("GET", "http://synthetic.invalid",
		    "https://different.invalid/system", "", ""));
	}

	@Test
	public void rejectsRedirectTargetsPortChangesAndMalformedConfiguration() {
		String[] paths = { "//different.invalid/system", "http://synthetic.invalid:8043/system",
		    "https://synthetic.invalid:8042/system", "http://user:password@synthetic.invalid:8042/system",
		    "http://synthetic.invalid:8042/invalid path", null };
		for (String path : paths) {
			assertThrows(IOException.class, () -> httpClient.createConnection("GET",
			    "http://synthetic.invalid:8042", path, null, null));
		}
		assertThrows(IOException.class, () -> httpClient.createConnection("GET", null, "/system", "", ""));
	}

	@Test
	public void supportsHttpsAndIpv6WithoutFollowingRedirects() throws IOException {
		HttpURLConnection connection = httpClient.createConnection("GET", "https://[::1]:8042",
		    "/changes?limit=1000&since=12", null, null);
		assertEquals("https://[::1]:8042/changes?limit=1000&since=12", connection.getURL().toString());
		assertFalse(connection.getInstanceFollowRedirects());
		connection.disconnect();
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
        assertEquals("The Orthanc request could not be completed", exception.getMessage());
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

	@Test
	public void remoteErrorContentIsClosedWithoutBeingReadOrExposed() throws IOException {
		HttpURLConnection connection = mock(HttpURLConnection.class);
		java.io.InputStream body = mock(java.io.InputStream.class);
		when(connection.getErrorStream()).thenReturn(body);
		IOException failure = assertThrows(IOException.class, () -> OrthancHttpClient.throwConnectionException(
		    new OrthancConfiguration(), connection));
		assertEquals("The Orthanc request could not be completed", failure.getMessage());
		verify(body).close();
		verify(body, never()).read();
	}

	@Test
	public void failedConnectionChecksAlwaysDisconnect() throws IOException {
		OrthancHttpClient client = spy(new OrthancHttpClient());
		HttpURLConnection connection = mock(HttpURLConnection.class);
		doReturn(connection).when(client).createConnection("GET", "http://synthetic.invalid", "/system", "", "");
		when(connection.getResponseCode()).thenThrow(new java.net.SocketTimeoutException());
		assertThrows(IOException.class, () -> client.testOrthancConnection("http://synthetic.invalid", "", ""));
		verify(connection).disconnect();
	}

	@Test
	public void errorStreamCloseFailureDoesNotPreventDisconnect() throws IOException {
		HttpURLConnection connection = mock(HttpURLConnection.class);
		java.io.InputStream error = mock(java.io.InputStream.class);
		when(connection.getErrorStream()).thenReturn(error);
		doThrow(new IOException("synthetic close failure")).when(error).close();
		OrthancHttpClient.closeConnection(connection);
		verify(connection).disconnect();
	}
	
}
