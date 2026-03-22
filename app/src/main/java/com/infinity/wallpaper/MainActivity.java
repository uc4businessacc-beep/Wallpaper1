package com.infinity.wallpaper;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.infinity.wallpaper.ui.CollectionsFragment;
import com.infinity.wallpaper.ui.WallpapersFragment;
import com.infinity.wallpaper.ui.StudioFragment;
import com.infinity.wallpaper.ui.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Make status and navigation bars black to match app theme
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.black));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.black));

        BottomNavigationView navView = findViewById(R.id.top_navigation);
        View indicator = findViewById(R.id.bottom_indicator);
        FragmentManager fm = getSupportFragmentManager();

        // Load default fragment
        if (savedInstanceState == null) {
            fm.beginTransaction().replace(R.id.nav_host_fragment, new CollectionsFragment()).commit();
            navView.setSelectedItemId(R.id.navigation_collections);
        }

        // Position indicator under the selected item after layout pass
        navView.post(() -> moveIndicatorTo(navView, indicator, navView.getSelectedItemId()));

        navView.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();

            if (id == R.id.navigation_collections) {
                selected = new CollectionsFragment();
            } else if (id == R.id.navigation_wallpapers) {
                selected = new WallpapersFragment();
            } else if (id == R.id.navigation_settings) {
                selected = new SettingsFragment();
            } else if (id == R.id.navigation_studio) {
                selected = new StudioFragment();
            }
            if (selected != null) {
                fm.beginTransaction().replace(R.id.nav_host_fragment, selected).commit();
                moveIndicatorTo(navView, indicator, id);
                return true;
            }
            return false;
        });
    }

    private void moveIndicatorTo(BottomNavigationView navView, View indicator, int itemId) {
        if (indicator == null || navView == null) return;
        int menuSize = navView.getMenu().size();
        int index = 0;
        for (int i = 0; i < menuSize; i++) {
            if (navView.getMenu().getItem(i).getItemId() == itemId) {
                index = i;
                break;
            }
        }

        int width = navView.getWidth();
        if (width == 0) return;
        // compute center x for item
        float itemWidth = (float) width / menuSize;
        float targetCenter = itemWidth * index + itemWidth / 2f;
        // adjust indicator to center under item; indicator is inside the top nav container, same coords
        float indicatorHalf = indicator.getWidth() / 2f;
        float targetX = targetCenter - indicatorHalf;

        indicator.animate().x(targetX).setDuration(200).start();
    }
}
