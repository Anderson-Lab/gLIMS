package dslab.glims;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;

import dslab.glims.CredentialMediator.NoRefreshTokenException;

public class TokenServlet extends GLIMSServlet {


	private static final long serialVersionUID = 1L;
	
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		resp.setContentType("text/plain");
		PrintWriter writer = resp.getWriter();
		
		Cookie jSessionId = null;
		for (Cookie cookie : req.getCookies()) {
			if (cookie.getName().equals("JSESSIONID"))
				jSessionId = cookie;
		}
		
		if (jSessionId == null)
			writer.print("No JSESSIONID found.");
		else {
			CredentialMediator mediator = getCredentialMediator(req, resp);
			Credential credential = null;
			try {
				credential = mediator.getActiveCredential();
			} catch (NoRefreshTokenException e) {
				e.printStackTrace();
			}
			String accessToken = credential.getAccessToken();
			String refreshToken = credential.getRefreshToken();
			writer.println("{ \"AccessToken\": " + "\"" + accessToken + "\"" + ", \"RefreshToken\": "  
						+ "\"" + refreshToken  + "\"" + " }");
		}
	}
}
