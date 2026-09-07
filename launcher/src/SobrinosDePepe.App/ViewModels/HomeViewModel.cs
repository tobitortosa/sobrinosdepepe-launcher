using System.Diagnostics;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// La pantalla con el botón JUGAR. Al apretarlo pide el pack, aplica lo que cambió
/// y abre el juego. Es todo lo que un jugador necesita hacer.
/// </summary>
public partial class HomeViewModel : ObservableObject
{
    private readonly ShellViewModel _shell;
    private readonly string _token;

    [ObservableProperty] private string _username;
    [ObservableProperty] private bool _isAdmin;

    [ObservableProperty] private string _serverLabel = "Consultando el servidor…";
    [ObservableProperty] private bool _serverOnline;
    [ObservableProperty] private bool _serverChecked;

    [ObservableProperty] private bool _isWorking;
    [ObservableProperty] private string _stage = "";
    [ObservableProperty] private string _detail = "";
    [ObservableProperty] private double _percent;
    [ObservableProperty] private bool _hasProgress;

    [ObservableProperty] private string? _error;
    [ObservableProperty] private string? _errorDetail;
    [ObservableProperty] private string? _message;

    /// <summary>Si el juego ya está abierto, JUGAR vuelve a esa ventana en vez de abrir otra.</summary>
    [ObservableProperty] private bool _isGameRunning;

    /// <summary>
    /// El botón recién ofrece volver al juego cuando la ventana está en pantalla. Antes
    /// de eso el juego todavía se está abriendo, y ofrecer "volver" no tendría sentido.
    /// </summary>
    public bool ShowReturnToGame => IsGameRunning && !IsWorking;

    partial void OnIsGameRunningChanged(bool value) => OnPropertyChanged(nameof(ShowReturnToGame));

    partial void OnIsWorkingChanged(bool value) => OnPropertyChanged(nameof(ShowReturnToGame));

    public HomeViewModel(ShellViewModel shell, string token, Account account)
    {
        _shell = shell;
        _token = token;
        _username = account.Username;
        _isAdmin = account.IsAdmin;

        var installed = PackInstaller.InstalledVersion();
        Message = installed is null
            ? "Primera vez: la instalación tarda unos minutos."
            : $"Pack de mods {installed} instalado.";

        // Puede haber quedado el juego abierto de antes, incluso de otra vez que se
        // abrió el launcher.
        IsGameRunning = GameProcess.IsRunning();

        _ = CheckServerAsync();
        _ = WatchGameAsync();
    }

    private async Task CheckServerAsync()
    {
        var info = await Core.ServerStatus.QueryAsync(AppConfig.ServerAddress);
        ServerOnline = info.Online;
        ServerChecked = true;
        ServerLabel = info.Online
            ? $"Servidor online · {info.Players}/{info.MaxPlayers} jugando"
            : "Servidor apagado";
    }

    /// <summary>Mira cada tanto si el juego sigue abierto, para que el botón diga la verdad.</summary>
    private async Task WatchGameAsync()
    {
        while (true)
        {
            await Task.Delay(TimeSpan.FromSeconds(3));
            var running = GameProcess.IsRunning();
            if (running != IsGameRunning) IsGameRunning = running;
        }
    }

    /// <summary>
    /// El administrador publicó mods nuevos. Los del jugador tienen que ser los mismos
    /// que los del servidor, así que si está adentro hay que sacarlo: se avisa, se
    /// cierra el juego, se instalan los mods y se vuelve a entrar solo.
    /// </summary>
    public async Task PackChangedAsync(string version)
    {
        if (IsWorking) return;

        if (!IsGameRunning)
        {
            // Nada que interrumpir: el pack se instala solo cuando aprete JUGAR.
            Message = $"Hay mods nuevos (pack {version}). Apretá JUGAR para instalarlos.";
            return;
        }

        for (var quedan = 10; quedan > 0; quedan--)
        {
            Message = $"Mods nuevos: el juego se cierra en {quedan}… y vuelve a abrirse solo.";
            if (quedan % 3 == 0) _shell.ComeToFront();
            await Task.Delay(TimeSpan.FromSeconds(1));
        }

        Message = "Cerrando el juego para instalar los mods…";
        await GameProcess.CloseAsync();
        IsGameRunning = false;

        await PlayCommand.ExecuteAsync(null);
    }

    [RelayCommand]
    private async Task PlayAsync()
    {
        if (IsWorking) return;

        // Si ya está abierto, se vuelve a esa ventana: dos Minecraft escribiendo en la
        // misma carpeta se pisan entre ellos.
        if (GameProcess.IsRunning())
        {
            IsGameRunning = true;
            Message = GameProcess.BringToFront()
                ? "El juego ya estaba abierto."
                : "El juego ya está abierto. Buscalo en la barra de tareas.";
            return;
        }

        IsWorking = true;
        Error = null;
        ErrorDetail = null;
        Message = null;
        HasProgress = false;
        Stage = "Buscando actualizaciones…";
        Detail = "";

        try
        {
            var pack = await _shell.Api.PackAsync(_token);

            var progress = new Progress<SetupProgress>(p =>
            {
                Stage = p.Stage;
                HasProgress = p.Total > 0;
                Percent = p.Total > 0 ? p.Current * 100.0 / p.Total : 0;
                Detail = p.BytesTotal > 0
                    ? $"{p.Current} de {p.Total} archivos · {p.BytesDone / 1024 / 1024} de {p.BytesTotal / 1024 / 1024} MB"
                    : $"{p.Current} de {p.Total} archivos";
            });

            var detail = new Progress<string>(line => Detail = line);

            var installer = new PackInstaller(
                new GameSetup(LauncherPaths.GameDir, _shell.Http),
                new ModSynchronizer(
                    new HashedDownloader(_shell.Http, backend: (AppConfig.ApiUrl, _token)),
                    LauncherPaths.OwnModsFile),
                OverridesDirectory());

            var (version, report) = await installer.ApplyAsync(pack, progress, detail);

            Stage = "Abriendo el juego…";
            Detail = "La primera vez tarda un poco más.";
            HasProgress = false;

            var launcher = new GameSetup(LauncherPaths.GameDir, _shell.Http).CreateLauncherWithoutJavaExtractor();
            var runner = new GameRunner(launcher, Path.Combine(LauncherPaths.Root, "game-output.log"));
            var process = runner.BuildProcess(version, Username, pack.Server.Address);

            var started = new TaskCompletionSource();

            _ = Task.Run(async () =>
            {
                try
                {
                    var run = await runner.RunAsync(process, started: started);

                    // El juego corre en su propio hilo; la pantalla se toca en el de la interfaz.
                    Dispatcher.UIThread.Post(() =>
                    {
                        IsGameRunning = false;
                        if (run.ExitCode != 0)
                        {
                            Error = "El juego se cerró con un error.";
                            ErrorDetail = ReadTail(run.LogPath);
                        }
                    });
                }
                catch (Exception ex)
                {
                    started.TrySetException(ex);
                }
            });

            // Se espera a que el juego muestre su ventana. Hasta entonces el cargador
            // sigue girando: es lo único que le dice al jugador que algo está pasando,
            // y con Java pasan varios segundos entre arrancar y ver algo en pantalla.
            await started.Task;
            var appeared = await GameProcess.WaitForWindowAsync(process);

            IsGameRunning = appeared;
            Stage = "";
            Detail = "";

            if (appeared)
            {
                Message = report.WasUpToDate
                    ? "Listo, el juego está abierto."
                    : $"Actualizado: {report.ModsDownloaded} mods nuevos, {report.ModsRemoved} quitados. El juego está abierto.";
            }
            else
            {
                Message = null;
                Error = "El juego se cerró antes de abrir.";
                ErrorDetail = ReadTail(Path.Combine(LauncherPaths.Root, "game-output.log"));
            }
        }
        catch (ApiException ex)
        {
            Error = ex.Message;
        }
        catch (Exception ex)
        {
            Error = ex.Message;
            ErrorDetail = ex.ToString();
        }
        finally
        {
            IsWorking = false;
        }
    }

    [RelayCommand]
    private void CopyError()
    {
        var text = $"SOBRINOS DE PEPE launcher {AppVersion.Label}\nUsuario: {Username}\nPack de mods: {PackInstaller.InstalledVersion()}\n\n{Error}\n\n{ErrorDetail}";
        Clipboard.Set(text);
        Message = "Copiado. Pegalo en el chat así lo miramos.";
    }

    /// <summary>Abre un enlace en el navegador. Lo usan los botones de las redes.</summary>
    [RelayCommand]
    private static void OpenLink(string url) =>
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });

    /// <summary>Cierra el juego, por si quedó trabado o quieren volver a entrar limpio.</summary>
    [RelayCommand]
    private async Task CloseGameAsync()
    {
        Message = "Cerrando el juego…";
        await GameProcess.CloseAsync();
        IsGameRunning = GameProcess.IsRunning();
        Message = IsGameRunning ? "No pude cerrarlo. Cerralo desde su ventana." : "El juego se cerró.";
    }

    [RelayCommand]
    private void OpenFolder() =>
        Process.Start(new ProcessStartInfo("explorer.exe", LauncherPaths.Root) { UseShellExecute = true });

    [RelayCommand]
    private async Task RepairAsync()
    {
        // Reparar es lo mismo que jugar, pero borrando los mods para que se bajen de nuevo.
        try
        {
            if (Directory.Exists(LauncherPaths.ModsDir))
                foreach (var file in Directory.EnumerateFiles(LauncherPaths.ModsDir, "*.jar"))
                    File.Delete(file);
        }
        catch (IOException ex)
        {
            Error = $"No pude limpiar la carpeta de mods: {ex.Message}";
            return;
        }

        await PlayAsync();
    }

    [RelayCommand]
    private void OpenAdmin() => _shell.ShowAdmin(_token, new Account(Username, "active", "admin"));

    [RelayCommand]
    private async Task LogOutAsync()
    {
        try { await _shell.Api.LogoutAsync(_token); } catch (Exception) { /* la sesión local se borra igual */ }
        SessionStore.Clear();
        _shell.ShowLogin("Cerraste sesión.");
    }

    private static string? OverridesDirectory()
    {
        var candidate = Path.Combine(AppContext.BaseDirectory, "overrides");
        return Directory.Exists(candidate) ? candidate : null;
    }

    private static string ReadTail(string path, int lines = 60)
    {
        try
        {
            var all = File.ReadAllLines(path);
            return string.Join('\n', all.Skip(Math.Max(0, all.Length - lines)));
        }
        catch (IOException)
        {
            return "(no pude leer el log)";
        }
    }
}
