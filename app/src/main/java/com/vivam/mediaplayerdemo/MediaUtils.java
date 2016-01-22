package com.vivam.mediaplayerdemo;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.SimpleFormatter;

/**
 * Created by vivam on 1/20/16.
 */
public class MediaUtils {

    public static ArrayList<MusicBean> getLocalMusicList(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = cr.query(uri, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        ArrayList<MusicBean> list = new ArrayList<MusicBean>();
        if (!cursor.moveToFirst()) {
            return list;
        }

        final int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        final int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        final int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        do {
            MusicBean music = new MusicBean();
            music.setId(cursor.getLong(idColumn));
            music.setTitle(cursor.getString(titleColumn));
            music.setDuration(cursor.getLong(durationColumn));

            list.add(music);
        } while (cursor.moveToNext());
        return list;
    }

    public static Uri uriWithAppendedId(long id) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public static String dateFormat(long millis) {
        String formatStr;
        if (millis < 3600000) {
            formatStr = "mm:ss";
        } else {
            formatStr = "HH:mm:ss";
        }
        return new SimpleDateFormat(formatStr).format(new Date(millis));
    }
}
