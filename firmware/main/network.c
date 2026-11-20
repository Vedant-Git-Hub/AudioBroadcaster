#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driver/gpio.h"
#include "esp_event.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "mdns.h"
#include "nvs.h"
#include "sdkconfig.h"

#include "network.h"

static const char *TAG = "audio_network";
static volatile bool ready;
static volatile bool sta_connected;
static volatile bool sta_attempting;
static int retries;
static TaskHandle_t fallback_task_handle;

bool network_ready(void) { return ready; }
const char *network_mode_label(void) { return sta_connected ? "STA" : "AP"; }

static void led_task(void *arg)
{
    gpio_reset_pin(GPIO_NUM_22); // Green LED on Ai-Thinker Audio Kit V2.2.
    gpio_set_direction(GPIO_NUM_22, GPIO_MODE_OUTPUT);
    bool blink = false;
    while (true) {
        blink = !blink;
        // The board LED is active low: 0 is lit, 1 is off.
        gpio_set_level(GPIO_NUM_22, sta_connected ? 0 : blink);
        vTaskDelay(pdMS_TO_TICKS(400));
    }
}

static void restart_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(1200));
    esp_restart();
}

static int from_hex(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static bool form_value(const char *body, const char *key, char *out, size_t capacity)
{
    size_t key_len = strlen(key);
    const char *part = body;
    while (*part) {
        const char *end = strchr(part, '&');
        if (!end) end = part + strlen(part);
        if ((size_t)(end - part) > key_len && strncmp(part, key, key_len) == 0 && part[key_len] == '=') {
            const char *value = part + key_len + 1;
            size_t used = 0;
            while (value < end) {
                char c = *value++;
                if (c == '+') c = ' ';
                else if (c == '%' && value + 1 < end) {
                    int high = from_hex(value[0]), low = from_hex(value[1]);
                    if (high < 0 || low < 0) return false;
                    c = (char)((high << 4) | low);
                    value += 2;
                }
                if (c == 0 || used + 1 >= capacity) return false;
                out[used++] = c;
            }
            out[used] = 0;
            return true;
        }
        part = *end ? end + 1 : end;
    }
    return false;
}

static esp_err_t page_get(httpd_req_t *request)
{
    static const char page[] =
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<title>AudioBridge Wi-Fi</title><style>body{font:18px sans-serif;max-width:420px;"
        "margin:3rem auto;padding:0 1rem}input,button{box-sizing:border-box;width:100%;padding:.8rem;"
        "margin:.4rem 0 1rem;font-size:1rem}</style></head><body><h1>AudioBridge</h1>"
        "<p>Connect this board to your home Wi-Fi. Your phone can then stay on that Wi-Fi and keep internet access.</p>"
        "<form method='post' action='/configure'><label>Wi-Fi name (SSID)<input name='ssid' maxlength='32' required></label>"
        "<label>Wi-Fi password<input name='password' type='password' maxlength='63'></label>"
        "<button type='submit'>Save and connect</button></form>"
        "<p>After saving, the board restarts and tries the network. Its green LED is steady when connected. "
        "If it cannot connect after five retries, A1S-Audio returns and the LED blinks.</p>"
        "<form method='post' action='/reset'><button type='submit'>Forget saved Wi-Fi</button></form>"
        "</body></html>";
    httpd_resp_set_type(request, "text/html; charset=utf-8");
    httpd_resp_set_hdr(request, "Cache-Control", "no-store");
    return httpd_resp_send(request, page, HTTPD_RESP_USE_STRLEN);
}

static esp_err_t configure_post(httpd_req_t *request)
{
    if (request->content_len <= 0 || request->content_len > 180) {
        httpd_resp_send_err(request, HTTPD_400_BAD_REQUEST, "Invalid form length");
        return ESP_FAIL;
    }
    char body[181];
    int total = 0;
    while (total < request->content_len) {
        int count = httpd_req_recv(request, body + total, request->content_len - total);
        if (count <= 0) return ESP_FAIL;
        total += count;
    }
    body[total] = 0;
    char ssid[33], password[64];
    if (!form_value(body, "ssid", ssid, sizeof(ssid)) || !ssid[0] ||
        !form_value(body, "password", password, sizeof(password))) {
        httpd_resp_send_err(request, HTTPD_400_BAD_REQUEST, "Invalid Wi-Fi name or password");
        return ESP_FAIL;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open("audiowifi", NVS_READWRITE, &handle);
    if (err == ESP_OK) {
        err = nvs_set_str(handle, "ssid", ssid);
        if (err == ESP_OK) err = nvs_set_str(handle, "password", password);
        if (err == ESP_OK) err = nvs_commit(handle);
        nvs_close(handle);
    }
    if (err != ESP_OK) {
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "Could not save Wi-Fi settings");
        return ESP_FAIL;
    }
    httpd_resp_set_type(request, "text/html; charset=utf-8");
    httpd_resp_sendstr(request, "<h1>Saved</h1><p>The board is restarting. Join your home Wi-Fi, then open the AudioBridge app.</p>");
    xTaskCreate(restart_task, "wifi_restart", 2048, NULL, 4, NULL);
    return ESP_OK;
}

static esp_err_t reset_post(httpd_req_t *request)
{
    nvs_handle_t handle;
    if (nvs_open("audiowifi", NVS_READWRITE, &handle) == ESP_OK) {
        nvs_erase_all(handle);
        nvs_commit(handle);
        nvs_close(handle);
    }
    httpd_resp_sendstr(request, "Saved Wi-Fi removed. The board is restarting in setup mode.");
    xTaskCreate(restart_task, "wifi_restart", 2048, NULL, 4, NULL);
    return ESP_OK;
}

static void start_web_server(void)
{
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.stack_size = 4096;
    httpd_handle_t server = NULL;
    ESP_ERROR_CHECK(httpd_start(&server, &config));
    const httpd_uri_t home = {.uri = "/", .method = HTTP_GET, .handler = page_get};
    const httpd_uri_t save = {.uri = "/configure", .method = HTTP_POST, .handler = configure_post};
    const httpd_uri_t reset = {.uri = "/reset", .method = HTTP_POST, .handler = reset_post};
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &home));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &save));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &reset));
}

static void configure_ap(void)
{
    wifi_config_t ap = {0};
    strncpy((char *)ap.ap.ssid, CONFIG_BROADCAST_AP_SSID, sizeof(ap.ap.ssid) - 1);
    ap.ap.ssid_len = strlen((char *)ap.ap.ssid);
    strncpy((char *)ap.ap.password, CONFIG_BROADCAST_AP_PASSWORD, sizeof(ap.ap.password) - 1);
    ap.ap.authmode = WIFI_AUTH_WPA2_PSK;
    ap.ap.max_connection = CONFIG_BROADCAST_MAX_LISTENERS;
    ap.ap.channel = 1;
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap));
}

static void fallback_task(void *arg)
{
    while (true) {
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
        ESP_LOGW(TAG, "Saved Wi-Fi unavailable after five retries; starting setup hotspot");
        ready = false;
        sta_connected = false;
        sta_attempting = false;
        ESP_ERROR_CHECK(esp_wifi_stop());
        configure_ap();
        ESP_ERROR_CHECK(esp_wifi_start());
    }
}

static void wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START && sta_attempting) {
        esp_wifi_connect();
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED && sta_attempting) {
        ready = false;
        sta_connected = false;
        if (++retries <= 5) {
            ESP_LOGW(TAG, "Router connection failed; retry %d/5", retries);
            esp_wifi_connect();
        } else {
            xTaskNotifyGive(fallback_task_handle);
        }
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        const ip_event_got_ip_t *event = data;
        retries = 0;
        ready = true;
        sta_connected = true;
        ESP_LOGI(TAG, "Connected to router, IP " IPSTR, IP2STR(&event->ip_info.ip));
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_AP_START) {
        ready = true;
        ESP_LOGI(TAG, "Setup hotspot: %s, open http://audiobridge.local/ (or http://192.168.4.1/)",
                 CONFIG_BROADCAST_AP_SSID);
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_AP_STOP) {
        ready = false;
    }
}

void network_start(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();
    esp_netif_create_default_wifi_ap();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event, NULL));
    ESP_ERROR_CHECK(mdns_init());
    ESP_ERROR_CHECK(mdns_hostname_set("audiobridge"));
    ESP_ERROR_CHECK(mdns_service_add("AudioBridge setup", "_http", "_tcp", 80, NULL, 0));
    start_web_server();
    xTaskCreate(led_task, "network_led", 2048, NULL, 3, NULL);
    xTaskCreate(fallback_task, "wifi_fallback", 3072, NULL, 4, &fallback_task_handle);

    char ssid[33] = {0}, password[64] = {0};
    nvs_handle_t handle;
    if (nvs_open("audiowifi", NVS_READONLY, &handle) == ESP_OK) {
        size_t length = sizeof(ssid);
        if (nvs_get_str(handle, "ssid", ssid, &length) != ESP_OK) ssid[0] = 0;
        length = sizeof(password);
        if (nvs_get_str(handle, "password", password, &length) != ESP_OK) password[0] = 0;
        nvs_close(handle);
    }
    if (!ssid[0] && CONFIG_BROADCAST_WIFI_SSID[0]) {
        strncpy(ssid, CONFIG_BROADCAST_WIFI_SSID, sizeof(ssid) - 1);
        strncpy(password, CONFIG_BROADCAST_WIFI_PASSWORD, sizeof(password) - 1);
    }
    if (ssid[0]) {
        wifi_config_t sta = {0};
        strncpy((char *)sta.sta.ssid, ssid, sizeof(sta.sta.ssid) - 1);
        strncpy((char *)sta.sta.password, password, sizeof(sta.sta.password) - 1);
        sta.sta.threshold.authmode = WIFI_AUTH_OPEN;
        sta_attempting = true;
        ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
        ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta));
        ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));
        ESP_LOGI(TAG, "Connecting to saved Wi-Fi: %s", ssid);
    } else {
        configure_ap();
    }
    ESP_ERROR_CHECK(esp_wifi_start());
}
