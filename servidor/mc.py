# -*- coding: utf-8 -*-
"""
Manda comandos y archivos al servidor por la API de Pterodactyl.

Las credenciales salen de web/.env.local, que no está en el repositorio.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

ENV = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "web", ".env.local")

cfg = {}
for line in open(ENV, encoding="utf-8"):
    if "=" in line and not line.startswith("#"):
        clave, valor = line.split("=", 1)
        cfg[clave.strip()] = valor.strip().strip('"')

LOG = "/logs/latest.log"

BASE = "%s/api/client/servers/%s" % (cfg["PTERODACTYL_URL"].rstrip("/"), cfg["PTERODACTYL_SERVER_ID"])
HEAD = {
    "Authorization": "Bearer " + cfg["PTERODACTYL_KEY"],
    "Accept": "application/json",
    "Content-Type": "application/json",
}


def call(path, data=None, raw=None, method=None):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    head = dict(HEAD)
    if raw is not None:
        head["Content-Type"] = "text/plain"
    peticion = urllib.request.Request(
        BASE + path, data=body, headers=head, method=method or ("POST" if body else "GET")
    )
    with urllib.request.urlopen(peticion) as respuesta:
        return respuesta.read().decode("utf-8", "replace")


def cmd(comando):
    """Ejecuta un comando en la consola del servidor."""
    call("/command", {"command": comando})


def responde(comando, espera=1.5):
    """
    Ejecuta un comando y devuelve lo que el servidor contesto, en lineas.

    La API del panel manda el comando y corta: no hay respuesta. Lo unico que
    queda es la consola, asi que se mira cuanto medía el log antes y se lee lo
    que aparecio despues. En este proyecto las conclusiones "por logica" ya
    fallaron tres veces; esto es para no tener que confiar.
    """
    antes = len(read(LOG).splitlines())
    cmd(comando)
    time.sleep(espera)
    lineas = read(LOG).splitlines()[antes:]
    return [l.split("]: ")[-1].strip() for l in lineas]


def write(ruta, texto):
    """Escribe un archivo del servidor, creando las carpetas que falten."""
    call("/files/write?file=" + urllib.parse.quote(ruta), raw=texto.encode("utf-8"))


def read(ruta):
    return call("/files/contents?file=" + urllib.parse.quote(ruta))


def download(ruta):
    """
    Baja un archivo del servidor tal cual, en bytes.

    read() no sirve para los binarios: decodifica a utf-8 y un .mca o un .dat
    queda destrozado. Y /files/contents corta en 4 MiB —un .mca de region lo pasa
    sin esfuerzo y contesta 400— asi que se pide la URL firmada del nodo, que es
    la misma que usa el boton de descargar del panel y no tiene tope.
    """
    firmada = json.loads(
        call("/files/download?file=" + urllib.parse.quote(ruta)))["attributes"]["url"]
    with urllib.request.urlopen(firmada) as respuesta:
        return respuesta.read()


def upload(carpeta, ruta_local):
    """
    Sube un archivo binario (un .jar) a una carpeta del servidor.

    write() no sirve para esto: manda el cuerpo como texto y un .jar queda
    corrupto. El panel da una URL firmada que apunta al nodo, y el archivo se
    manda ahi como multipart, sin la clave de la API.
    """
    firmada = json.loads(call("/files/upload"))["attributes"]["url"]
    destino = firmada + "&directory=" + urllib.parse.quote(carpeta)
    nombre = os.path.basename(ruta_local)
    borde = "----sobrinosdepepe" + os.urandom(8).hex()

    with open(ruta_local, "rb") as archivo:
        contenido = archivo.read()

    cuerpo = b"".join([
        ("--%s\r\n" % borde).encode(),
        ('Content-Disposition: form-data; name="files"; filename="%s"\r\n' % nombre).encode(),
        b"Content-Type: application/java-archive\r\n\r\n",
        contenido,
        ("\r\n--%s--\r\n" % borde).encode(),
    ])

    peticion = urllib.request.Request(
        destino,
        data=cuerpo,
        headers={"Content-Type": "multipart/form-data; boundary=" + borde},
        method="POST",
    )
    with urllib.request.urlopen(peticion) as respuesta:
        return respuesta.status


def ls(carpeta):
    """Los nombres de los archivos que hay en una carpeta del servidor."""
    datos = json.loads(call("/files/list?directory=" + urllib.parse.quote(carpeta)))
    return [item["attributes"]["name"] for item in datos["data"]]


def delete(carpeta, nombres):
    """Borra archivos de una carpeta del servidor."""
    call("/files/delete", {"root": carpeta, "files": nombres})


def power(senal):
    """start, stop, restart o kill."""
    call("/power", {"signal": senal})


if __name__ == "__main__":
    if sys.argv[1] == "cmd":
        cmd(" ".join(sys.argv[2:]))
    elif sys.argv[1] == "read":
        print(read(sys.argv[2]))
    elif sys.argv[1] == "power":
        power(sys.argv[2])
