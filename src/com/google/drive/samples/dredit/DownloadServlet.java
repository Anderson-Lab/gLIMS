package com.google.drive.samples.dredit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.drive.samples.dredit.CredentialMediator.NoRefreshTokenException;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.util.ServiceException;

public class DownloadServlet extends DrEditServlet {

	private static final long serialVersionUID = 1L; // did this because eclipse wanted. not sure why

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		String fileId = request.getParameter("file_id");
		String outputType = request.getParameter("output_type");
		
		if (fileId == null) {
			sendError(response, 400, "The 'file_id' URI parameter must be specified.");
			return;
		}
		if (outputType == null) {
			sendError(response, 400, "The 'output_type' URI parameter must be specified.");
			return;
		}
		if (outputType.equals("standard")) {
			doStdOutput(request, response, fileId);
		} else if (outputType.equals("alternate")) {
			System.out.println("alternate output type");
			doAltOutput(request, response, fileId);
		} else {
			sendError(response, 400, "The 'output_type' parameter must be 'standard' or 'alternate.'");
		}
		
	}
	
	class ChildListCallback extends JsonBatchCallback<ChildList> {
		private ChildList children;
		private String id;
		public void setId(String id) {
			this.id = id;
		}
		public String getId() {
			return id;
		}
		@Override
		public void onSuccess(ChildList _children, GoogleHeaders responseHeaders) {
			log("Success");
			children = _children;
		}
		public ChildList getChildren() { 
			return children; 
		}
		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders) throws IOException {
			log(e.getErrors().get(0).getMessage());
		}
	};
	
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
	
	/**
	 * alternate output
	 */
	private void doAltOutput(HttpServletRequest request, HttpServletResponse response, String fileId) throws IOException {
		System.out.println("do alternate output");
		System.out.println("this method is not implemented!!!");
	}

	private String getFileTitle(List<GetCallback> getCallbacks, String metadataKeyId) {
		String metadataKeyName = null;
		for (GetCallback getCallback : getCallbacks) {
			if (getCallback.getFile().getId().equals(metadataKeyId)) {
				metadataKeyName = getCallback.getFile().getTitle();
			}
		}
		if (metadataKeyName == null) {
			System.out.println("metadataKeyName was null");
		}
		return metadataKeyName;
	}
	
	/**
	 * standard output
	 */
	private void doStdOutput(HttpServletRequest request, HttpServletResponse response, String fileId) throws IOException {
		Drive service = getDriveService(request, response);
		//fileId = "0B_4L9UB-A6C3ZDN3b2lEaTdFN1E"; // root folder. remove this after debug
		
		// get the the collection to export with drive api
		File file = null;
		try {
			file = service.files().get(fileId).execute();
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 401) {
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will recover.
				deleteCredential(request, response);
				sendError(response, 401, "Unauthorized");
				return;
			}
		}
		
		if (file != null) {
			// Get a file service and open a channel to write to
			FileService fileService = FileServiceFactory.getFileService();
			AppEngineFile blobFile = fileService.createNewBlobFile("text/plain");
			boolean lock = true;
			FileWriteChannel writeChannel = fileService.openWriteChannel(blobFile, lock);
			PrintWriter out = new PrintWriter(Channels.newWriter(writeChannel, "UTF-8"));
			
			// set up the hm of hm's representing metadata categories
			HashMap<String, HashMap<String, String>> fileHash = new HashMap<String, HashMap<String, String>>();
			HashMap<String, ArrayList<String>> masterMetadata = new HashMap<String, ArrayList<String>>();
			
			// get all metadata cats
			List<ChildReference> metadataKeys = service.children().list(fileId).execute().getItems();
			BatchRequest batch = service.batch();
			
			List<ChildListCallback> childCallbacks = new ArrayList<ChildListCallback>();
			List<GetCallback> getCallbacks = new ArrayList<GetCallback>();
			
			for (ChildReference metadataKey : metadataKeys) { // for every metadataKey folder								
				String metadataKeyId = metadataKey.getId();
				
				// batch up childlist requests
				com.google.api.services.drive.Drive.Children.List httpRequest = service.children().list(metadataKey.getId());
				ChildListCallback callback = new ChildListCallback();
				callback.setId(metadataKeyId);
				childCallbacks.add(callback);
				httpRequest.queue(batch, callback);
				
				// btach up getTitle requests
				Get getMetadataKeyName = service.files().get(metadataKeyId);
				GetCallback getBack = new GetCallback();
				getCallbacks.add(getBack);
				getMetadataKeyName.queue(batch, getBack);
				
			}
			batch.execute();
			
			for (ChildListCallback callback : childCallbacks) {
				List<ChildReference> metadataVals = callback.getChildren().getItems();
				String metadataKeyId = callback.getId();
				String metadataKeyName = getFileTitle(getCallbacks, metadataKeyId);

				List<ChildListCallback> metadataValbatchCallbacks = new ArrayList<ChildListCallback>();
				List<GetCallback> metadataValNameCallbacks = new ArrayList<GetCallback>();
				for(ChildReference metadataVal : metadataVals) { // for every data file in the metadataVal folder
					String metadataValId = metadataVal.getId();
					
					com.google.api.services.drive.Drive.Children.List httpRequest = service.children().list(metadataValId);
					ChildListCallback metadataValcallback = new ChildListCallback();
					metadataValcallback.setId(metadataValId);
					metadataValbatchCallbacks.add(metadataValcallback);
					httpRequest.queue(batch, metadataValcallback);
					
					Get getMetadataValName = service.files().get(metadataValId);
					GetCallback metadataValGetBack = new GetCallback();
					metadataValNameCallbacks.add(metadataValGetBack);
					getMetadataValName.queue(batch, metadataValGetBack);
				}
				batch.execute();
				
				for (ChildListCallback metadataValcallback : metadataValbatchCallbacks) {
					List<ChildReference> dataFiles = metadataValcallback.getChildren().getItems();	
					String metadataValName = getFileTitle(metadataValNameCallbacks, metadataValcallback.getId());					
					
					for (ChildReference dataFile : dataFiles) {
						String key = dataFile.getId();
						if (!fileHash.containsKey(key)) {
							fileHash.put(key, new HashMap<String, String>());
						}
						HashMap<String, String> metadata = fileHash.get(key);
						if (!masterMetadata.containsKey(metadataKeyName)) {
							System.out.println("metadataKeyName" + metadataKeyName);
							masterMetadata.put(metadataKeyName, new ArrayList<String>());
						}
						ArrayList<String> keys = masterMetadata.get(metadataKeyName);
						keys.add(key);
						metadata.put(metadataKeyName, metadataValName);
					}
				}
			}
			
			List<GetCallback> getDataCallbacks = new ArrayList<GetCallback>();
			String [] keys = fileHash.keySet().toArray(new String[0]);
			for (String key : keys) {
				Get getDataKey = service.files().get(key);
				GetCallback getBack = new GetCallback();
				getDataCallbacks.add(getBack);
				getDataKey.queue(batch, getBack);
			}
			batch.execute();
			
			// print out the contents of the hashes in standard format
			for (String metadata_name : masterMetadata.keySet()) {
				out.print(metadata_name + "\t");
			}
			boolean first = true;
			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				File dataFile = getDataCallbacks.get(i).getFile();
				if (first) {
					String firstData = downloadFileContent(service, dataFile);
					String[] firstlines = firstData.split("\n");
					for (String string : firstlines) {
						out.print(string.split(" ")[0] + "\t");
					}
					out.print("\n");
					first = false;
				}
				for (String metadata_name : masterMetadata.keySet()) {
					out.print(fileHash.get(key).get(metadata_name) + "\t");
				}
				String fileData = downloadFileContent(service, dataFile);
				String[] lines = fileData.split("\n");
				for (String string : lines) {
					out.print(string.split(" ")[1] + "\t");
				}
				out.println();
			}
			
			// close everything
			out.close();
			writeChannel.closeFinally();
			
			// serve the blob
			BlobKey blobKey = fileService.getBlobKey(blobFile);
			BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
			response.addHeader("Content-Disposition", "attachment; filename=" + blobKey.getKeyString() + ".txt");
			blobstoreService.serve(blobKey, response);
			
		} else {
			sendError(response, 404, "File not found.");
		}
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
	private Drive getDriveService(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Credential credentials = getCredential(req, resp);
		return new Builder(TRANSPORT, JSON_FACTORY, credentials).build();
	}
	
	/**
	 * 
	 * @param docsService
	 * @param documentListEntry
	 * @return
	 * @throws IOException
	 */
	private DocumentListFeed getFeed(DocsService docsService, DocumentListEntry documentListEntry) throws IOException {

		DocumentListFeed documentListFeed = new DocumentListFeed();
		String urlString = "https://docs.google.com/feeds/default/private/full/" + getFolderIdFromHref(documentListEntry.getId()) + "/contents";
		URL url = new URL(urlString);
		while (true) {
			try {
				documentListFeed = docsService.getFeed(url, DocumentListFeed.class);
				break;
			} catch (IllegalArgumentException e) {
				try {
					Thread.sleep(1000);
					System.out.println("sleep");
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			} catch (ServiceException e) {
				System.out.println("service exception");
				e.printStackTrace();
			} catch (Exception e) {
				System.out.println("exception in get feed");
				System.out.println("exception type: " + e.getClass());
				System.out.println(e.getMessage());
			}
		}
		return documentListFeed;
	}
	
	/**
	 * 
	 * @param service
	 * @param fileId
	 * @return A list of child references of fildId
	 */
	private List<ChildReference> getChildren(Drive service, String fileId) {
		List<ChildReference> children = null;
		while (true) {
			try {
				children = service.children().list(fileId).execute().getItems();
				break;
			} catch (IOException ioex) {
				System.out.println(ioex.getMessage());
				ioex.printStackTrace();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					System.out.println(e.getMessage());
					e.printStackTrace();
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}			
		}
		return children;
	}
	
	/**
	 * 
	 * @param href
	 * @return
	 */
	private String getFolderIdFromHref(String href) {
		int startIndex = href.indexOf("folder%3A");
		int offset = "folder%3A".length();
		return href.substring(startIndex + offset);
	}
	
	/**
	 * 
	 * @param href
	 * @return
	 */
	private String getFileIdFromHref(String href) {
		int startIndex = href.indexOf("file%3A");
		int offset = "file%3A".length();
		return href.substring(startIndex + offset);
	}
	
	/**
	 * Download the content of a given file.
	 * 
	 * @param service
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private String downloadFileContent(Drive service, File file) throws IOException {
		GenericUrl u = new GenericUrl(file.getDownloadUrl());
		Get request = service.files().get(file.getId());
		ByteArrayOutputStream bos = new ByteArrayOutputStream(); // new ByteArrayOutputStream();
		MediaHttpDownloader mhd = request.getMediaHttpDownloader();
		//mhd.setChunkSize(10 * 0x100000);
		mhd.download(u, bos);
		return bos.toString();
	}

}
