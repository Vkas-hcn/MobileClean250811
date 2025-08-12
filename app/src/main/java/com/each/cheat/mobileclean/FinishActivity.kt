package com.each.cheat.mobileclean

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.each.cheat.mobileclean.databinding.ActivityCleanFinishBinding
import com.each.cheat.mobileclean.photo.PhotoActivity

class FinishActivity : AppCompatActivity() {

    private val binding by lazy { ActivityCleanFinishBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        displayResult()
    }

    private fun setupUI() {
        // 返回按钮
        binding.imgBack.setOnClickListener {
            navigateToHome()
        }

        binding.llImg.setOnClickListener {
            startActivity(Intent(this, PhotoActivity::class.java))
            finish()
        }

        binding.llFolder.setOnClickListener {
            startActivity(Intent(this, FileActivity::class.java))
            finish()
        }
    }

    private fun displayResult() {
        val cleanedSize = intent.getLongExtra("cleaned_size", 0L)
        val formattedSize = StorageUtils.formatBytes(cleanedSize)

        binding.tvJundTip.text = "Saved $formattedSize space for you"
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        navigateToHome()
    }
}