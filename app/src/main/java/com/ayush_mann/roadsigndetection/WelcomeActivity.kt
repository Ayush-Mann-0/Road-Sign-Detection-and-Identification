package com.ayush_mann.roadsigndetection

import android.animation.AnimatorInflater
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_welcome)

        val logo = findViewById<ImageView>(R.id.welcomeLogo)
        val getStartedBtn = findViewById<Button>(R.id.btnGetStarted)

        // Animate logo and button
        val bounce = AnimatorInflater.loadAnimator(this, R.animator.bounce)
        bounce.setTarget(logo)
        bounce.start()

        val scaleUp = AnimatorInflater.loadAnimator(this, R.animator.scale_up)
        scaleUp.setTarget(getStartedBtn)
        scaleUp.start()

        getStartedBtn.setOnClickListener {
            startActivity(Intent(this, OptionsActivity::class.java))
        }
    }
}
