package dslab.glims.cli;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;
import com.google.appengine.api.files.LockException;
import com.google.common.base.Preconditions;


public class StructuredUploader {

	private static Permission perm = new Permission().setRole("writer").setType("user");

	/** Global instance of the HTTP transport. */
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/** Global Drive API client. */
	private static Drive drive;
	
	private final static String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	/** Authorizes the installed application to access user's protected data. */
	private static Credential authorize() throws Exception {
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				StructuredUploader.class.getResourceAsStream("client_secrets.json"));
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			System.out
					.println("Enter Client ID and Secret from https://code.google.com/apis/console/?api=drive "
							+ "into resources/client_secrets.json");
			System.exit(1);
		}
		// set up file credential store
		FileCredentialStore credentialStore = new FileCredentialStore(
				new java.io.File(System.getProperty("user.home"),
						".credentials/drive.json"), JSON_FACTORY);
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
				Collections.singleton(DriveScopes.DRIVE_FILE))
				.setCredentialStore(credentialStore).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Preconditions.checkArgument(args.length >= 2,
				"Usage: java -jar Uploader.jar <path to file> <root name>");
		String fileName = args[0];
		String rootName = args[1];
		
		
		try {
			try {
				// authorization
				Credential credential = authorize();
				// set up the global Drive instance
				drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY,
						credential).setApplicationName("Midgard Uploader/1.0")
						.build();

				// run commands

				System.out.println("Starting Resumable Media Upload");

//				List<ParentReference> parents = new ArrayList<ParentReference>();
//				File uploadedFile = uploadFile(fileName, "TODO_fix_title", "text/plain", parents, false);
				// Now update the permissions
//				drive.permissions().insert(uploadedFile.getId(), perm).execute();
				
				//AppEngineFile blobFile = downloadFileContentToBlob(service, file); // now returns aef
				writeDocsLabelsFromBlobStandardFormat(fileName, rootName, drive);
				
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

				System.out.println("Success!");
				return;
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		System.exit(1);
	}

	private static String determineParent(String email, String workflow) {
		String root = "0B7Jfx3RRVE5YenZEY1N5cE5pRms";
		String parentFolder = null;

		String level = root;
		String parent = root;
		boolean lowest = false;

		// Checks for user
		level = folderContains(email, level);
		if (level != null) {

			DateFormat today = new SimpleDateFormat("E, MM/dd/yyyy");
			Date now = new Date();
			String nowStr = today.format(now);

			// Checks for today's date
			parent = level;
			level = folderContains(nowStr, level);
			if (level != null) {

				// Checks for a workflowID folder
				parent = level;
				level = folderContains(workflow, level);
				// System.out.println("level: "+level);
				if (level != null) {

					// Finds the highest folder number; add 1 and creates it.
					try {
						File child = null;
						int lastRun = 0;
						ChildList children = drive.children().list(level)
								.execute();
						for (ChildReference element : children.getItems()) {
							child = drive.files().get(element.getId())
									.execute();
							lastRun = Integer.parseInt(child.getTitle());
						}
						lastRun += 1;
						String next = new Integer(lastRun).toString();
						// System.out.println("level: "+level);
						// System.out.println("next: "+next);
						parentFolder = createFolderWithParentAndTitle(level,
								next);

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					} finally {
						lowest = true;
					}

				} else {
					parentFolder = createFolderWithParentAndTitle(parent,
							workflow);
				}
			} else {
				parentFolder = createFolderWithParentAndTitle(parent, nowStr);
			}
		} else {
			parentFolder = createFolderWithParentAndTitle(parent, email);
		}

		try {
			File file = drive.files().get(parentFolder).execute();
			drive.permissions().insert(file.getId(), perm).execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!lowest)
			parentFolder = determineParent(email, workflow);

		return parentFolder;
	}
	
	private static String folderContains(String filename, String folderID) {

		// Returns the ID of a specified filename in a folder or null if it does
		// not exist

		File child = null;
		ChildList children = null;
		try {
			children = drive.children().list(folderID).execute();
			for (ChildReference element : children.getItems()) {
				child = drive.files().get(element.getId()).execute();
				if (child.getTitle().equals(filename)) {
					break;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// System.out.println("children: "+children.toPrettyString());
			// System.out.println("child: "+child.toPrettyString());
		}

		if (child != null && child.getTitle().equals(filename)) {
			return child.getId();
		}

		return null;
	}
	
	private static String createFolderWithParentAndTitle(String parentID,
			String title) {
		String folderMIME = "application/vnd.google-apps.folder";

		File resultsDes = new File()
				.setTitle(title)
				.setParents(
						Arrays.asList(new ParentReference().setId(parentID)))
				.setMimeType(folderMIME);
		File returned = null;
		try {
			// System.out.println(resultsDes);
			returned = drive.files().insert(resultsDes).execute();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (returned != null)
			return returned.getId();

		return null;
	}
	
	/** Uploads a file using either resumable or direct media upload. */
	private static File uploadFile(String filePath, String title,
			String mimeType, List<ParentReference> parents, boolean useDirectUpload) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setTitle(title);
	    //fileMetadata.setParents(Arrays.asList(new ParentReference().setId(parentId)));
		fileMetadata.setParents(parents);
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
	
	private static void writeDocsLabelsFromBlobStandardFormat(String fileName, String rootName, Drive service)
			throws FileNotFoundException, LockException, IOException {
				
		String optionalRootCollectionId = "kljkljlkjlkjlkj";
		BufferedReader reader = new BufferedReader(new FileReader(fileName));

		String metadataline = reader.readLine();

		String[] metadataArray = metadataline.split("\t");
		for (int i = 0; i < metadataArray.length; i++)
			metadataArray[i] = metadataArray[i].trim();

		File rootFolder = null;
		try {
			rootFolder = service.files().get(optionalRootCollectionId).execute();
		} catch (Exception e) {	}
		
		BatchRequest batch = service.batch();
		StructuredUploader uploader = new StructuredUploader();
		FileCallback rootFolderCallback = uploader.new FileCallback();
		if (rootFolder == null) {
			// Create root folder
			Insert rootFolderInsert = service.files().insert(new File().setTitle(rootName).setMimeType(FOLDER_MIME_TYPE));
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
				FileListCallback fileListCallback = uploader.new FileListCallback();
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
				FileCallback metadataKeyCallback = uploader.new FileCallback();
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
					FileCallback metadataValCallback = uploader.new FileCallback();
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
		reader.close();
		reader = new BufferedReader(new FileReader(fileName));
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
	}
	
	class FileCallback extends JsonBatchCallback<File> {
		private File file;
		private boolean done = false;
		private Insert httpRequest = null;

		public File getFile() {
			return file;
		}
		
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
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
	
	class FileListCallback extends JsonBatchCallback<FileList> {
		private FileList fileList;

		public FileList getFileList() {
			return fileList;
		}		
		
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
			System.out.println(e.getErrors().get(0).getMessage());
		}
		
		@Override
		public void onSuccess(FileList t, GoogleHeaders responseHeaders) {
			this.fileList = t;
		}
	};

}
