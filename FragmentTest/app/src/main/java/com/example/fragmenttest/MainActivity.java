package com.example.fragmenttest;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button)findViewById(R.id.leftfragment_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replaceFragment(new AnthorRightFragment(),"AnthorRightFragment");
            }
        });

        Button mReplacerightfragment_button = (Button)findViewById(R.id.replacerightfragment_button);
        mReplacerightfragment_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replaceFragment(new RightFragment(),"RightFragment");
            }
        });

        replaceFragment(new RightFragment(),"RightFragment");
    }

    public void replaceFragment(Fragment fragment,String Tag){
        FragmentManager mFragmentManager = getSupportFragmentManager();
        FragmentTransaction mFragmentTransaction = mFragmentManager.beginTransaction();
        mFragmentTransaction.replace(R.id.right_fragmentlayout,fragment);
        mFragmentTransaction.addToBackStack(Tag);
        mFragmentTransaction.commit();
    }
}
