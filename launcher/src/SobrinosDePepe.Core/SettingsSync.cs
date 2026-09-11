using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;

namespace SobrinosDePepe.Core;

/// <summary>Lo que se guarda en la cuenta, ya empaquetado, y su hash.</summary>
public sealed record SettingsSnapshot(byte[] Zip, string Sha1)
{
    public int Files { get; init; }
}

/// <summary>De quién son los ajustes que están ahora en la carpeta del juego.</summary>
public sealed record SettingsMarker(string Username, string Sha1);

/// <summary>
/// La configuración del juego viaja con la cuenta, no con la computadora.
///
/// Se guarda <c>options.txt</c> —las teclas, la sensibilidad del mouse, el FOV, el
/// volumen, la distancia de renderizado— y la carpeta <c>config/</c> entera, que es
/// donde los mods dejan lo suyo: Sodium, los shaders de Iris, el sonido.
///
/// El launcher lo baja antes de abrir el juego y lo sube cuando el juego se cierra,
/// que es cuando Minecraft terminó de escribir sus archivos. Así el que se sienta en
/// otra máquina se encuentra sus propias teclas, y el que presta la suya las recupera
/// enteras cuando vuelve a entrar.
///
/// **Por qué el zip sale siempre igual.** Las entradas van ordenadas por nombre y con
/// la misma fecha, así que dos veces el mismo contenido dan el mismo sha1. De eso
/// depende no escribir en la base ni pisar archivos cuando en realidad no cambió nada.
/// </summary>
public static class SettingsSync
{
    /// <summary>Los archivos sueltos de la raíz del juego que sí son del jugador.</summary>
    private static readonly string[] LooseFiles = ["options.txt"];

    private const string ConfigFolder = "config";

    /// <summary>
    /// Lo que nunca viaja: es de la máquina o de la sesión, y llevarlo a otra PC
    /// rompe cosas. El fingerprint de Sodium es de la placa de video, y servers.dat
    /// lo escribe el propio launcher en cada instalación.
    /// </summary>
    private static readonly string[] NeverSync =
    [
        "sodium-fingerprint.json",
        "usercache.json",
        "username-cache.json",
        "servers.dat"
    ];

    /// <summary>Una fecha fija para que el mismo contenido dé siempre el mismo zip.</summary>
    private static readonly DateTimeOffset FixedDate = new(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);

    private static string MarkerPath => Path.Combine(LauncherPaths.Root, "settings.json");

    /// <summary>Empaqueta lo que hay ahora en la carpeta del juego.</summary>
    public static SettingsSnapshot Pack(string gameDir)
    {
        var files = new SortedDictionary<string, string>(StringComparer.Ordinal);

        foreach (var name in LooseFiles)
        {
            var path = Path.Combine(gameDir, name);
            if (File.Exists(path)) files[name] = path;
        }

        var configDir = Path.Combine(gameDir, ConfigFolder);
        if (Directory.Exists(configDir))
        {
            foreach (var path in Directory.EnumerateFiles(configDir, "*", SearchOption.AllDirectories))
            {
                if (NeverSync.Contains(Path.GetFileName(path), StringComparer.OrdinalIgnoreCase)) continue;
                var relative = Path.GetRelativePath(gameDir, path).Replace('\\', '/');
                files[relative] = path;
            }
        }

        using var memory = new MemoryStream();
        using (var zip = new ZipArchive(memory, ZipArchiveMode.Create, leaveOpen: true))
        {
            foreach (var (relative, path) in files)
            {
                var entry = zip.CreateEntry(relative, CompressionLevel.Optimal);
                entry.LastWriteTime = FixedDate;
                using var source = File.OpenRead(path);
                using var target = entry.Open();
                source.CopyTo(target);
            }
        }

        var bytes = memory.ToArray();
        return new SettingsSnapshot(bytes, Hash(bytes)) { Files = files.Count };
    }

    /// <summary>
    /// Deja la carpeta del juego igual a lo que dice el zip: escribe lo que trae y
    /// borra de <c>config/</c> lo que no está.
    ///
    /// Borrar es a propósito. Si no, el que entra con su cuenta en una máquina
    /// prestada se queda con los ajustes del dueño mezclados con los suyos, y
    /// "mi configuración" deja de querer decir nada. Lo que se borra son configs de
    /// mods: si falta alguna, el mod la vuelve a escribir con sus valores por defecto.
    /// </summary>
    public static int Apply(byte[] zipBytes, string gameDir)
    {
        using var memory = new MemoryStream(zipBytes);
        using var zip = new ZipArchive(memory, ZipArchiveMode.Read);

        var written = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var entry in zip.Entries)
        {
            if (string.IsNullOrEmpty(entry.Name)) continue;

            var relative = entry.FullName.Replace('/', Path.DirectorySeparatorChar);
            var destination = Path.GetFullPath(Path.Combine(gameDir, relative));

            // Un zip puede traer rutas con ".." y escribir donde no tiene que escribir.
            // El zip lo hicimos nosotros, pero viaja por la red y vuelve.
            if (!destination.StartsWith(Path.GetFullPath(gameDir), StringComparison.OrdinalIgnoreCase)) continue;
            if (NeverSync.Contains(Path.GetFileName(destination), StringComparer.OrdinalIgnoreCase)) continue;

            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            entry.ExtractToFile(destination, overwrite: true);
            written.Add(destination);
        }

        var configDir = Path.Combine(gameDir, ConfigFolder);
        if (Directory.Exists(configDir))
        {
            foreach (var path in Directory.EnumerateFiles(configDir, "*", SearchOption.AllDirectories))
            {
                if (written.Contains(Path.GetFullPath(path))) continue;
                if (NeverSync.Contains(Path.GetFileName(path), StringComparer.OrdinalIgnoreCase)) continue;
                File.Delete(path);
            }
        }

        return written.Count;
    }

    /// <summary>
    /// De quién son los ajustes que están en la carpeta y con qué hash se sincronizaron.
    /// Sin esto no hay forma de saber si lo que está en disco es de esta cuenta o de la
    /// anterior, y habría que pisar todo en cada arranque.
    /// </summary>
    public static SettingsMarker? ReadMarker()
    {
        try
        {
            if (!File.Exists(MarkerPath)) return null;
            return JsonSerializer.Deserialize<SettingsMarker>(File.ReadAllText(MarkerPath));
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
            return null;
        }
    }

    public static void WriteMarker(string username, string sha1)
    {
        try
        {
            Directory.CreateDirectory(LauncherPaths.Root);
            File.WriteAllText(MarkerPath, JsonSerializer.Serialize(new SettingsMarker(username, sha1)));
        }
        catch (IOException)
        {
            // Sin marcador se sincroniza de más, que es molesto pero no rompe nada.
        }
    }

    private static string Hash(byte[] bytes) => Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();
}
