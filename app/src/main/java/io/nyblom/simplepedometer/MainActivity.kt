package io.nyblom.simplepedometer

import android.Manifest.permission.ACTIVITY_RECOGNITION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rebtel.myapplication.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private val sharedPref by lazy {
        applicationContext.getSharedPreferences(
            "stepspref", Context.MODE_PRIVATE
        )
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.startButton.setOnClickListener {
            startForegroundService(Intent(this, StepsService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions( arrayOf(ACTIVITY_RECOGNITION), 10)

        }
        val steps = sharedPref.getInt("steps", 0)
        val runCount = sharedPref.getInt("runCount", 0)
        binding.runCountText.text = "Run Count $runCount"
        binding.stepsText.text = "Steps: $steps"
    }
}