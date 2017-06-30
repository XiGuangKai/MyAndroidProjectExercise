package com.example.androidthreadtest;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final int Handle_UPDATE_UI = 1;
    private TextView mUpdate_UI_TextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button mUpdate_UI_button = (Button)findViewById(R.id.updata_UI);
        mUpdate_UI_button.setOnClickListener(this);

        mUpdate_UI_TextView = (TextView)findViewById(R.id.Update_Ui_Text);
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.updata_UI:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message message = new Message();
                        message.what = Handle_UPDATE_UI;
                        handler.sendMessage(message);
                    }
                }).start();
                break;
            default:
                break;
        }
    }

    private Handler handler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what){
                case Handle_UPDATE_UI:
                    mUpdate_UI_TextView.setText("This is Handler update UI");
                    break;
                default:
                    break;
            }
        }
    };
}
