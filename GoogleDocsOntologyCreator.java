import com.google.gdata.data.Link;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.acl.AclEntry;
import com.google.gdata.data.acl.AclFeed;
import com.google.gdata.data.acl.AclRole;
import com.google.gdata.data.acl.AclScope;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.docs.RevisionEntry;
import com.google.gdata.data.docs.RevisionFeed;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GoogleDocsOntologyCreator {
	
	private static DocumentList documentList;
	private static final String APPLICATION_NAME = "JavaGDataClientSampleAppV3.0";
	public static final String DEFAULT_HOST = "docs.google.com";
	
	/**
	 * Constructor
	 * 
	 * @param outputStream
	 *            Stream to print output to.
	 * @throws DocumentListException
	 */
	public GoogleDocsOntologyCreator(String appName, String host) throws DocumentListException {
		documentList = new DocumentList(appName, host);
	}
	
	/**
	 * Authenticates the client using ClientLogin
	 * 
	 * @param username
	 *            User's email address
	 * @param password
	 *            User's password
	 * @throws DocumentListException
	 * @throws AuthenticationException
	 */
	public void login(String username, String password)
			throws AuthenticationException, DocumentListException {
		documentList.login(username, password);
	}

	/**
	 * Prints out the specified document entry.
	 * 
	 * @param doc
	 *            the document entry to print.
	 */
	public void printDocumentEntry(DocumentListEntry doc) {
		StringBuffer output = new StringBuffer();

		output.append(" -- " + doc.getTitle().getPlainText() + " ");
		if (!doc.getParentLinks().isEmpty()) {
			for (Link link : doc.getParentLinks()) {
				output.append("[" + link.getTitle() + "] ");
			}
		}
		output.append(doc.getResourceId());

		System.out.println(output);
	}

	/**
	 * Execute the "list" command.
	 * 
	 * @param args
	 *            arguments for the "list" command. args[0] = "list" args[1] =
	 *            category ("all", "folders", "documents", "spreadsheets",
	 *            "pdfs", "presentations", "starred", "trashed") args[2] =
	 *            folderId (required if args[1] is "folder")
	 * 
	 * @throws IOException
	 *             when an error occurs in communication with the Doclist
	 *             service.
	 * @throws MalformedURLException
	 *             when an malformed URL is used.
	 * @throws ServiceException
	 *             when the request causes an error in the Doclist service.
	 * @throws DocumentListException
	 */
	private void executeList(String[] args) throws IOException,
			ServiceException, DocumentListException {
		DocumentListFeed feed = null;
		String msg = "";

		switch (args.length) {
		case 1:
			msg = "List of docs: ";
			feed = documentList.getDocsListFeed("all");
			break;
		case 2:
			msg = "List of all " + args[1] + ": ";
			feed = documentList.getDocsListFeed(args[1]);
			break;
		case 3:
			if (args[1].equals("folder")) {
				msg = "Contents of folder_id '" + args[2] + "': ";
				feed = documentList.getFolderDocsListFeed(args[2]);
			}
			break;
		}

		if (feed != null) {
			System.out.println(msg);
			for (DocumentListEntry entry : feed.getEntries()) {
				printDocumentEntry(entry);
			}
		} else {
			System.out.println("Error in executeList.");
		}
	}

	public static void main(String[] args) throws DocumentListException,
			IOException, ServiceException, InterruptedException {

//		try {
//			FileInputStream fstream = new FileInputStream(
//					"collection_All_ANIT_Raw_Merged_10658.txt");
//
//			// Get the object of DataInputStream
//			DataInputStream in = new DataInputStream(fstream);
//			BufferedReader br = new BufferedReader(new InputStreamReader(in));
//			String strLine;
//
//			// Read File Line By Line
//			// while ((strLine = br.readLine()) != null) {
//			// // Print the content on the console
//			// System.out.println(strLine);
//			// }
//
//			String collectionId = br.readLine();
//			String type = br.readLine();
//
//			System.out.println(collectionId);
//			System.out.println(type);
//
//			// Close the input stream
//			in.close();
//
//		} catch (Exception e) {
//			System.err.println("Error: " + e.getMessage());
//		}
//		
		String host = DocumentList.DEFAULT_HOST;
		String appName = APPLICATION_NAME;
		GoogleDocsOntologyCreator gdoc = new GoogleDocsOntologyCreator(APPLICATION_NAME, host);
		documentList = new DocumentList(appName, host);
		gdoc.login("birgmetabol", "port1031");
		
//		String[] array = new String[0];
//		
//		gdoc.executeList(array);
		
		String filepath = "C:\\Users\\pharre\\workspace\\GoogleDocuments";
		String title = "myfile.txt";
		documentList.uploadFile(filepath, title);
	}
}
