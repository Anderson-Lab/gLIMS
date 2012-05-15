import com.google.gdata.client.spreadsheet.*;
import com.google.gdata.data.Link;
import com.google.gdata.data.extensions.EventFeed;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.*;
import com.google.gdata.client.*;
import com.google.gdata.client.calendar.*;
import com.google.gdata.data.*;
import com.google.gdata.data.extensions.*;

import java.net.URL;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class MySpreadsheetIntegration {
	public static void main(String[] args) throws AuthenticationException,
			MalformedURLException, IOException, ServiceException {
		
		String USERNAME;
		String PASSWORD;
		String docUrl;

		USERNAME = "birgmetabol@gmail.com";
		PASSWORD = "port1031";
		docUrl = "https://docs.google.com/spreadsheet/ccc?key=0AkWq5hN7hQ-SdDdvYTVqQnYtZ2V1YWpKM25VMkYtZnc&pli=1#gid=0";

		SpreadsheetService service = new SpreadsheetService(
				"MySpreadsheetIntegration-v1");
		service.setUserCredentials(USERNAME, PASSWORD);
		
		int keyIndex = docUrl.indexOf("key") + 4;
		int endIndex = docUrl.indexOf("&", keyIndex);
		String key = docUrl.substring(keyIndex, endIndex);
		
		// Define the URL to request. This should never change.
		URL SPREADSHEET_FEED_URL = new URL(
				"https://spreadsheets.google.com/feeds/spreadsheets/private/full/" + key);

		SpreadsheetEntry entry = service.getEntry(SPREADSHEET_FEED_URL,
				SpreadsheetEntry.class);

		System.out.println(entry.getTitle().getPlainText());

		// Get the first worksheet of the entry.
		WorksheetFeed worksheetFeed = service.getFeed(
				entry.getWorksheetFeedUrl(), WorksheetFeed.class);
		List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
		WorksheetEntry worksheet = worksheets.get(0);

		// Fetch the list feed of the worksheet.
		URL listFeedUrl = worksheet.getListFeedUrl();
		ListFeed listFeed = service.getFeed(listFeedUrl, ListFeed.class);
		
		// Iterate through each row, printing its cell values.
		for (ListEntry row : listFeed.getEntries()) { // Print the first column's cell value
				 System.out.print(row.getTitle().getPlainText() + "\t"); 
				 // Iterateover the remaining columns, and print each cell value 
				 for (String tag : row.getCustomElements().getTags()) {
					 System.out.print(row.getCustomElements().getValue(tag) + "\t");
			     }
				 	 System.out.println();
		}
	}
}