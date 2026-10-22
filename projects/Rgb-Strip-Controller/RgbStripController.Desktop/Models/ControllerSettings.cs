namespace RgbStripController.Desktop.Models;

public sealed class ControllerSettings
{
    public string BaseAddress { get; set; } = "";
    public byte Red { get; set; } = 255;
    public byte Green { get; set; } = 128;
    public byte Blue { get; set; } = 64;
    public bool IsOn { get; set; } = true;
    public RainbowDirection Direction { get; set; } = RainbowDirection.Forward;
}
