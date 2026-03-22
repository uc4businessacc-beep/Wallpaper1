package com.infinity.wallpaper.ui;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.infinity.wallpaper.R;
import com.infinity.wallpaper.data.WallpaperItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CollectionsFragment extends Fragment {

    private RecyclerView recyclerSections;
    private SwipeRefreshLayout swipeRefresh;
    private final List<String> categoryOrder = new ArrayList<>();
    private boolean isRefreshing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collections, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerSections = view.findViewById(R.id.recycler_sections);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_collections);

        // Hide the default SwipeRefreshLayout indicator - we use our custom one
        swipeRefresh.setProgressViewOffset(false, -500, -500);
        swipeRefresh.setOnRefreshListener(this::refreshWithZigzag);

        loadCategories();
        loadAllWallpapersGrouped();
    }

    private void refreshWithZigzag() {
        if (isRefreshing) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        isRefreshing = true;


        // Clear and reload categories + wallpapers (no overlay / no animations)
        categoryOrder.clear();
        loadCategories();
        loadAllWallpapersGroupedRefresh();
    }

    private void animateRefreshComplete() {
        if (!isAdded()) return;
        swipeRefresh.setRefreshing(false);
        isRefreshing = false;
    }

    private void loadAllWallpapersGroupedRefresh() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("wallpapers").get().addOnCompleteListener(task -> {
            if (!isAdded()) return;

            final Map<String, WallpaperItem> dedupe = new LinkedHashMap<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        WallpaperItem direct = doc.toObject(WallpaperItem.class);
                        if (direct != null) {
                            direct.id = doc.getId();
                            dedupe.put(direct.id != null ? direct.id : doc.getId(), direct);
                        }
                    } catch (Exception e) { }
                }
            }
            buildAndShowSections(new ArrayList<>(dedupe.values()));

            // Immediately finish refresh (no delayed animation)
            animateRefreshComplete();
        });
    }

    private void loadCategories() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> list = new ArrayList<>();
        db.collection("collection").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Object o = doc.get("name");
                    if (o != null) list.add(o.toString());
                    else list.add(doc.getId());
                }
                Log.d("CollectionsFragment", "Loaded categories (collection): count=" + list.size());
                categoryOrder.clear();
                categoryOrder.addAll(list);
            } else {
                db.collection("collections").get().addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful() && task2.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task2.getResult()) {
                            Object o = doc.get("name");
                            if (o != null) list.add(o.toString());
                            else list.add(doc.getId());
                        }
                    }
                    Log.d("CollectionsFragment", "Loaded categories (collections fallback): count=" + list.size());
                    categoryOrder.clear();
                    categoryOrder.addAll(list);
                });
            }
        });
    }

    private void loadAllWallpapersGrouped() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("wallpapers").get().addOnCompleteListener(task -> {
            final java.util.Map<String, WallpaperItem> dedupe = new LinkedHashMap<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        WallpaperItem direct = doc.toObject(WallpaperItem.class);
                        if (direct != null) {
                            direct.id = doc.getId();
                            dedupe.put(direct.id != null ? direct.id : doc.getId(), direct);
                        }
                    } catch (Exception e) { }

                    try {
                        for (String key : doc.getData().keySet()) {
                            Object value = doc.get(key);
                            if (value instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> m = (java.util.Map<String, Object>) value;
                                WallpaperItem it = mapToWallpaperItem(m);
                                if (it != null) {
                                    String mapKey = (it.id != null && !it.id.isEmpty()) ? it.id : (doc.getId() + ":" + key);
                                    it.id = mapKey;
                                    dedupe.put(mapKey, it);
                                }
                            }
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }

                    db.collection("wallpapers").document(doc.getId()).collection("names").get().addOnCompleteListener(sub -> {
                        if (sub.isSuccessful() && sub.getResult() != null) {
                            for (QueryDocumentSnapshot sd : sub.getResult()) {
                                try {
                                    WallpaperItem it = sd.toObject(WallpaperItem.class);
                                    if (it != null) {
                                        it.id = sd.getId();
                                        dedupe.put(it.id, it);
                                    } else {
                                        java.util.Map<String, Object> m = sd.getData();
                                        WallpaperItem it2 = mapToWallpaperItem(m);
                                        if (it2 != null) {
                                            it2.id = sd.getId();
                                            dedupe.put(it2.id, it2);
                                        }
                                    }
                                } catch (Exception ex) { ex.printStackTrace(); }
                            }
                        }
                        // after each subcollection returns, rebuild sections to include newly fetched items
                        buildAndShowSections(new ArrayList<>(dedupe.values()));
                    });
                }
                // initial build with what we have synchronously
                buildAndShowSections(new ArrayList<>(dedupe.values()));
            } else {
                buildAndShowSections(new ArrayList<>());
            }
        });
    }

    private void buildAndShowSections(List<WallpaperItem> wallpapers) {
        // group by category (case-preserve first seen)
        Map<String, List<WallpaperItem>> temp = new LinkedHashMap<>();
        for (WallpaperItem it : wallpapers) {
            // skip items without a category
            if (it.category == null || it.category.trim().isEmpty()) continue;
            String cat = it.category != null ? it.category : "Uncategorized";
            String matchedKey = null;
            for (String k : temp.keySet()) {
                if (k.equalsIgnoreCase(cat)) { matchedKey = k; break; }
            }
            String key = matchedKey != null ? matchedKey : cat;
            if (!temp.containsKey(key)) temp.put(key, new ArrayList<>());
            temp.get(key).add(it);
        }

        // Build final ordered map honoring categoryOrder
        Map<String, List<WallpaperItem>> byCategory = new LinkedHashMap<>();
        // first add categories in categoryOrder if they exist in temp
        for (String ordered : categoryOrder) {
            for (String key : new ArrayList<>(temp.keySet())) {
                if (key.equalsIgnoreCase(ordered)) {
                    byCategory.put(key, temp.remove(key));
                    break;
                }
            }
        }
        // then add remaining categories
        for (Map.Entry<String, List<WallpaperItem>> e : temp.entrySet()) {
            byCategory.put(e.getKey(), e.getValue());
        }

        // set up sections adapter on the UI thread
        requireActivity().runOnUiThread(() -> {
            CategorySectionsAdapter sectionsAdapter = new CategorySectionsAdapter(requireContext(), byCategory, category -> {
                com.infinity.wallpaper.ui.wallpapers.CategoryViewAllTabsFragment frag = com.infinity.wallpaper.ui.wallpapers.CategoryViewAllTabsFragment.newInstance(category);
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, frag)
                        .addToBackStack(null)
                        .commit();
            });
            recyclerSections.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
            recyclerSections.setAdapter(sectionsAdapter);
        });
    }

    private WallpaperItem mapToWallpaperItem(java.util.Map<String, Object> m) {
        if (m == null) return null;
        WallpaperItem it = new WallpaperItem();
        try {
            Object name = m.get("name");
            if (name != null) it.name = name.toString();
            Object cat = m.get("category");
            if (cat instanceof java.util.List) {
                java.util.List l = (java.util.List) cat;
                if (!l.isEmpty()) it.category = l.get(0).toString();
            } else if (cat != null) it.category = cat.toString();
            Object bg = m.get("bgUrl");
            if (bg != null) it.bgUrl = bg.toString();
            Object prev = m.get("previewUrl");
            if (prev != null) it.previewUrl = prev.toString();
            Object mask = m.get("maskUrl");
            if (mask != null) it.maskUrl = mask.toString();
            Object premium = m.get("isPremium");
            if (premium instanceof Boolean) it.isPremium = (Boolean) premium;
            else if (premium != null) it.isPremium = Boolean.parseBoolean(premium.toString());
            Object themes = m.get("themes");
            if (themes instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> tm = (java.util.Map<String, Object>) themes;
                it.themes = tm;
            }
            Object idv = m.get("id");
            if (idv != null) it.id = idv.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return it;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private int dpToPx(int dp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.round(dp * dm.density);
    }
}
