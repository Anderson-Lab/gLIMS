package com.google.drive.samples.dredit;

import com.google.drive.samples.dredit.model.State;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to check that the current user is authorized and to serve the start
 * page for DrEdit.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class StartPageServlet extends DrEditServlet {

	/**
	 * Ensure that the user is authorized, and setup the required values for
	 * index.jsp.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

//		// Deserialize the state in order to specify some values to the DrEdit JavaScript client below.
//		Collection<String> ids = new ArrayList<String>();
//
//		// Assume an empty ID in the list if no IDs were set.
//		ids.add("");
//		if (req.getParameter("state") != null) {
//			State state = new State(req.getParameter("state"));
//			if (state.ids != null && state.ids.size() > 0) {
//				ids = state.ids;
//			}
//		}
//
//		req.setAttribute("ids", new Gson().toJson(ids).toString());
//		req.setAttribute("client_id", new Gson().toJson(getClientId(req, resp)));
		if (getCredential(req,resp) != null)
			req.getRequestDispatcher("/glims.jsp").forward(req, resp);
	}
}
