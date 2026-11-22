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
assert presets['h2d']['labels']=={'nozzle.0':'LEFT NOZZLE','nozzle.1':'RIGHT NOZZLE'}
assert panel['presentation']['source']==dict(scope='connection',key='model')
assert {key for key,preset in presets.items() if preset.get('minTemperatureRows')==2}=={'h2c','h3c'}
assert len(presets['h3c']['labels'])==9
assert models['required'] and 'generic' not in presets
for model in presets:
    assert presets[model]['artwork']==artwork.artwork(model),model
    assert (bool(presets[model]['artwork']))==(model!='generic')
print('Package presets passed: H2D nozzle orientation, model choices and reproducible artwork')
