import fs from "node:fs/promises";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const defaultInput = "";
const defaultOutDir = path.join(scriptDir, "outputs/inos_cd86_cd206_tgfb_charts");

function parseArgs(argv) {
  const args = { input: defaultInput, outDir: defaultOutDir };
  for (let i = 2; i < argv.length; i += 1) {
    const item = argv[i];
    // R wrapper forwards --options; consume it so options.json is not treated as the data file.
    if (item === "--input") args.input = argv[++i];
    else if (item === "--output") args.output = argv[++i];
    else if (item === "--out-dir") args.outDir = argv[++i];
    else if (item === "--options") args.options = argv[++i];
    else if (item.startsWith("--") && i + 1 < argv.length && !argv[i + 1].startsWith("--")) i += 1;
    else if (!item.startsWith("--")) args.input = item;
  }
  return args;
}

function mean(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function sd(values) {
  if (values.length < 2) return 0;
  const m = mean(values);
  return Math.sqrt(values.reduce((sum, value) => sum + (value - m) ** 2, 0) / (values.length - 1));
}

function logGamma(z) {
  const p = [676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.615029162590, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7];
  if (z < 0.5) return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * z)) - logGamma(1 - z);
  z -= 1;
  let x = 0.99999999999980993;
  for (let i = 0; i < p.length; i += 1) x += p[i] / (z + i + 1);
  const t = z + p.length - 0.5;
  return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
}

function betaContinuedFraction(x, a, b) {
  const qab = a + b;
  const qap = a + 1;
  const qam = a - 1;
  let c = 1;
  let d = 1 - (qab * x) / qap;
  if (Math.abs(d) < 1e-30) d = 1e-30;
  d = 1 / d;
  let h = d;
  for (let m = 1; m <= 200; m += 1) {
    const m2 = 2 * m;
    let aa = (m * (b - m) * x) / ((qam + m2) * (a + m2));
    d = 1 + aa * d;
    if (Math.abs(d) < 1e-30) d = 1e-30;
    c = 1 + aa / c;
    if (Math.abs(c) < 1e-30) c = 1e-30;
    d = 1 / d;
    h *= d * c;
    aa = (-(a + m) * (qab + m) * x) / ((a + m2) * (qap + m2));
    d = 1 + aa * d;
    if (Math.abs(d) < 1e-30) d = 1e-30;
    c = 1 + aa / c;
    if (Math.abs(c) < 1e-30) c = 1e-30;
    d = 1 / d;
    const del = d * c;
    h *= del;
    if (Math.abs(del - 1) < 3e-12) break;
  }
  return h;
}

function regularizedBeta(x, a, b) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const bt = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x));
  if (x < (a + 1) / (a + b + 2)) return (bt * betaContinuedFraction(x, a, b)) / a;
  return 1 - (bt * betaContinuedFraction(1 - x, b, a)) / b;
}

function studentTCdf(t, df) {
  if (!Number.isFinite(df) || df <= 0) return 0.5;
  const x = df / (df + t * t);
  const ib = regularizedBeta(x, df / 2, 0.5);
  return t >= 0 ? 1 - 0.5 * ib : 0.5 * ib;
}

function welchPValue(a, b) {
  const va = sd(a) ** 2;
  const vb = sd(b) ** 2;
  const na = a.length;
  const nb = b.length;
  const se = Math.sqrt(va / na + vb / nb);
  if (!Number.isFinite(se) || se === 0) return 1;
  const t = Math.abs((mean(a) - mean(b)) / se);
  const df = ((va / na + vb / nb) ** 2) / ((va * va) / (na * na * (na - 1)) + (vb * vb) / (nb * nb * (nb - 1)));
  return Math.max(0, Math.min(1, 2 * (1 - studentTCdf(t, df))));
}

function sigMark(p) {
  if (p < 0.0001) return "****";
  if (p < 0.001) return "***";
  if (p < 0.01) return "**";
  if (p < 0.05) return "*";
  return "ns";
}

function sanitizeName(name) {
  return String(name).replace(/[\\/:*?"<>|β]/g, (m) => (m === "β" ? "beta" : "_"));
}

async function loadRows(input) {
  const extension = path.extname(input).toLowerCase();
  if (extension === ".json") {
    const extracted = JSON.parse(await fs.readFile(input, "utf8"));
    return rowsFromExtracted(extracted, input);
  }
  if (extension === ".csv" || extension === ".txt" || extension === ".tsv") {
    return parseDelimited(await fs.readFile(input, "utf8"));
  }
  if (extension === ".xls" || extension === ".xlsx" || extension === ".xlsm") {
    return loadWorkbookRows(input);
  }
  throw new Error(`不支持的输入文件类型: ${extension || "未知"}，请上传 xls/xlsx/csv/txt 文件`);
}

function rowsFromExtracted(extracted, input) {
  const sheet = Array.isArray(extracted)
    ? extracted.find((item) => item && Array.isArray(item.rows))
    : extracted;
  if (sheet && Array.isArray(sheet.rows)) {
    return sheet.rows;
  }
  if (Array.isArray(extracted) && extracted.every((row) => Array.isArray(row))) {
    return extracted;
  }
  throw new Error(`未能从 ${input} 读取表格行数据`);
}

async function loadWorkbookRows(input) {
  try {
    const module = await import("xlsx");
    const XLSX = module.default || module;
    const workbook = XLSX.readFile(input, { cellDates: false });
    const sheetName = workbook.SheetNames[0];
    if (!sheetName) throw new Error("Excel 文件没有可读取的工作表");
    const rows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], {
      header: 1,
      defval: "",
      raw: true,
    });
    return rows
      .map((row) => row.map(normalizeCell))
      .filter((row) => row.some((item) => String(item).trim()));
  } catch (error) {
    return loadWorkbookRowsWithLegacyExtractor(input, error);
  }
}

function loadWorkbookRowsWithLegacyExtractor(input, cause) {
  const extractor = path.join(scriptDir, "extract_xls_biff.py");
  const result = spawnSync("python3", [extractor, input], { encoding: "utf8", maxBuffer: 20 * 1024 * 1024 });
  if (result.status !== 0) {
    const reason = [cause.message, result.stderr || result.stdout]
      .filter(Boolean)
      .join("\n");
    throw new Error(reason || `读取 ${input} 失败`);
  }
  const extracted = JSON.parse(result.stdout);
  return rowsFromExtracted(extracted, input);
}

function normalizeCell(value) {
  if (value == null) return "";
  if (value instanceof Date) return value.toISOString();
  return value;
}

function detectDelimiter(text) {
  const firstLine = text.split(/\r?\n/).find((line) => line.trim()) || "";
  const tabs = (firstLine.match(/\t/g) || []).length;
  const commas = (firstLine.match(/,/g) || []).length;
  return tabs > commas ? "\t" : ",";
}

function parseDelimited(text) {
  const delimiter = detectDelimiter(text);
  const rows = [];
  let row = [];
  let value = "";
  let quoted = false;

  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        value += '"';
        i += 1;
      } else if (char === '"') {
        quoted = false;
      } else {
        value += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === delimiter) {
      row.push(value);
      value = "";
    } else if (char === "\n") {
      row.push(value);
      rows.push(row);
      row = [];
      value = "";
    } else if (char !== "\r") {
      value += char;
    }
  }

  if (value.length > 0 || row.length > 0) {
    row.push(value);
    rows.push(row);
  }
  return rows.filter((items) => items.some((item) => String(item).trim()));
}

function numericCell(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const number = Number(String(value ?? "").trim());
  return Number.isFinite(number) ? number : null;
}

function parseChartData(rows) {
  if (rows.length > 0) {
    const header = rows[0].map((item) => String(item).trim().toLowerCase());
    if (header.includes("marker") && header.includes("group") && header.includes("value")) {
      return parseStandardGroupedFormat(rows);
    }
  }
  return parseTwoBlockGeneFormat(rows);
}

function parseStandardGroupedFormat(rows) {
  const header = rows[0].map((item) => String(item).trim().toLowerCase());
  const markerIndex = header.indexOf("marker");
  const groupIndex = header.indexOf("group");
  const valueIndex = header.indexOf("value");
  const geneOrder = [];
  const geneMap = new Map();

  for (const row of rows.slice(1)) {
    const gene = String(row[markerIndex] || "").trim();
    const group = String(row[groupIndex] || "").trim();
    const value = numericCell(row[valueIndex]);
    if (!gene || !group || !Number.isFinite(value)) continue;
    if (!geneMap.has(gene)) {
      geneMap.set(gene, new Map());
      geneOrder.push(gene);
    }
    const groupMap = geneMap.get(gene);
    if (!groupMap.has(group)) groupMap.set(group, []);
    groupMap.get(group).push(value);
  }

  return geneOrder.map((gene) => {
    const groupMap = geneMap.get(gene);
    const labels = orderedGroupLabels(groupMap);
    return {
      gene,
      groups: labels.map((label) => ({ label, values: groupMap.get(label) })),
    };
  }).filter((item) => item.groups.length >= 2);
}

function parseTwoBlockGeneFormat(rows) {
  const geneOrder = [];
  const geneMap = new Map();

  for (let r = 0; r < rows.length; r += 1) {
    const row = rows[r] || [];
    const headers = row
      .map((value, col) => ({ value, col }))
      .filter(({ value }) => typeof value === "string" && value.trim());
    if (headers.length < 2) continue;

    for (let dataRowIndex = r + 1; dataRowIndex < rows.length; dataRowIndex += 1) {
      const dataRow = rows[dataRowIndex] || [];
      const group = dataRow[1];
      if (typeof group !== "string" || !group.trim()) break;

      for (const { value: gene, col } of headers) {
        const values = [dataRow[col], dataRow[col + 1], dataRow[col + 2]]
          .map(numericCell)
          .filter((cell) => Number.isFinite(cell));
        if (values.length < 2) continue;
        const geneName = gene.trim();
        const groupName = group.trim();
        if (!geneMap.has(geneName)) {
          geneMap.set(geneName, new Map());
          geneOrder.push(geneName);
        }
        const groupMap = geneMap.get(geneName);
        if (!groupMap.has(groupName)) groupMap.set(groupName, []);
        groupMap.get(groupName).push(...values);
      }
    }
  }

  return geneOrder.map((gene) => {
    const groupMap = geneMap.get(gene);
    const labels = orderedGroupLabels(groupMap);
    return {
      gene,
      groups: labels.map((label) => ({ label, values: groupMap.get(label) })),
    };
  });
}

function orderedGroupLabels(groupMap) {
  const labels = [...groupMap.keys()];
  const preferredGroupOrder = ["M0", "M1", "M2"];
  const allPreferred = labels.every((label) => preferredGroupOrder.includes(label));
  if (!allPreferred) return labels;
  return labels.sort((a, b) => preferredGroupOrder.indexOf(a) - preferredGroupOrder.indexOf(b));
}

function niceYMax(maxValue) {
  const padded = maxValue * 1.28;
  if (padded <= 2) return 2;
  if (padded <= 4) return 4;
  if (padded <= 6) return 6;
  if (padded <= 8) return 8;
  if (padded <= 10) return 10;
  return Math.ceil(padded / 2) * 2;
}

function buildComparisons(stats) {
  const comparisons = [];
  for (let i = 0; i < stats.length; i += 1) {
    for (let j = i + 1; j < stats.length; j += 1) {
      const p = welchPValue(stats[i].values, stats[j].values);
      comparisons.push({ from: i, to: j, p, sig: sigMark(p), span: j - i });
    }
  }
  return comparisons.sort((a, b) => a.span - b.span || a.from - b.from || a.to - b.to);
}

function renderChart(data, compact = false) {
  const stats = data.groups.map((group) => ({ ...group, mean: mean(group.values), sd: sd(group.values) }));
  const comparisons = buildComparisons(stats);
  const width = compact ? 430 : 430;
  const height = compact ? 610 : 640;
  const margin = compact ? { left: 125, right: 28, top: 64, bottom: 160 } : { left: 122, right: 28, top: 52, bottom: 118 };
  const plotH = height - margin.top - margin.bottom;
  const x0 = margin.left;
  const y0 = height - margin.bottom;
  const maxValue = Math.max(...stats.flatMap((s) => [s.mean + s.sd, ...s.values]));
  const yMax = niceYMax(maxValue);
  const tickStep = yMax <= 4 ? 1 : 2;
  const yScale = (value) => y0 - (value / yMax) * plotH;
  const barW = compact ? 58 : 58;
  const centerGap = compact ? 86 : 86;
  const firstX = x0 + 34 + barW / 2;
  const xs = stats.map((_, index) => firstX + index * centerGap);
  const axisEnd = xs[xs.length - 1] + barW / 2 + 24;
  const colors = ["#ff2a00", "#1f55ff", "#00d92f", "#ff34d2", "#b27a32"];
  const dotOffsets = [-16, -5, 6, 17, -10, 11];

  const ticks = [];
  for (let value = 0; value <= yMax + 1e-9; value += tickStep) ticks.push(value);
  const tickSvg = ticks.map((value) => `
    <line x1="${x0 - 18}" y1="${yScale(value)}" x2="${x0}" y2="${yScale(value)}" stroke="#111" stroke-width="5"/>
    <text x="${x0 - 30}" y="${yScale(value) + 12}" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="34" font-weight="700">${value}</text>
  `).join("");

  const barSvg = stats.map((stat, index) => {
    const x = xs[index] - barW / 2;
    const y = yScale(stat.mean);
    const errLow = yScale(Math.max(0, stat.mean - stat.sd));
    const errTop = yScale(stat.mean + stat.sd);
    const dots = stat.values.map((value, dotIndex) => `<circle cx="${xs[index] + dotOffsets[dotIndex % dotOffsets.length]}" cy="${yScale(value)}" r="${compact ? 7 : 8}" fill="#000"/>`).join("");
    return `
      <rect x="${x}" y="${y}" width="${barW}" height="${y0 - y}" fill="none" stroke="${colors[index % colors.length]}" stroke-width="6"/>
      <line x1="${xs[index]}" y1="${errLow}" x2="${xs[index]}" y2="${errTop}" stroke="#111" stroke-width="5"/>
      <line x1="${xs[index] - 27}" y1="${errTop}" x2="${xs[index] + 27}" y2="${errTop}" stroke="#111" stroke-width="5"/>
      <line x1="${xs[index] - 27}" y1="${errLow}" x2="${xs[index] + 27}" y2="${errLow}" stroke="#111" stroke-width="5"/>
      ${dots}
      <line x1="${xs[index]}" y1="${y0}" x2="${xs[index]}" y2="${y0 + 20}" stroke="#111" stroke-width="5"/>
      <text x="${xs[index]}" y="${y0 + 42}" transform="rotate(-45 ${xs[index]} ${y0 + 42})" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="${compact ? 30 : 31}" font-weight="700">${stat.label}</text>
    `;
  }).join("");

  const levelRatios = [0.68, 0.80, 0.98, 0.91, 0.79, 0.87];
  const bracketSvg = comparisons.map((comparison, index) => {
    const y = yScale(yMax * (levelRatios[index] ?? Math.min(0.97, 0.72 + index * 0.07)));
    const labelOffset = comparison.sig.includes("*") ? 20 : 16;
    const labelSize = comparison.sig.includes("*") ? 44 : 30;
    return `
      <path d="M ${xs[comparison.from]} ${y + 24} L ${xs[comparison.from]} ${y} L ${xs[comparison.to]} ${y} L ${xs[comparison.to]} ${y + 24}" fill="none" stroke="#111" stroke-width="5"/>
      <text x="${(xs[comparison.from] + xs[comparison.to]) / 2}" y="${y - labelOffset}" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="${labelSize}" font-weight="700">${comparison.sig}</text>
    `;
  }).join("");

  return {
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <rect width="100%" height="100%" fill="#fff"/>
      <line x1="${x0}" y1="${margin.top}" x2="${x0}" y2="${y0}" stroke="#111" stroke-width="6"/>
      ${tickSvg}
      ${barSvg}
      <line x1="${x0}" y1="${y0}" x2="${axisEnd}" y2="${y0}" stroke="#111" stroke-width="6"/>
      ${bracketSvg}
      <text x="${compact ? 52 : 50}" y="${margin.top + plotH / 2}" transform="rotate(-90 ${compact ? 52 : 50} ${margin.top + plotH / 2})" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="${compact ? 36 : 36}" font-weight="700">${data.gene} mRNA (FC)</text>
    </svg>`,
    summary: {
      gene: data.gene,
      groups: stats.map((stat) => ({ label: stat.label, values: stat.values, mean: Number(stat.mean.toFixed(4)), sd: Number(stat.sd.toFixed(4)) })),
      comparisons: comparisons.map((comparison) => ({
        comparison: `${stats[comparison.from].label} vs ${stats[comparison.to].label}`,
        p: Number(comparison.p.toPrecision(4)),
        sig: comparison.sig,
      })),
    },
  };
}

function combinedSvg(chartData) {
  const cellW = 430;
  const cellH = 610;
  const gapX = 28;
  const gapY = 28;
  const rowCount = Math.ceil(chartData.length / 2);
  const width = cellW * 2 + gapX;
  const height = rowCount * cellH + (rowCount - 1) * gapY;
  const panels = chartData.map((data, index) => {
    const { svg } = renderChart(data, true);
    const inner = svg.replace(/^<svg[^>]*>/, "").replace("</svg>", "");
    const x = (index % 2) * (cellW + gapX);
    const y = Math.floor(index / 2) * (cellH + gapY);
    return `<g transform="translate(${x},${y})">${inner}</g>`;
  }).join("\n");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" fill="#fff"/>${panels}</svg>`;
}

async function writeChart(data, outDir) {
  const result = renderChart(data);
  const base = path.join(outDir, `${sanitizeName(data.gene)}_PCR_template_style`);
  await fs.writeFile(`${base}.svg`, result.svg, "utf8");
  await sharp(Buffer.from(result.svg), { density: 260 }).png().toFile(`${base}.png`);
  return { ...result, pngPath: `${base}.png`, svgPath: `${base}.svg` };
}

const args = parseArgs(process.argv);
if (!args.input) throw new Error("缺少输入文件");
await fs.mkdir(args.outDir, { recursive: true });
const rows = await loadRows(args.input);
const chartData = parseChartData(rows);
if (chartData.length === 0) throw new Error("没有识别到 iNOS/CD86/CD206/TGF-β 这类双块多基因数据。");

const results = [];
for (const data of chartData) results.push(await writeChart(data, args.outDir));

const combined = combinedSvg(chartData);
const combinedSvgPath = path.join(args.outDir, "iNOS_CD86_CD206_TGFb_PCR_combined.svg");
const combinedPngPath = path.join(args.outDir, "iNOS_CD86_CD206_TGFb_PCR_combined.png");
await fs.writeFile(combinedSvgPath, combined, "utf8");
await sharp(Buffer.from(combined), { density: 220 }).png().toFile(combinedPngPath);

const summary = {
  input: args.input,
  note: "同一基因中重复出现的 M0 已合并；每个基因按 M0、M1、M2 顺序作图，并进行全部两两比较。",
  summary: results.map((result, index) => ({
    ...result.summary,
    png: result.pngPath,
    svg: result.svgPath,
  })),
  combinedPngPath,
  combinedSvgPath,
};
const summaryPath = path.join(args.outDir, "PCR_chart_summary.json");
await fs.writeFile(summaryPath, JSON.stringify(summary, null, 2), "utf8");
if (args.output) {
  await fs.copyFile(combinedPngPath, args.output);
}
console.log(JSON.stringify({ ...summary, summaryPath }, null, 2));
