import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import postcss from 'postcss';
import tailwindcss from 'tailwindcss';
import { describe, expect, it } from 'vitest';
import tailwindConfig from '../../tailwind.config';

async function compileTypographyUtilities() {
  const source = [
    'font-sans',
    'font-numeric',
    'text-caption',
    'text-label',
    'text-body',
    'text-body-strong',
    'text-card-title',
    'text-section-title',
    'text-page-title',
    'text-table-number',
    'text-xs',
    'text-sm'
  ].join(' ');
  const result = await postcss([
    tailwindcss({
      ...tailwindConfig,
      content: [{ raw: `<div class="${source}"></div>`, extension: 'html' }]
    })
  ]).process('@tailwind utilities;', { from: undefined });
  return postcss.parse(result.css);
}

function declarationsFor(root: postcss.Root, selector: string) {
  const declarations: Record<string, string> = {};
  root.walkRules(selector, (rule) => {
    rule.walkDecls((declaration) => {
      declarations[declaration.prop] = declaration.value;
    });
  });
  return declarations;
}

describe('global viewport layout', () => {
  it('allows the document body to shrink with the layout viewport', () => {
    const mainStyles = readFileSync(resolve(process.cwd(), 'src/assets/main.css'), 'utf8');
    const bodyRule = mainStyles.match(/body\s*\{(?<declarations>[^}]*)\}/)?.groups?.declarations;

    expect(bodyRule).toMatch(/min-width:\s*0\s*;/);
  });

  it('compiles the Figma typography scale as project-wide Tailwind utilities', async () => {
    const utilities = await compileTypographyUtilities();

    expect(declarationsFor(utilities, '.font-sans')['font-family']).toContain('Noto Sans SC Variable');
    expect(declarationsFor(utilities, '.font-numeric')['font-family']).toMatch(/^"Inter Variable"/);
    expect(declarationsFor(utilities, '.text-caption')).toMatchObject({
      'font-size': '12px',
      'line-height': '18px',
      'font-weight': '400'
    });
    expect(declarationsFor(utilities, '.text-label')).toMatchObject({
      'font-size': '12px',
      'line-height': '18px',
      'font-weight': '500'
    });
    expect(declarationsFor(utilities, '.text-body')).toMatchObject({
      'font-size': '14px',
      'line-height': '22px',
      'font-weight': '400'
    });
    expect(declarationsFor(utilities, '.text-body-strong')).toMatchObject({
      'font-size': '14px',
      'line-height': '22px',
      'font-weight': '500'
    });
    expect(declarationsFor(utilities, '.text-card-title')).toMatchObject({
      'font-size': '15px',
      'line-height': '22px',
      'font-weight': '500'
    });
    expect(declarationsFor(utilities, '.text-section-title')).toMatchObject({
      'font-size': '16px',
      'line-height': '24px',
      'font-weight': '600'
    });
    expect(declarationsFor(utilities, '.text-page-title')).toMatchObject({
      'font-size': '24px',
      'line-height': '32px',
      'font-weight': '700'
    });
    expect(declarationsFor(utilities, '.text-table-number')).toMatchObject({
      'font-size': '14px',
      'line-height': '20px',
      'font-weight': '600'
    });
    expect(declarationsFor(utilities, '.text-xs')['line-height']).toBe('18px');
    expect(declarationsFor(utilities, '.text-sm')['line-height']).toBe('22px');
  });

  it('applies the Figma Chinese font stack at the application root', () => {
    const mainStyles = postcss.parse(readFileSync(resolve(process.cwd(), 'src/assets/main.css'), 'utf8'));
    const rootRule = mainStyles.nodes.find((node): node is postcss.Rule => node.type === 'rule' && node.selector === ':root');
    const fontFamily = rootRule?.nodes.find((node): node is postcss.Declaration => node.type === 'decl' && node.prop === 'font-family');

    expect(fontFamily?.value).toMatch(/^"Noto Sans SC Variable", "Noto Sans SC"/);
  });
});
