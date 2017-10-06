/*
   Stand 21.09.17
   - Basis for XCar
   - unterschiedlicher Code  bei SebenRC / Crawler (backward, v)

   (NB:Please do not use Digital pin 7 as input or output because is used in the comunication with MAX3421E)
*/

#include <Servo.h>
#include <P2PMQTT.h>
#include <SoundLib.h>

//Pins
#define motorPin    9  // YELLOW
#define servoPin    8  // GREEN

// car type
#define carTypeSeben  1
#define carTypeCrawl  2
int carType = 0;

// return codes -> android
#define rcOk       1
#define rcError    8
#define rcCancel  16  // mission cancel

// commands
#define cmdInit  1
#define cmdMove  2
#define cmdWait  3
#define cmdStop  9

// MQTT
#define mqttTopic "AN"

// Kommandos
int plId, plCmd, plT, plR, plV; // Payload, kommt von ganz oben, Zeit, Kurvenradius, Geschw.
int oldId = -1;
String lastMsg = "";                   // println
int lastGetType = 0;
boolean backw = false;
unsigned long tStart;
unsigned long duration;
unsigned long lfdTime;
unsigned long loopStart;
unsigned long loopTime;
unsigned long tWait4Timeout;
boolean iAmActive = false;
int type = 0;

boolean subscribed = false; // interessiert sich jemand
boolean missionFinish = false;

// drive
Servo lenkungServo;
Servo motorServo;  // weil das geht auch ueber pwm
int motorServoVal = 90;

// los gehts
P2PMQTT mqtt(true); // add parameter true for debugging
P2PMQTTpublish pub;

void setup() {
  Serial.begin(9600);
  Serial.println("init...");
  lenkungServo.attach(servoPin);
  motorServo.attach(motorPin);
  doWait();  // erst mal beide Motoren auf Stillstand
  delay(5000);  // Zeit zwischen einpluggen und "Start"

  mqtt.begin("XCar1");  // --> P2PMQTT -> AndroidAccessory -> MAX3421E::powerOn()
  if (mqtt.isConnected()) {  // AndroidAccessory.isConnected() -> UsbHost...()
       logge (" +++ init connected");
  } else {
       logge (" ### init connected failure");
  }    
 
  Serial.println("start");
  tStart = millis();
  tWait4Timeout = millis();  // TODO : reagieren auf timeout von oben
}  // end setup()

void loop() {
       
  if (missionFinish) { 
    delay(100); 
    goto loopEnd; 
    }
  loopStart = millis();
 
// 1. gibt's was von oben?
  type = mqtt.getType(mqtt.buffer); 
 
  if (type < 0) { goto loopEnd; }
   
// 2. Auswerten des Pakets
  switch(type) {

  case CONNECT:
     logge (" getType: connect");
     break;

  case SUBSCRIBE:  // die da oben deuten an, dass sie sich interessieren
    subscribed = mqtt.checkTopic(mqtt.buffer, type, mqttTopic);  // check "AN"
    if (subscribed) {
      logge (" getType: subscribed");
    } else {
      logge(" getType: ??? not subscribed");
    }
    break;

  case PUBLISH:  // ds kommt vom Android
      plId  = (unsigned int) mqtt.getPayload(mqtt.buffer, type)[0];
      plCmd = (unsigned int) mqtt.getPayload(mqtt.buffer, type)[1];
      plT = (unsigned int) mqtt.getPayload(mqtt.buffer, type)[2];  // in sek., bei cmdInit: 101/102: Seben / Crawler
      plR = (unsigned int) mqtt.getPayload(mqtt.buffer, type)[3];  // 0..15 , 8==geradeaus, 0: voll rechts, 15: voll links
      plV = (unsigned int) mqtt.getPayload(mqtt.buffer, type)[4];  // -- bei carTypeSeben::  1..15 , 8==halt, 0: voll speed back, 15: voll speed vor

      tStart = millis();      
      logge("-> got cmd: " + String(plId) + " / " + String(plCmd) + " / " + String(plT) + " / " + String(plR) + " / " + String(plV) + " ** AT ** " + String(tStart));

      // egal, ob gar kein oder kein neues Kommand kommmt: test auf weiter so
      if (plId == 0 || plCmd == 0 || plId == oldId)  {   // nix neues von oben
        goto loopEnd;
      }
      oldId = plId;
      iAmActive = true;
      duration = plT * 1000; // in ms
      tStart = millis();

      switch(plCmd) {

        case cmdInit:
          logge(" ---> I N I T   mit carType = " + String(plT));
           if (plT == 101 || plT == 102) {
             carType = plT - 100;  // wir kriegen 101 / 102
             doPublish(rcOk, plT);
           } else {
             cancel(" #### cmdInit: falsche plT --> ERROR.. " + String(plT), 17);
           }
          goto loopEnd;  
          break;

        case cmdMove:
          logge(" ---> M O V E  mit duration = " + String(duration));
          if (carType == 0) {
            cancel(" #### cmdMove vor cmdInit --> ERROR", 2);
          } else {
            doMove();
          }
          break;

        case cmdWait:
          logge(" ---> W A I T   mit duration = " + String(duration));
          doWait();
          break;

        case cmdStop:
          logge(" ---> S T O P");
          doPublish(rcOk, 0);
          doWait();
          missionFinish = true;
          break;

      } // switch cmd

    break;  //   case PUBLISH

  default:         // do nothing
    break;
  } // switch mqtt.type

loopEnd:
    if (iAmActive && (millis() - tStart > duration) ) {  // Zeit abgelaufen ?
      doPublish(rcOk, 0);    
      // sende OK ans Android, und warte auf neues Kommando. Wir fahren einfach weiter.... 
    }
 
} // end loop

void doMove() {
   int v0 = 0;
   if (carType == carTypeSeben) {
     v0 = plV;     // 1..15
     if (v0 == 0) v0 = 8;  // goldene Mitte --> auf 90 gemappt
     v0 = 16 - v0;   // sonst macht er backward
     motorServoVal = map(v0, 1, 15, 20, 160);

     if (motorServoVal > 90 ) { // backward
       if (! backw) { // reverse
         logge(" # # # # backwards");
         backw = true;
         motorServo.write(180);
         delay(100);
         motorServo.write(90);
         delay(100);
       }
     } else {
       backw = false;
     }
   } else { // crawl
     motorServoVal = map(v0, 1, 127, 70, 110);  // Startwert
   }
   motorServo.write(motorServoVal);

   int r0 = plR;
   if (r0 == 0) r0 = 8;
   int lenkungServoVal = map(r0, 1, 15, 20, 160);
   lenkungServo.write(lenkungServoVal); // brumm brumm....

   logge(" +++ MOVE mit v/r: " + String(motorServoVal) + " / " + String(lenkungServoVal));
} // doMove()

void doWait () {
  logge(" + + + Stay + + + ");
  lenkungServo.write(90);
  motorServoVal = 90;
  motorServo.write(motorServoVal);
}

void doPublish(int rc, int val)  {  //  wir sind rum
  if (mqtt.isConnected() && subscribed) {
    byte bval[3];
    bval[0] = plId;
    bval[1] = rc;
    bval[2] = val;
    pub.payload = bval;
    pub.fixedHeader = 48;
    pub.length = 4 + 3;
    pub.lengthTopicMSB = 0;
    pub.lengthTopicLSB = 2;
    pub.topic = (byte*) mqttTopic;
    mqtt.publish(pub);
    logge("published rc/id: " + String(rc) + " bei id: " + plId + " ** AT ** " + String(millis()));
    iAmActive = false;
    tWait4Timeout = millis();
  } else {
    logge(" ??? reached but not conn+subsr ??? id/rc:" + String(plId) + " / " + String(rc));
  }
}

///////////////////////////////////// TOOLS
void cancel(String msg, int rcVal) {
  logge(msg);
  doWait();
  doPublish(rcCancel, rcVal);
  missionFinish = true;
}

void logge(String msg) {
  if (msg != lastMsg) {
    lastMsg = msg;
    Serial.println("### " + msg);
  }
}

int sign(unsigned long x, unsigned long y) {
   if (x >= y) return 1; else return -1;
}


