using System.Diagnostics;
using Avalonia.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RgbStripController.Desktop.Models;
using RgbStripController.Desktop.Services;

namespace RgbStripController.Desktop.ViewModels;

public partial class MainViewModel : ViewModelBase, IDisposable
{
    private readonly ILightService _lightService;
    private readonly JsonSettingsService _settingsService;
    private readonly CancellationTokenSource _shutdown = new();
    private bool _updatingColor;

    public MainViewModel(ILightService lightService, JsonSettingsService settingsService)
    {
        _lightService = lightService;
        _settingsService = settingsService;

        var loaded = settingsService.Load();
        BaseAddress = loaded.Settings.BaseAddress;
        Red = loaded.Settings.Red;
        Green = loaded.Settings.Green;
        Blue = loaded.Settings.Blue;
        IsOn = loaded.Settings.IsOn;
        Direction = loaded.Settings.Direction;
        WarningMessage = loaded.Warning ?? "";
        Status = "Idle";
    }

    public IReadOnlyList<RainbowDirection> Directions { get; } =
        [RainbowDirection.Forward, RainbowDirection.Reverse];

    public bool CanInteract => !IsBusy;

    public bool HasEndpointError => EndpointError.Length > 0;

    public string HeaderStatus => IsBusy
        ? "Sending…"
        : EndpointValidation.IsValid(BaseAddress)
            ? "Ready to send"
            : "Check address";

    public string ColorHex => $"#{Color.R:X2}{Color.G:X2}{Color.B:X2}";

    public string EndpointError => string.IsNullOrWhiteSpace(BaseAddress)
        ? "Enter the controller's HTTP or HTTPS base address."
        : EndpointValidation.IsValid(BaseAddress)
            ? ""
            : "Use an HTTP or HTTPS address with an optional port and no path, query, or credentials.";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(EndpointError))]
    [NotifyPropertyChangedFor(nameof(HasEndpointError))]
    [NotifyPropertyChangedFor(nameof(HeaderStatus))]
    [NotifyCanExecuteChangedFor(nameof(ApplyCommand))]
    [NotifyCanExecuteChangedFor(nameof(StartRainbowCommand))]
    private string baseAddress = "";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HeaderStatus))]
    [NotifyCanExecuteChangedFor(nameof(ApplyCommand))]
    [NotifyCanExecuteChangedFor(nameof(StartRainbowCommand))]
    private bool isBusy;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(StartRainbowCommand))]
    private bool isOn = true;

    [ObservableProperty]
    private string status = "Idle";

    [ObservableProperty]
    private string warningMessage = "";

    [ObservableProperty]
    private RainbowDirection direction = RainbowDirection.Forward;

    [ObservableProperty]
    private byte red = 255;

    [ObservableProperty]
    private byte green = 128;

    [ObservableProperty]
    private byte blue = 64;

    [ObservableProperty]
    private Color color = Color.FromRgb(255, 128, 64);

    private bool CanApply() => !IsBusy && EndpointValidation.IsValid(BaseAddress);

    private bool CanStartRainbow() => IsOn && CanApply();

    [RelayCommand(CanExecute = nameof(CanApply))]
    private Task ApplyAsync() => RunRequestAsync(
        "Sending color…",
        "Color applied.",
        (endpoint, token) => _lightService.SetColorAsync(
            endpoint,
            IsOn ? Red : (byte)0,
            IsOn ? Green : (byte)0,
            IsOn ? Blue : (byte)0,
            token));

    [RelayCommand(CanExecute = nameof(CanStartRainbow))]
    private Task StartRainbowAsync() => RunRequestAsync(
        "Starting rainbow…",
        "Rainbow started.",
        (endpoint, token) => _lightService.StartRainbowAsync(endpoint, Direction, token));

    public bool SaveSettings()
    {
        var warning = _settingsService.Save(new ControllerSettings
        {
            BaseAddress = EndpointValidation.IsValid(BaseAddress) ? BaseAddress.Trim() : "",
            Red = Red,
            Green = Green,
            Blue = Blue,
            IsOn = IsOn,
            Direction = Direction,
        });

        WarningMessage = warning ?? "";
        return warning is null;
    }

    public void Dispose()
    {
        if (_shutdown.IsCancellationRequested)
            return;

        _shutdown.Cancel();
        _shutdown.Dispose();
    }

    partial void OnIsBusyChanged(bool value) => OnPropertyChanged(nameof(CanInteract));

    partial void OnRedChanged(byte value) => UpdateColorFromChannels();
    partial void OnGreenChanged(byte value) => UpdateColorFromChannels();
    partial void OnBlueChanged(byte value) => UpdateColorFromChannels();

    partial void OnColorChanged(Color value)
    {
        OnPropertyChanged(nameof(ColorHex));
        if (_updatingColor)
            return;

        _updatingColor = true;
        Red = value.R;
        Green = value.G;
        Blue = value.B;
        _updatingColor = false;
    }

    private void UpdateColorFromChannels()
    {
        if (_updatingColor)
            return;

        _updatingColor = true;
        Color = Color.FromRgb(Red, Green, Blue);
        _updatingColor = false;
    }

    private async Task RunRequestAsync(
        string sendingStatus,
        string successStatus,
        Func<Uri, CancellationToken, Task> send)
    {
        if (IsBusy)
            return;
        if (!EndpointValidation.TryParse(BaseAddress, out var endpoint) || endpoint is null)
        {
            Status = "Enter a valid controller address.";
            return;
        }

        IsBusy = true;
        Status = sendingStatus;
        try
        {
            await send(endpoint, _shutdown.Token);
            Status = successStatus;
        }
        catch (OperationCanceledException) when (_shutdown.IsCancellationRequested)
        {
        }
        catch (OperationCanceledException)
        {
            Status = "Controller request timed out after 5 seconds.";
            Trace.TraceError("Controller request timed out.");
        }
        catch (Exception exception)
        {
            Status = $"Failed: {exception.Message}";
            Trace.TraceError($"Controller request failed: {exception}");
        }
        finally
        {
            IsBusy = false;
        }
    }
}
