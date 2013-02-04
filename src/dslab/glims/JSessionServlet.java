package dslab.glims;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JSessionServlet extends GLIMSServlet {


	private static final long serialVersionUID = 1L;
	
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		PrintWriter writer = resp.getWriter();
		for (Cookie cookie : req.getCookies()) {
			if (cookie.getName().equals("JSESSIONID"))
				writer.println(cookie.getValue());
		}
				
	}
}
