package com.example.weather;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.os.Bundle;

import static java.net.Proxy.Type.HTTP;

public class MainActivity extends AppCompatActivity {

    //定义需要获取的内容来源地址
    private static final String SERVER_URL =
            "http://www.webservicex.net/WeatherForecast.asmx/GetWeatherByPlaceName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        HttpPost request = new HttpPost(SERVER_URL); //根据内容来源地址创建一个Http请求
        // 添加一个变量
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        // 设置一个地区名称
        params.add(new BasicNameValuePair("PlaceName", "NewYork"));  //添加必须的参数

        try {
            //设置参数的编码
            request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
            //发送请求并获取反馈
            HttpResponse httpResponse = new DefaultHttpClient().execute(request);

            // 解析返回的内容
            if(httpResponse.getStatusLine().getStatusCode() != 404){
                String result = EntityUtils.toString(httpResponse.getEntity());
                System.out.println(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
