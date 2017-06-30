package activitytest.com.example.activitytest;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class FirstActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.first_layout);
        Button button1 = (Button) findViewById(R.id.button_1);
        button1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Toast.makeText(FirstActivity.this,"This is the FirstActivity",Toast.LENGTH_SHORT).show();
                //finish();
                //显示Intent
                //Intent intent = new Intent(FirstActivity.this,SecondActivity.class);
                //startActivity(intent);

                //隐式Intent
                //Intent intent = new Intent("com.example.activitytest.ACTION_START");
                Intent intent = new Intent();
                intent.setAction("123456789");
                intent.addCategory("0987654321");
                startActivity(intent);

                Intent thirdintent = new Intent(Intent.ACTION_VIEW);
                thirdintent.setData(Uri.parse("http://www.baidu.com"));
                startActivity(thirdintent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case R.id.add_item:
                Toast.makeText(FirstActivity.this,"You click the add item",Toast.LENGTH_SHORT).show();
            case R.id.remove_item:
                Toast.makeText(FirstActivity.this,"You click the remove item",Toast.LENGTH_SHORT).show();
                finish();
            default:
                Toast.makeText(FirstActivity.this,"You not click the Item",Toast.LENGTH_SHORT).show();
        }
        return true;
    }
}
