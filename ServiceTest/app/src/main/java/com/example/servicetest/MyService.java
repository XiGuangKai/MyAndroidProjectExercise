package com.example.servicetest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class MyService extends Service {

    private static final String TAG = "MyService";

    private DownloadBinder mBinder = new DownloadBinder();

    public MyService() {
    }

    class DownloadBinder extends Binder {
        public void Download(){
            Log.d(TAG,"DownloadBinder Download()");
        }

        public int getProgress(int a){
            Log.d(TAG,"DownloadBinder getProgress() a = " + a);
            return a;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        Log.d(TAG,"onCreate()");
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent PI = PendingIntent.getActivity(this,0,intent,0);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentText("This is Content text")
                .setContentTitle("This is Context Title")
                .setWhen(System.currentTimeMillis())
                .setContentIntent(PI)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .build();
        startForeground(1,notification);
    }

    @Override
    public int onStartCommand(Intent intent,int flag,int startId){
        Log.d(TAG,"onStartCommand()");
        return super.onStartCommand(intent,flag,startId);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG,"onDetroy()");
    }
}
