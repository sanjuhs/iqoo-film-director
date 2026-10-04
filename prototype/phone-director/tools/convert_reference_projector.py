#!/usr/bin/env python3
"""Pre-event research utility: verify the exact public source and convert vision only.

Run with the isolated converter environment. Downloads, core conversion and training
are deliberately outside this tool. Model inputs/outputs stay in ignored private/.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
RUNTIME = ROOT / 'prototype/phone-director/app/src/main/cpp/llama.cpp'
RUNTIME_REV = '11fe02151f79c41d0d4af7da708755d73b9c0da6'
SOURCE_REV = '2fc06364715b967f1860aea9cf38778875588b17'
SOURCE_NAME = 'model.safetensors-00001-of-00001.safetensors'
SOURCE_SIZE = 1746942600
SOURCE_SHA = '04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696'
SOURCE_METADATA = {
    'config.json': 'b90b86f35c8e6925ef74ee04d0e758f0a845c83a42089ad82bbaa948de9b4204',
    'model.safetensors.index.json': 'd8a08838a613b025eb7952ed9db11696213e57e76a375661ef5c12f9dd5dcf4e',
    'preprocessor_config.json': '27225450ac9c6529872ee1924fcb0962ff5634834f817040f444118116f4e516',
    'LICENSE': 'bbedc3fda3305820b977265f01b8619d87570a6739de3a5582c3464840f1e57a',
    'README.md': '87a163af54f32fa608a0f8d3ac67945c53dd2b4a7c96740b3d7fdc28e8458864',
}

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while block := stream.read(1024 * 1024):
            h.update(block)
    return h.hexdigest()

def private_path(path):
    path = path.resolve()
    if not path.is_relative_to((ROOT / 'private').resolve()):
        raise ValueError('Model files must stay under the ignored private directory')
    return path

def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument('--source', type=Path, default=ROOT / 'private/qwen-vision-source')
    args.add_argument('--output', type=Path, default=ROOT / 'private/director-mmproj.gguf')
    args.add_argument('--record', type=Path, default=ROOT / 'private/evidence/vision-projector-provenance.json')
    options = args.parse_args()
    source, output, record = map(private_path, (options.source, options.output, options.record))
    if output.exists():
        raise ValueError('Output already exists; preserve it or choose a fresh ignored output path')
    revision = subprocess.check_output(['git', '-C', str(RUNTIME), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != RUNTIME_REV:
        raise ValueError('The converter checkout does not match the required pin')
    shard = source / SOURCE_NAME
    if shard.stat().st_size != SOURCE_SIZE or digest(shard) != SOURCE_SHA:
        raise ValueError('Official source size/SHA verification failed; conversion stopped')
    for name, expected in SOURCE_METADATA.items():
        if digest(source / name) != expected:
            raise ValueError('Pinned source metadata digest mismatch: ' + name)
    config = json.loads((source / 'config.json').read_text())
    if config.get('architectures') != ['Qwen3_5ForConditionalGeneration']:
        raise ValueError('Unexpected official checkpoint architecture')
    index = json.loads((source / 'model.safetensors.index.json').read_text())
    vision = [name for name in index['weight_map'] if name.startswith('model.visual.')]
    if len(vision) != 153 or any(index['weight_map'][name] != SOURCE_NAME for name in vision):
        raise ValueError('Unexpected vision tensor inventory')
    if not (source / 'LICENSE').is_file():
        raise ValueError('Retain the official model license before conversion')
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(RUNTIME / 'convert_hf_to_gguf.py'), str(source),
                    '--mmproj', '--outtype', 'f16', '--outfile', str(output)], check=True)
    # Inspect the converted tensor inventory with the same pinned GGUF implementation.
    sys.path.insert(0, str(RUNTIME / 'gguf-py'))
    from gguf import GGUFReader
    reader = GGUFReader(str(output))
    names = [tensor.name for tensor in reader.tensors]
    if not names or any(name.startswith(('blk.', 'token_embd.', 'output.')) for name in names):
        raise ValueError('Unexpected language tensors in projector output; do not install it')
    result = dict(schema='minifilm.reference-projector.provenance.v1', preEventResearch=True,
                  sourceRepo='Qwen/Qwen3.5-0.8B', sourceRevision=SOURCE_REV,
                  sourceShard=SOURCE_NAME, sourceBytes=SOURCE_SIZE, sourceSha256=SOURCE_SHA,
                  sourceVisionTensors=len(vision), converterRevision=RUNTIME_REV,
                  outputType='f16', projectorBytes=output.stat().st_size,
                  projectorSha256=digest(output), projectorTensors=len(names),
                  configSha256=digest(source / 'config.json'),
                  licenseSha256=digest(source / 'LICENSE'),
                  trainingPerformed=False, coreConverted=False)
    record.parent.mkdir(parents=True, exist_ok=True)
    record.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
