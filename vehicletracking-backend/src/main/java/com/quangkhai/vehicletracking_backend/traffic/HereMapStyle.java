package com.quangkhai.vehicletracking_backend.traffic;

/** Styles exposed by the HERE Raster Tile API through the application's tile proxy. */
public enum HereMapStyle {
    ROADMAP("explore.day", "png", "image/png"),
    SATELLITE("explore.satellite.day", "jpeg", "image/jpeg"),
    DARK("explore.night", "png", "image/png");

    private final String hereStyle;
    private final String format;
    private final String contentType;

    HereMapStyle(String hereStyle, String format, String contentType) {
        this.hereStyle = hereStyle;
        this.format = format;
        this.contentType = contentType;
    }

    public String hereStyle() {
        return hereStyle;
    }

    public String format() {
        return format;
    }

    public String contentType() {
        return contentType;
    }
}
