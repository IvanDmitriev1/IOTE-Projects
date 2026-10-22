#include "HttpHelpers.h"

namespace
{
bool ParseByte(const char *digits, uint8_t &out)
{
    if (!digits || !*digits)
    {
        return false;
    }

    uint16_t value = 0;

    for (const char *p = digits; *p; ++p)
    {
        if (*p < '0' || *p > '9')
        {
            return false;
        }
        value = value * 10 + (*p - '0');
        if (value > 255)
        {
            return false;
        }
    }
    out = static_cast<uint8_t>(value);
    return true;
}

} // namespace

bool QueryByte(Request &req, const char *name, uint8_t &out)
{
    char digits[4] = {};
    return req.query(name, digits, sizeof(digits)) && ParseByte(digits, out);
}

bool AreRgbKeysValid(const char *q)
{
    if (!q || !*q)
    {
        return false;
    }

    uint8_t seen = 0;
    while (*q)
    {
        uint8_t bit;
        if (q[0] == 'r')
        {
            bit = 1;
        }
        else if (q[0] == 'g')
        {
            bit = 2;
        }
        else if (q[0] == 'b')
        {
            bit = 4;
        }
        else
        {
            return false;
        }

        if (q[1] != '=' || (seen & bit))
        {
            return false;
        }
        seen |= bit;
        q += 2;
        if (!*q || *q == '&')
        {
            return false;
        }
        while (*q && *q != '&')
        {
            ++q;
        }
        if (*q == '&' && !*++q)
        {
            return false;
        }
    }
    return seen == 7;
}

bool HasNoRequestBody(Request &req, const char *transferEncoding)
{
    return req.left() == 0 && transferEncoding[0] == '\0';
}
