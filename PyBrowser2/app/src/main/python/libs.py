"""Gerenciamento das bibliotecas instaladas pelo usuário (pip --target)."""

import csv
import json
import os
import re
import shutil


def _meta(info_dir):
    name = version = None
    try:
        with open(os.path.join(info_dir, "METADATA"), encoding="utf-8", errors="replace") as f:
            for line in f:
                if not line.strip():
                    break
                if line.startswith("Name:"):
                    name = line[5:].strip()
                elif line.startswith("Version:"):
                    version = line[8:].strip()
    except OSError:
        pass
    return name, version


def _norm(name):
    return re.sub(r"[-_.]+", "-", name).lower()


def list_installed(package_dir):
    """Retorna JSON: [{"name": ..., "version": ...}, ...]"""
    out = []
    if os.path.isdir(package_dir):
        for entry in sorted(os.listdir(package_dir), key=str.lower):
            if entry.endswith(".dist-info"):
                name, version = _meta(os.path.join(package_dir, entry))
                if name:
                    out.append({"name": name, "version": version or "?"})
    return json.dumps(out)


def uninstall(name, package_dir):
    """Remove uma biblioteca usando o RECORD do dist-info. Só toca em arquivos dentro de package_dir."""
    target = _norm(name)
    root = os.path.realpath(package_dir)
    for entry in os.listdir(package_dir):
        if not entry.endswith(".dist-info"):
            continue
        info = os.path.join(package_dir, entry)
        found, _ = _meta(info)
        if not found or _norm(found) != target:
            continue
        removed, dirs = 0, set()
        record = os.path.join(info, "RECORD")
        if os.path.exists(record):
            with open(record, encoding="utf-8", errors="replace", newline="") as f:
                for row in csv.reader(f):
                    if not row or not row[0]:
                        continue
                    path = os.path.realpath(os.path.join(package_dir, row[0]))
                    if not path.startswith(root + os.sep):
                        continue
                    if os.path.isfile(path) or os.path.islink(path):
                        os.remove(path)
                        removed += 1
                        dirs.add(os.path.dirname(path))
        for d in sorted(dirs, key=len, reverse=True):
            shutil.rmtree(os.path.join(d, "__pycache__"), ignore_errors=True)
            while d != root and d.startswith(root + os.sep) and os.path.isdir(d) and not os.listdir(d):
                os.rmdir(d)
                d = os.path.dirname(d)
        shutil.rmtree(info, ignore_errors=True)
        return json.dumps({"removed": removed})
    raise ValueError("Biblioteca não encontrada: " + name)
