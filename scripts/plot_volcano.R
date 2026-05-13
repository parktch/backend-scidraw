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

png(filename = output, width = 1200, height = 900, res = 150)
plot(
  x, y,
  pch = 19,
  col = ifelse(abs(x) >= 1 & y >= 1.3, "#D94F45", "#4E79A7"),
  xlab = numeric_columns[1],
  ylab = paste0("-log10(abs(", numeric_columns[2], "))"),
  main = "SciDraw Volcano Plot"
)
abline(v = c(-1, 1), col = "#888888", lty = 2)
abline(h = 1.3, col = "#888888", lty = 2)
dev.off()
