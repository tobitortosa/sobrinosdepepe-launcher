using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// Decide qué pantalla se ve. Todo el launcher es una ventana que cambia de contenido.
/// </summary>
public partial class ShellViewModel : ObservableObject
{
    private readonly HttpClient _http = HashedDownloader.CreateHttpClient();

    [ObservableProperty]
    private ObservableObject? _current;

    public LauncherApi Api { get; }
    public HttpClient Http => _http;

    /// <summary>Se dispara cuando hay que traer la ventana al frente.</summary>
    public event Action? BringToFront;

    public ShellViewModel()
    {
        Api = new LauncherApi(AppConfig.ApiUrl, _http);
        LauncherPaths.EnsureCreated();
        Current = new StartupViewModel();
    }

    /// <summary>
    /// Lo primero que pasa al abrir: se busca una versión nueva del launcher y, si la
    /// hay, se instala y la aplicación se reinicia. Después se entra con la sesión
    /// guardada, o se pide iniciar sesión.
    /// </summary>
    public async Task StartAsync()
    {
        var startup = Current as StartupViewModel ?? new StartupViewModel();
        Current = startup;

        await Updater.RunAsync(new Progress<UpdateProgress>(startup.Report));

        startup.EnteringSession();
        await ResumeSessionAsync();

        // Si no había sesión guardada, se pide iniciar sesión.
        if (Current == startup) ShowLogin();

        // Desde acá, si aparece una versión nueva se corta lo que se esté haciendo.
        // El aviso llega desde un hilo de fondo y la pantalla solo se puede cambiar
        // desde el hilo de la interfaz.
        UpdateWatcher.Start(
            Api,
            version => Dispatcher.UIThread.Post(() =>
            {
                Current = new UpdateRequiredViewModel(this, version);
                ComeToFront();
            }),
            version => Dispatcher.UIThread.Post(() =>
            {
                // Los mods nuevos los instala la pantalla principal, que ya sabe
                // sincronizar el pack y volver a abrir el juego.
                if (Current is HomeViewModel home) _ = home.PackChangedAsync(version);
            }));
    }

    /// <summary>Vuelve al principio, después de una actualización que no hizo falta aplicar.</summary>
    public void RestartFlow() => _ = StartAsync();

    /// <summary>Trae la ventana adelante, para que un aviso obligatorio no pase de largo.</summary>
    public void ComeToFront() => BringToFront?.Invoke();

    /// <summary>Le dice a la pantalla actual por qué no se cerró el launcher.</summary>
    public void NotifyCannotClose()
    {
        if (Current is HomeViewModel home)
            home.Message = "El launcher se queda abierto mientras juegues: así te avisa si hay "
                + "una actualización. Si lo querés cerrar igual, cerralo de nuevo.";
    }

    public void ShowLogin(string? message = null) =>
        Current = new LoginViewModel(this) { Notice = message };

    public void ShowPending(string token, Account account) =>
        Current = new PendingViewModel(this, token, account);

    public void ShowBaneado(Account account) =>
        Current = new BaneadoViewModel(this, account);

    public void ShowHome(string token, Account account) =>
        Current = new HomeViewModel(this, token, account);

    public void ShowAdmin(string token, Account account) =>
        Current = new AdminViewModel(this, token, account);

    /// <summary>Al arrancar, si hay una sesión guardada se entra sin preguntar nada.</summary>
    public async Task ResumeSessionAsync()
    {
        var token = SessionStore.Load();
        if (token is null) return;

        try
        {
            var account = await Api.AccountAsync(token);
            Route(token, account);
        }
        catch (ApiException)
        {
            // La sesión venció o la cuenta cambió: se pide login de nuevo.
            SessionStore.Clear();
        }
        catch (HttpRequestException)
        {
            // Sin conexión no se puede validar la sesión: se muestra el login.
            SessionStore.Clear();
        }
    }

    public void Route(string token, Account account)
    {
        // Elegir la contraseña propia va antes que todo lo demás.
        if (account.MustChangePassword) Current = new ChangePasswordViewModel(this, token, account);
        // El baneo va antes que la pantalla de espera: si no, un baneado veía el
        // JUGAR y se enteraba recien cuando el servidor le rechazaba la conexion.
        else if (account.IsBanned) ShowBaneado(account);
        else if (account.IsPending) ShowPending(token, account);
        else ShowHome(token, account);
    }
}
