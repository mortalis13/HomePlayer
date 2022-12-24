package org.mortalis.homeplayer.decoder;

public class DecoderNative {
  
  native static public DecoderResult decodeSamples(String audioPath, int view_with, int view_height);
  
  native static public void stopDecoding();
  
}
