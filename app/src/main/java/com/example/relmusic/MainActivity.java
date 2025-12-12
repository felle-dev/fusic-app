package com.example.relmusic;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.relmusic.ui.pages.search.SearchActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.relmusic.databinding.ActivityMainBinding;
import com.example.relmusic.ui.music.MusicItem;
import com.example.relmusic.ui.pages.nowplaying.NowPlayingActivity;
import com.example.relmusic.service.MusicService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REFRESH_ANIMATION_DURATION = 1000;
    private static final int REFRESH_COOLDOWN_DURATION = 2000;
    private static final int MINI_PLAYER_ANIMATION_DURATION = 300;
    private static final int ALBUM_ART_ROTATION_DURATION = 8000;
    private static final float TOOLBAR_FADE_THRESHOLD = 0.7f;

    private ActivityMainBinding binding;
    private TextView toolbarTitle;
    private CollapsingToolbarLayout collapsingToolbar;
    private String currentTitle = "RelMusic";

    // Mini Player Components
    private MaterialCardView miniPlayerContainer;
    private ImageView miniAlbumArt;
    private TextView miniSongTitle;
    private TextView miniArtistName;
    private MaterialButton miniPlayPauseButton;
    private MaterialButton miniNextButton;
    private MaterialButton miniCloseButton;

    // State Management
    private MusicItem currentPlayingItem;
    private boolean isPlaying = false;
    private boolean isMiniPlayerVisible = false;
    private boolean isReceiverRegistered = false;
    private boolean isActivityDestroyed = false;

    // Handlers
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver musicUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isActivityDestroyed || isFinishing() || isDestroyed()) {
                return;
            }

            try {
                String action = intent.getAction();
                if (action == null) return;

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
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling broadcast: " + e.getMessage(), e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            enableEdgeToEdge();

            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            setupWindowInsets();

            if (!initializeToolbarComponents()) {
                Log.e(TAG, "Failed to initialize toolbar components");
                return;
            }

            if (!initializeMiniPlayer()) {
                Log.e(TAG, "Failed to initialize mini player components");
                return;
            }

            setupNavigation();
            setupToolbarActions();
            setupSearchButton();
            registerMusicUpdateReceiver();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private boolean initializeToolbarComponents() {
        try {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbarTitle = findViewById(R.id.toolbar_title_main);
            collapsingToolbar = findViewById(R.id.collapsing_toolbar);
            AppBarLayout appBarLayout = findViewById(R.id.app_bar_layout);

            if (toolbar == null || toolbarTitle == null || collapsingToolbar == null || appBarLayout == null) {
                Log.e(TAG, "One or more toolbar components are null");
                return false;
            }

            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }

            collapsingToolbar.setTitle(currentTitle);
            setupCollapsingToolbarTitleAnimation(appBarLayout);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing toolbar: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupNavigation() {
        try {
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView == null) {
                Log.e(TAG, "BottomNavigationView is null");
                return;
            }

            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            NavigationUI.setupWithNavController(binding.navView, navController);

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (!isActivityDestroyed) {
                    updateTitle(destination.getId());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation: " + e.getMessage(), e);
        }
    }

    private void setupToolbarActions() {
        try {
            MaterialButton refreshButton = findViewById(R.id.refresh_button);
            if (refreshButton != null) {
                refreshButton.setOnClickListener(v -> refreshMusicFragmentData());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar actions: " + e.getMessage(), e);
        }
    }

    private void setupSearchButton() {
        MaterialButton searchButton = findViewById(R.id.search_button);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                Intent searchIntent = new Intent(this, SearchActivity.class);
                startActivity(searchIntent);
            });
        }
    }

    private void refreshMusicFragmentData() {
        try {
            MaterialButton refreshButton = findViewById(R.id.refresh_button);
            if (refreshButton == null) return;

            // Animate refresh button
            animateRefreshButton(refreshButton);

            // Clear all caches
            clearAllFragmentCaches();

            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            NavDestination currentDestination = navController.getCurrentDestination();

            if (currentDestination == null) {
                refreshAllFragmentsInBackground();
                showRefreshToast("Refreshing all libraries...");
                return;
            }

            int destinationId = currentDestination.getId();

            // Refresh based on current destination
            if (destinationId == R.id.navigation_music) {
                refreshFragmentByType(com.example.relmusic.ui.music.MusicFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.album.AlbumFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.artist.ArtistFragment.class);
                showRefreshToast("Refreshing music, album, and artist library...");
            } else if (destinationId == R.id.navigation_album) {
                refreshFragmentByType(com.example.relmusic.ui.album.AlbumFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.music.MusicFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.artist.ArtistFragment.class);
                showRefreshToast("Refreshing album, music, and artist library...");
            } else if (destinationId == R.id.navigation_artist) {
                refreshFragmentByType(com.example.relmusic.ui.artist.ArtistFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.music.MusicFragment.class);
                refreshFragmentInBackground(com.example.relmusic.ui.album.AlbumFragment.class);
                showRefreshToast("Refreshing artist, music, and album library...");
            } else {
                refreshAllFragmentsInBackground();
                showRefreshToast("Refreshing all libraries...");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing fragments: " + e.getMessage(), e);
            showRefreshToast("Error refreshing library");
            resetRefreshButton();
        }
    }

    private void animateRefreshButton(MaterialButton refreshButton) {
        refreshButton.animate()
                .rotation(360f)
                .setDuration(REFRESH_ANIMATION_DURATION)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    if (!isActivityDestroyed && refreshButton != null) {
                        refreshButton.setRotation(0f);
                    }
                })
                .start();

        refreshButton.setEnabled(false);
        mainHandler.postDelayed(() -> {
            if (!isActivityDestroyed && refreshButton != null) {
                refreshButton.setEnabled(true);
            }
        }, REFRESH_COOLDOWN_DURATION);
    }

    private void clearAllFragmentCaches() {
        com.example.relmusic.ui.album.AlbumFragment.clearCache();
        com.example.relmusic.ui.artist.ArtistFragment.clearCache();
    }

    private void showRefreshToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void resetRefreshButton() {
        MaterialButton refreshButton = findViewById(R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setRotation(0f);
        }
    }

    // Generic method to refresh fragments by type
    private <T extends Fragment> void refreshFragmentByType(Class<T> fragmentClass) {
        try {
            Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                Fragment currentFragment = navHostFragment.getChildFragmentManager()
                        .getPrimaryNavigationFragment();

                if (currentFragment != null && fragmentClass.isInstance(currentFragment)) {
                    refreshFragment(currentFragment);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing fragment by type: " + e.getMessage(), e);
        }
    }

    // Generic method to refresh fragments in background
    private <T extends Fragment> void refreshFragmentInBackground(Class<T> fragmentClass) {
        try {
            Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                for (Fragment fragment : navHostFragment.getChildFragmentManager().getFragments()) {
                    if (fragmentClass.isInstance(fragment)) {
                        refreshFragment(fragment);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing fragment in background: " + e.getMessage(), e);
        }
    }

    // Centralized fragment refresh method
    private void refreshFragment(Fragment fragment) {
        if (fragment instanceof com.example.relmusic.ui.music.MusicFragment) {
            ((com.example.relmusic.ui.music.MusicFragment) fragment).refreshData();
        } else if (fragment instanceof com.example.relmusic.ui.album.AlbumFragment) {
            ((com.example.relmusic.ui.album.AlbumFragment) fragment).refreshData();
        } else if (fragment instanceof com.example.relmusic.ui.artist.ArtistFragment) {
            ((com.example.relmusic.ui.artist.ArtistFragment) fragment).refreshData();
        }
    }

    private void refreshAllFragmentsInBackground() {
        refreshFragmentInBackground(com.example.relmusic.ui.music.MusicFragment.class);
        refreshFragmentInBackground(com.example.relmusic.ui.album.AlbumFragment.class);
        refreshFragmentInBackground(com.example.relmusic.ui.artist.ArtistFragment.class);
    }

    private void enableEdgeToEdge() {
        try {
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
            }

            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling edge-to-edge: " + e.getMessage(), e);
        }
    }

    private void setupWindowInsets() {
        try {
            View rootView = binding.getRoot();
            if (rootView == null) return;

            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                return WindowInsetsCompat.CONSUMED;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up window insets: " + e.getMessage(), e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMusicUpdateReceiver() {
        try {
            if (!isReceiverRegistered && musicUpdateReceiver != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(MusicService.ACTION_MUSIC_UPDATED);
                filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
                filter.addAction(MusicService.ACTION_HIDE_MINI_PLAYER);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(musicUpdateReceiver, filter);
                }

                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering broadcast receiver: " + e.getMessage(), e);
            isReceiverRegistered = false;
        }
    }

    private boolean initializeMiniPlayer() {
        try {
            miniPlayerContainer = findViewById(R.id.miniPlayerContainer);
            miniAlbumArt = findViewById(R.id.miniAlbumArt);
            miniSongTitle = findViewById(R.id.miniSongTitle);
            miniArtistName = findViewById(R.id.miniArtistName);
            miniPlayPauseButton = findViewById(R.id.miniPlayPauseButton);
            miniNextButton = findViewById(R.id.miniNextButton);
            miniCloseButton = findViewById(R.id.miniCloseButton);

            if (miniPlayerContainer == null || miniAlbumArt == null ||
                    miniSongTitle == null || miniArtistName == null ||
                    miniPlayPauseButton == null || miniNextButton == null || miniCloseButton == null) {
                Log.e(TAG, "One or more mini player components are null");
                return false;
            }

            setupMiniPlayerClickListeners();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing mini player: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupMiniPlayerClickListeners() {
        miniPlayerContainer.setOnClickListener(v -> {
            if (!isActivityDestroyed) {
                openNowPlayingActivity();
            }
        });

        miniPlayPauseButton.setOnClickListener(v -> {
            if (!isActivityDestroyed) {
                sendMusicServiceAction(MusicService.ACTION_TOGGLE_PLAY_PAUSE);
            }
        });

        miniNextButton.setOnClickListener(v -> {
            if (!isActivityDestroyed) {
                sendMusicServiceAction(MusicService.ACTION_NEXT);
            }
        });

        miniCloseButton.setOnClickListener(v -> {
            if (!isActivityDestroyed) {
                sendMusicServiceAction(MusicService.ACTION_STOP);
                hideMiniPlayer();
            }
        });
    }

    private void sendMusicServiceAction(String action) {
        try {
            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(action);
            startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error sending service action " + action + ": " + e.getMessage(), e);
        }
    }
    public void showMiniPlayer(MusicItem musicItem) {
        if (isActivityDestroyed || isFinishing() || isDestroyed() || musicItem == null) {
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

            loadAlbumArt(musicItem);

            if (!isMiniPlayerVisible) {
                animateMiniPlayerIn();
            }

            updateMiniPlayerPlayButton();
        } catch (Exception e) {
            Log.e(TAG, "Error showing mini player: " + e.getMessage(), e);
        }
    }

    private void loadAlbumArt(MusicItem musicItem) {
        try {
            Glide.with(this)
                    .load(musicItem.getAlbumArtUri())
                    .placeholder(R.drawable.ic_outline_music_note_24)
                    .error(R.drawable.ic_outline_music_note_24)
                    .into(miniAlbumArt);
        } catch (Exception e) {
            Log.e(TAG, "Error loading album art: " + e.getMessage(), e);
        }
    }

    private void animateMiniPlayerIn() {
        isMiniPlayerVisible = true;
        miniPlayerContainer.setVisibility(View.VISIBLE);
        miniPlayerContainer.setTranslationY(miniPlayerContainer.getHeight());
        miniPlayerContainer.animate()
                .translationY(0)
                .setDuration(MINI_PLAYER_ANIMATION_DURATION)
                .withEndAction(() -> {
                    if (!isActivityDestroyed) {
                        broadcastMiniPlayerVisibility(true);
                    }
                })
                .start();
    }

    public void hideMiniPlayer() {
        if (isActivityDestroyed || isFinishing() || isDestroyed()) {
            return;
        }

        try {
            if (isMiniPlayerVisible && miniPlayerContainer != null) {
                isMiniPlayerVisible = false;
                miniPlayerContainer.animate()
                        .translationY(miniPlayerContainer.getHeight())
                        .setDuration(MINI_PLAYER_ANIMATION_DURATION)
                        .withEndAction(() -> {
                            if (!isActivityDestroyed && miniPlayerContainer != null) {
                                miniPlayerContainer.setVisibility(View.GONE);
                                broadcastMiniPlayerVisibility(false);
                            }
                        })
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding mini player: " + e.getMessage(), e);
        }
    }

    private void broadcastMiniPlayerVisibility(boolean isVisible) {
        if (isActivityDestroyed) return;

        try {
            Intent intent = new Intent("MINI_PLAYER_VISIBILITY_CHANGED");
            intent.putExtra("is_visible", isVisible);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting mini player visibility: " + e.getMessage(), e);
        }
    }

    public void updateMiniPlayerState(boolean playing) {
        if (isActivityDestroyed) return;

        try {
            isPlaying = playing;
            updateMiniPlayerPlayButton();
        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player state: " + e.getMessage(), e);
        }
    }

    private void updateMiniPlayerPlayButton() {
        if (isActivityDestroyed || miniPlayPauseButton == null) return;

        try {
            int iconRes = isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24;
            miniPlayPauseButton.setIconResource(iconRes);
        } catch (Exception e) {
            Log.e(TAG, "Error updating play button: " + e.getMessage(), e);
        }
    }

    private void openNowPlayingActivity() {
        if (isActivityDestroyed || currentPlayingItem == null) return;

        try {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            intent.putExtra("music_item", currentPlayingItem);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
        } catch (Exception e) {
            Log.e(TAG, "Error opening now playing activity: " + e.getMessage(), e);
        }
    }

    public void startMusicService(MusicItem musicItem) {
        if (isActivityDestroyed || musicItem == null) return;

        try {
            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);
            serviceIntent.putExtra("music_item", musicItem);
            startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting music service: " + e.getMessage(), e);
        }
    }

    private void updateTitle(int destinationId) {
        if (isActivityDestroyed) return;

        try {
            if (destinationId == R.id.navigation_music) {
                currentTitle = "Music";
            } else if (destinationId == R.id.navigation_album) {
                currentTitle = "Albums";
            } else if (destinationId == R.id.navigation_settings) {
                currentTitle = "Settings";
            } else if (destinationId == R.id.navigation_artist) {
                currentTitle = "Artist";
            } else {
                currentTitle = "RelMusic";
            }

            if (toolbarTitle != null) {
                toolbarTitle.setText(currentTitle);
            }
            if (collapsingToolbar != null) {
                collapsingToolbar.setTitle(currentTitle);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating title: " + e.getMessage(), e);
        }
    }

    private void setupCollapsingToolbarTitleAnimation(AppBarLayout appBarLayout) {
        if (appBarLayout == null) return;

        try {
            appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
                private boolean isCollapsed = false;

                @Override
                public void onOffsetChanged(AppBarLayout appBar, int verticalOffset) {
                    if (isActivityDestroyed || toolbarTitle == null || collapsingToolbar == null) {
                        return;
                    }

                    try {
                        int totalScrollRange = appBar.getTotalScrollRange();
                        float percentage = Math.abs(verticalOffset) / (float) totalScrollRange;

                        // Only update when crossing threshold to reduce calculations
                        boolean shouldCollapse = percentage > TOOLBAR_FADE_THRESHOLD;

                        if (shouldCollapse != isCollapsed) {
                            isCollapsed = shouldCollapse;

                            if (isCollapsed) {
                                toolbarTitle.setVisibility(View.VISIBLE);
                                toolbarTitle.setAlpha(1f);
                                collapsingToolbar.setTitle("");
                            } else {
                                toolbarTitle.setVisibility(View.INVISIBLE);
                                toolbarTitle.setAlpha(0f);
                                collapsingToolbar.setTitle(currentTitle);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in toolbar animation: " + e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar animation: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            sendMusicServiceAction(MusicService.ACTION_REQUEST_STATE);
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (miniPlayerContainer != null) {
                miniPlayerContainer.clearAnimation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isActivityDestroyed = true;

        mainHandler.removeCallbacksAndMessages(null);

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(musicUpdateReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered or already unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage(), e);
            } finally {
                isReceiverRegistered = false;
            }
        }

        // Clear resources
        try {
            currentPlayingItem = null;

            if (!isDestroyed() && miniAlbumArt != null) {
                Glide.with(this).clear(miniAlbumArt);
            }

            if (binding != null) {
                binding = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing references: " + e.getMessage(), e);
        }
    }
}