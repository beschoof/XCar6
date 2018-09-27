package com.example.besch.xcar6;
// XCar6, Stand: 15.05.18
// + Winkelmessung via Gyro
// --> erweiterung um Parameter a (angle)

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.besch.xcar6.mission.Mission;
import com.example.besch.xcar6.mission.MissionStep;
import com.example.besch.xcar6.mission.MissionUtils;
import com.example.besch.xcar6.tools.Tools;
import com.example.mqttlib.UsbConnection12;
import com.example.mqttlib.WroxAccessory;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.util.Locale;


public class MainActivity extends Activity {

   private WroxAccessory mAccessory;
   private UsbManager mUsbManager;
   private UsbConnection12 connection;
   String subscription;
   private int subscriptionId = 0;
   private byte plCmdId = 0;
   TextView myLog;
   TextToSpeech textToSpeech;
   EditText missionFileName;
   Mission myMission = null;
   final String logTAG = "MainActivity";
   final String MQTT_TOPIC = "AN";
   Tools tools;
//   String sFileName1 = "/mnt/sdcard/misc/X2_Seben.txt";  // Handy
   String sFileName1 = Environment.getExternalStorageDirectory().getPath() + "/misc/X6_Crawl.txt";  // Handy
//   String sFileName1 = "/mnt/sdcard2/misc/X1_Crawl.txt";   // Tablet
   final static int CMD_INIT = 1;
   final static int CMD_MOVE = 2;
   final static int CMD_WAIT = 3;
   final static int CMD_STOP = 9;

   private MrWiley mrWiley;
   boolean ocvMode = false;
   int oldDir = 0;


   @Override
   public void onCreate(Bundle savedInstanceState) {
      try {
         super.onCreate(savedInstanceState);
         textToSpeech = new TextToSpeech(
               getApplicationContext(),
               new TextToSpeech.OnInitListener() {
                  @Override
                  public void onInit(int status) {
                     if (status != TextToSpeech.ERROR) {
                        textToSpeech.setLanguage(Locale.UK);
                     }
                  }
               });
         setContentView(R.layout.activity_main);
         myLog = (TextView) findViewById(R.id.logText1);
         tools = new Tools(myLog);
         myLog.setMovementMethod(new ScrollingMovementMethod());
         tools.logge(logTAG, "onCreate beginn");
         speakText("onCreate beginn");
         missionFileName = (EditText) findViewById(R.id.MissionFileName);
         missionFileName.setText(sFileName1);
         mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
         connection = new UsbConnection12(this, mUsbManager);
         mAccessory = new WroxAccessory(this, myLog);
         plCmdId = 0;

         tools.logge(logTAG, "XCar6 onCreate ok");
         speakText("onCreate ok");
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


   public void cmdTrace(View v) {
      startOcvMode();
   }

   public void cmdForward(View v) {   // wird nur einmal gedrückt, arbeitet die gesamte Mission ab
      try {
         String fileName = missionFileName.getText().toString();
         tools.logge(logTAG, "Forward begin...: " + fileName);
         MissionUtils missionUtils = new MissionUtils();
         myMission = new Mission();
         myMission.setMissionSteps(missionUtils.getMission(fileName, myLog));
         runMissionStep();  // erster Schritt wird angestoßen, der Rest folgt im Loop
         tools.logge(logTAG, "Forward ende");
      } catch (Exception e) {
         tools.logge(logTAG, "Error bei cmdForward", e);
      }
   }

   public void cmdStop(View v) {
      tools.logge(logTAG, "STOP command");
      speakText("command STOP");
      doStop();
   }

   void runMissionStep() {
      MissionStep m = null;

         m = myMission.getNextStep();

         if (m == null) {
            tools.logge(logTAG, "run Step = null");
            doStop();
            return;
         }

         tools.logge(logTAG, "run step: " + myMission.getLine() + ": " + m.toString());
         speakText("run step " + m.getCmd());

         String cmd = m.getCmd();

         byte plCmd = 0; // drive (ggf. auch mit v=0)
         byte plT = 0;  // time
         byte plR = 0;  // radius
         byte plV = 0;  // geschw
         byte plS = 0;  // weg
         byte plA = 0;  // Winkel

         for (int i = 0; i < m.getParamKeys().length; i++) {
            String pKey = m.getParamKeys()[i];
            int iVal = m.getParamVals()[i];
            if (pKey.equals("T")) { // bei >= 100 -> millis, sonst sek.
               plT = (byte) iVal;
            } else if (pKey.equals("R")) {
               plR = (byte) (iVal + 8); // -7..7 -> 1..15  , keine 0
            } else if (pKey.equals("V")) {
               plV = (byte) (iVal + 8); // -7..7 -> 1..15  , keine 0
            } else if (pKey.equals("S")) {
               plS = (byte) iVal;
            } else if (pKey.equals("A")) {
               plA = (byte) iVal;
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
         } else if (cmd.equals("FIND")) {
            tools.logge(logTAG, "move");
            startOcvMode();
         } else {
            tools.logge(logTAG, "Error bei runMissionStep::Unbekanntes Kommando: " + cmd);
            doStop();
         }

         if (! ocvMode) sendToCar (plCmd,plT,plR,plV,plS,plA, cmd);

   }

   private void sendToCar (byte plCmd, byte plT, byte plR, byte plV, byte plS, byte plA, String cmd) {

      byte[] buffer = new byte[7]; // ID, cmd, T, R, V, S, A
      if (++plCmdId > 100) plCmdId = 1;

      buffer[0] = plCmdId;
      buffer[1] = plCmd;
      buffer[2] = plT;
      buffer[3] = plR;
      buffer[4] = plV;
      buffer[5] = plS;
      buffer[6] = plA;

      try {
      mAccessory.publish(MQTT_TOPIC, buffer); // ab an den Arduino
      } catch (Exception e) {
         tools.logge(logTAG, "Error bei runMissionStep", e);
         doStop();
      }
      myLog.refreshDrawableState();
      tools.logge(logTAG, " -> Published cmd: " + cmd + ", b=" + Tools.b2s(buffer));
   }

   void doStop() {
      try {
         byte[] buffer = new byte[2];
         buffer[0] = ++plCmdId;
         buffer[1] = CMD_STOP;
         tools.logge(logTAG, "STOP");
         mAccessory.publish(MQTT_TOPIC, buffer);
         mAccessory.disconnect();
         speakText("do stop and out");
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
               tools.logge(logTAG, "onReceive pl: " + Tools.b2s(payload));
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

               int rcVal = 0;
               if (payload.length > 2) {
                  int k=1;
                  for (int i=2; i<payload.length; i++) {
                     rcVal += payload[i] * k;
                     k *= 256;
                  }
                  tools.logge(logTAG, ":: onReceive retVal = " + rcVal + " -> " + Tools.b2s(payload));
               }

               if (! ocvMode && payload[0] < 16) {
                  runMissionStep();  // Weiter geht's!
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
      textToSpeech.shutdown();
   }

   private void startOcvMode() {
      speakText("trace mode started");
      ocvMode = true;
      if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this,
            mOpenCVCallBack)) {
         Log.i(logTAG, "Couldn't connect to OpenCV");
         speakText("Couldn't connect to OpenCV");
      }
   }

   private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
      @Override
      public void onManagerConnected(int status) {
         if (status == LoaderCallbackInterface.SUCCESS) {
            Log.i(logTAG, "Connected to OpenCV");
            mrWiley = new MrWiley(mAppContext, mOcvHandler);
            setContentView(mrWiley);
            if (!mrWiley.openCamera())
               finish();
         } else {
            super.onManagerConnected(status);
         }
      }
   };

   protected static final byte ACTION_LEFT = 0;
   protected static final byte ACTION_RIGHT = 1;
   protected static final byte ACTION_STOP = 2;
   private  Handler mOcvHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         byte plCmd = CMD_MOVE; // drive (ggf. auch mit v=0)
         byte plT = 0;  // time
         byte plV = 3;  // geschw
         byte plR = 8;  // radius
         byte plS = 0;  // weg
         byte plA = 0;  // Winkel
         int  newDir = 0; // left: -1, right: 1

         switch (msg.what) {
            case ACTION_LEFT:
               plR = 10;  // radius
               newDir = -1;
               if (oldDir != newDir) {
                  speakText("go left");
                  oldDir = newDir;
               }
               Log.i(logTAG, "< < < < < < < < < < < < < < < < < < < < < < < < < < < ");
               break;
            case ACTION_RIGHT:
               plR = 6;  // radius
               newDir = 1;
               if (oldDir != newDir) {
                  speakText("go right");
                  oldDir = newDir;
               }
               Log.i(logTAG, "> > > > > > > > > > > > > > > > > > > > > > > > > > > > >");
               break;
            case ACTION_STOP:
 //              plCmd = CMD_WAIT;
 //              plV = 0;
 //              plR = 0;
//				speakText("go stop");
               break;
         }
         plV += 8;
//         plR += 8;
         sendToCar (plCmd,plT,plR,plV,plS,plA, "FIND");
      }
   };


   @Override
   public void onStop() {
      super.onStop();
   }

   public void speakText(String tts){
//      textToSpeech.speak(tts, TextToSpeech.QUEUE_ADD, null);
   }
}
