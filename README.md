# Cloud Drive S3

<p align="center">
  <strong>Aplicativo Android de armazenamento em nuvem pessoal usando Amazon S3</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" alt="Kotlin 1.9" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/AWS%20SDK-Kotlin%201.0-orange?logo=amazonaws" alt="AWS SDK" />
  <img src="https://img.shields.io/badge/ExoPlayer-Media3%201.2-red" alt="ExoPlayer" />
  <img src="https://img.shields.io/badge/Criptografia-AES--256--GCM-darkgreen" alt="AES-256-GCM" />
</p>

---

## Sobre o Projeto

**Cloud Drive S3** transforma um bucket Amazon S3 no seu drive pessoal de nuvem. O app permite enviar fotos, videos, audios e qualquer arquivo do celular para o S3, navegar pelos arquivos armazenados, previsualiza-los diretamente no dispositivo e baixa-los de volta — tudo com criptografia ponta-a-ponta opcional e uma interface moderna em Material Design 3.

---

## Funcionalidades

### Gerenciamento de Arquivos

| Funcionalidade | Descricao |
|---|---|
| **Upload de arquivos** | Envie qualquer tipo de arquivo para o bucket S3 |
| **Upload multiplo** | Selecione varios arquivos de uma so vez para enviar |
| **Upload via compartilhamento** | Compartilhe arquivos diretamente de outros apps (Share Intent) |
| **Download de arquivos** | Baixe arquivos do S3 para a pasta `Downloads/CloudDriveS3/` |
| **Criar pastas** | Organize seus arquivos criando pastas diretamente no app |
| **Excluir arquivos** | Remova arquivos do bucket com confirmacao de seguranca |
| **Navegacao por pastas** | Navegue pela hierarquia de pastas dentro do bucket |

### Visualizacao de Arquivos

| Funcionalidade | Descricao |
|---|---|
| **Preview de imagens** | Visualize imagens com zoom e arrastar (pinch-to-zoom, double-tap) |
| **Galeria de imagens** | Deslize entre imagens da mesma pasta (swipe horizontal) |
| **Preview de videos** | Reproduza videos com controles nativos (ExoPlayer/Media3) |
| **Preview de audio** | Reproduza audio com interface customizada (play/pause, seek, duracao) |
| **Preview de PDF** | Visualize PDFs com navegacao por paginas (PdfRenderer nativo) |
| **Icones por tipo** | Icones distintos para imagens, videos, audios, documentos e outros |
| **Miniaturas** | Thumbnails de imagens carregados diretamente do S3 (via Coil) |
| **Modo grade/lista** | Alterne entre visualizacao em grade ou lista |

### Transferencias

| Funcionalidade | Descricao |
|---|---|
| **Fila de transferencias** | Gerencie uploads e downloads em fila com estado individual |
| **Progresso em tempo real** | Acompanhe o progresso de cada transferencia em porcentagem |
| **Transferencias em paralelo** | Ate 3 transferencias simultaneas (semaforo configuravel) |
| **Retry automatico** | Reenvio automatico com backoff exponencial em caso de falha |
| **Upload multipart** | Arquivos grandes enviados em partes para maior confiabilidade |
| **Transferencias em segundo plano** | Uploads e downloads continuam mesmo ao sair do app (Foreground Service) |
| **Notificacoes de progresso** | Acompanhe o status das transferencias pela barra de notificacoes |

### Seguranca

| Funcionalidade | Descricao |
|---|---|
| **Criptografia AES-256-GCM** | Arquivos criptografados antes do upload, descriptografados apos o download |
| **Chave por perfil** | Cada perfil AWS tem sua propria chave de criptografia |
| **Autenticacao biometrica** | Proteja o acesso ao app com impressao digital ou Face ID |
| **Armazenamento seguro** | Credenciais salvas com EncryptedSharedPreferences |
| **HTTPS obrigatorio** | Comunicacao sempre criptografada em transito |
| **Multiplos perfis** | Gerencie diferentes configuracoes AWS com troca rapida |

### Interface

| Funcionalidade | Descricao |
|---|---|
| **Tema claro/escuro/sistema** | Troque o tema manualmente ou sincronize com o sistema |
| **Dynamic Colors** | Paleta de cores adaptada automaticamente ao papel de parede (Android 12+) |
| **Badge de transferencias** | Contador de transferencias ativas na barra de navegacao |
| **Material Design 3** | Interface moderna com componentes e gestos do Material 3 |

---

## Arquitetura

O projeto segue uma arquitetura em camadas com separacao clara de responsabilidades:

```
com.clouddrive/
|
|-- CloudDriveApp.kt              # Application class
|-- MainActivity.kt               # Entry point, Compose host, Share Intent handler
|
|-- crypto/                        # Camada de seguranca
|   |-- EncryptionManager.kt      # AES-256-GCM: gera chave, encripta, decripta
|
|-- s3/                            # Camada de dados (AWS S3)
|   |-- S3Config.kt               # Data class: accessKeyId, secretKey, region, bucket
|   |-- S3ClientProvider.kt       # Singleton com cache do S3Client
|   |-- S3Repository.kt           # CRUD: list, upload, download, delete, headObject
|   |-- SettingsManager.kt        # DataStore: credenciais, perfis, criptografia
|
|-- transfer/                      # Camada de fila de transferencias
|   |-- TransferItem.kt           # Modelo de item na fila (estado, progresso, tipo)
|   |-- TransferManager.kt        # Singleton: fila, semaforo, retry, criptografia
|   |-- RetryPolicy.kt            # Backoff exponencial com jitter
|
|-- service/                       # Camada de servicos (Background)
|   |-- TransferService.kt        # Foreground Service para notificacoes e background
|
|-- ui/                            # Camada de apresentacao (Jetpack Compose)
    |-- FileListScreen.kt         # Tela principal: listagem, navegacao, acoes
    |-- SettingsScreen.kt         # Configuracao AWS, perfis, biometria, criptografia
    |-- TransferQueueScreen.kt    # Fila de transferencias com progresso
    |-- ImagePreviewDialog.kt     # Preview de imagem individual com zoom/pan
    |-- ImageGalleryDialog.kt     # Galeria: swipe entre imagens da pasta
    |-- VideoPreviewDialog.kt     # Preview de video com ExoPlayer
    |-- AudioPreviewDialog.kt     # Preview de audio com UI customizada
    |-- PdfPreviewDialog.kt       # Preview de PDF com PdfRenderer
    |-- ZoomableImage.kt          # Composable reutilizavel de zoom/pan
    |-- S3ImageFetcher.kt         # Fetcher do Coil para imagens direto do S3
    |-- theme/
        |-- Theme.kt              # Material 3 + Dynamic Colors
```

### Fluxo de Dados

```
[Acao do usuario]
      |
      v
[FileListScreen] -----> [TransferManager] -----> [EncryptionManager]
      |                        |                        |
      |                        | (dados encriptados)    |
      |                        v                        |
      |                 [S3Repository] <---------------'
      |                        |
      |                  [S3ClientProvider]
      |                        |
      |                   [Amazon S3]
      |
      | (preview)
      v
[ImageGalleryDialog / VideoPreviewDialog / AudioPreviewDialog / PdfPreviewDialog]
```

### Fluxo de Criptografia

```
Upload:
  Arquivo (bytes)
    -> EncryptionManager.encrypt()
    -> [IV (12 bytes) | Ciphertext | GCM Tag (128 bits)]
    -> S3Repository.upload() com metadata { "encrypted": "true" }

Download:
  S3Repository.headObject() -> verifica metadata "encrypted"
    -> EncryptionManager.decrypt([IV | Ciphertext])
    -> bytes originais
```

---

## Pre-requisitos

- **JDK 17**
- **Android SDK** (via Android Studio ou command-line tools)
- **Conta AWS** com um bucket S3 criado
- **Credenciais IAM** (Access Key ID + Secret Access Key) com permissoes no bucket

### Permissoes AWS necessarias (IAM Policy)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:HeadObject"
      ],
      "Resource": [
        "arn:aws:s3:::SEU-BUCKET-AQUI",
        "arn:aws:s3:::SEU-BUCKET-AQUI/*"
      ]
    }
  ]
}
```

---

## Como Executar

### 1. Clonar o repositorio

```bash
git clone https://github.com/jamessonlps/storage-cloud-drive.git
cd storage-cloud-drive
```

### 2. Configurar o Android SDK

#### Opcao A: Via terminal (sem Android Studio)

```bash
# macOS com Homebrew
brew install --cask android-commandlinetools

# Aceitar licencas
yes | sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" --licenses

# Instalar componentes necessarios
sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Crie o arquivo `local.properties` na raiz:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

#### Opcao B: Via Android Studio

Abra o projeto e aguarde a sincronizacao do Gradle.

### 3. Compilar

```bash
./gradlew assembleDebug
# APK gerado em: app/build/outputs/apk/debug/app-debug.apk
```

### 4. Instalar no dispositivo

#### Via ADB (sem Android Studio)

1. Ative **Depuracao USB** em Configuracoes > Opcoes do desenvolvedor
2. Conecte via USB no modo "Transferir arquivos"
3. Aceite a autorizacao de depuracao no celular

```bash
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
adb devices                                                      # verificar dispositivo
adb install app/build/outputs/apk/debug/app-debug.apk           # instalar
adb install -r app/build/outputs/apk/debug/app-debug.apk        # reinstalar
```

#### Via Gradle

```bash
./gradlew installDebug   # compila e instala automaticamente
```

### 5. Configurar o S3 no app

Na primeira execucao, preencha as credenciais na tela de configuracoes:

| Campo | Descricao | Exemplo |
|---|---|---|
| **Access Key ID** | Chave de acesso IAM | `AKIAIOSFODNN7EXAMPLE` |
| **Secret Access Key** | Chave secreta IAM | `wJalrXUtnFEMI/K7MDENG/...` |
| **Regiao** | Regiao AWS do bucket | `us-east-1` |
| **Nome do Bucket** | Nome do bucket S3 | `meu-drive-pessoal` |

---

## Uso do Aplicativo

### Navegacao

- **Tela principal**: lista arquivos e pastas do bucket
- **Entrar em pasta**: toque na pasta desejada
- **Voltar**: toque em `..` ou no botao Back
- **Atualizar**: icone de refresh na barra superior
- **Alternar grade/lista**: icone na barra superior

### Operacoes com Arquivos

| Acao | Como fazer |
|---|---|
| Upload de 1 arquivo | Botao `+` > selecionar arquivo |
| Upload de varios arquivos | Botao `+` > selecionar multiplos arquivos |
| Upload por compartilhamento | Compartilhar de outro app > selecionar Cloud Drive S3 |
| Download | Icone de download no arquivo |
| Excluir | Icone de lixeira > confirmar |
| Criar pasta | Icone de pasta na barra superior |
| Preview | Toque no arquivo (imagens, videos, audios, PDFs) |

### Galeria de Imagens

- Toque em qualquer imagem para abrir o preview
- Deslize horizontalmente para navegar entre imagens da pasta
- Pinch-to-zoom ou toque duplo para ampliar
- Quando ampliado, arraste para deslocar; quando em 1x, deslize para proxima imagem

### Preview de Video

- Reproduz diretamente no app com controles nativos do ExoPlayer
- Suporta os formatos suportados pelo codec do dispositivo (MP4, MKV, WebM, etc.)

### Preview de Audio

- Interface customizada com: play/pause, barra de seek, duracao, pular +-10s
- Icone colorido por formato (MP3, AAC, OGG, FLAC, etc.)

### Preview de PDF

- Renderiza via `PdfRenderer` nativo do Android
- Navegue entre paginas deslizando horizontalmente
- Indicador "Pagina X de Y" no topo

### Fila de Transferencias

- Acesse pelo icone na barra inferior (mostra badge com contagem ativa)
- Acompanhe progresso individual de cada transferencia
- Erros sao retentados automaticamente com backoff exponencial

### Criptografia

1. Va em **Configuracoes > Seguranca**
2. Ative **"Criptografia AES-256"**
3. Uma chave unica e gerada para o seu perfil e salva com seguranca
4. A partir dai, todos os uploads sao criptografados automaticamente
5. Downloads de arquivos marcados como criptografados sao descriptografados automaticamente

> Arquivos enviados sem criptografia continuam legíveis normalmente. A criptografia e retrocompativel.

---

## Stack Tecnologica

| Tecnologia | Versao | Uso |
|---|---|---|
| **Kotlin** | 1.9.22 | Linguagem principal |
| **Jetpack Compose** | BOM 2023.10.01 | Framework de UI declarativa |
| **Material 3** | Compose M3 | Design system e componentes |
| **AWS SDK for Kotlin** | 1.0.30 | Comunicacao com Amazon S3 |
| **Media3 / ExoPlayer** | 1.2.1 | Reproducao de video e audio |
| **DataStore Preferences** | 1.0.0 | Persistencia de configuracoes |
| **Security Crypto** | 1.1.0-alpha06 | EncryptedSharedPreferences para credenciais |
| **Biometric** | 1.1.0 | Autenticacao biometrica |
| **Coil** | 2.5.0 | Carregamento de imagens/thumbnails |
| **Kotlin Coroutines** | 1.7.3 | Operacoes assincronas e concorrencia |
| **Navigation Compose** | 2.7.6 | Navegacao entre telas |
| **javax.crypto** | Android built-in | AES-256-GCM (criptografia) |
| **PdfRenderer** | Android built-in | Renderizacao de PDFs |
| **Android Gradle Plugin** | 8.2.2 | Build system |
| **Gradle** | 8.5 | Gerenciador de dependencias |

---

## Seguranca

### Criptografia em repouso (AES-256-GCM)

- Algoritmo: AES-256-GCM (autenticado — garante confidencialidade e integridade)
- IV de 12 bytes gerado aleatoriamente por arquivo
- Layout do ciphertext: `[IV (12 bytes)][Ciphertext + GCM Tag (128 bits)]`
- Chave de 256 bits gerada com `KeyGenerator` e armazenada em `EncryptedSharedPreferences`
- Arquivo S3 recebe metadata `x-amz-meta-encrypted: true` para identificacao automatica

### Credenciais

- Salvas no `EncryptedSharedPreferences` (backed by Android Keystore)
- Nunca escritas em texto claro em disco ou logs
- Campo de senha com toggle de visibilidade para Secret Access Key

### Comunicacao

- `usesCleartextTraffic=false` no Manifest — apenas HTTPS
- AWS SDK usa TLS 1.2+ por padrao

> **Nota de producao**: considere **AWS Cognito** ou **STS (Security Token Service)** em vez de chaves IAM estaticas de longa duracao.

---

## Comandos de Build

```bash
# Build debug
./gradlew assembleDebug

# Build release (configure assinatura primeiro em app/build.gradle.kts)
./gradlew assembleRelease

# Compilar + instalar no dispositivo conectado
./gradlew installDebug

# Build completo com verificacoes
./gradlew build

# Lint
./gradlew lint

# Pular lint durante desenvolvimento
./gradlew assembleDebug -x lint

# Limpar artefatos
./gradlew clean
```

---

## Configuracao de Build

| Parametro | Valor |
|---|---|
| **Compile SDK** | 34 (Android 14) |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 34 |
| **Java Target** | JDK 17 |
| **Kotlin Compiler Extension** | 1.5.8 |
| **Gradle** | 8.5 |
| **Android Gradle Plugin** | 8.2.2 |

---

## Proximos Passos / Roadmap

### Alta prioridade

- [ ] **Compartilhar arquivos do S3** — gerar link pre-assinado com validade configuravel
- [ ] **Busca de arquivos** — filtrar por nome na pasta atual ou recursivamente
- [ ] **Renomear arquivos e pastas** — operacao de copia + delete no S3
- [ ] **Mover arquivos** — selecionar destino ou arrastar para outra pasta

### Media prioridade

- [ ] **Selecao multipla** — selecionar varios arquivos para download/exclusao em lote
- [ ] **Ordenacao e filtros** — ordenar por nome, tamanho, data; filtrar por tipo
- [ ] **Favoritos** — marcar arquivos/pastas para acesso rapido
- [ ] **Historico de transferencias** — log persistente de uploads e downloads

### Melhorias tecnicas

- [ ] **Testes unitarios** — cobertura de `S3Repository`, `TransferManager`, `EncryptionManager`
- [ ] **Testes instrumentados** — fluxos de UI com Compose Testing
- [ ] **ProGuard para release** — habilitar minificacao e otimizacao
- [ ] **Build de release assinado** — configurar keystore e signing config
- [ ] **AWS Cognito** — substituir chaves IAM estaticas por tokens temporarios

### Novas funcionalidades

- [ ] **Widget de upload** — enviar arquivos direto da tela inicial do Android
- [ ] **Auto-backup de fotos** — sincronizar a galeria automaticamente com o S3
- [ ] **Modo offline** — cache local dos arquivos visualizados recentemente
- [ ] **Compressao antes do upload** — reduzir tamanho de imagens e videos
- [ ] **Sincronizacao de pastas** — manter uma pasta local espelhada no S3

---

## Licenca

Este projeto e distribuido sob a licenca MIT. Veja o arquivo `LICENSE` para mais detalhes.
