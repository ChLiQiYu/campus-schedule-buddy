package com.example.schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar

class LearningHubFragment : Fragment(R.layout.fragment_learning_hub) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar_learning_hub)
        toolbar.title = "协同中心"
        view.findViewById<MaterialButton>(R.id.btn_open_group_sync).setOnClickListener {
            startActivity(Intent(requireContext(), GroupSyncActivity::class.java))
        }
    }
}
