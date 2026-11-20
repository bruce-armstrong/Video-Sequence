package com.videosequence.local;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class VideoThumbnails {
    private static final int WIDTH = 196;
    private static final int HEIGHT = 126;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
        1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String uri, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private int generation;

    VideoThumbnails(Context context) {
        this.context = context;
    }

    void bind(String uri, ImageView view) {
        Bitmap cached = cache.get(uri);
        if (cached != null) {
            show(view, cached);
            return;
        }
        int requestGeneration = generation;
        worker.execute(() -> {
            Bitmap preview = cache.get(uri);
            if (preview == null) {
                preview = readFrame(uri);
                if (preview != null) cache.put(uri, preview);
            }
            Bitmap result = preview;
            if (result != null) {
                main.post(() -> {
                    if (requestGeneration == generation) show(view, result);
                });
            }
        });
    }

    void clearPending() {
        generation++;
        worker.getQueue().clear();
        main.removeCallbacksAndMessages(null);
    }

    void close() {
        clearPending();
        worker.shutdownNow();
    }

    private void show(ImageView view, Bitmap bitmap) {
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(bitmap);
    }

    private Bitmap readFrame(String uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.parse(uri));
            if (Build.VERSION.SDK_INT >= 27) {
                return retriever.getScaledFrameAtTime(-1,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT);
            }
            Bitmap frame = retriever.getFrameAtTime();
            if (frame == null) return null;
            float scale = Math.min(1f, Math.min((float) WIDTH / frame.getWidth(),
                (float) HEIGHT / frame.getHeight()));
            Bitmap scaled = Bitmap.createScaledBitmap(frame,
                Math.max(1, Math.round(frame.getWidth() * scale)),
                Math.max(1, Math.round(frame.getHeight() * scale)), true);
            if (scaled != frame) frame.recycle();
            return scaled;
        } catch (RuntimeException unavailable) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // A provider can fail while its file is being closed.
            }
        }
    }
}
