using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

public partial class LoginViewModel : ObservableObject
{
    private readonly ShellViewModel _shell;

    [ObservableProperty] private string _username = "";
    [ObservableProperty] private string _password = "";
    [ObservableProperty] private string _confirmPassword = "";
    [ObservableProperty] private bool _isRegistering;
    [ObservableProperty] private string? _error;
    [ObservableProperty] private string? _notice;
    [ObservableProperty] private bool _isBusy;

    /// <summary>
    /// Uno solo para los dos campos: apretar el ojo muestra la contraseña y la
    /// repetición juntas. Con uno por campo hay que apretar dos veces para
    /// comparar, que es justo para lo que se usa.
    /// </summary>
    [ObservableProperty] private bool _mostrarContrasena;

    public LoginViewModel(ShellViewModel shell) => _shell = shell;

    public string Title => IsRegistering ? "Crear una cuenta" : "Iniciar sesión";
    public string ActionText => IsRegistering ? "CREAR CUENTA" : "INICIAR SESIÓN";
    public string SwitchText => IsRegistering ? "Ya tengo una cuenta" : "Crear una cuenta";

    partial void OnIsRegisteringChanged(bool value)
    {
        Error = null;
        Notice = null;
        OnPropertyChanged(nameof(Title));
        OnPropertyChanged(nameof(ActionText));
        OnPropertyChanged(nameof(SwitchText));
    }

    [RelayCommand]
    private void Switch() => IsRegistering = !IsRegistering;

    [RelayCommand]
    private async Task SubmitAsync()
    {
        Error = null;
        Notice = null;

        var user = Username.Trim();

        if (user.Length == 0 || Password.Length == 0)
        {
            Error = "Completá usuario y contraseña.";
            return;
        }

        if (IsRegistering && Password != ConfirmPassword)
        {
            Error = "Las contraseñas no coinciden.";
            return;
        }

        IsBusy = true;
        try
        {
            var session = IsRegistering
                ? await _shell.Api.RegisterAsync(user, Password)
                : await _shell.Api.LoginAsync(user, Password);

            SessionStore.Save(session.Token);
            _shell.Route(session.Token, session.Account);
        }
        catch (ApiException ex)
        {
            Error = ex.Message;
        }
        catch (HttpRequestException)
        {
            Error = "No me pude conectar. Revisá tu internet y volvé a intentar.";
        }
        catch (TaskCanceledException)
        {
            Error = "La conexión tardó demasiado. Volvé a intentar.";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
