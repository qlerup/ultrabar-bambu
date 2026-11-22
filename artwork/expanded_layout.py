"""Bambu's expanded card. Boxes use thousandths of their parent."""

ALL = [0, 0, 1000, 1000]
CARD = '#23333f'
MUTED = '#a5b9c3'


def text(label, box, size, **style):
    return dict(op='text', text=label, box=box, size=size, **style)


def background():
    return dict(op='rect', box=ALL, color=CARD, radius=9)


def thermometer(title, field):
    return [
        background(),
        text(title, [40, 185, 920, 270], 11, align='center', color=MUTED),
        text('{printer.' + field + '}\u00b0C', [40, 460, 920, 350], 15, align='center', bold=True),
    ]


def nozzle_cells():
    label_box = [65, 180, 465, 265]
    temperature_box = [65, 480, 465, 340]
    material_box = [550, 180, 390, 265]
    color_box = [550, 480, 390, 340]
    return [
        background(),
        text('hotend.{item.tool}', label_box, 13, bold=True, lookup='labels',
             fallback='Hotend {item.tool}', color=MUTED),
        text('{item.temperature}\u00b0C', temperature_box, 14, bold=True),
        text('{item.material}', material_box, 12, when='item.material', align='center'),
        text('filament.{item.filamentProfile}', material_box, 12, unless='item.material',
             lookup='labels', align='center'),
        dict(op='swatch', box=color_box, size=13, color='@item.color', radius=2, when='item.color'),
        text('\u2014', color_box, 9, unless='item.color', align='center', color=MUTED),
    ]


def details():
    # The printer heading shares the top of the card with the tool grid.
    nodes = [
        text('{values.title}  ·  {values.status}', [50, 52, 205, 85], 14, bold=True),
        text('{values.progress}%', [50, 285, 205, 178], 27, bold=True, when='printer.fresh'),
        text('\u2014', [50, 285, 205, 178], 27, bold=True, unless='printer.fresh'),
        text('{values.job}', [50, 475, 205, 73], 11, when='settings.showJob'),
        dict(op='bar', box=[50, 565, 205, 13], source='values.progress', color='$accent', radius=2),
        text('{values.remaining}', [50, 605, 205, 70], 10, color=MUTED, when='settings.showRemaining'),
        text('Layer {values.layers}', [50, 690, 205, 70], 10, color=MUTED, when='settings.showLayers'),
    ]
    grid = dict(op='grid', box=[0, 0, 785, 1000], source='printer.nozzle_readings',
                children=nozzle_cells(), gap=5)
    tool_area = [
        dict(grid, columns=3, key='tool', slots=9, when='printer.model', equals='h3c'),
        dict(grid, rows=2, when='printer.model', equals='h2c'),
        dict(grid, rows=1, unless='printer.model', oneOf=['h2c', 'h3c']),
        dict(op='group', box=[803, 0, 197, 485], children=thermometer('Chamber', 'chamber_temper')),
        dict(op='group', box=[803, 515, 197, 485], children=thermometer('Bed', 'bed_temper')),
    ]
    nodes.append(dict(op='group', box=[280, 62, 670, 845], children=tool_area,
                      when='settings.showTemperatures'))
    return nodes
