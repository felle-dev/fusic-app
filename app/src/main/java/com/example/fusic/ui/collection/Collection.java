package com.example.fusic.ui.collection;

import java.util.List;

public class Collection {
    private long id;
    private String name;
    private List<Long> musicIds; // Store music item IDs
    private long createdAt;

    public Collection(long id, String name, List<Long> musicIds, long createdAt) {
        this.id = id;
        this.name = name;
        this.musicIds = musicIds;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Long> getMusicIds() {
        return musicIds;
    }

    public void setMusicIds(List<Long> musicIds) {
        this.musicIds = musicIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getSongCount() {
        return musicIds != null ? musicIds.size() : 0;
    }
}