package com.example.besch.xcar6.mission;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;

import android.widget.TextView;

public class MissionUtils {

   private int lineNr = 0;
   private ArrayList<MissionStep> myMission = null;
   TextView log;

   public ArrayList<MissionStep> getMission(String fileName, TextView aLog) {
      this.log = aLog;
      try {
         parse (new FileReader(fileName));
      } catch (Exception e) {
         log.append("Error: " + e.getMessage() + "\n");
      }
      log.append("### getMission Zeilen: "+ myMission.size() + "\n");
      return myMission;
   }

   public void parse(Reader reader) {
      try {
         myMission = new ArrayList<MissionStep>();
         BufferedReader in = new BufferedReader(reader);
         String line = "";
         while ( (line = in.readLine()) != null ) {
            ++lineNr;
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
               if (line.contains("#"))
                  line = line.substring(0, line.indexOf("#")-1).trim();
               MissionStep missionObject = parseLine(line);
               myMission.add(missionObject);
               log.append(":::parse: " + missionObject.toString());
            }
         }
         in.close();
      } catch (Exception e) {
         log.append("Error: " + e.getMessage() + " in Zeile: " + lineNr + "\n" );
      }
   }

   private MissionStep parseLine (String line) {
      MissionStep missionObject = null;
      try {
         String cmd = "";
         String [] args = null;
         String [] paramKeys = null;
         int    [] paramVals = null;

         args = line.toUpperCase().split(",");
         cmd = args[0].trim();

         paramKeys = new String[args.length-1];
         paramVals = new int[args.length-1];
         for (int i=1; i<args.length; i++) {
            String arg = args[i].replaceAll(" ", "");
            if (arg.length() > 0) {
               paramKeys[i-1] = arg.substring(0, 1);
               paramVals[i-1] = Integer.parseInt(arg.substring(1));
            }
         }

         missionObject = new MissionStep(cmd, paramKeys, paramVals);
      } catch (Exception e) {
         log.append("Error: " + e.getMessage() + " in Zeile: " + lineNr + "\n" );
      }
      return missionObject;
   }

}
