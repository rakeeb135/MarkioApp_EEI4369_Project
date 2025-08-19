package com.eei4369.markio;

// Represents a single bookmark entry with all its details.
public class Bookmark {
    private long id;
    private String title;
    private String notes;
    private String contentType; // e.g., "link", "image", "document"
    private String contentUri; // URI for local files
    private String linkUrl; // URL for web links
    private String geographicLocation; // "latitude,longitude" string
    private String readableAddress; // Human-readable location like "Street, City"
    private long timestamp; // When created/last updated
    private String tags; // Comma-separated tags

    // Constructor for new bookmarks
    public Bookmark(String title, String notes, String contentType, String contentUri, String linkUrl, String geographicLocation, String readableAddress, long timestamp, String tags) {
        this.title = title;
        this.notes = notes;
        this.contentType = contentType;
        this.contentUri = contentUri;
        this.linkUrl = linkUrl;
        this.geographicLocation = geographicLocation;
        this.readableAddress = readableAddress;
        this.timestamp = timestamp;
        this.tags = tags;
    }

    // Constructor for existing bookmarks (with ID from database)
    public Bookmark(long id, String title, String notes, String contentType, String contentUri, String linkUrl, String geographicLocation, String readableAddress, long timestamp, String tags) {
        this.id = id;
        this.title = title;
        this.notes = notes;
        this.contentType = contentType;
        this.contentUri = contentUri;
        this.linkUrl = linkUrl;
        this.geographicLocation = geographicLocation;
        this.readableAddress = readableAddress;
        this.timestamp = timestamp;
        this.tags = tags;
    }

    // --- Getters ---
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public String getContentType() { return contentType; }
    public String getContentUri() { return contentUri; }
    public String getLinkUrl() { return linkUrl; }
    public String getGeographicLocation() { return geographicLocation; }
    public String getReadableAddress() { return readableAddress; }
    public long getTimestamp() { return timestamp; }
    public String getTags() { return tags; }

    // --- Setters ---
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setContentUri(String contentUri) { this.contentUri = contentUri; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public void setGeographicLocation(String geographicLocation) { this.geographicLocation = geographicLocation; }
    public void setReadableAddress(String readableAddress) { this.readableAddress = readableAddress; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setTags(String tags) { this.tags = tags; }
}