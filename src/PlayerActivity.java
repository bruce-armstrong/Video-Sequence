package com.videosequence.local;

import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.ArrayList;

public final class PlayerActivity extends Screen {
    static final String PREVIEW_NAME = "previewName";
    private static final class Entry {
        final String group;
        final Playlist.Video video;

        Entry(String group, Playlist.Video video) {
            this.group = group;
            this.video = video;
        }
    }

    private final ArrayList<Entry> queue = new ArrayList<>();
    private VideoView player;
    private TextView heading;
    private TextView detail;
    private Button previous;
    private Button next;
    private int index;
    private int position;
    private boolean complete;
    private boolean active;
    private boolean preview;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri source = getIntent().getData();
        preview = source != null;
        if (preview) {
            String name = getIntent().getStringExtra(PREVIEW_NAME);
            queue.add(new Entry("Preview", new Playlist.Video(source.toString(), name)));
        } else {
            for (Playlist.Group group : new Playlist(this).groups()) {
                for (Playlist.Video video : group.videos) queue.add(new Entry(group.name, video));
            }
        }
        if (queue.isEmpty()) {
            finish();
            return;
        }
        if (state != null) {
            index = Math.min(state.getInt("index"), queue.size() - 1);
            position = state.getInt("position");
            complete = state.getBoolean("complete");
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = column();
        LinearLayout header = row();
        header.setPadding(dp(8), dp(6), dp(12), dp(6));
        header.addView(button(preview ? "‹ Back" : "‹ Edit", this::finish));
        LinearLayout title = column();
        title.setPadding(dp(8), 0, 0, 0);
        heading = label("", 18, TEXT, true);
        detail = label("", 14, MUTED, false);
        heading.setMaxLines(2);
        detail.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.addView(heading);
        title.addView(detail);
        weighted(header, title);
        root.addView(header);

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        player = new VideoView(this);
        stage.addView(player, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        MediaController controller = new MediaController(this);
        player.setMediaController(controller);
        controller.setAnchorView(player);
        player.setOnCompletionListener(media -> {
            if (active) advance();
        });
        player.setOnErrorListener((media, what, extra) -> {
            playbackError();
            return true;
        });
        LinearLayout actions = row();
        previous = button("Previous", () -> play(index - 1, 0));
        next = button("Next", () -> {
            if (complete) play(0, 0);
            else if (preview) finish();
            else advance();
        });
        weighted(actions, previous);
        weighted(actions, next);
        root.addView(actions);
        show(root);
        updateLabels();
    }

    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (player != null && !complete) play(index, position);
    }

    @Override protected void onStop() {
        active = false;
        rememberPosition();
        if (player != null) player.stopPlayback();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        rememberPosition();
        state.putInt("index", index);
        state.putInt("position", position);
        state.putBoolean("complete", complete);
        super.onSaveInstanceState(state);
    }

    private void rememberPosition() {
        if (player != null && !complete && player.getDuration() > 0) {
            position = player.getCurrentPosition();
        }
    }

    private void play(int newIndex, int startAt) {
        if (newIndex < 0 || newIndex >= queue.size()) return;
        index = newIndex;
        position = startAt;
        complete = false;
        updateLabels();
        try {
            player.setVideoURI(Uri.parse(queue.get(index).video.uri));
            if (startAt > 0) player.seekTo(startAt);
            player.start();
        } catch (SecurityException missingAccess) {
            playbackError();
        }
    }

    private void advance() {
        if (index + 1 < queue.size()) {
            play(index + 1, 0);
            return;
        }
        complete = true;
        player.stopPlayback();
        updateLabels();
    }

    private void updateLabels() {
        Entry entry = queue.get(index);
        if (preview) {
            heading.setText(entry.video.name);
            detail.setText(complete ? "Preview finished" : "Preview");
            previous.setVisibility(Button.GONE);
            next.setText(complete ? "Replay video" : "Done");
            return;
        }
        heading.setText(complete ? "Sequence finished" : entry.video.name);
        detail.setText(complete ? queue.size() + " videos in sequence"
            : entry.group + "  ·  " + (index + 1) + " of " + queue.size());
        previous.setEnabled(!complete && index > 0);
        previous.setAlpha(previous.isEnabled() ? 1 : 0.35f);
        next.setText(complete ? "Replay sequence" : index + 1 == queue.size() ? "Finish" : "Next");
    }

    private void playbackError() {
        if (!active || isFinishing()) return;
        if (preview) {
            new AlertDialog.Builder(this).setTitle("Video unavailable")
                .setMessage(queue.get(index).video.name + " could not be played.")
                .setOnCancelListener(dialog -> finish())
                .setPositiveButton("Back", (dialog, which) -> finish())
                .show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Video unavailable")
            .setMessage(queue.get(index).video.name + " could not be played.")
            .setCancelable(false)
            .setNegativeButton("Edit sequence", (dialog, which) -> finish())
            .setPositiveButton("Skip video", (dialog, which) -> advance())
            .show();
    }
}
