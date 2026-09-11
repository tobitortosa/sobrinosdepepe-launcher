using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace SobrinosDePepe.Core;

public sealed record AdminUser(
    [property: JsonPropertyName("id")] int Id,
    [property: JsonPropertyName("username")] string Username,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt)
{
    public bool IsPending => Status == "pending";
    public bool IsActive => Status == "active";
    public bool IsBanned => Status == "banned";
    public bool IsAdmin => Role == "admin";

    public string StatusLabel => Status switch
    {
        "pending" => "pendiente",
        "active" => "activo",
        "banned" => "baneado",
        _ => Status,
    };
}

public sealed record AdminMod(
    [property: JsonPropertyName("projectId")] string ProjectId,
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("versionNumber")] string VersionNumber,
    [property: JsonPropertyName("filename")] string Filename,
    [property: JsonPropertyName("side")] string Side,
    [property: JsonPropertyName("license")] string License,
    [property: JsonPropertyName("pageUrl")] string PageUrl,
    [property: JsonPropertyName("size")] long Size)
{
    public string SideLabel => Side switch
    {
        "client" => "cliente",
        "server" => "servidor",
        _ => "ambos",
    };

    public string SizeLabel => $"{Size / 1024} KB";
}

public sealed record PackDraft(
    [property: JsonPropertyName("publishedVersion")] string? PublishedVersion,
    [property: JsonPropertyName("hasUnpublishedChanges")] bool HasUnpublishedChanges,
    [property: JsonPropertyName("minecraft")] string Minecraft,
    [property: JsonPropertyName("fabricLoader")] string FabricLoader,
    [property: JsonPropertyName("mods")] List<AdminMod> Mods,
    [property: JsonPropertyName("serverSide")] List<string> ServerSide);

public sealed record UploadResult(
    [property: JsonPropertyName("added")] List<UploadedMod> Added,
    [property: JsonPropertyName("rejected")] List<RejectedFile> Rejected);

public sealed record UploadedMod(
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("side")] string Side,
    [property: JsonPropertyName("recognised")] bool Recognised,
    [property: JsonPropertyName("note")] string? Note);

public sealed record RejectedFile(
    [property: JsonPropertyName("filename")] string Filename,
    [property: JsonPropertyName("reason")] string Reason);

public sealed record PublishResult(
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("mods")] int Mods,
    [property: JsonPropertyName("serverSide")] List<string> ServerSide);

public sealed record TemporaryPassword(
    [property: JsonPropertyName("username")] string Username,
    [property: JsonPropertyName("password")] string Password);

/// <summary>
/// Lo que usa el panel de administración, que vive dentro del launcher. Nada de esto
/// lleva secretos: son llamadas al backend, que verifica el rol y es el único que
/// tiene la clave del panel de Minehost.
/// </summary>
public sealed record ServerMod(
    [property: JsonPropertyName("filename")] string Filename,
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("size")] long Size);

public sealed record ServerMods(
    [property: JsonPropertyName("directory")] string Directory,
    [property: JsonPropertyName("missing")] List<ServerMod> Missing,
    [property: JsonPropertyName("extra")] List<string> Extra,
    [property: JsonPropertyName("ok")] List<string> Ok);

public sealed record ServerState(
    [property: JsonPropertyName("state")] string State,
    [property: JsonPropertyName("online")] bool Online,
    [property: JsonPropertyName("error")] string? Error)
{
    /// <summary>Los estados que devuelve el panel, en castellano.</summary>
    public string Label => State switch
    {
        "running" => "encendido",
        "starting" => "arrancando",
        "stopping" => "apagándose",
        "offline" => "apagado",
        _ => Error is null ? "no sé" : "no pude preguntar",
    };

    public bool IsBusy => State is "starting" or "stopping";
}

public sealed record ServerModsResult(
    [property: JsonPropertyName("uploaded")] List<string> Uploaded,
    [property: JsonPropertyName("removed")] List<string> Removed,
    [property: JsonPropertyName("note")] string Note);

public static class LauncherAdminApi
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public static async Task<List<AdminUser>> UsersAsync(
        this LauncherApi api, string token, string? search = null, CancellationToken ct = default)
    {
        var path = string.IsNullOrWhiteSpace(search)
            ? "api/admin/users"
            : $"api/admin/users?q={Uri.EscapeDataString(search)}";
        var body = await api.SendAsync<UsersResponse>(HttpMethod.Get, path, token, null, ct);
        return body.Users;
    }

    public static Task ApproveAsync(this LauncherApi api, string token, int userId, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Post, $"api/admin/users/{userId}/approve", token, null, ct);

    public static Task BanAsync(this LauncherApi api, string token, int userId, string reason, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Post, $"api/admin/users/{userId}/ban", token,
            JsonContent(new { reason }), ct);

    public static Task UnbanAsync(this LauncherApi api, string token, int userId, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Post, $"api/admin/users/{userId}/unban", token, null, ct);

    public static Task<TemporaryPassword> ResetPasswordAsync(
        this LauncherApi api, string token, int userId, CancellationToken ct = default) =>
        api.SendAsync<TemporaryPassword>(HttpMethod.Post, $"api/admin/users/{userId}/password", token, null, ct);

    public static Task<PackDraft> PackDraftAsync(this LauncherApi api, string token, CancellationToken ct = default) =>
        api.SendAsync<PackDraft>(HttpMethod.Get, "api/admin/mods", token, null, ct);

    public static Task RemoveModAsync(
        this LauncherApi api, string token, string projectId, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Delete,
            $"api/admin/mods?projectId={Uri.EscapeDataString(projectId)}", token, null, ct);

    public static Task<PublishResult> PublishAsync(this LauncherApi api, string token, CancellationToken ct = default) =>
        api.SendAsync<PublishResult>(HttpMethod.Post, "api/admin/pack/publish", token, null, ct);

    /// <summary>Sube uno o varios .jar. El backend calcula los hashes y reconoce cada mod.</summary>
    public static async Task<UploadResult> UploadModsAsync(
        this LauncherApi api, string token, IEnumerable<string> paths, CancellationToken ct = default)
    {
        var form = new MultipartFormDataContent();
        foreach (var path in paths)
        {
            var content = new StreamContent(File.OpenRead(path));
            content.Headers.ContentType = new MediaTypeHeaderValue("application/java-archive");
            form.Add(content, "files", Path.GetFileName(path));
        }

        return await api.SendAsync<UploadResult>(HttpMethod.Post, "api/admin/mods/upload", token, form, ct);
    }

    /// <summary>
    /// Sube mods a la cuenta propia, no al pack. El backend le dice que no a toda
    /// cuenta que no sea admin: es lo único que separa esto de que cada jugador se
    /// pueda meter lo que quiera en la carpeta.
    /// </summary>
    public static async Task<UploadResult> UploadMyModsAsync(
        this LauncherApi api, string token, IEnumerable<string> paths, CancellationToken ct = default)
    {
        var form = new MultipartFormDataContent();
        foreach (var path in paths)
        {
            var content = new StreamContent(File.OpenRead(path));
            content.Headers.ContentType = new MediaTypeHeaderValue("application/java-archive");
            form.Add(content, "files", Path.GetFileName(path));
        }

        return await api.SendAsync<UploadResult>(HttpMethod.Post, "api/my-mods", token, form, ct);
    }

    /// <summary>Saca un mod de la cuenta. El .jar queda, por si el pack lo usa.</summary>
    public static Task RemoveMyModAsync(
        this LauncherApi api, string token, string sha1, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Delete,
            $"api/my-mods?sha1={Uri.EscapeDataString(sha1)}", token, null, ct);

    /// <summary>Si el servidor está encendido, apagado o arrancando.</summary>
    public static Task<ServerState> ServerStateAsync(this LauncherApi api, string token, CancellationToken ct = default) =>
        api.SendAsync<ServerState>(HttpMethod.Get, "api/admin/server", token, null, ct);

    /// <summary>Qué mods tiene el servidor comparado con lo que dice el pack.</summary>
    public static Task<ServerMods> ServerModsAsync(this LauncherApi api, string token, CancellationToken ct = default) =>
        api.SendAsync<ServerMods>(HttpMethod.Get, "api/admin/server/mods", token, null, ct);

    /// <summary>Sube al servidor los mods que falten.</summary>
    public static Task<ServerModsResult> UploadServerModsAsync(
        this LauncherApi api, string token, CancellationToken ct = default) =>
        api.SendAsync<ServerModsResult>(HttpMethod.Post, "api/admin/server/mods", token,
            JsonContent(new { upload = true }), ct);

    /// <summary>Borra del servidor los archivos indicados, por nombre.</summary>
    public static Task<ServerModsResult> RemoveServerModsAsync(
        this LauncherApi api, string token, IEnumerable<string> filenames, CancellationToken ct = default) =>
        api.SendAsync<ServerModsResult>(HttpMethod.Post, "api/admin/server/mods", token,
            JsonContent(new { remove = filenames.ToArray() }), ct);

    /// <summary>"start", "restart" o "stop".</summary>
    public static Task PowerAsync(this LauncherApi api, string token, string signal, CancellationToken ct = default) =>
        api.SendAsync<JsonElement>(HttpMethod.Post, "api/admin/server/power", token,
            JsonContent(new { signal }), ct);

    private static StringContent JsonContent(object value) =>
        new(JsonSerializer.Serialize(value, Json), System.Text.Encoding.UTF8, "application/json");

    private sealed record UsersResponse([property: JsonPropertyName("users")] List<AdminUser> Users);
}
