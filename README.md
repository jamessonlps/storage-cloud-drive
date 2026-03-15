# Cloud Drive S3

<p align="center">
  <strong>Aplicativo Android de armazenamento em nuvem pessoal usando Amazon S3</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" alt="Kotlin 1.9" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/AWS%20SDK-Kotlin%201.0-orange?logo=amazonaws" alt="AWS SDK" />
</p>

---

## Sobre o Projeto

**Cloud Drive S3** transforma um bucket Amazon S3 no seu drive pessoal de nuvem. O app permite enviar fotos, videos, audios e qualquer arquivo do celular para o S3, navegar pelos arquivos armazenados e baixa-los de volta para o dispositivo -- tudo com uma interface moderna em Material Design 3.

### Funcionalidades

| Funcionalidade | Descricao |
|---|---|
| **Upload de arquivos** | Envie qualquer tipo de arquivo do dispositivo para o bucket S3 |
| **Download de arquivos** | Baixe arquivos do S3 para a pasta Downloads do celular |
| **Navegacao por pastas** | Navegue pela hierarquia de pastas dentro do bucket |
| **Criar pastas** | Organize seus arquivos criando pastas diretamente pelo app |
| **Excluir arquivos** | Remova arquivos do bucket com confirmacao de seguranca |
| **Transferencias em segundo plano** | Uploads e downloads continuam mesmo ao sair do app (Foreground Service) |
| **Notificacoes de progresso** | Acompanhe o status das transferencias pela barra de notificacoes |
| **Icones por tipo** | Icones visuais distintos para imagens, videos, audios e documentos |
| **Tema claro/escuro** | Suporte automatico a tema escuro com Dynamic Colors (Android 12+) |
| **Configuracao segura** | Credenciais AWS salvas localmente com DataStore |

---

## Arquitetura

O projeto segue uma arquitetura baseada em camadas com separacao clara de responsabilidades:

```
com.clouddrive/
|
|-- MainActivity.kt              # Activity principal, ponto de entrada
|-- CloudDriveApp.kt             # Application class
|
|-- s3/                           # Camada de dados (AWS S3)
|   |-- S3Config.kt              # Data class de configuracao AWS
|   |-- S3ClientProvider.kt      # Singleton do cliente S3
|   |-- S3Repository.kt          # Operacoes CRUD no bucket
|   |-- SettingsManager.kt       # Persistencia de credenciais (DataStore)
|
|-- service/                      # Camada de servicos (Background)
|   |-- TransferService.kt       # Foreground Service para uploads/downloads
|
|-- ui/                           # Camada de apresentacao (Jetpack Compose)
    |-- FileListScreen.kt        # Tela principal - listagem e acoes
    |-- SettingsScreen.kt        # Tela de configuracao AWS
    |-- theme/
        |-- Theme.kt             # Definicao de cores e tema Material 3
```

### Diagrama de Fluxo

```
+------------------+       +-------------------+       +------------------+
|                  |       |                   |       |                  |
|  SettingsScreen  | ----> |  SettingsManager  | ----> |    DataStore     |
|  (Configuracao)  |       |  (Persistencia)   |       |  (Preferences)  |
|                  |       |                   |       |                  |
+------------------+       +-------------------+       +------------------+
                                    |
                                    | S3Config
                                    v
+------------------+       +-------------------+       +------------------+
|                  |       |                   |       |                  |
|  FileListScreen  | ----> | TransferService   | ----> |   S3Repository   |
|  (UI Principal)  |       | (Foreground Svc)  |       |   (Operacoes)    |
|                  |       |                   |       |                  |
+------------------+       +-------------------+       +------------------+
       ^                           |                           |
       | Broadcast                 | Notificacoes              | S3ClientProvider
       | (refresh)                 v                           v
+------------------+       +------------------+       +------------------+
|                  |       |                  |       |                  |
|  Device Storage  |       | NotificationMgr  |       |   Amazon S3      |
|  (Downloads/)    |       | (Progresso)      |       |   (Bucket)       |
|                  |       |                  |       |                  |
+------------------+       +------------------+       +------------------+
```

---

## Pre-requisitos

- **JDK 17**
- **Android SDK** (via Android Studio ou command-line tools)
- **Conta AWS** com um bucket S3 criado
- **Credenciais AWS** (Access Key ID e Secret Access Key) com permissoes no bucket

### Permissoes AWS necessarias (IAM Policy)

O usuario IAM precisa das seguintes permissoes no bucket:

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
        "s3:DeleteObject"
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

#### Opcao A: Sem Android Studio (via terminal)

Instale as ferramentas de linha de comando do Android:

```bash
# macOS (Homebrew)
brew install --cask android-commandlinetools

# Aceitar licencas
yes | sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" --licenses

# Instalar componentes necessarios
sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Crie o arquivo `local.properties` na raiz do projeto:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

#### Opcao B: Com Android Studio

Abra o projeto pelo Android Studio e aguarde a sincronizacao do Gradle. O SDK sera configurado automaticamente.

### 3. Compilar o APK

```bash
./gradlew assembleDebug
```

O APK sera gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Instalar no celular via USB (sem Android Studio)

1. **Ative a Depuracao USB** no celular:
   - Va em **Configuracoes > Sobre o telefone** e toque **7 vezes** em "Numero da versao"
   - Va em **Configuracoes > Opcoes do desenvolvedor** e ative **Depuracao USB**

2. **Conecte o celular via USB** e selecione o modo **"Transferir arquivos"**

3. **Aceite a autorizacao** de depuracao que aparecera na tela do celular

4. **Instale o APK**:

```bash
# Adicione o adb ao PATH (macOS com Homebrew)
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"

# Verifique se o celular foi detectado
adb devices

# Instale o app
adb install app/build/outputs/apk/debug/app-debug.apk
```

> Para reinstalar apos alteracoes, use `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### 5. Configurar o S3

Na primeira execucao, a tela de configuracoes sera exibida automaticamente. Preencha:

| Campo | Descricao | Exemplo |
|---|---|---|
| **Access Key ID** | Chave de acesso IAM | `AKIAIOSFODNN7EXAMPLE` |
| **Secret Access Key** | Chave secreta IAM | `wJalrXUtnFEMI/K7MDENG/...` |
| **Regiao** | Regiao AWS do bucket | `us-east-1` |
| **Nome do Bucket** | Nome do bucket S3 | `meu-drive-pessoal` |

---

## Uso do Aplicativo

### Tela Principal (File List)

A tela principal exibe todos os arquivos e pastas no bucket S3:

- **Navegar em pastas**: toque em uma pasta para entrar; use `..` para voltar
- **Atualizar lista**: toque no icone de refresh na barra superior
- **Ver detalhes**: cada arquivo mostra nome e tamanho formatado

### Upload de Arquivos

1. Toque no botao flutuante **+** (canto inferior direito)
2. Selecione qualquer arquivo do dispositivo
3. O arquivo sera enviado para a pasta atual no bucket
4. Uma notificacao confirmara o upload

### Download de Arquivos

1. Na lista de arquivos, toque no icone de **download** (nuvem com seta)
2. O arquivo sera salvo em `Downloads/CloudDriveS3/`
3. Uma notificacao mostrara o caminho do arquivo baixado

### Criar Pasta

1. Toque no icone de **nova pasta** na barra superior
2. Digite o nome da pasta
3. Toque em "Criar"

### Excluir Arquivo

1. Toque no icone de **lixeira** no arquivo desejado
2. Confirme a exclusao no dialogo

### Configuracoes

Acesse as configuracoes a qualquer momento pelo icone de **engrenagem** na barra superior para alterar credenciais AWS ou trocar de bucket.

---

## Stack Tecnologica

| Tecnologia | Versao | Uso |
|---|---|---|
| **Kotlin** | 1.9.22 | Linguagem principal |
| **Jetpack Compose** | BOM 2024.01 | Framework de UI declarativa |
| **Material 3** | Compose M3 | Design system e componentes |
| **AWS SDK for Kotlin** | 1.0.30 | Comunicacao com Amazon S3 |
| **DataStore Preferences** | 1.0.0 | Persistencia de configuracoes |
| **Coil** | 2.5.0 | Carregamento de imagens |
| **Kotlin Coroutines** | 1.7.3 | Operacoes assincronas |
| **Navigation Compose** | 2.7.6 | Navegacao entre telas |
| **Android Gradle Plugin** | 8.2.2 | Build system |
| **Gradle** | 8.5 | Gerenciador de dependencias |

---

## Estrutura de Arquivos

```
storage-cloud-drive/
|-- build.gradle.kts                    # Build raiz - plugins Kotlin e AGP
|-- settings.gradle.kts                 # Configuracao de repositorios e modulos
|-- gradle.properties                   # Propriedades do Gradle (JVM, AndroidX)
|-- .gitignore                          # Arquivos ignorados pelo Git
|-- gradlew                            # Script Gradle wrapper (Linux/macOS)
|-- gradlew.bat                        # Script Gradle wrapper (Windows)
|-- gradle/
|   |-- wrapper/
|       |-- gradle-wrapper.jar         # Gradle wrapper JAR
|       |-- gradle-wrapper.properties  # Versao do Gradle (8.5)
|-- app/
    |-- build.gradle.kts                # Build do modulo app - dependencias
    |-- proguard-rules.pro              # Regras ProGuard para AWS SDK
    |-- src/main/
        |-- AndroidManifest.xml         # Permissoes e declaracao de componentes
        |-- java/com/clouddrive/
        |   |-- CloudDriveApp.kt        # Application class
        |   |-- MainActivity.kt         # Activity com Compose
        |   |-- s3/
        |   |   |-- S3Config.kt         # Modelo de configuracao (4 campos)
        |   |   |-- S3ClientProvider.kt # Singleton com cache do S3Client
        |   |   |-- S3Repository.kt     # CRUD: list, upload, download, delete
        |   |   |-- SettingsManager.kt  # DataStore para salvar credenciais
        |   |-- service/
        |   |   |-- TransferService.kt  # Foreground Service para transferencias
        |   |-- ui/
        |       |-- FileListScreen.kt   # Tela principal (~320 linhas)
        |       |-- SettingsScreen.kt   # Formulario de configuracao AWS
        |       |-- theme/
        |           |-- Theme.kt        # Tema Material 3 + Dynamic Colors
        |-- res/
            |-- drawable/
            |   |-- ic_launcher_background.xml  # Fundo do icone (azul)
            |   |-- ic_launcher_foreground.xml  # Icone do app (nuvem + seta)
            |-- mipmap-anydpi-v26/
            |   |-- ic_launcher.xml     # Adaptive icon definition
            |-- values/
            |   |-- strings.xml         # Strings do app
            |   |-- themes.xml          # Tema base XML
            |-- xml/
                |-- file_paths.xml      # FileProvider paths para downloads
```

---

## Seguranca

- **HTTPS obrigatorio**: `usesCleartextTraffic=false` no Manifest impede trafego nao criptografado
- **Credenciais locais**: salvas no DataStore do Android (armazenamento privado do app)
- **Campo de senha**: Secret Access Key usa `PasswordVisualTransformation` com toggle de visibilidade
- **Sem hardcoding**: nenhuma credencial e armazenada no codigo-fonte

> **Importante**: para producao, considere usar **AWS Cognito** ou **STS (Security Token Service)** em vez de chaves IAM estaticas. Chaves de longa duracao no dispositivo representam risco se o aparelho for comprometido.

---

## Compilacao

### Build de Debug

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools  # se nao estiver configurado
./gradlew assembleDebug
```

O APK sera gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Build de Release

```bash
./gradlew assembleRelease
```

> Nota: para builds de release, configure a assinatura do APK em `app/build.gradle.kts`.

### Instalar diretamente no dispositivo

```bash
./gradlew installDebug   # compila e instala via ADB automaticamente
```

---

## Licenca

Este projeto e distribuido sob a licenca MIT. Veja o arquivo `LICENSE` para mais detalhes.
