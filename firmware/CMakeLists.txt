cmake_minimum_required(VERSION 3.5)

include($ENV{ADF_PATH}/CMakeLists.txt)
set(COMPONENTS main)
include($ENV{IDF_PATH}/tools/cmake/project.cmake)

project(audio_broadcaster)

# Several ADF components include board.h but do not declare the custom board as
# a CMake requirement. Give only those compiled components the board header.
foreach(adf_component audio_hal audio_stream battery_service bluetooth_service
        display_service dueros_service esp_peripherals input_key_service
        ota_service playlist)
    if(TARGET __idf_${adf_component})
        target_include_directories(__idf_${adf_component} PRIVATE
            "${CMAKE_SOURCE_DIR}/components/ai-thinker-esp32-a1s/ai_thinker_esp32_a1s")
    endif()
endforeach()

# ADF's codec library needs the I2C bus object in esp_peripherals, but archive
# order otherwise leaves it unselected. Force that object into the link.
target_link_options(${PROJECT_NAME}.elf PRIVATE "LINKER:-u,i2c_bus_create")
