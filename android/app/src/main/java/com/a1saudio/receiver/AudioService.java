package com.a1saudio.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioService extends Service {
    public static final String ACTION_START = "com.a1saudio.receiver.START";
    public static final String ACTION_STOP = "com.a1saudio.receiver.STOP";
    public static final String ACTION_PLAY_PAUSE = "com.a1saudio.receiver.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.a1saudio.receiver.NEXT";
    public static final String ACTION_PREVIOUS = "com.a1saudio.receiver.PREVIOUS";
    public static final String ACTION_MUTE = "com.a1saudio.receiver.MUTE";
    public static final String ACTION_VOLUME = "com.a1saudio.receiver.VOLUME";
    public static final String EXTRA_VOLUME = "source_volume";
    private static final String CHANNEL = "audio_playback";
    private static final int NOTIFICATION_ID = 1;
    private static final int PORT = 45678;
    private static final int PCM_BYTES = 960;
    private static final int START_PACKETS = 12;
    private static final int MAX_QUEUE_PACKETS = 40;
    private static final byte[] SILENCE = new byte[PCM_BYTES];
    private volatile boolean running;
    private volatile DatagramSocket socket;
    private volatile InetAddress boardAddress;
    public static volatile boolean isListening, sourceOnline, sourcePlaying = true, sourceMuted;
    public static volatile boolean sourceCanNext, sourceCanPrevious;
    public static volatile int sourceVolume = 100;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListening();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getAction() != null && !ACTION_START.equals(intent.getAction())) {
            String action = intent.getAction();
            String command = null;
            if (ACTION_PLAY_PAUSE.equals(action)) command = "SRC PLAYPAUSE\n";
            else if (ACTION_MUTE.equals(action)) command = sourceMuted ? "SRC MUTE 0\n" : "SRC MUTE 1\n";
            else if (ACTION_VOLUME.equals(action)) {
                int value = intent.getIntExtra(EXTRA_VOLUME, 100);
                if (value >= 0 && value <= 100) command = "SRC VOL " + value + "\n";
            }
            if (command != null && socket != null && boardAddress != null) {
                try { send(socket, boardAddress, command); }
                catch (Exception e) { Log.w("A1S receiver", "Control failed", e); }
            }
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Audio playback", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification("Finding AudioBridge on Wi-Fi"));
        running = true;
        isListening = true;
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "A1SAudio:receive");
        wakeLock.acquire();
        worker = new Thread(this::receiveLoop, "A1S receiver");
        worker.start();
        return START_NOT_STICKY;
    }

    private PendingIntent serviceAction(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, AudioService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification(String status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openAction = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("AudioBridge")
                .setContentText(status)
                .setContentIntent(openAction)
                .addAction(new Notification.Action.Builder(sourcePlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        sourcePlaying ? "Pause stream" : "Resume stream", serviceAction(ACTION_PLAY_PAUSE, 2)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_lock_silent_mode,
                        sourceMuted ? "Unmute" : "Mute", serviceAction(ACTION_MUTE, 3)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,
                        "Stop", serviceAction(ACTION_STOP, 1)).build())
                .setOngoing(true).build();
    }

    private void updateStatus(String status) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(status));
    }

    private Network wifiNetwork() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return network;
        }
        return null;
    }

    private InetAddress subnetBroadcast(Network network) throws Exception {
        LinkProperties properties = getSystemService(ConnectivityManager.class).getLinkProperties(network);
        if (properties == null) return null;
        for (LinkAddress link : properties.getLinkAddresses()) {
            if (!(link.getAddress() instanceof Inet4Address)) continue;
            byte[] ip = link.getAddress().getAddress();
            int prefix = link.getPrefixLength();
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            int address = ((ip[0] & 255) << 24) | ((ip[1] & 255) << 16) |
                    ((ip[2] & 255) << 8) | (ip[3] & 255);
            int broadcast = address | ~mask;
            return InetAddress.getByAddress(new byte[] {(byte)(broadcast >> 24),
                    (byte)(broadcast >> 16), (byte)(broadcast >> 8), (byte)broadcast});
        }
        return null;
    }

    private InetAddress discover(DatagramSocket current, Network network) throws Exception {
        current.setBroadcast(true);
        current.setSoTimeout(500);
        InetAddress global = InetAddress.getByName("255.255.255.255");
        InetAddress local = subnetBroadcast(network);
        byte[] reply = new byte[160];
        DatagramPacket packet = new DatagramPacket(reply, reply.length);
        long deadline = System.currentTimeMillis() + 5000;
        while (running && System.currentTimeMillis() < deadline) {
            send(current, global, "DISCOVER AUB1\n");
            if (local != null && !local.equals(global)) send(current, local, "DISCOVER AUB1\n");
            long round = System.currentTimeMillis() + 1000;
            while (running && System.currentTimeMillis() < round) {
                try { packet.setLength(reply.length); current.receive(packet); }
                catch (java.net.SocketTimeoutException ignored) { continue; }
                if (packet.getPort() != PORT) continue;
                String message = new String(reply, 0, packet.getLength(), StandardCharsets.US_ASCII);
                if (message.startsWith("HERE AUB1 48000 2 16 240 45678 "))
                    return packet.getAddress();
            }
        }
        return null;
    }

    private void playbackLoop(AudioTrack track, LinkedBlockingDeque<byte[]> queue,
                              AtomicBoolean session) {
        try {
            while (running && session.get() && queue.size() < START_PACKETS)
                Thread.sleep(5);
            if (!running || !session.get()) return;
            track.play();
            while (running && session.get()) {
                byte[] pcm = queue.poll(20, TimeUnit.MILLISECONDS);
                if (pcm == null) pcm = SILENCE;
                int offset = 0;
                while (offset < PCM_BYTES && running && session.get()) {
                    int written = track.write(pcm, offset, PCM_BYTES - offset, AudioTrack.WRITE_BLOCKING);
                    if (written <= 0) break;
                    offset += written;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) Log.w("A1S receiver", "Playback failed", e);
        } finally {
            session.set(false);
        }
    }

    private void receiveLoop() {
        while (running) {
            Network network = wifiNetwork();
            if (network == null) {
                updateStatus("Waiting for Wi-Fi connection");
                retryDelay();
                continue;
            }
            DatagramSocket current = null;
            AudioTrack track = null;
            InetAddress board = null;
            Thread player = null;
            AtomicBoolean session = new AtomicBoolean(true);
            try {
                current = new DatagramSocket();
                socket = current;
                network.bindSocket(current);
                updateStatus("Finding AudioBridge on Wi-Fi");
                board = discover(current, network);
                if (board == null) continue;
                boardAddress = board;
                sourceOnline = true;
                current.setSoTimeout(1000);
                current.setReceiveBufferSize(256 * 1024);
                send(current, board, "JOIN\n");
                byte[] bytes = new byte[1200];
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                long joinDeadline = System.currentTimeMillis() + 3000;
                boolean joined = false;
                boolean boardFull = false;
                while (running && System.currentTimeMillis() < joinDeadline) {
                    try { packet.setLength(bytes.length); current.receive(packet); }
                    catch (java.net.SocketTimeoutException ignored) { continue; }
                    if (!packet.getAddress().equals(board) || packet.getPort() != PORT) continue;
                    String response = new String(bytes, 0, packet.getLength(), StandardCharsets.US_ASCII);
                    if (response.equals("OK AUB1 48000 2 16 240\n")) { joined = true; break; }
                    if (response.startsWith("ERR full")) { boardFull = true; break; }
                }
                if (!joined) {
                    updateStatus(boardFull ? "Board listener limit reached" : "Waiting for AudioBridge");
                    continue;
                }

                int minBytes = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT);
                int bufferBytes = Math.max(minBytes, 960 * 12);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(48000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferBytes)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build();
                if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack failed");
                LinkedBlockingDeque<byte[]> queue = new LinkedBlockingDeque<>(MAX_QUEUE_PACKETS);
                AudioTrack playbackTrack = track;
                player = new Thread(() -> playbackLoop(playbackTrack, queue, session), "A1S playback");
                player.start();
                updateStatus("Playing from AudioBridge");
                long lastKeep = System.currentTimeMillis();
                long lastState = 0;
                long lastAudio = lastKeep;
                long lastReport = lastKeep;
                long expected = -1;
                int received = 0, missingPackets = 0, queueDrops = 0;
                while (running && session.get()) {
                    long now = System.currentTimeMillis();
                    if (now - lastKeep >= 3000) { send(current, board, "KEEP\n"); lastKeep = now; }
                    if (now - lastState >= 2000) { send(current, board, "GET STATE\n"); lastState = now; }
                    if (now - lastAudio > 3000) break;
                    try { packet.setLength(bytes.length); current.receive(packet); }
                    catch (java.net.SocketTimeoutException ignored) { continue; }
                    if (packet.getAddress().equals(board) && packet.getLength() < 120) {
                        String state = new String(bytes, 0, packet.getLength(), StandardCharsets.US_ASCII);
                        String[] fields = state.trim().split("\\s+");
                        if (fields.length == 13 && fields[0].equals("STATE") && fields[1].equals("BOARD")) {
                            sourceOnline = true;
                            sourceVolume = Integer.parseInt(fields[4]);
                            sourceMuted = fields[6].equals("1");
                            sourcePlaying = fields[8].equals("1");
                            updateStatus(sourcePlaying ? "Streaming from AudioBridge" : "AudioBridge stream paused");
                        }
                    }
                    if (!packet.getAddress().equals(board) || packet.getPort() != PORT ||
                            packet.getLength() != 976 || bytes[0] != 'A' || bytes[1] != 'U' ||
                            bytes[2] != 'B' || bytes[3] != '1' ||
                            bytes[12] != 0 || (bytes[13] & 255) != 240 ||
                            bytes[14] != 2 || bytes[15] != 1) continue;
                    long sequence = ((long)(bytes[4] & 255) << 24) | ((long)(bytes[5] & 255) << 16)
                            | ((long)(bytes[6] & 255) << 8) | (bytes[7] & 255);
                    if (expected >= 0) {
                        long missing = (sequence - expected) & 0xffffffffL;
                        if (missing >= 0x80000000L) continue;
                        missingPackets += (int)Math.min(missing, 100000);
                        for (int i = 0; i < Math.min(missing, 3); i++)
                            queue.offerLast(SILENCE);
                    }
                    if (!queue.offerLast(Arrays.copyOfRange(bytes, 16, 976))) {
                        queue.pollFirst();
                        queue.offerLast(Arrays.copyOfRange(bytes, 16, 976));
                        queueDrops++;
                    }
                    if (queue.size() > 32) {
                        if (queue.pollFirst() != null) queueDrops++;
                    }
                    expected = (sequence + 1) & 0xffffffffL;
                    lastAudio = System.currentTimeMillis();
                    received++;
                    if (lastAudio - lastReport >= 10000) {
                        Log.i("A1S receiver", "10s received=" + received + " missing=" + missingPackets +
                                " queueDrops=" + queueDrops + " queued=" + queue.size() +
                                " underruns=" + track.getUnderrunCount());
                        received = missingPackets = queueDrops = 0;
                        lastReport = lastAudio;
                    }
                }
            } catch (Exception e) {
                if (running) { Log.w("A1S receiver", "Receive failed", e); updateStatus("Reconnecting: " + e.getMessage()); }
            } finally {
                session.set(false);
                if (player != null) player.interrupt();
                if (track != null) {
                    track.pause();
                    if (player != null) try { player.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    track.flush();
                    track.release();
                }
                if (current != null) {
                    if (board != null) try { send(current, board, "LEAVE\n"); } catch (Exception ignored) { }
                    current.close();
                }
                socket = null;
                boardAddress = null;
                sourceOnline = false;
            }
            retryDelay();
        }
    }

    private static void send(DatagramSocket socket, InetAddress ip, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        socket.send(new DatagramPacket(bytes, bytes.length, ip, PORT));
    }

    private void retryDelay() {
        try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void stopListening() {
        running = false;
        isListening = false;
        sourceOnline = false;
        DatagramSocket current = socket;
        if (current != null) current.close();
        if (worker != null) worker.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
