package com.example.besch.xcar6;
// XCar1, Stand: 29.8.17

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.besch.xcar6.mission.Mission;
import com.example.besch.xcar6.mission.MissionObject;
import com.example.besch.xcar6.mission.MissionUtils;
import com.example.besch.xcar6.tools.Tools;
import com.example.mqttlib.UsbConnection12;
import com.example.mqttlib.WroxAccessory;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.IOException;
import java.util.Date;


public class MainActivity extends Activity {

   private WroxAccessory mAccessory;
   private UsbManager mUsbManager;
   private UsbConnection12 connection;
   String subscription;
   private int subscriptionId = 0;
   private byte plCmdId = 0;
   TextView myLog;
   EditText missionFileName;
   Mission myMission = null;
   final String logTAG = "MainActivity";
   final String MQTT_TOPIC = "AN";
   Tools tools;
   String sFileName1 = "/mnt/sdcard/misc/X1_Seben.txt";  // Handy
//   String sFileName1 = "/mnt/sdcard2/misc/X1_Crawl.txt";   // Tablet
   final static int CMD_INIT = 1;
   final static int CMD_MOVE = 2;
   final static int CMD_WAIT = 3;
   final static int CMD_STOP = 9;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      try {
         super.onCreate(savedInstanceState);
         setContentView(R.layout.activity_main);
         myLog = (TextView) findViewById(R.id.logText1);
         tools = new Tools(myLog);
         myLog.setMovementMethod(new ScrollingMovementMethod());
         tools.logge(logTAG, " ### XCar1 onCreate beginn...");  // erst muss my
         missionFileName = (EditText) findViewById(R.id.MissionFileName);
         missionFileName.setText(sFileName1);
         mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
         connection = new UsbConnection12(this, mUsbManager);
         mAccessory = new WroxAccessory(this, myLog);
         plCmdId = 0;
         tools.logge(logTAG, " ### XCar1 ...onCreate ende");
      } catch (Exception e) {
         tools.logge(logTAG, "Error bei onCreate", e);
      }
   }

   @Override
   protected void onResume() {
      super.onResume();
      tools.logge(logTAG, "...onResume...");
      /*  */
      try {
         tools.logge(logTAG, "cmdConnect beginn...  ->  mAccessory.connect()...");
         mAccessory.connect(connection);
         tools.logge(logTAG, "cmdConnect ...  ->  mAccessory.subscribe()...");
         subscription = mAccessory.subscribe(receiver, MQTT_TOPIC, subscriptionId++);
         tools.logge(logTAG, "cmdConnect ende, my subscription is: " + subscription);
      } catch (IOException e) {
         tools.logge(logTAG, "Error: " + e.getMessage());
      }
      /*  */
   }


   public void cmdConnect(View v) {
         /*
      try {
         tools.logge(logTAG, "cmdConnect beginn...  ->  mAccessory.connect()...");
         mAccessory.connect(connection);
         tools.logge(logTAG, "cmdConnect ...  ->  mAccessory.subscribe()...");
         subscription = mAccessory.subscribe(receiver, MQTT_TOPIC, subscriptionId++);
         tools.logge(logTAG, "cmdConnect ende, my subscription is: " + subscription);
      } catch (IOException e) {
         tools.logge(logTAG, "### Error bei cmdConnect", e);
      }
      /*  */

   }

   public void cmdForward(View v) {
      tools.logge(logTAG, "cmdForward beginn...");
      try {
         tools.logge(logTAG, "cmdForward " + sFileName1);
         String fileName = missionFileName.getText().toString();
         tools.logge(logTAG, "cmdForward / Filename = " + fileName);
         MissionUtils missionUtils = new MissionUtils();
         myMission = new Mission();
         myMission.setMission(missionUtils.getMission(fileName, myLog));
         runMissionStep();
         tools.logge(logTAG, "cmdForward ende");
      } catch (Exception e) {
         tools.logge(logTAG, "Error bei cmdForward", e);
      }
   }

   public void cmdStop(View v) {
      tools.logge(logTAG, "###    S T O P    ###");
      doStop();
   }

   void runMissionStep() {
      MissionObject m = null;
      byte[] buffer = new byte[5]; // ID, cmd, T, R, V

      try {
         if (++plCmdId > 100) plCmdId = 1;
         buffer[0] = plCmdId;

         m = myMission.getNextStep();

         if (m == null) {
            tools.logge(logTAG, "runMissionStep, Step = null");
            doStop();
            return;
         }

         tools.logge(logTAG, "runMissionStep, m.step: " + myMission.getLine() + ": " + m.toString());

         String cmd = m.getCmd();

         byte plCmd = 0; // drive (ggf. auch mit v=0)
         byte plT = 0;
         byte plR = 0;
         byte plV = 0;

         for (int i = 0; i < m.getParamKeys().length; i++) {
            String pKey = m.getParamKeys()[i];
            int iVal = (int) m.getParamVals()[i];
            if (pKey.equals("T")) { // bei >= 100 -> millis, sonst sek.
               plT = (byte) iVal;
            } else if (pKey.equals("R")) {
               plR = (byte) (iVal + 8); // -7..7 -> 1..15  , keine 0
            } else if (pKey.equals("V")) {
               plV = (byte) (iVal + 8); // -7..7 -> 1..15  , keine 0
            }
         }

         if (cmd.equals("INIT")) { // == Init für car type
            plCmd = CMD_INIT;
            tools.logge(logTAG, "init: " + plT);
         } else if (cmd.equals("WAIT")) { // == MOVE mit v=0
            plCmd = CMD_WAIT;
            tools.logge(logTAG, "wait: " + plT);
         } else if (cmd.equals("MOVE")) {
            plCmd = CMD_MOVE; // drive
            tools.logge(logTAG, "move");
         } else {
            throw new Exception("Unbekanntes Kommando: " + cmd);
         }

         buffer[1] = plCmd;
         buffer[2] = plT;
         buffer[3] = plR;
         buffer[4] = plV;
         myLog.refreshDrawableState();
         tools.logge(logTAG, "cmd: " + cmd + ", b=" + Tools.b2s(buffer));
         mAccessory.publish(MQTT_TOPIC, buffer); // ab an den Arduino
         tools.logge(logTAG, " --> Published **AT " + (new Date().toString()));
      } catch (Exception e) {
         tools.logge(logTAG, "Error bei runMissionStep", e);
         doStop();
      }
   }

   void doStop() {
      try {
         byte[] buffer = new byte[2];
         buffer[0] = ++plCmdId;
         buffer[1] = CMD_STOP;
         tools.logge(logTAG, "###    S T O P    ###");
         mAccessory.publish(MQTT_TOPIC, buffer);
         mAccessory.disconnect();
         tools.logge(logTAG, "STOP ENDE");
      } catch (IOException e) {
         tools.logge(logTAG, "Error bei doStop", e);  // Exception kam aus mAccessory.publish()...
      }
   }

   // Create the reciever and act on the data
   private BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
         try {
            if (!intent.getAction().equalsIgnoreCase(subscription)) {  //"com.wiley.wroxaccessories.SUBSCRIBE.AN"
               tools.logge(logTAG, "onreceive mit falscher Subscription. intent.getAction== " + intent.getAction());
               return;
            }
            byte[] payload = intent.getByteArrayExtra(subscription + ".payload");
            if (payload[0] == 0 && payload.length == 1) {
               tools.logge(logTAG, "onReceive mit leerer Payload? payload[0] = " + payload[0]);
            } else {
               tools.logge(logTAG, "onReceive pl: " + Tools.b2s(payload) + " ** AT ** " + ( new Date().toString()));
               if (payload[0] != plCmdId) {
                  tools.logge(logTAG, "onReceive plCMD diff, MyID: " + plCmdId);
               }
               switch (payload[1]) {
                  case 1:
                     tools.logge(logTAG, "--> OK");
                     break;
                  case 4:
                     tools.logge(logTAG, "--> TIMEOUT");
                     break;
                  case 8:
                     tools.logge(logTAG, "--> ERROR");
                     break;
                  case 12:
                     tools.logge(logTAG, "--> NO_SINAL -> STOP");
                     break;
                  case 16:
                     tools.logge(logTAG, "--> CANCEL");
                     doStop();
                     break;
                  default:
                     tools.logge(logTAG, "--> ??? invalid RC " + payload[1]);
                     doStop();
               }
               if (payload[0] < 16) {
                  runMissionStep();
               }
            }
         } catch (Exception e) {
            tools.logge(logTAG, "Error im BroadcastReceiver", e);
         }
      } // onReceive()
   };

   @Override
   protected void onDestroy() {
      super.onDestroy();
      doStop();
   }


   @Override
   public void onStop() {
   }
}
