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

package org.openmrs.module.imaging.api;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.impl.DicomStudyServiceImpl;
import org.openmrs.module.imaging.api.study.DicomInstance;
import org.openmrs.module.imaging.api.study.DicomSeries;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.openmrs.module.imaging.api.ClientConnectionPair.setupMockClientWithStatus;

public class DicomStudyServiceTest extends BaseModuleContextSensitiveTest {
	
	private static final String DICOMSTUDY_TEST_DATASET = "testDicomStudyDataset.xml";
	
	private DicomStudyService dicomStudyService;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@Mock
	private DicomStudyDao dicomStudyDao;
	
	@Before
	public void setUp() throws Exception {
		if (dicomStudyService == null) {
			dicomStudyService = Context.getService(DicomStudyService.class);
		}
		dicomStudyDao = Context.getRegisteredComponent("imaging.DicomStudyDao", DicomStudyDao.class);
		executeDataSet(DICOMSTUDY_TEST_DATASET);
	}
	
	@Test
	public void testGetStudiesOfPatient_shouldGetAllStudyByPatient() throws Exception {
		Patient patientWithStudies = Context.getPatientService().getPatient(1);
		List<DicomStudy> studies = dicomStudyService.getStudiesOfPatient(patientWithStudies);
		assertNotNull(studies);
		assertEquals(2, studies.size());
		for (DicomStudy study : studies) {
			assertEquals(patientWithStudies, study.getMrsPatient());
		}
		
		Patient patientWithoutStudies = Context.getPatientService().getPatient(2);
		List<DicomStudy> noStudies = dicomStudyService.getStudiesOfPatient(patientWithoutStudies);
		assertNotNull(noStudies);
		assertEquals(0, noStudies.size());
	}
	
	@Test
	public void getAllStudies_shouldReturnAllStudiesFromDb() {
		List<DicomStudy> studies = dicomStudyService.getAllStudies();
		
		assertNotNull(studies);
		assertFalse(studies.isEmpty());
		assertEquals(2, studies.size());
		
		DicomStudy firstStudy = studies.get(0);
		assertEquals("studyInstanceUID555", firstStudy.getStudyInstanceUID());
	}
	
	@Test
	public void testFetchAllStudies_shouldThrowIOException_whenHttpStatusNotOk() throws Exception {

		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);

		ClientConnectionPair pair = setupMockClientWithStatus(500, "POST", "/tools/find", "Internal Server Error", config);

		DicomStudyServiceImpl service = new DicomStudyServiceImpl();
		service.setHttpClient(pair.getClient());

		IOException thrown = assertThrows(IOException.class, () -> service.fetchAllStudies(config));
		assertTrue(thrown.getMessage().contains("Request to Orthanc server " + config.getOrthancBaseUrl() + " failed with error"));
	}
	
	@Test
	public void testFetchAllStudies_successfulResponseCallsCreateOrUpdate() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		ClientConnectionPair pair = setupMockClientWithStatus(200, "POST", "/tools/find", "", config);
		doNothing().when(pair.getClient()).sendOrthancQuery(any(), anyString());
		when(pair.getConnection().getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
		
		String mockJson = "[{" + "\"ID\": \"orthanc123\", " + "\"MainDicomTags\": {\"StudyInstanceUID\": \"abc\"}, "
		        + "\"PatientMainDicomTags\": {\"PatientName\": \"John Doe\", \"Gender\": \"M\"}" + "}]";
		
		InputStream inputStream = new ByteArrayInputStream(mockJson.getBytes());
		when(pair.getConnection().getInputStream()).thenReturn(inputStream);
		
		DicomStudyServiceImpl serverImpl = new DicomStudyServiceImpl();
		DicomStudyServiceImpl spyService = Mockito.spy(serverImpl);
		spyService.setHttpClient(pair.getClient());
		doNothing().when(spyService).createOrUpdateStudy(any(), any());
		
		spyService.fetchAllStudies(config);
		verify(spyService, times(1)).createOrUpdateStudy(eq(config), any());
	}
	
	@Test
	public void testFetchAllStudies_nonOkResponseThrowException() throws IOException {
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://localhost:8042");
		config.setOrthancUsername("user");
		config.setOrthancPassword("pass");

		ClientConnectionPair pair = setupMockClientWithStatus(
				HttpURLConnection.HTTP_UNAVAILABLE,
				"POST",
				"/tools/find",
				"Service Unavailable",
				config
		);

		DicomStudyServiceImpl dicomStudyService = new DicomStudyServiceImpl();
		dicomStudyService.setHttpClient(pair.getClient());

		// Now call and assert
		IOException ex = assertThrows(IOException.class, () ->
				dicomStudyService.fetchAllStudies(config)
		);
		assertTrue(ex.getMessage().contains("Request to Orthanc server " + config.getOrthancBaseUrl() + " failed with error"));
	}
	
	@Test
	public void testUpload_success() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		byte[] data = "dummy DICOM data".getBytes();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
		
		ClientConnectionPair pair = setupMockClientWithStatus(200, "POST", "/instances", "", config);
		HttpURLConnection mockConnection = pair.getConnection();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(mockConnection.getOutputStream()).thenReturn(outputStream);
		
		// Inject mock client into service
		dicomStudyService.setHttpClient(pair.getClient()); // Make sure this sets the client
		
		int result = dicomStudyService.uploadFile(config, inputStream);
		
		assertEquals(200, result);
		
		String writtenData = outputStream.toString();
		assertTrue(writtenData.contains("dummy DICOM data"));
		
		// Verifications
		verify(mockConnection).setRequestProperty("Content-Type", "application/dicom");
		verify(mockConnection).setDoOutput(true);
	}
	
	@Test
	public void testCreateOrUpdateStudy_createsNewStudyWhenNoneExists() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		String jsonString = "{\n" + "  \"ID\": \"OrthanUID123\",\n" + "  \"MainDicomTags\": {\n"
		        + "    \"StudyInstanceUID\": \"testStudyUID123\",\n" + "    \"StudyDate\": \"20250701\",\n"
		        + "    \"StudyTime\": \"123456\",\n"
		        + "    \"StudyDescription\": \"Test new or update study description\"\n" + "  },\n"
		        + "  \"PatientMainDicomTags\": {\n" + "    \"PatientName\": \"TestOrthancPatient\",\n"
		        + "    \"Gender\": \"M\"\n" + "  }\n" + "}";
		
		JsonNode jsonData = objectMapper.readTree(jsonString);
		
		DicomStudy foundStudy = dicomStudyDao.getByStudyInstanceUID(config, "testStudyUID123");
		assertNull(foundStudy);
		
		dicomStudyService.createOrUpdateStudy(config, jsonData);
		
		DicomStudy savedStudy = dicomStudyDao.getByStudyInstanceUID(config, "testStudyUID123");
		assertNotNull(savedStudy);
		assertEquals("OrthanUID123", savedStudy.getOrthancStudyUID());
		assertEquals(0, savedStudy.getLinkStatus());
		assertEquals("TestOrthancPatient", savedStudy.getPatientName());
		assertEquals("Test new or update study description", savedStudy.getStudyDescription());
	}
	
	@Test
	public void testCreateOrUpdateStudy_updatesExistingStudy() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		Patient patient = Context.getPatientService().getPatient(1);
		
		String jsonString = "{\n" + "  \"ID\": \"orthancUID123\",\n" + "  \"MainDicomTags\": {\n"
		        + "    \"StudyInstanceUID\": \"studyInstanceUID444\",\n" + "    \"StudyDate\": \"2025-07-11\",\n"
		        + "    \"StudyTime\": \"14:35:00\",\n" + "    \"StudyDescription\": \"CT Head without contrast\"\n"
		        + "  },\n" + "  \"PatientMainDicomTags\": {\n" + "    \"PatientName\": \"Test Imaging\",\n"
		        + "    \"Gender\": \"F\"\n" + "  }\n" + "}";
		JsonNode studyData = objectMapper.readTree(jsonString);
		
		DicomStudy existingStudy = new DicomStudy("studyInstanceUID444", "orthancUID444", 0, 60,
		        "{\"differences\":[], \"score\": 0}", patient, config, "Test Imaging", "2025-07-11", "14:35:00",
		        "CT Head without contrast", "F");
		
		DicomStudy foundStudy = dicomStudyDao.getByStudyInstanceUID(config, existingStudy.getStudyInstanceUID());
		assertNotNull(foundStudy);
		assertEquals("orthancUID444", existingStudy.getOrthancStudyUID());
		
		dicomStudyService.createOrUpdateStudy(config, studyData);
		DicomStudy updatedStudy = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID444");
		assertNotNull(updatedStudy);
		
		DicomStudy foundUpdateStudy = dicomStudyDao.getByPatient(patient).get(1);
		assertEquals("orthancUID123", foundUpdateStudy.getOrthancStudyUID());
		assertEquals(0, foundUpdateStudy.getLinkStatus());
		assertEquals("studyInstanceUID444", foundUpdateStudy.getStudyInstanceUID());
	}
	
	@Test
	public void fetchNewChangedStudiesByConfiguration_shouldProcessStudiesCorrectly() throws Exception {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		assertNotNull(config);
		
		// Prepare mock response JSON
		String mockJson = "{\n" + "  \"Changes\": [\n" + "    {\"ChangeType\": \"NewStudy\", \"ID\": \"mock-study-1\"},\n"
		        + "    {\"ChangeType\": \"StableStudy\", \"ID\": \"mock-study-2\"}\n" + "  ],\n" + "  \"Last\": 100,\n"
		        + "  \"Done\": \"true\"\n" + "}";
		
		// Setup mocked HTTP client + connection
		String expectedPath = "/changes?limit=1000";
		ClientConnectionPair pair = setupMockClientWithStatus(200, "GET", expectedPath, mockJson, config);
		when(pair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(mockJson.getBytes(StandardCharsets.UTF_8)));
		
		// Spy on service to stub downstream call
		DicomStudyServiceImpl realService = new DicomStudyServiceImpl();
		DicomStudyServiceImpl spyService = Mockito.spy(realService);
		spyService.setHttpClient(pair.getClient());
		
		doNothing().when(spyService).fetchNewChangedStudiesByConfigurationAndStudyUIDs(eq(config),
		    eq(Arrays.asList("mock-study-1", "mock-study-2")));
		
		spyService.fetchNewChangedStudiesByConfiguration(config);
		assertEquals(100, config.getLastChangedIndex().intValue());
		
		verify(spyService).fetchNewChangedStudiesByConfigurationAndStudyUIDs(eq(config),
		    eq(Arrays.asList("mock-study-1", "mock-study-2")));
	}
	
	@Test
	public void testFetchNewChangedStudiesByConfigurationAndStudyUIDs_success() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		assertNotNull("Config should not be null", config);
		
		String studyInstanceUID = "study1";
		String jsonResponse = "{ \"StudyInstanceUID\": \"" + studyInstanceUID + "\", \"PatientName\": \"John Doe\" }";
		
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(200, "GET", "/studies/"
		        + studyInstanceUID, "", config);
		
		when(pair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));
		
		DicomStudyServiceImpl realService = new DicomStudyServiceImpl();
		DicomStudyServiceImpl spyService = Mockito.spy(realService);
		spyService.setHttpClient(pair.getClient());
		
		doNothing().when(spyService).createOrUpdateStudy(eq(config), any());
		spyService.fetchNewChangedStudiesByConfigurationAndStudyUIDs(config, Collections.singletonList(studyInstanceUID));
		verify(spyService, times(1)).createOrUpdateStudy(eq(config), any());
	}
	
	@Test
	public void testGetStudiesByConfiguration_shouldReturnStudiesByConfiguration() {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config1 = orthancConfigurationService.getOrthancConfiguration(1);
		
		List<DicomStudy> studies1 = dicomStudyService.getStudiesByConfiguration(config1);
		assertNotNull(studies1);
		
		OrthancConfiguration config2 = orthancConfigurationService.getOrthancConfiguration(2);
		List<DicomStudy> studies2 = dicomStudyService.getStudiesByConfiguration(config2);
		assertTrue(studies2.isEmpty());
	}
	
	@Test
	public void testSetPatient_shouldPatientSignedToStudy() {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		Patient patient = Context.getPatientService().getPatient(2);
		DicomStudy study = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID555");
		
		dicomStudyService.setPatient(study, patient);
		assertNotNull(study);
		assertNotNull(study.getMrsPatient());
		assertEquals(2, study.getMrsPatient().getPatientId().intValue());
	}
	
	@Test
	public void testDeleteStudy_shouldThrowIOExceptionAndNotRemoveStudy_onHttpError() throws Exception {
		DicomStudy study = dicomStudyDao.get(1);
		OrthancConfiguration config = study.getOrthancConfiguration();
		
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(401, "DELETE",
		    "/studies/" + study.getStudyInstanceUID(), "Study Not found", config);
		
		DicomStudyServiceImpl test = new DicomStudyServiceImpl();
		test.setHttpClient(pair.getClient());
		
		try {
			test.deleteStudy(study);
			fail("Expected IOException to be thrown due to HTTP error");
		}
		catch (IOException e) {
			assertTrue(e.getMessage().contains("Failed to create HTTP connection"));
		}
		assertNotNull("Study should NOT be removed from DB on HTTP error", dicomStudyDao.get(study.getId()));
	}
	
	@Test
	public void testDeleteStudy_shouldRemoveStudyFromDb() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		DicomStudy study = dicomStudyDao.get(1);
		ClientConnectionPair pair = ClientConnectionPair.setupMockClientWithStatus(200, "DELETE",
		    "/studies/" + study.getOrthancStudyUID(), "", config);
		when(pair.getConnection().getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
		
		dicomStudyService.setHttpClient(pair.getClient());
		dicomStudyService.deleteStudy(study);
		
		DicomStudy deletedStudy = dicomStudyDao.get(study.getId());
		assertNull("DicomStudy should be deleted from DB", deletedStudy);
	}
	
	@Test
	public void testDeleteStudyFromOpenmrs_shouldStudyDeleteFromOpenMRSDbNoFound() {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		DicomStudy study = dicomStudyDao.get(1);
		dicomStudyService.deleteStudyFromOpenmrs(study);
		
		DicomStudy deletedStudy = dicomStudyDao.getByStudyInstanceUID(config, study.getStudyInstanceUID());
		assertNull(deletedStudy);
	}
	
	@Test
	public void fetchSeries_shouldReturnDicomSeriesListWhenOrthancReturnsValidData() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		String jsonResponse = "[{" + "\"MainDicomTags\": {" + "\"SeriesInstanceUID\": \"testSeriesUID123\","
		        + "\"SeriesDescription\": \"Test Series\"," + "\"SeriesNumber\": \"1\"," + "\"Modality\": \"CT\","
		        + "\"SeriesDate\": \"20250717\"," + "\"SeriesTime\": \"123000\"" + "}," + "\"ID\": \"abcd1\"" + "}]";
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		InputStream responseStream = new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8));
		when(mockPair.getConnection().getInputStream()).thenReturn(responseStream);
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		List<DicomSeries> result = dicomStudyService.fetchSeries(study);
		assertNotNull(result);
		assertEquals(1, result.size());
		DicomSeries series = result.get(0);
		assertEquals("testSeriesUID123", series.getSeriesInstanceUID());
		assertEquals("abcd1", series.getOrthancSeriesUID());
		assertEquals("Test Series", series.getSeriesDescription());
		assertEquals("1", series.getSeriesNumber());
		assertEquals("CT", series.getModality());
		assertEquals("20250717", series.getSeriesDate());
		assertEquals("123000", series.getSeriesTime());
	}
	
	@Test
	public void testFetchSeries_shouldReturnSeriesOfStudy() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		//Mock Json response from Orthanc for series
		String jsonResponse = "[\n" + "  {\n" + "    \"ID\": \"series1\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SeriesInstanceUID\": \"SeriesUID.1.2.3.1\",\n" + "      \"SeriesDescription\": \"Head MRI\",\n"
		        + "      \"SeriesNumber\": \"1\",\n" + "      \"Modality\": \"MR\",\n"
		        + "      \"SeriesDate\": \"20250101\",\n" + "      \"SeriesTime\": \"120000\"\n" + "    }\n" + "  },\n"
		        + "  {\n" + "    \"ID\": \"series2\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SeriesInstanceUID\": \"SeriesUID.1.2.3.2\",\n" + "      \"SeriesDescription\": \"Brain CT\",\n"
		        + "      \"SeriesNumber\": \"2\",\n" + "      \"Modality\": \"CT\",\n"
		        + "      \"SeriesDate\": \"20250101\",\n" + "      \"SeriesTime\": \"121000\"\n" + "    }\n" + "  }\n" + "]";
		
		when(mockPair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));
		
		doNothing().when(mockPair.getClient()).sendOrthancQuery(any(HttpURLConnection.class), anyString());
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		List<DicomSeries> series = dicomStudyService.fetchSeries(study);
		
		assertNotNull(series);
		assertEquals("SeriesUID.1.2.3.1", series.get(0).getSeriesInstanceUID());
		assertEquals("series1", series.get(0).getOrthancSeriesUID());
		assertEquals("Head MRI", series.get(0).getSeriesDescription());
		assertEquals("MR", series.get(0).getModality());
		
		assertEquals("SeriesUID.1.2.3.2", series.get(1).getSeriesInstanceUID());
		assertEquals("series2", series.get(1).getOrthancSeriesUID());
		assertEquals("Brain CT", series.get(1).getSeriesDescription());
		assertEquals("CT", series.get(1).getModality());
		
		// Verify that Orthanc query was sent
		verify(mockPair.getClient()).sendOrthancQuery(eq(mockPair.getConnection()), contains("\"Level\": \"Series\""));
	}
	
	@Test
	public void testFetchInstances_schouldReturnInstancesOfSeries() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		String jsonResponse = "[\n" + " 	{\n" + "    \"ID\": \"instance1\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SOPInstanceUID\": \"SOPInstanceUID1.2.3.4.1\",\n" + "      \"InstanceNumber\": \"1\",\n"
		        + "      \"ImagePositionPatient\": \"0\\\\0\\\\0\",\n" + "      \"NumberOfFrames\": \"1\"\n" + "  	}\n"
		        + "  },\n" + "  {\n" + "    \"ID\": \"instance2\",\n" + "    \"MainDicomTags\": {\n"
		        + "      \"SOPInstanceUID\": \"SOPInstanceUID1.2.3.4.2\",\n" + "      \"InstanceNumber\": \"2\",\n"
		        + "      \"ImagePositionPatient\": \"0\\\\0\\\\1\",\n" + "      \"NumberOfFrames\": \"1\"\n" + "    }\n"
		        + "  }\n" + "]";
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		when(mockPair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));
		
		doNothing().when(mockPair.getClient()).sendOrthancQuery(any(HttpURLConnection.class), anyString());
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		List<DicomInstance> instances = dicomStudyService.fetchInstances("seriesUID1.2.3.1", study);
		
		assertNotNull(instances);
		assertEquals(2, instances.size());
		
		assertEquals("instance1", instances.get(0).getOrthancInstanceUID());
		assertEquals("SOPInstanceUID1.2.3.4.1", instances.get(0).getSopInstanceUID());
		assertEquals("1", instances.get(0).getInstanceNumber());
		
		assertEquals("instance2", instances.get(1).getOrthancInstanceUID());
		assertEquals("SOPInstanceUID1.2.3.4.2", instances.get(1).getSopInstanceUID());
		assertEquals("2", instances.get(1).getInstanceNumber());
		
		// Verify that sendOrthancQuery was called with the correct JSON query string
		verify(mockPair.getClient()).sendOrthancQuery(eq(mockPair.getConnection()),
		    contains("\"SeriesInstanceUID\":\"seriesUID1.2.3.1\""));
	}
	
	@Test
	public void fetchInstances_shouldReturnEmptyListWhenOrthancReturnsNoInstances() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "POST",
		    "/tools/find", "", config);
		
		when(mockPair.getConnection().getInputStream()).thenReturn(
		    new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)));
		
		doNothing().when(mockPair.getClient()).sendOrthancQuery(any(HttpURLConnection.class), anyString());
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		List<DicomInstance> instances = dicomStudyService.fetchInstances("seriesInstanceUID1.2.3.1", study);
		
		assertNotNull(instances);
		assertTrue(instances.isEmpty());
	}
	
	@Test
	public void testFetchInstancePreview_shouldReturnPreviewBytesAndContentType() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		ClientConnectionPair mockPair = ClientConnectionPair.setupMockClientWithStatus(HttpURLConnection.HTTP_OK, "GET",
		    "/instances/instance1/preview", "", config);
		
		byte[] fakeImageBytes = "fakeImageData".getBytes(StandardCharsets.UTF_8);
		when(mockPair.getConnection().getInputStream()).thenReturn(new ByteArrayInputStream(fakeImageBytes));
		
		when(mockPair.getConnection().getInputStream()).thenReturn(new ByteArrayInputStream(fakeImageBytes));
		when(mockPair.getConnection().getContentType()).thenReturn("image/jpeg");
		
		dicomStudyService.setHttpClient(mockPair.getClient());
		DicomStudy study = dicomStudyService.getDicomStudy(1);
		
		DicomStudyService.PreviewResult result = dicomStudyService.fetchInstancePreview("instance1", study);
		
		assertNotNull(result);
		assertArrayEquals(fakeImageBytes, result.data);
		assertEquals("image/jpeg", result.contentType);
		
		verify(mockPair.getClient()).createConnection(eq("GET"), eq(config.getOrthancBaseUrl()),
		    eq("/instances/instance1/preview"), eq(config.getOrthancUsername()), eq(config.getOrthancPassword()));
	}
}
