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

public class DicomSeriesTest {
	
	@Test
	public void testConstructorAndGetters() {
		
		OrthancConfiguration config = new OrthancConfiguration();
		
		DicomSeries dicomSeries = new DicomSeries("SERIES_UID_123", "ORTHANC_UID_456", config, "Abdomen CT", "10", "CT",
		        "20250701", "15:45");
		
		assertEquals("SERIES_UID_123", dicomSeries.getSeriesInstanceUID());
		assertEquals("ORTHANC_UID_456", dicomSeries.getOrthancSeriesUID());
		assertEquals(config, dicomSeries.getOrthancConfiguration());
		assertEquals("Abdomen CT", dicomSeries.getSeriesDescription());
		assertEquals("10", dicomSeries.getSeriesNumber());
		assertEquals("CT", dicomSeries.getModality());
		assertEquals("20250701", dicomSeries.getSeriesDate());
		assertEquals("15:45", dicomSeries.getSeriesTime());
	}
	
	@Test
	public void testSettersAndGetters() {
		DicomSeries series = new DicomSeries();
		OrthancConfiguration config = new OrthancConfiguration();
		
		series.setSeriesInstanceUID("UID_001");
		series.setOrthancSeriesUID("ORTH_UID_001");
		series.setOrthancConfiguration(config);
		series.setSeriesDescription("Head MRI");
		series.setSeriesNumber("3");
		series.setModality("MRI");
		series.setSeriesDate("20250630");
		series.setSeriesTime("09:30");
		
		assertEquals("UID_001", series.getSeriesInstanceUID());
		assertEquals("ORTH_UID_001", series.getOrthancSeriesUID());
		assertEquals(config, series.getOrthancConfiguration());
		assertEquals("Head MRI", series.getSeriesDescription());
		assertEquals("3", series.getSeriesNumber());
		assertEquals("MRI", series.getModality());
		assertEquals("20250630", series.getSeriesDate());
		assertEquals("09:30", series.getSeriesTime());
	}
}
