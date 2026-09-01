import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('global viewport layout', () => {
  it('allows the document body to shrink with the layout viewport', () => {
    const mainStyles = readFileSync(resolve(process.cwd(), 'src/assets/main.css'), 'utf8');
    const bodyRule = mainStyles.match(/body\s*\{(?<declarations>[^}]*)\}/)?.groups?.declarations;

    expect(bodyRule).toMatch(/min-width:\s*0\s*;/);
  });
});
