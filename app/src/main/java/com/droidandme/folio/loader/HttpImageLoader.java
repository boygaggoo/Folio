package com.droidandme.folio.loader;

import android.annotation.TargetApi;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.OperationCanceledException;
import android.provider.MediaStore;
import android.util.Log;

import com.droidandme.folio.util.DataLoad;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Created by pseth.
 * Loader which will download images from a set of URIs and store it in MediaStore/Gallery on the device.
 */
public class HttpImageLoader extends AsyncTaskLoader<Object> {
    final ForceLoadContentObserver mObserver;

    private final static String TAG = "HttpImageLoader";
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    CancellationSignal mCancellationSignal;
    Boolean mSuccess = false;
    SharedPreferences mSharedPref;

    public HttpImageLoader(Context context) {
        super(context);
        mObserver = new ForceLoadContentObserver();
        mSharedPref = context.getSharedPreferences("com.droidandme.folio.PREFERENCE_FILE_KEY", Context.MODE_PRIVATE);
    }

    /**
     * This method will load urls in background and store it in the device Media store.
     * This will be done once the app starts.
     */
    //TODO: Check for duplicates in the db
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Boolean loadInBackground() {
        Boolean success = false;
        synchronized (this) {
            if (isLoadInBackgroundCanceled()) {
                throw new OperationCanceledException();
            }
            mCancellationSignal = new CancellationSignal();
        }

        SharedPreferences.Editor editor = mSharedPref.edit();
        //Getting the URLs from a static list //test-only
        for (String urlString : DataLoad.imageUrls) {
            try {
                if(mSharedPref.getString(urlString, null) == null) { //new image to download
                    Bitmap bitmap = BitmapFactory.decodeStream(new URL(urlString).openConnection().getInputStream());
                    if (bitmap != null) {
                        String finalUri = insertImage(getContext().getContentResolver(), bitmap, "title", "description", "/folio/");
                        //loadedImgs.put(urlString, finalUri);
                        editor.putString(urlString, finalUri);
                        editor.commit();
                    }
                } else {
                    Log.v(TAG, "Image already exists in the album ");
                }
            } catch (IOException e) {
                success = false;
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    mCancellationSignal = null;
                    //editor.commit();
                }
            }
        }
        return success;
    }

    /**
     * Download a bitmap from a URL and write the content to an output stream.
     *
     * @param urlString The URL to fetch
     * @return true if successful, false otherwise
     */
    public boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadBitmap - " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
            }
        }
        return false;
    }

    /**
     * Insert an image and create a thumbnail for it.
     *
     * @param cr The content resolver to use
     * @param source The stream to use for the image
     * @param title The name of the image
     * @param description The description of the image
     * @return The URL to the newly created image, or <code>null</code> if the image failed to be stored
     *              for any reason.
     */
    public static final String insertImage(ContentResolver cr, Bitmap source,
                                           String title, String description, String filepath) {

        File imagePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+ File.separator + filepath + File.separator);
        File file = new File(imagePath, "FOLIO_"+ System.currentTimeMillis() +".jpg");
        if (!imagePath.exists()) {
            imagePath.mkdirs();
        }

        Uri url = null;
        String stringUrl = null;    /* value to be returned */


        try {
            if (source != null) {
                //OutputStream imageOut = cr.openOutputStream(url);
                OutputStream imageOut = new FileOutputStream(file);
                try {
                    source.compress(Bitmap.CompressFormat.JPEG, 50, imageOut);
                } finally {
                    imageOut.flush();
                    imageOut.close();
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, title);
                values.put(MediaStore.Images.Media.DESCRIPTION, description);
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis ());
                values.put(MediaStore.Images.ImageColumns.BUCKET_ID, file.toString().toLowerCase(Locale.US).hashCode());
                values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, file.getName().toLowerCase(Locale.US));
                values.put("_data", file.getAbsolutePath());

                //ContentResolver cr = getContentResolver();
                url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                long id = ContentUris.parseId(url);
                // Wait until MINI_KIND thumbnail is generated.
                Bitmap miniThumb = MediaStore.Images.Thumbnails.getThumbnail(cr, id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null);
                // This is for backward compatibility.
                Bitmap microThumb = StoreThumbnail(cr, miniThumb, id, 50F, 50F,
                        MediaStore.Images.Thumbnails.MICRO_KIND);
            } else {
                Log.e(TAG, "Failed to create thumbnail, removing original");
                cr.delete(url, null, null);
                url = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert image", e);
            if (url != null) {
                cr.delete(url, null, null);
                url = null;
            }
        }

        if (url != null) {
            stringUrl = url.toString();
        }

        return stringUrl;
    }


    private static final Bitmap StoreThumbnail(
            ContentResolver cr,
            Bitmap source,
            long id,
            float width, float height,
            int kind) {
        // create the matrix to scale it
        Matrix matrix = new Matrix();

        float scaleX = width / source.getWidth();
        float scaleY = height / source.getHeight();

        matrix.setScale(scaleX, scaleY);

        Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
                source.getWidth(),
                source.getHeight(), matrix,
                true);

        ContentValues values = new ContentValues(4);
        values.put(MediaStore.Images.Thumbnails.KIND,     kind);
        values.put(MediaStore.Images.Thumbnails.IMAGE_ID, (int)id);
        values.put(MediaStore.Images.Thumbnails.HEIGHT,   thumb.getHeight());
        values.put(MediaStore.Images.Thumbnails.WIDTH,    thumb.getWidth());

        Uri url = cr.insert(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, values);

        try {
            OutputStream thumbOut = cr.openOutputStream(url);

            thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
            thumbOut.close();
            return thumb;
        }
        catch (FileNotFoundException ex) {
            return null;
        }
        catch (IOException ex) {
            return null;
        }
    }

    /* Runs on the UI thread */
    @Override
    public void deliverResult(Object obj) {
        Boolean bool = (Boolean) obj;

        Boolean old = mSuccess;
        mSuccess = bool;

        if (isStarted()) {
            super.deliverResult(bool);
        }
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     *
     * Must be called from the UI thread
     */
    @Override
    protected void onStartLoading() {
        if (mSuccess != null) {
            deliverResult(mSuccess);
        }
        if (takeContentChanged() || mSuccess == null) {
            forceLoad();
        }
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    public void onCanceled(Object bool) {

    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        mSuccess = false;
    }



}
