package com.supercomplex.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.supercomplex.app.data.AppDatabase
import com.supercomplex.app.databinding.ActivityMainBinding
import com.supercomplex.app.ui.explore.ExploreFragment
import com.supercomplex.app.ui.search.SearchFragment
import com.supercomplex.app.ui.downloads.DownloadsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result; downloads still run without notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force the Room database to initialise early so subsequent calls don't block.
        AppDatabase.init(applicationContext)

        // Ensure the system bar matches the app background.
        window.statusBarColor = getColor(R.color.bg_primary)
        window.navigationBarColor = getColor(R.color.bg_primary)

        if (savedInstanceState == null) {
            switchTo(ExploreFragment(), ExploreFragment::class.simpleName)
        }

        binding.navExplore.setOnClickListener {
            switchTo(ExploreFragment(), ExploreFragment::class.simpleName)
            highlightNav(R.id.navExplore)
        }
        binding.navSearch.setOnClickListener {
            switchTo(SearchFragment(), SearchFragment::class.simpleName)
            highlightNav(R.id.navSearch)
        }
        binding.navDownloads.setOnClickListener {
            switchTo(DownloadsFragment(), DownloadsFragment::class.simpleName)
            highlightNav(R.id.navDownloads)
        }
        highlightNav(R.id.navExplore)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun switchTo(fragment: Fragment, tag: String?) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commitAllowingStateLoss()
    }

    private fun highlightNav(selectedId: Int) {
        binding.navExplore.isSelected = selectedId == R.id.navExplore
        binding.navSearch.isSelected = selectedId == R.id.navSearch
        binding.navDownloads.isSelected = selectedId == R.id.navDownloads
    }
}
