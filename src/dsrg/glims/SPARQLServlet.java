package dsrg.glims;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryException;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

public class SPARQLServlet extends GLIMSServlet {

	private static final long serialVersionUID = 1L;
	static String defaultNameSpace = "drive#";

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		
		System.out.println("starting sqarql query get");
		
		Drive service = getDriveService(request, response);
		Model metabolites = null;
		String fileId = request.getParameter("fileId");
		fileId = "0B7Jfx3RRVE5YbjJ2dFhxLXBSczg";
		String driveDataURI = "http://drive.google.com/glimsdata";
        
		// get the the collection to export with drive API
		File file = null;
		try {
			file = service.files().get(fileId).execute();
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 401) {
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will
				// recover.
				deleteCredential(request, response);
				sendError(response, 401, "Unauthorized");
				return;
			}
		}
		
		if (file != null) {
			
			System.out.println(file.getTitle());
			
			// set up the hashmap of hashmaps representing metadata categories
			HashMap<String, HashMap<String, String>> fileHash = new HashMap<String, HashMap<String, String>>();
			HashMap<String, ArrayList<String>> masterMetadata = new HashMap<String, ArrayList<String>>();
			
			// get all metadata categories
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
							//System.out.println("metadataKeyName " + metadataKeyName);
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
			response.setContentType(DEFAULT_MIMETYPE);
			PrintWriter outwriter = response.getWriter();
			
	    	
	        // create an empty model
	        Model model = ModelFactory.createDefaultModel();
	        
			for (String fileIdString : fileHash.keySet() ) {
				for (String metadataCategoryString : fileHash.get(fileIdString).keySet()) {
			        Property predicate = ResourceFactory.createProperty("http://"+metadataCategoryString.replaceAll(" ",""));
			        //Property predicate = ResourceFactory.createProperty("", metadataCategoryString);
			        Resource spectrum = model.createResource(fileIdString);
			        spectrum.addProperty(predicate, fileHash.get(fileIdString).get(metadataCategoryString));
				}
			}
			
			System.out.println(model);
			
			outwriter.println("************************************************************");
	        outwriter.println("Listing the model");
	        outwriter.println("************************************************************\n");
	        
			// list the statements in the graph
	        StmtIterator iter = model.listStatements();
	        // print out the predicate, subject and object of each statement
	        while (iter.hasNext()) {
	            Statement stmt      = iter.nextStatement();
	            Resource  subject   = stmt.getSubject();
	            Property  predicate = stmt.getPredicate();
	            RDFNode   object    = stmt.getObject();
	            
	            outwriter.print("SUBJECT: " + subject.toString());
	            outwriter.print("\tPREDICATE: " + predicate.toString() + " ");
	            if (object instanceof Resource) {
	                outwriter.print("\tOBJECT: " + object.toString());
	            } else { // object is a literal
	                outwriter.print("\tOBJECT: \"" + object.toString() + "\"");
	            }
	            outwriter.println(" .");
	        }
	        
	        
	        // now issue some queries
	        //String queryStr = request.getParameter("queryStr");
	        //String queryStr = "select ?x where { ?x <#species> \"Seriatopora hystrix\" } ";
	        String queryStr = "select ?x ?z where { ?x <http://description> ?z } ";
	        outwriter.println("************************************************************");
	        outwriter.println("Applying your query string, sir");
	        outwriter.println("************************************************************\n");
	        Query query = null;
	        query = QueryFactory.create(queryStr);
	        System.out.println(query);
			QueryExecution qexec = QueryExecutionFactory.create(query, model);
			try {
				ResultSet result = qexec.execSelect();
				System.out.println("result: " + result);
				System.out.println("result has next " + result.hasNext());
				while (result.hasNext()) {
					QuerySolution soln = result.nextSolution();
					System.out.println("query solution: " + soln);
					RDFNode filename = soln.get("?file");
					if (filename != null) {
						System.out.println("The file: " + filename + " matches your query!");
					} else
						System.out.println("No rdf found!");
				}
			} finally {
				qexec.close();
			}
			
	        System.out.println("done");
	        
		} else {
			sendError(response, 404, "File not found.");
		}
		
		// to redirect results to a jsp
		// req.setAttribute("name", "edward");
		// RequestDispatcher dispatcher =
		// getServletContext().getRequestDispatcher("/sparql.jsp");
		// dispatcher.forward(req, resp);
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

	private String getFileTitle(List<GetCallback> getCallbacks,
			String metadataKeyId) {
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
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders)
				throws IOException {
			log(e.getErrors().get(0).getMessage());
		}
	};

	class GetCallback extends JsonBatchCallback<File> {
		private File file;

		public File getFile() {
			return file;
		}

		@Override
		public void onFailure(GoogleJsonError e, GoogleHeaders responseHeaders)
				throws IOException {
			log(e.getErrors().get(0).getMessage());
		}

		@Override
		public void onSuccess(File t, GoogleHeaders responseHeaders) {
			this.file = t;
		}
	};
	
	private void printModel(Model model) {
		// list the statements in the graph
		StmtIterator iter = model.listStatements();

		// print out the predicate, subject and object of each statement
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object

			System.out.print("SUBJECT: " + subject.toString());
			System.out.print("\tPREDICATE: " + predicate.toString() + " ");
			if (object instanceof Resource) {
				System.out.print("\tOBJECT: " + object.toString());
			} else {
				// object is a literal
				System.out.print("\tOBJECT: \"" + object.toString() + "\"");
			}
			System.out.println(" .");
		}
	}
	
}
