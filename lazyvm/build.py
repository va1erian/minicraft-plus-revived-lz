"""Builds the LazyVM port of Minicraft Plus.

    python lazyvm/build.py            compile the game into build/classes
    python lazyvm/build.py --report   compile against LazyVM's class library instead and
                                      write the java.* API it lacks to lazyvm/missing-api.txt

The normal build uses javac 8 with the JDK's own boot class path, plus lib/*.jar, the
shims in lazyvm/shims and LazyVM's lazyrt.jar (for lazy.gfx). The report build swaps
the boot class path for lazyrt.jar, so every java.* class or member the game uses and
LazyVM does not provide shows up as a compile error.

Paths can be overridden with the LAZYVM_HOME and JAVA8_HOME environment variables.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAZYVM_HOME = os.environ.get(
    "LAZYVM_HOME", r"E:\repos\lazyvm\.claude\worktrees\minicraft-lazyvm-port-ab8592")
JAVA8_HOME = os.environ.get(
    "JAVA8_HOME", os.path.join(os.path.expanduser("~"), ".jdks", "temurin-1.8.0_504"))
LAZYRT = os.path.join(LAZYVM_HOME, "dist", "lazyrt.jar")
SOURCE_DIRS = ["src/client/java", "src/common/java", "lazyvm/shims"]
CLASSES = os.path.join(ROOT, "build", "classes")
REPORT = os.path.join(ROOT, "lazyvm", "missing-api.txt")


def sources():
    found = []
    for d in SOURCE_DIRS:
        for dirpath, _, files in os.walk(os.path.join(ROOT, d)):
            found += [os.path.join(dirpath, f) for f in files if f.endswith(".java")]
    return sorted(found)


def lib_jars():
    """lib/*.jar except org.json, which lazyvm/shims/org/json replaces (see lazyvm/gaps.md)."""
    lib = os.path.join(ROOT, "lib")
    return sorted(os.path.join(lib, f) for f in os.listdir(lib)
                  if f.endswith(".jar") and not f.startswith("json-"))


def javac(out_dir, extra, classpath):
    os.makedirs(out_dir, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as f:
        f.write("\n".join('"%s"' % s.replace("\\", "/") for s in sources()))
        argfile = f.name
    cmd = [os.path.join(JAVA8_HOME, "bin", "javac.exe"), "-source", "8", "-target", "8",
           "-encoding", "UTF-8", "-nowarn", "-Xmaxerrs", "100000", "-d", out_dir,
           "-cp", os.pathsep.join(classpath)] + extra + ["@" + argfile]
    try:
        return subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, errors="replace")
    finally:
        os.unlink(argfile)


def build():
    shutil.rmtree(CLASSES, ignore_errors=True)  # no stale classes from deleted sources
    result = javac(CLASSES, [], lib_jars() + [LAZYRT])
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    if result.returncode == 0:
        print("built", CLASSES)
    return result.returncode


# One javac diagnostic: "path:line: error: message", then source and caret lines, then
# optional "symbol:" and "location:" lines.
DIAG = re.compile(r"^(?P<file>.+?\.java):(?P<line>\d+): error: (?P<msg>.*)$")


def parse(stderr):
    diags, cur = [], None
    for line in stderr.splitlines():
        m = DIAG.match(line)
        if m:
            cur = {"file": os.path.relpath(m["file"], ROOT).replace("\\", "/"),
                   "line": int(m["line"]), "msg": m["msg"], "src": None}
            diags.append(cur)
        elif cur is not None:
            s = line.strip()
            if s.startswith("symbol:"):
                cur["symbol"] = s[len("symbol:"):].strip()
            elif s.startswith("location:"):
                cur["location"] = s[len("location:"):].strip()
            elif s.startswith("found:"):
                cur["found"] = s[len("found:"):].strip()
            elif cur["src"] is None and s and s != "^":
                cur["src"] = s
    return diags


def location_type(loc):
    """'variable x of type List<String>' -> 'List'; 'interface Collection<E>' -> 'Collection'."""
    m = re.search(r"of type (\S+)", loc) or re.match(r"(?:class|interface|enum) (\S+)", loc)
    return re.sub(r"<.*", "", m.group(1)) if m else loc


_imports = {}
# Owners of missing members that are library types rather than game classes.
JAVA_TYPES = {"ArrayList", "Arrays", "BufferedReader", "Class", "Collection", "FileSystem",
              "Files", "HashSet", "List", "Locale", "Math", "Runtime", "Set", "String",
              "System", "Thread", "URI", "Map", "HashMap", "Character", "Integer", "Objects"}


def qualify(file, simple):
    """Resolves a simple class name through the single-type imports of {@code file}."""
    if file not in _imports:
        with open(os.path.join(ROOT, file), encoding="utf-8") as f:
            found = re.findall(r"^import ([\w.]+);", f.read(), re.M)
        _imports[file] = {imp.rsplit(".", 1)[1]: imp for imp in found}
    outer = simple.split(".", 1)
    if outer[0] in _imports[file]:
        return ".".join([_imports[file][outer[0]]] + outer[1:])
    return simple


def key_of(d):
    msg, sym, loc = d["msg"], d.get("symbol", ""), d.get("location", "")
    m = re.match(r"package (\S+) does not exist", msg)
    if m:
        imp = re.match(r"import (?:static )?([\w.]+)", d["src"] or "")
        return "class", imp.group(1) if imp else qualify(d["file"], m.group(1))
    if sym.startswith("class "):
        return "class", qualify(d["file"], sym[len("class "):])
    if sym.startswith("variable ") and qualify(d["file"], sym[len("variable "):]) != sym[9:]:
        return "class", qualify(d["file"], sym[len("variable "):])  # a missing class used by name
    if sym.startswith("method ") or sym.startswith("variable "):
        kind, name = sym.split(" ", 1)
        owner = location_type(loc).rsplit(".", 1)[-1]
        if owner not in JAVA_TYPES:
            return "follow-on", "member of a game class whose supertype is missing"
        return "member", "%s.%s" % (owner, re.sub(r"\(\w+\[\]\)", "(Object[])", name))
    m = re.match(r"method (\S+) in class (\S+) cannot be applied to given types", msg)
    if m:
        return "member", "%s.%s(%s)" % (m.group(2), m.group(1), d.get("found", ""))
    if msg.startswith("invalid method reference") or msg.startswith("method does not override"):
        return "follow-on", msg  # caused by a missing stream() or a missing supertype above
    return "other", msg


def report():
    with tempfile.TemporaryDirectory() as out:
        result = javac(out, ["-bootclasspath", LAZYRT, "-extdirs", ""], lib_jars())
    diags = parse(result.stderr)
    groups = defaultdict(lambda: defaultdict(list))
    for d in diags:
        kind, key = key_of(d)
        groups[kind][key].append("%s:%d" % (d["file"], d["line"]))
    lines = ["Compile of the port against LazyVM's lazyrt.jar as boot class path",
             "(python lazyvm/build.py --report). %d errors, deduplicated below;" % len(diags),
             "each entry gives the error count and up to three call sites.", ""]
    titles = {"class": "Missing classes (or classes in missing packages)",
              "member": "Missing members (Type.member as javac names them)",
              "follow-on": "Follow-on errors (caused by the gaps above, not gaps themselves)",
              "other": "Other errors"}
    for kind in ("class", "member", "follow-on", "other"):
        if not groups[kind]:
            continue
        lines += ["== %s: %d ==" % (titles[kind], len(groups[kind]))]
        for key in sorted(groups[kind]):
            sites = groups[kind][key]
            more = " ..." if len(sites) > 3 else ""
            lines.append("%-60s %4d  %s%s" % (key, len(sites), ", ".join(sites[:3]), more))
        lines.append("")
    lines += ["== Raw javac output ==", result.stderr]
    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print("%d errors; summary in %s" % (len(diags), REPORT))
    return 0


if __name__ == "__main__":
    sys.exit(report() if "--report" in sys.argv[1:] else build())
