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

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.module.imaging.api.RequestProcedureStepService;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.module.imaging.web.controller.ResponseModel.ProcedureStepResponse;
import org.openmrs.module.imaging.web.controller.ResponseModel.RequestProcedureResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.Assert.*;

public class RequestProcedureControllerTest extends BaseWebControllerTest {
	
	private static final String REQUEST_PROCEDURE_DATASET = "testRequestProcedureDataset.xml";
	
	@Autowired
	private RequestProcedureController controller;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(REQUEST_PROCEDURE_DATASET);
	}
	
	@Test
    @Transactional
    public void testUseRequestProcedures_shouldReturnOnlyScheduledProcedures() throws Exception {
        RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
        List<RequestProcedure> requestProcedures = requestProcedureService.getRequestProceduresByStatus("scheduled");
        assertNotNull("Request procedures should not be null", requestProcedures);
        assertTrue("Expected all procedures to be scheduled",
                requestProcedures.stream().allMatch(rp -> "scheduled".equalsIgnoreCase(rp.getStatus())));
    }
	
	@Test
	@Transactional
	public void testUpdateRequestStatus_shouldMarkProcedureCompletedIfAllStepsCompleted() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");

		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);

		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);// scheduled procedure
		assertNotNull(requestProcedure);

		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertFalse(steps.isEmpty());

		RequestProcedureStep step = steps.get(0);

        StudyUpdatePayload payload = getStudyUpdatePayload(requestProcedure, step);

        // Convert payload to JSON
        String jsonRequestContent = new ObjectMapper().writeValueAsString(payload);

		MockHttpServletRequest request = newPostRequest("/rest/v1/worklist/updaterequeststatus?studyInstanceUID="
				+ requestProcedure.getStudyInstanceUID()
				+ "&scheduledProcedureStepID="+ step.getId().toString(), jsonRequestContent);

		MockHttpServletResponse response = new MockHttpServletResponse();

		controller.updateRequestStatus(
                request,
                response,
                payload
        );

		RequestProcedureStep updatedStep = requestProcedureStepService.getProcedureStep(step.getId());
		assertEquals("completed", updatedStep.getPerformedProcedureStepStatus());

		RequestProcedure updatedProcedure = requestProcedureService.getRequestProcedure(requestProcedure.getId());
		assertEquals(requestProcedure.getStudyInstanceUID(), updatedProcedure.getStudyInstanceUID());

		List<RequestProcedureStep> updatedSteps = requestProcedureStepService.getAllStepByRequestProcedure(updatedProcedure);
		boolean allCompleted = updatedSteps.stream()
				.allMatch(s -> "completed".equalsIgnoreCase(s.getPerformedProcedureStepStatus()));

		if (allCompleted) {
			assertEquals("completed", updatedProcedure.getStatus());
		}
	}
	
	private static StudyUpdatePayload getStudyUpdatePayload(RequestProcedure requestProcedure,
	        RequestProcedureStep step) {
		StudyUpdatePayload payload = new StudyUpdatePayload();
		
		// StudyInfo
		StudyUpdatePayload.StudyInfo info = new StudyUpdatePayload.StudyInfo();
		info.setStudyInstanceUID(requestProcedure.getStudyInstanceUID());
		payload.setStudyInfo(info);

		StudyUpdatePayload.SeriesEntry entry = new StudyUpdatePayload.SeriesEntry();
		entry.setScheduledProcedureStepID(step.getId().toString());

        StudyUpdatePayload.InstanceInfo instance = new StudyUpdatePayload.InstanceInfo();
        instance.setScheduledProcedureStepID(step.getId().toString());
        instance.setStudyInstanceUID(requestProcedure.getStudyInstanceUID());

        List<StudyUpdatePayload.SeriesEntry> list = new ArrayList<>();
        list.add(entry);
		payload.setSeriesList(list);
		return payload;
	}
	
	@Test
	@Transactional
	public void testSaveRequestProcedure_shouldCreateNewProcedure() {
		Map<String, Object> requestPostData = new HashMap<>();
		requestPostData.put("patientUuid", "63cafe66-924c-4868-aaa5-d2d77bf26789");
		requestPostData.put("configurationId", 1);
		requestPostData.put("accessionNumber", "ACC2001");
		requestPostData.put("requestingPhysician", "Dr. House");
		requestPostData.put("requestDescription", "Chest CT");
		requestPostData.put("priority", "High");

		// Simulate POST request with helper
		MockHttpServletRequest request = newPostRequest(
				"/rest/v1/worklist/saverequest", requestPostData);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<Object> result = controller.saveRequestProcedure(requestPostData, request, response);
		assertEquals(HttpStatus.OK, result.getStatusCode());

		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		List<RequestProcedure> procedures = requestProcedureService.getAllRequestProcedures();
		boolean found = procedures.stream()
				.anyMatch(rp -> "ACC2001".equals(rp.getAccessionNumber())
				&& "scheduled".equalsIgnoreCase(rp.getStatus())
				&& "Dr. House".equals(rp.getRequestingPhysician()));
		assertTrue("New request procedure should be created", found);
	}
	
	@Test
	@Transactional
	public void testSaveRequestProcedureStep_shouldCreateStepAndUpdateRequest() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");

		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		assertNotNull(requestProcedure);

		Map<String, Object> stepPostData = new HashMap<>();
		stepPostData.put("requestId", requestProcedure.getId());
		stepPostData.put("modality", "CT");
		stepPostData.put("aetTitle", "CT_AET1");
		stepPostData.put("scheduledPerformingPhysician", "Dr. Smith");
		stepPostData.put("requestedProcedureDescription", "Chest CT");
		stepPostData.put("stepStartDate", "2025-08-25");
		stepPostData.put("stepStartTime", "10:00:00");
		stepPostData.put("stationName", "CT Station 1");
		stepPostData.put("procedureStepLocation", "Radiology Dept");

		MockHttpServletRequest request = newPostRequest(
				"/rest/v1/worklist/savestep",
				stepPostData
		);
		MockHttpServletResponse response = new MockHttpServletResponse();
		ResponseEntity<Object> result = controller.saveRequestProcedureStep(stepPostData, request, response);
		assertEquals(HttpStatus.OK, result.getStatusCode());

		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		boolean found = steps.stream().anyMatch(step ->
						"CT".equals(step.getModality()) &&
						"CT_AET1".equals(step.getAetTitle()) &&
						"Dr. Smith".equals(step.getScheduledPerformingPhysician()) &&
						"Chest CT".equals(step.getRequestedProcedureDescription()) &&
						"scheduled".equals(step.getPerformedProcedureStepStatus())
				);
		assertTrue("New procedure step should be created", found);

		RequestProcedure updatedRequest = requestProcedureService.getRequestProcedure(requestProcedure.getId());
		assertEquals("progress", updatedRequest.getStatus());
		assertEquals(4, steps.size());
	}
	
	@Test
	@Transactional
	public void testUseRequestsByPatient_shouldReturnAllRequestsForPatient() throws Exception {
		Patient patient = Context.getPatientService().getPatient(1);
		assertNotNull(patient);

		MockHttpServletRequest request = newGetRequest(
				"/rest/v1/worklist/patientrequests",
				new BaseWebControllerTest.Parameter("patient", patient.getUuid())
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<Object> result = controller.useRequestsByPatient(patient.getUuid(),request, response);
		assertEquals(HttpStatus.OK, result.getStatusCode());

		List<RequestProcedureResponse> requestList = (List<RequestProcedureResponse>) result.getBody();
		assertNotNull(requestList);
		assertFalse(requestList.isEmpty());

		requestList.sort(Comparator.comparing(RequestProcedureResponse::getId));

		assertEquals(3, requestList.size());

		assertEquals("in progress", requestList.get(0).getStatus());
		assertEquals("testInstanceUID888", requestList.get(0).getStudyInstanceUID());

		assertEquals("completed", requestList.get(1).getStatus());
		assertEquals("testInstanceUID999", requestList.get(1).getStudyInstanceUID());

		assertEquals("scheduled", requestList.get(2).getStatus());
		assertEquals("testInstanceUID333", requestList.get(2).getStudyInstanceUID());
	}
	
	@Test
	@Transactional
	public void testUseProcedureStep_shouldReturnAllStepsForRequest() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");

		Patient patient = Context.getPatientService().getPatient(1);
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);

		// Simulate a GET request
		MockHttpServletRequest request = newGetRequest("/rest/v1/imaging/studies?patient=" + patient.getUuid());
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<Object> result = controller.useProcedureStep(requestProcedure.getId(), request, response);
		assertEquals(HttpStatus.OK, result.getStatusCode());

		List<ProcedureStepResponse> stepList = (List<ProcedureStepResponse>) result.getBody();
		assertNotNull(stepList);
		assertFalse(stepList.isEmpty());

		stepList.sort(Comparator.comparing(ProcedureStepResponse::getId));

		assertEquals(3, stepList.size());

		assertEquals(requestProcedure.getId(), stepList.get(0).getId());
		assertEquals("Head Scan", stepList.get(0).getRequestedProcedureDescription());
	}
	
	@Test
	@Transactional
	public void testDeleteRequest_shouldDeleteProcedureAndSteps() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");
		
		// Fetch an existing request procedure
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		assertNotNull(requestProcedure);
		
		List<RequestProcedureStep> stepsBefore = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertFalse(stepsBefore.isEmpty());
		
		MockHttpServletRequest request = newDeleteRequest("/rest/v1/worklist/request", new BaseWebControllerTest.Parameter(
		        "requestId", String.valueOf(requestProcedure.getId())));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.deleteRequest(requestProcedure.getId(), request, response);
		
		assertEquals(HttpStatus.OK, result.getStatusCode());
		
		List<RequestProcedureStep> stepsAfter = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertTrue(stepsAfter.isEmpty());
		
		RequestProcedure deletedRequest = requestProcedureService.getRequestProcedure(requestProcedure.getId());
		assertNull(deletedRequest);
	}
	
	@Test
	@Transactional
	public void testDeleteProcedureStep_shouldDeleteStep() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");
		
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		
		RequestProcedureStep step = requestProcedureStepService.getProcedureStep(1);
		assertNotNull(step);
		
		// Ensure step exists before deletion
		RequestProcedure requestProcedure = step.getRequestProcedure();
		List<RequestProcedureStep> stepsBefore = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertTrue(stepsBefore.contains(step));
		
		MockHttpServletRequest request = newDeleteRequest("/rest/v1/worklist/requeststep",
		    new BaseWebControllerTest.Parameter("stepId", String.valueOf(step.getId())));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<Object> result = controller.deleteProcedureStep(step.getId(), request, response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		
		RequestProcedureStep deletedStep = requestProcedureStepService.getProcedureStep(step.getId());
		assertNull(deletedStep);
		
		RequestProcedure remainingRequest = requestProcedureService.getRequestProcedure(requestProcedure.getId());
		assertNotNull(remainingRequest);
	}
}
