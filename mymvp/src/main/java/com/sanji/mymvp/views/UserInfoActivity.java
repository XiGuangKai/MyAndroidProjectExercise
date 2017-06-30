package com.sanji.mymvp.views;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.sanji.mymvp.R;
import com.sanji.mymvp.iview.IUserInfoView;
import com.sanji.mymvp.model.UserInfoModel;
import com.sanji.mymvp.presenter.UserInfoPresenter;

public class UserInfoActivity extends AppCompatActivity implements IUserInfoView.View {
    private TextView tv_name;
    private TextView tv_age;
    private TextView tv_address;
    private IUserInfoView.Presenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_name = (TextView) findViewById(R.id.userName);
        tv_age = (TextView) findViewById(R.id.age);
        tv_address = (TextView) findViewById(R.id.address);
        new UserInfoPresenter(this);
        presenter.start();
    }

    @Override
    public String loadUserId() {
        return "1000";//假设需要查询的用户信息的userId是1000
    }

    @Override
    public void showLoading() {
        Toast.makeText(this, "正在加载", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void dismissLoading() {
        Toast.makeText(this, "加载完成", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showUserInfo(UserInfoModel userInfoModel) {
        if (userInfoModel != null) {
            tv_name.setText(userInfoModel.getName());
//            tv_age.setText(userInfoModel.getAge());
//            tv_address.setText(userInfoModel.getAddress());
        }
    }

    @Override
    public void setPresenter(IUserInfoView.Presenter presenter) {
    this.presenter=presenter;
    }
}
