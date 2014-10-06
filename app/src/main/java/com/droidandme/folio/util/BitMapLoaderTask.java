package com.droidandme.folio.util;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import com.droidandme.folio.BuildConfig;
import com.droidandme.folio.cache.MemoryCache;
import com.droidandme.folio.ui.AsyncDrawable;

import java.lang.ref.WeakReference;

import static android.os.Build.VERSION_CODES.HONEYCOMB;

/**
 * Created by pseth.
 */
public class BitMapLoaderTask extends AsyncTask<Uri, Void, Bitmap> {

    private static final String TAG = "BitMapLoaderTask";
    private static final int FADE_IN_TIME = 200;

    private static final Integer DEFAULT_THUMBNAIL_WIDTH = 100;
    private static final Integer DEFAULT_THUMBNAIL_HEIGHT = 100;

    private final WeakReference<ImageView> mWeakRefImgView;
    private Integer mTHUMBNAIL_HEIGHT;
    private Integer mTHUMBNAIL_WIDTH;
    private final MemoryCache mMemoryCache;
    protected Uri mUri;

    private Bitmap mLoadingBitmap;


    public BitMapLoaderTask(Uri uri, ImageView imgView, MemoryCache memCache, Integer THUMBNAIL_WIDTH, Integer THUMBNAIL_HEIGHT) {
        //Create a weak ref to the image view to make sure ImageView can be garbage collected
        mWeakRefImgView = new WeakReference<ImageView>(imgView);
        mMemoryCache = memCache;
        mTHUMBNAIL_HEIGHT = THUMBNAIL_HEIGHT;
        mTHUMBNAIL_WIDTH = THUMBNAIL_WIDTH;
        mUri = uri;
    }

    @Override
    protected Bitmap doInBackground(Uri... params) {
        //Null mTHUMBNAIL_HEIGHT/mTHUMBNAIL_WIDTH indicates no sampling required.
        if (mTHUMBNAIL_HEIGHT == null || mTHUMBNAIL_WIDTH == null) {
            //Check if the bimap is in cache
            //Indicate full size image in memory with high appended to the imgPath.
            if (mMemoryCache.getBitmapFromMemCache(mUri.getPath() + "high") != null) {
                return mMemoryCache.getBitmapFromMemCache(mUri.getPath() + "high");
            }
            //else get the bitmap
            Bitmap bitmap = BitmapFactory.decodeFile(mUri.getPath());

            //add it to memory cache
            mMemoryCache.addBitmapToMemCache(mUri.getPath() + "high", bitmap);
            return bitmap;

        }
        //Check if the bimap is in cache
        if (mMemoryCache.getBitmapFromMemCache(mUri.getPath()) != null) {
            Log.v("%%%%%%%%%%%%%%", "Loading from memory cache");
            return mMemoryCache.getBitmapFromMemCache(mUri.getPath());
        }

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mUri.getPath(), options);

        //Check if user supplied THUMBNAIL_HEIGHT/THUMBNAIL_WIDTH is greater than original height and width
        if (options.outHeight < mTHUMBNAIL_HEIGHT || options.outWidth < mTHUMBNAIL_WIDTH) {
            Log.w("EfficientBitMap.getPreview():",
                    "Requested height and width of the image is smaller than the original size of the iamge. Defaulting to 100X100");
            mTHUMBNAIL_HEIGHT = DEFAULT_THUMBNAIL_HEIGHT;
            mTHUMBNAIL_WIDTH = DEFAULT_THUMBNAIL_WIDTH;
        }
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, mTHUMBNAIL_HEIGHT, mTHUMBNAIL_WIDTH);

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (MemoryCache.Version.hasHoneyComb()) {
            addInBitmapOptions(options, mMemoryCache);
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        //get the bitmap
        Bitmap bitmap = BitmapFactory.decodeFile(mUri.getPath(), options);

        //add it to memory cache
        mMemoryCache.addBitmapToMemCache(mUri.getPath(), bitmap);
        return bitmap;
    }

    // Once complete, see if ImageView is still around and set bitmap.
    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            bitmap = null;
        }

        final ImageView imageView = getAttachedImageView();
        if (bitmap != null && imageView != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onPostExecute - setting bitmap");
            }
            setImageDrawable(imageView, bitmap);
        }
    }

    /**
     * Returns the ImageView associated with this task as long as the ImageView's task still
     * points to this task as well. Returns null otherwise.
     */
    private ImageView getAttachedImageView() {
        final ImageView imageView = mWeakRefImgView.get();
        final BitMapLoaderTask bitmapWorkerTask = getBitmapLoaderTask(imageView);

        if (this == bitmapWorkerTask) {
            return imageView;
        }

        return null;
    }

    private void setImageDrawable(ImageView imageView, Bitmap bitmap) {
        // Transition drawable with a transparent drawable and the final drawable
        final TransitionDrawable td =
                new TransitionDrawable(new Drawable[]{
                        new ColorDrawable(android.R.color.transparent),
                        new BitmapDrawable(bitmap)
                });
                /*// Set background to loading bitmap
                imageView.setBackgroundDrawable(
                        new BitmapDrawable(mLoadingBitmap));
*/
        imageView.setImageDrawable(td);
        td.startTransition(FADE_IN_TIME);
    }

    /**
     * Set placeholder bitmap that shows when the the background thread is running.
     *
     * @param resId
     */
    public void setLoadingImage(int resId) {
        mLoadingBitmap = BitmapFactory.decodeResource(null, resId);
    }

    @TargetApi(HONEYCOMB)
    private void addInBitmapOptions(BitmapFactory.Options options, MemoryCache cache) {
        // inBitmap only works with mutable bitmaps so force the decoder to
        // return mutable bitmaps.
        options.inMutable = true;

        if (cache != null) {
            // Try and find a bitmap to use for inBitmap
            Bitmap inBitmap = cache.getBitmapFromReusableSet(options);

            if (inBitmap != null) {
                options.inBitmap = inBitmap;
            }
        }
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }


    private static BitMapLoaderTask getBitmapLoaderTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitMapLoaderTask();
            }
        }
        return null;
    }

    public static boolean cancelPotentialTask(Uri data, ImageView imageView) {
        final BitMapLoaderTask bitMapLoaderTask = getBitmapLoaderTask(imageView);
        if (bitMapLoaderTask != null) { //means there was an existing task
            Log.v(TAG, "Already an existing task");
            final Uri uri = bitMapLoaderTask.mUri;
            if (uri == null || !uri.equals(data)) {
                Log.v(TAG, "Cancel existing task");
                bitMapLoaderTask.cancel(true);
            } else {
                //its already in progress
                Log.v(TAG, "Not cancelling, its already in progress");
                return false;
            }
        }
        return true;
    }
}



