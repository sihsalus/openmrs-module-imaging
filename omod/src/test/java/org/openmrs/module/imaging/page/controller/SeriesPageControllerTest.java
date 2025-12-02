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
import org.openmrs.module.imaging.api.study.DicomSeries;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.ui.framework.Model;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class SeriesPageControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private DicomStudyService dicomStudyService;
	
	private SeriesPageController controller;
	
	private OrthancConfigurationService orthancConfigurationService;
	
	private Patient patient;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("testDicomStudyDataset.xml");
		controller = (SeriesPageController) applicationContext.getBean("seriesPageController");
		
		patient = Context.getPatientService().getPatient(1);
		dicomStudyService = Context.getService(DicomStudyService.class);
		orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
	}
	
	@Test
	public void testGet_shouldPopulateModelWithSeriesList() throws IOException {
		
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		dicomStudyService = Context.getService(DicomStudyService.class);
		
		String jsonResponse = "[{\"MainDicomTags\": {\"SeriesInstanceUID\": \"testSeriesUID123\","
		        + "\"SeriesDescription\": \"Test Series\"," + "\"SeriesNumber\": \"1\"," + "\"Modality\": \"CT\","
		        + "\"SeriesDate\": \"20250717\"," + "\"SeriesTime\": \"123000\"}," + "\"ID\": \"abcd1\"}]";
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		InputStream responseStream = new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8));
		when(mockPair.getConnection().getInputStream()).thenReturn(responseStream);
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		Model model = new PageModel();
		controller.get(model, study.getId());
		
		assertNotNull(model.getAttribute("serieses"));
		assertNotNull(model.getAttribute("studyId"));
		assertNotNull(model.getAttribute("studyInstanceUID"));
		assertNotNull(model.getAttribute("privilegeModifyImageData"));
		
		// Verify serieses list has exactly 1 item
		Object seriesObj = model.getAttribute("serieses");
		assertTrue(seriesObj instanceof List<?>);
		List<?> seriesList = (List<?>) seriesObj;
		assertEquals(1, seriesList.size());
		
		Object firstSeries = seriesList.get(0);
		assertNotNull(firstSeries);
		if (firstSeries instanceof DicomSeries) {
			DicomSeries series = (DicomSeries) firstSeries;
			assertEquals("testSeriesUID123", series.getSeriesInstanceUID());
		}
	}
	
	@Test
	public void testDeleteSeries_shouldRedirectWithSuccessMessage() throws Exception {
		
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		// Prepare a DicomStudy object
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		// Mock the HTTP DELETE request to return HTTP_OK
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "DELETE",
		    "/series/MOCK-SERIES-UID", "", config);
		
		HttpURLConnection mockConnection = mockPair.getConnection();
		when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
		
		// Set the mock client in the service
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		RedirectAttributes redirectAttrs = new RedirectAttributesModelMap();
		
		// Call the controller
		String view = controller.deleteSeries(redirectAttrs, "MOCK-SERIES-UID", study.getId(), patient);
		
		// Verify redirect view and attributes
		assertEquals("redirect:/imaging/series.page", view);
		assertEquals(patient.getId().toString(), redirectAttrs.getAttribute("patientId"));
		assertEquals(study.getId().toString(), redirectAttrs.getAttribute("studyId"));
		assertEquals("Series successfully deleted", redirectAttrs.getAttribute("message"));
	}
	
}
