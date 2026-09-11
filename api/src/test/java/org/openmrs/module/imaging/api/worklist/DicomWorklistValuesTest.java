package org.openmrs.module.imaging.api.worklist;

import org.junit.Test;
import static org.junit.Assert.*;

public class DicomWorklistValuesTest {
	@Test
	public void normalizesSupportedDatesIncludingFutureAndLeapDates() {
		assertEquals("20300228", DicomWorklistValues.date("20300228"));
		assertEquals("20280229", DicomWorklistValues.date("2028-02-29"));
		assertEquals("20300101", DicomWorklistValues.date("20300101093000"));
	}

	@Test
	public void rejectsImpossibleDatesAndInvalidLegacyTime() {
		String[] inputs = { "", "20270229", "20281301", "20300101250000", "2028-2-29", "00000101",
		    "20300101\n", "2030-01-01\t", null };
		for (String input : inputs) {
			assertThrows(IllegalArgumentException.class, () -> DicomWorklistValues.date(input));
		}
	}

	@Test
	public void normalizesTimesWithoutLosingMidnightOrNoon() {
		assertEquals("000000", DicomWorklistValues.time("00:00"));
		assertEquals("120000", DicomWorklistValues.time("12:00:00"));
		assertEquals("235959", DicomWorklistValues.time("235959"));
	}

	@Test
	public void rejectsInvalidTimesAndUnsafeAeTitles() {
		for (String time : new String[] { "24:00", "13:99", "120060", "1:00", "12:00:00.1", "120000\n", null }) {
			assertThrows(IllegalArgumentException.class, () -> DicomWorklistValues.time(time));
		}
		for (String title : new String[] { "", " ", "ABCDEFGHIJKLMNOPQ", "A\\B", "A\nB", "CT_ROOM\n", "ÁREA", null }) {
			assertThrows(IllegalArgumentException.class, () -> DicomWorklistValues.aeTitle(title));
		}
		assertEquals("ABCDEFGHIJKLMNOP", DicomWorklistValues.aeTitle("ABCDEFGHIJKLMNOP"));
		assertEquals("CT_ROOM", DicomWorklistValues.aeTitle(" CT_ROOM "));
	}
}
