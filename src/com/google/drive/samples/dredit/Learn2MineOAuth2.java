package com.google.drive.samples.dredit;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.client.extensions.appengine.auth.oauth2.AppEngineCredentialStore;

public class Learn2MineOAuth2 {

	// Path to client_secrets.json which should contain a JSON document such as:
	// {
	// "web": {
	// "client_id": "[[YOUR_CLIENT_ID]]",
	// "client_secret": "[[YOUR_CLIENT_SECRET]]",
	// "auth_uri": "https://accounts.google.com/o/oauth2/auth",
	// "token_uri": "https://accounts.google.com/o/oauth2/token"
	// }
	// }
	public static String CLIENT_ID = "1032662904755.apps.googleusercontent.com"; // eddredit
	public static String CLIENT_SECRET = "MNnpo8BqRFlR1Jkh2ZW4iJB4"; // ed dr edit
	private static final String CLIENTSECRETS_LOCATION = "/client_secrets.json";
	public static final String REDIRECT_URI = "http://localhost:8888/oauth2callback";
	public static final List<String> SCOPES = Arrays.asList(
			"https://www.googleapis.com/auth/drive.file",
			"https://www.googleapis.com/auth/userinfo.email",
			"https://www.googleapis.com/auth/userinfo.profile",
			"https://docs.googleusercontent.com/",
			"https://docs.google.com/feeds/",
			"https://spreadsheets.google.com/feeds/");

	private static GoogleAuthorizationCodeFlow flow = null;

	public static HashMap<String, String> codePersistance = new HashMap<String, String>();

	/**
	 * Exception thrown when an error occurred while retrieving credentials.
	 */
	@SuppressWarnings("serial")
	public static class GetCredentialsException extends Exception {

		protected String authorizationUrl;

		/**
		 * Construct a GetCredentialsException.
		 * 
		 * @param authorizationUrl
		 *            The authorization URL to redirect the user to.
		 */
		public GetCredentialsException(String authorizationUrl) {
			this.authorizationUrl = authorizationUrl;
		}

		/**
		 * Set the authorization URL.
		 */
		public void setAuthorizationUrl(String authorizationUrl) {
			this.authorizationUrl = authorizationUrl;
		}

		/**
		 * @return the authorizationUrl
		 */
		public String getAuthorizationUrl() {
			return authorizationUrl;
		}
	}

	/**
	 * Exception thrown when a code exchange has failed.
	 */
	@SuppressWarnings("serial")
	public static class CodeExchangeException extends GetCredentialsException {

		/**
		 * Construct a CodeExchangeException.
		 * 
		 * @param authorizationUrl
		 *            The authorization URL to redirect the user to.
		 */
		public CodeExchangeException(String authorizationUrl) {
			super(authorizationUrl);
		}

	}

	/**
	 * Exception thrown when no refresh token has been found.
	 */
	@SuppressWarnings("serial")
	public static class NoRefreshTokenException extends GetCredentialsException {

		/**
		 * Construct a NoRefreshTokenException.
		 * 
		 * @param authorizationUrl
		 *            The authorization URL to redirect the user to.
		 */
		public NoRefreshTokenException(String authorizationUrl) {
			super(authorizationUrl);
		}

	}

	/**
	 * Exception thrown when no user ID could be retrieved.
	 */
	@SuppressWarnings("serial")
	private static class NoUserIdException extends Exception {
	}

	/**
	 * Retrieved stored credentials for the provided user ID.
	 * 
	 * @param userId
	 *            User's ID.
	 * @return Stored Credential if found, {@code null} otherwise.
	 * @throws IOException
	 */
	public static Credential getStoredCredentials(String userId)
			throws IOException {
		return getFlow().loadCredential(userId);
	}

	public Credential getActiveCredential(String userId)
			throws NoRefreshTokenException, IOException {
		// ...
		AppEngineCredentialStore aecs = new AppEngineCredentialStore();
		Credential credential = getStoredCredentials(userId);
		aecs.store(userId, credential);
		// ...
		return credential;
	}

	/**
	 * Store OAuth 2.0 credentials in the application's database.
	 * 
	 * @param userId
	 *            User's ID.
	 * @param credentials
	 *            The OAuth 2.0 credentials to store.
	 * @throws IOException
	 */
	static void storeCredentials(String userId, Credential credentials)
			throws IOException {
		// credentialstorehashmap.put(userId, credentials);
		getFlow().getCredentialStore().store(userId, credentials);
	}

	/**
	 * Build an authorization flow and store it as a static class attribute.
	 * 
	 * @return GoogleAuthorizationCodeFlow instance.
	 * @throws IOException
	 *             Unable to load client_secrets.json.
	 */
	public static GoogleAuthorizationCodeFlow getFlow() throws IOException {
		if (flow == null) {
			HttpTransport httpTransport = new NetHttpTransport();
			JacksonFactory jsonFactory = new JacksonFactory();
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
					jsonFactory, Learn2MineOAuth2.class
							.getResourceAsStream(CLIENTSECRETS_LOCATION));
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
					jsonFactory, clientSecrets, SCOPES)
					.setAccessType("offline").setApprovalPrompt("force")
					.setCredentialStore(new AppEngineCredentialStore()).build();
		}
		return flow;
	}

	/**
	 * Exchange an authorization code for OAuth 2.0 credentials.
	 * 
	 * @param authorizationCode
	 *            Authorization code to exchange for OAuth 2.0 credentials.
	 * @return OAuth 2.0 credentials.
	 * @throws CodeExchangeException
	 *             An error occurred.
	 */
	static Credential exchangeCode(String authorizationCode, String userId)
			throws CodeExchangeException {
		try {
			GoogleAuthorizationCodeFlow flow = getFlow();
			GoogleTokenResponse response = flow
					.newTokenRequest(authorizationCode)
					.setRedirectUri(REDIRECT_URI).execute();

			return flow.createAndStoreCredential(response, userId);
		} catch (IOException e) {
			System.err.println("An error occurred: " + e);
			throw new CodeExchangeException(null);
		}
	}

	/**
	 * Send a request to the UserInfo API to retrieve the user's information.
	 * 
	 * @param credentials
	 *            OAuth 2.0 credentials to authorize the request.
	 * @return User's information.
	 * @throws NoUserIdException
	 *             An error occurred.
	 */
	static Userinfo getUserInfo(Credential credentials)
			throws NoUserIdException {
		Oauth2 userInfoService = Oauth2
				.builder(new NetHttpTransport(), new JacksonFactory())
				.setHttpRequestInitializer(credentials).build();
		Userinfo userInfo = null;
		try {
			userInfo = userInfoService.userinfo().get().execute();
		} catch (IOException e) {
			System.err.println("An error occurred: " + e);
		}
		if (userInfo != null && userInfo.getId() != null) {
			return userInfo;
		} else {
			throw new NoUserIdException();
		}
	}

	/**
	 * Retrieve the authorization URL.
	 * 
	 * @param emailAddress
	 *            User's e-mail address.
	 * @param state
	 *            State for the authorization URL.
	 * @return Authorization URL to redirect the user to.
	 * @throws IOException
	 *             Unable to load client_secrets.json.
	 */
	public static String getAuthorizationUrl(String emailAddress, String state)
			throws IOException {
		GoogleAuthorizationCodeRequestUrl urlBuilder = getFlow()
				.newAuthorizationUrl().setRedirectUri(REDIRECT_URI)
				.setState(state);
		urlBuilder.set("user_id", emailAddress);
		return urlBuilder.build();
	}

	/**
	 * Retrieve credentials using the provided authorization code.
	 * 
	 * This function exchanges the authorization code for an access token and
	 * queries the UserInfo API to retrieve the user's e-mail address. If a
	 * refresh token has been retrieved along with an access token, it is stored
	 * in the application database using the user's e-mail address as key. If no
	 * refresh token has been retrieved, the function checks in the application
	 * database for one and returns it if found or throws a
	 * NoRefreshTokenException with the authorization URL to redirect the user
	 * to.
	 * 
	 * @param authorizationCode
	 *            Authorization code to use to retrieve an access token.
	 * @param state
	 *            State to set to the authorization URL in case of error.
	 * @return OAuth 2.0 credentials instance containing an access and refresh
	 *         token.
	 * @throws NoRefreshTokenException
	 *             No refresh token could be retrieved from the available
	 *             sources.
	 * @throws IOException
	 *             Unable to load client_secrets.json.
	 */
	public static Credential getCredentials(String authorizationCode,
			String state, String userId) throws CodeExchangeException,
			NoRefreshTokenException, IOException {
		// DO NOT CALL
		String emailAddress = "";
		try {
			Credential credentials = exchangeCode(authorizationCode, userId);
			Userinfo userInfo = getUserInfo(credentials);
			emailAddress = userInfo.getEmail();
			if (credentials.getRefreshToken() != null) {
				storeCredentials(emailAddress, credentials);
				return credentials;
			} else {
				credentials = getStoredCredentials(emailAddress);
				if (credentials != null
						&& credentials.getRefreshToken() != null) {
					return credentials;
				}
			}
		} catch (CodeExchangeException e) {
			e.printStackTrace();
			// Drive apps should try to retrieve the user and credentials for
			// the current
			// session.
			// If none is available, redirect the user to the authorization URL.
			e.setAuthorizationUrl(getAuthorizationUrl(emailAddress, state));
			throw e;
		} catch (NoUserIdException e) {
			e.printStackTrace();
		}
		// No refresh token has been retrieved.
		String authorizationUrl = getAuthorizationUrl(emailAddress, state);
		throw new NoRefreshTokenException(authorizationUrl);
	}

	public static String CodeRequest(String email) throws IOException {
		String url = getAuthorizationUrl(email, "/profile");

		AuthorizationCodeResponseUrl authResponse = new AuthorizationCodeResponseUrl(
				url);
		return authResponse.getCode();
	}
}

