"""Reads the Alchemist exports of the dynamic-population experiments into a pandas DataFrame, with a cache.

Every file is parsed once: the cache (python/.cache/parsed.pkl) remembers each file with its modification time and
size, so that only new or changed files are parsed again, and the files that disappeared are dropped.
"""

from __future__ import annotations

from concurrent.futures import ProcessPoolExecutor
import pickle
import re
from pathlib import Path

import numpy as np
import pandas as pd
import yaml

import config

CACHE_VERSION = 3  # Bump when parse_file changes
_CACHE_FILE = config.CACHE_DIR / "parsed.pkl"
_VARIABLE = re.compile(r"(?P<name>[A-Za-z._-]+) = (?P<value>[^,]*),?")
_FLOAT = re.compile(r"^[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?$")
_RASTER_CACHE: dict[Path, tuple] = {}


def _typed(value: str):
    value = value.strip()
    return float(value) if _FLOAT.match(value) else value


def _shape_raster(path: Path) -> tuple | None:
    """(u0, v0, step, rows, cols, area, inside_flat, offset_cache) for a shape raster CSV."""
    if path in _RASTER_CACHE:
        return _RASTER_CACHE[path]
    if not path.exists():
        return None
    meta = {}
    with open(path) as file:
        for line in file:
            if not line.startswith("#"):
                break
            meta.update({m["name"]: m["value"].strip() for m in _VARIABLE.finditer(line[1:])})
    values = np.loadtxt(path, comments="#", ndmin=2)
    step = float(meta["step"])
    u0 = float(meta["xmin"])
    v0 = float(meta["ymin"])
    inside = values <= 0
    n_inside = int(inside.sum())
    rows, cols = values.shape
    area = n_inside * step * step
    inside_flat = np.flatnonzero(inside)
    entry = (u0, v0, step, rows, cols, area, inside_flat, {})
    _RASTER_CACHE[path] = entry
    return entry


def _compute_covered(metric_path: Path, times: np.ndarray) -> pd.Series:
    """Percentage (0..100) of the placed shape within r(N) = fill(N) / sqrt(2) (the covering radius of a square lattice
    of spacing fill(N): the hexagonal one, fill(N) / sqrt(3), is reached only by a perfect hexagonal tiling of the shape)
    of at least one active device, where fill(N) = sqrt(2 * |Omega| / (sqrt(3) * N)) depends on the active robot count N."""
    scenario = metric_path.parent.name
    pos_dir = config.DATA_DIR / "positions" / scenario
    pos_path = pos_dir / metric_path.name
    pl_path = pos_dir / f"{metric_path.stem}_placement.csv"
    if not pos_path.exists() or not pl_path.exists():
        return pd.Series(np.nan, index=times)
    pl_cols = ["time", "shape", "ox", "oy", "ax", "ay", "bx", "by"]
    pl = pd.read_csv(pl_path, sep=" ", comment="#", header=None, names=pl_cols, na_values=["NaN"])
    if pl.empty:
        return pd.Series(np.nan, index=times)
    # Snap times to the export grid (interval is 2.0s in positions, metric times are multiples of EXPORT_INTERVAL=10.0s)
    pl["time"] = (pl["time"] / 2.0).round() * 2.0
    target_times = set(times)
    pl = pl[pl["time"].isin(target_times)].drop_duplicates("time", keep="first").set_index("time")
    if pl.empty:
        return pd.Series(np.nan, index=times)
    dev = pd.read_csv(
        pos_path, sep=" ", comment="#", header=None, usecols=[0, 1, 2, 3],
        names=["time", "id", "x", "y"], dtype=np.float32, na_values=["NaN"],
    )
    if dev.empty:
        return pd.Series(np.nan, index=times)
    dev["time"] = (dev["time"] / 2.0).round() * 2.0
    dev = dev[dev["time"].isin(target_times)].drop_duplicates(["time", "id"], keep="first")
    out: dict[float, float] = {}
    for t, grp in dev.groupby("time", sort=False):
        if t not in pl.index:
            continue
        where = pl.loc[t]
        if pd.isna(where["ox"]):
            continue
        raster = _shape_raster(pos_dir / "shapes" / f"{where['shape']}.csv")
        if raster is None:
            continue
        u0, v0, step, rows, cols, area, inside_flat, offset_cache = raster
        dx = grp["x"].to_numpy() - float(where["ox"])
        dy = grp["y"].to_numpy() - float(where["oy"])
        u = dx * float(where["ax"]) + dy * float(where["ay"])
        v = dx * float(where["bx"]) + dy * float(where["by"])
        n_dev = len(u)
        if n_dev == 0:
            continue
        if n_dev not in offset_cache:
            # Hexagonal lattice spacing fill(N) = sqrt(2 * area / (sqrt(3) * N)), radius r(N) = fill(N) / sqrt(2)
            fill2 = 2.0 * area / (np.sqrt(3.0) * n_dev)
            rad_step = np.sqrt(fill2 / 2.0) / step
            r_pix = int(np.ceil(rad_step + 0.5))
            grid = np.arange(-r_pix, r_pix + 1)
            dr, dc = np.meshgrid(grid, grid, indexing="ij")
            keep = (np.maximum(np.abs(dr) - 0.5, 0) ** 2 + np.maximum(np.abs(dc) - 0.5, 0) ** 2) <= rad_step ** 2
            offset_cache[n_dev] = (dr[keep], dc[keep], rad_step ** 2)
        dr, dc, rad_step2 = offset_cache[n_dev]
        cu = (u - u0) / step
        cv = (v - v0) / step
        ci = np.rint(cu).astype(np.int32)
        ri = np.rint(cv).astype(np.int32)
        fc = cu - ci
        fr = cv - ri
        mask = ((dr[None, :] - fr[:, None]) ** 2 + (dc[None, :] - fc[:, None]) ** 2) <= rad_step2
        rr = ri[:, None] + dr[None, :]
        cc = ci[:, None] + dc[None, :]
        valid = mask & (rr >= 0) & (rr < rows) & (cc >= 0) & (cc < cols)
        covered = np.zeros(rows * cols, dtype=bool)
        covered[rr[valid] * cols + cc[valid]] = True
        out[float(t)] = float(covered[inside_flat].mean() * 100.0)
    return pd.Series([out.get(float(t), np.nan) for t in times], index=times)


def parse_file(path: Path) -> pd.DataFrame:
    """One Alchemist export: the variables of the header (e.g. seed, shape) become constant columns."""
    variables: dict = {}
    columns: list[str] | None = None
    last_comment = ""
    rows: list[list[str]] = []
    with open(path) as file:
        for line in file:
            if line.startswith("#"):
                if not variables and " = " in line:
                    variables = {m["name"]: _typed(m["value"]) for m in _VARIABLE.finditer(line[1:])}
                last_comment = line
            elif line.strip():
                if columns is None:  # The last comment before the data names the columns
                    columns = last_comment.lstrip("#").split()
                rows.append(line.split())
    if columns is None or not rows:
        return pd.DataFrame()
    frame = pd.DataFrame(np.array(rows, dtype=float), columns=columns)
    # Snap the time on the export grid (it carries a small offset that differs between runs), keep the first sample
    frame[config.TIME_COLUMN] = (frame[config.TIME_COLUMN] / config.EXPORT_INTERVAL).round() * config.EXPORT_INTERVAL
    frame = frame.drop_duplicates(config.TIME_COLUMN, keep="first").reset_index(drop=True)
    if "covered" not in frame.columns:
        cov = _compute_covered(path, frame[config.TIME_COLUMN].to_numpy())
        frame["covered"] = cov.to_numpy()
    if not variables:  # Fall back on the file name: <root>_seed-0.0_shape-star.csv
        for key, value in re.findall(r"_([A-Za-z]+)-([^_]+?)(?=_|\.csv$)", path.name):
            variables[key] = _typed(value)
    for key, value in variables.items():
        frame[key] = value
    frame.attrs["variables"] = list(variables)
    return frame


def _load_cache() -> dict:
    try:
        with open(_CACHE_FILE, "rb") as file:
            cache = pickle.load(file)
        if cache.get("version") == CACHE_VERSION:
            return cache
    except Exception:  # Unreadable, or written by another version of pandas: parse again
        pass
    return {"version": CACHE_VERSION, "files": {}}


def load(scenarios: dict[str, str] | None = None, use_cache: bool = True, verbose: bool = True) -> pd.DataFrame:
    """All the runs of the scenarios that have data, in one frame with the columns scenario, label, the variables of
    the header (seed, shape, ...), time and the metrics."""
    scenarios = scenarios or config.SCENARIOS
    cache = _load_cache() if use_cache else {"version": CACHE_VERSION, "files": {}}
    entries = cache["files"]
    seen, frames = set(), []
    items, to_parse = [], []
    for scenario, label in scenarios.items():
        folder = config.DATA_DIR / scenario
        if not folder.is_dir():
            if verbose:
                print(f"[loader] no data for {scenario}: skipped")
            continue
        for path in sorted(folder.glob(f"{scenario}_*.csv")):
            key = str(path.relative_to(config.DATA_DIR))
            stat = path.stat()
            signature = (stat.st_mtime_ns, stat.st_size)
            seen.add(key)
            entry = entries.get(key)
            if entry is None or entry[0] != signature:
                to_parse.append((key, signature, path))
            items.append((scenario, label, key, signature))
    if to_parse:
        paths = [path for _, _, path in to_parse]
        if len(paths) > 4:
            with ProcessPoolExecutor() as executor:
                parsed_frames = list(executor.map(parse_file, paths))
        else:
            parsed_frames = [parse_file(p) for p in paths]
        for (key, signature, _), frame in zip(to_parse, parsed_frames):
            entries[key] = (signature, frame)
    parsed = len(to_parse)
    for scenario, label, key, signature in items:
        frame = entries[key][1]
        if not frame.empty:
            frames.append(frame.assign(scenario=scenario, label=label, file=key, mtime=signature[0]))
    removed = set(entries) - seen
    for key in removed:
        del entries[key]
    if use_cache and (parsed or removed):
        config.CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with open(_CACHE_FILE, "wb") as file:
            pickle.dump(cache, file, protocol=pickle.HIGHEST_PROTOCOL)
    if verbose:
        print(f"[loader] {len(seen)} files: {parsed} parsed, {len(seen) - parsed} from the cache")
    if not frames:
        return pd.DataFrame()
    variables = list(dict.fromkeys(v for f in frames for v in f.attrs.get("variables", [])))
    data = pd.concat(frames, ignore_index=True)
    configuration = [v for v in variables if v not in config.SEED_VARS]
    for variable in configuration:
        for scenario, defaults in config.VARIABLE_DEFAULTS.items():
            if variable in defaults:
                missing = (data["scenario"] == scenario) & data[variable].isna()
                data.loc[missing, variable] = defaults[variable]
        # Strings, so that grouping never trips on a mix of types or on NaN (a variable a scenario lacks reads "-")
        data[variable] = data[variable].map(_text)
    data = _drop_superseded(data, configuration, verbose)
    data.attrs["configuration"] = configuration
    data["label"] = pd.Categorical(data["label"], categories=[l for s, l in scenarios.items()
                                                                if s in set(data["scenario"])], ordered=True)
    data["series"] = _series(data, scenarios, configuration)
    return data


def _text(value) -> str:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "-"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def _drop_superseded(data: pd.DataFrame, configuration: list[str], verbose: bool) -> pd.DataFrame:
    """Keeps, for every scenario, configuration and seed, only the newest file (an old export repeated by a new one)."""
    keys = ["scenario", *configuration, *[s for s in config.SEED_VARS if s in data.columns]]
    files = data.drop_duplicates("file")[["file", "mtime", *keys]].sort_values("mtime")
    kept = files.drop_duplicates(keys, keep="last")["file"]
    if len(kept) < len(files):
        if verbose:
            superseded = sorted(set(files["file"]) - set(kept))
            print(f"[loader] {len(superseded)} files superseded by newer runs, ignored: {', '.join(superseded)}")
        data = data[data["file"].isin(set(kept))]
    return data.reset_index(drop=True)


def _ordered(variable: str, values) -> list[str]:
    known = list(config.VALUE_LABELS.get(variable, {}))
    return [v for v in known if v in values] + sorted(v for v in values if v not in known)


def _series(data: pd.DataFrame, scenarios: dict[str, str], configuration: list[str]) -> pd.Categorical:
    """The line each run belongs to: the scenario, split by the variables (but the facet one) that take more than one
    value within it."""
    splitting = [v for v in configuration if v != config.FACET_VARIABLE]
    names = pd.Series(data["label"].astype(str), index=data.index)
    order = []
    for scenario, label in scenarios.items():
        rows = data["scenario"] == scenario
        if not rows.any():
            continue
        varying = [v for v in splitting if data.loc[rows, v].nunique() > 1]
        if not varying:
            order.append(label)
            continue
        combos = data.loc[rows, varying].drop_duplicates()
        combos = combos.sort_values(varying, key=lambda column: column.map(
            {value: i for i, value in enumerate(_ordered(column.name, set(column)))}))
        for combo in combos.itertuples(index=False):
            parts = [config.VALUE_LABELS.get(v, {}).get(value, f"{v}={value}") for v, value in zip(varying, combo)]
            name = f"{label} ({', '.join(parts)})"
            order.append(name)
            match = rows.copy()
            for v, value in zip(varying, combo):
                match &= data[v] == value
            names[match] = name
    return pd.Categorical(names, categories=order, ordered=True)


def configuration_columns(data: pd.DataFrame) -> list[str]:
    """The variables of the header that identify a configuration (all but the seeds), e.g. shape."""
    return list(data.attrs.get("configuration", []))


def metric_columns(data: pd.DataFrame) -> list[str]:
    """The metrics in the data (every numeric column but the time and the variables of the header)."""
    excluded = {config.TIME_COLUMN, "scenario", "label", "series", "file", "mtime", *config.SEED_VARS,
                *configuration_columns(data)}
    numeric = [c for c in data.columns if c not in excluded and pd.api.types.is_numeric_dtype(data[c])]
    known = [m for m in config.METRICS if m in numeric]
    return known + [m for m in numeric if m not in known]


def _text(value) -> str:
    """The text of a yaml value: a string, or the string of a formula like `{ formula: "'name'" }`."""
    if isinstance(value, dict):
        value = value.get("formula", "")
    return str(value).strip().strip("'\"")


def yaml_variables(name: str) -> dict | None:
    """The variables of the yaml whose exports are named [name] (its `filename` variable, or the fileNameRoot or the
    last folder of the exportPath of one of its exporters), or None."""
    for path in sorted(config.YAML_DIR.glob("*.yml")):
        try:
            document = yaml.safe_load(path.read_text()) or {}
        except yaml.YAMLError:
            continue
        variables = document.get("variables", {}) or {}
        names = {_text(variables.get("filename", ""))}
        for exporter in document.get("export", []) or []:
            parameters = exporter.get("parameters", {}) if isinstance(exporter, dict) else {}
            if isinstance(parameters, dict):
                names.add(_text(parameters.get("fileNameRoot", "")))
                names.add(_text(parameters.get("exportPath", "")).rstrip("/").split("/")[-1])
        if name in names:
            return variables
    return None


def population_events(scenario: str) -> dict:
    """nodes, spawnTime, spawnCount, killTime, survivors from the yaml of [scenario] (the defaults when missing)."""
    values = dict(config.DEFAULT_POPULATION)
    for key, value in (yaml_variables(scenario) or {}).items():
        if key in values and isinstance(value, (int, float)):
            values[key] = value
    return values


def defined_shapes() -> list[str]:
    """The names of the shapes in src/main/resources/shapes.yml, in their order."""
    with open(config.SHAPES_FILE) as file:
        return list((yaml.safe_load(file) or {}).keys())
