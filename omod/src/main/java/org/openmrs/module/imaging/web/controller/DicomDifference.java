package org.openmrs.module.imaging.web.controller;

public class DicomDifference {
	
	private String tag;
	
	private String dbValue;
	
	private String payloadValue;
	
	private String stepId;
	
	public DicomDifference(String tag, String dbValue, String payloadValue) {
		this.tag = tag;
		this.dbValue = dbValue;
		this.payloadValue = payloadValue;
	}
	
	public DicomDifference(String tag, String dbValue, String payloadValue, String stepId) {
		this.tag = tag;
		this.dbValue = dbValue;
		this.payloadValue = payloadValue;
		this.stepId = stepId;
	}
	
	public String getTag() {
		return tag;
	}
	
	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public String getDbValue() {
		return dbValue;
	}
	
	public void setDbValue(String dbValue) {
		this.dbValue = dbValue;
	}
	
	public String getPayloadValue() {
		return payloadValue;
	}
	
	public void setPayloadValue(String payloadValue) {
		this.payloadValue = payloadValue;
	}
	
	public String getStepId() {
		return stepId;
	}
	
	public void setStepId(String stepId) {
		this.stepId = stepId;
	}
}
