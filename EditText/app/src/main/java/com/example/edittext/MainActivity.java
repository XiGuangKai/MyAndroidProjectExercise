package com.example.edittext;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private EditText editText1;
    private Button button1;
    private Button button2;
    private Button button3;
    private Button AlerDialogbutton;
    private Button progressdialogbutton;
    private ImageView imageView1;
    private ProgressBar progressBar1;
    private int progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText1 = (EditText) findViewById(R.id.edittest_1);
        button1 = (Button)findViewById(R.id.button1);
        button1.setOnClickListener(this);

        button2 = (Button)findViewById(R.id.button2);
        imageView1 = (ImageView)findViewById(R.id.imageView1);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch(view.getId()) {
                    case R.id.button2:
                        imageView1.setImageResource(R.mipmap.ic_launcher_round);
                        break;
                    default:
                        break;
                }
            }
        });

        button3 = (Button) findViewById(R.id.button3);
        progressBar1 = (ProgressBar) findViewById(R.id.progressBar1);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()){
                    case R.id.button3:
                        if(progressBar1.getVisibility() == View.GONE){
                            progressBar1.setVisibility(View.VISIBLE);
                            progress = progressBar1.getProgress();
                            progress += 30;
                            progressBar1.setProgress(progress);
                        }else{
                            progress -= 20;
                            progressBar1.setVisibility(View.GONE);
                        }
                }
            }
        });

        AlerDialogbutton = (Button) findViewById(R.id.AlerDialogbutton);
        AlerDialogbutton.setOnClickListener(this);

        progressdialogbutton = (Button) findViewById(R.id.progressdialogbutton);
        progressdialogbutton.setOnClickListener(this);
    }
    @Override
    public void onClick(View v){
        switch(v.getId()){
            case R.id.button1:
                String inputedittext = editText1.getText().toString();
                Log.d("MainActivity",inputedittext);
                Toast.makeText(MainActivity.this,inputedittext,Toast.LENGTH_SHORT).show();
                break;
            case R.id.AlerDialogbutton:
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setCancelable(false);
                dialog.setTitle("This is AlerDialog");
                dialog.setMessage("Something Important");
                dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(MainActivity.this,"Delete the Important things",Toast.LENGTH_SHORT).show();
                    }
                });
                dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(MainActivity.this,"Not Delete the Important things",Toast.LENGTH_SHORT).show();
                    }
                });
                dialog.show();
                break;
            case R.id.progressdialogbutton:
                ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setCancelable(true);
                progressDialog.setTitle("This is the ProgressDilog");
                progressDialog.setMessage("Loading.....");
                progressDialog.show();
                break;
            default:
                Log.d("MainActivity","Null");
                Toast.makeText(MainActivity.this,"No define",Toast.LENGTH_SHORT).show();
                break;
        }
    }
}
