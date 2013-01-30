package edward.rest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class MakeQueries {
	
	public static void main(String[] args) throws IOException {
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader("foaf.rdf"));
			Model model = ModelFactory.createDefaultModel();
//			String line;
//			while((line = bufferedReader.readLine()) != null) {
//				String[] elements = line.split("\\s");
//				for (String el : elements) {
//					System.out.print(el + "\t");
//				}
//				System.out.println();
//			}
//			bufferedReader.close();
			
			try {
				Query query = QueryFactory.create("select ?x where { ?x hasName \"edward\" } ");				
			} catch (Exception e) {
				System.out.println(e);
			}
			
			
			
//			StmtIterator iter = model.listStatements();
//			while (iter.hasNext()) {
//	            Statement stmt      = iter.nextStatement();         // get next statement
//	            Resource  subject   = stmt.getSubject();   // get the subject
//	            Property  predicate = stmt.getPredicate(); // get the predicate
//	            RDFNode   object    = stmt.getObject();    // get the object
//	            
//	            System.out.print("SUBJECT: " + subject.toString());
//	            System.out.print("\tPREDICATE: " + predicate.toString() + " ");
//	            if (object instanceof Resource) {
//	                System.out.print("\tOBJECT: " + object.toString());
//	            } else {
//	                // object is a literal
//	                System.out.print("\tOBJECT: \"" + object.toString() + "\"");
//	            }
//	            System.out.println(" .");
//	        }
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
