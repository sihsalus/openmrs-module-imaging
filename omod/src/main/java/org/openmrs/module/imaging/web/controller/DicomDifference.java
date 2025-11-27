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
