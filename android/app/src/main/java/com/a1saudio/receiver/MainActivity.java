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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(20, 16, 31);
    private static final int CARD = Color.rgb(38, 31, 54);
    private static final int WHITE = Color.rgb(250, 247, 255);
    private static final int MUTED = Color.rgb(195, 186, 210);
    private static final int GREEN = Color.rgb(107, 239, 163);
    private static final int RED = Color.rgb(255, 91, 104);
    private static final int MAGENTA = Color.rgb(234, 71, 202);

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
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(24) + left, dp(24) + top,
                    dp(24) + right, dp(16) + bottom);
            return insets;
        });
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

        Button privacy = button("Privacy policy", CARD, WHITE,
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Vedant-Git-Hub/AudioBroadcaster/blob/main/docs/PRIVACY_POLICY.md"))));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, dp(48));
        privacyParams.topMargin = dp(18);
        content.addView(privacy, privacyParams);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("designed by Vedant Bhale", 12, MUTED);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }
}
