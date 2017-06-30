package com.example.servicetest;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "MainActivity";

    private MyService.DownloadBinder downloadBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button StartService = (Button)findViewById(R.id.Start_servece);
        Button StopService = (Button)findViewById(R.id.Stop_Service);

        StartService.setOnClickListener(this);
        StopService.setOnClickListener(this);

        Button BindService = (Button)findViewById(R.id.Bind_Service);
        Button UnBindService = (Button)findViewById(R.id.Unbind_Service);

        BindService.setOnClickListener(this);
        UnBindService.setOnClickListener(this);

        Button StartIntentService = (Button) findViewById(R.id.Start_IntentService);
        StartIntentService.setOnClickListener(this);
    }

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.Start_servece:
                Intent startServiceIntent = new Intent();
                startServiceIntent.setClass(this,MyService.class);
                startService(startServiceIntent);
                break;
            case R.id.Stop_Service:
                Intent stopServiceIntent = new Intent();
                stopServiceIntent.setClass(this,MyService.class);
                stopService(stopServiceIntent);
                break;
            case R.id.Bind_Service:
                Intent BinderServiceIntent = new Intent().setClass(this,MyService.class);
                bindService(BinderServiceIntent,serviceConnection,BIND_AUTO_CREATE);
                break;
            case R.id.Unbind_Service:
                unbindService(serviceConnection);
                break;
            case R.id.Start_IntentService:
                Log.d(TAG,"This thread is " + Thread.currentThread().getId());
                Intent startIntentService = new Intent(this,MyIntentService.class);
                startService(startIntentService);
            default:
                break;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            downloadBinder = (MyService.DownloadBinder)iBinder;
            downloadBinder.Download();
            downloadBinder.getProgress(123456);
            Log.d(TAG,"downloadBinder.getProgress = " + downloadBinder.getProgress(123456));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG,"onServiceDisconnected");
        }
    };
}
