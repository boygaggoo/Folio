package com.droidandme.folio.ui;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.droidandme.folio.util.BitMapLoaderTask;
import com.droidandme.folio.cache.MemoryCache;
import com.droidandme.folio.R;

import java.io.File;

public class ImageDetailFragment extends Fragment {

    private static final String IMAGE_DATA_EXTRA = "resId";
    private String mImgPath;
    private ImageView mImageView;
    private MemoryCache mMemoryCache;

    public static ImageDetailFragment newInstance(String imagePath, MemoryCache memoryCache) {
        final ImageDetailFragment f = new ImageDetailFragment();
        final Bundle args = new Bundle();
        args.putString(IMAGE_DATA_EXTRA, imagePath);
        f.setArguments(args);
        return f;
    }

    public ImageDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImgPath = getArguments() != null ? getArguments().getString(IMAGE_DATA_EXTRA) : null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // fragment_image_detail.xml contains just an ImageView
        final View v = inflater.inflate(R.layout.fragment_image_detail, container, false);
        mImageView = (ImageView) v.findViewById(R.id.imageView);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (ImageDetailActivity.class.isInstance(getActivity())) {
            mMemoryCache = ((ImageDetailActivity) getActivity()).getMemoryCache();
        }
        //Call parent activity to load the img from the album object which was also cached already.
        Uri uri = Uri.fromFile(new File(mImgPath));
        //Check if there is already a task running for this view and if yes then cancel the previous one.
        if(BitMapLoaderTask.cancelPotentialTask(uri, mImageView)) {
            //Background thread to process the bitmaps
            BitMapLoaderTask loadTask = new BitMapLoaderTask(uri,mImageView, mMemoryCache, null, null);
            AsyncDrawable asyncDrawable = new AsyncDrawable(loadTask);
            mImageView.setImageDrawable(asyncDrawable);
            loadTask.execute(uri);
        }

    }

}
