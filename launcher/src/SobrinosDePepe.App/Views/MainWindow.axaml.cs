using Avalonia.Controls;
using SobrinosDePepe.App.ViewModels;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.Views;

public partial class MainWindow : Window
{
    private readonly ShellViewModel _shell = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _shell;

        Opened += async (_, _) => await _shell.StartAsync();

        // Cuando aparece una versión nueva, la ventana se pone adelante: si no, alguien
        // jugando en pantalla completa no se enteraría nunca. Puede estar minimizada,
        // así que primero hay que restaurarla.
        _shell.BringToFront += () =>
        {
            if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
            Show();
            Activate();
        };

        // La pantalla de actualización obligatoria se queda encima del juego. Sin esto
        // el juego recupera el foco enseguida y el aviso no se ve nunca.
        _shell.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(ShellViewModel.Current))
                Topmost = _shell.Current is UpdateRequiredViewModel;
        };

        // El launcher se queda abierto mientras se juega: al cerrarse dejaría de avisar
        // de las actualizaciones, y son obligatorias.
        //
        // Pero **nadie puede quedar atrapado**. Antes esto cancelaba el cierre siempre
        // que hubiera un proceso de Java vivo en nuestra carpeta, y cuando el juego se
        // cerraba mal —la ventana se va pero el proceso queda— el launcher no se dejaba
        // cerrar nunca más. Dos salidas, las dos sin diálogos nuevos:
        //
        //   - si el juego **no tiene ventana**, es un proceso colgado y no un juego: se
        //     lo mata y el launcher se cierra;
        //   - si la tiene, se avisa una vez, y **el segundo intento cierra igual**. El
        //     que insiste en cerrar sabe lo que quiere.
        var yaAvise = false;
        Closing += (_, e) =>
        {
            if (!GameProcess.IsRunning()) return;

            if (!GameProcess.HasWindow())
            {
                GameProcess.KillLeftovers();
                return;
            }

            if (yaAvise) return;

            yaAvise = true;
            e.Cancel = true;
            Activate();
            _shell.NotifyCannotClose();
        };
    }
}
