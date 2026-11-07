package com.dream.tv.player

/**
 * m3u8 广告切片过滤 —— 逐逻辑移植桌面版 lib/utils/m3u8-utils.ts + m3u8-ad-*.ts：
 * ① 启发式块分析（文件名前缀/路径/时长网格/重复签名五维评分）
 * ② SCTE-35 CUE-OUT/CUE-IN 状态机
 * ③ HLS 插播元数据（DATERANGE interstitial）剥离
 */
object M3u8AdFilter {

    private val AD_PATH_KEYWORDS = listOf(
        "advert", "preroll", "midroll", "postroll",
        "dai", "vast", "ima", "adjump", "commercial", "sponsor",
    )

    private val INTERSTITIAL_MARKERS = listOf(
        "class=\"com.apple.hls.interstitial\"",
        "x-asset-uri=", "x-asset-list=", "x-playout-limit=", "x-restrict=", "cue=\"once\"",
    )

    private val AUX_PREFIXES = listOf(
        "#EXT-X-ASSET:", "#EXT-X-CUE-OUT-CONT", "#EXT-X-PLACEMENT-OPPORTUNITY",
        "#EXT-OATCLS-SCTE35", "#EXT-X-SCTE35",
    )

    private val NTSC_FRACTIONS = setOf(
        33, 67, 133, 167, 233, 267, 333, 367, 433, 467,
        533, 567, 633, 667, 733, 767, 833, 867, 933, 967,
    )
    private val FILM24_FRACTIONS = setOf(2, 4, 6, 8, 12, 16, 20, 24)

    private data class Segment(val url: String, val duration: Double, val lineIndex: Int)
    private data class Block(val segments: MutableList<Segment>, val startLineIndex: Int, var hasCueTag: Boolean)
    private data class MainPattern(
        val filenamePrefix: String,
        val avgDuration: Double,
        val pathPrefix: String,
        val origin: String,
        val ntscGrid: Boolean,
        val filmGrid: Boolean,
    )

    /** 入口：广告过滤（启发式，桌面版默认档位） */
    fun filter(content: String): String {
        if (!content.startsWith("#EXTM3U")) return content
        val lines = content.split(Regex("\r?\n"))
        val hasCueTag = content.contains("#EXT-X-CUE-OUT") || content.contains("#EXT-X-CUE-IN")

        // ---------- 启发式块分析 ----------
        val adLineIndices = HashSet<Int>()
        if (!hasCueTag) {
            val blocks = parseBlocks(lines)
            if (blocks.isNotEmpty()) {
                val main = learnMainPattern(blocks)
                val duplicates = findDuplicateSignatureBlockIndices(blocks)
                for ((blockIdx, block) in blocks.withIndex()) {
                    val isDup = duplicates.contains(blockIdx)
                    val score = scoreBlock(block, main, isDup)
                    if (score >= 5.0) {
                        for (seg in block.segments) {
                            adLineIndices.add(seg.lineIndex)
                            adLineIndices.add(seg.lineIndex - 1) // EXTINF 行
                        }
                    } else if (block.segments.isNotEmpty()) {
                        // 单段独立评分（无 DISCONTINUITY 包裹的散广告）
                        for (seg in block.segments) {
                            val single = Block(mutableListOf(seg), seg.lineIndex - 1, false)
                            if (scoreBlock(single, main, false) >= 4.0) {
                                adLineIndices.add(seg.lineIndex)
                                adLineIndices.add(seg.lineIndex - 1)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 行状态机输出 ----------
        val out = ArrayList<String>(lines.size)
        var insideCueAd = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()

            if (adLineIndices.contains(i)) { i++; continue }
            if (isInterstitial(t) || isAuxMetadata(t)) { i++; continue }

            if (t.startsWith("#EXT-X-CUE-OUT")) {
                insideCueAd = true
                if (out.isNotEmpty() && out.last().trim() == "#EXT-X-DISCONTINUITY") out.removeAt(out.size - 1)
                i++; continue
            }
            if (t.startsWith("#EXT-X-CUE-IN")) {
                insideCueAd = false
                if (i + 1 < lines.size && lines[i + 1].trim() == "#EXT-X-DISCONTINUITY") i++
                i++; continue
            }
            if (insideCueAd) { i++; continue }

            out.add(line)
            i++
        }
        return out.joinToString("\n")
    }

    // ---------- 块解析 ----------

    private fun parseBlocks(lines: List<String>): List<Block> {
        val blocks = ArrayList<Block>()
        var current = Block(mutableListOf(), 0, false)
        for ((index, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-CUE-OUT") || line.startsWith("#EXT-X-CUE-IN")) {
                current.hasCueTag = true
            }
            if (line == "#EXT-X-DISCONTINUITY") {
                if (current.segments.isNotEmpty()) blocks.add(current)
                current = Block(mutableListOf(), index + 1, false)
                continue
            }
            if (!line.startsWith("#EXTINF:")) continue
            val duration = Regex("#EXTINF:([\\d.]+)").find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val url = lines.getOrNull(index + 1)?.trim()
            if (!url.isNullOrBlank() && !url.startsWith("#")) {
                current.segments.add(Segment(url, duration, index + 1))
            }
        }
        if (current.segments.isNotEmpty()) blocks.add(current)
        return blocks
    }

    // ---------- 主模式学习 ----------

    private fun learnMainPattern(blocks: List<Block>): MainPattern {
        val main = blocks.maxByOrNull { it.segments.size }
            ?: return MainPattern("", 0.0, "", "", false, false)
        if (main.segments.isEmpty()) return MainPattern("", 0.0, "", "", false, false)

        val filenames = main.segments.map { extractFilename(it.url) }
        val prefix = commonPrefix(filenames)
        val avg = main.segments.sumOf { it.duration } / main.segments.size
        val loc = extractLocation(main.segments[0].url)
        return MainPattern(
            filenamePrefix = prefix,
            avgDuration = avg,
            pathPrefix = loc.first,
            origin = loc.second,
            ntscGrid = hasDominantGrid(main.segments, NTSC_FRACTIONS, 0.30),
            filmGrid = hasDominantGrid(main.segments, FILM24_FRACTIONS, 0.35),
        )
    }

    private fun commonPrefix(strings: List<String>): String {
        if (strings.size < 2) return ""
        var prefix = ""
        for (i in strings[0].indices) {
            val c = strings[0][i]
            if (!strings.all { it.getOrNull(i) == c }) break
            prefix += c
        }
        return prefix
    }

    private fun extractFilename(url: String): String = url.substringAfterLast('/')

    private fun extractLocation(url: String): Pair<String, String> {
        val path = if (url.contains("://")) {
            val schemeEnd = url.indexOf("://") + 3
            val pathStart = url.indexOf('/', schemeEnd)
            if (pathStart >= 0) url.substring(0, pathStart) + url.substring(pathStart) else url
        } else url
        val lastSlash = path.lastIndexOf('/')
        val prefix = if (lastSlash >= 0) path.substring(0, lastSlash + 1) else ""
        val origin = if (url.contains("://")) url.substring(0, url.indexOf("://") + 3).let {
            val slash = url.indexOf('/', it.length)
            if (slash >= 0) url.substring(0, slash) else url
        } else ""
        return prefix to origin
    }

    // ---------- 重复签名 ----------

    private fun findDuplicateSignatureBlockIndices(blocks: List<Block>): Set<Int> {
        val dups = HashSet<Int>()
        if (blocks.size < 2) return dups
        val mainIdx = blocks.indices.maxByOrNull { blocks[it].segments.size } ?: return dups
        val maxSegments = blocks[mainIdx].segments.size
        val mainAvg = if (maxSegments > 0) blocks[mainIdx].segments.sumOf { it.duration } / maxSegments else 0.0

        val signatureMap = HashMap<String, MutableList<Int>>()
        blocks.forEachIndexed { index, block ->
            if (block.segments.size < 3) return@forEachIndexed
            val first = block.segments[0].duration
            val uniform = block.segments.all { abs(it.duration - first) < 0.005 }
            val matchesMain = mainAvg > 0 && abs(first - mainAvg) < 0.05
            if (uniform && matchesMain) return@forEachIndexed
            val sig = block.segments.joinToString(",") { String.format("%.3f", it.duration) }
            signatureMap.getOrPut(sig) { mutableListOf() }.add(index)
        }
        val maxOccurrences = maxOf(2, (blocks.size * 0.3).toInt())
        for ((_, indices) in signatureMap) {
            if (indices.size < 2 || indices.size > maxOccurrences) continue
            for (index in indices) {
                if (index != mainIdx && blocks[index].segments.size < maxSegments * 0.8) dups.add(index)
            }
        }
        return dups
    }

    // ---------- 评分 ----------

    private fun scoreBlock(block: Block, main: MainPattern, isDuplicate: Boolean): Double {
        if (block.hasCueTag || isDuplicate) return 10.0
        var score = 0.0
        // 关键词
        for (seg in block.segments) {
            val url = seg.url.lowercase()
            if (AD_PATH_KEYWORDS.any { url.contains(it) }) { score += 2.5; break }
        }
        if (block.segments.isEmpty()) return score
        // 文件名前缀不符
        if (main.filenamePrefix.length >= 2) {
            if (block.segments.all { !extractFilename(it.url).startsWith(main.filenamePrefix) }) score += 1.5
        }
        // 路径/域不符
        if (main.pathPrefix.isNotEmpty() || main.origin.isNotEmpty()) {
            val locations = block.segments.map { extractLocation(it.url) }
            if (locations.all { it.first != main.pathPrefix }) score += 5.0
            else if (main.origin.isNotEmpty() && locations.all { it.second.isNotEmpty() && it.second != main.origin }) score += 3.5
        }
        // 时长离群（小块）
        if (main.avgDuration > 0 && block.segments.size <= 6) {
            val blockAvg = block.segments.sumOf { it.duration } / block.segments.size
            val ratio = blockAvg / main.avgDuration
            if (ratio < 0.6 || ratio > 1.8) score += 1.5
        }
        // 时长网格（帧率签名）
        if (!main.ntscGrid && isSmallGrid(block.segments, NTSC_FRACTIONS, 0.8)) score += 3.5
        if (!main.filmGrid && isSmallGrid(block.segments, FILM24_FRACTIONS, 0.75)) score += 3.5
        return score
    }

    // ---------- 时长网格 ----------

    private fun msFraction(duration: Double): Int =
        (Math.abs(duration - kotlin.math.floor(duration)) * 1000).roundToInt() % 1000

    private fun gridRatio(segments: List<Segment>, fractions: Set<Int>): Double {
        if (segments.isEmpty()) return 0.0
        return segments.count { fractions.contains(msFraction(it.duration)) }.toDouble() / segments.size
    }

    private fun hasDominantGrid(segments: List<Segment>, fractions: Set<Int>, threshold: Double): Boolean =
        segments.size >= 4 && gridRatio(segments, fractions) > threshold

    private fun isSmallGrid(segments: List<Segment>, fractions: Set<Int>, threshold: Double): Boolean =
        segments.size in 2..10 && gridRatio(segments, fractions) >= threshold

    // ---------- 插播元数据 ----------

    private fun isInterstitial(t: String): Boolean {
        if (!t.startsWith("#EXT-X-DATERANGE:")) return false
        val lower = t.lowercase()
        return INTERSTITIAL_MARKERS.any { lower.contains(it) }
    }

    private fun isAuxMetadata(t: String): Boolean =
        AUX_PREFIXES.any { t.startsWith(it) }

    private fun abs(x: Double): Double = kotlin.math.abs(x)
    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
