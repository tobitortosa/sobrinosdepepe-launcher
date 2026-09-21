using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using DnsClient;

namespace SobrinosDePepe.Core;

/// <summary>
/// Lo que contesta el servidor cuando se le pide el estado. <paramref name="Names"/> son
/// los nombres de quienes están jugando: el servidor manda una muestra de hasta doce, así
/// que con más de doce adentro el número de <paramref name="Players"/> sigue siendo el
/// bueno y la lista queda corta.
/// </summary>
public sealed record ServerInfo(
    bool Online, string? Version, int Protocol, int Players, int MaxPlayers, string? Motd,
    int LatencyMs, IReadOnlyList<string> Names)
{
    public static readonly ServerInfo Offline = new(false, null, 0, 0, 0, null, 0, []);
}

/// <summary>
/// El punto verde o rojo del launcher. Hay un detalle importante: sobrinosdepepe.minehost.pro
/// no tiene registro A, se resuelve por SRV hacia otro host y otro puerto. El cliente de
/// Minecraft hace esa resolución solo, pero nosotros la tenemos que hacer a mano para el ping.
/// </summary>
public static class ServerStatus
{
    public static async Task<(string Host, int Port)> ResolveAsync(string address, int defaultPort = 25565)
    {
        try
        {
            var lookup = new LookupClient();
            var result = await lookup.QueryAsync($"_minecraft._tcp.{address}", QueryType.SRV);
            var srv = result.Answers.SrvRecords().OrderBy(r => r.Priority).FirstOrDefault();
            if (srv is not null)
                return (srv.Target.Value.TrimEnd('.'), srv.Port);
        }
        catch (DnsResponseException)
        {
            // Sin SRV se usa el host tal cual.
        }

        return (address, defaultPort);
    }

    public static async Task<ServerInfo> QueryAsync(string address, int timeoutMs = 3000)
    {
        var (host, port) = await ResolveAsync(address);
        var started = Environment.TickCount64;

        try
        {
            using var client = new TcpClient();
            using var cts = new CancellationTokenSource(timeoutMs);
            await client.ConnectAsync(host, port, cts.Token);
            await using var stream = client.GetStream();

            // Handshake pidiendo estado, con el hostname original (el server puede usarlo).
            var handshake = new List<byte> { 0x00 };
            WriteVarInt(handshake, -1);
            WriteString(handshake, address);
            handshake.AddRange([(byte)(port >> 8), (byte)(port & 0xFF)]);
            WriteVarInt(handshake, 1);
            await WritePacketAsync(stream, handshake, cts.Token);
            await WritePacketAsync(stream, [0x00], cts.Token);

            _ = await ReadVarIntAsync(stream, cts.Token);   // largo del paquete
            _ = await ReadVarIntAsync(stream, cts.Token);   // id del paquete
            var jsonLength = await ReadVarIntAsync(stream, cts.Token);

            var buffer = new byte[jsonLength];
            var read = 0;
            while (read < jsonLength)
            {
                var chunk = await stream.ReadAsync(buffer.AsMemory(read, jsonLength - read), cts.Token);
                if (chunk == 0) break;
                read += chunk;
            }

            var latency = (int)(Environment.TickCount64 - started);
            using var doc = JsonDocument.Parse(Encoding.UTF8.GetString(buffer, 0, read));
            var root = doc.RootElement;

            var version = root.TryGetProperty("version", out var v) && v.TryGetProperty("name", out var vn)
                ? vn.GetString() : null;
            var protocol = root.TryGetProperty("version", out var v2) && v2.TryGetProperty("protocol", out var pr)
                ? pr.GetInt32() : 0;
            var online = root.TryGetProperty("players", out var pl) && pl.TryGetProperty("online", out var on)
                ? on.GetInt32() : 0;
            var max = pl.ValueKind == JsonValueKind.Object && pl.TryGetProperty("max", out var mx)
                ? mx.GetInt32() : 0;
            // La muestra puede no venir: el servidor la omite cuando no hay nadie.
            var names = new List<string>();
            if (pl.ValueKind == JsonValueKind.Object && pl.TryGetProperty("sample", out var sample) &&
                sample.ValueKind == JsonValueKind.Array)
                foreach (var player in sample.EnumerateArray())
                    if (player.TryGetProperty("name", out var name) && name.GetString() is { } texto)
                        names.Add(texto);

            var motd = root.TryGetProperty("description", out var d)
                ? (d.ValueKind == JsonValueKind.String ? d.GetString() : d.TryGetProperty("text", out var t) ? t.GetString() : null)
                : null;

            return new ServerInfo(true, version, protocol, online, max, motd, latency, names);
        }
        catch (Exception ex) when (ex is SocketException or OperationCanceledException or IOException or JsonException)
        {
            return ServerInfo.Offline;
        }
    }

    private static async Task WritePacketAsync(NetworkStream stream, IEnumerable<byte> payload, CancellationToken ct)
    {
        var body = payload.ToArray();
        var frame = new List<byte>();
        WriteVarInt(frame, body.Length);
        frame.AddRange(body);
        await stream.WriteAsync(frame.ToArray(), ct);
    }

    private static void WriteVarInt(List<byte> target, int value)
    {
        var v = unchecked((uint)value);
        while (true)
        {
            if ((v & ~0x7Fu) == 0) { target.Add((byte)v); return; }
            target.Add((byte)((v & 0x7F) | 0x80));
            v >>= 7;
        }
    }

    private static void WriteString(List<byte> target, string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        WriteVarInt(target, bytes.Length);
        target.AddRange(bytes);
    }

    private static async Task<int> ReadVarIntAsync(NetworkStream stream, CancellationToken ct)
    {
        var result = 0;
        for (var i = 0; i < 5; i++)
        {
            var one = new byte[1];
            if (await stream.ReadAsync(one, ct) == 0) throw new IOException("Conexión cerrada.");
            result |= (one[0] & 0x7F) << (7 * i);
            if ((one[0] & 0x80) == 0) return result;
        }
        throw new IOException("VarInt inválido.");
    }
}
