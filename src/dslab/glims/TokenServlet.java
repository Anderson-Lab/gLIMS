package dslab.glims;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;

public class TokenServlet extends GLIMSServlet {

	private static final long serialVersionUID = 1L;

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		if (req.getCookies() != null) {
			for (Cookie cookie : req.getCookies()) {
				System.out.println("cookie " + cookie);
				System.out.println(cookie.getName());
				System.out.println(cookie.getValue());
				System.out.println();
			}			
		}
		
		Credential credential = null;
		try {
			credential = getCredential(req, resp);
			if (credential == null) {
				Cookie oauthRedirectUrl = new Cookie("oauthRedirectUrl", "/token");
				resp.addCookie(oauthRedirectUrl);
				resp.sendRedirect("/");
				return;				
			}
			resp.setContentType("text/plain");
			PrintWriter writer = resp.getWriter();
			String accessToken = credential.getAccessToken();
			String refreshToken = credential.getRefreshToken();
			writer.println("{ \"AccessToken\": " + "\"" + accessToken + "\""
					+ ", \"RefreshToken\": " + "\"" + refreshToken + "\""
					+ " }");
		} catch (IOException ioe) {
			System.out.println(ioe);
		} catch (Exception e) {
			System.out.println(e);
		}
	}
}
