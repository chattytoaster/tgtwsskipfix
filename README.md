# TG TWS Skip Fix 🎧

**TG TWS Skip Fix** — LSPosed (Xposed) модуль для Android, исправляющий баг работы физических и сенсорных кнопок Bluetooth-гарнитур и TWS-наушников (Galaxy Buds, AirPods, Redmi Buds и др.) при прослушивании музыки в Telegram.

Позволяет полноценно использовать:
* **Одинарный тап** — Пауза / Возобновление (`Play / Pause`)
* **Двойной тап** — Следующий трек (`Next track / Skip`)
* **Тройной тап** — Предыдущий трек (`Previous track`)

---

## 🔍 В чём заключалась проблема

На прошивках **Xiaomi HyperOS, MIUI**, а также на ряде AOSP-прошивок при воспроизведении музыки в Telegram кнопки наушников либо полностью игнорировались, либо срабатывали только в YouTube и других системных плеерах.

### Технический корень бага:
1. Для обратной совместимости с Android 4.x Telegram вызывает устаревший API `AudioManager.registerRemoteControlClient` и `AudioManager.registerMediaButtonEventReceiver`.
2. Из-за этого системный класс Android `MediaSessionLegacyHelper` создаёт «теневую» сессию `MediaSessionHelper-org.telegram.messenger.web` со скудными флагами (`flags=2`, без `FLAG_HANDLES_MEDIA_BUTTONS` и с `mediaButtonReceiver = null`).
3. При этом настоящий нативный плеер Telegram (`telegramAudioPlayer`, `flags=3`) создаётся на более низком приоритете в стеке.
4. HyperOS перенаправляет все Bluetooth AVRCP сигналы гарнитуры в эту теневую сессию-заглушку, которая бесследно их «проглатывает», не давая коду Telegram обработать нажатия.

---

## 💡 Что делает модуль

* Блокирует вызовы устаревших методов `AudioManager.registerRemoteControlClient` и `registerMediaButtonEventReceiver`.
* Перехватывает любые попытки активации сессий `MediaSessionHelper`, принудительно оставляя их неактивными (`active=false`).
* Для настоящей сессии `telegramAudioPlayer` гарантирует флаги `flags=3` (`FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS`) и привязывает прямой `PendingIntent` к `org.telegram.messenger.MusicPlayerReceiver`.
* В результате сессия плеера Telegram становится единственным приоритетным обработчиком медиакнопок в системе.

---

## 🚀 Установка

1. Скачайте последний `.apk` из раздела [Releases](../../releases).
2. Установите APK на устройство.
3. Откройте **LSPosed Manager**.
4. В разделе **«Модули»** найдите **TG TWS Skip Fix** и включите его.
5. Убедитесь, что в области применения (scope) выбран **Telegram** (`org.telegram.messenger.web` или `org.telegram.messenger`).
6. Перезапустите Telegram (`Принудительно остановить` в настройках приложения или через свайп из недавних).

---

## 🛠 Сборка из исходников

Для сборки требуется установленный Android SDK (платформа `android-34` или новее) и JDK 17+:

```powershell
./build.ps1
```

Собранный и подписанный APK появится в папке `release/TG-TWS-Skip-Fix-v1.0.apk`.

---

## 📄 Лицензия

MIT License
