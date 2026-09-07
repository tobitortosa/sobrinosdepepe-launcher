using Avalonia.Controls;
using SobrinosDePepe.App.ViewModels;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.Views;

public partial class UninstallWindow : Window
{
    private readonly UninstallViewModel _model = new();

    public UninstallWindow()
    {
        InitializeComponent();
        DataContext = _model;

        _model.Close += Close;

        Closing += (_, e) =>
        {
            // A mitad del borrado no se cierra: quedaría media instalación dando vueltas,
            // que es justo lo que un desinstalador no tiene que hacer.
            if (_model.IsWorking)
            {
                e.Cancel = true;
                return;
            }

            // Si ya se borraron los datos y cierran con la X en vez del botón, se hace el
            // último paso igual. Terminar la desinstalación no puede depender de por dónde
            // se fueron.
            if (_model.IsDone) Uninstall.FinishAndExit();
        };
    }
}
