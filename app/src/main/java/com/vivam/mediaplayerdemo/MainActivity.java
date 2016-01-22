package com.vivam.mediaplayerdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener,
        View.OnClickListener {

    private static String LOG_TAG = "MainActivity";

    private RecyclerView mRecyclerView;
    private TextView mTitleTextView;
    private TextView mDurationTextView;
    private ImageButton mPauseButton;
    private ImageButton mPreviousButton;
    private ImageButton mNextButton;
    private SeekBar mSeekBar;

    private MusicAdapter mAdapter;

    private BroadcastReceiver mReceiver;

    private boolean mIsDragging = false;

    private ArrayList<MusicBean> mData;

    private boolean mInitList = true;

    private MusicBean mCurrentMusic;

    private boolean mIsPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        startService(new Intent(this, MediaPlayService.class));
        mReceiver = new ProgressBroadcastReceiver();

        mData = MediaUtils.getLocalMusicList(this);

        initView();
    }

    private void initView() {
        mTitleTextView = (TextView) findViewById(R.id.title);
        mDurationTextView = (TextView) findViewById(R.id.duration);

        mPauseButton = (ImageButton) findViewById(R.id.btn_pause);
        mPauseButton.setOnClickListener(this);

        mPreviousButton = (ImageButton) findViewById(R.id.btn_previous);
        mPreviousButton.setOnClickListener(this);

        mNextButton = (ImageButton) findViewById(R.id.btn_next);
        mNextButton.setOnClickListener(this);

        mSeekBar = (SeekBar) findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(this);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MusicAdapter();
        mRecyclerView.setAdapter(mAdapter);

        enableAll(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, new IntentFilter(MediaPlayService.ACTION_PROGRESS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private void enableAll(boolean enable) {
        mPauseButton.setEnabled(enable);
        mPreviousButton.setEnabled(enable);
        mNextButton.setEnabled(enable);
        mTitleTextView.setVisibility(enable ? View.VISIBLE : View.GONE);
        mDurationTextView.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private void updateProgress(int progress) {
        mSeekBar.setProgress(progress);
        StringBuilder sb = new StringBuilder();
        if (mCurrentMusic != null) {
            long current = progress * mCurrentMusic.getDuration() / 100;
            sb.append(MediaUtils.dateFormat(current))
                    .append(" / ")
                    .append(MediaUtils.dateFormat(mCurrentMusic.getDuration()));
            mDurationTextView.setText(sb.toString());
        }
    }

    @Override
    public void onClick(View v) {
        String action = null;

        switch (v.getId()) {
            case R.id.btn_pause:
                if (mIsPaused) {
                    mIsPaused = false;
                    mPauseButton.setImageResource(R.drawable.ic_pause_outline);
                    action = MediaPlayService.ACTION_RESUME;
                } else {
                    mIsPaused = true;
                    mPauseButton.setImageResource(R.drawable.ic_play_outline);
                    action = MediaPlayService.ACTION_PAUSE;
                }
                break;

            case R.id.btn_previous:
                syncView();
                action = MediaPlayService.ACTION_PREVIOUS;
                break;

            case R.id.btn_next:
                syncView();
                action = MediaPlayService.ACTION_NEXT;
                break;
        }

        if (action != null) {
            Intent intent = new Intent(this, MediaPlayService.class);
            intent.setAction(action);
            startService(intent);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            Log.d(LOG_TAG, "onProgressChanged: progress = " + progress);
            Intent intent = new Intent(this, MediaPlayService.class);
            intent.setAction(MediaPlayService.ACTION_SEEK);
            intent.putExtra(MediaPlayService.EXTRA_SEEK_PROGRESS, progress);
            startService(intent);
            updateProgress(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mIsDragging = true;
        Intent intent = new Intent(this, MediaPlayService.class);
        intent.setAction(MediaPlayService.ACTION_PAUSE);
        startService(intent);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mIsDragging = false;
    }

    private void syncView() {
        mTitleTextView.setText(mCurrentMusic.getTitle());
        updateProgress(0);

        mPauseButton.setImageResource(R.drawable.ic_pause_outline);
        mIsPaused = false;
    }

    private class ProgressBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = intent.getIntExtra(MediaPlayService.EXTRA_PROGRESS, 0);
            if (progress > 0) {
                updateProgress(progress);
            }

            MusicBean music = (MusicBean) intent.getSerializableExtra(MediaPlayService.EXTRA_MUSIC);
            if (music != null) {
                mCurrentMusic = music;
                syncView();
            }
        }
    }

    private class MusicAdapter extends RecyclerView.Adapter<ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_music, null));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            final MusicBean item = mData.get(position);
            if (item != null) {
                holder.titleTv.setText(item.getTitle());
                holder.durationTv.setText(MediaUtils.dateFormat(item.getDuration()));
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCurrentMusic = item;

                        syncView();

                        Intent intent = new Intent(MainActivity.this, MediaPlayService.class);
                        intent.setAction(MediaPlayService.ACTION_PLAY);
                        intent.putExtra(MediaPlayService.EXTRA_MUSIC, item);
                        intent.putExtra(MediaPlayService.EXTRA_LIST_CHANGED, mInitList);
                        if (mInitList) {
                            mInitList = false;
                            intent.putExtra(MediaPlayService.EXTRA_MUSIC_LIST, mData);
                        }
                        startService(intent);
                        enableAll(true);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return mData != null ? mData.size() : 0;
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        TextView titleTv;
        TextView durationTv;

        public ViewHolder(View itemView) {
            super(itemView);
            titleTv = (TextView) itemView.findViewById(R.id.title);
            durationTv = (TextView) itemView.findViewById(R.id.duration);
        }
    }
}
