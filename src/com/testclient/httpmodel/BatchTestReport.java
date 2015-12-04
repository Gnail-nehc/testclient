package com.alipics.testassets.testclient.httpmodel;

public class BatchTestReport {
	private String name="";
	private String time="";
	private String duration="";
	private String result="";
	private String request="";
	private String response="";
	private String checkpoint="";
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getResult() {
		return result;
	}
	public void setResult(String result) {
		this.result = result;
	}
	public String getRequest() {
		return request;
	}
	public void setRequest(String request) {
		this.request = request;
	}
	public String getResponse() {
		return response;
	}
	public void setResponse(String response) {
		this.response = response;
	}
	public String getDuration() {
		return duration;
	}
	public void setDuration(String duration) {
		this.duration = duration;
	}
	public String getCheckpoint() {
		return checkpoint;
	}
	public void setCheckpoint(String checkpoint) {
		this.checkpoint = checkpoint;
	}
}
