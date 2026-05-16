args <- commandArgs(trailingOnly = TRUE)

read_arg <- function(name) {
  index <- match(name, args)
  if (is.na(index) || index == length(args)) {
    stop(paste("缺少参数", name), call. = FALSE)
  }
  args[index + 1]
}

input <- read_arg("--input")
output <- read_arg("--output")
options_path <- read_arg("--options")

read_options <- function(path) {
  if (!file.exists(path)) {
    return("{}")
  }
  paste(readLines(path, warn = FALSE, encoding = "UTF-8"), collapse = "")
}

option_text <- function(options, name, default) {
  pattern <- paste0('"', name, '"\\s*:\\s*"([^"]*)"')
  matched <- regmatches(options, regexpr(pattern, options, perl = TRUE))
  if (length(matched) == 0 || matched == "") {
    return(default)
  }
  sub(paste0('.*"', name, '"\\s*:\\s*"([^"]*)".*'), "\\1", matched, perl = TRUE)
}

palette_colors <- function(name) {
  switch(
    name,
    red = colorRampPalette(c("#4E79A7", "#FFFFFF", "#D94F45"))(80),
    violet = colorRampPalette(c("#4ECDC4", "#FFFFFF", "#7B61FF"))(80),
    dark = colorRampPalette(c("#1D3557", "#F1FAEE", "#91C7B1"))(80),
    colorRampPalette(c("#2A9D8F", "#FFFFFF", "#4E79A7"))(80)
  )
}

options <- read_options(options_path)
data <- tryCatch(
  read.csv(input, check.names = FALSE, stringsAsFactors = FALSE),
  error = function(e) stop(paste("文件无法解析为 CSV:", e$message), call. = FALSE)
)

if (nrow(data) == 0 || ncol(data) < 2) {
  stop("热图至少需要两列且包含数据行", call. = FALSE)
}

numeric_columns <- names(data)[vapply(data, function(column) {
  values <- suppressWarnings(as.numeric(column))
  sum(!is.na(values)) >= max(1, floor(length(values) * 0.6))
}, logical(1))]

if (length(numeric_columns) < 2) {
  stop("热图需要至少两列数值字段", call. = FALSE)
}

matrix_data <- as.matrix(data[, numeric_columns, drop = FALSE])
storage.mode(matrix_data) <- "numeric"
matrix_data <- matrix_data[stats::complete.cases(matrix_data), , drop = FALSE]

if (nrow(matrix_data) < 2 || ncol(matrix_data) < 2) {
  stop("热图可用数值数据太少", call. = FALSE)
}

if (nrow(matrix_data) > 80) {
  matrix_data <- matrix_data[seq_len(80), , drop = FALSE]
}
if (ncol(matrix_data) > 40) {
  matrix_data <- matrix_data[, seq_len(40), drop = FALSE]
}

scaled <- t(scale(t(matrix_data)))
scaled[is.na(scaled)] <- 0

row_order <- tryCatch(stats::hclust(stats::dist(scaled))$order, error = function(e) seq_len(nrow(scaled)))
col_order <- tryCatch(stats::hclust(stats::dist(t(scaled)))$order, error = function(e) seq_len(ncol(scaled)))
scaled <- scaled[row_order, col_order, drop = FALSE]

png(filename = output, width = 1200, height = 900, res = 150)
par(mar = c(7, 7, 4, 2))
image(
  x = seq_len(ncol(scaled)),
  y = seq_len(nrow(scaled)),
  z = t(scaled[nrow(scaled):1, , drop = FALSE]),
  col = palette_colors(option_text(options, "palette", "blue")),
  axes = FALSE,
  xlab = "",
  ylab = "",
  main = "SciDraw Heatmap"
)
axis(1, at = seq_len(ncol(scaled)), labels = colnames(scaled), las = 2, cex.axis = 0.7)
axis(2, at = seq_len(nrow(scaled)), labels = rev(rownames(scaled)), las = 2, cex.axis = 0.6)
box()
dev.off()
