import os
import zipfile
import subprocess
import sys

PROJECT_DIR = "/storage/emulated/0/projeto_apk"
REPO_NAME = "navegador-apk-v2"

def run_cmd(command, cwd=PROJECT_DIR):
    print(f"-> Executando: {command}")
    result = subprocess.run(command, shell=True, cwd=cwd, text=True, capture_output=True)
    if result.returncode != 0:
        print(f"Erro: {result.stderr.strip()}")
    else:
        if result.stdout.strip():
            print(result.stdout.strip())
    return result.returncode == 0

def processar_arquivos_zip():
    print("🔍 Procurando arquivos ZIP na pasta do projeto...")
    arquivos_zip = [f for f in os.listdir(PROJECT_DIR) if f.endswith('.zip')]
    
    if not arquivos_zip:
        print("ℹ️ Nenhum arquivo ZIP novo encontrado. Usando os arquivos já presentes na pasta.")
        return

    for arquivo_zip in arquivos_zip:
        caminho_zip = os.path.join(PROJECT_DIR, arquivo_zip)
        print(f"📦 Encontrado o arquivo ZIP: {arquivo_zip}. Lendo e descompactando...")
        
        try:
            with zipfile.ZipFile(caminho_zip, 'r') as zip_ref:
                zip_ref.extractall(PROJECT_DIR)
                print(f"✔ Sucesso ao descompactar {arquivo_zip}!")
        except Exception as e:
            print(f"⚠️ Erro ao descompactar o arquivo {arquivo_zip}: {e}")

def main():
    print("🚀 Iniciando automação inteligente do projeto Android...")
    
    if not os.path.exists(PROJECT_DIR):
        os.makedirs(PROJECT_DIR, exist_ok=True)
        print(f"✔ Pasta criada: {PROJECT_DIR}")

    os.chdir(PROJECT_DIR)

    # 1. Identificar, ler e descompactar arquivos ZIP automaticamente
    processar_arquivos_zip()

    # 2. Garantir a existência do buildozer.spec
    spec_path = os.path.join(PROJECT_DIR, "buildozer.spec")
    if not os.path.exists(spec_path):
        print("📦 Criando arquivo buildozer.spec padrão...")
        with open(spec_path, "w", encoding="utf-8") as f:
            f.write("""[app]
title = Navegador
package.name = navegador
package.domain = org.brancofx
source.include_exts = py,png,jpg,kv,atlas
source.dir = .
version = 1.0
requirements = python3,kivy
orientation = portrait
fullscreen = 0
android.permissions = INTERNET
""")
        print("✔ Arquivo buildozer.spec criado com sucesso!")

    # 3. Garantir a existência do workflow do GitHub Actions
    workflow_dir = os.path.join(PROJECT_DIR, ".github", "workflows")
    workflow_path = os.path.join(workflow_dir, "build.yml")
    if not os.path.exists(workflow_path):
        os.makedirs(workflow_dir, exist_ok=True)
        print("📦 Criando configuração otimizada do GitHub Actions...")
        with open(workflow_path, "w", encoding="utf-8") as f:
            f.write("""name: Compilar APK Android

on:
  workflow_dispatch:
  push:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-22.04
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java
      uses: actions/setup-java@v5
      with:
        distribution: 'temurin'
        java-version: '17'

    - name: Set up Python
      uses: actions/setup-python@v5
      with:
        python-version: '3.10'

    - name: Dependências do Sistema
      run: |
        sudo dpkg --add-architecture i386
        sudo apt-get update
        sudo apt-get install -y \\
          build-essential \\
          git \\
          zip \\
          unzip \\
          wget \\
          curl \\
          autoconf \\
          libtool \\
          pkg-config \\
          zlib1g-dev \\
          libncurses5:i386 \\
          libstdc++6:i386 \\
          libz1:i386 \\
          libffi-dev \\
          libssl-dev

    - name: Instalar Buildozer e Dependências Python
      run: |
        python -m pip install --upgrade pip
        pip install --upgrade cython virtualenv
        pip install --upgrade buildozer

    - name: Compilar APK com Buildozer
      env:
        HOME: ${{ github.workspace }}
      run: |
        buildozer -v --debug android debug

    - name: Salvar APK Gerado
      uses: actions/upload-artifact@v4
      with:
        name: meu-aplicativo-apk
        path: bin/*.apk
""")
        print("✔ Configuração do GitHub Actions criada com sucesso!")

    # 4. Inicializar Git e configurar repositório remoto
    if not os.path.exists(os.path.join(PROJECT_DIR, ".git")):
        run_cmd("git init")
        run_cmd("git branch -M main")

    print("🌐 Configurando repositório no GitHub...")
    run_cmd(f"git remote remove origin 2>/dev/null || true")
    run_cmd(f"git remote add origin https://github.com/brancofx/{REPO_NAME}.git")

    # 5. Adicionar, commitar e enviar arquivos
    run_cmd("git add .")
    run_cmd('git commit -m "Automacao completa via script Python com leitura de ZIP" || echo "Nada para commitar"')
    
    print("🚀 Enviando código para o GitHub...")
    if not run_cmd("git push -u origin main --force"):
        run_cmd("git push -u origin master --force")

    print("\n" + "="*40)
    print("🎯 Processo finalizado!")
    print("Deseja acompanhar a compilação do APK na nuvem agora?")
    print("="*40)
    
    opcao = input("Acompanhar build no GitHub Actions? (s/n): ").strip().lower()
    if opcao == 's':
        print("\n⏳ Abrindo monitoramento do GitHub Actions...")
        os.system("gh run watch")
    else:
        print("\n✔ Tudo pronto! Você pode acompanhar o progresso no GitHub a qualquer momento.")

if __name__ == "__main__":
    main()
