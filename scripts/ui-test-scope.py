#!/usr/bin/env python3
"""Which on-device UI test modules a change can reach: the scoping of scripts/ui-tests.sh.

    python -I scripts/ui-test-scope.py [--why] <base> [<head>]
    python -I scripts/ui-test-scope.py --self-check

Prints the UI test modules, one a line, that the change from commit <base> to <head> (by default
the working tree, untracked files included) can reach; --why adds the reason for each.

A module's own sources (main or androidTest) reach the module, and a feature module's main sources
also :app, which shows every screen. A changed Kotlin file of :core:dsp, :core:settings, :core:audio
or :core:designsystem (main or testFixtures) reaches only the modules that use what changed in it:
each changed line belongs to a member of a class (`fun`, `val`, `var` or a nested class at the
class's first indentation) or, anywhere else, to the whole top-level declaration. A changed member
reaches the modules whose code names the member and can see its class (name it, or name
something in the core modules that names it); a whole declaration reaches every module that names
it. Core code that uses what changed changes in turn, and so on. Comments, blank lines, imports
and lone closing brackets change nothing.

The broad set (every module that depends on that core module) is kept where names cannot tell:
a core module's resources, manifest or build file, a file that cannot be read as Kotlin, anything
in :core:testing, and TwangleTheme, which every screen draws. Members called without their name
(operators, toString, equals, hashCode, componentN, getValue) count as the whole class.

It reads names, not types: a name used for something else as well only widens the set. The
overnight run tests everything, so a rare way to miss a module (a constructor argument moved
between two of the same type) is caught there.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_MODULES = [':core:testing', ':feature:tuner', ':feature:settings', ':feature:metronome',
              ':feature:practice', ':feature:eartraining', ':core:audio', ':app']
DIRS = {m: m[1:].replace(':', '/') for m in UI_MODULES}
EVERYTHING = set(UI_MODULES)
SCREENS = {':feature:tuner', ':feature:settings', ':feature:metronome', ':feature:practice',
           ':feature:eartraining', ':app'}
# Every UI test module that depends on each core module (build.gradle.kts): the broad set.
BROAD = {
    'core/dsp': SCREENS | {':core:audio'},
    'core/settings': SCREENS,
    'core/designsystem': SCREENS,
    'core/audio': SCREENS | {':core:audio'},
}
LIB_KOTLIN = re.compile(r'^(core/(?:dsp|settings|audio|designsystem))/src/(?:main|testFixtures)/kotlin/.*\.kt$')
# The theme every screen draws: a change to it reaches everything.
EVERY_SCREEN = {('io.github.klaboukes.twangle.core.designsystem', 'TwangleTheme')}
# Members Kotlin calls without their name.
IMPLICIT = {'toString', 'equals', 'hashCode', 'invoke', 'get', 'set', 'contains', 'iterator', 'next',
            'hasNext', 'compareTo', 'getValue', 'setValue', 'provideDelegate', 'plus', 'minus', 'times',
            'div', 'rem', 'rangeTo', 'rangeUntil', 'unaryMinus', 'unaryPlus', 'not', 'inc', 'dec',
            'plusAssign', 'minusAssign', 'timesAssign', 'divAssign', 'remAssign'}

MODIFIERS = (r'(?:(?:public|internal|private|protected|open|abstract|sealed|data|enum|annotation|inline|'
             r'value|inner|override|lateinit|const|suspend|tailrec|operator|infix|external|expect|actual|'
             r'companion|fun(?=\s+interface))\s+)*')
DECL = re.compile(r'^(\s*)(' + MODIFIERS + r')(class|interface|object|fun|val|var|typealias)\b(.*)$')
WORD = re.compile(r'[A-Za-z_]\w*')
CLOSING = re.compile(r'^[\s})\],;]*$')


def git(*args, check=True):
    out = subprocess.run(['git', *args], cwd=ROOT, capture_output=True, check=check)
    return out.stdout.decode('utf-8', 'replace')


def without_generics(text):
    while True:
        shorter = re.sub(r'<[^<>]*>', '', text)
        if shorter == text:
            return text
        text = shorter


def declared(code):
    """(indent, modifiers, kind, name) of a declaration line, or None. Name is None for `companion object`."""
    match = DECL.match(code)
    if not match:
        return None
    indent, mods, kind, rest = match.groups()
    rest = rest.replace('`', '')
    if kind in ('class', 'interface', 'object', 'typealias'):
        name = re.match(r'\s+(\w+)', rest)
        name = name.group(1) if name else None
    else:
        head = re.match(r'\s*([\w.?]+)', without_generics(rest))
        if not head:
            return None  # a destructuring `val (a, b)`
        name = head.group(1).split('.')[-1].rstrip('?')
    return len(indent), mods, kind, name


def code_lines(text):
    """The file's lines with comments taken out (strings are not parsed: a `//` in one cuts the line)."""
    out, block = [], False
    for line in text.replace('\r', '').split('\n'):
        code, i = '', 0
        while i < len(line):
            if block:
                end = line.find('*/', i)
                if end < 0:
                    i = len(line)
                else:
                    block, i = False, end + 2
            elif line.startswith('/*', i):
                block, i = True, i + 2
            elif line.startswith('//', i):
                break
            else:
                code += line[i]
                i += 1
        out.append(code.rstrip())
    return out


class KtFile:
    def __init__(self, path, text):
        self.path = path
        self.code = code_lines(text)
        self.joined = '\n'.join(self.code)
        self.words = set(WORD.findall(self.joined))
        package = re.search(r'^package\s+([\w.]+)', self.joined, re.M)
        self.package = package.group(1) if package else None
        self.decls = [declared(c) if c.strip() else None for c in self.code]
        # The top-level declarations: (start line, name, class-like); a line before the first is the header.
        self.tops = []
        for i, d in enumerate(self.decls):
            if d and d[0] == 0 and d[3]:
                self.tops.append((self._lead(i, 0), d[3], d[2] in ('class', 'interface', 'object')))

    def _lead(self, i, indent):
        """Line [i]'s declaration with its annotations above it, at the same indentation."""
        while i > 0 and self.code[i - 1].startswith(' ' * indent + '@') and \
                (len(self.code[i - 1]) - len(self.code[i - 1].lstrip())) == indent:
            i -= 1
        return i

    def top_at(self, i):
        found = None
        for n, top in enumerate(self.tops):
            if top[0] <= i:
                found = n
        return found

    def owner(self, i):
        """What line [i] belongs to: ('*', name) for a whole top-level declaration, ('m', name, member)
        for a class member, or None for the header, a comment, a blank line or a lone closing bracket."""
        code = self.code[i]
        if not code.strip() or CLOSING.match(code):
            return None
        n = self.top_at(i)
        if n is None:
            return None
        start, name, classlike = self.tops[n]
        end = self.tops[n + 1][0] if n + 1 < len(self.tops) else len(self.code)
        if not classlike or i == start or not code.startswith(' '):
            return ('*', name)
        # The members' indentation: the class's first indented line.
        member_indent = next((len(c) - len(c.lstrip()) for c in self.code[start + 1:end]
                              if c.strip() and c.startswith(' ')), None)
        if member_indent is None:
            return ('*', name)
        j = i
        while j > start:
            c = self.code[j]
            depth = len(c) - len(c.lstrip()) if c.strip() else None
            if depth is not None and depth <= member_indent and not CLOSING.match(c):
                break
            j -= 1
        if j == start or len(self.code[j]) - len(self.code[j].lstrip()) < member_indent:
            return ('*', name)
        # An annotation line belongs to the member under it.
        while self.code[j].lstrip().startswith('@') and j + 1 < end:
            nxt = next((k for k in range(j + 1, end) if self.code[k].strip()), None)
            if nxt is None:
                break
            j = nxt
        d = self.decls[j]
        if not d or d[0] != member_indent or not d[3]:
            return ('*', name)  # init, a companion object, an enum entry, a constructor argument
        if 'operator' in d[1] or d[3] in IMPLICIT or re.fullmatch(r'component\d+', d[3]):
            return ('*', name)
        return ('m', name, d[3])

    def declares(self, i, word):
        d = self.decls[i]
        return bool(d) and d[3] == word

    def sees(self, package, name):
        return self.package == package or \
            re.search(r'\b' + re.escape(package) + r'\.(?:\*|' + re.escape(name) + r'\b)', self.joined) is not None


class Sources:
    """The Kotlin sources of the modules at [head] (None: the working tree)."""

    def __init__(self, head):
        dirs = sorted({d for d in DIRS.values()} | {'core/dsp', 'core/settings', 'core/designsystem'})
        pattern = re.compile(r'^(' + '|'.join(map(re.escape, dirs)) + r')/src/(main|androidTest|testFixtures)/.*\.kt$')
        if head is None:
            names = [p for p in git('ls-files', '-co', '--exclude-standard', '--', *dirs).split('\n')
                     if pattern.match(p) and (ROOT / p).is_file()]
            texts = {p: (ROOT / p).read_text(encoding='utf-8', errors='replace') for p in names}
        else:
            names = [p for p in git('ls-tree', '-r', '--name-only', head, '--', *dirs).split('\n') if pattern.match(p)]
            texts = read_at(head, names)
        self.files = {p: KtFile(p, t) for p, t in texts.items()}
        self.lib = [f for p, f in self.files.items() if LIB_KOTLIN.match(p)]
        self.users = {m: [f for p, f in self.files.items()
                          if re.match(re.escape(d) + r'/src/(main|androidTest)/', p)] for m, d in DIRS.items()}


def read_at(rev, paths):
    """The text of each of [paths] at commit [rev], in one git call."""
    if not paths:
        return {}
    request = ''.join(f'{rev}:{p}\n' for p in paths).encode('utf-8')
    out = subprocess.run(['git', 'cat-file', '--batch'], cwd=ROOT, input=request, capture_output=True, check=True).stdout
    texts, pos = {}, 0
    for p in paths:
        header_end = out.index(b'\n', pos)
        header = out[pos:header_end].split()
        if header[-1] == b'missing':
            pos = header_end + 1
            continue
        size = int(header[2])
        texts[p] = out[header_end + 1:header_end + 1 + size].decode('utf-8', 'replace')
        pos = header_end + 1 + size + 1
    return texts


class Scope:
    def __init__(self, sources):
        self.src = sources
        self.star_memo = {}
        self.children_memo = {}
        self.namers_memo = {}

    def key(self, f, owner):
        """A graph node for what [owner] of file [f] names: ('*', package, name) or ('m', package, name, member)."""
        return (owner[0], f.package) + owner[1:]

    def children(self, node):
        """The core declarations whose code uses [node]: they change with it."""
        if node not in self.children_memo:
            self.children_memo[node] = self._children(node)
        return self.children_memo[node]

    def _children(self, node):
        word = node[2] if node[0] == '*' else node[3]
        bare = re.compile(r'\b' + re.escape(word) + r'\b')
        # Outside its own class a member is used as one: `x.name`, `X::name`, or a named argument `name = x`.
        member = re.compile(r'(?:\.|::)\s*' + re.escape(word) + r'\b|\b' + re.escape(word) + r'\s*=(?!=)')
        found = set()
        for f in self.src.lib:
            if word not in f.words or (node[0] == '*' and not f.sees(node[1], node[2])):
                continue
            for i, c in enumerate(f.code):
                if not bare.search(c) or f.declares(i, word) or c.lstrip().startswith(('import ', 'package ')):
                    continue
                owner = f.owner(i)
                if not owner:
                    continue
                child = self.key(f, owner)
                own_class = child[1:3] == node[1:3]
                if child != node and (node[0] == '*' or own_class or
                                      member.search(c) and self.can_reach(f, node[1], node[2])):
                    found.add(child)
        return found

    def can_reach(self, f, package, name):
        """Whether code in [f] can hold a [name]: it names it, or names a declaration whose code names it
        (`repository.settings.first().tempo` holds an AppSettings without naming it)."""
        if f.sees(package, name):
            return True
        key = (package, name)
        if key not in self.namers_memo:
            self.namers_memo[key] = {(g.package, top[1]) for g in self.src.lib
                                     if name in g.words and g.sees(package, name) for top in g.tops}
        return any(f.sees(*namer) for namer in self.namers_memo[key] if namer[1] in f.words)

    def reachable(self, starts):
        seen, todo = set(starts), list(starts)
        while todo:
            for child in self.children(todo.pop()):
                if child not in seen:
                    seen.add(child)
                    todo.append(child)
        return seen

    def direct(self, node):
        """{module: example file} that use [node] themselves."""
        out = {}
        if node[0] == '*':
            _, package, name = node
            for m, files in self.src.users.items():
                f = next((f for f in files if name in f.words and f.sees(package, name)), None)
                if f:
                    out[m] = f.path
        else:
            _, package, name, member = node
            can_see = self.star(package, name)
            for m in can_see:
                f = next((f for f in self.src.users[m] if member in f.words), None)
                if f:
                    out[m] = f.path
        return out

    def star(self, package, name):
        """The modules that can see top-level declaration [name] at all: they name it, or name what names it."""
        key = (package, name)
        if key not in self.star_memo:
            self.star_memo[key] = set()  # a cycle back here adds nothing new
            modules = set()
            for node in self.reachable({('*', package, name)}):
                if node[0] == '*':
                    modules |= set(self.direct(node))
                else:
                    modules |= set(self.direct(node)) if node[1:3] != key else set()
            self.star_memo[key] = modules
        return self.star_memo[key]


def describe(node):
    return node[2] if node[0] == '*' else f'{node[2]}.{node[3]}'


def changed_lines(base, head, path):
    """Line numbers (0-based) removed from the old file and added to the new one."""
    args = ['diff', '-U0', '--no-color', '--no-ext-diff', base] + ([head] if head else []) + ['--', path]
    old, new = [], []
    for line in git(*args).split('\n'):
        hunk = re.match(r'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', line)
        if hunk:
            a, a_n, b, b_n = int(hunk[1]), int(hunk[2] or 1), int(hunk[3]), int(hunk[4] or 1)
            old += range(a - 1, a - 1 + a_n)
            new += range(b - 1, b - 1 + b_n)
    return old, new


def affected(base, head=None):
    """{module: reason} for the change from [base] to [head] (None: the working tree)."""
    diff = ['diff', '--name-status', '-M', '--no-color', base] + ([head] if head else [])
    entries = []
    for line in git(*diff).split('\n'):
        if not line.strip():
            continue
        parts = line.split('\t')
        status = parts[0][0]
        if status == 'R':
            entries += [('D', parts[1]), ('A', parts[2])]
        else:
            entries.append((status, parts[-1]))
    if head is None:
        entries += [('A', p) for p in git('ls-files', '--others', '--exclude-standard').split('\n') if p]

    modules = {}

    def add(targets, reason):
        for m in targets:
            modules.setdefault(m, reason)

    lib_changes = []
    for status, path in entries:
        if re.match(r'.*\.md$|\.github/|\.claude/|scripts/|docs/|fastlane/|website/|.*/src/test/|core/dsp/src/benchmark/|wear/|baselineprofile/', path):
            continue  # nothing on the phone: docs, CI, store texts, JVM tests, the watch app
        own = next((m for m, d in DIRS.items() if path.startswith(d + '/src/androidTest/')), None)
        if own:
            add([own], f'{path} changed')
        elif re.match(r'feature/\w+/', path):
            module = ':feature:' + path.split('/')[1]
            add([module, ':app'] if module in EVERYTHING else EVERYTHING, f'{path} changed')
        elif path.startswith('app/'):
            add([':app'], f'{path} changed')
        elif path.startswith('core/testing/'):
            add(EVERYTHING, f'{path} changed (test helpers every module uses)')
        elif LIB_KOTLIN.match(path):
            lib_changes.append((status, path))
        elif re.match(r'core/(dsp|settings|audio|designsystem)/src/', path):
            add(BROAD[path.split('/src/')[0]], f'{path} changed (not Kotlin)')
        else:
            add(EVERYTHING, f'{path} changed (a build file)')
    if not lib_changes:
        return modules

    src = Sources(head)
    scope = Scope(src)
    old_texts = read_at(base, [p for s, p in lib_changes if s != 'A'])
    starts = {}
    for status, path in lib_changes:
        module_dir = LIB_KOTLIN.match(path).group(1)
        old = KtFile(path, old_texts[path]) if path in old_texts else None
        new = src.files.get(path)
        if status == 'A' or status == 'D' or (old and new and old.package != new.package):
            for f in (old, new):
                for _, name, _ in (f.tops if f else []):
                    starts.setdefault(('*', f.package, name), path)
        else:
            if not old or not new or not new.package:
                add(BROAD[module_dir], f'{path} changed (not read as Kotlin)')
                continue
            removed, added = changed_lines(base, head, path)
            for f, lines in ((old, removed), (new, added)):
                for i in lines:
                    owner = f.owner(i) if i < len(f.code) else None
                    if owner:
                        starts.setdefault(scope.key(f, owner), path)
    for node, path in sorted(starts.items()):
        for reached in sorted(scope.reachable({node})):
            if reached[1:3] in EVERY_SCREEN:
                add(BROAD['core/designsystem'], f'{describe(reached)} changed ({path}): every screen draws it')
            for m, user in sorted(scope.direct(reached).items()):
                via = '' if reached == node else f', through {describe(reached)}'
                reason = f'{user} uses {describe(node)}{via} ({path})'
                # :app's tests show every screen, as for a change in the feature's own sources.
                add([m, ':app'] if m.startswith(':feature:') and '/src/main/' in user else [m], reason)
    return modules


def self_check():
    sample = KtFile('Sample.kt', '''package a.b

import x.y.Z

/** Docs. */
data class Settings(
    val tempo: Int = 120,
    val left: Boolean = false,
) {
    @Volatile
    private var cache = 0

    fun twice(): Int {
        val local = tempo * 2
        return local
    }

    operator fun plus(other: Settings) = this

    companion object {
        val DEFAULT = Settings()
    }
}

fun <T> List<T>.second(): T = this[1]
val Settings.half get() = tempo / 2
''')
    expect = {
        2: None, 5: ('*', 'Settings'), 6: ('m', 'Settings', 'tempo'), 7: ('m', 'Settings', 'left'),
        8: ('*', 'Settings'), 9: ('m', 'Settings', 'cache'), 10: ('m', 'Settings', 'cache'), 13: ('m', 'Settings', 'twice'),
        15: None, 17: ('*', 'Settings'), 20: ('*', 'Settings'), 24: ('*', 'second'), 25: ('*', 'half'),
    }
    failures = [f'line {i + 1}: {sample.owner(i)} != {want}' for i, want in expect.items() if sample.owner(i) != want]
    # The Play it back increment (Phase 55): core/dsp and core/settings changed, but only Ear training uses it.
    if subprocess.run(['git', 'cat-file', '-e', '416171f0^{commit}'], cwd=ROOT).returncode == 0:
        got = sorted(affected('7d016e0f', '416171f0'))
        if got != [':app', ':feature:eartraining']:
            failures.append(f'Play it back: {got}')
    print('\n'.join(failures) if failures else 'ok')
    return 1 if failures else 0


def main(argv):
    if argv[:1] == ['--self-check']:
        return self_check()
    why = '--why' in argv
    args = [a for a in argv if a != '--why']
    if not 1 <= len(args) <= 2:
        print(__doc__.split('\n\n')[1], file=sys.stderr)
        return 2
    # Plain \n on Windows too: the shell script reads the module names.
    sys.stdout.reconfigure(encoding='utf-8', newline='\n')
    modules = affected(*args)
    for m in UI_MODULES:
        if m in modules:
            print(f'{m:22s} {modules[m]}' if why else m)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
