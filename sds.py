"""
generate_histograms.py

Reads the original image and the processed output images produced by
ApplyFilters.java and generates a histogram comparison figure.

Usage:
    python generate_histograms.py <original_image>

Example:
    python generate_histograms.py src.jpg
"""

import sys
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
from PIL import Image

def load_gray(path):
    return np.array(Image.open(path).convert('L')).flatten()

def plot_hist(ax, pixels, title):
    counts, _ = np.histogram(pixels, bins=256, range=(0, 255))
    ax.bar(range(256), counts, color='#1a1a2e', width=1.0)
    ax.set_xlim(0, 255)
    ax.set_xlabel('Pixel Intensity')
    ax.set_ylabel('Frequency')
    ax.set_title(title)
    ax.yaxis.set_major_formatter(
        ticker.FuncFormatter(lambda x, _: f'{int(x/1000)}k' if x >= 1000 else str(int(x)))
    )
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

if __name__ == '__main__':
    original_path = sys.argv[1] if len(sys.argv) > 1 else 'src.jpg'

    outputs = [
        ('out_sequential.jpg',        'Sequential'),
        ('out_multithreaded.jpg',      'Multithreaded'),
        ('out_threadpool.jpg',         'Thread Pool'),
        ('out_forkjoin.jpg',           'Fork/Join'),
        ('out_completablefuture.jpg',  'CompletableFuture'),
    ]

    # --- Figure 1: Original vs Sequential (main comparison) ---
    fig, axes = plt.subplots(1, 2, figsize=(12, 4))
    fig.suptitle(f'Histogram Equalization — {original_path}', fontsize=13, fontweight='bold')
    plot_hist(axes[0], load_gray(original_path), 'Original Image')
    plot_hist(axes[1], load_gray('out_sequential.jpg'), 'Processed Image (Sequential)')
    plt.tight_layout()
    plt.savefig('histogram_comparison.png', dpi=150, bbox_inches='tight')
    print('Saved: histogram_comparison.png')

    # --- Figure 2: All implementations side by side ---
    fig2, axes2 = plt.subplots(2, 3, figsize=(16, 8))
    fig2.suptitle('Histogram — All Implementations', fontsize=13, fontweight='bold')
    axes2_flat = axes2.flatten()

    plot_hist(axes2_flat[0], load_gray(original_path), 'Original')
    for idx, (filename, label) in enumerate(outputs):
        plot_hist(axes2_flat[idx + 1], load_gray(filename), label)

    plt.tight_layout()
    plt.savefig('histogram_all_implementations.png', dpi=150, bbox_inches='tight')
    print('Saved: histogram_all_implementations.png')
    plt.show()