package org.mozilla.mixedreality;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static JsonFactory jsonFactory = new JsonFactory();
    
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
    
    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
      ObjectMapper objectMapper = new ObjectMapper();
      try {
        HashMap args = objectMapper.readValue(inputStream, HashMap.class);
        HashMap requestHeaders = (HashMap)args.get("headers");
        String targetUrl = (String)((HashMap)args.get("pathParameters")).get("url");
        System.out.println("Target URL: " + targetUrl);
        /*String origin = getHeader(requestHeaders, "Origin");
        OutputStream encodedStream = Base64.getEncoder().wrap(outputStream);
        HttpClient http = HttpClients.createDefault();
        RequestConfig reqConfig = RequestConfig.custom().setRedirectsEnabled(false).build();
        
        HttpGet get = new HttpGet(targetUrl);
        get.setConfig(reqConfig);
        
        for (Object k : requestHeaders.keySet()) {
        	if (k.toString().equalsIgnoreCase("origin")) continue;
        	get.addHeader(k.toString(), requestHeaders.get(k).toString());
        }
        
        HttpResponse response = http.execute(get);
        InputStream bodyStream = response.getEntity().getContent();
        System.out.println(response.getStatusLine());
        
        for (Header h : response.getAllHeaders()) {
        	System.out.println(h);
        }
        
        encodedStream.close();*/
    } catch (IOException e) {
      System.out.println(e);
    }
    }
}
