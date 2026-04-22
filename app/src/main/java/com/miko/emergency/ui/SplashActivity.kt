package com.miko.emergency.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.miko.emergency.databinding.ActivitySplashBinding
import com.miko.emergency.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateSplash()

        lifecycleScope.launch {
            delay(2500)
            val prefs = PreferenceManager.getInstance(this@SplashActivity)
            if (prefs.isFirstLaunch) {
                startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            }
            finish()
        }
    }

    private fun animateSplash() {
        binding.ivLogo.alpha = 0f
        binding.tvAppName.alpha = 0f
        binding.tvTagline.alpha = 0f

        val logoAnim = ObjectAnimator.ofFloat(binding.ivLogo, View.ALPHA, 0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
        }
        val nameAnim = ObjectAnimator.ofFloat(binding.tvAppName, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 400
        }
        val taglineAnim = ObjectAnimator.ofFloat(binding.tvTagline, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 700
        }

        AnimatorSet().apply {
            playTogether(logoAnim, nameAnim, taglineAnim)
            start()
        }
    }
}
