package com.example.besch.xcar6.mission;

import java.util.ArrayList;

public class Mission {

   private ArrayList<MissionObject> mission;
   private int i=0;

   public void setMission(ArrayList<MissionObject> mission) {
      this.mission = mission;
   }

   public MissionObject getNextStep() {
      if (i < mission.size()) {
         return mission.get(i++);
      } else {
         return null;
      }
   }

   public int getLine() {
      return i;
   }
}