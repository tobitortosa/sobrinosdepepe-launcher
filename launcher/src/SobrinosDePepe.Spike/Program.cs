using System.Diagnostics;
using SobrinosDePepe.Core;

// Herramienta de consola para probar el núcleo del launcher sin interfaz.
//
// Contra el backend (lo que va a hacer el launcher de verdad):
//   dotnet run --project src/SobrinosDePepe.Spike -- --api https://... --user PEPE --pass ****
//
// Con un pack local, para desarrollar sin backend:
//   dotnet run --project src/SobrinosDePepe.Spike -- PEPE
//
// Opciones: --no-launch (instala y no abre el juego) · --ram 4096

Console.OutputEncoding = System.Text.Encoding.UTF8;
Console.WriteLine("SOBRINOS DE PEPE · instalador (consola)");
Console.WriteLine(new string('─', 60));

var api = Value("--api");
var user = Value("--user");
var pass = Value("--pass");
var launch = !args.Contains("--no-launch");
var ramMb = Value("--ram") is { } raw && int.TryParse(raw, out var mb) ? mb : (int?)null;
var localUsername = args.FirstOrDefault(a => !a.StartsWith('-')) ?? "PEPE";

if (!Environment.Is64BitOperatingSystem)
{
    Fail("Esta versión de Minecraft necesita Windows de 64 bits. Mojang no publica Java 25 para 32 bits.");
    return 1;
}

const long needed = 2L * 1024 * 1024 * 1024;
var free = LauncherPaths.FreeDiskBytes();
if (free < needed)
{
    Fail($"Hacen falta unos 2 GB libres y hay {free / 1024 / 1024} MB. Liberá espacio y volvé a intentar.");
    return 1;
}

// Comprobación rápida de si el juego está abierto, para diagnosticar.
if (args.Contains("--estado-juego"))
{
    using var abierto = GameProcess.Find();
    Console.WriteLine(abierto is null
        ? "No hay ningún Minecraft abierto desde esta carpeta."
        : $"Minecraft abierto · proceso {abierto.Id} · ventana: {(abierto.MainWindowHandle != IntPtr.Zero ? "sí" : "no")}");
    return 0;
}

// Muestra la lista de servidores del menú multijugador, para diagnosticar.
// Con --ensure corre encima lo mismo que corre al instalar, para ver qué queda.
if (args.Contains("--lista-servidores"))
{
    var archivo = Value("--archivo") ?? Path.Combine(LauncherPaths.GameDir, "servers.dat");
    Console.WriteLine($"Archivo: {archivo}");

    if (args.Contains("--ensure"))
    {
        var cambio = ServerList.Ensure(archivo,
            new SavedServer(Value("--nombre") ?? "SOBRINOS DE PEPE",
                            Value("--direccion") ?? "sobrinosdepepe.minehost.pro",
                            AcceptTextures: true));
        Console.WriteLine(cambio ? "  (se reescribió el archivo)" : "  (ya estaba como tenía que estar)");
    }

    foreach (var s in ServerList.Read(archivo))
        Console.WriteLine($"  {s.Name,-24} {s.Address,-38} pack: {(s.AcceptTextures ? "acepta solo" : "pregunta")}");
    return 0;
}

// Pregunta quiénes están conectados, para diagnosticar.
if (args.Contains("--conectados"))
{
    var direccion = Value("--servidor") ?? AppConfigFallback();
    var jugadores = await ServerQuery.PlayersAsync(direccion);
    Console.WriteLine(jugadores.Answered
        ? jugadores.Names.Count == 0
            ? "El servidor contestó: no hay nadie conectado."
            : $"Conectados ({jugadores.Names.Count}): {string.Join(", ", jugadores.Names)}"
        : "El servidor no contestó la consulta (¿enable-query está en true?).");
    return 0;
}

LauncherPaths.EnsureCreated();
using var http = HashedDownloader.CreateHttpClient();

Pack pack;
string username;
string? token = null;

try
{
    if (api is not null)
    {
        // ---- Flujo real: iniciar sesión y traer el pack publicado.
        if (user is null || pass is null)
        {
            Fail("Con --api hay que pasar también --user y --pass.");
            return 1;
        }

        var client = new LauncherApi(new Uri(api), http);

        Console.WriteLine($"Backend: {api}");
        var session = await client.LoginAsync(user, pass);
        token = session.Token;
        SessionStore.Save(token);

        Console.WriteLine($"Sesión iniciada como {session.Account.Username}" +
                          (session.Account.IsAdmin ? " (administrador)" : ""));

        if (session.Account.IsPending)
        {
            Console.WriteLine();
            Console.WriteLine("Tu cuenta todavía no fue aprobada. Cuando te aprueben, volvé a entrar.");
            return 0;
        }

        Console.WriteLine("Buscando actualizaciones…");
        pack = await client.PackAsync(token);
        username = session.Account.Username;

        var installed = PackInstaller.InstalledVersion();
        Console.WriteLine(installed is null
            ? $"Primera instalación · pack {pack.PackVersion}"
            : installed == pack.PackVersion
                ? $"Pack {pack.PackVersion}: ya lo tenés"
                : $"Pack {installed} → {pack.PackVersion}: hay novedades");
    }
    else
    {
        // ---- Modo de desarrollo: pack tomado de un archivo local.
        var packPath = FindFile("pack.json");
        if (packPath is null)
        {
            Fail("No encontré pack.json. Para usar el backend, pasá --api, --user y --pass.");
            return 1;
        }

        pack = Pack.Load(packPath);
        username = localUsername;
        Console.WriteLine($"Pack local {pack.PackVersion} ({packPath})");
    }

    Console.WriteLine($"Minecraft {pack.Minecraft} · Fabric {pack.FabricLoader} · {pack.ClientMods.Count()} mods");
    Console.WriteLine($"Servidor: {pack.Server.Name} ({pack.Server.Address})");
    Console.WriteLine($"Usuario: {username} · UUID: {OfflineIdentity.Dashed(username)}");
    Console.WriteLine($"Carpeta: {LauncherPaths.Root}");
    Console.WriteLine();

    // ---- Instalar o actualizar.
    var lastPercent = -1;
    var lastStage = "";
    var progress = new Progress<SetupProgress>(p =>
    {
        var percent = p.Total > 0 ? p.Current * 100 / p.Total : 0;
        var text = p.BytesTotal > 0
            ? $"  {p.Stage,-18} {percent,3}%  {p.Current}/{p.Total} archivos  {p.BytesDone / 1024 / 1024}/{p.BytesTotal / 1024 / 1024} MB"
            : $"  {p.Stage,-18} {percent,3}%  {p.Current}/{p.Total} archivos";

        if (Console.IsOutputRedirected)
        {
            if (p.Stage == lastStage && percent / 10 == lastPercent / 10) return;
            Console.WriteLine(text);
        }
        else
        {
            if (p.Stage == lastStage && percent == lastPercent) return;
            Console.Write('\r' + text.PadRight(78));
        }

        lastPercent = percent;
        lastStage = p.Stage;
    });

    var detail = new Progress<string>(line => Console.WriteLine("  " + line));

    // Los archivos que el admin subió salen del backend y hacen falta credenciales.
    var backend = api is not null && token is not null ? (new Uri(api), token) : ((Uri, string)?)null;

    var installer = new PackInstaller(
        new GameSetup(LauncherPaths.GameDir, http),
        new ModSynchronizer(new HashedDownloader(http, backend: backend), LauncherPaths.OwnModsFile),
        FindDirectory("overrides"));

    var (version, report) = await installer.ApplyAsync(pack, progress, detail);
    Console.WriteLine();
    Console.WriteLine(report.WasUpToDate
        ? $"Todo al día ({report.Elapsed.TotalSeconds:F0} s de verificación)."
        : $"Listo en {report.Elapsed.TotalSeconds:F0} s · {report.ModsDownloaded} mods bajados · " +
          $"{report.ModsRemoved} borrados · {report.ConfigsWritten} configs nuevas");

    // ---- Estado del servidor.
    var info = await ServerStatus.QueryAsync(pack.Server.Address);
    Console.WriteLine(info.Online
        ? $"Servidor online · {info.Version} · {info.Players}/{info.MaxPlayers} · {info.LatencyMs} ms"
        : "Servidor offline o no responde.");
    Console.WriteLine();

    if (!launch)
    {
        Console.WriteLine("Listo (no se abre el juego por --no-launch).");
        return 0;
    }

    // ---- Arrancar.
    var launcher = new GameSetup(LauncherPaths.GameDir, http).CreateLauncherWithoutJavaExtractor();
    var runner = new GameRunner(launcher, Path.Combine(LauncherPaths.Root, "game-output.log"));
    // El permiso de entrada al servidor. Sin backend no hay ninguno, y el servidor
    // no deja entrar: la fase 0 se prueba con --api, --user y --pass.
    string? ticket = null;
    if (api is not null && token is not null)
    {
        ticket = await new LauncherApi(new Uri(api), http).TicketAsync(token);
        Console.WriteLine("Permiso de entrada: listo");
    }
    else
    {
        Console.WriteLine("Sin backend no hay permiso de entrada: el servidor va a rechazar la conexión.");
    }

    using var process = runner.BuildProcess(version, username, pack.Server.Address, ticket, ramMb);

    Console.WriteLine($"Java: {process.StartInfo.FileName}");
    Console.WriteLine($"RAM máxima: {ramMb ?? GameRunner.RecommendedRamMb()} MB");
    Console.WriteLine("Abriendo el juego…");
    Console.WriteLine(new string('─', 60));

    var run = await runner.RunAsync(process, line =>
    {
        if (line.Contains("Setting user:") || line.Contains("Connecting to") ||
            line.Contains("Loading Minecraft") || line.Contains("/ERROR]") || line.Contains("/FATAL]"))
            Console.WriteLine(line);
    });

    Console.WriteLine(new string('─', 60));
    Console.WriteLine(run.ExitCode == 0
        ? "El juego se cerró normalmente."
        : $"El juego terminó con código {run.ExitCode}. Mirá {run.LogPath}");
    return run.ExitCode;
}
catch (ApiException ex)
{
    Console.WriteLine();
    Fail(ex.Message);
    return 1;
}
catch (Exception ex)
{
    Console.WriteLine();
    Fail(ex.Message);
    await File.WriteAllTextAsync(LauncherPaths.LauncherLog, ex.ToString());
    Console.WriteLine($"Detalle completo en {LauncherPaths.LauncherLog}");
    return 1;
}

string? Value(string name)
{
    var index = Array.IndexOf(args, name);
    return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
}

static void Fail(string message)
{
    var before = Console.ForegroundColor;
    Console.ForegroundColor = ConsoleColor.Red;
    Console.WriteLine("Error: " + message);
    Console.ForegroundColor = before;
}

static string AppConfigFallback() => "sobrinosdepepe.minehost.pro";

static string? FindFile(string name) => Candidates(name).FirstOrDefault(File.Exists);
static string? FindDirectory(string name) => Candidates(name).FirstOrDefault(Directory.Exists);

static IEnumerable<string> Candidates(string name) =>
[
    Path.Combine(AppContext.BaseDirectory, name),
    Path.Combine(Directory.GetCurrentDirectory(), name),
    Path.Combine(Directory.GetCurrentDirectory(), "launcher", name),
];
