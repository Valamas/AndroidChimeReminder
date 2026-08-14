package com.valamas.reminders.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.valamas.reminders.App
import kotlinx.coroutines.launch
import com.valamas.reminders.BuildConfig
import com.valamas.reminders.R
import com.valamas.reminders.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show version number
        binding.versionText.text = "Version ${BuildConfig.VERSION_NAME}"

        binding.privacyPolicyButton.setOnClickListener {
            findNavController().navigate(R.id.action_about_to_privacyPolicy)
        }

        binding.restoreButton.setOnClickListener {
            (requireActivity().application as App).billingManager.restorePurchases()
            Toast.makeText(requireContext(), R.string.pro_restore_checking, Toast.LENGTH_SHORT).show()
        }

        if (BuildConfig.DEBUG) {
            val billing = (requireActivity().application as App).billingManager
            binding.debugProSwitch.visibility = View.VISIBLE
            binding.debugProSwitch.isChecked = billing.isPro.value
            binding.debugProSwitch.setOnCheckedChangeListener { _, checked ->
                billing.debugSetPro(checked)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                billing.isPro.collect { isPro ->
                    binding.debugProSwitch.isChecked = isPro
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
