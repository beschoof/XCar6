package com.example.mqttlib;

import android.util.Log;
import android.widget.TextView;

public class Tools {

   public Tools() {
   }

   public static String b2s(byte[] buffer ) {
      String s = "L: " + buffer.length + ", S: ";
      for (int i=0; i<buffer.length; i++) {
         if (i>0) s += ";";
         s += buffer[i];
      }
      return s;
   }


}