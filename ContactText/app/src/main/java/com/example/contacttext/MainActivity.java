package com.example.contacttext;

import android.content.pm.PackageManager;
import android.database.Cursor;

import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    ArrayAdapter arrayAdapter;
    List <String> list = new ArrayList<>();
    ListView listView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.Contact_List);
//        arrayAdapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,list);
//        listView.setAdapter(arrayAdapter);
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_CONTACTS},1);
        }else{
            ReadContact();
        }
    }

    Handler mUpdateUIHandle = new Handler() {
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case 0:
                    arrayAdapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,list);
                    listView.setAdapter(arrayAdapter);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onResume(){
        super.onResume();
    }

    public void ReadContact(){
        Cursor cursor;
        cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null,null,null,null);
        try{
            while (cursor.moveToNext()){
                String ContactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String ContactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                list.add(ContactName + "\n" + ContactNumber);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(cursor != null){
                cursor.close();
            }
            mUpdateUIHandle.sendEmptyMessage(0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestcode,String[] permission,int[] grantResult){
        switch(requestcode){
            case 1:
                if(grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED){
                    ReadContact();
                }else{
                    Toast.makeText(MainActivity.this,"Get the Permission Failed",Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
}
