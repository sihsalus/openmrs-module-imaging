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

import org.openmrs.module.imaging.OrthancConfiguration;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class OrthancHttpClient {
	
	public HttpURLConnection createConnection(String method, String url, String path, String username, String password)
	        throws IOException {
		URL serverURL;
		try {
			URI base = URI.create(url);
			URI target = base.resolve(path);
			if (!("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))
			        || target.getHost() == null || target.getRawUserInfo() != null
			        || !Objects.equals(base.getRawAuthority(), target.getRawAuthority())
			        || !Objects.equals(base.getScheme(), target.getScheme())) {
				throw new IllegalArgumentException();
			}
			serverURL = target.toURL();
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw new IOException("Invalid Orthanc HTTP configuration");
		}
		String credentials = (username == null ? "" : username) + ":" + (password == null ? "" : password);
		String encoding = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		HttpURLConnection con = (HttpURLConnection) serverURL.openConnection();
		con.setRequestMethod(method);
		con.setRequestProperty("Authorization", "Basic " + encoding);
		con.setUseCaches(false);
		con.setInstanceFollowRedirects(false);
		con.setConnectTimeout(10000);
		con.setReadTimeout(60000);
		return con;
	}
	
	/**
	 * @param con http url request connection
	 * @param query the query string
	 * @throws IOException IO exception
	 */
	public void sendOrthancQuery(HttpURLConnection con, String query) throws IOException {
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        con.setRequestProperty( "charset", "utf-8");
        con.setDoOutput(true);
        byte[] data = query.getBytes(StandardCharsets.UTF_8);
        con.setRequestProperty( "Content-Length", Integer.toString(data.length));
        try(DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.write(data);
        }
    }
	
	/**
	 * @param config the orthanc server configuration
	 * @param con the http url connection
	 * @throws IOException the IO exception
	 */
	public static void throwConnectionException(OrthancConfiguration config, HttpURLConnection con) throws IOException {
		// Error bodies may contain DICOM identifiers or patient metadata.
		try (InputStream ignored = con.getErrorStream()) {
			// Close without reading or relaying remote content.
		} catch (IOException ignored) {
			// Preserve the generic failure even if closing the error stream fails.
		}
		throw new IOException("The Orthanc request could not be completed");
	}

	public static void closeConnection(HttpURLConnection connection) {
		if (connection == null) {
			return;
		}
		try (InputStream ignored = connection.getErrorStream()) {
			// Discard remote error content; it can contain patient metadata.
		} catch (IOException ignored) {
			// Disconnect must still run when closing a failed response throws.
		} finally {
			connection.disconnect();
		}
	}
	
	/**
	 * @param config
	 * @return
	 */
	public boolean isOrthancReachable(OrthancConfiguration config) {
		HttpURLConnection connection = null;
		try {
			if (config == null) {
				return false;
			}
			connection = createConnection("GET", config.getOrthancBaseUrl(), "/system",
			    config.getOrthancUsername(), config.getOrthancPassword());
			connection.setConnectTimeout(3000); // 3 seconds timeout
			connection.setReadTimeout(3000);
			
			int responseCode = connection.getResponseCode();
			return responseCode == 200;
		}
		catch (IOException e) {
			return false;
		} finally {
			if (connection != null) {
				closeConnection(connection);
			}
		}
	}
	
	/**
	 * @param url the Url
	 * @param username the user name
	 * @param password the password
	 * @return the response status
	 * @throws IOException the IO exception
	 */
	public int testOrthancConnection(String url, String username, String password) throws IOException {
		HttpURLConnection con = createConnection("GET", url, "/system", username, password);
		try {
			return con.getResponseCode();
		} finally {
			closeConnection(con);
		}
	}
	
	public int getStatus(HttpURLConnection con) throws IOException {
		return con.getResponseCode();
	}
	
	public InputStream getResponseStream(HttpURLConnection con) throws IOException {
		return con.getInputStream();
	}
	
	public String getErrorMessage(HttpURLConnection con) throws IOException {
		return con.getResponseMessage();
	}
}
