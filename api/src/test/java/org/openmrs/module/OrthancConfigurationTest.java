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
package org.openmrs.module;

import org.junit.jupiter.api.Test;
import org.openmrs.module.imaging.OrthancConfiguration;

import static org.junit.jupiter.api.Assertions.*;

public class OrthancConfigurationTest {
	
	@Test
	public void testConstructorAndGetters() {
		
		OrthancConfiguration config = new OrthancConfiguration("http://localhost:8052", "http://proxy:8052", "admin",
		        "secret", 2);
		
		assertEquals("http://localhost:8052", config.getOrthancBaseUrl());
		assertEquals("http://proxy:8052", config.getOrthancProxyUrl());
		assertEquals("admin", config.getOrthancUsername());
		assertEquals("secret", config.getOrthancPassword());
		assertEquals(2, config.getLastChangedIndex());
	}
	
	@Test
	public void testSettersAndGetters() {
		
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://localhost:8062");
		config.setOrthancProxyUrl("http://proxy:8062");
		config.setOrthancUsername("user1");
		config.setOrthancPassword("user1");
		config.setLastChangedIndex(-1);
		
		assertEquals("http://localhost:8062", config.getOrthancBaseUrl());
		assertEquals("http://proxy:8062", config.getOrthancProxyUrl());
		assertEquals("user1", config.getOrthancUsername());
		assertEquals("user1", config.getOrthancPassword());
		assertEquals(-1, config.getLastChangedIndex());
	}
	
	@Test
	public void testDefaultLastChangedIndexValue() {
		OrthancConfiguration config = new OrthancConfiguration();
		assertEquals(-1, config.getLastChangedIndex(), "Default value for lastChangedIndex should be -1");
	}
}
