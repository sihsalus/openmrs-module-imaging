package org.openmrs.module.imaging;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImagingUploadPropertiesTest {

	private final AdministrationService administration = mock(AdministrationService.class);

	private final ImagingProperties properties = new ImagingProperties();

	@Before
	public void setUp() {
		properties.administrationService = administration;
	}

	@Test
	public void usesDefaultAndAcceptsTrimmedPositiveByteLimits() {
		when(administration.getGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, "200000000"))
		    .thenReturn("200000000", " 1 ", Long.toString(Long.MAX_VALUE - 65536));
		assertEquals(Long.valueOf(200000000), properties.getMaxUploadImageDataSize());
		assertEquals(Long.valueOf(1), properties.getMaxUploadImageDataSize());
		assertEquals(Long.valueOf(Long.MAX_VALUE - 65536), properties.getMaxUploadImageDataSize());
	}

	@Test
	public void rejectsInvalidLimitsInsteadOfSilentlyAllowingAnUnlimitedUpload() {
		String[] invalid = { null, "", " ", "0", "-1", "1.5", "200 MB", "9223372036854775808",
		    Long.toString(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE - 65535) };
		for (String value : invalid) {
			when(administration.getGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE, "200000000"))
			    .thenReturn(value);
			APIException error = assertThrows(APIException.class, () -> properties.getMaxUploadImageDataSize());
			assertEquals("The imaging upload size limit must be a positive number of bytes", error.getMessage());
		}
	}
}
