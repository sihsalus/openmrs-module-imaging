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

package org.openmrs.module.imaging.page.controller;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ClientConnectionPair;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.study.DicomInstance;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.ui.framework.Model;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InstancesPageControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private InstancesPageController controller;
	
	private Patient patient;
	
	private DicomStudyService dicomStudyService;
	
	private OrthancConfigurationService orthancConfigurationService;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("testDicomStudyDataset.xml");
		controller = (InstancesPageController) applicationContext.getBean("instancesPageController");
		patient = Context.getPatientService().getPatient(1);
		dicomStudyService = Context.getService(DicomStudyService.class);
		orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
	}
	
	@Test
	public void testGet_shouldPopulateModelWithInstancesList() throws IOException {
		
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		// Sample JSON response from Orthanc for /series/{id}/instances
		String jsonResponse = "[{\"MainDicomTags\": {\"SOPInstanceUID\": \"SOPUID_1.2.3.4.5.6\","
		        + "\"InstanceNumber\": \"1\"}," + "\"ID\": \"instanceID_1\"}]";
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		InputStream responseStream = new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8));
		when(mockPair.getConnection().getInputStream()).thenReturn(responseStream);
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		Model model = new PageModel();
		controller.get(model, "MOCK-SERIES-UID", study.getId());
		
		assertNotNull(model.getAttribute("instances"));
		assertNotNull(model.getAttribute("studyInstanceUID"));
		
		Object instancesObj = model.getAttribute("instances");
		assertTrue(instancesObj instanceof List<?>);
		List<DicomInstance> instances = (List<DicomInstance>) instancesObj;
		assertEquals(1, instances.size());
		
		DicomInstance firstInstance = instances.get(0);
		assertEquals("SOPUID_1.2.3.4.5.6", firstInstance.getSopInstanceUID());
		assertEquals("instanceID_1", firstInstance.getOrthancInstanceUID());
	}
	
	@Test
	public void testPreviewInstance_shouldReturnPreviewBytes() throws IOException {
		// Arrange
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		OrthancConfiguration config = study.getOrthancConfiguration();
		String orthancInstanceUID = "SAMPLE_UID";
		
		// Mock HTTP connection to Orthanc
		byte[] fakeImage = new byte[] { 1, 2, 3, 4 };
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "GET",
		    "/instances/" + orthancInstanceUID + "/preview", "", config);
		when(mockPair.getConnection().getInputStream()).thenReturn(new ByteArrayInputStream(fakeImage));
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		ResponseEntity<?> response = controller.previewInstance(orthancInstanceUID, study.getId());
		
		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertArrayEquals(fakeImage, (byte[]) response.getBody());
		assertTrue(response.getHeaders().containsKey("Content-type"));
	}
}
