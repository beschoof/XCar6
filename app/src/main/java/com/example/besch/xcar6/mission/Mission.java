package com.example.besch.xcar6.mission;

import java.util.ArrayList;

public class Mission {

   private ArrayList<MissionStep> missionSteps;
   private int i=0;

   public void setMissionSteps(ArrayList<MissionStep> missionSteps) {
      this.missionSteps = missionSteps;
   }

   public MissionStep getNextStep() {
      if (i < missionSteps.size()) {
         return missionSteps.get(i++);
      } else {
         return null;
      }
   }

   public int getLine() {
      return i;
   }
}