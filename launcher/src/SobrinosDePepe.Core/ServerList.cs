using System.Buffers.Binary;
using System.Text;

namespace SobrinosDePepe.Core;

/// <param name="AcceptTextures">
/// Si el cliente acepta solo el resource pack que manda ese servidor. Es el campo
/// <c>acceptTextures</c> del archivo, y es POR SERVIDOR: no hay ninguna opción global
/// en options.txt. Sin él, Minecraft pregunta "¿querés descargar el pack?" la primera
/// vez que entrás, y para el jugador es un cartel raro que nadie sabe qué contesta.
/// </param>
public sealed record SavedServer(string Name, string Address, bool AcceptTextures = false);

/// <summary>
/// La lista de servidores del menú multijugador, el archivo servers.dat.
///
/// Al apretar JUGAR el launcher entra directo al servidor, pero si alguien se
/// desconecta y va al menú, la lista tiene que tener el servidor cargado. Si no, no
/// sabe a qué dirección conectarse.
///
/// El archivo está en formato NBT sin comprimir, el mismo que usa Minecraft para los
/// mundos. Acá se lee y se escribe solo lo que hace falta: el nombre y la dirección
/// de cada servidor, respetando los que la persona haya agregado por su cuenta.
/// </summary>
public static class ServerList
{
    private const byte TagEnd = 0;
    private const byte TagByte = 1;
    private const byte TagInt = 3;
    private const byte TagString = 8;
    private const byte TagList = 9;
    private const byte TagCompound = 10;

    /// <summary>
    /// Deja el servidor en la lista, primero y aceptando su resource pack. Si ya está
    /// pero le falta lo segundo, lo corrige; si hay otros servidores, los conserva.
    /// Devuelve true cuando tuvo que tocar el archivo.
    ///
    /// Se revisa en cada JUGAR y no solo la primera vez, porque el que ya tenía el
    /// servidor cargado de antes también tiene que dejar de ver el cartel del pack.
    /// </summary>
    public static bool Ensure(string path, SavedServer server)
    {
        var existing = Read(path);
        var mismo = existing.FirstOrDefault(
            s => string.Equals(s.Address, server.Address, StringComparison.OrdinalIgnoreCase));

        if (mismo is not null && mismo.AcceptTextures == server.AcceptTextures) return false;

        // El servidor de la comunidad va primero: es el que van a usar.
        var updated = new List<SavedServer> { server };
        updated.AddRange(existing.Where(s => !ReferenceEquals(s, mismo)));

        Write(path, updated);
        return true;
    }

    public static List<SavedServer> Read(string path)
    {
        if (!File.Exists(path)) return [];

        try
        {
            return Parse(File.ReadAllBytes(path));
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or ArgumentOutOfRangeException)
        {
            // Un archivo ilegible se reemplaza: es una lista de accesos, no datos del jugador.
            return [];
        }
    }

    public static void Write(string path, IEnumerable<SavedServer> servers)
    {
        var body = new List<byte>();

        // Raíz: un compound sin nombre.
        body.Add(TagCompound);
        WriteName(body, "");

        // Dentro, la lista "servers" de compounds.
        body.Add(TagList);
        WriteName(body, "servers");

        var list = servers.ToList();
        body.Add(TagCompound);
        WriteInt(body, list.Count);

        foreach (var server in list)
        {
            body.Add(TagString);
            WriteName(body, "ip");
            WriteText(body, server.Address);

            body.Add(TagString);
            WriteName(body, "name");
            WriteText(body, server.Name);

            // Solo se escribe cuando es true. Ausente significa "preguntar", que es lo
            // que corresponde para los servidores que la persona agregó por su cuenta.
            if (server.AcceptTextures)
            {
                body.Add(TagByte);
                WriteName(body, "acceptTextures");
                body.Add(1);
            }

            body.Add(TagEnd);
        }

        body.Add(TagEnd);

        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllBytes(path, body.ToArray());
    }

    // ------------------------------------------------------------------ Lectura

    private static List<SavedServer> Parse(byte[] data)
    {
        var found = new List<SavedServer>();
        var at = 0;

        if (Read1(data, ref at) != TagCompound) throw new InvalidDataException("No es un servers.dat.");
        SkipName(data, ref at);
        ReadCompound(data, ref at, found);

        return found;
    }

    private static void ReadCompound(byte[] data, ref int at, List<SavedServer> found)
    {
        while (true)
        {
            var type = Read1(data, ref at);
            if (type == TagEnd) return;

            var name = ReadText(data, ref at);

            if (type == TagList && name == "servers")
            {
                ReadServers(data, ref at, found);
                continue;
            }

            SkipPayload(data, ref at, type);
        }
    }

    private static void ReadServers(byte[] data, ref int at, List<SavedServer> found)
    {
        var elementType = Read1(data, ref at);
        var count = ReadInt(data, ref at);

        for (var i = 0; i < count; i++)
        {
            if (elementType != TagCompound)
            {
                SkipPayload(data, ref at, elementType);
                continue;
            }

            string? name = null;
            string? ip = null;
            var acceptTextures = false;

            while (true)
            {
                var type = Read1(data, ref at);
                if (type == TagEnd) break;

                var field = ReadText(data, ref at);

                if (type == TagString && field == "name") name = ReadText(data, ref at);
                else if (type == TagString && field == "ip") ip = ReadText(data, ref at);
                // Se lee para no pisárselo a los servidores que agregó la persona:
                // reescribir el archivo sin esto los devolvería a "preguntar".
                else if (type == TagByte && field == "acceptTextures") acceptTextures = Read1(data, ref at) != 0;
                else SkipPayload(data, ref at, type);
            }

            if (!string.IsNullOrEmpty(ip)) found.Add(new SavedServer(name ?? ip, ip, acceptTextures));
        }
    }

    private static void SkipPayload(byte[] data, ref int at, byte type)
    {
        switch (type)
        {
            case TagByte: at += 1; break;
            case 2: at += 2; break;                 // short
            case TagInt: at += 4; break;
            case 4: at += 8; break;                 // long
            case 5: at += 4; break;                 // float
            case 6: at += 8; break;                 // double
            case 7: Advance(ref at, ReadInt(data, ref at)); break;          // byte array
            case TagString: Advance(ref at, ReadUShort(data, ref at)); break;
            case TagList:
            {
                var element = Read1(data, ref at);
                var count = ReadInt(data, ref at);
                for (var i = 0; i < count; i++) SkipPayload(data, ref at, element);
                break;
            }
            case TagCompound:
            {
                while (true)
                {
                    var inner = Read1(data, ref at);
                    if (inner == TagEnd) break;
                    SkipName(data, ref at);
                    SkipPayload(data, ref at, inner);
                }
                break;
            }
            case 11: Advance(ref at, ReadInt(data, ref at) * 4); break;     // int array
            case 12: Advance(ref at, ReadInt(data, ref at) * 8); break;     // long array
            default: throw new InvalidDataException($"Etiqueta desconocida: {type}");
        }
    }

    private static byte Read1(byte[] data, ref int at) => data[at++];

    private static ushort ReadUShort(byte[] data, ref int at)
    {
        var value = BinaryPrimitives.ReadUInt16BigEndian(data.AsSpan(at));
        at += 2;
        return value;
    }

    private static int ReadInt(byte[] data, ref int at)
    {
        var value = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(at));
        at += 4;
        return value;
    }

    private static string ReadText(byte[] data, ref int at)
    {
        var length = ReadUShort(data, ref at);
        var text = Encoding.UTF8.GetString(data, at, length);
        at += length;
        return text;
    }

    private static void SkipName(byte[] data, ref int at) => Advance(ref at, ReadUShort(data, ref at));

    /// <summary>
    /// Avanza la posición. Existe porque escribir "at += Leer(ref at)" no funciona:
    /// C# toma el valor de at antes de llamar al lector, así que descarta lo que el
    /// lector ya avanzó y la posición retrocede.
    /// </summary>
    private static void Advance(ref int at, int amount) => at += amount;

    // ------------------------------------------------------------------ Escritura

    private static void WriteName(List<byte> target, string name) => WriteText(target, name);

    private static void WriteText(List<byte> target, string text)
    {
        var bytes = Encoding.UTF8.GetBytes(text);
        target.Add((byte)(bytes.Length >> 8));
        target.Add((byte)(bytes.Length & 0xFF));
        target.AddRange(bytes);
    }

    private static void WriteInt(List<byte> target, int value)
    {
        target.Add((byte)(value >> 24));
        target.Add((byte)(value >> 16));
        target.Add((byte)(value >> 8));
        target.Add((byte)value);
    }
}
