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
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.imaging.web.controller;

public class DicomDifference {
	
	private String tag;
	
	private String fromOpenmrs;
	
	private String fromPacs;
	
	private String stepId;
	
	public DicomDifference(String tag, String fromOpenmrs, String fromPacs) {
		this.tag = tag;
		this.fromOpenmrs = fromOpenmrs;
		this.fromPacs = fromPacs;
	}
	
	public DicomDifference(String tag, String fromOpenmrs, String fromPacs, String stepId) {
		this.tag = tag;
		this.fromOpenmrs = fromOpenmrs;
		this.fromPacs = fromPacs;
		this.stepId = stepId;
	}
	
	public String getTag() {
		return tag;
	}
	
	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public String getFromOpenmrs() {
		return fromOpenmrs;
	}
	
	public void setFromOpenmrs(String fromOpenmrs) {
		this.fromOpenmrs = fromOpenmrs;
	}
	
	public String getFromPacs() {
		return fromPacs;
	}
	
	public void setFromPacs(String fromPacs) {
		this.fromPacs = fromPacs;
	}
	
	public String getStepId() {
		return stepId;
	}
	
	public void setStepId(String stepId) {
		this.stepId = stepId;
	}
}
