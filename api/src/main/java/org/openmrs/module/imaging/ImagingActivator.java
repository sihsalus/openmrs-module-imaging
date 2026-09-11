/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.imaging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.APIException;
import org.openmrs.module.BaseModuleActivator;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class ImagingActivator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	/**
	 * @see #started()
	 */
	public void started() {
		log.info("Started Imaging");
	}

	@Override
	public void contextRefreshed() {
		// Core rebuilds its shared parser whenever any module refreshes the web
		// context. This hook runs for all started modules with an OpenMRS session.
		// Never mutate parser limits during an upload request.
		CommonsMultipartResolver resolver;
		try {
			resolver = Context.getRegisteredComponent("multipartResolver", CommonsMultipartResolver.class);
		}
		catch (APIException e) {
			log.debug("The imaging web multipart parser is unavailable in this context");
			return;
		}
		if (resolver == null) {
			return; // API-only contexts do not install the web multipart parser.
		}
		try {
			ImagingProperties imageProps = Context.getRegisteredComponent("imagingProperties", ImagingProperties.class);
			if (imageProps == null) {
				log.error("Imaging configuration is unavailable; retaining the framework multipart limit");
				return;
			}
			long requestLimit = Math.addExact(imageProps.getMaxUploadImageDataSize(),
			    ImagingProperties.MULTIPART_OVERHEAD_BYTES);
			resolver.setMaxUploadSize(requestLimit);
		}
		catch (APIException | ArithmeticException e) {
			// An invalid imaging setting must not break another module's refresh.
			// The upload controller also validates the GP and rejects such uploads.
			log.error("Invalid imaging upload limit; retaining the framework multipart limit");
		}
	}
	
	/**
	 * @see #shutdown()
	 */
	public void shutdown() {
		log.info("Shutdown Imaging");
	}
	
}
