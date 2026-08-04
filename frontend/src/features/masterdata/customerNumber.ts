export function generateCustomerNumber(): string {
  const now = new Date();
  const date = [now.getFullYear(), now.getMonth() + 1, now.getDate()]
    .map((value) => String(value).padStart(2, '0'))
    .join('');
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase().padEnd(6, '0');
  return `CUS-${date}-${suffix}`;
}
