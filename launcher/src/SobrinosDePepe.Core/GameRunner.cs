using System.Diagnostics;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;
using CmlLib.Core.Version;

namespace SobrinosDePepe.Core;

public sealed record GameRunResult(int ExitCode, string LogPath);

/// <summary>
/// Arranca el juego con el Java propio y la sesión offline, y entra directo al servidor.
/// </summary>
public sealed class GameRunner
{
    /// <summary>
    /// La variable de entorno con la que se le pasa el permiso de entrada al juego.
    /// El mod del servidor la lee del lado del cliente: mod-acceso, Canal.VARIABLE.
    /// </summary>
    public const string TicketVariable = "SOBRINOSDEPEPE_TICKET";

    private readonly MinecraftLauncher _launcher;
    private readonly string _logPath;

    public GameRunner(MinecraftLauncher launcher, string logPath)
    {
        _launcher = launcher;
        _logPath = logPath;
    }

    /// <summary>
    /// Cuánta memoria darle a la JVM. Mojang usa 4 GB por defecto para esta versión;
    /// acá se baja si la PC tiene poca RAM para no dejarla sin nada.
    /// </summary>
    public static int RecommendedRamMb()
    {
        var totalBytes = GC.GetGCMemoryInfo().TotalAvailableMemoryBytes;
        var totalGb = totalBytes / (1024.0 * 1024 * 1024);
        if (totalGb < 8) return 3072;
        if (totalGb < 16) return 4096;
        return 6144;
    }

    /// <param name="ticket">
    /// El permiso de entrada que emitió el backend. Viaja en una variable de entorno del
    /// proceso del juego: un archivo se copia junto con la carpeta de mods y un argumento
    /// se ve en el administrador de tareas y termina pegado en los reportes de error.
    /// Va en null solo cuando se corre sin backend, y ahí el servidor no deja entrar.
    /// </param>
    public Process BuildProcess(
        IVersion version, string username, string serverAddress, string? ticket, int? ramMb = null)
    {
        var session = new MSession(username, "0", OfflineIdentity.Plain(username))
        {
            UserType = "msa"
        };

        var maxRam = ramMb ?? RecommendedRamMb();

        var options = new MLaunchOption
        {
            Session = session,
            MaximumRamMb = maxRam,
            MinimumRamMb = Math.Min(2048, maxRam),
            GameLauncherName = "sobrinosdepepe",
            GameLauncherVersion = "0.1",
            VersionType = "release",
            // Entra directo al servidor al arrancar: el jugador no pasa por el menú.
            ServerIp = serverAddress,
            ServerPort = 25565,
            Features = ["is_quick_play_multiplayer"]
        };

        var process = _launcher.BuildProcess(version, options);

        if (!string.IsNullOrEmpty(ticket))
        {
            process.StartInfo.Environment[TicketVariable] = ticket;
        }

        process.StartInfo.RedirectStandardOutput = true;
        process.StartInfo.RedirectStandardError = true;
        process.StartInfo.UseShellExecute = false;
        return process;
    }

    /// <summary>
    /// Corre el juego y guarda toda su salida en un archivo. Sin esto, cuando a alguien
    /// no le arranca, lo único que llega es "no me anda".
    /// </summary>
    /// <param name="started">
    /// Se completa en cuanto el proceso arrancó. Quien llama lo usa para empezar a
    /// esperar la ventana del juego, que recién existe después de esto.
    /// </param>
    public async Task<GameRunResult> RunAsync(
        Process process,
        Action<string>? onLine = null,
        TaskCompletionSource? started = null,
        CancellationToken ct = default)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_logPath)!);
        await using var log = new StreamWriter(_logPath, append: false);

        void Write(string? line)
        {
            if (line is null) return;
            lock (log) log.WriteLine(line);
            onLine?.Invoke(line);
        }

        process.OutputDataReceived += (_, e) => Write(e.Data);
        process.ErrorDataReceived += (_, e) => Write(e.Data);

        process.Start();
        started?.TrySetResult();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        await process.WaitForExitAsync(ct);
        await log.FlushAsync(ct);
        return new GameRunResult(process.ExitCode, _logPath);
    }

    /// <summary>El comando completo, para poder pegarlo en un reporte de error.</summary>
    public static string DescribeCommand(Process process)
    {
        var args = process.StartInfo.ArgumentList.Count > 0
            ? string.Join(' ', process.StartInfo.ArgumentList)
            : process.StartInfo.Arguments;
        return $"{process.StartInfo.FileName} {args}";
    }
}
