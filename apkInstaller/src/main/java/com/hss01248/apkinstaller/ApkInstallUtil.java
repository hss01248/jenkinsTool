package com.hss01248.apkinstaller;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.hss01248.activityresult.ActivityResultListener;
import com.hss01248.activityresult.StartActivityUtil;
import com.hss01248.openuri.OpenUri;

import java.io.File;

/**
 * @Despciption todo
 * @Author hss
 * @Date 13/07/2022 17:21
 * @Version 1.0
 */
public class ApkInstallUtil {

    /*
     *
     * 判断是否是8.0,8.0需要处理未知应用来源权限问题,否则直接安装
     */
    public static void checkAndInstallApk(final FragmentActivity activity, final File file) {
        LogUtils.w(file.getAbsolutePath());
        if (Build.VERSION.SDK_INT >= 26) {
            boolean b = activity.getPackageManager().canRequestPackageInstalls();
            if (b) {
                installApk(activity, file);
            } else {
                ToastUtils.showLong("请打开本app的安装apk的权限");
                //  引导用户手动开启安装权限
                Uri packageURI = Uri.parse("package:" + AppUtils.getAppPackageName());//设置这个才能
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,packageURI);
                //startActivityForResult(intent, 235);
                StartActivityUtil.goOutAppForResult(activity, intent, new ActivityResultListener() {
                    @Override
                    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
                        installApk(activity, file);
                    }

                    @Override
                    public void onActivityNotFound(Throwable e) {
                        e.printStackTrace();
                    }
                });

                /*PFragment pFragment = new PFragment();
                activity.getSupportFragmentManager().beginTransaction().add(pFragment, UUID.randomUUID().toString()).commitNowAllowingStateLoss();
                pFragment.askInstallPermission(activity, new Runnable() {
                    @Override
                    public void run() {
                        installApk(activity, file);
                    }
                });*/
            }
        } else {
            installApk(activity, file);
        }
    }

    private static void installApk(FragmentActivity activity, File file) {

        try {
            Uri uri = OpenUri.fromFile(activity, file);
            LogUtils.w(uri.toString());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        }catch (Throwable throwable){
            throwable.printStackTrace();
            ToastUtils.showLong(throwable.getMessage());
        }

    }
}
