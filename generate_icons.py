#!/usr/bin/env python3
"""
Icon generation script for No Home Work app.
This script creates notebook-themed icons at all required Android densities.
Since we can't use external libraries, this creates simple colored PNGs.
"""

import os
import struct
import zlib

def create_notebook_png(output_path, size=(108, 108)):
    """Create a notebook-themed PNG icon."""
    width, height = size
    
    def png_chunk(chunk_type, data):
        chunk_len = struct.pack('>I', len(data))
        chunk_crc = struct.pack('>I', zlib.crc32(chunk_type + data) & 0xffffffff)
        return chunk_len + chunk_type + data + chunk_crc
    
    # PNG signature
    signature = b'\x89PNG\r\n\x1a\n'
    
    # IHDR chunk
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    ihdr = png_chunk(b'IHDR', ihdr_data)
    
    # Create image data - notebook with lines
    raw_data = bytearray()
    for y in range(height):
        for x in range(width):
            # Create a notebook pattern
            # Background: light gray
            r, g, b = 248, 249, 250
            
            # Add some subtle lines
            if y % 8 == 0 and x > width // 4:
                r, g, b = min(r - 30, 255), min(g - 30, 255), min(b - 30, 255)
            
            # Add red margin line
            if x == width // 4 and y > height // 10:
                r, g, b = 231, 76, 60  # Red
            
            # Add notebook border
            if x < 2 or x >= width - 2 or y < 2 or y >= height - 2:
                r, g, b = 100, 100, 100
            
            # Add pencil diagonal
            if abs(x - y) < 3 and x > width // 2 and y > height // 2:
                r, g, b = 255, 193, 7  # Yellow/orange
            
            raw_data.extend([r, g, b, 255])
    
    # Compress the data
    compressed = zlib.compress(raw_data, 9)
    idat = png_chunk(b'IDAT', compressed)
    
    # IEND chunk
    iend = png_chunk(b'IEND', b'')
    
    png_data = signature + ihdr + idat + iend
    
    with open(output_path, 'wb') as f:
        f.write(png_data)
    
    print(f"Created notebook icon: {output_path}")


def create_adaptive_icon():
    """Create adaptive icon XML files."""
    
    # Adaptive icon background
    adaptive_background = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
    
    with open('app/src/main/res/drawable/ic_launcher.xml', 'w') as f:
        f.write(adaptive_background)
    print("Created adaptive icon XML")


def main():
    """Generate all icon variants."""
    
    # Define all densities and sizes
    densities = [
        ('mdpi', 48),
        ('hdpi', 72),
        ('xhdpi', 96),
        ('xxhdpi', 144),
        ('xxxhdpi', 192),
    ]
    
    # Create icons for each density
    for density, size in densities:
        svg_dir = f'app/src/main/res/mipmap-{density}'
        os.makedirs(svg_dir, exist_ok=True)
        
        # Create both icon types
        fg_path = os.path.join(svg_dir, 'ic_launcher.png')
        round_path = os.path.join(svg_dir, 'ic_launcher_round.png')
        
        create_notebook_png(fg_path, (size, size))
        create_notebook_png(round_path, (size, size))
    
    # Create adaptive icon
    create_adaptive_icon()
    
    print("\n✅ Icon generation complete!")
    print("\nFor better quality icons, replace the generated PNGs with:")
    print("  1. Design proper icons in Figma, Inkscape, or Adobe Illustrator")
    print("  2. Export at all densities (48, 72, 96, 144, 192px)")
    print("  3. Use the notebook/pencil theme with ruled lines")
    print("\nThe current icons are functional but simple.")


if __name__ == '__main__':
    main()
