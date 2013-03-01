package dslab.glims;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.model.File;

import dslab.glims.CredentialMediator.InvalidClientSecretsException;

/**
 * Abstract servlet that sets up credentials and provides some convenience
 * methods.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public abstract class GLIMSServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	public static final HttpTransport TRANSPORT = new NetHttpTransport();
	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/**
	 * Default MIME type of files created or handled by gLIMS.
	 * 
	 * This is also set in the Google APIs Console under the Drive SDK tab.
	 */
	public static final String DEFAULT_MIMETYPE = "text/plain";

	/**
	 * MIME type to use when sending responses back to gLIMS JavaScript client.
	 */
	public static final String JSON_MIMETYPE = "application/json";

	/**
	 * Path component under war/ to locate client_secrets.json file.
	 */
	public static final String CLIENT_SECRETS_FILE_PATH = "/WEB-INF/client_secrets.json";

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
	class FileCallback extends JsonBatchCallback<File> {
		private File file;
		private boolean done = false;
		private Insert httpRequest = null;

		public File getFile() {
			return file;
		}
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
			log(e.getMessage());
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
	

	public void sendError(HttpServletResponse resp, int code, String message) {
		try {
			resp.sendError(code, message);
		} catch (IOException e) {
			throw new RuntimeException(message);
		}
	}

	public InputStream getClientSecretsStream() {
		return getServletContext().getResourceAsStream(CLIENT_SECRETS_FILE_PATH);
	}

	public CredentialMediator getCredentialMediator(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		// Authorize or fetch credentials. Required here to ensure this happens
		// on first page load. Then, credentials will be stored in the user's
		// session.
		CredentialMediator mediator;
		try {
			mediator = new CredentialMediator(req, getClientSecretsStream(), SCOPES);
			mediator.getActiveCredential();
			return mediator;
		} catch (CredentialMediator.NoRefreshTokenException e) {
			try {
				resp.sendRedirect(e.getAuthorizationUrl());
			} catch (IOException ioe) {
				throw new RuntimeException("Failed to redirect user for authorization");
			}
			throw new RuntimeException("No refresh token found. Re-authorizing.");
		} catch (InvalidClientSecretsException e) {
			String message = String.format("This application is not properly configured: %s", e.getMessage());
			sendError(resp, 500, message);
			throw new RuntimeException(message);
		}
	}

	public Credential getCredential(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		try {
			CredentialMediator mediator = getCredentialMediator(req, resp);
			return mediator.getActiveCredential();
		} catch (CredentialMediator.NoRefreshTokenException e) {
			try {
				resp.sendRedirect(e.getAuthorizationUrl());
			} catch (IOException ioe) {
				ioe.printStackTrace();
				throw new RuntimeException("Failed to redirect for authorization.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getClientId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		return getCredentialMediator(req, resp).getClientSecrets().getWeb().getClientId();
	}

	public void deleteCredential(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		CredentialMediator mediator = getCredentialMediator(req, resp);
		mediator.deleteActiveCredential();
	}

}
