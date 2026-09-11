/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.imaging.api.impl;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.study.DicomInstance;
import org.openmrs.module.imaging.api.study.DicomSeries;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

@Transactional
public class DicomStudyServiceImpl extends BaseOpenmrsService implements DicomStudyService {
	
	private OrthancHttpClient httpClient = new OrthancHttpClient();
	
	private DicomStudyDao dao;
	
	/**
	 * @param dao the dao to set
	 */
	public void setDao(DicomStudyDao dao) {
		this.dao = dao;
	}
	
	/**
	 * @return the dao
	 */
	public DicomStudyDao getDao() {
		return dao;
	}
	
	public void setHttpClient(OrthancHttpClient httpClient) {
		this.httpClient = httpClient;
	}
	
	@Override
	public void updateLinkStatus(DicomStudy study, int newLinkStatus) {
		dao.updateLinkStatus(study, newLinkStatus);
	}
	
	/**
	 * @throws IOException the IO exception
	 */
	@Override
	public void fetchAllStudies() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		List<OrthancConfiguration> configs = orthancConfigurationService.getAllOrthancConfigurations();
		for (OrthancConfiguration config : configs) {
			fetchAllStudies(config);
		}
	}
	
	/**
	 * @param config the configuration
	 * @throws IOException the IO exception
	 */
	@Override
	public void fetchAllStudies(OrthancConfiguration config) throws IOException {
		for (JsonNode studyData : findResources(config, "Study", Collections.<String, String>emptyMap())) {
			createOrUpdateStudy(config, studyData);
		}
	}

	/**
	 * @param config the orthanc configuration
	 * @param studyData the patient image study data
	 */
	public void createOrUpdateStudy(OrthancConfiguration config, JsonNode studyData) {
		if (config == null || studyData == null || !studyData.isObject()) {
			throw new IllegalArgumentException("Orthanc returned invalid study metadata");
		}
		String studyInstanceUID = studyData.path("MainDicomTags").path("StudyInstanceUID").getTextValue();
		String orthancStudyUID = studyData.path("ID").getTextValue();
		if (studyInstanceUID == null || studyInstanceUID.trim().isEmpty()
		        || orthancStudyUID == null || !orthancStudyUID.matches("[A-Za-z0-9-]+")) {
			throw new IllegalArgumentException("Orthanc returned invalid study metadata");
		}
		String patientName = studyData.path("PatientMainDicomTags").path("PatientName").getTextValue();
		String studyDate = Optional.ofNullable(studyData.path("MainDicomTags").path("StudyDate").getTextValue()).orElse("");
		String studyTime = Optional.ofNullable(studyData.path("MainDicomTags").path("StudyTime").getTextValue()).orElse("");
		String studyDescription = Optional.ofNullable(
		    studyData.path("MainDicomTags").path("StudyDescription").getTextValue()).orElse("");
		String gender = Optional.ofNullable(studyData.path("PatientMainDicomTags").path("PatientSex").getTextValue()).orElse("");
		DicomStudy study = new DicomStudy(studyInstanceUID, orthancStudyUID, 0, 60, "{\"differences\":[], \"score\": 0}",
		        null, config, patientName, studyDate, studyTime, studyDescription, gender);
		
		DicomStudy existingStudy = dao.getByStudyInstanceUID(config, studyInstanceUID);
		if (existingStudy == null && orthancStudyUID != null && !orthancStudyUID.trim().isEmpty()) {
			existingStudy = dao.getByOrthancStudyUID(config, orthancStudyUID);
		}
		// new study? -> save new
		if (existingStudy == null) {
			dao.save(study);
		} else {
			existingStudy = dao.getForUpdate(existingStudy.getId());
			if (existingStudy == null) {
				throw new IllegalStateException("The study changed during synchronization; retry the operation");
			}
			// Keep the patient association and refresh metadata so edits in Orthanc are reflected in SIHSALUS.
			existingStudy.setStudyInstanceUID(study.getStudyInstanceUID());
			existingStudy.setOrthancStudyUID(study.getOrthancStudyUID());
			existingStudy.setPatientName(study.getPatientName());
			existingStudy.setStudyDate(study.getStudyDate());
			existingStudy.setStudyTime(study.getStudyTime());
			existingStudy.setStudyDescription(study.getStudyDescription());
			existingStudy.setGender(study.getGender());
			dao.save(existingStudy);
		}
	}
	
	/**
	 * @param config the orthanc configuration
	 * @param is the input strem
	 * @return the respose code
	 * @throws IOException the IO exception
	 */
	@Override
	public DicomStudyService.UploadResult uploadFile(OrthancConfiguration config, InputStream is) throws IOException {
		if (is == null) {
			throw new IOException("DICOM file is missing");
		}
		// Orthanc accepts ZIP too, but its array response is not the single-study
		// contract of this API. Reject the archive before any remote write.
		PushbackInputStream source = new PushbackInputStream(is, 4);
		byte[] header = new byte[4];
		int count = IOUtils.read(source, header);
		if (count == 0) {
			throw new IOException("DICOM file is missing");
		}
		if (count > 0) {
			source.unread(header, 0, count);
		}
		if (count == 4 && header[0] == 'P' && header[1] == 'K'
		        && ((header[2] == 3 && header[3] == 4) || (header[2] == 5 && header[3] == 6)
		            || (header[2] == 7 && header[3] == 8))) {
			throw new DicomStudyService.UnsupportedArchiveException();
		}
		HttpURLConnection con = httpClient.createConnection("POST", config.getOrthancBaseUrl(), "/instances",
		    config.getOrthancUsername(), config.getOrthancPassword());
		if (con == null) {
			throw new IOException("Failed to create HTTP connection");
		}
		try {
			con.setRequestProperty("Content-Type", "application/dicom");
			con.setDoOutput(true);
			con.setChunkedStreamingMode(65536);
			try (OutputStream output = con.getOutputStream()) {
				IOUtils.copy(source, output);
			}
			int status = con.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw new IOException("Orthanc could not confirm the DICOM upload");
			}
			JsonNode uploadResponse;
			try (InputStream responseStream = con.getInputStream()) {
				if (responseStream == null) {
					throw new IOException("Orthanc did not confirm a stored study");
				}
				uploadResponse = new ObjectMapper().readTree(responseStream);
			}
			if (uploadResponse == null || !uploadResponse.isObject()) {
				throw new IOException("Orthanc returned an unsupported upload response");
			}
			String uploadStatus = uploadResponse.path("Status").getTextValue();
			String orthancStudyUID = uploadResponse.path("ParentStudy").getTextValue();
			if (!("Success".equals(uploadStatus) || "AlreadyStored".equals(uploadStatus))
			        || orthancStudyUID == null || !orthancStudyUID.matches("[A-Za-z0-9-]+")) {
				throw new IOException("Orthanc did not confirm a stored study");
			}
			DicomStudyService.UploadResult result = new DicomStudyService.UploadResult();
			result.statusCode = status;
			result.orthancStudyUID = orthancStudyUID;
			result.study = fetchStudyByOrthancStudyUID(config, orthancStudyUID);
			if (result.study == null) {
				throw new IOException("The uploaded study could not be synchronized");
			}
			result.studyInstanceUID = result.study.getStudyInstanceUID();
			return result;
		}
		finally {
			OrthancHttpClient.closeConnection(con);
		}
	}
	
	/**
	 * @param pt the openmrs patient
	 * @return the list of studies
	 */
	@Override
	public List<DicomStudy> getStudiesOfPatient(Patient pt) {
		return dao.getByPatient(pt);
	}
	
	@Override
	public void synchronizeStudiesOfPatient(Patient pt) {
		Set<Integer> synchronizedConfigurations = new HashSet<Integer>();
		for (DicomStudy study : dao.getByPatient(pt)) {
			OrthancConfiguration configuration = study.getOrthancConfiguration();
			if (configuration == null || configuration.getId() == null
			        || synchronizedConfigurations.contains(configuration.getId())) {
				continue;
			}
			
			synchronizedConfigurations.add(configuration.getId());
			try {
				fetchNewChangedStudiesByConfiguration(configuration);
			}
			catch (IOException e) {
				throw new APIException("The imaging studies could not be synchronized");
			}
		}
	}
	
	public List<DicomStudy> getStudiesByConfiguration(OrthancConfiguration config) {
		return dao.getByConfiguration(config);
	}
	
	/**
	 * @return the list dicom studies
	 */
	@Override
	public List<DicomStudy> getAllStudies() {
		return dao.getAll();
	}
	
	/**
	 * @throws IOException the IO exception
	 */
	@Override
	public void fetchNewChangedStudies() throws IOException {
		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		List<OrthancConfiguration> configs = orthancConfigurationService.getAllOrthancConfigurations();
		for (OrthancConfiguration config : configs) {
			fetchNewChangedStudiesByConfiguration(config);
		}
	}
	
	/**
	 * @param config The orthanc configuation
	 * @throws IOException the IO exception
	 */
	public void fetchNewChangedStudiesByConfiguration(OrthancConfiguration config) throws IOException {
		while (true) {
			int cursor = config.getLastChangedIndex() == null ? -1 : config.getLastChangedIndex();
			String params = "?limit=1000" + (cursor == -1 ? "" : "&since=" + cursor);
			HttpURLConnection connection = openConnection(config, "GET", "/changes" + params);
			try {
				JsonNode page = readJson(connection);
				if (!page.isObject() || !page.path("Changes").isArray() || !page.path("Last").isIntegralNumber()
				        || !page.path("Done").isBoolean()) {
					throw new IOException("Orthanc returned an invalid change page");
				}
				long last = page.path("Last").getLongValue();
				boolean done = page.path("Done").getBooleanValue();
				if (last < 0 || last > Integer.MAX_VALUE || last < cursor || (!done && last == cursor)) {
					throw new IOException("The Orthanc synchronization cursor did not advance safely");
				}
				List<String> ids = new ArrayList<>();
				for (JsonNode change : page.path("Changes")) {
					String type = requiredText(change, "ChangeType");
					if ("NewStudy".equals(type) || "StableStudy".equals(type)) {
						ids.add(resourceId(requiredText(change, "ID")));
					}
				}
				fetchNewChangedStudiesByConfigurationAndStudyUIDs(config, ids);
				// Advance only after the complete page has synchronized successfully.
				config.setLastChangedIndex((int) last);
				Context.getService(OrthancConfigurationService.class).updateOrthancConfiguration(config);
				if (done) {
					return;
				}
			} finally {
				OrthancHttpClient.closeConnection(connection);
			}
		}
	}

	/**
	 * @param config the orthanc configuration
	 * @param orthancStudyIds the study instance UIDs
	 * @throws IOException the IO exception
	 */
	public void fetchNewChangedStudiesByConfigurationAndStudyUIDs(OrthancConfiguration config, List<String> orthancStudyIds)
	        throws IOException {
		for (String id : orthancStudyIds) {
			HttpURLConnection connection = openConnection(config, "GET", "/studies/" + resourceId(id));
			try {
				JsonNode metadata = readJson(connection);
				if (!metadata.isObject()) {
					throw new IOException("Orthanc returned invalid study metadata");
				}
				createOrUpdateStudy(config, metadata);
			} finally {
				OrthancHttpClient.closeConnection(connection);
			}
		}
	}

	private DicomStudy fetchStudyByOrthancStudyUID(OrthancConfiguration config, String orthancStudyUID) throws IOException {
		HttpURLConnection con = httpClient.createConnection("GET", config.getOrthancBaseUrl(),
		    "/studies/" + orthancStudyUID, config.getOrthancUsername(), config.getOrthancPassword());
		if (con == null) {
			throw new IOException("Failed to create HTTP connection");
		}
		
		try {
			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("The uploaded study could not be synchronized");
			}
			JsonNode studyData;
			try (InputStream response = con.getInputStream()) {
				if (response == null) {
					throw new IOException("Orthanc did not return study metadata");
				}
				studyData = new ObjectMapper().readTree(response);
			}
			if (studyData == null || !studyData.isObject()
			        || !orthancStudyUID.equals(studyData.path("ID").getTextValue())) {
				throw new IOException("Orthanc returned inconsistent study metadata");
			}
			String studyInstanceUID = studyData.path("MainDicomTags").path("StudyInstanceUID").getTextValue();
			if (studyInstanceUID == null || studyInstanceUID.trim().isEmpty()) {
				throw new IOException("Orthanc did not return a study instance UID");
			}
			createOrUpdateStudy(config, studyData);
			return dao.getByStudyInstanceUID(config, studyInstanceUID);
		}
		finally {
			OrthancHttpClient.closeConnection(con);
		}
	}
	
	@Override
	public DicomStudy getDicomStudy(int id) {
		return dao.get(id);
	}

	@Override
	public DicomStudy getDicomStudyForUpdate(int id) {
		return dao.getForUpdate(id);
	}
	
	/**
	 * @param studyInstanceUID the study instance UID
	 * @return the dicom study
	 */
	@Override
	public DicomStudy getDicomStudy(OrthancConfiguration config, String studyInstanceUID) {
		return dao.getByStudyInstanceUID(config, studyInstanceUID);
	}
	
	/**
	 * @param study the dicom study
	 * @param patient the openmrs patient
	 */
	@Override
	public void setPatient(DicomStudy study, Patient patient) {
		study.setMrsPatient(patient);
		dao.save(study);
	}
	
	/**
	 * @param dicomStudy the dicom study
	 */
	@Override
	public void deleteStudy(DicomStudy study) throws IOException {
		OrthancConfiguration config = studyConfiguration(study);
		HttpURLConnection connection = openConnection(config, "DELETE", "/studies/" + resourceId(study.getOrthancStudyUID()));
		try {
			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_NOT_FOUND) {
				throw new IOException("The study could not be deleted");
			}
			dao.remove(study);
		} catch (IOException e) {
			throw new IOException("The study could not be deleted");
		} finally {
			OrthancHttpClient.closeConnection(connection);
		}
	}

	@Override
	public void deleteStudyFromOpenmrs(DicomStudy study) {
		dao.remove(study);
	}

	@Override
	public void deleteSeries(String orthancSeriesUID, DicomStudy study) throws IOException {
		OrthancConfiguration config = studyConfiguration(study);
		String seriesId = resourceId(orthancSeriesUID);
		verifySeriesStudy(config, seriesId, study);
		HttpURLConnection connection = openConnection(config, "DELETE", "/series/" + seriesId);
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("The series could not be deleted");
			}
		} catch (IOException e) {
			throw new IOException("The series could not be deleted");
		} finally {
			OrthancHttpClient.closeConnection(connection);
		}
	}

	@Override
	public List<DicomSeries> fetchSeries(DicomStudy study) throws IOException {
		OrthancConfiguration config = studyConfiguration(study);
		Map<String, String> query = new LinkedHashMap<>();
		query.put("StudyInstanceUID", queryIdentifier(study.getStudyInstanceUID()));
		JsonNode data = findResources(config, "Series", query);
		List<DicomSeries> result = new ArrayList<>();
		for (JsonNode item : data) {
			if (!item.isObject()) {
				throw new IOException("Orthanc returned invalid series metadata");
			}
			JsonNode tags = item.path("MainDicomTags");
			result.add(new DicomSeries(requiredText(tags, "SeriesInstanceUID"), resourceId(requiredText(item, "ID")),
			    config, text(tags, "SeriesDescription"), text(tags, "SeriesNumber"), text(tags, "Modality"),
			    text(tags, "SeriesDate"), text(tags, "SeriesTime")));
		}
		return result;
	}

	@Override
	public List<DicomInstance> fetchInstances(String seriesInstanceUID, DicomStudy study) throws IOException {
		OrthancConfiguration config = studyConfiguration(study);
		Map<String, String> query = new LinkedHashMap<>();
		query.put("StudyInstanceUID", queryIdentifier(study.getStudyInstanceUID()));
		query.put("SeriesInstanceUID", queryIdentifier(seriesInstanceUID));
		JsonNode data = findResources(config, "Instance", query);
		List<DicomInstance> result = new ArrayList<>();
		for (JsonNode item : data) {
			if (!item.isObject()) {
				throw new IOException("Orthanc returned invalid instance metadata");
			}
			JsonNode tags = item.path("MainDicomTags");
			result.add(new DicomInstance(requiredText(tags, "SOPInstanceUID"), resourceId(requiredText(item, "ID")),
			    text(tags, "InstanceNumber"), text(tags, "ImagePositionPatient"), text(tags, "NumberOfFrames"), config));
		}
		return result;
	}

	@Override
	public PreviewResult fetchInstancePreview(String orthancInstanceUID, DicomStudy study) throws IOException {
		OrthancConfiguration config = studyConfiguration(study);
		String instanceId = resourceId(orthancInstanceUID);
		JsonNode instance = readResource(config, "/instances/" + instanceId, instanceId);
		verifySeriesStudy(config, resourceId(requiredText(instance, "ParentSeries")), study);
		HttpURLConnection connection = openConnection(config, "GET", "/instances/" + instanceId + "/preview");
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("The image preview could not be retrieved");
			}
			String contentType = connection.getContentType();
			String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
			if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType)) {
				throw new IOException("Orthanc returned an unsupported preview format");
			}
			try (InputStream input = connection.getInputStream()) {
				if (input == null) {
					throw new IOException("The image preview could not be retrieved");
				}
				PreviewResult result = new PreviewResult();
				result.data = IOUtils.toByteArray(input);
				result.contentType = mediaType;
				return result;
			}
		} catch (IOException e) {
			throw new IOException("The image preview could not be retrieved");
		} finally {
			OrthancHttpClient.closeConnection(connection);
		}
	}

	private void verifySeriesStudy(OrthancConfiguration config, String seriesId, DicomStudy study) throws IOException {
		JsonNode series = readResource(config, "/series/" + seriesId, seriesId);
		if (!resourceId(study.getOrthancStudyUID()).equals(requiredText(series, "ParentStudy"))) {
			throw new IOException("The image resource does not belong to the selected study");
		}
	}

	@Override
	public boolean isStudyForPatient(DicomStudy study, Patient patient) throws IOException {
		if (patient == null || patient.getUuid() == null || patient.getUuid().trim().isEmpty()) {
			return false;
		}
		OrthancConfiguration config = studyConfiguration(study);
		String studyId = resourceId(study.getOrthancStudyUID());
		JsonNode metadata = readResource(config, "/studies/" + studyId, studyId);
		String patientId = metadata.path("PatientMainDicomTags").path("PatientID").getTextValue();
		String studyUid = metadata.path("MainDicomTags").path("StudyInstanceUID").getTextValue();
		return patient.getUuid().equals(patientId) && studyUid != null && studyUid.equals(study.getStudyInstanceUID());
	}

	private JsonNode readResource(OrthancConfiguration config, String path, String id) throws IOException {
		HttpURLConnection connection = openConnection(config, "GET", path);
		try {
			JsonNode resource = readJson(connection);
			if (!resource.isObject() || !id.equals(requiredText(resource, "ID"))) {
				throw new IOException("Orthanc returned inconsistent resource metadata");
			}
			return resource;
		} finally {
			OrthancHttpClient.closeConnection(connection);
		}
	}

	private JsonNode findResources(OrthancConfiguration config, String level, Map<String, String> query) throws IOException {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("Level", level);
		request.put("Expand", true);
		request.put("Query", query);
		HttpURLConnection connection = openConnection(config, "POST", "/tools/find");
		try {
			httpClient.sendOrthancQuery(connection, new ObjectMapper().writeValueAsString(request));
			JsonNode result = readJson(connection);
			if (!result.isArray()) {
				throw new IOException("Orthanc returned an invalid search response");
			}
			return result;
		} catch (IOException e) {
			throw new IOException("The imaging resources could not be retrieved");
		} finally {
			OrthancHttpClient.closeConnection(connection);
		}
	}

	private JsonNode readJson(HttpURLConnection connection) throws IOException {
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("The Orthanc request could not be completed");
			}
			try (InputStream input = connection.getInputStream()) {
				if (input == null) {
					throw new IOException("Orthanc returned no metadata");
				}
				JsonNode result = new ObjectMapper().readTree(input);
				if (result == null) {
					throw new IOException("Orthanc returned no metadata");
				}
				return result;
			}
		} catch (IOException e) {
			throw new IOException("Orthanc returned an invalid response");
		}
	}

	private HttpURLConnection openConnection(OrthancConfiguration config, String method, String path) throws IOException {
		try {
			HttpURLConnection connection = httpClient.createConnection(method, config.getOrthancBaseUrl(), path,
			    config.getOrthancUsername(), config.getOrthancPassword());
			if (connection == null) {
				throw new IOException("The Orthanc connection could not be created");
			}
			return connection;
		} catch (IOException e) {
			throw new IOException("The Orthanc connection could not be created");
		}
	}

	private OrthancConfiguration studyConfiguration(DicomStudy study) throws IOException {
		if (study == null || study.getOrthancConfiguration() == null) {
			throw new IOException("The study configuration is unavailable");
		}
		return study.getOrthancConfiguration();
	}

	private String resourceId(String value) throws IOException {
		if (value == null || !value.matches("[A-Za-z0-9-]+")) {
			throw new IOException("Invalid imaging resource identifier");
		}
		return value;
	}

	private String queryIdentifier(String value) throws IOException {
		if (value == null || value.trim().isEmpty() || value.indexOf('*') >= 0 || value.indexOf('?') >= 0
		        || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
			throw new IOException("Invalid imaging query identifier");
		}
		return value;
	}

	private String requiredText(JsonNode object, String field) throws IOException {
		String value = object.path(field).getTextValue();
		if (value == null || value.trim().isEmpty()) {
			throw new IOException("Orthanc returned incomplete metadata");
		}
		return value;
	}

	private String text(JsonNode object, String field) {
		String value = object.path(field).getTextValue();
		return value == null ? "" : value;
	}
}
