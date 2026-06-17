# cordova-plugin-kopere-media-notification

Plugin Cordova Android para exibir controles de mídia na notificação do sistema usando `MediaSession` + `MediaStyle`.

Ele foi criado para players HTML5 dentro de apps Cordova, como o `src/page/mod/supervideo/script.js` do Kopere APP Mobile.

## O que ele faz

- Mostra notificação de mídia do Android com título, curso/autor, capa, progresso e botões.
- Expõe ações da notificação para JavaScript: `play`, `pause`, `previous`, `next`, `seekto`, `stop` e `dismissed`.
- Usa `ForegroundService` com `foregroundServiceType="mediaPlayback"`.
- Cria `MediaSessionCompat` para integração com lock screen, controles Bluetooth/headset e área de mídia do Android.
- Carrega capa por URL `https://`, `file://` ou `content://`.

## Instalação

```bash
cordova plugin add ./cordova-plugin-kopere-media-notification
```

Ou, se estiver em outro repositório:

```bash
cordova plugin add https://github.com/EduardoKrausME/cordova-plugin-kopere-media-notification.git
```

## API JavaScript

```javascript
KopereMediaNotification.start(data, onAction, onError);
KopereMediaNotification.update(data, onSuccess, onError);
KopereMediaNotification.stop(onSuccess, onError);
KopereMediaNotification.hasPermission(onSuccess, onError);
KopereMediaNotification.requestPermission(onSuccess, onError);
```

## Campos aceitos

```javascript
{
    title: "Nome do vídeo",
    artist: "Nome do curso",
    album: "Moodle",
    artwork: "https://site.com/thumb.jpg",
    duration: 600000,
    position: 120000,
    playing: true,
    channelId: "kopere_media_playback",
    channelName: "Reprodução de mídia",
    color: "#2196F3",
    compactActions: [0, 1, 2]
}
```

`duration` e `position` devem ser enviados em milissegundos.

## Exemplo simples

```javascript
var video = document.querySelector("video");

KopereMediaNotification.start({
    title: "Aula 01",
    artist: "Curso de Moodle",
    artwork: "https://site.com/thumb.jpg",
    duration: video.duration * 1000,
    position: video.currentTime * 1000,
    playing: !video.paused
}, function(event) {
    if (event.action === "play") {
        video.play();
    } else if (event.action === "pause") {
        video.pause();
    } else if (event.action === "previous") {
        video.currentTime = Math.max(0, video.currentTime - 15);
    } else if (event.action === "next") {
        video.currentTime = Math.min(video.duration, video.currentTime + 15);
    } else if (event.action === "seekto") {
        video.currentTime = event.position / 1000;
    } else if (event.action === "stop") {
        video.pause();
        KopereMediaNotification.stop();
    }
});

setInterval(function() {
    KopereMediaNotification.update({
        duration: video.duration * 1000,
        position: video.currentTime * 1000,
        playing: !video.paused
    });
}, 1000);
```

## Exemplo para `src/page/mod/supervideo/script.js`

Veja `examples/supervideo-integration.js`.

## Observações

Este plugin não reproduz o vídeo dentro da notificação. A notificação mostra controles nativos, capa, título e progresso. Para mini vídeo flutuante, o recurso correto é Picture-in-Picture, que é separado deste plugin.

No Android 13+, o plugin possui a permissão `POST_NOTIFICATIONS` no Manifest e fornece `requestPermission`. Em notificações de mídia, o Android possui tratamento especial quando existe `MediaSession`, mas o app ainda pode chamar `requestPermission` para não depender do comportamento do fabricante.
