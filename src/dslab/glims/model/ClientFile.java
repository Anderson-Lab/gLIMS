package dslab.glims.model;

import java.io.Reader;

import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * An object representing a File and its content, for use while interacting with
 * a DrEdit JavaScript client. Can be serialized and deserialized using Gson.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class ClientFile {

	/**
	 * ID of file.
	 */
	public String resource_id;

	/**
	 * Title of file.
	 */
	public String title;

	/**
	 * Description of file.
	 */
	public String description;

	/**
	 * MIME type of file.
	 */
	public String mimeType;

	/**
	 * Content body of file.
	 */
	public String content;

	/**
	 * Parent folders of file.
	 */
	public String parents;

	/**
	 * Empty constructor required by Gson.
	 */
	public ClientFile() {
	}

	/**
	 * Creates a new ClientFile based on the given File and content.
	 */
	public ClientFile(File file, String content) {
		this.resource_id = file.getId();
		this.title = file.getTitle();
		this.description = file.getDescription();
		this.mimeType = file.getMimeType();
		this.content = content;
	}

	/**
	 * Creates a new ClientFile based on the given File and content and the parents.
	 */
	public ClientFile(File file, String content, String parents) {
		this.resource_id = file.getId();
		this.title = file.getTitle();
		this.description = file.getDescription();
		this.mimeType = file.getMimeType();
		this.content = content;
		this.parents = parents;
	}

	/**
	 * Construct a new ClientFile from its JSON representation.
	 * 
	 * @param in
	 *            Reader of JSON string to parse.
	 */
	public ClientFile(Reader in) {
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		ClientFile other = gson.fromJson(in, ClientFile.class);
		this.resource_id = other.resource_id;
		this.title = other.title;
		this.description = other.description;
		this.mimeType = other.mimeType;
		this.content = other.content;
		this.parents = other.parents;
	}

	/**
	 * @return JSON representation of this ClientFile.
	 */
	public String toJson() {
		String s = new Gson().toJson(this).toString();
		return s;
	}

	/**
	 * @return Representation of this ClientFile as a Drive file.
	 */
	public File toFile() {
		File file = new File();
		file.setId(this.resource_id);
		file.setTitle(this.title);
		file.setDescription(this.description);
		file.setMimeType(this.mimeType);
		return file;
	}
}
