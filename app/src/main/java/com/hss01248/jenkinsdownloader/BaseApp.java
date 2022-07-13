package com.hss01248.jenkinsdownloader;

import androidx.multidex.MultiDexApplication;

import com.hss01248.jenkins.JenkinsTool;


/**
 * @Despciption todo
 * @Author hss
 * @Date 02/12/2021 16:32
 * @Version 1.0
 */
public class BaseApp extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        JenkinsTool.init("http://10.0.20.6:8080/job/AKULAKU_ANDROID/api/json","huangss",  "1102a6a1f528e0211de0b2cfe4a8585953");
    }
}
