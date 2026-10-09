__id__ = "md3_player"
__name__ = "Material Player"
__description__ = "Стилизация тг плеера в Material Expressive стиль"
__author__ = "@RnPlugins"
__version__ = "1.0.0-beta.1"
__build__ = 39
__icon__ = "RnDev/18"
__app_version__ = ">=12.10.1"
__sdk_version__ = ">=1.4.3.3"

import base64
import traceback
from base_plugin import BasePlugin
from java.nio import ByteBuffer
from dalvik.system import InMemoryDexClassLoader
from org.telegram.messenger import ApplicationLoader
from android_utils import run_on_ui_thread
from ui.settings import Header, Selector, Switch, Text

DEX_B64 = """__DEX_B64__"""

class Md3PlayerPlugin(BasePlugin):
    def on_plugin_load(self):
        self.dex_class = None
        self.dex_loader = None
        self.author_sheet_class = None
        try:
            dex_bytes = base64.b64decode(DEX_B64)
            buffer = ByteBuffer.wrap(dex_bytes)
            parent_cl = ApplicationLoader.applicationContext.getClassLoader()
            self.dex_loader = InMemoryDexClassLoader(buffer, parent_cl)
            self.dex_class = self.dex_loader.loadClass("dev.rooni.player.PlayerHook")
            self.author_sheet_class = self.dex_loader.loadClass("dev.rooni.author.AuthorBottomSheet")

            start_method = self.dex_class.getMethod("start")
            start_method.invoke(None)

            self.apply_settings()
            run_on_ui_thread(self.check_author_promo, delay=700)
        except Exception as e:
            self.log(f"Failed to load Java MD3 Player module: {e}\n{traceback.format_exc()}")

    def check_author_promo(self):
        try:
            if self.get_setting("author_promo_shown", False):
                return
            if getattr(self, "author_sheet_class", None) is None:
                return

            is_sub = self.author_sheet_class.getMethod("isSubscribedToAuthor").invoke(None)
            if is_sub:
                self.set_setting("author_promo_shown", True)
                return

            shown = self.author_sheet_class.getMethod("showFromCurrentActivity").invoke(None)
            if shown:
                self.set_setting("author_promo_shown", True)
        except Exception as e:
            self.log(f"[MD3Player] Error in check_author_promo: {e}\n{traceback.format_exc()}")

    def show_author_sheet(self):
        try:
            if getattr(self, "author_sheet_class", None) is not None:
                self.author_sheet_class.getMethod("showFromCurrentActivity").invoke(None)
        except Exception as e:
            self.log(f"[MD3Player] Error showing author sheet: {e}\n{traceback.format_exc()}")

    def on_author_click(self, _=None):
        self.show_author_sheet()

    def on_gratitude_click(self, _=None):
        try:
            from org.telegram.messenger.browser import Browser
            from org.telegram.ui import LaunchActivity
            ctx = LaunchActivity.instance
            if ctx is None:
                ctx = ApplicationLoader.applicationContext
            Browser.openUrl(ctx, "https://t.me/exteraless")
        except Exception as e:
            self.log(f"[MD3Player] Error opening gratitude link: {e}")

    def on_plugin_unload(self):
        try:
            if hasattr(self, "dex_class") and self.dex_class is not None:
                stop_method = self.dex_class.getMethod("stop")
                stop_method.invoke(None)
            self.dex_class = None
            self.author_sheet_class = None
            self.dex_loader = None
        except Exception as e:
            self.log(f"Failed to unload Java MD3 Player module: {e}")

    def apply_settings(self):
        try:
            mini_dialogs = bool(self.get_setting("mini_player_dialogs", True))
            context_bar = bool(self.get_setting("context_bar_enabled", True))
            color_idx = int(self.get_setting("color_source", 0))
            color_source = "cover" if color_idx == 0 else "theme"
            online_lyrics = True
            wavy_seekbar = bool(self.get_setting("wavy_seekbar", True))

            if self.dex_class is not None:
                for method in self.dex_class.getMethods():
                    if method.getName() == "updateSettings":
                        param_count = len(method.getParameterTypes())
                        if param_count == 5:
                            method.invoke(None, mini_dialogs, context_bar, color_source, online_lyrics, wavy_seekbar)
                            break
                        elif param_count == 6:
                            method.invoke(None, mini_dialogs, context_bar, color_source, online_lyrics, wavy_seekbar, False)
                            break
        except Exception as e:
            self.log(f"[MD3Player] Apply settings error: {e}\n{traceback.format_exc()}")

    def on_setting_change(self, *args):
        self.apply_settings()

    def create_settings(self):
        return [
            Header(text="Отображение"),
            Switch(
                key="mini_player_dialogs",
                text="На главном экране",
                subtext="Кастомизировать плеер на главной странице",
                default=True,
                icon="player",
                on_change=self.on_setting_change,
            ),
            Switch(
                key="context_bar_enabled",
                text="Панель в чате",
                subtext="Компактный плеер сверху в переписке",
                default=True,
                icon="msg_message",
                on_change=self.on_setting_change,
            ),
            Header(text="Стиль"),
            Selector(
                key="color_source",
                text="Цвета",
                default=0,
                items=[
                    "Обложка трека",
                    "Тема Telegram",
                ],
                icon="msg_palette",
                on_change=self.on_setting_change,
            ),
            Switch(
                key="wavy_seekbar",
                text="Волнистый прогресс",
                subtext="Анимация волны на прогрессе",
                default=True,
                icon="msg_autodelete_badge2",
                on_change=self.on_setting_change,
            ),
            Header(text="О плагине"),
            Text(
                text="Автор",
                subtext="Подробнее об авторе",
                icon="msg_openprofile",
                on_click=self.on_author_click,
            ),
            Text(
                text="Благодарность",
                subtext="Большое спасибо exteraless",
                icon="msg_link",
                on_click=self.on_gratitude_click,
            ),
        ]
