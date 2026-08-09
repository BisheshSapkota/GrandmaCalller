package com.grandmacaller.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.grandmacaller.app.data.Relative
import com.grandmacaller.app.data.RelativeStore
import com.grandmacaller.app.databinding.ActivitySettingsBinding
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var relatives: MutableList<Relative>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        relatives = RelativeStore.load(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList())
        binding.relativesList.adapter = adapter

        binding.addButton.setOnClickListener { addRelative() }

        binding.relativesList.setOnItemLongClickListener { _, _, position, _ ->
            val removed = relatives.removeAt(position)
            RelativeStore.save(this, relatives)
            refreshList()
            Toast.makeText(this, "Removed ${removed.displayName}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun addRelative() {
        val name = binding.displayNameInput.text.toString().trim()
        val messengerId = binding.messengerIdInput.text.toString().trim()
        val spokenRaw = binding.spokenNamesInput.text.toString().trim()

        if (name.isEmpty() || messengerId.isEmpty() || spokenRaw.isEmpty()) {
            Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val spokenNames = spokenRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        relatives.add(
            Relative(
                id = UUID.randomUUID().toString(),
                displayName = name,
                messengerId = messengerId,
                spokenNames = spokenNames
            )
        )
        RelativeStore.save(this, relatives)
        refreshList()

        binding.displayNameInput.text.clear()
        binding.messengerIdInput.text.clear()
        binding.spokenNamesInput.text.clear()
    }

    private fun refreshList() {
        adapter.clear()
        adapter.addAll(displayList())
        adapter.notifyDataSetChanged()
    }

    private fun displayList(): List<String> =
        relatives.map { "${it.displayName}  —  says: ${it.spokenNames.joinToString(", ")}" }
}
