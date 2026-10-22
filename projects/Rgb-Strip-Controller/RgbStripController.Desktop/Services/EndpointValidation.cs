namespace RgbStripController.Desktop.Services;

public static class EndpointValidation
{
    public static bool IsValid(string? value) => TryParse(value, out _);

    public static bool TryParse(string? value, out Uri? endpoint)
    {
        endpoint = null;
        if (string.IsNullOrWhiteSpace(value))
            return false;

        var candidate = value.Trim();
        if (candidate.Any(character => char.IsWhiteSpace(character) || char.IsControl(character)) ||
            !Uri.TryCreate(candidate, UriKind.Absolute, out var parsed) ||
            (parsed.Scheme != Uri.UriSchemeHttp && parsed.Scheme != Uri.UriSchemeHttps) ||
            string.IsNullOrWhiteSpace(parsed.Host) ||
            Uri.CheckHostName(parsed.Host) == UriHostNameType.Unknown ||
            !string.IsNullOrEmpty(parsed.UserInfo) ||
            !string.IsNullOrEmpty(parsed.Query) ||
            !string.IsNullOrEmpty(parsed.Fragment) ||
            (parsed.AbsolutePath != "/" && parsed.AbsolutePath.Length != 0) ||
            (!parsed.IsDefaultPort && parsed.Port is < 1 or > 65535))
        {
            return false;
        }

        var schemeSeparator = candidate.IndexOf("://", StringComparison.Ordinal);
        var authorityEnd = candidate.IndexOfAny(['/', '?', '#'], schemeSeparator + 3);
        var authority = authorityEnd < 0
            ? candidate[(schemeSeparator + 3)..]
            : candidate[(schemeSeparator + 3)..authorityEnd];
        if (authority.EndsWith(':'))
            return false;

        endpoint = parsed;
        return true;
    }
}
