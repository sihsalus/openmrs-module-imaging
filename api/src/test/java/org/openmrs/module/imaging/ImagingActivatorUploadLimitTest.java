package org.openmrs.module.imaging;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.apache.commons.logging.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.ServiceContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImagingActivatorUploadLimitTest {

	private final AdministrationService administration = mock(AdministrationService.class);
	private final ApplicationContext application = mock(ApplicationContext.class);
	private final ImagingProperties properties = new ImagingProperties();
	private final ImagingActivator activator = new ImagingActivator();
	private final Log log = mock(Log.class);
	private final CommonsMultipartResolver resolver = frameworkResolver();
	private ServiceContext serviceContext;
	private ApplicationContext previousApplication;

	@Before
	public void setUp() throws Exception {
		properties.administrationService = administration;
		when(application.getBean("imagingProperties", ImagingProperties.class)).thenReturn(properties);
		when(application.getBean("multipartResolver", CommonsMultipartResolver.class)).thenReturn(resolver);
		setLimit("200000000");
		Field logger = ImagingActivator.class.getDeclaredField("log");
		logger.setAccessible(true);
		logger.set(activator, log);
		// Exercise Context's real component lookup without starting a database or
		// replacing the ServiceContext singleton used by other module tests.
		serviceContext = ServiceContext.getInstance();
		previousApplication = serviceContext.getApplicationContext();
		serviceContext.setApplicationContext(application);
	}

	@After
	public void restoreApplicationContext() {
		if (serviceContext != null) {
			serviceContext.setApplicationContext(previousApplication);
		}
	}

	@Test
	public void refreshAddsMultipartOverheadWithoutChangingTheSharedPerFileLimit() {
		activator.contextRefreshed();
		assertEquals(200065536L, resolver.getFileUpload().getSizeMax());
		assertEquals(-1L, resolver.getFileUpload().getFileSizeMax());
		activator.contextRefreshed();
		assertEquals(200065536L, resolver.getFileUpload().getSizeMax());
	}

	@Test
	public void refreshConfiguresTheNewFrameworkResolverAndReadsTheCurrentProperty() {
		activator.contextRefreshed();
		CommonsMultipartResolver replacement = frameworkResolver();
		when(application.getBean("multipartResolver", CommonsMultipartResolver.class)).thenReturn(replacement);
		setLimit("100000000");
		activator.contextRefreshed();
		assertEquals(100065536L, replacement.getFileUpload().getSizeMax());
		assertEquals(200065536L, resolver.getFileUpload().getSizeMax());
	}

	@Test
	public void startedDoesNotReadThePropertyOrMutateTheFrameworkResolver() {
		activator.started();
		assertEquals(75000000L, resolver.getFileUpload().getSizeMax());
		verifyZeroInteractions(application, administration);
	}

	@Test
	public void exactSizeFileWithNormalMultipartMetadataReachesTheControllerBoundary() {
		setLimit("4");
		activator.contextRefreshed();
		MultipartHttpServletRequest parsed = resolver.resolveMultipart(multipartRequest("data"));
		try {
			assertNotNull(parsed.getFile("file"));
			assertEquals(4L, parsed.getFile("file").getSize());
			assertEquals("1", parsed.getParameter("configurationId"));
			assertEquals("synthetic-patient", parsed.getParameter("patient"));
			// Parsing runs before the OpenMRS session/auth filters. It must use the
			// configured limit without another database-backed property lookup.
			verify(administration).getGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, "200000000");
			verifyNoMoreInteractions(administration);
		}
		finally {
			resolver.cleanupMultipart(parsed);
		}
	}

	@Test
	public void parserStillRejectsRequestsLargerThanTheFileLimitAndBoundedOverhead() {
		setLimit("4");
		activator.contextRefreshed();
		String oversized = new String(new char[(int) ImagingProperties.MULTIPART_OVERHEAD_BYTES + 5]);
		assertThrows(MaxUploadSizeExceededException.class, () -> {
			MultipartHttpServletRequest parsed = resolver.resolveMultipart(multipartRequest(oversized));
			resolver.cleanupMultipart(parsed);
		});
	}

	@Test
	public void invalidAndOverflowingPropertiesPreserveTheFrameworkLimitAndLogOnlyAFixedMessage() {
		String[] invalid = { null, "", "0", "-1", "synthetic-private-setting", Long.toString(Long.MAX_VALUE),
		    Long.toString(Long.MAX_VALUE - ImagingProperties.MULTIPART_OVERHEAD_BYTES + 1) };
		for (String value : invalid) {
			setLimit(value);
			activator.contextRefreshed();
			assertEquals(75000000L, resolver.getFileUpload().getSizeMax());
		}
		verify(log, times(invalid.length)).error(
		    "Invalid imaging upload limit; retaining the framework multipart limit");
		verifyNoMoreInteractions(log);
	}

	@Test
	public void invalidConfigurationDoesNotDiscardAPreviouslyConfiguredLimit() {
		activator.contextRefreshed();
		setLimit("invalid");
		activator.contextRefreshed();
		assertEquals(200065536L, resolver.getFileUpload().getSizeMax());
	}

	@Test
	public void largestRepresentableRequestLimitDoesNotOverflowToAnUnlimitedValue() {
		setLimit(Long.toString(Long.MAX_VALUE - ImagingProperties.MULTIPART_OVERHEAD_BYTES));
		activator.contextRefreshed();
		assertEquals(Long.MAX_VALUE, resolver.getFileUpload().getSizeMax());
	}

	@Test
	public void apiOnlyContextWithoutAMultipartResolverCanRefresh() {
		when(application.getBean("multipartResolver", CommonsMultipartResolver.class))
		    .thenThrow(new NoSuchBeanDefinitionException("multipartResolver"));
		activator.contextRefreshed();
		verifyZeroInteractions(administration);
	}

	private void setLimit(String value) {
		when(administration.getGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, "200000000"))
		    .thenReturn(value);
	}

	private static CommonsMultipartResolver frameworkResolver() {
		CommonsMultipartResolver resolver = new CommonsMultipartResolver();
		resolver.setMaxUploadSize(75000000L);
		return resolver;
	}

	private static MockHttpServletRequest multipartRequest(String content) {
		String boundary = "synthetic-imaging-boundary";
		String body = "--" + boundary + "\r\n"
		    + "Content-Disposition: form-data; name=\"configurationId\"\r\n\r\n1\r\n"
		    + "--" + boundary + "\r\n"
		    + "Content-Disposition: form-data; name=\"patient\"\r\n\r\nsynthetic-patient\r\n"
		    + "--" + boundary + "\r\n"
		    + "Content-Disposition: form-data; name=\"file\"; filename=\"synthetic.dcm\"\r\n"
		    + "Content-Type: application/dicom\r\n\r\n" + content + "\r\n"
		    + "--" + boundary + "--\r\n";
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/openmrs/ws/rest/v1/imaging/instances");
		request.setContentType("multipart/form-data; boundary=" + boundary);
		request.setContent(body.getBytes(StandardCharsets.US_ASCII));
		return request;
	}
}
