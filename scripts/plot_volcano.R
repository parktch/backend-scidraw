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

option_number <- function(options, name, default) {
  pattern <- paste0('"', name, '"\\s*:\\s*(-?[0-9]+\\.?[0-9]*)')
  matched <- regmatches(options, regexpr(pattern, options, perl = TRUE))
  if (length(matched) == 0 || matched == "") {
    return(default)
  }
  value <- suppressWarnings(as.numeric(sub(paste0('.*" ', name), "", matched)))
  value <- suppressWarnings(as.numeric(sub(paste0('.*"', name, '"\\s*:\\s*'), "", matched, perl = TRUE)))
  ifelse(is.na(value), default, value)
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
    red = c("#4E79A7", "#D94F45"),
    violet = c("#4ECDC4", "#7B61FF"),
    dark = c("#91C7B1", "#1D3557"),
    c("#2A9D8F", "#4E79A7")
  )
}

options <- read_options(options_path)
logfc_cutoff <- option_number(options, "logfc", 1)
pvalue_cutoff <- option_number(options, "pvalue", 0.05)
colors <- palette_colors(option_text(options, "palette", "blue"))

data <- tryCatch(
  read.csv(input, check.names = FALSE, stringsAsFactors = FALSE),
  error = function(e) stop(paste("文件无法解析为 CSV:", e$message), call. = FALSE)
)

if (nrow(data) == 0 || ncol(data) < 2) {
  stop("文件至少需要两列且包含数据行", call. = FALSE)
}

numeric_columns <- names(data)[vapply(data, function(column) {
  values <- suppressWarnings(as.numeric(column))
  sum(!is.na(values)) >= max(1, floor(length(values) * 0.6))
}, logical(1))]

if (length(numeric_columns) < 2) {
  stop("文件缺少可用于作图的数值列", call. = FALSE)
}

x <- suppressWarnings(as.numeric(data[[numeric_columns[1]]]))
y_raw <- suppressWarnings(as.numeric(data[[numeric_columns[2]]]))
valid <- !is.na(x) & !is.na(y_raw)

if (sum(valid) < 2) {
  stop("数值列格式不正确，可用于作图的数据太少", call. = FALSE)
}

y <- -log10(pmax(abs(y_raw[valid]), .Machine$double.xmin))
x <- x[valid]
threshold <- -log10(max(pvalue_cutoff, .Machine$double.xmin))
significant <- abs(x) >= logfc_cutoff & y >= threshold

png(filename = output, width = 1200, height = 900, res = 150)
plot(
  x, y,
  pch = 19,
  col = ifelse(significant, colors[2], colors[1]),
  xlab = numeric_columns[1],
  ylab = paste0("-log10(abs(", numeric_columns[2], "))"),
  main = "SciDraw Volcano Plot"
)
abline(v = c(-logfc_cutoff, logfc_cutoff), col = "#888888", lty = 2)
abline(h = threshold, col = "#888888", lty = 2)
dev.off()
