using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace SobrinosDePepe.Core;

public sealed record Account(
    [property: JsonPropertyName("username")] string Username,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("mustChangePassword")] bool MustChangePassword = false)
{
    public bool IsPending => Status == "pending";
    public bool IsBanned => Status == "banned";
    public bool IsAdmin => Role == "admin";
}

public sealed record Session(string Token, Account Account);

/// <summary>
/// Lo que tiene que estar al día en la máquina del jugador. Cualquiera de los dos
/// puede venir en null si el backend todavía no lo sabe; ahí no se hace nada.
/// </summary>
public sealed record Versions(
    [property: JsonPropertyName("launcher")] string? Launcher,
    [property: JsonPropertyName("pack")] string? Pack);

/// <summary>
/// Un error que ya viene explicado por el backend. El mensaje se puede mostrar tal cual.
/// </summary>
public sealed class ApiException(string message, HttpStatusCode status) : Exception(message)
{
    public HttpStatusCode Status { get; } = status;
}

/// <summary>
/// Todo lo que el launcher le pide al backend: crear la cuenta, iniciar sesión y traer
/// el pack. Cada vez que el jugador aprieta JUGAR se vuelve a pedir el pack, así una
/// publicación nueva del admin llega sola.
/// </summary>
public sealed class LauncherApi
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _http;
    private readonly Uri _baseUrl;

    public LauncherApi(Uri baseUrl, HttpClient? http = null)
    {
        _baseUrl = baseUrl;
        _http = http ?? HashedDownloader.CreateHttpClient();
    }

    public Task<Session> RegisterAsync(string username, string password, CancellationToken ct = default) =>
        AuthAsync("api/auth/register", username, password, ct);

    public Task<Session> LoginAsync(string username, string password, CancellationToken ct = default) =>
        AuthAsync("api/auth/login", username, password, ct);

    private async Task<Session> AuthAsync(string path, string username, string password, CancellationToken ct)
    {
        using var response = await _http.PostAsJsonAsync(new Uri(_baseUrl, path), new { username, password }, Json, ct);
        var body = await ReadAsync<AuthResponse>(response, ct);
        return new Session(body.Token, body.User);
    }

    /// <summary>Estado de la cuenta. La pantalla de espera lo consulta cada tanto.</summary>
    public async Task<Account> AccountAsync(string token, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, "api/me", token);
        using var response = await _http.SendAsync(request, ct);
        return await ReadAsync<Account>(response, ct);
    }

    /// <summary>
    /// Las versiones al día del launcher y del pack de mods. No lleva sesión: es lo
    /// único que se consulta cada diez segundos y no dice nada que no esté publicado.
    /// </summary>
    public async Task<Versions> VersionsAsync(CancellationToken ct = default)
    {
        using var response = await _http.GetAsync(new Uri(_baseUrl, "api/version"), ct);
        return await ReadAsync<Versions>(response, ct);
    }

    /// <summary>
    /// El permiso para entrar al servidor. Se pide justo antes de abrir el juego y se
    /// le pasa al juego en una variable de entorno; el mod del servidor lo revisa
    /// durante el login y sin él no deja entrar. Dura doce horas y va firmado con el
    /// nombre de la cuenta, así que no sirve para prestárselo a otro.
    /// </summary>
    public async Task<string> TicketAsync(string token, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, "api/ticket", token);
        using var response = await _http.SendAsync(request, ct);
        var body = await ReadAsync<TicketResponse>(response, ct);
        return body.Ticket;
    }

    /// <summary>El pack publicado. Una cuenta pendiente recibe un error explicado.</summary>
    public async Task<Pack> PackAsync(string token, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, "api/pack", token);
        using var response = await _http.SendAsync(request, ct);
        return await ReadAsync<Pack>(response, ct);
    }

    /// <summary>
    /// La persona elige su propia contraseña. Se usa cuando entró con la provisoria
    /// que le restableció el administrador.
    /// </summary>
    public async Task ChangePasswordAsync(
        string token, string password, string confirm, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Post, "api/auth/password", token);
        request.Content = JsonContent.Create(new { password, confirm }, options: Json);
        using var response = await _http.SendAsync(request, ct);
        await ReadAsync<JsonElement>(response, ct);
    }

    public async Task LogoutAsync(string token, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Post, "api/auth/logout", token);
        using var response = await _http.SendAsync(request, ct);
        // Cerrar sesión en el servidor es un extra: el token local ya se borró.
    }

    /// <summary>
    /// Llamada autenticada genérica. La usa el panel de administración, que vive en el
    /// mismo launcher pero no tiene ningún secreto: el backend verifica el rol.
    /// </summary>
    internal async Task<T> SendAsync<T>(
        HttpMethod method, string path, string token, HttpContent? content, CancellationToken ct)
    {
        using var request = Authorized(method, path, token);
        request.Content = content;
        using var response = await _http.SendAsync(request, ct);
        return await ReadAsync<T>(response, ct);
    }

    private HttpRequestMessage Authorized(HttpMethod method, string path, string token)
    {
        var request = new HttpRequestMessage(method, new Uri(_baseUrl, path));
        request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        return request;
    }

    private static async Task<T> ReadAsync<T>(HttpResponseMessage response, CancellationToken ct)
    {
        var text = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
        {
            var message = TryReadError(text) ?? $"El servidor respondió {(int)response.StatusCode}.";
            throw new ApiException(message, response.StatusCode);
        }

        var value = JsonSerializer.Deserialize<T>(text, Json);
        if (value is null) throw new ApiException("El servidor devolvió una respuesta vacía.", response.StatusCode);
        return value;
    }

    private static string? TryReadError(string text)
    {
        try
        {
            using var document = JsonDocument.Parse(text);
            return document.RootElement.TryGetProperty("error", out var error) ? error.GetString() : null;
        }
        catch (JsonException)
        {
            return null;
        }
    }

    private sealed record TicketResponse(
        [property: JsonPropertyName("ticket")] string Ticket,
        [property: JsonPropertyName("vence")] long Vence);

    private sealed record AuthResponse(
        [property: JsonPropertyName("token")] string Token,
        [property: JsonPropertyName("user")] Account User);
}
