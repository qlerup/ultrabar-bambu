"""App-owned expanded printer card. Boxes use thousandths of their parent."""
ALL=[0,0,1000,1000]
CARD='#23333f'
MUTED='#a5b9c3'

def text(label,box,size,**style):
    return dict(op='text',text=label,box=box,size=size,**style)

def background(box,color= CARD,radius=14):
    return dict(op='rect',box=box,color=color,radius=radius)

def thermometer(title,field):
    return [background(ALL),text(title,[10,90,980,310],13,align='center',color=MUTED),
            text('{printer.'+field+'}\u00b0C',[10,400,980,490],26,align='center',bold=True)]

def nozzle_cells():
    return [background(ALL),text('nozzle.{item.tool}',[15,50,970,525],22,bold=True,align='center',
                lookup='labels',fallback='T{item.tool}',suffix='   {item.temperature}\u00b0C',unless='narrow'),
            text('T{item.tool}   {item.temperature}\u00b0C',[15,50,970,525],21,bold=True,align='center',when='narrow'),
            text('{item.material}',[8,610,750,350],17,when='item.material',align='center',bold=True),
            text('filament.{item.filamentProfile}',[8,610,750,350],17,unless='item.material',lookup='labels',align='center',bold=True),
            dict(op='swatch',box=[760,610,195,350],size=18,color='@item.color',radius=2,when='item.color')]

def details():
    nodes=[text('{values.title}',[18,4,735,95],17,bold=True),
           text('{values.status}',[765,4,210,95],13,align='right',color='$accent'),
           text('{values.progress}%',[18,270,225,240],42,bold=True,when='printer.fresh'),
           text('\u2014',[18,270,225,240],42,bold=True,unless='printer.fresh'),
           text('{values.job}',[18,520,225,95],14,when='settings.showJob'),
           dict(op='bar',box=[18,635,225,22],source='values.progress',color='$accent',radius=3),
           text('{values.remaining}',[18,685,225,88],13,color=MUTED,when='settings.showRemaining'),
           text('Layer {values.layers}',[18,785,225,88],13,color=MUTED,when='settings.showLayers')]
    cell=nozzle_cells()
    grid=dict(op='grid',box=[0,0,785,1000],source='printer.nozzle_readings',children=cell,gap=4)
    tool_area=[dict(grid,columns=3,key='tool',slots=9,when='printer.model',equals='h3c'),
               dict(grid,rows=2,when='printer.model',equals='h2c'),
               dict(grid,rows=1,unless='printer.model',oneOf=['h2c','h3c'])]
    tool_area.extend([dict(op='group',box=[795,0,205,485],children=thermometer('Chamber','chamber_temper')),
                      dict(op='group',box=[795,510,205,490],children=thermometer('Bed','bed_temper'))])
    nodes.append(dict(op='group',box=[260,125,725,850],children=tool_area,when='settings.showTemperatures'))
    return nodes
