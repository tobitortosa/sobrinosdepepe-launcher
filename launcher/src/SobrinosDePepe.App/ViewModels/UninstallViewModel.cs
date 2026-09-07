using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// La pantalla que se abre cuando alguien desinstala desde Windows.
///
/// Tiene tres momentos: preguntar, borrar y despedirse. Se pregunta una sola vez y no hay
/// opciones que elegir: se borra todo. Los mundos del servidor están en el servidor, así
/// que acá no se pierde nada que no se pueda volver a bajar.
/// </summary>
public partial class UninstallViewModel : ObservableObject
{
    /// <summary>Lo que se va a borrar, ya escrito para mostrar: "1,8 GB".</summary>
    [ObservableProperty] private string? _size;

    [ObservableProperty] private bool _isAsking = true;
    [ObservableProperty] private bool _isWorking;
    [ObservableProperty] private bool _isDone;

    [ObservableProperty] private string _message = "";
    [ObservableProperty] private double _percent;

    /// <summary>Se dispara cuando hay que cerrar la ventana.</summary>
    public event Action? Close;

    public UninstallViewModel()
    {
        _ = MeasureAsync();
    }

    /// <summary>
    /// Cuánto ocupa la instalación. Recorrer un par de gigas de archivos tarda un instante,
    /// así que la cifra aparece sola cuando está lista y la pantalla no espera por ella.
    /// </summary>
    private async Task MeasureAsync()
    {
        var megas = await Task.Run(Uninstall.TotalSize) / 1024.0 / 1024;
        if (megas < 1) return;

        // Coma decimal, como se escribe acá. La cultura del programa es la invariante
        // (InvariantGlobalization en el csproj), así que el punto hay que cambiarlo a mano.
        Size = megas >= 1024
            ? $"{megas / 1024:0.0} GB".Replace('.', ',')
            : $"{megas:0} MB";
    }

    [RelayCommand]
    private async Task RemoveAsync()
    {
        if (IsWorking) return;

        IsAsking = false;
        IsWorking = true;
        Message = "Cerrando el juego…";

        // Si dejaron el juego abierto, sus archivos están tomados y no se pueden borrar.
        await GameProcess.CloseAsync();

        var progress = new Progress<UninstallProgress>(step =>
        {
            Message = step.Message;
            Percent = step.Percent;
        });

        await Uninstall.RemoveDataAsync(progress);

        IsWorking = false;
        IsDone = true;
    }

    /// <summary>El botón "Mejor me quedo": no se tocó nada, se cierra y listo.</summary>
    [RelayCommand]
    private void Cancel() => Close?.Invoke();

    /// <summary>
    /// El último paso. Velopack borra su instalación, los accesos directos y la entrada de
    /// Windows, y un cmd suelto remata la carpeta. No se puede esperar a que termine: lo
    /// primero que hace Velopack es cerrar este mismo programa.
    /// </summary>
    [RelayCommand]
    private void Finish()
    {
        Uninstall.FinishAndExit();
        Environment.Exit(0);
    }

    [RelayCommand]
    private static void OpenLink(string url) =>
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
}
