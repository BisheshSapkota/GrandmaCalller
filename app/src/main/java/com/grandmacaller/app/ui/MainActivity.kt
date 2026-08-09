package com.grandmacaller.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.grandmacaller.app.R
import com.grandmacaller.app.data.NameMatcher
import com.grandmacaller.app.data.Relative
import com.grandmacaller.app.data.RelativeStore
import com.grandmacaller.app.databinding.ActivityMainBinding
import com.grandmacaller.app.service.CallSession
import com.grandmacaller.app.service.CallTapAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var relatives: List<Relative>

    private val micPermissionRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        relatives = RelativeStore.load(this)

        binding.micButton.setOnClickListener {
            if (relatives.isEmpty()) {
                Toast.makeText(
                    this,
                    "Add relatives first via ⚙ Manage Relatives",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            ensureMicPermissionThenListen()
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (!isAccessibilityServiceEnabled()) {
            promptEnableAccessibilityService()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case relatives were edited in Settings
        relatives = RelativeStore.load(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${CallTapAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (component in splitter) {
            if (component.equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }

    private fun promptEnableAccessibilityService() {
        AlertDialog.Builder(this)
            .setTitle("One-time setup needed")
            .setMessage(
                "So the app can press the call button for her automatically, " +
                    "turn on its Accessibility permission.\n\n" +
                    "On the next screen: find \"${getString(R.string.app_name)}\" " +
                    "in the list and turn it on."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Skip for now", null)
            .show()
    }

    private fun ensureMicPermissionThenListen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startListening()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermissionRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        binding.statusText.text = getString(R.string.listening)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull().orEmpty()
                    handleTranscript(transcript)
                }

                override fun onError(error: Int) {
                    binding.statusText.text = getString(R.string.not_understood)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Nepali (Nepal) locale. Falls back to device default if unsupported.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ne-NP")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleTranscript(transcript: String) {
        when (val result = NameMatcher.findBestMatch(transcript, relatives)) {
            is NameMatcher.MatchResult.NoConfidentMatch -> {
                // Previously this silently guessed the closest-scoring
                // relative even when the match was weak or two people were
                // equally plausible -- that's how a garbled "Upakar" ended
                // up dialing Suntali. Now: if we're not confident, just ask
                // again instead of calling someone.
                binding.statusText.text = getString(R.string.not_understood)
            }
            is NameMatcher.MatchResult.Found -> confirmThenCall(result.relative)
        }
    }

    /**
     * Shows who we're about to call and gives a few seconds to cancel
     * before actually opening Messenger and arming the auto-tap. This is
     * the safety net for speech-recognition mistakes -- previously a bad
     * transcript could dial the wrong person with zero chance to stop it.
     */
    private fun confirmThenCall(relative: Relative) {
        var confirmed = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_call_title, relative.displayName))
            .setMessage(getString(R.string.confirm_call_body))
            .setPositiveButton(getString(R.string.confirm_call_now)) { _, _ ->
                confirmed = true
                openMessengerCall(relative)
            }
            .setNegativeButton(getString(R.string.confirm_cancel)) { _, _ ->
                binding.statusText.text = getString(R.string.tap_to_speak)
            }
            .setCancelable(false)
            .create()
        dialog.show()

        // Auto-confirm after a few seconds so grandma doesn't have to read
        // and tap a button herself -- but leaves a real window to cancel if
        // it's about to call the wrong person.
        object : android.os.CountDownTimer(4000, 1000) {
            override fun onTick(msUntilFinished: Long) {
                if (!dialog.isShowing) cancel()
            }
            override fun onFinish() {
                if (dialog.isShowing && !confirmed) {
                    dialog.dismiss()
                    openMessengerCall(relative)
                }
            }
        }.start()
    }

    /**
     * Deep-links into the Messenger conversation with this person.
     * NOTE: Messenger does not expose a public deep link that starts the CALL
     * directly — this opens their chat thread, and grandma (or a one-time
     * accessibility-assisted tap, see service/CallTapAccessibilityService)
     * still needs one tap on the call icon. Test this on the target device;
     * Messenger's deep link behavior has changed across versions.
     */
    private fun openMessengerCall(relative: Relative) {
        binding.statusText.text = getString(R.string.opening_messenger, relative.displayName)

        // Arm the accessibility service for exactly this one call attempt.
        // It disarms itself after a single tap or after a short timeout, so
        // it can never fire again (e.g. on hangup) without a new voice
        // command re-arming it here.
        CallSession.arm(relative.messengerId)

        val uri = Uri.parse("https://m.me/${relative.messengerId}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.facebook.orca") // Messenger's package name
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Messenger not installed, or deep link rejected — fall back to browser
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
