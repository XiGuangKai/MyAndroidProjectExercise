package com.example.networktest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    TextView mTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button Send_resuest = (Button)findViewById(R.id.Send_Request);
        mTextView = (TextView)findViewById(R.id.Response_Text);
        Send_resuest.setOnClickListener(this);
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.Send_Request:
                sendRequestWithHttpURl();
                break;
            default:
                break;
        }
    }

    public void sendRequestWithHttpURl(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                BufferedReader reader = null;
                try{
                    URL url = new URL("https://www.baidu.com/");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    InputStream inputStream = connection.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder respone = new StringBuilder();
                    String line;
                    while((line = reader.readLine()) != null){
                        respone.append(line);
                    }
                    showResponse(respone.toString());
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    if(reader != null){
                        try{
                            reader.close();
                        }catch(IOException e){
                            e.printStackTrace();
                        }
                    }

                    if(connection != null){
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    public void showResponse(final String s){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setText(s);
            }
        });
    }
}
