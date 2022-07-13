package com.hss01248.jenkins;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import androidx.fragment.app.FragmentActivity;


import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.EncodeUtils;
import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.blankj.utilcode.util.Utils;
import com.hss01248.apkinstaller.ApkInstallUtil;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;


import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * by hss
 * data:2020/6/12
 * desc:
 */
public class JenkinsTool {

   static String url = "";


    public static void init(String url,String userId,String apiToken){
        JenkinsTool.url = url;
        byte[] bytes = (userId + ":" + apiToken).getBytes(Charset.forName("UTF-8"));
        auth = "Basic " + EncodeUtils.base64Encode2String(bytes);
    }


    public static void showBuildList(FragmentActivity activity) {

        showBuildList(activity, url);
    }

    static void dismiss(final Dialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                }
            });
        }
    }

    //@RxLogObservable
    static Observable<String> url(String url) {
        return Observable.just(url);
    }

    //@RxLogObservable
    static Observable<BuildInfo> request2(BuildInfo info) {
        return Observable.just(info);
    }

    static String auth;
    static OkHttpClient client;

     static void showBuildList(final FragmentActivity activity, final String url) {
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setMessage("拉取最新打包信息中...");
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        client = new OkHttpClient.Builder().build();


        final List<BuildInfo>[] infos = new List[]{null};

        final long start = System.currentTimeMillis();
        url(url)
                .subscribeOn(Schedulers.io())
                .map(new Function<String, Response>() {
                    @Override
                    public Response apply(String s) throws Exception {
                        Request.Builder builder = new Request.Builder().url(url);
                        builder.addHeader("Authorization", auth);
                        return client.newCall(builder.build()).execute();
                    }
                })
                .map(new Function<Response, List<BuildInfo>>() {
                    @Override
                    public List<BuildInfo> apply(Response response) throws Exception {
                        String json = response.body().string();

                        JSONObject jsonObject = new JSONObject(json);
                        JSONArray arr = jsonObject.getJSONArray("builds");
                        infos[0] = new ArrayList<>();
                        if (arr != null && arr.length() > 0) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                BuildInfo info = new BuildInfo();
                                info.number = o.optLong("number");
                                info.url = o.optString("url") + "api/json";
                                infos[0].add(info);
                            }
                        }
                        LogUtils.i(infos[0]);
                        return infos[0];
                    }
                })
                .flatMapIterable(new Function<List<BuildInfo>, Iterable<BuildInfo>>() {
                    @Override
                    public Iterable<BuildInfo> apply(List<BuildInfo> buildInfos) throws Exception {
                        return buildInfos;
                    }
                })
                //.observeOn(Schedulers.io())
                .flatMap(new Function<BuildInfo, ObservableSource<BuildInfo>>() {
                    @Override
                    public ObservableSource<BuildInfo> apply(BuildInfo buildInfo) throws Exception {
                        return request2(buildInfo)
                                .subscribeOn(Schedulers.io())
                                .map(new Function<BuildInfo, BuildInfo>() {
                                    @Override
                                    public BuildInfo apply(BuildInfo buildInfo) throws Exception {
                                        Request.Builder builder = new Request.Builder().url(buildInfo.url);
                                        builder.addHeader("Authorization", auth);
                                        Response response1 = client.newCall(builder.build()).execute();

                                        String json = response1.body().string();
                                        //LogUtils.json(json);
                                        JSONObject object = new JSONObject(json);
                                        buildInfo.desc = object.optString("description");
                                        buildInfo.timestamp = object.optLong("timestamp");
                                        buildInfo.result = object.optString("result");
                                        buildInfo.building = object.optBoolean("building");
                                        if (!TextUtils.isEmpty(buildInfo.result)) {
                                            buildInfo.result = buildInfo.result.toLowerCase();
                                        }
                                        if (buildInfo.desc.contains("<br />")) {
                                            buildInfo.desc = buildInfo.desc.substring(0, buildInfo.desc.indexOf("<br />"));
                                        }
                                        try {

                                            parseArtifacts(buildInfo,object.getJSONArray("artifacts"),object.getString("url") + "artifact/");


                                            /*JSONObject jsonObject = getRightArtifactInfo(object.getJSONArray("artifacts"));
                                            buildInfo.downloadPath = object.getString("url") + "artifact/" + jsonObject.optString("relativePath");
                                            //当为aab,或者多个输出时,路径和名字会不对,要遍历查找
                                            String fileName = URLUtil.guessFileName(buildInfo.downloadPath, "", "");
                                            buildInfo.fileName = fileName;*/
                                        } catch (Throwable throwable) {
                                            throwable.printStackTrace();
                                        }
                                        return buildInfo;
                                    }
                                }).onErrorResumeNext(new ObservableSource<BuildInfo>() {
                                    @Override
                                    public void subscribe(Observer<? super BuildInfo> observer) {

                                    }
                                });
                    }
                }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableObserver<BuildInfo>() {
                    @Override
                    public void onNext(BuildInfo buildInfo) {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        end(start);
                        dismiss(dialog);
                        ToastUtils.showLong(e.getClass().getSimpleName() + " ; " + e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        end(start);
                        dismiss(dialog);
                        //LogUtils.obj(infos[0]);
                        showInfos(infos[0], activity);
                    }
                });


    }

    private static void parseArtifacts(BuildInfo buildInfo, JSONArray artifacts,String urlPreffix) {
        if(artifacts == null || artifacts.length() ==0){
            return ;
        }
        for (int i = 0; i < artifacts.length(); i++) {
            try {
                JSONObject jsonObject = artifacts.getJSONObject(i);
                String relativePath = jsonObject.optString("relativePath");
                LogUtils.d("artifacts.relativePath",relativePath);
                if(!relativePath.endsWith(".apk")){
                    continue;
                }
                String path = urlPreffix + relativePath;
                buildInfo.downloadPaths.add(path);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }

        }
    }


    private static void end(long start) {
        LogUtils.w("cost ms:"+(System.currentTimeMillis() - start));
    }

    private static void showInfos(final List<BuildInfo> infos, final FragmentActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        CharSequence[] items = getItems(infos);
        AlertDialog alertDialog = builder.setTitle("选择一个,点击即可下载安装")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BuildInfo info = infos.get(which);
                        if (info.downloadPaths == null || info.downloadPaths.isEmpty()) {
                            ToastUtils.showLong("正在打包中或打包被取消,或者打包产物没有apk:"+info.result);
                            return;
                        }
                        if (info.downloadPaths.size() == 1) {
                            download(info.downloadPaths.get(0), activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), activity);
                            return;
                        }
                        pickOneToDownload(info, activity);

                    }
                }).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();

    }

    private static void pickOneToDownload(BuildInfo info, FragmentActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        CharSequence[] items = getNames(info);
        AlertDialog alertDialog = builder.setTitle("打包有多个apk,请选择一个下载安装\naab加固选择: sec...arm64_v8a.apk")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        download(info.downloadPaths.get(which), activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), activity);

                    }
                }).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
    }

    private static CharSequence[] getNames(BuildInfo info) {
        CharSequence[] names = new String[info.downloadPaths.size()];
        for (int i = 0; i < info.downloadPaths.size(); i++) {
            names[i] = URLUtil.guessFileName(info.downloadPaths.get(i), "", "");
        }
        return names;
    }

    private static void download(String path, final String dir, final FragmentActivity activity) {
        LogUtils.i(path);
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setMessage("apk包下载中...");
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Observable.just(path)
                .subscribeOn(Schedulers.io())
                .map(new Function<String, File>() {
                    @Override
                    public File apply(String downloadPath) throws Exception {
                        //clearDir(dir);
                        Request.Builder builder = new Request.Builder().url(downloadPath);
                        builder.addHeader("Authorization", auth);
                        Response response = client.newCall(builder.build()).execute();
                        String length = response.header("Content-Length");
                        long len = 0;
                        LogUtils.w("length:"+length);
                        if(TextUtils.isDigitsOnly(length)){
                            len = Long.parseLong(length);
                        }
                        String fileName = URLUtil.guessFileName(downloadPath, "", "");
                        File path = new File(dir, fileName);
                        long fileLength = path.length();
                        boolean exists = path.exists();
                        if(exists && fileLength == len && len> 0){
                            LogUtils.i("之前已经下载过了:", path);
                            ToastUtils.showShort("之前已经下载过了,立刻安装");
                            return path;
                        }
                        final long finalLen = len;
                        boolean success = writeFileFromIS(path, response.body().byteStream(), false, new OnProgressUpdateListener() {
                            @Override
                            public void onProgressUpdate(final long progress) {
                                if(finalLen == 0){
                                    return;
                                }
                                dialog.getWindow().getDecorView().post(new Runnable() {
                                    @Override
                                    public void run() {
                                     String lenghts =    ConvertUtils.byte2FitMemorySize(finalLen,2);
                                        String read =    ConvertUtils.byte2FitMemorySize(progress,2);
                                        String percent  = (progress * 100/finalLen) +"%";
                                        dialog.setMessage(percent + " , "+ read + "/" + lenghts);
                                    }
                                });
                            }
                        });
                        if (success) {
                            return path;
                        }
                        throw new IOException("文件保存失败");
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableObserver<File>() {
                    @Override
                    public void onNext(File file) {
                        dismiss(dialog);
                        checkAndInstall(file, activity);

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        dismiss(dialog);

                    }

                    @Override
                    public void onComplete() {

                    }
                });

    }
    static int sBufferSize = 524288;
     static boolean writeFileFromIS(final File file,
                                          final InputStream is,
                                          final boolean append,
                                          final OnProgressUpdateListener listener) {
        if (is == null || !FileUtils.createOrExistsFile(file)) {
            Log.e("FileIOUtils", "create file <" + file + "> failed.");
            return false;
        }
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file, append), sBufferSize);
            if (listener == null) {
                byte[] data = new byte[sBufferSize];
                for (int len; (len = is.read(data)) != -1; ) {
                    os.write(data, 0, len);
                }
            } else {
                //double totalSize = is.available();
                int curSize = 0;
                listener.onProgressUpdate(0);
                byte[] data = new byte[sBufferSize];
                for (int len; (len = is.read(data)) != -1; ) {
                    os.write(data, 0, len);
                    curSize += len;
                    listener.onProgressUpdate(curSize );
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public interface OnProgressUpdateListener {
        void onProgressUpdate(long progress);
    }





    public static void clearDownloadDir() {
        String dir = Utils.getApp().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        File di = new File(dir);
        if (!di.exists()) {
            return;
        }
        File[] files = di.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.getName().contains(".apk")) {
                file.delete();
            }
        }
    }

    private static void checkAndInstall(File file, FragmentActivity activity) {
        ApkInstallUtil.checkAndInstallApk(activity, file);
    }




    private static CharSequence[] getItems(List<BuildInfo> infos) {
        CharSequence[] strings = new CharSequence[infos.size()];

        //5778-common-origin/feature/fromRmPhoneV1-anonymous
        for (int i = 0; i < infos.size(); i++) {
            String desc = infos.get(i).desc;
            if (!TextUtils.isEmpty(desc) && desc.contains("-")) {
                desc = desc.replaceFirst("-origin/", "\n分支: ");
                int idx = desc.lastIndexOf("-");
                desc = desc.substring(0, idx) + "\n打包人: " + desc.substring(idx + 1);
            }
            desc = (i + 1) + ". 打包类型:" + desc + "\n";
            desc = desc + "版本: " + getVersionName(infos.get(i).fileName) + "\n";
            desc = desc + "time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(infos.get(i).timestamp) + "\n";
            if(infos.get(i).building){
                desc = desc + "打包结果: " + "正在打包中..." + "\n";
            }else {
                desc = desc + "打包结果: " + infos.get(i).result + "\n";
            }


            strings[i] = desc;
        }
        return strings;
    }

    private static String getVersionName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        try {
            if (fileName.contains("_")) {
                return fileName.split("_")[1];
            }
            return fileName;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return fileName;
        }

    }


    public static class BuildInfo {
        public long number;
        public String url;
        String desc;
        String downloadPath;
        String fileName;
        long timestamp;
        String result;
        boolean building;

        List<String> downloadPaths = new ArrayList<>();
    }
}
