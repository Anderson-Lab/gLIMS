/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package dslab.glims.cli;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.gson.Gson;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;
import com.google.common.base.Preconditions;

import dslab.glims.CredentialMediator;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Uploader {


	/**
	 * HttpTransport to use for external requests.
	 */
	private static final HttpTransport TRANSPORT = new NetHttpTransport();
	
	private Permission perm = new Permission().setRole("writer").setType("user");

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/** Global Drive API client. */
	private static Drive drive;
	
	
	
	public String upload(String jSessionID, String pathToFile, String title, String mimeType, String raw_email, String parentID) {
		try {

		Credential credentials = OAuth2Native.getCredentialFromJSessionID(jSessionID);
		
		String email = null;
		if (raw_email.contains("__at__")) {
			String[] email_parts = raw_email.split("\\__at__");
			email = email_parts[0] + "@" + email_parts[1];
		} else {
			email = raw_email;
		}
		perm.setValue(email);

			try {
				// authorization
				// set up the global Drive instance
				drive = new Drive.Builder(TRANSPORT, JSON_FACTORY,
						credentials)
						.setApplicationName("gLIMS Uploader/1.0").build();

				// run commands

				View.header1("Starting Resumable Media Upload");

				File uploadedFile = uploadFile(pathToFile, title, mimeType, parentID, false);
				// Now update the permissions
				drive.permissions().insert(uploadedFile.getId(), perm).execute();

				/*
				 * View.header1("Starting Resumable Media Download");
				 * downloadFile(false, updatedFile);
				 * 
				 * View.header1("Starting Simple Media Upload"); uploadedFile =
				 * uploadFile(true);
				 * 
				 * View.header1("Starting Simple Media Download");
				 * downloadFile(true, uploadedFile);
				 */

				View.header1("Success!");
				return uploadedFile.getId();
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return "ERROR";
	}
	
	public static void main(String[] args) throws Exception {
		Preconditions
		.checkArgument(args.length >= 5,
				"Usage: java -jar Uploader.jar <jsession id> <path to file> <title> <mime type> <email> [<parent id>]");

		
		String jSessionID = args[0];
		String fileName = args[1];
		String title = args[2];
		String mimeType = args[3];
		String raw_email = args[4];
		String parentID = "root";
		if (args.length > 5)
			parentID = args[5];
		
		new Uploader().upload(jSessionID, fileName, title, mimeType, raw_email, parentID);
	}

	/** Uploads a file using either resumable or direct media upload. */
	private File uploadFile(String filePath, String title,
			String mimeType, String parentId, boolean useDirectUpload) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setTitle(title);
	    fileMetadata.setParents(Arrays.asList(new ParentReference().setId(parentId)));
		java.io.File uploadFile = new java.io.File(filePath);
		InputStreamContent mediaContent = new InputStreamContent(mimeType,
				new BufferedInputStream(new FileInputStream(uploadFile)));
		mediaContent.setLength(uploadFile.length());

		Drive.Files.Insert insert = drive.files().insert(fileMetadata,
				mediaContent);
		
		MediaHttpUploader uploader = insert.getMediaHttpUploader();
		uploader.setDirectUploadEnabled(useDirectUpload);
		uploader.setProgressListener(new FileUploadProgressListener());
		return insert.execute();
	}
}
