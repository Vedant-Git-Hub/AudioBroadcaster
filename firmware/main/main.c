#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <arpa/inet.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "nvs_flash.h"
#include "sdkconfig.h"

#include "audio_pipeline.h"
#include "board.h"
#include "es8388.h"
#include "i2s_stream.h"
#include "raw_stream.h"
#include "network.h"

#define SAMPLE_RATE             48000
#define CHANNELS               2
#define BITS_PER_SAMPLE        16
#define FRAMES_PER_PACKET      240
#define AUDIO_BYTES            (FRAMES_PER_PACKET * CHANNELS * BITS_PER_SAMPLE / 8)
#define HEADER_BYTES           16
#define CLIENT_TIMEOUT_US      (10LL * 1000 * 1000)

static const char *TAG = "broadcaster";
static int udp_socket = -1;
static SemaphoreHandle_t clients_lock;

typedef struct {
    struct sockaddr_in address;
    int64_t last_seen_us;
    bool active;
} listener_t;

static listener_t listeners[CONFIG_BROADCAST_MAX_LISTENERS];

static bool same_peer(const struct sockaddr_in *a, const struct sockaddr_in *b)
{
    return a->sin_addr.s_addr == b->sin_addr.s_addr && a->sin_port == b->sin_port;
}

static void expire_listeners(int64_t now_us)
{
    for (int i = 0; i < CONFIG_BROADCAST_MAX_LISTENERS; ++i) {
        if (listeners[i].active && now_us - listeners[i].last_seen_us > CLIENT_TIMEOUT_US) {
            listeners[i].active = false;
            ESP_LOGI(TAG, "Listener slot %d expired", i + 1);
        }
    }
}

static void reply_to(const struct sockaddr_in *peer, const char *message)
{
    sendto(udp_socket, message, strlen(message), 0,
           (const struct sockaddr *)peer, sizeof(*peer));
}

static void control_task(void *arg)
{
    char message[64];
    while (true) {
        struct sockaddr_in peer;
        socklen_t peer_len = sizeof(peer);
        ssize_t len = recvfrom(udp_socket, message, sizeof(message) - 1, 0,
                               (struct sockaddr *)&peer, &peer_len);
        if (len < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                ESP_LOGW(TAG, "UDP receive error: %d", errno);
            }
            continue;
        }
        message[len] = '\0';
        if (peer.sin_family != AF_INET) {
            continue;
        }

        const bool discover = strcmp(message, "DISCOVER AUB1\n") == 0;
        if (discover) {
            char response[80];
            snprintf(response, sizeof(response), "HERE AUB1 48000 2 16 240 %d %s\n",
                     CONFIG_BROADCAST_UDP_PORT, network_mode_label());
            reply_to(&peer, response);
            continue;
        }
        const bool join = strcmp(message, "JOIN\n") == 0;
        const bool keep = strcmp(message, "KEEP\n") == 0;
        const bool leave = strcmp(message, "LEAVE\n") == 0;
        if (!join && !keep && !leave) {
            reply_to(&peer, "ERR command\n");
            continue;
        }

        const int64_t now_us = esp_timer_get_time();
        int slot = -1;
        xSemaphoreTake(clients_lock, portMAX_DELAY);
        expire_listeners(now_us);
        for (int i = 0; i < CONFIG_BROADCAST_MAX_LISTENERS; ++i) {
            if (listeners[i].active && same_peer(&listeners[i].address, &peer)) {
                slot = i;
                break;
            }
        }
        if (leave) {
            if (slot >= 0) {
                listeners[slot].active = false;
            }
        } else if (slot >= 0) {
            listeners[slot].last_seen_us = now_us;
        } else if (join) {
            for (int i = 0; i < CONFIG_BROADCAST_MAX_LISTENERS; ++i) {
                if (!listeners[i].active) {
                    listeners[i].address = peer;
                    listeners[i].last_seen_us = now_us;
                    listeners[i].active = true;
                    slot = i;
                    break;
                }
            }
        }
        xSemaphoreGive(clients_lock);

        if (leave) {
            reply_to(&peer, "OK leave\n");
        } else if (slot >= 0) {
            reply_to(&peer, "OK AUB1 48000 2 16 240\n");
            if (join) {
                ESP_LOGI(TAG, "Listener joined slot %d", slot + 1);
            }
        } else {
            reply_to(&peer, join ? "ERR full\n" : "ERR unknown\n");
        }
    }
}

static void put_u32(uint8_t *dest, uint32_t value)
{
    dest[0] = (uint8_t)(value >> 24);
    dest[1] = (uint8_t)(value >> 16);
    dest[2] = (uint8_t)(value >> 8);
    dest[3] = (uint8_t)value;
}

#if CONFIG_BROADCAST_SILENCE_GATE
static void suppress_quiet_input(uint8_t *pcm)
{
    // The measured paused-source noise stays below this 100 ms moving average.
    // Hysteresis and a one-packet fade avoid rapid switching and clicks.
    static uint16_t recent[20];
    static uint32_t recent_sum;
    static unsigned recent_index;
    static bool open;
    uint32_t magnitude = 0;
    for (int i = 0; i < AUDIO_BYTES; i += 2) {
        int32_t sample = (int16_t)((uint16_t)pcm[i] | ((uint16_t)pcm[i + 1] << 8));
        magnitude += sample < 0 ? -sample : sample;
    }
    const uint16_t packet_level = magnitude / (AUDIO_BYTES / 2);
    recent_sum -= recent[recent_index];
    recent[recent_index] = packet_level;
    recent_sum += packet_level;
    recent_index = (recent_index + 1) % 20;
    const uint32_t average = recent_sum / 20;
    const bool was_open = open;
    if (!open && average >= CONFIG_BROADCAST_SILENCE_THRESHOLD) {
        open = true;
    } else if (open && average < CONFIG_BROADCAST_SILENCE_THRESHOLD * 2 / 3) {
        open = false;
    }
    if (open && was_open) {
        return;
    }
    if (!open && !was_open) {
        memset(pcm, 0, AUDIO_BYTES);
        return;
    }
    for (int frame = 0; frame < FRAMES_PER_PACKET; ++frame) {
        const int gain = open ? frame + 1 : FRAMES_PER_PACKET - frame - 1;
        for (int channel = 0; channel < CHANNELS; ++channel) {
            int offset = (frame * CHANNELS + channel) * 2;
            int32_t sample = (int16_t)((uint16_t)pcm[offset] | ((uint16_t)pcm[offset + 1] << 8));
            int16_t faded = (int16_t)(sample * gain / FRAMES_PER_PACKET);
            pcm[offset] = (uint8_t)faded;
            pcm[offset + 1] = (uint8_t)((uint16_t)faded >> 8);
        }
    }
}
#endif

static void audio_task(void *arg)
{
    audio_element_handle_t raw = arg;
    uint8_t packet[HEADER_BYTES + AUDIO_BYTES];
    memcpy(packet, "AUB1", 4);
    packet[14] = CHANNELS;
    packet[15] = 1; // Signed 16-bit little-endian interleaved PCM.
    uint32_t sequence = 0;
    uint32_t sample_frame = 0;

    while (true) {
        int have = 0;
        while (have < AUDIO_BYTES) {
            int count = raw_stream_read(raw, (char *)packet + HEADER_BYTES + have,
                                        AUDIO_BYTES - have);
            if (count <= 0) {
                ESP_LOGW(TAG, "Audio capture read failed: %d", count);
                vTaskDelay(pdMS_TO_TICKS(10));
                have = 0;
                break;
            }
            have += count;
        }
        if (have != AUDIO_BYTES) {
            continue;
        }

#if CONFIG_BROADCAST_SILENCE_GATE
        suppress_quiet_input(packet + HEADER_BYTES);
#endif

        put_u32(packet + 4, sequence++);
        put_u32(packet + 8, sample_frame);
        sample_frame += FRAMES_PER_PACKET;
        packet[12] = (uint8_t)(FRAMES_PER_PACKET >> 8);
        packet[13] = (uint8_t)FRAMES_PER_PACKET;

        if (!network_ready()) {
            continue;
        }
        struct sockaddr_in destinations[CONFIG_BROADCAST_MAX_LISTENERS];
        int count = 0;
        xSemaphoreTake(clients_lock, portMAX_DELAY);
        for (int i = 0; i < CONFIG_BROADCAST_MAX_LISTENERS; ++i) {
            if (listeners[i].active) {
                destinations[count++] = listeners[i].address;
            }
        }
        xSemaphoreGive(clients_lock);

        for (int i = 0; i < count; ++i) {
            // Never hold up capture for a slow or unreachable receiver.
            sendto(udp_socket, packet, sizeof(packet), MSG_DONTWAIT,
                   (const struct sockaddr *)&destinations[i], sizeof(destinations[i]));
        }
    }
}

static audio_element_handle_t start_capture(void)
{
    audio_board_handle_t board = audio_board_init();
    if (!board || !board->audio_hal) {
        ESP_LOGE(TAG, "Codec init failed; check ES8388 and board pin variant");
        abort();
    }
    ESP_ERROR_CHECK(audio_hal_ctrl_codec(board->audio_hal,
                                         AUDIO_HAL_CODEC_MODE_ENCODE, AUDIO_HAL_CTRL_START));
    ESP_ERROR_CHECK(es8388_set_mic_gain(MIC_GAIN_0DB));

    audio_pipeline_cfg_t pipeline_cfg = DEFAULT_AUDIO_PIPELINE_CONFIG();
    audio_pipeline_handle_t pipeline = audio_pipeline_init(&pipeline_cfg);
    if (!pipeline) {
        abort();
    }
    i2s_stream_cfg_t i2s_cfg = I2S_STREAM_CFG_DEFAULT_WITH_TYLE_AND_CH(
        CODEC_ADC_I2S_PORT, SAMPLE_RATE, I2S_DATA_BIT_WIDTH_16BIT,
        AUDIO_STREAM_READER, I2S_SLOT_MODE_STEREO);
    i2s_cfg.buffer_len = AUDIO_BYTES;
    i2s_cfg.out_rb_size = AUDIO_BYTES * 2;
    audio_element_handle_t i2s = i2s_stream_init(&i2s_cfg);

    raw_stream_cfg_t raw_cfg = RAW_STREAM_CFG_DEFAULT();
    raw_cfg.type = AUDIO_STREAM_READER;
    audio_element_handle_t raw = raw_stream_init(&raw_cfg);
    if (!i2s || !raw) {
        abort();
    }
    ESP_ERROR_CHECK(audio_pipeline_register(pipeline, i2s, "i2s"));
    ESP_ERROR_CHECK(audio_pipeline_register(pipeline, raw, "raw"));
    const char *links[] = {"i2s", "raw"};
    ESP_ERROR_CHECK(audio_pipeline_link(pipeline, links, 2));
    ESP_ERROR_CHECK(i2s_stream_set_clk(i2s, SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS));
    ESP_ERROR_CHECK(audio_pipeline_run(pipeline));
    return raw;
}

void app_main(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    clients_lock = xSemaphoreCreateMutex();
    if (!clients_lock) {
        abort();
    }
    network_start();

    udp_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    if (udp_socket < 0) {
        ESP_LOGE(TAG, "Cannot create UDP socket: %d", errno);
        abort();
    }
    struct timeval timeout = {.tv_sec = 1, .tv_usec = 0};
    setsockopt(udp_socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    struct sockaddr_in local = {
        .sin_family = AF_INET,
        .sin_port = htons(CONFIG_BROADCAST_UDP_PORT),
        .sin_addr.s_addr = htonl(INADDR_ANY),
    };
    if (bind(udp_socket, (struct sockaddr *)&local, sizeof(local)) < 0) {
        ESP_LOGE(TAG, "Cannot bind UDP port %d: %d", CONFIG_BROADCAST_UDP_PORT, errno);
        abort();
    }

    audio_element_handle_t raw = start_capture();
    if (xTaskCreate(control_task, "control", 4096, NULL, 5, NULL) != pdPASS ||
        xTaskCreate(audio_task, "audio_send", 6144, raw, 7, NULL) != pdPASS) {
        abort();
    }
    ESP_LOGI(TAG, "Streaming 48 kHz stereo PCM, port %d, max listeners %d",
             CONFIG_BROADCAST_UDP_PORT, CONFIG_BROADCAST_MAX_LISTENERS);
}
