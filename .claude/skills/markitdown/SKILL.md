---
name: markitdown
description: Convert files (PDF, Word, PowerPoint, Excel, images, audio, HTML, CSV, JSON, XML, ZIP, EPub, YouTube URLs) into clean Markdown for use with LLMs, using Microsoft's MarkItDown utility. Use when the user wants to extract or convert document/file content to Markdown/text, ingest documents for an LLM, OCR/transcribe content, or set up the markitdown MCP server.
---

# MarkItDown

MarkItDown is a lightweight Python utility from Microsoft (https://github.com/microsoft/markitdown)
that converts many file types into LLM-friendly Markdown while preserving structure
(headings, lists, tables, links). Use it whenever you need to turn a document into text/Markdown.

## When to use this skill

- Convert a PDF / DOCX / PPTX / XLSX / image / audio / HTML / CSV / JSON / XML / ZIP / EPub to Markdown
- Extract text from a YouTube URL (transcript)
- OCR an image or scanned PDF, or transcribe audio
- Batch-ingest documents to feed an LLM
- Stand up the `markitdown-mcp` MCP server so other agents can convert files

## Supported formats

PDF, PowerPoint (PPTX), Word (DOCX), Excel (XLSX/XLS), Outlook (MSG), images
(EXIF + OCR), audio (metadata + speech transcription), HTML, CSV, JSON, XML,
ZIP (iterates contents), EPub, and YouTube URLs.

## Installation

Requires **Python 3.10+**.

```bash
# Everything
pip install 'markitdown[all]'

# Or only the converters you need (smaller install)
pip install 'markitdown[pdf, docx, pptx, xlsx]'
```

Available extras: `[pdf]` `[docx]` `[pptx]` `[xlsx]` `[xls]` `[outlook]`
`[audio-transcription]` `[youtube-transcription]` `[az-doc-intel]`
`[az-content-understanding]` `[all]`.

From source:

```bash
git clone git@github.com:microsoft/markitdown.git
cd markitdown
pip install -e 'packages/markitdown[all]'
```

## Command-line usage

```bash
# Convert and redirect to a file
markitdown path-to-file.pdf > document.md

# Convert with -o output flag
markitdown path-to-file.pdf -o document.md

# Pipe input via stdin
cat path-to-file.pdf | markitdown

# List installed plugins
markitdown --list-plugins

# Enable third-party plugins
markitdown --use-plugins path-to-file.pdf

# Azure Document Intelligence
markitdown path-to-file.pdf -o document.md -d -e "<endpoint>"
```

## Python API

```python
from markitdown import MarkItDown

md = MarkItDown(enable_plugins=False)   # set enable_plugins=True to load plugins
result = md.convert("test.xlsx")
print(result.text_content)              # the Markdown text
```

The result object exposes `.text_content` (and `.markdown`) plus `.title` when available.

### Choosing the right convert method (security)

Prefer the narrowest method for your input source — sanitize untrusted input:

- `convert(source)` — convenience dispatch (path, URL, or stream).
- `convert_local(path)` — local files only.
- `convert_stream(stream, stream_info=...)` — a binary file-like object; pass
  a `StreamInfo` (e.g. `StreamInfo(extension=".pdf", mimetype="application/pdf")`)
  to hint the type when it can't be inferred.
- `convert_uri(uri)` — `http:`, `https:`, `file:`, or `data:` URIs.
- `convert_response(response)` — an existing `requests` response object.

### LLM image descriptions / OCR via vision model

```python
from markitdown import MarkItDown
from openai import OpenAI  # or any Anthropic-compatible client per the plugin

client = OpenAI()
md = MarkItDown(llm_client=client, llm_model="gpt-4o")
result = md.convert("example.jpg")
print(result.text_content)
```

The `markitdown-ocr` plugin adds LLM-vision OCR without extra ML libraries:

```bash
pip install markitdown-ocr openai
```

```python
md = MarkItDown(enable_plugins=True, llm_client=OpenAI(), llm_model="gpt-4o")
result = md.convert("document_with_images.pdf")
```

### Azure Document Intelligence

```python
md = MarkItDown(docintel_endpoint="<endpoint>")
result = md.convert("test.pdf")
```

### Azure Content Understanding (structured field extraction)

```bash
pip install 'markitdown[az-content-understanding]'
```

```python
md = MarkItDown(cu_endpoint="<endpoint>")           # auto-selects analyzer
result = md.convert("report.pdf")
print(result.markdown)                              # includes YAML front matter

# Custom analyzer + restrict file types to control cost
from markitdown.converters import ContentUnderstandingFileType
md = MarkItDown(
    cu_endpoint="<endpoint>",
    cu_analyzer_id="my-invoice-analyzer",
    cu_file_types=[ContentUnderstandingFileType.PDF],
)
```

## MCP server (markitdown-mcp)

Exposes one tool, `convert_to_markdown(uri)`, accepting `http:`, `https:`,
`file:`, or `data:` URIs. **Local/trusted use only** — do not expose beyond
localhost.

```bash
pip install markitdown-mcp

# STDIO (default)
markitdown-mcp

# Streamable HTTP / SSE
markitdown-mcp --http --host 127.0.0.1 --port 3001

# Docker
docker build -t markitdown-mcp:latest .
docker run -it --rm markitdown-mcp:latest
```

Claude Desktop / MCP client config (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "markitdown": {
      "command": "docker",
      "args": ["run", "--rm", "-i", "-v", "/home/user/data:/workdir", "markitdown-mcp:latest"]
    }
  }
}
```

The `-v /home/user/data:/workdir` mount is needed for the container to read local files.

## Docker (CLI)

```bash
docker build -t markitdown:latest .
docker run --rm -i markitdown:latest < ~/your-file.pdf > output.md
```

## Notes & gotchas

- Sanitize inputs; avoid converting untrusted data with broad methods.
- Python **3.10+** is required.
- Install only the extras you need to keep the footprint small; missing a
  converter extra (e.g. no `[pdf]`) means that format won't convert.
- For scanned PDFs/images, plain conversion won't read text — use the LLM/OCR
  plugin or Azure Document Intelligence.
