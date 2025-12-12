package com.example.fusic.ui.collection;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fusic.R;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {

    private List<Collection> collections;
    private OnCollectionClickListener clickListener;
    private OnCollectionLongClickListener longClickListener;

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
    }

    public interface OnCollectionLongClickListener {
        void onCollectionLongClick(Collection collection);
    }

    public CollectionAdapter(List<Collection> collections,
                             OnCollectionClickListener clickListener,
                             OnCollectionLongClickListener longClickListener) {
        this.collections = collections;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection, parent, false);
        return new CollectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CollectionViewHolder holder, int position) {
        Collection collection = collections.get(position);
        holder.bind(collection);
    }

    @Override
    public int getItemCount() {
        return collections.size();
    }

    public void updateCollections(List<Collection> newCollections) {
        this.collections = newCollections;
        notifyDataSetChanged();
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;
        private TextView textName;
        private TextView textSongCount;

        public CollectionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.collectionCard);
            textName = itemView.findViewById(R.id.textCollectionName);
            textSongCount = itemView.findViewById(R.id.textSongCount);
        }

        public void bind(Collection collection) {
            textName.setText(collection.getName());
            int count = collection.getSongCount();
            textSongCount.setText(count + (count == 1 ? " song" : " songs"));

            cardView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCollectionClick(collection);
                }
            });

            cardView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onCollectionLongClick(collection);
                    return true;
                }
                return false;
            });
        }
    }
}