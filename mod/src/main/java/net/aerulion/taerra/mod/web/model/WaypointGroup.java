package net.aerulion.taerra.mod.web.model;

import com.google.gson.annotations.SerializedName;

public record WaypointGroup(
    String id, String label, @SerializedName("private") boolean restricted, String icon) {}
