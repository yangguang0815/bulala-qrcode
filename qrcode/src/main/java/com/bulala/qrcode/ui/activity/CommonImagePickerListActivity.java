package com.bulala.qrcode.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bulala.qrcode.R;
import com.bulala.qrcode.adapter.ListViewDataAdapter;
import com.bulala.qrcode.adapter.ViewHolderBase;
import com.bulala.qrcode.adapter.ViewHolderCreator;
import com.bulala.qrcode.picker.ImageBucket;
import com.bulala.qrcode.picker.ImagePickerHelper;
import com.bulala.qrcode.utils.CommonUtils;
import com.bulala.qrcode.utils.ImageLoaderHelper;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class CommonImagePickerListActivity extends AppCompatActivity {

    private static final int IMAGE_PICKER_DETAIL_REQUEST_CODE = 200;
    private Context mContext;

    public static final String KEY_BUNDLE_ALBUM_PATH = "KEY_BUNDLE_ALBUM_PATH";
    public static final String KEY_BUNDLE_ALBUM_NAME = "KEY_BUNDLE_ALBUM_NAME";

    private ListView mImagePickerListView;

    private ListViewDataAdapter<ImageBucket> mListViewAdapter = null;
    private AsyncTask<Void, Void, List<ImageBucket>> mAlbumLoadTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = this;
        setContentView(R.layout.activity_common_image_picker_list);
        initViewsAndEvents();
    }

    public View getLoadingTargetView() {
        return mImagePickerListView;
    }

    private void initViewsAndEvents() {

        mImagePickerListView = findViewById(R.id.common_image_picker_list_view);

        View titleView = findViewById(R.id.common_toolbar);
        TextView tvTitle = titleView.findViewById(R.id.tv_title);
        tvTitle.setText(getResources().getString(R.string.title_image_picker));
        ImageView ivBack = titleView.findViewById(R.id.iv_back);
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mListViewAdapter = new ListViewDataAdapter<>(new ViewHolderCreator<ImageBucket>() {
            @Override
            public ViewHolderBase<ImageBucket> createViewHolder(int position) {
                return new ViewHolderBase<ImageBucket>() {

                    ImageView mItemImage;
                    TextView mItemTitle;

                    @Override
                    public View createView(LayoutInflater layoutInflater) {
                        View convertView = layoutInflater.inflate(R.layout
                                .list_item_common_image_picker, null);
                        mItemImage = convertView.findViewById(R.id
                                .list_item_common_image_picker_thumbnail);
                        mItemTitle = convertView.findViewById(R.id
                                .list_item_common_image_picker_title);
                        return convertView;
                    }

                    @Override
                    public void showData(int position, ImageBucket itemData) {
                        if (null != itemData) {
                            String imagePath = itemData.bucketList.get(0).getImagePath();
                            if (!CommonUtils.isEmpty(imagePath)) {
                                ImageLoader.getInstance().displayImage("file://" + imagePath,
                                        mItemImage,
                                        ImageLoaderHelper.getInstance(mContext).getDisplayOptions());
                            }

                            int count = itemData.count;
                            String title = itemData.bucketName;

                            if (!CommonUtils.isEmpty(title)) {
                                mItemTitle.setText(title + "(" + count + ")");
                            }
                        }
                    }
                };
            }
        });
        mImagePickerListView.setAdapter(mListViewAdapter);

        mImagePickerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (null != mListViewAdapter && null != mListViewAdapter.getDataList() &&
                        !mListViewAdapter.getDataList().isEmpty() &&
                        position < mListViewAdapter.getDataList().size()) {

                    Bundle extras = new Bundle();
                    extras.putParcelableArrayList(KEY_BUNDLE_ALBUM_PATH, mListViewAdapter
                            .getDataList().get(position).bucketList);
                    extras.putString(KEY_BUNDLE_ALBUM_NAME, mListViewAdapter.getDataList().get
                            (position).bucketName);

                    Intent intent = new Intent(mContext, CommonImagePickerDetailActivity.class);
                    intent.putExtras(extras);
                    startActivityForResult(intent, IMAGE_PICKER_DETAIL_REQUEST_CODE);
                }
            }
        });

        mAlbumLoadTask = new AsyncTask<Void, Void, List<ImageBucket>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                ImagePickerHelper.getHelper().init(mContext);
            }

            @Override
            protected List<ImageBucket> doInBackground(Void... params) {
                return ImagePickerHelper.getHelper().getImagesBucketList();
            }

            @Override
            protected void onPostExecute(List<ImageBucket> list) {
                mListViewAdapter.getDataList().addAll(list);
                mListViewAdapter.notifyDataSetChanged();
            }
        };

        mAlbumLoadTask.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mAlbumLoadTask && !mAlbumLoadTask.isCancelled()) {
            mAlbumLoadTask.cancel(true);
            mAlbumLoadTask = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == IMAGE_PICKER_DETAIL_REQUEST_CODE) {
            setResult(RESULT_OK, data);
            finish();
        }
    }
}
