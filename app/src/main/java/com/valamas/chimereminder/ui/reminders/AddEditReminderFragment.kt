package com.valamas.chimereminder.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.valamas.chimereminder.App
import com.valamas.chimereminder.R
import com.valamas.chimereminder.data.Reminder
import com.valamas.chimereminder.data.RepeatType
import com.valamas.chimereminder.databinding.FragmentAddEditReminderBinding
import com.valamas.chimereminder.viewmodel.RemindersViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

class AddEditReminderFragment : Fragment() {

    private data class BundledChime(val label: String, val resName: String)

    // Default first (= Ripple), rest alphabetical.
    private val bundledChimes = listOf(
        BundledChime("Default",    "chime_ripple"), // always first
        BundledChime("Bird",       "chime_bird"),
        BundledChime("Bird Song",  "chime_birdsong"),
        BundledChime("Cricket",    "chime_cricket"),
        BundledChime("Dolphin",    "chime_dolphin"),
        BundledChime("Doorbell",   "chime_doorbell"),
        BundledChime("Fire",       "chime_fire"),
        BundledChime("Geiger",     "chime_geiger"),
        BundledChime("High Pitch", "chime_high"),
        BundledChime("Horse",      "chime_horse"),
        BundledChime("Kettle",     "chime_kettle"),
        BundledChime("Laser",      "chime_laser"),
        BundledChime("Mbira",      "chime_mbira"),
        BundledChime("Morse",      "chime_morse"),
        BundledChime("Music Box",  "chime_musicbox"),
        BundledChime("Photon",     "chime_photon"),
        BundledChime("Pulse",      "chime_pulse"),
        BundledChime("Radar",      "chime_radar"),
        BundledChime("Retro",      "chime_retro"),
        BundledChime("Water Drip", "chime_drip"),
        BundledChime("Zap",        "chime_zap"),
    )

    private var _binding: FragmentAddEditReminderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by viewModels()
    private val args: AddEditReminderFragmentArgs by navArgs()
    private var editingReminder: Reminder? = null
    private var selectedSoundUri: String = "raw:chime_ripple"
    private var selectedSoundLabel: String = "Default"
    private var previewPlayer: MediaPlayer? = null
    private var isCountdownMode = false

    private val isPro get() =
        (requireActivity().application as App).billingManager.isPro.value

    private var isDirty = false
    private var isLoading = true

    // Snapshot of the form state at load-complete time. Used as a fallback to detect
    // dirty state by direct comparison if the listener-based isDirty flag misses changes.
    private var originalSnapshot: String? = null

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data
                    ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    selectedSoundUri = uri.toString()
                    selectedSoundLabel = RingtoneManager.getRingtone(requireContext(), uri)
                        ?.getTitle(requireContext()) ?: getString(R.string.sound_custom)
                } else {
                    selectedSoundUri = ""
                    selectedSoundLabel = ""
                }
                markDirty()
                updateSoundButton()
            }
        }

    private val audioFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedSoundUri = it.toString()
                selectedSoundLabel = getContentDisplayName(it) ?: getString(R.string.sound_custom)
                markDirty()
                updateSoundButton()
            }
        }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) audioFilePicker.launch(arrayOf("audio/*"))
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        binding.timePicker.setIs24HourView(true)

        binding.repeatGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.customDaysGroup.visibility =
                if (checkedId == R.id.repeatCustom) View.VISIBLE else View.GONE
            markDirty()
        }

        binding.modeToggleGroup.check(R.id.modeClockButton)
        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) setMode(checkedId == R.id.modeCountdownButton)
        }

        binding.soundButton.setOnClickListener { showSoundPicker() }
        binding.saveButton.setOnClickListener { save() }

        // Dirty listeners
        binding.timePicker.setOnTimeChangedListener { _, _, _ -> markDirty() }
        binding.volumeSlider.addOnChangeListener { _, _, fromUser -> if (fromUser) markDirty() }
        binding.volumeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                previewCurrentSound(slider.value.toInt())
            }
        })
        val dirtyWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = markDirty()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.labelInput.addTextChangedListener(dirtyWatcher)
        binding.countdownHours.addTextChangedListener(dirtyWatcher)
        binding.countdownMinutes.addTextChangedListener(dirtyWatcher)
        listOf(binding.cbMon, binding.cbTue, binding.cbWed, binding.cbThu,
               binding.cbFri, binding.cbSat, binding.cbSun).forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> markDirty() }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!isDirty && !snapshotChanged()) {
                        findNavController().popBackStack()
                        return
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.save_changes_title)
                        .setMessage(R.string.save_changes_message)
                        .setPositiveButton(R.string.save_changes_save) { _, _ -> save() }
                        .setNegativeButton(R.string.save_changes_discard) { _, _ ->
                            isDirty = false
                            findNavController().popBackStack()
                        }
                        .show()
                }
            }
        )

        val isEditing = args.reminderId != -1L
        (requireActivity() as AppCompatActivity).supportActionBar?.title =
            getString(if (isEditing) R.string.edit_reminder else R.string.add_reminder)

        if (isEditing) {
            loadReminder(args.reminderId)
            binding.deleteButton.visibility = View.VISIBLE
            binding.deleteButton.setOnClickListener {
                editingReminder?.let { r ->
                    isDirty = false
                    viewModel.delete(r)
                    findNavController().popBackStack()
                }
            }
        } else {
            // New reminder: setup complete, enable dirty tracking
            isLoading = false
            originalSnapshot = currentSnapshot()
        }
    }

    private fun markDirty() {
        if (!isLoading) isDirty = true
    }

    private fun currentSnapshot(): String {
        val hour: Int
        val minute: Int
        if (Build.VERSION.SDK_INT >= 23) {
            hour = binding.timePicker.hour
            minute = binding.timePicker.minute
        } else {
            @Suppress("DEPRECATION")
            hour = binding.timePicker.currentHour
            @Suppress("DEPRECATION")
            minute = binding.timePicker.currentMinute
        }
        return buildString {
            append(isCountdownMode).append('|')
            append(hour).append(':').append(minute).append('|')
            append(binding.labelInput.text.toString()).append('|')
            append(binding.repeatGroup.checkedRadioButtonId).append('|')
            append(getCustomDays()).append('|')
            append(binding.countdownHours.text.toString()).append('|')
            append(binding.countdownMinutes.text.toString()).append('|')
            append(selectedSoundUri).append('|')
            append(binding.volumeSlider.value.toInt())
        }
    }

    private fun snapshotChanged(): Boolean {
        val baseline = originalSnapshot ?: return false
        return baseline != currentSnapshot()
    }

    private fun setMode(countdown: Boolean) {
        isCountdownMode = countdown
        binding.clockSection.visibility = if (countdown) View.GONE else View.VISIBLE
        binding.countdownSection.visibility = if (countdown) View.VISIBLE else View.GONE
        binding.countdownError.visibility = View.GONE
        binding.customDaysError.visibility = View.GONE
    }

    private fun loadReminder(id: Long) {
        lifecycleScope.launch {
            val reminder = (requireActivity().application as App)
                .database.reminderDao().getById(id) ?: return@launch
            editingReminder = reminder
            selectedSoundUri = reminder.soundUri
            selectedSoundLabel = reminder.soundLabel

            if (Build.VERSION.SDK_INT >= 23) {
                binding.timePicker.hour = reminder.hour
                binding.timePicker.minute = reminder.minute
            } else {
                @Suppress("DEPRECATION")
                binding.timePicker.currentHour = reminder.hour
                @Suppress("DEPRECATION")
                binding.timePicker.currentMinute = reminder.minute
            }

            binding.labelInput.setText(reminder.label)
            binding.volumeSlider.value = reminder.volume.toFloat()

            if (reminder.isCountdown) {
                binding.modeToggleGroup.check(R.id.modeCountdownButton)
                val remainingMs = reminder.nextTriggerMs - System.currentTimeMillis()
                if (remainingMs > 0) {
                    val totalMinutes = maxOf(1, ((remainingMs + 30_000L) / 60_000L).toInt())
                    binding.countdownHours.setText((totalMinutes / 60).toString())
                    binding.countdownMinutes.setText((totalMinutes % 60).toString())
                }
                // if already fired, leave the default (0h, 10m)
            }

            when (reminder.repeatType) {
                RepeatType.ONE_TIME -> binding.repeatOnce.isChecked = true
                RepeatType.DAILY -> binding.repeatDaily.isChecked = true
                RepeatType.CUSTOM_DAYS -> {
                    binding.repeatCustom.isChecked = true
                    binding.customDaysGroup.visibility = View.VISIBLE
                    val days = reminder.customDays
                        .split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()
                    binding.cbMon.isChecked = 2 in days
                    binding.cbTue.isChecked = 3 in days
                    binding.cbWed.isChecked = 4 in days
                    binding.cbThu.isChecked = 5 in days
                    binding.cbFri.isChecked = 6 in days
                    binding.cbSat.isChecked = 7 in days
                    binding.cbSun.isChecked = 1 in days
                }
            }
            updateSoundButton()
            // Loading complete — any further changes are user-driven
            isLoading = false
            originalSnapshot = currentSnapshot()
        }
    }

    private fun getCustomDays(): String {
        val days = mutableListOf<Int>()
        if (binding.cbMon.isChecked) days += 2
        if (binding.cbTue.isChecked) days += 3
        if (binding.cbWed.isChecked) days += 4
        if (binding.cbThu.isChecked) days += 5
        if (binding.cbFri.isChecked) days += 6
        if (binding.cbSat.isChecked) days += 7
        if (binding.cbSun.isChecked) days += 1
        return days.joinToString(",")
    }

    private fun save() {
        val reminder = buildReminder() ?: return
        isDirty = false
        viewModel.saveInBackground(reminder)
        findNavController().popBackStack()
    }

    private fun buildReminder(): Reminder? {
        return if (isCountdownMode) buildCountdownReminder() else buildClockReminder()
    }

    private fun buildCountdownReminder(): Reminder? {
        val hours = binding.countdownHours.text.toString().trim().toIntOrNull() ?: 0
        val minutes = binding.countdownMinutes.text.toString().trim().toIntOrNull() ?: 0
        val totalMinutes = hours * 60 + minutes
        if (totalMinutes < 1) {
            binding.countdownError.visibility = View.VISIBLE
            return null
        }
        binding.countdownError.visibility = View.GONE
        val triggerMs = System.currentTimeMillis() + totalMinutes * 60_000L
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        return Reminder(
            id = editingReminder?.id ?: 0L,
            label = binding.labelInput.text.toString().trim(),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            repeatType = RepeatType.ONE_TIME,
            customDays = "",
            soundUri = selectedSoundUri,
            soundLabel = selectedSoundLabel,
            volume = binding.volumeSlider.value.toInt(),
            isEnabled = true,
            nextTriggerMs = triggerMs,
            isCountdown = true
        )
    }

    private fun buildClockReminder(): Reminder? {
        val hour: Int
        val minute: Int
        if (Build.VERSION.SDK_INT >= 23) {
            hour = binding.timePicker.hour
            minute = binding.timePicker.minute
        } else {
            @Suppress("DEPRECATION")
            hour = binding.timePicker.currentHour
            @Suppress("DEPRECATION")
            minute = binding.timePicker.currentMinute
        }
        val repeatType = when (binding.repeatGroup.checkedRadioButtonId) {
            R.id.repeatDaily -> RepeatType.DAILY
            R.id.repeatCustom -> RepeatType.CUSTOM_DAYS
            else -> RepeatType.ONE_TIME
        }
        val customDays = if (repeatType == RepeatType.CUSTOM_DAYS) getCustomDays() else ""
        if (repeatType == RepeatType.CUSTOM_DAYS && customDays.isEmpty()) {
            binding.customDaysError.visibility = View.VISIBLE
            return null
        }
        binding.customDaysError.visibility = View.GONE
        return Reminder(
            id = editingReminder?.id ?: 0L,
            label = binding.labelInput.text.toString().trim(),
            hour = hour,
            minute = minute,
            repeatType = repeatType,
            customDays = customDays,
            soundUri = selectedSoundUri,
            soundLabel = selectedSoundLabel,
            volume = binding.volumeSlider.value.toInt(),
            isEnabled = true
        )
    }

    // ── Sound picker ──────────────────────────────────────────────────────────

    private fun showSoundPicker() {
        val categories = arrayOf(
            getString(R.string.sound_category_bundled),
            getString(R.string.sound_category_system),
            getString(R.string.sound_category_file)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_sound)
            .setItems(categories) { _, which ->
                when (which) {
                    0 -> showBundledChimePicker()
                    1 -> if (isPro) ringtonePicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION
                            )
                        }
                    ) else showSoundUpgradeDialog()
                    2 -> if (isPro) pickAudioFile() else showSoundUpgradeDialog()
                }
            }
            .show()
    }

    private fun showSoundUpgradeDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pro_upgrade_title)
            .setMessage(R.string.pro_upgrade_message)
            .setPositiveButton(R.string.pro_upgrade_button) { _, _ ->
                (requireActivity().application as App).billingManager.launchPurchase(requireActivity())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBundledChimePicker() {
        val proUser = isPro
        val names = bundledChimes.map { chime ->
            if (!proUser && chime.resName.isNotEmpty() && chime.resName != "chime_ripple") "${chime.label} \uD83D\uDD12"
            else chime.label
        }.toTypedArray()

        val initialIndex = bundledChimes.indexOfFirst { chime ->
            if (chime.resName.isEmpty()) selectedSoundUri.isEmpty()
            else selectedSoundUri == "raw:${chime.resName}"
        }.coerceAtLeast(0)

        var pendingIndex = initialIndex

        val dialogView = layoutInflater.inflate(R.layout.dialog_chime_picker, null)
        val previewSlider = dialogView.findViewById<Slider>(R.id.previewVolumeSlider)
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.chimeListView)

        var previewVolume = 7
        previewSlider.value = previewVolume.toFloat()
        previewSlider.addOnChangeListener { _, value, _ -> previewVolume = value.toInt() }

        listView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
        listView.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            names
        )
        listView.setItemChecked(initialIndex, true)
        listView.setOnItemClickListener { _, _, which, _ ->
            pendingIndex = which
            previewChime(bundledChimes[which], previewVolume)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_bundled_chime)
            .setView(dialogView)
            .setPositiveButton(R.string.select_sound) { _, _ ->
                stopPreview()
                val chime = bundledChimes[pendingIndex]
                val locked = !proUser && chime.resName.isNotEmpty() && chime.resName != "chime_ripple"
                if (locked) {
                    showSoundUpgradeDialog()
                    return@setPositiveButton
                }
                selectedSoundUri = if (chime.resName.isEmpty()) "" else "raw:${chime.resName}"
                selectedSoundLabel = chime.label
                markDirty()
                updateSoundButton()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> stopPreview() }
            .setOnDismissListener { stopPreview() }
            .show()
    }

    private fun previewCurrentSound(volumeLevel: Int) {
        stopPreview()
        val ctx = requireContext()
        val audioManager = ctx.getSystemService(AudioManager::class.java)
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val savedVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val targetVol = (volumeLevel * maxAlarmVol / 15).coerceIn(0, maxAlarmVol)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)

        val uri: Uri = when {
            selectedSoundUri.isEmpty() ->
                Uri.parse("android.resource://${ctx.packageName}/${R.raw.chime}")
            selectedSoundUri.startsWith("raw:") -> {
                val resName = selectedSoundUri.removePrefix("raw:")
                val resId = ctx.resources.getIdentifier(resName, "raw", ctx.packageName)
                val id = if (resId != 0) resId else R.raw.chime
                Uri.parse("android.resource://${ctx.packageName}/$id")
            }
            else -> Uri.parse(selectedSoundUri)
        }

        try {
            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                setOnCompletionListener {
                    it.release()
                    previewPlayer = null
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
                }
                setOnErrorListener { _, _, _ ->
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
        }
    }

    private fun previewChime(chime: BundledChime, volumeLevel: Int = 7) {
        stopPreview()
        val resName = chime.resName.ifEmpty { "chime" }
        val resId = requireContext().resources
            .getIdentifier(resName, "raw", requireContext().packageName)
        if (resId == 0) return

        val ctx = requireContext()
        val audioManager = ctx.getSystemService(AudioManager::class.java)
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val savedVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val targetVol = (volumeLevel * maxAlarmVol / 15).coerceIn(0, maxAlarmVol)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)

        previewPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val uri = Uri.parse("android.resource://${ctx.packageName}/$resId")
            setDataSource(ctx, uri)
            setOnCompletionListener {
                it.release()
                previewPlayer = null
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
            }
            setOnErrorListener { _, _, _ ->
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
                true
            }
            prepare()
            start()
        }
    }

    private fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) { }
        previewPlayer = null
    }

    private fun pickAudioFile() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            audioFilePicker.launch(arrayOf("audio/*"))
        } else {
            requestAudioPermission.launch(permission)
        }
    }

    private fun updateSoundButton() {
        binding.soundButton.text = when {
            selectedSoundUri.isEmpty() -> getString(R.string.sound_default)
            selectedSoundUri == "raw:chime_ripple" && selectedSoundLabel == "Default" ->
                getString(R.string.sound_default)
            selectedSoundLabel.isNotEmpty() -> selectedSoundLabel
            else -> getString(R.string.sound_custom)
        }
    }

    private fun getContentDisplayName(uri: Uri): String? = try {
        requireContext().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }

    override fun onDestroyView() {
        stopPreview()
        super.onDestroyView()
        _binding = null
    }
}
