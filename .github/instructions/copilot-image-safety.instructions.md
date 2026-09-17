---
applyTo: "**"
---

# Copilot Image Safety

The installed Copilot Chat build can overflow its JavaScript call stack when an
image upload returns HTTP 401 and a large image is embedded as a Base64 fallback.

- Never return more than one image-producing tool result in a single assistant
  turn. This includes `view_image` and Blockbench screenshot tools.
- Before calling `view_image`, check the source file size. Do not call it directly
  for files larger than 1 MiB.
- For a larger source, create a temporary JPEG preview under
  `build/ai-previews/`, preserving aspect ratio, with a maximum dimension of
  1200 pixels and a target size below 500 KiB. Inspect only that preview and do
  not modify the source image.
- After one image has been inspected, use text-only tools for the rest of that
  assistant turn. Defer any additional image inspection to a later turn.