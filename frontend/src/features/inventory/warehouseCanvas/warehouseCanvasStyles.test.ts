import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { compileStyle, parse } from 'vue/compiler-sfc'
import postcss from 'postcss'
import { afterEach, describe, expect, it } from 'vitest'

const scope = 'data-v-warehouse-style-test'
function compiledRules(path: string) {
  const source = readFileSync(resolve(process.cwd(), path), 'utf8')
  const { descriptor } = parse(source)
  return postcss.parse(descriptor.styles.map((style) => compileStyle({ source: style.content, filename: path, id: scope, scoped: !!style.scoped }).code).join('\n'))
}

afterEach(() => { document.body.innerHTML = ''; document.head.querySelectorAll('[data-warehouse-style-test]').forEach((node) => node.remove()) })

describe('warehouse combined visual states', () => {
  it('does not apply search border paint to an invalid search match', () => {
    const rules = compiledRules('src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.vue')
    document.body.innerHTML = `<div class="warehouse-floor" ${scope}><div id="invalid" data-testid="warehouse-sku-block-invalid" data-search-match="true" data-warning="true" ${scope}></div><div id="valid" data-testid="warehouse-sku-block-valid" data-search-match="true" data-warning="false" ${scope}></div><div data-search-match="false" ${scope}></div></div>`
    const invalid = document.getElementById('invalid')!
    const valid = document.getElementById('valid')!
    const searchPaint = rules.nodes.filter((node): node is postcss.Rule => node.type === 'rule' && node.selector.includes('data-search-match') && node.nodes.some((decl) => decl.type === 'decl' && /^border/.test(decl.prop)))
    expect(searchPaint.some((rule) => valid.matches(rule.selector))).toBe(true)
    // jsdom does not implement CSS specificity; test actual compiled selector
    // eligibility here and verify final computed color in the real IAB.
    expect(searchPaint.some((rule) => invalid.matches(rule.selector))).toBe(false)
  })

  it('visibly outlines the search control when its input has focus', () => {
    const rules = compiledRules('src/features/inventory/views/WarehouseCanvasView.vue')
    const style = document.createElement('style')
    style.dataset.warehouseStyleTest = ''
    // jsdom's selector engine does not propagate :focus-within to ancestors.
    // Emulate only that browser pseudo-state; exercise the real compiled paint.
    // Native keyboard focus and computed longhands are also asserted in the IAB.
    rules.walkRules((rule) => { rule.selector = rule.selector.replaceAll(':focus-within', '[data-test-focus-within]') })
    style.textContent = rules.toString()
    document.head.append(style)
    document.body.innerHTML = `<span class="search-control" ${scope}><input aria-label="搜索 SKU 或产品名称" ${scope}></span>`
    const control = document.querySelector<HTMLElement>('.search-control')!
    const input = control.querySelector('input')!
    input.focus()
    expect(document.activeElement).toBe(input)
    control.toggleAttribute('data-test-focus-within', control.contains(document.activeElement))
    const appearance = getComputedStyle(control)
    // jsdom preserves outline shorthand rather than expanding its longhands.
    expect(appearance.outline).toMatch(/^2px solid /)
  })
})
