package uk.yaylali.cellseg.data.ml

import timber.log.Timber
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin implementation of Cellpose's flow-following post-processing.
 *
 * Algorithm (spec §13.5):
 * 1. Threshold the cellprob channel to obtain a foreground binary mask.
 * 2. For each foreground pixel, follow the (dY, dX) flow-vector field
 *    for [maxIter] steps using bilinear-interpolated gradient lookup.
 * 3. Cluster pixels by their converged final position (±1 pixel tolerance).
 * 4. Discard clusters whose area < [minSize].
 * 5. Return a label image (IntArray, row-major) where 0 = background and
 *    1..N = cell indices.
 *
 * Design constraints:
 * - Zero per-iteration heap allocation (bilinear sampling is fully inlined;
 *   flow-following loop only visits foreground pixels, not the full HW grid).
 * - Target: 1024×1024 with ~500 cells in 2–4 s on Pixel 7 class.
 */
object FlowFollowingPostprocessor {

    private const val MIN_SIZE_DEFAULT = 15
    private const val CELLPROB_THRESHOLD_DEFAULT = 0.0f

    /**
     * @param output    Flat float array from ONNX, shape (1, 3, H, W).
     *                  Channels: ch0=dY, ch1=dX, ch2=cellprob.
     * @param width     W
     * @param height    H
     * @param maxIter   Gradient-descent steps per pixel (from [SegmentationParams]).
     * @param flowThreshold   Reserved; not used in flow-following directly.
     * @param cellprobThreshold  Minimum cellprob to consider foreground.
     * @param minSize   Minimum cell area in pixels.
     * @return IntArray label image (row-major, size H×W). 0 = background, 1..N = cells.
     */
    fun postprocess(
        output: FloatArray,
        width: Int,
        height: Int,
        maxIter: Int = 250,
        flowThreshold: Float = 0.4f,
        cellprobThreshold: Float = CELLPROB_THRESHOLD_DEFAULT,
        minSize: Int = MIN_SIZE_DEFAULT,
    ): IntArray {
        val hw = width * height
        val maxX = (width - 1).toFloat()
        val maxY = (height - 1).toFloat()

        // output layout: [dY channel: 0..hw-1] [dX channel: hw..2hw-1] [prob channel: 2hw..3hw-1]
        // Access directly — avoids copying 3 × hw floats (~12 MB for 1000×1000).
        val dYOff = 0
        val dXOff = hw
        val probOff = 2 * hw

        // Collect foreground pixel *indices* once; the flow-following loop only
        // visits these, avoiding 250 passes over all background pixels.
        // Diagnostics: log cellprob range so we can detect NNAPI/model output problems.
        var probMin = Float.MAX_VALUE; var probMax = -Float.MAX_VALUE
        for (i in 0 until hw) {
            val p = output[probOff + i]
            if (p < probMin) probMin = p
            if (p > probMax) probMax = p
        }
        Timber.d("Postprocessor: cellprob min=%.3f max=%.3f threshold=%.3f size=%dx%d",
            probMin, probMax, cellprobThreshold, width, height)

        val fgIndices = IntArray(hw)
        var fgCount = 0
        for (i in 0 until hw) {
            if (output[probOff + i] > cellprobThreshold) fgIndices[fgCount++] = i
        }

        // Normalize flow vectors to unit length per-pixel (matches Python Cellpose: dP / (||dP|| + ε)).
        // Raw ONNX output flows have large magnitude; without normalization positions diverge or
        // barely move, so all clusters fall below minSize and the result is always 0 cells.
        val normDY = FloatArray(hw)
        val normDX = FloatArray(hw)
        for (i in 0 until hw) {
            val dy = output[dYOff + i]
            val dx = output[dXOff + i]
            val mag = sqrt(dy * dy + dx * dx) + 1e-20f
            normDY[i] = dy / mag
            normDX[i] = dx / mag
        }

        // Per-pixel current position (initialised to pixel centre).
        val posY = FloatArray(hw) { i -> (i / width).toFloat() }
        val posX = FloatArray(hw) { i -> (i % width).toFloat() }

        // Follow flows — fully inlined bilinear sampling, zero heap allocation.
        val w2 = width - 2   // safe upper bound for x0/y0 so x1/y1 never OOB
        val h2 = height - 2
        repeat(maxIter) {
            for (fi in 0 until fgCount) {
                val idx = fgIndices[fi]
                val py = posY[idx]
                val px = posX[idx]

                // Bilinear sample — inlined to avoid Pair<Float,Float> allocation
                val x0 = floor(px).toInt().coerceIn(0, w2)
                val y0 = floor(py).toInt().coerceIn(0, h2)
                val x1 = x0 + 1
                val y1 = y0 + 1
                val tx = px - x0
                val ty = py - y0
                val i00 = y0 * width + x0
                val i01 = y0 * width + x1
                val i10 = y1 * width + x0
                val i11 = y1 * width + x1
                val gy = (1f - ty) * ((1f - tx) * normDY[i00] + tx * normDY[i01]) +
                        ty * ((1f - tx) * normDY[i10] + tx * normDY[i11])
                val gx = (1f - ty) * ((1f - tx) * normDX[i00] + tx * normDX[i01]) +
                        ty * ((1f - tx) * normDX[i10] + tx * normDX[i11])

                posY[idx] = (py + gy).coerceIn(0f, maxY)
                posX[idx] = (px + gx).coerceIn(0f, maxX)
            }
        }

        // Cluster by converged position using histogram + local-maximum seeding,
        // matching Python Cellpose get_masks() behavior.
        //
        // Background: unit-normalised flows cause pixels to oscillate ±1 px around
        // the true attractor rather than stopping exactly there.  Exact-integer
        // matching puts oscillating pixels in separate tiny clusters, all filtered
        // away by minSize → 0 cells.  Python avoids this with a 2-D histogram,
        // a 5×5 max-filter to locate attractor seeds, and per-pixel nearest-seed
        // assignment — we replicate that here.

        val labels = IntArray(hw)

        // (1) Histogram of rounded final positions.
        val hist = IntArray(hw)
        val cyArr = IntArray(fgCount)
        val cxArr = IntArray(fgCount)
        for (fi in 0 until fgCount) {
            val idx = fgIndices[fi]
            val cy = posY[idx].roundToInt().coerceIn(0, height - 1)
            val cx = posX[idx].roundToInt().coerceIn(0, width - 1)
            cyArr[fi] = cy
            cxArr[fi] = cx
            hist[cy * width + cx]++
        }

        // (2) Find seeds: positions that are the strict local maximum in a 5×5 window.
        // Ties broken by row-major order so each local-max region produces one seed.
        // Equivalent to scipy.ndimage.maximum_filter1d(h,5) applied on both axes.
        val seedLabel = IntArray(hw)   // 0 = not a seed
        var nextSeed = 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cnt = hist[y * width + x]
                if (cnt == 0) continue
                var localMax = true
                outer@ for (dy in -2..2) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    for (dx in -2..2) {
                        if (dy == 0 && dx == 0) continue
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val c = hist[ny * width + nx]
                        // A strictly-larger neighbour, or an equal-count neighbour that
                        // appears earlier in row-major order, disqualifies this position.
                        if (c > cnt || (c == cnt && ny * width + nx < y * width + x)) {
                            localMax = false; break@outer
                        }
                    }
                }
                if (localMax) seedLabel[y * width + x] = nextSeed++
            }
        }
        val numSeeds = nextSeed - 1

        // (3) Assign each fg pixel to its nearest seed within SEARCH_R pixels.
        //     Direct histogram-hit (lbl != 0) is taken immediately; otherwise a
        //     (2·SEARCH_R+1)² window search finds the closest seed.
        val SEARCH_R = 6
        val clusterSizes = IntArray(numSeeds + 1)
        for (fi in 0 until fgCount) {
            val idx = fgIndices[fi]
            val cy = cyArr[fi]
            val cx = cxArr[fi]
            var lbl = seedLabel[cy * width + cx]   // direct seed hit
            if (lbl == 0) {
                var bestDist2 = Int.MAX_VALUE
                for (dy in -SEARCH_R..SEARCH_R) {
                    val ny = (cy + dy).coerceIn(0, height - 1)
                    for (dx in -SEARCH_R..SEARCH_R) {
                        val nx = (cx + dx).coerceIn(0, width - 1)
                        val sl = seedLabel[ny * width + nx]
                        if (sl != 0) {
                            val d2 = dy * dy + dx * dx
                            if (d2 < bestDist2) { bestDist2 = d2; lbl = sl }
                        }
                    }
                }
            }
            if (lbl != 0) {
                labels[idx] = lbl
                clusterSizes[lbl]++
            }
        }

        // (4) Re-index to contiguous 1..M, dropping clusters smaller than minSize.
        val remap = IntArray(numSeeds + 1)
        var newLabel = 0
        for (i in 1..numSeeds) {
            if (clusterSizes[i] >= minSize) remap[i] = ++newLabel
        }
        for (idx in 0 until hw) {
            labels[idx] = remap[labels[idx]]
        }

        Timber.d("Postprocessor: fgCount=%d rawClusters=%d finalCells=%d minSize=%d",
            fgCount, numSeeds, newLabel, minSize)
        return labels
    }
}
