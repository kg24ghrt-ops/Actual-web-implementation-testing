#!/usr/bin/env python3
"""
Lined Notebook Paper Generator
Generates realistic school-style lined paper with exact ISO 216 dimensions.
Uses only Python standard library (PIL/Pillow for image generation).

Features:
- Paper texture simulation for realistic appearance
- Non-uniform lighting to avoid "perfect" look
- Hole punches and margin lines
- Multiple paper sizes and line styles
"""

import sys
import random
from paper_dimensions import (
    PAPER_SIZES, LINE_STYLES, 
    get_paper_size, get_line_style, calculate_lines_count
)

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image, ImageDraw, ImageFilter, ImageEnhance


def add_paper_texture(img, texture_intensity=0.08):
    """
    Add realistic paper texture by introducing subtle color variations.
    
    Real paper is never perfectly white - it has subtle variations in color
    and brightness due to the manufacturing process and fiber distribution.
    
    Args:
        img: PIL Image object
        texture_intensity: Controls the strength of the texture (0.0-0.2 recommended)
    
    Returns:
        PIL Image with added texture
    """
    pixels = img.load()
    width, height = img.size
    
    # Use a fixed seed for reproducibility if needed
    # random.seed(42)
    
    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            
            # Add subtle noise variation (paper fiber texture)
            noise_range = int(texture_intensity * 255)
            noise = random.randint(-noise_range, noise_range)
            
            # Apply noise with slight warm tint (paper tends to be slightly warm)
            warm_tint = random.randint(0, int(noise_range * 0.3))
            
            pixels[x, y] = (
                max(0, min(255, r + noise + warm_tint)),
                max(0, min(255, g + noise + warm_tint // 2)),
                max(0, min(255, b + noise))
            )
    
    return img


def apply_lighting_effects(img, lighting_variation=0.05):
    """
    Apply non-uniform lighting to avoid the "perfect digital" look.
    
    Real-world photos have:
    - Slight vignetting (darker corners)
    - Uneven illumination from light sources
    - Subtle shadows and highlights
    
    Args:
        img: PIL Image object
        lighting_variation: Intensity of lighting variation (0.0-0.15 recommended)
    
    Returns:
        PIL Image with realistic lighting effects
    """
    width, height = img.size
    pixels = img.load()
    
    # Calculate center for vignette effect
    center_x, center_y = width // 2, height // 2
    max_distance = math.sqrt(center_x**2 + center_y**2)
    
    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            
            # Vignette effect (darker corners)
            distance_from_center = math.sqrt((x - center_x)**2 + (y - center_y)**2)
            vignette_factor = 1.0 - (distance_from_center / max_distance) * lighting_variation
            
            # Subtle horizontal gradient (simulates overhead lighting)
            vertical_gradient = 1.0 - lighting_variation * 0.3 * (abs(y - height//2) / (height//2))
            
            # Combine lighting effects
            combined_factor = vignette_factor * vertical_gradient
            
            # Apply lighting adjustment
            pixels[x, y] = (
                max(0, min(255, int(r * combined_factor))),
                max(0, min(255, int(g * combined_factor))),
                max(0, min(255, int(b * combined_factor)))
            )
    
    return img


def generate_lined_paper(
    paper_size: str = 'A4',
    line_style: str = 'college_ruled',
    output_file: str = 'lined_paper.png',
    dpi: int = 96,
    show_margin_line: bool = True,
    hole_punches: bool = False,
    add_texture: bool = True,
    texture_intensity: float = 0.08,
    add_lighting: bool = True,
    lighting_variation: float = 0.05,
):
    """Generate a realistic lined notebook paper image.
    
    Args:
        paper_size: Paper size ('A4' or 'A5')
        line_style: Line style ('college_ruled', 'wide_ruled', 'narrow_ruled')
        output_file: Output filename path
        dpi: Image resolution in dots per inch
        show_margin_line: Whether to draw the red vertical margin line
        hole_punches: Whether to add hole punch marks
        add_texture: Enable paper texture simulation
        texture_intensity: Strength of paper texture (0.0-0.2)
        add_lighting: Enable realistic lighting effects
        lighting_variation: Strength of lighting variation (0.0-0.15)
    
    Returns:
        Path to generated image file
    """
    
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
    
    # Apply paper texture for realism
    if add_texture:
        img = add_paper_texture(img, texture_intensity)
    
    # Apply lighting effects for non-perfect appearance
    if add_lighting:
        img = apply_lighting_effects(img, lighting_variation)
    
    # Save the image
    img.save(output_file, 'PNG', dpi=(dpi, dpi))
    
    # Print stats
    num_lines = calculate_lines_count(paper, lines)
    print(f"✓ Generated {paper_size} lined paper ({line_style})")
    print(f"  Dimensions: {paper.width_mm}mm × {paper.height_mm}mm")
    print(f"  Pixels: {width_px} × {height_px} @ {dpi} DPI")
    print(f"  Lines: {num_lines} horizontal lines")
    print(f"  Line spacing: {lines.line_spacing_mm}mm")
    print(f"  Paper texture: {'Enabled' if add_texture else 'Disabled'} (intensity: {texture_intensity})")
    print(f"  Lighting effects: {'Enabled' if add_lighting else 'Disabled'} (variation: {lighting_variation})")
    print(f"  Output: {output_file}")
    
    return output_file

if __name__ == '__main__':
    import argparse
    import math
    
    parser = argparse.ArgumentParser(description='Generate realistic lined notebook paper')
    parser.add_argument('--size', choices=['A4', 'A5'], default='A4', help='Paper size (default: A4)')
    parser.add_argument('--style', choices=['college_ruled', 'wide_ruled', 'narrow_ruled'], 
                        default='college_ruled', help='Line style (default: college_ruled)')
    parser.add_argument('--output', default='lined_paper.png', help='Output filename')
    parser.add_argument('--dpi', type=int, default=96, help='DPI (default: 96)')
    parser.add_argument('--no-margin-line', action='store_true', help='Hide red margin line')
    parser.add_argument('--hole-punches', action='store_true', help='Add hole punch marks')
    parser.add_argument('--no-texture', action='store_true', help='Disable paper texture')
    parser.add_argument('--texture-intensity', type=float, default=0.08, 
                        help='Paper texture intensity (default: 0.08)')
    parser.add_argument('--no-lighting', action='store_true', help='Disable lighting effects')
    parser.add_argument('--lighting-variation', type=float, default=0.05,
                        help='Lighting variation intensity (default: 0.05)')
    
    args = parser.parse_args()
    
    generate_lined_paper(
        paper_size=args.size,
        line_style=args.style,
        output_file=args.output,
        dpi=args.dpi,
        show_margin_line=not args.no_margin_line,
        hole_punches=args.hole_punches,
        add_texture=not args.no_texture,
        texture_intensity=args.texture_intensity,
        add_lighting=not args.no_lighting,
        lighting_variation=args.lighting_variation
    )
