#pragma once

#ifndef LOW_MEMORY_MCU
#define LOW_MEMORY_MCU
#endif
#include <aWOT.h>

bool QueryByte(Request &req, const char *name, uint8_t &out);
bool AreRgbKeysValid(const char *query);
bool HasNoRequestBody(Request &req, const char *transferEncoding);
