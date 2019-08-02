package org.mozilla.mixedreality;

import java.io.InputStream;
import java.io.OutputStream;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.Context; 

public class LambdaCorsProxy implements RequestStreamHandler {
  public void handler(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    int letter;

    while((letter = inputStream.read()) != -1)
    {
      outputStream.write(Character.toUpperCase(letter));
    }
  }
}
