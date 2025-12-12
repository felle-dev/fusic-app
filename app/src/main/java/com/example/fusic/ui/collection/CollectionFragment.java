package com.example.fusic.ui.collection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fusic.R;
import com.example.fusic.databinding.FragmentCollectionBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CollectionFragment extends Fragment {

    private FragmentCollectionBinding binding;
    private ExecutorService executorService;
    private CollectionAdapter adapter;
    private SharedPreferences preferences;
    private static final String PREFS_NAME = "CollectionsPrefs";
    private static final String KEY_COLLECTIONS = "collections";
    private Gson gson;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CollectionViewModel collectionViewModel =
                new ViewModelProvider(this).get(CollectionViewModel.class);

        binding = FragmentCollectionBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        executorService = Executors.newSingleThreadExecutor();
        preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();

        setupRecyclerView();
        setupAddButtons();
        loadCollectionData();

        return root;
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.collectionRecyclerView;
        recyclerView.setHasFixedSize(true);

        // GridLayoutManager with span size for the add button to take full width
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Make the add button (last item) span both columns
                if (adapter != null && position == adapter.getItemCount() - 1
                        && adapter.getItemCount() > 0) {
                    return 2; // Full width
                }
                return 1; // Normal collection items take 1 column
            }
        });
        recyclerView.setLayoutManager(gridLayoutManager);

        adapter = new CollectionAdapter(new ArrayList<>(), this::onCollectionClick, this::onCollectionLongClick);
        adapter.setOnAddCollectionClickListener(this::showAddCollectionDialog);
        recyclerView.setAdapter(adapter);
    }

    private void setupAddButtons() {
        // Only the empty state button now
        binding.btnCreateFirstCollection.setOnClickListener(v -> showAddCollectionDialog());
    }

    private void showAddCollectionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_collection, null);

        EditText editTextName = dialogView.findViewById(R.id.editTextCollectionName);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Collection")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String collectionName = editTextName.getText().toString().trim();
                    if (!collectionName.isEmpty()) {
                        createCollection(collectionName);
                    } else {
                        Toast.makeText(requireContext(), "Collection name cannot be empty",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createCollection(String name) {
        executorService.execute(() -> {
            List<Collection> collections = loadCollections();

            // Check for duplicate names
            for (Collection collection : collections) {
                if (collection.getName().equalsIgnoreCase(name)) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Collection already exists",
                                    Toast.LENGTH_SHORT).show());
                    return;
                }
            }

            Collection newCollection = new Collection(
                    System.currentTimeMillis(),
                    name,
                    new ArrayList<>(),
                    System.currentTimeMillis()
            );

            collections.add(newCollection);
            saveCollections(collections);

            requireActivity().runOnUiThread(() -> {
                adapter.updateCollections(collections);
                updateEmptyState(collections.isEmpty());
                Toast.makeText(requireContext(), "Collection created", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void onCollectionClick(Collection collection) {
        Intent intent = new Intent(requireContext(), CollectionDetailActivity.class);
        intent.putExtra("collection", collection);
        startActivity(intent);
    }

    private void onCollectionLongClick(Collection collection) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Collection")
                .setMessage("Are you sure you want to delete \"" + collection.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCollection(collection))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCollection(Collection collection) {
        executorService.execute(() -> {
            List<Collection> collections = loadCollections();
            collections.removeIf(c -> c.getId() == collection.getId());
            saveCollections(collections);

            requireActivity().runOnUiThread(() -> {
                adapter.updateCollections(collections);
                updateEmptyState(collections.isEmpty());
                Toast.makeText(requireContext(), "Collection deleted", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void loadCollectionData() {
        showLoading(true);

        executorService.execute(() -> {
            List<Collection> collections = loadCollections();

            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                adapter.updateCollections(collections);
                updateEmptyState(collections.isEmpty());
            });
        });
    }

    private List<Collection> loadCollections() {
        String json = preferences.getString(KEY_COLLECTIONS, null);
        if (json != null) {
            Type type = new TypeToken<List<Collection>>(){}.getType();
            return gson.fromJson(json, type);
        }
        return new ArrayList<>();
    }

    private void saveCollections(List<Collection> collections) {
        String json = gson.toJson(collections);
        preferences.edit().putString(KEY_COLLECTIONS, json).apply();
    }

    private void updateEmptyState(boolean isEmpty) {
        if (binding == null) return;

        if (isEmpty) {
            binding.collectionRecyclerView.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.collectionRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        if (binding == null) return;

        if (show) {
            binding.loadingLayout.setVisibility(View.VISIBLE);
            binding.collectionRecyclerView.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.GONE);
        } else {
            binding.loadingLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        binding = null;
    }
}