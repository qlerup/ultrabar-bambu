"""Bambu-owned artwork and presets. Rebuild widget.json without changing Studio."""
import json
from pathlib import Path

MODELS = [
    ('generic', 'Other / model not selected'), ('a1', 'A1'), ('a1-mini', 'A1 mini'), ('a2l', 'A2L'),
    ('p1p', 'P1P'), ('p1s', 'P1S'), ('p2s', 'P2S'), ('x1', 'X1'), ('x1c', 'X1 Carbon'),
    ('x1e', 'X1E'), ('x2d', 'X2D'), ('h2d', 'H2D'), ('h2d-pro', 'H2D Pro'),
    ('h2s', 'H2S'), ('h2c', 'H2C'), ('h3c', 'H3C'),
]


def artwork(model):
    shapes=[]
    def shape(kind, points, color):shapes.append(dict(type=kind,points=points,fill=color))
    def rect(a,b,c,d,color):shape('rect',[a,b,c,d],color)
    def roundrect(a,b,c,d,r,color):shape('roundRect',[a,b,c,d,r,r],color)
    def circle(x,y,r,color):shape('oval',[x-r,y-r,x+r,y+r],color)
    if model=='generic':return []
    if model in ('a1','a1-mini','a2l'):
        mini=model=='a1-mini'
        roundrect(8,82,92,93,4,'#afb9bc');rect(18,70,70,82,'#526166')
        rect(65 if mini else 73,10,72 if mini else 80,85,'#d5dbdd')
        if not mini:rect(18,10,25,85,'#d5dbdd')
        rail=19 if model=='a2l' else 27
        rect(12,rail,86,rail+6,'#d5dbdd');roundrect(37,rail-3,51,rail+15,3,'#e9edef');rect(41,rail+12,47,rail+17,'accent')
        roundrect(74,74,89,83,2,'#263238')
        if model!='a2l':
            circle(81 if mini else 51,14 if mini else 10,9,'#b7c0c3');circle(81 if mini else 51,14 if mini else 10,3,'#46565b')
        return shapes
    left,right=(5,81) if model.startswith('h') else (16,74)
    shell={'x1':'#d7dad6','x1c':'#bfc6c9','x1e':'#bfc6c9','h2d-pro':'#747c82','p2s':'#69767c','x2d':'#9ca4a6'}.get(model,'#505c61')
    shape('polygon',[right,10,96,19,96,85,right,94],'#263238')
    roundrect(left,10,right,94,3,shell);rect(left+5,22,right-5,84,'#172329')
    if model=='p1p':
        rect(left+7,16,left+12,83,shell);rect(right-12,16,right-7,83,shell);rect(left+7,35,right-7,41,shell)
        roundrect((left+right)/2-7,32,(left+right)/2+7,48,2,'#edf1f2')
    else:
        rect(left+8,27,right-8,81,'#334950');rect(left+12,32,right-12,77,'#142126');rect(left+12,37,right-12,40,'#a9b4b8')
        rect((left+right)/2-7,38,(left+right)/2+7,51,'#69787f')
        if model in ('h2d','h2d-pro','x2d'):
            rect((left+right)/2-6,50,(left+right)/2-2,54,'accent');rect((left+right)/2+2,50,(left+right)/2+6,54,'accent')
        roundrect(right-11,50,right-8,61,1,'#a8b8bc')
    rect(left+11,76,right-11,80,'#8b9c9f');mid=(left+right)/2
    shape('polygon',[mid-9,75,mid-9,64,mid,59,mid+9,64,mid+9,75],'#334b42');rect(left+5,13,left+17,18,'accent')
    if model=='x1e':circle(right-7,16,2,'#d4b166')
    if model in ('p2s','x2d','h2d-pro'):roundrect(left+22,12,left+38,20,2,'#172329')
    if model=='h2c':
        rect(left+11,5,right-10,10,'#7b898f')
        for i in range(6):circle(left+17+i*8,7,2,'accent' if i%2==0 else '#bfc6c9')
    if model=='h3c':
        rect(left,10,right,22,'#bfc6c9');roundrect(left+2,0,right+7,17,3,'#202f34')
        for i in range(4):circle(left+13+i*14,5,5,'#6d8288')
    return shapes


def main():
    path=Path(__file__).resolve().parents[1]/'widget.json'
    package=json.loads(path.read_text(encoding='utf-8'));panel=package['widgets'][0]['studio']
    model=next(field for field in panel['connection']['fields'] if field['key']=='model')
    model['required']=True
    selectable=[(value,label) for value,label in MODELS if value!='generic']
    model['options']=[dict(value=value,label=label) for value,label in selectable]
    panel['presentation']=dict(api=1,source=dict(scope='connection',key='model'),presets=[dict(value=value,artwork=artwork(value),labels={'nozzle.0':'LEFT NOZZLE','nozzle.1':'RIGHT NOZZLE'} if value in ('h2d','h2d-pro') else {}) for value,_ in selectable])
    for preset in panel['presentation']['presets']:
        if preset['value'] in ('h2c', 'h3c'):
            preset['nozzleGrid'] = dict(materialSize=17, swatchSize=18, verticalInset=5,
                                       sideTemperatures=["chamber_temper","bed_temper"])
            preset['minTemperatureRows'] = 3 if preset['value']=='h3c' else 2
        if preset['value']=='h3c':
            preset['labels']={f'nozzle.{tool}':f'NOZZLE T{tool}' for tool in range(9)}
        if preset['value'] in ('h3c','h2c'):
            preset['labels'].update(chamber_temper='Chamber',bed_temper='Bed')
            preset['labels'].update({'filament.'+key: label for key,label in {
                'GFG02':'PETG HF','GFG96':'PETG HF','GFG00':'PETG','GFG99':'PETG',
                'GFA00':'PLA','GFA01':'PLA Matte','GFL99':'PLA','GFB00':'ABS','GFB99':'ABS',
                'GFB01':'ASA','GFB50':'ABS-GF','GFG50':'PETG-CF','GFC00':'PC'
            }.items()})
    raw=json.dumps(package,indent=2)
    if len(raw.encode('utf-8'))>=65536:
        # Keep each shape on one line and the surrounding package easy to review.
        import re
        raw=re.sub(r'\{\s*"type": "(?:rect|roundRect|oval|polygon)",\s*"points": \[[^\]]*\],\s*"fill": "[^"]+"\s*\}',
                   lambda match: json.dumps(json.loads(match.group())),raw)
    path.write_text(raw+'\n',encoding='utf-8',newline='\n')


if __name__=='__main__':main()
