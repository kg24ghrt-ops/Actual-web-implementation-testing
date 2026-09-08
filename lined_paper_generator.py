#!/usr/bin/env python3
"""
Lined Notebook Paper Generator
Generates realistic school-style lined paper with exact ISO 216 dimensions.
Uses only Python standard library (PIL/Pillow for image generation).
"""

import sys
from paper_dimensions import (
    PAPER_SIZES, LINE_STYLES, 
    get_paper_size, get_line_style, calculate_lines_count
)

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image, ImageDraw

def generate_lined_paper(
    paper_size: str = 'A4',
    line_style: str = 'college_ruled',
    output_file: str = 'lined_paper.png',
    dpi: int = 96,
    show_margin_line: bool = True,
    hole_punches: bool = False,
):
    """Generate a realistic lined notebook paper image."""
    
    # Get paper dimensions
    paper = get_paper_size(paper_size)
    lines = get_line_style(line_style)
    
    # Convert to pixels
    width_px, height_px = paper.to_pixels(dpi)
    
    # Create white paper background
    img = Image.new('RGB', (width_px, height_px), '#FFFFFF')
    draw = ImageDraw.Draw(img)
    
    # Helper: mm to pixels
    def mm_to_px(mm):
        return int((mm / 25.4) * dpi)
    
    # Calculate margins in pixels
    margin_top_px = mm_to_px(lines.margin_top_mm)
    margin_bottom_px = mm_to_px(lines.margin_bottom_mm)
    margin_left_px = mm_to_px(lines.margin_left_mm)
    margin_right_px = mm_to_px(lines.margin_right_mm)
    
    # Draw horizontal lines
    line_y = margin_top_px
    line_spacing_px = mm_to_px(lines.line_spacing_mm)
    available_height = height_px - margin_top_px - margin_bottom_px
    
    while line_y < height_px - margin_bottom_px:
        draw.line(
            [(margin_left_px, line_y), (width_px - margin_right_px, line_y)],
            fill=lines.line_color,
            width=lines.line_width_px
        )
        line_y += line_spacing_px
    
    # Draw vertical margin line (red)
    if show_margin_line:
        draw.line(
            [(margin_left_px, margin_top_px), 
             (margin_left_px, height_px - margin_bottom_px)],
            fill='#E74C3C',
            width=2
        )
    
    # Draw hole punches (optional)
    if hole_punches:
        hole_radius = mm_to_px(3)  # 3mm radius holes
        hole_y_positions = [
            mm_to_px(40),
            mm_to_px(paper.height_mm / 2),
            mm_to_px(paper.height_mm - 40)
        ]
        hole_x = mm_to_px(10)
        
        for y_pos in hole_y_positions:
            draw.ellipse(
                [(hole_x - hole_radius, y_pos - hole_radius),
                 (hole_x + hole_radius, y_pos + hole_radius)],
                fill='#FFFFFF'
            )
    
    # Save the image
    img.save(output_file, 'PNG', dpi=(dpi, dpi))
    
    # Print stats
    num_lines = calculate_lines_count(paper, lines)
    print(f"✓ Generated {paper_size} lined paper ({line_style})")
    print(f"  Dimensions: {paper.width_mm}mm × {paper.height_mm}mm")
    print(f"  Pixels: {width_px} × {height_px} @ {dpi} DPI")
    print(f"  Lines: {num_lines} horizontal lines")
    print(f"  Line spacing: {lines.line_spacing_mm}mm")
    print(f"  Output: {output_file}")
    
    return output_file

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Generate realistic lined notebook paper')
    parser.add_argument('--size', choices=['A4', 'A5'], default='A4', help='Paper size (default: A4)')
    parser.add_argument('--style', choices=['college_ruled', 'wide_ruled', 'narrow_ruled'], 
                        default='college_ruled', help='Line style (default: college_ruled)')
    parser.add_argument('--output', default='lined_paper.png', help='Output filename')
    parser.add_argument('--dpi', type=int, default=96, help='DPI (default: 96)')
    parser.add_argument('--no-margin-line', action='store_true', help='Hide red margin line')
    parser.add_argument('--hole-punches', action='store_true', help='Add hole punch marks')
    
    args = parser.parse_args()
    
    generate_lined_paper(
        paper_size=args.size,
        line_style=args.style,
        output_file=args.output,
        dpi=args.dpi,
        show_margin_line=not args.no_margin_line,
        hole_punches=args.hole_punches
    )
