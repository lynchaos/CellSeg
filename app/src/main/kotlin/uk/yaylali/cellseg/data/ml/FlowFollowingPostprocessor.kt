package uk.yaylali.cellseg.data.ml

import timber.log.Timber
import kotlin.math.floor

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
                val gy = (1f - ty) * ((1f - tx) * output[dYOff + i00] + tx * output[dYOff + i01]) +
                        ty * ((1f - tx) * output[dYOff + i10] + tx * output[dYOff + i11])
                val gx = (1f - ty) * ((1f - tx) * output[dXOff + i00] + tx * output[dXOff + i01]) +
                        ty * ((1f - tx) * output[dXOff + i10] + tx * output[dXOff + i11])

                posY[idx] = (py + gy).coerceIn(0f, maxY)
                posX[idx] = (px + gx).coerceIn(0f, maxX)
            }
        }

        // Cluster by converged position — discretise to integer grid cell.
        val labels = IntArray(hw)
        val clusterMap = HashMap<Long, Int>(fgCount / 2 + 1)
        var nextLabel = 1
        val clusterSizes = mutableListOf<Int>()  // index = label - 1

        for (fi in 0 until fgCount) {
            val idx = fgIndices[fi]
            val cy = posY[idx].toInt()
            val cx = posX[idx].toInt()
            val key = cy.toLong() * width + cx
            val existing = clusterMap[key]
            if (existing != null) {
                labels[idx] = existing
                clusterSizes[existing - 1]++
            } else {
                clusterMap[key] = nextLabel
                labels[idx] = nextLabel
                clusterSizes.add(1)
                nextLabel++
            }
        }

        // Filter small clusters and re-index labels to contiguous 1..M.
        val remap = IntArray(nextLabel)
        var newLabel = 0
        for (i in 1 until nextLabel) {
            if (clusterSizes[i - 1] >= minSize) remap[i] = ++newLabel
        }
        for (idx in 0 until hw) {
            labels[idx] = remap[labels[idx]]
        }

        Timber.d("Postprocessor: fgCount=%d rawClusters=%d finalCells=%d minSize=%d",
            fgCount, nextLabel - 1, newLabel, minSize)
        return labels
    }
}
