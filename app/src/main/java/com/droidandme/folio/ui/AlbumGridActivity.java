package com.droidandme.folio.ui;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.droidandme.folio.R;
import com.droidandme.folio.cache.MemoryCache;
import com.droidandme.folio.loader.HttpImageLoader;
import com.droidandme.folio.loader.MediaStoreLoader;
import com.droidandme.folio.util.BitMapLoaderTask;
import com.droidandme.folio.util.Gallery;

import java.io.File;

/**
 * This is the main launcher activity which will display the albums in users gallery.
 * It will include albums from the device Media store as well as the ones downloaded from internet.
 */

public class AlbumGridActivity extends Activity implements LoaderManager.LoaderCallbacks<Object> {

    private static final String TAG = "AlbumGridActivity";

    private static final Uri MEDIA_STORE_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    private static final String[] PROJECTION = {MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA, MediaStore.Images.Media.BUCKET_DISPLAY_NAME};

    private static final int ALBUM_LOADER_ID = 1;
    private static final int HTTP_ALBUM_LOADER_ID = 2;

    private static final int THUMBNAIL_WIDTH = 100;
    private static final int THUMBNAIL_HEIGHT = 100;

    // The adapter that binds our data to the ListView
    private SimpleCursorAdapter mAdapter;

    private Gallery mGallery;

    private MemoryCache mMemoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_album);

        //Initialize cache
        MemoryCache.MemoryCacheParams cacheParams =
                new MemoryCache.MemoryCacheParams();

        cacheParams.setMemCacheSizePercent(0.50f); // Set memory cache to 25% of app memory

        mMemoryCache = MemoryCache.getInstance(cacheParams);

        //Create adapter for the GridView
        String from[] = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        int to[] = {R.id.albumCover, R.id.albumName};

        mAdapter = new SimpleCursorAdapter(this, R.layout.view_album,
                null, from, to, 0);

        GridView albumGrid = (GridView) findViewById(R.id.albumGridView);
        //Associate the (now empty) adapter with the GridView
        albumGrid.setAdapter(mAdapter);

        /**
         * On Click event for Single Gridview Item
         * */
        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {

                //Find which album was clicked
                Gallery.Album album = mGallery.getAlbums()[position];
                Intent i = new Intent(getApplicationContext(), PhotosGridActivity.class);
                i.putExtra("albumName",album.getAlbumName());
                i.putExtra("albumId", position);
                i.putExtra("gallery", mGallery);
                startActivity(i);
            }
        });


        //Initialize the loader
        LoaderManager.LoaderCallbacks<Object> mCallbacks = this;

        LoaderManager lm = getLoaderManager();
        lm.initLoader(ALBUM_LOADER_ID, null, mCallbacks).forceLoad();
        lm.initLoader(HTTP_ALBUM_LOADER_ID, null, mCallbacks).forceLoad();

    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this puts items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.album, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }

    // Loader call back methods here
    @Override
    public Loader<Object> onCreateLoader(int id, Bundle args) {
        switch(id) {
            //Loader 1: Will fetch and load images from Media Store of the device
            case ALBUM_LOADER_ID:
               return new MediaStoreLoader(AlbumGridActivity.this, MEDIA_STORE_CONTENT_URI, PROJECTION,
                        null, null, null, MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            //Loader 2: Will download images from internet and store it in the device
            case HTTP_ALBUM_LOADER_ID:
                return new HttpImageLoader(AlbumGridActivity.this);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Object> loader, Object obj) {
        switch (loader.getId()) {
            case ALBUM_LOADER_ID: {
                Cursor cur = (Cursor) obj;
                Log.i("ListingImages", " query count=" + cur.getCount());

                // The asynchronous load is complete and the data
                // is now available for use. Only now can we associate
                // the queried Cursor with the SimpleCursorAdapter.
                mAdapter.swapCursor(cur);
                //Map the data from cursor to Gallery/Album object
                albumCursorToObjectMapping(cur);

                mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                    @Override
                    public boolean setViewValue(View view, Cursor cur, int columnIndex) {
                        int bucketColumn = cur.getColumnIndex(
                                MediaStore.Images.Media.DATA);
                        int bucketNameColumn = cur.getColumnIndex(
                                MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

                        if (view.getId() == R.id.albumCover) {
                            ImageView imageView = (ImageView) view;
                            Log.v("Path to image:", cur.getString(bucketColumn));

                            Uri uri = Uri.fromFile(new File(cur.getString(bucketColumn)));
                            //Check if there is already a task running for this view and if yes then cancel the previous one.
                            if (BitMapLoaderTask.cancelPotentialTask(uri, imageView)) {
                                Log.v(TAG, "Creating new BitMapLoaderTask to load view ");
                                //Background thread to process the bitmaps
                                BitMapLoaderTask loadTask = new BitMapLoaderTask(uri, imageView, mMemoryCache, THUMBNAIL_HEIGHT, THUMBNAIL_WIDTH);
                                AsyncDrawable asyncDrawable = new AsyncDrawable(loadTask);
                                loadTask.setLoadingImage(R.drawable.empty_photo);
                                imageView.setImageDrawable(asyncDrawable);
                                loadTask.execute(uri);
                            }

                            return true;
                        } else if (view.getId() == R.id.albumName) {
                            TextView albumNameText = (TextView) view;
                            albumNameText.setText(cur.getString(bucketNameColumn));
                            return true;
                        }
                        Log.v(TAG, "Nothing to bind");
                        return false;
                    }
                });
                break;
             }

            case HTTP_ALBUM_LOADER_ID: {
                Boolean success = (Boolean) obj;
                Log.v(TAG, "HTTP call was successful=" + success);
                break;
            }

        }
        // Gridview now displays the data
    }

    @Override
    public void onLoaderReset(Loader<Object> loader) {
        //For whatever reason, the Loader 's data is now unavailable.
        // Remove any references to the old data by replacing it with
        // a null Cursor.
        mAdapter.swapCursor(null);
    }

    private void albumCursorToObjectMapping(Cursor cur) {
        mGallery = new Gallery(Gallery.GALLERY.MEDIA_GALLERY);
        Gallery.Album albums[] = new Gallery.Album[cur.getCount()];
        if(cur.moveToFirst()) {
            do {
                Gallery.Album album = new Gallery.Album(cur.getInt(cur.getColumnIndex(MediaStore.Images.Media._ID)),
                        cur.getString(cur.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)));
                albums[cur.getPosition()] =  album;
            } while (cur.moveToNext());
            mGallery.setAlbums(albums);
        }
    }

}