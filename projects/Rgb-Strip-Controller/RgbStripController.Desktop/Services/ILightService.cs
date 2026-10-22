using RgbStripController.Desktop.Models;

namespace RgbStripController.Desktop.Services;

public interface ILightService
{
    Task SetColorAsync(Uri endpoint, byte red, byte green, byte blue, CancellationToken cancellationToken);
    Task StartRainbowAsync(Uri endpoint, RainbowDirection direction, CancellationToken cancellationToken);
}
