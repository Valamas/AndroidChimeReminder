package com.valamas.chimereminder.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.valamas.chimereminder.App
import com.valamas.chimereminder.BuildConfig
import com.valamas.chimereminder.R
import com.valamas.chimereminder.databinding.FragmentAboutBinding

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
