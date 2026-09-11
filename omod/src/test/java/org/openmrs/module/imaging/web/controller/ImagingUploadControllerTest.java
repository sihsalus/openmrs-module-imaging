package org.openmrs.module.imaging.web.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.OrthancConfiguration;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OrthancConfigurationService;
import org.openmrs.module.imaging.api.client.OrthancHttpClient;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.module.imaging.web.controller.ResponseModel.DicomStudyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImagingUploadControllerTest extends BaseWebControllerTest {

	private final DicomStudyController controller = new DicomStudyController();
	private DicomStudyService studies;
	private OrthancConfiguration configuration;
	private OrthancHttpClient client;
	private DicomStudy study;
	private Patient patient;

	@Before
	public void setUp() throws Exception {
		executeDataSet("testDicomStudyDataset.xml");
		studies = Context.getService(DicomStudyService.class);
		configuration = Context.getService(OrthancConfigurationService.class).getOrthancConfiguration(1);
		study = studies.getDicomStudy(1);
		patient = Context.getPatientService().getPatient(1);
		client = mock(OrthancHttpClient.class);
		studies.setHttpClient(client);
	}

	@Test
	public void rejectsMissingFilesUnknownPatientsAndUnknownConfigurationsBeforeRemoteWrites() throws Exception {
		assertEquals(400, upload(null, patient.getUuid()).getStatusCodeValue());
		assertEquals(400, upload(file(new byte[0]), patient.getUuid()).getStatusCodeValue());
		MultipartFile file = mock(MultipartFile.class);
		when(file.getOriginalFilename()).thenReturn("synthetic.dcm");
		when(file.getSize()).thenReturn(4L);
		assertEquals(404, upload(file, "unknown-patient").getStatusCodeValue());
		assertEquals(404, controller.uploadStudies(file, 99999, patient.getUuid(),
		    new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCodeValue());
		verify(file, never()).getInputStream();
		verifyZeroInteractions(client);
	}

	@Test
	public void rejectsZipNamesAndDisguisedZipSignaturesWithoutSendingThemToOrthanc() throws Exception {
		for (String name : new String[] { "synthetic.zip", "synthetic.ZIP" }) {
			MockMultipartFile archive = new MockMultipartFile("file", name, "application/zip", new byte[] { 1 });
			assertEquals(415, upload(archive, patient.getUuid()).getStatusCodeValue());
		}
		for (byte[] magic : new byte[][] { { 80, 75, 3, 4 }, { 80, 75, 5, 6 }, { 80, 75, 7, 8 } }) {
			assertEquals(415, upload(file(magic), patient.getUuid()).getStatusCodeValue());
		}
		verifyZeroInteractions(client);
	}

	@Test
	public void refusesOversizedFilesWithoutReadingOrAllocatingTheirBody() throws Exception {
		setLimit(4);
		MultipartFile file = mock(MultipartFile.class);
		when(file.getOriginalFilename()).thenReturn("synthetic.dcm");
		when(file.getSize()).thenReturn(5L);
		assertEquals(413, upload(file, patient.getUuid()).getStatusCodeValue());
		verify(file, never()).getInputStream();
		verifyZeroInteractions(client);
	}

	@Test
	public void acceptsTheExactLimitAndPreservesAReviewedDuplicateForTheSamePatient() throws Exception {
		setLimit(4);
		studies.updateLinkStatus(study, 2);
		String comparison = study.getComparisonResult();
		mockStoredStudy("AlreadyStored");
		ResponseEntity<Object> response = upload(file(new byte[] { 1, 2, 3, 4 }), patient.getUuid());
		assertEquals(200, response.getStatusCodeValue());
		DicomStudyResponse body = (DicomStudyResponse) response.getBody();
		assertEquals(Integer.valueOf(2), body.getLinkStatus());
		assertEquals(patient.getUuid(), body.getMrsPatientUuid());
		assertEquals(2, study.getLinkStatus());
		assertEquals(comparison, study.getComparisonResult());
		assertEquals(patient, study.getMrsPatient());
	}

	@Test
	public void refusesToAssignADuplicateStudyOwnedByAnotherPatient() throws Exception {
		studies.updateLinkStatus(study, 2);
		String comparison = study.getComparisonResult();
		mockStoredStudy("AlreadyStored");
		Patient other = Context.getPatientService().getPatient(2);
		assertEquals(409, upload(file(new byte[] { 1, 2, 3, 4 }), other.getUuid()).getStatusCodeValue());
		assertEquals(patient, study.getMrsPatient());
		assertEquals(2, study.getLinkStatus());
		assertEquals(comparison, study.getComparisonResult());
	}

	@Test
	public void uploadWithoutAPatientDoesNotClearAnExistingAssociation() throws Exception {
		mockStoredStudy("Success");
		assertEquals(200, upload(file(new byte[] { 1, 2, 3, 4 }), null).getStatusCodeValue());
		assertEquals(patient, study.getMrsPatient());
		assertEquals(0, study.getLinkStatus());
	}

	@Test
	public void closesTheMultipartStreamOnRemoteFailureAndRetainsTheExistingAssociation() throws Exception {
		final boolean[] closed = { false };
		InputStream source = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }) {
			@Override
			public void close() throws IOException {
				closed[0] = true;
				super.close();
			}
		};
		MultipartFile file = mock(MultipartFile.class);
		when(file.getOriginalFilename()).thenReturn("synthetic.dcm");
		when(file.getSize()).thenReturn(4L);
		when(file.getInputStream()).thenReturn(source);
		when(client.createConnection(anyString(), anyString(), anyString(), anyString(), anyString()))
		    .thenThrow(new IOException("synthetic connection failure"));
		assertThrows(IOException.class, () -> upload(file, patient.getUuid()));
		assertTrue(closed[0]);
		assertEquals(patient, study.getMrsPatient());
	}

	@Test
	public void assignmentForTheSamePatientIsIdempotentAndPreservesClinicalReview() {
		studies.updateLinkStatus(study, 2);
		String comparison = study.getComparisonResult();
		assertEquals(200, assign(patient.getUuid(), true).getStatusCodeValue());
		assertEquals(2, study.getLinkStatus());
		assertEquals(comparison, study.getComparisonResult());
		assertEquals(patient, study.getMrsPatient());
	}

	@Test
	public void anotherPatientCannotUnlinkTheStudy() {
		studies.updateLinkStatus(study, 2);
		assertEquals(409, assign(Context.getPatientService().getPatient(2).getUuid(), false).getStatusCodeValue());
		assertEquals(patient, study.getMrsPatient());
		assertEquals(2, study.getLinkStatus());
	}

	@Test
	public void explicitUnlinkAndReassignAllowAnUnownedStudyToBeLinked() {
		assertEquals(200, assign(patient.getUuid(), false).getStatusCodeValue());
		assertNull(study.getMrsPatient());
		assertEquals(-1, study.getLinkStatus());
		Patient other = Context.getPatientService().getPatient(2);
		assertEquals(200, assign(other.getUuid(), true).getStatusCodeValue());
		assertEquals(other, study.getMrsPatient());
		assertEquals(0, study.getLinkStatus());
	}

	private ResponseEntity<Object> assign(String patientUuid, boolean assign) {
		return controller.assignStudy(study.getId(), patientUuid, assign, new MockHttpServletRequest(),
		    new MockHttpServletResponse());
	}

	private ResponseEntity<Object> upload(MultipartFile file, String patientUuid) throws IOException {
		return controller.uploadStudies(file, configuration.getId(), patientUuid, new MockHttpServletRequest(),
		    new MockHttpServletResponse());
	}

	private MockMultipartFile file(byte[] bytes) {
		return new MockMultipartFile("file", "synthetic.dcm", "application/dicom", bytes);
	}

	private void setLimit(long bytes) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, Long.toString(bytes)));
	}

	private void mockStoredStudy(String status) throws IOException {
		HttpURLConnection upload = mock(HttpURLConnection.class);
		HttpURLConnection metadata = mock(HttpURLConnection.class);
		when(client.createConnection("POST", configuration.getOrthancBaseUrl(), "/instances",
		    configuration.getOrthancUsername(), configuration.getOrthancPassword())).thenReturn(upload);
		when(client.createConnection("GET", configuration.getOrthancBaseUrl(), "/studies/" + study.getOrthancStudyUID(),
		    configuration.getOrthancUsername(), configuration.getOrthancPassword())).thenReturn(metadata);
		when(upload.getOutputStream()).thenReturn(new ByteArrayOutputStream());
		when(upload.getResponseCode()).thenReturn(200);
		when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(("{\"Status\":\"" + status
		    + "\",\"ParentStudy\":\"" + study.getOrthancStudyUID() + "\"}").getBytes(StandardCharsets.UTF_8)));
		when(metadata.getResponseCode()).thenReturn(200);
		when(metadata.getInputStream()).thenReturn(new ByteArrayInputStream(("{\"ID\":\"" + study.getOrthancStudyUID()
		    + "\",\"MainDicomTags\":{\"StudyInstanceUID\":\"" + study.getStudyInstanceUID() + "\"},"
		    + "\"PatientMainDicomTags\":{\"PatientName\":\"Synthetic fixture\"}}").getBytes(StandardCharsets.UTF_8)));
	}
}
