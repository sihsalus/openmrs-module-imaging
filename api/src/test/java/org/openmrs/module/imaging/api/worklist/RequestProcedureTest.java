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

package org.openmrs.module.imaging.api.worklist;

import org.junit.jupiter.api.Test;
import org.openmrs.Patient;
import org.openmrs.module.imaging.OrthancConfiguration;

import static org.junit.jupiter.api.Assertions.*;

public class RequestProcedureTest {
	
	@Test
	public void testConstructorAndGetters() {
		
		Patient patient = new Patient();
		OrthancConfiguration config = new OrthancConfiguration();
		
		RequestProcedure requestProcedure = new RequestProcedure("in progress", patient, config, "ACC12345", "UID456",
		        "Dr. Smith", "Chest X-ray", "High");
		
		assertEquals("in progress", requestProcedure.getStatus());
		assertEquals(patient, requestProcedure.getMrsPatient());
		assertEquals(config, requestProcedure.getOrthancConfiguration());
		assertEquals("ACC12345", requestProcedure.getAccessionNumber());
		assertEquals("UID456", requestProcedure.getStudyInstanceUID());
		assertEquals("Dr. Smith", requestProcedure.getRequestingPhysician());
		assertEquals("Chest X-ray", requestProcedure.getRequestDescription());
		assertEquals("High", requestProcedure.getPriority());
	}
	
	@Test
	public void testSettersAndGetters() {
		
		RequestProcedure requestProcedure = new RequestProcedure();
		Patient patient = new Patient();
		OrthancConfiguration config = new OrthancConfiguration();
		
		requestProcedure.setStatus("completed");
		requestProcedure.setMrsPatient(patient);
		requestProcedure.setOrthancConfiguration(config);
		requestProcedure.setAccessionNumber("ACC789");
		requestProcedure.setStudyInstanceUID("UID987");
		requestProcedure.setRequestingPhysician("Dr. Jones");
		requestProcedure.setRequestDescription("MRI Brain");
		requestProcedure.setPriority("Low");
		
		assertEquals("completed", requestProcedure.getStatus());
		assertEquals(patient, requestProcedure.getMrsPatient());
		assertEquals(config, requestProcedure.getOrthancConfiguration());
		assertEquals("ACC789", requestProcedure.getAccessionNumber());
		assertEquals("UID987", requestProcedure.getStudyInstanceUID());
		assertEquals("Dr. Jones", requestProcedure.getRequestingPhysician());
		assertEquals("MRI Brain", requestProcedure.getRequestDescription());
		assertEquals("Low", requestProcedure.getPriority());
		
	}
}
