package com.example.playaudiotest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private MediaPlayer mMediaPlayer = new MediaPlayer();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button Start = (Button)findViewById(R.id.Start);
        Button Stop = (Button)findViewById(R.id.Stop);
        Button Pause = (Button)findViewById(R.id.Pause);

        Start.setOnClickListener(this);
        Stop.setOnClickListener(this);
        Pause.setOnClickListener(this);

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }else{
            initMadiePlayer();
        }
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.Start:
                if(!mMediaPlayer.isPlaying()){
                    mMediaPlayer.start();
                }
                break;
            case R.id.Pause:
                if(mMediaPlayer.isPlaying()){
                    mMediaPlayer.pause();
                }
                break;
            case R.id.Stop:
                if (mMediaPlayer.isPlaying()){
                    mMediaPlayer.stop();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] Permission,int[] grantResult){
        switch (requestCode){
            case 1:
                if(grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED){
                    initMadiePlayer();
                }else{
                    Toast.makeText(MainActivity.this,"Request the Permission Failed",Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }


    public void initMadiePlayer(){
        try{
            File file = new File(Environment.getExternalStorageDirectory(),"music.mp3");
            mMediaPlayer.setDataSource(file.getPath());
            mMediaPlayer.prepare();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(mMediaPlayer != null){
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
    }
}
