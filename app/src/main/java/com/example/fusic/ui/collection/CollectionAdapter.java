package com.example.fusic.ui.collection;

import android.content.Context;
import android.content.Intent;
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
    private Context context;
    private OnCollectionClickListener clickListener;
    private OnCollectionLongClickListener longClickListener;

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
    }

    public interface OnCollectionLongClickListener {
        void onCollectionLongClick(Collection collection);
    }

    public CollectionAdapter(List<Collection> collections, Context context) {
        this.collections = collections;
        this.context = context;
    }

    public CollectionAdapter(List<Collection> collections,
                             OnCollectionClickListener clickListener,
                             OnCollectionLongClickListener longClickListener) {
        this.collections = collections;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public CollectionAdapter(List<Collection> collections,
                             Context context,
                             OnCollectionClickListener clickListener,
                             OnCollectionLongClickListener longClickListener) {
        this.collections = collections;
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setOnCollectionClickListener(OnCollectionClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnCollectionLongClickListener(OnCollectionLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection, parent, false);
        if (context == null) {
            context = parent.getContext();
        }
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

            int count = collection.getMusicIds() != null ? collection.getMusicIds().size() : 0;
            textSongCount.setText(count + (count == 1 ? " song" : " songs"));

            cardView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCollectionClick(collection);
                } else if (context != null) {
                    Intent intent = new Intent(context, CollectionDetailActivity.class);
                    intent.putExtra("collection", collection);
                    context.startActivity(intent);
                }
            });

            // Long click - Show options menu
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