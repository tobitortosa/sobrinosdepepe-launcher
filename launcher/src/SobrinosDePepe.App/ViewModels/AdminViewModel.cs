using System.Collections.ObjectModel;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Platform.Storage;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App.ViewModels;

/// <summary>
/// El panel de administración, dentro del mismo launcher. Dos cosas: las cuentas y
/// los mods. Ninguna acción se hace acá: todas se le piden al backend, que verifica
/// el rol y es el único que puede hablarle al servidor de Minecraft.
/// </summary>
public partial class AdminViewModel : ObservableObject
{
    private readonly ShellViewModel _shell;
    private readonly string _token;

    [ObservableProperty] private string _username;
    [ObservableProperty] private int _tab;          // 0 = usuarios, 1 = mods, 2 = servidor, 3 = mis mods
    [ObservableProperty] private bool _isBusy;
    [ObservableProperty] private string? _error;
    [ObservableProperty] private string? _notice;

    // Confirmación de las acciones que no se pueden deshacer.
    [ObservableProperty] private string? _confirmText;
    private Func<Task>? _pendingAction;

    // Usuarios
    public ObservableCollection<UserRow> Users { get; } = [];
    [ObservableProperty] private string _search = "";
    [ObservableProperty] private int _pendingCount;
    [ObservableProperty] private string _onlineSummary = "";

    // Mods
    public ObservableCollection<AdminMod> Mods { get; } = [];
    [ObservableProperty] private string _packVersion = "—";
    [ObservableProperty] private string _packInfo = "";
    [ObservableProperty] private bool _hasUnpublishedChanges;
    [ObservableProperty] private string? _serverSideNote;

    // Mis mods: los que tiene esta cuenta ademas del pack
    public ObservableCollection<MyMod> MyMods { get; } = [];
    [ObservableProperty] private string _myModsSummary = "";

    // El servidor
    public ObservableCollection<ServerMod> ServerMissing { get; } = [];
    public ObservableCollection<string> ServerExtra { get; } = [];
    [ObservableProperty] private string _serverModsSummary = "";
    [ObservableProperty] private bool _serverNeedsUpload;
    [ObservableProperty] private bool _serverHasExtra;
    [ObservableProperty] private string _serverState = "consultando…";
    [ObservableProperty] private bool _serverOnline;

    public AdminViewModel(ShellViewModel shell, string token, Account account)
    {
        _shell = shell;
        _token = token;
        _username = account.Username;
        _ = LoadUsersAsync();
        _ = LoadModsAsync();
        _ = LoadServerModsAsync();
        _ = LoadMyModsAsync();
    }

    public bool ShowingUsers => Tab == 0;
    public bool ShowingMods => Tab == 1;
    public bool ShowingServer => Tab == 2;
    public bool ShowingMyMods => Tab == 3;

    partial void OnTabChanged(int value)
    {
        OnPropertyChanged(nameof(ShowingUsers));
        OnPropertyChanged(nameof(ShowingMods));
        OnPropertyChanged(nameof(ShowingServer));
        OnPropertyChanged(nameof(ShowingMyMods));
    }

    [RelayCommand]
    private void ShowUsers() => Tab = 0;

    [RelayCommand]
    private void ShowMods() => Tab = 1;

    [RelayCommand]
    private void ShowServer() => Tab = 2;

    [RelayCommand]
    private void ShowMyMods() => Tab = 3;

    [RelayCommand]
    private void Back() => _shell.ShowHome(_token, new Account(Username, "active", "admin"));

    // ----------------------------------------------------------------- Usuarios

    [RelayCommand]
    private async Task LoadUsersAsync()
    {
        await RunAsync(async () =>
        {
            var users = await _shell.Api.UsersAsync(_token, Search);

            // Quiénes están jugando se le pregunta al servidor directamente: contesta
            // la lista completa de nombres.
            var online = await ServerQuery.PlayersAsync(AppConfig.ServerAddress);
            var jugando = online.Names.ToHashSet(StringComparer.OrdinalIgnoreCase);

            Users.Clear();
            foreach (var user in users)
                Users.Add(new UserRow(user, Username, jugando.Contains(user.Username)));

            PendingCount = users.Count(u => u.IsPending);
            OnlineSummary = !online.Answered
                ? "No pude preguntarle al servidor quién está jugando."
                : jugando.Count == 0
                    ? "Nadie está jugando en este momento."
                    : jugando.Count == 1
                        ? "1 jugando ahora"
                        : $"{jugando.Count} jugando ahora";
        });
    }

    [RelayCommand]
    private async Task ApproveAsync(UserRow user)
    {
        await RunAsync(async () =>
        {
            await _shell.Api.ApproveAsync(_token, user.Id);
            Notice = $"{user.Username} aprobado y agregado a la whitelist.";
            await LoadUsersAsync();
        });
    }

    [RelayCommand]
    private void Ban(UserRow user) =>
        Ask($"¿Banear a {user.Username}? Lo sacamos de la whitelist y lo echamos del servidor.",
            async () =>
            {
                await _shell.Api.BanAsync(_token, user.Id, "Baneado");
                Notice = $"{user.Username} baneado, sacado de la whitelist y echado del servidor.";
                await LoadUsersAsync();
            });

    [RelayCommand]
    private async Task UnbanAsync(UserRow user)
    {
        await RunAsync(async () =>
        {
            await _shell.Api.UnbanAsync(_token, user.Id);
            Notice = $"{user.Username} vuelve a estar habilitado.";
            await LoadUsersAsync();
        });
    }

    [RelayCommand]
    private void ResetPassword(UserRow user) =>
        Ask($"¿Restablecer la contraseña de {user.Username}? Va a entrar con la provisoria y ahí elige la suya.",
            async () =>
            {
                var result = await _shell.Api.ResetPasswordAsync(_token, user.Id);
                Clipboard.Set(result.Password);
                Notice = $"{result.Username} entra con: {result.Password}  (copiada, dictásela). " +
                         "Al entrar le va a pedir que elija una propia.";
            });

    // --------------------------------------------------------------------- Mods

    [RelayCommand]
    private async Task LoadModsAsync()
    {
        await RunAsync(async () =>
        {
            var draft = await _shell.Api.PackDraftAsync(_token);
            Mods.Clear();
            foreach (var mod in draft.Mods) Mods.Add(mod);

            PackVersion = draft.PublishedVersion ?? "sin publicar";
            PackInfo = $"Minecraft {draft.Minecraft} · Fabric {draft.FabricLoader} · {draft.Mods.Count} mods";
            HasUnpublishedChanges = draft.HasUnpublishedChanges;
            ServerSideNote = draft.ServerSide.Count > 0
                ? $"{draft.ServerSide.Count} de estos van también en el servidor"
                : null;
        });
    }

    [RelayCommand]
    private async Task AddModsAsync()
    {
        var paths = await ElegirJarsAsync("Elegí los mods del pack (.jar)");
        if (paths.Count == 0) return;

        await RunAsync(async () =>
        {
            var result = await _shell.Api.UploadModsAsync(_token, paths);

            var lines = new List<string>();
            if (result.Added.Count > 0)
                lines.Add($"{result.Added.Count} agregados: " +
                          string.Join(", ", result.Added.Select(a => $"{a.Title} {a.Version}")));
            foreach (var bad in result.Rejected)
                lines.Add($"{bad.Filename}: {bad.Reason}");
            foreach (var note in result.Added.Where(a => a.Note is not null))
                lines.Add(note.Note!);

            Notice = string.Join("\n", lines);
            await LoadModsAsync();
        });
    }

    [RelayCommand]
    private void RemoveMod(AdminMod mod) =>
        Ask($"¿Quitar {mod.Title} del pack? Se les borra a todos cuando publiques.",
            async () =>
            {
                await _shell.Api.RemoveModAsync(_token, mod.ProjectId);
                Notice = $"{mod.Title} quitado del pack. Publicá para que les llegue a todos.";
                await LoadModsAsync();
            });

    // ----------------------------------------------------------------- Mis mods

    /// <summary>
    /// Los mods que esta cuenta tiene ademas del pack. No se publican: son solo
    /// para las maquinas donde entre esta cuenta, y viajan con ella.
    ///
    /// El backend solo deja subir a las cuentas admin. Esa es toda la seguridad de
    /// esto y por eso vive alla: si la lista la escribiera el launcher en la PC,
    /// cualquiera podria agregarle un xray y sobreviviria a la sincronizacion, que
    /// es exactamente lo que pasaba con el viejo mods-propios.txt.
    /// </summary>
    [RelayCommand]
    private async Task LoadMyModsAsync()
    {
        await RunAsync(async () =>
        {
            var mios = await _shell.Api.MyModsAsync(_token);
            MyMods.Clear();
            foreach (var mod in mios) MyMods.Add(mod);

            MyModsSummary = mios.Count == 0
                ? "No tenés ninguno. Los que agregues acá se instalan solos en cualquier computadora donde entres con tu cuenta."
                : $"{mios.Count} mod(s), solo tuyos. Se instalan en cualquier computadora donde entres con tu cuenta.";
        });
    }

    [RelayCommand]
    private async Task AddMyModsAsync()
    {
        var paths = await ElegirJarsAsync("Elegí tus mods (.jar)");
        if (paths.Count == 0) return;

        await RunAsync(async () =>
        {
            var result = await _shell.Api.UploadMyModsAsync(_token, paths);

            var lines = new List<string>();
            if (result.Added.Count > 0)
                lines.Add($"{result.Added.Count} agregados: " +
                          string.Join(", ", result.Added.Select(a => $"{a.Title} {a.Version}")));
            foreach (var bad in result.Rejected)
                lines.Add($"{bad.Filename}: {bad.Reason}");
            lines.Add("Apretá JUGAR para que se instalen.");

            Notice = string.Join("\n", lines);
            await LoadMyModsAsync();
        });
    }

    [RelayCommand]
    private void RemoveMyMod(MyMod mod) =>
        Ask($"¿Sacar {mod.Title} de tus mods? La próxima vez que juegues se borra de la carpeta.",
            async () =>
            {
                await _shell.Api.RemoveMyModAsync(_token, mod.Sha1);
                Notice = $"{mod.Title} sacado de tus mods.";
                await LoadMyModsAsync();
            });

    /// <summary>El explorador de archivos, que las dos pantallas de mods abren igual.</summary>
    private static async Task<List<string>> ElegirJarsAsync(string titulo)
    {
        var window = (Avalonia.Application.Current?.ApplicationLifetime as IClassicDesktopStyleApplicationLifetime)?.MainWindow;
        if (window is null) return [];

        var files = await window.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = titulo,
            AllowMultiple = true,
            FileTypeFilter = [new FilePickerFileType("Mods de Minecraft") { Patterns = ["*.jar"] }],
        });

        return files
            .Select(f => f.TryGetLocalPath())
            .Where(p => !string.IsNullOrEmpty(p))
            .Select(p => p!)
            .ToList();
    }

    // ----------------------------------------------------------------- Servidor

    /// <summary>
    /// Compara la carpeta de mods del servidor con el pack. Los mods marcados como
    /// de cliente no van al servidor: Fabric los ignora igual, y ensucian el log.
    /// </summary>
    [RelayCommand]
    private async Task LoadServerModsAsync()
    {
        await RunAsync(async () =>
        {
            var state = await _shell.Api.ServerModsAsync(_token);

            ServerMissing.Clear();
            foreach (var mod in state.Missing) ServerMissing.Add(mod);

            ServerExtra.Clear();
            foreach (var name in state.Extra) ServerExtra.Add(name);

            // El estado real del servidor, para no tener que adivinarlo.
            var power = await _shell.Api.ServerStateAsync(_token);
            ServerState = power.Label;
            ServerOnline = power.Online;

            ServerNeedsUpload = state.Missing.Count > 0;
            ServerHasExtra = state.Extra.Count > 0;
            ServerModsSummary = state.Missing.Count == 0 && state.Extra.Count == 0
                ? $"El servidor tiene los {state.Ok.Count} mods que corresponden."
                : $"{state.Ok.Count} en orden · {state.Missing.Count} faltan · {state.Extra.Count} de más";
        });
    }

    [RelayCommand]
    private void UploadServerMods() =>
        Ask($"¿Subir al servidor los {ServerMissing.Count} mods que faltan?",
            async () =>
            {
                var result = await _shell.Api.UploadServerModsAsync(_token);
                Notice = result.Uploaded.Count > 0
                    ? $"Subidos al servidor: {string.Join(", ", result.Uploaded)}. {result.Note}"
                    : result.Note;
                await LoadServerModsAsync();
            });

    [RelayCommand]
    private void RemoveServerMod(string filename) =>
        Ask($"¿Borrar {filename} del servidor? Se borra el archivo, no se puede deshacer.",
            async () =>
            {
                var result = await _shell.Api.RemoveServerModsAsync(_token, [filename]);
                Notice = $"Borrado del servidor: {filename}. {result.Note}";
                await LoadServerModsAsync();
            });

    [RelayCommand]
    private void RestartServer() =>
        Ask("¿Reiniciar el servidor? Los que estén jugando se van a desconectar un momento.",
            async () =>
            {
                await _shell.Api.PowerAsync(_token, "restart");
                Notice = "El servidor está reiniciando. En un minuto vuelve.";
            });

    /// <summary>Deja una acción esperando el sí. Un clic de más no rompe nada.</summary>
    private void Ask(string question, Func<Task> action)
    {
        ConfirmText = question;
        _pendingAction = action;
    }

    [RelayCommand]
    private async Task ConfirmAsync()
    {
        var action = _pendingAction;
        ConfirmText = null;
        _pendingAction = null;
        if (action is not null) await RunAsync(action);
    }

    [RelayCommand]
    private void CancelConfirm()
    {
        ConfirmText = null;
        _pendingAction = null;
    }

    [RelayCommand]
    private async Task PublishAsync()
    {
        await RunAsync(async () =>
        {
            var result = await _shell.Api.PublishAsync(_token);
            Notice = $"Pack {result.Version} publicado con {result.Mods} mods. " +
                     "Cada jugador lo recibe al apretar JUGAR.";
            if (result.ServerSide.Count > 0)
                Notice += "\n\nEstos van también en el servidor, por SFTP a /mods:\n" +
                          string.Join("\n", result.ServerSide);
            await LoadModsAsync();
        });
    }

    // ------------------------------------------------------------------ Común

    /// <summary>
    /// Cuántas operaciones hay en curso. Se cuentan en vez de usar un candado porque
    /// al abrir el panel se cargan las tres listas a la vez, y un candado dejaba pasar
    /// solo a la primera.
    /// </summary>
    private int _running;

    private async Task RunAsync(Func<Task> action)
    {
        _running++;
        IsBusy = true;
        Error = null;
        // El aviso anterior no tiene por qué seguir en pantalla: cada acción trae el suyo.
        Notice = null;

        try
        {
            await action();
        }
        catch (ApiException ex)
        {
            Error = ex.Message;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException)
        {
            Error = "No me pude conectar con el backend.";
        }
        catch (Exception ex)
        {
            Error = ex.Message;
        }
        finally
        {
            _running--;
            IsBusy = _running > 0;
        }
    }
}
