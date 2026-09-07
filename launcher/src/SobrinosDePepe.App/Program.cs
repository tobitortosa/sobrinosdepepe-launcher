using Avalonia;
using SobrinosDePepe.Core;
using Velopack;

namespace SobrinosDePepe.App;

internal static class Program
{
    /// <summary>
    /// true cuando el launcher se abrió para desinstalarse. Windows lo llama así desde
    /// "Aplicaciones instaladas": lo dejó escrito UninstallEntry.
    /// </summary>
    public static bool IsUninstalling { get; private set; }

    [STAThread]
    public static void Main(string[] args)
    {
        // Velopack maneja la instalación y las actualizaciones. Tiene que ser lo primero
        // que corra: en el arranque posterior a una actualización, esta llamada termina
        // el trabajo y reinicia la aplicación.
        //
        // El aviso de desinstalación es el último recurso para no dejar basura: si alguien
        // desinstala sin pasar por nuestra pantalla (una herramienta automática, o Velopack
        // llamado a mano), esto borra el juego y los mods antes de que se borre la carpeta.
        VelopackApp.Build()
            .OnBeforeUninstallFastCallback(_ => Uninstall.RemoveDataQuickly())
            .Run();

        IsUninstalling = args.Any(a => a.Equals("--uninstall", StringComparison.OrdinalIgnoreCase));

        if (!IsUninstalling) UninstallEntry.PointToLauncher();

        BuildAvaloniaApp().StartWithClassicDesktopLifetime([]);
    }

    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<Application>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();
}
