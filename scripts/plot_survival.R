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
    red = c("#4E79A7", "#D94F45", "#F28E2B", "#59A14F"),
    violet = c("#4ECDC4", "#7B61FF", "#B06AB3", "#45B7D1"),
    dark = c("#91C7B1", "#1D3557", "#E63946", "#457B9D"),
    c("#2A9D8F", "#4E79A7", "#E9C46A", "#E76F51")
  )
}

km_curve <- function(time, status) {
  order_index <- order(time)
  time <- time[order_index]
  status <- status[order_index]
  event_times <- sort(unique(time[status == 1]))
  survival <- 1
  x <- c(0)
  y <- c(1)
  for (event_time in event_times) {
    at_risk <- sum(time >= event_time)
    events <- sum(time == event_time & status == 1)
    if (at_risk > 0) {
      x <- c(x, event_time, event_time)
      y <- c(y, survival, survival * (1 - events / at_risk))
      survival <- survival * (1 - events / at_risk)
    }
  }
  x <- c(x, max(time))
  y <- c(y, survival)
  list(x = x, y = y)
}

options <- read_options(options_path)
data <- tryCatch(
  read.csv(input, check.names = FALSE, stringsAsFactors = FALSE),
  error = function(e) stop(paste("文件无法解析为 CSV:", e$message), call. = FALSE)
)

if (nrow(data) == 0 || ncol(data) < 2) {
  stop("生存曲线至少需要时间和状态两列", call. = FALSE)
}

numeric_columns <- names(data)[vapply(data, function(column) {
  values <- suppressWarnings(as.numeric(column))
  sum(!is.na(values)) >= max(1, floor(length(values) * 0.6))
}, logical(1))]

if (length(numeric_columns) < 2) {
  stop("生存曲线需要至少两列数值字段：时间和结局状态", call. = FALSE)
}

time_column <- numeric_columns[1]
status_column <- numeric_columns[2]
group_candidates <- setdiff(names(data), numeric_columns)
group_column <- if (length(group_candidates) > 0) group_candidates[1] else ""

time <- suppressWarnings(as.numeric(data[[time_column]]))
status_raw <- suppressWarnings(as.numeric(data[[status_column]]))
status <- ifelse(status_raw > 0, 1, 0)
groups <- if (group_column == "") factor("All") else as.factor(data[[group_column]])
valid <- !is.na(time) & time >= 0 & !is.na(status) & !is.na(groups) & groups != ""

if (sum(valid) < 2 || sum(status[valid] == 1) < 1) {
  stop("生存曲线可用数据太少或缺少事件状态", call. = FALSE)
}

time <- time[valid]
status <- status[valid]
groups <- droplevels(groups[valid])
if (nlevels(groups) > 6) {
  keep <- names(sort(table(groups), decreasing = TRUE))[seq_len(6)]
  selected <- groups %in% keep
  time <- time[selected]
  status <- status[selected]
  groups <- droplevels(groups[selected])
}

colors <- palette_colors(option_text(options, "palette", "blue"))
png(filename = output, width = 1200, height = 900, res = 150)
par(mar = c(5, 5, 4, 2))
plot(
  0,
  0,
  type = "n",
  xlim = c(0, max(time)),
  ylim = c(0, 1),
  xlab = time_column,
  ylab = "Survival probability",
  main = "SciDraw Survival Curve"
)
for (index in seq_along(levels(groups))) {
  group <- levels(groups)[index]
  selected <- groups == group
  curve <- km_curve(time[selected], status[selected])
  lines(curve$x, curve$y, type = "s", lwd = 2, col = colors[(index - 1) %% length(colors) + 1])
}
legend("topright", legend = levels(groups), col = rep(colors, length.out = nlevels(groups)), lwd = 2, bty = "n")
dev.off()
