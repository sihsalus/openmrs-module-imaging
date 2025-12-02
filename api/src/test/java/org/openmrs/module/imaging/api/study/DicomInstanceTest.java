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

package org.openmrs.module.imaging.api.study;

import org.junit.jupiter.api.Test;
import org.openmrs.module.imaging.OrthancConfiguration;

import static org.junit.jupiter.api.Assertions.*;

public class DicomInstanceTest {
	
	@Test
	public void testConstructorAndGetters() {
		
		OrthancConfiguration config = new OrthancConfiguration();
		
		DicomInstance dicomInstance = new DicomInstance("sop_instanceUID_123", "orthanc123", "5", "10\\20\\30", "30", config);
		
		assertEquals("sop_instanceUID_123", dicomInstance.getSopInstanceUID());
		assertEquals("orthanc123", dicomInstance.getOrthancInstanceUID());
		assertEquals("5", dicomInstance.getInstanceNumber());
		assertEquals("10\\20\\30", dicomInstance.getImagePositionPatient());
		assertEquals("30", dicomInstance.getNumberOfFrames());
		assertEquals(config, dicomInstance.getOrthancConfiguration());
	}
	
	@Test
	public void testSettersAndGetters() {
		DicomInstance instance = new DicomInstance();
		OrthancConfiguration config = new OrthancConfiguration();
		
		instance.setSopInstanceUID("sop_test_instanceUID_123");
		instance.setOrthancInstanceUID("orth_test_UID_456");
		instance.setInstanceNumber("10");
		instance.setImagePositionPatient("0\\0\\1");
		instance.setOrthancConfiguration(config);
		
		assertEquals("sop_test_instanceUID_123", instance.getSopInstanceUID());
		assertEquals("orth_test_UID_456", instance.getOrthancInstanceUID());
		assertEquals("10", instance.getInstanceNumber());
		assertEquals("0\\0\\1", instance.getImagePositionPatient());
		assertEquals(config, instance.getOrthancConfiguration());
		
		// Default null unless set via constructor
		assertNull(instance.getNumberOfFrames());
	}
}
