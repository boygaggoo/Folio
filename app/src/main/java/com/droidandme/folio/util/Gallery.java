package com.droidandme.folio.util;

import java.io.Serializable;

/**
 * Created by pseth.
 */
public class Gallery implements Serializable {

    public enum GALLERY {MEDIA_GALLERY, ONLINE_GALLERY};

    private GALLERY mType = GALLERY.MEDIA_GALLERY; //Type indicates Media Gallery or Online Gallery
    private Album[] mAlbums;

    public Gallery(GALLERY type) {
        this.mType = type;
    }

    public void setAlbums(Album[] albums) {
        this.mAlbums = albums;
    }

    public GALLERY getType() {
        return mType;
    }

    public Album[] getAlbums() {
        return mAlbums;
    }

//    public Album getAlbumByAlbumId(int id) {
//        if (mAlbums != null) {
//
//        }
//    }
//

    public static class Album implements Serializable {
        public int mAlbumId; //Id from the Media Store for the album
        public String mAlbumName; //Bucket name/album name
        public Image[] mImages; //List of images in the album

        public Album(int albumId, String albumName) {
            mAlbumId = albumId;
            mAlbumName = albumName;
        }

        public void setImages(Image[] images) {
            this.mImages = images;
        }

        public int getAlbumId() {
            return mAlbumId;
        }

        public String getAlbumName() {
            return mAlbumName;
        }

        public Image[] getImages() {
            return mImages;
        }

        public static class Image implements Serializable {
            public int mImgId;
            public String mImgPath;

            public Image(int imgId, String imgPath) {
                mImgId = imgId;
                mImgPath = imgPath;
            }

            public int getImgId() {
                return mImgId;
            }

            public String getImgPath() {
                return mImgPath;
            }
        }
    }
}
