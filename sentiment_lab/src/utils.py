"""재현 가능한 감성 실험을 위한 작은 유틸리티 모음."""

import json
import random
from pathlib import Path
from typing import Any

import numpy as np

try:
    import torch
except ImportError:  # pragma: no cover
    torch = None


def set_seed(seed: int = 42) -> None:
    """Python, NumPy, PyTorch 시드를 고정한다."""
    random.seed(seed)
    np.random.seed(seed)

    if torch is not None:
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)


def save_json(data: Any, output_path: Path) -> None:
    """파이썬 객체를 JSON 파일로 저장한다."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    set_seed(42)
    print("Seed configured.")
