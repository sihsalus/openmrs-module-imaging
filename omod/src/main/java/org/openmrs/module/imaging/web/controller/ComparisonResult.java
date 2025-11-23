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
