namespace SobrinosDePepe.Core;

public sealed record ModSyncResult(int Downloaded, int AlreadyOk, int Removed);

/// <summary>
/// Deja las carpetas del juego exactamente iguales a lo que dice el pack: baja lo que
/// falta y borra lo que no está en la lista. Lo segundo importa: si al quitar un mod del
/// pack el jar sigue en la PC del jugador, el juego crashea al arrancar.
///
/// Administra mods/ y shaderpacks/. Los archivos que el jugador haya puesto a mano en
/// esas carpetas se borran: son carpetas del pack, no del jugador.
///
/// La única excepción es lo que esté nombrado en <see cref="LauncherPaths.OwnModsFile"/>,
/// que se deja como está. Es para el que quiere un mod de cliente propio —un zoom, algo
/// de accesibilidad— sin metérselo a todo el servidor. Hay que escribir el nombre del
/// archivo a mano, que es la forma de que nadie termine con un jar viejo sin darse cuenta.
/// </summary>
public sealed class ModSynchronizer
{
    private readonly HashedDownloader _downloader;
    private readonly string? _ownModsFile;

    public ModSynchronizer(HashedDownloader downloader, string? ownModsFile = null)
    {
        _downloader = downloader;
        _ownModsFile = ownModsFile;
    }

    /// <summary>
    /// Los nombres de archivo que el jugador pidió conservar. Una línea por mod; se
    /// ignoran las vacías y las que empiezan con #. Si el archivo no existe o no se
    /// puede leer, la lista queda vacía: ante la duda se sincroniza como siempre, que
    /// es el lado que no deja el juego sin arrancar.
    /// </summary>
    private HashSet<string> OwnMods()
    {
        var propios = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        if (_ownModsFile is null || !File.Exists(_ownModsFile)) return propios;

        try
        {
            foreach (var line in File.ReadAllLines(_ownModsFile))
            {
                var name = line.Trim();
                if (name.Length == 0 || name.StartsWith('#')) continue;
                propios.Add(Path.GetFileName(name));
            }
        }
        catch (Exception)
        {
        }

        return propios;
    }

    public async Task<ModSyncResult> SyncAsync(
        Pack pack,
        string gameDir,
        IProgress<string>? progress = null,
        CancellationToken ct = default)
    {
        var downloaded = 0;
        var alreadyOk = 0;
        var removed = 0;
        var propios = OwnMods();

        foreach (var group in pack.ByFolder)
        {
            var folder = Path.Combine(gameDir, group.Key);
            Directory.CreateDirectory(folder);

            var wanted = group.ToList();

            foreach (var file in wanted)
            {
                ct.ThrowIfCancellationRequested();
                var destination = Path.Combine(folder, file.Filename);
                var didDownload = await _downloader.EnsureFileAsync(
                    file.Url, destination, file.Sha1, file.Sha512, ct);

                if (didDownload)
                {
                    downloaded++;
                    progress?.Report($"bajado   {file.Slug} {file.VersionNumber}");
                }
                else
                {
                    alreadyOk++;
                    progress?.Report($"ya está  {file.Slug} {file.VersionNumber}");
                }
            }

            var expected = wanted.Select(m => m.Filename).ToHashSet(StringComparer.OrdinalIgnoreCase);
            var pattern = group.Key == "shaderpacks" ? "*.zip" : "*.jar";

            foreach (var file in Directory.EnumerateFiles(folder, pattern))
            {
                var name = Path.GetFileName(file);
                if (expected.Contains(name)) continue;

                if (propios.Contains(name))
                {
                    progress?.Report($"propio   {name} (no se toca)");
                    continue;
                }

                File.Delete(file);
                removed++;
                progress?.Report($"borrado  {name} (no está en el pack)");
            }
        }

        return new ModSyncResult(downloaded, alreadyOk, removed);
    }
}
