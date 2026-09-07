namespace SobrinosDePepe.Core;

/// <summary>
/// Todo vive en %LOCALAPPDATA%\SobrinosDePepe. No se toca nunca %APPDATA%\.minecraft:
/// los jugadores tienen que poder seguir usando TLauncher sin que les pisemos nada.
/// </summary>
public static class LauncherPaths
{
    public static string Root { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "SobrinosDePepe");

    public static string GameDir => Path.Combine(Root, "game");
    public static string PackDir => Path.Combine(Root, "pack");
    public static string ModsDir => Path.Combine(GameDir, "mods");
    public static string ConfigDir => Path.Combine(GameDir, "config");
    public static string LogsDir => Path.Combine(GameDir, "logs");
    public static string LauncherLog => Path.Combine(Root, "launcher.log");
    public static string SessionFile => Path.Combine(Root, "session.dat");

    /// <summary>
    /// Los mods que el jugador puso a mano y quiere conservar, uno por línea.
    /// Sin este archivo, la sincronización borra cualquier .jar que no esté en el
    /// pack, y eso es a propósito: un jar viejo que sobrevive a un cambio de pack
    /// crashea el juego al arrancar. Esto es la excepción explícita para quien sabe
    /// lo que está haciendo, y por eso hay que escribir el nombre a mano.
    ///
    /// Vive en la raíz y no adentro de mods/, que es una carpeta del pack.
    /// </summary>
    public static string OwnModsFile => Path.Combine(Root, "mods-propios.txt");

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(Root);
        Directory.CreateDirectory(GameDir);
        Directory.CreateDirectory(PackDir);
        Directory.CreateDirectory(ModsDir);
    }

    /// <summary>Espacio libre en el disco donde se instala, en bytes.</summary>
    public static long FreeDiskBytes()
    {
        var root = Path.GetPathRoot(Path.GetFullPath(Root));
        if (string.IsNullOrEmpty(root)) return long.MaxValue;
        return new DriveInfo(root).AvailableFreeSpace;
    }
}
