args <- commandArgs(trailingOnly = TRUE)

read_arg <- function(name) {
  index <- match(name, args)
  if (is.na(index) || index == length(args)) {
    stop(paste("缺少参数", name), call. = FALSE)
  }
  args[index + 1]
}

script_dir <- function() {
  command_args <- commandArgs(trailingOnly = FALSE)
  file_arg <- grep("^--file=", command_args, value = TRUE)
  if (length(file_arg) > 0) {
    return(dirname(normalizePath(sub("^--file=", "", file_arg[1]), mustWork = TRUE)))
  }
  getwd()
}

input <- read_arg("--input")
output <- read_arg("--output")

node_script <- file.path(script_dir(), "pcr_charts.mjs")
out_dir <- file.path(dirname(output), "pcr_charts")
dir.create(out_dir, recursive = TRUE, showWarnings = FALSE)

# PCR options are handled by the backend. The Node script only needs data/output paths.
command <- c(
  node_script,
  "--input", input,
  "--out-dir", out_dir,
  "--output", output
)

result <- system2("node", command, stdout = TRUE, stderr = TRUE)
status <- attr(result, "status")
if (!is.null(status) && status != 0) {
  stop(paste(result, collapse = "\n"), call. = FALSE)
}

if (!file.exists(output) || file.info(output)$size <= 0) {
  stop("PCR 脚本未生成有效图片", call. = FALSE)
}
