package org.openmrs.module.imaging.api.dao;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;
import static org.junit.Assert.*;

/**
 * Tests methods in {@link DicomStudyDao}
 */
public class DicomStudyDAOTest extends BaseModuleContextSensitiveTest {
	
	private static final String DICOMSTUDY_TEST_DATASET = "testDicomStudyDataset.xml";
	
	DicomStudyDao dicomStudyDao = null;
	
	//private DicomStudy dicomStudy;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(DICOMSTUDY_TEST_DATASET);
		assertNotNull(applicationContext);
		dicomStudyDao = (DicomStudyDao) applicationContext.getBean("imaging.DicomStudyDao");
	}
	
	@Test
	public void testGetByPatient_shouldReturnStudiesForPatient() throws Exception {
		Patient patient = Context.getPatientService().getPatient(1);
		List<DicomStudy> studies = dicomStudyDao.getByPatient(patient);
		assertNotNull(studies);
		assertFalse(studies.isEmpty());
		assertEquals(2, studies.size());
	}
	
	@Test
	public void testGetAll_shouldReturnAllStudies() throws Exception {
		List<DicomStudy> allStudies = dicomStudyDao.getAll();
		assertNotNull(allStudies);
		assertFalse(allStudies.isEmpty());
		assertEquals(2, allStudies.size());
	}
	
	@Test
	public void testGetByConfiguration_shouldReturnStudies() throws Exception {
		OrthancConfigurationService configService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = configService.getOrthancConfiguration(1);
		
		List<DicomStudy> result = dicomStudyDao.getByConfiguration(config);
		assertNotNull(result);
		assertEquals("studyInstanceUID555", result.get(0).getStudyInstanceUID());
		assertEquals("orthancUID555", result.get(0).getOrthancStudyUID());
	}
	
	@Test
	public void testGetByStudyInstanceUID_shouldReturnCorrectStudy() throws Exception {
		OrthancConfigurationService configService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = configService.getOrthancConfiguration(1);
		
		DicomStudy study = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID555");
		assertNotNull(study);
		assertEquals("studyInstanceUID555", study.getStudyInstanceUID());
		assertEquals("orthancUID555", study.getOrthancStudyUID());
		assertEquals("John Doe", study.getPatientName());
		
		DicomStudy secondStudy = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID444");
		assertNotNull(secondStudy);
		assertEquals("studyInstanceUID444", secondStudy.getStudyInstanceUID());
		assertEquals("orthancUID444", secondStudy.getOrthancStudyUID());
		assertEquals("Test Imaging", secondStudy.getPatientName());
	}
	
	@Test
	public void testSave_shouldPersistStudy() throws Exception {
		OrthancConfigurationService configService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = configService.getOrthancConfiguration(1);
		
		Patient patient = Context.getPatientService().getPatient(1);
		
		DicomStudy newStudy = new DicomStudy();
		newStudy.setMrsPatient(patient);
		newStudy.setStudyInstanceUID("studyInstanceUID777");
		newStudy.setOrthancStudyUID("orthancUID777");
		newStudy.setStudyDescription("Test_New_Study");
		newStudy.setMatching(1);
		newStudy.setComparisonResult("NoComparisonData");
		newStudy.setOrthancConfiguration(config);
		
		dicomStudyDao.save(newStudy);
		
		DicomStudy retrieved = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID777");
		assertNotNull(retrieved);
		assertEquals("Test_New_Study", retrieved.getStudyDescription());
		assertEquals("orthancUID777", retrieved.getOrthancStudyUID());
		assertEquals(1, retrieved.getMatching());
		assertEquals("NoComparisonData", retrieved.getComparisonResult());
		
		List<DicomStudy> allStudies = dicomStudyDao.getAll();
		assertEquals(3, allStudies.size());
	}
	
	@Test
	public void testRemove_shouldDeleteStudy() throws Exception {
		OrthancConfigurationService configService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration config = configService.getOrthancConfiguration(1);
		
		List<DicomStudy> allStudies = dicomStudyDao.getAll();
		assertEquals(2, allStudies.size());
		
		DicomStudy study = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID444");
		dicomStudyDao.remove(study);
		
		DicomStudy deleted = dicomStudyDao.getByStudyInstanceUID(config, "studyInstanceUID444");
		assertNull(deleted);
		
		List<DicomStudy> studies = dicomStudyDao.getAll();
		assertEquals(1, studies.size());
	}
}
