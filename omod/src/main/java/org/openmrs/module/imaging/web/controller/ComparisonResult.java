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

import java.util.List;

public class ComparisonResult {
	
	private int score;
	
	private List<DicomDifference> differences;
	
	public ComparisonResult(int score, List<DicomDifference> differences) {
		this.score = score;
		this.differences = differences;
	}
	
	public int getScore() {
		return score;
	}
	
	public void setScore(int score) {
		this.score = score;
	}
	
	public List<DicomDifference> getDifferences() {
		return differences;
	}
	
	public void setDifferences(List<DicomDifference> differences) {
		this.differences = differences;
	}
}
