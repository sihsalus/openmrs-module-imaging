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

import org.apache.commons.io.IOUtils;
import org.openmrs.module.imaging.OrthancConfiguration;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class OrthancHttpClient {
	
	public HttpURLConnection createConnection(String method, String url, String path, String username, String password)
	        throws IOException {
		String encoding = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		URL serverURL = URI.create(url).resolve(path).toURL();
		HttpURLConnection con = (HttpURLConnection) serverURL.openConnection();
		con.setRequestMethod(method);
		con.setRequestProperty("Authorization", "Basic " + encoding);
		con.setUseCaches(false);
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
		String errorMessage;
		try {
			InputStream errorStream = con.getErrorStream();
			if (errorStream != null) {
				errorMessage = IOUtils.toString(errorStream, StandardCharsets.UTF_8);
			} else {
				errorMessage = "Unknown error";
			}
		}
		catch (IOException e) {
			errorMessage = "Failed to read error stream: " + e.getMessage();
		}
		
		throw new IOException("Request to Orthanc server " + config.getOrthancBaseUrl() + " failed with error: "
		        + errorMessage);
	}
	
	/**
	 * @param config
	 * @return
	 */
	public boolean isOrthancReachable(OrthancConfiguration config) {
		try {
			URL url = new URL(config.getOrthancBaseUrl() + "/system"); // `/system` is a common endpoint in Orthanc
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(3000); // 3 seconds timeout
			connection.setReadTimeout(3000);
			
			String auth = config.getOrthancUsername() + ":" + config.getOrthancPassword();
			String encodeAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			connection.setRequestProperty("Authorization", "Basic " + encodeAuth);
			
			int responseCode = connection.getResponseCode();
			return responseCode == 200;
		}
		catch (IOException e) {
			return false;
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
		int status = con.getResponseCode();
		con.disconnect();
		return status;
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
