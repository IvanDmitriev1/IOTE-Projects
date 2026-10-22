using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Microsoft.Extensions.DependencyInjection;
using RgbStripController.Desktop.Services;
using RgbStripController.Desktop.ViewModels;
using RgbStripController.Desktop.Views;

namespace RgbStripController.Desktop;

public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            var services = new ServiceCollection()
                .AddSingleton<HttpClient>(_ => new HttpClient(new SocketsHttpHandler { AllowAutoRedirect = false })
                {
                    Timeout = TimeSpan.FromSeconds(5),
                })
                .AddSingleton<ILightService, HttpLightService>()
                .AddSingleton<JsonSettingsService>()
                .AddTransient<MainViewModel>()
                .BuildServiceProvider();

            var viewModel = services.GetRequiredService<MainViewModel>();
            var mainWindow = new MainWindow
            {
                DataContext = viewModel,
            };
            var closeWithoutSaving = false;
            mainWindow.Closing += (_, args) =>
            {
                if (!closeWithoutSaving && !viewModel.SaveSettings())
                {
                    viewModel.WarningMessage += " Close again to exit without saving.";
                    closeWithoutSaving = true;
                    args.Cancel = true;
                    return;
                }

                viewModel.Dispose();
            };
            desktop.MainWindow = mainWindow;
            desktop.Exit += (_, _) => services.Dispose();
        }

        base.OnFrameworkInitializationCompleted();
    }
}
