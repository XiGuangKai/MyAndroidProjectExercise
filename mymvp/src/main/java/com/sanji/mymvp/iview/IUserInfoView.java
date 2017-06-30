package com.sanji.mymvp.iview;

import com.sanji.mymvp.model.UserInfoModel;
import com.sanji.mymvp.presenter.BasePresenter;

public interface IUserInfoView {
    interface View extends BaseView<Presenter> {
        String loadUserId();//假设接口请求需要一个userId

        void showLoading();//展示加载框

        void dismissLoading();//取消加载框展示

        void showUserInfo(UserInfoModel userInfoModel);//将网络请求得到的用户信息回调
    }
    interface Presenter extends BasePresenter {
        void loadUserInfo();
    }
}
