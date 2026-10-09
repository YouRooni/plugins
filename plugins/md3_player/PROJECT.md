# MD3 Music Player

## Описание
Портированный и усовершенствованный полнофункциональный Material 3 (Material You) плеер музыки из exteraless для exteraGram:
* **PlayerSheet**: Полноэкранный Material Design 3 BottomSheet плеер с плавной анимацией открытия/закрытия, размытием фона, динамическими цветами, кнопкой добавления в профиль/избранное, скоростью воспроизведения, очередью и вызовом классического плеера.
* **PlayerMiniView**: Плавающее Material 3 мини-окно/пилюля в списке чатов с круглым анимированным индикатором прогресса и бесшовным морфингом (shared-element transition) в большой плеер.
* **PlayerBarView**: Компактный MD3-бар воспроизведения над чатом вместо стоковой полосы.
* **LyricsView & OnlineLyrics**: Автоматический поиск и отображение синхронизированных текстов песен онлайн через LRCLIB.
* **WavySeekBar**: Анимированный волнистый сикбар воспроизведения Android 13/14+.
* **PlayerColors**: Умная генерация палитры (Google Material Color Utilities Monet / Celebi / Score). Исправлена проблема оригинала с синим оттенком в монохромных темах — теперь плеер поддерживает чистую монохромную палитру и динамическое извлечение цветов с обложки трека даже для мини-плеера в списке чатов!

## Настройки
* `mini_player_dialogs` — отключение/включение мини-плеера в списке чатов.
* `context_bar_enabled` — отключение/включение кастомной MD3 полоски в чатах (если выключено — стоковая полоска).
* `color_source` — выбор источника цветов:
  * `Обложка трека (Material You)` (по умолчанию)
  * `Цвет темы Telegram`
* `online_lyrics` — онлайн-поиск текстов песен.
* `wavy_seekbar` — волнистый сикбар.

## Хуки
* `BaseFragment.showDialog(Dialog)` & `AudioPlayerAlert.show()` — перехват открытия аудио-плеера Telegram и подмена на `PlayerSheet`.
* `FragmentContextView.checkPlayer(boolean)` — вставка и управление видимостью `PlayerBarView`.
* `MainTabsActivity.createView` & `DialogsActivity.createView` — вставка плавающего `PlayerMiniView`.
