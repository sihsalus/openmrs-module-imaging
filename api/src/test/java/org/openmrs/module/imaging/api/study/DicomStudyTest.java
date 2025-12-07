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
import org.openmrs.Patient;
import org.openmrs.module.imaging.OrthancConfiguration;

import static org.junit.jupiter.api.Assertions.*;

public class DicomStudyTest {
	
	@Test
	public void testConstructorAndGetters() {
		Patient mockPatient = new Patient();
		OrthancConfiguration mockConfig = new OrthancConfiguration();
		
		DicomStudy dicomStudy = new DicomStudy("studyUID123", "orthancUID456", 0, 60, "{\"differences\":[], \"score\": 0}",
		        mockPatient, mockConfig, "John Doe", "20250601", "122600.979000", "Chest X-Ray", "M");
		
		assertEquals("studyUID123", dicomStudy.getStudyInstanceUID());
		assertEquals("orthancUID456", dicomStudy.getOrthancStudyUID());
		assertEquals(mockPatient, dicomStudy.getMrsPatient());
		assertEquals(mockConfig, dicomStudy.getOrthancConfiguration());
		assertEquals("John Doe", dicomStudy.getPatientName());
		assertEquals("20250601", dicomStudy.getStudyDate());
		assertEquals("122600.979000", dicomStudy.getStudyTime());
		assertEquals("Chest X-Ray", dicomStudy.getStudyDescription());
		assertEquals("M", dicomStudy.getGender());
	}
	
	@Test
	public void testSettersAndGetters() {
		OrthancConfiguration config = new OrthancConfiguration();
		
		DicomStudy study = new DicomStudy();
		study.setStudyInstanceUID("studyUID999");
		study.setOrthancStudyUID("orthancUID888");
		
		Patient patient = new Patient();
		study.setMrsPatient(patient);
		study.setOrthancConfiguration(config);
		
		study.setPatientName("Jane Smith");
		study.setStudyDate("20250701");
		study.setStudyTime("14:30");
		study.setStudyDescription("MRI Brain");
		study.setGender("F");
		
		assertEquals("studyUID999", study.getStudyInstanceUID());
		assertEquals("orthancUID888", study.getOrthancStudyUID());
		assertEquals(patient, study.getMrsPatient());
		assertEquals(config, study.getOrthancConfiguration());
		assertEquals("Jane Smith", study.getPatientName());
		assertEquals("20250701", study.getStudyDate());
		assertEquals("14:30", study.getStudyTime());
		assertEquals("MRI Brain", study.getStudyDescription());
		assertEquals("F", study.getGender());
	}
}
