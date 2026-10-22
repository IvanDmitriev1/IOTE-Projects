using RgbStripController.Desktop.Models;

namespace RgbStripController.Desktop.Services;

public sealed class HttpLightService(HttpClient httpClient) : ILightService
{
    public Task SetColorAsync(Uri endpoint, byte red, byte green, byte blue, CancellationToken cancellationToken) =>
        SendAsync(endpoint, "rgb", $"r={red}&g={green}&b={blue}", cancellationToken);

    public Task StartRainbowAsync(Uri endpoint, RainbowDirection direction, CancellationToken cancellationToken) =>
        SendAsync(endpoint, "rainbow", $"direction={(int)direction}", cancellationToken);

    private async Task SendAsync(Uri endpoint, string path, string query, CancellationToken cancellationToken)
    {
        var uri = new UriBuilder(endpoint)
        {
            Path = $"/{path}",
            Query = query,
        }.Uri;

        using var request = new HttpRequestMessage(HttpMethod.Get, uri);
        using var response = await httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        if (!response.IsSuccessStatusCode)
            throw new HttpRequestException($"Controller returned HTTP {(int)response.StatusCode} ({response.StatusCode}).");
    }
}
