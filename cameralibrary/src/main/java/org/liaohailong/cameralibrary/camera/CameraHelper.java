package org.liaohailong.cameralibrary.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Describe as : 相机开启协助类
 * <p>
 * 相机宽高与界面宽高关系↓
 * <p>
 * displayWidth
 * //////////////////////
 * /                    /
 * /                    /
 * /                    /
 * /                    /
 * /                    /
 * /       portrait     /  displayHeight
 * /                    /
 * /                    /
 * /                    /
 * /                    /
 * //////////////////////
 * <p>
 * displayWidth
 * ////////////////////////////////
 * /                              /
 * /                              /
 * /          landscape           /  displayHeight
 * /                              /
 * /                              /
 * ////////////////////////////////
 * <p>
 * 预览界面的Activity，无需在AndroidManifest.xml中设置screenOrientation.
 * Created by LHL on 2018/4/7.
 */

public final class CameraHelper {
    private static final String TAG = "CameraHelper";
    //权限请求相关
    static final int REQUEST_CAMERA_PERMISSION_CODE = 0x001;
    static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 0x002;
    static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 0x003;

    //相机配置相关
    private Camera mCamera;
    private Camera.Parameters mParameters;

    //基本信息相关
    private WeakReference<Activity> mActivityWeak;
    private Camera.PreviewCallback mPreviewCallback;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private int mFaceType = Camera.CameraInfo.CAMERA_FACING_BACK;
    private String directoryPath = "";//保存路径
    private int displayWidth = 1920;
    private int displayHeight = 1080;
    private Bitmap.CompressFormat mCompressFormat = Bitmap.CompressFormat.PNG;
    private CameraOptCallback mCameraOptCallback;
    private boolean isAutoFocus = true;
    private Runnable mAutoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoFocus) {
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success && mCamera != null) {
                            mCamera.cancelAutoFocus();
                            startAutoFocus();
                        }
                    }
                });
            }
        }
    };

    //视频录制相关
    private boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    private String mRecordFilePath;

    //日志输入相关
    private boolean logEnable = true;

    private CameraHelper(Activity activity, SurfaceView surfaceView) {
        mActivityWeak = new WeakReference<>(activity);
        mSurfaceView = surfaceView;
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceHolder = holder;
                if (CameraUtil.requestCameraPermissionIfNeed(mActivityWeak.get()))
                    onStart();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceHolder = null;
                onStop();
            }
        });
    }

    private void log(String msg) {
        if (logEnable) {
            Log.i(TAG, msg);
        }
    }

    /**
     * {@link Activity#onStart()}方法调用
     */
    public void onStart() {
        if (mSurfaceHolder == null) {
            return;
        }
        if (mCamera == null) {
            if (openCamera(mFaceType)) {
                startPreview();
            }
        } else {
            startPreview();
        }
    }

    /**
     * {@link Activity#onStop()}方法调用
     */
    public void onStop() {
        releaseRecorder();
        releaseCamera();
    }

    /**
     * {@link Activity#onDestroy()}方法调用
     */
    public void onDestroy() {
        releaseCamera();
        mActivityWeak = null;
        mPreviewCallback = null;
        mSurfaceView = null;
        mSurfaceHolder = null;
        mCameraOptCallback = null;
    }

    /**
     * 切换前后摄像头
     */
    public void switchCamera() {
        mFaceType = mFaceType == Camera.CameraInfo.CAMERA_FACING_BACK
                ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        onStop();
        onStart();
    }

    /**
     * 拍照
     */
    public void takePicture() {
        if (mCamera != null && CameraUtil.requestWriteStoragePermissionIfNeed(mActivityWeak.get())) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    if (mCamera != null) {
                        mCamera.startPreview();
                    }
                    CameraUtil.savePic(mFaceType, directoryPath, data, mCompressFormat, mCameraOptCallback);
                }
            });
        }
    }

    /**
     * 手动聚焦
     */
    public void focus() {
        stopAutoFocus();
        mAutoFocusRunnable.run();
        startAutoFocus();
    }

    /**
     * @return 当前是否正在录制
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * @return 开启屏幕录制
     */
    public boolean startRecorder() {
        if (mCamera == null) {
            return false;
        }
        if (mSurfaceHolder == null) {
            return false;
        }
        if (!CameraUtil.requestWriteStoragePermissionIfNeed(mActivityWeak.get())) {
            return false;
        }
        if (!CameraUtil.requestRecordAudioPermissionIfNeed(mActivityWeak.get())) {
            return false;
        }
        //Step 1 :Unlock and set camera to MediaRecorder
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();//必须解锁
        mMediaRecorder.setCamera(mCamera);

        //Step 2 :Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        //Step 3 :Set a CamcorderProfile(requires API Level 8 or higher)
        //PS:此处需要注意~~~profile中的videoFrameWidth和videoFrameHeight如果超出预览视图的宽高，就会录制失败!
        //PS:此处需要注意~~~profile中的videoFrameWidth和videoFrameHeight如果超出预览视图的宽高，就会录制失败!
        //PS:此处需要注意~~~profile中的videoFrameWidth和videoFrameHeight如果超出预览视图的宽高，就会录制失败!
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        int targetWidth = mSurfaceView.getHeight();
        int targetHeight = mSurfaceView.getWidth();
        Camera.Size bestSize = getBestSize(targetWidth, targetHeight, mParameters.getSupportedPreviewSizes());
        profile.videoFrameWidth = bestSize.width;
        profile.videoFrameHeight = bestSize.height;
        mMediaRecorder.setProfile(profile);//此质量直接影响录制文件的总大小
        mMediaRecorder.setOrientationHint(mFaceType == Camera.CameraInfo.CAMERA_FACING_FRONT ? 180 : 0);//反录制镜像！！！

        //Step 4 :Set output file
        String suffix = "";
        switch (profile.fileFormat) {
            case MediaRecorder.OutputFormat.THREE_GPP:
                suffix = ".3gp";
                break;
            case MediaRecorder.OutputFormat.MPEG_4:
                suffix = ".mp4";
                break;
        }
        mRecordFilePath = directoryPath + "/" + CameraUtil.getDateFormatStr() + suffix;
        mMediaRecorder.setOutputFile(mRecordFilePath);

        //Step 5 :Set the preview output
        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        //Step 6 :Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            releaseRecorder();
            return false;
        }
        return true;
    }

    /**
     * 停止录制
     */
    public void stopRecorder() {
        releaseRecorder();
        if (mCameraOptCallback != null) {
            mCameraOptCallback.onVideoRecordComplete(mRecordFilePath);
        }
    }

    /**
     * 释放录制器
     */
    private void releaseRecorder() {
        if (mMediaRecorder == null) {
            return;
        }
        if (mCamera == null) {
            return;
        }
        if (mSurfaceHolder == null) {
            return;
        }
        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            mMediaRecorder = null;
            isRecording = false;
            mCamera.lock();
        }
    }

    /**
     * 开启相机
     *
     * @param faceType 前置/后置
     * @return 是否开启成功
     */
    private boolean openCamera(int faceType) {
        boolean isSupport = supportCameraFacing(faceType);
        if (isSupport) {
            try {
                mCamera = Camera.open(faceType);
                initParameters(mCamera);
                mCamera.setPreviewCallback(mPreviewCallback);
                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 判断是否支持某个相机
     *
     * @param faceType 需要支持的相机类型
     * @return true表示支持
     */
    private boolean supportCameraFacing(int faceType) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == faceType) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param camera 配置相机基本参数
     */
    private void initParameters(Camera camera) {
        try {
            mParameters = camera.getParameters();

            //获取与指定狂傲相等或最接近的尺寸
            //设置预览尺寸
            int targetWidth = mSurfaceView.getHeight();
            int targetHeight = mSurfaceView.getWidth();
            Camera.Size bestSize = getBestSize(targetWidth, targetHeight, mParameters.getSupportedPreviewSizes());
            if (bestSize != null) {
                mParameters.setPreviewSize(bestSize.width, bestSize.height);
            }
            //设置保存图片
            targetWidth = displayWidth;
            targetHeight = displayHeight;
            Camera.Size bestPictureSize = getBestSize(targetWidth, targetHeight, mParameters.getSupportedPictureSizes());
            mParameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);
            //对焦模式
            if (isSupportFocus(Camera.Parameters.FOCUS_MODE_AUTO)) {
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            camera.setParameters(mParameters);
        } catch (Exception ex) {
            ex.printStackTrace();
            toast("相机初始化失败！！！Exception = " + ex.toString());
        }
    }

    private void toast(String msg) {
        if (mActivityWeak != null && mActivityWeak.get() != null) {
            Toast.makeText(mActivityWeak.get(), msg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 获取最佳尺寸
     *
     * @param targetWidth  目标宽
     * @param targetHeight 目标高
     * @param sizeList     相机自身提供的尺寸集合
     * @return 最佳展示尺寸
     */
    private Camera.Size getBestSize(int targetWidth, int targetHeight, List<Camera.Size> sizeList) {
        Camera.Size bestSize = null;
        //目标大小的宽高比
        float targetRatio = targetWidth * 1.0f / targetHeight;
        float minDiff = targetRatio;

        for (Camera.Size size : sizeList) {
            float supportRatio = size.width * 1.0f / size.height;
            log("系统支持的尺寸  = " + supportRatio);
        }

        for (Camera.Size size : sizeList) {
            if (size.width == targetWidth && size.height == targetHeight) {
                bestSize = size;
                break;
            }
            float supportRatio = size.width * 1.0f / size.height;
            float tempDiff = Math.abs(supportRatio - targetRatio);
            if (tempDiff < minDiff) {
                minDiff = tempDiff;
                bestSize = size;
            }
        }

        if (bestSize != null) {
            log("目标尺寸   targetWidth = " + targetWidth
                    + "  targetHeight = " + targetHeight
                    + " ---> targetRatio = " + targetRatio);
            log("最优尺寸   bestSize.width =  " + bestSize.width
                    + " bestSize.height = " + bestSize.height
                    + " ---> supportRatio = " + bestSize.width * 1.0f / bestSize.height);
        }
        return bestSize;
    }

    /**
     * @param focusMode 聚焦模式
     * @return 是否支持
     */
    private boolean isSupportFocus(String focusMode) {
        List<String> supportedFocusModes = mParameters.getSupportedFocusModes();
        for (String supportedFocusMode : supportedFocusModes) {
            if (TextUtils.equals(focusMode, supportedFocusMode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 设置相机旋转角度
     */
    private void setCameraDisplayOrientation() {
        Activity activity = mActivityWeak.get();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mFaceType, cameraInfo);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        int screenDegree = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                screenDegree = 0;
                break;
            case Surface.ROTATION_90:
                screenDegree = 90;
                break;
            case Surface.ROTATION_180:
                screenDegree = 180;
                break;
            case Surface.ROTATION_270:
                screenDegree = 270;
                break;
        }
        int displayOrientation;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (cameraInfo.orientation + screenDegree) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - screenDegree + 360) % 360;
        }
        if (mCamera != null) {
            mCamera.setDisplayOrientation(displayOrientation);
        }
        log("屏幕的旋转角度 = " + rotation);
        log("setDisplayOrientation ---> " + displayOrientation);
    }

    /**
     * 开启界面预览
     */
    private void startPreview() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                setCameraDisplayOrientation();
                mCamera.startPreview();
                //自动聚焦
                startAutoFocus();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开始自动聚焦
     */
    private void startAutoFocus() {
        if (isAutoFocus) {
            mSurfaceView.removeCallbacks(mAutoFocusRunnable);
            mSurfaceView.postDelayed(mAutoFocusRunnable, 3000);
        }
    }

    /**
     * 停止自动聚焦
     */
    private void stopAutoFocus() {
        if (mCamera != null) {
            mCamera.cancelAutoFocus();
        }
        mSurfaceView.removeCallbacks(mAutoFocusRunnable);
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    public final static class Builder {
        private WeakReference<Activity> activity;
        private Camera.PreviewCallback previewCallback;
        private SurfaceView surfaceView;
        private int faceType = Camera.CameraInfo.CAMERA_FACING_BACK;
        private String directoryPath = "";//保存路径
        private Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.PNG;
        private CameraOptCallback cameraOptCallback;
        private boolean isAutoFocus = true;
        private boolean logEnable = true;

        public final Builder setActivity(Activity activity) {
            this.activity = new WeakReference<>(activity);
            return this;
        }

        public final Builder setPreviewCallback(Camera.PreviewCallback previewCallback) {
            this.previewCallback = previewCallback;
            return this;
        }

        public final Builder setSurfaceView(SurfaceView surfaceView) {
            this.surfaceView = surfaceView;
            return this;
        }

        public final Builder setFaceType(int faceType) {
            this.faceType = faceType;
            return this;
        }

        public final Builder setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
            return this;
        }

        public final Builder setCompressFormat(Bitmap.CompressFormat compressFormat) {
            this.compressFormat = compressFormat;
            return this;
        }

        public final Builder setCameraOptCallback(CameraOptCallback cameraOptCallback) {
            this.cameraOptCallback = cameraOptCallback;
            return this;
        }

        public final Builder setAutoFocus(boolean autoFocus) {
            isAutoFocus = autoFocus;
            return this;
        }

        public final Builder setLogEnable(boolean logEnable) {
            this.logEnable = logEnable;
            return this;
        }

        public CameraHelper build() {
            if (activity == null) {
                throw new IllegalArgumentException("activity can not be empty");
            }
            if (surfaceView == null) {
                throw new IllegalArgumentException("surfaceView can not be empty");
            }
            Activity activity = this.activity.get();
            Point point = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(point);
            CameraHelper cameraHelper = new CameraHelper(activity, surfaceView);
            cameraHelper.mPreviewCallback = previewCallback;
            cameraHelper.mFaceType = faceType;
            cameraHelper.directoryPath = directoryPath;
            cameraHelper.displayWidth = point.y;
            cameraHelper.displayHeight = point.x;
            cameraHelper.mCompressFormat = compressFormat;
            cameraHelper.mCameraOptCallback = cameraOptCallback;
            cameraHelper.isAutoFocus = isAutoFocus;
            cameraHelper.logEnable = logEnable;
            return cameraHelper;
        }
    }
}
