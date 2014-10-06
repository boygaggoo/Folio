package com.droidandme.folio.ui;

import android.graphics.drawable.BitmapDrawable;

import com.droidandme.folio.util.BitMapLoaderTask;

import java.lang.ref.WeakReference;

/**
 * Created by pseth.
 * A drawable that will link the async tas that is loading the bitmap.
 *
 */
public class AsyncDrawable extends BitmapDrawable{
    private WeakReference<BitMapLoaderTask> m_bitMapLoaderWeak;

    public AsyncDrawable(BitMapLoaderTask loaderTask) {
        m_bitMapLoaderWeak = new WeakReference<BitMapLoaderTask>(loaderTask);
    }

    public BitMapLoaderTask getBitMapLoaderTask() {
        return m_bitMapLoaderWeak.get();
    }
}
