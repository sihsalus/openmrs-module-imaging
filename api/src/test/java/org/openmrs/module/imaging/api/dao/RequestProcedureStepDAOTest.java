package org.openmrs.module.imaging.api.dao;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;
import static org.junit.Assert.*;

/**
 * Tests methods in {@link RequestProcedureStepDao}
 */
public class RequestProcedureStepDAOTest extends BaseModuleContextSensitiveTest {
	
	private RequestProcedureStepDao requestProcedureStepDao;
	
	private RequestProcedureDao requestProcedureDao;
	
	private static final String REQUEST_PROCEDURE_STEP_TEST_DATASET = "testRequestProcedureStepDataset.xml";
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(REQUEST_PROCEDURE_STEP_TEST_DATASET);
		assertNotNull(applicationContext);
		requestProcedureDao = (RequestProcedureDao) applicationContext.getBean("imaging.RequestProcedureDao");
		requestProcedureStepDao = (RequestProcedureStepDao) applicationContext.getBean("imaging.RequestProcedureStepDao");
	}
	
	@Test
	public void testGetAll_shouldReturnAllSteps() {
		List<RequestProcedureStep> steps = requestProcedureStepDao.getAll();
		assertEquals(3, steps.size());
	}
	
	@Test
	public void testGetAllStepByRequestProcedure_shouldReturnAssociatedSteps() {
		RequestProcedure procedure = requestProcedureDao.get(1);
		List<RequestProcedureStep> stepList = requestProcedureStepDao.getAllStepByRequestProcedure(procedure);
		assertEquals(3, stepList.size());
		assertEquals("scheduled", stepList.get(0).getPerformedProcedureStepStatus());
		assertEquals("Chest Scan", stepList.get(2).getRequestedProcedureDescription());
		assertEquals("CT", stepList.get(1).getModality());
	}
	
	@Test
	public void testGetById_shouldReturnCorrectProcedureStep() {
		RequestProcedureStep step = requestProcedureStepDao.get(1);
		assertNotNull(step);
		assertEquals("CT", step.getModality());
		assertEquals("Head Scan", step.getRequestedProcedureDescription());
		assertEquals("CT Station", step.getStationName());
		assertEquals("Dr. Physician1", step.getScheduledPerformingPhysician());
	}
	
	@Test
	public void testSave_shouldAddNewStep() {
		
		RequestProcedure procedure = requestProcedureDao.get(1);
		RequestProcedureStep newStep = new RequestProcedureStep();
		newStep.setRequestProcedure(procedure);
		newStep.setId(4);
		newStep.setModality("CT");
		newStep.setScheduledPerformingPhysician("Test new step_physician");
		newStep.setRequestedProcedureDescription("CT Scan Chest New");
		newStep.setAetTitle("testSave_AET2");
		newStep.setStepStartDate("2025-07-03");
		newStep.setStepStartTime("10:00");
		newStep.setPerformedProcedureStepStatus("scheduled");
		newStep.setStationName("CT Room");
		newStep.setProcedureStepLocation("Radiology");
		
		requestProcedureStepDao.save(newStep);
		
		List<RequestProcedureStep> stepList = requestProcedureStepDao.getAllStepByRequestProcedure(procedure);
		assertEquals(4, stepList.size());
		
		//		RequestProcedureStep retrieved = requestProcedureStepDao.get(3);
		//		assertNotNull(retrieved);
		//		assertEquals("CT Scan Chest New", retrieved.getRequestedProcedureDescription());
	}
	
	@Test
	public void testRemove_shouldDeleteStep() {
		RequestProcedureStep step = requestProcedureStepDao.get(1);
		assertNotNull(step);
		requestProcedureStepDao.remove(step);
		
		RequestProcedureStep deleted = requestProcedureStepDao.get(1);
		assertNull(deleted);
		
		List<RequestProcedureStep> stepList = requestProcedureStepDao.getAll();
		assertEquals(2, stepList.size());
	}
	
	@Test
	public void testUpdate_shouldUpdateStepData() {
		RequestProcedureStep step = requestProcedureStepDao.get(1);
		assertNotNull(step);
		
		step.setScheduledPerformingPhysician("Physician in station");
		step.setPerformedProcedureStepStatus("completed");
		requestProcedureStepDao.update(step);
		
		RequestProcedureStep updtedStep = requestProcedureStepDao.get(1);
		assertNotNull(updtedStep);
		assertEquals("Physician in station", updtedStep.getScheduledPerformingPhysician());
		assertEquals("completed", updtedStep.getPerformedProcedureStepStatus());
	}
}
