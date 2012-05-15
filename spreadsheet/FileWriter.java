package spreadsheet;

import java.io.*;

public class FileWriter {

	String fileName;

	/** Constructor. */
	FileWriter(String aFileName) {
		fileName = aFileName;
	}

	/** Write fixed content to the given file. */
	void write() throws IOException {
		System.out.println("Writing to file named " + fileName);
		Writer out = new OutputStreamWriter(new FileOutputStream(fileName));
		try {
			int i;
			for (i=0; i<100; i++) {
				out.write(i + "\t" + "data\n");
			}
		} finally {
			out.close();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		FileWriter test = new FileWriter("myfile.txt");
		test.write();

	}

}
