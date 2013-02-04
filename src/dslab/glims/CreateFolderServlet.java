package dslab.glims;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.gson.Gson;

public class CreateFolderServlet extends GLIMSServlet {
	
	private final static String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
	
	/**
	 * First deployed version
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) {
		
		System.out.println("do get");
		
	}
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		Drive service = null;
		try {
			service = getDriveService(req, resp);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		String metadata = req.getParameter("metadata");
		String data = req.getParameter("data");
		String optionalRootCollectionId = "kljkljlkjlkjlkj";
		
		Gson gson = new Gson(); // used here for meta, later for data
		
		HashMap meta = gson.fromJson(metadata, HashMap.class);
		Object[] objects = meta.keySet().toArray();
		String[] metadataArray = new String[objects.length];
		for (int i = 0; i < objects.length; i++) {
			metadataArray[i] = objects[i].toString().trim();
		}
		
		File rootFolder = null;
		try {
			rootFolder = service.files().get(optionalRootCollectionId).execute();
		} catch (Exception e) {	}
		
		BatchRequest batch = service.batch();
		FileCallback rootFolderCallback = new FileCallback();
		
		String description = ((LinkedHashMap)meta.get("Description")).keySet().toString();
		if (rootFolder == null) {
			// Create root folder
			Insert rootFolderInsert = service.files().insert(new File().setTitle(description).setMimeType(FOLDER_MIME_TYPE));
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
		try {
			batch.execute();
			System.out.println("batch executed ");
		} catch (IOException e1) {
			e1.printStackTrace();
		} // Now create all the new folders and get the children subfolders
		if (rootFolder == null) { // Now it's been created
			rootFolder = rootFolderCallback.getFile();
			System.out.println("rootFolder " + rootFolder);
		}
		
		
		
		// Creates the new metadata key folders
		List<FileCallback> metadataKeyCallbacks = new ArrayList<FileCallback>();				
		ArrayList<ParentReference> list = new ArrayList<ParentReference>();
		System.out.println("rootFolder.getId() : " + rootFolder.getId());
		list.add(new ParentReference().setId(rootFolder.getId()));
		for (int i = 0; i < metadataArray.length; i++) {
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
		String[] firstData = null;
		List<FileCallback> metadataValCallbacks = new ArrayList<FileCallback>();
		
		for (Object key : meta.keySet()) {
			//System.out.println(key);
			String strKey = (String) key;
			LinkedHashMap content = (LinkedHashMap) meta.get(strKey);
			for (Object key2 : content.keySet()) {
				String metadataKey = strKey;
				// data[i] is this metadata value, so strkey2
				String strKey2 = (String) key2;
				// Only create if new
				if (!metadataChildren.get(metadataKey).containsKey(strKey2)) {
					ArrayList<ParentReference> valList = new ArrayList<ParentReference>();
					valList.add(new ParentReference().setId(metadataKeyFolders.get(metadataKey).getId()));
					Insert metadataValInsert = service.files().insert(new File().setTitle(strKey2).setMimeType(FOLDER_MIME_TYPE).setParents(valList));
					FileCallback metadataValCallback = new FileCallback();
					metadataValCallback.setHttpRequest(metadataValInsert);
					metadataValCallbacks.add(metadataValCallback);
					metadataValInsert.queue(batch, metadataValCallback);
					HashMap<String, File> metadataKeyChildren = metadataChildren
							.get(metadataKey);
					metadataKeyChildren.put(strKey2, null);
					// System.out.println(metadataKey + ": " + data[i]);
				}
				if (batch.size() > 10) {
					batch.execute();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				//System.out.println(content.get(key2));
				ArrayList output = (ArrayList)content.get(key2);
				for (Object item : output) {
					//System.out.println(item);
					String val = (String)item;
					//System.out.println(val);
				}
			}
			//System.out.println(content);
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
		
		
		// now to the data
		// unpack the json data data
		HashMap datahash = gson.fromJson(data, HashMap.class);
		
		Map<String, String> mydatakeys = new HashMap<String, String>(20);
		for (Object key : datahash.keySet()) {
			String strKey = (String) key;
			String content = (String) datahash.get(strKey);
			mydatakeys.put(strKey, content);
		}				
		for (String title : mydatakeys.keySet()) {
			File dataFile = new File();
			dataFile.setTitle(title);
			dataFile.setMimeType("text/plain");
			ArrayList<ParentReference> dataList = new ArrayList<ParentReference>();
			for (Object key : meta.keySet()) {
				String strKey = (String) key;
				LinkedHashMap content = (LinkedHashMap) meta.get(strKey);
				for (Object key2 : content.keySet()) {
					ArrayList output = (ArrayList)content.get(key2);
					for (Object item : output) {
						String val = (String)item;
						if (val.equals(title)) {
							File parent = metadataChildren.get(strKey).get(key2);
							dataList.add(new ParentReference().setId(parent.getId()));
						}
					}
				}
			}
			System.out.println(dataList);
			dataFile.setParents(dataList);
			ByteArrayContent byteArrayContent = new ByteArrayContent("text/plain", mydatakeys.get(title).getBytes());
			while (true) {
				try {
					dataFile = service.files().insert(dataFile, byteArrayContent).execute();
					break;
				} catch (GoogleJsonResponseException gjre) {
					System.out.println(gjre.getMessage());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ie) {
						System.out.println(ie.getMessage());
					}
				}
			}
		}
		System.out.println("done");
		// done
	}

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
	private Drive getDriveService(HttpServletRequest req,
			HttpServletResponse resp) throws IOException {
		Credential credentials = getCredential(req, resp);
		return new Builder(TRANSPORT, JSON_FACTORY, credentials).build();
	}
	
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
}
