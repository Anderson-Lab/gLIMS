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
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.common.base.Preconditions;

import java.io.FileOutputStream;
import java.io.IOException;
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

 /** Global instance of the HTTP transport. */
 private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

 /** Global instance of the JSON factory. */
 private static final JsonFactory JSON_FACTORY = new JacksonFactory();

 /** OAuth 2.0 scopes. */
 private static final List<String> SCOPES = Arrays.asList(
     "https://www.googleapis.com/auth/userinfo.profile",
     "https://www.googleapis.com/auth/userinfo.email",
     "https://www.googleapis.com/auth/drive"
     );

 private static final int MB = 0x100000;
 private static Oauth2 oauth2;
 private static Drive service;
 private static Permission perm = new Permission().setRole("writer").setType("user");

 public static void main(String[] args) throws Exception {
	Preconditions
		.checkArgument(args.length >= 2,
				"Usage: java -jar Downloader.jar <file id> <path to file> [-d [direct|resumable] -c <path to credentials> -j <jsession id> -s <client secrets>]\nDefault credential file is credentials.dat\nNo default jsession id\nDefault client secrets is client_secrets.json");

	 String fileID = args[0];
	 String pathToFile = args[1];
	 String credentialPath = "credentials.dat";
	 String jSessionID = null;
	 boolean direct = true;
	 if ((args.length-2) % 2 == 0) {
		 for (int i = 2; i < args.length; i+=2) {
			 if (args[i].equals("-c"))
				 credentialPath = args[i+1];
			 else if (args[i].equals("-s"))
				 OAuth2Native.CLIENT_SECRETS_FILE_PATH = args[i+1];
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
	 	 
	// authorization
    Credential credential = null;
    if (jSessionID == null) {
		credential = OAuth2Native.authorize(HTTP_TRANSPORT, JSON_FACTORY, SCOPES,credentialPath);
    } else
    	credential = OAuth2Native.getCredentialFromJSessionID(jSessionID);

    // set up global Oauth2 instance
     oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
         "gLIMS/1.0").build();
     // run commands
     //tokenInfo(credential.getAccessToken());
     //userInfo();
     
     service = new Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();
	 
     downloadDriveFile(fileID, pathToFile, direct);
 }
 
 public static void downloadDriveFile(String ID, String pathToFile, boolean direct) {
	 try {
	     try {
	       // get file id
	       String fileID = ID;
	    	 
	       
	       File fileToDownload = service.files().get(fileID).execute();
	       GenericUrl u = new GenericUrl(fileToDownload.getDownloadUrl());
	       System.out.println("md5: "+fileToDownload.getMd5Checksum());
		   
	       Get request = service.files().get(fileToDownload.getId());
		   //String name = fileToDownload.getTitle();
		   
		   FileOutputStream bos = new FileOutputStream(pathToFile);
		   MediaHttpDownloader mhd = request.getMediaHttpDownloader();
		   mhd.setDirectDownloadEnabled(direct);
		   //mhd.setChunkSize(100*MB);
		   mhd.download(u, bos);
		   bos.close();
	
		   //System.out.println(name);
	       return;
	     } catch (IOException e) {
	       System.err.println(e.getMessage());
	     }
	   } catch (Throwable t) {
	     t.printStackTrace();
	   }
	   System.exit(1);
 }
 
 static void header(String name) {
   System.out.println();
   System.out.println("================== " + name + " ==================");
   System.out.println();
 }
}