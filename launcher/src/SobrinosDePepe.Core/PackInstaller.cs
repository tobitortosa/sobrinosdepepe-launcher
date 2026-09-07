using System.Text.Json;
using CmlLib.Core.Version;

namespace SobrinosDePepe.Core;

public sealed record InstallReport(
    string PackVersion,
    bool WasUpToDate,
    int ModsDownloaded,
    int ModsRemoved,
    int ConfigsWritten,
    TimeSpan Elapsed);

/// <summary>
/// Deja la instalación exactamente igual a lo que dice el pack publicado.
/// Se corre cada vez que el jugador aprieta JUGAR, así una publicación nueva del admin
/// llega sola: si se agregó un mod se descarga, y si se quitó se borra de la carpeta.
///
/// Se verifica todo en cada arranque a propósito. Es rápido cuando no cambió nada, y
/// hace que una instalación a medias se arregle sola en vez de terminar en un crash
/// que nadie puede diagnosticar.
/// </summary>
public sealed class PackInstaller
{
    private readonly GameSetup _setup;
    private readonly ModSynchronizer _mods;
    private readonly string? _overridesDir;

    public PackInstaller(GameSetup setup, ModSynchronizer mods, string? overridesDir)
    {
        _setup = setup;
        _mods = mods;
        _overridesDir = overridesDir;
    }

    public async Task<(IVersion Version, InstallReport Report)> ApplyAsync(
        Pack pack,
        IProgress<SetupProgress>? progress = null,
        IProgress<string>? detail = null,
        CancellationToken ct = default)
    {
        var started = DateTime.UtcNow;
        var previous = InstalledVersion();

        await _setup.InstallVanillaAsync(pack.Minecraft, progress, ct);
        var version = await _setup.InstallFabricAsync(pack.Minecraft, pack.FabricLoader, progress, ct);

        var sync = await _mods.SyncAsync(pack, LauncherPaths.GameDir, detail, ct);

        var configs = 0;
        if (_overridesDir is not null)
        {
            var seeded = ConfigSeeder.Seed(_overridesDir, LauncherPaths.GameDir, detail);
            configs = seeded.Written;
        }

        // El servidor, cargado en el menú multijugador. Al apretar JUGAR se entra
        // directo, pero si alguien se desconecta y va al menú lo tiene que encontrar ahí.
        //
        // Va con AcceptTextures en true: el servidor manda un resource pack chiquito con
        // los carteles en castellano, y sin esto Minecraft pregunta si lo quiere bajar la
        // primera vez que entra. Es un cartel que nadie sabe qué contestar.
        var added = ServerList.Ensure(
            Path.Combine(LauncherPaths.GameDir, "servers.dat"),
            new SavedServer(pack.Server.Name, pack.Server.Address, AcceptTextures: true));
        if (added) detail?.Report($"servidor {pack.Server.Name} listo en el menú multijugador");

        MarkInstalled(pack.PackVersion);

        var report = new InstallReport(
            pack.PackVersion,
            previous == pack.PackVersion && sync.Downloaded == 0 && sync.Removed == 0 && configs == 0,
            sync.Downloaded,
            sync.Removed,
            configs,
            DateTime.UtcNow - started);

        return (version, report);
    }

    private static string MarkerPath => Path.Combine(LauncherPaths.PackDir, "installed.json");

    public static string? InstalledVersion()
    {
        try
        {
            if (!File.Exists(MarkerPath)) return null;
            using var document = JsonDocument.Parse(File.ReadAllText(MarkerPath));
            return document.RootElement.TryGetProperty("packVersion", out var value) ? value.GetString() : null;
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
            return null;
        }
    }

    private static void MarkInstalled(string packVersion)
    {
        Directory.CreateDirectory(LauncherPaths.PackDir);
        var json = JsonSerializer.Serialize(new { packVersion, installedAt = DateTime.UtcNow });
        File.WriteAllText(MarkerPath, json);
    }
}
