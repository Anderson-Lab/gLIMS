package dslab.glims.cli;


/*
 * Copyright (c) 2010 Google Inc.
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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Children;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.common.base.Preconditions;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Command-line sample for the Google OAuth2 API described at <a
 * href="http://code.google.com/apis/accounts/docs/OAuth2Login.html">Using OAuth 2.0 for Login
 * (Experimental)</a>.
 *
 * @author Yaniv Inbar
 */
public class Downloader {

	private static Drive service;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("Usage: java -jar Downloader.jar <file id> <path to file> [-d [direct|resumable] -c <path to credentials> -j <jsession id>]\nDefault credential file is credentials.dat\nNo default jsession id");
			return;
		}

		String fileID = args[0];
		String pathToFile = args[1];
		String credentialPath = "credentials.dat";
		String jSessionID = null;
		boolean direct = true;
		if ((args.length-2) % 2 == 0) {
			for (int i = 2; i < args.length; i+=2) {
				if (args[i].equals("-c"))
					credentialPath = args[i+1];
				else if (args[i].equals("-j"))
					jSessionID = args[i+1];
				else if (args[i].equals("-d"))
					if (args[i+1].equals("direct"))
						direct = true;
					else
						direct = false;
			}
		} else {
			System.err.println("Invalid number of command line arguments");
			System.exit(1);
		}

		new Downloader().download(fileID, pathToFile, credentialPath, jSessionID, direct);
	}

	private class NoTimeoutHttpRequestInitializer implements HttpRequestInitializer {
		private Credential credential = null;
		public NoTimeoutHttpRequestInitializer(Credential c){
			credential = c;
		}

		@Override
		public void initialize(HttpRequest httpRequest) throws IOException {
			credential.initialize(httpRequest);
			httpRequest.setReadTimeout(0);
		}
	}

	public void download(String fileID, String pathToFile, String credentialPath, String jSessionID, boolean direct) throws Exception {

		// authorization
		Credential credential = null;
		if (jSessionID == null) {
			credential = OAuth2Native.authorize(credentialPath);
		} else
			credential = OAuth2Native.getCredentialFromJSessionID(jSessionID);

		NoTimeoutHttpRequestInitializer init = new NoTimeoutHttpRequestInitializer(credential);
		service = new Builder(OAuth2Native.HTTP_TRANSPORT, OAuth2Native.JSON_FACTORY, credential).setHttpRequestInitializer(init).build();
		downloadDriveFile(fileID, pathToFile, direct);
	}
	
	public String getTitle(String fileID, String credentialPath, String jSessionID) throws Exception {
		// authorization
		Credential credential = null;
		if (jSessionID == null) {
			credential = OAuth2Native.authorize(credentialPath);
		} else
			credential = OAuth2Native.getCredentialFromJSessionID(jSessionID);

		NoTimeoutHttpRequestInitializer init = new NoTimeoutHttpRequestInitializer(credential);
		service = new Builder(OAuth2Native.HTTP_TRANSPORT, OAuth2Native.JSON_FACTORY, credential).setHttpRequestInitializer(init).build();
		File f = service.files().get(fileID).execute();
		return f.getTitle();
	}

	public final static int IN_CACHE = 1;
	public final static int DOWNLOAD_SUCCESS = 0;
	public final static int DOWNLOAD_FAIL = -1;
	
	private static int downloadDriveFile(String ID, String pathToFile, boolean direct) {
		try {
			try {
				// get file id
				String fileID = ID;
				
				File fileToDownload = service.files().get(fileID).execute();
				GenericUrl u = new GenericUrl(fileToDownload.getDownloadUrl());
				
				System.out.println("md5: "+fileToDownload.getMd5Checksum());
				System.out.println("title: "+fileToDownload.getTitle());
				java.io.File f = new java.io.File(pathToFile);
				if (f.exists()) {
					FileInputStream fis = new FileInputStream(f);
					String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
					if (md5.equals(fileToDownload.getMd5Checksum()))
						return IN_CACHE;
				}
				
				Get request = service.files().get(fileToDownload.getId());
				//String name = fileToDownload.getTitle();

				FileOutputStream bos = new FileOutputStream(pathToFile);
				MediaHttpDownloader mhd = request.getMediaHttpDownloader();
				mhd.setProgressListener(new FileDownloadProgressListener());
				mhd.setDirectDownloadEnabled(direct);
				//mhd.setChunkSize(100*MB);
				mhd.download(u, bos);
				bos.close();

				//System.out.println(name);
				return DOWNLOAD_SUCCESS;
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return DOWNLOAD_FAIL;
	}

	static void header(String name) {
		System.out.println();
		System.out.println("================== " + name + " ==================");
		System.out.println();
	}
}