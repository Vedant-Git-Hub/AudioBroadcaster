idf_component_register(SRCS "main.c" "network.c" INCLUDE_DIRS "."
    REQUIRES ai-thinker-esp32-a1s audio_board audio_hal audio_pipeline audio_stream
             esp_peripherals display_service esp_wifi esp_event esp_netif
             nvs_flash lwip esp_timer esp_http_server driver mdns)
