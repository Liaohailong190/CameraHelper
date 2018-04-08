package org.liaohailong.cameralibrary.camera;

import android.graphics.Bitmap;

/**
 * Describe as : 相机拍照，录像回调
 * Created by LHL on 2018/4/7.
 */

public interface CameraOptCallback {

    /**
     * 拍照回调
     *
     * @param path   文件存储路径
     * @param bitmap 当前截图
     */
    void onPictureComplete(String path, Bitmap bitmap);

    /**
     * 录像回调
     *
     * @param path 录像文件存储地址
     */
    void onVideoRecordComplete(String path);
}
