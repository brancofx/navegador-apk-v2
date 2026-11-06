import code
import contextlib
import os
import re
import shlex
import sys
import traceback

import navegador


class Stopped(BaseException):
    pass


class Console:
    encoding = "utf-8"

    def __init__(self, bridge):
        self.bridge = bridge

    def write(self, text):
        self.bridge.log(text)
        return len(text)

    def flush(self):
        pass


terminal = code.InteractiveConsole()


def install(command, package_dir):
    args = shlex.split(command)
    if len(args) < 3 or args[:2] != ["pip", "install"]:
        raise ValueError("Use pip install nome-da-biblioteca.")
    for requirement in args[2:]:
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+!~<>=,\-\[\]*]*", requirement):
            raise ValueError("Informe nomes de pacotes PyPI e versões, sem outras opções.")
    from pip._internal.cli.main import main

    result = main([
        "--isolated", "--disable-pip-version-check", "install", "--no-input",
        "--only-binary=:all:", "--no-compile", "--upgrade", "--target", package_dir,
        "--index-url", "https://pypi.org/simple/", *args[2:],
    ])
    if result:
        raise RuntimeError(f"pip terminou com código {result}.")


def run(source, name, bridge, interactive, data_dir):
    console = Console(bridge)
    ticks = 0
    package_dir = os.path.join(data_dir, "bibliotecas")
    os.makedirs(package_dir, exist_ok=True)
    if package_dir not in sys.path:
        sys.path.insert(0, package_dir)

    def trace(frame, event, arg):
        nonlocal ticks
        ticks += 1
        if ticks >= 128:
            ticks = 0
            if bridge.cancelled():
                raise Stopped()
        return trace

    with contextlib.redirect_stdout(console), contextlib.redirect_stderr(console):
        navegador._current = navegador.Browser(bridge)
        previous_trace = sys.gettrace()
        previous_argv = sys.argv
        try:
            if bridge.cancelled():
                raise Stopped()
            sys.settrace(trace)
            if interactive:
                terminal.locals["driver"] = navegador.driver
                if re.match(r"^pip\s+", source) and "\n" not in source:
                    install(source, package_dir)
                else:
                    # InteractiveConsole.push preserves multiline input and displays expression results.
                    for line in source.split("\n"):
                        more = terminal.push(line)
                    if more:
                        print("… continue o bloco ou envie uma linha em branco.")
                    return "terminal"
            else:
                script = compile(source, name, "exec")
                sys.argv = [name]
                namespace = {"__name__": "__main__", "__file__": name, "driver": navegador.driver}
                exec(script, namespace)
        except Stopped:
            terminal.resetbuffer()
            return "interrompido"
        except SystemExit as error:
            if error.code not in (None, 0):
                print(f"SystemExit: {error.code}")
                return "erro"
        except BaseException:
            if bridge.cancelled():
                terminal.resetbuffer()
                return "interrompido"
            traceback.print_exc()
            return "erro"
        finally:
            sys.settrace(previous_trace)
            sys.argv = previous_argv
            navegador._current = None
    return "interrompido" if bridge.cancelled() else "concluído"
