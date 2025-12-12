package com.example.relmusic.ui.pages.search;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.example.relmusic.R;
import com.example.relmusic.databinding.SearchFragmentBinding;
import com.example.relmusic.service.MusicService;
import com.example.relmusic.ui.music.MusicAdapter;
import com.example.relmusic.ui.music.MusicItem;
import com.example.relmusic.ui.pages.nowplaying.NowPlayingActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";

    private SearchFragmentBinding binding;
    private MusicAdapter searchAdapter;
    private List<MusicItem> allMusicList = new ArrayList<>();
    private List<MusicItem> searchResults = new ArrayList<>();
    private ExecutorService executorService;
    private Handler searchHandler;
    private Runnable searchRunnable;
    private static final int PERMISSION_REQUEST_CODE = 124;
    private static final int SEARCH_DELAY = 300;
    private boolean isSearching = false;

    private MaterialCardView miniPlayerContainer;
    private ImageView miniAlbumArt;
    private TextView miniSongTitle;
    private TextView miniArtistName;
    private MaterialButton miniPlayPauseButton;
    private MaterialButton miniNextButton;
    private MaterialButton miniCloseButton;

    private MusicItem currentPlayingItem;
    private boolean isPlaying = false;
    private boolean isMiniPlayerVisible = false;
    private boolean isReceiverRegistered = false;
    private boolean isFragmentDestroyed = false;


    private BroadcastReceiver miniPlayerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isFragmentDestroyed) {
                return;
            }

            try {
                String action = intent.getAction();
                if (action != null) {
                    switch (action) {
                        case MusicService.ACTION_MUSIC_UPDATED:
                            MusicItem musicItem = intent.getParcelableExtra("music_item");
                            boolean playing = intent.getBooleanExtra("is_playing", false);
                            if (musicItem != null) {
                                showMiniPlayer(musicItem);
                                updateMiniPlayerState(playing);
                            }
                            break;
                        case MusicService.ACTION_PLAYBACK_STATE_CHANGED:
                            boolean playingState = intent.getBooleanExtra("is_playing", false);
                            updateMiniPlayerState(playingState);
                            break;
                        case MusicService.ACTION_HIDE_MINI_PLAYER:
                            hideMiniPlayer();
                            break;
                        case "MINI_PLAYER_VISIBILITY_CHANGED":
                            boolean isVisible = intent.getBooleanExtra("is_visible", false);
                            int height = intent.getIntExtra("height", 0);
                            adjustRecyclerViewPadding(isVisible, height);
                            break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling broadcast: " + e.getMessage(), e);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = SearchFragmentBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        executorService = Executors.newSingleThreadExecutor();
        searchHandler = new Handler(Looper.getMainLooper());

        initializeMiniPlayer();
        setupRecyclerView();
        setupSearchView();
        loadAllMusic();

        return root;
    }

    private void initializeMiniPlayer() {
        try {
            miniPlayerContainer = binding.miniPlayerContainer;
            miniAlbumArt = binding.miniAlbumArt;
            miniSongTitle = binding.miniSongTitle;
            miniArtistName = binding.miniArtistName;
            miniPlayPauseButton = binding.miniPlayPauseButton;
            miniNextButton = binding.miniNextButton;
            miniCloseButton = binding.miniCloseButton;

            miniPlayerContainer.setOnClickListener(v -> {
                if (!isFragmentDestroyed) {
                    openNowPlayingActivity();
                }
            });

            miniPlayPauseButton.setOnClickListener(v -> {
                if (!isFragmentDestroyed && getActivity() != null) {
                    try {
                        Intent serviceIntent = new Intent(getActivity(), MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_TOGGLE_PLAY_PAUSE);
                        getActivity().startService(serviceIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error toggling play/pause: " + e.getMessage(), e);
                    }
                }
            });

            miniNextButton.setOnClickListener(v -> {
                if (!isFragmentDestroyed && getActivity() != null) {
                    try {
                        Intent serviceIntent = new Intent(getActivity(), MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_NEXT);
                        getActivity().startService(serviceIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing next: " + e.getMessage(), e);
                    }
                }
            });

            miniCloseButton.setOnClickListener(v -> {
                if (!isFragmentDestroyed && getActivity() != null) {
                    try {
                        Intent serviceIntent = new Intent(getActivity(), MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_STOP);
                        getActivity().startService(serviceIntent);
                        hideMiniPlayer();
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping music: " + e.getMessage(), e);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error initializing mini player: " + e.getMessage(), e);
        }
    }

    private void setupRecyclerView() {
        searchAdapter = new MusicAdapter(searchResults, getContext());
        binding.searchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.searchRecyclerView.setAdapter(searchAdapter);

        searchAdapter.setOnMusicItemClickListener(new MusicAdapter.OnMusicItemClickListener() {
            @Override
            public void onMusicItemClick(MusicItem musicItem) {
                startMusicServiceAndOpenNowPlaying(musicItem);
            }

            @Override
            public void onPlayButtonClick(MusicItem musicItem) {
                startMusicService(musicItem);
            }
        });
    }

    private void setupSearchView() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();

                binding.clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
            }
        });

        binding.clearButton.setOnClickListener(v -> {
            binding.searchEditText.setText("");
            binding.searchEditText.requestFocus();
            showKeyboard();
        });

        binding.searchEditText.requestFocus();

        binding.searchEditText.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.searchEditText.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Now show the keyboard
                showKeyboard();
            }
        });
    }

    private void showKeyboard() {
        if (getActivity() != null && !isFragmentDestroyed && binding != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (binding != null && binding.searchEditText != null) {
                    binding.searchEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_FORCED);
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    }
                }
            }, 100); // 100ms delay
        }
    }
    private void performSearch(String query) {
        if (query.isEmpty()) {
            showInitialState();
            return;
        }

        if (isSearching) return;

        isSearching = true;
        showLoading(true);

        executorService.execute(() -> {
            List<MusicItem> results = new ArrayList<>();
            String lowerQuery = query.toLowerCase();

            for (MusicItem item : allMusicList) {
                if (item.getTitle().toLowerCase().contains(lowerQuery) ||
                        item.getArtist().toLowerCase().contains(lowerQuery) ||
                        item.getAlbum().toLowerCase().contains(lowerQuery)) {
                    results.add(item);
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    isSearching = false;
                    updateSearchResults(results, query);
                });
            }
        });
    }

    private void updateSearchResults(List<MusicItem> results, String query) {
        if (binding == null) return;

        searchResults.clear();
        searchResults.addAll(results);
        searchAdapter.notifyDataSetChanged();

        updateUI(query);
    }

    private void updateUI(String query) {
        if (binding == null) return;

        if (query.isEmpty()) {
            showInitialState();
        } else if (searchResults.isEmpty()) {
            showNoResults(query);
        } else {
            showResults();
        }
    }

    private void showInitialState() {
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.initialState.setVisibility(View.VISIBLE);
        binding.loadingIndicator.setVisibility(View.GONE);
    }

    private void showResults() {
        binding.searchRecyclerView.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.initialState.setVisibility(View.GONE);
        binding.loadingIndicator.setVisibility(View.GONE);
    }

    private void showNoResults(String query) {
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.initialState.setVisibility(View.GONE);
        binding.loadingIndicator.setVisibility(View.GONE);

        binding.emptyStateText.setText("No results found for \"" + query + "\"");
        binding.emptyStateSubtext.setText("Try searching with different keywords");
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void loadAllMusic() {
        checkPermissionAndLoadMusic();
    }

    private void checkPermissionAndLoadMusic() {
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{permission}, PERMISSION_REQUEST_CODE);
        } else {
            loadMusicFromDevice();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFromDevice();
            } else {
                Toast.makeText(getContext(), "Permission denied. Cannot search music files.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadMusicFromDevice() {
        executorService.execute(() -> {
            List<MusicItem> tempMusicList = new ArrayList<>();

            ContentResolver contentResolver = requireContext().getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

            try (Cursor cursor = contentResolver.query(musicUri, projection, selection, null, sortOrder)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                    int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                    do {
                        long id = cursor.getLong(idColumn);
                        String title = cursor.getString(titleColumn);
                        String artist = cursor.getString(artistColumn);
                        String album = cursor.getString(albumColumn);
                        long duration = cursor.getLong(durationColumn);
                        String path = cursor.getString(pathColumn);
                        long albumId = cursor.getLong(albumIdColumn);

                        Uri albumArtUri = Uri.parse("content://media/external/audio/albumart/" + albumId);

                        MusicItem musicItem = new MusicItem(id, title, artist, album, duration, path, albumArtUri);
                        tempMusicList.add(musicItem);

                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error loading music files", Toast.LENGTH_SHORT).show();
                    });
                }
                return;
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    allMusicList.clear();
                    allMusicList.addAll(tempMusicList);
                });
            }
        });
    }

    private void startMusicServiceAndOpenNowPlaying(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);

        new Handler().postDelayed(() -> {
            openNowPlaying(musicItem);
        }, 200);
    }

    private void startMusicService(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);
    }

    private void startMusicServiceWithPlaylist(MusicItem selectedSong) {
        if (getContext() == null || searchResults.isEmpty()) {
            return;
        }

        int selectedIndex = -1;
        for (int i = 0; i < searchResults.size(); i++) {
            if (searchResults.get(i).getId() == selectedSong.getId()) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        Intent playlistIntent = new Intent(getContext(), MusicService.class);
        playlistIntent.setAction(MusicService.ACTION_SET_PLAYLIST);
        playlistIntent.putParcelableArrayListExtra("playlist", new ArrayList<>(searchResults));
        playlistIntent.putExtra("start_index", selectedIndex);
        getContext().startService(playlistIntent);

        Intent playIntent = new Intent(getContext(), MusicService.class);
        playIntent.setAction(MusicService.ACTION_PLAY);
        playIntent.putExtra("music_item", selectedSong);
        getContext().startService(playIntent);
    }

    private void openNowPlaying(MusicItem musicItem) {
        Intent intent = new Intent(getContext(), NowPlayingActivity.class);
        intent.putExtra("music_item", (Parcelable) musicItem);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().overridePendingTransition(
                    R.anim.slide_in_bottom,
                    R.anim.slide_out_top
            );
        }
    }

    private void openNowPlayingActivity() {
        if (isFragmentDestroyed || currentPlayingItem == null || getActivity() == null) {
            return;
        }

        try {
            Intent intent = new Intent(getActivity(), NowPlayingActivity.class);
            intent.putExtra("music_item", currentPlayingItem);
            startActivity(intent);

            getActivity().overridePendingTransition(
                    R.anim.slide_in_bottom,
                    R.anim.slide_out_top
            );
        } catch (Exception e) {
            Log.e(TAG, "Error opening now playing activity: " + e.getMessage(), e);
        }
    }

    public void showMiniPlayer(MusicItem musicItem) {
        if (isFragmentDestroyed || getActivity() == null || musicItem == null) {
            return;
        }

        try {
            currentPlayingItem = musicItem;

            if (miniSongTitle == null || miniArtistName == null ||
                    miniAlbumArt == null || miniPlayerContainer == null) {
                Log.e(TAG, "Mini player components are null");
                return;
            }

            miniSongTitle.setText(musicItem.getTitle());
            miniArtistName.setText(musicItem.getArtist());

            try {
                Glide.with(this)
                        .load(musicItem.getAlbumArtUri())
                        .placeholder(R.drawable.ic_outline_music_note_24)
                        .error(R.drawable.ic_outline_music_note_24)
                        .into(miniAlbumArt);

            } catch (Exception e) {
                Log.e(TAG, "Error loading album art: " + e.getMessage(), e);
            }

            if (!isMiniPlayerVisible) {
                isMiniPlayerVisible = true;
                miniPlayerContainer.setVisibility(View.VISIBLE);
                miniPlayerContainer.setTranslationY(miniPlayerContainer.getHeight());
                miniPlayerContainer.animate()
                        .translationY(0)
                        .setDuration(300)
                        .start();
            }

            updateMiniPlayerPlayButton();
        } catch (Exception e) {
            Log.e(TAG, "Error showing mini player: " + e.getMessage(), e);
        }
    }

    public void hideMiniPlayer() {
        if (isFragmentDestroyed) {
            return;
        }

        try {
            if (isMiniPlayerVisible && miniPlayerContainer != null) {
                isMiniPlayerVisible = false;
                miniPlayerContainer.animate()
                        .translationY(miniPlayerContainer.getHeight())
                        .setDuration(300)
                        .withEndAction(() -> {
                            if (!isFragmentDestroyed && miniPlayerContainer != null) {
                                miniPlayerContainer.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding mini player: " + e.getMessage(), e);
        }
    }

    public void updateMiniPlayerState(boolean playing) {
        if (isFragmentDestroyed) {
            return;
        }

        try {
            isPlaying = playing;
            updateMiniPlayerPlayButton();

        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player state: " + e.getMessage(), e);
        }
    }

    private void updateMiniPlayerPlayButton() {
        if (isFragmentDestroyed || miniPlayPauseButton == null) {
            return;
        }

        try {
            int iconRes = isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24;
            miniPlayPauseButton.setIconResource(iconRes);
        } catch (Exception e) {
            Log.e(TAG, "Error updating play button: " + e.getMessage(), e);
        }
    }

    private void adjustRecyclerViewPadding(boolean isVisible, int height) {
        if (binding != null && binding.searchRecyclerView != null) {
            RecyclerView recyclerView = binding.searchRecyclerView;
            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    isVisible ? height : 0
            );

            recyclerView.post(() -> {
                if (recyclerView.getAdapter() != null) {
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
            });
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMusicUpdateReceiver() {
        try {
            if (!isReceiverRegistered && miniPlayerReceiver != null && getActivity() != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(MusicService.ACTION_MUSIC_UPDATED);
                filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
                filter.addAction(MusicService.ACTION_HIDE_MINI_PLAYER);
                filter.addAction("MINI_PLAYER_VISIBILITY_CHANGED");

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    getActivity().registerReceiver(miniPlayerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    getActivity().registerReceiver(miniPlayerReceiver, filter);
                }

                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering broadcast receiver: " + e.getMessage(), e);
            isReceiverRegistered = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        registerMusicUpdateReceiver();

        if (getActivity() != null) {
            Intent requestStateIntent = new Intent(getActivity(), MusicService.class);
            requestStateIntent.setAction(MusicService.ACTION_REQUEST_STATE);
            getActivity().startService(requestStateIntent);

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null && miniPlayerReceiver != null && isReceiverRegistered) {
            try {
                getActivity().unregisterReceiver(miniPlayerReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
            }
        }

        try {
            if (miniPlayerContainer != null) {
                miniPlayerContainer.clearAnimation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isFragmentDestroyed = true;

        if (isReceiverRegistered && miniPlayerReceiver != null && getActivity() != null) {
            try {
                getActivity().unregisterReceiver(miniPlayerReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered or already unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage(), e);
            } finally {
                isReceiverRegistered = false;
            }
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        try {
            currentPlayingItem = null;

            if (getActivity() != null && !getActivity().isDestroyed()) {
                if (miniAlbumArt != null) {
                    Glide.with(this).clear(miniAlbumArt);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing references: " + e.getMessage(), e);
        }

        binding = null;
    }
}