package dev.rooni.player;

import org.telegram.messenger.LocaleController;

import java.util.Locale;

public final class PlayerStrings {

    private PlayerStrings() {
    }

    private static boolean isRu() {
        try {
            Locale loc = LocaleController.getInstance().getCurrentLocale();
            return loc != null && loc.getLanguage() != null && loc.getLanguage().toLowerCase().startsWith("ru");
        } catch (Throwable e) {
            return true;
        }
    }

    public static String get(String key) {
        boolean ru = isRu();
        switch (key) {
            case "from_chat":
                return ru ? "Из чата" : "From chat";
            case "from_profile":
                return ru ? "Из профиля" : "From profile";
            case "from_search":
                return ru ? "Из поиска" : "From search";
            case "music":
                return ru ? "Музыка" : "Music";
            case "lyrics":
                return ru ? "Текст" : "Lyrics";
            case "queue":
                return ru ? "Далее" : "Up next";
            case "shuffle":
                return ru ? "Перемешать" : "Shuffle";
            case "speed":
                return ru ? "Скорость воспроизведения" : "Playback speed";
            case "seek":
                return ru ? "Перемотка" : "Seek";
            case "open":
                return ru ? "Открыть плеер" : "Open player";
            case "classic":
                return ru ? "Классический плеер" : "Classic player";
            case "settings":
                return ru ? "Настройки плеера" : "Player settings";
            case "no_lyrics_title":
                return ru ? "В файле нет текста" : "No lyrics in the file";
            case "no_lyrics_text":
                return ru ? "Поиск текста онлайн в базе LRCLIB." : "You can search LRCLIB for lyrics.";
            case "find_lyrics":
                return ru ? "Искать онлайн" : "Search online";
            case "lyrics_searching":
                return ru ? "Поиск текста песни…" : "Searching for lyrics…";
            case "lyrics_not_found":
                return ru ? "Текст не найден" : "Lyrics not found";
            case "lyrics_not_found_text":
                return ru ? "Ни один сервис не вернул текст для этого трека." : "None of the providers returned lyrics for this track.";
            case "lyrics_error":
                return ru ? "Не удалось загрузить текст" : "Couldn't load lyrics";
            case "lyrics_error_text":
                return ru ? "Проверьте подключение и повторите попытку." : "Check your connection and try again.";
            case "retry":
                return ru ? "Повторить" : "Try again";
            case "instrumental":
                return ru ? "Инструментальный" : "Instrumental";
            case "instrumental_text":
                return ru ? "У этого трека нет текста." : "This track has no lyrics.";
            case "source_synced_file":
                return ru ? "Синхронизированный текст из файла" : "Synced lyrics from the file";
            case "source_plain_file":
                return ru ? "Текст из файла" : "Lyrics from the file";
            default:
                return key;
        }
    }

    public static String format(String key, Object... args) {
        boolean ru = isRu();
        switch (key) {
            case "source_synced":
                return String.format(Locale.getDefault(), ru ? "Синхронизированный текст · %1$s" : "Synced lyrics · %1$s", args);
            case "source_plain":
                return String.format(Locale.getDefault(), ru ? "Текст · %1$s" : "Lyrics · %1$s", args);
            case "tracks":
                return String.format(Locale.getDefault(), ru ? "%1$d треков" : "%1$d tracks", args);
            default:
                return String.format(Locale.getDefault(), key, args);
        }
    }
}
