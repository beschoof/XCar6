package com.example.mqttlib;


      import java.io.IOException;
      import java.util.ArrayList;

      import com.example.mqttlib.MQTT;

      import android.content.BroadcastReceiver;
      import android.content.Context;
      import android.content.Intent;
      import android.content.IntentFilter;
      import android.os.AsyncTask;
      import android.widget.TextView;

public class WroxAccessory {
   private static final String logTAG = "WroxAccessory";
   public static final String SUBSCRIBE = "com.wiley.wroxaccessories.SUBSCRIBE";
   private Context mContext;
   private MonitoringThread mMonitoringThread;
   private BroadcastReceiver receiver;

   public WroxAccessory(Context context, TextView myLog) {
      mContext = context;
   }

   public void connect(Connection connection) throws IOException {
      mMonitoringThread = new MonitoringThread(connection);
      Thread thread = new Thread(null, mMonitoringThread, "MonitoringThread");
      thread.start();
      byte[] bConnect = MQTT.connect();
      new WriteHelper().execute(bConnect);
   }

   public void publish(String topic, byte[] message) throws IOException {
      new WriteHelper().execute(MQTT.publish(topic, message));
   }

   public String subscribe(BroadcastReceiver receiver, String topic, int id) throws IOException {
      new WriteHelper().execute(MQTT.subscribe(id, topic, MQTT.AT_MOST_ONCE));
      this.receiver = receiver; // Register receiver
      String sub = WroxAccessory.SUBSCRIBE + "." + topic;  // "com.wiley.wroxaccessories.SUBSCRIBE.AN"
      IntentFilter filter = new IntentFilter();
      filter.addAction(sub);
      mContext.registerReceiver(receiver, filter);
      return sub;
   }

   public void unsubscribe(String topic, int id) throws IOException {
      new WriteHelper().execute(MQTT.unsubscribe(id, topic)); // Send unsub
      // TODO Should only unregister the correct action, now it removes all subscriptions
      mContext.unregisterReceiver(receiver);
   }

   public void pingreq() throws IOException {
      new WriteHelper().execute(MQTT.ping());
   }

   public void disconnect() throws IOException {

      if (mMonitoringThread.mConnection != null) {
         mMonitoringThread.mConnection.close();
      }

      if (receiver != null) {
         mContext.unregisterReceiver(receiver);
      }
   }

   private class WriteHelper extends AsyncTask<byte[], Void, Void> {

      @Override
      protected Void doInBackground(byte[]... params) {
         try {
            mMonitoringThread.mConnection.getOutputStream().write(params[0]);
         } catch (Exception e) {
         }
         return null;
      }
   }

   private class MonitoringThread implements Runnable {

      Connection mConnection;

      public MonitoringThread(Connection connection) {
         mConnection = connection;
      }

      public void run() {
         int ret = 0;
         byte[] buffer = new byte[16384];

         while (ret >= 0) {
            try {
               ret = mConnection.getInputStream().read(buffer);
            } catch (Exception e) {
               break;
            }

            if (ret > 0) {
               MQTTMessage msg = MQTT.decode(buffer);

               if (msg.type == MQTT.PUBLISH) {
                  Intent publishIntent = new Intent();
                  publishIntent.setAction(SUBSCRIBE + "." + msg.variableHeader.get("topic_name"));
                  publishIntent.putExtra(SUBSCRIBE + "." + msg.variableHeader.get("topic_name") + ".topic",
                        msg.variableHeader.get("topic_name").toString());                                     //"com.wiley.wroxaccessories.SUBSCRIBE.AN"
                  publishIntent.putExtra(SUBSCRIBE + "." + msg.variableHeader.get("topic_name") + ".payload",
                        msg.payload);
                  mContext.sendBroadcast(publishIntent);
               }
            }
         }
      }
   }
}