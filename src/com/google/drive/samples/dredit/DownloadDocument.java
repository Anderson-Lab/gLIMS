package com.google.drive.samples.dredit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import com.google.api.client.auth.oauth2.Credential;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.GoogleAuthTokenFactory.OAuth2Token;
import com.google.gdata.client.GoogleAuthTokenFactory.UserToken;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;

public class DownloadDocument {

	public static final String DEFAULT_HOST = "docs.google.com";

	public static final String SPREADSHEETS_SERVICE_NAME = "wise";
	public static final String SPREADSHEETS_HOST = "spreadsheets.google.com";

	private final String URL_FEED = "/feeds";
	private final String URL_DOWNLOAD = "/download";
	private final String URL_DOCLIST_FEED = "/private/full";

	private final String URL_DEFAULT = "/default";
	@SuppressWarnings("unused")
	private final String URL_FOLDERS = "/contents";
	@SuppressWarnings("unused")
	private final String URL_ACL = "/acl";
	@SuppressWarnings("unused")
	private final String URL_REVISIONS = "/revisions";

	@SuppressWarnings("unused")
	private final String URL_CATEGORY_DOCUMENT = "/-/document";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_SPREADSHEET = "/-/spreadsheet";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_PDF = "/-/pdf";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_PRESENTATION = "/-/presentation";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_STARRED = "/-/starred";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_TRASHED = "/-/trashed";
	@SuppressWarnings("unused")
	private final String URL_CATEGORY_FOLDER = "/-/folder";
	private final String URL_CATEGORY_EXPORT = "/Export";

	private InputStream raw_data;

	@SuppressWarnings("unused")
	private final String PARAMETER_SHOW_FOLDERS = "showfolders=true";

	private String host = "docs.google.com";
	private DocsService service;
	public GoogleService spreadsheetsService;

	/**
	 * Upload a file.
	 * 
	 * @param filepath
	 *            path to uploaded file.
	 * @param title
	 *            title to use for uploaded file.
	 * 
	 * @throws ServiceException
	 *             when the request causes an error in the Doclist service.
	 * @throws IOException
	 *             when an error occurs in communication with the Doclist
	 *             service.
	 * @throws DocumentListException
	 */
	public DocumentListEntry uploadFile(String filepath, String title)
			throws IOException, ServiceException, DocumentListException {
		if (filepath == null || title == null) {
			throw new DocumentListException(
					"Google Docs Error: null passed in for required parameters");
		}

		File file = new File(filepath);
		String mimeType = DocumentListEntry.MediaType.fromFileName(
				file.getName()).getMimeType();

		DocumentEntry newDocument = new DocumentEntry();
		newDocument.setFile(file, mimeType);
		newDocument.setTitle(new PlainTextConstruct(title));

		return service.insert(buildUrl(URL_DEFAULT + URL_DOCLIST_FEED),
				newDocument);
	}

	public InputStream getRawData() {
		return raw_data;
	}

	/**
	 * DownloadDocument() and getinfilepath() will need to be changed eventually
	 * Currently, exported file name is hard-coded to test.___
	 * 
	 * @throws Exception
	 */
	public DownloadDocument(String resource_id) throws Exception {
		UserService userService = UserServiceFactory.getUserService();
		User user = userService.getCurrentUser();
		service = new DocsService("edDrEdit");

		// service.setUserCredentials("Learn2Mine@gmail.com", "birglab1");
		service.setOAuth2Credentials(Learn2MineOAuth2.getStoredCredentials(user.getEmail()));
		
		spreadsheetsService = new GoogleService(SPREADSHEETS_SERVICE_NAME, "Learn2Mine"); // eventually switch to google authentication
		// spreadsheetsService.setUserCredentials("Learn2Mine@gmail.com",
		// "birglab1");
		spreadsheetsService.setOAuth2Credentials(Learn2MineOAuth2.getStoredCredentials(user.getEmail()));

		String[] type_resource_id = resource_id.split(":");
		if (type_resource_id[0].equalsIgnoreCase("spreadsheet")) {
			raw_data = downloadSpreadsheet(type_resource_id[1], "csv");
		} else if (type_resource_id[0].equalsIgnoreCase("document")) {
			raw_data = downloadDocument(type_resource_id[1], "txt");
		}

	}

	@SuppressWarnings("rawtypes")
	public InputStream CSVmetadatafirst(InputStream in, int num_samples)
			throws Exception, FileNotFoundException {

		Scanner infile = new Scanner(in);

		int i = 0;
		List<List> data = new ArrayList<List>();
		while (infile.hasNextLine()) {
			String line = infile.nextLine();
			data.add(Arrays.asList(line.split(",")));
			i = i + 1;

		}
		StringBuffer sb = new StringBuffer();
		for (i = 0; i < num_samples + 1; i++) {
			int cnt = 0;
			for (int j = 0; j < data.size(); j++) {
				if (data.get(j).size() != num_samples + 1)
					continue;
				if (cnt > 0)
					sb.append(",");
				sb.append((String) data.get(j).get(i));
				cnt++;
			}
			sb.append("\n");
		}

		/* copied lines excludes attribute names */

		infile.close();
		return new ByteArrayInputStream(sb.toString().getBytes());
	}

	/**
	 * Downloads a file.
	 * 
	 * @param exportUrl
	 *            the full url of the export link to download the file from.
	 * @param filepath
	 *            path and name of the object to be saved as.
	 * 
	 * @throws IOException
	 * @throws MalformedURLException
	 * @throws ServiceException
	 * @throws DocumentListException
	 */
	InputStream downloadFile(URL exportUrl) throws IOException,
			MalformedURLException, ServiceException, DocumentListException {
		if (exportUrl == null) {
			throw new DocumentListException(
					"null passed in for required parameters");
		}

		MediaContent mc = new MediaContent();
		mc.setUri(exportUrl.toString());
		System.out.println("ms: " + service.getMedia(mc).toString());
		MediaSource ms = service.getMedia(mc);

		InputStream inStream = null;
		inStream = ms.getInputStream();
		/*
		 * StringBuffer sb = new StringBuffer();
		 * 
		 * int c; while ((c = inStream.read()) != -1) { sb.append((char)c); }
		 * System.out.println(sb.toString()); System.exit(1);
		 */
		return new DataInputStream(inStream);

	}

	/**
	 * Downloads a spreadsheet file.
	 * 
	 * @param resourceId
	 *            the resource id of the object to be downloaded.
	 * @param format
	 *            format to download the file to. The following file types are
	 *            supported: spreadsheets: "ods", "pdf", "xls", "csv", "html",
	 *            "tsv"
	 * 
	 * @throws IOException
	 * @throws MalformedURLException
	 * @throws ServiceException
	 * @throws DocumentListException
	 */
	public InputStream downloadSpreadsheet(String resourceId, String format)
			throws IOException, MalformedURLException, ServiceException,
			DocumentListException {
		if (resourceId == null || format == null) {
			throw new DocumentListException(
					"null passed in for required parameters");
		}

		/*
		 * UserToken docsToken = (UserToken) service.getAuthTokenFactory()
		 * .getAuthToken(); UserToken spreadsheetsToken = (UserToken)
		 * spreadsheetsService .getAuthTokenFactory().getAuthToken();
		 * service.setUserToken(spreadsheetsToken.getValue());
		 */
		UserService userService = UserServiceFactory.getUserService();
		User user = userService.getCurrentUser();
		Credential c = Learn2MineOAuth2.getStoredCredentials(user.getEmail());
		service.setOAuth2Credentials(c);

		HashMap<String, String> parameters = new HashMap<String, String>();
		parameters.put("key",
				resourceId.substring(resourceId.lastIndexOf(':') + 1));
		parameters.put("exportFormat", format);

		// If exporting to .csv or .tsv, add the gid parameter to specify which
		// sheet to export
		if (format.equals("csv") || format.equals("tsv")) {
			parameters.put("gid", "0"); // download only the first sheet
		}

		URL url = buildUrl(SPREADSHEETS_HOST, URL_DOWNLOAD + "/spreadsheets"
				+ URL_CATEGORY_EXPORT, parameters);
		System.out.println(url.toString());

		InputStream result = downloadFile(url);

		// Restore docs token for our DocList client
		// service.setUserToken(docsToken.getValue());
		return result;
	}

	/**
	 * Builds a URL from a patch.
	 * 
	 * @param path
	 *            the path to add to the protocol/host
	 * 
	 * @throws MalformedURLException
	 * @throws DocumentListException
	 */
	private URL buildUrl(String path) throws MalformedURLException,
			DocumentListException {
		if (path == null) {
			throw new DocumentListException("null path");
		}

		return buildUrl(path, null);
	}

	/**
	 * Builds a URL with parameters.
	 * 
	 * @param path
	 *            the path to add to the protocol/host
	 * @param parameters
	 *            parameters to be added to the URL.
	 * 
	 * @throws MalformedURLException
	 * @throws DocumentListException
	 */
	private URL buildUrl(String path, String[] parameters)
			throws MalformedURLException, DocumentListException {
		if (path == null) {
			throw new DocumentListException("null path");
		}

		return buildUrl(host, path, parameters);
	}

	/**
	 * Builds a URL with parameters.
	 * 
	 * @param domain
	 *            the domain of the server
	 * @param path
	 *            the path to add to the protocol/host
	 * @param parameters
	 *            parameters to be added to the URL.
	 * 
	 * @throws MalformedURLException
	 * @throws DocumentListException
	 */
	private URL buildUrl(String domain, String path, String[] parameters)
			throws MalformedURLException, DocumentListException {
		if (path == null) {
			throw new DocumentListException("null path");
		}

		StringBuffer url = new StringBuffer();
		url.append("https://" + domain + URL_FEED + path);

		if (parameters != null && parameters.length > 0) {
			url.append("?");
			for (int i = 0; i < parameters.length; i++) {
				url.append(parameters[i]);
				if (i != (parameters.length - 1)) {
					url.append("&");
				}
			}
		}

		return new URL(url.toString());
	}

	/**
	 * Builds a URL with parameters.
	 * 
	 * @param domain
	 *            the domain of the server
	 * @param path
	 *            the path to add to the protocol/host
	 * @param parameters
	 *            parameters to be added to the URL as key value pairs.
	 * 
	 * @throws MalformedURLException
	 * @throws DocumentListException
	 */
	private URL buildUrl(String domain, String path,
			Map<String, String> parameters) throws MalformedURLException,
			DocumentListException {
		if (path == null) {
			throw new DocumentListException("null path");
		}

		StringBuffer url = new StringBuffer();
		url.append("https://" + domain + URL_FEED + path);

		if (parameters != null && parameters.size() > 0) {
			Set<Map.Entry<String, String>> params = parameters.entrySet();
			Iterator<Map.Entry<String, String>> itr = params.iterator();

			url.append("?");
			while (itr.hasNext()) {
				Map.Entry<String, String> entry = itr.next();
				url.append(entry.getKey() + "=" + entry.getValue());
				if (itr.hasNext()) {
					url.append("&");
				}
			}
		}

		return new URL(url.toString());
	}

	@SuppressWarnings("serial")
	public class DocumentListException extends Exception {
		public DocumentListException() {
			super();
		}

		public DocumentListException(String msg) {
			super(msg);
		}
	}

	public InputStream downloadDocument(String resourceId, String format)
			throws IOException, MalformedURLException, ServiceException,
			DocumentListException {
		
		if (resourceId == null || format == null) {
			throw new DocumentListException("null passed in for required parameters");
		}
		String[] parameters = { "docID=" + resourceId, "exportFormat=" + format };
		URL url = buildUrl(URL_DOWNLOAD + "/documents" + URL_CATEGORY_EXPORT,
				parameters);
		System.out.println(url.toString());
		// System.out.println("format: "+format);

		UserService userService = UserServiceFactory.getUserService();
		User user = userService.getCurrentUser();
		Credential c = Learn2MineOAuth2.getStoredCredentials(user.getEmail());
		service.setOAuth2Credentials(c);

		return downloadFile(url);
	}
}