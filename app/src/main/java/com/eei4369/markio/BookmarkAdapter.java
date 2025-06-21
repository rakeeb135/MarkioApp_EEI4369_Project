package com.eei4369.markio;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    private Context context;
    private List<Bookmark> bookmarkList;
    private OnBookmarkClickListener listener;

    // Interface to handle clicks on bookmark items
    public interface OnBookmarkClickListener {
        void onBookmarkClick(long id, String contentType, String contentUri, String linkUrl);
        void onBookmarkLongClick(long id);
    }

    // Constructor to set up the adapter
    public BookmarkAdapter(Context context, List<Bookmark> bookmarkList, OnBookmarkClickListener listener) {
        this.context = context;
        this.bookmarkList = bookmarkList;
        this.listener = listener;
    }

    // Creates new view holders when needed (eg -- for new items appearing on screen)
    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for a single bookmark item
        View view = LayoutInflater.from(context).inflate(R.layout.bookmark_item, parent, false);
        return new BookmarkViewHolder(view);
    }

    // Binds data from a Bookmark object to a view holder at a given position
    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        Bookmark currentBookmark = bookmarkList.get(position);

        holder.titleTextView.setText(currentBookmark.getTitle());
        holder.notesTextView.setText(currentBookmark.getNotes());

        // Format and display the timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(currentBookmark.getTimestamp()));
        holder.timestampTextView.setText("Saved: " + formattedDate);

        // Handle content preview (image, video, link icon) and link text visibility
        String contentType = currentBookmark.getContentType();
        String contentUriStr = currentBookmark.getContentUri();
        String linkUrl = currentBookmark.getLinkUrl();

        if ("link".equals(contentType) && linkUrl != null && !linkUrl.isEmpty()) {
            holder.linkTextView.setText(linkUrl);
            holder.linkTextView.setVisibility(View.VISIBLE);
            holder.thumbnailImageView.setImageResource(R.drawable.ic_link); // Set link icon
            holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.transparent));
        } else if (contentUriStr != null && !contentUriStr.isEmpty()) {
            Uri contentUri = Uri.parse(contentUriStr);
            String mimeType = context.getContentResolver().getType(contentUri);

            holder.linkTextView.setVisibility(View.GONE); // Hide link text if it's a file

            if (mimeType != null && mimeType.startsWith("image/")) {
                Glide.with(context).load(contentUri)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .into(holder.thumbnailImageView);
                holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.transparent));
            } else if (mimeType != null && mimeType.startsWith("video/")) {
                holder.thumbnailImageView.setImageResource(android.R.drawable.ic_media_play);
                holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.black));
            } else if (mimeType != null && mimeType.startsWith("audio/")) {
                holder.thumbnailImageView.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
                holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.black));
            } else { // Generic file (document)
                holder.thumbnailImageView.setImageResource(R.drawable.ic_document); // Set document icon
                holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.black));
            }
        } else { // No content URI or link (just notes/text bookmark)
            holder.linkTextView.setVisibility(View.GONE);
            holder.thumbnailImageView.setImageResource(android.R.drawable.ic_menu_agenda); // Default notes icon
            holder.thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.thumbnailImageView.setBackgroundColor(context.getResources().getColor(android.R.color.transparent));
        }

        // Handle Geographic Location display
        String readableAddress = currentBookmark.getReadableAddress();
        if (readableAddress != null && !readableAddress.isEmpty() && !readableAddress.equals("Location: Not selected") && !readableAddress.contains("0.000000,0.000000")) {
            holder.locationTextView.setText(readableAddress);
            holder.locationTextView.setVisibility(View.VISIBLE);
        } else {
            holder.locationTextView.setVisibility(View.GONE);
        }

        // Display Tags
        String tags = currentBookmark.getTags();
        if (tags != null && !tags.trim().isEmpty()) {
            String formattedTags = "#" + tags.trim().replace(",", " #"); // Add # prefix to each tag
            holder.tagsTextView.setText(formattedTags);
            holder.tagsTextView.setVisibility(View.VISIBLE);
        } else {
            holder.tagsTextView.setVisibility(View.GONE);
        }

        // Set up click listener for the whole item view
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(
                        currentBookmark.getId(),
                        currentBookmark.getContentType(),
                        currentBookmark.getContentUri(),
                        currentBookmark.getLinkUrl()
                );
            }
        });

        // Set up long click listener for editing
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkLongClick(currentBookmark.getId());
                return true; // Consume the long click event
            }
            return false;
        });
    }

    // Returns the total number of items in the list
    @Override
    public int getItemCount() {
        return bookmarkList.size();
    }

    // Updates the list of bookmarks and notifies the RecyclerView to refresh
    public void setBookmarkList(List<Bookmark> newList) {
        this.bookmarkList = newList;
        notifyDataSetChanged(); // Tell RecyclerView to redraw all items
    }

    // ViewHolder class: holds references to the UI elements of each item view
    public static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView notesTextView;
        ImageView thumbnailImageView;
        TextView linkTextView;
        TextView locationTextView;
        TextView timestampTextView;
        TextView tagsTextView; // For tags display

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.textViewBookmarkTitle);
            notesTextView = itemView.findViewById(R.id.textViewBookmarkNotes);
            thumbnailImageView = itemView.findViewById(R.id.imageViewBookmarkThumbnail);
            linkTextView = itemView.findViewById(R.id.textViewBookmarkLink);
            locationTextView = itemView.findViewById(R.id.textViewBookmarkLocation);
            timestampTextView = itemView.findViewById(R.id.textViewBookmarkTimestamp);
            tagsTextView = itemView.findViewById(R.id.textViewBookmarkTags);
        }
    }
}