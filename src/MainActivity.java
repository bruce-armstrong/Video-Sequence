package com.videosequence.local;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class MainActivity extends Screen {
    private static final int PICK_VIDEOS = 1;
    private Playlist playlist;
    private VideoThumbnails thumbnails;
    private ScrollView scroll;
    private int pickerGroup = -1;
    private ArrayList<String> pendingVideos;
    private final HashSet<String> excludedVideos = new HashSet<>();
    private AlertDialog reviewDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playlist = new Playlist(this);
        thumbnails = new VideoThumbnails(getApplicationContext());
        pickerGroup = state == null ? -1 : state.getInt("pickerGroup", -1);
        render(state == null ? 0 : state.getInt("scroll"));
        if (state != null) {
            pendingVideos = state.getStringArrayList("pendingVideos");
            ArrayList<String> excluded = state.getStringArrayList("excludedVideos");
            if (excluded != null) excludedVideos.addAll(excluded);
            if (pendingVideos != null && pickerGroup >= 0 && pickerGroup < playlist.groups().size()) {
                reviewSelection();
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("pickerGroup", pickerGroup);
        state.putInt("scroll", scroll.getScrollY());
        state.putStringArrayList("pendingVideos", pendingVideos);
        state.putStringArrayList("excludedVideos", new ArrayList<>(excludedVideos));
    }

    private void render(int offset) {
        thumbnails.clearPending();
        LinearLayout root = column();
        LinearLayout header = column();
        header.setPadding(dp(20), dp(18), dp(20), dp(12));
        header.addView(label("Video Sequence", 28, TEXT, true));
        LinearLayout playlistBar = row();
        TextView playlistName = label(playlist.current().name, 18, TEXT, true);
        playlistName.setMaxLines(2);
        playlistName.setEllipsize(TextUtils.TruncateAt.END);
        weighted(playlistBar, playlistName);
        playlistBar.addView(button("Playlists", this::showPlaylists));
        header.addView(playlistBar);
        int total = 0;
        for (Playlist.Group group : playlist.groups()) total += group.videos.size();
        header.addView(label(playlist.groups().size() + " groups  ·  " + total + " videos", 14, MUTED, false));
        Button reorder = button("Reorder groups", this::reorderGroups);
        reorder.setEnabled(playlist.groups().size() > 1);
        reorder.setAlpha(reorder.isEnabled() ? 1 : 0.35f);
        header.addView(reorder, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = column();
        list.setPadding(dp(16), 0, dp(16), dp(16));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (playlist.groups().isEmpty()) {
            TextView empty = label("Create a group, then add videos from your device.", 16, MUTED, false);
            empty.setPadding(dp(4), dp(28), dp(4), 0);
            list.addView(empty);
        }
        for (int i = 0; i < playlist.groups().size(); i++) addGroupCard(list, i);

        LinearLayout actions = row();
        actions.setPadding(dp(16), dp(8), dp(16), dp(8));
        weighted(actions, button("+ New group", () -> editName(null)));
        Button play = button("Play sequence", () -> startActivity(new Intent(this, PlayerActivity.class)));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(MINT);
        fill.setCornerRadius(dp(16));
        play.setBackground(fill);
        play.setTextColor(BACKGROUND);
        play.setAlpha(total > 0 ? 1 : 0.4f);
        play.setEnabled(total > 0);
        weighted(actions, play);
        root.addView(actions);

        show(root);
        ScrollView renderedScroll = scroll;
        renderedScroll.post(() -> renderedScroll.scrollTo(0, offset));
    }

    private void addGroupCard(LinearLayout list, int index) {
        Playlist.Group group = playlist.groups().get(index);
        LinearLayout card = column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        card.setPadding(dp(12), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = dp(12);
        list.addView(card, layout);

        LinearLayout heading = row();
        weighted(heading, label((index + 1) + ". " + group.name, 20, TEXT, true));
        Button menu = button("⋮", () -> groupMenu(group));
        menu.setContentDescription("Options for group " + group.name);
        heading.addView(menu);
        card.addView(heading);

        for (int i = 0; i < group.videos.size(); i++) {
            Playlist.Video video = group.videos.get(i);
            LinearLayout line = row();
            ImageView thumbnail = new ImageView(this);
            thumbnail.setBackgroundColor(BACKGROUND);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            thumbnail.setImageResource(android.R.drawable.ic_media_play);
            thumbnail.setContentDescription("Preview " + video.name);
            thumbnail.setFocusable(true);
            thumbnail.setOnClickListener(view -> previewVideo(video.uri, video.name));
            LinearLayout.LayoutParams preview = new LinearLayout.LayoutParams(dp(56), dp(36));
            preview.rightMargin = dp(8);
            line.addView(thumbnail, preview);
            thumbnails.bind(video.uri, thumbnail);
            TextView title = label(video.name, 15, TEXT, false);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            weighted(line, title);
            addOrderButtons(line, group.videos, i, video.name, this::changed);
            Button options = button("⋮", () -> videoMenu(group, video));
            options.setContentDescription("Options for " + video.name);
            line.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(line);
        }
        card.addView(button("+ Add videos", () -> chooseVideoSource(index)));
    }

    private void reorderGroups() {
        ArrayList<Playlist.Group> order = new ArrayList<>(playlist.groups());
        ScrollView orderScroll = new ScrollView(this);
        LinearLayout rows = column();
        rows.setPadding(dp(16), dp(8), dp(16), dp(8));
        orderScroll.addView(rows);
        renderGroupOrder(rows, order);
        new AlertDialog.Builder(this).setTitle("Reorder groups")
            .setView(orderScroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save order", (dialog, which) -> {
                playlist.groups().clear();
                playlist.groups().addAll(order);
                changed();
            }).show();
    }

    private void renderGroupOrder(LinearLayout rows, List<Playlist.Group> order) {
        rows.removeAllViews();
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i).name;
            LinearLayout line = row();
            weighted(line, label((i + 1) + ". " + name, 16, TEXT, true));
            addOrderButtons(line, order, i, "group " + name, () -> renderGroupOrder(rows, order));
            rows.addView(line);
        }
    }

    private <T> void addOrderButtons(LinearLayout row, List<T> items, int index, String name, Runnable onMove) {
        for (int step : new int[]{-1, 1}) {
            int destination = index + step;
            Button move = button(step < 0 ? "↑" : "↓", () -> {
                Collections.swap(items, index, destination);
                onMove.run();
            });
            move.setContentDescription("Move " + name + (step < 0 ? " up" : " down"));
            move.setEnabled(destination >= 0 && destination < items.size());
            move.setAlpha(move.isEnabled() ? 1 : 0.25f);
            row.addView(move, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
    }

    @Override protected void onDestroy() {
        if (reviewDialog != null) reviewDialog.dismiss();
        thumbnails.close();
        super.onDestroy();
    }

    private boolean changed() {
        boolean saved = playlist.save();
        if (!saved) toast("Could not save changes.");
        render(scroll.getScrollY());
        return saved;
    }

    private void editName(Playlist.Group group) {
        editName(group == null ? "New group" : "Rename group", "Group name",
            group == null ? null : group.name, name -> {
                if (group == null) playlist.groups().add(new Playlist.Group(name));
                else group.name = name;
                changed();
                if (group == null) scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            });
    }

    private void showPlaylists() {
        String[] names = new String[playlist.sequences.size()];
        for (int i = 0; i < names.length; i++) names[i] = playlist.sequences.get(i).name;
        new AlertDialog.Builder(this).setTitle("Playlists")
            .setSingleChoiceItems(names, playlist.selected, (dialog, index) -> {
                dialog.dismiss();
                if (index == playlist.selected) return;
                playlist.selected = index;
                savePlaylistSelection();
            })
            .setPositiveButton("New playlist", (dialog, which) ->
                editName("New playlist", "Playlist name", null, name -> {
                    playlist.sequences.add(new Playlist.Sequence(name, new ArrayList<>()));
                    playlist.selected = playlist.sequences.size() - 1;
                    savePlaylistSelection();
                }))
            .setNeutralButton("Rename current", (dialog, which) -> {
                Playlist.Sequence current = playlist.current();
                editName("Rename playlist", "Playlist name", current.name, name -> {
                    current.name = name;
                    changed();
                });
            })
            .show();
    }

    private void savePlaylistSelection() {
        if (!playlist.save()) toast("Could not save changes.");
        render(0);
    }

    private interface NameAction {
        void save(String name);
    }

    private void editName(String title, String hint, String currentName, NameAction action) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine();
        input.setHint(hint);
        if (currentName != null) {
            input.setText(currentName);
            input.selectAll();
        }
        LinearLayout field = column();
        field.setPadding(dp(24), 0, dp(24), 0);
        field.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(field).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter a name");
                return;
            }
            dialog.dismiss();
            action.save(name);
        }));
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    private void groupMenu(Playlist.Group group) {
        new AlertDialog.Builder(this).setTitle(group.name)
            .setItems(new String[]{"Rename group", "Remove group"}, (dialog, option) -> {
                if (option == 0) {
                    editName(group);
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Remove group?")
                    .setMessage("This removes the group from your sequence. Your video files stay on your device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (confirm, which) -> {
                        playlist.groups().remove(group);
                        if (changed()) {
                            for (Playlist.Video video : group.videos) releaseIfUnused(video);
                        }
                    }).show();
            }).show();
    }

    private void videoMenu(Playlist.Group group, Playlist.Video video) {
        new AlertDialog.Builder(this).setTitle(video.name)
            .setItems(new String[]{"Move to another group", "Remove from sequence"}, (dialog, option) -> {
                if (option == 0) {
                    moveToGroup(group, video);
                } else {
                    group.videos.remove(video);
                    if (changed()) releaseIfUnused(video);
                }
            }).show();
    }

    private void moveToGroup(Playlist.Group current, Playlist.Video video) {
        ArrayList<Playlist.Group> destinations = new ArrayList<>(playlist.groups());
        destinations.remove(current);
        if (destinations.isEmpty()) {
            toast("Create another group first.");
            return;
        }
        String[] names = new String[destinations.size()];
        for (int i = 0; i < names.length; i++) names[i] = destinations.get(i).name;
        new AlertDialog.Builder(this).setTitle("Move to group").setItems(names, (dialog, index) -> {
            current.videos.remove(video);
            destinations.get(index).videos.add(video);
            changed();
        }).show();
    }

    private void releaseIfUnused(Playlist.Video video) {
        if (playlist.containsUri(video.uri)) return;
        try {
            getContentResolver().releasePersistableUriPermission(Uri.parse(video.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The file provider may already have revoked access.
        }
    }

    private void chooseVideoSource(int index) {
        if (!hasPhotoPicker()) {
            pickVideos(index);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Add videos")
            .setItems(new String[]{"Browse albums", "Browse files"}, (dialog, option) -> {
                if (option == 0) pickFromAlbums(index);
                else pickVideos(index);
            }).show();
    }

    private boolean hasPhotoPicker() {
        return Build.VERSION.SDK_INT >= 33 ||
            (Build.VERSION.SDK_INT >= 30 && SdkExtensions.getExtensionVersion(30) >= 2);
    }

    private void pickFromAlbums(int index) {
        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES)
            .setType("video/*")
            .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        // Picker versions that support this extra open directly to Albums.
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_LAUNCH_TAB, MediaStore.PICK_IMAGES_TAB_ALBUMS);
        pickerGroup = index;
        try {
            startActivityForResult(intent, PICK_VIDEOS);
        } catch (ActivityNotFoundException unavailable) {
            toast("Albums are unavailable on this device. Browse videos by folder.");
            pickVideos(index);
        }
    }

    private void pickVideos(int index) {
        pickerGroup = index;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/*")
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEOS);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_VIDEOS) return;
        if (result != RESULT_OK || data == null || pickerGroup < 0 || pickerGroup >= playlist.groups().size()) {
            clearSelection();
            return;
        }
        Playlist.Group group = playlist.groups().get(pickerGroup);
        HashSet<String> existing = new HashSet<>();
        for (Playlist.Video video : group.videos) existing.add(video.uri);

        ClipData clips = data.getClipData();
        int count = clips == null ? 1 : clips.getItemCount();
        pendingVideos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Uri uri = clips == null ? data.getData() : clips.getItemAt(i).getUri();
            if (uri != null && existing.add(uri.toString())) pendingVideos.add(uri.toString());
        }
        if (pendingVideos.isEmpty()) {
            clearSelection();
            toast("Those videos are already in this group.");
            return;
        }
        reviewSelection();
    }

    private void reviewSelection() {
        ScrollView selection = new ScrollView(this);
        LinearLayout rows = column();
        rows.setPadding(dp(16), dp(4), dp(16), dp(4));
        selection.addView(rows);
        for (String uri : pendingVideos) {
            String name = displayName(Uri.parse(uri));
            LinearLayout line = row();
            CheckBox include = new CheckBox(this);
            include.setText(name);
            include.setTextColor(TEXT);
            include.setMinimumHeight(dp(48));
            include.setChecked(!excludedVideos.contains(uri));
            include.setOnCheckedChangeListener((view, checked) -> {
                if (checked) excludedVideos.remove(uri);
                else excludedVideos.add(uri);
                updateAddButton();
            });
            weighted(line, include);
            Button preview = button("Preview", () -> previewVideo(uri, name));
            preview.setContentDescription("Preview " + name);
            line.addView(preview);
            rows.addView(line);
        }
        reviewDialog = new AlertDialog.Builder(this).setTitle("Review selected videos")
            .setView(selection)
            .setNegativeButton("Cancel", (dialog, which) -> clearSelection())
            .setPositiveButton("Add videos", (dialog, which) -> addReviewedVideos())
            .setOnCancelListener(dialog -> clearSelection())
            .show();
        updateAddButton();
    }

    private void updateAddButton() {
        Button add = reviewDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        int count = pendingVideos.size() - excludedVideos.size();
        add.setText(count == 1 ? "Add 1 video" : "Add " + count + " videos");
        add.setEnabled(count > 0);
    }

    private void previewVideo(String uri, String name) {
        startActivity(new Intent(this, PlayerActivity.class)
            .setData(Uri.parse(uri))
            .putExtra(PlayerActivity.PREVIEW_NAME, name));
    }

    private void addReviewedVideos() {
        Playlist.Group group = playlist.groups().get(pickerGroup);
        int failed = 0;
        for (String uri : pendingVideos) {
            if (excludedVideos.contains(uri)) continue;
            Uri source = Uri.parse(uri);
            try {
                getContentResolver().takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                group.videos.add(new Playlist.Video(uri, displayName(source)));
            } catch (SecurityException problem) {
                failed++;
            }
        }
        clearSelection();
        changed();
        if (failed > 0) toast("Could not keep access to " + failed + " selected video(s).");
    }

    private void clearSelection() {
        pickerGroup = -1;
        pendingVideos = null;
        excludedVideos.clear();
        reviewDialog = null;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (RuntimeException ignored) {
            // Some providers cannot supply metadata.
        }
        return "Video";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
