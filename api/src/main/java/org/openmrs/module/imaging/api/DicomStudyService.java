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

import org.codehaus.jackson.JsonNode;
import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.study.DicomInstance;
import org.openmrs.module.imaging.api.study.DicomSeries;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This service exposes module's core functionality. It is a Spring managed bean which is configured
 * in moduleApplicationContext.xml.
 * <p>
 * It can be accessed only via Context:<br>
 * <code>
 * Context.getService(DicomStudyService.class).someMethod();
 * </code>
 * 
 * @see org.openmrs.api.context.Context
 */
@Service
@Transactional
public interface DicomStudyService extends OpenmrsService {
	
	DicomStudyDao getDao();
	
	List<DicomStudy> getStudiesOfPatient(Patient pt);
	
	List<DicomStudy> getStudiesByConfiguration(OrthancConfiguration config);
	
	List<DicomStudy> getAllStudies();
	
	DicomStudy getDicomStudy(int id);
	
	DicomStudy getDicomStudy(OrthancConfiguration config, String studyInstanceUID);
	
	void fetchAllStudies() throws IOException;
	
	void fetchAllStudies(OrthancConfiguration orthancConfiguration) throws IOException;
	
	void createOrUpdateStudy(OrthancConfiguration orthancConfiguration, JsonNode studyData);
	
	void fetchNewChangedStudies() throws IOException;
	
	void fetchNewChangedStudiesByConfiguration(OrthancConfiguration orthancConfiguration) throws IOException;
	
	int uploadFile(OrthancConfiguration config, InputStream is) throws IOException;
	
	void setPatient(DicomStudy study, Patient patient);
	
	void deleteStudy(DicomStudy dicomStudy) throws IOException;
	
	void deleteStudyFromOpenmrs(DicomStudy dicomStudy);
	
	void deleteSeries(String seriesOrthancUID, DicomStudy study) throws IOException;
	
	List<DicomSeries> fetchSeries(DicomStudy study) throws IOException;
	
	List<DicomInstance> fetchInstances(String seriesInstanceUID, DicomStudy study) throws IOException;
	
	void setHttpClient(OrthancHttpClient client);
	
	void updateMatching(DicomStudy study, int newMatchingStatus);
	
	class PreviewResult {
		
		public byte[] data;
		
		public String contentType;
	}
	
	PreviewResult fetchInstancePreview(String orthancInstanceUID, DicomStudy study) throws IOException;
}
