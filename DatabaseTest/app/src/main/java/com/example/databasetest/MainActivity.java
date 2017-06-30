package com.example.databasetest;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private MyDatabaseHelper dbHelper;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new MyDatabaseHelper(MainActivity.this,"BookStore.db",null,3);
        Button mCreateDatabase = (Button)findViewById(R.id.Create_Database);
        mCreateDatabase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dbHelper.getWritableDatabase();
            }
        });

        final Button mAddData = (Button) findViewById(R.id.add_data);
        mAddData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("name","The first line code");
                values.put("author","Guo Lin");
                values.put("pages",454);
                values.put("price",50);
                db.insert("Book",null,values);
                values.clear();

                values.put("name","Crazi Android");
                values.put("author","Li Gang");
                values.put("pages",510);
                values.put("price",100);
                db.insert("Book",null,values);
                values.clear();

                Toast.makeText(MainActivity.this,"Add Data Success",Toast.LENGTH_SHORT).show();
            }
        });

        Button mUpdate_data = (Button) findViewById(R.id.update_data);
        mUpdate_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("price",55);
                db.update("Book",values,"name=?",new String[]{"The first line code"});
                Toast.makeText(MainActivity.this,"Update Data Success",Toast.LENGTH_SHORT).show();
            }
        });

        final Button mDeleteData = (Button)findViewById(R.id.Delete_Data);
        mDeleteData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete("Book","pages > ?",new String[]{"500"});
                Toast.makeText(MainActivity.this,"Delete the Data Success",Toast.LENGTH_SHORT).show();
            }
        });

        Button mQuery_Data = (Button)findViewById(R.id.Query_Data);
        mQuery_Data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor mCursor = db.query("Book",null,null,null,null,null,null);
                if(mCursor.moveToFirst()){
                    do{
                        String name = mCursor.getString(mCursor.getColumnIndex("name"));
                        String author = mCursor.getString(mCursor.getColumnIndex("author"));
                        int pages = mCursor.getInt(mCursor.getColumnIndex("pages"));
                        double price = mCursor.getDouble(mCursor.getColumnIndex("price"));
                        Log.d(TAG,"name = " + name);
                        Log.d(TAG,"author = " + author);
                        Log.d(TAG,"pages = " + pages);
                        Log.d(TAG,"price = " + price);
                    }while (mCursor.moveToNext());

                }
                mCursor.close();
            }
        });
    }
}
