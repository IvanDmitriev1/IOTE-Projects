using System.Text.Json;
using System.Text.Json.Serialization;
using RgbStripController.Desktop.Models;

namespace RgbStripController.Desktop.Services;

public sealed class JsonSettingsService
{
    private static readonly string SettingsPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RgbStripController",
        "settings.json");

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter(allowIntegerValues: false) },
    };

    public SettingsLoadResult Load()
    {
        try
        {
            var settings = JsonSerializer.Deserialize<ControllerSettings>(File.ReadAllText(SettingsPath), SerializerOptions)
                ?? new ControllerSettings();
            var warning = Normalize(settings);
            return new(settings, warning);
        }
        catch (FileNotFoundException)
        {
            return new(new ControllerSettings(), null);
        }
        catch (DirectoryNotFoundException)
        {
            return new(new ControllerSettings(), null);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
            return new(new ControllerSettings(), $"Saved settings could not be read and were reset: {exception.Message}");
        }
    }

    public string? Save(ControllerSettings settings)
    {
        string? temporaryPath = null;
        try
        {
            var directory = Path.GetDirectoryName(SettingsPath)!;
            Directory.CreateDirectory(directory);
            temporaryPath = Path.Combine(directory, $"settings.{Guid.NewGuid():N}.tmp");
            using (var stream = new FileStream(temporaryPath, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                JsonSerializer.Serialize(stream, settings, SerializerOptions);
                stream.Flush(flushToDisk: true);
            }

            File.Move(temporaryPath, SettingsPath, overwrite: true);
            return null;
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
            if (temporaryPath is not null)
            {
                try { File.Delete(temporaryPath); }
                catch (IOException) { }
                catch (UnauthorizedAccessException) { }
            }

            return $"Saved settings could not be written: {exception.Message}";
        }
    }

    private static string? Normalize(ControllerSettings settings)
    {
        var hadInvalidValues = false;
        if (settings.BaseAddress is null ||
            (settings.BaseAddress.Length > 0 && !EndpointValidation.IsValid(settings.BaseAddress)))
        {
            settings.BaseAddress = "";
            hadInvalidValues = true;
        }
        else
        {
            settings.BaseAddress = settings.BaseAddress.Trim();
        }

        if (!Enum.IsDefined(settings.Direction))
        {
            settings.Direction = RainbowDirection.Forward;
            hadInvalidValues = true;
        }

        return hadInvalidValues ? "Some saved settings were invalid and have been reset." : null;
    }
}
