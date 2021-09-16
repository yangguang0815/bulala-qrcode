package com.bulala.qrcode;

import android.app.Application;
import android.os.Bundle;

import com.bulala.qrcode.common.Constant;
import com.bulala.qrcode.utils.ImageLoaderHelper;
import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Description 二维码application
 *
 * @author yg
 * @date 2021/8/27
 */
public class QRApplication {
    /**
     * QRApplication实例
     */
    private static QRApplication instance;

    /**
     * 初始化二维码组件
     *
     * @param application 应用全局上下文
     */
    public static void initQRCode(Application application) {
        ImageLoader.getInstance().init(ImageLoaderHelper.getInstance(application).getImageLoaderConfiguration(Constant.IMAGE_LOADER_CACHE_PATH));
    }

    /**
     * 获取QRApplication实例
     *
     * @return QRApplication实例
     */
    public static QRApplication getInstance() {
        if (instance == null) {
            synchronized (QRApplication.class) {
                if (instance == null) {
                    instance = new QRApplication();
                }
            }
        }
        return instance;
    }

    /**
     * 扫描成功所触发的监听
     */
    private OnResultListener mOnResultListener;

    /**
     * 扫描成功触发监听接口
     */
    public interface OnResultListener {
        /**
         * 用户自定义扫描成功后所触发的监听，若用户不设置，则会跳转到默认结果界面
         *
         * @param bundle 扫描信息bundle，Key值为：ResultActivity.BUNDLE_KEY_SCAN_RESULT，类型为String
         */
        void onResultListener(Bundle bundle);
    }

    /**
     * 设置扫描成功后触发的监听
     *
     * @param mOnResultListener 使用者设置的扫描成功触发监听
     */
    public void setOnResultListener(OnResultListener mOnResultListener) {
        this.mOnResultListener = mOnResultListener;
    }

    /**
     * 获取扫描成功触发监听
     *
     * @return 扫描成功后触发的监听
     */
    public QRApplication.OnResultListener getOnResultListener() {
        return mOnResultListener;
    }
}
