"""Small 2D geometry helpers for building the crossing network.

All coordinates are local metres: x points east, y points north, with the origin at
RD (Rijksdriehoek, EPSG:28992) coordinate 180440, 579965 in the middle of the crossing.
"""
from __future__ import annotations

import math
from typing import Iterable, Sequence

Point = tuple[float, float]


def sub(a: Point, b: Point) -> Point:
    return a[0] - b[0], a[1] - b[1]


def add(a: Point, b: Point) -> Point:
    return a[0] + b[0], a[1] + b[1]


def mul(a: Point, f: float) -> Point:
    return a[0] * f, a[1] * f


def length(a: Point) -> float:
    return math.hypot(a[0], a[1])


def norm(a: Point) -> Point:
    n = length(a)
    return (a[0] / n, a[1] / n) if n > 1e-9 else (0.0, 0.0)


def right_normal(direction: Point) -> Point:
    """Unit vector pointing to the right of a direction of travel."""
    d = norm(direction)
    return d[1], -d[0]


def polyline_length(points: Sequence[Point]) -> float:
    return sum(length(sub(points[i + 1], points[i])) for i in range(len(points) - 1))


def offset(points: Sequence[Point], distance: float) -> list[Point]:
    """Offsets a polyline sideways; positive distances move it to the right."""
    out: list[Point] = []
    n = len(points)
    for i, p in enumerate(points):
        if i == 0:
            normal = right_normal(sub(points[1], p))
        elif i == n - 1:
            normal = right_normal(sub(p, points[i - 1]))
        else:
            n1 = right_normal(sub(p, points[i - 1]))
            n2 = right_normal(sub(points[i + 1], p))
            bis = norm(add(n1, n2))
            cos_half = max(0.3, bis[0] * n1[0] + bis[1] * n1[1])
            normal = mul(bis, 1.0 / cos_half)
        out.append(add(p, mul(normal, distance)))
    return out


def point_at(points: Sequence[Point], s: float) -> Point:
    """Point at arc length s along a polyline (clamped to its ends)."""
    if s <= 0:
        return points[0]
    for i in range(len(points) - 1):
        seg = length(sub(points[i + 1], points[i]))
        if s <= seg:
            return add(points[i], mul(sub(points[i + 1], points[i]), s / seg if seg else 0))
        s -= seg
    return points[-1]


def direction_at(points: Sequence[Point], s: float) -> Point:
    total = 0.0
    for i in range(len(points) - 1):
        seg = length(sub(points[i + 1], points[i]))
        if s <= total + seg or i == len(points) - 2:
            return norm(sub(points[i + 1], points[i]))
        total += seg
    return norm(sub(points[-1], points[-2]))


def project(points: Sequence[Point], q: Point) -> float:
    """Arc length of the point on the polyline closest to q."""
    best, best_s, total = float("inf"), 0.0, 0.0
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        ab = sub(b, a)
        seg = length(ab)
        t = 0.0 if seg < 1e-9 else max(0.0, min(1.0, ((q[0] - a[0]) * ab[0] + (q[1] - a[1]) * ab[1]) / (seg * seg)))
        p = add(a, mul(ab, t))
        d = length(sub(q, p))
        if d < best:
            best, best_s = d, total + t * seg
        total += seg
    return best_s


def cut(points: Sequence[Point], s0: float, s1: float) -> list[Point]:
    """The part of a polyline between arc lengths s0 and s1."""
    out = [point_at(points, s0)]
    total = 0.0
    for i in range(len(points) - 1):
        seg = length(sub(points[i + 1], points[i]))
        total += seg
        if s0 < total < s1:
            out.append(points[i + 1])
    out.append(point_at(points, s1))
    return dedupe(out)


def extend(points: Sequence[Point], before: float = 0.0, after: float = 0.0) -> list[Point]:
    pts = list(points)
    if before:
        pts.insert(0, add(pts[0], mul(norm(sub(pts[0], pts[1])), before)))
    if after:
        pts.append(add(pts[-1], mul(norm(sub(pts[-1], pts[-2])), after)))
    return pts


def bezier(p0: Point, d0: Point, p1: Point, d1: Point, step: float = 1.0) -> list[Point]:
    """Cubic Bezier from p0 (leaving in direction d0) to p1 (arriving in direction d1)."""
    dist = length(sub(p1, p0))
    k = dist * 0.42
    c0 = add(p0, mul(norm(d0), k))
    c1 = sub(p1, mul(norm(d1), k))
    n = max(4, int(dist / step))
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        x = u ** 3 * p0[0] + 3 * u * u * t * c0[0] + 3 * u * t * t * c1[0] + t ** 3 * p1[0]
        y = u ** 3 * p0[1] + 3 * u * u * t * c0[1] + 3 * u * t * t * c1[1] + t ** 3 * p1[1]
        pts.append((x, y))
    return pts


def resample(points: Sequence[Point], step: float = 2.0) -> list[Point]:
    total = polyline_length(points)
    n = max(1, round(total / step))
    return [point_at(points, total * i / n) for i in range(n + 1)]


def dedupe(points: Iterable[Point], eps: float = 0.05) -> list[Point]:
    out: list[Point] = []
    for p in points:
        if not out or length(sub(p, out[-1])) > eps:
            out.append(p)
    return out


def join(*parts: Sequence[Point]) -> list[Point]:
    out: list[Point] = []
    for part in parts:
        out.extend(part)
    return dedupe(out)


def segment_intersection(a: Point, b: Point, c: Point, d: Point):
    """Returns (t, u) where a + t(b-a) == c + u(d-c), or None when they don't cross."""
    r = sub(b, a)
    s = sub(d, c)
    den = r[0] * s[1] - r[1] * s[0]
    if abs(den) < 1e-12:
        return None
    qp = sub(c, a)
    t = (qp[0] * s[1] - qp[1] * s[0]) / den
    u = (qp[0] * r[1] - qp[1] * r[0]) / den
    if 0 <= t <= 1 and 0 <= u <= 1:
        return t, u
    return None
