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

package org.openmrs.module.imaging.page.controller;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.ui.framework.Model;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImagingSettingsPageControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private ImagingSettingsPageController controller;
	
	private OrthancConfigurationService orthancConfigurationService;
	
	private DicomStudyService dicomStudyService;
	
	private RequestProcedureService requestProcedureService;

	private static class ByteArrayServletOutputStream extends ServletOutputStream {
		
		private final ByteArrayOutputStream delegate;
		
		private ByteArrayServletOutputStream(ByteArrayOutputStream delegate) {
			this.delegate = delegate;
		}
		
		@Override
		public boolean isReady() {
			return true;
		}
		
		@Override
		public void setWriteListener(WriteListener writeListener) {
		}
		
		@Override
		public void write(int b) throws IOException {
			delegate.write(b);
		}
	}
	
	@Before
	public void setUp() throws Exception {
		controller = (ImagingSettingsPageController) applicationContext.getBean("imagingSettingsPageController");
		orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		dicomStudyService = Context.getService(DicomStudyService.class);
		requestProcedureService = Context.getService(RequestProcedureService.class);
	}
	
	@Test
	public void testGet_shouldPopulateModel() {
		Model model = new PageModel();
		controller.get(model);
		
		assertNotNull(model.getAttribute("orthancConfigurations"));
		assertNotNull(model.getAttribute("privilegeManagerOrthancConfiguration"));
	}
	
	@Test
    public void testStoreConfiguration_shouldSaveConfiguration() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String orthancBaseUrl = "http://localhost:8052";
        String orthancProxy = "";
        String orthancUsername = "orthanc";
        String orthancPassword = "orthanc";

        String redirect = controller.storeConfiguration(
                redirectAttributes, orthancBaseUrl, orthancProxy, orthancUsername, orthancPassword);

        assertEquals("redirect:/imaging/imagingSettings.page", redirect);
        // Verify it was saved
        OrthancConfiguration config = orthancConfigurationService.getAllOrthancConfigurations().stream()
                .filter(c -> c.getOrthancBaseUrl().equals(orthancBaseUrl))
                .findFirst()
                .orElse(null);
        assertNotNull(config);
        assertEquals(orthancUsername, config.getOrthancUsername());
    }
	
	@Test
	public void testDeleteConfiguration_shouldDeleteIfNoStudyOrProcedure() {
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://localhost:8052");
		config.setOrthancProxyUrl("");
		config.setOrthancUsername("orthanc");
		config.setOrthancPassword("orthanc");
		orthancConfigurationService.saveOrthancConfiguration(config);
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		String redirect = controller.deleteConfiguration(redirectAttributes, config.getId());
		
		assertEquals("redirect:/imaging/imagingSettings.page", redirect);
		assertTrue(redirectAttributes.getFlashAttributes().containsKey("message"));
	}
	
	@Test
	public void testCheckConfiguration_shouldReturnMessage() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(outputStream));
		
		controller.checkConfiguration(response, "http://localhost:8052", "", "orthanc", "orthanc");
		
		String responseText = outputStream.toString();
		assertTrue(responseText.contains("Check successful"));
	}
	
	@Test
	public void testCheckConfiguration_shouldReturnInvalidMessage() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(outputStream));
		
		controller.checkConfiguration(response, "http://localhost:8062", "", "orthanc", "orthanc");
		String invalidResponseText = outputStream.toString();
		assertTrue(invalidResponseText.contains("Connection refused"));
	}
}
