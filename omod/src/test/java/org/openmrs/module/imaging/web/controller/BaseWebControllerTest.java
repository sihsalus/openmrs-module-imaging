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

package org.openmrs.module.imaging.web.controller;

import junit.framework.Assert;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Ignore;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Ignore
public class BaseWebControllerTest extends BaseModuleWebContextSensitiveTest {
	
	@Autowired
	protected RequestMappingHandlerAdapter handlerAdapter;
	
	@Autowired
	protected List<RequestMappingHandlerMapping> handlerMappings;
	
	public MockHttpServletRequest request(RequestMethod method, String requestURI) {
		MockHttpServletRequest request = new MockHttpServletRequest(method.toString(), requestURI);
		request.addHeader("content-type", "application/json");
		return request;
	}
	
	public static class Parameter {
		
		public String name;
		
		public String value;
		
		public Parameter(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}
	
	public MockHttpServletRequest newRequest(RequestMethod method, String requestURI, Parameter... parameters) {
		MockHttpServletRequest request = request(method, requestURI);
		for (Parameter parameter : parameters) {
			request.addParameter(parameter.name, parameter.value);
		}
		return request;
	}
	
	public MockHttpServletRequest newRequest(RequestMethod method, String requestURI, Map<String, String> headers,
	        Parameter... parameters) {
		MockHttpServletRequest request = newRequest(method, requestURI, parameters);
		headers.forEach(request::addHeader);
		return request;
	}
	
	public MockHttpServletRequest newDeleteRequest(String requestURI, Parameter... parameters) {
		return newRequest(RequestMethod.DELETE, requestURI, parameters);
	}
	
	public MockHttpServletRequest newGetRequest(String requestURI, Parameter... parameters) {
		return newRequest(RequestMethod.GET, requestURI, parameters);
	}
	
	public MockHttpServletRequest newGetRequest(String requestURI, Map<String, String> headers, Parameter... parameters) {
		return newRequest(RequestMethod.GET, requestURI, headers, parameters);
	}
	
	public MockHttpServletRequest newPostRequest(String requestURI, Object content) {
		return newWriteRequest(requestURI, content, RequestMethod.POST);
	}
	
	public MockHttpServletRequest newPutRequest(String requestURI, Object content) {
		return newWriteRequest(requestURI, content, RequestMethod.PUT);
	}
	
	private MockHttpServletRequest newWriteRequest(String requestURI, Object content, RequestMethod requestMethod) {
		MockHttpServletRequest request = request(requestMethod, requestURI);
		try {
			String json = new ObjectMapper().writeValueAsString(content);
			request.setContent(json.getBytes("UTF-8"));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return request;
	}
	
	public MockHttpServletRequest newPostRequest(String requestURI, Map<String, String> params) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", requestURI);
		for (Map.Entry<String, String> entry : params.entrySet()) {
			request.addParameter(entry.getKey(), entry.getValue());
		}
		return request;
	}
	
	public MockHttpServletRequest newPostRequest(String requestURI, MultipartFile file, int configurationId) {
		MockHttpServletRequest request = request(RequestMethod.POST, requestURI);
		request.setMethod("POST");
		request.setContentType("multipart/form-data");
		request.setParameter("configurationId", String.valueOf(configurationId));
		request.setAttribute("file", file); // simulate @RequestParam("file")
		return request;
	}
	
	public MockHttpServletRequest newPutRequest(String requestURI, String content) {
		MockHttpServletRequest request = request(RequestMethod.PUT, requestURI);
		try {
			String json = new ObjectMapper().writeValueAsString(content);
			request.setContent(json.getBytes("UTF-8"));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return request;
	}
	
	public MockHttpServletResponse handle(HttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		HandlerExecutionChain handlerExecutionChain = null;
		for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
			handlerExecutionChain = handlerMapping.getHandler(request);
			if (handlerExecutionChain != null) {
				break;
			}
		}
		Assert.assertNotNull("The request URI does not exist", handlerExecutionChain);
		
		handlerAdapter.handle(request, response, handlerExecutionChain.getHandler());
		
		return response;
	}
	
	public SimpleObject deserialize(MockHttpServletResponse response) throws Exception {
		String content = response.getContentAsString();
		Assert.assertFalse("Response is empty", content.isEmpty());
		return new ObjectMapper().readValue(response.getContentAsString(), SimpleObject.class);
	}
	
}
