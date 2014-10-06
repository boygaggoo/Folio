package com.droidandme.folio.cache;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.droidandme.folio.BuildConfig;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by pseth.
 * Memory cache used to store recently processed bitmaps in memory to improve performance
 */
public class MemoryCache {
    private static final String TAG = "MemoryCache";

    // Default memory cache size in kilobytes
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 5; // 5MB

    private LruCache<String, Bitmap> mMemoryCache;

    private Set<SoftReference<Bitmap>> mReusableBitmaps;

    private int memCacheSize;

    private static MemoryCache instance;

    private MemoryCache(MemoryCacheParams params) {

        memCacheSize = params.memCacheSize;

        if (Version.hasHoneyComb()) {
            // only for honeycomb and newer versions
            mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
        }
        Log.d(TAG, "Memory cache created (size = " + memCacheSize + ")");
        mMemoryCache = new LruCache<String, Bitmap>(memCacheSize) {
            // The cache size will be measured in kilobytes rather than
            // number of items.
                @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    final int bitmapSize = bitmap.getByteCount() / 1024;
                    return bitmapSize == 0 ? 1 : bitmapSize;
                }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                // We're running on Honeycomb or later, so add the bitmap
                // to a SoftReference set for possible use with inBitmap later
                if(Version.hasHoneyComb()) {
                    Log.v(TAG, "Evicted bitmap added for reuse");
                    mReusableBitmaps.add(new SoftReference(oldValue));
                }
            }

            };
        }


    public static MemoryCache getInstance(MemoryCacheParams params) {
        // No existing ImageCache, create one and store it in RetainFragment
        if (instance == null) {
            instance = new MemoryCache(params);
        }

        return instance;
    }


    /**
     * Adds a bitmap to both memory cache.
     * @param data Unique identifier for the bitmap to store
     * @param value The bitmap drawable to store
     */
    public void addBitmapToMemCache(String data, Bitmap value) {
        if (data == null || value == null) {
            return;
        }
        if (getBitmapFromMemCache(data) == null) {
            mMemoryCache.put(data, value);
        }
    }

    /**
     * Get from memory cache.
     *
     * @param data Unique identifier for which item to get
     * @return The bitmap drawable if found in cache, null otherwise
     */
    public Bitmap getBitmapFromMemCache(String data) {
        Bitmap memValue = null;

        memValue = mMemoryCache.get(data);

        if (BuildConfig.DEBUG && memValue != null) {
            Log.d(TAG, "Memory cache hit");
        }

        return memValue;
    }

    /**
     * @param options - BitmapFactory.Options with out* options populated
     * @return Bitmap that case be used for inBitmap
     */
    public Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
        Bitmap bitmap = null;

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized (mReusableBitmaps) {
                final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
                Bitmap item;

                while (iterator.hasNext()) {
                    item = iterator.next().get();

                    if (null != item && item.isMutable()) {
                        // Check to see it the item can be used for inBitmap
                        if (canUseForInBitmap(item, options)) {
                            bitmap = item;

                            // Remove from reusable set so it can't be used again
                            iterator.remove();
                            break;
                        }
                    } else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove();
                    }
                }
            }
        }

        return bitmap;
    }

    /**
     * Clears memory cache
     */

    public void clearCache() {
        if (mMemoryCache != null) {
            mMemoryCache.evictAll();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Memory cache cleared");
            }
        }
    }

    /**
     * A holder class that contains cache parameters.
     */
    public static class MemoryCacheParams {
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;

        /**
         * Sets the memory cache size based on a percentage of the max available VM memory.
         * Eg. setting percent to 0.2 would set the memory cache to one fifth of the available
         * memory.
         *
         * @param percent Percent of available app memory to use to size memory cache
         */
        public void setMemCacheSizePercent(float percent) {
            if (percent < 0.01f || percent > 0.8f) {
                throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                        + "between 0.01 and 0.8 (inclusive)");
            }
            memCacheSize = Math.round(percent * Runtime.getRuntime().maxMemory() / 1024);
        }
    }


    /**
     * @param candidate - Bitmap to check
     * @param targetOptions - Options that have the out* value populated
     * @return true if <code>candidate</code> can be used for inBitmap re-use with
     *      <code>targetOptions</code>
     */

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static boolean canUseForInBitmap(
            Bitmap candidate, BitmapFactory.Options targetOptions) {
        if (!Version.hasKitKat()) {
            // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return candidate.getWidth() == targetOptions.outWidth
                    && candidate.getHeight() == targetOptions.outHeight
                    && targetOptions.inSampleSize == 1;
        }

        // From Android 4.4 (KitKat) onward we can re-use if the byte size of the new bitmap
        // is smaller than the reusable bitmap candidate allocation byte count.
        int width = targetOptions.outWidth / targetOptions.inSampleSize;
        int height = targetOptions.outHeight / targetOptions.inSampleSize;
        int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
        return byteCount <= candidate.getAllocationByteCount();
    }

    /**
     * Return the byte usage per pixel of a bitmap based on its configuration.
     * @param config The bitmap configuration.
     * @return The byte usage per pixel.
     */
    private static int getBytesPerPixel(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        } else if (config == Bitmap.Config.RGB_565) {
            return 2;
        } else if (config == Bitmap.Config.ARGB_4444) {
            return 2;
        } else if (config == Bitmap.Config.ALPHA_8) {
            return 1;
        }
        return 1;
    }

public static class Version {
    public static boolean hasHoneyComb() {
        return (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);
    }

    public static boolean hasKitKat() {
        return (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
    }
}


}


