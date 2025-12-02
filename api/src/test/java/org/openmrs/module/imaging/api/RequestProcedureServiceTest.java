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

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class RequestProcedureServiceTest extends BaseModuleContextSensitiveTest {
	
	private static final String REQUEST_PROCEDURE_DATASET = "testRequestProcedureDataset.xml";
	
	private RequestProcedureService requestProcedureService;
	
	@Before
	public void setUp() throws Exception {
		if (requestProcedureService == null) {
			requestProcedureService = Context.getService(RequestProcedureService.class);
		}
		executeDataSet(REQUEST_PROCEDURE_DATASET);
	}
	
	@Test
	public void getAllRequestProcedures_shouldReturnAllProceduresFromDatabase() {
		List<RequestProcedure> requestProcedureList = requestProcedureService.getAllRequestProcedures();
		
		assertNotNull(requestProcedureList);
		assertEquals(3, requestProcedureList.size());
	}
	
	@Test
	public void getAllRequestProceduresByStatus_shouldProceduresFromDatabase() {
		List<RequestProcedure> requestProcedureList = requestProcedureService.getRequestProceduresByStatus("in progress");
		
		assertNotNull(requestProcedureList);
		assertEquals(1, requestProcedureList.size());
	}
	
	@Test
	public void getRequestProcedureByPatient_shouldReturnProceduresForPatient() {
		Patient patient = Context.getPatientService().getPatient(1);
		
		List<RequestProcedure> requestProcedureList = requestProcedureService.getRequestProcedureByPatient(patient);
		assertNotNull(requestProcedureList);
		assertEquals(3, requestProcedureList.size());
		assertEquals(3, requestProcedureList.size());
	}
	
	@Test
	public void getRequestProcedureByPatient_shouldReturnEmptyListIfNoProcedures() {
		Patient patient = Context.getPatientService().getPatient(7); // patient with no entries
		List<RequestProcedure> results = requestProcedureService.getRequestProcedureByPatient(patient);
		assertNotNull(results);
		assertTrue(results.isEmpty());
	}
	
	@Test
	public void getAllByStudyInstanceUID_shouldReturnProceduresForStudy() {
		DicomStudyService studyService = Context.getService(DicomStudyService.class);
		DicomStudy study = studyService.getDicomStudy(1);
		assertNotNull(study);
		assertEquals("testInstanceUID888", study.getStudyInstanceUID());
		
		List<RequestProcedure> procedureList = requestProcedureService.getAllByStudyInstanceUID(study.getStudyInstanceUID());
		assertNotNull(procedureList);
		assertEquals("ACC1001", procedureList.get(0).getAccessionNumber());
		assertEquals("in progress", procedureList.get(0).getStatus());
		assertEquals("testInstanceUID888", procedureList.get(0).getStudyInstanceUID());
	}
	
	@Test
	public void getRequestProcedureByAccessionNumber_shouldReturnCorrectRequestProcedure() {
		String accessNumber = "ACC1001";
		RequestProcedure result = requestProcedureService.getRequestProcedureByAccessionNUmber(accessNumber);
		
		assertNotNull(result);
		assertEquals(accessNumber, result.getAccessionNumber());
		assertEquals("testInstanceUID888", result.getStudyInstanceUID());
		assertEquals("1", result.getMrsPatient().getPatientId().toString());
	}
	
	@Test
	public void getRequestProcedureByAccessionNumber_shouldReturnNullForUnknownNumber() {
		String unknownAccessionNumber = "UNKNOWN";
		RequestProcedure result = requestProcedureService.getRequestProcedureByAccessionNUmber(unknownAccessionNumber);
		assertNull(result);
	}
	
	@Test
	public void deleteRequestProcedure_shouldRemoveRequestProcedureFromDatabase() throws IOException, IOException {
		String accessionNumber = "ACC1001"; // Must exist in test dataset
		RequestProcedure toDelete = requestProcedureService.getRequestProcedureByAccessionNUmber(accessionNumber);
		assertNotNull(toDelete);
		
		// Delete it
		requestProcedureService.deleteRequestProcedure(toDelete);
		// Try to fetch it again - should be null
		RequestProcedure deleted = requestProcedureService.getRequestProcedureByAccessionNUmber(accessionNumber);
		assertNull(deleted);
	}
	
	@Test
	public void newRequest_shouldSaveNewRequestProcedure() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		List<OrthancConfiguration> configs = orthancConfigurationService.getAllOrthancConfigurations();
		OrthancConfiguration config = configs.get(0);
		
		String newAccessionNumber = "NEW_ACC_999";
		RequestProcedure newRequestProcedure = new RequestProcedure();
		newRequestProcedure.setAccessionNumber(newAccessionNumber);
		Patient patient = Context.getPatientService().getPatient(1);
		
		newRequestProcedure.setStudyInstanceUID("New_Study_UID");
		newRequestProcedure.setRequestingPhysician("New_Doctor");
		newRequestProcedure.setOrthancConfiguration(config);
		newRequestProcedure.setMrsPatient(patient);
		newRequestProcedure.setStatus("scheduled");
		newRequestProcedure.setPriority("low");
		newRequestProcedure.setRequestDescription("new test request");
		
		requestProcedureService.newRequest(newRequestProcedure);
		// Retrieve saved entity
		RequestProcedure saved = requestProcedureService.getRequestProcedureByAccessionNUmber(newAccessionNumber);
		assertNotNull(saved);
		assertEquals(newAccessionNumber, saved.getAccessionNumber());
		assertEquals("New_Study_UID", saved.getStudyInstanceUID());
	}
	
	@Test
	public void getRequestProcedureByConfig_shouldReturnMatchingProcedures() {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		List<OrthancConfiguration> configs = orthancConfigurationService.getAllOrthancConfigurations();
		
		List<RequestProcedure> procedures = requestProcedureService.getRequestProcedureByConfig(configs.get(0));
		assertNotNull(procedures);
		assertFalse(procedures.isEmpty());
		
		// Optionally verify that all returned procedures have this config
		for (RequestProcedure procedure : procedures) {
			assertEquals(configs.get(0).getId(), procedure.getOrthancConfiguration().getId());
		}
	}
	
	@Test
	public void updateRequestStatus_shouldModifyStatusInDatabase() {
		String accessionNumber = "ACC1003"; // existing accession number in dataset
		RequestProcedure procedure = requestProcedureService.getRequestProcedureByAccessionNUmber(accessionNumber);
		assertNotNull(procedure);
		
		String newStatus = "complete";
		procedure.setStatus(newStatus);
		
		requestProcedureService.updateRequestStatus(procedure);
		
		RequestProcedure updated = requestProcedureService.getRequestProcedureByAccessionNUmber(accessionNumber);
		assertNotNull(updated);
		assertEquals(newStatus, updated.getStatus());
		assertEquals("testInstanceUID333", updated.getStudyInstanceUID());
	}
	
}
