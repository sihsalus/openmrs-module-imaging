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

package org.openmrs.module.imaging;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("imagingProperties")
public class ImagingProperties {

	// The parser limits the complete multipart request, including normal form metadata.
	static final long MULTIPART_OVERHEAD_BYTES = 64L * 1024L;
	
	protected final Log log = LogFactory.getLog(getClass());
	
	@Autowired
	@Qualifier("conceptService")
	protected ConceptService conceptService;
	
	@Autowired
	@Qualifier("adminService")
	protected AdministrationService administrationService;
	
	public ConceptService getConceptService() {
		return conceptService;
	}
	
	public AdministrationService getAdministrationService() {
		return administrationService;
	}
	
	/**
	 * @param globalPropertyName the global property name
	 * @return the openmrs concept
	 */
	protected Concept getConceptByGlobalProperty(String globalPropertyName) {
		String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
		Concept concept = conceptService.getConceptByUuid(globalProperty);
		if (concept == null) {
			throw new IllegalStateException("Configuration required: " + globalPropertyName);
		}
		return concept;
	}
	
	/**
	 * @param globalPropertyName the global property name
	 * @param required the required id
	 * @return the value of the global property
	 */
	protected String getGlobalProperty(String globalPropertyName, boolean required) {
		String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
		if (required && StringUtils.isEmpty(globalProperty)) {
			throw new APIException("Configuration required: " + globalPropertyName);
		}
		return globalProperty;
	}
	
	/**
	 * @return the global property for the max upload data size
	 */
	public Long getMaxUploadImageDataSize() {
		String globalProperty = administrationService.getGlobalProperty(ImagingConstants.GP_MAX_UPLOAD_IMAGEDATA_SIZE,
		    "200000000");
		try {
			long limit = Long.parseLong(globalProperty.trim());
			if (limit <= 0) {
				throw new NumberFormatException();
			}
			Math.addExact(limit, MULTIPART_OVERHEAD_BYTES);
			return limit;
		}
		catch (NumberFormatException | NullPointerException | ArithmeticException e) {
			throw new APIException("The imaging upload size limit must be a positive number of bytes", e);
		}
	}
}
