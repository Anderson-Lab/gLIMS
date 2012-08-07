
package edward.rest;

import java.io.InputStream;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

public class QuickStart {

    public static void main(String[] args) throws Exception {
    	
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet("http://localhost:8888/file?file_id=0B_4L9UB-A6C3LXZ3OU5NU2x1LUk");
        
        BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", "1n0ggyteghl03");
    	CookieStore cookieStore = new BasicCookieStore();
    	cookieStore.addCookie(cookie);
    	httpclient.setCookieStore(cookieStore);
        
    	
        HttpResponse response = httpclient.execute(httpGet);
        
        try {
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();
            String content = new Scanner(inputStream).useDelimiter("\\A").next();
            System.out.println(content);
        } catch (Exception e) {
        	System.out.println(e.getMessage());
        }
        finally {
            httpGet.releaseConnection();
        }
    }

}
