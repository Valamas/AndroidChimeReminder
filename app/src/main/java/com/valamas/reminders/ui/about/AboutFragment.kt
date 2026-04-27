package com.valamas.reminders.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.valamas.reminders.App
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

        binding.privacyPolicyButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
