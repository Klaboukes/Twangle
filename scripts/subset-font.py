"""Minimal TrueType subsetter: keeps .notdef plus the glyphs of the given code points (simple glyphs only).

Made core/designsystem/src/main/res/font/music_signs.ttf from Noto Sans Symbols (SIL OFL 1.1), as
found in Android's /system/fonts/NotoSansSymbols-Regular-Subsetted.ttf:

    python scripts/subset-font.py NotoSansSymbols-Regular-Subsetted.ttf music_signs.ttf 2669,266A,266D,266E,266F 1300 500

The optional last two numbers set the line ascent and descent (font units; hhea and OS/2 typo/win
metrics). Noto's own (2189/600) are taller than most letter faces, so a sign would make a line
taller than its letters; 1300/500 just covers these glyphs (-477..1209).

Drops layout tables (GSUB/GPOS/GDEF) and glyph names; keeps name and OS/2. Standard library only.
"""
import struct, sys
src, dst = sys.argv[1], sys.argv[2]
cps = [int(c, 16) for c in sys.argv[3].split(',')]
d = open(src, 'rb').read()
n = struct.unpack('>H', d[4:6])[0]
T = {}
for i in range(n):
    tag, cs, off, ln = struct.unpack('>4sIII', d[12+16*i:28+16*i]); T[tag.decode()] = (off, ln)
def tbl(t): o, l = T[t]; return d[o:o+l]
# cmap (format 4, 3/1)
def cmapd():
    off = T['cmap'][0]; nt = struct.unpack('>H', d[off+2:off+4])[0]; m = {}
    for i in range(nt):
        pid, eid, so = struct.unpack('>HHI', d[off+4+8*i:off+12+8*i]); s = off+so
        if struct.unpack('>H', d[s:s+2])[0] != 4: continue
        segx2 = struct.unpack('>H', d[s+6:s+8])[0]; seg = segx2//2
        ends = struct.unpack('>%dH' % seg, d[s+14:s+14+segx2]); starts = struct.unpack('>%dH' % seg, d[s+16+segx2:s+16+2*segx2])
        deltas = struct.unpack('>%dh' % seg, d[s+16+2*segx2:s+16+3*segx2]); rp = s+16+3*segx2
        roffs = struct.unpack('>%dH' % seg, d[rp:rp+segx2])
        for k, (a, b) in enumerate(zip(starts, ends)):
            for c in range(a, b+1):
                if roffs[k] == 0: g = (c+deltas[k]) & 0xFFFF
                else:
                    p = rp+2*k+roffs[k]+2*(c-a); g = struct.unpack('>H', d[p:p+2])[0]
                    if g: g = (g+deltas[k]) & 0xFFFF
                m[c] = g
    return m
cm = cmapd()
old = [0] + [cm[c] for c in cps]
head = bytearray(tbl('head')); ilf = struct.unpack('>h', head[50:52])[0]
lo = T['loca'][0]; gl = T['glyf'][0]
def gdata(g):
    if ilf == 0: a, b = struct.unpack('>HH', d[lo+2*g:lo+2*g+4]); a *= 2; b *= 2
    else: a, b = struct.unpack('>II', d[lo+4*g:lo+4*g+8])
    data = d[gl+a:gl+b]
    if data: assert struct.unpack('>h', data[:2])[0] >= 0, 'composite glyph'
    return data
glyf = b''; loca = [0]
for g in old:
    gd = gdata(g); gd += b'\0' * ((4 - len(gd) % 4) % 4); glyf += gd; loca.append(len(glyf))
locab = struct.pack('>%dI' % len(loca), *loca)
hhea = bytearray(tbl('hhea')); nhm = struct.unpack('>H', hhea[34:36])[0]
hm = T['hmtx'][0]
def metric(g):
    if g < nhm: return struct.unpack('>Hh', d[hm+4*g:hm+4*g+4])
    aw = struct.unpack('>H', d[hm+4*(nhm-1):hm+4*(nhm-1)+2])[0]
    lsb = struct.unpack('>h', d[hm+4*nhm+2*(g-nhm):hm+4*nhm+2*(g-nhm)+2])[0]; return aw, lsb
hmtx = b''.join(struct.pack('>Hh', *metric(g)) for g in old)
struct.pack_into('>H', hhea, 34, len(old))
# advanceWidthMax / extents
aws = [metric(g)[0] for g in old]; struct.pack_into('>H', hhea, 10, max(aws))
maxp = bytearray(tbl('maxp')); struct.pack_into('>H', maxp, 4, len(old))
struct.pack_into('>h', head, 50, 1)
struct.pack_into('>I', head, 8, 0)
# bbox over kept glyphs
bb = [struct.unpack('>4h', gdata(g)[2:10]) for g in old if gdata(g)]
struct.pack_into('>4h', head, 36, min(b[0] for b in bb), min(b[1] for b in bb), max(b[2] for b in bb), max(b[3] for b in bb))
os2 = bytearray(tbl('OS/2')); struct.pack_into('>HH', os2, 64, min(cps), max(cps))
if len(sys.argv) > 5:
    asc, desc = int(sys.argv[4]), int(sys.argv[5])
    struct.pack_into('>hhh', hhea, 4, asc, -desc, 0)
    struct.pack_into('>hhhHH', os2, 68, asc, -desc, 0, asc, desc)
post = bytearray(tbl('post')[:32]); struct.pack_into('>I', post, 0, 0x00030000)
# cmap format 4: one segment per code point + 0xFFFF
segs = [(c, c, (i+1 - c) & 0xFFFF) for i, c in enumerate(cps)] + [(0xFFFF, 0xFFFF, 1)]
sc = len(segs); import math
es = 2 ** int(math.floor(math.log2(sc))); sr = es*2; sel = int(math.log2(es)); rs = sc*2 - sr
sub = struct.pack('>HHHHHHH', 4, 0, 0, sc*2, sr, sel, rs)
sub += struct.pack('>%dH' % sc, *[s[1] for s in segs]) + b'\0\0'
sub += struct.pack('>%dH' % sc, *[s[0] for s in segs])
sub += struct.pack('>%dH' % sc, *[s[2] for s in segs])
sub += struct.pack('>%dH' % sc, *([0]*sc))
sub = sub[:2] + struct.pack('>H', len(sub)) + sub[4:]
cmap = struct.pack('>HH', 0, 2) + struct.pack('>HHI', 0, 3, 20) + struct.pack('>HHI', 3, 1, 20) + sub
out = {'OS/2': bytes(os2), 'cmap': cmap, 'glyf': glyf, 'head': bytes(head), 'hhea': bytes(hhea), 'hmtx': hmtx,
       'loca': locab, 'maxp': bytes(maxp), 'name': tbl('name'), 'post': bytes(post)}
def csum(b):
    b += b'\0' * ((4 - len(b) % 4) % 4); return sum(struct.unpack('>%dI' % (len(b)//4), b)) & 0xFFFFFFFF
tags = sorted(out); nt = len(tags)
es = 2 ** int(math.floor(math.log2(nt))); sr = es*16; sel = int(math.log2(es)); rs = nt*16 - sr
hdr = struct.pack('>IHHHH', 0x00010000, nt, sr, sel, rs)
off = 12 + 16*nt; dirs = b''; body = b''
for t in tags:
    b = out[t]; dirs += struct.pack('>4sIII', t.encode(), csum(b), off + len(body), len(b))
    body += b + b'\0' * ((4 - len(b) % 4) % 4)
font = bytearray(hdr + dirs + body)
adj = (0xB1B0AFBA - csum(bytes(font))) & 0xFFFFFFFF
hoff = 12 + 16*nt + sum(len(out[t]) + (4 - len(out[t]) % 4) % 4 for t in tags[:tags.index('head')])
struct.pack_into('>I', font, hoff + 8, adj)
open(dst, 'wb').write(font)
print('wrote', dst, len(font), 'bytes,', len(old), 'glyphs')
