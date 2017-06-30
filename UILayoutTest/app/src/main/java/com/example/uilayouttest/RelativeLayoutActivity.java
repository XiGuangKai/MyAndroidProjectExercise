package com.example.uilayouttest;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class RelativeLayoutActivity extends AppCompatActivity {

    private Intent mIntent;
    private int resultCode = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_relative_layout);
        setContentView(R.layout.activity_relative_layout2);

        Button btnClose=(Button)findViewById(R.id.btnClose);
        btnClose.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                //数据是使用Intent返回
                Intent intent = new Intent();
                //把返回数据存入Intent
                intent.putExtra("result", getClass().getName());
                //设置返回数据
                RelativeLayoutActivity.this.setResult(RESULT_OK, intent);
                //关闭Activity
                RelativeLayoutActivity.this.finish();
            }
        });
    }
}
