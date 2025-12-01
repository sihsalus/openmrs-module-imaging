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
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.module.imaging.api.RequestProcedureStepService;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.ui.framework.Model;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class RequestProcedurePageControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private RequestProcedurePageController controller;
	
	private Patient patient;
	
	private RequestProcedureService requestProcedureService;
	
	private RequestProcedureStepService requestProcedureStepService;
	
	private OrthancConfigurationService orthancConfigurationService;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("testRequestProcedureDataset.xml");
		
		controller = (RequestProcedurePageController) applicationContext.getBean("requestProcedurePageController");
		requestProcedureService = Context.getService(RequestProcedureService.class);
		requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		
		patient = Context.getPatientService().getPatient(1);
	}
	
	@Test
	public void testGet_shouldPopulateModelWithStepList() {
		executeDataSet("testRequestProcedureStepDataset.xml");
		Model model = new PageModel();
		controller.get(model, patient);
		
		assertNotNull(model.getAttribute("requestProcedureMap"));
		assertNotNull(model.getAttribute("orthancConfigurations"));
		assertNotNull(model.getAttribute("privilegeEditWorklist"));
		
		Object procedureObj = model.getAttribute("requestProcedureMap");
		assertTrue(procedureObj instanceof Map<?, ?>);
		Map<?, ?> procedureList = (Map<?, ?>) procedureObj;
		assertEquals(3, procedureList.size());
	}
	
	@Test
	public void testNewRequest_WithPrivilege() {
		RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
		User user = Context.getAuthenticatedUser();
		user.hasPrivilege(ImagingConstants.PRIVILEGE_MODIFY_IMAGE_DATA);
		
		String view = controller.newRequest(redirectAttributes, patient, 1, "ACC123", "UID123", "Dr. Smith",
		    "Test Description", "High");
		
		assertEquals("redirect:/imaging/requestProcedure.page", view);
		verify(redirectAttributes).addAttribute(eq("message"), contains("The new request procedure is successfully added"));
		verify(redirectAttributes).addAttribute(eq("patientId"), eq(patient.getId()));
	}
	
	@Test
	public void testDeleteRequest_WithNoSteps() {
		RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
		List<RequestProcedure> beforeRequestProcedures = requestProcedureService.getRequestProcedureByPatient(patient);
		assertEquals(3, beforeRequestProcedures.size());
		
		String view = controller.deleteRequest(redirectAttributes, 1, patient);
		assertEquals("redirect:/imaging/requestProcedure.page", view);
		
		verify(redirectAttributes).addAttribute(eq("message"), contains("successfully deleted"));
		verify(redirectAttributes).addAttribute(eq("patientId"), eq(patient.getId()));
		List<RequestProcedure> requestProcedures = requestProcedureService.getRequestProcedureByPatient(patient);
		assertEquals(2, requestProcedures.size());
	}
	
	@Test
	public void testNewProceureStep_shouldSuccessful() throws Exception {
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		
		String view = controller.newProcedureStep(redirectAttributes, requestProcedure.getId(), "CT", "AET1", "Dr. Ref",
		    "Chest Scan", "2025-08-19", "09:30", "Station1", "Room101", patient);
		
		assertEquals("redirect:/imaging/requestProcedure.page", view);
		assertEquals("The step of the request procedure are successfully created",
		    redirectAttributes.getAttribute("message"));
		
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertFalse(steps.isEmpty());
		RequestProcedureStep created = steps.get(0);
		assertEquals("CT", created.getModality());
		assertEquals("Room101", created.getProcedureStepLocation());
		assertEquals("scheduled", created.getPerformedProcedureStepStatus());
		
		RequestProcedure updated = requestProcedureService.getRequestProcedure(requestProcedure.getId());
		assertEquals("progress", updated.getStatus());
	}
	
	@Test
	public void testDeleteProcedureStep_shouldSuccessfulDeleted() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertFalse("Precondition: at least one step should exist", steps.isEmpty());
		int stepId = steps.get(0).getId();
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		String view = controller.deleteProcedureStep(redirectAttributes, stepId, patient);
		
		assertEquals("redirect:/imaging/requestProcedure.page", view);
		assertEquals("Request procedure successfully deleted", redirectAttributes.getAttribute("message"));
		
		// Verify step is deleted
		assertNull(requestProcedureStepService.getProcedureStep(stepId));
	}
	
	@Test
	public void testDeleteProcedureStep_PermissionDenied() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");
		// remove privilege from authenticated user
		// TODO: need to ask how to remove privilege
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(1);
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		assertFalse(steps.isEmpty());
		int stepId = steps.get(0).getId();
		
		RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
		String view = controller.deleteProcedureStep(redirectAttributes, stepId, patient);
		
		assertEquals("redirect:/imaging/requestProcedure.page", view);
		//TODO: the test here is not completed.
		//        assertEquals("Permission denied (you don't have the necessary privileges)",
		//                redirectAttributes.getAttribute("message"));
		
		// Ensure step still exists
		//assertNotNull(requestProcedureStepService.getProcedureStep(stepId));
	}
	
}
