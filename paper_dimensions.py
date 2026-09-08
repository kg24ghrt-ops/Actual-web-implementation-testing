#!/usr/bin/env python3
"""
ISO 216 Paper Size Standards - A4 and A5 Lined Notebook Paper
Source: https://en.wikipedia.org/wiki/Paper_size (ISO 216 standard)

This module provides exact real-world dimensions for generating
realistic lined notebook paper formats.
"""

from dataclasses import dataclass
from typing import Tuple

@dataclass
class PaperSize:
    """Represents a paper size with exact ISO 216 dimensions."""
    name: str
    width_mm: float
    height_mm: float
    width_inches: float
    height_inches: float
    
    @property
    def aspect_ratio(self) -> float:
        """Returns the √2 aspect ratio (approximately 1.414)."""
        return self.height_mm / self.width_mm
    
    def to_pixels(self, dpi: int = 96) -> Tuple[int, int]:
        """Convert dimensions to pixels at given DPI."""
        width_px = int((self.width_mm / 25.4) * dpi)
        height_px = int((self.height_mm / 25.4) * dpi)
        return (width_px, height_px)
    
    def to_points(self) -> Tuple[float, float]:
        """Convert dimensions to points (1pt = 1/72 inch)."""
        width_pt = self.width_inches * 72
        height_pt = self.height_inches * 72
        return (width_pt, height_pt)

# ISO 216 Standard Paper Sizes (verified from Wikipedia)
# A series: aspect ratio = √2, A0 area = 1 m²
PAPER_SIZES = {
    'A4': PaperSize(
        name='A4',
        width_mm=210.0,
        height_mm=297.0,
        width_inches=8.27,
        height_inches=11.69,
        # Source: "210 mm × 297 mm (8.3 in × 11.7 in)"
    ),
    'A5': PaperSize(
        name='A5',
        width_mm=148.0,
        height_mm=210.0,
        width_inches=5.83,
        height_inches=8.27,
        # Source: "148 mm × 210 mm (5.8 in × 8.3 in)"
    ),
}

# Lined notebook paper specifications (standard school notebook)
@dataclass
class LineSpecs:
    """Specifications for lined paper."""
    line_spacing_mm: float
    margin_top_mm: float
    margin_bottom_mm: float
    margin_left_mm: float
    margin_right_mm: float
    line_color: str
    line_width_px: int

# Standard school notebook line spacing (varies by region)
LINE_STYLES = {
    'college_ruled': LineSpecs(
        line_spacing_mm=7.1,      # 9/32 inch (~7.14mm)
        margin_top_mm=20.0,
        margin_bottom_mm=15.0,
        margin_left_mm=25.0,       # Red vertical margin line
        margin_right_mm=15.0,
        line_color='#4A90E2',      # Light blue lines
        line_width_px=1,
    ),
    'wide_ruled': LineSpecs(
        line_spacing_mm=8.7,      # 11/32 inch (~8.73mm)
        margin_top_mm=20.0,
        margin_bottom_mm=15.0,
        margin_left_mm=25.0,
        margin_right_mm=15.0,
        line_color='#4A90E2',
        line_width_px=1,
    ),
    'narrow_ruled': LineSpecs(
        line_spacing_mm=6.35,     # 1/4 inch (6.35mm)
        margin_top_mm=20.0,
        margin_bottom_mm=15.0,
        margin_left_mm=25.0,
        margin_right_mm=15.0,
        line_color='#4A90E2',
        line_width_px=1,
    ),
}

def get_paper_size(size_name: str) -> PaperSize:
    """Get paper size by name (case-insensitive)."""
    size_name = size_name.upper()
    if size_name not in PAPER_SIZES:
        raise ValueError(f"Unknown paper size: {size_name}. Available: {list(PAPER_SIZES.keys())}")
    return PAPER_SIZES[size_name]

def get_line_style(style_name: str) -> LineSpecs:
    """Get line style by name (case-insensitive)."""
    style_name = style_name.lower()
    if style_name not in LINE_STYLES:
        raise ValueError(f"Unknown line style: {style_name}. Available: {list(LINE_STYLES.keys())}")
    return LINE_STYLES[style_name]

def calculate_lines_count(paper: PaperSize, line_style: LineSpecs) -> int:
    """Calculate number of lines that fit on the page."""
    available_height = paper.height_mm - line_style.margin_top_mm - line_style.margin_bottom_mm
    return int(available_height / line_style.line_spacing_mm)

if __name__ == '__main__':
    print("=" * 60)
    print("ISO 216 PAPER SIZE STANDARDS")
    print("Source: Wikipedia - Paper Size (verified)")
    print("=" * 60)
    
    for name, paper in PAPER_SIZES.items():
        print(f"\n{name} PAPER:")
        print(f"  Dimensions: {paper.width_mm}mm × {paper.height_mm}mm")
        print(f"  Inches:     {paper.width_inches}\" × {paper.height_inches}\"")
        print(f"  Aspect Ratio: {paper.aspect_ratio:.4f} (√2 = 1.4142)")
        print(f"  Points:     {paper.to_points()[0]:.1f}pt × {paper.to_points()[1]:.1f}pt")
        print(f"  Pixels @96DPI: {paper.to_pixels(96)[0]}px × {paper.to_pixels(96)[1]}px")
        
        for style_name, line_style in LINE_STYLES.items():
            line_count = calculate_lines_count(paper, line_style)
            print(f"    {style_name.replace('_', ' ').title()}: {line_count} lines ({line_style.line_spacing_mm}mm spacing)")
    
    print("\n" + "=" * 60)
    print("Ready for lined notebook paper generation!")
    print("=" * 60)
