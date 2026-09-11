package org.openmrs.module.imaging.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.impl.DicomStudyServiceImpl;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.Patient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UploadValidationTest {

	@Test
	public void rejectsArchivesAndEmptyFilesBeforeOpeningOrthanc() throws Exception {
		byte[][] files = { {}, { 80, 75, 3, 4, 1 }, { 80, 75, 5, 6 }, { 80, 75, 7, 8 } };
		for (byte[] file : files) {
			OrthancHttpClient client = mock(OrthancHttpClient.class);
			DicomStudyServiceImpl service = new DicomStudyServiceImpl();
			service.setHttpClient(client);
			assertThrows(IOException.class, () -> service.uploadFile(new OrthancConfiguration(),
			    new ByteArrayInputStream(file)));
			verifyZeroInteractions(client);
		}
	}

	@Test
	public void rejectsANullSourceBeforeOpeningOrthanc() {
		OrthancHttpClient client = mock(OrthancHttpClient.class);
		DicomStudyServiceImpl service = new DicomStudyServiceImpl();
		service.setHttpClient(client);
		assertThrows(IOException.class, () -> service.uploadFile(new OrthancConfiguration(), null));
		verifyZeroInteractions(client);
	}

	@Test
	public void rejectsUnconfirmedOrMalformedSuccessResponsesAndClosesConnection() throws Exception {
		String[] responses = { "", "null", "[]", "{}", "not-json",
		    "{\"Status\":\"Failure\",\"ParentStudy\":\"study-1\"}",
		    "{\"Status\":\"Success\"}", "{\"Status\":\"Success\",\"ParentStudy\":\"../patients\"}" };
		for (String response : responses) {
			OrthancConfiguration config = new OrthancConfiguration();
			config.setOrthancBaseUrl("http://synthetic-orthanc.invalid");
			config.setOrthancUsername("");
			config.setOrthancPassword("");
			OrthancHttpClient client = mock(OrthancHttpClient.class);
			HttpURLConnection connection = mock(HttpURLConnection.class);
			when(client.createConnection("POST", config.getOrthancBaseUrl(), "/instances", "", ""))
			    .thenReturn(connection);
			when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			when(connection.getResponseCode()).thenReturn(200);
			when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
			DicomStudyServiceImpl service = new DicomStudyServiceImpl();
			service.setHttpClient(client);
			assertThrows(IOException.class, () -> service.uploadFile(config,
			    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
			verify(connection).disconnect();
			verify(client, times(1)).createConnection(anyString(), anyString(), anyString(), anyString(), anyString());
		}
	}

	@Test
	public void disconnectsWhenWritingFailsWithoutRetrying() throws Exception {
		OrthancConfiguration config = new OrthancConfiguration();
		config.setOrthancBaseUrl("http://synthetic-orthanc.invalid");
		config.setOrthancUsername("");
		config.setOrthancPassword("");
		OrthancHttpClient client = mock(OrthancHttpClient.class);
		HttpURLConnection connection = mock(HttpURLConnection.class);
		when(client.createConnection("POST", config.getOrthancBaseUrl(), "/instances", "", "")).thenReturn(connection);
		when(connection.getOutputStream()).thenThrow(new IOException("synthetic disconnection"));
		DicomStudyServiceImpl service = new DicomStudyServiceImpl();
		service.setHttpClient(client);
		assertThrows(IOException.class, () -> service.uploadFile(config,
		    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
		verify(connection).disconnect();
		verify(connection, never()).getResponseCode();
	}

	@Test
	public void acceptsStoredAndAlreadyStoredResponsesWithoutResettingTheClinicalAssociation() throws Exception {
		for (String status : new String[] { "Success", "AlreadyStored" }) {
			UploadFixture fixture = new UploadFixture(status);
			DicomStudy stored = new DicomStudy();
			stored.setId(1);
			stored.setStudyInstanceUID("1.2.3");
			stored.setOrthancStudyUID("study-1");
			Patient owner = new Patient();
			owner.setUuid("synthetic-patient-uuid");
			stored.setMrsPatient(owner);
			stored.setLinkStatus(2);
			stored.setComparisonResult("{\"score\":100,\"differences\":[]}");
			when(fixture.dao.getByStudyInstanceUID(fixture.config, "1.2.3")).thenReturn(stored);
			when(fixture.dao.getForUpdate(1)).thenReturn(stored);
			TrackingInputStream metadata = fixture.metadata("{\"ID\":\"study-1\","
			    + "\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\",\"StudyDescription\":\"Updated metadata\"}}");
			byte[] bytes = { 1, 2, 3, 4, 5 };
			// A stream is allowed to return fewer than four bytes while reading the prefix.
			InputStream shortReads = new ByteArrayInputStream(bytes) {
				@Override
				public synchronized int read(byte[] buffer, int offset, int length) {
					return super.read(buffer, offset, Math.min(length, 1));
				}
			};
			DicomStudyService.UploadResult result = fixture.service.uploadFile(fixture.config, shortReads);
			assertSame(stored, result.study);
			assertSame(owner, stored.getMrsPatient());
			assertEquals(2, stored.getLinkStatus());
			assertEquals("{\"score\":100,\"differences\":[]}", stored.getComparisonResult());
			assertEquals("Updated metadata", stored.getStudyDescription());
			assertEquals("study-1", result.orthancStudyUID);
			assertEquals("1.2.3", result.studyInstanceUID);
			assertArrayEquals(bytes, fixture.output.toByteArray());
			assertTrue(fixture.output.closed);
			assertTrue(fixture.uploadResponse.closed);
			assertTrue(metadata.closed);
			verify(fixture.upload).setChunkedStreamingMode(65536);
			verify(fixture.upload).disconnect();
			verify(fixture.studyConnection).disconnect();
			verify(fixture.dao).save(stored);
			verify(fixture.client, times(2)).createConnection(anyString(), anyString(), anyString(), anyString(), anyString());
		}
	}

	@Test
	public void rejectsMissingOrInconsistentStudyMetadataWithoutPersistingIt() throws Exception {
		String[] invalid = { "", "null", "[]", "{}", "not-json",
		    "{\"ID\":\"other-study\",\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3\"}}",
		    "{\"ID\":\"study-1\"}", "{\"ID\":\"study-1\",\"MainDicomTags\":{\"StudyInstanceUID\":\" \"}}" };
		for (String response : invalid) {
			UploadFixture fixture = new UploadFixture("Success");
			TrackingInputStream metadata = fixture.metadata(response);
			assertThrows(IOException.class, () -> fixture.service.uploadFile(fixture.config,
			    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
			verifyZeroInteractions(fixture.dao);
			assertTrue(metadata.closed);
			verify(fixture.upload).disconnect();
			verify(fixture.studyConnection).disconnect();
		}
	}

	@Test
	public void doesNotFollowOrRetryUnconfirmedHttpResponses() throws Exception {
		for (int status : new int[] { 302, 400, 401, 403, 500, 503 }) {
			UploadFixture fixture = new UploadFixture("Success");
			when(fixture.upload.getResponseCode()).thenReturn(status);
			assertThrows(IOException.class, () -> fixture.service.uploadFile(fixture.config,
			    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
			verify(fixture.upload, never()).getInputStream();
			verify(fixture.upload).disconnect();
			verifyZeroInteractions(fixture.studyConnection, fixture.dao);
		}
	}

	@Test
	public void disconnectsBothConnectionsWhenStudySynchronizationFails() throws Exception {
		UploadFixture fixture = new UploadFixture("AlreadyStored");
		when(fixture.studyConnection.getResponseCode()).thenThrow(new java.net.SocketTimeoutException());
		assertThrows(IOException.class, () -> fixture.service.uploadFile(fixture.config,
		    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
		verify(fixture.upload).disconnect();
		verify(fixture.studyConnection).disconnect();
		verifyZeroInteractions(fixture.dao);
	}

	@Test
	public void rejectsNullResponseStreamsWithoutPersistingOrConfirmingTheStudy() throws Exception {
		UploadFixture noUploadResponse = new UploadFixture("Success");
		when(noUploadResponse.upload.getInputStream()).thenReturn(null);
		assertThrows(IOException.class, () -> noUploadResponse.service.uploadFile(noUploadResponse.config,
		    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
		verify(noUploadResponse.upload).disconnect();
		verifyZeroInteractions(noUploadResponse.studyConnection, noUploadResponse.dao);

		UploadFixture noMetadata = new UploadFixture("Success");
		when(noMetadata.studyConnection.getResponseCode()).thenReturn(200);
		when(noMetadata.studyConnection.getInputStream()).thenReturn(null);
		assertThrows(IOException.class, () -> noMetadata.service.uploadFile(noMetadata.config,
		    new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })));
		verify(noMetadata.upload).disconnect();
		verify(noMetadata.studyConnection).disconnect();
		verifyZeroInteractions(noMetadata.dao);
	}

	private static class UploadFixture {
		final OrthancConfiguration config = new OrthancConfiguration();
		final OrthancHttpClient client = mock(OrthancHttpClient.class);
		final DicomStudyDao dao = mock(DicomStudyDao.class);
		final HttpURLConnection upload = mock(HttpURLConnection.class);
		final HttpURLConnection studyConnection = mock(HttpURLConnection.class);
		final DicomStudyServiceImpl service = new DicomStudyServiceImpl();
		final TrackingOutputStream output = new TrackingOutputStream();
		final TrackingInputStream uploadResponse;

		UploadFixture(String status) throws IOException {
			config.setOrthancBaseUrl("http://synthetic-orthanc.invalid");
			config.setOrthancUsername("");
			config.setOrthancPassword("");
			service.setHttpClient(client);
			service.setDao(dao);
			when(client.createConnection("POST", config.getOrthancBaseUrl(), "/instances", "", "")).thenReturn(upload);
			when(client.createConnection("GET", config.getOrthancBaseUrl(), "/studies/study-1", "", ""))
			    .thenReturn(studyConnection);
			when(upload.getOutputStream()).thenReturn(output);
			when(upload.getResponseCode()).thenReturn(200);
			uploadResponse = new TrackingInputStream("{\"Status\":\"" + status + "\",\"ParentStudy\":\"study-1\"}");
			when(upload.getInputStream()).thenReturn(uploadResponse);
		}

		TrackingInputStream metadata(String json) throws IOException {
			TrackingInputStream input = new TrackingInputStream(json);
			when(studyConnection.getResponseCode()).thenReturn(200);
			when(studyConnection.getInputStream()).thenReturn(input);
			return input;
		}
	}

	private static class TrackingInputStream extends ByteArrayInputStream {
		boolean closed;

		TrackingInputStream(String value) {
			super(value.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

	private static class TrackingOutputStream extends ByteArrayOutputStream {
		boolean closed;

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
