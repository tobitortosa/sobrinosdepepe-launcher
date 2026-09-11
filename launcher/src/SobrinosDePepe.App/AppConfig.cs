using System.Text.Json;

namespace SobrinosDePepe.App;

/// <summary>
/// La dirección del backend. Viene compilada, y se puede cambiar sin recompilar
/// poniendo un archivo launcher.json al lado del ejecutable. Sirve para probar
/// contra el backend local sin tocar el código.
/// </summary>
public static class AppConfig
{
    public const string DefaultApiUrl = "https://sobrinosdepepe.com";
    public const string DefaultServerAddress = "sobrinosdepepe.minehost.pro";

    public static Uri ApiUrl { get; } = Resolve();

    /// <summary>La dirección del servidor de Minecraft, para el indicador de estado.</summary>
    public static string ServerAddress { get; } = ReadSetting("serverAddress") ?? DefaultServerAddress;

    private static string? ReadSetting(string key)
    {
        var file = Path.Combine(AppContext.BaseDirectory, "launcher.json");
        if (!File.Exists(file)) return null;

        try
        {
            using var document = JsonDocument.Parse(File.ReadAllText(file));
            return document.RootElement.TryGetProperty(key, out var value) ? value.GetString() : null;
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
            return null;
        }
    }

    private static Uri Resolve()
    {
        var fromEnvironment = Environment.GetEnvironmentVariable("QUEMANDAN_API");
        if (!string.IsNullOrWhiteSpace(fromEnvironment) &&
            Uri.TryCreate(fromEnvironment, UriKind.Absolute, out var envUri))
            return envUri;

        if (Uri.TryCreate(ReadSetting("apiUrl"), UriKind.Absolute, out var fromFile)) return fromFile;

        return new Uri(DefaultApiUrl);
    }
}
