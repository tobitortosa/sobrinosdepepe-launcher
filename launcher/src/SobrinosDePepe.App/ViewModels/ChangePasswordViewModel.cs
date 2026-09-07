using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// Aparece cuando alguien entró con la contraseña provisoria. No se puede saltear:
/// la idea es que la contraseña de cada uno la sepa solo esa persona, y no quien
/// se la restableció.
/// </summary>
public partial class ChangePasswordViewModel : ObservableObject
{
    private readonly ShellViewModel _shell;
    private readonly string _token;
    private readonly Account _account;

    [ObservableProperty] private string _password = "";
    [ObservableProperty] private string _confirm = "";
    [ObservableProperty] private string? _error;
    [ObservableProperty] private bool _isBusy;

    /// <summary>Uno solo para los dos campos, igual que en la pantalla de inicio.</summary>
    [ObservableProperty] private bool _mostrarContrasena;

    public ChangePasswordViewModel(ShellViewModel shell, string token, Account account)
    {
        _shell = shell;
        _token = token;
        _account = account;
    }

    public string Username => _account.Username;

    [RelayCommand]
    private async Task SaveAsync()
    {
        Error = null;

        if (Password.Length < 6)
        {
            Error = "La contraseña necesita al menos 6 caracteres.";
            return;
        }

        if (Password != Confirm)
        {
            Error = "Las dos contraseñas no coinciden.";
            return;
        }

        IsBusy = true;
        try
        {
            await _shell.Api.ChangePasswordAsync(_token, Password, Confirm);
            _shell.Route(_token, _account with { MustChangePassword = false });
        }
        catch (ApiException ex)
        {
            Error = ex.Message;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException)
        {
            Error = "No me pude conectar. Probá de nuevo.";
        }
        finally
        {
            IsBusy = false;
        }
    }

    [RelayCommand]
    private void LogOut()
    {
        SessionStore.Clear();
        _shell.ShowLogin();
    }
}
