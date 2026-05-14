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
package org.openmrs.module.imaging.web.controller.RequestModel;

public class OrthancConfigurationRequest {
	
	private String orthancBaseUrl;
	
	private String orthancProxyUrl;
	
	private String orthancUsername;
	
	private String orthancPassword;
	
	public String getOrthancBaseUrl() {
		return orthancBaseUrl;
	}
	
	public void setOrthancBaseUrl(String orthancBaseUrl) {
		this.orthancBaseUrl = orthancBaseUrl;
	}
	
	public String getOrthancProxyUrl() {
		return orthancProxyUrl;
	}
	
	public void setOrthancProxyUrl(String orthancProxyUrl) {
		this.orthancProxyUrl = orthancProxyUrl;
	}
	
	public String getOrthancUsername() {
		return orthancUsername;
	}
	
	public void setOrthancUsername(String orthancUsername) {
		this.orthancUsername = orthancUsername;
	}
	
	public String getOrthancPassword() {
		return orthancPassword;
	}
	
	public void setOrthancPassword(String orthancPassword) {
		this.orthancPassword = orthancPassword;
	}
}
