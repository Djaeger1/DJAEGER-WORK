#!/usr/bin/env python3
import argparse, json, math
from pathlib import Path
from xml.sax.saxutils import escape

p=argparse.ArgumentParser()
p.add_argument("--bible", required=True)
p.add_argument("--scene", required=True)
p.add_argument("--topic", required=True)
p.add_argument("--cast", required=True)
p.add_argument("--number", type=int, required=True)
p.add_argument("--output", required=True)
a=p.parse_args()

bible=json.loads(Path(a.bible).read_text(encoding="utf-8"))
scene=json.loads(a.scene)
cast=[x.strip() for x in a.cast.split(",") if x.strip()]
topic=a.topic.split("|")[0].strip()
onscreen=(scene.get("on_screen_text") or "").strip()
purpose=(scene.get("purpose") or "LEARN").replace("_"," ").title()
voice=(scene.get("voice_over") or "").strip()
if not onscreen:
    onscreen=topic

W,H=1280,720
palettes=[
    ("#FFF3D7","#BFE8FF","#FFD56A","#4F7CAC"),
    ("#F0F9E8","#CDECCF","#FFB86B","#4E8F6B"),
    ("#F6EDFF","#D8C7FF","#FFCCEF","#7251A6"),
    ("#FFF0F0","#FFD0D0","#FFD166","#B45F6B"),
    ("#EAF7FF","#C8EEFF","#A7E8BD","#437C90"),
    ("#FFF8E6","#FFE0A8","#CDE7FF","#7A6AAE"),
]
bg1,bg2,accent,ink=palettes[(a.number-1)%len(palettes)]

def t(x): return escape(str(x))
def el(tag, **kw):
    attrs=" ".join(f'{k.replace("_","-")}="{t(v)}"' for k,v in kw.items())
    return f"<{tag} {attrs}/>"
def text(x,y,s,size=42,weight=700,fill="#27364A",anchor="middle"):
    return f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="DejaVu Sans, sans-serif" font-size="{size}" font-weight="{weight}" fill="{fill}">{t(s)}</text>'

svg=[f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{bg1}"/><stop offset="1" stop-color="{bg2}"/></linearGradient>
 <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="7" stdDeviation="7" flood-color="#203040" flood-opacity=".16"/></filter>
</defs>
<rect width="1280" height="720" fill="url(#bg)"/>
<circle cx="1100" cy="105" r="64" fill="{accent}" opacity=".35"/>
<circle cx="105" cy="110" r="40" fill="#FFFFFF" opacity=".65"/>
<circle cx="160" cy="82" r="52" fill="#FFFFFF" opacity=".45"/>
<path d="M0 610 Q180 550 350 615 T720 605 T1280 600 V720 H0Z" fill="#FFFFFF" opacity=".65"/>
''']

# Learning board.
svg.append(f'<rect x="315" y="80" width="650" height="440" rx="42" fill="#FFFFFF" stroke="{ink}" stroke-width="5" filter="url(#shadow)"/>')
svg.append(f'<rect x="350" y="116" width="580" height="68" rx="24" fill="{accent}" opacity=".32"/>')
svg.append(text(640,161,purpose,28,800,ink))
# Split long text into simple lines.
words=onscreen.split()
lines=[]; cur=[]
for w in words:
    if len(" ".join(cur+[w]))>24 and cur:
        lines.append(" ".join(cur));cur=[w]
    else: cur.append(w)
if cur: lines.append(" ".join(cur))
lines=lines[:3]
start=285-(len(lines)-1)*38
for i,line in enumerate(lines):
    svg.append(text(640,start+i*76,line,50 if len(line)<18 else 42,800,"#25364D"))

# Visual learning icons, deterministic by scene.
cx=640
iy=430
if a.number in (1,6):
    for dx, col in [(-120,"#FF7B7B"),(-40,"#FFD166"),(40,"#6ED7A9"),(120,"#65B5FF")]:
        svg.append(f'<path d="M {cx+dx} {iy+20} l 18 -36 40 -6 -29 -28 8 -40 -37 19 -37 -19 8 40 -29 28 40 6 z" fill="{col}" opacity=".9"/>')
elif a.number in (2,3):
    for k,(dx,col) in enumerate([(-150,"#FF7B7B"),(-50,"#FFD166"),(50,"#6ED7A9"),(150,"#65B5FF")],1):
        svg.append(f'<circle cx="{cx+dx}" cy="{iy}" r="42" fill="{col}"/>')
        svg.append(text(cx+dx,iy+14,k,34,800,"#FFFFFF"))
elif a.number==4:
    for dx,col in [(-120,"#65B5FF"),(0,"#FF7B7B"),(120,"#6ED7A9")]:
        svg.append(f'<rect x="{cx+dx-45}" y="{iy-45}" width="90" height="90" rx="20" fill="{col}"/>')
else:
    svg.append(f'<path d="M520 {iy+20} l55 55 125 -145" fill="none" stroke="#55A66F" stroke-width="28" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<circle cx="810" cy="{iy}" r="64" fill="#FFD166" opacity=".9"/>')

# Character primitives.
def head(x,y,skin):
    return [f'<circle cx="{x}" cy="{y}" r="54" fill="{skin}" stroke="#3C4050" stroke-width="4"/>',
            f'<circle cx="{x-18}" cy="{y+4}" r="6" fill="#2D2D35"/>',
            f'<circle cx="{x+18}" cy="{y+4}" r="6" fill="#2D2D35"/>',
            f'<path d="M{x-18} {y+28} Q{x} {y+42} {x+18} {y+28}" fill="none" stroke="#A55353" stroke-width="5" stroke-linecap="round"/>']

def draw_nara(x,base,s=1.0):
    y=base-225*s; out=[]
    out += head(x,y,"#C98D67")
    out += [f'<path d="M{x-51} {y-20} Q{x-20} {y-78} {x+48} {y-38} Q{x+30} {y-60} {x+5} {y-58} Q{x-25} {y-62} {x-51} {y-20}" fill="#202431"/>',
            f'<path d="M{x-28} {y-52} q-24 -24 -8 -43 q20 8 18 28" fill="none" stroke="#202431" stroke-width="12" stroke-linecap="round"/>',
            f'<polygon points="{x-40},{y-45} {x-33},{y-60} {x-25},{y-45} {x-10},{y-42} {x-22},{y-32} {x-18},{y-16} {x-33},{y-25} {x-47},{y-16} {x-43},{y-32} {x-55},{y-42}" fill="#FFD166"/>',
            f'<rect x="{x-48}" y="{y+55}" width="96" height="110" rx="28" fill="#2DA6A1" stroke="#3C4050" stroke-width="4"/>',
            f'<path d="M{x} {y+58} V{y+145}" stroke="#F7F1E3" stroke-width="5"/>',
            f'<rect x="{x-42}" y="{y+160}" width="38" height="65" rx="15" fill="#E4B63C"/><rect x="{x+4}" y="{y+160}" width="38" height="65" rx="15" fill="#E4B63C"/>',
            f'<ellipse cx="{x-24}" cy="{base}" rx="34" ry="16" fill="#E34C4C"/><ellipse cx="{x+25}" cy="{base}" rx="34" ry="16" fill="#E34C4C"/>']
    return out

def draw_bimo(x,base,s=1.0):
    y=base-225*s; out=[]
    out += head(x,y,"#B97E58")
    out += [f'<path d="M{x-50} {y-27} Q{x} {y-80} {x+50} {y-27} L{x+42} {y-58} Q{x} {y-82} {x-42} {y-58}Z" fill="#171C29"/>',
            f'<circle cx="{x-19}" cy="{y+3}" r="17" fill="none" stroke="#323746" stroke-width="5"/><circle cx="{x+19}" cy="{y+3}" r="17" fill="none" stroke="#323746" stroke-width="5"/><path d="M{x-2} {y+3} H{x+2}" stroke="#323746" stroke-width="5"/>',
            f'<rect x="{x-49}" y="{y+55}" width="98" height="110" rx="24" fill="#ED8B36" stroke="#3C4050" stroke-width="4"/>',
            f'<rect x="{x-24}" y="{y+58}" width="48" height="100" rx="16" fill="#62B7E8"/>',
            f'<rect x="{x-42}" y="{y+160}" width="38" height="65" rx="15" fill="#344A73"/><rect x="{x+4}" y="{y+160}" width="38" height="65" rx="15" fill="#344A73"/>',
            f'<ellipse cx="{x-24}" cy="{base}" rx="34" ry="16" fill="#F8FAFC"/><ellipse cx="{x+25}" cy="{base}" rx="34" ry="16" fill="#F8FAFC"/>']
    return out

def draw_sasa(x,base,s=1.0):
    y=base-225*s; out=[]
    out += head(x,y,"#9F694C")
    out += [f'<ellipse cx="{x-62}" cy="{y-18}" rx="24" ry="40" fill="#1E2029"/><ellipse cx="{x+62}" cy="{y-18}" rx="24" ry="40" fill="#1E2029"/>',
            f'<path d="M{x-50} {y-30} Q{x} {y-78} {x+50} {y-30} Q{x} {y-60} {x-50} {y-30}" fill="#1E2029"/>',
            f'<circle cx="{x-63}" cy="{y-30}" r="7" fill="#7C4DB3"/><circle cx="{x+63}" cy="{y-30}" r="7" fill="#7C4DB3"/>',
            f'<path d="M{x+30} {y-48} l8 -12 8 12 14 2 -10 10 3 14 -15 -7 -13 7 3 -14 -11 -10z" fill="#FFD166"/>',
            f'<rect x="{x-48}" y="{y+55}" width="96" height="110" rx="25" fill="#F3D45E" stroke="#3C4050" stroke-width="4"/>',
            f'<path d="M{x-36} {y+75} H{x+36} V{y+165} H{x-36}Z" fill="#7656B6"/>',
            f'<rect x="{x-42}" y="{y+160}" width="38" height="65" rx="15" fill="#7656B6"/><rect x="{x+4}" y="{y+160}" width="38" height="65" rx="15" fill="#7656B6"/>',
            f'<ellipse cx="{x-24}" cy="{base}" rx="34" ry="16" fill="#72D2B2"/><ellipse cx="{x+25}" cy="{base}" rx="34" ry="16" fill="#72D2B2"/>']
    return out

def draw_pip(x,y):
    return [f'<ellipse cx="{x}" cy="{y}" rx="54" ry="61" fill="#26B9C4" stroke="#3C4050" stroke-width="4"/>',
            f'<ellipse cx="{x}" cy="{y+20}" rx="32" ry="36" fill="#F3CF59"/>',
            f'<circle cx="{x-18}" cy="{y-12}" r="8" fill="#20242E"/><circle cx="{x+18}" cy="{y-12}" r="8" fill="#20242E"/>',
            f'<path d="M{x-8} {y+4} L{x+12} {y+4} L{x+2} {y+16}Z" fill="#F28C28"/>',
            f'<path d="M{x-12} {y-58} q-8 -25 8 -32 M{x+4} {y-58} q8 -25 20 -26" fill="none" stroke="#26B9C4" stroke-width="9" stroke-linecap="round"/>',
            f'<path d="M{x-45} {y+2} q-35 12 -28 40 M{x+45} {y+2} q35 12 28 40" fill="none" stroke="#26B9C4" stroke-width="18" stroke-linecap="round"/>']

people=[x for x in cast if x!="Pip"]
positions={1:[170],2:[150,1130],3:[135,640,1145]}
xs=positions.get(len(people),[150,1130])
for name,x in zip(people,xs):
    if name=="Nara": svg += draw_nara(x,675)
    elif name=="Bimo": svg += draw_bimo(x,675)
    elif name=="Sasa": svg += draw_sasa(x,675)
if "Pip" in cast:
    px=1080 if len(people)<2 else 1035
    svg += draw_pip(px,565)

# Footer caption and small continuity badge.
caption=voice if len(voice)<=90 else voice[:87]+"..."
svg.append(f'<rect x="300" y="548" width="680" height="96" rx="30" fill="#FFFFFF" opacity=".91" filter="url(#shadow)"/>')
svg.append(text(640,592,caption,25,600,"#35445B"))
svg.append(text(640,625,"Nara • Bimo • Sasa • Pip",18,700,ink))
svg.append(f'<rect x="1020" y="34" width="220" height="38" rx="19" fill="#FFFFFF" opacity=".8"/>')
svg.append(text(1130,60,f"DJAEGER KIDS • {a.number}",16,800,ink))
svg.append("</svg>")
Path(a.output).write_text("".join(svg), encoding="utf-8")
