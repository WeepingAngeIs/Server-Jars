"""Tiny SNBT (FTB Quests flavour) parser + writer that round-trips numbers exactly."""
import re


class Num:
    __slots__ = ('text',)

    def __init__(self, text):
        self.text = str(text)

    def __repr__(self):
        return self.text

    @property
    def value(self):
        t = self.text.rstrip('bslfdBSLFD')
        return float(t) if ('.' in t or 'e' in t.lower()) else int(t)

    def __eq__(self, other):
        return isinstance(other, Num) and other.text == self.text


_TOKEN = re.compile(r'''\s+|#[^\n]*|(?P<str>"(?:[^"\\]|\\.)*")|(?P<punct>[{}\[\]:,])|(?P<word>[^\s{}\[\]:,"#]+)''')


def _tokens(text):
    pos = 0
    n = len(text)
    while pos < n:
        m = _TOKEN.match(text, pos)
        if not m:
            raise ValueError(f'bad char at {pos}: {text[pos:pos+20]!r}')
        pos = m.end()
        if m.group('str') is not None:
            yield ('str', m.group('str'))
        elif m.group('punct') is not None:
            yield ('p', m.group('punct'))
        elif m.group('word') is not None:
            yield ('w', m.group('word'))


def _unquote(s):
    out = []
    i = 1
    while i < len(s) - 1:
        c = s[i]
        if c == '\\':
            i += 1
            c = s[i]
            out.append({'n': '\n', 't': '\t', 'r': '\r'}.get(c, c))
        else:
            out.append(c)
        i += 1
    return ''.join(out)


_NUM = re.compile(r'^-?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?[bslfdBSLFD]?$')


class _Parser:
    def __init__(self, text):
        self.toks = list(_tokens(text))
        self.i = 0

    def peek(self):
        return self.toks[self.i] if self.i < len(self.toks) else (None, None)

    def next(self):
        t = self.toks[self.i]
        self.i += 1
        return t

    def value(self):
        kind, tok = self.next()
        if kind == 'p' and tok == '{':
            return self.map()
        if kind == 'p' and tok == '[':
            return self.list()
        if kind == 'str':
            return _unquote(tok)
        if kind == 'w':
            if tok == 'true':
                return True
            if tok == 'false':
                return False
            if _NUM.match(tok):
                return Num(tok)
            return tok
        raise ValueError(f'unexpected {tok!r}')

    def map(self):
        d = {}
        while True:
            kind, tok = self.peek()
            if kind == 'p' and tok == '}':
                self.next()
                return d
            if kind == 'p' and tok == ',':
                self.next()
                continue
            kind, tok = self.next()
            key = _unquote(tok) if kind == 'str' else tok
            k2, t2 = self.next()
            if not (k2 == 'p' and t2 == ':'):
                raise ValueError(f'expected : after {key}')
            d[key] = self.value()

    def list(self):
        l = []
        while True:
            kind, tok = self.peek()
            if kind == 'p' and tok == ']':
                self.next()
                return l
            if kind == 'p' and tok == ',':
                self.next()
                continue
            l.append(self.value())


def parse(text):
    p = _Parser(text)
    v = p.value()
    return v


def _quote(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


_IDENT = re.compile(r'^[A-Za-z0-9_.-]+$')


def dump(v, ind=0):
    tab = '\t' * ind
    if isinstance(v, dict):
        if not v:
            return '{ }'
        out = ['{\n']
        for k, val in v.items():
            key = k if _IDENT.match(k) else _quote(k)
            out.append(f'{tab}\t{key}: {dump(val, ind + 1)}\n')
        out.append(tab + '}')
        return ''.join(out)
    if isinstance(v, list):
        if not v:
            return '[ ]'
        out = ['[\n']
        for val in v:
            out.append(f'{tab}\t{dump(val, ind + 1)}\n')
        out.append(tab + ']')
        return ''.join(out)
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, Num):
        return v.text
    if isinstance(v, (int, float)):
        return str(v)
    return _quote(str(v))


def load(path):
    return parse(open(path, encoding='utf-8').read())


def save(path, v):
    open(path, 'w', encoding='utf-8').write(dump(v) + '\n')
