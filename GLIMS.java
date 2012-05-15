import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import com.google.gdata.util.ServiceException;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GLIMS {

	private static final String APPLICATION_NAME = "JavaGDataClientSampleAppV3.0";
	private PrintStream out;
	private DocumentList documentList;

	/**
	 * Constructor
	 * 
	 * @param outputStream
	 *            Stream to print output to.
	 * @throws DocumentListException
	 */
	public GLIMS(PrintStream outputStream, String appName, String host)
			throws DocumentListException {
		out = outputStream;
		documentList = new DocumentList(appName, host);
	}

	public void upload(String filename) throws MalformedURLException,
			IOException, ServiceException, DocumentListException {
		
		ArrayList<String> categories = getCategories(filename);
		String glimsEntry = returnDocumentEntry(documentList.createNew("gLIMS", "folder"));
		System.out.println(glimsEntry);

		String type = "folder";
		for (String title : categories) {
			//documentList.createNew(title, type); // there's another version of
													// this where can give url
		}

		// String[] createArray = new String[3];
		// createArray[0] = "create";
		// createArray[1] = "folder";
		// createArray[2] = collectionId;
		// doc.executeCreate(createArray);

		// doc.executeList(anArray); // an array length 1 so it lists everything
		// System.out.println("\n");

		// createArray[0] = "create";
		// createArray[1] = "folder";
		// createArray[2] = type;
		// doc.executeCreate(createArray);

		// String[] moveArray = new String[2];
		// moveArray[0] = "0B0Wq5hN7hQ-SRlMtRFlzYXNHZ2c";
		// moveArray[1] = "0B0Wq5hN7hQ-SQVBQUC1XZVZpY2s";
		// doc.executeMove(moveArray);

		// String label;
		// String[] commandArray = new String[3];
		// commandArray[0] = "create";
		// commandArray[1] = "folder";
		//
		// commandArray[2] = "gLIMS";
		// String glims = doc.executeCreate(commandArray);
		// int startIndex = glims.indexOf("folder:") + 7;
		// glims = glims.substring(startIndex);
		// System.out.println(glims);

		// for (String s : categories) {
		// int endIndex = s.indexOf("\t");
		// label = s.substring(0, endIndex);
		// System.out.println(label);
		// commandArray[2] = label;
		// catIds.add(doc.executeCreate(commandArray));
		// }

		// int start;
		// commandArray[0] = "move";
		// commandArray[2] = glims;
		// for (String s : catIds) {
		// // move resource folder
		// start = s.indexOf("folder:") + 7;
		// commandArray[1] = s.substring(start);
		// doc.executeMove(commandArray);
		// doc.removeFromRoot(commandArray[1]);
		// }

		// upload filepath filename
		// commandArray[0] = "";
		// commandArray[1] =
		// "C:\\Users\\pharre\\workspace\\GoogleDocuments\\myfile.txt";
		// commandArray[2] = "myfile.txt";
		// doc.executeUpload(commandArray);
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
	private void login(String username, String password)
			throws AuthenticationException, DocumentListException {
		documentList.login(username, password);
	}

	/**
	 * getCategories
	 * 
	 * @param filename
	 * @return an ArrayList of the folders
	 */
	private ArrayList<String> getCategories(String filename) {

		ArrayList<String> categories = new ArrayList<String>();

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null
					&& !strLine.substring(0, 1).equals("X")) {
				categories.add(strLine.substring(0, strLine.indexOf("\t")));
			}
			in.close();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}

		return categories;
	}

	/**
	 * Prints out the specified document entry.
	 * 
	 * @param doc
	 *            the document entry to print.
	 */
	public String returnDocumentEntry(DocumentListEntry doc) {
		StringBuffer output = new StringBuffer();

		output.append(doc.getTitle().getPlainText() + "\t");
		if (!doc.getParentLinks().isEmpty()) {
			for (Link link : doc.getParentLinks()) {
				output.append("[" + link.getTitle() + "] ");
			}
		}
		output.append(doc.getResourceId());

		return output.toString();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws DocumentListException,
			IOException, ServiceException, InterruptedException {

		String user = "birgmetabol";
		String password = "port1031";
		String host = DocumentList.DEFAULT_HOST;
		String filename = "C:\\Users\\pharre\\workspace\\GoogleDocuments\\collection_All_ANIT_Raw_Merged_10658.txt";

		// ArrayList<String> categories = new ArrayList<String>();
		// ArrayList<String> catIds = new ArrayList<String>();

		GLIMS glims = new GLIMS(System.out, APPLICATION_NAME, host);
		glims.login(user, password);
		glims.upload(filename);

	}
}
