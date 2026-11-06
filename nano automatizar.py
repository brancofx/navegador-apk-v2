import os
import subprocess
import sys

DIRETORIO_PROJETO = "/storage/emulated/0/projeto_apk"
os.makedirs(DIRETORIO_PROJETO, exist_ok=True)
os.chdir(DIRETORIO_PROJETO)

NOME_REPOSITORIO = "meu-navegador-apk"

def rodar_comando(comando):
    print(f"-> Executando: {comando}")
    resultado = subprocess.run(comando, shell=True, text=True)
    if resultado.returncode != 0:
        print(f"Erro ao executar: {comando}")
        sys.exit(1)

def criar_buildozer_spec():
    content = """[app]
title = Navegador Automacao
package.name = navegadorautomacao
package.domain = org.automacao
source.include_exts = py,png,jpg,kv,atlas
source.main = navegador.py
requirements = python3,kivy
version = 0.1
android.permissions = INTERNET
orientation = portrait

[buildozer]
log_level = 2
"""
    with open("buildozer.spec", "w", encoding="utf-8") as f:
        f.write(content)
    print("✔ Arquivo buildozer.spec criado com sucesso!")

def criar_workflow_github():
    os.makedirs(".github/workflows", exist_ok=True)
    content = """name: Compilar APK Android

on:
  workflow_dispatch:
  push:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-python@v5
      with:
        python-version: '3.10'
    - name: Dependencias do Sistema
      run: |
        sudo apt-get update
        sudo apt-get install -y git zip unzip openjdk-17-jdk python3-pip autoconf libtool pkg-config zlib1g-dev libncurses5-dev libncursesw5-dev libtinfo5 cmake libffi-dev libssl-dev
    - name: Compilar com Buildozer
      run: |
        pip install --upgrade pip cython buildozer
        buildozer -v android debug
    - name: Salvar APK Gerado
      uses: actions/upload-artifact@v4
      with:
        name: meu-aplicativo-apk
        path: bin/*.apk
"""
    with open(".github/workflows/build.yml", "w", encoding="utf-8") as f:
        f.write(content)
    print("✔ Configuração do GitHub Actions criada com sucesso!")

def main():
    if not os.path.exists("navegador.py"):
        print(f"❌ Erro: O arquivo 'navegador.py' não foi encontrado em: {DIRETORIO_PROJETO}")
        return

    print("🚀 Iniciando automação completa para gerar o APK...")
    criar_buildozer_spec()
    criar_workflow_github()

    # Configuração segura do Git para armazenamento externo
    subprocess.run("git config --global --add safe.directory /storage/emulated/0/projeto_apk", shell=True)

    if not os.path.exists(".git"):
        rodar_comando("git init")
        rodar_comando("git branch -M main")

    print("📦 Criando o repositório no GitHub via API...")
    subprocess.run(f"gh repo create {NOME_REPOSITORIO} --public --confirm", shell=True)

    rodar_comando("git add .")
    rodar_comando('git commit -m "Automacao de geracao de APK"')
    subprocess.run(f"gh repo set-default {NOME_REPOSITORIO}", shell=True)
    rodar_comando("git push -u origin main --force")

    print("\n🎉 Tudo pronto! Os arquivos foram enviados e o APK está sendo gerado na nuvem.")
    print("💡 Para acompanhar o andamento em tempo real direto do terminal, digite:")
    print("   gh run watch")

if __name__ == "__main__":
    main()
