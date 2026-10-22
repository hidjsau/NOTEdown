#!/usr/bin/env bash
# Turns flat files named  a_b_c_file.ext  into  a/b/c/file.ext
# Special token: "dotgithub" becomes ".github"
# Safe to run many times. Files without "_" in the name stay where they are.
set -euo pipefail
cd "$(dirname "$0")"

count=0
shopt -s nullglob
for f in *_*; do
  [ -f "$f" ] || continue
  IFS='_' read -r -a parts <<< "$f"
  n=${#parts[@]}
  dir=""
  for ((i = 0; i < n - 1; i++)); do
    p="${parts[$i]}"
    [ -n "$p" ] || { echo "Bad file name (empty segment): $f" >&2; exit 1; }
    [ "$p" = "dotgithub" ] && p=".github"
    dir+="$p/"
  done
  name="${parts[$((n - 1))]}"
  [ -n "$name" ] || { echo "Bad file name (no file part): $f" >&2; exit 1; }
  mkdir -p "$dir"
  mv -f -- "$f" "$dir$name"
  echo "$f  ->  $dir$name"
  count=$((count + 1))
done
echo "Restructured $count file(s)."
