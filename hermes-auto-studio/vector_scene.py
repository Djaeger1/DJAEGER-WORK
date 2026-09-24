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
subject=(scene.get("subject") or scene.get("visual_goal") or onscreen or topic).strip()
subject_l=subject.lower()
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

# Clean semantic learning board. Generative keyframes intentionally contain no captions:
# text is composited after AI-video generation to prevent warped AI text.
svg.append(f'<rect x="315" y="80" width="650" height="440" rx="42" fill="#FFFFFF" stroke="{ink}" stroke-width="5" filter="url(#shadow)"/>')
svg.append(f'<ellipse cx="640" cy="450" rx="245" ry="34" fill="#D7E3EA" opacity=".55"/>')

def animal_cat(cx,cy):
    return [
      f'<circle cx="{cx}" cy="{cy}" r="108" fill="#F4A261" stroke="#4B4B55" stroke-width="6"/>',
      f'<path d="M{cx-82} {cy-72} L{cx-105} {cy-160} L{cx-28} {cy-105}Z" fill="#F4A261" stroke="#4B4B55" stroke-width="6"/>',
      f'<path d="M{cx+82} {cy-72} L{cx+105} {cy-160} L{cx+28} {cy-105}Z" fill="#F4A261" stroke="#4B4B55" stroke-width="6"/>',
      f'<circle cx="{cx-38}" cy="{cy-12}" r="11" fill="#242A33"/><circle cx="{cx+38}" cy="{cy-12}" r="11" fill="#242A33"/>',
      f'<path d="M{cx} {cy+10} l-12 12 h24z" fill="#E76F51"/>',
      f'<path d="M{cx-18} {cy+39} Q{cx} {cy+53} {cx+18} {cy+39}" fill="none" stroke="#6D4C41" stroke-width="6"/>',
      f'<path d="M{cx-24} {cy+20} H{cx-120} M{cx+24} {cy+20} H{cx+120} M{cx-25} {cy+38} L{cx-115} {cy+62} M{cx+25} {cy+38} L{cx+115} {cy+62}" stroke="#6D4C41" stroke-width="4"/>'
    ]

def animal_elephant(cx,cy):
    return [
      f'<ellipse cx="{cx-93}" cy="{cy}" rx="92" ry="112" fill="#A9B8C7" stroke="#4B5563" stroke-width="6"/>',
      f'<ellipse cx="{cx+93}" cy="{cy}" rx="92" ry="112" fill="#A9B8C7" stroke="#4B5563" stroke-width="6"/>',
      f'<circle cx="{cx}" cy="{cy}" r="112" fill="#B8C6D1" stroke="#4B5563" stroke-width="6"/>',
      f'<circle cx="{cx-38}" cy="{cy-18}" r="10" fill="#20242E"/><circle cx="{cx+38}" cy="{cy-18}" r="10" fill="#20242E"/>',
      f'<path d="M{cx} {cy+28} C{cx+4} {cy+100} {cx+22} {cy+160} {cx-20} {cy+184}" fill="none" stroke="#9DABB7" stroke-width="42" stroke-linecap="round"/>'
    ]

def animal_chicken(cx,cy):
    return [
      f'<ellipse cx="{cx}" cy="{cy+28}" rx="118" ry="92" fill="#FFD166" stroke="#6B5C36" stroke-width="6"/>',
      f'<circle cx="{cx+60}" cy="{cy-58}" r="58" fill="#FFE08A" stroke="#6B5C36" stroke-width="6"/>',
      f'<path d="M{cx+112} {cy-60} l48 18 -48 18z" fill="#F28C28"/>',
      f'<circle cx="{cx+76}" cy="{cy-72}" r="8" fill="#222831"/>',
      f'<circle cx="{cx+30}" cy="{cy-118}" r="20" fill="#E85D5D"/><circle cx="{cx+58}" cy="{cy-126}" r="20" fill="#E85D5D"/>',
      f'<path d="M{cx-75} {cy+100} v55 M{cx-15} {cy+105} v52" stroke="#F28C28" stroke-width="10" stroke-linecap="round"/>'
    ]

def animal_dog(cx,cy):
    return [
      f'<circle cx="{cx}" cy="{cy}" r="108" fill="#C98B5B" stroke="#4D3B32" stroke-width="6"/>',
      f'<ellipse cx="{cx-102}" cy="{cy-38}" rx="42" ry="78" fill="#9E6846" transform="rotate(-20 {cx-102} {cy-38})"/>',
      f'<ellipse cx="{cx+102}" cy="{cy-38}" rx="42" ry="78" fill="#9E6846" transform="rotate(20 {cx+102} {cy-38})"/>',
      f'<circle cx="{cx-38}" cy="{cy-15}" r="10" fill="#20242E"/><circle cx="{cx+38}" cy="{cy-15}" r="10" fill="#20242E"/>',
      f'<ellipse cx="{cx}" cy="{cy+25}" rx="20" ry="15" fill="#2E3038"/>',
      f'<path d="M{cx-30} {cy+52} Q{cx} {cy+75} {cx+30} {cy+52}" fill="none" stroke="#6B3E35" stroke-width="6"/>'
    ]

cx,iy=640,338
if any(k in subject_l for k in ("kucing","cat")):
    svg += animal_cat(cx,iy)
elif any(k in subject_l for k in ("gajah","elephant")):
    svg += animal_elephant(cx,iy)
elif any(k in subject_l for k in ("ayam","chicken","rooster")):
    svg += animal_chicken(cx,iy)
elif any(k in subject_l for k in ("anjing","dog","puppy")):
    svg += animal_dog(cx,iy)
elif any(k in subject_l for k in ("segitiga","triangle")):
    svg.append(f'<path d="M{cx} 190 L{cx-155} 455 L{cx+155} 455Z" fill="#FFD166" stroke="{ink}" stroke-width="9"/>')
elif any(k in subject_l for k in ("lingkaran","circle","bulat")):
    svg.append(f'<circle cx="{cx}" cy="{iy}" r="145" fill="#65B5FF" stroke="{ink}" stroke-width="9"/>')
elif any(k in subject_l for k in ("persegi","square","kotak")):
    svg.append(f'<rect x="{cx-135}" y="{iy-135}" width="270" height="270" rx="24" fill="#6ED7A9" stroke="{ink}" stroke-width="9"/>')
elif any(k in subject_l for k in ("merah","red")):
    svg.append(f'<circle cx="{cx}" cy="{iy}" r="145" fill="#F05D5E" stroke="{ink}" stroke-width="9"/>')
elif any(k in subject_l for k in ("biru","blue")):
    svg.append(f'<circle cx="{cx}" cy="{iy}" r="145" fill="#4EA5FF" stroke="{ink}" stroke-width="9"/>')
elif any(k in subject_l for k in ("hijau","green")):
    svg.append(f'<circle cx="{cx}" cy="{iy}" r="145" fill="#63C174" stroke="{ink}" stroke-width="9"/>')
else:
    # Generic but clean learning objects, deliberately visual rather than text.
    for dx,col in [(-120,"#FF7B7B"),(-40,"#FFD166"),(40,"#6ED7A9"),(120,"#65B5FF")]:
        svg.append(f'<circle cx="{cx+dx}" cy="{iy}" r="48" fill="{col}" stroke="{ink}" stroke-width="4"/>')

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

# No generated text overlays in the AI keyframe. Captions are added later in post-production.
svg.append("</svg>")
Path(a.output).write_text("".join(svg), encoding="utf-8")
