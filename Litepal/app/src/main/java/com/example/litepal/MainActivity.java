package com.example.litepal;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.litepal.crud.DataSupport;
import org.litepal.tablemanager.Connector;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button Create_Database = (Button)findViewById(R.id.Create_Database);
        Create_Database.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Connector.getDatabase();
                Toast.makeText(MainActivity.this,"Create DB Success",Toast.LENGTH_SHORT).show();
            }
        });

        Button Add_Data = (Button)findViewById(R.id.Add_Data);
        Add_Data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Book mBook = new Book();
                mBook.setAuthor("Guo Lin");
                mBook.setId(1);
                mBook.setName("The First Line");
                mBook.setPages(545);
                mBook.setPrice(16.95);
                mBook.setPress("Unknow");
                mBook.save();
                Toast.makeText(MainActivity.this,"Add Data Success",Toast.LENGTH_SHORT).show();
            }
        });

        Button Update_data = (Button) findViewById(R.id.Update_Data);
        Update_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Book mBook = new Book();
                mBook.setPrice(14.95);
                mBook.setPress("Anchor");
                mBook.updateAll("name = ? and author = ?","The First Line","Guo Lin");
                Toast.makeText(MainActivity.this,"Update Data Success",Toast.LENGTH_SHORT).show();
            }
        });


        Button Delete_data = (Button)findViewById(R.id.Delete_data);
        Delete_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Book mBook = new Book();
                DataSupport.deleteAll(Book.class,"price < ?","15");
                Toast.makeText(MainActivity.this,"Delete Data Success",Toast.LENGTH_SHORT).show();
            }
        });


        Button Find_data = (Button)findViewById(R.id.Find_Data);
        Find_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Book> books = DataSupport.findAll(Book.class);
                for(Book book:books){
                    Log.d(TAG,"Book name = "+ book.getName());
                    Log.d(TAG,"Book price = " + book.getPrice());
                    Log.d(TAG,"Book author = " + book.getAuthor());
                    Log.d(TAG,"Book press = " + book.getPress());
                    Log.d(TAG,"Book pages = " + book.getPages());
                }
            }
        });
    }
}
