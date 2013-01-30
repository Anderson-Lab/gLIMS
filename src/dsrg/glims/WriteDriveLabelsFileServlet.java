package dsrg.glims;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileReadChannel;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.files.LockException;
import com.google.appengine.tools.util.ClientCookie;
import com.google.gson.Gson;

import dsrg.glims.model.ClientFile;




/**
 * Servlet providing a small API for the DrEdit JavaScript client to use in
 * manipulating files. Each operation (GET, POST, PUT) issues requests to the
 * Google Drive API.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class WriteDriveLabelsFileServlet extends GLIMSServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final static String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
	
	/**
	 * This writes all the labels to the Drive
	 * @param file_id - the Drive id of the file to be split and labeled
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		//req.getSession().setAttribute("userId", req.getParameter("userId"));
		Drive service = getDriveService(req, resp);
		String fileId = req.getParameter("file_id");

		if (fileId == null) {
			sendError(resp, 400, "The `file_id` URI parameter must be specified.");
			return;
		}

		File file = null;
		try {
			file = service.files().get(fileId).execute();
		} catch (GoogleJsonResponseException e) {
			e.printStackTrace();
			if (e.getStatusCode() == 401) {
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will recover.
				deleteCredential(req, resp);
				sendError(resp, 401, "Unauthorized");
				return;
			}
		}
		
		if (file != null) {

			String content = "asdf jkl;";
			AppEngineFile blobFile = downloadFileContentToBlob(service, file);
			writeDocsLabelsFromBlobStandardFormat(blobFile, service);
			
			resp.setContentType(JSON_MIMETYPE);
			resp.getWriter().print(new ClientFile(file, content).toJson());
		} else {
			sendError(resp, 404, "File not found");
		}
	}

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
//	private String downloadFileContent(Drive service, File file) throws IOException {
//
//		GenericUrl u = new GenericUrl(file.getDownloadUrl());
//		Get request = service.files().get(file.getId());
//		ByteArrayOutputStream bos = new ByteArrayOutputStream();
//		MediaHttpDownloader mhd = request.getMediaHttpDownloader();
//		mhd.setChunkSize(10 * 0x100000);
//		mhd.download(u, bos);
//		
//		return bos.toString();
//	}

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
	
	class GetCallback extends JsonBatchCallback<File> {
		private File file;

		public File getFile() {
			return file;
		}
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
			log(e.getErrors().get(0).getMessage());
		}
		@Override
		public void onSuccess(File t, GoogleHeaders responseHeaders) {
			this.file = t;
		}
	};
	
	class FileListCallback extends JsonBatchCallback<FileList> {
		private FileList fileList;

		public FileList getFileList() {
			return fileList;
		}		
		
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
			log(e.getErrors().get(0).getMessage());
		}
		@Override
		public void onSuccess(FileList t, GoogleHeaders responseHeaders) {
			this.fileList = t;
		}
	};
	
	/**
	 * @param blobFile
	 * @param service
	 * @throws FileNotFoundException
	 * @throws LockException
	 * @throws IOException
	 */
	private void writeDocsLabelsFromBlobStandardFormat(AppEngineFile blobFile, Drive service)
			throws FileNotFoundException, LockException, IOException {
				
		String optionalRootCollectionId = "kljkljlkjlkjlkj";
		// old version with no batching
		FileService fileService = FileServiceFactory.getFileService();
		FileReadChannel readChannel = fileService.openReadChannel(blobFile, false);
		BufferedReader reader = new BufferedReader(Channels.newReader(readChannel, "UTF8"));

		String metadataline = reader.readLine();

		String[] metadataArray = metadataline.split("\t");
		for (int i = 0; i < metadataArray.length; i++)
			metadataArray[i] = metadataArray[i].trim();

		File rootFolder = null;
		try {
			rootFolder = service.files().get(optionalRootCollectionId).execute();
		} catch (Exception e) {	}
		
		BatchRequest batch = service.batch();
		FileCallback rootFolderCallback = new FileCallback();
		if (rootFolder == null) {
			// Create root folder
			Insert rootFolderInsert = service.files().insert(new File().setTitle(blobFile.getNamePart()).setMimeType(FOLDER_MIME_TYPE));
			rootFolderInsert.queue(batch, rootFolderCallback);
		}
		
		// Keep track of all metadata key folders that already exist and their metadata value subfolders
		HashMap<String,String> id2Title = new HashMap<String,String>();
		HashMap<String,File> metadataKeyFolders = new HashMap<String,File>();
		HashMap<String,FileListCallback> metadataChildrenCallbacks = new HashMap<String,FileListCallback>();
		if (rootFolder != null) { // Get metadata key folders
			List<File> metadataKeyFileList = service.files().list().setQ("'"+rootFolder.getId()+"' in parents").execute().getItems();
			for (File file : metadataKeyFileList) {
				metadataKeyFolders.put(file.getTitle(),file);
				id2Title.put(file.getId(), file.getTitle());
				com.google.api.services.drive.Drive.Files.List metadataChildrenFileList = service.files().list().setQ("'"+file.getId()+"' in parents");
				FileListCallback fileListCallback = new FileListCallback();
				metadataChildrenFileList.queue(batch, fileListCallback);
				metadataChildrenCallbacks.put(file.getTitle(), fileListCallback);
			}
		}
		batch.execute(); // Now create all the new folders and get the children subfolders
		if (rootFolder == null) // Now it's been created
			rootFolder = rootFolderCallback.getFile();
		
		// Creates the new metadata key folders
		List<FileCallback> metadataKeyCallbacks = new ArrayList<FileCallback>();				
		ArrayList<ParentReference> list = new ArrayList<ParentReference>();
		list.add(new ParentReference().setId(rootFolder.getId()));
		int dataStart = 0;
		for (int i = 0; i < metadataArray.length; i++) {
			if (metadataArray[i].equals("X")) {
				dataStart = i;
				break;
			}
			if (!metadataKeyFolders.containsKey(metadataArray[i])) {
				Insert metadataKeyInsert = service.files().insert(new File().setTitle(metadataArray[i]).setMimeType(FOLDER_MIME_TYPE).setParents(list));
				FileCallback metadataKeyCallback = new FileCallback();
				metadataKeyCallbacks.add(metadataKeyCallback);
				metadataKeyInsert.queue(batch,metadataKeyCallback);
			}
			if (batch.size() > 10)
				batch.execute();
		}
		if (batch.size() > 0)
			batch.execute(); // All metadata key folders have been created
		
		HashMap<String,HashMap<String,File>> metadataChildren = new HashMap<String,HashMap<String,File>>();
		// Now we need to correct the folder hierarchy
		for (FileCallback callback : metadataKeyCallbacks) {
			id2Title.put(callback.getFile().getId(), callback.getFile().getTitle());
			metadataKeyFolders.put(callback.getFile().getTitle(), callback.getFile()); // Add to master list
			if (!metadataChildren.containsKey(callback.getFile().getTitle()))
				metadataChildren.put(callback.getFile().getTitle(), new HashMap<String,File>());
		}
		
		// Now create an up to date child list from the new metadata and the old
		for (String metadataKey : metadataChildrenCallbacks.keySet()) {
			if (!metadataChildren.containsKey(metadataKey))
				metadataChildren.put(metadataKey, new HashMap<String,File>());			
			HashMap<String,File> metadataKeyChildren = metadataChildren.get(metadataKey);
			for (File file : metadataChildrenCallbacks.get(metadataKey).getFileList().getItems())
				metadataKeyChildren.put(file.getTitle(), file);			
		}
		
		// Now create the metadata values that don't already exist
		String line;
		String [] firstData = null;
		List<FileCallback> metadataValCallbacks = new ArrayList<FileCallback>();				
		while ((line = reader.readLine()) != null) {
			String [] data = line.split("\t");
			if (firstData == null) {
				firstData = line.split("\t");
				for (int i = 0; i < firstData.length; i++)
					firstData[i] = firstData[i].trim();
			}
			
			for (int i = 0; i < dataStart; i++) {
				data[i] = data[i].trim();
				if (firstData[i].equals(""))
					continue;
				if (data[i].equals(""))
					data[i] = firstData[i];
				String metadataKey = metadataArray[i]; 
				// Only create if new
				if (!metadataChildren.get(metadataKey).containsKey(data[i])) {						
					ArrayList<ParentReference> valList = new ArrayList<ParentReference>();
					valList.add(new ParentReference().setId(metadataKeyFolders.get(metadataKey).getId()));		
					Insert metadataValInsert = service.files().insert(new File().setTitle(data[i]).setMimeType(FOLDER_MIME_TYPE).setParents(valList));
					FileCallback metadataValCallback = new FileCallback();
					metadataValCallback.setHttpRequest(metadataValInsert);
					metadataValCallbacks.add(metadataValCallback);
					metadataValInsert.queue(batch,metadataValCallback);
					HashMap<String,File> metadataKeyChildren = metadataChildren.get(metadataKey);
					metadataKeyChildren.put(data[i], null);
					//System.out.println(metadataKey + ": " + data[i]);
				}
				if (batch.size() > 10) {
					batch.execute();	
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		while (batch.size() > 0) {
			batch.execute();
			for (FileCallback callback : metadataValCallbacks) {
				if (callback.getFile() != null) {
					List<ParentReference> prList = callback.getFile().getParents();
					for (ParentReference pr : prList) {
						String metadataKey = id2Title.get(pr.getId());
						//if (!metadataChildren.containsKey(metadataKey))
						//	metadataChildren.put(metadataKey, new HashMap<String,File>());
						HashMap<String,File> metadataKeyChildren = metadataChildren.get(metadataKey); 
						metadataKeyChildren.put(callback.getFile().getTitle(), callback.getFile());
					}
				} else {
					callback.getHttpRequest().queue(batch, callback);
				}
			}
			System.out.println(batch.size());
		}
		
		// Now to the data
		readChannel = fileService.openReadChannel(blobFile, false);
		reader = new BufferedReader(Channels.newReader(readChannel, "UTF8"));
		// Now open the file again and actually upload the data
		reader.readLine(); // Read header which we don't need
		while ((line = reader.readLine()) != null) {
			String [] data = line.split("\t");
						
			ArrayList<ParentReference> dataList = new ArrayList<ParentReference>();
			for (int i = 0; i < dataStart; i++) {
				if (firstData[i].equals(""))
					continue;
				data[i] = data[i].trim();
				if (data[i].equals(""))
					data[i] = firstData[i];
				String metadataKey = metadataArray[i];
				File parent = metadataChildren.get(metadataKey).get(data[i]);
				dataList.add(new ParentReference().setId(parent.getId()));
			}
			
			StringBuffer buffer = new StringBuffer();
			buffer.append("X\tY\n");			
			for (int i = dataStart+1; i < data.length; i++)
				buffer.append(metadataArray[i]).append("\t").append(data[i]).append("\n");
			
			File dataFile = new File();
			dataFile.setTitle(data[dataStart]);
			dataFile.setMimeType("text/plain");
			dataFile.setParents(dataList);
			ByteArrayContent byteArrayContent = new ByteArrayContent("text/plain", buffer.toString().getBytes());
			while (true) {
				try {
					dataFile = service.files().insert(dataFile, byteArrayContent).execute();
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
				dataFile = service.files().insert(dataFile, byteArrayContent).execute();
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
			for (int i = 0; i < dataline.length; i++) {
				if (dataline[i].equals("")) {
					dataline[i] = data[i];
				}
			}
			
			// find or create the correct folder
			for (int i = 0; i < dataStart; i++) {

				if (hashMap.containsKey(dataline[i])) { // folder already there
					metadataValue.add(hashMap.get(dataline[i]));
				} else { // create new folder
					File folder = new File();
					folder.setTitle(dataline[i]);
					folder.setMimeType(FOLDER_MIME_TYPE);
					folder.setParents(Arrays.asList(new ParentReference().setId(metadataField.get(i).getId())));
					while (true) {
						try {
							folder = service.files().insert(folder).execute();
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
					dataFile = service.files().insert(dataFile, byteArrayContent).execute();
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
		*/

//		FileService fileService = FileServiceFactory.getFileService();
//		FileReadChannel readChannel = fileService.openReadChannel(blobFile, false);
//		BufferedReader reader = new BufferedReader(Channels.newReader(readChannel, "UTF8"));
//
//		String metadataline = reader.readLine();
//		String firstDataline = reader.readLine();
//
//		String[] metadataArray = metadataline.split("\t");
//		String[] data = firstDataline.split("\t");
//		ArrayList<File> metadataField = new ArrayList<File>();
//		ArrayList<File> metadataValue = new ArrayList<File>();
//		ArrayList<GetCallback> getValCallbacks = new ArrayList<GetCallback>();
//
//		// make root folder
//		String desc = "description";
//		int descIndex;
//		for (descIndex = 0; descIndex < metadataArray.length; descIndex++) {
//			if (metadataArray[descIndex].equalsIgnoreCase((desc))) {
//				break;
//			}
//		}
//		File rootFolder = new File();
//		rootFolder.setTitle(data[descIndex]);
//		rootFolder.setMimeType(FOLDER_MIME_TYPE);
//		while (true) {
//			try {
//				rootFolder = service.files().insert(rootFolder).execute();
//				break;
//			} catch (GoogleJsonResponseException e) {
//				try {
//					Thread.sleep(1000);
//				} catch (InterruptedException e1) {
//					e1.printStackTrace();
//				}
//			}
//		}
//		
//		BatchRequest batch = service.batch();
//		
//		// metadata categories
//		List<GetCallback> getCatCallbacks = new ArrayList<GetCallback>();
//		
//		int dataStart = 0;
//		for (int i = 0; i < metadataArray.length; i++) {
//			if (metadataArray[i].equals("X")) {
//				dataStart = i;
//				break;
//			}
//			File folder = new File();
//			folder.setTitle(metadataArray[i]);
//			folder.setMimeType(FOLDER_MIME_TYPE);
//			folder.setParents(Arrays.asList(new ParentReference().setId(rootFolder.getId())));
//			Insert insert = service.files().insert(folder);
//			GetCallback getBack = new GetCallback();
//			getCatCallbacks.add(getBack);
//			insert.queue(batch, getBack);
//			metadataField.add(folder);
//		}
//		batch.execute();
//		
//		HashMap<String, File> hashMap = new HashMap<String, File>();
//
//		// first row - metadata values
//		for (int i = 0; i < dataStart; i++) {
//			File folder = new File();
//			folder.setTitle(data[i]);
//			folder.setMimeType(FOLDER_MIME_TYPE);
//			folder.setParents(Arrays.asList(new ParentReference().setId(getCatCallbacks.get(i).getFile().getId())));
//			Insert insert = service.files().insert(folder);
//			GetCallback getBack = new GetCallback();
//			getValCallbacks.add(getBack);
//			insert.queue(batch, getBack);
//			metadataValue.add(folder);
//			hashMap.put(data[i], folder);
//
//		}
//		batch.execute();
//
//		for (String string : hashMap.keySet()) {
//			System.out.println(string);
//		}
//
//		// first row - data values
//		StringBuffer buffer = new StringBuffer();
//		for (int i = dataStart; i < data.length; i++) {
//			buffer.append(metadataArray[i] + "\t" + data[i] + "\n");
//		}
//		File dataFile = new File();
//		dataFile.setTitle(data[dataStart]);
//		dataFile.setMimeType("text/plain");
//		ArrayList<ParentReference> list = new ArrayList<ParentReference>();
//		for (GetCallback gcb : getValCallbacks) {
//			list.add(new ParentReference().setId(gcb.getFile().getId()));
//		}
//		dataFile.setParents(list);
//		ByteArrayContent byteArrayContent = new ByteArrayContent("text/plain", buffer.toString().getBytes());
//		while (true) {
//			try {
//				dataFile = service.files().insert(dataFile, byteArrayContent).execute();
//				break;
//			} catch (GoogleJsonResponseException e) {
//				try {
//					Thread.sleep(1000);
//				} catch (InterruptedException e1) {
//					e1.printStackTrace();
//				}
//			}
//		}
//
//		// all the rest of the data
//		
//		// metadata 
//		String line;
//		while ((line = reader.readLine()) != null) {
//			metadataValue.clear();
//			list.clear();
//			String[] dataline = line.split("\t"); // need no nulls
//			for (int i=0; i<dataline.length; i++) {
//				if (dataline[i].equals("")) {
//					dataline[i] = data[i];
//				}
//			}
//
//			// find or create the correct folder
//			for (int i = 0; i < dataStart; i++) {
//
//				if (hashMap.containsKey(dataline[i])) { // folder already there
//					metadataValue.add(hashMap.get(dataline[i]));
//				}
//				else { // create new folder
//					File folder = new File();
//					folder.setTitle(getValCallbacks.get(i).getFile().getTitle());
//					//folder.setTitle(dataline[i]);
//					folder.setMimeType(FOLDER_MIME_TYPE);
//					folder.setParents(Arrays.asList(new ParentReference().setId(getCatCallbacks.get(i).getFile().getId())));
//					while (true) {
//						try {
//							folder = service.files().insert(folder).execute();
//							break;
//						} catch (GoogleJsonResponseException e) {
//							try {
//								Thread.sleep(1000);
//							} catch (InterruptedException e1) {
//								e1.printStackTrace();
//							}
//						}
//					}
//					metadataValue.add(folder);
//					hashMap.put(dataline[i], folder);
//				}
//			}
//
//			// create the datafile
//			buffer = new StringBuffer();
//			for (int i = dataStart; i < dataline.length; i++) {
//				buffer.append(metadataArray[i] + "\t" + dataline[i] + "\n");
//			}
//			dataFile = new File();
//			dataFile.setTitle(dataline[dataStart]);
//			dataFile.setMimeType("text/plain");
//			// set parents
//			for (File parent : metadataValue) {
//				list.add(new ParentReference().setId(parent.getId()));
//			}
//			dataFile.setParents(list);
//			byteArrayContent = new ByteArrayContent("text/plain", buffer.toString().getBytes()); // bytearraycontent
//			while (true) {
//				try {
//					dataFile = service.files().insert(dataFile, byteArrayContent).execute();
//					break;
//				} catch (GoogleJsonResponseException e) {
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e1) {
//						e1.printStackTrace();
//					}
//				}
//			}
//		}
//	}
//
//	private void writeDocsLabels(String content, String applicationName, Credential credential, Drive drive)
//			throws IOException {
//
//		// make root folder
//		String desc = "Description\t";
//		int startIndex = content.indexOf(desc) + desc.length();
//		String description = content.substring(startIndex, content.indexOf("\n", startIndex));
//		File rootFolder = new File();
//		rootFolder.setTitle(description);
//		rootFolder.setMimeType(FOLDER_MIME_TYPE);
//		rootFolder = drive.files().insert(rootFolder).execute();
//
//		String[][] metadata = readMetadata(content, drive);
//
//		ArrayList<File>[] parents = makeFolders(metadata, rootFolder, drive, content);
//
//		for (int i = 0; i < parents[1].size(); i++) {
//			System.out.println(parents[1].get(i).getTitle());
//		}
//
//		String[] xyStrings = readData(content, drive);
//		// File[] parents = new File[xyStrings.length];
//		// for (int i=0; i<xyStrings.length; i++) {
//		// parents[i] = rootFolder;
//		// }
//
//		for (int i = 1; i < xyStrings.length; i++) {
//			File file = new File();
//			file.setTitle("data");
//			file.setMimeType("text/plain");
//			ArrayList<ParentReference> list = new ArrayList<ParentReference>();
//			for (File parent : parents[i]) {
//				list.add(new ParentReference().setId(parent.getId()));
//			}
//
//			file.setParents(list);
//			ByteArrayContent bac = new ByteArrayContent("text/plain", xyStrings[i].getBytes());
//			while (true) {
//				try {
//					file = drive.files().insert(file, bac).execute();
//					break;
//				} catch (GoogleJsonResponseException e) {
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e1) {
//						e1.printStackTrace();
//					}
//				}
//			}
//		}

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
