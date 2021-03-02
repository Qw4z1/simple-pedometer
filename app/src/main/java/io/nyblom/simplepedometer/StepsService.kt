package io.nyblom.simplepedometer

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_STEP_COUNTER
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rebtel.myapplication.R
import java.util.concurrent.TimeUnit


class StepsService : Service(), SensorEventListener {

    private var steps = 0
    private val notificationId = 11011
    private val channelId = "vibeStepNotification"
    private val registeredClients = mutableListOf<Messenger>()

    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    private val sensorManager by lazy {
        ContextCompat.getSystemService(
            applicationContext,
            SensorManager::class.java
        )
    }

    private val sharedPref by lazy {
        applicationContext.getSharedPreferences(
            "stepspref", Context.MODE_PRIVATE
        )
    }

    val messenger: Messenger = Messenger(IncomingHandler(Looper.getMainLooper()))

    private val notification: Notification
        get() {
            val contentIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), 0
            )

            return NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("Counting the steps")
                .setWhen(System.currentTimeMillis())
                .setContentTitle("Step Count:$steps")
                .setContentText("Always be counting the steps")
                .setContentIntent(contentIntent)
                .build()
        }

    // Messages
    private val registerClient = 1
    private val unregisterClient = 2
    private val setValue = 3
    private val stopForeground = 4

    inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                registerClient -> {
                    msg.arg1 = steps
                    registeredClients.add(msg.replyTo)
                }
                unregisterClient -> {
                    msg.arg1 = steps
                    registeredClients.remove(msg.replyTo)
                }
                stopForeground -> {
                    stopForeground(true)
                    stopSelf()
                }
                setValue -> {
                    val deadClients = mutableListOf<Int>()
                    registeredClients.forEachIndexed { index, client ->
                        try {
                            client.send(
                                Message.obtain(
                                    null,
                                    setValue, steps, 0
                                )
                            )
                        } catch (e: RemoteException) {
                            deadClients.add(index)
                        }
                    }
                    deadClients.forEach { registeredClients.removeAt(it) }
                }
                else -> super.handleMessage(msg)
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, notification)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Step Counter",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        with(sharedPref.edit()) {
            putInt("runCount", sharedPref.getInt("runCount", 0) + 1)
            apply()
        }

        sensorManager?.getDefaultSensor(TYPE_STEP_COUNTER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = messenger.binder

    override fun onSensorChanged(event: SensorEvent) {
        steps = event.values[0].toInt()
        with(sharedPref.edit()) {
            putInt("steps", steps)
            apply()
        }
        step()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // noop
    }

    fun step() {
        if (!thresholdReached()) return
        lastPostTime = SystemClock.currentThreadTimeMillis()
        steps += 1
        notificationManager.notify(notificationId, notification)
        try {
            messenger.send(Message().apply {
                what = setValue
                arg1 = steps
            })
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private data class Threshold(val time: Int, val unit: TimeUnit)

    private val postThreshold = Threshold(10, TimeUnit.SECONDS)
    private var lastPostTime = 0L
    private fun thresholdReached(): Boolean {
        return postThreshold.time -
                TimeUnit.SECONDS.convert(
                    SystemClock.currentThreadTimeMillis(),
                    postThreshold.unit
                ) > 0
    }

}