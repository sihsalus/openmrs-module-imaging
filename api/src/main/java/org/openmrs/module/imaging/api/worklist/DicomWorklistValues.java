package org.openmrs.module.imaging.api.worklist;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** DICOM DA and TM values. Legacy API input is normalized before storage/export. */
public final class DicomWorklistValues {

	private static final DateTimeFormatter LEGACY_DATETIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
	    .withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss")
	    .withResolverStyle(ResolverStyle.STRICT);

	private DicomWorklistValues() {
	}

	public static String date(String value) {
		if (value == null || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Missing scheduled date");
		}
		String input = value.trim();
		try {
			LocalDate date;
			if (input.matches("[0-9]{14}")) {
				date = LocalDateTime.parse(input, LEGACY_DATETIME).toLocalDate();
			}
			else if (input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
				date = LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE);
			}
			else if (input.matches("[0-9]{8}")) {
				date = LocalDate.parse(input, DateTimeFormatter.BASIC_ISO_DATE);
			}
			else {
				throw new IllegalArgumentException("Invalid scheduled date");
			}
			if (date.getYear() < 1 || date.getYear() > 9999) {
				throw new IllegalArgumentException("Invalid scheduled date");
			}
			return date.format(DateTimeFormatter.BASIC_ISO_DATE);
		}
		catch (DateTimeException e) {
			throw new IllegalArgumentException("Invalid scheduled date", e);
		}
	}

	public static String time(String value) {
		if (value == null || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Missing scheduled time");
		}
		String input = value.trim();
		try {
			if (input.matches("[0-9]{6}")) {
				return LocalTime.parse(input, TIME).format(TIME);
			}
			if (input.matches("[0-9]{2}:[0-9]{2}(:[0-9]{2})?")) {
				return LocalTime.parse(input, DateTimeFormatter.ISO_LOCAL_TIME).format(TIME);
			}
			throw new IllegalArgumentException("Invalid scheduled time");
		}
		catch (DateTimeException e) {
			throw new IllegalArgumentException("Invalid scheduled time", e);
		}
	}

	public static String aeTitle(String value) {
		if (value != null && value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid station AE title");
		}
		String title = value == null ? "" : value.trim();
		if (!title.matches("[ -~]{1,16}") || title.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("Invalid station AE title");
		}
		return title;
	}
}
