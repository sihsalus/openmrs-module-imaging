package org.openmrs.module.imaging.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.RequestProcedureService;
import org.openmrs.module.imaging.api.RequestProcedureStepService;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the callback against the module's services and synthetic H2 fixtures. */
public class WorklistCallbackTest extends BaseWebControllerTest {

	private final RequestProcedureController controller = new RequestProcedureController();
	private RequestProcedureService requests;
	private RequestProcedureStepService steps;
	private DicomStudyService studies;
	private OrthancHttpClient client;
	private RequestProcedure procedure;
	private DicomStudy study;

	@Before
	public void setUp() throws Exception {
		executeDataSet("testRequestProcedureDataset.xml");
		executeDataSet("testRequestProcedureStepDataset.xml");
		requests = Context.getService(RequestProcedureService.class);
		steps = Context.getService(RequestProcedureStepService.class);
		studies = Context.getService(DicomStudyService.class);
		procedure = requests.getRequestProcedure(1);
		study = studies.getDicomStudy(1);
		client = mock(OrthancHttpClient.class);
		studies.setHttpClient(client);
		HttpURLConnection changes = mock(HttpURLConnection.class);
		when(client.createConnection(eq("GET"), anyString(), startsWith("/changes?"), anyString(), anyString()))
		    .thenReturn(changes);
		when(changes.getResponseCode()).thenReturn(200);
		when(changes.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(
		    "{\"Changes\":[],\"Last\":0,\"Done\":true}".getBytes(StandardCharsets.UTF_8)));
		mockStudyPatient(procedure.getMrsPatient().getUuid());
	}

	private void mockStudyPatient(String patientUuid) throws IOException {
		HttpURLConnection metadata = mock(HttpURLConnection.class);
		when(client.createConnection(eq("GET"), anyString(), eq("/studies/" + study.getOrthancStudyUID()),
		    anyString(), anyString())).thenReturn(metadata);
		when(metadata.getResponseCode()).thenReturn(200);
		String json = "{\"ID\":\"" + study.getOrthancStudyUID() + "\",\"MainDicomTags\":{\"StudyInstanceUID\":\""
		    + study.getStudyInstanceUID() + "\"},\"PatientMainDicomTags\":{\"PatientID\":\"" + patientUuid + "\"}}";
		when(metadata.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void matchingUuidAccessionAndEveryStepCompletesTheRequestWithScore100() throws Exception {
		StudyUpdatePayload payload = payload(1, 2, 3);
		ResponseEntity<?> response = callback(payload);
		assertEquals(200, response.getStatusCodeValue());
		ComparisonResult comparison = (ComparisonResult) response.getBody();
		assertEquals(100, comparison.getScore());
		assertTrue(comparison.getDifferences().isEmpty());
		assertEquals(2, study.getLinkStatus());
		assertEquals(procedure.getMrsPatient(), study.getMrsPatient());
		assertEquals("completed", procedure.getStatus());
		for (int id : new int[] { 1, 2, 3 }) {
			assertEquals("completed", steps.getProcedureStep(id).getPerformedProcedureStepStatus());
		}
		// A repeated Orthanc notification must retain the same association and score.
		assertEquals(200, callback(payload).getStatusCodeValue());
		assertEquals(2, study.getLinkStatus());
		assertEquals("completed", procedure.getStatus());
	}

	@Test
	public void partialNotificationKeepsUnreportedStepsScheduledAndRequiresReview() throws Exception {
		ResponseEntity<?> response = callback(payload(1));
		assertEquals(200, response.getStatusCodeValue());
		assertEquals("completed", steps.getProcedureStep(1).getPerformedProcedureStepStatus());
		assertEquals("scheduled", steps.getProcedureStep(2).getPerformedProcedureStepStatus());
		assertEquals("scheduled", steps.getProcedureStep(3).getPerformedProcedureStepStatus());
		assertEquals("in progress", procedure.getStatus());
		assertEquals(1, study.getLinkStatus());
		assertTrue(((ComparisonResult) response.getBody()).getScore() < 100);
	}

	@Test
	public void missingClinicalMetadataRequiresReviewWithoutBlockingAConfirmedIdentity() throws Exception {
		StudyUpdatePayload payload = payload(1, 2, 3);
		payload.getSeriesList().get(0).getInstanceInfo().setPatientBirthDate(null);
		ResponseEntity<?> response = callback(payload);
		assertEquals(200, response.getStatusCodeValue());
		assertEquals("completed", procedure.getStatus());
		ComparisonResult comparison = (ComparisonResult) response.getBody();
		assertTrue(comparison.getScore() < 100);
		assertFalse(comparison.getDifferences().isEmpty());
		assertEquals(1, study.getLinkStatus());
	}

	@Test
	public void persistsAllDiscrepanciesAcrossMultipleStepsWithoutTruncatingClinicalEvidence() throws Exception {
		StudyUpdatePayload payload = payload(1, 2, 3);
		String differentName = String.join("", Collections.nCopies(64, "X"));
		String differentDescription = String.join("", Collections.nCopies(64, "Z"));
		String scheduledDescription = String.join("", Collections.nCopies(64, "Y"));
		procedure.setRequestingPhysician(scheduledDescription);
		for (StudyUpdatePayload.SeriesEntry entry : payload.getSeriesList()) {
			RequestProcedureStep scheduled = steps.getProcedureStep(Integer.parseInt(entry.getScheduledProcedureStepID()));
			scheduled.setScheduledPerformingPhysician(scheduledDescription);
			scheduled.setRequestedProcedureDescription(scheduledDescription);
			entry.getInstanceInfo().setPatientName(differentName);
			entry.getInstanceInfo().setPatientBirthDate("19000101");
			entry.getInstanceInfo().setScheduledPerformingPhysician(differentName);
			entry.getInstanceInfo().setPerformedProcedureStepDescription(differentDescription);
		}
		ResponseEntity<?> response = callback(payload);
		assertEquals(200, response.getStatusCodeValue());
		ComparisonResult comparison = (ComparisonResult) response.getBody();
		assertEquals(13, comparison.getDifferences().size());
		String expectedJson = study.getComparisonResult();
		assertTrue("Fixture must exceed the previous production column limit", expectedJson.length() > 2000);
		int studyId = study.getId();
		DbSession session = applicationContext.getBean("dbSessionFactory", DbSessionFactory.class).getCurrentSession();
		session.flush();
		session.clear();
		DicomStudy reloaded = studies.getDicomStudy(studyId);
		assertEquals(1, reloaded.getLinkStatus());
		assertEquals(expectedJson, reloaded.getComparisonResult());
		assertEquals(13, new ObjectMapper().readTree(reloaded.getComparisonResult()).path("differences").size());
	}

	@Test
	public void rejectsWrongPatientOnALaterSeriesBeforeAnyMutation() throws Exception {
		for (String identity : new String[] { "1", "unknown-uuid", "", null }) {
			StudyUpdatePayload payload = payload(1, 2, 3);
			payload.getSeriesList().get(2).getInstanceInfo().setPatientID(identity);
			assertRejectedUnchanged(payload, 409);
		}
	}

	@Test
	public void rejectsWrongOrAbsentAccessionBeforeAnyMutation() throws Exception {
		for (String accession : new String[] { "OTHER", "", null }) {
			assertRejectedUnchanged(payload(accession, 1, 2, 3), 409);
		}
	}

	@Test
	public void rejectsConflictingInstanceStudyAndStepIdentifiers() throws Exception {
		StudyUpdatePayload wrongStudy = payload(1, 2);
		wrongStudy.getSeriesList().get(1).getInstanceInfo().setStudyInstanceUID("another-study");
		assertRejectedUnchanged(wrongStudy, 409);
		StudyUpdatePayload wrongStep = payload(1, 2);
		wrongStep.getSeriesList().get(1).getInstanceInfo().setScheduledProcedureStepID("1");
		assertRejectedUnchanged(wrongStep, 409);
	}

	@Test
	public void rejectsMalformedOrUnidentifiedSeriesAlongsideAnOtherwiseValidSeries() throws Exception {
		for (String id : new String[] { null, "", " ", "not-an-id", "2147483648" }) {
			StudyUpdatePayload payload = payload(1, 2);
			payload.getSeriesList().get(1).setScheduledProcedureStepID(id);
			assertRejectedUnchanged(payload, 400);
		}
		StudyUpdatePayload unknownStep = payload(1, 2);
		unknownStep.getSeriesList().get(1).setScheduledProcedureStepID("999999");
		assertRejectedUnchanged(unknownStep, 404);
		StudyUpdatePayload nullEntry = payload(1, 2);
		nullEntry.getSeriesList().set(1, null);
		assertRejectedUnchanged(nullEntry, 400);
		StudyUpdatePayload nullInstance = payload(1, 2);
		nullInstance.getSeriesList().get(1).setInstanceInfo(null);
		assertRejectedUnchanged(nullInstance, 409);
	}

	@Test
	public void rejectsMissingTopLevelData() throws Exception {
		assertRejectedUnchanged(null, 400);
		assertRejectedUnchanged(new StudyUpdatePayload(), 400);
		StudyUpdatePayload payload = payload(1);
		payload.setSeriesList(null);
		assertRejectedUnchanged(payload, 400);
		payload.setSeriesList(Collections.emptyList());
		assertRejectedUnchanged(payload, 400);
		payload = payload(1);
		payload.getStudyInfo().setStudyInstanceUID(" ");
		assertRejectedUnchanged(payload, 400);
	}

	@Test
	public void rejectsSeriesFromDifferentRequestsForTheSamePatient() throws Exception {
		RequestProcedure otherRequest = requests.getRequestProcedure(2);
		steps.getProcedureStep(2).setRequestProcedure(otherRequest);
		assertRejectedUnchanged(payload(1, 2), 409);
	}

	@Test
	public void duplicateSeriesCannotHideAConflictingPatient() throws Exception {
		StudyUpdatePayload payload = payload(1, 1);
		payload.getSeriesList().get(1).getInstanceInfo().setPatientID("another-patient");
		assertRejectedUnchanged(payload, 409);
	}

	@Test
	public void aLaterSeriesDiscrepancyCannotBeHiddenByAnEarlierMatchingSeries() throws Exception {
		StudyUpdatePayload payload = payload(1, 2, 3, 1);
		payload.getSeriesList().get(3).getInstanceInfo().setPatientBirthDate("19000101");
		ResponseEntity<?> response = callback(payload);
		assertEquals(200, response.getStatusCodeValue());
		assertTrue(((ComparisonResult) response.getBody()).getScore() < 100);
		assertEquals(1, study.getLinkStatus());
	}

	@Test
	public void multipleMatchingSeriesForTheSameStepRetainACompleteMatch() throws Exception {
		ResponseEntity<?> response = callback(payload(1, 2, 3, 1));
		assertEquals(200, response.getStatusCodeValue());
		assertEquals(100, ((ComparisonResult) response.getBody()).getScore());
		assertEquals(2, study.getLinkStatus());
	}

	@Test
	public void rejectedUnreportedStepsDoNotPenalizeACompleteStudy() throws Exception {
		steps.updatePerformedProcedureStepStatus(steps.getProcedureStep(3), "rejected");
		ResponseEntity<?> response = callback(payload(1, 2));
		assertEquals(200, response.getStatusCodeValue());
		assertEquals(100, ((ComparisonResult) response.getBody()).getScore());
		assertEquals(2, study.getLinkStatus());
		assertEquals("completed", procedure.getStatus());
		assertEquals("rejected", steps.getProcedureStep(3).getPerformedProcedureStepStatus());
	}

	@Test
	public void neverReopensRejectedSteps() throws Exception {
		steps.updatePerformedProcedureStepStatus(steps.getProcedureStep(3), "rejected");
		assertEquals(200, callback(payload(1, 2, 3)).getStatusCodeValue());
		assertEquals("rejected", steps.getProcedureStep(3).getPerformedProcedureStepStatus());
		assertEquals("completed", procedure.getStatus());
	}

	@Test
	public void allRejectedStepsDoNotAssociateTheStudyOrCallOrthanc() throws Exception {
		for (int id : new int[] { 1, 2, 3 }) {
			steps.updatePerformedProcedureStepStatus(steps.getProcedureStep(id), "rejected");
		}
		assertEquals(409, callback(payload(1, 2, 3)).getStatusCodeValue());
		verifyZeroInteractions(client);
		assertEquals("in progress", procedure.getStatus());
		assertEquals(0, study.getLinkStatus());
	}

	@Test
	public void refusesAStudyAlreadyLinkedToAnotherPatient() throws Exception {
		studies.setPatient(study, Context.getPatientService().getPatient(2));
		assertEquals(409, callback(payload(1, 2, 3)).getStatusCodeValue());
		assertEquals(Integer.valueOf(2), study.getMrsPatient().getId());
		assertScheduledSteps();
		assertEquals("in progress", procedure.getStatus());
	}

	@Test
	public void aValidCallbackCannotAutoLinkAnOrthancStudyBelongingToAnotherPatient() throws Exception {
		mockStudyPatient(Context.getPatientService().getPatient(2).getUuid());
		assertEquals(409, callback(payload(1, 2, 3)).getStatusCodeValue());
		assertScheduledSteps();
		assertEquals("in progress", procedure.getStatus());
		assertEquals(0, study.getLinkStatus());
		assertEquals(procedure.getMrsPatient(), study.getMrsPatient());
		verify(client).createConnection(eq("GET"), anyString(), eq("/studies/" + study.getOrthancStudyUID()),
		    anyString(), anyString());
	}

	@Test
	public void missingSynchronizedStudyDoesNotCompleteAnyStep() throws Exception {
		StudyUpdatePayload payload = payload(1, 2, 3);
		payload.getStudyInfo().setStudyInstanceUID("not-synchronized");
		for (StudyUpdatePayload.SeriesEntry entry : payload.getSeriesList()) {
			entry.getInstanceInfo().setStudyInstanceUID("not-synchronized");
		}
		assertThrows(IOException.class, () -> callback(payload));
		assertScheduledSteps();
		assertEquals("studyUID555", procedure.getStudyInstanceUID());
		assertEquals("in progress", procedure.getStatus());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void exportedWorklistUsesPatientUuidAndDicomDateAndTimeFormats() {
		ResponseEntity<Object> response = controller.useRequestProcedures("all", new MockHttpServletRequest(),
		    new MockHttpServletResponse());
		assertEquals(200, response.getStatusCodeValue());
		List<Map<String, Object>> exported = (List<Map<String, Object>>) response.getBody();
		Map<String, Object> request = exported.stream().filter(row -> "1".equals(row.get("RequestedProcedureID")))
		    .findFirst().get();
		assertEquals(procedure.getMrsPatient().getUuid(), request.get("PatientID"));
		assertEquals("20140828", request.get("PatientBirthDate"));
		assertEquals("ISO_IR 192", request.get("SpecificCharacterSet"));
		List<Map<String, Object>> sequence = (List<Map<String, Object>>) request.get("ScheduledProcedureStepSequence");
		Map<String, Object> first = sequence.stream().filter(row -> "1".equals(row.get("ScheduledProcedureStepID")))
		    .findFirst().get();
		assertEquals("20240704", first.get("ScheduledProcedureStepStartDate"));
		assertEquals("093000", first.get("ScheduledProcedureStepStartTime"));
	}

	private void assertRejectedUnchanged(StudyUpdatePayload payload, int status) throws IOException {
		assertEquals(status, callback(payload).getStatusCodeValue());
		verifyZeroInteractions(client);
		assertScheduledSteps();
		assertEquals("in progress", procedure.getStatus());
		assertEquals("studyUID555", procedure.getStudyInstanceUID());
		assertEquals(0, study.getLinkStatus());
		assertEquals(procedure.getMrsPatient(), study.getMrsPatient());
	}

	private void assertScheduledSteps() {
		for (int id : new int[] { 1, 2, 3 }) {
			assertEquals("scheduled", steps.getProcedureStep(id).getPerformedProcedureStepStatus());
		}
	}

	private ResponseEntity<?> callback(StudyUpdatePayload payload) throws IOException {
		return controller.updateRequestStatus(new MockHttpServletRequest(), new MockHttpServletResponse(), payload);
	}

	private StudyUpdatePayload payload(int... ids) {
		return payload(procedure.getAccessionNumber(), ids);
	}

	private StudyUpdatePayload payload(String accession, int... ids) {
		Map<String, Object> info = new HashMap<>();
		info.put("accessionNumber", accession);
		info.put("studyInstanceUID", procedure.getStudyInstanceUID());
		info.put("referringPhysicianName", procedure.getRequestingPhysician());
		List<Map<String, Object>> entries = new ArrayList<>();
		for (int id : ids) {
			RequestProcedureStep step = steps.getProcedureStep(id);
			Map<String, Object> instance = new HashMap<>();
			instance.put("patientID", procedure.getMrsPatient().getUuid());
			instance.put("patientName", procedure.getMrsPatient().getGivenName() + "^"
			    + procedure.getMrsPatient().getFamilyName());
			instance.put("patientBirthDate", new SimpleDateFormat("yyyyMMdd").format(procedure.getMrsPatient().getBirthdate()));
			instance.put("scheduledProcedureStepID", Integer.toString(id));
			instance.put("studyInstanceUID", procedure.getStudyInstanceUID());
			instance.put("scheduledPerformingPhysician", step.getScheduledPerformingPhysician());
			instance.put("performedProcedureStepDescription", step.getRequestedProcedureDescription());
			Map<String, Object> series = new HashMap<>();
			series.put("modality", step.getModality());
			series.put("stationName", step.getStationName());
			Map<String, Object> entry = new HashMap<>();
			entry.put("scheduledProcedureStepID", Integer.toString(id));
			entry.put("instanceInfo", instance);
			entry.put("seriesInfo", series);
			entries.add(entry);
		}
		Map<String, Object> body = new HashMap<>();
		body.put("studyInfo", info);
		body.put("seriesList", entries);
		return new ObjectMapper().convertValue(body, StudyUpdatePayload.class);
	}
}
