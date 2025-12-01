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

package org.openmrs.module.imaging.api.dao;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;
import static org.junit.Assert.*;

/**
 * Tests methods in {@link RequestProcedureDao}
 */
public class RequestProcedureDAOTest extends BaseModuleContextSensitiveTest {
	
	private RequestProcedureDao requestProcedureDao;
	
	private static final String REQUEST_PROCEDURE_TEST_DATASET = "testRequestProcedureDataset.xml";
	
	private RequestProcedure requestProcedure;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(REQUEST_PROCEDURE_TEST_DATASET);
		assertNotNull(applicationContext);
		requestProcedureDao = (RequestProcedureDao) applicationContext.getBean("imaging.RequestProcedureDao");
	}
	
	@Test
	public void testGetAll_shouldReturnDataFromDB() {
		List<RequestProcedure> allRequests = requestProcedureDao.getAll();
		assertNotNull(allRequests);
		assertEquals(3, allRequests.size());
	}
	
	@Test
	public void testGetAllByStudyInstanceUID_shouldReturnMatchingRecords() {
		String studyInstanceUID = "testInstanceUID888";
		
		List<RequestProcedure> results = requestProcedureDao.getAllByStudyInstanceUID(studyInstanceUID);
		
		assertNotNull(results);
		assertFalse(results.isEmpty());
		
		// Verify all results have the requested studyInstanceUID
		for (RequestProcedure rp : results) {
			assertEquals(studyInstanceUID, rp.getStudyInstanceUID());
		}
	}
	
	@Test
	public void testGetByPatient_shouldReturnCorrectRequests() {
		Patient patient = Context.getPatientService().getPatient(1); // assuming patient ID 1 exists in test dataset
		List<RequestProcedure> requests = requestProcedureDao.getByPatient(patient);
		assertEquals(3, requests.size());
		for (RequestProcedure rp : requests) {
			assertEquals(patient.getPatientId(), rp.getMrsPatient().getPatientId());
		}
	}
	
	@Test
	public void testGetByAccessNumber_shouldReturnRequestProcedure() {
		String accessNumber = "ACC1001";
		RequestProcedure result = requestProcedureDao.getByAccessionNumber(accessNumber);
		assertNotNull(result);
		assertEquals(accessNumber, result.getAccessionNumber());
	}
	
	@Test
	public void testGetByConfig_shouldReturnRequestProcedure() {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		
		List<RequestProcedure> requestProcedureList = requestProcedureDao.getRequestProcedureByConfig(config);
		assertNotNull(requestProcedureList);
		assertEquals(3, requestProcedureList.size());
	}
	
	@Test
	public void testSave_shouldPersistRequestProcedure() {
		Patient patient = Context.getPatientService().getPatient(1);
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		RequestProcedure requestProcedure = new RequestProcedure();
		requestProcedure.setId(3);
		requestProcedure.setRequestingPhysician("test_physician");
		requestProcedure.setMrsPatient(patient);
		requestProcedure.setPriority("High");
		requestProcedure.setStudyInstanceUID("StudyUID222");
		requestProcedure.setStatus("scheduled");
		requestProcedure.setOrthancConfiguration(config);
		requestProcedure.setAccessionNumber("ACC222");
		requestProcedure.setRequestDescription("Test request procedure description");
		
		requestProcedureDao.save(requestProcedure);
		
		RequestProcedure newRequestProcedure = requestProcedureDao.getByAccessionNumber("ACC222");
		assertNotNull(newRequestProcedure);
		assertEquals("ACC222", newRequestProcedure.getAccessionNumber());
		
		List<RequestProcedure> allRequests = requestProcedureDao.getAll();
		assertNotNull(allRequests);
		assertEquals(4, allRequests.size());
	}
	
	@Test
	public void testUpdate_shouldModifyExistingRequestProcedure() {
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		
		requestProcedure.setRequestDescription("Update request procedure description");
		requestProcedure.setStudyInstanceUID("testInstanceUID444");
		
		Patient patient = Context.getPatientService().getPatient(2);
		// update patient
		requestProcedure.setMrsPatient(patient);
		requestProcedureDao.update(requestProcedure);
		
		RequestProcedure updated = requestProcedureDao.getByAccessionNumber("ACC1001");
		assertEquals("testInstanceUID444", updated.getStudyInstanceUID());
		assertTrue(requestProcedure.getRequestDescription().contains("Update request procedure description"));
	}
	
	@Test
	public void testRemove_shouldDeleteRequestProcedure() {
		RequestProcedure result = requestProcedureDao.getByAccessionNumber("ACC1002");
		assertNotNull(result);
		requestProcedureDao.remove(result);
		
		RequestProcedure deleted = requestProcedureDao.getByAccessionNumber("ACC1002");
		assertNull(deleted);
	}
}
