# CameraHelper
easy to use camera

依赖相关


总工程添加  maven { url 'https://jitpack.io' }


主模块添加  implementation 'com.github.Liaohailong190:CameraHelper:v1.0'


Step 1: 添加权限 AndroidManifest.xml 和设置主题


    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <item name="windowActionBar">false</item>
    <item name="android:windowNoTitle">true</item>
    <item name="windowNoTitle">true</item>
    <item name="android:windowFullscreen">true</item>
   
Step 2:Activity->onCreate方法中初始化CameraHelper和回调接口


    //相机操作类
    private CameraHelper mCameraHelper;
    //相机操作数据回调
    private CameraOptCallback mCameraOptCallback = new CameraOptCallbackAdapter() {
        @Override
        public void onPictureComplete(String path, Bitmap bitmap) {
            if (mImageView != null) {
                mImageView.setImageBitmap(bitmap);
            }
            Toast.makeText(MainActivity.this, "图片保存路径 = " + path, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onVideoRecordComplete(String path) {
            Toast.makeText(MainActivity.this, "录像保存路径 = " + path, Toast.LENGTH_LONG).show();
        }
    };
    mCameraHelper = new CameraHelper.Builder()
                        .setActivity(this)
                        .setSurfaceView(mSurfaceView)
                        .setAutoFocus(true)//默认开启，3秒一次对焦
                        .setCameraOptCallback(mCameraOptCallback)//相机操作回调
                        .setDirectoryPath(mSavePath)//缓存路径
                        .setFlashEnable(false)//是否开启闪光灯拍照
                        .setScaleEnable(true)//是否支持手势缩放
                        .build();
                    
Step 3:设置Activity生命周期相关回调


    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraHelper != null) {
            mCameraHelper.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraHelper != null) {
            mCameraHelper.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraHelper != null) {
            mCameraHelper.onDestroy();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //激活摄像头
        if (CameraUtil.isCameraPermissionGranted(requestCode, grantResults)) {
            if (mCameraHelper != null) {
                mCameraHelper.onStart();
            }
        }
    }
    
Step 4:调用接口


        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.surface_view://手动对焦
                    if (mCameraHelper != null) {
                        mCameraHelper.focus();
                    }
                    break;
                case R.id.avatar_img://重置预览图
                    if (mImageView != null) {
                        mImageView.setImageBitmap(null);
                    }
                    break;
                case R.id.switch_btn://切换前后置摄像头
                    if (mCameraHelper != null) {
                        mCameraHelper.switchCamera();
                    }
                    break;
                case R.id.flash_btn://打开/关闭闪光灯
                    if (mCameraHelper != null) {
                        if (mCameraHelper.isFlashOn()) {
                            mCameraHelper.flashOff();
                        } else {
                            mCameraHelper.flashOn();
                        }
                    }
                    break;
                case R.id.shot_btn://拍照
                    if (mCameraHelper != null) {
                        mCameraHelper.takePicture();
                    }
                    break;
                case R.id.record_btn://录像
                    if (mCameraHelper != null) {
                        if (mCameraHelper.isRecording()) {
                            mCameraHelper.stopRecorder();
                        } else {
                            mCameraHelper.startRecorder();
                        }
                    }
                    break;
            }
        }
    
