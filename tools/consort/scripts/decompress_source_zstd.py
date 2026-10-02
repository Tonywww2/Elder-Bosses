"""Decompress a workspace copy of an Elden Ring ZSTD DCX file."""
import struct
import sys
from pathlib import Path
import zstandard

source, destination = map(Path, sys.argv[1:3])
data = source.read_bytes()
assert data[:4] == b"DCX\0" and data[40:44] == b"ZSTD"
expected = struct.unpack_from(">I", data, 28)[0]
offset = data.index(b"\x28\xb5\x2f\xfd", 68)
expanded = zstandard.ZstdDecompressor().decompress(data[offset:], max_output_size=expected)
assert len(expanded) == expected and expanded[:4] == b"BND4"
destination.write_bytes(expanded)
print(f"Decompressed ZSTD regulation: {len(expanded)} bytes")
