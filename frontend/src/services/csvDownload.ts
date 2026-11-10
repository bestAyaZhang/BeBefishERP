export type CsvValue = string | number | null | undefined;

export function escapeCsvCell(value: CsvValue, emptyValue = ''): string {
  if (value == null || (typeof value === 'number' && !Number.isFinite(value))) return emptyValue;
  if (typeof value === 'string' && !value.trim()) return emptyValue;
  // Prefix before any whitespace so spreadsheet import cannot expose a formula after trimming.
  const text = typeof value === 'string' && /^\s*[=+\-@]/u.test(value) ? `'${value}` : String(value);
  return /[",\r\n]/u.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function downloadCsv(content: string, filename: string): void {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  try {
    anchor.click();
  } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
