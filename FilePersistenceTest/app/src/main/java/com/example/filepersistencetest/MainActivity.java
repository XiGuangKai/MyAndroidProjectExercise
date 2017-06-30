package com.example.filepersistencetest;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button mSavebutton;
    private Button mCancelButton;
    private EditText mEdittext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mEdittext = (EditText)findViewById(R.id.medit_text);
        mSavebutton = (Button)findViewById(R.id.save_button);
        mCancelButton = (Button)findViewById(R.id.cancel_button);

        mSavebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"to save the edit text");
                String mWirteString = mEdittext.getText().toString();
                if(!TextUtils.isEmpty(mWirteString)){
                    save(mWirteString);
                    Toast.makeText(MainActivity.this,"Wirte " + mWirteString + " Successful",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(MainActivity.this,"Please Input the String",Toast.LENGTH_SHORT).show();
                }
            }
        });
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"to read the file");
                String mReadString = read();
                if(!TextUtils.isEmpty(mReadString)){
                    mEdittext.setText(mReadString);
                }else{
                    mEdittext.setText("This file is null");
                }
            }
        });
    }

    public void save(String string){
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try{
            out = openFileOutput("wdy_data.txt", Context.MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(string);
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try{
                if(writer != null){
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public String read(){
        FileInputStream in = null;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        try{
            in = openFileInput("wdy_data.txt");
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while ((line = reader.readLine()) != null){
                content.append(line);
            }
        }catch (IOException E){
            E.printStackTrace();
        }finally {
            if(reader != null){
                try{
                    reader.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        return content.toString();
    }
}
