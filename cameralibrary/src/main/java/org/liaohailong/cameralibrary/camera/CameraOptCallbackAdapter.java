package org.liaohailong.cameralibrary.camera;

import android.graphics.Bitmap;

/**
 * Describe as : {@link CameraOptCallback}接口适配器
 * Created by LHL on 2018/4/7.
 */

public abstract class CameraOptCallbackAdapter implements CameraOptCallback {
    @Override
    public void onPictureComplete(String path, Bitmap bitmap) {

    }

    @Override
    public void onVideoRecordComplete(String path) {

    }
}
