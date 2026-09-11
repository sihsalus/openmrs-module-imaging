package org.openmrs.module.imaging.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.Patient;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.impl.DicomStudyServiceImpl;
import org.openmrs.module.imaging.api.study.DicomStudy;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StudyResourceAccessTest {

	private final DicomStudyServiceImpl service = new DicomStudyServiceImpl();
	private final OrthancHttpClient client = mock(OrthancHttpClient.class);
	private final DicomStudyDao dao = mock(DicomStudyDao.class);
	private final OrthancConfiguration config = new OrthancConfiguration();
	private final DicomStudy study = new DicomStudy();

	@Before
	public void setUp() {
		config.setOrthancBaseUrl("http://synthetic-orthanc.invalid");
		config.setOrthancUsername("");
		config.setOrthancPassword("");
		study.setId(1);
		study.setOrthancConfiguration(config);
		study.setOrthancStudyUID("study-1");
		study.setStudyInstanceUID("1.2.3");
		service.setHttpClient(client);
		service.setDao(dao);
	}

	@Test
	public void seriesDeletionChecksItsParentStudyBeforeSendingDelete() throws Exception {
		Response metadata = response("GET", "/series/series-1", 200,
		    "{\"ID\":\"series-1\",\"ParentStudy\":\"other-study\"}");
		IOException error = assertThrows(IOException.class, () -> service.deleteSeries("series-1", study));
		assertFalse(error.getMessage().contains("other-study"));
		verify(client, never()).createConnection(eq("DELETE"), anyString(), anyString(), anyString(), anyString());
		assertTrue(metadata.input.closed);
		verify(metadata.connection).disconnect();
	}

	@Test
	public void deletesAMatchingSeriesAndClosesBothConnections() throws Exception {
		Response metadata = seriesMetadata("study-1");
		Response deletion = response("DELETE", "/series/series-1", 200, "{}");
		service.deleteSeries("series-1", study);
		assertTrue(metadata.input.closed);
		verify(metadata.connection).disconnect();
		verify(deletion.connection).disconnect();
	}

	@Test
	public void previewChecksBothInstanceParentSeriesAndSeriesParentStudyBeforeReturningPixels() throws Exception {
		Response instance = instanceMetadata();
		Response series = seriesMetadata("other-study");
		assertThrows(IOException.class, () -> service.fetchInstancePreview("instance-1", study));
		verify(client, never()).createConnection(eq("GET"), anyString(), endsWith("/preview"), anyString(), anyString());
		assertTrue(instance.input.closed);
		assertTrue(series.input.closed);
		verify(instance.connection).disconnect();
		verify(series.connection).disconnect();
	}

	@Test
	public void previewReturnsOnlySupportedImageTypesAndClosesThePixelStream() throws Exception {
		instanceMetadata();
		seriesMetadata("study-1");
		Response preview = response("GET", "/instances/instance-1/preview", 200, "synthetic-pixels");
		when(preview.connection.getContentType()).thenReturn("image/png");
		DicomStudyService.PreviewResult result = service.fetchInstancePreview("instance-1", study);
		assertEquals("image/png", result.contentType);
		assertArrayEquals("synthetic-pixels".getBytes(StandardCharsets.UTF_8), result.data);
		assertTrue(preview.input.closed);
		verify(preview.connection).disconnect();
	}

	@Test
	public void refusesHtmlDisguisedAsAPreview() throws Exception {
		instanceMetadata();
		seriesMetadata("study-1");
		Response preview = response("GET", "/instances/instance-1/preview", 200, "<html>synthetic</html>");
		when(preview.connection.getContentType()).thenReturn("text/html");
		assertThrows(IOException.class, () -> service.fetchInstancePreview("instance-1", study));
		verify(preview.connection, never()).getInputStream();
		verify(preview.connection).disconnect();
	}

	@Test
	public void refusesMissingAndInconsistentParentMetadata() throws Exception {
		for (String json : new String[] { "null", "[]", "{}", "not-json",
		    "{\"ID\":\"other-series\",\"ParentStudy\":\"study-1\"}", "{\"ID\":\"series-1\"}" }) {
			Response metadata = response("GET", "/series/series-1", 200, json);
			assertThrows(IOException.class, () -> service.deleteSeries("series-1", study));
			assertTrue(metadata.input.closed);
			verify(metadata.connection).disconnect();
		}
		verify(client, never()).createConnection(eq("DELETE"), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	public void rejectsPathTraversalAndAbsentIdentifiersBeforeOpeningAConnection() {
		for (String id : new String[] { null, "", "../studies/study-2", "id?expand", "id/path", "%2f" }) {
			assertThrows(IOException.class, () -> service.deleteSeries(id, study));
			assertThrows(IOException.class, () -> service.fetchInstancePreview(id, study));
		}
		verifyZeroInteractions(client);
	}

	@Test
	public void rejectsUnscopedQueriesBeforeOpeningAConnection() {
		for (String id : new String[] { null, "", " ", "*", "1.?", "1\\2", "1.2\n" }) {
			assertThrows(IOException.class, () -> service.fetchInstances(id, study));
		}
		verifyZeroInteractions(client);
	}

	@Test
	public void serializesStudyAndSeriesQueryValuesWithoutAllowingJsonInjection() throws Exception {
		String seriesUid = "1.2.4\",\"PatientID\":\"other";
		Response search = response("POST", "/tools/find", 200, "[]");
		assertTrue(service.fetchInstances(seriesUid, study).isEmpty());
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(client).sendOrthancQuery(eq(search.connection), body.capture());
		JsonNode query = new ObjectMapper().readTree(body.getValue());
		assertEquals("Instance", query.path("Level").getTextValue());
		assertTrue(query.path("Expand").getBooleanValue());
		assertEquals(2, query.path("Query").size());
		assertEquals("1.2.3", query.path("Query").path("StudyInstanceUID").getTextValue());
		assertEquals(seriesUid, query.path("Query").path("SeriesInstanceUID").getTextValue());
		assertTrue(search.input.closed);
		verify(search.connection).disconnect();
	}

	@Test
	public void searchRejectsMalformedBodiesAndClosesStreams() throws Exception {
		for (String json : new String[] { "", "null", "{}", "not-json", "[null]", "[{}]" }) {
			Response search = response("POST", "/tools/find", 200, json);
			assertThrows(IOException.class, () -> service.fetchSeries(study));
			assertTrue(search.input.closed);
			verify(search.connection).disconnect();
		}
	}

	@Test
	public void networkFailuresCloseConnectionsAndDoNotDeleteTheLocalStudy() throws Exception {
		Response deletion = response("DELETE", "/studies/study-1", 500, "synthetic-private-content");
		IOException error = assertThrows(IOException.class, () -> service.deleteStudy(study));
		assertEquals("The study could not be deleted", error.getMessage());
		verify(deletion.connection).disconnect();
		verifyZeroInteractions(dao);
		Response search = response("POST", "/tools/find", 200, "[]");
		doThrow(new SocketTimeoutException("synthetic-private-content")).when(client)
		    .sendOrthancQuery(eq(search.connection), anyString());
		error = assertThrows(IOException.class, () -> service.fetchInstances("1.2.4", study));
		assertEquals("The imaging resources could not be retrieved", error.getMessage());
		verify(search.connection).disconnect();
	}

	@Test
	public void studyDeletionIsIdempotentWhenOrthancAlreadyRemovedTheResource() throws Exception {
		Response deletion = response("DELETE", "/studies/study-1", 404, "{}");
		service.deleteStudy(study);
		verify(dao).remove(study);
		verify(deletion.connection).disconnect();
	}

	@Test
	public void fullSynchronizationUsesTheStudyQueryLevelAndClosesItsResponse() throws Exception {
		Response search = response("POST", "/tools/find", 200, "[]");
		service.fetchAllStudies(config);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(client).sendOrthancQuery(eq(search.connection), body.capture());
		JsonNode request = new ObjectMapper().readTree(body.getValue());
		assertEquals("Study", request.path("Level").getTextValue());
		assertEquals(0, request.path("Query").size());
		assertTrue(search.input.closed);
		verify(search.connection).disconnect();
	}

	@Test
	public void aFailedChangeBatchClosesEveryConnectionWithoutAdvancingItsCursor() throws Exception {
		config.setLastChangedIndex(5);
		Response page = response("GET", "/changes?limit=1000&since=5", 200,
		    "{\"Changes\":[{\"ChangeType\":\"NewStudy\",\"ID\":\"study-1\"},"
		    + "{\"ChangeType\":\"StableStudy\",\"ID\":\"study-2\"}],\"Last\":7,\"Done\":true}");
		Response first = response("GET", "/studies/study-1", 200,
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"}}");
		Response second = response("GET", "/studies/study-2", 503, "private-synthetic-content");
		assertThrows(IOException.class, () -> service.fetchNewChangedStudiesByConfiguration(config));
		assertEquals(Integer.valueOf(5), config.getLastChangedIndex());
		assertTrue(page.input.closed);
		assertTrue(first.input.closed);
		verify(page.connection).disconnect();
		verify(first.connection).disconnect();
		verify(second.connection).disconnect();
		verify(client, times(3)).createConnection(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	public void malformedAndStalledChangePagesCannotAdvanceTheCursorOrLoopForever() throws Exception {
		config.setLastChangedIndex(5);
		for (String json : new String[] { "null", "{}", "not-json",
		    "{\"Changes\":[],\"Last\":5,\"Done\":false}",
		    "{\"Changes\":[],\"Last\":4,\"Done\":true}",
		    "{\"Changes\":[],\"Last\":2147483648,\"Done\":true}" }) {
			Response page = response("GET", "/changes?limit=1000&since=5", 200, json);
			assertThrows(IOException.class, () -> service.fetchNewChangedStudiesByConfiguration(config));
			assertEquals(Integer.valueOf(5), config.getLastChangedIndex());
			assertTrue(page.input.closed);
			verify(page.connection).disconnect();
		}
		verifyZeroInteractions(dao);
	}

	@Test
	public void missingStudyIdentityIsRejectedBeforeAnyDatabaseAccess() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		for (String json : new String[] { "null", "[]", "{}", "{\"ID\":\"study-1\"}",
		    "{\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"}}",
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\" \"}}" }) {
			JsonNode data = mapper.readTree(json);
			assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateStudy(config, data));
		}
		assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateStudy(config, null));
		verifyZeroInteractions(dao);
	}

	@Test
	public void synchronizesPatientSexUsingTheOrthancDicomTag() throws Exception {
		JsonNode metadata = new ObjectMapper().readTree("{\"ID\":\"study-1\","
		    + "\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"},\"PatientMainDicomTags\":{\"PatientSex\":\"F\"}}");
		service.createOrUpdateStudy(config, metadata);
		ArgumentCaptor<DicomStudy> saved = ArgumentCaptor.forClass(DicomStudy.class);
		verify(dao).save(saved.capture());
		assertEquals("F", saved.getValue().getGender());
	}

	@Test
	public void automaticLinkingChecksTheCurrentOrthancPatientAndStudyIdentity() throws Exception {
		Patient patient = new Patient();
		patient.setUuid("synthetic-patient");
		Response matching = response("GET", "/studies/study-1", 200,
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"},"
		    + "\"PatientMainDicomTags\":{\"PatientID\":\"synthetic-patient\"}}");
		assertTrue(service.isStudyForPatient(study, patient));
		assertTrue(matching.input.closed);
		verify(matching.connection).disconnect();
		for (String json : new String[] {
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"}}",
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"},\"PatientMainDicomTags\":{\"PatientID\":\"other\"}}",
		    "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\"other\"},\"PatientMainDicomTags\":{\"PatientID\":\"synthetic-patient\"}}"
		}) {
			Response metadata = response("GET", "/studies/study-1", 200, json);
			assertFalse(service.isStudyForPatient(study, patient));
			assertTrue(metadata.input.closed);
			verify(metadata.connection).disconnect();
		}
		Response wrongResource = response("GET", "/studies/study-1", 200, "{\"ID\":\"another-resource\"}");
		assertThrows(IOException.class, () -> service.isStudyForPatient(study, patient));
		assertTrue(wrongResource.input.closed);
		verify(wrongResource.connection).disconnect();
	}

	private Response instanceMetadata() throws IOException {
		return response("GET", "/instances/instance-1", 200,
		    "{\"ID\":\"instance-1\",\"ParentSeries\":\"series-1\"}");
	}

	private Response seriesMetadata(String parent) throws IOException {
		return response("GET", "/series/series-1", 200,
		    "{\"ID\":\"series-1\",\"ParentStudy\":\"" + parent + "\"}");
	}

	private Response response(String method, String path, int status, String body) throws IOException {
		Response response = new Response(body);
		when(client.createConnection(method, config.getOrthancBaseUrl(), path, "", "")).thenReturn(response.connection);
		when(response.connection.getResponseCode()).thenReturn(status);
		when(response.connection.getInputStream()).thenReturn(response.input);
		return response;
	}

	private static class Response {
		final HttpURLConnection connection = mock(HttpURLConnection.class);
		final TrackingInput input;

		Response(String body) {
			input = new TrackingInput(body);
		}
	}

	private static class TrackingInput extends ByteArrayInputStream {
		boolean closed;

		TrackingInput(String value) {
			super(value.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
