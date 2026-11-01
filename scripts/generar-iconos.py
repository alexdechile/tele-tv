#!/usr/bin/env python3
"""Dibuja el ícono y el banner de Tele TV.

En Android TV el lanzador no usa `android:icon` para la grilla de apps: usa el
**banner** 320×180. Tele no declaraba banner, así que el botón salía gris y
vacío en el launcher del KM6. Acá se generan los dos, con la identidad de la app
(fondo casi negro y el ámbar del foco, `PillFocus = #FFC24B`).

Uso:  scripts/generar-iconos.py
Salida:
  tv/src/main/res/mipmap-*/ic_launcher.png      (ícono cuadrado)
  tv/src/main/res/drawable/banner_tele.png      (banner del launcher)
  tv/src/main/res/drawable/icono_tele.png       (copia suelta, 192 px)
  docs/icono-tele.png, docs/banner-tele.png     (para mirarlos)
"""
from __future__ import annotations

import pathlib

from PIL import Image, ImageDraw, ImageFont

RAIZ = pathlib.Path(__file__).resolve().parent.parent
RES = RAIZ / "tv/src/main/res"
FUENTES = RAIZ / "scripts/fuentes"

NEGRO = (20, 20, 20)      # fondo, igual que el reproductor
NEGRO_UI = (10, 10, 10)
BLANCO = (255, 255, 255)
AMBAR = (255, 194, 75)    # PillFocus
GRIS = (158, 158, 158)    # StatusSecondary

DENSIDADES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def dibujar_icono(lado: int) -> Image.Image:
    img = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    radio = lado * 0.22
    d.rounded_rectangle([0, 0, lado - 1, lado - 1], radius=radio, fill=NEGRO)

    # Franja ámbar abajo: el acento de la app, como la franja del portal.
    franja = lado * 0.075
    d.rounded_rectangle([0, lado - franja * 1.6, lado, lado], radius=radio, fill=AMBAR)
    d.rectangle([0, lado - franja * 1.6, lado, lado - franja * 0.8], fill=NEGRO)
    d.rectangle([0, lado - franja * 0.8, lado, lado], fill=AMBAR)

    # Lupa: aro + mango (el gesto de la app es buscar).
    cx, cy = lado * 0.44, lado * 0.42
    r = lado * 0.19
    grosor = max(2, round(lado * 0.075))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=AMBAR, width=grosor)
    d.line(
        [(cx + r * 0.72, cy + r * 0.72), (cx + r * 1.75, cy + r * 1.75)],
        fill=AMBAR,
        width=grosor,
    )
    return img


def dibujar_banner() -> Image.Image:
    ancho, alto = 320, 180
    img = Image.new("RGB", (ancho, alto), NEGRO_UI)
    d = ImageDraw.Draw(img)

    d.rectangle([0, 0, ancho, 8], fill=AMBAR)

    # Lupa a la izquierda, como el ícono.
    cx, cy, r = 52, 92, 24
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=AMBAR, width=7)
    d.line([(cx + r * 0.7, cy + r * 0.7), (cx + r * 1.5, cy + r * 1.5)], fill=AMBAR, width=8)

    titulo = ImageFont.truetype(str(FUENTES / "inter_extrabold.ttf"), 52)
    sub = ImageFont.truetype(str(FUENTES / "inter_semibold.ttf"), 17)
    d.text((100, 52), "Tele TV", font=titulo, fill=BLANCO)
    d.text((103, 118), "Busca y reproduce", font=sub, fill=GRIS)
    return img


def main() -> None:
    for carpeta, lado in DENSIDADES.items():
        destino = RES / f"mipmap-{carpeta}" / "ic_launcher.png"
        destino.parent.mkdir(parents=True, exist_ok=True)
        dibujar_icono(lado).save(destino)
        print(f"  {destino.relative_to(RAIZ)} ({lado}px)")

    banner = RES / "drawable" / "banner_tele.png"
    dibujar_banner().save(banner)
    print(f"  {banner.relative_to(RAIZ)} (320x180)")

    copia = RES / "drawable" / "icono_tele.png"
    dibujar_icono(192).save(copia)
    print(f"  {copia.relative_to(RAIZ)} (192px)")

    docs = RAIZ / "docs"
    docs.mkdir(exist_ok=True)
    dibujar_icono(512).save(docs / "icono-tele.png")
    dibujar_banner().save(docs / "banner-tele.png")
    print(f"  docs/icono-tele.png, docs/banner-tele.png")


if __name__ == "__main__":
    main()
