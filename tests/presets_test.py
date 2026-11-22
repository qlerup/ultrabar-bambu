"""Check the shipped package data, independently of a Studio checkout."""
import importlib.util
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
module=importlib.util.spec_from_file_location('printer_artwork',root/'artwork/printer_presets.py')
artwork=importlib.util.module_from_spec(module);module.loader.exec_module(artwork)
panel=json.loads((root/'widget.json').read_text(encoding='utf-8'))['widgets'][0]['studio']
presets={preset['value']:preset for preset in panel['presentation']['presets']}
models=next(field for field in panel['connection']['fields'] if field['key']=='model')
assert {option['value'] for option in models['options']}==set(presets)
assert {k:v for k,v in presets['h2d']['labels'].items() if k.startswith('nozzle.')}=={'nozzle.0':'LEFT NOZZLE','nozzle.1':'RIGHT NOZZLE'}
assert panel['presentation']['source']==dict(scope='connection',key='model')
expanded=panel['layout']['expanded']
grids=[node for group in expanded for node in group.get('children',[]) if node['op']=='grid']
assert any(g.get('equals')=='h3c' and g['columns']==3 and g['slots']==9 for g in grids)
assert any(g.get('equals')=='h2c' and g['rows']==2 for g in grids)
for grid in grids:
    cells = grid['children']
    name, temperature, material = [
        next(cell['box'] for cell in cells if cell.get('text') == field)
        for field in ['hotend.{item.tool}', '{item.temperature}\u00b0C', '{item.material}']
    ]
    color = next(cell['box'] for cell in cells if cell['op'] == 'swatch')
    assert name[0] == temperature[0] and material[0] == color[0]
    assert max(name[0] + name[2], temperature[0] + temperature[2]) <= material[0]
    assert name[1] == material[1] and temperature[1] == color[1]
assert models['required'] and 'generic' not in presets
for model in presets:
    hotend_count = {'h3c': 9, 'h2c': 7}.get(model, 2)
    assert [presets[model]['labels'][f'hotend.{tool}'] for tool in range(hotend_count)] == [
        f'Hotend {number}' for number in range(1, hotend_count + 1)
    ]
    assert presets[model]['artwork']==artwork.artwork(model),model
    assert (bool(presets[model]['artwork']))==(model!='generic')
print('Package presets passed: H2D nozzle orientation, model choices and reproducible artwork')
