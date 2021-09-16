package com.bulala.qrcode.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bulala.qrcode.R;
import com.bulala.qrcode.adapter.ListViewDataAdapter;
import com.bulala.qrcode.adapter.ViewHolderBase;
import com.bulala.qrcode.adapter.ViewHolderCreator;
import com.bulala.qrcode.picker.ImageItem;
import com.bulala.qrcode.utils.CommonUtils;
import com.bulala.qrcode.utils.ImageLoaderHelper;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class CommonImagePickerDetailActivity extends AppCompatActivity {

    public static final String KEY_BUNDLE_RESULT_IMAGE_PATH = "KEY_BUNDLE_RESULT_IMAGE_PATH";

    private GridView commonImagePickerDetailGridView;

    private ListViewDataAdapter<ImageItem> mGridViewAdapter = null;
    private List<ImageItem> mGridListData = null;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = this;
        setContentView(R.layout.activity_common_image_picker_detail);
        getBundleExtras();
        initViewsAndEvents();
    }

    private void getBundleExtras() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            mGridListData = intent.getExtras().getParcelableArrayList(CommonImagePickerListActivity
                    .KEY_BUNDLE_ALBUM_PATH);

            View titleView = findViewById(R.id.common_toolbar);
            TextView tvTitle = titleView.findViewById(R.id.tv_title);
            tvTitle.setText(getResources().getString(R.string.choose_image));
            String title = intent.getExtras().getString(CommonImagePickerListActivity.KEY_BUNDLE_ALBUM_NAME);
            if (!CommonUtils.isEmpty(title)) {
                tvTitle.setText(title);
            }
            ImageView ivBack = titleView.findViewById(R.id.iv_back);
            ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    private void initViewsAndEvents() {
        commonImagePickerDetailGridView = findViewById(R.id.common_image_picker_detail_grid_view);

        mGridViewAdapter = new ListViewDataAdapter<>(new ViewHolderCreator<ImageItem>() {
            @Override
            public ViewHolderBase<ImageItem> createViewHolder(int position) {
                return new ViewHolderBase<ImageItem>() {

                    ImageView mItemImage;

                    @Override
                    public View createView(LayoutInflater layoutInflater) {
                        View convertView = layoutInflater.inflate(R.layout.grid_item_common_image_picker, null);
                        mItemImage = convertView.findViewById(R.id.grid_item_common_image_picker_image);
                        return convertView;
                    }

                    @Override
                    public void showData(int position, ImageItem itemData) {
                        if (null != itemData) {
                            String imagePath = itemData.getImagePath();
                            if (!CommonUtils.isEmpty(imagePath)) {
                                ImageLoader.getInstance().displayImage("file://" + imagePath,
                                        mItemImage, ImageLoaderHelper.getInstance(mContext).getDisplayOptions());
                            }
                        }
                    }
                };
            }
        });
        mGridViewAdapter.getDataList().addAll(mGridListData);
        commonImagePickerDetailGridView.setAdapter(mGridViewAdapter);

        commonImagePickerDetailGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (null != mGridViewAdapter && null != mGridViewAdapter.getDataList() &&
                        !mGridViewAdapter.getDataList().isEmpty() &&
                        position < mGridViewAdapter.getDataList().size()) {

                    Intent intent = new Intent();
                    intent.putExtra(KEY_BUNDLE_RESULT_IMAGE_PATH,
                            mGridViewAdapter.getDataList().get(position).getImagePath());

                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
    }
}
