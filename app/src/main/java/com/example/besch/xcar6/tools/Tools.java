package com.example.besch.xcar6.tools;

import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

public class Tools {
   TextView myLog;

   public Tools(TextView myLog) {
      this.myLog = myLog;

   }
   public static String b2s(byte[] buffer ) {
      String s = "L: " + buffer.length + ", S: ";
      for (int i=0; i<buffer.length; i++) {
         if (i>0) s += ";";
         s += buffer[i];
      }
      return s;
   }

   public void logge(String logTAG, String s, Exception e) {
      logge(logTAG, s);
      e.printStackTrace();
      StackTraceElement[] trs = e.getStackTrace();
      for (StackTraceElement tr : trs) {
         logge(logTAG, tr.toString());
      }
   }

   public void logge(String logTAG, String s) {
      Log.d(logTAG, s);
      this.myLog.append(s + "\n");
   }



}