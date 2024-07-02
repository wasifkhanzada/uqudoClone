package com.example.practice_android_4

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.IBinder

class SoundPoolService : Service() {

    private lateinit var soundPool: SoundPool
    private var shotSoundId: Int = 0
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate() {
        super.onCreate()

        // Initialize SoundPool based on the Android version
//        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            val audioAttributes = AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_GAME)
//                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                .build()
//
//            SoundPool.Builder()
//                .setMaxStreams(1)
//                .setAudioAttributes(audioAttributes)
//                .build()
//        } else {
//            SoundPool(1, android.media.AudioManager.STREAM_MUSIC, 0)
//        }

        mediaPlayer = MediaPlayer.create(this, R.raw.beep_sound_1) // Replace 'sound' with your sound file

        // Load the shot sound
//        shotSoundId = soundPool.load(this, R.raw.beep_sound_1, 1)
    }

//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Handle the intent to play the sound
//        intent?.getStringExtra("ACTION")?.let {
//            if (it == "PLAY_SHOT_SOUND") {
//                playShotSound()
//            }
//        }
//        return START_NOT_STICKY
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaPlayer.start()
        return START_STICKY
    }

    private fun playShotSound() {
        soundPool.play(shotSoundId, 1f, 1f, 0, 0, 1f)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
//        soundPool.release()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}