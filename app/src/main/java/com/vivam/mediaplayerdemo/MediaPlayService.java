package com.vivam.mediaplayerdemo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import static android.os.Handler.*;

/**
 * Created by vivam on 1/20/16.
 */
public class MediaPlayService extends Service
        implements MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener,
        MediaPlayer.OnCompletionListener {

    private static final String LOG_TAG = "MediaPlayService";

    public static final String ACTION_PLAY = "com.vivam.action.PLAY";
    public static final String ACTION_PAUSE = "com.vivam.action.PAUSE";
    public static final String ACTION_RESUME = "com.vivam.action.RESUME";
    public static final String ACTION_PREVIOUS = "com.vivam.action.PREVIOUS";
    public static final String ACTION_NEXT = "com.vivam.action.NEXT";
    public static final String ACTION_STOP = "com.vivam.action.STOP";
    public static final String ACTION_SEEK = "com.vivam.action.SEEK";
    public static final String ACTION_PROGRESS = "com.vivam.action.PROGRESS";

    public static final String EXTRA_MUSIC = "music";
    public static final String EXTRA_MUSIC_LIST = "musicList";
    public static final String EXTRA_LIST_CHANGED = "listChanged";
    public static final String EXTRA_SEEK_PROGRESS = "seekProgress";
    public static final String EXTRA_PROGRESS = "progress";

    private MediaPlayer mMediaPlayer = null;

    private AudioManager mAudioManager;

    private MusicBean mCurrentMusic;
    private ArrayList<MusicBean> mMusicList;
    private boolean mListChanged;

    private HandlerThread mPlayThread;
    private Handler mPlayHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        initMediaPlayer();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // could not get audio focus.
        }

        mPlayThread = new HandlerThread(LOG_TAG + "-PlayThread");

        mPlayThread.start();
        mPlayHandler = new Handler(mPlayThread.getLooper(), mCallback);

        mMusicList = new ArrayList<MusicBean>();
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    mCurrentMusic = (MusicBean) intent.getSerializableExtra(EXTRA_MUSIC);
                    mListChanged = intent.getBooleanExtra(EXTRA_LIST_CHANGED, false);
                    if (mListChanged) {
                        if (mMusicList == null) {
                            mMusicList = new ArrayList<MusicBean>();
                        }
                        mMusicList.clear();
                        ArrayList<MusicBean> list = (ArrayList<MusicBean>)
                                intent.getSerializableExtra(EXTRA_MUSIC_LIST);
                        if (list != null) {
                            mMusicList.addAll(list);
                        }
                    }
                    if (mCurrentMusic != null) {
                        enqueuePlay();
                    }
                    break;

                case ACTION_PAUSE:
                    pausePlaying();
                    break;

                case ACTION_RESUME:
                    resumePlaying();
                    break;

                case ACTION_PREVIOUS:
                    playPrevious();
                    break;

                case ACTION_NEXT:
                    playNext();
                    break;

                case ACTION_STOP:
                    stopPlaying();
                    break;

                case ACTION_SEEK:
                    int progress = intent.getIntExtra(EXTRA_SEEK_PROGRESS, 0);
                    seekPlaying(progress);
                    break;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playNext();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mMediaPlayer == null) {
                    initMediaPlayer();
                } else if (!mMediaPlayer.isPlaying()) {
                    mMediaPlayer.start();
                }
                mMediaPlayer.setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback
                // and release media player
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
                mMediaPlayer = null;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but is's ok to keep playing
                // at an attenuated level
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.setVolume(0.1f, 0.1f);
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        stopPlaying();
        super.onDestroy();
    }

    private void enqueuePlay() {
        mPlayHandler.removeMessages(MSG_PLAY);
        mPlayHandler.obtainMessage(MSG_PLAY, mCurrentMusic).sendToTarget();
    }

    private static final int MSG_PLAY = 1;
    private static final int MSG_PROGRESS = 2;
    private static final int MSG_CHANGED = 3;

    private static final long DELAY_MILLIS = 1000;

    private Callback mCallback = new Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            switch (msg.what) {
                case MSG_PLAY:
                    MusicBean music = (MusicBean) msg.obj;
                    startPlaying(music.getId());
                    break;

                case MSG_PROGRESS:
                    mPlayHandler.removeMessages(MSG_PROGRESS);
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        Intent intent = new Intent(ACTION_PROGRESS);
                        intent.putExtra(EXTRA_PROGRESS, getProgress());
                        sendBroadcast(intent);
                        Message newMsg = mPlayHandler.obtainMessage(MSG_PROGRESS);
                        mPlayHandler.sendMessageDelayed(newMsg, DELAY_MILLIS);
                    }
                    break;

                case MSG_CHANGED:
                    Intent intent = new Intent(ACTION_PROGRESS);
                    intent.putExtra(EXTRA_MUSIC, mCurrentMusic);
                    sendBroadcast(intent);
                    break;
            }

            return true;
        }
    };

    private int getProgress() {
        return mMediaPlayer.getCurrentPosition() * 100 / mMediaPlayer.getDuration();
    }

    private void startPlaying(long id) {
        if (mMediaPlayer == null) {
            initMediaPlayer();
        } else {
            mMediaPlayer.reset();
        }

        Uri uri = MediaUtils.uriWithAppendedId(id);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mMediaPlayer.setDataSource(this, uri);
            mMediaPlayer.prepare();
            mMediaPlayer.start();

            Message msg = mPlayHandler.obtainMessage(MSG_PROGRESS);
            mPlayHandler.sendMessageDelayed(msg, DELAY_MILLIS);

        } catch (IOException e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private void pausePlaying() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }
    }

    private void resumePlaying() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mPlayHandler.obtainMessage(MSG_PROGRESS).sendToTarget();
        }
    }

    private void playPrevious() {
        if (mCurrentMusic != null && mMusicList != null) {
            int currentIndex = getCurrentIndex();
            if (currentIndex == -1) {
                return;
            }

            if (currentIndex == 0) {
                currentIndex = mMusicList.size();
            }
            mCurrentMusic = mMusicList.get(--currentIndex);
            mPlayHandler.obtainMessage(MSG_CHANGED).sendToTarget();
            enqueuePlay();
        }
    }

    private void playNext() {
        if (mCurrentMusic != null && mMusicList != null) {
            int currentIndex = getCurrentIndex();
            if (currentIndex == -1) {
                return;
            }

            if (++currentIndex == mMusicList.size()) {
                currentIndex = 0;
            }
            mCurrentMusic = mMusicList.get(currentIndex);
            mPlayHandler.obtainMessage(MSG_CHANGED).sendToTarget();
            enqueuePlay();
        }
    }

    private void stopPlaying() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mPlayThread.quit();
    }

    private void seekPlaying(int progress) {
        if (progress < 0) {
            progress = 0;
        }
        if (mMediaPlayer == null) {
            initMediaPlayer();
        }
        if (mCurrentMusic != null) {
            int msec = (int) (mCurrentMusic.getDuration() * progress / 100);
            try {
                mMediaPlayer.seekTo(msec);
                mMediaPlayer.start();
                mPlayHandler.obtainMessage(MSG_PROGRESS).sendToTarget();
            } catch (IllegalStateException e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private int getCurrentIndex() {
        for (MusicBean m : mMusicList) {
            if (mCurrentMusic.getId() == m.getId()) {
                return mMusicList.indexOf(m);
            }
        }
        return -1;
    }
}
