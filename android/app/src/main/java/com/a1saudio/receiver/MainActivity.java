package com.a1saudio.receiver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(20, 16, 31);
    private static final int CARD = Color.rgb(38, 31, 54);
    private static final int WHITE = Color.rgb(250, 247, 255);
    private static final int MUTED = Color.rgb(195, 186, 210);
    private static final int GREEN = Color.rgb(107, 239, 163);
    private static final int RED = Color.rgb(255, 91, 104);
    private static final int MAGENTA = Color.rgb(234, 71, 202);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView sourceStatus;
    private TextView volumeLabel;
    private SeekBar volumeSlider;
    private Button previousButton;
    private Button playButton;
    private Button nextButton;
    private Button muteButton;
    private boolean sliding;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (sourceStatus != null) {
                sourceStatus.setText(!AudioService.isListening ? "Start listening to connect" :
                        AudioService.sourceOnline ? "AudioBridge board connected" : "Waiting for AudioBridge board");
                sourceStatus.setTextColor(AudioService.sourceOnline ? GREEN : MUTED);
                if (!sliding) {
                    volumeSlider.setProgress(Math.min(100, AudioService.sourceVolume));
                    volumeLabel.setText("Board stream volume  " + AudioService.sourceVolume + "%");
                }
                playButton.setText(AudioService.sourcePlaying ? "❚❚  Pause" : "▶  Play");
                muteButton.setText(AudioService.sourceMuted ? "Unmute" : "Mute");
                previousButton.setEnabled(AudioService.sourceOnline && AudioService.sourceCanPrevious);
                nextButton.setEnabled(AudioService.sourceOnline && AudioService.sourceCanNext);
                playButton.setEnabled(AudioService.sourceOnline);
                muteButton.setEnabled(AudioService.sourceOnline);
                volumeSlider.setEnabled(AudioService.sourceOnline);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void control(String action) {
        startService(new Intent(this, AudioService.class).setAction(action));
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String label, int size, int color) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, int color, int textColor, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackground(rounded(color, 16));
        button.setOnClickListener(action);
        return button;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(24), dp(24), dp(24), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_audiobridge);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        logoParams.bottomMargin = dp(16);
        content.addView(logo, logoParams);

        TextView title = text("AudioBridge", 32, WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView tagline = text("Your audio, wherever you listen.", 16, MAGENTA);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(-1, -2);
        taglineParams.topMargin = dp(3);
        taglineParams.bottomMargin = dp(26);
        content.addView(tagline, taglineParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, 20));
        TextView format = text("●  48 kHz stereo · uncompressed", 15, GREEN);
        format.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(format);
        TextView description = text("Connect to the same Wi-Fi as your AudioBridge board. Tap Start and the app finds it automatically. Audio keeps playing in the background through your selected output.", 15, MUTED);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(12);
        card.addView(description, descriptionParams);
        content.addView(card);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), dp(18), dp(18), dp(18));
        controls.setBackground(rounded(CARD, 20));
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(18);
        content.addView(controls, controlsParams);
        TextView controlsTitle = text("AUDIOBRIDGE CONTROLS", 13, MAGENTA);
        controlsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        controls.addView(controlsTitle);
        sourceStatus = text("Start listening to connect", 15, MUTED);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(8);
        controls.addView(sourceStatus, statusParams);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
        rowParams.topMargin = dp(14);
        controls.addView(row, rowParams);
        previousButton = button("◀◀", MAGENTA, WHITE,
                v -> control(AudioService.ACTION_PREVIOUS));
        playButton = button("▶  Play", GREEN, BACKGROUND,
                v -> control(AudioService.ACTION_PLAY_PAUSE));
        nextButton = button("▶▶", MAGENTA, WHITE,
                v -> control(AudioService.ACTION_NEXT));
        row.addView(previousButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -1, 2);
        middle.setMargins(dp(7), 0, dp(7), 0);
        row.addView(playButton, middle);
        row.addView(nextButton, new LinearLayout.LayoutParams(0, -1, 1));
        muteButton = button("Mute", RED, WHITE, v -> control(AudioService.ACTION_MUTE));
        LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(-1, dp(44));
        muteParams.topMargin = dp(10);
        controls.addView(muteButton, muteParams);
        volumeLabel = text("Board stream volume  100%", 15, WHITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(15);
        controls.addView(volumeLabel, labelParams);
        volumeSlider = new SeekBar(this);
        volumeSlider.setMax(100);
        volumeSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        volumeSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(MAGENTA));
        controls.addView(volumeSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { sliding = true; }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) volumeLabel.setText("Board stream volume  " + value + "%");
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                sliding = false;
                startService(new Intent(MainActivity.this, AudioService.class)
                        .setAction(AudioService.ACTION_VOLUME)
                        .putExtra(AudioService.EXTRA_VOLUME, bar.getProgress()));
            }
        });
        TextView sourceHint = text("Volume, mute, and pause control audio on the board. Skip requires a control link to the LINEIN source.", 12, MUTED);
        controls.addView(sourceHint);

        Button start = button("▶   Start listening", GREEN, BACKGROUND, v -> {
            Intent intent = new Intent(this, AudioService.class);
            intent.setAction(AudioService.ACTION_START);
            startForegroundService(intent);
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(56));
        startParams.topMargin = dp(28);
        content.addView(start, startParams);

        Button stop = button("■   Stop listening", RED, WHITE, v -> {
            Intent intent = new Intent(this, AudioService.class);
            intent.setAction(AudioService.ACTION_STOP);
            startService(intent);
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-1, dp(56));
        stopParams.topMargin = dp(12);
        content.addView(stop, stopParams);

        Button setup = button("⚙   Set up board Wi-Fi", MAGENTA, WHITE,
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://audiobridge.local/"))));
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(-1, dp(54));
        setupParams.topMargin = dp(24);
        content.addView(setup, setupParams);

        TextView hint = text("First time? Join A1S-Audio (password: audiobridge), open setup, then return to your normal Wi-Fi. Use Stop in the notification to close playback.", 13, MUTED);
        hint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(16);
        content.addView(hint, hintParams);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("designed by Vedant Bhale", 12, MUTED);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        handler.post(refresh);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }
}
