package com.desmond.gptwake;

import java.util.Arrays;

/** Bounded, volatile diagnostic audio; disabled and erased when the screen is hidden. */
final class RecentAudioBuffer {
    private final float[] samples;
    private int next;
    private int size;
    private boolean enabled;

    RecentAudioBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        samples = new float[capacity];
    }

    synchronized void enable() {
        clear();
        enabled = true;
    }

    synchronized void disable() {
        enabled = false;
        clear();
    }

    private void clear() {
        Arrays.fill(samples, 0);
        next = size = 0;
    }

    synchronized void append(float[] frame) {
        if (!enabled) return;
        for (float value : frame) {
            samples[next] = value;
            next = (next + 1) % samples.length;
        }
        size = Math.min(samples.length, size + frame.length);
    }

    synchronized float[] snapshot() {
        float[] result = new float[size];
        int start = (next - size + samples.length) % samples.length;
        for (int i = 0; i < size; i++) result[i] = samples[(start + i) % samples.length];
        return result;
    }
}
