"""Runs the LazyVM port of Minicraft Plus (build it first with lazyvm/build.py).

    python lazyvm/run.py [--headless] [--dump FILE.ppm] [--keys SCRIPT] [-Dname=value ...]
                         [-- game arguments]

--headless       no window (-Dlazy.gfx=headless)
--dump FILE      write the last frame as a binary PPM when the window closes
--keys SCRIPT    feed scripted input to the game (format in minicraft.core.io.KeyScript)
-D...            passed to the VM as is

The game runs in build/run, so its logs land in build/run/logs, and it saves into
build/run/save unless the game arguments give another --savedir.
"""
import os
import subprocess
import sys

from build import LAZYRT, LAZYVM_HOME, ROOT, CLASSES, lib_jars


def main(argv):
    vm_opts, game_args = [], []
    if "--" in argv:
        i = argv.index("--")
        argv, game_args = argv[:i], argv[i + 1:]
    it = iter(argv)
    for a in it:
        if a == "--headless":
            vm_opts.append("-Dlazy.gfx=headless")
        elif a == "--dump":
            vm_opts.append("-Dlazy.gfx.dump=" + os.path.abspath(next(it)))
        elif a == "--keys":
            vm_opts.append("-Dminicraft.lazyvm.keys=" + os.path.abspath(next(it)))
        elif a.startswith("-D") or a.startswith("-X"):
            vm_opts.append(a)
        else:
            sys.exit("unknown option " + a)
    run_dir = os.path.join(ROOT, "build", "run")
    os.makedirs(run_dir, exist_ok=True)
    if "--savedir" not in game_args:
        game_args = ["--savedir", os.path.join(run_dir, "save")] + game_args
    cp = [CLASSES] + lib_jars() + [os.path.join(ROOT, "src", "client", "resources")]
    cmd = [os.path.join(LAZYVM_HOME, "target", "release", "lazyvm.exe"),
           "-Xbootclasspath:" + LAZYRT, "-cp", os.pathsep.join(cp)] + vm_opts + \
          ["minicraft.core.Game"] + game_args
    return subprocess.call(cmd, cwd=run_dir)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
