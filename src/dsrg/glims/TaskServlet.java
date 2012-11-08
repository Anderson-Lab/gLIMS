package dsrg.glims;

import java.io.IOException;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

public class TaskServlet extends GLIMSServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		
		String fileId = req.getParameter("file_id");
		
		if (fileId == null) {
			sendError(resp, 400, "The `file_id` URI parameter must be specified.");
			return;
		}
		
	    Queue queue = QueueFactory.getDefaultQueue();
	    String userId = (String)req.getSession().getAttribute("userId");
	    queue.add(withUrl("/write").method(Method.GET).param("file_id", fileId).param("userId", userId));
	}

}
