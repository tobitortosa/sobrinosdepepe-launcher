using System.Diagnostics;

namespace SobrinosDePepe.Core;

/// <summary>Lo que la pantalla de desinstalación muestra mientras borra.</summary>
public sealed record UninstallProgress(string Message, int Percent);

/// <summary>
/// Borrar todo lo que dejó el launcher, sin excusas.
///
/// El reparto del trabajo es a propósito: acá se borra lo pesado (el Minecraft, el Java
/// y los mods, que son un par de gigas) mientras la pantalla lo cuenta, y recién al final
/// se llama a Velopack, que borra su propia instalación, los accesos directos y la entrada
/// de "Aplicaciones instaladas". Al revés no se puede: Velopack mata el proceso que corre
/// desde la carpeta, así que en cuanto arranca ya no queda nadie para mostrar nada.
///
/// El remate lo hace un cmd suelto: espera a que Velopack termine y borra la carpeta raíz
/// entera. Es la única forma de garantizar que no queda ni un archivo, porque para entonces
/// nuestro propio ejecutable ya no existe.
/// </summary>
public static class Uninstall
{
    /// <summary>
    /// Todo lo que es nuestro y no pertenece a Velopack. Lo demás de la carpeta raíz
    /// (current, packages, Update.exe y el ejecutable puente) lo borra Velopack.
    /// </summary>
    private static IEnumerable<string> DataPaths() => new[]
    {
        LauncherPaths.GameDir,
        LauncherPaths.PackDir,
        LauncherPaths.SessionFile,
        LauncherPaths.LauncherLog,
    };

    /// <summary>
    /// Cuánto espacio se recupera al desinstalar, en bytes. Es la carpeta entera y no solo
    /// los datos: el launcher también ocupa, y lo que se muestra tiene que ser lo que el
    /// jugador va a ver aparecer en su disco.
    /// </summary>
    public static long TotalSize()
    {
        try
        {
            long total = 0;

            foreach (var file in Directory.EnumerateFiles(LauncherPaths.Root, "*", SearchOption.AllDirectories))
                try { total += new FileInfo(file).Length; } catch (Exception) { }

            return total;
        }
        catch (Exception)
        {
            // Una carpeta que desaparece mientras se cuenta no cambia nada: es una cifra
            // para mostrar, no un cálculo del que dependa nada.
            return 0;
        }
    }

    /// <summary>
    /// Borra el juego, el pack y la sesión, contando el avance. El juego se borra por
    /// carpeta y no de un saque para que la barra se mueva: son un par de gigas y en un
    /// disco lento tarda lo suyo.
    /// </summary>
    public static async Task RemoveDataAsync(IProgress<UninstallProgress> progress)
    {
        await Task.Run(() =>
        {
            // Las carpetas de adentro de game: mods, versiones, librerías, assets, el Java
            // y los registros. Es donde está el peso.
            var pieces = Directory.Exists(LauncherPaths.GameDir)
                ? Directory.GetFileSystemEntries(LauncherPaths.GameDir)
                : [];

            for (var i = 0; i < pieces.Length; i++)
            {
                progress.Report(new UninstallProgress(
                    "Borrando el juego y los mods…",
                    (int)(i * 80.0 / Math.Max(pieces.Length, 1))));
                Remove(pieces[i]);
            }

            progress.Report(new UninstallProgress("Borrando el juego y los mods…", 80));
            Remove(LauncherPaths.GameDir);

            progress.Report(new UninstallProgress("Borrando la configuración…", 90));
            Remove(LauncherPaths.PackDir);

            progress.Report(new UninstallProgress("Cerrando la sesión…", 96));
            Remove(LauncherPaths.SessionFile);
            Remove(LauncherPaths.LauncherLog);

            progress.Report(new UninstallProgress("Listo.", 100));
        });
    }

    /// <summary>
    /// La misma limpieza pero sin contar nada, para cuando la desinstalación no pasa por
    /// nuestra pantalla: Velopack llama a esto antes de borrarse. Tiene treinta segundos
    /// de plazo, así que va directo y sin reintentos largos.
    /// </summary>
    public static void RemoveDataQuickly()
    {
        foreach (var path in DataPaths()) Remove(path, attempts: 1);
    }

    /// <summary>
    /// Le pasa la posta a Velopack y deja programado el barrido final. Después de esto no
    /// hay nada más que hacer: hay que salir, porque Velopack necesita que la carpeta no
    /// tenga procesos vivos adentro.
    /// </summary>
    /// <returns>false si el launcher no está instalado, y entonces no hay nada que borrar.</returns>
    public static bool FinishAndExit()
    {
        var update = Path.Combine(LauncherPaths.Root, "Update.exe");
        if (!File.Exists(update)) return false;

        // Un cmd suelto, fuera de la carpeta que se va a borrar: corre el desinstalador de
        // Velopack, le da unos segundos y recién ahí borra lo que haya quedado. Si viviera
        // adentro de la carpeta se borraría a sí mismo a mitad de camino.
        //
        // El ping es la espera: timeout necesita una consola de verdad y acá no hay ninguna.
        // Y el /s hace que cmd saque las comillas de los extremos y tome el resto tal cual,
        // que es la única forma de que las rutas con espacios no lo confundan.
        var command =
            $"\"{update}\" --uninstall --silent & " +
            $"ping -n 6 127.0.0.1 >nul & " +
            $"rmdir /s /q \"{LauncherPaths.Root}\"";

        Process.Start(new ProcessStartInfo("cmd.exe", $"/s /c \"{command}\"")
        {
            WorkingDirectory = Path.GetTempPath(),
            CreateNoWindow = true,
            UseShellExecute = false,
        });

        return true;
    }

    /// <summary>
    /// Borra un archivo o una carpeta y no se rinde a la primera: Windows deja archivos
    /// tomados unos segundos después de cerrar el programa que los usaba, y un mod puede
    /// venir marcado como de solo lectura.
    /// </summary>
    private static void Remove(string path, int attempts = 3)
    {
        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                if (Directory.Exists(path))
                {
                    ClearReadOnly(path);
                    Directory.Delete(path, recursive: true);
                }
                else if (File.Exists(path))
                {
                    File.SetAttributes(path, FileAttributes.Normal);
                    File.Delete(path);
                }

                return;
            }
            catch (Exception) when (attempt < attempts)
            {
                Thread.Sleep(400);
            }
            catch (Exception)
            {
                // Lo que no se pudo borrar acá se lo lleva el barrido final de FinishAndExit.
                return;
            }
        }
    }

    private static void ClearReadOnly(string directory)
    {
        try
        {
            foreach (var file in Directory.EnumerateFiles(directory, "*", SearchOption.AllDirectories))
                try { File.SetAttributes(file, FileAttributes.Normal); } catch (Exception) { }
        }
        catch (Exception)
        {
        }
    }
}
