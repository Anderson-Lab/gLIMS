package dslab.glims.cli;


/*
 * Copyright (c) 2011 Google Inc.
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
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;

import dslab.glims.CredentialMediator;
import dslab.glims.CredentialMediator.InvalidClientSecretsException;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class OAuth2Native {
	/** Global instance of the HTTP transport. */
	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	/** Global instance of the JSON factory. */
	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/**
	 * Scopes for which to request access from the user.
	 */
	public static final List<String> SCOPES = Arrays.asList(
			// Required to access and manipulate files.
			"https://www.googleapis.com/auth/drive",
			// Required to identify the user in our data store.
			"https://www.googleapis.com/auth/userinfo.email",
			"https://www.googleapis.com/auth/userinfo.profile",
			"https://docs.google.com/feeds/"/*
			 * "https://docs.googleusercontent.com/"
			 */);

	private static String CLIENT_ID = "1015223624366.apps.googleusercontent.com";
	private static String CLIENT_SECRET = "8ou5wFWbmbbFQuA-BD1ljaPR";

	public static Credential getCredentialFromJSessionID(String jSessionID) throws InvalidClientSecretsException {
		Credential credentials = CredentialMediator.buildEmptyCredential(CLIENT_ID,CLIENT_SECRET);

		String jsonResponse = OAuth2Native.getHTML("http://myglims.appspot.com/token",jSessionID);
		Data data = new Gson().fromJson(jsonResponse, Data.class);

		credentials.setAccessToken(data.AccessToken);
		credentials.setRefreshToken(data.RefreshToken);
		return credentials;
	}

	public static String getHTML(String urlToRead,String jSessionID) {
		URL url;
		HttpURLConnection conn;
		BufferedReader rd;
		String line;
		String result = "";
		try {
			url = new URL(urlToRead);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Cookie", "JSESSIONID='"+jSessionID+"'");
			rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = rd.readLine()) != null) {
				result += line;
			}
			rd.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	class Data {
		public String AccessToken;
		public String RefreshToken;
	}

	/**
	 * Authorizes the installed application to access user's protected data.
	 *
	 * @param transport HTTP transport
	 * @param jsonFactory JSON factory
	 * @param receiver verification code receiver
	 * @param scopes OAuth 2.0 scopes
	 */
	public static Credential authorize(String credentialPath) throws Exception {
		try {
			File cred = new File(credentialPath);

			FileCredentialStore fcs = new FileCredentialStore(cred , JSON_FACTORY);
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
					HTTP_TRANSPORT, JSON_FACTORY, CLIENT_ID, CLIENT_SECRET, SCOPES).setAccessType("offline")
					.setApprovalPrompt("auto").setCredentialStore(fcs).build();
			//.setAccessType("online")
			//.setApprovalPrompt("auto").build();
			Credential c = flow.loadCredential("datascienceresearch@gmail.com");

			if (c != null)
				return c;
			else {
				System.err.println("No valid token. Please visit https://myglims.appspot.com/token");
				/*browse(flow.newAuthorizationUrl().setRedirectUri(redirectUri).build());
	      // receive authorization code and exchange it for an access token
	      String code = receiver.waitForCode();
	      GoogleTokenResponse response =
	          flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
	      // store credential and return it
	      return flow.createAndStoreCredential(response, "datascienceresearch@gmail.com");
				 */
			}
			//System.out.println(flow.getCredentialStore());
			//;
			//return c;
		} finally {
			//receiver.stop();
		}
		return null;
	}

	/** Open a browser at the given URL. */
	/*private static void browse(String url) {
    // first try the Java Desktop
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Action.BROWSE)) {
        try {
          desktop.browse(URI.create(url));
          return;
        } catch (IOException e) {
          // handled below
        }
      }
    }
    // Next try rundll32 (only works on Windows)
    try {
      Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
      return;
    } catch (IOException e) {
      // handled below
    }
    // Next try the requested browser (e.g. "google-chrome")
    if (BROWSER != null) {
      try {
        Runtime.getRuntime().exec(new String[] {BROWSER, url});
        return;
      } catch (IOException e) {
        // handled below
      }
    }
    // Finally just ask user to open in their browser using copy-paste
    System.out.println("Please open the following URL in your browser:");
    System.out.println("  " + url);
  }*/

	private OAuth2Native() {
	}
}