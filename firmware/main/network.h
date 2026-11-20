#pragma once

#include <stdbool.h>

void network_start(void);
bool network_ready(void);
const char *network_mode_label(void);
