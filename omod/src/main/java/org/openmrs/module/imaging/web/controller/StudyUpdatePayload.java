package org.openmrs.module.imaging.web.controller;

import java.util.List;

public class StudyUpdatePayload {
	
	private StudyInfo studyInfo;
	
	private List<SeriesEntry> seriesList;
	
	public StudyInfo getStudyInfo() {
		return studyInfo;
	}
	
	public void setStudyInfo(StudyInfo studyInfo) {
		this.studyInfo = studyInfo;
	}
	
	public List<SeriesEntry> getSeriesList() {
		return seriesList;
	}
	
	public void setSeriesList(List<SeriesEntry> seriesList) {
		this.seriesList = seriesList;
	}
	
	public static class StudyInfo {
		
		private String accessionNumber;
		
		private String studyInstanceUID;
		
		private String referringPhysicianName;
		
		private String studyDescription;
		
		private Integer StudyID;
		
		public String getAccessionNumber() {
			return accessionNumber;
		}
		
		public String getStudyInstanceUID() {
			return studyInstanceUID;
		}
		
		public void setStudyInstanceUID(String studyInstanceUID) {
			this.studyInstanceUID = studyInstanceUID;
		}
		
		public String getReferringPhysicianName() {
			return referringPhysicianName;
		}
		
		public String getStudyDescription() {
			return studyDescription;
		}
		
		public Integer getStudyID() {
			return StudyID;
		}
	}
	
	public static class SeriesEntry {
		
		private SeriesInfo seriesInfo;
		
		private InstanceInfo instanceInfo;
		
		private String scheduledProcedureStepID;
		
		public SeriesInfo getSeriesInfo() {
			return seriesInfo;
		}
		
		public InstanceInfo getInstanceInfo() {
			return instanceInfo;
		}
		
		public void setInstanceInfo(InstanceInfo instanceInfo) {
			this.instanceInfo = instanceInfo;
		}
		
		public String getScheduledProcedureStepID() {
			return scheduledProcedureStepID;
		}
		
		public void setScheduledProcedureStepID(String scheduledProcedureStepID) {
			this.scheduledProcedureStepID = scheduledProcedureStepID;
		}
	}
	
	public static class SeriesInfo {
		
		private String seriesID;
		
		private String modality;
		
		private String seriesDescription;
		
		private String seriesInstanceUID;
		
		private String stationName;
		
		private String parentStudy;
		
		public String getSeriesID() {
			return seriesID;
		}
		
		public String getModality() {
			return modality;
		}
		
		public String getSeriesDescription() {
			return seriesDescription;
		}
		
		public String getParentStudy() {
			return parentStudy;
		}
		
		public String getSeriesInstanceUID() {
			return seriesInstanceUID;
		}
		
		public String getStationName() {
			return stationName;
		}
	}
	
	public static class InstanceInfo {
		
		private String patientBirthDate;
		
		private String patientID;
		
		private String patientName;
		
		private String scheduledProcedureStepID;
		
		private String studyInstanceUID;
		
		private String numberOfSlices;
		
		private String scheduledPerformingPhysician;
		
		private String performedProcedureStepDescription;
		
		private String performedProcedureStepStartDate;
		
		private String performedProcedureStepStartTime;
		
		private String requestedProcedureDescription;
		
		public String getPatientBirthDate() {
			return patientBirthDate;
		}
		
		public String getPatientName() {
			return patientName;
		}
		
		public String getPatientID() {
			return patientID;
		}
		
		public String getScheduledProcedureStepID() {
			return scheduledProcedureStepID;
		}
		
		public void setScheduledProcedureStepID(String scheduledProcedureStepID) {
			this.scheduledProcedureStepID = scheduledProcedureStepID;
		}
		
		public String getStudyInstanceUID() {
			return studyInstanceUID;
		}
		
		public void setStudyInstanceUID(String studyInstanceUID) {
			this.studyInstanceUID = studyInstanceUID;
		}
		
		public String getNumberOfSlices() {
			return numberOfSlices;
		}
		
		public String getScheduledPerformingPhysician() {
			return scheduledPerformingPhysician;
		}
		
		public String getPerformedProcedureStepStartTime() {
			return performedProcedureStepStartTime;
		}
		
		public String getPerformedProcedureStepStartDate() {
			return performedProcedureStepStartDate;
		}
		
		public String getPerformedProcedureStepDescription() {
			return performedProcedureStepDescription;
		}
		
		public String getRequestedProcedureDescription() {
			return requestedProcedureDescription;
		}
	}
	
}
