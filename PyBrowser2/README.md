# PyBrowser 2.0 (código-fonte)

Navegador Android com terminal Python embutido para automatizar páginas web.
Esta versão reconstrói o PyBrowser 1.0 e acrescenta as melhorias pedidas.

> **Status honesto:** o projeto **ainda não foi compilado**. Foi escrito num ambiente sem Android SDK.
> Testei de verdade apenas: o código Python (`runner.py`, `libs.py`), a sintaxe dos JS, o XML e o balanceamento do Kotlin.
> O Kotlin/Compose e o JS de inspeção em página real **não foram executados**. Pode haver erros de compilação pequenos
> na primeira tentativa. Se o build falhar, o log aponta a linha exata.

## Como gerar o APK

**Pelo celular/PC, sem instalar nada (GitHub Actions):**
1. Crie um repositório no GitHub e envie o conteúdo desta pasta (a pasta `.github` precisa ir junto).
2. Aba **Actions → Build APK → Run workflow**.
3. Ao terminar, baixe o artefato `PyBrowser-2.0-debug-apk`, descompacte e instale o `.apk`
   (permita "instalar de fontes desconhecidas"). O APK de debug já vem assinado com a chave de debug.

**Android Studio (Koala ou mais novo):** *File → Open* nesta pasta, aguarde o sync e rode `app`.
Ou, na linha de comando com Gradle 8.7+ e JDK 17: `gradle :app:assembleDebug`.

O `applicationId` é `br.com.pybrowser.v2`, então instala **ao lado** do PyBrowser 1.0 sem conflito de assinatura.

## O que foi adicionado

| Pedido | Onde está |
|---|---|
| Gerenciador de scripts | Aba **Scripts**: criar, abrir, renomear, duplicar, excluir (`Store.kt`, `ScriptsUi.kt`) |
| Editor com destaque de sintaxe | `PyHighlight` (comentários, strings, decoradores, números, palavras-chave, builtins), auto-indentação e barra de teclas rápidas |
| Instalação/gerenciamento de bibliotecas | Aba **Libs**: instalar via pip, listar com versão, remover (`libs.py`, `OtherUi.kt`) |
| Navegador com inspeção de elementos | Aba **Navegador** → *Inspecionar*: toque no elemento e veja tag, CSS, XPath, texto e atributos, com destaque na página (`inspector.js`) |
| XPath, CSS Selector e JavaScript | Painel *Testar seletor*: roda CSS, XPath ou JS na página, mostra contagem, amostra e destaca os elementos. Os scripts Python usam `By.XPATH`, `By.CSS_SELECTOR` e `driver.execute_script` |
| Tarefas em segundo plano | *⏳ Segundo plano* (uma vez) e *Agendar repetição* (mín. 15 min) via WorkManager com notificação (`Work.kt`) |
| Histórico de execuções | Aba **Histórico**: status, duração, modo, log completo e "Executar de novo" |
| Exportar/importar `.py` | Seletor de arquivos do Android (importa vários de uma vez; exporta pelo menu *⋮ Mais*) |
| Logs em tempo real | Aba **Logs**: saída do Python e `console.*` da página (`[js]`) ao vivo, botão **Parar**, copiar, terminal Python / `pip install` |
| Celulares e tablets | Largura ≥ 600 dp: barra lateral, lista + editor lado a lado, painel do inspetor ao lado da página. Abaixo disso: barra inferior e telas empilhadas |

## Limites do Android (importante)

- **Segundo plano não é garantido.** O Android 12+ pode negar serviço em primeiro plano a partir do segundo plano, e o
  Doze/economia de bateria atrasa ou pula execuções agendadas. O app tenta mostrar a notificação e, se for negada, roda sem ela.
  Para tarefas confiáveis, deixe o app aberto ou desative a otimização de bateria para o PyBrowser.
- A página roda numa WebView sem tela durante tarefas em segundo plano. Sites que exigem aba visível podem se comportar diferente.
- `pip` instala só *wheels* (`--only-binary`), então pacotes com código nativo costumam não funcionar.

## Diferenças em relação ao PyBrowser 1.0

Não havia código-fonte do 1.0 (só o APK), então a interface e a rede foram reescritas.
**Mantido:** navegação, API `navegador`/Selenium-like (`navegador.py`, `automacao.js`, `runner.py` inalterados), terminal, pip, VPN WireGuard (importar `.conf`, ligar/desligar), proxy `host:porta`.
**Não recriado:** importação de lista de proxies em TXT e DNS personalizado da VPN (use a linha `DNS =` do próprio `.conf`).
O exemplo agora abre `file:///android_asset/demo.html` (o domínio `.invalid` do original nunca carregava).

## Segurança

- A seleção do inspetor chega ao app por mensagem de console com um *nonce* secreto; nenhum objeto Java é exposto às páginas.
- O proxy não usa fallback direto: se cair, a navegação falha em vez de vazar pela conexão normal.
- Scripts Python têm acesso total ao conteúdo das páginas (formulários, senhas digitadas). Execute só o que você entende.
- `usesCleartextTraffic` continua ligado, como no original (permite páginas `http://`).

## Estrutura

```
app/src/main/
  python/   navegador.py runner.py (do 1.0)  libs.py (novo)
  assets/   automacao.js (do 1.0)  inspector.js (novo)  demo.html  exemplo.py  licenses/
  java/br/com/pybrowser/
    Engine.kt   WebView, ponte Python↔navegador, logs, histórico
    Store.kt    scripts, histórico, agendamentos
    Work.kt     segundo plano (WorkManager)       Net.kt   VPN e proxy
    MainActivity.kt  navegação adaptativa         ScriptsUi.kt / BrowserUi.kt / OtherUi.kt
```

Se o sync do Gradle reclamar de versões, os pontos a ajustar são `com.chaquo.python` (16.0.0), AGP (8.5.2) e Kotlin (2.0.20)
em `build.gradle.kts`, e `extractPackages("pip")` em `app/build.gradle.kts`.
