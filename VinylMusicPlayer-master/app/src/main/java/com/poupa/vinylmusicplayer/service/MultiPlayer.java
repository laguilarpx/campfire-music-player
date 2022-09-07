package com.poupa.vinylmusicplayer.service;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.poupa.vinylmusicplayer.R;
import com.poupa.vinylmusicplayer.service.playback.Playback;
import com.poupa.vinylmusicplayer.util.PreferenceUtil;

/**
 * @author Andrew Neal, Karim Abou Zeid (kabouzeid)
 */
public class MultiPlayer implements Playback, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    public static final String TAG = MultiPlayer.class.getSimpleName();

    private MediaPlayer mCurrentMediaPlayer = new MediaPlayer();
    private MediaPlayer mNextMediaPlayer;

    private final Context context;
    @Nullable
    private Playback.PlaybackCallbacks callbacks;

    private boolean mIsInitialized = false;

    private float duckingFactor = 1;
    private float replaygain = Float.NaN;

    /**
     * Constructor of <code>MultiPlayer</code>
     */
    public MultiPlayer(final Context context) {
        this.context = context;
        mCurrentMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
    }

    /**
     * @param path The path of the file, or the http/rtsp URL of the stream
     *             you want to play
     * @return True if the <code>player</code> has been prepared and is
     * ready to play, false otherwise
     */
    @Override
    public boolean setDataSource(@NonNull final String path) {
        mIsInitialized = false;
        mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, path);
        if (mIsInitialized) {
            setNextDataSource(null);
        }
        return mIsInitialized;
    }

    /**
     * @param player The {@link MediaPlayer} to use
     * @param path   The path of the file, or the http/rtsp URL of the stream
     *               you want to play
     * @return True if the <code>player</code> has been prepared and is
     * ready to play, false otherwise
     */
    private boolean setDataSourceImpl(@NonNull final MediaPlayer player, @NonNull final String path) {
        if (context == null) {
            return false;
        }
        try {
            player.reset();
            player.setOnPreparedListener(null);
            if (path.startsWith("content://")) {
                player.setDataSource(context, Uri.parse(path));
            } else {
                player.setDataSource(path);
            }
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.prepare();
        } catch (Exception e) {
            return false;
        }
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        context.sendBroadcast(intent);
        return true;
    }

    /**
     * Set the MediaPlayer to start when this MediaPlayer finishes playback.
     *
     * @param path The path of the file, or the http/rtsp URL of the stream
     *             you want to play
     */
    @Override
    public void setNextDataSource(@Nullable final String path) {
        if (context == null) {
            return;
        }
        try {
            mCurrentMediaPlayer.setNextMediaPlayer(null);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "Next media player is current one, continuing");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Media player not initialized!");
            return;
        }
        if (mNextMediaPlayer != null) {
            mNextMediaPlayer.release();
            mNextMediaPlayer = null;
        }
        if (path == null) {
            return;
        }
        if (PreferenceUtil.getInstance().gaplessPlayback()) {
            mNextMediaPlayer = new MediaPlayer();
            mNextMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            if (setDataSourceImpl(mNextMediaPlayer, path)) {
                try {
                    mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
                } catch (@NonNull IllegalArgumentException | IllegalStateException e) {
                    Log.e(TAG, "setNextDataSource: setNextMediaPlayer()", e);
                    if (mNextMediaPlayer != null) {
                        mNextMediaPlayer.release();
                        mNextMediaPlayer = null;
                    }
                }
            } else {
                if (mNextMediaPlayer != null) {
                    mNextMediaPlayer.release();
                    mNextMediaPlayer = null;
                }
            }
        }
    }

    /**
     * Sets the callbacks
     *
     * @param callbacks The callbacks to use
     */
    @Override
    public void setCallbacks(@Nullable Playback.PlaybackCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * @return True if the player is ready to go, false otherwise
     */
    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Starts or resumes playback.
     */
    @Override
    public void start() {
        try {
            mCurrentMediaPlayer.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Resets the MediaPlayer to its uninitialized state.
     */
    @Override
    public void stop() {
        mCurrentMediaPlayer.reset();
        mIsInitialized = false;
    }

    /**
     * Releases resources associated with this MediaPlayer object.
     */
    @Override
    public void release() {
        stop();
        mCurrentMediaPlayer.release();
        if (mNextMediaPlayer != null) {
            mNextMediaPlayer.release();
        }
    }

    /**
     * Pauses playback. Call start() to resume.
     */
    @Override
    public void pause() {
        try {
            mCurrentMediaPlayer.pause();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether the MultiPlayer is playing.
     */
    @Override
    public boolean isPlaying() {
        return mIsInitialized && mCurrentMediaPlayer.isPlaying();
    }

    /**
     * Gets the duration of the file.
     *
     * @return The duration in milliseconds
     */
    @Override
    public int duration() {
        if (!mIsInitialized) {
            return -1;
        }
        try {
            return mCurrentMediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds
     */
    @Override
    public int position() {
        if (!mIsInitialized) {
            return -1;
        }
        try {
            return mCurrentMediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Gets the current playback position.
     *
     * @param whereto The offset in milliseconds from the start to seek to
     */
    @Override
    public void seek(final int whereto) {
        try {
            mCurrentMediaPlayer.seekTo(whereto);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void setVolume(final float vol) {
        try {
            mCurrentMediaPlayer.setVolume(vol, vol);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId The audio session ID
     */
    @Override
    public boolean setAudioSessionId(final int sessionId) {
        try {
            mCurrentMediaPlayer.setAudioSessionId(sessionId);
            return true;
        } catch (@NonNull IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns the audio session ID.
     *
     * @return The current audio session ID.
     */
    @Override
    public int getAudioSessionId() {
        return mCurrentMediaPlayer.getAudioSessionId();
    }

    public void setReplayGain(float replaygain) {
        this.replaygain = replaygain;
        updateVolume();
    }

    public void setDuckingFactor(float duckingFactor) {
        this.duckingFactor = duckingFactor;
        updateVolume();
    }

    private void updateVolume() {
        float volume = 1.0f;
        if (!Float.isNaN(replaygain)) {
            volume = replaygain;
        }

        volume *= duckingFactor;

        setVolume(volume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onError(final MediaPlayer mp, final int what, final int extra) {
        if (mp == mCurrentMediaPlayer) {
            if (context != null) {
                Toast.makeText(context, context.getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
            }
            mIsInitialized = false;
            mCurrentMediaPlayer.release();
            if (mNextMediaPlayer != null) {
                mCurrentMediaPlayer = mNextMediaPlayer;
                mIsInitialized = true;
                mNextMediaPlayer = null;
                if (callbacks != null) {
                    callbacks.onTrackWentToNext();
                }
            } else {
                mCurrentMediaPlayer = new MediaPlayer();
                mCurrentMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            }
        } else {
            mIsInitialized = false;
            mCurrentMediaPlayer.release();
            mCurrentMediaPlayer = new MediaPlayer();
            mCurrentMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            if (context != null) {
                Toast.makeText(context, context.getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCompletion(final MediaPlayer mp) {
        if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
            mIsInitialized = false;
            mCurrentMediaPlayer.release();
            mCurrentMediaPlayer = mNextMediaPlayer;
            mIsInitialized = true;
            mNextMediaPlayer = null;
            if (callbacks != null)
                callbacks.onTrackWentToNext();
        } else {
            if (callbacks != null)
                callbacks.onTrackEnded();
        }
    }
}
