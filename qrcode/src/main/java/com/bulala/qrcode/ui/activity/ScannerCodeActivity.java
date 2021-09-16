package com.bulala.qrcode.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bulala.qrcode.QRApplication;
import com.bulala.qrcode.R;
import com.bulala.qrcode.camera.CameraManager;
import com.bulala.qrcode.decode.DecodeThread;
import com.bulala.qrcode.decode.DecodeUtils;
import com.bulala.qrcode.handler.ScannerCodeActivityHandler;
import com.bulala.qrcode.utils.BeepManager;
import com.bulala.qrcode.utils.CommonUtils;
import com.bulala.qrcode.utils.InactivityTimer;
import com.nineoldandroids.view.ViewHelper;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Description 扫描二维码界面
 *
 * @author yg
 * @date 2021/8/27
 */
public class ScannerCodeActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener, RadioGroup.OnCheckedChangeListener {

    public static final int IMAGE_PICKER_REQUEST_CODE = 100;
    protected static String TAG_LOG = null;
    private Context mContext;

    private CameraManager cameraManager;
    private ScannerCodeActivityHandler handler;

    private boolean hasSurface;
    private boolean isLightOn;

    private InactivityTimer mInactivityTimer;
    private BeepManager mBeepManager;

    private int mQrcodeCropWidth = 0;
    private int mQrcodeCropHeight = 0;
    private int mBarcodeCropWidth = 0;
    private int mBarcodeCropHeight = 0;

    private ObjectAnimator mScanMaskObjectAnimator = null;

    private Rect cropRect;
    private int dataMode = DecodeUtils.DECODE_DATA_MODE_QRCODE;
    private SurfaceView capturePreview;
    private ImageView captureErrorMask;
    private ImageView captureScanMask;
    private RelativeLayout captureCropView;
    private ImageView capturePictureBtn;
    private ImageView captureLightBtn;
    private RadioGroup captureModeGroup;
    private RelativeLayout captureContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = this;
        setContentView(R.layout.activity_scanner_code);
        initPermission();
        initView();
    }

    private void initView() {
        capturePreview = findViewById(R.id.capture_preview);
        captureErrorMask = findViewById(R.id.capture_error_mask);
        captureScanMask = findViewById(R.id.capture_scan_mask);
        captureCropView = findViewById(R.id.capture_crop_view);
        capturePictureBtn = findViewById(R.id.capture_picture_btn);
        captureLightBtn = findViewById(R.id.capture_light_btn);
        captureModeGroup = findViewById(R.id.capture_mode_group);
        captureContainer = findViewById(R.id.capture_container);
        capturePictureBtn.setOnClickListener(this);
        captureLightBtn.setOnClickListener(this);
        captureModeGroup.setOnCheckedChangeListener(this);
        findViewById(R.id.top_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        hasSurface = false;
        mInactivityTimer = new InactivityTimer(this);
        mBeepManager = new BeepManager(this);

        initCropViewAnimator();
    }

    private void initCropViewAnimator() {
        mQrcodeCropWidth = getResources().getDimensionPixelSize(R.dimen.qrcode_crop_width);
        mQrcodeCropHeight = getResources().getDimensionPixelSize(R.dimen.qrcode_crop_height);

        mBarcodeCropWidth = getResources().getDimensionPixelSize(R.dimen.barcode_crop_width);
        mBarcodeCropHeight = getResources().getDimensionPixelSize(R.dimen.barcode_crop_height);
    }

    public void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        int[] location = new int[2];
        captureCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1];

        int cropWidth = captureCropView.getWidth();
        int cropHeight = captureCropView.getHeight();

        int containerWidth = captureContainer.getWidth();
        int containerHeight = captureContainer.getHeight();

        int x = cropLeft * cameraWidth / containerWidth;
        int y = cropTop * cameraHeight / containerHeight;

        int width = cropWidth * cameraWidth / containerWidth;
        int height = cropHeight * cameraHeight / containerHeight;

        setCropRect(new Rect(x, y, width + x, height + y));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        handler = null;

        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(capturePreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            capturePreview.getHolder().addCallback(this);
        }

        mInactivityTimer.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG_LOG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initCamera(holder);
    }

    /**
     * A valid barcode has been found, so give an indication of success and show the results.
     */
    public void handleDecode(String result, Bundle bundle) {
        mInactivityTimer.onActivity();
        mBeepManager.playBeepSoundAndVibrate();

        if (!CommonUtils.isEmpty(result) && CommonUtils.isUrl(result)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(result));
            startActivity(intent);
        } else {
            bundle.putString(ResultActivity.BUNDLE_KEY_SCAN_RESULT, result);
            QRApplication.OnResultListener onResultListener = QRApplication.getInstance().getOnResultListener();
            if (onResultListener == null) {
                Intent intent = new Intent(this, ResultActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            } else {
                onResultListener.onResultListener(bundle);
                finish();
            }
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG_LOG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new ScannerCodeActivityHandler(this, cameraManager);
            }

            onCameraPreviewSuccess();
        } catch (IOException ioe) {
            Log.w(TAG_LOG, ioe);
//            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG_LOG, "Unexpected error initializing camera", e);
//            displayFrameworkBugMessageAndExit();
        }
    }

    private void onCameraPreviewSuccess() {
        initCrop();
        captureErrorMask.setVisibility(View.GONE);

        ViewHelper.setPivotX(captureScanMask, 0.0f);
        ViewHelper.setPivotY(captureScanMask, 0.0f);

        mScanMaskObjectAnimator = ObjectAnimator.ofFloat(captureScanMask, "scaleY", 0.0f, 1.0f);
        mScanMaskObjectAnimator.setDuration(2000);
        mScanMaskObjectAnimator.setInterpolator(new DecelerateInterpolator());
        mScanMaskObjectAnimator.setRepeatCount(-1);
        mScanMaskObjectAnimator.setRepeatMode(ObjectAnimator.RESTART);
        mScanMaskObjectAnimator.start();
    }

    private void displayFrameworkBugMessageAndExit() {
        captureErrorMask.setVisibility(View.VISIBLE);
        new AlertDialog.Builder(this)
                .setTitle(R.string.restricted_permissions)
                .setMessage(R.string.tips_open_camera_error)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
            String imagePath = data.getStringExtra(CommonImagePickerDetailActivity
                    .KEY_BUNDLE_RESULT_IMAGE_PATH);

            if (!CommonUtils.isEmpty(imagePath)) {
                ImageLoader.getInstance().loadImage("file://" + imagePath, new ImageLoadingListener() {
                    @Override
                    public void onLoadingStarted(String imageUri, View view) {
                    }

                    @Override
                    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {

                    }

                    @Override
                    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                        String resultZxing = new DecodeUtils(DecodeUtils.DECODE_DATA_MODE_ALL)
                                .decodeWithZxing(loadedImage);
                        String resultZbar = new DecodeUtils(DecodeUtils.DECODE_DATA_MODE_ALL)
                                .decodeWithZbar(loadedImage);

                        if (!CommonUtils.isEmpty(resultZbar)) {
                            Bundle extras = new Bundle();
                            extras.putInt(DecodeThread.DECODE_MODE, DecodeUtils.DECODE_MODE_ZBAR);

                            handleDecode(resultZbar, extras);
                        } else if (!CommonUtils.isEmpty(resultZxing)) {
                            Bundle extras = new Bundle();
                            extras.putInt(DecodeThread.DECODE_MODE, DecodeUtils.DECODE_MODE_ZXING);

                            handleDecode(resultZxing, extras);
                        } else {
                            Toast.makeText(mContext, "无数据", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onLoadingCancelled(String imageUri, View view) {

                    }
                });
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.capture_picture_btn) {
            Intent intent = new Intent(mContext, CommonImagePickerListActivity.class);
            startActivityForResult(intent, IMAGE_PICKER_REQUEST_CODE);
        } else if (v.getId() == R.id.capture_light_btn) {
            if (isLightOn) {
                cameraManager.setTorch(false);
                captureLightBtn.setSelected(false);
            } else {
                cameraManager.setTorch(true);
                captureLightBtn.setSelected(true);
            }
            isLightOn = !isLightOn;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.capture_mode_barcode) {
            PropertyValuesHolder qr2barWidthVH = PropertyValuesHolder.ofFloat("width",
                    1.0f, (float) mBarcodeCropWidth / mQrcodeCropWidth);
            PropertyValuesHolder qr2barHeightVH = PropertyValuesHolder.ofFloat("height",
                    1.0f, (float) mBarcodeCropHeight / mQrcodeCropHeight);
            ValueAnimator valueAnimator = ValueAnimator.ofPropertyValuesHolder(qr2barWidthVH, qr2barHeightVH);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Float fractionW = (Float) animation.getAnimatedValue("width");
                    Float fractionH = (Float) animation.getAnimatedValue("height");

                    RelativeLayout.LayoutParams parentLayoutParams = (RelativeLayout.LayoutParams) captureCropView.getLayoutParams();
                    parentLayoutParams.width = (int) (mQrcodeCropWidth * fractionW);
                    parentLayoutParams.height = (int) (mQrcodeCropHeight * fractionH);
                    captureCropView.setLayoutParams(parentLayoutParams);
                }
            });
            valueAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    initCrop();
                    setDataMode(DecodeUtils.DECODE_DATA_MODE_BARCODE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            valueAnimator.start();


        } else if (checkedId == R.id.capture_mode_qrcode) {
            PropertyValuesHolder bar2qrWidthVH = PropertyValuesHolder.ofFloat("width",
                    1.0f, (float) mQrcodeCropWidth / mBarcodeCropWidth);
            PropertyValuesHolder bar2qrHeightVH = PropertyValuesHolder.ofFloat("height",
                    1.0f, (float) mQrcodeCropHeight / mBarcodeCropHeight);
            ValueAnimator valueAnimator = ValueAnimator.ofPropertyValuesHolder(bar2qrWidthVH, bar2qrHeightVH);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Float fractionW = (Float) animation.getAnimatedValue("width");
                    Float fractionH = (Float) animation.getAnimatedValue("height");

                    RelativeLayout.LayoutParams parentLayoutParams = (RelativeLayout.LayoutParams) captureCropView.getLayoutParams();
                    parentLayoutParams.width = (int) (mBarcodeCropWidth * fractionW);
                    parentLayoutParams.height = (int) (mBarcodeCropHeight * fractionH);
                    captureCropView.setLayoutParams(parentLayoutParams);
                }
            });

            valueAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    initCrop();
                    setDataMode(DecodeUtils.DECODE_DATA_MODE_QRCODE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            valueAnimator.start();
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }

        mBeepManager.close();
        mInactivityTimer.onPause();
        cameraManager.closeDriver();

        if (!hasSurface) {
            capturePreview.getHolder().removeCallback(this);
        }

        if (null != mScanMaskObjectAnimator && mScanMaskObjectAnimator.isStarted()) {
            mScanMaskObjectAnimator.cancel();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mInactivityTimer.shutdown();
        super.onDestroy();
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public Rect getCropRect() {
        return cropRect;
    }

    public void setCropRect(Rect cropRect) {
        this.cropRect = cropRect;
    }

    public int getDataMode() {
        return dataMode;
    }

    public void setDataMode(int dataMode) {
        this.dataMode = dataMode;
    }

    private void initPermission() {
        //请求Camera权限 与 文件读写 权限
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    displayFrameworkBugMessageAndExit();
                } else {
                    hasSurface = true;
                    onResume();
                }
                break;
            }
            default:
                break;
        }
    }
}
