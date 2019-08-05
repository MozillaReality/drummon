package org.mozilla.mixedreality;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

public class Drummon implements RequestStreamHandler {
  private static String ProxyHost = System.getenv("ProxyHost");
  private static List<String> AllowedOrigins = Arrays.asList(System.getenv("AllowedOrigins").split(" "));
  private static ObjectMapper ObjectMapper = new ObjectMapper();
  private static Base64.Encoder Base64Encoder = Base64.getEncoder();
  private static RequestConfig GetConfig = RequestConfig.custom().setRedirectsEnabled(false).build();

  private HttpClient http = HttpClients.createDefault();

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
    try {
      HashMap args = ObjectMapper.readValue(inputStream, HashMap.class);
      HashMap requestHeaders = (HashMap)args.get("headers");
      String targetUrl = (String)((HashMap)args.get("pathParameters")).get("proxy");
      HashMap queryParameters = (HashMap)args.get("multiValueQueryStringParameters");

      boolean wroteQsArg = false;

      for (Object k : queryParameters.keySet()) {
        targetUrl = targetUrl + (wroteQsArg ? "&" : "?");
        wroteQsArg = true;

        List<String> vals = (List<String>)queryParameters.get(k);

        if (vals.size() == 0) {
          targetUrl = targetUrl + URLEncoder.encode(k.toString());
        } else {
          for (int i = 0; i < vals.size(); i++) {
            targetUrl = targetUrl + URLEncoder.encode(k.toString()) + "=" + URLEncoder.encode(vals.get(i));
          }
        }
      }

      String origin = getHeader(requestHeaders, "Origin");
      OutputStream encodedStream = Base64Encoder.wrap(outputStream);

      HttpGet get = new HttpGet(targetUrl);
      get.setConfig(GetConfig);

      for (Object k : requestHeaders.keySet()) {
        String ks = k.toString().toLowerCase();
        if (ks.equals("origin")) continue;
        if (ks.equals("host")) continue;
        if (ks.startsWith("x-forwarded-")) continue;
        get.addHeader(ks, requestHeaders.get(k).toString());
      }

      HttpResponse response = http.execute(get);
      InputStream bodyStream = response.getEntity().getContent();
      IOUtils.write("{ \"isBase64Encoded\": true, \"statusCode\": ", outputStream, "UTF-8");
      IOUtils.write("" + response.getStatusLine().getStatusCode(), outputStream, "UTF-8");
      IOUtils.write(", \"headers\": { ", outputStream, "UTF-8");
      boolean startedHeaders = false;

      for (Header h : response.getAllHeaders()) {
        String name = h.getName().toLowerCase();
        if (name.startsWith("access-control-")) continue;

        if (startedHeaders) {
          IOUtils.write(",", outputStream, "UTF-8");
        }

        startedHeaders = true;

        IOUtils.write("\"", outputStream, "UTF-8");
        IOUtils.write(jsonEscaped(h.getName()), outputStream, "UTF-8");
        IOUtils.write("\":\"", outputStream, "UTF-8");

        if (name.equals("location")) {
          IOUtils.write(jsonEscaped(ProxyHost), outputStream, "UTF-8");
          IOUtils.write(jsonEscaped("/"), outputStream, "UTF-8");
        }

        IOUtils.write(jsonEscaped(h.getValue()), outputStream, "UTF-8");
        IOUtils.write("\"", outputStream, "UTF-8");
      }
      if (origin != null && (AllowedOrigins.contains("*") || AllowedOrigins.contains(origin))) {
        if (startedHeaders) {
          IOUtils.write(",", outputStream, "UTF-8");
        }

        startedHeaders = true;

        IOUtils.write("\"Access-Control-Allow-Origin\": \"", outputStream, "UTF-8");
        IOUtils.write(jsonEscaped(origin), outputStream, "UTF-8");
        IOUtils.write("\", \"Access-Control-Allow-Methods\": \"GET, HEAD, OPTIONS\",", outputStream, "UTF-8");
        IOUtils.write("\"Access-Control-Allow-Headers\": \"Range\",", outputStream, "UTF-8");
        IOUtils.write("\"Access-Control-Expose-Headers\": \"Accept-Ranges, Content-Encoding, Content-Length, Content-Range\"", outputStream, "UTF-8");
      }

      if (startedHeaders) {
        IOUtils.write(",", outputStream, "UTF-8");
      }

      startedHeaders = true;
      IOUtils.write("\"Vary\": \"Origin\",", outputStream, "UTF-8");
      IOUtils.write("\"X-Content-Type-Options\": \"nosniff\"", outputStream, "UTF-8");
      IOUtils.write("}, \"body\": \"", outputStream, "UTF-8");

      IOUtils.copy(bodyStream, encodedStream, 1024 * 1024);
      encodedStream.flush();
      IOUtils.write("\"}", outputStream, "UTF-8");
    } catch (IOException e) { 
      e.printStackTrace();
      System.out.println(e);
    }
  }

  private String jsonEscaped(String s) {
    StringBuffer sb = new StringBuffer();
    final int len = s.length();

    for(int i=0;i<len;i++){
      char ch=s.charAt(i);
      switch(ch){
      case '"':
        sb.append("\\\"");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      case '\b':
        sb.append("\\b");
        break;
      case '\f':
        sb.append("\\f");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case '\r':
        sb.append("\\r");
        break;
      case '\t':
        sb.append("\\t");
        break;
      case '/':
        sb.append("\\/");
        break;
      default:
                //Reference: http://www.unicode.org/versions/Unicode5.1.0/
        if((ch>='\u0000' && ch<='\u001F') || (ch>='\u007F' && ch<='\u009F') || (ch>='\u2000' && ch<='\u20FF')){
          String ss=Integer.toHexString(ch);
          sb.append("\\u");
          for(int k=0;k<4-ss.length();k++){
            sb.append('0');
          }
          sb.append(ss.toUpperCase());
        }
        else{
          sb.append(ch);
        }
      }
    }

    return sb.toString();
  }

  private String getHeader(HashMap headers, String header) {
    for (Object key : headers.keySet()) {
      if (key.toString().equalsIgnoreCase(header)) {
        return headers.get(key).toString();
      }
    }

    return null;
  }

  private void removeHeader(HashMap headers, String header) {
    Object foundHeader = null;

    for (Object key : headers.keySet()) {
      if (key.toString().equalsIgnoreCase(header)) {
        foundHeader = key;
        break;
      }
    }

    if (foundHeader != null) {
      headers.remove(foundHeader);
    }
  }
}
