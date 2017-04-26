package com.commontime.plugin.notification.gcm;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by gjm on 25/04/17.
 */
public class NotificationMediaPlayer {
    private static NotificationMediaPlayer ourInstance = new NotificationMediaPlayer();
    private Context context;

    public static NotificationMediaPlayer getInstance() {
        return ourInstance;
    }

    private NotificationMediaPlayer() {
        players = new HashMap<Integer, NotPlayer>();
    }

    private Map<Integer, NotPlayer> players;

    private class NotPlayer {
        public int id;
        public MediaPlayer player;
        public File file;
        public int oldVolume;

        public NotPlayer(int id) {
            this.id = id;
            player = new MediaPlayer();
        }
    }

    public void play(Context context, String file, int volumePercentage, boolean loop, int notId) throws IOException {

        this.context = context;
        MediaPlayer player = null;
        if (!players.containsKey(notId)) {
            players.put(notId, new NotPlayer(notId));
        }
        player = players.get(notId).player;

        if( player.isPlaying() ) {
            player.stop();
        }

        InputStream noise = context.getAssets().open(file);
        File outputFile = File.createTempFile("noise", ".wav", context.getExternalCacheDir());
        players.get(notId).file = outputFile;

        streamToFile(noise, outputFile);

        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int oldVolume = manager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        players.get(notId).oldVolume = oldVolume;

        int streamMaxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        int useVolume = streamMaxVolume * volumePercentage / 100;
        manager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, useVolume, AudioManager.FLAG_PLAY_SOUND);

        final Uri uri = Uri.fromFile(outputFile);

        if(!player.isPlaying()) {
            player.reset();
            player.setDataSource(context, uri);
            player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            player.setLooping(loop);
            player.prepare();
            player.start();
        }
    }

    public void stop(int notId) {
        if( players.containsKey(notId)) {
            MediaPlayer player = players.get(notId).player;

            if( player != null && player.isPlaying() ) {
                player.stop();
            }

            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            manager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, players.get(notId).oldVolume, AudioManager.FLAG_PLAY_SOUND);
            players.get(notId).file.delete();
            players.remove(notId);
        }
    }

    private void streamToFile( InputStream input, File output ) throws IOException {
        try {
            OutputStream os = new FileOutputStream(output, false);
            try {
                try {
                    byte[] buffer = new byte[4 * 1024]; // or other buffer size
                    int read;

                    while ((read = input.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    os.flush();
                } finally {
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace(); // handle exception, define IOException and others
            }
        } finally {
            input.close();
        }
    }
}
