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

import com.droidandme.folio.R;
import com.droidandme.folio.cache.MemoryCache;
import com.droidandme.folio.loader.MediaStoreLoader;
import com.droidandme.folio.util.BitMapLoaderTask;
import com.droidandme.folio.util.Gallery;

import java.io.File;

/**
 * Activity that displays the images in an album in a grid view.
 */

public class PhotosGridActivity extends Activity implements LoaderManager.LoaderCallbacks<Object> {
    private final static String TAG = "PhotosGridActivity";

    private static final Uri MEDIA_STORE_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    private static final String[] PROJECTION = {MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA};
    private static String SELECTION = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
    private static String[] SELECTION_ARGS = {""};

    // The loader's unique id. Loader ids are specific to the Activity or
    // Fragment in which they reside.
    private static final int MEDIA_LOADER_ID = 1;

    private static final int THUMBNAIL_WIDTH = 75;
    private static final int THUMBNAIL_HEIGHT = 75;

    // The adapter that binds our data to the ListView
    private SimpleCursorAdapter mAdapter;
    private Gallery mGallery;
    private int mAlbumId;
    private MemoryCache mMemoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_album_preview);

        //Initialize cache
        MemoryCache.MemoryCacheParams cacheParams =
                new MemoryCache.MemoryCacheParams();

        cacheParams.setMemCacheSizePercent(0.50f); // Set memory cache to 25% of app memory

        mMemoryCache = MemoryCache.getInstance(cacheParams);

        //Get intent data
        mGallery = (Gallery) getIntent().getSerializableExtra("gallery");
        mAlbumId = (int) getIntent().getIntExtra("albumId", -1);
        // add this bucket id to the selection args for querying the content provider
        SELECTION_ARGS[0] = (String) getIntent().getStringExtra("albumName");


        //String from[] = {MediaStore.Images.Media.DATA};
        String from[] = {MediaStore.Images.Media.DATA};
        int to[] = {R.id.photo};

        mAdapter = new SimpleCursorAdapter(this, R.layout.view_album_photos,
                null, from, to, 0);

        //Display the albums with a cover photo in GridView
        GridView photoGrid = (GridView) findViewById(R.id.albumPreviewGridView);

        //Associate the (now empty) adapter with the GridView
        photoGrid.setAdapter(mAdapter);

        /**
         * On Click event for Single Gridview Item
         * */
        photoGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                // Sending image id to ImageDetailActivity
                final Intent i = new Intent(getApplicationContext(), ImageDetailActivity.class);
                i.putExtra("position", position);
                i.putExtra("gallery", mGallery);
                i.putExtra("albumId", mAlbumId);
                startActivity(i);
            }
        });

        // The Activity (which implements the LoaderCallbacks<Cursor>
        // interface) is the callbacks object through which we will interact
        // with the LoaderManager. The LoaderManager uses this object to
        // instantiate the Loader and to notify the client when data is made
        // available/unavailable.
        LoaderManager.LoaderCallbacks<Object> mCallbacks = this;

        // Initialize the Loader with id '1' and callbacks 'mCallbacks'.
        // If the loader doesn't already exist, one is created. Otherwise,
        // the already created Loader is reused. In either case, the
        // LoaderManager will manage the Loader across the Activity/Fragment
        // lifecycle, will receive any new loads once they have completed,
        // and will report this new data back to the 'mCallbacks' object.

        LoaderManager lm = getLoaderManager();
        lm.initLoader(MEDIA_LOADER_ID, null, mCallbacks);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.album_preview, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Loader call back methods here
    @Override
    public Loader<Object> onCreateLoader(int id, Bundle args) {
        // Create a new CursorLoader with the following query parameters.
        return new MediaStoreLoader(PhotosGridActivity.this, MEDIA_STORE_CONTENT_URI, PROJECTION, SELECTION, SELECTION_ARGS, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Object> loader, Object obj) {
        // A switch-case is useful when dealing with multiple Loaders/IDs
        switch (loader.getId()) {
            case MEDIA_LOADER_ID:
                Cursor cur = (Cursor) obj;
                Log.i("ListingImages", " query count=" + cur.getCount());

                // The asynchronous load is complete and the data
                // is now available for use. Only now can we associate
                // the queried Cursor with the SimpleCursorAdapter.
                mAdapter.swapCursor(cur);
                //Map the data from cursor to Gallery/Album/Image object
                cursorToObjectMapping(cur);
                mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                    @Override
                    public boolean setViewValue(View view, Cursor cur, int columnIndex) {
                        if (view.getId() == R.id.photo) {
                            int bucketColumn = cur.getColumnIndex(
                                    MediaStore.Images.Media.DATA);
                            ImageView imageView = (ImageView) view;
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
                        }
                        Log.v(TAG, "Nothing to bind");
                        return false;
                    }
                });
                break;
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

    private void cursorToObjectMapping(Cursor cur) {
        Gallery.Album.Image images[] = new Gallery.Album.Image[cur.getCount()];
        if (cur.moveToFirst()) {
            do {
                Gallery.Album.Image img = new Gallery.Album.Image(cur.getInt(cur.getColumnIndex(MediaStore.Images.Media._ID)),
                        cur.getString(cur.getColumnIndex(MediaStore.Images.Media.DATA)));

                images[cur.getPosition()] = img;

            } while (cur.moveToNext());
            mGallery.getAlbums()[mAlbumId].setImages(images);
        }
    }
}