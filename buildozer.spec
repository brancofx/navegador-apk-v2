[app]
title = Navegador
package.name = navegador
package.domain = org.brancofx
source.include_exts = py,png,jpg,kv,atlas,zip
source.dir = .
version = 1.0
requirements = python3,kivy,android,certifi,urllib3
orientation = portrait
fullscreen = 0
android.permissions = INTERNET
android.api = 33
android.minapi = 21
android.ndk = 25b
android.accept_sdk_license = True

[buildozer]
log_level = 2
warn_on_root = 1
