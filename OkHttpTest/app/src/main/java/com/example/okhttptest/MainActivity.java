package com.example.okhttptest;

import android.app.DownloadManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    TextView mResponseTextView = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button mSendRequestButton = (Button)findViewById(R.id.Send_request);
        mSendRequestButton.setOnClickListener(this);

        mResponseTextView = (TextView)findViewById(R.id.Respond_TextView);
    }

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.Send_request:
                sendRequestWithOkHttp();
                break;
            default:
                break;
        }
    }

    private void sendRequestWithOkHttp(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("http://www.baidu.com")
                        .build();

                try{
                    Response response = okHttpClient.newCall(request).execute();

                    String ResponseData = response.body().string();

                    ShowResponseData(ResponseData);

                }catch (IOException e){
                    e.printStackTrace();
                }

            }
        }).start();
    }

    private void ShowResponseData(final String s){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResponseTextView.setText(s);
            }
        });
    }
}
