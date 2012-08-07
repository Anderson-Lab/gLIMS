package com.google.drive.samples.dredit;

import java.io.IOException;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.model.File;

class FileCallback extends JsonBatchCallback<File> {
	private File file;
	private boolean done = false;
	private Insert httpRequest = null;

	public File getFile() {
		return file;
	}
	@Override
	public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
		System.out.println(e.getMessage());
		
	}
	@Override
	public void onSuccess(File t, GoogleHeaders responseHeaders) {
		this.file = t;
		done = true;
	}
	
	public void setHttpRequest(Insert hr) {
		httpRequest = hr;
	}
	
	public Insert getHttpRequest() {
		return httpRequest;
	}
	
	public boolean getStatus() {
		return done;
	}
};
