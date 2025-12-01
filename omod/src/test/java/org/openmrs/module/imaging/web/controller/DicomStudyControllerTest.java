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

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.module.imaging.ClientConnectionPair;
import org.openmrs.module.imaging.web.controller.ResponseModel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.openmrs.module.imaging.ClientConnectionPair.setupMockClientWithStatus;

public class DicomStudyControllerTest extends BaseWebControllerTest {
	
	@Mock
	private DicomStudyService dicomStudyService;
	
	private OrthancConfigurationService orthancConfigurationService;
	
	@InjectMocks
	private DicomStudyController controller;
	
	private OrthancConfiguration config;
	
	private DicomStudy study;
	
	private Patient patient;
	
	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		executeDataSet("testDicomStudyDataset.xml");// loads test data
		
		orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		patient = Context.getPatientService().getPatient(1);
		config = orthancConfigurationService.getOrthancConfiguration(1);
		dicomStudyService = Context.getService(DicomStudyService.class);
		study = dicomStudyService.getDicomStudy(1);
	}
	
	@Test
	public void testUseStudiesByPatient_ShouldReturnStudiesForPatient() throws Exception {
		
		// Simulate a GET request
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/studies?patient=" + patient.getUuid());
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.useStudiesByPatient(patient.getUuid(), request, response);
		assertEquals(200, result.getStatusCode().value());
		List<DicomStudyResponse> studyResponses = (List<DicomStudyResponse>) result.getBody();
		assertNotNull(studyResponses);
		assertFalse(studyResponses.isEmpty());
		assertEquals("studyInstanceUID555", studyResponses.get(0).getStudyInstanceUID());
		assertEquals("studyInstanceUID444", studyResponses.get(1).getStudyInstanceUID());
	}
	
	@Test
	public void testUseStudiesByConfig_ShouldReturnStudiesWithScores() throws Exception {
		
		// Simulate a GET request
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/studiesbyconfig?configurationId=" + config.getId()
		        + "&patient=" + patient.getUuid());
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.useStudiesByConfig(config.getId(), patient.getUuid(), request, response);
		
		// Assert
		assertEquals(200, result.getStatusCodeValue());
		assertNotNull(result.getBody());
		
		StudiesWithScoreResponse body = (StudiesWithScoreResponse) result.getBody();
		assertNotNull(body.studies);
		assertEquals(3, body.studies.size());
		assertEquals("studyInstanceUID555", body.studies.get(0).getStudyInstanceUID());
		
		Map<String, Integer> scores = body.scores;
		assertTrue(scores.containsKey("studyInstanceUID555"));
		assertTrue(scores.get("studyInstanceUID555") > 0); // fuzzy score > 0
	}
	
	@Test
	public void testUseStudySeries_ShouldReturnSeriesList() throws Exception {
		
		// fake orthanc response;
		String jsonResponse = "[{" + "\"MainDicomTags\": {" + "\"SeriesInstanceUID\": \"seriesInstanceUID222\","
		        + "\"SeriesDescription\": \"Chest CT\"," + "\"SeriesNumber\": \"2\"," + "\"Modality\": \"CT\","
		        + "\"SeriesDate\": \"20250717\"," + "\"SeriesTime\": \"090000\"" + "}," + "\"ID\": \"series-uid-002\""
		        + "}]";
		
		ClientConnectionPair mockPair = setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST", "/tools/find", "",
		    config);
		
		InputStream responseStream = new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8));
		when(mockPair.getConnection().getInputStream()).thenReturn(responseStream);
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		// Simulate a GET request
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/studyseries?studyId=" + study.getId());
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.useStudySeries(study.getId(), request, response);
		
		List<DicomSeriesResponse> body = (List<DicomSeriesResponse>) result.getBody();
		
		assertEquals(200, result.getStatusCodeValue());
		assertNotNull(body);
		assertEquals("2", body.get(0).getSeriesNumber());
		assertEquals("Chest CT", body.get(0).getSeriesDescription());
		assertEquals("seriesInstanceUID222", body.get(0).getSeriesInstanceUID());
		assertEquals("CT", body.get(0).getModality());
		assertEquals("20250717", body.get(0).getSeriesDate());
		assertEquals("090000", body.get(0).getSeriesTime());
	}
	
	@Test
	public void testUseStudyInstances_ShouldReturnInstanceList() throws Exception {
		
		String jsonResponse = "[\n" + " 	{\n" + "    \"ID\": \"instance1\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SOPInstanceUID\": \"SOPInstanceUID1.2.3.4.1\",\n" + "      \"InstanceNumber\": \"1\",\n"
		        + "      \"ImagePositionPatient\": \"0\\\\0\\\\0\",\n" + "      \"NumberOfFrames\": \"1\"\n" + "  	}\n"
		        + "  },\n" + "  {\n" + "    \"ID\": \"instance2\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SOPInstanceUID\": \"SOPInstanceUID1.2.3.4.2\",\n" + "      \"InstanceNumber\": \"2\",\n"
		        + "      \"ImagePositionPatient\": \"0\\\\0\\\\1\",\n" + "      \"NumberOfFrames\": \"1\"\n" + "    }\n"
		        + "  }\n" + "]";
		
		ClientConnectionPair mockPair = setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST", "/tools/find", "",
		    config);
		
		when(mockPair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));
		doNothing().when(mockPair.getClient()).sendOrthancQuery(any(HttpURLConnection.class), anyString());
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		// Simulate a GET request
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/studyinstances?studyId=" + study.getId()
		        + "&seriesInstanceUID=seriesInstanceUID222");
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.useStudyInstances(study.getId(), "seriesInstanceUID222", request,
		    response);
		
		assertEquals(200, result.getStatusCodeValue());
		List<DicomInstanceResponse> body = (List<DicomInstanceResponse>) result.getBody();
		assertNotNull(body);
		assertEquals(2, body.size());
		assertEquals("SOPInstanceUID1.2.3.4.1", body.get(0).getSopInstanceUID());
		assertEquals("1", body.get(0).getInstanceNumber());
		assertEquals("SOPInstanceUID1.2.3.4.2", body.get(1).getSopInstanceUID());
		assertEquals("2", body.get(1).getInstanceNumber());
	}
	
	@Test
	public void testUseOrthancConfigurations_ShouldReturnConfigurations() throws Exception {
		
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/configurations");
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.useOrthancConfigurations(request, response);
		
		assertEquals(200, result.getStatusCodeValue());
		List<OrthancConfigurationResponse> body = (List<OrthancConfigurationResponse>) result.getBody();
		
		assertNotNull(body);
		assertEquals(2, body.size());
		assertEquals("http://localhost:8052", body.get(0).getOrthancBaseUrl());
		assertEquals("http://localhost:8062", body.get(1).getOrthancBaseUrl());
	}
	
	@Test
	public void testUploadStudies_ShouldUploadFileAndReturnOk() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "test.dcm", "application/dicom",
		        "dummy dicom content".getBytes());
		
		ClientConnectionPair pair = setupMockClientWithStatus(200, "POST", "/instances", "", config);
		HttpURLConnection mockConnection = pair.getConnection();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(mockConnection.getOutputStream()).thenReturn(outputStream);
		
		dicomStudyService.setHttpClient(pair.getClient()); // Make sure this sets the client
		
		MockHttpServletRequest request = newPostRequest("/rest/v1/imaging/instances", file, config.getId());
		;
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.uploadStudies(file, config.getId(), request, response);
		assertEquals(200, result.getStatusCodeValue());
	}
	
	@Test
	public void getLinkStudies_shouldCallFetchAllStudies_WhenFetchOptionIsAll() throws Exception {
		String jsonStudies = "[{\n" + "  \"ID\": \"OrthanUID123\",\n" + "  \"MainDicomTags\": {\n"
		        + "    \"StudyInstanceUID\": \"testStudyUID123\",\n" + "    \"StudyDate\": \"20250701\",\n"
		        + "    \"StudyTime\": \"123456\",\n"
		        + "    \"StudyDescription\": \"Test new or update study description\"\n" + "  },\n"
		        + "  \"PatientMainDicomTags\": {\n" + "    \"PatientName\": \"TestOrthancPatient\",\n"
		        + "    \"Gender\": \"M\"\n" + "  }\n" + "}]";
		
		ClientConnectionPair pair = setupMockClientWithStatus(200, "POST", "/tools/find", "", config);
		doNothing().when(pair.getClient()).sendOrthancQuery(any(), anyString());
		when(pair.getConnection().getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
		when(pair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(jsonStudies.getBytes(StandardCharsets.UTF_8)));
		dicomStudyService.setHttpClient(pair.getClient());
		
		String jsonRequestContent = "{ \"configurationId\": 1, \"fetchOption\": \"all\" }";
		MockHttpServletRequest request = newPostRequest("/rest/v1/imaging/linkstudies?configurationId=" + config.getId()
		        + "&fetchOption=all", jsonRequestContent);
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.getLinkStudies(config.getId(), "all", request, response);
		assertEquals(200, result.getStatusCodeValue());
	}
	
	@Test
	public void testAssignStudy_ShouldAssignStudyToPatient() {
		Patient assignedPatient = Context.getPatientService().getPatient(2);
		String assignPatientUuid = assignedPatient.getUuid();
		String jsonRequestContent = "{" + "\"studyId\": 1," + "\"patient\": \"" + assignPatientUuid + "\","
		        + "\"isAssign\": true" + "}";
		MockHttpServletRequest request = newPostRequest("/rest/v1/imaging/assingstudy?studyId=" + study.getId()
		        + "&patient=" + assignedPatient.getUuid() + "&isAssign=true", jsonRequestContent);
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.assignStudy(study.getId(), assignedPatient.getUuid(), true, request,
		    response);
		assertEquals(200, result.getStatusCodeValue());
		assertEquals(assignedPatient, study.getMrsPatient());
		assertEquals(assignPatientUuid, study.getMrsPatient().getUuid());
	}
	
	@Test
	public void testAssignStudy_ShouldUnAssignStudyToPatient() {
		String assignPatientUuid = patient.getUuid();
		String jsonRequestContent = "{" + "\"studyId\": 1," + "\"patient\": \"" + assignPatientUuid + "\","
		        + "\"isAssign\": true" + "}";
		MockHttpServletRequest request = newPostRequest("/rest/v1/imaging/assingstudy?studyId=" + study.getId()
		        + "&patient=" + patient.getUuid() + "&isAssign=true", jsonRequestContent);
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.assignStudy(study.getId(), patient.getUuid(), false, request, response);
		assertEquals(200, result.getStatusCodeValue());
		assertNull(study.getMrsPatient());
	}
	
	@Test
	public void testDeleteStudy_ShouldDeleteFromOpenmrs() throws Exception {
		DicomStudy studyBefore = dicomStudyService.getDicomStudy(3);
		assertNotNull(studyBefore);
		
		MockHttpServletRequest request = newDeleteRequest("/rest/v1/imaging/study",
		    new Parameter("studyId", String.valueOf(studyBefore.getId())), new Parameter("deleteOption", "openmrs"));
		
		MockHttpServletResponse response = new MockHttpServletResponse();
		ResponseEntity<Object> result = controller.deleteStudy(studyBefore.getId(), "openmrs", request, response);
		
		assertEquals(200, result.getStatusCodeValue());
		DicomStudy studyAfter = dicomStudyService.getDicomStudy(3);
		assertNull(studyAfter);
	}
	
	@Test
	public void testDeleteStudy_ShouldDeleteFromOrthanc() throws Exception {
		DicomStudy studyBefore = dicomStudyService.getDicomStudy(3);
		assertNotNull(studyBefore);
		
		ClientConnectionPair pair = setupMockClientWithStatus(200, "DELETE", "/studies/" + studyBefore.getOrthancStudyUID(),
		    "", config);
		doNothing().when(pair.getClient()).sendOrthancQuery(any(), anyString());
		when(pair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
		dicomStudyService.setHttpClient(pair.getClient());
		
		MockHttpServletRequest request = newDeleteRequest("/rest/v1/imaging/study",
		    new Parameter("studyId", String.valueOf(studyBefore.getId())), new Parameter("deleteOption", "openmrsOrthanc"));
		
		MockHttpServletResponse response = new MockHttpServletResponse();
		ResponseEntity<Object> result = controller.deleteStudy(studyBefore.getId(), "openmrsOrthanc", request, response);
		
		assertEquals(200, result.getStatusCodeValue());
		DicomStudy studyAfter = dicomStudyService.getDicomStudy(3);
		assertNull(studyAfter);
	}
	
	@Test
	public void testDeleteSeries_ShouldDeleteFromOrthanc() throws Exception {
		DicomStudy studyBefore = dicomStudyService.getDicomStudy(3);
		assertNotNull(studyBefore);
		
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(200, "DELETE", "/series/SERIES-UID-123", // replace with actual series UID
		    "", config);
		doNothing().when(pair.getClient()).sendOrthancQuery(any(HttpURLConnection.class), any());
		when(pair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
		dicomStudyService.setHttpClient(pair.getClient());
		
		MockHttpServletRequest request = newDeleteRequest("/rest/v1/imaging/series",
		    new Parameter("studyId", String.valueOf(studyBefore.getId())), new Parameter("orthancSeriesUID",
		            "SERIES-UID-123"));
		
		MockHttpServletResponse response = new MockHttpServletResponse();
		ResponseEntity<Object> result = controller.deleteSeries("SERIES-UID-123", studyBefore.getId(), request, response);
		assertEquals(200, result.getStatusCodeValue());
	}
	
	@Test
	public void testPreviewInstance_ShouldReturnPreviewData() throws Exception {
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(200, "GET",
		    "/instances/instance1/preview", "", config);
		
		byte[] fakeImageBytes = "fakeImageData".getBytes(StandardCharsets.UTF_8);
		when(mockPair.getConnection().getInputStream()).thenReturn(new ByteArrayInputStream(fakeImageBytes));
		when(mockPair.getConnection().getContentType()).thenReturn("image/jpeg");
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		String orthanceInstanceUID = "instance1";
		
		ResponseEntity<?> result = controller.previewInstance(orthanceInstanceUID, study.getId());
		
		assertNotNull(result);
		assertEquals(200, result.getStatusCodeValue());
		assertArrayEquals(fakeImageBytes, (byte[]) result.getBody());
		assertEquals("image/jpeg", result.getHeaders().get("Content-type").get(0));
		
	}
}
