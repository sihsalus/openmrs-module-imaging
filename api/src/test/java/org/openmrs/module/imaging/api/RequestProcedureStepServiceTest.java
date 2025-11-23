package org.openmrs.module.imaging.api;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class RequestProcedureStepServiceTest extends BaseModuleContextSensitiveTest {
	
	private static final String REQUEST_PROCEDURE_STEP_DATASET = "testRequestProcedureStepDataset.xml";
	
	private RequestProcedureStepService requestProcedureStepService;
	
	@Before
	public void setUp() throws Exception {
		if (requestProcedureStepService == null) {
			requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		}
		executeDataSet(REQUEST_PROCEDURE_STEP_DATASET);
	}
	
	@Test
	public void getAllStepByRequestProcedure_shouldReturnAllSteps() {
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedure procedure = requestProcedureService.getRequestProcedure(1);
		assertNotNull(procedure);
		
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(procedure);
		assertNotNull(steps);
		assertFalse(steps.isEmpty());
		
		assertEquals(3, steps.size());
		assertEquals("CT", steps.get(0).getModality());
		assertEquals("2024-07-03", steps.get(1).getStepStartDate());
		assertEquals("MRI_AET", steps.get(2).getAetTitle());
	}
	
	@Test
	public void getProcedureStep_shouldReturnProcedureStepById() {
		int knownStepId = 3;
		int unknownStepId = 4;
		RequestProcedureStep step = requestProcedureStepService.getProcedureStep(knownStepId);
		assertNotNull(step);
		assertEquals("1", step.getRequestProcedure().getId().toString());
		assertEquals("Chest Scan", step.getRequestedProcedureDescription());
		
		RequestProcedureStep unknowStep = requestProcedureStepService.getProcedureStep(unknownStepId);
		assertNull(unknowStep);
	}
	
	@Test
	public void newProcedureStep_shouldSaveProcedureStep() throws IOException {
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedure procedure = requestProcedureService.getRequestProcedure(1);
		
		RequestProcedureStep step = new RequestProcedureStep();
		step.setRequestProcedure(procedure);
		step.setModality("MRI");
		step.setProcedureStepLocation("MRI location");
		step.setStepStartDate("20-07-25");
		step.setStepStartTime("14:50");
		step.setStationName("MRI station");
		step.setScheduledPerformingPhysician("test");
		step.setAetTitle("MRI-AET");
		step.setRequestedProcedureDescription("test");
		step.setPerformedProcedureStepStatus("high");
		
		// Save the step
		requestProcedureStepService.newProcedureStep(step);
		
		assertEquals("4", step.getId().toString());
		
		RequestProcedureStep fetched = requestProcedureStepService.getProcedureStep(step.getId());
		assertNotNull(fetched);
		assertEquals("MRI", fetched.getModality());
		assertEquals("20-07-25", fetched.getStepStartDate());
		assertEquals("14:50", step.getStepStartTime());
		assertEquals("high", fetched.getPerformedProcedureStepStatus());
	}
	
	@Test
	public void deleteProcedureStep_shouldRemoveProcedureStepFromDatabase() throws IOException {
		int existingStepId = 3;
		
		// Fetch existing procedure step
		RequestProcedureStep step = requestProcedureStepService.getProcedureStep(existingStepId);
		assertNotNull(step);
		
		requestProcedureStepService.deleteProcedureStep(step);
		
		// Try fetching again — should be null
		RequestProcedureStep deleted = requestProcedureStepService.getProcedureStep(existingStepId);
		assertNull(deleted);
		
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedure procedure = requestProcedureService.getRequestProcedure(1);
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(procedure);
		assertEquals(2, steps.size());
	}
	
	@Test
	public void updateProcedureStep_shouldPersistChangesInDatabase() {
		int existingStepId = 3;
		int noExistingStepId = 4;
		
		// Fetch existing step
		RequestProcedureStep step = requestProcedureStepService.getProcedureStep(existingStepId);
		assertNotNull(step);
		
		step.setPerformedProcedureStepStatus("complete");
		requestProcedureStepService.updateProcedureStep(step);
		
		// Fetch again to verify changes
		RequestProcedureStep updated = requestProcedureStepService.getProcedureStep(existingStepId);
		assertNotNull(updated);
		assertEquals("complete", updated.getPerformedProcedureStepStatus());
		
		// Fetch existing step
		RequestProcedureStep noExistingStep = requestProcedureStepService.getProcedureStep(noExistingStepId);
		assertNull(noExistingStep);
	}
	
}
