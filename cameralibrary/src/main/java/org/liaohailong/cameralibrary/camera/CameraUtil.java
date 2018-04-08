package org.liaohailong.cameralibrary.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Describe as : 图片处理工具
 * Created by LHL on 2018/4/7.
 */

public final class CameraUtil {
    private static final String TAG = "CameraUtil";

    private CameraUtil() throws IllegalAccessException {
        throw new IllegalAccessException("no instance!");
    }

    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    static void savePic(
            final int faceType,
            String directoryPath,
            final byte[] data,
            final Bitmap.CompressFormat format,
            final CameraOptCallback cameraOptCallback) {
        File saveFile = createDirectoryIfNotExist(directoryPath);
        if (saveFile == null) {
            return;
        }
        String suffix = "";
        switch (format) {
            case PNG:
                suffix = ".png";
                break;
            case JPEG:
                suffix = ".jpeg";
                break;
            case WEBP:
                suffix = ".webp";
                break;
        }
        final String path = saveFile.getAbsolutePath() + "/" + getDateFormatStr() + suffix;
        sExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                final Bitmap result;
                if (faceType == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = mirror(rotate(rawBitmap, 270f));
                } else {
                    result = rotate(rawBitmap, 90f);
                }
                File file = new File(path);
                FileOutputStream fileOutputStream = null;
                BufferedOutputStream bufferedOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(file);
                    bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                    if (result.compress(format, 100, bufferedOutputStream)) {
                        sHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (cameraOptCallback != null) {
                                    cameraOptCallback.onPictureComplete(path, result);
                                }
                            }
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    closeStream(fileOutputStream);
                    closeStream(bufferedOutputStream);
                }
            }
        });
    }

    private static File createDirectoryIfNotExist(String savePath) {
        File file = new File(savePath);
        if (!file.exists()) {
            createDirectory(file);
        }
        if (file.isFile()) {
            boolean delete = file.delete();
            if (delete) {
                createDirectory(file);
            }
        }
        return file;
    }

    private static void createDirectory(File file) {
        boolean mkdirs = file.mkdirs();
        Log.i(TAG, "createDirectoryIfNotExist ---> mkdirs = " + mkdirs
                + "  savePath = " + file.getAbsolutePath());
    }


    private static Bitmap mirror(Bitmap rawBitmap) {
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1f);
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), matrix, true);
    }

    private static Bitmap rotate(Bitmap rawBitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), matrix, true);
    }

    private static void closeStream(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static String getDateFormatStr() {
        Date date = new Date();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd#HH:mm:ss");
        return simpleDateFormat.format(date);
    }

    static boolean requestCameraPermissionIfNeed(Activity activity) {
        return requestPermissionIfNeed(
                activity,
                new String[]{Manifest.permission.CAMERA},
                "",
                CameraHelper.REQUEST_CAMERA_PERMISSION_CODE);
    }

    static boolean requestWriteStoragePermissionIfNeed(Activity activity) {
        return requestPermissionIfNeed(
                activity,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                "",
                CameraHelper.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
    }

    static boolean requestRecordAudioPermissionIfNeed(Activity activity) {
        return requestPermissionIfNeed(activity,
                new String[]{Manifest.permission.RECORD_AUDIO},
                "",
                CameraHelper.REQUEST_RECORD_AUDIO_PERMISSION_CODE);
    }

    public static boolean isCameraPermissionGranted(int requestCode, @NonNull int[] grantResults) {
        return requestCode == CameraHelper.REQUEST_CAMERA_PERMISSION_CODE && checkPermissionResultIfGranted(grantResults);
    }

    public static boolean isWriteStoragePermissionGranted(int requestCode, @NonNull int[] grantResults) {
        return requestCode == CameraHelper.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE && checkPermissionResultIfGranted(grantResults);
    }

    public static boolean isRecordAudioPermissionGranted(int requestCode, @NonNull int[] grantResults) {
        return requestCode == CameraHelper.REQUEST_RECORD_AUDIO_PERMISSION_CODE && checkPermissionResultIfGranted(grantResults);
    }

    private static boolean checkPermissionResultIfGranted(@NonNull int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 如果需要请求权限，则弹框提示，并作出需要权限的解释（以toast展示）
     *
     * @param activity    当前界面
     * @param permissions 权限
     * @param explanation 解释
     * @param requestCode 触发requestPermissions后，在onRequestPermissionsResult里用到（区分是在请求哪组权限）
     * @return 如果为true，表示已经获取了这些权限；false表示还未获取，并正在执行请求
     */
    private static boolean requestPermissionIfNeed(Activity activity,
                                                   String[] permissions, CharSequence explanation, int requestCode) {
        ArrayList<String> permissionList = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }

        if (permissionList.isEmpty()) {
            return true;
        }

        String[] needRequestPermissions = permissionList.toArray(new String[permissionList.size()]);

        for (String permission : needRequestPermissions) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                if (!TextUtils.isEmpty(explanation)) {
                    Toast.makeText(activity, explanation, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }

        ActivityCompat.requestPermissions(activity,
                needRequestPermissions,
                requestCode);
        return false;
    }
}
