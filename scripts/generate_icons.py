#!/usr/bin/env python3
"""Generate Android app icons from a source image at all required mipmap sizes."""
import os
from PIL import Image, ImageDraw

SOURCE = "/home/z/my-project/upload/1770418339249_edit_13463967055851.png"
RES_DIR = "/home/z/my-project/civileg2/app/src/main/res"

# Adaptive icon sizes (foreground is 108dp, but we generate full launcher icons)
# For ic_launcher (full icon): standard mipmap sizes
LAUNCHER_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Foreground size for adaptive icon (108dp × density)
FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

def make_rounded_square(img, size, radius_ratio=0.2):
    """Create a rounded square icon from the source image."""
    # Create a new image with transparent background
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    
    # Resize source to fit within the rounded square (with padding)
    padding = int(size * 0.08)
    inner_size = size - 2 * padding
    resized = img.copy()
    resized = resized.resize((inner_size, inner_size), Image.LANCZOS)
    
    # Create rounded square mask
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    radius = int(size * radius_ratio)
    draw.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=radius, fill=255)
    
    # Paste the resized image onto the result
    result.paste(resized, (padding, padding))
    
    # Apply mask for rounded corners
    result.putalpha(mask)
    
    return result

def make_foreground(img, size):
    """Create adaptive icon foreground (108dp circle-safe zone)."""
    # Adaptive icon safe zone is a centered circle of 66dp diameter
    # in a 108dp canvas. Scale: 66/108 = 0.611 of the canvas
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    
    # The icon should fit within the center 72dp safe zone (with some margin)
    # We'll use about 80% of the 108dp canvas
    icon_size = int(size * 0.78)
    offset = (size - icon_size) // 2
    
    # Resize source
    resized = img.copy()
    resized = resized.resize((icon_size, icon_size), Image.LANCZOS)
    
    # Create circular mask for adaptive icon
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    center = size // 2
    circle_radius = int(size * 0.40)  # Safe zone radius
    draw.ellipse([center - circle_radius, center - circle_radius, 
                  center + circle_radius, center + circle_radius], fill=255)
    
    result.paste(resized, (offset, offset))
    result.putalpha(mask)
    
    return result

def main():
    source = Image.open(SOURCE).convert("RGBA")
    
    # Generate ic_launcher (full icon with rounded corners)
    for folder, size in LAUNCHER_SIZES.items():
        icon = make_rounded_square(source, size)
        path = os.path.join(RES_DIR, folder)
        os.makedirs(path, exist_ok=True)
        
        # Save as PNG (rename webp references later if needed)
        out_path = os.path.join(path, "ic_launcher.png")
        icon.save(out_path, "PNG")
        print(f"Generated: {out_path} ({size}x{size})")
        
        # Also generate round version
        round_path = os.path.join(path, "ic_launcher_round.png")
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse([(0, 0), (size - 1, size - 1)], fill=255)
        
        inner = int(size * 0.84)
        offset = (size - inner) // 2
        resized = source.resize((inner, inner), Image.LANCZOS)
        round_icon.paste(resized, (offset, offset))
        round_icon.putalpha(mask)
        round_icon.save(round_path, "PNG")
        print(f"Generated: {round_path}")
    
    # Generate ic_launcher_foreground for adaptive icon
    for folder, size in FOREGROUND_SIZES.items():
        fg = make_foreground(source, size)
        path = os.path.join(RES_DIR, folder)
        out_path = os.path.join(path, "ic_launcher_foreground.png")
        fg.save(out_path, "PNG")
        print(f"Generated: {out_path} ({size}x{size})")
    
    print("\nDone! All icons generated.")

if __name__ == "__main__":
    main()