"""Canonical production outputs and local authoring inputs for the Consort pipeline."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK = ROOT / "tools/consort"
ASSETS = ROOT / "src/main/resources/assets/elder_bosses"
DATA = ASSETS / "boss/promised_consort"
RUNTIME_RIG = DATA / "rig"
ANIMATIONS = ASSETS / "animations/entity/promised_consort"
GEOMETRY = ASSETS / "geo/entity/promised_consort.geo.json"
TEXTURES = ASSETS / "textures/entity/promised_consort"
PREVIEWS = ROOT / "build/asset-previews/consort"
