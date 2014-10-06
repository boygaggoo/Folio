package com.droidandme.folio.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Window;
import android.view.WindowManager;

import com.droidandme.folio.R;
import com.droidandme.folio.cache.MemoryCache;
import com.droidandme.folio.util.Gallery;

public class ImageDetailActivity extends FragmentActivity {

    private ViewPager mViewPager;
    private ImagePagerAdapter mPagerAdapter;
    public static Gallery mGallery;
    public static Gallery.Album mAlbum;
    public static int mAlbumId;
    public static int position;

    private MemoryCache mMemoryCache;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getActionBar().hide();

        setContentView(R.layout.activity_image_detail);

        //Initialize cache

        MemoryCache.MemoryCacheParams cacheParams =
                new MemoryCache.MemoryCacheParams();

        cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory

        mMemoryCache = MemoryCache.getInstance(cacheParams);


        //Get gallery obj object from intent
        mGallery = (Gallery) getIntent().getSerializableExtra("gallery");
        mAlbumId = (int) getIntent().getIntExtra("albumId", -1);
        position = (int) getIntent().getIntExtra("position", -1);

        mAlbum = mGallery.getAlbums()[mAlbumId];

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ImagePagerAdapter(getSupportFragmentManager(), mAlbum.getImages().length);

        //set the adapter
        mViewPager.setAdapter(mPagerAdapter);

        mViewPager.setPageMargin((int) getResources().getDimension(R.dimen.horizontal_page_margin));
        mViewPager.setOffscreenPageLimit(2);

        // Set the current item based on the extra passed in to this activity
        final int extraCurrentItem = getIntent().getIntExtra("position", -1);
        if (extraCurrentItem != -1) {
            mViewPager.setCurrentItem(extraCurrentItem);
        }

        // Set up activity to go full screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    }

    private class ImagePagerAdapter extends FragmentStatePagerAdapter {
        private final int mSize;

        public ImagePagerAdapter(FragmentManager fm, int size) {
            super(fm);
            this.mSize = size;
        }

        @Override
        public Fragment getItem(int position) {
            Gallery.Album.Image img = mAlbum.getImages()[position];
            return ImageDetailFragment.newInstance(img.getImgPath(), mMemoryCache);
        }

        @Override
        public int getCount() {
            return mSize;
        }
    }

    /**
     * Called by the ViewPager child fragments to get the memory cache
     */
    public MemoryCache getMemoryCache() {
        return mMemoryCache;
    }



}