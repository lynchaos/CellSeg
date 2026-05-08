#!/usr/bin/env python3
"""
convert_cyto3_to_onnx.py
Convert Cellpose cyto3 PyTorch weights to a quantised ONNX model
compatible with ONNX Runtime Android (opset 17, FP16).

Requirements:
    pip install cellpose torch onnx onnxruntime onnxsim

Usage:
    python tools/convert_cyto3_to_onnx.py --output cyto3-fp16.onnx

The output file should then be uploaded to:
    https://huggingface.co/kmlyyll/cellpose-cyto3-onnx
"""

import argparse
import os
import sys

try:
    import torch
    import onnx
    import onnxsim
    from cellpose.models import CellposeModel
except ImportError as exc:
    sys.exit(
        f"Missing dependency: {exc}.\n"
        "Install with: pip install cellpose torch onnx onnxruntime onnxsim"
    )


def _patch_make_style() -> None:
    """Replace dynamic kernel_size avg_pool in make_style with AdaptiveAvgPool2d.

    cellpose's make_style uses ``F.avg_pool2d(x, kernel_size=x.shape[2:])`` which
    produces a non-constant shape during ONNX tracing.  AdaptiveAvgPool2d(1) is
    semantically identical (global average pool → 1×1) and is fully ONNX-compatible.
    """
    import torch.nn as nn
    from cellpose import resnet_torch

    class _PatchedMakeStyle(nn.Module):
        def __init__(self, conv_3D: bool = False) -> None:
            super().__init__()
            self.flatten = nn.Flatten()
            self.pool = nn.AdaptiveAvgPool3d(1) if conv_3D else nn.AdaptiveAvgPool2d(1)

        def forward(self, x0):
            style = self.pool(x0)
            style = self.flatten(style)
            style = style / torch.sum(style ** 2, axis=1, keepdim=True) ** 0.5
            return style

    resnet_torch.make_style = _PatchedMakeStyle


def export(output_path: str, opset: int = 17) -> None:
    import torch.nn as nn

    _patch_make_style()
    print("Loading Cellpose cyto3 model …")
    model = CellposeModel(model_type="cyto3", gpu=False)
    # Re-apply patch to any already-instantiated make_style submodules
    for name, module in model.net.named_modules():
        if type(module).__name__ == "make_style":
            from cellpose import resnet_torch
            new_mod = resnet_torch.make_style(conv_3D=False)
            parent = model.net
            parts = name.split(".")
            for p in parts[:-1]:
                parent = getattr(parent, p)
            setattr(parent, parts[-1], new_mod)

    # Wrap to expose only the primary (1, 3, H, W) output.
    # CPnet.forward() returns (T[-1], style, T) — we only need T[-1].
    class _SingleOutputWrapper(nn.Module):
        def __init__(self, net: nn.Module) -> None:
            super().__init__()
            self.net = net

        def forward(self, x: torch.Tensor) -> torch.Tensor:
            return self.net(x)[0]

    net = _SingleOutputWrapper(model.net).eval()

    # Use a NON-ZERO dummy input.  The make_style module computes
    # style / ||style||, which is 0/0 = NaN when the input is all-zeros.
    # onnxsim would constant-fold that NaN into the weights, corrupting the model.
    dummy = torch.ones(1, 2, 256, 256, dtype=torch.float32) * 0.5

    print(f"Exporting to ONNX (opset {opset}) …")
    torch.onnx.export(
        net,
        dummy,
        output_path,
        opset_version=opset,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input":  {0: "batch", 2: "height", 3: "width"},
            "output": {0: "batch", 2: "height", 3: "width"},
        },
        dynamo=False,
    )

    print("Simplifying …")
    model_onnx = onnx.load(output_path)
    model_simplified, ok = onnxsim.simplify(model_onnx)
    if ok:
        onnx.save(model_simplified, output_path)
    else:
        print("Warning: simplification failed, using unsimplified model")

    # NOTE: FP16 weight conversion is intentionally NOT done.
    # convert_float_to_float16 with the zero dummy input above caused
    # make_style to constant-fold a 0/0 = NaN into the model, producing
    # completely wrong output (billions instead of [-15, 45]).
    # The model stays FP32 — ~25 MB is acceptable for on-device inference.

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"Saved: {output_path}  ({size_mb:.1f} MB)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output", default="cyto3-fp16.onnx",
                        help="Output ONNX file path (default: cyto3-fp16.onnx)")
    parser.add_argument("--opset", type=int, default=17,
                        help="ONNX opset version (default: 17)")
    args = parser.parse_args()
    export(args.output, args.opset)


if __name__ == "__main__":
    main()
