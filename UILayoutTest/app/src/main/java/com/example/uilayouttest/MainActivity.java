package com.example.uilayouttest;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private String TAG = "MainActivity";
    private int mRequestCode = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button Relativelayout = (Button) findViewById(R.id.Relativelayoutbutton);
        Relativelayout.setOnClickListener(this);

        Button FrameLayoutbutton = (Button) findViewById(R.id.FrameLayoutbutton);
        FrameLayoutbutton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.Relativelayoutbutton:
                Intent Relativelayoutintent = new Intent(MainActivity.this,RelativeLayoutActivity.class);
                startActivityForResult(Relativelayoutintent,mRequestCode);
                break;
            case R.id.FrameLayoutbutton:
                Intent FrameLayoutActivityintent = new Intent(MainActivity.this,FrameLayoutActivity.class);
                startActivityForResult(FrameLayoutActivityintent,mRequestCode);
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == 1) {
            switch (resultCode) { //resultCode为回传的标记，我在B中回传的是RESULT_OK
                case RESULT_OK:
                    Log.d(TAG,getClass().getName());
                    String result = data.getExtras().getString("result");//得到新Activity 关闭后返回的数据
                    Log.i(TAG, "result = " + result);
                    break;
                default:
                    break;
            }
        }else{
            Log.d(TAG,"requestCode != 1");
        }
    }
}
