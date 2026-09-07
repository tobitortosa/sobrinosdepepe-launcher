using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using SobrinosDePepe.App.Views;

namespace SobrinosDePepe.App;

public partial class Application : Avalonia.Application
{
    public override void Initialize() => AvaloniaXamlLoader.Load(this);

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
            desktop.MainWindow = Program.IsUninstalling
                ? new UninstallWindow()
                : new MainWindow();

        base.OnFrameworkInitializationCompleted();
    }
}
