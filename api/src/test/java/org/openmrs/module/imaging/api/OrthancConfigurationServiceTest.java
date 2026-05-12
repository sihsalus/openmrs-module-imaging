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
import org.mockito.Mock;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrthancConfigurationServiceTest extends BaseModuleContextSensitiveTest {
	
	private OrthancConfigurationService orthancConfigurationService;
	
	@Mock
	private OrthancHttpClient mockHttpClient;
	
	private static final String ORTHANC_CONFIGURATION_TEST_DATASET = "testOrthancConfigurationDataset.xml";
	
	@Before
	public void setUp() throws Exception {
		if (orthancConfigurationService == null) {
			orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		}
		orthancConfigurationService.setHttpClient(mockHttpClient);
		executeDataSet(ORTHANC_CONFIGURATION_TEST_DATASET);
	}
	
	@Test
	public void getAllOrthancConfigurations_shouldReturnFromDatabase() {
		OrthancConfigurationService service = Context.getService(OrthancConfigurationService.class);
		List<OrthancConfiguration> configs = service.getAllOrthancConfigurations();
		assertNotNull(configs);
		assertFalse(configs.isEmpty());
		assertEquals(1, configs.size());
		assertEquals("http://localhost:8052", configs.get(0).getOrthancBaseUrl());
	}
	
	@Test
	public void getOrthancConfigurationByID_shouldReturnSingleConfig() {
		OrthancConfiguration config = orthancConfigurationService.getOrthancConfiguration(1);
		assertNotNull(config);
		assertEquals(Integer.valueOf(1), config.getId());
	}
	
	@Test
    public void saveOrthancConfiguration_shouldSaveWhenReachable() {
        OrthancConfiguration config = new OrthancConfiguration();
        config.setId(1);
        config.setOrthancBaseUrl("http://localhost:8052");
        config.setOrthancUsername("testUser");
        config.setOrthancPassword("testPassword");
        config.setOrthancProxyUrl("");

        when(mockHttpClient.isOrthancReachable(config)).thenReturn(true);

		Exception exception = assertThrows(RuntimeException.class, () -> {
			orthancConfigurationService.saveOrthancConfiguration(config);
		});

		assertEquals("A configuration with the same base URL already exists", exception.getMessage());

		List<OrthancConfiguration> configs = orthancConfigurationService.getAllOrthancConfigurations();
        assertTrue(configs.stream().anyMatch(c -> "http://localhost:8052".equals(c.getOrthancBaseUrl())));
    }
	
	@Test(expected = IllegalArgumentException.class)
	public void saveOrthancConfiguration_shouldThrowWhenNotReachable() {
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://localhost:8062");
		config.setOrthancUsername("errorUser");
		config.setOrthancPassword("errorUser");
		config.setOrthancProxyUrl("");
		
		when(mockHttpClient.isOrthancReachable(config)).thenReturn(false);
		orthancConfigurationService.saveOrthancConfiguration(config);
	}
	
}
