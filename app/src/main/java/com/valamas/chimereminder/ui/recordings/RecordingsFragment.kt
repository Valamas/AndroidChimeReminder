package com.valamas.chimereminder.ui.recordings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valamas.chimereminder.App
import com.valamas.chimereminder.R
import com.valamas.chimereminder.data.UserRecording
import com.valamas.chimereminder.databinding.FragmentRecordingsBinding
import com.valamas.chimereminder.viewmodel.RecordingsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class RecordingsFragment : Fragment() {

    private var _binding: FragmentRecordingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordingsViewModel by viewModels()

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var recordingDurationMs: Long = 0L
    private var isRecording = false

    private var previewPlayer: MediaPlayer? = null

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecordingDialog() else
                Toast.makeText(requireContext(), R.string.recording_mic_denied, Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RecordingsAdapter(
            onPlay = { playRecording(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recordingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recordingsList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordings.collect { list ->
                        adapter.submitList(list)
                        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isPro.collect { pro ->
                        binding.recordFab.visibility = if (pro) View.VISIBLE else View.GONE
                        binding.proPrompt.visibility = if (pro) View.GONE else View.VISIBLE
                        binding.emptyText.visibility =
                            if (!pro || adapter.itemCount > 0) View.GONE else View.VISIBLE
                    }
                }
            }
        }

        binding.recordFab.setOnClickListener { checkMicAndRecord() }

        binding.proPrompt.setOnClickListener {
            (requireActivity().application as App).billingManager.launchPurchase(requireActivity())
        }
    }

    private fun checkMicAndRecord() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startRecordingDialog()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record, null)
        val chronometer = dialogView.findViewById<Chronometer>(R.id.recordTimer)
        val recordToggle = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.recordToggleButton)
        val previewButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.previewRecordingButton)
        val nameInput = dialogView.findViewById<EditText>(R.id.recordingNameInput)

        isRecording = false
        previewButton.isEnabled = false
        chronometer.text = "0:00"

        recordToggle.setOnClickListener {
            if (!isRecording) {
                startRecording()
                chronometer.base = SystemClock.elapsedRealtime()
                chronometer.start()
                recordToggle.text = getString(R.string.recording_stop)
                recordToggle.setIconResource(R.drawable.ic_stop)
                previewButton.isEnabled = false
            } else {
                recordingDurationMs = System.currentTimeMillis() - recordingStartMs
                stopRecording()
                chronometer.stop()
                recordToggle.text = getString(R.string.recording_record)
                recordToggle.setIconResource(R.drawable.ic_mic)
                previewButton.isEnabled = recordingFile != null
            }
        }

        previewButton.setOnClickListener {
            val file = recordingFile ?: return@setOnClickListener
            stopPreview()
            try {
                previewPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    val audioManager = requireContext().getSystemService(AudioManager::class.java)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max * 7 / 15, 0)
                    setOnCompletionListener { it.release(); previewPlayer = null }
                    prepare()
                    start()
                }
            } catch (_: Exception) { }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recording_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                stopRecording()
                stopPreview()
                val name = nameInput.text.toString().trim()
                val file = recordingFile
                if (name.isEmpty() || file == null) {
                    file?.delete()
                    recordingFile = null
                    return@setPositiveButton
                }
                val durationMs = recordingDurationMs
                viewModel.insert(UserRecording(
                    name = name,
                    filename = file.name,
                    durationMs = durationMs
                ))
                recordingFile = null
            }
            .setNegativeButton(R.string.recording_discard) { _, _ ->
                stopRecording()
                stopPreview()
                recordingFile?.delete()
                recordingFile = null
            }
            .setOnDismissListener {
                stopRecording()
                stopPreview()
                recordingFile?.let { f -> if (f.exists()) f.delete() }
                recordingFile = null
            }
            .show()
    }

    private fun startRecording() {
        val dir = File(requireContext().filesDir, "recordings").also { it.mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        recordingFile = file
        recordingStartMs = System.currentTimeMillis()

        recorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            try { prepare(); start(); isRecording = true }
            catch (_: IOException) { recordingFile?.delete(); recordingFile = null }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
    }

    private fun playRecording(recording: UserRecording) {
        val file = File(requireContext().filesDir, "recordings/${recording.filename}")
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.recording_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        stopPreview()
        try {
            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                val audioManager = requireContext().getSystemService(AudioManager::class.java)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max * 7 / 15, 0)
                setOnCompletionListener { it.release(); previewPlayer = null }
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    private fun confirmDelete(recording: UserRecording) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.usageCount(recording)
            val message = if (count > 0)
                getString(R.string.recording_in_use, count)
            else
                getString(R.string.recording_delete_confirm)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    val file = File(requireContext().filesDir, "recordings/${recording.filename}")
                    file.delete()
                    viewModel.delete(recording)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun stopPreview() {
        try { previewPlayer?.stop(); previewPlayer?.release() } catch (_: Exception) { }
        previewPlayer = null
    }

    override fun onDestroyView() {
        stopRecording()
        stopPreview()
        super.onDestroyView()
        _binding = null
    }
}
