package com.videosequence.local;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

final class Playlist {
    static final class Video {
        final String uri;
        final String name;

        Video(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    static final class Group {
        String name;
        final ArrayList<Video> videos = new ArrayList<>();

        Group(String name) {
            this.name = name;
        }
    }

    static final class Sequence {
        String name;
        final ArrayList<Group> groups;

        Sequence(String name, ArrayList<Group> groups) {
            this.name = name;
            this.groups = groups;
        }
    }

    final ArrayList<Sequence> sequences = new ArrayList<>();
    int selected;
    private final SharedPreferences preferences;

    Playlist(Context context) {
        preferences = context.getSharedPreferences("playlist", Context.MODE_PRIVATE);
        try {
            String library = preferences.getString("sequences", null);
            if (library == null) {
                JSONArray legacy = new JSONArray(preferences.getString("groups", "[]"));
                sequences.add(new Sequence("My playlist", readGroups(legacy)));
            } else {
                JSONObject saved = new JSONObject(library);
                JSONArray items = saved.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    sequences.add(new Sequence(item.getString("name"), readGroups(item.getJSONArray("groups"))));
                }
                selected = Math.max(0, Math.min(saved.getInt("selected"), sequences.size() - 1));
            }
        } catch (JSONException invalidData) {
            throw new IllegalStateException("Could not read saved playlist", invalidData);
        }
    }

    Sequence current() {
        return sequences.get(selected);
    }

    ArrayList<Group> groups() {
        return current().groups;
    }

    Sequence removeCurrent() {
        int previous = selected;
        Sequence removed = sequences.remove(previous);
        boolean needsReplacement = sequences.isEmpty();
        if (needsReplacement) sequences.add(new Sequence("My playlist", new ArrayList<>()));
        selected = Math.min(previous, sequences.size() - 1);
        if (save()) return removed;

        if (needsReplacement) sequences.clear();
        sequences.add(previous, removed);
        selected = previous;
        // Restore the prior in-memory preferences after a failed disk write.
        save();
        return null;
    }

    private static ArrayList<Group> readGroups(JSONArray savedGroups) throws JSONException {
        ArrayList<Group> groups = new ArrayList<>();
        for (int i = 0; i < savedGroups.length(); i++) {
            JSONObject savedGroup = savedGroups.getJSONObject(i);
            Group group = new Group(savedGroup.getString("name"));
            JSONArray videos = savedGroup.getJSONArray("videos");
            for (int j = 0; j < videos.length(); j++) {
                JSONObject video = videos.getJSONObject(j);
                group.videos.add(new Video(video.getString("uri"), video.getString("name")));
            }
            groups.add(group);
        }
        return groups;
    }

    boolean save() {
        try {
            JSONArray items = new JSONArray();
            for (Sequence sequence : sequences) {
                items.put(new JSONObject().put("name", sequence.name).put("groups", writeGroups(sequence.groups)));
            }
            String library = new JSONObject().put("items", items).put("selected", selected).toString();
            return preferences.edit().putString("sequences", library).commit();
        } catch (JSONException invalidData) {
            throw new IllegalStateException(invalidData);
        }
    }

    private static JSONArray writeGroups(ArrayList<Group> groups) throws JSONException {
        JSONArray output = new JSONArray();
        for (Group group : groups) {
            JSONArray videos = new JSONArray();
            for (Video video : group.videos) {
                videos.put(new JSONObject().put("uri", video.uri).put("name", video.name));
            }
            output.put(new JSONObject().put("name", group.name).put("videos", videos));
        }
        return output;
    }

    boolean containsUri(String uri) {
        for (Sequence sequence : sequences) {
            for (Group group : sequence.groups) {
                for (Video video : group.videos) {
                    if (video.uri.equals(uri)) return true;
                }
            }
        }
        return false;
    }
}
