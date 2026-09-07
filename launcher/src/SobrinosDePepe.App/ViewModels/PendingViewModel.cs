using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// La cuenta existe pero todavía no está habilitada en el servidor. Desde que el
/// registro aprueba solo, la única forma de llegar acá es que el panel de Minehost
/// no haya contestado justo en ese momento.
///
/// Se consulta cada 20 segundos y la pantalla cambia sola. Volver a iniciar sesión
/// también reintenta la activación, así que cerrar y abrir el launcher alcanza.
/// </summary>
public partial class PendingViewModel : ObservableObject, IDisposable
{
    private readonly ShellViewModel _shell;
    private readonly string _token;
    private readonly CancellationTokenSource _polling = new();

    [ObservableProperty] private string _username;
    [ObservableProperty] private bool _isChecking;
    [ObservableProperty] private string? _error;

    public PendingViewModel(ShellViewModel shell, string token, Account account)
    {
        _shell = shell;
        _token = token;
        _username = account.Username;
        _ = PollAsync();
    }

    private async Task PollAsync()
    {
        try
        {
            while (!_polling.IsCancellationRequested)
            {
                await Task.Delay(TimeSpan.FromSeconds(20), _polling.Token);
                await CheckAsync();
            }
        }
        catch (OperationCanceledException)
        {
        }
    }

    [RelayCommand]
    private async Task CheckAsync()
    {
        if (IsChecking) return;
        IsChecking = true;
        Error = null;

        try
        {
            var account = await _shell.Api.AccountAsync(_token, _polling.Token);
            if (!account.IsPending)
            {
                _polling.Cancel();
                _shell.Route(_token, account);
            }
        }
        catch (ApiException ex)
        {
            Error = ex.Message;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException)
        {
            Error = "No me pude conectar.";
        }
        finally
        {
            IsChecking = false;
        }
    }

    [RelayCommand]
    private void LogOut()
    {
        _polling.Cancel();
        SessionStore.Clear();
        _shell.ShowLogin();
    }

    public void Dispose() => _polling.Dispose();
}
