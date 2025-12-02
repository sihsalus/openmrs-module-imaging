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
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ClientConnectionPair;
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.ui.framework.Model;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StudiesPageControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private StudiesPageController controller;
	
	private Patient testPatient;
	
	private DicomStudyService dicomStudyService;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("testDicomStudyDataset.xml");
		testPatient = Context.getPatientService().getPatient(1);
		
		Context.getAdministrationService().setGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, "200000000");
		controller = (StudiesPageController) applicationContext.getBean("studiesPageController");
	}
	
	@Test
	public void testGet_shouldPopulateModelOnGet() {
		Model model = new PageModel();
		controller.get(model, testPatient);
		
		assertNotNull(model.getAttribute("studies"));
		assertNotNull(model.getAttribute("orthancConfigurations"));
		assertTrue((Boolean) model.getAttribute("privilegeModifyImageData"));
		assertNotNull(model.getAttribute("maxUploadImageDataSize"));
	}
	
	@Test
    public void testHandleMaxSizeException_shouldRedirectWithMessage() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(200_000_000);

        String redirectUrl = controller.handleMaxSizeException(ex, redirectAttributes, testPatient);

        assertEquals("redirect:/imaging/studies.page", redirectUrl);
        assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
        assertEquals("File size exceeds maximum upload limit. Please upload a smaller file.", redirectAttributes.getAttribute("message"));
    }
	
	@Test
	public void testUploadStudy_shouldUploadFilesAndRedirect() throws Exception {
		
		OrthancConfigurationService orthancService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancService.getOrthancConfiguration(1);
		
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(200, "POST", "/instances", "", config);
		HttpURLConnection mockConnection = pair.getConnection();
		
		// Mock getOutputStream
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		doReturn(outputStream).when(mockConnection).getOutputStream();
		
		// Inject the mocked client
		dicomStudyService = Context.getService(DicomStudyService.class);
		dicomStudyService.setHttpClient(pair.getClient());
		
		MultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "dummy.dcm", "application/dicom",
		        "dummy data".getBytes());
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		String redirectUrl = controller.uploadStudy(redirectAttributes, null, config.getId(), new MultipartFile[] { file },
		    testPatient);
		assertEquals("redirect:/imaging/studies.page", redirectUrl);
		assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
		assertNotNull(redirectAttributes.getAttribute("message"));
		assertEquals("All files uploaded", redirectAttributes.getAttribute("message"));
	}
	
	@Test
	public void syncStudy_allStudiesWithNoConfig_shouldFetchAll() throws IOException {
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		String view = controller.syncStudy(redirectAttributes, -1, "all", testPatient);
		
		// Assert redirect URL
		assertEquals("redirect:/imaging/syncStudies.page", view);
		assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
		assertEquals("Not all studies could be downloaded successfully. The server might be unavailable.",
		    redirectAttributes.getAttribute("message"));
	}
	
	@Test
	public void syncStudy_shouldHandleServerValid() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		
		// Setup mock client & connection with 200 OK
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		// Simulate a JSON response body from Orthanc
		String mockJson = "[{ \"PatientID\": \"123\", \"StudyInstanceUID\": \"abc\" }]";
		InputStream inputStream = new ByteArrayInputStream(mockJson.getBytes(StandardCharsets.UTF_8));
		when(pair.getConnection().getInputStream()).thenReturn(inputStream);
		
		dicomStudyService = Context.getService(DicomStudyService.class);
		dicomStudyService.setHttpClient(pair.getClient()); // inject mocked client if controller delegates
		
		// Call controller
		String viewValid = controller.syncStudy(redirectAttributes, 1, "all", testPatient);
		
		// Assertions
		assertEquals("redirect:/imaging/syncStudies.page", viewValid);
		assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
		
		String message = (String) redirectAttributes.getAttribute("message");
		assertNotNull(message);
		assertTrue(message.contains("Studies successfully fetched"));
	}
	
	@Test
	public void syncStudy_mixedServers_shouldHandleServerInvalid() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		redirectAttributes = new RedirectAttributesModelMap(); // resets
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(500, // simulate server error
		    "POST", "/instances", "Server error", config);
		HttpURLConnection mockConnection = pair.getConnection();
		doReturn(new ByteArrayOutputStream()).when(mockConnection).getOutputStream();
		dicomStudyService = Context.getService(DicomStudyService.class);
		dicomStudyService.setHttpClient(pair.getClient());
		
		MultipartFile file = new MockMultipartFile("file", "dummy.dcm", "application/dicom", "dummy data".getBytes());
		String redirectUrl = controller.uploadStudy(redirectAttributes, null, config.getId(), new MultipartFile[] { file },
		    testPatient);
		
		assertEquals("redirect:/imaging/studies.page", redirectUrl);
		assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
		assertTrue(((String) Objects.requireNonNull(redirectAttributes.getAttribute("message")))
		        .contains("Some files could not be uploaded"));
	}
	
	@Test
	public void deleteStudy_withPrivilege_shouldDeny() {
		User user = Context.getUserService().getUser(1);
		assertNotNull(user);
		
		boolean hasPrivilege = user.hasPrivilege(ImagingConstants.PRIVILEGE_MODIFY_IMAGE_DATA);
		assertTrue(hasPrivilege);
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		dicomStudyService = Context.getService(DicomStudyService.class);
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		String result = controller.deleteStudy(redirectAttributes, study.getId(), testPatient, "openmrs");
		
		assertEquals("redirect:/imaging/studies.page", result);
		assertEquals("Study successfully deleted", redirectAttributes.getAttribute("message"));
		assertEquals(testPatient.getId().toString(), redirectAttributes.getAttribute("patientId"));
	}
	
	//	@Test
	//	public void deleteStudy_withoutPrivilege_shouldDeny() throws Exception {
	// TODO: Need to find method how to update super user.
	//		User user = Context.getUserService().getUser(3);
	//		assertNotNull(user);
	
	//		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
	//
	//		// Get a study and patient from your service
	//		dicomStudyService = Context.getService(DicomStudyService.class);
	//		DicomStudy study = dicomStudyService.getDicomStudy(2); // study ID from your dataset
	//
	//		// Call the method
	//		String result = controller.deleteStudy(redirectAttributes, study.getId(), testPatient, "openmrs");
	//
	//		// Verify redirect and message
	//		assertEquals("redirect:/imaging/studies.page", result);
	//		assertEquals("Permission denied (you don't have the necessary privileges)",
	//		    redirectAttributes.getAttribute("message"));
	//		assertEquals(testPatient.getId(), redirectAttributes.getAttribute("patientId"));
	//	}
}
