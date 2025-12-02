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
package org.openmrs.module.imaging.api.study;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;
import org.openmrs.module.imaging.OrthancConfiguration;

import javax.persistence.Column;

public class DicomStudy extends BaseOpenmrsData implements java.io.Serializable {
	
	private static final long serialVersionUID = 1;
	
	private Integer id;
	
	private String studyInstanceUID;
	
	private String orthancStudyUID;
	
	private Integer matching;
	
	private Integer matchingScore;
	
	@Column(name = "comparisonResult", length = 2000)
	private String comparisonResult;
	
	private Patient mrsPatient;
	
	private OrthancConfiguration orthancConfiguration;
	
	private String patientName;
	
	private String studyDate;
	
	private String studyTime;
	
	private String studyDescription;
	
	private String gender;
	
	public DicomStudy() {
	}
	
	public DicomStudy(String studyInstanceUID, String orthancStudyUID, int matching, int matchingScore,
	    String comparisonResult, Patient patient, OrthancConfiguration config, String patientName, String studyDate,
	    String studyTime, String studyDescription, String gender) {
		this.studyInstanceUID = studyInstanceUID;
		this.orthancStudyUID = orthancStudyUID;
		this.matching = matching;
		this.matchingScore = matchingScore;
		this.comparisonResult = comparisonResult;
		this.mrsPatient = patient;
		this.orthancConfiguration = config;
		this.patientName = patientName;
		this.studyDate = studyDate;
		this.studyTime = studyTime;
		this.studyDescription = studyDescription;
		this.gender = gender;
	}
	
	@Override
	public Integer getId() {
		return this.id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	// Getters and Setters
	public String getStudyInstanceUID() {
		return this.studyInstanceUID;
	}
	
	public void setStudyInstanceUID(String studyInstanceUID) {
		this.studyInstanceUID = studyInstanceUID;
	}
	
	public void setMatching(int matching) {
		this.matching = matching;
	}
	
	public int getMatching() {
		return matching;
	}
	
	public void setMatchingScore(Integer matchingScore) {
		this.matchingScore = matchingScore;
	}
	
	public Integer getMatchingScore() {
		return matchingScore;
	}
	
	public void setComparisonResult(String comparisonResult) {
		this.comparisonResult = comparisonResult;
	}
	
	public String getComparisonResult() {
		return comparisonResult;
	}
	
	public String getOrthancStudyUID() {
		return orthancStudyUID;
	}
	
	public void setOrthancStudyUID(String orthancStudyUID) {
		this.orthancStudyUID = orthancStudyUID;
	}
	
	public Patient getMrsPatient() {
		return this.mrsPatient;
	}
	
	public void setMrsPatient(Patient mrsPatient) {
		this.mrsPatient = mrsPatient;
	}
	
	public OrthancConfiguration getOrthancConfiguration() {
		return this.orthancConfiguration;
	}
	
	public void setOrthancConfiguration(OrthancConfiguration orthancConfiguration) {
		this.orthancConfiguration = orthancConfiguration;
	}
	
	public String getPatientName() {
		return this.patientName;
	}
	
	public void setPatientName(String patientName) {
		this.patientName = patientName;
	}
	
	public String getStudyDate() {
		return this.studyDate;
	}
	
	public void setStudyDate(String studyDate) {
		this.studyDate = studyDate;
	}
	
	public String getStudyTime() {
		return this.studyTime;
	}
	
	public void setStudyTime(String studyTime) {
		this.studyTime = studyTime;
	}
	
	public String getStudyDescription() {
		return this.studyDescription;
	}
	
	public void setStudyDescription(String studyDescription) {
		this.studyDescription = studyDescription;
	}
	
	public String getGender() {
		return this.gender;
	}
	
	public void setGender(String gender) {
		this.gender = gender;
	}
}
