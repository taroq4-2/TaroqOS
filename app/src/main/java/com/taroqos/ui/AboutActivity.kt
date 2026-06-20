package com.taroqos.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.taroqos.R
import com.taroqos.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var b: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        supportActionBar?.apply {
            title = "حول Taroq OS"
            setDisplayHomeAsUpEnabled(true)
        }

        b.btnWebsite.setOnClickListener {
            openUrl("https://os73.com")
        }

        b.btnInstagram.setOnClickListener {
            openUrl("https://instagram.com/Taro0q")
        }

        b.btnGithub.setOnClickListener {
            openUrl("https://github.com/taroq4-2/TaroqOS")
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
