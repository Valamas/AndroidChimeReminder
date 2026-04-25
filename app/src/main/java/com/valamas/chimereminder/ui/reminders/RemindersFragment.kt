package com.valamas.chimereminder.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valamas.chimereminder.R
import com.valamas.chimereminder.databinding.FragmentRemindersBinding
import com.valamas.chimereminder.viewmodel.RemindersViewModel
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_reminders, menu)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_add_reminder) {
                    findNavController().navigate(
                        RemindersFragmentDirections.actionRemindersToAddEdit(-1L)
                    )
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val adapter = RemindersAdapter(
            onToggle = { reminder -> viewModel.toggleEnabled(reminder) },
            onClick = { reminder ->
                findNavController().navigate(
                    RemindersFragmentDirections.actionRemindersToAddEdit(reminder.id)
                )
            },
            onLongClick = { reminder ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete)
                    .setMessage(R.string.delete_confirm)
                    .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(reminder) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reminders.collect { list ->
                    adapter.submitList(list)
                    binding.emptyText.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
