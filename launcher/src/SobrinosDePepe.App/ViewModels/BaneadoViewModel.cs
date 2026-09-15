using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// El final del camino: la cuenta está baneada, o lo está la IP desde la que se
/// abre el launcher. Desde acá no se llega a ninguna otra pantalla, que es todo el
/// punto: sin esto el baneado veía el JUGAR igual y se enteraba recién cuando el
/// servidor le rechazaba la conexión.
/// </summary>
public partial class BaneadoViewModel : ObservableObject
{
    private readonly ShellViewModel _shell;

    [ObservableProperty] private string _username;

    public BaneadoViewModel(ShellViewModel shell, Account account)
    {
        _shell = shell;
        _username = account.Username;
    }

    [RelayCommand]
    private void LogOut()
    {
        SessionStore.Clear();
        _shell.ShowLogin();
    }
}
