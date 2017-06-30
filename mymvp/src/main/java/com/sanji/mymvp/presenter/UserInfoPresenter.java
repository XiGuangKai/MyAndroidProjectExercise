package com.sanji.mymvp.presenter;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import com.sanji.mymvp.R;
import com.sanji.mymvp.iview.IUserInfoView;
import com.sanji.mymvp.model.UserInfoModel;

public class UserInfoPresenter implements IUserInfoView.Presenter {
    private IUserInfoView.View view;

    public UserInfoPresenter(IUserInfoView.View view) {
        this.view = view;
        view.setPresenter(this);
    }
    public void loadUserInfo() {
        String userId = view.loadUserId();
        view.showLoading();//接口请求前显示loading
        //这里模拟接口请求回调
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                UserInfoModel userInfoModel = new UserInfoModel("小宝", 1, "杭州");
                view.showUserInfo(userInfoModel);
                view.dismissLoading();
            }
        }, 3000);
    }

    @Override
    public void start() {
        loadUserInfo();
    }
}
