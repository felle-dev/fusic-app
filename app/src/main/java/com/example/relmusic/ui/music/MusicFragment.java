package com.example.relmusic.ui.music;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.relmusic.MainActivity;
import com.example.relmusic.R;
import com.example.relmusic.databinding.FragmentMusicBinding;
import com.example.relmusic.service.MusicService;
import com.example.relmusic.ui.pages.nowplaying.NowPlayingActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicFragment extends Fragment {

    private FragmentMusicBinding binding;
    private MusicAdapter musicAdapter;
    private List<MusicItem> musicList = new ArrayList<>();
    private ExecutorService executorService;
    private static final int PERMISSION_REQUEST_CODE = 123;

    private static List<MusicItem> cachedMusicList = null;
    private static long lastCacheTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000;
    private boolean isLoading = false;

    private BroadcastReceiver miniPlayerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("MINI_PLAYER_VISIBILITY_CHANGED".equals(intent.getAction())) {
                boolean isVisible = intent.getBooleanExtra("is_visible", false);
                int height = intent.getIntExtra("height", 0);

                if (binding != null && binding.musicRecyclerView != null) {
                    RecyclerView recyclerView = binding.musicRecyclerView;
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
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        MusicViewModel musicViewModel =
                new ViewModelProvider(this).get(MusicViewModel.class);

        binding = FragmentMusicBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        executorService = Executors.newSingleThreadExecutor();
        setupRecyclerView();
        loadMusicData();

        return root;
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.musicRecyclerView;

        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        musicAdapter = new MusicAdapter(musicList, getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(musicAdapter);

        musicAdapter.setOnMusicItemClickListener(new MusicAdapter.OnMusicItemClickListener() {
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

    private void loadMusicData() {
        if (isCacheValid()) {
            loadFromCache();
            return;
        }

        checkPermissionAndLoadMusic();
    }


    private boolean isCacheValid() {
        return cachedMusicList != null &&
                !cachedMusicList.isEmpty() &&
                (System.currentTimeMillis() - lastCacheTime) < CACHE_DURATION;
    }

    private void loadFromCache() {
        if (binding != null && binding.musicRecyclerView != null) {
            binding.musicRecyclerView.post(() -> {
                musicList.clear();
                musicList.addAll(cachedMusicList);

                if (musicAdapter != null) {
                    musicAdapter.notifyDataSetChanged();
                }

                updateUI();
            });
        }
    }

    private void startMusicServiceAndOpenNowPlaying(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);

        new android.os.Handler().postDelayed(() -> {
            openNowPlaying(musicItem);
        }, 200);
    }

    private void startMusicService(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);
    }

    private void startMusicServiceWithPlaylist(MusicItem selectedSong) {
        if (getContext() == null || musicList.isEmpty()) {
            return;
        }

        int selectedIndex = -1;
        for (int i = 0; i < musicList.size(); i++) {
            if (musicList.get(i).getId() == selectedSong.getId()) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        Intent playlistIntent = new Intent(getContext(), MusicService.class);
        playlistIntent.setAction(MusicService.ACTION_SET_PLAYLIST);
        playlistIntent.putParcelableArrayListExtra("playlist", new ArrayList<>(musicList));
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
                Toast.makeText(getContext(), "Permission denied. Cannot access music files.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadMusicFromDevice() {
        if (isLoading) return;

        isLoading = true;
        showLoading(true);

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
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    isLoading = false;
                    Toast.makeText(getContext(), "Error loading music files", Toast.LENGTH_SHORT).show();
                    showRefreshComplete(false);
                });
                return;
            }

            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                isLoading = false;

                cachedMusicList = new ArrayList<>(tempMusicList);
                lastCacheTime = System.currentTimeMillis();

                int previousSize = musicList.size();
                musicList.clear();
                musicList.addAll(tempMusicList);

                if (musicAdapter != null) {
                    musicAdapter.notifyDataSetChanged();
                }

                updateUI();
                showRefreshComplete(true);

                int newCount = tempMusicList.size();
                if (newCount == previousSize && previousSize > 0) {
                    Toast.makeText(getContext(),
                            "Music library is up to date (" + newCount + " songs)",
                            Toast.LENGTH_SHORT).show();
                } 
            });
        });
    }

    private void updateUI() {
        if (binding == null) return;

        if (musicList.isEmpty()) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.musicRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.musicRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
    }

    private void showRefreshComplete(boolean success) {
        if (binding == null) return;
    }

    public void refreshData() {

        cachedMusicList = null;
        lastCacheTime = 0;

        if (getContext() != null) {
            Toast.makeText(getContext(), "Refreshing music library...", Toast.LENGTH_SHORT).show();
        }

        loadMusicData();
    }

    public static void clearCache() {
        cachedMusicList = null;
        lastCacheTime = 0;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            IntentFilter filter = new IntentFilter("MINI_PLAYER_VISIBILITY_CHANGED");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getActivity().registerReceiver(miniPlayerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getActivity().registerReceiver(miniPlayerReceiver, filter);
            }

            Intent requestStateIntent = new Intent(getActivity(), MusicService.class);
            requestStateIntent.setAction(MusicService.ACTION_REQUEST_STATE);
            getActivity().startService(requestStateIntent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null && miniPlayerReceiver != null) {
            try {
                getActivity().unregisterReceiver(miniPlayerReceiver);
            } catch (IllegalArgumentException e) {
            }
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