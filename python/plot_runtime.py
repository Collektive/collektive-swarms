"""Plots of the runtime-positioning experiment (runtimePositioning.yml): every metric over time, the mean over the
seeds with the band of the spread, the switches of positioning as vertical lines and the star phases shaded (the
shape alternates between circle and star).

    python python/plot_runtime.py                          # every chart, in charts/runtime/ (pdf and png)
    python python/plot_runtime.py --metrics jain inside --format png

Charts:
- charts/runtime/<fmt>/<metric>.<fmt>   one metric
- charts/runtime/<fmt>/overview.<fmt>   a panel per metric
plus charts/runtime/summary.csv, with mean, std and number of seeds of every metric by time.
"""

from __future__ import annotations

import argparse
import math

import matplotlib.pyplot as plt
import seaborn as sns
from matplotlib.lines import Line2D
from matplotlib.patches import Patch

import config
import loader
from plot_dynamic import COLUMN_WIDTH, EVENT_COLOR, PERCENT_METRICS, UNIT_METRICS, apply_comp_log_scale, apply_paper_style, apply_sqrt_scale, metric_label, save

SCENARIO = "runtimePositioning"
CHARTS_DIR = config.ROOT / "charts" / "runtime"
# As in runtimePositioningEntrypoint: the positioning in use up to each time, and the shape that alternates
POSITIONING = [(600.0, "Displacement"), (1200.0, "Distance"), (math.inf, "Position")]
SHAPE_PERIOD = 300.0  # circle first, then star, and so on
LINE_COLOR = sns.color_palette("viridis", as_cmap=True)(0.0)
STAR_SHADE = "0.9"


def draw(ax, data, metric: str, annotate: bool) -> None:
    end = data[config.TIME_COLUMN].max()
    for start in range(int(SHAPE_PERIOD), math.ceil(end), int(2 * SHAPE_PERIOD)):  # The star phases
        ax.axvspan(start, min(start + SHAPE_PERIOD, end), color=STAR_SHADE, lw=0, zorder=0)
    previous = 0.0
    for switch, name in POSITIONING:
        if switch < end:
            ax.axvline(switch, color=EVENT_COLOR, ls="--", lw=0.85, zorder=1)
        if annotate:
            ax.annotate(name, xy=((previous + min(switch, end)) / 2, 1.0), xycoords=("data", "axes fraction"),
                        ha="center", va="bottom", fontsize=7.2, color=EVENT_COLOR)
        previous = switch
    frame = data.dropna(subset=[metric])
    if not frame.empty:
        sns.lineplot(data=frame, x=config.TIME_COLUMN, y=metric, color=LINE_COLOR, errorbar=config.ERRORBAR,
                     err_kws={"alpha": 0.2, "lw": 0}, lw=1.1, ax=ax)
    if metric in UNIT_METRICS:
        apply_comp_log_scale(ax)
    elif metric in PERCENT_METRICS:
        apply_comp_log_scale(ax, top=100.0)
    ax.set_xlim(0, end)
    ax.set_xticks([t for t in range(0, int(end) + 1, int(SHAPE_PERIOD))])
    ax.tick_params(axis="both", which="major", pad=1.5, length=2.0, width=0.5)
    ax.set_xlabel("Time (s)", labelpad=1.5)
    ax.set_ylabel(metric_label(metric), labelpad=2.0)


def handles(seeds: int) -> list:
    return [
        Line2D([], [], color=LINE_COLOR, lw=1.6, label=f"mean ± sd ({seeds} seeds)"),
        Patch(color=STAR_SHADE, label="star (else circle)"),
        Line2D([], [], color=EVENT_COLOR, ls="--", lw=0.9, label="positioning switch"),
    ]


def plot_paper_stack(data, formats) -> None:
    """Compact stacked figure of Jain's index, inside fraction, and mean speed for the paper (single IEEE column)."""
    paper_metrics = [m for m in ("jain", "covered", "motion") if m in data.columns]
    if not paper_metrics:
        return
    seeds = data["seed"].nunique()
    rows = len(paper_metrics)
    fig_height = 0.7 * rows + 0.32
    fig, axes = plt.subplots(rows, 1, figsize=(COLUMN_WIDTH, fig_height), sharex=True, squeeze=False)
    short_labels = {
        "jain": r"$J(t)$",
        "inside": r"$I(t)$",
        "covered": r"$C(t)$ (%)",
        "motion": r"$\bar{v}(t)$ (m/s)",
    }
    for idx, (ax, metric) in enumerate(zip(axes.flat, paper_metrics)):
        draw(ax, data, metric, annotate=(idx == 0))
        ax.set_ylabel(short_labels.get(metric, metric_label(metric)), labelpad=2.0, fontsize=7.2)
        if metric in UNIT_METRICS:  # Fewer ticks, as they crowd near 0 in short rows
            ax.set_yticks([0.0, 0.9, 1.0])
        elif metric in PERCENT_METRICS:
            ax.set_yticks([0, 90, 100])
        elif metric == "motion":
            apply_sqrt_scale(ax)
            ax.set_ylim(0.0, 0.92)
            ax.set_yticks([0.0, 0.1, 0.4, 0.8])
        if idx < rows - 1:
            ax.set_xlabel("")
            ax.tick_params(labelbottom=False)
    # Margins in inches, so that they do not shrink with the rows
    fig.subplots_adjust(left=0.13, right=0.98, top=1 - 0.24 / fig_height, bottom=0.5 / fig_height, hspace=0.16)
    fig.legend(handles=handles(seeds), loc="lower center", ncol=3, fontsize=7.2, frameon=False,
               bbox_to_anchor=(0.52, 0.027 / fig_height), handlelength=1.2, handletextpad=0.3, columnspacing=0.7)
    save(fig, CHARTS_DIR / "paper", formats)
    paper_dir = config.ROOT / "charts" / "paper"
    paper_dir.mkdir(parents=True, exist_ok=True)
    for fmt in formats:
        shutil_src = CHARTS_DIR / fmt / f"paper.{fmt}"
        if shutil_src.exists():
            (paper_dir / f"runtime_paper.{fmt}").write_bytes(shutil_src.read_bytes())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--metrics", nargs="+", help="metrics to plot (default: all)")
    parser.add_argument("--format", nargs="+", default=["pdf", "png"], help="pdf, png, svg, ... (default: pdf png)")
    parser.add_argument("--no-cache", action="store_true", help="accepted for process.py: the data is always parsed")
    args = parser.parse_args()

    # Without the cache: loading one scenario would drop the cached files of the others
    data = loader.load({SCENARIO: "Runtime positioning"}, use_cache=False)
    if data.empty:
        raise SystemExit(f"No data in {config.DATA_DIR / SCENARIO}")
    if "motion" in data.columns:
        data["motion"] *= data["fill"]  # Exported in lattice spacings per time unit: back to meters per second
    metrics = [m for m in loader.metric_columns(data) if not args.metrics or m in args.metrics]
    seeds = data["seed"].nunique()

    apply_paper_style()
    plot_paper_stack(data, args.format)
    for metric in metrics:
        fig, ax = plt.subplots(figsize=(COLUMN_WIDTH, 1.35))
        draw(ax, data, metric, annotate=True)
        fig.legend(handles=handles(seeds), loc="lower center", ncol=3, fontsize=6.3, frameon=False,
                   bbox_to_anchor=(0.5, -0.2), handlelength=1.2, handletextpad=0.3, columnspacing=0.7)
        save(fig, CHARTS_DIR / metric, args.format)

    columns = 4
    rows = math.ceil(len(metrics) / columns)
    fig, axes = plt.subplots(rows, columns, figsize=(4.0 * columns, 2.8 * rows), sharex=True, squeeze=False)
    for ax, metric in zip(axes.flat, metrics):
        draw(ax, data, metric, annotate=False)
        ax.set_title(metric_label(metric), fontsize=9)
        ax.set_ylabel("")
    for ax in list(axes.flat)[len(metrics):]:
        ax.set_visible(False)
    fig.legend(handles=handles(seeds), loc="lower center", ncol=3, fontsize=9, frameon=False,
               bbox_to_anchor=(0.5, -0.03))
    fig.suptitle("Runtime positioning: displacement, distance, position-based; circle and star alternating")
    fig.tight_layout()
    save(fig, CHARTS_DIR / "overview", args.format)

    grouped = data.groupby(config.TIME_COLUMN)[metrics]
    summary = grouped.agg(["mean", "std", "count"])
    summary.columns = [f"{metric}_{stat}" for metric, stat in summary.columns]
    summary.to_csv(CHARTS_DIR / "summary.csv")
    print(f"[runtime] {len(metrics)} metrics, {seeds} seeds -> {CHARTS_DIR}")


if __name__ == "__main__":
    main()
