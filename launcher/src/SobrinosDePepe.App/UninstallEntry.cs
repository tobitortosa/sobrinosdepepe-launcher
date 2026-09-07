using Microsoft.Win32;
using SobrinosDePepe.Core;

namespace SobrinosDePepe.App;

/// <summary>
/// La entrada de "Aplicaciones instaladas" de Windows.
///
/// Velopack la crea al instalar y la deja apuntando a su propio Update.exe, que desinstala
/// sin decir nada y sin mostrar nada. Acá se la reescribe para que apunte al launcher con
/// --uninstall: quien desinstale desde Windows ve nuestra pantalla, con el logo y una
/// explicación, y no una barra gris de sistema.
///
/// Se reescribe en cada arranque a propósito. Cada actualización de Velopack vuelve a
/// escribir la clave con su valor, así que alcanza con que el jugador abra el launcher una
/// vez después de actualizar para que quede como corresponde.
///
/// QuietUninstallString no se toca: es la que usan las herramientas que desinstalan sin
/// intervención, y ahí una ventana sería un estorbo. Ese camino queda limpio igual porque
/// Velopack avisa antes de borrar y ahí se hace la limpieza (ver Program.Main).
/// </summary>
public static class UninstallEntry
{
    private const string Key = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\SobrinosDePepe";

    public static void PointToLauncher()
    {
        try
        {
            var exe = Environment.ProcessPath;
            if (exe is null) return;

            // Si el .exe que corre no es el instalado, esto es una compilación de desarrollo.
            // Sin este control, abrir el proyecto desde Visual Studio dejaría el "Desinstalar"
            // de Windows apuntando a una carpeta de compilación que mañana no existe.
            if (!Path.GetFullPath(exe).StartsWith(
                    Path.GetFullPath(LauncherPaths.Root), StringComparison.OrdinalIgnoreCase))
                return;

            using var key = Registry.CurrentUser.OpenSubKey(Key, writable: true);
            if (key is null) return;

            var expected = $"\"{exe}\" --uninstall";
            if (key.GetValue("UninstallString") as string == expected) return;

            key.SetValue("UninstallString", expected);
        }
        catch (Exception)
        {
            // Sin permiso o con la clave borrada a mano queda el desinstalador de Velopack,
            // que hace el mismo trabajo sin la pantalla. No es motivo para no abrir.
        }
    }
}
