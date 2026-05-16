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
    red = c("#4E79A7", "#D94F45", "#F28E2B", "#76B7B2", "#59A14F"),
    violet = c("#4ECDC4", "#7B61FF", "#B06AB3", "#45B7D1", "#96CEB4"),
    dark = c("#91C7B1", "#1D3557", "#457B9D", "#A8DADC", "#E63946"),
    c("#2A9D8F", "#4E79A7", "#E9C46A", "#F4A261", "#E76F51")
  )
}

options <- read_options(options_path)
data <- tryCatch(
  read.csv(input, check.names = FALSE, stringsAsFactors = FALSE),
  error = function(e) stop(paste("文件无法解析为 CSV:", e$message), call. = FALSE)
)

if (nrow(data) == 0 || ncol(data) < 2) {
  stop("箱线图至少需要两列且包含数据行", call. = FALSE)
}

numeric_columns <- names(data)[vapply(data, function(column) {
  values <- suppressWarnings(as.numeric(column))
  sum(!is.na(values)) >= max(1, floor(length(values) * 0.6))
}, logical(1))]

if (length(numeric_columns) < 1) {
  stop("箱线图需要至少一列数值字段", call. = FALSE)
}

value_column <- numeric_columns[1]
group_candidates <- setdiff(names(data), numeric_columns)
group_column <- if (length(group_candidates) > 0) group_candidates[1] else names(data)[1]

values <- suppressWarnings(as.numeric(data[[value_column]]))
groups <- as.factor(if (group_column == value_column) "All" else data[[group_column]])
valid <- !is.na(values) & !is.na(groups) & groups != ""

if (sum(valid) < 2) {
  stop("箱线图可用数据太少", call. = FALSE)
}

values <- values[valid]
groups <- droplevels(groups[valid])
if (nlevels(groups) > 12) {
  keep <- names(sort(table(groups), decreasing = TRUE))[seq_len(12)]
  selected <- groups %in% keep
  values <- values[selected]
  groups <- droplevels(groups[selected])
}

colors <- palette_colors(option_text(options, "palette", "blue"))
png(filename = output, width = 1200, height = 900, res = 150)
par(mar = c(7, 5, 4, 2))
boxplot(
  values ~ groups,
  col = rep(colors, length.out = nlevels(groups)),
  xlab = group_column,
  ylab = value_column,
  main = "SciDraw Boxplot",
  las = 2,
  outline = TRUE
)
stripchart(values ~ groups, vertical = TRUE, method = "jitter", pch = 19, col = "#33333355", add = TRUE)
dev.off()
