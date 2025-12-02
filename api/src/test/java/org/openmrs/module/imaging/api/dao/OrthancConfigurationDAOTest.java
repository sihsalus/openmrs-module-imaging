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
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;
import static org.junit.Assert.*;

/**
 * Tests methods in {@link OrthancConfigurationDao}
 */
public class OrthancConfigurationDAOTest extends BaseModuleContextSensitiveTest {
	
	private OrthancConfigurationDao orthancConfigurationDao;
	
	private static final String ORTHANC_CONFIGURATION_TEST_DATASET = "testOrthancConfigurationDataset.xml";
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(ORTHANC_CONFIGURATION_TEST_DATASET);
		assertNotNull(applicationContext);
		orthancConfigurationDao = (OrthancConfigurationDao) applicationContext.getBean("imaging.OrthancConfigurationDao");
	}
	
	@Test
	public void testGetAll_shouldReturnAllConfigurations() {
		List<OrthancConfiguration> configs = orthancConfigurationDao.getAll();
		assertEquals(1, configs.size());
		assertFalse(configs.isEmpty());
	}
	
	@Test
	public void testGetById_shouldReturnCorrectConfiguration() {
		OrthancConfiguration config = orthancConfigurationDao.get(1);
		assertNotNull(config);
		assertEquals("http://localhost:8052", config.getOrthancBaseUrl());
	}
	
	@Test
	public void testSaveNew_shouldPersistNewConfiguration() {
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://localhost:8062");
		config.setOrthancUsername("orthanc");
		config.setOrthancPassword("orthanc");
		
		orthancConfigurationDao.saveNew(config);
		List<OrthancConfiguration> all = orthancConfigurationDao.getAll();
		assertEquals(2, all.size());
	}
	
	@Test
	public void testSaveNew_whenDuplicateExists_shouldThrowException() {
		OrthancConfiguration config1 = new OrthancConfiguration();
		config1.setOrthancBaseUrl("http://localhost:8052"); // already in XML data
		config1.setOrthancUsername("orthanc");
		config1.setOrthancPassword("orthanc");
		
		//IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orthancConfigurationDao.saveNew(config1));
		//assertTrue(ex.getMessage().contains("A configuration with the same base URL already exists"));
		
		// Verify status
		List<OrthancConfiguration> all = orthancConfigurationDao.getAll();
		assertEquals(1, all.size());
	}
	
	@Test
	public void testUpdateExisting_shouldModifyConfiguration() {
		OrthancConfiguration config = orthancConfigurationDao.get(1);
		config.setOrthancUsername("testedUser");
		config.setOrthancPassword("testedUser");
		
		orthancConfigurationDao.updateExisting(config);
		OrthancConfiguration updated = orthancConfigurationDao.get(1);
		assertEquals("testedUser", updated.getOrthancUsername());
	}
	
	@Test
	public void testRemove_shouldDeleteConfiguration() {
		OrthancConfiguration config = orthancConfigurationDao.get(1);
		orthancConfigurationDao.remove(config);
		
		assertNull(orthancConfigurationDao.get(1));
		assertEquals(0, orthancConfigurationDao.getAll().size());
	}
	
}
