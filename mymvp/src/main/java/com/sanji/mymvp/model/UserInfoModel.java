package com.sanji.mymvp.model;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.sanji.mymvp.R;

public class UserInfoModel {
    private String name;
    private int age;
    private String address;

    public UserInfoModel(String name, int age, String address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }

    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
