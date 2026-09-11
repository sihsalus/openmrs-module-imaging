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
package org.openmrs.module.imaging.web.controller;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.openmrs.annotation.Authorized;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.module.imaging.api.RequestProcedureStepService;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.DicomWorklistValues;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.module.imaging.web.controller.ResponseModel.ProcedureStepResponse;
import org.openmrs.module.imaging.web.controller.ResponseModel.RequestProcedureResponse;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller("${rootrootArtifactId}.RequestProcedureController")
@RequestMapping("/rest/" + RestConstants.VERSION_1 + "/worklist")
public class RequestProcedureController {
	
	private static final ObjectMapper mapper = new ObjectMapper();
	
	private static final int FUZZY_THRESHOLD = 98;
	
	private static final Set<String> ALLOWED_REQUEST_STATUSES = new HashSet<String>(Arrays.asList("scheduled", "progress",
	    "in progress", "completed"));
	
	private static final Set<String> ALLOWED_STEP_STATUSES = new HashSet<String>(Arrays.asList("scheduled", "in progress",
	    "completed", "rejected"));
	
	@RequestMapping(value = "/requests", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
    @Transactional
    public ResponseEntity<Object> useRequestProcedures(
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            HttpServletRequest request, HttpServletResponse response) {

        RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);

        Map<String, String> statusMapping = new HashMap<>();
        statusMapping.put("scheduled", "scheduled");
        statusMapping.put("progress", "in progress");
        statusMapping.put("completed", "completed");

        boolean filterAll = status == null || status.trim().isEmpty() || status.trim().equalsIgnoreCase("all");
        String normalizedStatus = filterAll ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!filterAll && !ALLOWED_REQUEST_STATUSES.contains(normalizedStatus)) {
            return new ResponseEntity<Object>("Invalid request status", HttpStatus.BAD_REQUEST);
        }

        // Determine the database status to query
        String dbStatus = filterAll ? "" : statusMapping.getOrDefault(normalizedStatus, normalizedStatus);

        // Fetch requests
        List<RequestProcedure> requests = filterAll
                ? requestProcedureService.getAllRequestProcedures()
                : requestProcedureService.getRequestProceduresByStatus(dbStatus);

		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
        List<Map<String,Object>> result = new LinkedList<Map<String,Object>>();
        for (RequestProcedure rp : requests) {
            Map<String,Object> map = new HashMap<String,Object>();
            writeProcedure(rp, map, requestProcedureStepService);
            result.add(map);
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
	
	/**
	 * @param rp The request procedure object
	 * @param map The worklist data map
	 * @param requestProcedureStepService The request procedure step service
	 */
	private static void writeProcedure(RequestProcedure rp, Map<String, Object> map,
	        RequestProcedureStepService requestProcedureStepService) {

		map.put("SpecificCharacterSet", "ISO_IR 192");
		map.put("AccessionNumber", rp.getAccessionNumber());
		map.put("PatientName", rp.getMrsPatient().getPersonName().getFullName());
		map.put("PatientID", rp.getMrsPatient().getUuid());
		Date birthDate = rp.getMrsPatient().getBirthdate();
		map.put("PatientBirthDate", birthDate == null ? "" : new SimpleDateFormat("yyyyMMdd").format(birthDate));
		map.put("PatientSex", rp.getMrsPatient().getGender());
		map.put("StudyInstanceUID", rp.getStudyInstanceUID());
		map.put("RequestingPhysician", rp.getRequestingPhysician()); // RequestingPhysician
		map.put("RequestedProcedureDescription", rp.getRequestDescription());
		map.put("RequestedProcedureID", rp.getId().toString());
		map.put("RequestedProcedurePriority", rp.getPriority() == null ? "" : rp.getPriority().toUpperCase(Locale.ROOT));

		// Read the procedure step
		List<RequestProcedureStep> procedureStep = requestProcedureStepService.getAllStepByRequestProcedure(rp);
		List<Map<String, Object>> stepList = new ArrayList<>();
		for(RequestProcedureStep step : procedureStep) {
			writeProcedureStep(step, stepList);
		}
		map.put("ScheduledProcedureStepSequence", stepList);
	}
	
	/**
	 * @param step The request procedure step
	 * @param stepList The list of the procedure step
	 */
	private static void writeProcedureStep(RequestProcedureStep step, List<Map<String, Object>> stepList) {
		Map<String, Object> stepMap = new HashMap<String, Object>();
		stepMap.put("Modality", step.getModality());
		stepMap.put("ScheduledStationAETitle", step.getAetTitle());
		stepMap.put("ScheduledProcedureStepStartDate", DicomWorklistValues.date(step.getStepStartDate()));
		stepMap.put("ScheduledProcedureStepStartTime", DicomWorklistValues.time(step.getStepStartTime()));
		stepMap.put("ScheduledPerformingPhysicianName", step.getScheduledPerformingPhysician());
		stepMap.put("PerformedProcedureStepStatus", step.getPerformedProcedureStepStatus());
		stepMap.put("ScheduledProcedureStepDescription", step.getRequestedProcedureDescription());
		stepMap.put("ScheduledProcedureStepID", step.getId().toString());
		stepMap.put("ScheduledStationName", step.getStationName() == null ? "" : step.getStationName().trim());
		stepMap.put("ScheduledProcedureStepLocation", step.getProcedureStepLocation() == null ? "" : step.getProcedureStepLocation().trim());
		stepMap.put("CommentsOnTheScheduledProcedureStep", "no value available");
		stepList.add(stepMap);
	}
	
	/**
	 * @param payload The whole study data procedure that has been performed in this step.
	 */
	@RequestMapping(value = "/updaterequeststatus", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_RECEIVE_ORTHANC_UPDATES)
	@Transactional(rollbackFor = IOException.class)
	public ResponseEntity<?> updateRequestStatus(HttpServletRequest request, HttpServletResponse response,
	        @RequestBody StudyUpdatePayload payload) throws IOException {
		if (payload == null || payload.getStudyInfo() == null || payload.getSeriesList() == null
		        || payload.getSeriesList().isEmpty() || !isNotBlank(payload.getStudyInfo().getStudyInstanceUID())) {
			return new ResponseEntity<String>("Invalid Orthanc update payload", HttpStatus.BAD_REQUEST);
		}
		RequestProcedureService requestService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService stepService = Context.getService(RequestProcedureStepService.class);
		DicomStudyService studyService = Context.getService(DicomStudyService.class);
		Map<Integer, RequestProcedureStep> steps = new LinkedHashMap<Integer, RequestProcedureStep>();
		RequestProcedure procedure = null;
		String studyUid = payload.getStudyInfo().getStudyInstanceUID();

		// Validate the entire notification before changing any clinical state. A
		// numeric step ID alone is not evidence that an image belongs to a patient.
		for (StudyUpdatePayload.SeriesEntry entry : payload.getSeriesList()) {
			if (entry == null) {
				return new ResponseEntity<String>("Invalid series entry", HttpStatus.BAD_REQUEST);
			}
			String stepId = entry.getScheduledProcedureStepID();
			if (!isNotBlank(stepId)) {
				return new ResponseEntity<String>("Missing procedure step identity", HttpStatus.BAD_REQUEST);
			}
			RequestProcedureStep step;
			try {
				step = stepService.getProcedureStep(Integer.parseInt(stepId));
			}
			catch (NumberFormatException e) {
				return new ResponseEntity<String>("Invalid procedure step ID", HttpStatus.BAD_REQUEST);
			}
			if (step == null || step.getRequestProcedure() == null) {
				return new ResponseEntity<String>("Procedure step not found", HttpStatus.NOT_FOUND);
			}
			RequestProcedure candidate = step.getRequestProcedure();
			StudyUpdatePayload.InstanceInfo instance = entry.getInstanceInfo();
			if (candidate.getMrsPatient() == null || candidate.getOrthancConfiguration() == null
			        || candidate.getId() == null || !isNotBlank(candidate.getMrsPatient().getUuid())
			        || instance == null || !candidate.getMrsPatient().getUuid().equals(instance.getPatientID())
			        || !stepId.equals(instance.getScheduledProcedureStepID())
			        || !isNotBlank(candidate.getAccessionNumber())
			        || !candidate.getAccessionNumber().equals(payload.getStudyInfo().getAccessionNumber())
			        || (isNotBlank(instance.getStudyInstanceUID()) && !studyUid.equals(instance.getStudyInstanceUID()))
			        || (procedure != null && !procedure.getId().equals(candidate.getId()))) {
				return new ResponseEntity<String>("The study identity does not match the requested procedure",
				    HttpStatus.CONFLICT);
			}
			procedure = candidate;
			if (!"rejected".equalsIgnoreCase(step.getPerformedProcedureStepStatus())) {
				steps.put(step.getId(), step);
			}
		}
		if (procedure == null || steps.isEmpty()) {
			return new ResponseEntity<String>("No eligible procedure steps in notification", HttpStatus.CONFLICT);
		}

		OrthancConfiguration configuration = procedure.getOrthancConfiguration();
		studyService.fetchNewChangedStudiesByConfiguration(configuration);
		DicomStudy study = studyService.getDicomStudy(configuration, studyUid);
		if (study == null) {
			throw new IOException("The notified study could not be synchronized");
		}
		study = studyService.getDicomStudyForUpdate(study.getId());
		if (study == null) {
			throw new IOException("The notified study is no longer available");
		}
		if (study.getMrsPatient() != null && !procedure.getMrsPatient().getUuid().equals(study.getMrsPatient().getUuid())) {
			return new ResponseEntity<String>("The study is already linked to another patient", HttpStatus.CONFLICT);
		}
		if (!studyService.isStudyForPatient(study, procedure.getMrsPatient())) {
			return new ResponseEntity<String>("The Orthanc study does not match the requested patient", HttpStatus.CONFLICT);
		}

		String previousStudyUid = procedure.getStudyInstanceUID();
		for (RequestProcedureStep step : steps.values()) {
			stepService.updatePerformedProcedureStepStatus(step, "completed");
		}
		List<RequestProcedureStep> allSteps = stepService.getAllStepByRequestProcedure(procedure);
		ComparisonResult comparison = compareWorklistStudyData(procedure, allSteps, payload);
		assignRequestProceduredStudyToPatient(procedure, previousStudyUid, payload, comparison, study);
		procedure.setStudyInstanceUID(studyUid);
		boolean completed = !allSteps.isEmpty() && allSteps.stream().allMatch(step ->
		    "completed".equalsIgnoreCase(step.getPerformedProcedureStepStatus())
		        || "rejected".equalsIgnoreCase(step.getPerformedProcedureStepStatus()));
		procedure.setStatus(completed ? "completed" : "in progress");
		requestService.updateRequestStatus(procedure);
		return ResponseEntity.ok(comparison);
	}

	/**
	 * @param requestProcedure The procedure for requesting patient image data.
	 * @param payload The metadata of image study for comparison
	 * @param comparisonResult The result of comparing the metadata from the Image Study with that
	 *            from OpenMRS.
	 * @throws IOException
	 */
	private void assignRequestProceduredStudyToPatient(RequestProcedure requestProcedure, String previousStudyInstanceUID,
	        StudyUpdatePayload payload, ComparisonResult comparisonResult, DicomStudy study) throws IOException {
		DicomStudyService studyService = Context.getService(DicomStudyService.class);
		unlinkPreviousStudyIfUidChanged(studyService, requestProcedure.getOrthancConfiguration(),
		    requestProcedure.getMrsPatient(), previousStudyInstanceUID, payload.getStudyInfo().getStudyInstanceUID());
		studyService.updateLinkStatus(study, comparisonResult.getScore() == 100 ? 2 : 1);
		study.setComparisonResult(mapper.writeValueAsString(comparisonResult));
		studyService.setPatient(study, requestProcedure.getMrsPatient());
	}

	private void unlinkPreviousStudyIfUidChanged(DicomStudyService dicomStudyService, OrthancConfiguration config,
	        Patient patient, String previousStudyInstanceUID, String newStudyInstanceUID) {
		if (!isNotBlank(previousStudyInstanceUID) || !isNotBlank(newStudyInstanceUID)
		        || previousStudyInstanceUID.equals(newStudyInstanceUID)) {
			return;
		}
		
		DicomStudy previousStudy = dicomStudyService.getDicomStudy(config, previousStudyInstanceUID);
		if (previousStudy != null) {
			previousStudy = dicomStudyService.getDicomStudyForUpdate(previousStudy.getId());
		}
		if (previousStudy == null || previousStudy.getMrsPatient() == null || patient == null
		        || !patient.equals(previousStudy.getMrsPatient())) {
			return;
		}
		
		dicomStudyService.setPatient(previousStudy, null);
		dicomStudyService.updateLinkStatus(previousStudy, -1);
	}
	
	/**
	 * @param requestProcedure The procedure for requesting patient image data.
	 * @param stepList The procedure steps of the request procedure
	 * @param payload The metadata of image study for comparison
	 */
	private ComparisonResult compareWorklistStudyData (
            RequestProcedure requestProcedure,
            List<RequestProcedureStep> stepList,
            StudyUpdatePayload payload) {

        int score = 0;
        List<DicomDifference> diffs = new ArrayList<>();

        if (requestProcedure == null || payload == null || payload.getStudyInfo() == null) {
            return new ComparisonResult(score, diffs); // Nothing to compare
        }

        // 1. Study-level comparison
        String accessionDB = requestProcedure.getAccessionNumber();
        String accessionPayload = payload.getStudyInfo().getAccessionNumber();
        if (isNotBlank(accessionDB) && isNotBlank(accessionPayload) &&
                accessionDB.equalsIgnoreCase(accessionPayload)) {
            score += 10;
        } else {
            diffs.add(new DicomDifference("AccessionNumber", accessionDB, accessionPayload));
        }

        // referringPhysicianName
        String requestingPhysicianDB = requestProcedure.getRequestingPhysician();
        String requestingPhysicianPayload = payload.getStudyInfo().getReferringPhysicianName();
        if (hasTextMismatch(requestingPhysicianDB, requestingPhysicianPayload, FUZZY_THRESHOLD)) {
            diffs.add(new DicomDifference("RequestingPhysician", requestingPhysicianDB, requestingPhysicianPayload));
        } else {
            score += 10;
        }

        //2. Step-level comparison
        if (stepList != null && !stepList.isEmpty() && payload.getSeriesList() != null) {
            int stepScoreTotal = 0;
            int maxStepScorePerStep = 80;
            int normalizedStepMax = 80;

            List<RequestProcedureStep> eligibleSteps = stepList.stream()
                    .filter(step -> !"rejected".equalsIgnoreCase(step.getPerformedProcedureStepStatus()))
                    .collect(Collectors.toList());
            for (RequestProcedureStep step : eligibleSteps) {
                List<StudyUpdatePayload.SeriesEntry> entries = payload.getSeriesList().stream()
                        .filter(entry -> step.getId() != null && entry != null
                                && step.getId().toString().equals(entry.getScheduledProcedureStepID()))
                        .collect(Collectors.toList());
                if (entries.isEmpty()) {
                    continue;
                }
                int stepScore = maxStepScorePerStep;
                for (StudyUpdatePayload.SeriesEntry entry : entries) {
                    stepScore = Math.min(stepScore, compareProcedureStep(step, entry, diffs));
                }
                stepScoreTotal += stepScore;
            }

            int totalPossibleStepPoints = eligibleSteps.size() * maxStepScorePerStep;
            if (totalPossibleStepPoints > 0 ) {
                int normalizedStepScore = (int)((double) stepScoreTotal / totalPossibleStepPoints * normalizedStepMax);
                score += normalizedStepScore;
            }
        }
        return new ComparisonResult(score, diffs);
    }
	
    /** Compare every reported series; one matching series cannot hide another discrepancy. */
    private int compareProcedureStep(RequestProcedureStep step, StudyUpdatePayload.SeriesEntry entry,
            List<DicomDifference> diffs) {
        int stepScore = 0;

        // Extract entry components safely
        StudyUpdatePayload.InstanceInfo inst = entry.getInstanceInfo();
        String patientNameDB = getPatientNameDB(step);

        StudyUpdatePayload.SeriesInfo series = entry.getSeriesInfo();
        String patientNamePayload = inst != null
                ? inst.getPatientName()
                : null;
        String normalizedPatientNamePayload = patientNamePayload != null
                ? patientNamePayload.replace("^", " ").trim()
                : "";

        if (hasTextMismatch(patientNameDB, normalizedPatientNamePayload, FUZZY_THRESHOLD)) {
            diffs.add(new DicomDifference("PatientName", patientNameDB, normalizedPatientNamePayload, step.getId().toString()));
        } else {
            stepScore += 15;
        }

        // Patient ID
        Patient patient = step.getRequestProcedure() != null
                ? step.getRequestProcedure().getMrsPatient()
                : null;

        String patientIdDB = patient != null
                ? patient.getUuid()
                : null;

        String patientIdPayload = inst != null ? inst.getPatientID() : null;

        if (isNotBlank(patientIdPayload) && patientIdPayload.equalsIgnoreCase(patientIdDB)){
            stepScore += 10;
        } else {
            diffs.add(new DicomDifference("PatientID",  patientIdDB, patientIdPayload));
        }

        // Patient birthdate
        Date birthDate = patient != null && patient.getBirthdate() != null
                ? patient.getBirthdate()
                : null;

        String patientBirthDateDB = null;
        if (birthDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            patientBirthDateDB = sdf.format(birthDate);
        }

        String patientBirthDatePayload = entry.getInstanceInfo() != null
                ? entry.getInstanceInfo().getPatientBirthDate()
                : null;

        if (isNotBlank(patientBirthDatePayload) && patientBirthDatePayload.equalsIgnoreCase(patientBirthDateDB)) {
            stepScore += 15;
        } else {
            diffs.add(new DicomDifference("PatientBirthDate", patientBirthDateDB, patientBirthDatePayload));
        }

        // Modality
        String modalityDB = step.getModality();
        String modalityPayload = series != null ? series.getModality() : null;

        if (isNotBlank(modalityDB) && modalityDB.equalsIgnoreCase(modalityPayload)) {
            stepScore += 10;
        } else {
            diffs.add(new DicomDifference("Modality", modalityDB, modalityPayload, step.getId().toString()));
        }

        // Scheduled performing physician
        String scheduledPhysicianDB = step.getScheduledPerformingPhysician();
        String scheduledPhysicianPayload = inst != null ? inst.getScheduledPerformingPhysician() : null;
        if (hasTextMismatch(scheduledPhysicianDB, scheduledPhysicianPayload, FUZZY_THRESHOLD)) {
            diffs.add(new DicomDifference("ScheduledPerformingPhysician", scheduledPhysicianDB, scheduledPhysicianPayload, step.getId().toString()));
        } else {
            stepScore += 10;
        }

        // Requested procedure description
        String requestedProcedureDB = step.getRequestedProcedureDescription();
        String performedProcedurePayload = inst != null ? inst.getPerformedProcedureStepDescription() : null;
        if (hasTextMismatch(requestedProcedureDB, performedProcedurePayload, FUZZY_THRESHOLD)) {
            diffs.add(new DicomDifference("PerformedProcedureStepDescription", requestedProcedureDB, performedProcedurePayload, step.getId().toString()));
        } else {
            stepScore += 10;
        }

        // Station Name
        String stationDB = step.getStationName();
        String stationPayload = series != null ? series.getStationName() : null;

        if (hasTextMismatch(stationDB, stationPayload, FUZZY_THRESHOLD)) {
            diffs.add(new DicomDifference("StationName", stationDB, stationPayload, step.getId().toString()));
        } else {
            stepScore += 10;
        }
        return stepScore;
    }

	private boolean hasTextMismatch(String a, String b, int threshold) {
		if (isNotBlank(a) && isNotBlank(b)) {
			int score = FuzzySearch.tokenSetRatio(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT));
			return score < threshold;
		}
		return true;
	}
	
	/**
	 * @param step The procedure step of worklist request
	 * @return The retrieved patient name
	 */
	private static String getPatientNameDB(RequestProcedureStep step) {
		Patient patient = step.getRequestProcedure() != null ? step.getRequestProcedure().getMrsPatient() : null;
		
		String givenNameDB = patient != null && patient.getGivenName() != null ? patient.getGivenName().trim() : "";
		
		String familyNameDB = patient != null && patient.getFamilyName() != null ? patient.getFamilyName().trim() : "";
		return (givenNameDB + " " + familyNameDB).trim();
	}
	
	@RequestMapping(value = "/updateprocedurestepstatus", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
    @Transactional
    public ResponseEntity<?> updateProcedureStepStatus(
            @RequestParam(value="stepId") int stepId,
            @RequestParam(value="status") String status,
            HttpServletRequest request, HttpServletResponse response ) {

        RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
        if (stepId <= 0) {
            return new ResponseEntity<>("step ID is missing", HttpStatus.BAD_REQUEST);
        }
        if (status == null || !ALLOWED_STEP_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT))) {
            return new ResponseEntity<>("Invalid procedure step status", HttpStatus.BAD_REQUEST);
        }
        RequestProcedureStep step = requestProcedureStepService.getProcedureStep(stepId);
        if (step == null) {
            return new ResponseEntity<>("Procedure step not found", HttpStatus.NOT_FOUND);
        }
        requestProcedureStepService.updatePerformedProcedureStepStatus(step, status.trim().toLowerCase(Locale.ROOT));
        return new ResponseEntity<>("", HttpStatus.OK);
    }
	
	/**
	 * @param requestPostData The data for the new request procedure
	 * @return The response entity resulting from the request processing
	 */
	@RequestMapping(value = "/saverequest", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> saveRequestProcedure(@RequestBody Map<String, Object> requestPostData,
													  HttpServletRequest request, HttpServletResponse response ) {

		if (requestPostData == null || !(requestPostData.get("patientUuid") instanceof String)
		        || !(requestPostData.get("configurationId") instanceof Integer)
		        || (Integer) requestPostData.get("configurationId") <= 0
		        || !validText(requestPostData.get("accessionNumber"), 16, true)
		        || !validText(requestPostData.get("requestingPhysician"), 64, true)
		        || !validText(requestPostData.get("requestDescription"), 64, true)
		        || !validText(requestPostData.get("priority"), 16, true)) {
			return new ResponseEntity<>("Invalid procedure request", HttpStatus.BAD_REQUEST);
		}
		String accession = ((String) requestPostData.get("accessionNumber")).trim();

		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);

		PatientService patientService = Context.getPatientService();
		String patientUuid = (String) requestPostData.get("patientUuid");
		Patient patient = patientService.getPatientByUuid(patientUuid);
		if (patient == null) {
			return new ResponseEntity<>("Patient not found", HttpStatus.NOT_FOUND);
		}

		OrthancConfigurationService orthancConfigurationService = Context.getService(OrthancConfigurationService.class);
		OrthancConfiguration configuration = orthancConfigurationService.getOrthancConfiguration((Integer) requestPostData.get("configurationId"));
		if (configuration == null) {
			return new ResponseEntity<>("Orthanc configuration not found", HttpStatus.NOT_FOUND);
		}

		RequestProcedure newReq = new RequestProcedure();
		newReq.setStatus("scheduled");
		newReq.setMrsPatient(patient);
		newReq.setOrthancConfiguration(configuration);
		newReq.setAccessionNumber(accession);
		// A worklist response needs a stable DICOM UID before acquisition.
		newReq.setStudyInstanceUID("2.25." + new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16));
		newReq.setRequestingPhysician(((String) requestPostData.get("requestingPhysician")).trim());
		newReq.setRequestDescription(((String) requestPostData.get("requestDescription")).trim());
		newReq.setPriority(((String) requestPostData.get("priority")).trim());
		try{
			requestProcedureService.newRequest(newReq);
			return new ResponseEntity<>("", HttpStatus.OK);
		} catch (IOException e) {
			throw new APIException("The procedure could not be saved", e);
		}
	}
	
	/**
	 * @param stepPostData The data for the procedure step
	 * @return The response entity resulting from the request processing
	 */
	@RequestMapping(value = "/savestep", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> saveRequestProcedureStep(@RequestBody Map<String, Object> stepPostData,
													   HttpServletRequest request,
													   HttpServletResponse response ) {
		if (stepPostData == null || !(stepPostData.get("requestId") instanceof Integer)
		        || (Integer) stepPostData.get("requestId") <= 0
		        || !validTextFields(stepPostData, "modality", "aetTitle", "stepStartDate", "stepStartTime",
		            "scheduledPerformingPhysician", "requestedProcedureDescription", "stationName", "procedureStepLocation")
		        || !validText(stepPostData.get("scheduledPerformingPhysician"), 64, true)
		        || !validText(stepPostData.get("requestedProcedureDescription"), 64, true)
		        || !validText(stepPostData.get("stationName"), 16, false)
		        || !validText(stepPostData.get("procedureStepLocation"), 16, false)) {
			return new ResponseEntity<>("Invalid procedure step", HttpStatus.BAD_REQUEST);
		}
		String date;
		String time;
		String aeTitle;
		String modality = (String) stepPostData.get("modality");
		try {
			date = DicomWorklistValues.date((String) stepPostData.get("stepStartDate"));
			time = DicomWorklistValues.time((String) stepPostData.get("stepStartTime"));
			aeTitle = DicomWorklistValues.aeTitle((String) stepPostData.get("aetTitle"));
			if (modality == null || !modality.trim().matches("[A-Z0-9_]{1,16}")) {
				throw new IllegalArgumentException();
			}
		}
		catch (IllegalArgumentException e) {
			return new ResponseEntity<>("Invalid DICOM scheduling values", HttpStatus.BAD_REQUEST);
		}

		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);

		int requestId = (Integer) stepPostData.get("requestId");
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(requestId);
		if (requestProcedure == null) {
			return new ResponseEntity<>("Request procedure not found", HttpStatus.NOT_FOUND);
		}

		RequestProcedureStep newStep = new RequestProcedureStep();
		newStep.setRequestProcedure(requestProcedure);
		newStep.setModality(modality.trim());
		newStep.setAetTitle(aeTitle);
		newStep.setScheduledPerformingPhysician(((String) stepPostData.get("scheduledPerformingPhysician")).trim());
		newStep.setRequestedProcedureDescription(((String) stepPostData.get("requestedProcedureDescription")).trim());
		newStep.setPerformedProcedureStepStatus("scheduled");
		newStep.setStepStartDate(date);
		newStep.setStepStartTime(time);
		newStep.setStationName(normalizeOptionalText((String) stepPostData.get("stationName")));
		newStep.setProcedureStepLocation(normalizeOptionalText((String) stepPostData.get("procedureStepLocation")));

		try{
			requestProcedureStepService.newProcedureStep(newStep);
			requestProcedure.setStatus("in progress");
			requestProcedureService.updateRequestStatus(requestProcedure);

			return new ResponseEntity<>("", HttpStatus.OK);
		} catch (IOException e) {
			throw new APIException("The procedure step could not be saved", e);
		}
	}
	
	/**
	 * @param patientUuid The patient unique ID
	 * @return The response entity resulting from the request processing
	 */
	@RequestMapping(value = "/patientrequests", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> useRequestsByPatient(@RequestParam("patient") String patientUuid,
													   HttpServletRequest request, HttpServletResponse response ) {
        RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
        PatientService patientService = Context.getPatientService();
        Patient patient = patientService.getPatientByUuid(patientUuid);
        if (patient == null) {
            return new ResponseEntity<>("Patient not found", HttpStatus.NOT_FOUND);
        }

        List<RequestProcedure> requests = requestProcedureService.getRequestProcedureByPatient(patient);
        List<RequestProcedureResponse> requestProcedureResponseList = new ArrayList<>();
        for (RequestProcedure req : requests) {
            RequestProcedureResponse reqRes = RequestProcedureResponse.createResponse(req);
            requestProcedureResponseList.add(reqRes);
        }
        return new ResponseEntity<>(requestProcedureResponseList, HttpStatus.OK);
    }
	
	/**
	 * @param requestId The request procedure ID
	 * @return The retrieved procedure step list
	 */
	@RequestMapping(value = "/requeststep", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> useProcedureStep(@RequestParam("requestId") int requestId,
												   HttpServletRequest request,
												   HttpServletResponse response ) {
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		RequestProcedure req = requestProcedureService.getRequestProcedure(requestId);
		if (req == null) {
			return new ResponseEntity<>("Request procedure not found", HttpStatus.NOT_FOUND);
		}
		List<RequestProcedureStep> steps = requestProcedureStepService.getAllStepByRequestProcedure(req);

		List<ProcedureStepResponse> procedureStepResponseList = steps.stream().map(ProcedureStepResponse::createResponse).collect(Collectors.toList());
		return new ResponseEntity<>(procedureStepResponseList, HttpStatus.OK);
	}
	
	/**
	 * @param requestId The request procedure ID
	 * @return The response entity
	 */
	@RequestMapping(value = "/request", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> deleteRequest(@RequestParam(value="requestId") int requestId,
											   HttpServletRequest request,
											   HttpServletResponse response ) {
		RequestProcedureService requestProcedureService = Context.getService(RequestProcedureService.class);
		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		RequestProcedure requestProcedure = requestProcedureService.getRequestProcedure(requestId);
		if (requestProcedure == null) {
			return new ResponseEntity<>("Request procedure not found", HttpStatus.NOT_FOUND);
		}

		List<RequestProcedureStep> stepList = requestProcedureStepService.getAllStepByRequestProcedure(requestProcedure);
		if (!stepList.isEmpty()) {
			try {
				for (RequestProcedureStep step : stepList) {
					requestProcedureStepService.deleteProcedureStep(step);
				}
			} catch (IOException e) {
				throw new APIException("The procedure steps could not be deleted", e);
			}
		}
		try {
			requestProcedureService.deleteRequestProcedure(requestProcedure);
			return new ResponseEntity<>("", HttpStatus.OK);
		}catch (IOException e) {
			throw new APIException("The procedure could not be deleted", e);
		}
	}
	
	/**
	 * @param stepId The procedure step of the request
	 * @param request The request of procedure
	 * @return The response entity
	 */
	@RequestMapping(value = "/requeststep", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Authorized(ImagingConstants.PRIVILEGE_EDIT_WORKLIST)
	@Transactional
	public ResponseEntity<Object> deleteProcedureStep(@RequestParam(value="stepId") int stepId,
											   HttpServletRequest request,
											   HttpServletResponse response ) {

		RequestProcedureStepService requestProcedureStepService = Context.getService(RequestProcedureStepService.class);
		RequestProcedureStep step = requestProcedureStepService.getProcedureStep(stepId);
		if (step == null) {
			return new ResponseEntity<>("Procedure step not found", HttpStatus.NOT_FOUND);
		}

		try {
			requestProcedureStepService.deleteProcedureStep(step);
			return new ResponseEntity<>("", HttpStatus.OK);
		}catch (IOException e) {
			throw new APIException("The procedure step could not be deleted", e);
		}
	}
	private static boolean validTextFields(Map<String, Object> values, String... keys) {
		for (String key : keys) {
			Object value = values.get(key);
			if (value != null && !(value instanceof String)) {
				return false;
			}
		}
		return true;
	}

	private static boolean validText(Object value, int maxLength, boolean required) {
		if (value == null) {
			return !required;
		}
		if (!(value instanceof String)) {
			return false;
		}
		String original = (String) value;
		if (original.chars().anyMatch(Character::isISOControl)) {
			return false;
		}
		String text = original.trim();
		return (!required || !text.isEmpty()) && text.length() <= maxLength && text.indexOf('\\') < 0;
	}

	private static String normalizeOptionalText(String value) {
		return value == null ? null : value.trim();
	}

}
