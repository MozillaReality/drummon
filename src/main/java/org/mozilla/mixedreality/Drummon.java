package org.mozilla.mixedreality;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
      IOUtils.write(response.getStatusLine().toString(), encodedStream, "UTF-8");
      IOUtils.write("\n", encodedStream);

      for (Header h : response.getAllHeaders()) {
        String name = h.getName().toLowerCase();
        if (name.startsWith("access-control-")) continue;

        if (name.equals("location")) {
          IOUtils.write("Location: ", encodedStream, "UTF-8");
          IOUtils.write(ProxyHost, encodedStream, "UTF-8");
          IOUtils.write("/", encodedStream, "UTF-8");
          IOUtils.write(h.getValue(), encodedStream, "UTF-8");
        } else {
          IOUtils.write(h.toString(), encodedStream, "UTF-8");
        }

        IOUtils.write("\n", encodedStream);
      }

      if (AllowedOrigins.contains("*") || (origin != null && AllowedOrigins.contains(origin))) {
        IOUtils.write("Access-Control-Allow-Origin: ", encodedStream, "UTF-8");
        IOUtils.write(origin, encodedStream, "UTF-8");
        IOUtils.write("\n", encodedStream, "UTF-8");
        IOUtils.write("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\n", encodedStream, "UTF-8");
        IOUtils.write("Access-Control-Allow-Headers: Range\n", encodedStream, "UTF-8");
        IOUtils.write("Access-Control-Expose-Headers: Accept-Ranges, Content-Encoding, Content-Length, Content-Range\n", encodedStream, "UTF-8");
      }

      IOUtils.write("Vary: Origin\n", encodedStream, "UTF-8");
      IOUtils.write("X-Content-Type-Options: nosniff\n", encodedStream, "UTF-8");

      IOUtils.copy(bodyStream, encodedStream, 1024 * 1024);

      encodedStream.close();
    } catch (IOException e) { }
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
