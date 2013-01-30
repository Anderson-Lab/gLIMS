package edward.rest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.model.File;

import dsrg.glims.CredentialMediator;

public class TestTokenDriver {
	/**
	 * Path component under war/ to locate client_secrets.json file.
	 */
	public static final String CLIENT_SECRETS_FILE_PATH = "war/WEB-INF/client_secrets.json";
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
	
	/**
	 * JsonFactory to use in parsing JSON.
	 */
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/**
	 * HttpTransport to use for external requests.
	 */
	private static final HttpTransport TRANSPORT = new NetHttpTransport();
	
	public static void main(String[] args) throws Exception {
		CredentialMediator cm = new CredentialMediator (getClientSecretsStream(),SCOPES);
		
		Credential credentials = cm.buildEmptyCredential();
		credentials.setAccessToken("ya29.AHES6ZRjcN5Pz2Sojkd97TLxcdS8vyDUGmwNEvwV51Fdcb0");
		credentials.setRefreshToken("1/H_6WcAEEKgeGCNRzu-7WUgkWH-PAZ5FVtKInRreapQw");
		
		Drive service = new Builder(TRANSPORT, JSON_FACTORY, credentials).build();
		java.io.File fileContent = new java.io.File("war/robots.txt");
	    FileContent mediaContent = new FileContent("text/plain", fileContent);
	    
	    File body = new File();
	    body.setTitle("robots");
	    body.setDescription("are awesome");
	    body.setMimeType("text/plain");
	    
		File file = service.files().insert(body,mediaContent).execute();
		System.out.println("Done");
	}
	
	public static InputStream getClientSecretsStream() {
		try {
			return new FileInputStream(CLIENT_SECRETS_FILE_PATH);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
