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
/// **Sin excepciones, y eso es a propósito.** Hasta el 2026-09-11 existía un
/// mods-propios.txt en la raíz: los jars nombrados ahí sobrevivían a la sincronización,
/// para el que quería un mod de cliente propio. Era también la única forma de dejar un
/// xray o un Meteor adentro de la carpeta y que el launcher no lo borrara, así que se
/// quitó. El mod que quiera estar en las PC de todos entra por el pack, que es donde el
/// admin lo mira antes: `npm run pack:mod`.
/// </summary>
public sealed class ModSynchronizer
{
    private readonly HashedDownloader _downloader;

    public ModSynchronizer(HashedDownloader downloader)
    {
        _downloader = downloader;
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

                File.Delete(file);
                removed++;
                progress?.Report($"borrado  {name} (no está en el pack)");
            }
        }

        return new ModSyncResult(downloaded, alreadyOk, removed);
    }
}
