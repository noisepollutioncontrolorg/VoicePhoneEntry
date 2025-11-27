        package com.example.voiceentry

        import android.Manifest
        import android.app.Activity
        import android.content.Intent
        import android.content.pm.PackageManager
        import android.media.ToneGenerator
        import android.media.AudioManager
        import android.os.Bundle
        import android.speech.RecognizerIntent
        import androidx.activity.result.contract.ActivityResultContracts
        import androidx.appcompat.app.AlertDialog
        import androidx.appcompat.app.AppCompatActivity
        import androidx.core.app.ActivityCompat
        import android.widget.Button
        import android.widget.EditText
        import android.widget.TextView
        import java.text.SimpleDateFormat
        import java.util.*

        class MainActivity : AppCompatActivity() {

            private lateinit var digits: List<EditText>
            private lateinit var btnListen: Button
            private lateinit var btnSave: Button
            private lateinit var btnExport: Button
            private lateinit var status: TextView
            private lateinit var db: DBHelper
            private var currentIndex = 0

            private val RECORD_AUDIO_REQ = 123

            private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    matches?.let {
                        handleSpeechResult(it[0])
                    }
                } else {
                    setStatus("Listening cancelled")
                }
            }

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)
                db = DBHelper(this)

                digits = listOf(
                    findViewById(R.id.d1), findViewById(R.id.d2), findViewById(R.id.d3),
                    findViewById(R.id.d4), findViewById(R.id.d5), findViewById(R.id.d6),
                    findViewById(R.id.d7), findViewById(R.id.d8), findViewById(R.id.d9),
                    findViewById(R.id.d10)
                )

                btnListen = findViewById(R.id.btnListen)
                btnSave = findViewById(R.id.btnSave)
                btnExport = findViewById(R.id.btnExport)
                status = findViewById(R.id.status)

                btnListen.setOnClickListener {
                    requestAudioPermissionThenListen()
                }

                btnSave.setOnClickListener {
                    saveIfComplete()
                }

                btnExport.setOnClickListener {
                    exportCsv()
                }

                clearAll()
            }

            private fun requestAudioPermissionThenListen() {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQ)
                } else {
                    startListening()
                }
            }

            override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                if (requestCode == RECORD_AUDIO_REQ) {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        startListening()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Permission required")
                            .setMessage("Microphone permission is required for voice entry.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }

            private fun startListening() {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak digits (0-9) or say the whole number)")
                try {
                    speechLauncher.launch(intent)
                    setStatus("Listening...")
                } catch (e: Exception) {
                    setStatus("Speech recognizer not available")
                }
            }

            private fun handleSpeechResult(result: String) {
                setStatus("Heard: $result")
                val normalized = result.lowercase(Locale.getDefault())
                val tokens = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

                val wordToDigit = mapOf(
                    "zero" to "0", "oh" to "0", "o" to "0",
                    "one" to "1", "two" to "2", "three" to "3", "four" to "4", "for" to "4",
                    "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9"
                )

                val foundDigits = mutableListOf<Char>()
                for (t in tokens) {
                    val singleDigit = when {
                        t.length == 1 && t[0].isDigit() -> t[0]
                        wordToDigit.containsKey(t) -> wordToDigit[t]!![0]
                        else -> null
                    }
                    singleDigit?.let { foundDigits.add(it) }
                }

                if (foundDigits.isEmpty()) {
                    setStatus("No digits detected. Try again.")
                    return
                }

                for (d in foundDigits) {
                    if (currentIndex >= 10) break
                    digits[currentIndex].setText(d.toString())
                    currentIndex++
                }

                if (currentIndex >= 10) {
                    val phone = getPhoneFromFields()
                    if (phone.length == 10) {
                        if (db.exists(phone)) {
                            setStatus("Duplicate: $phone — entry blocked")
                            playTone(false)
                            clearAll()
                        } else {
                            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val success = db.insertPhone(phone, ts)
                            if (success) {
                                setStatus("Saved: $phone")
                                playTone(true)
                                clearAll()
                            } else {
                                setStatus("Save failed")
                            }
                        }
                    } else {
                        setStatus("Invalid length: ${phone.length}")
                    }
                } else {
                    setStatus("Awaiting more digits (${currentIndex}/10)")
                }
            }

            private fun getPhoneFromFields(): String {
                val sb = StringBuilder()
                for (e in digits) {
                    val t = e.text.toString()
                    if (t.isBlank()) return sb.toString()
                    sb.append(t)
                }
                return sb.toString()
            }

            private fun saveIfComplete() {
                val phone = getPhoneFromFields()
                if (phone.length != 10) {
                    setStatus("Please enter 10 digits (current ${phone.length})")
                    return
                }
                if (db.exists(phone)) {
                    setStatus("Duplicate number. Not saved.")
                    playTone(false)
                    clearAll()
                    return
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val success = db.insertPhone(phone, ts)
                if (success) {
                    setStatus("Saved: $phone")
                    playTone(true)
                    clearAll()
                } else {
                    setStatus("Save failed")
                }
            }

            private fun playTone(success: Boolean) {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                if (success) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP)
                } else {
                    tg.startTone(ToneGenerator.TONE_PROP_NACK)
                }
                window.decorView.postDelayed({ tg.release() }, 300)
            }

            private fun exportCsv() {
                val entries = db.getAll()
                if (entries.isEmpty()) {
                    setStatus("No entries to export")
                    return
                }
                val sb = StringBuilder()
                sb.append("Sl No.,Phone,Timestamp
")
                for ((i, e) in entries.withIndex()) {
                    sb.append("${i+1},${e.phone},${e.timestamp}\n")
                }
                val filename = "entries_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                val fos = openFileOutput(filename, MODE_PRIVATE)
                fos.write(sb.toString().toByteArray())
                fos.close()
                setStatus("Exported CSV to app files: $filename")
            }

            private fun clearAll() {
                for (e in digits) e.setText("")
                currentIndex = 0
            }

            private fun setStatus(s: String) {
                status.text = "Status: $s"
            }
        }
