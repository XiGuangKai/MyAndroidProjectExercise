package com.example.notificationtest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button SendNotification = (Button) findViewById(R.id.send_Notification);
        SendNotification.setOnClickListener(this);
    }

    @Override
    public void onClick(View v){
        switch(v.getId()){
            case R.id.send_Notification:
                Intent intent = new Intent(this,NotificationActivity.class);
                PendingIntent PI = PendingIntent.getActivity(this,0,intent,0);
                NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                Notification mNotification = new NotificationCompat.Builder(this)
                        .setContentTitle("This is notification context")
                        .setContentText("This is notification context")
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                        .setContentIntent(PI)
                        //.setAutoCancel(true)
                        //.setLights(Color.GREEN,1000,100)
                        //.setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(new long[]{0,1000,1000,1000})
                        .build();
                mNotificationManager.notify(1,mNotification);
                break;
            default:
                break;
        }
    }
}
