package com.google.drive.samples.dredit;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileReadChannel;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.files.LockException;
import com.google.drive.samples.dredit.model.ClientFile;
import com.google.gson.Gson;

/**
 * Servlet providing a small API for the DrEdit JavaScript client to use in
 * manipulating files. Each operation (GET, POST, PUT) issues requests to the
 * Google Drive API.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class FileServlet extends DrEditServlet {

	private final static String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	/**
	 * Given a {@code file_id} URI parameter, return a JSON representation of
	 * the given file.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		Drive drive = getDriveService(req, resp);
		String fileId = req.getParameter("file_id");
		
		System.out.println("file id: " + fileId);
		

		if (fileId == null) {
			System.out.println("file id was null");
			sendError(resp, 400, "The `file_id` URI parameter must be specified.");
			return;
		}
		
		File file = null;
		try {
			file = drive.files().get(fileId).execute();
			System.out.println("****file " + file);
		} catch (GoogleJsonResponseException e) {
			System.out.println("problem opening file");
			System.out.println(e.getMessage());
			System.out.println();
			if (e.getStatusCode() == 401) { 
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will recover.
				deleteCredential(req, resp);
				sendError(resp, 401, "Unauthorized");
				return;
			}
		}
				
		if (file != null) {
			
			// get all the metadata for a file
			// does this by going two levels up in the file hierarchy
			// there is not enough error handling in this area
			List<ParentReference> parents = file.getParents();
			
			StringBuffer buffer = new StringBuffer();
			buffer.append("{ ");
			File parentFile = drive.files().get(parents.get(0).getId()).execute();
			List<ParentReference> grandParents = parentFile.getParents();
			ParentReference grandParent = grandParents.get(0);
			File grandParentFile = drive.files().get(grandParent.getId()).execute();
			buffer.append(" \"" + grandParentFile.getTitle() + "\": \"" + parentFile.getTitle() + "\"");
			for (int i=1; i<parents.size(); i++) {
				parentFile = drive.files().get(parents.get(i).getId()).execute();
				grandParents = parentFile.getParents();
				grandParent = grandParents.get(0);
				grandParentFile = drive.files().get(grandParent.getId()).execute();
				buffer.append(", \"" + grandParentFile.getTitle() + "\": \"" + parentFile.getTitle() + "\"");
			}
			buffer.append(" }");
			String parentsString = buffer.toString();
			
			CredentialMediator cm = getCredentialMediator(req, resp);

			String content = downloadFileContent(drive, file);

			if (content == null) {
				content = "";
			}
			resp.setContentType(JSON_MIMETYPE);
			resp.getWriter().print(new ClientFile(file, content, parentsString).toJson());
		} else {
			sendError(resp, 404, "File not found");
		}

	}
	
//	@Override
//	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
//
//		DocsService docsService = new DocsService("Edward's application");
//
//		Drive service = getDriveService(req, resp);
//		String fileId = req.getParameter("file_id");
//
//		if (fileId == null) {
//			sendError(resp, 400, "The `file_id` URI parameter must be specified.");
//			return;
//		}
//
//		File file = null;
//		try {
//			file = service.files().get(fileId).execute();
//		} catch (GoogleJsonResponseException e) {
//			e.printStackTrace();
//			if (e.getStatusCode() == 401) {
//				// The user has revoked our token or it is otherwise bad.
//				// Delete the local copy so that their next page load will
//				// recover.
//				deleteCredential(req, resp);
//				sendError(resp, 401, "Unauthorized");
//				return;
//			}
//		}
//
//		if (file != null) {
//
//			// String content = downloadFileContent(service, file);
//			//
//			// CredentialMediator cm = getCredentialMediator(req, resp);
//			// Credential credential;
//			// try {
//			// credential = cm.getActiveCredential();
//			// try {
//			// System.out.println("calling write docs labels");
//			// writeDocsLabels(content, service.getApplicationName(),
//			// credential, service);
//			// } catch (ServiceException e) {
//			// e.printStackTrace();
//			// }
//			// } catch (NoRefreshTokenException e) {
//			// e.printStackTrace();
//			// }
//
//			String content = "asdf jkl;";
//			AppEngineFile blobFile = downloadFileContentToBlob(service, file); // now
//																				// returns
//																				// aef
//
//			writeDocsLabelsFromBlobStandardFormat(blobFile, service);
//
//			// get parents
//			String parents = null;
//
//			// String parents = "";
//			// List<ParentReference> list = file.getParentReference();
//			// for (int i = 0; i < list.size(); i++) {
//			// String id = list.get(i).getId();
//			// File f = service.files().get(id).execute();
//			// File grandparent = service.files()
//			// .get(f.getParentReference().get(0).getId()).execute();
//			// parents += grandparent.getTitle() + ": ";
//			// parents += f.getTitle() + "\n";
//			// }
//			// System.out.println("parents\n" + parents);
//			// if (content == null) {
//			// content = "";
//			// }
//
//			resp.setContentType(JSON_MIMETYPE);
//			resp.getWriter().print(new ClientFile(file, content, parents).toJson());
//		} else {
//			sendError(resp, 404, "File not found");
//		}
//	}

	/**
	 * Create a new file given a JSON representation, and return the JSON
	 * representation of the created file.
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Drive service = getDriveService(req, resp);
		ClientFile clientFile = new ClientFile(req.getReader());
		File file = clientFile.toFile();

		File folder = new File();
		folder.setTitle("A test folder");
		folder.setMimeType("application/vnd.google-apps.folder");
		folder = service.files().insert(folder).execute();

		if (!clientFile.content.equals("")) {
			file = service.files().insert(file, ByteArrayContent.fromString(clientFile.mimeType, clientFile.content))
					.execute();
		} else {
			file = service.files().insert(file).execute();
		}

		List<ParentReference> list = file.getParents();
		ParentReference pc = new ParentReference();
		pc.put("kind", "drive#fileLink");
		pc.put("id", folder.getId());
		list.add(pc);
		file = service.files().insert(file).execute();

		resp.setContentType(JSON_MIMETYPE);
		resp.getWriter().print(new Gson().toJson(file.getId()).toString());
	}

	/**
	 * Update a file given a JSON representation, and return the JSON
	 * representation of the created file.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Drive service = getDriveService(req, resp);
		ClientFile clientFile = new ClientFile(req.getReader());
		File file = clientFile.toFile();
		file = service
				.files()
				.update(clientFile.resource_id, file,
						ByteArrayContent.fromString(clientFile.mimeType, clientFile.content)).execute();

		resp.setContentType(JSON_MIMETYPE);
		resp.getWriter().print(new Gson().toJson(file.getId()).toString());
	}

	/**
	 * Download the content of the given file.
	 * 
	 * @param service
	 *            Drive service to use for downloading.
	 * @param file
	 *            File metadata object whose content to download.
	 * @return String representation of file content. String is returned here
	 *         because this app is setup for text/plain files.
	 * @throws IOException
	 *             Thrown if the request fails for whatever reason.
	 */
	private String downloadFileContent(Drive service, File file) throws IOException {

		// URL url = new URL(file.getDownloadUrl());
		//
		// System.out.println(file.getDownloadUrl());
		//
		// InputStream is = null; try { is = downloadFile(service,url); } catch
		// (ServiceException e) { // TODO Auto-generated catch block
		// e.printStackTrace(); return null; } Scanner infile = new Scanner(is);
		//
		// int i = 0; StringBuffer contents = new StringBuffer(); while
		// (infile.hasNextLine()) { String line = infile.nextLine();
		// contents.append(line).append("\n"); } return contents.toString();

		GenericUrl u = new GenericUrl(file.getDownloadUrl());
		Get request = service.files().get(file.getId());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		MediaHttpDownloader mhd = request.getMediaHttpDownloader();
		mhd.setChunkSize(10 * 0x100000);
		mhd.download(u, bos);

		// FileService fileService = FileServiceFactory.getFileService();
		//
		// // Create a new Blob file with mime-type "text/plain"
		// AppEngineFile blobFile = fileService.createNewBlobFile("text/plain");
		//
		// // Open a channel to write to it
		// boolean lock = true;
		// FileWriteChannel writeChannel =
		// fileService.openWriteChannel(blobFile, lock);
		//
		// mhd.download(u, Channels.newOutputStream(writeChannel));
		// return blobFile;

		return bos.toString();
		/*
		 * try { return new
		 * Scanner(response.getContent()).useDelimiter("\\A").next(); } catch
		 * (java.util.NoSuchElementException e) { return ""; }
		 */
	}

	private AppEngineFile downloadFileContentToBlob(Drive service, File file) throws IOException {
		FileService fileService = FileServiceFactory.getFileService();
		AppEngineFile blobfile = fileService.createNewBlobFile("text/plain");
		FileWriteChannel writeChannel = fileService.openWriteChannel(blobfile, true);

		GenericUrl genericUrl = new GenericUrl(file.getDownloadUrl());
		Get get = service.files().get(file.getId());
		MediaHttpDownloader downloader = get.getMediaHttpDownloader();

		downloader.download(genericUrl, Channels.newOutputStream(writeChannel));
		writeChannel.closeFinally();
		return blobfile;
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
//	InputStream downloadFile(DocsService service, URL exportUrl) throws IOException, MalformedURLException,
//			ServiceException {
//		if (exportUrl == null) {
//			return null;
//		}
//
//		MediaContent mc = new MediaContent();
//		String u = exportUrl.toString();
//		u = u.replaceAll("securesc", "secure");
//		mc.setUri(u);
//		service.setReadTimeout(0);
//		MediaSource ms = service.getMedia(mc);
//
//		InputStream inStream = null;
//		inStream = ms.getInputStream();
//		/*
//		 * StringBuffer sb = new StringBuffer();
//		 * 
//		 * int c; while ((c = inStream.read()) != -1) { sb.append((char)c); }
//		 * System.out.println(sb.toString()); System.exit(1);
//		 */
//		return new DataInputStream(inStream);
//	}

	/**
	 * Build and return a Drive service object based on given request
	 * parameters.
	 * 
	 * @param req
	 *            Request to use to fetch code parameter or accessToken session
	 *            attribute.
	 * @param resp
	 *            HTTP response to use for redirecting for authorization if
	 *            needed.
	 * @return Drive service object that is ready to make requests, or null if
	 *         there was a problem.
	 */
	private Drive getDriveService(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Credential credentials = getCredential(req, resp);
		return new Builder(TRANSPORT, JSON_FACTORY, credentials).build();	}

	/*
	 * public InputStream downloadDocument(String resourceId, String format)
	 * throws IOException, MalformedURLException, ServiceException,
	 * DocumentListException {
	 * 
	 * if (resourceId == null || format == null) { throw new
	 * DocumentListException("null passed in for required parameters"); }
	 * String[] parameters = { "docID=" + resourceId, "exportFormat=" + format
	 * }; URL url = buildUrl(URL_DOWNLOAD + "/documents" + URL_CATEGORY_EXPORT,
	 * parameters); System.out.println(url.toString()); //
	 * System.out.println("format: "+format);
	 * 
	 * UserService userService = UserServiceFactory.getUserService(); User user
	 * = userService.getCurrentUser(); Credential c =
	 * Learn2MineOAuth2.getStoredCredentials(user.getEmail());
	 * service.setOAuth2Credentials(c);
	 * 
	 * return downloadFile(url); }
	 */

	/**
	 * @param blobFile
	 * @param drive
	 * @throws FileNotFoundException
	 * @throws LockException
	 * @throws IOException
	 */
	private void writeDocsLabelsFromBlobStandardFormat(AppEngineFile blobFile, Drive drive)
			throws FileNotFoundException, LockException, IOException {

		FileService fileService = FileServiceFactory.getFileService();
		FileReadChannel readChannel = fileService.openReadChannel(blobFile, false);
		BufferedReader reader = new BufferedReader(Channels.newReader(readChannel, "UTF8"));

		String metadataline = reader.readLine();
		String firstDataline = reader.readLine();

		String[] metadataArray = metadataline.split("\t");
		String[] data = firstDataline.split("\t");
		ArrayList<File> metadataField = new ArrayList<File>();
		ArrayList<File> metadataValue = new ArrayList<File>();

		// make root folder
		String desc = "Description";
		int descIndex;
		for (descIndex = 0; descIndex < metadataArray.length; descIndex++) {
			if (metadataArray[descIndex].equals(desc)) {
				break;
			}
		}
		File rootFolder = new File();
		rootFolder.setTitle(data[descIndex]);
		rootFolder.setMimeType(FOLDER_MIME_TYPE);
		while (true) {
			try {
				rootFolder = drive.files().insert(rootFolder).execute();
				break;
			} catch (GoogleJsonResponseException e) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}

		int dataStart = 0;
		for (int i = 0; i < metadataArray.length; i++) {
			if (metadataArray[i].equals("X")) {
				dataStart = i;
				break;
			}
			File folder = new File();
			folder.setTitle(metadataArray[i]);
			folder.setMimeType(FOLDER_MIME_TYPE);
			folder.setParents(Arrays.asList(new ParentReference().setId(rootFolder.getId())));
			while (true) {
				try {
					folder = drive.files().insert(folder).execute();
					break;
				} catch (GoogleJsonResponseException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
			metadataField.add(folder);
		}

		HashMap<String, File> hashMap = new HashMap<String, File>();

		// first row - metadata values
		for (int i = 0; i < dataStart; i++) {
			File folder = new File();
			folder.setTitle(data[i]);
			folder.setMimeType(FOLDER_MIME_TYPE);
			folder.setParents(Arrays.asList(new ParentReference().setId(metadataField.get(i).getId())));
			while (true) {
				try {
					folder = drive.files().insert(folder).execute();
					break;
				} catch (GoogleJsonResponseException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
			metadataValue.add(folder);
			hashMap.put(data[i], folder);

		}

		for (String string : hashMap.keySet()) {
			System.out.println(string);
		}

		// first row - data values
		StringBuffer buffer = new StringBuffer();
		for (int i = dataStart; i < data.length; i++) {
			buffer.append(metadataArray[i] + "\t" + data[i] + "\n");
		}
		File dataFile = new File();
		dataFile.setTitle(data[dataStart]);
		dataFile.setMimeType("text/plain");
		ArrayList<ParentReference> list = new ArrayList<ParentReference>();
		for (File parent : metadataValue) {
			list.add(new ParentReference().setId(parent.getId()));
		}
		dataFile.setParents(list);
		ByteArrayContent byteArrayContent = new ByteArrayContent("text/plain", buffer.toString().getBytes());
		while (true) {
			try {
				dataFile = drive.files().insert(dataFile, byteArrayContent).execute();
				break;
			} catch (GoogleJsonResponseException e) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}

		// all the rest of the data
		String line;
		while ((line = reader.readLine()) != null) {
			metadataValue.clear();
			list.clear();
			String[] dataline = line.split("\t"); // need no nulls

			// find or create the correct folder
			for (int i = 0; i < dataStart; i++) {

				if (hashMap.containsKey(dataline[i])) { // folder already there
					metadataValue.add(hashMap.get(dataline[i]));
				} else { // create new folder
					File folder = new File();
					folder.setTitle(dataline[i]);
					folder.setMimeType(FOLDER_MIME_TYPE);
					folder.setParents(Arrays.asList(new ParentReference().setId(metadataField.get(i)
							.getId())));
					while (true) {
						try {
							folder = drive.files().insert(folder).execute();
							break;
						} catch (GoogleJsonResponseException e) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}
						}
					}
					metadataValue.add(folder);
					hashMap.put(dataline[i], folder);
				}
			}

			// create the datafile
			buffer = new StringBuffer();
			for (int i = dataStart; i < dataline.length; i++) {
				buffer.append(metadataArray[i] + "\t" + dataline[i] + "\n");
			}
			dataFile = new File();
			dataFile.setTitle(dataline[dataStart]);
			dataFile.setMimeType("text/plain");
			// set parents
			for (File parent : metadataValue) {
				list.add(new ParentReference().setId(parent.getId()));
			}
			dataFile.setParents(list);
			byteArrayContent = new ByteArrayContent("text/plain", buffer.toString().getBytes()); // bytearraycontent
			while (true) {
				try {
					dataFile = drive.files().insert(dataFile, byteArrayContent).execute();
					break;
				} catch (GoogleJsonResponseException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	private void writeDocsLabels(String content, String applicationName, Credential credential, Drive drive)
			throws IOException {

		// make root folder
		String desc = "Description\t";
		int startIndex = content.indexOf(desc) + desc.length();
		String description = content.substring(startIndex, content.indexOf("\n", startIndex));
		File rootFolder = new File();
		rootFolder.setTitle(description);
		rootFolder.setMimeType(FOLDER_MIME_TYPE);
		rootFolder = drive.files().insert(rootFolder).execute();

		String[][] metadata = readMetadata(content, drive);

		ArrayList<File>[] parents = makeFolders(metadata, rootFolder, drive, content);

		for (int i = 0; i < parents[1].size(); i++) {
			System.out.println(parents[1].get(i).getTitle());
		}

		String[] xyStrings = readData(content, drive);
		// File[] parents = new File[xyStrings.length];
		// for (int i=0; i<xyStrings.length; i++) {
		// parents[i] = rootFolder;
		// }

		for (int i = 1; i < xyStrings.length; i++) {
			File file = new File();
			file.setTitle("data");
			file.setMimeType("text/plain");
			ArrayList<ParentReference> list = new ArrayList<ParentReference>();
			for (File parent : parents[i]) {
				list.add(new ParentReference().setId(parent.getId()));
			}

			file.setParents(list);
			ByteArrayContent bac = new ByteArrayContent("text/plain", xyStrings[i].getBytes());
			while (true) {
				try {
					file = drive.files().insert(file, bac).execute();
					break;
				} catch (GoogleJsonResponseException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
		}

		/*
		 * System.out.println("writedocslabels");
		 * 
		 * String[] lines = content.split("\n");
		 * 
		 * int dataStart; for (dataStart=0; dataStart<lines.length; dataStart++)
		 * { if (lines[dataStart].startsWith("X ")) { break; } }
		 * 
		 * 
		 * // declare arrays String[][] metadata = new String[dataStart][];
		 * File[][] folders = new File[dataStart][]; String[][] data = new
		 * String[lines.length - dataStart][];
		 * 
		 * // initialize arrays int i; for (i=0; i<dataStart; i++) { metadata[i]
		 * = lines[i].split("\t"); for (String s : metadata[i]) { s = s.trim();
		 * } folders[i] = new File[metadata[i].length]; } for (; i<lines.length;
		 * i++) { data[i-dataStart] = lines[i].split("\t"); for (String s :
		 * data[i-dataStart]) { s = s.trim(); } }
		 * 
		 * // make root folder File rootFolder = null; for (i=0;
		 * i<metadata.length; i++) { if (metadata[i][0].equals("Description")) {
		 * rootFolder = new File(); rootFolder.setTitle(metadata[i][1]);
		 * rootFolder.setMimeType(FOLDER_MIME_TYPE); rootFolder =
		 * drive.files().insert(rootFolder).execute(); } }
		 * 
		 * // check to see that we made a root folder if (rootFolder == null) {
		 * System.out.println("Error: root folder not created."); }
		 * 
		 * // int a, b; // for (a=0; a<metadata.length; a++) { // for (b=0;
		 * b<metadata[a].length; b++) { // System.out.print("*" +
		 * metadata[a][b].trim() + "* "); // } // System.out.println(); // }
		 * 
		 * // go through all the metadata System.out.println("metadata"); for
		 * (i=0; i<metadata.length; i++) { if (metadata[i][1].equals("")) { //
		 * there's no info in this metadata category, so skip it } else { File
		 * metaFolder = new File(); metaFolder.setTitle(metadata[i][0]);
		 * metaFolder.setMimeType(FOLDER_MIME_TYPE);
		 * metaFolder.setParents(Arrays.asList(new
		 * ParentReference.setId(rootFolder.getId()))); metaFolder =
		 * drive.files().insert(metaFolder).execute();
		 * 
		 * // iterate thru the rest of metadata[i] and see if we need to put
		 * more folders inside metaFolder // we know we need at least one more
		 * int j = 1; while (j<metadata[i].length &&
		 * !metadata[i][j].trim().equals("")) {
		 * 
		 * File infoFolder = new File();
		 * infoFolder.setTitle(metadata[i][j].trim());
		 * infoFolder.setMimeType(FOLDER_MIME_TYPE);
		 * infoFolder.setParents(Arrays.asList(new
		 * ParentReference.setId(metaFolder.getId())));
		 * 
		 * // need not to duplicate folders here int k; boolean duplicated =
		 * false; for (k=1; k<j; k++) { if
		 * (metadata[i][j].trim().equals(metadata[i][k].trim())) { folders[i][j]
		 * = folders[i][k]; duplicated = true; break; } }
		 * 
		 * if (!duplicated) { infoFolder =
		 * drive.files().insert(infoFolder).execute(); // put the infoFolder in
		 * the folders array so that we can find it later
		 * //System.out.println("new folder " + infoFolder); folders[i][j] =
		 * infoFolder; } System.out.println(metadata[i][j]);
		 * System.out.println(folders[i][j]); j++; } System.out.println(); } }
		 * 
		 * System.out.println("metadata.lenght " + metadata.length);
		 * System.out.println("folders.length" + folders.length);
		 * 
		 * System.out.println("\nData:"); int xval, yval; for (yval=1;
		 * yval<data[1].length; yval++) {
		 * 
		 * StringBuffer xystring = new StringBuffer();
		 * 
		 * for (xval=0; xval<data.length; xval++) {
		 * xystring.append(data[xval][0].trim() + " " + data[xval][yval].trim()
		 * + "\n"); }
		 * 
		 * File xydata = new File(); xydata.setTitle(data[0][yval]);
		 * xydata.setMimeType("text/plain"); ByteArrayContent bac = new
		 * ByteArrayContent("text/plain", xystring.toString().getBytes());
		 * xydata = drive.files().insert(xydata, bac).execute();
		 * System.out.println("File " + xydata.getTitle()); int c; for (c=0;
		 * c<metadata.length; c++) { if (yval<metadata[c].length &&
		 * metadata[c][yval]!=null) { System.out.println(metadata[c][yval]); } }
		 * 
		 * System.out.println(xystring); System.out.println(); }
		 */

		// for (File[] array : folders) {
		// for (File file : array) {
		// System.out.print(file + " ");
		// }
		// System.out.println();
		// }

	}

	private String[][] readMetadata(String content, Drive drive) {
		String[] lines = content.split("\n");
		int dataStart;
		for (dataStart = 0; dataStart < lines.length; dataStart++) {
			if (lines[dataStart].startsWith("X\t")) {
				break;
			}
		}
		int numColumns = lines[dataStart + 1].split("\t").length;
		String[][] metadata = new String[dataStart][numColumns];
		for (int i = 0; i < dataStart; i++) {
			String[] tabs = lines[i].split("\t");
			metadata[i][0] = tabs[0];
			if (tabs.length == numColumns) {
				for (int j = 1; j < numColumns; j++) {
					metadata[i][j] = tabs[j];
				}
			} else if (tabs.length == 2) {
				for (int j = 1; j < numColumns; j++) {
					metadata[i][j] = tabs[1];
				}
			} else {
				// System.out.println(tabs[0] +
				// " skipped because problematic on line: " + i);
				for (int j = 0; j < numColumns; j++) {
					metadata[i][j] = null;
				}
			}

		}

		return metadata;
	}

	private int getNumColumns(String content) {
		String[] lines = content.split("\n");
		int dataStart;
		for (dataStart = 0; dataStart < lines.length; dataStart++) {
			if (lines[dataStart].startsWith("X\t")) {
				break;
			}
		}
		return lines[dataStart + 1].split("\t").length;
	}

	private ArrayList<File>[] makeFolders(String[][] metadata, File root, Drive drive, String content)
			throws IOException {

		int numCols = getNumColumns(content);
		System.out.println(numCols);
		ArrayList<File>[] parents = new ArrayList[numCols];
		for (int i = 0; i < numCols; i++) {
			parents[i] = new ArrayList<File>();
		}
		// System.out.println("look at category values");
		for (int i = 0; i < metadata.length; i++) {
			if (metadata[i][0] != null) {

				System.out.print(metadata[i][0] + " ");

				File category = new File();
				category.setTitle(metadata[i][0]);
				category.setMimeType(FOLDER_MIME_TYPE);
				category.setParents(Arrays.asList(new ParentReference().setId(root.getId())));
				category = drive.files().insert(category).execute();

				for (int j = 1; j < metadata[i].length; j++) {
					boolean duplicate = false;
					int k;
					for (k = 1; k < j; k++) {
						if (metadata[i][j].equals(metadata[i][k])) {
							duplicate = true;
							break;
						}
					}
					if (!duplicate) {
						// System.out.print(metadata[i][j] + " ");
						File folder = new File();
						folder.setTitle(metadata[i][j]);
						folder.setMimeType(FOLDER_MIME_TYPE);
						folder.setParents(Arrays.asList(new ParentReference().setId(category.getId())));
						while (true) {
							try {
								folder = drive.files().insert(folder).execute();
								System.out.print(folder.getTitle() + " ");
								break;
							} catch (GoogleJsonResponseException e) {
								System.out.println("caught insert folder exception");
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
							}
						}
						parents[j].add(folder);
						// parents.get(j).setParentLink(arg0)
					} else {
						// System.out.print("*" + metadata[i][k] + " ");
						for (File file : parents[k]) {
							if (file.getTitle().equals(metadata[i][k])) {
								parents[j].add(file);
							}
						}
					}
				}
				System.out.println();
			}
		}
		return parents;
	}

	private String[] readData(String content, Drive drive) {

		String[] lines = content.split("\n");
		int dataStart;
		for (dataStart = 0; dataStart < lines.length; dataStart++) {
			if (lines[dataStart].startsWith("X\t")) {
				break;
			}
		}

		int numCols;
		numCols = lines[dataStart + 1].split("\t").length;
		String[] xyStrings = new String[numCols];

		String[][] data = new String[lines.length][];
		for (int i = 0; i < lines.length; i++) {
			data[i] = lines[i].split("\t");
		}

		for (int i = 1; i < data[dataStart + 1].length; i++) {
			StringBuffer xybuffer = new StringBuffer();
			for (int j = dataStart; j < data.length; j++) {
				xybuffer.append(data[j][0] + " " + data[j][i] + "\n");
			}
			xyStrings[i] = xybuffer.toString();
		}

		return xyStrings;
	}

}
