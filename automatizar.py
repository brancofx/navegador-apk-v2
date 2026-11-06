import os
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

def main():
    print("🚀 Iniciando automação completa para o projeto Android...")
    
    if not os.path.exists(PROJECT_DIR):
        os.makedirs(PROJECT_DIR, exist_ok=True)
        print(f"✔ Pasta criada: {PROJECT_DIR}")

    os.chdir(PROJECT_DIR)

    # 1. Verificar se o buildozer.spec existe, senão criar um básico
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

    # 2. Verificar se o workflow do GitHub Actions existe
    workflow_dir = os.path.join(PROJECT_DIR, ".github", "workflows")
    workflow_path = os.path.join(workflow_dir, "build.yml")
    if not os.path.exists(workflow_path):
        os.makedirs(workflow_dir, exist_ok=True)
        print("📦 Criando configuração do GitHub Actions...")
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
        sudo apt-get install -y \
          build-essential \
          git \
          zip \
          unzip \
          wget \
          curl \
          autoconf \
          libtool \
          pkg-config \
          zlib1g-dev \
          libncurses5:i386 \
          libstdc++6:i386 \
          libz1:i386 \
          libffi-dev \
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

    # 3. Inicializar Git localmente se necessário
    if not os.path.exists(os.path.join(PROJECT_DIR, ".git")):
        run_cmd("git init")
        run_cmd("git branch -M main")

    # 4. Criar repositório no GitHub via GH CLI se não existir
    print("🌐 Verificando repositório no GitHub...")
    repo_check = subprocess.run(f"gh repo view brancofx/{REPO_NAME}", shell=True, capture_output=True)
    if repo_check.returncode != 0:
        print(f"📦 Criando repositório remoto '{REPO_NAME}'...")
        run_cmd(f"gh repo create {REPO_NAME} --public --source=. --remote=origin")
    else:
        print("✔ Repositório remoto já existe. Vinculando...")
        run_cmd(f"git remote remove origin 2>/dev/null || true")
        run_cmd(f"git remote add origin https://github.com/brancofx/{REPO_NAME}.git")

    # 5. Commit e Push dos arquivos
    run_cmd("git add .")
    run_cmd('git commit -m "Atualizacao automatica do projeto via script Python" || echo "Nada para commitar"')
    
    print("🚀 Enviando código para o GitHub...")
    if not run_cmd("git push -u origin main --force"):
        run_cmd("git push -u origin master --force")

    print("\n" + "="*40)
    print("🎯 O que você deseja fazer agora?")
    print("1. Apenas publicar o código no GitHub")
    print("2. Publicar e ACOMPANHAR a geração do APK na nuvem (GitHub Actions)")
    print("="*40)
    
    opcao = input("Digite o número da sua escolha (1 ou 2): ").strip()
    
    if opcao == "2":
        print("\n⏳ Abrindo monitoramento do GitHub Actions...")
        print("Dica: Pressione Ctrl+C para sair do monitoramento a qualquer momento.")
        os.system("gh run watch")
    else:
        print("\n✔ Processo concluído! Código publicado com sucesso no GitHub.")

if __name__ == "__main__":
    main()
