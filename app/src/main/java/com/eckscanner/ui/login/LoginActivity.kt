package com.eckscanner.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.databinding.ActivityLoginBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.eckscanner.ui.home.HomeActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already configured, go to home
        val config = ApiClient.getConfig(this)
        if (config != null) {
            ApiClient.initialize(config.first, config.second)
            startHome()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener { connect() }
    }

    private fun connect() {
        val url = binding.editUrl.text.toString().trim()
        val token = binding.editToken.text.toString().trim()

        if (url.isEmpty() || token.isEmpty()) {
            showError("Completa ambos campos")
            return
        }

        binding.btnConnect.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.txtError.visibility = View.GONE

        ApiClient.initialize(url, token)

        lifecycleScope.launch {
            try {
                // Test connection by fetching warehouses
                val response = ApiClient.getService().getWarehouses()
                if (response.isSuccessful) {
                    ApiClient.saveConfig(this@LoginActivity, url, token)
                    DataWedgeReceiver.configureDataWedge(this@LoginActivity)
                    startHome()
                } else {
                    val code = response.code()
                    showError("Error $code: ${if (code == 401) "Token invalido" else if (code == 403) "API deshabilitada" else "Error de servidor"}")
                }
            } catch (e: Exception) {
                showError("No se pudo conectar: ${e.message}")
            } finally {
                binding.btnConnect.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showError(msg: String) {
        binding.txtError.text = msg
        binding.txtError.visibility = View.VISIBLE
    }

    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
