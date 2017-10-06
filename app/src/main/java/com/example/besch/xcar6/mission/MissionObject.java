package com.example.besch.xcar6.mission;

public class MissionObject {

   private String cmd = "";
   private String [] paramKeys = null;
   private int  [] paramVals = null;

   public MissionObject(String cmd, String[] paramKeys, int[] paramVals) {
      this.cmd = cmd;
      this.paramKeys = paramKeys;
      this.paramVals = paramVals;
   }

   public String getCmd() {
      return cmd;
   }
   public void setCmd(String cmd) {
      this.cmd = cmd;
   }
   public String[] getParamKeys() {
      return paramKeys;
   }
   public void setParamKeys(String[] paramKeys) {
      this.paramKeys = paramKeys;
   }
   public int[] getParamVals() {
      return paramVals;
   }
   public void setParamVals(int[] paramVals) {
      this.paramVals = paramVals;
   }

   @Override
   public String toString() {
      String s = "CMD: " + cmd + ", P: ";
      for (int i=0; i<paramKeys.length; i++) {
         s += paramKeys[i] + "=" + paramVals[i] + "; ";
      }
      return s;
   }

}

